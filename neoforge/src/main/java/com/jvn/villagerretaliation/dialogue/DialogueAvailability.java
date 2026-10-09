package com.jvn.villagerretaliation.dialogue;

import java.util.List;

public final class DialogueAvailability {
    private DialogueAvailability() { }

    public static String reason(DialogueContext context, List<DialogueCondition> conditions) {
        for (var condition : conditions) {
            String issue = condition.unavailableReason(context);
            if (!issue.isBlank()) return issue;
            if (condition.matches(context)) continue;
            if (condition instanceof DialogueCondition.AllOf all) return reason(context, all.conditions());
            if (condition instanceof DialogueCondition.AnyOf) return "Meet at least one of this response's requirements.";
            if (condition instanceof DialogueCondition.Reputation reputation) {
                if (reputation.minReputation() != null) return "Requires reputation of at least " + reputation.minReputation() + ".";
                if (reputation.maxReputation() != null) return "Requires reputation of at most " + reputation.maxReputation() + ".";
                return "Requires a different level of trust.";
            }
            if (condition instanceof DialogueCondition.PlayerItem) return "Requires the requested item.";
            if (condition instanceof DialogueCondition.Quest) return "Requires further quest progress.";
            if (condition instanceof DialogueCondition.CampaignEvidence) return "Discover the required evidence first.";
            if (condition instanceof DialogueCondition.CampaignStanding) return "Requires a different relationship or faction standing.";
            if (condition instanceof DialogueCondition.CampaignCompanion) return "Requires the appropriate companion nearby.";
            return "This response's requirements are not currently met.";
        }
        return "";
    }
}
