package com.example.elementalarms.ability;

import com.example.elementalarms.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Every ability in the mod. Each returns true when it was actually used. */
public final class Abilities {
    private Abilities() {}

    /* ------------------------------------------------------------------ */
    /* helpers                                                             */
    /* ------------------------------------------------------------------ */

    static void fail(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("message.elementalarms." + key).withStyle(ChatFormatting.RED), true);
    }

    static DamageSource attack(ServerLevel level, ServerPlayer player) {
        return level.damageSources().playerAttack(player);
    }

    static Level.ExplosionInteraction blockInteraction() {
        return Config.BLOCK_DAMAGE.get() ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE;
    }

    private static void strike(ServerLevel level, ServerPlayer player, Vec3 pos) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return;
        bolt.moveTo(pos.x, pos.y, pos.z);
        bolt.setCause(player);
        level.addFreshEntity(bolt);
    }

    static void igniteNear(ServerLevel level, ServerPlayer player, Vec3 center, double radius, int attempts) {
        if (!Config.BLOCK_DAMAGE.get()) return;
        RandomSource rng = level.getRandom();
        for (int i = 0; i < attempts; i++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            double r = Math.sqrt(rng.nextDouble()) * radius;
            BlockPos base = BlockPos.containing(center.x + Math.cos(angle) * r, center.y + 1, center.z + Math.sin(angle) * r);
            for (int dy = 0; dy < 5; dy++) {
                BlockPos bp = base.below(dy);
                if (level.isEmptyBlock(bp) && level.mayInteract(player, bp)
                        && BaseFireBlock.canBePlacedAt(level, bp, Direction.UP)) {
                    level.setBlockAndUpdate(bp, BaseFireBlock.getState(level, bp));
                    break;
                }
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* TRIDENT - lightning                                                 */
    /* ------------------------------------------------------------------ */

    /** Calls a lightning bolt on whatever you are looking at. */
    public static boolean smite(ServerPlayer player, ServerLevel level) {
        Vec3 pos = Targeting.resolvePoint(player, 64);
        if (pos == null) {
            fail(player, "no_target");
            return false;
        }
        strike(level, player, pos);
        return true;
    }

    /** Zaps the targeted mob, then the electricity jumps between nearby mobs. */
    public static boolean chainLightning(ServerPlayer player, ServerLevel level) {
        Targeting.Target t = Targeting.pick(player, 40);
        LivingEntity first = t.entity();
        if (first == null || !Targeting.isValidTarget(player, first)) {
            fail(player, "no_target");
            return false;
        }
        Vec3 hand = player.getEyePosition().add(player.getLookAngle().scale(0.5)).add(0, -0.2, 0);
        arc(level, player, hand, first, new HashSet<>(), 5);
        return true;
    }

    private static void arc(ServerLevel level, ServerPlayer player, Vec3 from, LivingEntity target, Set<UUID> hit, int jumpsLeft) {
        if (player.isRemoved() || !target.isAlive()) return;
        hit.add(target.getUUID());

        Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Fx.beam(level, from, to, ParticleTypes.ELECTRIC_SPARK);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z, 20, 0.3, 0.4, 0.3, 0.3);
        level.playSound(null, to.x, to.y, to.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.8f, 1.5f);
        target.hurt(attack(level, player), Config.damage(6f));

        if (jumpsLeft <= 0) return;
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(8),
                e -> Targeting.isValidTarget(player, e) && !hit.contains(e.getUUID()));
        if (candidates.isEmpty()) return;
        candidates.sort(Comparator.comparingDouble((LivingEntity e) -> e.distanceToSqr(target)));
        LivingEntity next = candidates.get(0);
        Scheduler.schedule(3, () -> arc(level, player, to, next, hit, jumpsLeft - 1));
    }

    /** A rain of real lightning bolts around the targeted point. */
    public static boolean thunderstorm(ServerPlayer player, ServerLevel level) {
        Vec3 center = Targeting.resolvePoint(player, 64);
        if (center == null) {
            fail(player, "no_target");
            return false;
        }
        RandomSource rng = level.getRandom();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 2f, 0.8f);
        for (int i = 0; i < 14; i++) {
            Scheduler.schedule(i * 5, () -> {
                if (player.isRemoved()) return;
                double angle = rng.nextDouble() * Math.PI * 2;
                double r = Math.sqrt(rng.nextDouble()) * 9;
                double x = center.x + Math.cos(angle) * r;
                double z = center.z + Math.sin(angle) * r;
                Vec3 ground = Targeting.surfaceAt(player, x, center.y + 6, z, 30);
                if (ground == null) return;
                if (ground.distanceToSqr(player.position()) < 9) return; // keep the caster safe
                strike(level, player, ground);
            });
        }
        return true;
    }

    /** Turns you into a bolt: a fast dash that shocks everything you pass through. */
    public static boolean lightningDash(ServerPlayer player, ServerLevel level) {
        Vec3 look = player.getLookAngle();
        Vec3 velocity = new Vec3(look.x, look.y * 0.6 + 0.1, look.z).normalize().scale(2.2);
        player.setDeltaMovement(velocity);
        player.hurtMarked = true;
        player.fallDistance = 0;
        AbilityManager.grantNoFall(player, 80);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 1.5f, 1.4f);

        Set<UUID> hit = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            Scheduler.schedule(i, () -> {
                if (player.isRemoved()) return;
                Vec3 p = player.position().add(0, player.getBbHeight() * 0.5, 0);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 14, 0.4, 0.5, 0.4, 0.25);
                List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(1.5),
                        e -> Targeting.isValidTarget(player, e) && !hit.contains(e.getUUID()));
                for (LivingEntity e : victims) {
                    hit.add(e.getUUID());
                    e.hurt(attack(level, player), Config.damage(7f));
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 25, 0.3, 0.4, 0.3, 0.4);
                    level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.6f, 1.6f);
                }
            });
        }
        return true;
    }

    /** Toggles a thunderstorm on / off. */
    public static boolean weatherControl(ServerPlayer player, ServerLevel level) {
        if (!level.dimensionType().hasSkyLight() || level.dimensionType().hasCeiling()) {
            fail(player, "no_sky");
            return false;
        }
        boolean callStorm = !level.isThundering();
        if (callStorm) {
            level.setWeatherParameters(0, 6000, true, true);
        } else {
            level.setWeatherParameters(6000, 0, false, false);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 4f, 1f);
        player.displayClientMessage(Component.translatable(callStorm ? "message.elementalarms.storm_called" : "message.elementalarms.storm_cleared")
                .withStyle(ChatFormatting.AQUA), true);
        return true;
    }

    /* ------------------------------------------------------------------ */
    /* GOLDEN AXE - fire                                                   */
    /* ------------------------------------------------------------------ */

    /** Hurls a large fireball. */
    public static boolean fireball(ServerPlayer player, ServerLevel level) {
        Vec3 look = player.getLookAngle();
        LargeFireball ball = new LargeFireball(level, player, look.x, look.y, look.z, 1);
        ball.setPos(player.getEyePosition().add(look.scale(1.2)));
        level.addFreshEntity(ball);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1f, 1f);
        return true;
    }

    /** A cone of flames in front of you. */
    public static boolean flameCone(ServerPlayer player, ServerLevel level) {
        final double range = 9;
        final double cosHalfAngle = Math.cos(Math.toRadians(32));
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        RandomSource rng = level.getRandom();

        Vec3 origin = eye.add(0, -0.3, 0).add(look.scale(0.8));
        for (int i = 0; i < 90; i++) {
            Vec3 d = Fx.jitter(look, rng, 0.28);
            level.sendParticles(ParticleTypes.FLAME, origin.x, origin.y, origin.z, 0, d.x, d.y, d.z, 0.35 + rng.nextDouble() * 0.35);
        }
        for (int i = 0; i < 20; i++) {
            Vec3 d = Fx.jitter(look, rng, 0.25);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, origin.x, origin.y, origin.z, 0, d.x, d.y, d.z, 0.3);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1f, 0.8f);

        AABB box = new AABB(eye, eye.add(look.scale(range))).inflate(range * 0.5);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> Targeting.isValidTarget(player, x))) {
            Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
            double dist = to.length();
            if (dist > range || dist < 0.001) continue;
            if (to.normalize().dot(look) < cosHalfAngle) continue;
            e.hurt(attack(level, player), Config.damage(4f));
            e.setSecondsOnFire(8);
        }

        igniteNear(level, player, eye.add(look.scale(5)), 4.5, 14);
        return true;
    }

    /** A column of fire at the target that burns for about six seconds. */
    public static boolean firePillar(ServerPlayer player, ServerLevel level) {
        Vec3 center = Targeting.resolvePoint(player, 32);
        if (center == null) {
            fail(player, "no_target");
            return false;
        }
        level.playSound(null, center.x, center.y, center.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.5f, 0.6f);
        igniteNear(level, player, center, 2.5, 6);

        RandomSource rng = level.getRandom();
        for (int i = 0; i < 12; i++) {
            Scheduler.schedule(i * 10, () -> {
                if (player.isRemoved()) return;
                for (int k = 0; k < 40; k++) {
                    double angle = rng.nextDouble() * Math.PI * 2;
                    double r = rng.nextDouble() * 2.2;
                    double y = center.y + rng.nextDouble() * 5;
                    level.sendParticles(ParticleTypes.FLAME, center.x + Math.cos(angle) * r, y, center.z + Math.sin(angle) * r,
                            0, 0, 0.25, 0, 1.0);
                }
                level.sendParticles(ParticleTypes.LAVA, center.x, center.y + 0.5, center.z, 4, 1.0, 0.3, 1.0, 0);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y + 3, center.z, 3, 0.8, 1.5, 0.8, 0.02);
                level.playSound(null, center.x, center.y, center.z, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1f, 1f);

                AABB zone = new AABB(center.x - 2.5, center.y - 1, center.z - 2.5, center.x + 2.5, center.y + 5, center.z + 2.5);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, zone, x -> Targeting.isValidTarget(player, x))) {
                    e.hurt(attack(level, player), Config.damage(3f));
                    e.setSecondsOnFire(6);
                }
            });
        }
        return true;
    }

    /** Sucks nearby fire into you as absorption hearts. */
    public static boolean flameAbsorb(ServerPlayer player, ServerLevel level) {
        final int radius = 10;
        BlockPos origin = player.blockPosition();
        Vec3 chest = player.position().add(0, 1.0, 0);
        int absorbed = 0;
        int shown = 0;

        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -radius, -radius), origin.offset(radius, radius, radius))) {
            if (pos.distSqr(origin) > radius * radius) continue;
            if (!(level.getBlockState(pos).getBlock() instanceof BaseFireBlock)) continue;
            if (!level.mayInteract(player, pos)) continue;

            Vec3 p = Vec3.atCenterOf(pos);
            level.removeBlock(pos, false);
            absorbed++;
            if (shown++ < 40) {
                Vec3 toward = chest.subtract(p).scale(0.12);
                level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 0, toward.x, toward.y, toward.z, 1.0);
            }
        }

        List<LivingEntity> burning = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                e -> e.isOnFire() && (e == player || e.isAlliedTo(player) || (e instanceof TamableAnimal pet && pet.isOwnedBy(player))));
        for (LivingEntity e : burning) {
            e.clearFire();
            absorbed++;
        }

        if (absorbed == 0) {
            fail(player, "no_flames");
            return false;
        }

        float current = player.getAbsorptionAmount();
        player.setAbsorptionAmount(Math.max(current, Math.min(12f, current + absorbed)));
        player.heal(Math.min(absorbed, 10) * 0.5f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_AMBIENT, SoundSource.PLAYERS, 1f, 1.4f);
        player.displayClientMessage(Component.translatable("message.elementalarms.absorbed", absorbed).withStyle(ChatFormatting.GOLD), true);
        return true;
    }

    /* ------------------------------------------------------------------ */
    /* IRON SWORD - explosions                                             */
    /* ------------------------------------------------------------------ */

    /** An explosion exactly where you are looking. */
    public static boolean blast(ServerPlayer player, ServerLevel level) {
        Vec3 pos = Targeting.resolvePoint(player, 48);
        if (pos == null) {
            fail(player, "no_target");
            return false;
        }
        level.explode(player, pos.x, pos.y, pos.z, 3.5f, blockInteraction());
        return true;
    }

    /** Blow yourself forward with an explosion at your feet (you take no damage and no fall damage). */
    public static boolean rocketLeap(ServerPlayer player, ServerLevel level) {
        level.explode(player, player.getX(), player.getY(), player.getZ(), 2.5f, Level.ExplosionInteraction.NONE);

        Vec3 v = player.getLookAngle().scale(1.4);
        player.setDeltaMovement(v.x, Math.max(v.y, 0.0) + 0.6, v.z);
        player.hurtMarked = true;
        player.fallDistance = 0;
        AbilityManager.grantNoFall(player, 160);
        return true;
    }

    /** A line of explosions racing away from you across the ground. */
    public static boolean shockwave(ServerPlayer player, ServerLevel level) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        final Vec3 dir = flat.lengthSqr() > 1.0E-4 ? flat.normalize() : Vec3.directionFromRotation(0, player.getYRot());
        final Vec3 origin = player.position();

        for (int i = 0; i < 8; i++) {
            final Vec3 p = origin.add(dir.scale(3 + i * 2.5));
            Scheduler.schedule(i * 3, () -> {
                Vec3 ground = Targeting.surfaceAt(player, p.x, origin.y + 3, p.z, 12);
                if (ground == null) return;
                level.explode(player, ground.x, ground.y + 0.2, ground.z, 2.2f, blockInteraction());
            });
        }
        return true;
    }

    /** Marks a mob (or a spot); three seconds later it detonates. Marked mobs glow and are tracked. */
    public static boolean detonationMark(ServerPlayer player, ServerLevel level) {
        Targeting.Target t = Targeting.pick(player, 48);
        LivingEntity entity = t.entity();
        Vec3 start;

        if (entity != null && Targeting.isValidTarget(player, entity)) {
            entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, 70, 0, false, false));
            start = entity.position().add(0, entity.getBbHeight() * 0.5, 0);
        } else {
            entity = null;
            Vec3 point = Targeting.resolvePoint(player, 48);
            if (point == null) {
                fail(player, "no_target");
                return false;
            }
            start = point;
        }

        level.playSound(null, start.x, start.y, start.z, SoundEvents.TNT_PRIMED, SoundSource.PLAYERS, 1.2f, 1f);
        fuse(level, player, entity, start, 60);
        return true;
    }

    private static void fuse(ServerLevel level, ServerPlayer player, @Nullable Entity entity, Vec3 lastPos, int ticksLeft) {
        Vec3 current = (entity != null && entity.isAlive())
                ? entity.position().add(0, entity.getBbHeight() * 0.5, 0)
                : lastPos;

        if (ticksLeft <= 0) {
            level.explode(player, current.x, current.y, current.z, 3.5f, blockInteraction());
            return;
        }

        level.sendParticles(ParticleTypes.SMOKE, current.x, current.y + 0.5, current.z, 6, 0.3, 0.3, 0.3, 0.02);
        level.sendParticles(ParticleTypes.FLAME, current.x, current.y + 0.5, current.z, 2, 0.2, 0.2, 0.2, 0.01);
        if (ticksLeft % 20 == 0) {
            level.playSound(null, current.x, current.y, current.z, SoundEvents.CREEPER_PRIMED, SoundSource.PLAYERS, 1f,
                    1.0f + (60 - ticksLeft) / 60f * 0.8f);
        }
        Scheduler.schedule(5, () -> fuse(level, player, entity, current, ticksLeft - 5));
    }
}
