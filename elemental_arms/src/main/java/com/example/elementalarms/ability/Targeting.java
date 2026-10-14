package com.example.elementalarms.ability;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public final class Targeting {
    private Targeting() {}

    /**
     * @param pos    where the ray ended (entity position, block hit location, or the far end of the ray)
     * @param entity the living entity under the crosshair, if any
     * @param hit    true if the ray hit an entity or a block
     */
    public record Target(Vec3 pos, @Nullable LivingEntity entity, boolean hit) {}

    public static Target pick(Player player, double range) {
        Level level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(range));

        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        boolean blockHit = block.getType() != HitResult.Type.MISS;
        double limitSqr = blockHit ? block.getLocation().distanceToSqr(eye) : range * range;

        AABB box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        EntityHitResult ent = ProjectileUtil.getEntityHitResult(level, player, eye, end, box,
                e -> e instanceof LivingEntity && !e.isSpectator() && e.isPickable());

        if (ent != null && ent.getEntity() instanceof LivingEntity living
                && eye.distanceToSqr(ent.getLocation()) <= limitSqr) {
            return new Target(living.position(), living, true);
        }
        if (blockHit) {
            return new Target(block.getLocation(), null, true);
        }
        return new Target(end, null, false);
    }

    /** Entity position, block hit location, or - if aiming at the sky - the ground below the end of the ray. */
    @Nullable
    public static Vec3 resolvePoint(Player player, double range) {
        Target t = pick(player, range);
        if (t.hit()) return t.pos();
        return surfaceAt(player, t.pos().x, t.pos().y, t.pos().z, 64);
    }

    /** First solid surface found when looking straight down from (x, yFrom, z). */
    @Nullable
    public static Vec3 surfaceAt(Entity context, double x, double yFrom, double z, double depth) {
        Vec3 from = new Vec3(x, yFrom, z);
        Vec3 to = new Vec3(x, yFrom - depth, z);
        BlockHitResult hit = context.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, context));
        return hit.getType() == HitResult.Type.MISS ? null : hit.getLocation();
    }

    public static boolean isValidTarget(Player caster, Entity e) {
        if (!(e instanceof LivingEntity living) || e == caster || !living.isAlive()
                || living.isSpectator() || e instanceof ArmorStand) {
            return false;
        }
        if (e instanceof TamableAnimal pet && pet.isOwnedBy(caster)) {
            return false;
        }
        if (VoidAbilities.isMinionOf(caster, e)) {
            return false;
        }
        return !e.isAlliedTo(caster);
    }
}
