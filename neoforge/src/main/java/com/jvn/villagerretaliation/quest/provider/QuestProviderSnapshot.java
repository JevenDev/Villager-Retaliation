package com.jvn.villagerretaliation.quest.provider;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public record QuestProviderSnapshot(ResourceLocation type, UUID id, String name, ResourceKey<Level> dimension,
        BlockPos position, String groupKey, boolean live) {
    public QuestProviderSnapshot {
        name = name == null ? "" : name;
        position = position == null ? null : position.immutable();
        groupKey = groupKey == null ? "" : groupKey;
    }
}
