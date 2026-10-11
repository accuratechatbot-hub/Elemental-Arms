package com.example.elementalarms.ability;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

@FunctionalInterface
public interface Ability {
    /**
     * @return true if the ability was actually used (cooldown + durability are only spent then)
     */
    boolean use(ServerPlayer player, ServerLevel level);
}
