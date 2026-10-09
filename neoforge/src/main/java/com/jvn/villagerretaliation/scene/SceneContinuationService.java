package com.jvn.villagerretaliation.scene;

import com.jvn.villagerretaliation.campaign.CampaignQuestAccess;
import com.jvn.villagerretaliation.campaign.CampaignRewardRecovery;
import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.ActionSequenceRunner;
import com.jvn.villagerretaliation.action.ActionStatus;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSceneExecution;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.conditions.QuestAvailabilityService;
import com.jvn.villagerretaliation.quest.runtime.QuestStageActionService;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneContinuation;
import com.jvn.villagerretaliation.scene.runtime.SceneInstance;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private static final Map<SceneSavedData, Integer> MAINTENANCE_CURSORS = new java.util.WeakHashMap<>();
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
        boolean conversation = !ConversationSceneExecution.capture().isEmpty();
        for (int index = 0; actions != null && index < actions.size(); index++) {
            var action = actions.get(index);
            boolean decision = conversation && (action.kind() == VillagerActionDefinition.Kind.CAMPAIGN_OUTCOME
                    || action.kind() == VillagerActionDefinition.Kind.QUEST && action.questAction() == VillagerActionDefinition.QuestAction.START);
            if (action.kind() == VillagerActionDefinition.Kind.CAMPAIGN_REWARD
                    || decision && CampaignQuestAccess.campaignFor(context.level().getServer(), questId == null ? action.questId() : questId) != null) {
                return runCampaignActions(context, actions, baseReplacements, trackerFlashHandler, sourcePointer, dialogueResume, questId, questRunId, index);
            }
        }
        ActionSequenceRunner.Result sequence = ActionSequenceRunner.run(
                context, actions, 0, baseReplacements, trackerFlashHandler, null);
        ActionResult result = sequence.outcome();
        if (!result.suspended()) return RunResult.of(result, null);
        SceneSavedData data = SceneSavedData.get(context.level());
        WaitTarget target = waitTarget(data, result);
        SceneInstance scene = target == null ? null : target.scene();
        if (target == null) return RunResult.of(ActionResult.unavailable(
                "action suspension target unavailable", result.capabilities(), result.legacyResult()), null);
        if (scene != null && scene.state().terminal() && scene.completionResult() != SceneInstance.CompletionResult.SUCCESS) {
            return RunResult.of(new ActionResult(ActionStatus.FAILED, "scene did not complete successfully",
                    result.capabilities(), result.legacyResult()), null);
        }
        int index = sequence.nextActionIndex() - (result.retryOnResume() ? 0 : 1);
        VillagerActionDefinition suspendedAction = actions.get(index);
        ResourceLocation ownerQuest = questId == null ? suspendedAction.questId() : questId;
        var dependency = target.dependencyId() == null ? null : data.continuation(target.dependencyId()).orElse(null);
        if (ownerQuest == null && dependency != null && dependency.campaignRecovery()) ownerQuest = dependency.questId();
        var ownerProgress = ownerQuest == null ? null
                : VillagerQuestSavedData.get(context.level()).get(context.player().getUUID(), ownerQuest);
        UUID ownerRun = questId == null && ownerProgress != null ? ownerProgress.questRunId() : questRunId;
        String fingerprint = CampaignRewardRecovery.fingerprint(context.level().getServer(), ownerQuest);
        if ((target.rewardOperation() != null || target.outcomeOperation() != null) && (ownerRun == null || fingerprint.isBlank())) {
            return RunResult.of(ActionResult.unavailable("Campaign action recovery requires its active quest bundle and run.",
                    result.capabilities(), result.legacyResult()), null);
        }
        SceneContinuation continuation = scene == null
                ? data.suspendRewardContinuation(context.player().getUUID(), context.villager().getUUID(),
                        pointer(sourcePointer, suspendedAction, index), actions, sequence.nextActionIndex(),
                        result.legacyResult().replacements(), ownerQuest, ownerRun)
                : data.suspendContinuation(scene, context.player().getUUID(),
                        context.villager().getUUID(), pointer(sourcePointer, suspendedAction, index),
                        actions, sequence.nextActionIndex(), result.legacyResult().replacements(), ownerQuest, ownerRun);
        continuation.bindCampaign(CampaignQuestAccess.campaignFor(context.level().getServer(), ownerQuest));
        if (!continuation.conversationEffect().isEmpty()) continuation.bindRecoveryFingerprint(fingerprint);
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
        applyWait(context.level().getServer(), data, continuation, target, result);
        continuation.dialogueResume(dialogueResume);
        continuation.campaignConsent(com.jvn.villagerretaliation.campaign.CampaignQuestAcceptanceService.captureConsent());
        continuation.decisionParticipants(com.jvn.villagerretaliation.campaign.CampaignDecisionParticipants.capture());
        data.changed();
        CampaignRewardRecovery.persist(context.level().getServer(), data, continuation);
        return RunResult.of(continuation.actionRecovery()
                ? ActionResult.suspendedContinuation(continuation.id(), continuation.actionDiagnostic(), result.capabilities(), result.legacyResult())
                : result, continuation.id());
    }

    private static RunResult runCampaignActions(DialogueContext context, List<VillagerActionDefinition> actions,
            Map<String, String> replacements, Runnable trackerFlashHandler, String source, SceneContinuation.DialogueResume dialogueResume,
            ResourceLocation questId, UUID questRunId, int rewardIndex) {
        var data = SceneSavedData.get(context.level());
        var ownerQuest = questId == null ? actions.get(rewardIndex).questId() : questId;
        var progress = ownerQuest == null ? null : VillagerQuestSavedData.get(context.level()).get(context.player().getUUID(), ownerQuest);
        var run = questRunId == null && progress != null ? progress.questRunId() : questRunId;
        String fingerprint = CampaignRewardRecovery.fingerprint(context.level().getServer(), ownerQuest);
        var definition = ownerQuest == null ? null : VillagerQuestResources.quest(context.level().getServer(), ownerQuest).orElse(null);
        var campaign = definition == null ? null : definition.campaign();
        String authority = com.jvn.villagerretaliation.campaign.CampaignAuthority.controlIssue(context.player());
        if (!authority.isBlank()) return RunResult.of(ActionResult.unavailable(authority, Set.of(), VillagerActionResult.EMPTY), null);
        if (fingerprint.isBlank() || campaign == null || data.futureVersion()) {
            return RunResult.of(ActionResult.unavailable("Campaign action recovery requires readable scene data and its original quest bundle.", Set.of(), VillagerActionResult.EMPTY), null);
        }
        String access = CampaignQuestAccess.executionIssue(context.level().getServer(), ownerQuest, campaign, context.player().getUUID());
        if (!access.isBlank()) return RunResult.of(ActionResult.unavailable(access, Set.of(), VillagerActionResult.EMPTY), null);
        if (progress != null && !QuestAvailabilityService.matchesProviderLock(context, definition, progress)) {
            return RunResult.of(ActionResult.unavailable("Return to this quest's assigned character to continue its actions.", Set.of(), VillagerActionResult.EMPTY), null);
        }
        var continuation = data.suspendRewardContinuation(context.player().getUUID(), context.villager().getUUID(),
                pointer(source, actions.get(rewardIndex), rewardIndex), actions, 0, replacements, ownerQuest, run);
        if (!matchesProvider(context, continuation) || !continuation.actions().equals(actions)
                || !Objects.equals(continuation.questRunId(), run)) {
            return RunResult.of(ActionResult.unavailable("This recovery record belongs to a different saved request.", Set.of(), VillagerActionResult.EMPTY), continuation.id());
        }
        continuation.bindCampaign(campaign);
        continuation.enableCampaignRecovery(fingerprint);
        continuation.resuming(SceneInstance.CompletionResult.SUCCESS);
        continuation.dialogueResume(dialogueResume);
        continuation.campaignConsent(com.jvn.villagerretaliation.campaign.CampaignQuestAcceptanceService.captureConsent());
        continuation.decisionParticipants(com.jvn.villagerretaliation.campaign.CampaignDecisionParticipants.capture());
        data.changed();
        ActionResult result;
        if (CampaignRewardRecovery.persist(context.level().getServer(), data, continuation)) {
            RESUMING.get().add(continuation.id());
            try { result = resumeActions(context, data, continuation, false, trackerFlashHandler); }
            finally { RESUMING.get().remove(continuation.id()); }
        } else {
            result = ActionResult.suspendedContinuation(continuation.id(), continuation.actionDiagnostic(), Set.of(), VillagerActionResult.EMPTY);
        }
        return RunResult.of(result, continuation.id());
    }

    private static RunResult existingContinuation(DialogueContext context,
            List<VillagerActionDefinition> actions, String sourcePointer) {
        if (context == null || actions == null) return null;
        for (int index = 0; index < actions.size(); index++) {
            VillagerActionDefinition action = actions.get(index);
            if (!(action.kind() == VillagerActionDefinition.Kind.START_SCENE && action.waitForScene())
                    && action.kind() != VillagerActionDefinition.Kind.FORCED_DIALOGUE
                    && action.kind() != VillagerActionDefinition.Kind.CAMPAIGN_REWARD
                    && action.kind() != VillagerActionDefinition.Kind.CAMPAIGN_OUTCOME
                    && !questOperation(action)) continue;
            String source = pointer(sourcePointer, action, index);
            for (SceneContinuation continuation : SceneSavedData.get(context.level()).continuations()) {
                if (!source.equals(continuation.sourcePointer())
                        || !context.player().getUUID().equals(continuation.playerId())
                        || !matchesProvider(context, continuation)) continue;
                if (continuation.questId() != null && continuation.questRunId() != null) {
                    var progress = VillagerQuestSavedData.get(context.level()).get(
                            context.player().getUUID(), continuation.questId());
                    if (progress == null || !continuation.questRunId().equals(progress.questRunId())) continue;
                }
                if (!continuation.conversationEffect().equals(ConversationSceneExecution.capture())) {
                    return RunResult.of(ActionResult.unavailable("This suspended sequence belongs to another conversation effect.",
                            Set.of(), VillagerActionResult.EMPTY), continuation.id());
                }
                String recoveryIssue = CampaignRewardRecovery.issue(context.level().getServer(), continuation);
                if (!recoveryIssue.isBlank()) return RunResult.of(ActionResult.unavailable(recoveryIssue,
                        Set.of(), VillagerActionResult.EMPTY), continuation.id());
                if (!continuation.actions().equals(actions)) {
                    return RunResult.of(ActionResult.unavailable("suspended action definition changed",
                            Set.of(), VillagerActionResult.EMPTY), continuation.id());
                }
                VillagerActionResult output = new VillagerActionResult(false, "", "", continuation.replacements(), false);
                return RunResult.of(continuation.actionRecovery() && !continuation.completionReceipt()
                        ? ActionResult.suspendedContinuation(continuation.id(), continuation.actionDiagnostic(), Set.of(), output)
                        : new ActionResult(continuation.actionStatus(), continuation.actionDiagnostic(),
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

    private static boolean matchesProvider(DialogueContext context, SceneContinuation continuation) {
        return context.villager().getUUID().equals(continuation.providerId())
                || com.jvn.villagerretaliation.campaign.CampaignQuestCharacters.canReplaceProvider(
                        context.level().getServer(), continuation.questId(), continuation.providerId(), context.villager().getUUID());
    }

    public static boolean hasPendingDialogue(DialogueContext context) {
        return SceneSavedData.get(context.level()).continuations().stream().anyMatch(continuation ->
                !continuation.completionReceipt() && continuation.dialogueResume() != null
                        && context.player().getUUID().equals(continuation.playerId())
                        && matchesProvider(context, continuation));
    }

    public static void maintain(MinecraftServer server, SceneSavedData data) {
        int work = 0;
        var continuations = List.copyOf(data.continuations());
        int cursor = MAINTENANCE_CURSORS.getOrDefault(data, 0);
        for (int inspected = 0; inspected < continuations.size() && work < MAX_CONTINUATIONS_PER_TICK; inspected++) {
            int index = Math.floorMod(cursor + inspected, continuations.size());
            SceneContinuation continuation = continuations.get(index);
            MAINTENANCE_CURSORS.put(data, (index + 1) % continuations.size());
            boolean retriedSave = continuation.recoveryPersistencePending();
            if (retriedSave) {
                if (server.overworld().getGameTime() % 100L != 0L) continue;
                work++;
                if (!CampaignRewardRecovery.persist(server, data, continuation)) continue;
                if (continuation.completionReceipt()) {
                    QuestStageActionService.settleContinuation(server.overworld(), continuation);
                    resume(server, data, continuation);
                }
            }
            if (continuation.completionReceipt()) continue;
            String recoveryIssue = CampaignRewardRecovery.issue(server, continuation);
            if (!recoveryIssue.isBlank()) {
                if (continuation.pauseForConsent(recoveryIssue)) data.changed();
                continue;
            }
            if (!CampaignQuestAccess.executionIssue(
                    server, continuation.questId(), continuation.campaignId(), continuation.playerId()).isBlank()) continue;
            ActionResult dependencyResult;
            if (continuation.rewardOperation() != null) {
                dependencyResult = CampaignRewardRecovery.poll(server, ActionResult.suspendedReward(
                        continuation.rewardOperation(), continuation.actionDiagnostic(), Set.of(), VillagerActionResult.EMPTY));
                if (dependencyResult.suspended()) continue;
            } else if (continuation.outcomeOperation() != null) {
                dependencyResult = com.jvn.villagerretaliation.campaign.CampaignLocationVariants.poll(server, ActionResult.suspendedOutcome(
                        continuation.outcomeOperation(), continuation.actionDiagnostic(), Set.of(), VillagerActionResult.EMPTY));
                if (dependencyResult.suspended()) continue;
            } else if (continuation.dependencyContinuationId() != null) {
                if (cyclicDependency(data, continuation.id(), continuation.dependencyContinuationId())) {
                    dependencyResult = ActionResult.failed("cyclic action continuation dependency", Set.of());
                } else {
                    SceneContinuation dependency = data.continuation(continuation.dependencyContinuationId()).orElse(null);
                    if (dependency == null || !dependency.completionReceipt()) continue;
                    dependencyResult = new ActionResult(dependency.actionStatus(), dependency.actionDiagnostic(),
                            Set.of(), VillagerActionResult.EMPTY);
                }
            } else if (continuation.campaignRecovery() && continuation.sceneInstanceId() == null) {
                dependencyResult = ActionResult.success(VillagerActionResult.EMPTY, Set.of());
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
            if (!retriedSave) work++;
            if (!dependencyResult.success()) {
                continuation.finish(dependencyResult);
                data.changed();
                if (CampaignRewardRecovery.persist(server, data, continuation)) QuestStageActionService.settleContinuation(server.overworld(), continuation);
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
        String recoveryIssue = CampaignRewardRecovery.issue(server, continuation);
        if (!recoveryIssue.isBlank()) {
            if (continuation.pauseForConsent(recoveryIssue)) data.changed();
            return;
        }
        if (continuation.hasUnreadableActions()) {
            if (continuation.pauseForConsent("A saved campaign action is unreadable. Repair the action before resuming.")) data.changed();
            return;
        }
        var authority = continuation.conversationEffect().isEmpty() ? null : ConversationSceneExecution.resolve(server, continuation);
        if (authority != null && !authority.diagnostic().isBlank()) {
            if (continuation.pauseForConsent(authority.diagnostic())) data.changed();
            return;
        }
        ServerPlayer player = authority == null ? server.getPlayerList().getPlayer(continuation.playerId()) : authority.controller();
        Villager provider = findVillager(server, com.jvn.villagerretaliation.campaign.CampaignQuestCharacters.continuationProvider(
                server, continuation.questId(), continuation.providerId()));
        if (player == null || provider == null || provider.level() != player.serverLevel()) return;
        if (continuation.questId() != null && continuation.questRunId() != null) {
            var progress = VillagerQuestSavedData.get(player.serverLevel())
                    .get(continuation.playerId(), continuation.questId());
            if (progress == null || !continuation.questRunId().equals(progress.questRunId())) {
                continuation.finish(ActionResult.unavailable("quest run changed", Set.of(), VillagerActionResult.EMPTY));
                data.changed();
                CampaignRewardRecovery.persist(server, data, continuation);
                return;
            }
        }
        DialogueContext context = VillagerInteractionService.createDialogueContext(player.serverLevel(), player, provider);
        if (continuation.campaignRecovery() && authority == null) {
            String issue = com.jvn.villagerretaliation.campaign.CampaignAuthority.controlIssue(player);
            if (!issue.isBlank()) {
                if (continuation.pauseForConsent(issue)) data.changed();
                return;
            }
        }
        String participationIssue = com.jvn.villagerretaliation.campaign.CampaignDecisionParticipants.issue(continuation.decisionParticipants());
        if (!participationIssue.isBlank()) {
            if (continuation.pauseForConsent(participationIssue)) data.changed();
            return;
        }
        var access = com.jvn.villagerretaliation.campaign.CampaignQuestAcceptanceService.resumeAccess(context, continuation.playerId(), continuation.campaignConsent());
        if (!access.allowed()) {
            if (access.paused()) {
                if (continuation.pauseForConsent(access.diagnostic())) data.changed();
            } else {
                continuation.finish(ActionResult.unavailable(access.diagnostic(), Set.of(), VillagerActionResult.EMPTY));
                data.changed();
                if (CampaignRewardRecovery.persist(server, data, continuation)) QuestStageActionService.settleContinuation(player.serverLevel(), continuation);
            }
            return;
        }
        if (!CampaignQuestAccess.executionIssue(server, continuation.questId(), continuation.campaignId(), continuation.playerId()).isBlank()) return;
        var remaining = com.jvn.villagerretaliation.campaign.CampaignQuestAcceptanceService.remainingConsent(context, continuation.playerId(), continuation.campaignConsent());
        if (!remaining.equals(continuation.campaignConsent())) {
            continuation.campaignConsent(remaining);
            data.changed();
        }
        DialogueContext personal = personalContext(context, continuation);
        if (continuation.dialogueResume() != null && personal == null) {
            if (continuation.pauseForConsent("The original player must return before their dialogue can resume.")) data.changed();
            return;
        }
        if (continuation.completionReceipt()) {
            if (continuation.actionStatus() == ActionStatus.SUCCESS || continuation.actionStatus() == ActionStatus.ALREADY_SATISFIED) {
                if (continuation.dialogueResume() != null) DialogueTreeService.resumeAfterActions(personal, continuation.dialogueResume(), continuation.replacements());
            }
            return;
        }
        if (!CampaignRewardRecovery.persist(server, data, continuation)) return;
        com.jvn.villagerretaliation.campaign.CampaignQuestAcceptanceService.withConsent(remaining,
                () -> com.jvn.villagerretaliation.campaign.CampaignDecisionParticipants.withParticipants(continuation.decisionParticipants(),
                        () -> resumeActions(context, data, continuation, true, () -> {
                            var recipient = server.getPlayerList().getPlayer(continuation.playerId());
                            if (recipient != null) VillagerQuestService.flashTracker(recipient, true);
                        }, authority)));
    }

    private static DialogueContext personalContext(DialogueContext context, SceneContinuation continuation) {
        var player = context.level().getServer().getPlayerList().getPlayer(continuation.playerId());
        return player == null || player.serverLevel() != context.level() ? null
                : VillagerInteractionService.createDialogueContext(context.level(), player, context.villager());
    }

    private static ActionResult resumeActions(DialogueContext context, SceneSavedData data, SceneContinuation continuation, boolean resumeDialogue, Runnable trackerFlashHandler) {
        return resumeActions(context, data, continuation, resumeDialogue, trackerFlashHandler, null);
    }

    private static ActionResult resumeActions(DialogueContext context, SceneSavedData data, SceneContinuation continuation,
            boolean resumeDialogue, Runnable trackerFlashHandler, ConversationSceneExecution.Access authority) {
        var server = context.level().getServer();
        var held = new boolean[1];
        var actionContext = new DialogueContext[] { context };
        var rejected = new ActionResult[1];
        var participants = new com.jvn.villagerretaliation.campaign.CampaignDecisionParticipants[1];
        ActionSequenceRunner.Result sequence = ActionSequenceRunner.run(context, continuation.actions(),
                continuation.nextActionIndex(), continuation.replacements(),
                trackerFlashHandler, (next, outcome) -> {
                    continuation.nextActionIndex(next);
                    continuation.clearPreparedRecoveryAction();
                    if (continuation.questId() != null) {
                        var progress = VillagerQuestSavedData.get(context.level()).get(continuation.playerId(), continuation.questId());
                        if (progress != null) continuation.bindQuestRun(progress.questRunId());
                    }
                    continuation.replacements().putAll(outcome.legacyResult().replacements());
                    if (authority == null) continuation.campaignConsent(com.jvn.villagerretaliation.campaign.CampaignQuestAcceptanceService.captureConsent());
                    data.changed();
                    if (!outcome.suspended() && (outcome.success() || !continuation.actions().get(next - 1).required())
                            && !CampaignRewardRecovery.persist(context.level().getServer(), data, continuation)) throw pauseForSave(continuation);
                }, index -> {
                    rejected[0] = null;
                    participants[0] = null;
                    if (authority != null) {
                        var current = ConversationSceneExecution.resolve(server, continuation);
                        actionContext[0] = continuation.actions().get(index).kind() == VillagerActionDefinition.Kind.CAMPAIGN_OUTCOME
                                ? context : personalContext(context, continuation);
                        String issue = current.diagnostic();
                        if (issue.isBlank() && actionContext[0] == null) issue = "The suspended action is waiting for its original player.";
                        if (!issue.isBlank()) {
                            continuation.pauseForConsent(issue);
                            held[0] = true;
                            data.changed();
                            CampaignRewardRecovery.persist(server, data, continuation);
                            throw pauseForSave(continuation);
                        }
                    }
                    var approval = com.jvn.villagerretaliation.dialogue.conversation.ConversationNestedConfirmations.beforeContinuation(context, continuation, index);
                    if (!approval.waiting().isBlank()) {
                        continuation.pauseForConsent(approval.waiting());
                        held[0] = true;
                        data.changed();
                        CampaignRewardRecovery.persist(server, data, continuation);
                        throw pauseForSave(continuation);
                    }
                    rejected[0] = approval.rejection();
                    participants[0] = approval.participants();
                    if (continuation.actionRecovery() && rejected[0] == null) {
                        continuation.prepareRecoveryAction(index);
                        if (!CampaignRewardRecovery.persist(context.level().getServer(), data, continuation)) {
                            continuation.clearPreparedRecoveryAction();
                            throw pauseForSave(continuation);
                        }
                    }
                }, (action, replacements) -> {
                    if (rejected[0] != null) return rejected[0];
                    if (authority == null) return com.jvn.villagerretaliation.action.VillagerActionRegistry.execute(context, action, replacements);
                    var progress = action.questId() == null ? null : VillagerQuestSavedData.get(context.level()).get(continuation.playerId(), action.questId());
                    boolean shared = action.kind() == VillagerActionDefinition.Kind.CAMPAIGN_OUTCOME;
                    if (shared && progress == null) return ActionResult.unavailable("The deciding player's original quest run is unavailable.", Set.of(), VillagerActionResult.EMPTY);
                    var actor = shared ? new com.jvn.villagerretaliation.campaign.CampaignOutcomeService.Actor(continuation.playerId(), progress.questRunId()) : null;
                    var applied = new ActionResult[1];
                    com.jvn.villagerretaliation.campaign.CampaignQuestAcceptanceService.withConsent(continuation.campaignConsent(), () -> {
                        var evidence = participants[0] == null ? continuation.decisionParticipants() : participants[0].save();
                        applied[0] = com.jvn.villagerretaliation.campaign.CampaignDecisionParticipants.withParticipants(evidence,
                                () -> authority.withController(
                                        () -> ConversationSceneExecution.withContinuation(continuation,
                                                () -> com.jvn.villagerretaliation.action.VillagerActionRegistry.execute(actionContext[0], action, replacements, actor))));
                        continuation.campaignConsent(com.jvn.villagerretaliation.campaign.CampaignQuestAcceptanceService.captureConsent());
                    });
                    return applied[0];
                });
        ActionResult result = sequence.outcome();
        if (held[0] || continuation.recoveryPersistencePending()) return result;
        if (result.suspended()) {
            WaitTarget target = waitTarget(data, result);
            if (target == null || cyclicDependency(data, continuation.id(), target.dependencyId())) {
                result = ActionResult.failed(target == null ? "action suspension target unavailable"
                        : "cyclic action continuation dependency", result.capabilities());
                continuation.finish(result);
            } else {
                applyWait(context.level().getServer(), data, continuation, target, result);
            }
        } else {
            continuation.finish(result);
        }
        data.changed();
        if (!CampaignRewardRecovery.persist(context.level().getServer(), data, continuation)) {
            return ActionResult.suspendedContinuation(continuation.id(), continuation.actionDiagnostic(), result.capabilities(), result.legacyResult());
        }
        QuestStageActionService.settleContinuation(context.level(), continuation);
        if (resumeDialogue && result.success() && continuation.dialogueResume() != null) {
            DialogueTreeService.resumeAfterActions(authority == null ? context : personalContext(context, continuation),
                    continuation.dialogueResume(), continuation.replacements());
        }
        return result.suspended() && continuation.actionRecovery()
                ? ActionResult.suspendedContinuation(continuation.id(), continuation.actionDiagnostic(), result.capabilities(), result.legacyResult()) : result;
    }

    private static ActionSequenceRunner.Pause pauseForSave(SceneContinuation continuation) {
        return new ActionSequenceRunner.Pause(ActionResult.suspendedContinuation(continuation.id(),
                continuation.actionDiagnostic(), Set.of(), VillagerActionResult.EMPTY));
    }

    private static WaitTarget waitTarget(SceneSavedData data, ActionResult result) {
        if (result.suspensionKind() == ActionResult.SuspensionKind.CAMPAIGN_REWARD) {
            return result.suspensionId() == null ? null : new WaitTarget(null, null, result.suspensionId(), null);
        }
        if (result.suspensionKind() == ActionResult.SuspensionKind.CAMPAIGN_OUTCOME) {
            return result.suspensionId() == null ? null : new WaitTarget(null, null, null, result.suspensionId());
        }
        UUID dependencyId = result.suspensionKind() == ActionResult.SuspensionKind.CONTINUATION
                ? result.suspensionId() : null;
        SceneContinuation dependency = dependencyId == null ? null : data.continuation(dependencyId).orElse(null);
        if (dependencyId != null && dependency == null) return null;
        UUID sceneId = dependency == null ? result.suspensionId() : dependency.sceneInstanceId();
        SceneInstance scene = data.get(sceneId).orElse(null);
        return scene == null && (dependency == null || !dependency.campaignRecovery()) ? null : new WaitTarget(scene, dependencyId, null, null);
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

    private static void applyWait(MinecraftServer server, SceneSavedData data, SceneContinuation continuation, WaitTarget target, ActionResult result) {
        if (target.rewardOperation() != null) {
            continuation.waitForReward(target.rewardOperation(), CampaignRewardRecovery.fingerprint(server, continuation.questId()));
        } else if (target.outcomeOperation() != null) {
            continuation.waitForOutcome(target.outcomeOperation(), CampaignRewardRecovery.fingerprint(server, continuation.questId()));
        } else {
            continuation.waitOn(target.scene() == null ? null : target.scene().id(), target.dependencyId(), result.retryOnResume());
            var dependency = target.dependencyId() == null ? null : data.continuation(target.dependencyId()).orElse(null);
            if (dependency != null && dependency.campaignRecovery()) {
                continuation.enableCampaignRecovery(CampaignRewardRecovery.fingerprint(server, continuation.questId()));
            }
        }
    }

    private record WaitTarget(SceneInstance scene, UUID dependencyId, UUID rewardOperation, UUID outcomeOperation) { }

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
