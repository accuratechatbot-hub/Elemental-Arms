package com.example.elementalarms.ability;

import com.example.elementalarms.Config;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** The Alt Mode page: three extra abilities for each weapon. */
public final class AltAbilities {
    private AltAbilities() {}

    /* ------------------------------------------------------------------ */
    /* helpers                                                             */
    /* ------------------------------------------------------------------ */

    private static Vec3 mid(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    private static List<LivingEntity> targetsAround(ServerPlayer player, ServerLevel level, double radius) {
        return level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                e -> Targeting.isValidTarget(player, e) && e.distanceToSqr(player) <= radius * radius);
    }

    private static void strike(ServerLevel level, ServerPlayer player, Vec3 pos) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return;
        bolt.moveTo(pos.x, pos.y, pos.z);
        bolt.setCause(player);
        level.addFreshEntity(bolt);
    }

    private static Vec3 flatLook(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        return flat.lengthSqr() > 1.0E-4 ? flat.normalize() : Vec3.directionFromRotation(0, player.getYRot());
    }

    /* ------------------------------------------------------------------ */
    /* TRIDENT - alt: storm lord                                           */
    /* ------------------------------------------------------------------ */

    /** For 8 seconds, lightning arcs from you into the 3 nearest mobs every half second. */
    public static boolean stormAura(ServerPlayer player, ServerLevel level) {
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 1.5f, 1.2f);

        for (int i = 0; i < 16; i++) {
            Scheduler.schedule(i * 10, () -> {
                if (player.isRemoved() || !player.isAlive()) return;
                Vec3 c = player.position().add(0, 1.0, 0);

                for (int k = 0; k < 20; k++) {
                    double a = (k / 20.0) * Math.PI * 2;
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x + Math.cos(a) * 1.6, c.y, c.z + Math.sin(a) * 1.6, 1, 0.05, 0.3, 0.05, 0.05);
                }

                List<LivingEntity> near = targetsAround(player, level, 8);
                near.sort(Comparator.comparingDouble((LivingEntity e) -> e.distanceToSqr(player)));
                int zapped = 0;
                for (LivingEntity e : near) {
                    if (zapped++ >= 3) break;
                    Fx.beam(level, c, mid(e), ParticleTypes.ELECTRIC_SPARK);
                    e.hurt(Abilities.attack(level, player), Config.damage(4f));
                    level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.5f, 1.8f);
                }
            });
        }
        return true;
    }

    /** A thunderous shockwave: damages, stuns (slowness) and throws back everything within 10 blocks, with 3 real bolts. */
    public static boolean thunderClap(ServerPlayer player, ServerLevel level) {
        final Vec3 c = player.position();
        level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 3f, 1.3f);

        for (int ring = 1; ring <= 5; ring++) {
            final double r = ring * 2.0;
            Scheduler.schedule(ring, () -> {
                for (int i = 0; i < 40; i++) {
                    double a = (i / 40.0) * Math.PI * 2;
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x + Math.cos(a) * r, c.y + 0.4, c.z + Math.sin(a) * r, 2, 0.1, 0.3, 0.1, 0.1);
                }
            });
        }

        int bolts = 0;
        for (LivingEntity e : targetsAround(player, level, 10)) {
            e.hurt(Abilities.attack(level, player), Config.damage(8f));
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));

            Vec3 away = e.position().subtract(c);
            double len = Math.max(0.001, Math.sqrt(away.x * away.x + away.z * away.z));
            e.setDeltaMovement(away.x / len, 0.45, away.z / len);
            e.hurtMarked = true;

            if (bolts++ < 3) {
                strike(level, player, e.position());
            }
        }
        return true;
    }

    /** 20 seconds of Speed II, Strength II, Haste II and Resistance, crackling with sparks. */
    public static boolean overcharge(ServerPlayer player, ServerLevel level) {
        final int duration = 400;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, duration, 1));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, duration, 1));
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, duration, 1));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, duration, 0));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.5f, 1.6f);

        for (int i = 0; i < duration / 10; i++) {
            Scheduler.schedule(i * 10, () -> {
                if (player.isRemoved() || !player.isAlive()) return;
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.5, 0.8, 0.5, 0.15);
            });
        }
        return true;
    }

    /* ------------------------------------------------------------------ */
    /* GOLDEN AXE - alt: inferno lord                                      */
    /* ------------------------------------------------------------------ */

    /** A wave of magma races 24 blocks across the ground in front of you. */
    public static boolean magmaWave(ServerPlayer player, ServerLevel level) {
        final Vec3 dir = flatLook(player);
        final Vec3 origin = player.position();
        final Set<UUID> hit = new HashSet<>();

        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.5f, 0.5f);

        for (int i = 1; i <= 20; i++) {
            final Vec3 p = origin.add(dir.scale(i * 1.2));
            Scheduler.schedule(i, () -> {
                if (player.isRemoved()) return;
                Vec3 ground = Targeting.surfaceAt(player, p.x, origin.y + 3, p.z, 10);
                if (ground == null) return;

                level.sendParticles(ParticleTypes.FLAME, ground.x, ground.y + 0.4, ground.z, 15, 0.8, 0.3, 0.8, 0.05);
                level.sendParticles(ParticleTypes.LAVA, ground.x, ground.y + 0.4, ground.z, 3, 0.8, 0.2, 0.8, 0);
                level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8f, 0.6f);
                Abilities.igniteNear(level, player, ground, 1.5, 3);

                AABB box = new AABB(ground.x - 1.8, ground.y - 1, ground.z - 1.8, ground.x + 1.8, ground.y + 3, ground.z + 1.8);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> Targeting.isValidTarget(player, x))) {
                    if (!hit.add(e.getUUID())) continue;
                    e.hurt(Abilities.attack(level, player), Config.damage(7f));
                    e.setSecondsOnFire(8);
                    e.setDeltaMovement(dir.x * 0.5, 0.6, dir.z * 0.5);
                    e.hurtMarked = true;
                }
            });
        }
        return true;
    }

    /** 15 seconds wrapped in flame: Resistance II and Strength, and anything within 3.5 blocks burns. */
    public static boolean flameCloak(ServerPlayer player, ServerLevel level) {
        final int duration = 300;
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, duration, 1));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, duration, 0));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_AMBIENT, SoundSource.PLAYERS, 1.5f, 0.8f);

        for (int i = 0; i < duration / 10; i++) {
            Scheduler.schedule(i * 10, () -> {
                if (player.isRemoved() || !player.isAlive()) return;
                for (int k = 0; k < 16; k++) {
                    double a = (k / 16.0) * Math.PI * 2;
                    level.sendParticles(ParticleTypes.FLAME, player.getX() + Math.cos(a) * 1.2, player.getY() + 0.2 + (k % 4) * 0.5,
                            player.getZ() + Math.sin(a) * 1.2, 1, 0.05, 0.1, 0.05, 0.02);
                }
                for (LivingEntity e : targetsAround(player, level, 3.5)) {
                    e.hurt(Abilities.attack(level, player), Config.damage(2.5f));
                    e.setSecondsOnFire(5);
                }
            });
        }
        return true;
    }

    /** A spinning tornado of fire rolls forward 20 blocks, sucking up and burning everything in its path. */
    public static boolean fireTornado(ServerPlayer player, ServerLevel level) {
        final Vec3 dir = flatLook(player);
        final Vec3 origin = player.position().add(dir.scale(2));

        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 2f, 0.4f);

        for (int t = 0; t < 80; t++) {
            final int tick = t;
            Scheduler.schedule(t, () -> {
                if (player.isRemoved()) return;
                Vec3 base = origin.add(dir.scale(tick * 0.25));
                Vec3 ground = Targeting.surfaceAt(player, base.x, base.y + 4, base.z, 12);
                if (ground == null) ground = base;

                for (int h = 0; h < 9; h++) {
                    double radius = 0.5 + h * 0.35;
                    double angle = tick * 0.6 + h * 0.9;
                    level.sendParticles(ParticleTypes.FLAME, ground.x + Math.cos(angle) * radius, ground.y + h * 0.7,
                            ground.z + Math.sin(angle) * radius, 1, 0.05, 0.05, 0.05, 0.01);
                }

                if (tick % 4 == 0) {
                    final Vec3 center = ground.add(0, 2, 0);
                    AABB box = new AABB(ground.x - 3.5, ground.y - 1, ground.z - 3.5, ground.x + 3.5, ground.y + 7, ground.z + 3.5);
                    for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> Targeting.isValidTarget(player, x))) {
                        Vec3 pull = center.subtract(mid(e)).normalize().scale(0.5);
                        e.setDeltaMovement(pull.x, 0.35, pull.z);
                        e.hurtMarked = true;
                        e.hurt(Abilities.attack(level, player), Config.damage(2f));
                        e.setSecondsOnFire(6);
                    }
                }
                if (tick % 10 == 0) {
                    level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1.5f, 0.6f);
                }
            });
        }
        return true;
    }

    /* ------------------------------------------------------------------ */
    /* IRON SWORD - alt: demolition                                        */
    /* ------------------------------------------------------------------ */

    /** A cluster of 12 explosions scattered around the targeted point. */
    public static boolean clusterBomb(ServerPlayer player, ServerLevel level) {
        final Vec3 center = Targeting.resolvePoint(player, 48);
        if (center == null) {
            Abilities.fail(player, "no_target");
            return false;
        }
        RandomSource rng = level.getRandom();
        level.playSound(null, center.x, center.y, center.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 3f, 0.6f);

        for (int i = 0; i < 12; i++) {
            Scheduler.schedule(10 + i * 3, () -> {
                if (player.isRemoved()) return;
                double a = rng.nextDouble() * Math.PI * 2;
                double r = Math.sqrt(rng.nextDouble()) * 7;
                Vec3 g = Targeting.surfaceAt(player, center.x + Math.cos(a) * r, center.y + 4, center.z + Math.sin(a) * r, 14);
                if (g == null) return;
                level.explode(player, g.x, g.y + 0.3, g.z, 2.4f, Abilities.blockInteraction());
            });
        }
        return true;
    }

    /** Rocket up, then slam down: a huge explosion where you land that throws everything around away. */
    public static boolean groundSlam(ServerPlayer player, ServerLevel level) {
        Vec3 v = player.getDeltaMovement();
        player.setDeltaMovement(v.x * 0.2, 1.3, v.z * 0.2);
        player.hurtMarked = true;
        player.fallDistance = 0;
        AbilityManager.grantNoFall(player, 200);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 2f, 0.8f);

        Scheduler.schedule(12, () -> slamDown(player, level, 0));
        return true;
    }

    private static void slamDown(ServerPlayer player, ServerLevel level, int tick) {
        if (player.isRemoved() || !player.isAlive()) return;

        if (tick == 0) {
            player.setDeltaMovement(0, -2.4, 0);
            player.hurtMarked = true;
        } else if (player.onGround() || tick > 60) {
            Vec3 p = player.position();
            level.explode(player, p.x, p.y, p.z, 4f, Abilities.blockInteraction());
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.x, p.y + 0.5, p.z, 2, 1, 0.5, 1, 0);
            for (LivingEntity e : targetsAround(player, level, 8)) {
                e.hurt(Abilities.attack(level, player), Config.damage(12f));
                Vec3 away = e.position().subtract(p);
                double len = Math.max(0.001, Math.sqrt(away.x * away.x + away.z * away.z));
                e.setDeltaMovement(away.x / len * 1.4, 0.7, away.z / len * 1.4);
                e.hurtMarked = true;
            }
            return;
        }

        level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.5, player.getZ(), 6, 0.3, 0.5, 0.3, 0.05);
        Scheduler.schedule(1, () -> slamDown(player, level, tick + 1));
    }

    /** Marks up to 10 mobs within 16 blocks; they detonate one after another. */
    public static boolean chainReaction(ServerPlayer player, ServerLevel level) {
        List<LivingEntity> targets = targetsAround(player, level, 16);
        if (targets.isEmpty()) {
            Abilities.fail(player, "no_target");
            return false;
        }
        targets.sort(Comparator.comparingDouble((LivingEntity e) -> e.distanceToSqr(player)));
        int count = Math.min(10, targets.size());

        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TNT_PRIMED, SoundSource.PLAYERS, 1.5f, 1f);
        for (int i = 0; i < count; i++) {
            final LivingEntity e = targets.get(i);
            final int delay = 15 + i * 8;
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, delay + 10, 0, false, false));
            Scheduler.schedule(delay, () -> {
                if (e.isRemoved() || !e.isAlive()) return;
                Vec3 p = mid(e);
                level.explode(player, p.x, p.y, p.z, 2.8f, Abilities.blockInteraction());
            });
        }
        return true;
    }

    /* ------------------------------------------------------------------ */
    /* STONE SWORD - alt: reality lord                                     */
    /* ------------------------------------------------------------------ */

    /** Everything within 14 blocks is hoisted into the air, then slammed back to the ground. */
    public static boolean gravityCrush(ServerPlayer player, ServerLevel level) {
        List<LivingEntity> victims = targetsAround(player, level, 14);
        if (victims.isEmpty()) {
            Abilities.fail(player, "no_target");
            return false;
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 1.5f, 0.5f);

        for (LivingEntity e : victims) {
            e.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 40, 5, false, true));
        }
        Scheduler.schedule(40, () -> {
            for (LivingEntity e : victims) {
                if (e.isRemoved() || !e.isAlive()) continue;
                e.removeEffect(MobEffects.LEVITATION);
                e.fallDistance = 15f;
                e.setDeltaMovement(0, -3.0, 0);
                e.hurtMarked = true;
                e.hurt(Abilities.attack(level, player), Config.damage(10f));
                level.sendParticles(ParticleTypes.EXPLOSION, e.getX(), e.getY() + 0.5, e.getZ(), 1, 0, 0, 0, 0);
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.5f, 0.6f);
        });
        return true;
    }

    /** Swaps places with the mob you look at (and scorches it). */
    public static boolean realitySwap(ServerPlayer player, ServerLevel level) {
        LivingEntity target = Targeting.pick(player, 48).entity();
        if (target == null || !Targeting.isValidTarget(player, target)) {
            Abilities.fail(player, "no_target");
            return false;
        }
        Vec3 a = player.position();
        Vec3 b = target.position();

        level.sendParticles(ParticleTypes.PORTAL, a.x, a.y + 1, a.z, 40, 0.4, 0.8, 0.4, 0.5);
        level.sendParticles(ParticleTypes.PORTAL, b.x, b.y + 1, b.z, 40, 0.4, 0.8, 0.4, 0.5);
        level.playSound(null, a.x, a.y, a.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 0.8f);
        level.playSound(null, b.x, b.y, b.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 1.2f);

        if (target instanceof ServerPlayer other) {
            other.teleportTo(level, a.x, a.y, a.z, other.getYRot(), other.getXRot());
        } else {
            target.moveTo(a.x, a.y, a.z, target.getYRot(), target.getXRot());
        }
        player.teleportTo(level, b.x, b.y, b.z, player.getYRot(), player.getXRot());
        player.fallDistance = 0;
        target.fallDistance = 0;

        target.hurt(Abilities.attack(level, player), Config.damage(4f));
        target.setSecondsOnFire(6);
        return true;
    }

    /** 10 seconds where everything around you crawls and you move at lightning speed. */
    public static boolean timeDilation(ServerPlayer player, ServerLevel level) {
        for (LivingEntity e : targetsAround(player, level, 20)) {
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 4));
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1));
        }
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 3));
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 200, 2));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 1));

        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2f, 0.6f);
        for (int w = 0; w < 4; w++) {
            final double r = 3 + w * 4;
            Scheduler.schedule(w * 3, () -> {
                if (player.isRemoved()) return;
                for (int i = 0; i < 50; i++) {
                    double ang = (i / 50.0) * Math.PI * 2;
                    level.sendParticles(ParticleTypes.END_ROD, player.getX() + Math.cos(ang) * r, player.getY() + 1.0,
                            player.getZ() + Math.sin(ang) * r, 0, 0, 0.02, 0, 1.0);
                }
            });
        }
        return true;
    }
}
