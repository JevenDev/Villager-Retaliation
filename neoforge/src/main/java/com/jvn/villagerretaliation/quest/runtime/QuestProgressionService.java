package com.jvn.villagerretaliation.quest.runtime;

import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.QuestScopeKey;
import com.jvn.villagerretaliation.quest.VillagerQuestFacts;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveQuery;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveRegistry;
import com.jvn.villagerretaliation.quest.tracking.QuestStageReadiness;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class QuestProgressionService {
    private QuestProgressionService() { }

    public static boolean advanceSaved(ServerLevel level, ServerPlayer player, QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress, Predicate<QuestDefinition.Objective> objectiveComplete,
            Predicate<List<DialogueCondition>> conditionsMet) {
        if (definition.campaign() != null || progress.completionPending()
                || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE
                || !progress.pendingStageEntry().isBlank() || !progress.pendingStageTransition().isBlank()
                || progress.hasPendingLifecycleEvents() || progress.hasPendingTriggerEvents()
                || progress.stageActions().values().stream().anyMatch(checkpoint -> !checkpoint.outcome().success())
                || definition.triggers().stream().anyMatch(trigger -> trigger.event() == QuestDefinition.TriggerEvent.STAGE_CHANGED)) return false;
        var stage = definition.stages().get(progress.currentStage());
        if (stage == null || !stage.exitActions().isEmpty()
                || stage.bonuses().stream().anyMatch(bonus -> !progress.bonusClaimed(stage.id(), bonus.id()))) return false;
        var readiness = QuestStageReadiness.forCurrentStage(definition, progress, objectiveComplete, conditionsMet);
        if (!readiness.ready()) return false;
        for (var predicate : stage.completeWhen()) {
            definition.objectives().stream().filter(objective -> objective.id().equals(predicate.objective()) && objectiveComplete.test(objective))
                    .forEach(objective -> progress.markObjectiveComplete(objective.id()));
        }
        var transition = QuestLifecycleService.transitionStage(definition, progress, readiness.nextStage(), level.getGameTime());
        if (!transition.changed()) return false;
        VillagerQuestFacts.get(level).setVariable(QuestScopeKey.quest(player.getUUID(), definition.id()).asString(), "stage", transition.currentStage());
        var next = definition.stages().get(transition.currentStage());
        if (!next.entryActions().isEmpty() || QuestObjectiveQuery.stageObjectives(definition, next.id()).stream()
                .anyMatch(QuestObjectiveRegistry::requiresLocatedTarget)) progress.requestStageEntry(next.id());
        VillagerQuestSavedData.get(level).setDirty();
        return true;
    }
}
