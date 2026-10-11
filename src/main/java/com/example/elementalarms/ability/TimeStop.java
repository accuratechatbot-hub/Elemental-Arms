package com.example.elementalarms.ability;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Freezes mobs, projectiles, primed TNT and falling blocks around the caster.
 *
 * Mobs get NoAI + NoGravity while frozen. Those flags are saved with the entity, so every frozen
 * entity also gets a tag; if the world is saved mid-freeze, the tag lets us undo it on load.
 */
public final class TimeStop {
    public static final String TAG_AI = "elementalarms_frozen_ai";
    public static final String TAG_GRAV = "elementalarms_frozen_grav";

    private static final class Frozen {
        final ServerLevel level;
        long until;
        final Vec3 pos;
        final Vec3 velocity;
        final boolean hadNoAi;
        final boolean hadNoGravity;
        final int fuse;

        Frozen(ServerLevel level, long until, Vec3 pos, Vec3 velocity, boolean hadNoAi, boolean hadNoGravity, int fuse) {
            this.level = level;
            this.until = until;
            this.pos = pos;
            this.velocity = velocity;
            this.hadNoAi = hadNoAi;
            this.hadNoGravity = hadNoGravity;
            this.fuse = fuse;
        }
    }

    private static final Map<UUID, Frozen> FROZEN = new HashMap<>();

    private TimeStop() {}

    private static boolean canFreeze(ServerPlayer caster, Entity e) {
        if (e instanceof Player || e.isSpectator() || e.isRemoved()) return false;
        if (e instanceof Mob) return Targeting.isValidTarget(caster, e);
        return e instanceof Projectile || e instanceof PrimedTnt || e instanceof FallingBlockEntity;
    }

    /** @return how many entities are now frozen */
    public static int freezeAround(ServerPlayer caster, ServerLevel level, double radius, int durationTicks) {
        AABB box = caster.getBoundingBox().inflate(radius);
        long until = level.getGameTime() + durationTicks;
        int count = 0;

        for (Entity e : level.getEntities(caster, box, x -> canFreeze(caster, x))) {
            if (e.distanceToSqr(caster) > radius * radius) continue;

            Frozen existing = FROZEN.get(e.getUUID());
            if (existing != null) {
                existing.until = Math.max(existing.until, until);
                count++;
                continue;
            }

            boolean hadNoAi = false;
            if (e instanceof Mob mob) {
                hadNoAi = mob.isNoAi();
                if (!hadNoAi) {
                    mob.setNoAi(true);
                    mob.addTag(TAG_AI);
                }
            }
            boolean hadNoGravity = e.isNoGravity();
            if (!hadNoGravity) {
                e.setNoGravity(true);
                e.addTag(TAG_GRAV);
            }
            int fuse = e instanceof PrimedTnt tnt ? tnt.getFuse() : -1;

            FROZEN.put(e.getUUID(), new Frozen(level, until, e.position(), e.getDeltaMovement(), hadNoAi, hadNoGravity, fuse));
            e.setDeltaMovement(Vec3.ZERO);
            count++;
        }
        return count;
    }

    public static boolean isFrozen(Entity e) {
        return FROZEN.containsKey(e.getUUID());
    }

    /** Called every server tick. */
    public static void tick() {
        if (FROZEN.isEmpty()) return;

        Iterator<Map.Entry<UUID, Frozen>> it = FROZEN.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Frozen> entry = it.next();
            Frozen f = entry.getValue();
            Entity e = f.level.getEntity(entry.getKey());

            if (e == null || e.isRemoved()) {
                // unloaded or dead; if it comes back with our tags, onEntityJoin cleans it up
                it.remove();
                continue;
            }
            if (f.level.getGameTime() >= f.until) {
                release(e, f);
                it.remove();
                continue;
            }

            e.setDeltaMovement(Vec3.ZERO);
            e.setPos(f.pos.x, f.pos.y, f.pos.z);
            if (e instanceof LivingEntity living) {
                living.setXxa(0);
                living.setYya(0);
                living.setZza(0);
            }
            if (e instanceof PrimedTnt tnt && f.fuse >= 0) {
                tnt.setFuse(f.fuse);
            }
            if (f.level.getGameTime() % 10 == 0) {
                f.level.sendParticles(ParticleTypes.END_ROD, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(),
                        2, 0.25, 0.35, 0.25, 0.0);
            }
        }
    }

    private static void release(Entity e, Frozen f) {
        if (e instanceof Mob mob && !f.hadNoAi) {
            mob.setNoAi(false);
        }
        e.removeTag(TAG_AI);
        if (!f.hadNoGravity) {
            e.setNoGravity(false);
        }
        e.removeTag(TAG_GRAV);
        if (!(e instanceof LivingEntity)) {
            e.setDeltaMovement(f.velocity);
            e.hurtMarked = true;
        }
    }

    /** Undo freeze flags on an entity that loaded with our tags but is not being tracked (e.g. after a restart). */
    public static void cleanup(Entity e) {
        if (e.getTags().contains(TAG_AI)) {
            if (e instanceof Mob mob) mob.setNoAi(false);
            e.removeTag(TAG_AI);
        }
        if (e.getTags().contains(TAG_GRAV)) {
            e.setNoGravity(false);
            e.removeTag(TAG_GRAV);
        }
    }

    public static boolean hasFreezeTag(Entity e) {
        return e.getTags().contains(TAG_AI) || e.getTags().contains(TAG_GRAV);
    }

    public static void releaseAll() {
        for (Map.Entry<UUID, Frozen> entry : FROZEN.entrySet()) {
            Entity e = entry.getValue().level.getEntity(entry.getKey());
            if (e != null) {
                release(e, entry.getValue());
            }
        }
        FROZEN.clear();
    }
}
