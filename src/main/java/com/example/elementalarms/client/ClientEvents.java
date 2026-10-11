package com.example.elementalarms.client;

import com.example.elementalarms.ElementalArms;
import com.example.elementalarms.ability.AbilityManager;
import com.example.elementalarms.ability.WeaponType;
import com.example.elementalarms.network.CycleModePacket;
import com.example.elementalarms.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = ElementalArms.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        while (ClientKeys.CYCLE.consumeClick()) {
            if (mc.player == null) continue;
            if (WeaponType.fromStack(mc.player.getMainHandItem()) == null) continue;
            ModNetwork.CHANNEL.sendToServer(new CycleModePacket(mc.player.isShiftKeyDown()));
        }
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        WeaponType weapon = WeaponType.fromStack(stack);
        if (weapon == null) return;

        int current = AbilityManager.getMode(stack, weapon);
        List<Component> tip = event.getToolTip();

        tip.add(Component.empty());
        tip.add(Component.translatable("tooltip.elementalarms.power", Component.translatable(weapon.powerKey()))
                .withStyle(weapon.color(), ChatFormatting.BOLD));

        List<WeaponType.Mode> modes = weapon.modes();
        for (int i = 0; i < modes.size(); i++) {
            boolean selected = i == current;
            MutableComponent line = Component.literal(selected ? "> " : "  ").append(modes.get(i).name(weapon));
            tip.add(line.withStyle(selected ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY));
        }

        tip.add(Component.translatable(weapon.passiveKey()).withStyle(ChatFormatting.GREEN));
        tip.add(Component.translatable("tooltip.elementalarms.use").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.elementalarms.cycle", ClientKeys.CYCLE.getTranslatedKeyMessage())
                .withStyle(ChatFormatting.GRAY));
    }
}
