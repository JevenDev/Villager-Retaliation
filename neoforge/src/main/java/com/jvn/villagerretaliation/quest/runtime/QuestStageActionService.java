package com.jvn.villagerretaliation.quest.runtime;

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

public final class QuestStageActionService {
    private QuestStageActionService() {
    }

    public static ActionResult run(DialogueContext context, QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress, String key, List<VillagerActionDefinition> actions,
            Map<String, String> replacements, Runnable trackerFlash) {
        QuestActionCheckpoint prior = refresh(context, progress, key);
        if (prior != null) return prior.outcome();
        String stage = progress.currentStage();
        SceneContinuationService.RunResult result = SceneContinuationService.run(context, actions,
                replacements, trackerFlash, definition.id() + "/" + progress.questRunId() + "/" + stage + "/" + key,
                null, definition.id(), progress.questRunId());
        if (stage.equals(progress.currentStage()) && !result.outcome().success()) {
            progress.recordStageAction(key, new QuestActionCheckpoint(result.outcome().status(),
                    result.outcome().message(), result.continuationId(), result.outcome().legacyResult().replacements()));
            VillagerQuestSavedData.get(context.level()).setDirty();
        }
        return result.suspended() ? ActionResult.suspendedContinuation(result.continuationId(),
                result.outcome().message(), result.outcome().capabilities(), result.outcome().legacyResult()) : result.outcome();
    }

    public static ActionResult pending(DialogueContext context, VillagerQuestSavedData.QuestProgress progress) {
        ActionResult suspended = null;
        for (String key : progress.stageActions().keySet()) {
            ActionResult result = refresh(context, progress, key).outcome();
            if (result.success()) continue;
            if (!result.suspended()) return result;
            if (suspended == null) suspended = result;
        }
        return suspended;
    }

    public static boolean ready(DialogueContext context, VillagerQuestSavedData.QuestProgress progress) {
        return pending(context, progress) == null;
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
        var continuation = SceneSavedData.get(context.level()).continuations().stream()
                .filter(value -> value.id().equals(prior.continuationId())).findFirst().orElse(null);
        if (continuation == null || !continuation.completionReceipt()) return prior;
        QuestActionCheckpoint result = new QuestActionCheckpoint(continuation.actionStatus(),
                continuation.actionDiagnostic(), continuation.id(), continuation.replacements());
        progress.recordStageAction(key, result);
        VillagerQuestSavedData.get(context.level()).setDirty();
        return result;
    }
}
