package com.jvn.villagerretaliation.dialogue.forced.container;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public record RecentContainerClick(
        ResourceKey<Level> dimension,
        BlockPos pos,
        long gameTime,
        Component containerName,
        ResourceLocation lootTable) {
    public RecentContainerClick {
        pos = pos.immutable();
    }
}
