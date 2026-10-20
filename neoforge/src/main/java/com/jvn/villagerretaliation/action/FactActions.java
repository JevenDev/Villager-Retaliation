package com.jvn.villagerretaliation.action;

import com.jvn.villagerretaliation.dialogue.DialogueRuntimeContext;
import com.jvn.villagerretaliation.dialogue.DialogueTemplates;
import com.jvn.villagerretaliation.quest.QuestFactScope;
import com.jvn.villagerretaliation.quest.runtime.QuestFactStore;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public final class FactActions {
    private FactActions() { }

    public static VillagerActionResult execute(DialogueRuntimeContext context, VillagerActionDefinition action,
            Map<String, String> replacements, QuestFactStore facts, StageSetter stages) {
        String scope = action.factScope().scopeKey(context, action.questId());
        if (scope.isBlank()) return VillagerActionResult.EMPTY;
        String value = "";
        int counter = 0;
        boolean changed;
        switch (action.kind()) {
            case SET_TAG -> changed = facts.setTag(scope, action.factTag());
            case CLEAR_TAG -> changed = facts.clearTag(scope, action.factTag());
            case SET_VARIABLE -> {
                value = DialogueTemplates.resolveTemplate(action.factValue(), replacements);
                if ("stage".equals(action.factKey()) && action.factScope() == QuestFactScope.QUEST) {
                    String source = action.questId() != null && action.questId().toString().equals(replacements.get("execution_quest"))
                            ? replacements.getOrDefault("execution_stage", "") : "";
                    changed = stages != null && stages.set(context, action.questId(), value, source);
                } else changed = facts.setVariable(scope, action.factKey(), value);
            }
            case COUNTER -> {
                counter = facts.addCounter(scope, action.factKey(), action.amount());
                value = Integer.toString(counter);
                changed = true;
            }
            default -> { return VillagerActionResult.EMPTY; }
        }
        return changed ? result(action, replacements, scope, value, counter) : VillagerActionResult.EMPTY;
    }

    public static VillagerActionResult result(VillagerActionDefinition action, Map<String, String> inheritedReplacements,
            String scopeKey, String value, int counterValue) {
        if (scopeKey == null || scopeKey.isBlank()) return VillagerActionResult.EMPTY;
        Map<String, String> replacements = new LinkedHashMap<>(inheritedReplacements);
        replacements.put("quest_fact_scope", action.factScope().name().toLowerCase(java.util.Locale.ROOT));
        replacements.put("quest_fact_scope_key", scopeKey);
        replacements.put("quest_fact_tag", action.factTag() == null ? "" : action.factTag().toString());
        replacements.put("quest_fact_key", action.factKey());
        replacements.put("quest_fact_value", value == null ? "" : value);
        replacements.put("quest_fact_counter", Integer.toString(counterValue));
        if ("stage".equals(action.factKey())) {
            replacements.put("quest_stage", value == null ? "" : value);
            replacements.put("current_stage", value == null ? "" : value);
        }
        return new VillagerActionResult(true, "", "", replacements, action.flashTracker());
    }

    @FunctionalInterface
    public interface StageSetter {
        boolean set(DialogueRuntimeContext context, ResourceLocation quest, String stage, String source);
    }
}
