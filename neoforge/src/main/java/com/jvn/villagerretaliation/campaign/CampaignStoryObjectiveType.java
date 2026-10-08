package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationObjectiveEvidence;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationResources;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSavedData;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveDebugState;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveEvaluationContext;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveResult;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveType;
import java.util.Optional;

public final class CampaignStoryObjectiveType implements QuestObjectiveType<CampaignStoryObjective> {
    private final QuestDefinition.ObjectiveType type;

    public CampaignStoryObjectiveType(QuestDefinition.ObjectiveType type) { this.type = type; }

    @Override public Optional<JsonObject> structuralSchema() { return Optional.of(CampaignStoryObjective.schema(type)); }

    @Override public Optional<String> validationError(QuestDefinition.Objective objective) {
        return objective.story() == null || objective.story().type() != type
                ? Optional.of("Campaign objective requires its typed story definition.") : Optional.empty();
    }

    @Override public QuestObjectiveResult evaluate(QuestObjectiveEvaluationContext context, QuestDefinition.Objective objective) {
        if (validationError(objective).isPresent()) return incomplete(validationError(objective).orElseThrow());
        if (context == null || context.player() == null || context.level() == null || context.definition() == null
                || context.definition().campaign() == null || context.progress() == null) return incomplete("Campaign quest context is unavailable.");
        String issue = CampaignQuestAccess.executionIssue(context.level(), context.player().getUUID(), context.definition(), context.progress());
        if (!issue.isBlank()) return incomplete(issue);
        if (!objective.conditions().isEmpty() && !context.matchesConditions(objective)) return incomplete("Story objective conditions are not met.");
        if (objective.story() instanceof CampaignStoryObjective.Outcome outcome) {
            var result = new CampaignOutcomePredicate(context.definition().campaign(), outcome.milestone(), outcome.outcome(), outcome.scope())
                    .evaluate(context.level(), context.player().getUUID());
            return result.matches() ? QuestObjectiveResult.complete("Story outcome resolved.")
                    : incomplete(result.diagnostic().isBlank() ? "The named story outcome has not been resolved in this scope." : result.diagnostic());
        }
        if (objective.story() instanceof CampaignStoryObjective.Follow follow) {
            var data = CampaignFollowSavedData.get(context.level());
            if (!data.diagnostic().isBlank()) return incomplete(data.diagnostic());
            return CampaignFollowService.completed(context, objective.id(), follow) ? QuestObjectiveResult.complete("Campaign character followed.")
                    : incomplete("Accompany the bound character through the authored journey to its destination.");
        }
        if (objective.story() instanceof CampaignStoryObjective.Investigate investigation) {
            var instance = CampaignSavedData.get(context.level()).instances().get(context.definition().campaign());
            if (instance == null || context.progress().questRunId() == null) return incomplete("The original campaign quest run is unavailable.");
            var data = CampaignEvidenceSavedData.get(context.level());
            if (!data.diagnostic().isBlank()) return incomplete(data.diagnostic());
            var key = new CampaignInvestigationReceipt.Key(context.definition().campaign(), instance.instanceId(), instance.fingerprint(),
                    context.player().getUUID(), context.definition().id(), context.progress().questRunId(), objective.id(), investigation.evidence());
            if (data.investigated(key)) return QuestObjectiveResult.complete("Evidence investigated.");
            var source = CampaignEvidenceService.resolve(context.level().getServer(), context.definition().campaign(), investigation.evidence());
            return incomplete(source.available() ? "Investigate the bound campaign evidence source." : source.diagnostic());
        }
        var speak = (CampaignStoryObjective.Speak) objective.story();
        var campaign = CampaignResources.current(context.level().getServer()).campaigns().get(context.definition().campaign());
        var instance = CampaignSavedData.get(context.level()).instances().get(context.definition().campaign());
        var conversation = ConversationResources.conversation(context.level().getServer(), speak.conversation()).orElse(null);
        if (campaign == null || instance == null || conversation == null || context.progress().questRunId() == null)
            return incomplete("The campaign conversation or original quest run is unavailable.");
        var data = ConversationSavedData.get(context.level());
        if (!data.diagnostic().isBlank()) return incomplete(data.diagnostic());
        var key = new ConversationObjectiveEvidence.Key(context.definition().campaign(), instance.instanceId(), instance.fingerprint(),
                context.player().getUUID(), context.definition().id(), context.progress().questRunId(), objective.id(),
                speak.conversation(), conversation.fingerprint(), speak.node(), speak.role());
        return data.hasObjectiveEvidence(key) ? QuestObjectiveResult.complete("Campaign character heard.")
                : incomplete("Speak with the bound campaign character at the authored conversation node.");
    }

    @Override public QuestObjectiveDebugState debugState(QuestObjectiveEvaluationContext context, QuestDefinition.Objective objective,
            QuestObjectiveResult result) { return new QuestObjectiveDebugState(result.complete() ? 1 : 0, result.message()); }

    private static QuestObjectiveResult incomplete(String message) { return QuestObjectiveResult.incomplete(0.0F, message); }
}
