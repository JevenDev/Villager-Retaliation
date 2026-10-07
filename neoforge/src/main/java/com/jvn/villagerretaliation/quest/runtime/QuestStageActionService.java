package com.jvn.villagerretaliation.quest.runtime;

import com.jvn.villagerretaliation.campaign.CampaignQuestAccess;
import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.ActionStatus;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.scene.SceneContinuationService;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneContinuation;
import net.minecraft.server.level.ServerLevel;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class QuestStageActionService {
    private QuestStageActionService() {
    }

    public static ActionResult run(DialogueContext context, QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress, String key, List<VillagerActionDefinition> actions,
            Map<String, String> replacements, Runnable trackerFlash) {
        return run(context, definition, progress, key, actions, replacements, trackerFlash, key);
    }

    public static ActionResult runTrigger(DialogueContext context, QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress, String key, List<VillagerActionDefinition> actions,
            Map<String, String> replacements, Runnable trackerFlash) {
        for (String savedKey : progress.stageActions().keySet()) {
            if (!savedKey.equals(key) && !savedKey.endsWith("/" + key)) continue;
            QuestActionCheckpoint prior = refresh(context, progress, savedKey);
            if (!prior.outcome().success()) return prior.outcome();
            progress.removeStageAction(savedKey);
            VillagerQuestSavedData.get(context.level()).setDirty();
        }
        return run(context, definition, progress, key, actions, replacements, trackerFlash, key + "/" + UUID.randomUUID());
    }

    private static ActionResult run(DialogueContext context, QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress, String key, List<VillagerActionDefinition> actions,
            Map<String, String> replacements, Runnable trackerFlash, String source) {
        String issue = CampaignQuestAccess.executionIssue(context.level(), context.player().getUUID(), definition, progress);
        if (!issue.isBlank()) return ActionResult.unavailable(issue, java.util.Set.of(), com.jvn.villagerretaliation.action.VillagerActionResult.EMPTY);
        QuestActionCheckpoint prior = refresh(context, progress, key);
        if (prior != null) return prior.outcome();
        String stage = progress.currentStage();
        UUID runId = progress.questRunId();
        SceneContinuationService.RunResult result = SceneContinuationService.run(context, actions,
                replacements, trackerFlash, definition.id() + "/" + runId + "/" + stage + "/" + source,
                null, definition.id(), runId);
        if (Objects.equals(runId, progress.questRunId()) && !result.outcome().success()) {
            String savedKey = stage.equals(progress.currentStage()) ? key : "stage/" + stage + "/" + key;
            progress.recordStageAction(savedKey, new QuestActionCheckpoint(result.outcome().status(),
                    result.outcome().message(), result.continuationId(), result.outcome().legacyResult().replacements()));
            VillagerQuestSavedData.get(context.level()).setDirty();
        }
        return result.suspended() ? ActionResult.suspendedContinuation(result.continuationId(),
                result.outcome().message(), result.outcome().capabilities(), result.outcome().legacyResult()) : result.outcome();
    }

    public static ActionResult pending(DialogueContext context, VillagerQuestSavedData.QuestProgress progress) {
        ActionResult suspended = null;
        for (String key : progress.stageActions().keySet()) {
            QuestActionCheckpoint checkpoint = refresh(context, progress, key);
            if (SceneContinuationService.dependsOnResumingContinuation(context.level(), checkpoint.continuationId())) continue;
            ActionResult result = checkpoint.outcome();
            if (result.success()) continue;
            if (!result.suspended()) return result;
            if (suspended == null) suspended = result;
        }
        return suspended;
    }

    public static boolean ready(DialogueContext context, VillagerQuestSavedData.QuestProgress progress) {
        return pending(context, progress) == null;
    }

    public static boolean readyForStageEntry(DialogueContext context, VillagerQuestSavedData.QuestProgress progress) {
        return ready(context, progress) && progress.stageActions().entrySet().stream().noneMatch(entry ->
                entry.getKey().startsWith("trigger/") && SceneContinuationService.isResuming(entry.getValue().continuationId()));
    }

    public static void settleContinuation(ServerLevel level, SceneContinuation continuation) {
        if (continuation.questId() == null || !continuation.completionReceipt()) return;
        VillagerQuestSavedData data = VillagerQuestSavedData.get(level);
        var progress = data.get(continuation.playerId(), continuation.questId());
        if (progress == null || (continuation.questRunId() != null
                && !continuation.questRunId().equals(progress.questRunId()))) return;
        for (var entry : progress.stageActions().entrySet()) {
            if (continuation.id().equals(entry.getValue().continuationId())) {
                progress.recordStageAction(entry.getKey(), new QuestActionCheckpoint(continuation.actionStatus(),
                        continuation.actionDiagnostic(), continuation.id(), continuation.replacements()));
                data.setDirty();
            }
        }
    }

    private static QuestActionCheckpoint refresh(DialogueContext context,
            VillagerQuestSavedData.QuestProgress progress, String key) {
        QuestActionCheckpoint prior = progress.stageActions().get(key);
        if (prior == null || prior.status() != ActionStatus.SUSPENDED || prior.continuationId() == null) return prior;
        var continuation = SceneSavedData.get(context.level()).continuation(prior.continuationId()).orElse(null);
        if (continuation == null || !continuation.completionReceipt()) return prior;
        QuestActionCheckpoint result = new QuestActionCheckpoint(continuation.actionStatus(),
                continuation.actionDiagnostic(), continuation.id(), continuation.replacements());
        progress.recordStageAction(key, result);
        VillagerQuestSavedData.get(context.level()).setDirty();
        return result;
    }
}
