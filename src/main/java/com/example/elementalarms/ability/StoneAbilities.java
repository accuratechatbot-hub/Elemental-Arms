package com.example.elementalarms.ability;

import com.example.elementalarms.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Abilities of the Stone Sword: telekinesis, God Blast, time control, reality warping - all powered by aggressive fire. */
public final class StoneAbilities {
    private StoneAbilities() {}

    /* ------------------------------------------------------------------ */
    /* helpers                                                             */
    /* ------------------------------------------------------------------ */

    private static void say(ServerPlayer player, String key, ChatFormatting color) {
        player.displayClientMessage(Component.translatable("message.elementalarms." + key).withStyle(color), true);
    }

    private static double distToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        double t = len2 < 1.0E-6 ? 0 : Math.max(0, Math.min(1, p.subtract(a).dot(ab) / len2));
        return p.distanceTo(a.add(ab.scale(t)));
    }

    private static Vec3 mid(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    /* ------------------------------------------------------------------ */
    /* TELEKINESIS                                                         */
    /* ------------------------------------------------------------------ */

    /** Lifts the mob you look at, holds it in front of you for 2.5 seconds, then hurls it. It explodes on impact. */
    public static boolean telekineticGrab(ServerPlayer player, ServerLevel level) {
        LivingEntity target = Targeting.pick(player, 30).entity();
        if (target == null || !Targeting.isValidTarget(player, target)) {
            Abilities.fail(player, "no_target");
            return false;
        }
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2f, 1.6f);
        holdTick(level, player, target, 0);
        return true;
    }

    private static void holdTick(ServerLevel level, ServerPlayer player, LivingEntity target, int tick) {
        if (player.isRemoved() || !player.isAlive() || target.isRemoved() || !target.isAlive()) return;

        if (tick >= 50) {
            Vec3 look = player.getLookAngle();
            target.setDeltaMovement(look.scale(2.6));
            target.hurtMarked = true;
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.5f, 0.7f);
            flightTick(level, player, target, 0);
            return;
        }

        Vec3 hold = player.getEyePosition().add(player.getLookAngle().scale(4)).subtract(0, target.getBbHeight() * 0.5, 0);
        target.setDeltaMovement(hold.subtract(target.position()).scale(0.35));
        target.hurtMarked = true;
        target.fallDistance = 0;
        Vec3 p = mid(target);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z, 3, 0.3, 0.4, 0.3, 0.02);
        level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.3, 0.4, 0.3, 0.01);

        Scheduler.schedule(1, () -> holdTick(level, player, target, tick + 1));
    }

    private static void flightTick(ServerLevel level, ServerPlayer player, LivingEntity target, int tick) {
        if (tick > 40 || player.isRemoved() || target.isRemoved() || !target.isAlive()) return;

        if (tick > 2 && (target.horizontalCollision || target.verticalCollision || target.onGround())) {
            Vec3 p = target.position();
            target.hurt(Abilities.attack(level, player), Config.damage(12f));
            target.setSecondsOnFire(8);
            level.explode(player, p.x, p.y + 0.5, p.z, 2.5f, Config.BLOCK_DAMAGE.get(), Abilities.blockInteraction());
            return;
        }
        Vec3 p = mid(target);
        level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 6, 0.3, 0.3, 0.3, 0.05);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, 2, 0.2, 0.2, 0.2, 0.01);
        Scheduler.schedule(1, () -> flightTick(level, player, target, tick + 1));
    }

    /** A burning shockwave that throws everything in front of you away. */
    public static boolean forcePush(ServerPlayer player, ServerLevel level) {
        final double range = 13;
        final double cosHalfAngle = Math.cos(Math.toRadians(40));
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();

        for (int i = 1; i <= 12; i++) {
            Vec3 p = eye.add(look.scale(i * 1.0));
            level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 6, 0.3 + i * 0.12, 0.3 + i * 0.12, 0.3 + i * 0.12, 0.05);
            level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 4, 0.3 + i * 0.1, 0.3 + i * 0.1, 0.3 + i * 0.1, 0.05);
        }
        Vec3 boom = eye.add(look.scale(2));
        level.sendParticles(ParticleTypes.SONIC_BOOM, boom.x, boom.y, boom.z, 1, 0, 0, 0, 0);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.5f, 1.2f);

        AABB box = new AABB(eye, eye.add(look.scale(range))).inflate(range * 0.5);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> Targeting.isValidTarget(player, x))) {
            Vec3 to = mid(e).subtract(eye);
            double dist = to.length();
            if (dist > range || dist < 0.001) continue;
            if (to.normalize().dot(look) < cosHalfAngle) continue;

            e.hurt(Abilities.attack(level, player), Config.damage(6f));
            e.setSecondsOnFire(6);
            e.setDeltaMovement(look.x * 2.4, look.y * 1.2 + 0.6, look.z * 2.4);
            e.hurtMarked = true;
        }
        return true;
    }

    /* ------------------------------------------------------------------ */
    /* GOD BLAST                                                           */
    /* ------------------------------------------------------------------ */

    /** Charge for ~1.2s (you are slowed), then fire a huge beam of hellfire that explodes along its path. */
    public static boolean godBlast(ServerPlayer player, ServerLevel level) {
        final int charge = 25;
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 2f, 1.4f);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, charge, 3, false, false));

        RandomSource rng = level.getRandom();
        for (int i = 0; i < charge; i++) {
            final int tick = i;
            Scheduler.schedule(i, () -> {
                if (player.isRemoved() || !player.isAlive()) return;
                Vec3 hand = player.getEyePosition().add(player.getLookAngle().scale(1.6)).add(0, -0.3, 0);
                double radius = 0.4 + 3.0 * (1.0 - tick / (double) charge);
                for (int k = 0; k < 14; k++) {
                    Vec3 off = new Vec3(rng.nextGaussian(), rng.nextGaussian(), rng.nextGaussian()).normalize().scale(radius);
                    Vec3 p = hand.add(off);
                    Vec3 in = off.scale(-0.12);
                    level.sendParticles(k % 3 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME,
                            p.x, p.y, p.z, 0, in.x, in.y, in.z, 1.0);
                }
            });
        }
        Scheduler.schedule(charge, () -> fireGodBlast(player, level));
        return true;
    }

    private static void fireGodBlast(ServerPlayer player, ServerLevel level) {
        if (player.isRemoved() || !player.isAlive()) return;

        final double range = 70;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 start = eye.add(look.scale(1.6)).add(0, -0.3, 0);
        Vec3 far = eye.add(look.scale(range));
        BlockHitResult hit = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        final Vec3 end = hit.getType() == HitResult.Type.MISS ? far : hit.getLocation();
        final double length = start.distanceTo(end);
        final Vec3 dir = length < 0.001 ? look : end.subtract(start).normalize();

        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3f, 0.7f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3f, 0.6f);

        int steps = Math.max(1, (int) (length * 2));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = start.lerp(end, i / (double) steps);
            level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 4, 0.3, 0.3, 0.3, 0.02);
            if (i % 2 == 0) {
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z, 2, 0.15, 0.15, 0.15, 0.01);
            }
            if (i % 4 == 0) {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, 1, 0.4, 0.4, 0.4, 0.02);
            }
        }

        AABB box = new AABB(start, end).inflate(3);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> Targeting.isValidTarget(player, x))) {
            if (distToSegment(mid(e), start, end) > 3.0) continue;
            e.hurt(Abilities.attack(level, player), Config.damage(25f));
            e.setSecondsOnFire(15);
            e.setDeltaMovement(e.getDeltaMovement().add(dir.x * 1.8, 0.5, dir.z * 1.8));
            e.hurtMarked = true;
        }

        int n = 0;
        for (double d = 6; d < length - 2; d += 7) {
            final Vec3 p = start.add(dir.scale(d));
            Scheduler.schedule(n * 2, () ->
                    level.explode(player, p.x, p.y, p.z, 3.5f, Config.BLOCK_DAMAGE.get(), Abilities.blockInteraction()));
            n++;
        }
        Scheduler.schedule(n * 2 + 2, () -> {
            level.explode(player, end.x, end.y, end.z, 8f, Config.BLOCK_DAMAGE.get(), Abilities.blockInteraction());
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, end.x, end.y, end.z, 3, 1.5, 1.5, 1.5, 0);
        });

        player.push(-dir.x * 1.2, 0.2, -dir.z * 1.2);
        player.hurtMarked = true;
    }

    /* ------------------------------------------------------------------ */
    /* AGGRESSIVE FIRE                                                     */
    /* ------------------------------------------------------------------ */

    /** Rings of hellfire race outward from you, burning and throwing back everything they touch. */
    public static boolean infernoNova(ServerPlayer player, ServerLevel level) {
        final Vec3 c = player.position();
        final Set<UUID> hit = new HashSet<>();
        RandomSource rng = level.getRandom();
        level.playSound(null, c.x, c.y, c.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 2f, 0.5f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.5f, 1.2f);

        for (int ring = 1; ring <= 8; ring++) {
            final double r = ring * 1.6;
            Scheduler.schedule(ring * 2, () -> {
                if (player.isRemoved()) return;
                int points = (int) (r * 10);
                for (int i = 0; i < points; i++) {
                    double a = (i / (double) points) * Math.PI * 2;
                    double px = c.x + Math.cos(a) * r;
                    double pz = c.z + Math.sin(a) * r;
                    level.sendParticles(ParticleTypes.FLAME, px, c.y + 0.3, pz, 2, 0.05, 0.4, 0.05, 0.03);
                    if (rng.nextInt(6) == 0) {
                        level.sendParticles(ParticleTypes.LAVA, px, c.y + 0.5, pz, 1, 0.1, 0.2, 0.1, 0);
                    }
                }
                Abilities.igniteNear(level, player, c, r, 3);

                AABB box = new AABB(c.x - r - 1.5, c.y - 1, c.z - r - 1.5, c.x + r + 1.5, c.y + 3, c.z + r + 1.5);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> Targeting.isValidTarget(player, x))) {
                    if (hit.contains(e.getUUID())) continue;
                    double dx = e.getX() - c.x;
                    double dz = e.getZ() - c.z;
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    if (dist > r + 1.0) continue;
                    hit.add(e.getUUID());

                    e.hurt(Abilities.attack(level, player), Config.damage(9f));
                    e.setSecondsOnFire(10);
                    double len = Math.max(dist, 0.001);
                    e.setDeltaMovement(dx / len * 1.3, 0.5, dz / len * 1.3);
                    e.hurtMarked = true;
                }
            });
        }
        return true;
    }

    /* ------------------------------------------------------------------ */
    /* TIME CONTROL                                                        */
    /* ------------------------------------------------------------------ */

    /** Freezes every mob, projectile, primed TNT and falling block within 24 blocks for 5 seconds. Damage still lands. */
    public static boolean timeStop(ServerPlayer player, ServerLevel level) {
        int frozen = TimeStop.freezeAround(player, level, 24, 100);
        if (frozen == 0) {
            Abilities.fail(player, "no_target");
            return false;
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2f, 0.5f);
        for (int i = 0; i < 40; i++) {
            double ang = (i / 40.0) * Math.PI * 2;
            level.sendParticles(ParticleTypes.END_ROD, player.getX() + Math.cos(ang) * 3, player.getY() + 1.0,
                    player.getZ() + Math.sin(ang) * 3, 0, 0, 0.05, 0, 1.0);
        }
        say(player, "time_stopped", ChatFormatting.AQUA);
        return true;
    }

    /** Jumps time forward: day becomes midnight, night becomes the next sunrise. Overworld only. */
    public static boolean timeShift(ServerPlayer player, ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) {
            say(player, "no_time", ChatFormatting.RED);
            return false;
        }
        long dayTime = level.getDayTime();
        long inDay = dayTime % 24000L;
        long base = dayTime - inDay;
        boolean isDay = inDay < 12500L;

        level.setDayTime(base + (isDay ? 18000L : 24000L));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.5f, 1.5f);
        say(player, isDay ? "time_night" : "time_day", ChatFormatting.AQUA);
        return true;
    }

    /** Pulls you back to where you were ~5 seconds ago (and restores lost health). Needs the sword held for a few seconds first. */
    public static boolean rewind(ServerPlayer player, ServerLevel level) {
        TimeTracker.Snapshot snap = TimeTracker.oldest(player);
        if (snap == null) {
            say(player, "no_rewind", ChatFormatting.RED);
            return false;
        }

        Vec3 from = player.position().add(0, 1, 0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y, from.z, 60, 0.4, 0.8, 0.4, 0.2);

        player.teleportTo(level, snap.x(), snap.y(), snap.z(), snap.yRot(), snap.xRot());
        player.fallDistance = 0;
        player.clearFire();
        player.setHealth(Math.max(player.getHealth(), Math.min(snap.health(), player.getMaxHealth())));
        player.getFoodData().setFoodLevel(Math.max(player.getFoodData().getFoodLevel(), snap.food()));
        TimeTracker.clear(player.getUUID());

        level.sendParticles(ParticleTypes.REVERSE_PORTAL, snap.x(), snap.y() + 1, snap.z(), 60, 0.4, 0.8, 0.4, 0.2);
        level.playSound(null, snap.x(), snap.y(), snap.z(), SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.4f, 1.8f);
        say(player, "rewound", ChatFormatting.AQUA);
        return true;
    }

    /* ------------------------------------------------------------------ */
    /* REALITY WARPING                                                     */
    /* ------------------------------------------------------------------ */

    /** Instantly warps you up to 48 blocks in the direction you look (stops before walls). */
    public static boolean realityBlink(ServerPlayer player, ServerLevel level) {
        Vec3 start = player.position();
        Vec3 look = player.getLookAngle();
        AABB box = player.getBoundingBox();

        double best = 0;
        for (double d = 0.5; d <= 48; d += 0.5) {
            if (!level.noCollision(player, box.move(look.scale(d)))) break;
            best = d;
        }
        if (best < 2) {
            say(player, "blocked", ChatFormatting.RED);
            return false;
        }

        Vec3 dest = start.add(look.scale(best));
        Fx.beam(level, start.add(0, 1, 0), dest.add(0, 1, 0), ParticleTypes.SOUL_FIRE_FLAME);
        level.sendParticles(ParticleTypes.PORTAL, start.x, start.y + 1, start.z, 40, 0.4, 0.8, 0.4, 0.5);
        level.playSound(null, start.x, start.y, start.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 0.8f);

        player.teleportTo(level, dest.x, dest.y, dest.z, player.getYRot(), player.getXRot());
        player.fallDistance = 0;
        AbilityManager.grantNoFall(player, 60);

        level.sendParticles(ParticleTypes.PORTAL, dest.x, dest.y + 1, dest.z, 40, 0.4, 0.8, 0.4, 0.5);
        level.sendParticles(ParticleTypes.FLAME, dest.x, dest.y + 1, dest.z, 20, 0.4, 0.8, 0.4, 0.1);
        level.playSound(null, dest.x, dest.y, dest.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 1.2f);
        return true;
    }

    /** Opens a fiery black hole that drags everything nearby in for 3 seconds, then detonates. */
    public static boolean singularity(ServerPlayer player, ServerLevel level) {
        Vec3 ground = Targeting.resolvePoint(player, 40);
        if (ground == null) {
            Abilities.fail(player, "no_target");
            return false;
        }
        final Vec3 c = ground.add(0, 2.5, 0);
        RandomSource rng = level.getRandom();
        level.playSound(null, c.x, c.y, c.z, SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 1.5f, 0.5f);

        for (int t = 0; t < 60; t += 2) {
            final int tick = t;
            Scheduler.schedule(t, () -> {
                if (player.isRemoved()) return;

                for (int i = 0; i < 14; i++) {
                    double a = rng.nextDouble() * Math.PI * 2;
                    double r = 1.0 + rng.nextDouble() * 5.0;
                    Vec3 p = c.add(Math.cos(a) * r, (rng.nextDouble() - 0.5) * 3, Math.sin(a) * r);
                    Vec3 in = c.subtract(p).scale(0.15);
                    level.sendParticles(i % 2 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME,
                            p.x, p.y, p.z, 0, in.x, in.y, in.z, 1.0);
                }
                level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 4, 0.4, 0.4, 0.4, 0.02);
                if (tick % 10 == 0) {
                    level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 2f, 0.5f + tick / 120f);
                }

                AABB zone = new AABB(c, c).inflate(14);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, zone, x -> Targeting.isValidTarget(player, x))) {
                    Vec3 d = c.subtract(mid(e));
                    double dist = d.length();
                    if (dist < 0.5 || dist > 14) continue;
                    e.setDeltaMovement(d.normalize().scale(0.65));
                    e.hurtMarked = true;
                    e.fallDistance = 0;
                    if (dist < 5) {
                        e.hurt(Abilities.attack(level, player), Config.damage(1.5f));
                        e.setSecondsOnFire(5);
                    }
                }
            });
        }

        Scheduler.schedule(60, () -> {
            if (player.isRemoved()) return;
            level.explode(player, c.x, c.y, c.z, 5f, Config.BLOCK_DAMAGE.get(), Abilities.blockInteraction());
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 2, 1, 1, 1, 0);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(6),
                    x -> Targeting.isValidTarget(player, x))) {
                e.hurt(Abilities.attack(level, player), Config.damage(15f));
                e.setSecondsOnFire(12);
            }
        });
        return true;
    }
}
