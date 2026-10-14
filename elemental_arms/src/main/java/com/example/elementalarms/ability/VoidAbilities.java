package com.example.elementalarms.ability;

import com.example.elementalarms.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Netherite Sword: ender manipulation, necromancy and the World Ender. */
public final class VoidAbilities {
    public static final String MINION_TAG = "elementalarms_minion";
    public static final String OWNER_KEY = "ElementalArmsOwner";
    /** Minions that are alive in this server session (anything tagged but not in here is stale). */
    private static final Set<UUID> MINIONS = new HashSet<>();

    private static final int CHARGE_TICKS = 80;
    private static final int MINION_LIFETIME = 1200;

    private VoidAbilities() {}

    /* ------------------------------------------------------------------ */
    /* helpers                                                             */
    /* ------------------------------------------------------------------ */

    public static boolean isMinion(Entity e) {
        return MINIONS.contains(e.getUUID());
    }

    public static boolean isMinionOf(Player owner, Entity e) {
        return isMinion(e) && e.getPersistentData().hasUUID(OWNER_KEY)
                && e.getPersistentData().getUUID(OWNER_KEY).equals(owner.getUUID());
    }

    /** Tagged as a minion in a save file, but this session did not summon it. */
    public static boolean isStaleMinion(Entity e) {
        return e.getTags().contains(MINION_TAG) && !MINIONS.contains(e.getUUID());
    }

    public static void clearMinions() {
        MINIONS.clear();
    }

    private static boolean canHarm(ServerLevel level, ServerPlayer caster, Entity e) {
        if (!Targeting.isValidTarget(caster, e)) return false;
        return !(e instanceof Player) || level.getServer().isPvpAllowed();
    }

    private static List<LivingEntity> around(ServerLevel level, ServerPlayer player, Vec3 center, double radius) {
        AABB box = new AABB(center, center).inflate(radius);
        List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> canHarm(level, player, e) && e.position().distanceToSqr(center) <= radius * radius);
        return found;
    }

    private static void warp(ServerLevel level, Entity e, double x, double y, double z) {
        if (e instanceof ServerPlayer sp) {
            sp.teleportTo(level, x, y, z, sp.getYRot(), sp.getXRot());
        } else {
            e.moveTo(x, y, z, e.getYRot(), e.getXRot());
            e.setDeltaMovement(Vec3.ZERO);
        }
        e.fallDistance = 0;
    }

    private static void teleportFx(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 1, at.z, 50, 0.4, 0.8, 0.4, 0.6);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1, at.z, 20, 0.3, 0.7, 0.3, 0.1);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 1f);
    }

    /** True if the entity's box would fit at feet position {@code feet}. */
    private static boolean fits(ServerLevel level, Entity e, Vec3 feet) {
        BlockPos bp = BlockPos.containing(feet);
        if (!level.hasChunkAt(bp) || level.isOutsideBuildHeight(bp)) return false;
        AABB box = e.getBoundingBox().move(feet.subtract(e.position()));
        return level.noCollision(e, box);
    }

    /** Removes blocks in a sphere. Returns how many were removed. */
    private static void disintegrate(ServerLevel level, ServerPlayer player, Vec3 center, double radius, boolean keepBlockEntities) {
        if (!Config.BLOCK_DAMAGE.get()) return;
        int r = (int) Math.ceil(radius);
        BlockPos c = BlockPos.containing(center);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radius * radius) continue;
                    pos.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    removeBlock(level, player, pos, keepBlockEntities);
                }
            }
        }
    }

    private static void removeBlock(ServerLevel level, ServerPlayer player, BlockPos pos, boolean keepBlockEntities) {
        if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) return;
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0) return;
        if (!level.mayInteract(player, pos)) return;
        if (keepBlockEntities && level.getBlockEntity(pos) != null) return;
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    private static void bolt(ServerLevel level, double x, double y, double z) {
        LightningBolt b = EntityType.LIGHTNING_BOLT.create(level);
        if (b == null) return;
        b.moveTo(x, y, z);
        b.setVisualOnly(true);
        level.addFreshEntity(b);
    }

    /* ------------------------------------------------------------------ */
    /* primary abilities                                                   */
    /* ------------------------------------------------------------------ */

    /** Teleport up to 100 blocks where you look. */
    public static boolean riftStep(ServerPlayer player, ServerLevel level) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(100));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double dist = hit.getType() == HitResult.Type.MISS ? 100 : eye.distanceTo(hit.getLocation()) - 0.6;

        for (double d = dist; d >= 2; d -= 0.5) {
            Vec3 feet = eye.add(look.scale(d)).subtract(0, player.getEyeHeight(), 0);
            if (fits(level, player, feet)) {
                teleportFx(level, player.position());
                warp(level, player, feet.x, feet.y, feet.z);
                AbilityManager.grantNoFall(player, 60);
                teleportFx(level, feet);
                return true;
            }
        }
        Abilities.fail(player, "blocked");
        return false;
    }

    /** Teleport behind the mob you look at and slash it. */
    public static boolean phaseStrike(ServerPlayer player, ServerLevel level) {
        LivingEntity target = Targeting.pick(player, 48).entity();
        if (target == null || !canHarm(level, player, target)) {
            Abilities.fail(player, "no_target");
            return false;
        }
        Vec3 facing = target.getLookAngle().multiply(1, 0, 1);
        if (facing.lengthSqr() < 1.0E-4) facing = new Vec3(0, 0, 1);
        facing = facing.normalize();

        Vec3 dest = null;
        for (int i = 0; i < 8 && dest == null; i++) {
            double ang = Math.toRadians(i * 45);
            double cos = Math.cos(ang), sin = Math.sin(ang);
            Vec3 dir = new Vec3(facing.x * cos - facing.z * sin, 0, facing.x * sin + facing.z * cos);
            Vec3 feet = target.position().subtract(dir.scale(1.6));
            if (fits(level, player, feet)) dest = feet;
        }
        if (dest == null) {
            Abilities.fail(player, "blocked");
            return false;
        }

        teleportFx(level, player.position());
        double dx = target.getX() - dest.x, dz = target.getZ() - dest.z;
        float yaw = (float) (Mth.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        player.teleportTo(level, dest.x, dest.y, dest.z, yaw, 0f);
        player.fallDistance = 0;
        AbilityManager.grantNoFall(player, 60);
        teleportFx(level, dest);

        target.hurt(Abilities.attack(level, player), Config.damage(16f));
        target.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + 1, target.getZ(), 3, 0.3, 0.3, 0.3, 0);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1f, 0.7f);
        return true;
    }

    /** Fires a volley of dragon fireballs (lingering purple acid). */
    public static boolean dragonBreath(ServerPlayer player, ServerLevel level) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        RandomSource rng = level.getRandom();
        for (int i = 0; i < 5; i++) {
            Vec3 dir = Fx.jitter(look, rng, i == 0 ? 0.0 : 0.12);
            DragonFireball ball = new DragonFireball(level, player, dir.x, dir.y, dir.z);
            ball.setPos(eye.x + look.x * 1.5, eye.y - 0.2 + look.y * 1.5, eye.z + look.z * 1.5);
            level.addFreshEntity(ball);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.PLAYERS, 1.5f, 1f);
        return true;
    }

    /** Throws every mob around you to random places far away. */
    public static boolean realityScatter(ServerPlayer player, ServerLevel level) {
        List<LivingEntity> victims = around(level, player, player.position(), 24);
        if (victims.isEmpty()) {
            Abilities.fail(player, "no_target");
            return false;
        }
        RandomSource rng = level.getRandom();
        int moved = 0;
        for (LivingEntity e : victims) {
            double angle = rng.nextDouble() * Math.PI * 2;
            double dist = 12 + rng.nextDouble() * 16;
            double x = player.getX() + Math.cos(angle) * dist;
            double z = player.getZ() + Math.sin(angle) * dist;
            Vec3 surface = Targeting.surfaceAt(player, x, player.getY() + 20, z, 60);
            if (surface == null) continue;
            teleportFx(level, e.position());
            warp(level, e, surface.x, surface.y, surface.z);
            teleportFx(level, surface);
            moved++;
        }
        if (moved == 0) {
            Abilities.fail(player, "no_target");
            return false;
        }
        return true;
    }

    /** Raises eight undead minions that fight for you for 60 seconds. */
    public static boolean raiseDead(ServerPlayer player, ServerLevel level) {
        RandomSource rng = level.getRandom();
        int spawned = 0;
        for (int i = 0; i < 8; i++) {
            EntityType<? extends Mob> type;
            if (i % 4 == 3) {
                type = EntityType.WITHER_SKELETON;
            } else if (i % 2 == 0) {
                type = EntityType.ZOMBIE;
            } else {
                type = EntityType.SKELETON;
            }
            Mob mob = type.create(level);
            if (mob == null) continue;

            double angle = (Math.PI * 2 / 8) * i;
            double x = player.getX() + Math.cos(angle) * 3;
            double z = player.getZ() + Math.sin(angle) * 3;
            Vec3 surface = Targeting.surfaceAt(player, x, player.getY() + 3, z, 10);
            double y = surface == null ? player.getY() : surface.y;
            mob.moveTo(x, y, z, rng.nextFloat() * 360f, 0f);

            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.MOB_SUMMONED, null, null);
            if (mob instanceof Zombie zombie) zombie.setBaby(false);
            mob.setPersistenceRequired();
            mob.addTag(MINION_TAG);
            mob.getPersistentData().putUUID(OWNER_KEY, player.getUUID());
            mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MINION_LIFETIME + 100, 0, false, false));
            mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, MINION_LIFETIME, 1, false, false));
            mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, MINION_LIFETIME, 1, false, false));

            MINIONS.add(mob.getUUID());
            level.addFreshEntity(mob);
            level.sendParticles(ParticleTypes.SOUL, x, y + 1, z, 20, 0.3, 0.6, 0.3, 0.05);
            spawned++;
            Scheduler.schedule(10, () -> minionTick(level, mob, player.getUUID(), MINION_LIFETIME - 10));
        }
        if (spawned == 0) return false;
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.6f, 1.6f);
        return true;
    }

    private static void minionTick(ServerLevel level, Mob mob, UUID ownerId, int ticksLeft) {
        if (mob.isRemoved() || !mob.isAlive()) {
            MINIONS.remove(mob.getUUID());
            return;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
        if (ticksLeft <= 0 || owner == null) {
            level.sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getY() + 1, mob.getZ(), 15, 0.3, 0.6, 0.3, 0.05);
            MINIONS.remove(mob.getUUID());
            mob.discard();
            return;
        }

        if (owner.level() == level && mob.distanceToSqr(owner) > 40 * 40) {
            mob.moveTo(owner.getX() + 1, owner.getY(), owner.getZ() + 1, mob.getYRot(), 0f);
        }

        LivingEntity current = mob.getTarget();
        if (current == null || !current.isAlive()) {
            LivingEntity best = null;
            double bestDist = Double.MAX_VALUE;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(20),
                    x -> x instanceof Enemy && x.isAlive() && !isMinion(x))) {
                double d = e.distanceToSqr(mob);
                if (d < bestDist) {
                    bestDist = d;
                    best = e;
                }
            }
            if (best != null) mob.setTarget(best);
        }

        Scheduler.schedule(10, () -> minionTick(level, mob, ownerId, ticksLeft - 10));
    }

    /** Drains the life of everything near you. */
    public static boolean soulDrain(ServerPlayer player, ServerLevel level) {
        List<LivingEntity> victims = around(level, player, player.position(), 14);
        if (victims.isEmpty()) {
            Abilities.fail(player, "no_target");
            return false;
        }
        Vec3 from = player.getEyePosition().subtract(0, 0.3, 0);
        float total = 0;
        for (LivingEntity e : victims) {
            e.hurt(Abilities.attack(level, player), Config.damage(7f));
            e.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
            Fx.beam(level, e.position().add(0, e.getBbHeight() / 2, 0), from, ParticleTypes.SOUL);
            total += 3.5f;
        }
        player.heal(Math.min(total, 10f));
        player.setAbsorptionAmount(Math.min(12f, player.getAbsorptionAmount() + total / 2));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 1f, 0.6f);
        return true;
    }

    /* ------------------------------------------------------------------ */
    /* WORLD ENDER                                                         */
    /* ------------------------------------------------------------------ */

    public static boolean worldEnder(ServerPlayer player, ServerLevel level) {
        Vec3 c = Targeting.resolvePoint(player, 96);
        if (c == null) {
            Abilities.fail(player, "no_target");
            return false;
        }
        final int radius = Config.WORLD_ENDER_RADIUS.get();

        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.elementalarms.world_ender_broadcast", player.getDisplayName())
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), false);
        player.displayClientMessage(Component.translatable("message.elementalarms.world_ender")
                .withStyle(ChatFormatting.DARK_PURPLE), true);

        if (level.dimension() == Level.OVERWORLD) {
            level.setWeatherParameters(0, 6000, true, true);
        }
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 4f, 0.5f);

        // charge: a closing ring of soul fire and a vortex rising from the target
        for (int t = 0; t < CHARGE_TICKS; t += 2) {
            final int tick = t;
            Scheduler.schedule(t, () -> {
                double progress = tick / (double) CHARGE_TICKS;
                double ringR = radius * (1.0 - 0.3 * progress);
                for (int i = 0; i < 48; i++) {
                    double a = Math.PI * 2 * i / 48 + tick * 0.05;
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, c.x + Math.cos(a) * ringR, c.y + 0.3,
                            c.z + Math.sin(a) * ringR, 1, 0.1, 0.4, 0.1, 0.02);
                }
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y + 1, c.z, 40, 1.5, 3.0 * progress + 0.5, 1.5, 0.3);
                if (tick % 20 == 0) {
                    level.playSound(null, c.x, c.y, c.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 4f, 0.6f + (float) progress * 0.4f);
                    RandomSource rng = level.getRandom();
                    bolt(level, c.x + (rng.nextDouble() - 0.5) * radius * 2, c.y, c.z + (rng.nextDouble() - 0.5) * radius * 2);
                }
            });
        }

        Scheduler.schedule(CHARGE_TICKS, () -> beginCollapse(level, player, c, radius));
        return true;
    }

    private static long[] sphere(int radius) {
        int r2 = radius * radius;
        long[] tmp = new long[(int) (4.2 * radius * radius * radius) + 64];
        int n = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 > r2) continue;
                    if (n == tmp.length) tmp = Arrays.copyOf(tmp, tmp.length * 2);
                    tmp[n++] = ((long) d2 << 21) | ((long) (dx + 64) << 14) | ((long) (dy + 64) << 7) | (long) (dz + 64);
                }
            }
        }
        long[] out = Arrays.copyOf(tmp, n);
        Arrays.sort(out);
        return out;
    }

    private static void beginCollapse(ServerLevel level, ServerPlayer player, Vec3 c, int radius) {
        if (player.isRemoved()) return;
        final boolean destroy = Config.BLOCK_DAMAGE.get();
        final long[] keys = sphere(radius);
        final int perTick = Math.min(3000, Math.max(200, keys.length / 100 + 1));
        final int[] cursor = {0};
        final BlockPos center = BlockPos.containing(c);
        AbilityManager.grantNoFall(player, 400);

        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 6f, 0.4f);
        collapseStep(level, player, c, center, keys, cursor, perTick, destroy, 0, radius);
    }

    private static void collapseStep(ServerLevel level, ServerPlayer player, Vec3 c, BlockPos center, long[] keys,
                                     int[] cursor, int perTick, boolean destroy, int tick, int radius) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        if (destroy) {
            int end = Math.min(keys.length, cursor[0] + perTick);
            for (int i = cursor[0]; i < end; i++) {
                long k = keys[i];
                int dx = (int) ((k >> 14) & 127) - 64;
                int dy = (int) ((k >> 7) & 127) - 64;
                int dz = (int) (k & 127) - 64;
                pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                removeBlock(level, player, pos, false);
            }
            cursor[0] = end;
        } else {
            cursor[0] = Math.min(keys.length, cursor[0] + Math.max(1, keys.length / 100));
        }

        double currentR = cursor[0] == 0 ? 0 : Math.sqrt((double) (keys[cursor[0] - 1] >> 21));
        boolean done = cursor[0] >= keys.length;
        RandomSource rng = level.getRandom();

        // everything inside the growing void dies
        if (tick % 2 == 0) {
            double reach = currentR + 2;
            AABB box = new AABB(c, c).inflate(reach);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                    x -> canHarm(level, player, x) && x.position().distanceToSqr(c) <= reach * reach)) {
                e.hurt(level.damageSources().genericKill(), Config.damage(100000f));
            }
        }

        // effects along the edge of the void
        for (int i = 0; i < 40; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double b = (rng.nextDouble() - 0.5) * Math.PI;
            double rr = Math.max(1, currentR);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x + Math.cos(a) * Math.cos(b) * rr,
                    c.y + Math.sin(b) * rr, c.z + Math.sin(a) * Math.cos(b) * rr, 1, 0.2, 0.2, 0.2, 0.05);
        }
        if (tick % 8 == 0) {
            double a = rng.nextDouble() * Math.PI * 2;
            double d = rng.nextDouble() * (radius + 6);
            bolt(level, c.x + Math.cos(a) * d, c.y + 2, c.z + Math.sin(a) * d);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3f, 0.5f + rng.nextFloat() * 0.4f);
        }

        if (done) {
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 6, radius / 4.0, radius / 4.0, radius / 4.0, 0);
            level.explode(player, c.x, c.y, c.z, 10f, false, Abilities.blockInteraction());
            level.playSound(null, c.x, c.y, c.z, SoundEvents.ENDER_DRAGON_DEATH, SoundSource.HOSTILE, 6f, 0.5f);
            return;
        }
        Scheduler.schedule(1, () -> collapseStep(level, player, c, center, keys, cursor, perTick, destroy, tick + 1, radius));
    }

    /* ------------------------------------------------------------------ */
    /* alt abilities                                                       */
    /* ------------------------------------------------------------------ */

    /** Yanks every mob within 40 blocks to a point in front of you. */
    public static boolean riftSummon(ServerPlayer player, ServerLevel level) {
        List<LivingEntity> victims = around(level, player, player.position(), 40);
        if (victims.isEmpty()) {
            Abilities.fail(player, "no_target");
            return false;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(5)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double d = hit.getType() == HitResult.Type.MISS ? 5 : Math.max(1.5, eye.distanceTo(hit.getLocation()) - 1);
        Vec3 point = new Vec3(player.getX() + look.x * d, player.getY(), player.getZ() + look.z * d);

        RandomSource rng = level.getRandom();
        int moved = 0;
        for (LivingEntity e : victims) {
            if (moved >= 40) break;
            for (int attempt = 0; attempt < 6; attempt++) {
                double ox = attempt == 5 ? 0 : (rng.nextDouble() - 0.5) * 6;
                double oz = attempt == 5 ? 0 : (rng.nextDouble() - 0.5) * 6;
                Vec3 feet = point.add(ox, 0, oz);
                if (fits(level, e, feet)) {
                    teleportFx(level, e.position());
                    warp(level, e, feet.x, feet.y, feet.z);
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
                    moved++;
                    break;
                }
            }
        }
        if (moved == 0) {
            Abilities.fail(player, "blocked");
            return false;
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, point.x, point.y + 1, point.z, 80, 1.5, 1.0, 1.5, 0.3);
        return true;
    }

    /** Cloaks you in death: resistance, regeneration and a wither aura. */
    public static boolean deathShroud(ServerPlayer player, ServerLevel level) {
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 300, 2));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 300, 3));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 300, 1));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.8f, 1.4f);
        shroudTick(level, player, 30);
        return true;
    }

    private static void shroudTick(ServerLevel level, ServerPlayer player, int pulsesLeft) {
        if (pulsesLeft <= 0 || !player.isAlive() || player.isRemoved()) return;
        Vec3 p = player.position();
        level.sendParticles(ParticleTypes.SOUL, p.x, p.y + 1, p.z, 12, 0.6, 0.8, 0.6, 0.03);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y + 0.2, p.z, 8, 2.5, 0.1, 2.5, 0.01);
        for (LivingEntity e : around(level, player, p, 8)) {
            e.hurt(level.damageSources().magic(), Config.damage(2f));
            e.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 1));
            player.heal(0.5f);
        }
        Scheduler.schedule(10, () -> shroudTick(level, player, pulsesLeft - 1));
    }

    /** A huge beam of void that cuts through mobs and terrain. */
    public static boolean voidSlash(ServerPlayer player, ServerLevel level) {
        final Vec3 eye = player.getEyePosition();
        final Vec3 look = player.getLookAngle();
        final Set<UUID> hit = new HashSet<>();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1f, 1.6f);

        for (int i = 0; i < 24; i++) {
            final int step = i;
            Scheduler.schedule(i, () -> {
                Vec3 p = eye.add(look.scale(2.0 * (step + 1))).subtract(0, 0.4, 0);
                if (!level.hasChunkAt(BlockPos.containing(p))) return;
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 4, 1.2, 1.2, 1.2, 0);
                level.sendParticles(ParticleTypes.PORTAL, p.x, p.y, p.z, 30, 1.5, 1.5, 1.5, 0.5);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z, 8, 1.2, 1.2, 1.2, 0.02);
                for (LivingEntity e : around(level, player, p, 3.0)) {
                    if (!hit.add(e.getUUID())) continue;
                    e.hurt(Abilities.attack(level, player), Config.damage(30f));
                    e.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 2));
                }
                disintegrate(level, player, p, 2.5, true);
            });
        }
        return true;
    }

    /** Used by the kill-heal passive. */
    public static void siphon(Player player, float amount) {
        player.heal(amount);
    }
}
