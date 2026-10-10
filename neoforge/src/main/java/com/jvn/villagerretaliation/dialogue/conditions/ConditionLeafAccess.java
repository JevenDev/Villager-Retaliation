package com.jvn.villagerretaliation.dialogue.conditions;

import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.quest.QuestTriggerContext;

@FunctionalInterface
public interface ConditionLeafAccess {
    DialogueCondition.ConditionOutcome evaluate(DialogueCondition condition, QuestTriggerContext trigger);

    default String unavailableReason(DialogueCondition condition, QuestTriggerContext trigger) {
        return "condition cannot be evaluated";
    }
}
