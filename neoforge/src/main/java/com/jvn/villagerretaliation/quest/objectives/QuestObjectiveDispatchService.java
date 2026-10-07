package com.jvn.villagerretaliation.quest.objectives;

import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.debug.QuestDebugTraceService;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

public final class QuestObjectiveDispatchService {
    private QuestObjectiveDispatchService() {
    }

    public static boolean poll(QuestObjectiveEvaluationContext context, Consumer<QuestDefinition.Objective> completed) {
        VillagerQuestSavedData.QuestProgress progress = context.progress();
        boolean changed = false;
        for (QuestDefinition.Objective objective : QuestObjectiveQuery.activeObjectives(context.definition(), progress)) {
            if (progress.objectiveComplete(objective.id()) || !isComplete(context, objective)) continue;
            boolean newlyComplete = progress.markObjectiveComplete(objective.id());
            changed |= newlyComplete;
            if (newlyComplete) completed.accept(objective);
            clearCompletedTarget(progress, objective);
        }
        return changed;
    }

    public static boolean dispatch(QuestObjectiveEvaluationContext context, QuestObjectiveEvent event,
            Consumer<QuestDefinition.Objective> completed) {
        VillagerQuestSavedData.QuestProgress progress = context.progress();
        boolean changed = false;
        for (QuestDefinition.Objective objective : QuestObjectiveQuery.activeObjectives(context.definition(), progress)) {
            int previousCounter = progress.objectiveCounter(objective.id());
            boolean previouslyComplete = progress.objectiveComplete(objective.id());
            if (previouslyComplete || !QuestObjectiveRegistry.eventKinds(objective).contains(event.kind())
                    || !QuestObjectiveRegistry.matchesEvent(context, objective, event)) continue;
            if (QuestObjectiveRegistry.requirements(objective).contains(QuestObjectiveRequirement.COUNTER)) {
                int count = progress.addObjectiveCounter(objective.id(), 1);
                changed = true;
                if (count >= objective.count() && progress.markObjectiveComplete(objective.id())) completed.accept(objective);
            } else if (isComplete(context, objective) && progress.markObjectiveComplete(objective.id())) {
                changed = true;
                completed.accept(objective);
            }
            clearCompletedTarget(progress, objective);
            if (QuestDebugTraceService.isEnabled(context.player())) {
                QuestDebugTraceService.record(context.player(), QuestDebugTraceService.EventType.OBJECTIVE_PROGRESS, context.definition().id(),
                        "objective=" + objective.id()
                                + " event=" + event.kind().name().toLowerCase(Locale.ROOT)
                                + " counter_delta=" + (progress.objectiveCounter(objective.id()) - previousCounter)
                                + " complete_delta=" + (!previouslyComplete && progress.objectiveComplete(objective.id())));
            }
        }
        return changed;
    }

    public static Set<ResourceLocation> candidateQuestIds(ServerLevel level, QuestObjectiveEvent event) {
        if (level == null || event == null || event.kind() == null) return Set.of();
        if (event.kind() == QuestObjectiveEventKind.MEMORY_EVENT) {
            return event.memoryEvent() == null || event.memoryEvent().tagId() == null
                    ? Set.of() : VillagerQuestResources.memoryEventQuestIds(level.getServer(), event.memoryEvent().tagId());
        }
        return VillagerQuestResources.questIdsForObjectiveEvent(level.getServer(), event.kind());
    }

    public static QuestDefinition.TriggerEvent triggerEventFor(QuestObjectiveEventKind kind) {
        return switch (kind) {
            case MOB_KILL -> QuestDefinition.TriggerEvent.MOB_KILL;
            case BLOCK_BREAK -> QuestDefinition.TriggerEvent.BLOCK_BREAK;
            case BLOCK_PLACE -> QuestDefinition.TriggerEvent.BLOCK_PLACE;
            case BLOCK_INTERACT -> QuestDefinition.TriggerEvent.BLOCK_INTERACT;
            case MEMORY_EVENT -> QuestDefinition.TriggerEvent.MEMORY_EVENT;
            case GIFT -> QuestDefinition.TriggerEvent.GIFT;
            case TRADE -> QuestDefinition.TriggerEvent.TRADE;
            case REPUTATION -> QuestDefinition.TriggerEvent.REPUTATION;
            case CRITERION -> QuestDefinition.TriggerEvent.CRITERION;
        };
    }

    private static boolean isComplete(QuestObjectiveEvaluationContext context, QuestDefinition.Objective objective) {
        return QuestObjectiveRegistry.evaluate(context, objective).map(QuestObjectiveResult::complete).orElse(false);
    }

    private static void clearCompletedTarget(VillagerQuestSavedData.QuestProgress progress, QuestDefinition.Objective objective) {
        if (objective.id().equals(progress.targetObjectiveId()) && progress.objectiveComplete(objective.id())) {
            progress.setTarget(progress.startedVillagerId(), progress.targetDimension(), null, "");
        }
    }
}
