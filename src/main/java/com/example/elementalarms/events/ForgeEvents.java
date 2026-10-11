package com.example.elementalarms.events;

import com.example.elementalarms.ElementalArms;
import com.example.elementalarms.ability.AbilityManager;
import com.example.elementalarms.ability.Scheduler;
import com.example.elementalarms.ability.WeaponType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityStruckByLightningEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ElementalArms.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ForgeEvents {
    private ForgeEvents() {}

    /* ---------------- ability trigger: Sneak + Right-click ---------------- */

    private static boolean handleUse(Player player, InteractionHand hand, ItemStack stack) {
        if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()) return false;
        WeaponType weapon = WeaponType.fromStack(stack);
        if (weapon == null) return false;
        if (player instanceof ServerPlayer serverPlayer) {
            AbilityManager.tryUse(serverPlayer, stack, weapon);
        }
        return true;
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (handleUse(event.getEntity(), event.getHand(), event.getItemStack())) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (handleUse(event.getEntity(), event.getHand(), event.getItemStack())) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    /* ---------------- passive immunities ---------------- */

    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        DamageSource source = event.getSource();

        if (source.is(DamageTypeTags.IS_FIRE) && WeaponType.holds(player, WeaponType.GOLDEN_AXE)) {
            event.setCanceled(true);
        } else if (source.is(DamageTypeTags.IS_EXPLOSION) && WeaponType.holds(player, WeaponType.IRON_SWORD)) {
            event.setCanceled(true);
        } else if (source.is(DamageTypes.LIGHTNING_BOLT) && WeaponType.holds(player, WeaponType.TRIDENT)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onStruckByLightning(EntityStruckByLightningEvent event) {
        if (event.getEntity() instanceof Player player && WeaponType.holds(player, WeaponType.TRIDENT)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        Player player = event.player;
        if (player.isOnFire() && WeaponType.holds(player, WeaponType.GOLDEN_AXE)) {
            player.clearFire();
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player && AbilityManager.hasNoFall(player)) {
            event.setCanceled(true);
        }
    }

    /* ---------------- scheduler ---------------- */

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Scheduler.tick();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        Scheduler.clear();
    }
}
