package com.jvn.villagerretaliation.quest.runtime;

import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.campaign.CampaignQuestAccess;
import com.jvn.villagerretaliation.campaign.CampaignQuestEntries;
import com.jvn.villagerretaliation.campaign.CampaignQuestReconciliation;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.VillagerQuestFacts;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.conditions.QuestAvailabilityService;
import com.jvn.villagerretaliation.quest.tracking.QuestStageReadiness;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class QuestStageTransitionCoordinator {
    private static final int MAX_STAGE_ADVANCES_PER_CHECK = 8;
    private final Access access;

    public QuestStageTransitionCoordinator(Access access) { this.access = access; }

    public void initializeQuestStage(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        initializeQuestStage(context, definition, progress, QuestLifecycleService.initialStage(definition));
    }

    public void initializeQuestStage(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            String entryStage) {
        QuestLifecycleService.StageTransition transition =
                QuestLifecycleService.initializeStage(definition, progress, context.level().getGameTime(), entryStage);
        String initialStage = transition.currentStage();
        if (initialStage.isBlank()) {
            return;
        }
        syncQuestStageFact(context, definition, progress.currentStage());
        QuestDefinition.Stage stage = definition.stages().get(progress.currentStage());
        if (stage != null) {
            access.runStageActions(context, definition, progress, "entry", stage.entryActions());
        }
        advanceStageIfComplete(context, definition, progress);
    }

    public boolean changeParallelStage(DialogueContext context, QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress, String from, String target) {
        if (!CampaignQuestAccess.executionIssue(context, definition).isBlank()
                || !QuestStageActionService.ready(context, progress)
                || !QuestParallelService.request(definition, progress, from, target)) return false;
        VillagerQuestSavedData.get(context.level()).setDirty();
        advanceParallel(context, definition, progress);
        access.ensureStageTarget(context, definition, progress);
        access.sendTrackerSync(context.player(), true);
        return true;
    }

    private boolean advanceParallel(DialogueContext context, QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        boolean changed = QuestParallelService.advance(definition, progress, context.level().getGameTime(),
                objective -> access.objectiveComplete(context.player(), context, context.level(), definition, progress, objective),
                conditions -> DialogueCondition.matchesAll(context, conditions),
                (key, actions) -> {
                    VillagerQuestSavedData.get(context.level()).setDirty();
                    return access.runStageActions(context, definition, progress, key, actions).success();
                });
        if (changed) {
            VillagerQuestSavedData.get(context.level()).setDirty();
            access.ensureStageTarget(context, definition, progress);
        }
        return changed;
    }

    public boolean changeQuestStage(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            String stage,
            boolean runExitActions,
            boolean runEntryActions) {
        if (context == null || definition == null || progress == null
                || !CampaignQuestAccess.executionIssue(context, definition).isBlank()) return false;
        resumePendingStageEntry(context, definition, progress);
        if (!QuestLifecycleService.canTransitionStage(progress, stage)
                || !definition.stages().containsKey(stage.trim())) {
            return false;
        }

        if (!QuestStageActionService.ready(context, progress)) return false;
        if (!progress.pendingStageTransition().isBlank() && !progress.pendingStageTransition().equals(stage.trim())) return false;
        CampaignQuestEntries.Entry campaignEntry = CampaignQuestEntries.stage(context, definition, stage.trim());
        if (CampaignQuestReconciliation.reconcile(context.player(), definition, progress)) {
            resumePendingStageEntry(context, definition, progress);
            access.sendTrackerSync(context.player(), true);
            return true;
        }
        if (!campaignEntry.available() || campaignEntry.catchUp() || !CampaignQuestAccess.executionIssue(context, definition).isBlank()) return false;
        QuestDefinition.Stage previous = definition.stages().get(progress.currentStage());
        if (previous != null && previous.next().equals(stage.trim()) && !QuestParallelService.complete(previous, progress)) return false;
        if (runExitActions && previous != null) {
            progress.requestStageTransition(stage);
            VillagerQuestSavedData.get(context.level()).setDirty();
            if (!access.runStageActions(context, definition, progress, "exit", previous.exitActions()).success()) return false;
        }
        QuestLifecycleService.StageTransition transition =
                QuestLifecycleService.transitionStage(definition, progress, stage, context.level().getGameTime());
        if (!transition.changed()) {
            return false;
        }
        syncQuestStageFact(context, definition, transition.currentStage());
        access.ensureStageTarget(context, definition, progress);
        access.dispatchStageChangedTriggers(context, definition, progress);

        if (runEntryActions && progress.currentStage().equals(transition.currentStage())) {
            progress.requestStageEntry(transition.currentStage());
            VillagerQuestSavedData.get(context.level()).setDirty();
            resumePendingStageEntry(context, definition, progress);
        }
        access.sendTrackerSync(context.player(), true);
        return true;
    }

    public boolean resumePendingStageEntry(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        if (progress == null || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE
                || !QuestAvailabilityService.matchesProviderLock(context, definition, progress)
                || !CampaignQuestAccess.executionIssue(context, definition).isBlank()
                || progress.pendingStageEntry().isBlank()
                || !progress.pendingStageEntry().equals(progress.currentStage())
                || !QuestStageActionService.readyForStageEntry(context, progress)) return false;
        QuestDefinition.Stage stage = definition.stages().get(progress.pendingStageEntry());
        if (stage == null) return false;
        progress.requestStageEntry("");
        syncQuestStageFact(context, definition, progress.currentStage());
        access.ensureStageTarget(context, definition, progress);
        VillagerQuestSavedData.get(context.level()).setDirty();
        access.runStageActions(context, definition, progress, "entry", stage.entryActions());
        return true;
    }

    public void syncQuestStageFact(
            DialogueContext context,
            QuestDefinition definition,
            String stage) {
        com.jvn.villagerretaliation.authoring.preview.QuestPlaytestSession.recordStage(context.player(), definition.id(), stage);
        String scopeKey = access.playerQuestScopeKey(context.player(), definition);
        if (!scopeKey.isBlank()) {
            VillagerQuestFacts.get(context.level()).setVariable(scopeKey, "stage", stage);
        }
    }

    public boolean advanceStageIfComplete(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        if (context == null || definition == null || progress == null || progress.completionPending() || definition.stages().isEmpty()
                || !CampaignQuestAccess.executionIssue(context, definition).isBlank()) {
            return false;
        }
        if (com.jvn.villagerretaliation.authoring.preview.QuestPlaytestSession.suspended(context.player(), definition.id())) return false;
        boolean changed = false;
        for (int i = 0; i < MAX_STAGE_ADVANCES_PER_CHECK; i++) {
            changed |= resumePendingStageEntry(context, definition, progress);
            if (com.jvn.villagerretaliation.scene.SceneContinuationService.hasPendingQuestOperation(context, definition.id())) break;
            if (!QuestStageActionService.ready(context, progress)) break;
            if (!progress.pendingStageTransition().isBlank()) {
                if (!changeQuestStage(context, definition, progress, progress.pendingStageTransition(), true, true)) break;
                changed = true;
                continue;
            }
            changed |= advanceParallel(context, definition, progress);
            if (!definition.stages().containsKey(progress.currentStage())
                    || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE) break;
            changed |= runEligibleStageBonuses(context, definition, progress);
            if (!QuestStageActionService.ready(context, progress)) break;
            QuestStageReadiness readiness = QuestStageReadiness.forCurrentStage(
                    context,
                    definition,
                    progress,
                    objective -> access.objectiveComplete(context.player(), context, context.level(), definition, progress, objective));
            if (com.jvn.villagerretaliation.authoring.preview.QuestPlaytestSession.held(context.player(), definition.id(), progress.currentStage())) break;
            if (!readiness.ready()) {
                break;
            }
            markReadyStageObjectivesComplete(context, definition, progress, readiness.currentStage());
            if (!changeQuestStage(context, definition, progress, readiness.nextStage(), true, true)) {
                break;
            }
            com.jvn.villagerretaliation.authoring.preview.QuestPlaytestSession.recordChoice(context.player(), definition.id(), "transition:" + readiness.currentStage());
            changed = true;
        }
        return changed;
    }

    private boolean runEligibleStageBonuses(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        QuestDefinition.Stage stage = definition.stages().get(progress.currentStage());
        if (stage == null || stage.bonuses().isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (QuestDefinition.BonusOutcome bonus : stage.bonuses()) {
            if (progress.bonusClaimed(stage.id(), bonus.id())
                    || !QuestStageReadiness.compositionMet(
                            context,
                            definition,
                            bonus.when(),
                            bonus.mode(),
                            bonus.count(),
                            objective -> access.objectiveComplete(
                                    context.player(), context, context.level(), definition, progress, objective))) {
                continue;
            }
            if (progress.claimBonus(stage.id(), bonus.id())) {
                changed = true;
                if (!access.runStageActions(context, definition, progress, "bonus/" + bonus.id(), bonus.actions()).success()) break;
            }
        }
        return changed;
    }

    private void markReadyStageObjectivesComplete(
            DialogueContext context,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            String stageId) {
        if (context == null || definition == null || progress == null || stageId == null || stageId.isBlank()) {
            return;
        }
        QuestDefinition.Stage stage = definition.stages().get(stageId);
        if (stage == null || stage.completeWhen().isEmpty()) {
            return;
        }
        Map<String, QuestDefinition.Objective> objectives = definition.objectives().stream()
                .collect(java.util.stream.Collectors.toMap(
                        QuestDefinition.Objective::id,
                        objective -> objective,
                        (first, ignored) -> first,
                        LinkedHashMap::new));
        for (QuestDefinition.StagePredicate predicate : stage.completeWhen()) {
            if (predicate.objective().isBlank()) {
                continue;
            }
            QuestDefinition.Objective objective = objectives.get(predicate.objective());
            if (objective != null
                    && access.objectiveComplete(context.player(), context, context.level(), definition, progress, objective)) {
                progress.markObjectiveComplete(objective.id());
            }
        }
    }

    public boolean advanceStageAfterEvent(
            ServerLevel level,
            ServerPlayer player,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        return advanceStageAfterEvent(level, player, definition, progress, access.contextForStartedVillager(level, player, progress).orElse(null));
    }

    public boolean advanceStageAfterEvent(ServerLevel level, ServerPlayer player, QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress, DialogueContext context) {
        if (context != null) return advanceStageIfComplete(context, definition, progress);
        if (!CampaignQuestAccess.executionIssue(level, player.getUUID(), definition, progress).isBlank()
                || com.jvn.villagerretaliation.scene.SceneContinuationService.hasPendingQuestOperation(level, player.getUUID(), definition.id())) return false;
        boolean changed = false;
        for (int index = 0; index < MAX_STAGE_ADVANCES_PER_CHECK; index++) {
            if (com.jvn.villagerretaliation.authoring.preview.QuestPlaytestSession.held(player, definition.id(), progress.currentStage())) break;
            if (!QuestProgressionService.advanceSaved(definition, progress, level.getGameTime(),
                    objective -> access.objectiveComplete(player, null, level, definition, progress, objective),
                    conditions -> access.conditionsMet(player, level, definition, progress, conditions))) break;
            VillagerQuestFacts.get(level).setVariable(access.playerQuestScopeKey(player, definition), "stage", progress.currentStage());
            VillagerQuestSavedData.get(level).setDirty();
            changed = true;
        }
        return changed;
    }

    public interface Access {
        void ensureStageTarget(DialogueContext context, QuestDefinition definition, VillagerQuestSavedData.QuestProgress progress);
        boolean dispatchStageChangedTriggers(DialogueContext context, QuestDefinition definition, VillagerQuestSavedData.QuestProgress progress);
        void sendTrackerSync(ServerPlayer player, boolean flash);
        String playerQuestScopeKey(ServerPlayer player, QuestDefinition definition);
        boolean objectiveComplete(ServerPlayer player, DialogueContext context, ServerLevel level, QuestDefinition definition, VillagerQuestSavedData.QuestProgress progress, QuestDefinition.Objective objective);
        ActionResult runStageActions(DialogueContext context, QuestDefinition definition, VillagerQuestSavedData.QuestProgress progress, String key, List<VillagerActionDefinition> actions);
        Optional<DialogueContext> contextForStartedVillager(ServerLevel level, ServerPlayer player, VillagerQuestSavedData.QuestProgress progress);
        boolean conditionsMet(ServerPlayer player, ServerLevel level, QuestDefinition definition,
                VillagerQuestSavedData.QuestProgress progress, List<DialogueCondition> conditions);
    }
}
