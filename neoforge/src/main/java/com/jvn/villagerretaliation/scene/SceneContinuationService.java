package com.jvn.villagerretaliation.scene;

import com.jvn.villagerretaliation.campaign.CampaignQuestAccess;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

public final class SceneContinuationService {
    private static final int MAX_CONTINUATIONS_PER_TICK = 16;
    private static final ThreadLocal<Set<UUID>> RESUMING = ThreadLocal.withInitial(HashSet::new);

    private SceneContinuationService() {
    }

    public static RunResult run(DialogueContext context, List<VillagerActionDefinition> actions,
            Map<String, String> baseReplacements, Runnable trackerFlashHandler, String sourcePointer) {
        return run(context, actions, baseReplacements, trackerFlashHandler, sourcePointer, null);
    }

    public static RunResult run(DialogueContext context, List<VillagerActionDefinition> actions,
            Map<String, String> baseReplacements, Runnable trackerFlashHandler, String sourcePointer,
            SceneContinuation.DialogueResume dialogueResume) {
        return run(context, actions, baseReplacements, trackerFlashHandler, sourcePointer, dialogueResume, null, null);
    }

    public static RunResult run(DialogueContext context, List<VillagerActionDefinition> actions,
            Map<String, String> baseReplacements, Runnable trackerFlashHandler, String sourcePointer,
            SceneContinuation.DialogueResume dialogueResume, ResourceLocation questId, UUID questRunId) {
        RunResult existing = existingContinuation(context, actions, sourcePointer);
        if (existing != null) return existing;
        ActionSequenceRunner.Result sequence = ActionSequenceRunner.run(
                context, actions, 0, baseReplacements, trackerFlashHandler, null);
        ActionResult result = sequence.outcome();
        if (!result.suspended()) return RunResult.of(result, null);
        SceneSavedData data = SceneSavedData.get(context.level());
        WaitTarget target = waitTarget(data, result);
        SceneInstance scene = target == null ? null : target.scene();
        if (scene == null) return RunResult.of(ActionResult.unavailable(
                "scene instance unavailable", result.capabilities(), result.legacyResult()), null);
        if (scene.state().terminal() && scene.completionResult() != SceneInstance.CompletionResult.SUCCESS) {
            return RunResult.of(new ActionResult(ActionStatus.FAILED, "scene did not complete successfully",
                    result.capabilities(), result.legacyResult()), null);
        }
        int index = sequence.nextActionIndex() - (result.retryOnResume() ? 0 : 1);
        VillagerActionDefinition suspendedAction = actions.get(index);
        ResourceLocation ownerQuest = questId == null ? suspendedAction.questId() : questId;
        var ownerProgress = ownerQuest == null ? null
                : VillagerQuestSavedData.get(context.level()).get(context.player().getUUID(), ownerQuest);
        UUID ownerRun = questId == null && ownerProgress != null ? ownerProgress.questRunId() : questRunId;
        SceneContinuation continuation = data.suspendContinuation(scene, context.player().getUUID(),
                context.villager().getUUID(), pointer(sourcePointer, suspendedAction, index),
                actions, sequence.nextActionIndex(), result.legacyResult().replacements(),
                ownerQuest, ownerRun);
        continuation.bindCampaign(CampaignQuestAccess.campaignFor(context.level().getServer(), ownerQuest));
        if (continuation.completionReceipt()) {
            return RunResult.of(new ActionResult(continuation.actionStatus(), continuation.actionDiagnostic(),
                    result.capabilities(), result.legacyResult()), continuation.id());
        }
        if (cyclicDependency(data, continuation.id(), target.dependencyId())) {
            ActionResult failure = ActionResult.failed("cyclic action continuation dependency", result.capabilities());
            continuation.finish(failure);
            data.changed();
            return RunResult.of(failure, continuation.id());
        }
        continuation.waitOn(scene.id(), target.dependencyId(), result.retryOnResume());
        continuation.dialogueResume(dialogueResume);
        continuation.campaignConsent(com.jvn.villagerretaliation.campaign.CampaignQuestAcceptanceService.captureConsent());
        data.changed();
        return RunResult.of(result, continuation.id());
    }

    private static RunResult existingContinuation(DialogueContext context,
            List<VillagerActionDefinition> actions, String sourcePointer) {
        if (context == null || actions == null) return null;
        for (int index = 0; index < actions.size(); index++) {
            VillagerActionDefinition action = actions.get(index);
            if (!(action.kind() == VillagerActionDefinition.Kind.START_SCENE && action.waitForScene())
                    && action.kind() != VillagerActionDefinition.Kind.FORCED_DIALOGUE
                    && !questOperation(action)) continue;
            String source = pointer(sourcePointer, action, index);
            for (SceneContinuation continuation : SceneSavedData.get(context.level()).continuations()) {
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

    public static boolean questOperation(VillagerActionDefinition action) {
        return action != null && (action.kind() == VillagerActionDefinition.Kind.QUEST_TRANSITION
                || action.kind() == VillagerActionDefinition.Kind.QUEST
                        && action.questAction() != VillagerActionDefinition.QuestAction.REMIND
                || action.kind() == VillagerActionDefinition.Kind.SET_VARIABLE
                        && action.factScope() == com.jvn.villagerretaliation.quest.QuestFactScope.QUEST
                        && "stage".equals(action.factKey()));
    }

    public static boolean isResuming(UUID continuationId) {
        return continuationId != null && RESUMING.get().contains(continuationId);
    }

    public static boolean dependsOnResumingContinuation(ServerLevel level, UUID continuationId) {
        if (RESUMING.get().isEmpty()) return false;
        SceneSavedData data = SceneSavedData.get(level);
        Set<UUID> visited = new HashSet<>();
        while (continuationId != null && visited.add(continuationId)) {
            if (isResuming(continuationId)) return true;
            SceneContinuation continuation = data.continuation(continuationId).orElse(null);
            continuationId = continuation == null ? null : continuation.dependencyContinuationId();
        }
        return false;
    }

    public static boolean hasPendingQuestOperation(DialogueContext context, ResourceLocation questId) {
        SceneSavedData data = SceneSavedData.get(context.level());
        if (data.continuations().stream().anyMatch(continuation ->
                !continuation.completionReceipt() && continuation.retrySuspendedAction()
                        && !RESUMING.get().contains(continuation.id())
                        && context.player().getUUID().equals(continuation.playerId())
                        && continuation.nextActionIndex() < continuation.actions().size()
                        && questId.equals(continuation.actions().get(continuation.nextActionIndex()).questId()))) return true;
        return data.active().stream().filter(scene -> !SceneLifecycleIntegration.isOriginatingScene(scene.id()))
                .flatMap(scene -> scene.receipts().values().stream())
                .map(com.jvn.villagerretaliation.scene.runtime.SceneOperationReceipt::actionResult)
                .filter(result -> result.suspended() && result.retryOnResume()
                        && result.suspensionKind() == ActionResult.SuspensionKind.CONTINUATION)
                .flatMap(result -> data.continuation(result.suspensionId()).stream())
                .anyMatch(continuation -> context.player().getUUID().equals(continuation.playerId())
                        && questId.equals(continuation.questId()));
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
            if (!CampaignQuestAccess.executionIssue(
                    server, continuation.questId(), continuation.campaignId(), continuation.playerId()).isBlank()) continue;
            ActionResult dependencyResult;
            if (continuation.dependencyContinuationId() != null) {
                if (cyclicDependency(data, continuation.id(), continuation.dependencyContinuationId())) {
                    dependencyResult = ActionResult.failed("cyclic action continuation dependency", Set.of());
                } else {
                    SceneContinuation dependency = data.continuation(continuation.dependencyContinuationId()).orElse(null);
                    if (dependency == null || !dependency.completionReceipt()) continue;
                    dependencyResult = new ActionResult(dependency.actionStatus(), dependency.actionDiagnostic(),
                            Set.of(), VillagerActionResult.EMPTY);
                }
            } else {
                SceneInstance scene = data.get(continuation.sceneInstanceId()).orElse(null);
                if (scene == null || !scene.state().terminal()) continue;
                if (continuation.state() == SceneContinuation.State.WAITING) {
                    continuation.resuming(scene.completionResult());
                    data.changed();
                }
                dependencyResult = scene.completionResult() == SceneInstance.CompletionResult.SUCCESS
                        ? ActionResult.success(VillagerActionResult.EMPTY, Set.of())
                        : ActionResult.failed("scene " + scene.completionResult(), Set.of());
            }
            work++;
            if (!dependencyResult.success()) {
                continuation.finish(dependencyResult);
                QuestStageActionService.settleContinuation(server.overworld(), continuation);
                data.changed();
                continue;
            }
            if (continuation.state() == SceneContinuation.State.WAITING) {
                continuation.resuming(SceneInstance.CompletionResult.SUCCESS);
                data.changed();
            }
            RESUMING.get().add(continuation.id());
            try {
                resume(server, data, continuation);
            } finally {
                RESUMING.get().remove(continuation.id());
            }
        }
    }

    private static void resume(MinecraftServer server, SceneSavedData data, SceneContinuation continuation) {
        if (!CampaignQuestAccess.executionIssue(server, continuation.questId(), continuation.campaignId(), continuation.playerId()).isBlank()) return;
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
        var access = com.jvn.villagerretaliation.campaign.CampaignQuestAcceptanceService.resumeAccess(context, continuation.campaignConsent());
        if (!access.allowed()) {
            if (access.paused()) {
                if (continuation.pauseForConsent(access.diagnostic())) data.changed();
            } else {
                continuation.finish(ActionResult.unavailable(access.diagnostic(), Set.of(), VillagerActionResult.EMPTY));
                QuestStageActionService.settleContinuation(player.serverLevel(), continuation);
                data.changed();
            }
            return;
        }
        var remaining = com.jvn.villagerretaliation.campaign.CampaignQuestAcceptanceService.remainingConsent(context, continuation.campaignConsent());
        if (!remaining.equals(continuation.campaignConsent())) {
            continuation.campaignConsent(remaining);
            data.changed();
        }
        com.jvn.villagerretaliation.campaign.CampaignQuestAcceptanceService.withConsent(remaining,
                () -> resumeActions(context, data, continuation));
    }

    private static void resumeActions(DialogueContext context, SceneSavedData data, SceneContinuation continuation) {
        ServerPlayer player = context.player();
        ActionSequenceRunner.Result sequence = ActionSequenceRunner.run(context, continuation.actions(),
                continuation.nextActionIndex(), continuation.replacements(),
                () -> VillagerQuestService.flashTracker(player, true), (next, outcome) -> {
                    continuation.nextActionIndex(next);
                    continuation.replacements().putAll(outcome.legacyResult().replacements());
                    continuation.campaignConsent(com.jvn.villagerretaliation.campaign.CampaignQuestAcceptanceService.captureConsent());
                    data.changed();
                });
        ActionResult result = sequence.outcome();
        if (result.suspended()) {
            WaitTarget target = waitTarget(data, result);
            if (target == null || cyclicDependency(data, continuation.id(), target.dependencyId())) {
                result = ActionResult.failed(target == null ? "action suspension target unavailable"
                        : "cyclic action continuation dependency", result.capabilities());
                continuation.finish(result);
                QuestStageActionService.settleContinuation(player.serverLevel(), continuation);
            } else {
                continuation.waitOn(target.scene().id(), target.dependencyId(), result.retryOnResume());
            }
        } else {
            continuation.finish(result);
            QuestStageActionService.settleContinuation(player.serverLevel(), continuation);
        }
        data.changed();
        if (result.success() && continuation.dialogueResume() != null) {
            DialogueTreeService.resumeAfterActions(context, continuation.dialogueResume(), continuation.replacements());
        }
    }

    private static WaitTarget waitTarget(SceneSavedData data, ActionResult result) {
        UUID dependencyId = result.suspensionKind() == ActionResult.SuspensionKind.CONTINUATION
                ? result.suspensionId() : null;
        SceneContinuation dependency = dependencyId == null ? null : data.continuation(dependencyId).orElse(null);
        if (dependencyId != null && dependency == null) return null;
        UUID sceneId = dependency == null ? result.suspensionId() : dependency.sceneInstanceId();
        SceneInstance scene = data.get(sceneId).orElse(null);
        return scene == null ? null : new WaitTarget(scene, dependencyId);
    }

    private static boolean cyclicDependency(SceneSavedData data, UUID parentId, UUID dependencyId) {
        Set<UUID> visited = new HashSet<>();
        visited.add(parentId);
        while (dependencyId != null) {
            if (!visited.add(dependencyId)) return true;
            SceneContinuation dependency = data.continuation(dependencyId).orElse(null);
            dependencyId = dependency == null ? null : dependency.dependencyContinuationId();
        }
        return false;
    }

    private record WaitTarget(SceneInstance scene, UUID dependencyId) {
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
        String operation = action.sceneOperationId();
        if (questOperation(action)) {
            operation = action.kind().serializedName() + "/" + action.questId() + "/" + switch (action.kind()) {
                case QUEST -> action.questAction().name();
                case QUEST_TRANSITION -> action.questTransition().stageId() + "/"
                        + action.questTransition().scenePath() + "/" + action.questTransition().responseId();
                case SET_VARIABLE -> action.factKey();
                default -> "";
            };
        }
        return base + "/" + index + "/" + operation;
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
