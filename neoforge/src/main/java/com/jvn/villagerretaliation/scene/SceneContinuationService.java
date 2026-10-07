package com.jvn.villagerretaliation.scene;

import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.ActionSequenceRunner;
import com.jvn.villagerretaliation.action.ActionStatus;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.runtime.QuestStageActionService;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneContinuation;
import com.jvn.villagerretaliation.scene.runtime.SceneInstance;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

public final class SceneContinuationService {
    private static final int MAX_CONTINUATIONS_PER_TICK = 16;

    private SceneContinuationService() {
    }

    public static RunResult run(DialogueContext context, List<VillagerActionDefinition> actions,
            Map<String, String> baseReplacements, Runnable trackerFlashHandler, String sourcePointer) {
        return run(context, actions, baseReplacements, trackerFlashHandler, sourcePointer, null);
    }

    public static RunResult run(DialogueContext context, List<VillagerActionDefinition> actions,
            Map<String, String> baseReplacements, Runnable trackerFlashHandler, String sourcePointer,
            SceneContinuation.DialogueResume dialogueResume) {
        RunResult existing = existingContinuation(context, actions, sourcePointer);
        if (existing != null) return existing;
        ActionSequenceRunner.Result sequence = ActionSequenceRunner.run(
                context, actions, 0, baseReplacements, trackerFlashHandler, null);
        ActionResult result = sequence.outcome();
        if (!result.suspended()) return RunResult.of(result, null);
        SceneSavedData data = SceneSavedData.get(context.level());
        SceneInstance scene = data.get(result.suspensionId()).orElse(null);
        if (scene == null) return RunResult.of(ActionResult.unavailable(
                "scene instance unavailable", result.capabilities(), result.legacyResult()), null);
        if (scene.state().terminal() && scene.completionResult() != SceneInstance.CompletionResult.SUCCESS) {
            return RunResult.of(new ActionResult(ActionStatus.FAILED, "scene did not complete successfully",
                    result.capabilities(), result.legacyResult()), null);
        }
        int index = sequence.nextActionIndex() - 1;
        SceneContinuation continuation = data.suspendContinuation(scene, context.player().getUUID(),
                context.villager().getUUID(), pointer(sourcePointer, actions.get(index), index),
                actions, sequence.nextActionIndex(), result.legacyResult().replacements());
        if (continuation.completionReceipt()) {
            return RunResult.of(new ActionResult(continuation.actionStatus(), continuation.actionDiagnostic(),
                    result.capabilities(), result.legacyResult()), continuation.id());
        }
        continuation.dialogueResume(dialogueResume);
        data.changed();
        return RunResult.of(result, continuation.id());
    }

    private static RunResult existingContinuation(DialogueContext context,
            List<VillagerActionDefinition> actions, String sourcePointer) {
        if (context == null || actions == null) return null;
        for (int index = 0; index < actions.size(); index++) {
            VillagerActionDefinition action = actions.get(index);
            if (action.kind() != VillagerActionDefinition.Kind.START_SCENE || !action.waitForScene()) continue;
            String source = pointer(sourcePointer, action, index);
            for (SceneContinuation continuation : SceneSavedData.get(context.level()).continuations()) {
                if (!java.util.Objects.equals(action.questId(), continuation.questId())) continue;
                if (!source.equals(continuation.sourcePointer())
                        || !context.player().getUUID().equals(continuation.playerId())
                        || !context.villager().getUUID().equals(continuation.providerId())) continue;
                if (continuation.questId() != null && continuation.questRunId() != null) {
                    var progress = VillagerQuestSavedData.get(context.level()).get(
                            context.player().getUUID(), continuation.questId());
                    if (progress == null || !continuation.questRunId().equals(progress.questRunId())) continue;
                }
                if (!continuation.actions().equals(actions)) {
                    return RunResult.of(ActionResult.unavailable("suspended action definition changed",
                            Set.of(), VillagerActionResult.EMPTY), continuation.id());
                }
                VillagerActionResult output = new VillagerActionResult(false, "", "", continuation.replacements(), false);
                return RunResult.of(new ActionResult(continuation.actionStatus(), continuation.actionDiagnostic(),
                        Set.of(), output, continuation.sceneInstanceId()), continuation.id());
            }
        }
        return null;
    }

    public static boolean hasPendingDialogue(DialogueContext context) {
        return SceneSavedData.get(context.level()).continuations().stream().anyMatch(continuation ->
                !continuation.completionReceipt() && continuation.dialogueResume() != null
                        && context.player().getUUID().equals(continuation.playerId())
                        && context.villager().getUUID().equals(continuation.providerId()));
    }

    public static void maintain(MinecraftServer server, SceneSavedData data) {
        int work = 0;
        for (SceneContinuation continuation : data.continuations()) {
            if (work >= MAX_CONTINUATIONS_PER_TICK) break;
            if (continuation.completionReceipt()) continue;
            SceneInstance scene = data.get(continuation.sceneInstanceId()).orElse(null);
            if (scene == null || !scene.state().terminal()) continue;
            work++;
            if (continuation.state() == SceneContinuation.State.WAITING) {
                continuation.resuming(scene.completionResult());
                data.changed();
            }
            if (scene.completionResult() != SceneInstance.CompletionResult.SUCCESS) {
                continuation.finish(ActionResult.failed("scene " + scene.completionResult(), Set.of()));
                QuestStageActionService.settleContinuation(server.overworld(), continuation);
                data.changed();
                continue;
            }
            resume(server, data, continuation);
        }
    }

    private static void resume(MinecraftServer server, SceneSavedData data, SceneContinuation continuation) {
        ServerPlayer player = server.getPlayerList().getPlayer(continuation.playerId());
        Villager provider = findVillager(server, continuation.providerId());
        if (player == null || provider == null || provider.level() != player.serverLevel()) return;
        if (continuation.questId() != null && continuation.questRunId() != null) {
            var progress = VillagerQuestSavedData.get(player.serverLevel())
                    .get(player.getUUID(), continuation.questId());
            if (progress == null || !continuation.questRunId().equals(progress.questRunId())) {
                continuation.finish(ActionResult.unavailable("quest run changed", Set.of(), VillagerActionResult.EMPTY));
                data.changed();
                return;
            }
        }
        DialogueContext context = VillagerInteractionService.createDialogueContext(player.serverLevel(), player, provider);
        ActionSequenceRunner.Result sequence = ActionSequenceRunner.run(context, continuation.actions(),
                continuation.nextActionIndex(), continuation.replacements(),
                () -> VillagerQuestService.flashTracker(player, true), (next, outcome) -> {
                    continuation.nextActionIndex(next);
                    continuation.replacements().putAll(outcome.legacyResult().replacements());
                    data.changed();
                });
        ActionResult result = sequence.outcome();
        if (result.suspended()) {
            continuation.waitOn(result.suspensionId());
        } else {
            continuation.finish(result);
            QuestStageActionService.settleContinuation(player.serverLevel(), continuation);
        }
        data.changed();
        if (result.success() && continuation.dialogueResume() != null) {
            DialogueTreeService.resumeAfterActions(context, continuation.dialogueResume(), continuation.replacements());
        }
    }

    private static Villager findVillager(MinecraftServer server, UUID id) {
        if (id == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof Villager villager) return villager;
        }
        return null;
    }

    private static String pointer(String source, VillagerActionDefinition action, int index) {
        String base = source == null || source.isBlank() ? "quest_action_sequence" : source;
        return base + "/" + index + "/" + action.sceneOperationId();
    }

    public record RunResult(boolean ran, boolean suspended, ActionResult outcome, UUID continuationId) {
        public RunResult(boolean ran, boolean suspended) {
            this(ran, suspended, new ActionResult(suspended ? ActionStatus.SUSPENDED : ActionStatus.SUCCESS,
                    "", Set.of(), ran ? VillagerActionResult.success() : VillagerActionResult.EMPTY), null);
        }

        private static RunResult of(ActionResult outcome, UUID continuationId) {
            return new RunResult(outcome.legacyResult().ran(), outcome.suspended(), outcome, continuationId);
        }
    }
}
