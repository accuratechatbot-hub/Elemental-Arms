package com.example.elementalarms.network;

import com.example.elementalarms.ElementalArms;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String PROTOCOL = "2";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ElementalArms.MODID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private ModNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(CycleModePacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CycleModePacket::encode)
                .decoder(CycleModePacket::decode)
                .consumerMainThread(CycleModePacket::handle)
                .add();

        CHANNEL.messageBuilder(ToggleAltPacket.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ToggleAltPacket::encode)
                .decoder(ToggleAltPacket::decode)
                .consumerMainThread(ToggleAltPacket::handle)
                .add();
    }
}
