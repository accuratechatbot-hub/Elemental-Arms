package com.example.elementalarms.ability;

import com.example.elementalarms.ElementalArms;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Supplier;

public enum WeaponType {
    TRIDENT("trident", ChatFormatting.AQUA, () -> Items.TRIDENT, List.of(
            new Mode("smite", 30, Abilities::smite),
            new Mode("chain", 60, Abilities::chainLightning),
            new Mode("storm", 400, Abilities::thunderstorm),
            new Mode("dash", 60, Abilities::lightningDash),
            new Mode("weather", 1200, Abilities::weatherControl))),

    GOLDEN_AXE("golden_axe", ChatFormatting.GOLD, () -> Items.GOLDEN_AXE, List.of(
            new Mode("fireball", 25, Abilities::fireball),
            new Mode("cone", 50, Abilities::flameCone),
            new Mode("pillar", 200, Abilities::firePillar),
            new Mode("absorb", 80, Abilities::flameAbsorb))),

    IRON_SWORD("iron_sword", ChatFormatting.RED, () -> Items.IRON_SWORD, List.of(
            new Mode("blast", 30, Abilities::blast),
            new Mode("leap", 80, Abilities::rocketLeap),
            new Mode("shockwave", 120, Abilities::shockwave),
            new Mode("mark", 100, Abilities::detonationMark)));

    public record Mode(String id, int cooldown, Ability ability) {
        public MutableComponent name(WeaponType weapon) {
            return Component.translatable("mode." + ElementalArms.MODID + "." + weapon.id() + "." + id);
        }
    }

    private final String id;
    private final ChatFormatting color;
    private final Supplier<Item> item;
    private final List<Mode> modes;

    WeaponType(String id, ChatFormatting color, Supplier<Item> item, List<Mode> modes) {
        this.id = id;
        this.color = color;
        this.item = item;
        this.modes = modes;
    }

    public String id() {
        return id;
    }

    public ChatFormatting color() {
        return color;
    }

    public List<Mode> modes() {
        return modes;
    }

    public String powerKey() {
        return "power." + ElementalArms.MODID + "." + id;
    }

    public String passiveKey() {
        return "tooltip." + ElementalArms.MODID + ".passive." + id;
    }

    @Nullable
    public static WeaponType fromStack(ItemStack stack) {
        if (stack.isEmpty()) return null;
        for (WeaponType type : values()) {
            if (stack.is(type.item.get())) return type;
        }
        return null;
    }

    /** True when the player holds this weapon in either hand (used for passive immunities). */
    public static boolean holds(Player player, WeaponType type) {
        Item i = type.item.get();
        return player.getMainHandItem().is(i) || player.getOffhandItem().is(i);
    }
}
