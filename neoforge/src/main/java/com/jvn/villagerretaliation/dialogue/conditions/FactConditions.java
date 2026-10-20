package com.jvn.villagerretaliation.dialogue.conditions;

import com.jvn.villagerretaliation.dialogue.DialogueCondition.QuestFact;
import com.jvn.villagerretaliation.quest.runtime.QuestFactStore;

public final class FactConditions {
    private FactConditions() { }

    public static boolean matches(QuestFact condition, QuestFactStore facts, String scope) {
        if (scope == null || scope.isBlank()) return false;
        if (!condition.tags().isEmpty() && condition.tags().stream().noneMatch(tag -> facts.hasTag(scope, tag))) return false;
        if (condition.key() == null || condition.key().isBlank()) return !condition.tags().isEmpty();
        var variable = facts.variable(scope, condition.key());
        if (!condition.values().isEmpty() && variable.stream().noneMatch(condition.values()::contains)) return false;
        int counter = facts.counter(scope, condition.key());
        if (condition.min() != null && counter < condition.min()) return false;
        if (condition.max() != null && counter > condition.max()) return false;
        return !condition.values().isEmpty() || condition.min() != null || condition.max() != null || variable.isPresent() || counter != 0;
    }
}
