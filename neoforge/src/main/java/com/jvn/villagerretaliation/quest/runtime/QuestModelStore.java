package com.jvn.villagerretaliation.quest.runtime;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

public interface QuestModelStore<P extends QuestProgressModel> {
    P get(UUID playerId, ResourceLocation questId);
    P getOrCreate(UUID playerId, ResourceLocation questId);
    List<Map.Entry<ResourceLocation, P>> activeProgress(UUID playerId);
    void changed();
}
