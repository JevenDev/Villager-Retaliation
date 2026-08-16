package com.jvn.villagerretaliation.quest.compiled;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

public record CompiledQuestCatalog(Map<ResourceLocation, CompiledQuest> questsById) {
    public CompiledQuestCatalog {
        questsById = questsById == null || questsById.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(questsById));
    }

    public Collection<CompiledQuest> quests() {
        return this.questsById.values();
    }

    public Optional<CompiledQuest> quest(ResourceLocation id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.questsById.get(id));
    }
}
