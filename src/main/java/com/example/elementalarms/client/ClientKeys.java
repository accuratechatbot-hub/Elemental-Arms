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

    public static final KeyMapping TOGGLE_ALT = new KeyMapping(
            "key.elementalarms.toggle_alt",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.elementalarms");

    private ClientKeys() {}
}
