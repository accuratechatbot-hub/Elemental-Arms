package com.example.elementalarms.ability;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Small particle helpers. */
public final class Fx {
    private Fx() {}

    /** Draws a line of particles between two points. */
    public static void beam(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions particle) {
        double dist = from.distanceTo(to);
        int steps = Math.max(1, (int) (dist * 3));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.lerp(to, i / (double) steps);
            level.sendParticles(particle, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
    }

    /** A direction close to {@code dir}, randomly spread by {@code amount}. */
    public static Vec3 jitter(Vec3 dir, RandomSource rng, double amount) {
        return dir.add(
                (rng.nextDouble() - 0.5) * 2 * amount,
                (rng.nextDouble() - 0.5) * 2 * amount,
                (rng.nextDouble() - 0.5) * 2 * amount).normalize();
    }
}
