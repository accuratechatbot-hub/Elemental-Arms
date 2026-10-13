package com.example.elementalarms;

import net.minecraftforge.common.ForgeConfigSpec;

public final class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue BLOCK_DAMAGE = BUILDER
            .comment("If false, ability explosions never destroy blocks and fire abilities never place fire.",
                    "Fireballs still follow the mobGriefing gamerule.")
            .define("blockDamage", true);

    public static final ForgeConfigSpec.DoubleValue DAMAGE_MULTIPLIER = BUILDER
            .comment("Multiplier applied to direct ability damage (chain lightning, flames, dash...).")
            .defineInRange("damageMultiplier", 1.0, 0.0, 10.0);

    public static final ForgeConfigSpec.DoubleValue COOLDOWN_MULTIPLIER = BUILDER
            .comment("Multiplier applied to every ability cooldown. 0 = no cooldowns.")
            .defineInRange("cooldownMultiplier", 1.0, 0.0, 10.0);

    public static final ForgeConfigSpec.IntValue DURABILITY_COST = BUILDER
            .comment("Durability the weapon loses each time an ability is used. 0 = free.")
            .defineInRange("durabilityCost", 0, 0, 100);

    public static final ForgeConfigSpec.BooleanValue FLIGHT = BUILDER
            .comment("If true, survival players can fly while holding any Elemental Arms weapon.")
            .define("weaponFlight", true);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private Config() {}

    public static float damage(float base) {
        return (float) (base * DAMAGE_MULTIPLIER.get());
    }
}
