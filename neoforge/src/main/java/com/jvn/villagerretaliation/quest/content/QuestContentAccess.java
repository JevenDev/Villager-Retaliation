package com.jvn.villagerretaliation.quest.content;

import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.QuestTriggerIndex;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuest;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveEventKind;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public interface QuestContentAccess {
    Optional<QuestDefinition> quest(ResourceLocation id);
    Optional<CompiledQuest> compiledQuest(ResourceLocation id);
    Optional<QuestTriggerIndex> questTriggerIndex(ResourceLocation id);
    Set<ResourceLocation> questIdsForObjectiveEvent(QuestObjectiveEventKind kind);
    Set<ResourceLocation> memoryEventQuestIds(ResourceLocation tag);
}
