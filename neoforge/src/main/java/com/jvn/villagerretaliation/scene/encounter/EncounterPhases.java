package com.jvn.villagerretaliation.scene.encounter;

import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.ActionStatus;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import com.jvn.villagerretaliation.campaign.CampaignQuestAccess;
import com.jvn.villagerretaliation.quest.QuestFactReceipt;
import com.jvn.villagerretaliation.quest.QuestScopeKey;
import com.jvn.villagerretaliation.quest.VillagerQuestFacts;
import com.jvn.villagerretaliation.scene.SceneResources;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneDefinitionReconciler;
import com.jvn.villagerretaliation.scene.runtime.SceneInstance;
import com.jvn.villagerretaliation.scene.runtime.SceneOperationReceipt;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

final class EncounterPhases {
    private EncounterPhases() { }

    static EncounterService.Result evaluate(MinecraftServer server, SceneSavedData data, EncounterInstance encounter,
            EncounterTemplate template, long time) {
        var scene = data.get(encounter.sceneId()).orElse(null);
        if (scene == null) return waiting(data, encounter, "Encounter phases require their original scene.");
        var ledger = encounter.phaseLedger();
        try {
            EncounterPhaseLedger.require(ledger.diagnostic().isBlank(), ledger.diagnostic());
            String issue = CampaignQuestAccess.contentIssue(server, scene.owningQuestId(), scene.owningCampaignId());
            EncounterPhaseLedger.require(issue.isBlank(), issue);
            var definition = SceneResources.scene(server, scene.sceneId()).orElse(null);
            EncounterPhaseLedger.require(definition != null, "The encounter's scene content is unavailable.");
            var reconciliation = SceneDefinitionReconciler.reconcile(scene, definition);
            EncounterPhaseLedger.require(reconciliation.safe(), reconciliation.diagnostic());
            issue = EncounterPersistence.prepare(server, data, encounter);
            if (!issue.isBlank()) return waiting(data, encounter, issue);
            boolean prepared = ledger.bind(template);
            prepared |= ledger.captureCompletion(encounter, template, time);
            if (prepared) {
                data.changed();
                issue = EncounterPersistence.checkpoint(server, data, encounter);
                if (!issue.isBlank()) return waiting(data, encounter, issue);
            }
            for (var run : ledger.runs().values()) {
                var phase = template.phases().stream().filter(value -> value.id().equals(run.phase())).findFirst().orElse(null);
                EncounterPhaseLedger.require(phase != null && run.ordinal() <= phase.maxFires()
                        && run.ordinal() <= encounter.phaseFireCounts().getOrDefault(run.phase(), 0)
                        && run.nextAction() <= phase.actions().size()
                        && run.participants().equals(encounter.participants()) && Objects.equals(run.quest(), scene.owningQuestId()),
                        "Saved phase ownership or progress is inconsistent. Restore matching encounter data.");
                if (run.ordinal() < encounter.phaseFireCounts().getOrDefault(run.phase(), 0))
                    EncounterPhaseLedger.require(run.nextAction() == phase.actions().size(), "An earlier phase run is unfinished. Restore matching scene data.");
            }
            for (var entry : encounter.phaseFireCounts().entrySet()) {
                var phase = template.phases().stream().filter(value -> value.id().equals(entry.getKey())).findFirst().orElse(null);
                EncounterPhaseLedger.require(phase != null && entry.getValue() >= 0 && entry.getValue() <= phase.maxFires(), "Saved phase counts no longer match the encounter.");
            }
            for (var phase : template.phases()) {
                int count = encounter.phaseFireCounts().getOrDefault(phase.id(), 0);
                if (count > 0) {
                    var result = execute(server, data, encounter, scene, phase, count, time);
                    if (!result.success()) return stop(server, data, encounter, result);
                }
                boolean canStart = encounter.state() == EncounterInstance.EncounterState.ACTIVE
                        || encounter.state() == EncounterInstance.EncounterState.COMPLETED && count == 0 && ledger.completionTriggered(phase.id());
                if (!canStart || count > 0 && !phase.repeatable() || count >= phase.maxFires()
                        || time < encounter.phaseNextAt().getOrDefault(phase.id(), 0L)
                        || !EncounterService.phaseMatches(encounter, template, phase, time)) continue;
                int ordinal = encounter.startPhaseFire(phase.id(), phase.repeatable() ? time + phase.repeatIntervalTicks() : 0L);
                var run = new EncounterPhaseLedger.Run(phase.id(), ordinal, UUID.randomUUID(), scene.currentStep(), scene.owningQuestId(), encounter.participants(), 0);
                EncounterPhaseLedger.require(!scene.receipts().containsKey(run.operation(encounter)), "The new phase already has a receipt. Restore matching phase history.");
                ledger.record(run);
                scene.prepareReceipt(run.operation(encounter), SceneOperationReceipt.Kind.ENCOUNTER_PHASE, time);
                data.changed();
                issue = EncounterPersistence.checkpoint(server, data, encounter);
                if (!issue.isBlank()) return waiting(data, encounter, issue);
                var result = execute(server, data, encounter, scene, phase, ordinal, time);
                if (!result.success()) return stop(server, data, encounter, result);
            }
            return EncounterService.Result.active();
        } catch (IllegalArgumentException exception) {
            return waiting(data, encounter, exception.getMessage());
        }
    }

    private static ActionResult execute(MinecraftServer server, SceneSavedData data, EncounterInstance encounter,
            SceneInstance scene, EncounterTemplate.Phase phase, int ordinal, long time) {
        var run = encounter.phaseLedger().runs().get(phase.id() + "/" + ordinal);
        String operation = scene.id() + "/encounter/" + encounter.id() + "/phase/" + phase.id() + "/" + ordinal;
        var phaseReceipt = scene.receipts().get(operation);
        if (run == null) {
            if (phaseReceipt != null && phaseReceipt.state() == SceneOperationReceipt.ReceiptState.COMPLETED)
                return ActionResult.alreadySatisfied("legacy phase already completed", Set.of(), VillagerActionResult.EMPTY);
            return ActionResult.unavailable("A legacy phase has no durable action plan. Restore or repair its original receipt before continuing.", Set.of(), VillagerActionResult.EMPTY);
        }
        if (phaseReceipt == null || phaseReceipt.kind() != SceneOperationReceipt.Kind.ENCOUNTER_PHASE)
            return ActionResult.unavailable("The saved phase receipt is missing or belongs to another effect.", Set.of(), VillagerActionResult.EMPTY);
        for (int index = 0; index < run.nextAction(); index++) {
            var action = phase.actions().get(index);
            var receipt = scene.receipts().get(run.operation(encounter, action.id()));
            if (receipt == null || receipt.kind() != EncounterService.phaseActionKind(action)
                    || receipt.state() != SceneOperationReceipt.ReceiptState.COMPLETED || !receipt.actionResult().success())
                return ActionResult.unavailable("The phase cursor has no matching completed action receipt.", Set.of(), VillagerActionResult.EMPTY);
            if (action.type() == EncounterTemplate.PhaseActionType.FACT)
                EncounterPhaseLedger.require(VillagerQuestFacts.get(server.overworld()).hasCampaignFact(factReceipt(encounter, run, action)),
                        "Quest facts are older than the acknowledged phase action. Restore matching fact data.");
        }
        if (run.nextAction() == phase.actions().size()) {
            if (phaseReceipt.state() != SceneOperationReceipt.ReceiptState.COMPLETED)
                return ActionResult.unavailable("The completed phase cursor has no matching completion receipt.", Set.of(), VillagerActionResult.EMPTY);
            return ActionResult.alreadySatisfied("phase already completed", Set.of(), VillagerActionResult.EMPTY);
        }
        if (phaseReceipt.state() == SceneOperationReceipt.ReceiptState.COMPLETED)
            return ActionResult.unavailable("Phase completion conflicts with its saved action cursor.", Set.of(), VillagerActionResult.EMPTY);
        if (scene.state().terminal() || encounter.state() != EncounterInstance.EncounterState.ACTIVE
                && encounter.state() != EncounterInstance.EncounterState.COMPLETED)
            return ActionResult.unavailable("The pending phase no longer belongs to an active encounter and scene.", Set.of(), VillagerActionResult.EMPTY);
        while (run.nextAction() < phase.actions().size()) {
            String authority = com.jvn.villagerretaliation.scene.runtime.SceneExecutionAuthority.campaignIssue(server, scene);
            if (!authority.isBlank()) return ActionResult.unavailable(authority, Set.of(), VillagerActionResult.EMPTY);
            var action = phase.actions().get(run.nextAction());
            var receipt = scene.prepareReceipt(run.operation(encounter, action.id()), EncounterService.phaseActionKind(action), time);
            if (receipt.kind() != EncounterService.phaseActionKind(action))
                return ActionResult.unavailable("The phase action receipt belongs to another effect.", Set.of(), VillagerActionResult.EMPTY);
            if (receipt.state() == SceneOperationReceipt.ReceiptState.COMPLETED)
                return ActionResult.unavailable("Phase action completion conflicts with its saved cursor.", Set.of(), VillagerActionResult.EMPTY);
            if (receipt.state() == SceneOperationReceipt.ReceiptState.REJECTED && receipt.actionResult().failed()) return receipt.actionResult();
            var result = apply(server, data, encounter, scene, run, action, receipt, time);
            receipt.recordAction(result, time);
            if (result.success()) {
                run = run.advance(run.nextAction() + 1);
                encounter.phaseLedger().record(run);
                if (run.nextAction() == phase.actions().size()) phaseReceipt.recordAction(ActionResult.success(VillagerActionResult.success(), Set.of()), time);
            }
            data.changed();
            String issue = EncounterPersistence.checkpoint(server, data, encounter);
            if (!issue.isBlank()) return suspended(scene, issue);
            if (!result.success()) return result;
        }
        return ActionResult.success(VillagerActionResult.success(), Set.of());
    }

    private static ActionResult apply(MinecraftServer server, SceneSavedData data, EncounterInstance encounter, SceneInstance scene,
            EncounterPhaseLedger.Run run, EncounterTemplate.PhaseAction action, SceneOperationReceipt receipt, long time) {
        try {
            return switch (action.type()) {
                case FACT -> {
                    var effect = factReceipt(encounter, run, action);
                    boolean applied = VillagerQuestFacts.get(server.overworld()).applyCampaignFact(server.overworld(), effect, false);
                    yield applied ? ActionResult.success(VillagerActionResult.success(), Set.of())
                            : ActionResult.alreadySatisfied("phase fact already saved", Set.of(), VillagerActionResult.EMPTY);
                }
                case TRANSITION -> {
                    var definition = SceneResources.scene(server, scene.sceneId()).orElse(null);
                    if (definition == null || !definition.steps().containsKey(action.target()))
                        yield ActionResult.unavailable("Phase transition target " + action.target() + " is unavailable.", Set.of(), VillagerActionResult.EMPTY);
                    var record = scene.stepRecords().get(run.step());
                    if (record == null || !scene.currentStep().equals(run.step()))
                        yield ActionResult.unavailable("The phase transition no longer owns the current scene step. Restore matching scene progress.", Set.of(), VillagerActionResult.EMPTY);
                    if (!record.chosenTransition().isBlank() && !record.chosenTransition().equals(action.target()))
                        yield ActionResult.failed("The phase step already chose transition " + record.chosenTransition() + ".", Set.of());
                    record.chooseTransition(action.target());
                    yield ActionResult.success(VillagerActionResult.success(), Set.of());
                }
                case NOTIFICATION, DIALOGUE -> {
                    String error = EncounterService.executePhaseAction(server, data, encounter, scene, action, receipt, time);
                    yield error.isBlank() ? ActionResult.success(VillagerActionResult.success(), Set.of()) : ActionResult.failed(error, Set.of());
                }
            };
        } catch (IOException exception) {
            return suspended(scene, "Campaign phase facts could not be saved. Restore writable world storage to continue: " + exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return ActionResult.unavailable(exception.getMessage(), Set.of(), VillagerActionResult.EMPTY);
        }
    }

    private static QuestFactReceipt factReceipt(EncounterInstance encounter, EncounterPhaseLedger.Run run, EncounterTemplate.PhaseAction action) {
        var scopes = new ArrayList<QuestScopeKey>();
        switch (action.scope()) {
            case WORLD -> scopes.add(QuestScopeKey.WORLD);
            case PLAYER -> run.participants().forEach(id -> scopes.add(QuestScopeKey.player(id)));
            case QUEST -> {
                EncounterPhaseLedger.require(run.quest() != null, "The phase fact has no owning quest.");
                run.participants().forEach(id -> scopes.add(QuestScopeKey.quest(id, run.quest())));
            }
        }
        return new QuestFactReceipt(run.factOperation(encounter, action.id()), run.actionToken(action.id()), scopes,
                action.tag(), action.tag() == null ? action.key() : "", action.tag() == null ? action.value() : "");
    }

    private static EncounterService.Result stop(MinecraftServer server, SceneSavedData data, EncounterInstance encounter, ActionResult result) {
        if (!result.failed()) return waiting(data, encounter, result.message());
        encounter.fail(result.message());
        data.changed();
        String issue = EncounterPersistence.checkpoint(server, data, encounter);
        return issue.isBlank() ? EncounterService.Result.failed(result.message()) : waiting(data, encounter, issue);
    }

    private static ActionResult suspended(SceneInstance scene, String diagnostic) {
        return new ActionResult(ActionStatus.SUSPENDED, diagnostic, Set.of(), VillagerActionResult.EMPTY, scene.id(), ActionResult.SuspensionKind.SCENE, true);
    }

    private static EncounterService.Result waiting(SceneSavedData data, EncounterInstance encounter, String diagnostic) {
        if (encounter.markContentUnresolved(diagnostic)) data.changed();
        return EncounterService.Result.waiting(diagnostic);
    }
}
