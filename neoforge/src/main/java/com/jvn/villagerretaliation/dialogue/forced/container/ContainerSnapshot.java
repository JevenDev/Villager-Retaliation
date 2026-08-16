package com.jvn.villagerretaliation.dialogue.forced.container;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public record ContainerSnapshot(
        ResourceKey<Level> dimension,
        BlockPos pos,
        Component containerName,
        ResourceLocation lootTable,
        int itemCount,
        List<ItemStack> itemStacks,
        long gameTime) {
    public ContainerSnapshot {
        pos = pos.immutable();
        itemStacks = ForcedDialogueContainers.copyStacks(itemStacks);
    }
}
