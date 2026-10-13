package com.example.elementalarms.ability;

import com.example.elementalarms.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/** Mode / page selection, cooldowns and the "use ability" entry point. */
public final class AbilityManager {
    /** Selected primary mode, stored on the item itself so each weapon remembers its own mode. */
    public static final String MODE_KEY = "ElementalArmsMode";
    /** Selected mode on the Alt page. */
    public static final String ALT_MODE_KEY = "ElementalArmsAltMode";
    /** Whether the weapon is currently on its Alt page. */
    public static final String ALT_KEY = "ElementalArmsAlt";
    private static final String NOFALL_KEY = "ElementalArmsNoFallUntil";

    private AbilityManager() {}

    /* ---------------- page + mode (stored on the item) ---------------- */

    public static boolean isAlt(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(ALT_KEY);
    }

    /** The modes of whichever page (primary or Alt) the item is currently on. */
    public static List<WeaponType.Mode> activeModes(ItemStack stack, WeaponType weapon) {
        return isAlt(stack) ? weapon.altModes() : weapon.modes();
    }

    public static int getMode(ItemStack stack, WeaponType weapon) {
        CompoundTag tag = stack.getTag();
        int raw = tag == null ? 0 : tag.getInt(isAlt(stack) ? ALT_MODE_KEY : MODE_KEY);
        return Math.floorMod(raw, activeModes(stack, weapon).size());
    }

    public static void setMode(ItemStack stack, int mode) {
        stack.getOrCreateTag().putInt(isAlt(stack) ? ALT_MODE_KEY : MODE_KEY, mode);
    }

    public static void cycleMode(ServerPlayer player, boolean reverse) {
        ItemStack stack = player.getMainHandItem();
        WeaponType weapon = WeaponType.fromStack(stack);
        if (weapon == null) return;

        List<WeaponType.Mode> modes = activeModes(stack, weapon);
        int next = Math.floorMod(getMode(stack, weapon) + (reverse ? -1 : 1), modes.size());
        setMode(stack, next);

        player.displayClientMessage(Component.translatable("message.elementalarms.mode_changed",
                modes.get(next).name(weapon)).withStyle(isAlt(stack) ? ChatFormatting.LIGHT_PURPLE : weapon.color()), true);
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.2f + next * 0.1f);
    }

    /** Flips the weapon in the main hand between its primary page and its Alt page. */
    public static void toggleAlt(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        WeaponType weapon = WeaponType.fromStack(stack);
        if (weapon == null) return;

        boolean alt = !isAlt(stack);
        stack.getOrCreateTag().putBoolean(ALT_KEY, alt);

        WeaponType.Mode current = activeModes(stack, weapon).get(getMode(stack, weapon));
        player.displayClientMessage(Component.translatable(alt ? "message.elementalarms.alt_on" : "message.elementalarms.alt_off",
                current.name(weapon)).withStyle(alt ? ChatFormatting.LIGHT_PURPLE : weapon.color()), true);
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7f, alt ? 0.6f : 1.6f);
    }

    /* ---------------- cooldowns (stored on the player, survive relog + death) ---------------- */

    private static CompoundTag persisted(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    /* ---------------- no fall damage window (dash / leap / flight ending) ---------------- */

    public static void grantNoFall(Player player, int ticks) {
        player.getPersistentData().putLong(NOFALL_KEY, player.level().getGameTime() + ticks);
    }

    public static boolean hasNoFall(Player player) {
        return player.level().getGameTime() <= player.getPersistentData().getLong(NOFALL_KEY);
    }

    /* ---------------- use ---------------- */

    public static void tryUse(ServerPlayer player, ItemStack stack, WeaponType weapon) {
        int index = getMode(stack, weapon);
        WeaponType.Mode mode = activeModes(stack, weapon).get(index);
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();

        CompoundTag data = persisted(player);
        String key = "cd_" + weapon.id() + "_" + mode.id();
        long ready = data.getLong(key);

        if (now < ready) {
            String seconds = String.format(Locale.ROOT, "%.1f", (ready - now) / 20.0);
            player.displayClientMessage(Component.translatable("message.elementalarms.cooldown",
                    mode.name(weapon), seconds).withStyle(ChatFormatting.RED), true);
            return;
        }

        if (mode.ability().use(player, level)) {
            int cooldown = (int) Math.round(mode.cooldown() * Config.COOLDOWN_MULTIPLIER.get());
            data.putLong(key, now + cooldown);

            int cost = Config.DURABILITY_COST.get();
            if (cost > 0) {
                stack.hurtAndBreak(cost, player, p -> p.broadcastBreakEvent(InteractionHand.MAIN_HAND));
            }
        }
    }
}
