package com.example.elementalarms.network;

import com.example.elementalarms.ability.AbilityManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: "change the mode of the weapon in my main hand". */
public record CycleModePacket(boolean reverse) {

    public static void encode(CycleModePacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.reverse);
    }

    public static CycleModePacket decode(FriendlyByteBuf buf) {
        return new CycleModePacket(buf.readBoolean());
    }

    public static void handle(CycleModePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sender = ctx.get().getSender();
        if (sender != null) {
            AbilityManager.cycleMode(sender, packet.reverse);
        }
    }
}
