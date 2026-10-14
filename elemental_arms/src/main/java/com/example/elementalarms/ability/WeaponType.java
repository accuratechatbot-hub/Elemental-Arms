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
    TRIDENT("trident", ChatFormatting.AQUA, () -> Items.TRIDENT,
            List.of(
                    new Mode("smite", 30, Abilities::smite),
                    new Mode("chain", 60, Abilities::chainLightning),
                    new Mode("storm", 400, Abilities::thunderstorm),
                    new Mode("dash", 60, Abilities::lightningDash),
                    new Mode("weather", 1200, Abilities::weatherControl)),
            List.of(
                    new Mode("aura", 300, AltAbilities::stormAura),
                    new Mode("clap", 160, AltAbilities::thunderClap),
                    new Mode("overcharge", 700, AltAbilities::overcharge))),

    GOLDEN_AXE("golden_axe", ChatFormatting.GOLD, () -> Items.GOLDEN_AXE,
            List.of(
                    new Mode("fireball", 25, Abilities::fireball),
                    new Mode("cone", 50, Abilities::flameCone),
                    new Mode("pillar", 200, Abilities::firePillar),
                    new Mode("absorb", 80, Abilities::flameAbsorb)),
            List.of(
                    new Mode("wave", 160, AltAbilities::magmaWave),
                    new Mode("cloak", 500, AltAbilities::flameCloak),
                    new Mode("tornado", 400, AltAbilities::fireTornado))),

    IRON_SWORD("iron_sword", ChatFormatting.RED, () -> Items.IRON_SWORD,
            List.of(
                    new Mode("blast", 30, Abilities::blast),
                    new Mode("leap", 80, Abilities::rocketLeap),
                    new Mode("shockwave", 120, Abilities::shockwave),
                    new Mode("mark", 100, Abilities::detonationMark)),
            List.of(
                    new Mode("cluster", 200, AltAbilities::clusterBomb),
                    new Mode("slam", 240, AltAbilities::groundSlam),
                    new Mode("chain", 300, AltAbilities::chainReaction))),

    STONE_SWORD("stone_sword", ChatFormatting.DARK_PURPLE, () -> Items.STONE_SWORD,
            List.of(
                    new Mode("grab", 100, StoneAbilities::telekineticGrab),
                    new Mode("push", 60, StoneAbilities::forcePush),
                    new Mode("godblast", 600, StoneAbilities::godBlast),
                    new Mode("nova", 160, StoneAbilities::infernoNova),
                    new Mode("timestop", 900, StoneAbilities::timeStop),
                    new Mode("timeshift", 1200, StoneAbilities::timeShift),
                    new Mode("rewind", 400, StoneAbilities::rewind),
                    new Mode("blink", 60, StoneAbilities::realityBlink),
                    new Mode("singularity", 500, StoneAbilities::singularity),
                    new Mode("meteor", 800, StoneAbilities::meteorShower)),
            List.of(
                    new Mode("crush", 400, AltAbilities::gravityCrush),
                    new Mode("swap", 120, AltAbilities::realitySwap),
                    new Mode("dilation", 700, AltAbilities::timeDilation))),

    NETHERITE_SWORD("netherite_sword", ChatFormatting.BLUE, () -> Items.NETHERITE_SWORD,
            List.of(
                    new Mode("step", 40, VoidAbilities::riftStep),
                    new Mode("phase", 100, VoidAbilities::phaseStrike),
                    new Mode("breath", 200, VoidAbilities::dragonBreath),
                    new Mode("scatter", 300, VoidAbilities::realityScatter),
                    new Mode("raise", 600, VoidAbilities::raiseDead),
                    new Mode("drain", 160, VoidAbilities::soulDrain),
                    new Mode("worldender", 4800, VoidAbilities::worldEnder)),
            List.of(
                    new Mode("pull", 300, VoidAbilities::riftSummon),
                    new Mode("shroud", 700, VoidAbilities::deathShroud),
                    new Mode("slash", 200, VoidAbilities::voidSlash)));

    public record Mode(String id, int cooldown, Ability ability) {
        public MutableComponent name(WeaponType weapon) {
            return Component.translatable("mode." + ElementalArms.MODID + "." + weapon.id() + "." + id);
        }
    }

    private final String id;
    private final ChatFormatting color;
    private final Supplier<Item> item;
    private final List<Mode> modes;
    private final List<Mode> altModes;

    WeaponType(String id, ChatFormatting color, Supplier<Item> item, List<Mode> modes, List<Mode> altModes) {
        this.id = id;
        this.color = color;
        this.item = item;
        this.modes = modes;
        this.altModes = altModes;
    }

    public String id() {
        return id;
    }

    public ChatFormatting color() {
        return color;
    }

    /** The primary page of abilities. */
    public List<Mode> modes() {
        return modes;
    }

    /** The Alt Mode page of abilities. */
    public List<Mode> altModes() {
        return altModes;
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

    /** True when the player holds any Elemental Arms weapon in either hand (used for flight). */
    public static boolean holdsAny(Player player) {
        for (WeaponType type : values()) {
            if (holds(player, type)) return true;
        }
        return false;
    }
}
