package com.jvn.villagerretaliation.quest.objectives;

import com.jvn.villagerretaliation.campaign.CampaignQuestAccess;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
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
        return poll(context, executionAllowed(context), completed);
    }

    public static boolean poll(QuestObjectiveEvaluationContext context, boolean executionAllowed,
            Consumer<QuestDefinition.Objective> completed) {
        if (!executionAllowed) return false;
        boolean changed = QuestObjectiveDispatcher.poll(context.model(),
                QuestObjectiveQuery.activeObjectives(context.definition(), context.model()), executionAllowed,
                objective -> isComplete(context, objective), completed);
        if (changed) updatePerformance(context);
        return changed;
    }

    public static boolean dispatch(QuestObjectiveEvaluationContext context, QuestObjectiveEvent event,
            Consumer<QuestDefinition.Objective> completed) {
        return dispatch(context, event, executionAllowed(context), completed);
    }

    public static boolean dispatch(QuestObjectiveEvaluationContext context, QuestObjectiveEvent event,
            boolean executionAllowed, Consumer<QuestDefinition.Objective> completed) {
        if (!executionAllowed) return false;
        Consumer<QuestObjectiveDispatcher.ObjectiveUpdate> observer = QuestDebugTraceService.isEnabled(context.player())
                ? update -> QuestDebugTraceService.record(context.player(), QuestDebugTraceService.EventType.OBJECTIVE_PROGRESS,
                        context.definition().id(), "objective=" + update.objectiveId()
                                + " event=" + update.eventKind().name().toLowerCase(Locale.ROOT)
                                + " counter_delta=" + update.counterDelta()
                                + " complete_delta=" + update.completed()) : null;
        boolean changed = QuestObjectiveDispatcher.dispatch(context.model(),
                QuestObjectiveQuery.activeObjectives(context.definition(), context.model()), true, event.kind(),
                objective -> QuestObjectiveRegistry.matchesEvent(context, objective, event),
                objective -> isComplete(context, objective), completed, observer);
        if (changed) updatePerformance(context);
        return changed;
    }

    private static void updatePerformance(QuestObjectiveEvaluationContext context) {
        if (context.player() != null && context.level() != null
                && context.model() instanceof com.jvn.villagerretaliation.quest.VillagerQuestSavedData.QuestProgress progress)
            com.jvn.villagerretaliation.quest.runtime.QuestPerformanceService.update(
                    context.level(), context.player().getUUID(), context.definition(), progress);
    }

    private static boolean executionAllowed(QuestObjectiveEvaluationContext context) {
        if (context.definition().campaign() == null) return true;
        return context.model() instanceof com.jvn.villagerretaliation.quest.VillagerQuestSavedData.QuestProgress progress
                && CampaignQuestAccess.executionIssue(context.level(), context.player() == null ? null : context.player().getUUID(),
                        context.definition(), progress).isBlank();
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
        return QuestObjectiveDispatcher.triggerEventFor(kind);
    }

    private static boolean isComplete(QuestObjectiveEvaluationContext context, QuestDefinition.Objective objective) {
        return QuestObjectiveRegistry.evaluate(context, objective).map(QuestObjectiveResult::complete).orElse(false);
    }
}
