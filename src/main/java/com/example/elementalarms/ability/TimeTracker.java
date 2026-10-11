package com.example.elementalarms.ability;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Remembers where a player was over the last ~5 seconds so Time Rewind can send them back.
 * Only records while the player holds the stone sword.
 */
public final class TimeTracker {
    public record Snapshot(ResourceKey<Level> dim, double x, double y, double z,
                           float yRot, float xRot, float health, int food) {}

    private static final int MAX_SNAPSHOTS = 20; // one every 5 ticks = 5 seconds
    private static final Map<UUID, ArrayDeque<Snapshot>> HISTORY = new HashMap<>();

    private TimeTracker() {}

    public static void record(ServerPlayer player) {
        if (player.tickCount % 5 != 0) return;
        if (!player.isAlive() || !WeaponType.holds(player, WeaponType.STONE_SWORD)) {
            HISTORY.remove(player.getUUID());
            return;
        }
        ArrayDeque<Snapshot> queue = HISTORY.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        queue.addLast(new Snapshot(player.level().dimension(), player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(), player.getHealth(), player.getFoodData().getFoodLevel()));
        while (queue.size() > MAX_SNAPSHOTS) {
            queue.removeFirst();
        }
    }

    /** The oldest snapshot taken in the player's current dimension, or null. */
    @Nullable
    public static Snapshot oldest(ServerPlayer player) {
        ArrayDeque<Snapshot> queue = HISTORY.get(player.getUUID());
        if (queue == null) return null;
        for (Snapshot s : queue) {
            if (s.dim().equals(player.level().dimension())) return s;
        }
        return null;
    }

    public static void clear(UUID id) {
        HISTORY.remove(id);
    }

    public static void clearAll() {
        HISTORY.clear();
    }
}
