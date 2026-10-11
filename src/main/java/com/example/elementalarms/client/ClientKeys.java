package com.example.elementalarms.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class ClientKeys {
    public static final KeyMapping CYCLE = new KeyMapping(
            "key.elementalarms.cycle_mode",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.elementalarms");

    private ClientKeys() {}
}
