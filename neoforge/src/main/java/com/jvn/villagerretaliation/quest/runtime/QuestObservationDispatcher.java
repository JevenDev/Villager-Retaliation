package com.jvn.villagerretaliation.quest.runtime;

import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.content.QuestContentAccess;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveDispatcher;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveEvent;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveEventKind;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

public final class QuestObservationDispatcher {
    private QuestObservationDispatcher() { }

    public static <P extends QuestProgressState> Result dispatch(QuestContentAccess content,
            QuestProgressStore<P> store, UUID playerId, QuestObjectiveEvent observation,
            Set<ResourceLocation> excludedQuestIds, Access<P> access) {
        Set<ResourceLocation> candidates = candidates(content, observation);
        var event = QuestObjectiveDispatcher.triggerEventFor(observation.kind());
        boolean changed = false;
        boolean progressNotice = false;
        for (var entry : store.activeProgress(playerId)) {
            if (excludedQuestIds.contains(entry.getKey())) continue;
            var definition = content.quest(entry.getKey()).orElse(null);
            if (definition == null) continue;
            boolean objectiveCandidate = candidates.contains(entry.getKey());
            boolean triggerCandidate = content.questTriggerIndex(entry.getKey())
                    .map(index -> index.hasEvent(event)).orElse(false);
            if (!objectiveCandidate && !triggerCandidate) continue;
            P progress = entry.getValue();
            if (!access.active(definition, progress)) continue;
            boolean questChanged = objectiveCandidate && access.objectives(definition, progress, observation);
            if (triggerCandidate) changed |= access.triggers(definition, progress, event, observation);
            if (questChanged) {
                access.advance(definition, progress);
                changed = true;
                progressNotice = true;
                access.updated(definition, progress);
                changed |= access.triggers(definition, progress, QuestDefinition.TriggerEvent.PROGRESS, null);
            }
        }
        return new Result(changed, progressNotice);
    }

    public static Set<ResourceLocation> candidates(QuestContentAccess content, QuestObjectiveEvent event) {
        if (event.kind() == QuestObjectiveEventKind.MEMORY_EVENT) {
            return event.memoryEvent() == null || event.memoryEvent().tagId() == null
                    ? Set.of() : content.memoryEventQuestIds(event.memoryEvent().tagId());
        }
        return content.questIdsForObjectiveEvent(event.kind());
    }

    public interface Access<P extends QuestProgressState> {
        boolean active(QuestDefinition definition, P progress);
        boolean objectives(QuestDefinition definition, P progress, QuestObjectiveEvent observation);
        boolean triggers(QuestDefinition definition, P progress, QuestDefinition.TriggerEvent event,
                QuestObjectiveEvent observation);
        void advance(QuestDefinition definition, P progress);
        void updated(QuestDefinition definition, P progress);
    }

    public record Result(boolean changed, boolean progressNotice) { }
}
