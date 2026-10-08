package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionRegistry;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.runtime.QuestLifecycleService;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

public final class CampaignOutcomeService {
    private CampaignOutcomeService() {
    }

    static Preparation prepare(DialogueContext context, VillagerActionDefinition action) {
        if (context == null || action.questId() == null || action.campaignOutcome() == null) return unavailable("Campaign decision is missing its quest or outcome.");
        String issue = CampaignAuthority.controlIssue(context.player());
        if (!issue.isBlank()) return unavailable(issue);
        var quest = VillagerQuestResources.quest(context.player().server, action.questId()).orElse(null);
        if (quest == null || quest.campaign() == null) return unavailable("The decision must belong to a campaign quest.");
        issue = CampaignQuestAccess.contentIssue(context, quest);
        if (!issue.isBlank()) return unavailable(issue);
        var content = CampaignResources.current(context.player().server).campaigns().get(quest.campaign());
        var reference = action.campaignOutcome();
        var milestone = content.definition().milestones().get(reference.milestone());
        if (milestone == null || !milestone.quest().equals(quest.id()) || !milestone.irreversible()
                || !milestone.outcomes().containsKey(reference.outcome())) return unavailable("The decision does not name an irreversible outcome owned by this quest.");
        var progress = VillagerQuestSavedData.get(context.level()).get(context.player().getUUID(), quest.id());
        var instance = CampaignSavedData.get(context.level()).instances().get(quest.campaign());
        if (progress == null || progress.questRunId() == null || instance == null) return unavailable("Start the campaign quest before resolving its outcome.");
        if (!com.jvn.villagerretaliation.quest.conditions.QuestAvailabilityService.matchesProviderLock(context, quest, progress)) {
            return unavailable("Return to this quest's assigned character to resolve the decision.");
        }
        UUID operation = UUID.nameUUIDFromBytes((instance.instanceId() + "/" + context.player().getUUID() + "/" + quest.id()
                + "/" + progress.questRunId() + "/" + reference.operationId()).getBytes(StandardCharsets.UTF_8));
        var consent = new CampaignOutcomeConsent(quest.id(), quest.campaign(), instance.instanceId(), instance.fingerprint(),
                instance.worldRevision(), progress.questRunId(), progress.currentStage(), progress.campaignStageRevision(), operation, reference);
        String title = content.localization().plainText(context.locale(), milestone.outcomes().get(reference.outcome()).titleKey()).orElse(reference.outcome());
        var prior = instance.outcomes().get(reference.milestone());
        if (prior != null) {
            if (prior.operationId().equals(operation) && prior.outcome().equals(reference.outcome())) return new Preparation(consent, title, false, true, "");
            return unavailable("The shared outcome is already committed: " + prior.title() + ". Continue your current story path.");
        }
        issue = CampaignQuestAccess.executionIssue(context, quest);
        if (!issue.isBlank()) return unavailable(issue);
        var personal = instance.players().get(context.player().getUUID());
        var entered = personal == null ? null : personal.milestones().get(reference.milestone());
        String stage = milestone.stage().isBlank() ? QuestLifecycleService.initialStage(quest) : milestone.stage();
        if (progress.state() != VillagerQuestSavedData.QuestState.ACTIVE || !progress.currentStage().equals(stage)
                || entered == null || entered.route() != CampaignInstance.Route.ORIGINAL || entered.completed()) {
            return unavailable("This decision no longer belongs to your active story stage.");
        }
        var accepted = progress.campaignAcceptance();
        boolean fixed = accepted != null && accepted.instanceId().equals(instance.instanceId())
                && accepted.fingerprint().equals(instance.fingerprint()) && reference.outcome().equals(accepted.fixedOutcomes().get(reference.milestone()));
        return new Preparation(consent, title, !fixed, false, "");
    }

    public static ActionResult execute(DialogueContext context, VillagerActionDefinition action) {
        var capabilities = VillagerActionRegistry.capabilities(action);
        var prepared = prepare(context, action);
        if (!prepared.diagnostic().isBlank()) return ActionResult.unavailable(prepared.diagnostic(), capabilities, VillagerActionResult.EMPTY);
        var consent = prepared.consent();
        if (prepared.applied()) return ActionResult.alreadySatisfied("Campaign outcome already committed", capabilities, result(consent, false));
        if (prepared.requiresConfirmation() && !CampaignQuestAcceptanceService.approvedOutcome(context, consent)) {
            return ActionResult.unavailable("Review and confirm this shared outcome before continuing.", capabilities, VillagerActionResult.EMPTY);
        }
        var content = CampaignResources.current(context.player().server).campaigns().get(consent.campaignId());
        try {
            var committed = CampaignSavedData.get(context.level()).commitWorldOutcome(content, consent.action().milestone(), consent.action().outcome(),
                    consent.operationId(), consent.worldRevision(), CampaignInstance.Origin.PLAYER_DECISION, CampaignDecisionParticipants.forOutcome(context.player().getUUID(), consent), context.level().getGameTime());
            if (committed.status() != CampaignSavedData.Status.APPLIED) {
                String diagnostic = committed.committed() == null ? committed.diagnostic() : "The shared outcome is already committed: " + committed.committed().title();
                return ActionResult.unavailable(diagnostic, capabilities, VillagerActionResult.EMPTY);
            }
            CampaignQuestAcceptanceService.outcomeApplied(consent.operationId());
            return ActionResult.success(result(consent, true), capabilities);
        } catch (IOException exception) {
            LogUtils.getLogger().error("Could not save campaign outcome {} for {}", consent.action().milestone(), consent.campaignId(), exception);
            return ActionResult.failed("The shared outcome could not be saved. Check world storage before retrying.", capabilities);
        }
    }

    static boolean committed(DialogueContext context, CampaignOutcomeConsent consent) {
        var instance = CampaignSavedData.get(context.level()).instances().get(consent.campaignId());
        if (instance == null || !instance.instanceId().equals(consent.instanceId())) return false;
        var outcome = instance.outcomes().get(consent.action().milestone());
        return outcome != null && outcome.operationId().equals(consent.operationId()) && outcome.outcome().equals(consent.action().outcome());
    }

    static CampaignQuestAcceptanceService.ResumeAccess resumeAccess(DialogueContext context, CampaignOutcomeConsent consent) {
        if (committed(context, consent)) return new CampaignQuestAcceptanceService.ResumeAccess(false, "");
        var quest = VillagerQuestResources.quest(context.player().server, consent.questId()).orElse(null);
        String issue = CampaignQuestAccess.contentIssue(context.level(), quest,
                VillagerQuestSavedData.get(context.level()).get(context.player().getUUID(), consent.questId()));
        if (!issue.isBlank() || quest == null || !consent.campaignId().equals(quest.campaign())) {
            return new CampaignQuestAcceptanceService.ResumeAccess(true, issue.isBlank() ? "The approved decision's campaign ownership changed." : issue);
        }
        var instance = CampaignSavedData.get(context.level()).instances().get(consent.campaignId());
        if (instance == null || !consent.instanceId().equals(instance.instanceId()) || !consent.fingerprint().equals(instance.fingerprint())) {
            return new CampaignQuestAcceptanceService.ResumeAccess(true, "Restore the approved decision's campaign content and instance.");
        }
        if (consent.worldRevision() != instance.worldRevision()) {
            var outcome = instance.outcomes().get(consent.action().milestone());
            return new CampaignQuestAcceptanceService.ResumeAccess(false, "The shared story changed. "
                    + (outcome == null ? "" : "Committed outcome: " + outcome.title() + ". ") + "Review the decision again.");
        }
        var progress = VillagerQuestSavedData.get(context.level()).get(context.player().getUUID(), consent.questId());
        if (progress == null || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE
                || !consent.questRunId().equals(progress.questRunId()) || !consent.stage().equals(progress.currentStage())
                || consent.stageRevision() != progress.campaignStageRevision()) {
            return new CampaignQuestAcceptanceService.ResumeAccess(false, "Your story stage changed. Review the decision again.");
        }
        return new CampaignQuestAcceptanceService.ResumeAccess(false, "");
    }

    private static VillagerActionResult result(CampaignOutcomeConsent consent, boolean applied) {
        return new VillagerActionResult(applied, "", "", Map.of("campaign_outcome", consent.action().outcome(),
                "campaign_milestone", consent.action().milestone(), "campaign_operation", consent.operationId().toString()), false);
    }

    private static Preparation unavailable(String diagnostic) { return new Preparation(null, "", false, false, diagnostic); }

    record Preparation(CampaignOutcomeConsent consent, String title, boolean requiresConfirmation, boolean applied, String diagnostic) {
    }
}
