package com.example.elementalarms.network;

import com.example.elementalarms.ability.AbilityManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: "flip the weapon in my main hand between its primary and Alt page". */
public record ToggleAltPacket() {

    public static void encode(ToggleAltPacket packet, FriendlyByteBuf buf) {
        // no data
    }

    public static ToggleAltPacket decode(FriendlyByteBuf buf) {
        return new ToggleAltPacket();
    }

    public static void handle(ToggleAltPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sender = ctx.get().getSender();
        if (sender != null) {
            AbilityManager.toggleAlt(sender);
        }
    }
}
