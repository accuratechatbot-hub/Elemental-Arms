package com.example.elementalarms.events;

import com.example.elementalarms.ElementalArms;
import com.example.elementalarms.ability.AbilityManager;
import com.example.elementalarms.ability.Scheduler;
import com.example.elementalarms.ability.TimeStop;
import com.example.elementalarms.ability.TimeTracker;
import com.example.elementalarms.ability.WeaponType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityStruckByLightningEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
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

    private static boolean fireproof(Player player) {
        return WeaponType.holds(player, WeaponType.GOLDEN_AXE) || WeaponType.holds(player, WeaponType.STONE_SWORD);
    }

    private static boolean blastproof(Player player) {
        return WeaponType.holds(player, WeaponType.IRON_SWORD) || WeaponType.holds(player, WeaponType.STONE_SWORD);
    }

    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        DamageSource source = event.getSource();

        if (source.is(DamageTypeTags.IS_FIRE) && fireproof(player)) {
            event.setCanceled(true);
        } else if (source.is(DamageTypeTags.IS_EXPLOSION) && blastproof(player)) {
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

        if (player.isOnFire() && fireproof(player)) {
            player.clearFire();
        }

        if (player instanceof ServerPlayer serverPlayer) {
            TimeTracker.record(serverPlayer);

            // Stone Sword aura: monsters close to you catch fire.
            if (serverPlayer.tickCount % 20 == 0 && WeaponType.holds(serverPlayer, WeaponType.STONE_SWORD)) {
                for (LivingEntity e : serverPlayer.serverLevel().getEntitiesOfClass(LivingEntity.class,
                        serverPlayer.getBoundingBox().inflate(4), x -> x instanceof Enemy && x.isAlive())) {
                    e.setSecondsOnFire(4);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        TimeTracker.clear(event.getEntity().getUUID());
    }

    /** A mob that was frozen when the world saved / its chunk unloaded must not stay frozen forever. */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (TimeStop.hasFreezeTag(event.getEntity()) && !TimeStop.isFrozen(event.getEntity())) {
            TimeStop.cleanup(event.getEntity());
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
            TimeStop.tick();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        TimeStop.releaseAll(); // un-freeze any mobs caught in a Time Stop
        Scheduler.clear();
        TimeTracker.clearAll();
    }
}
