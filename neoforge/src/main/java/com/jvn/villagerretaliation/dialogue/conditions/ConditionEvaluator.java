package com.jvn.villagerretaliation.dialogue.conditions;

import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionEvaluationTrace;
import com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome;
import com.jvn.villagerretaliation.quest.QuestTriggerContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ConditionEvaluator {
    private ConditionEvaluator() { }

    public static ConditionOutcome all(ConditionContext context, QuestTriggerContext trigger,
            List<DialogueCondition> conditions) {
        boolean unknown = false;
        if (conditions == null) return ConditionOutcome.MATCHED;
        for (DialogueCondition condition : conditions) {
            ConditionOutcome outcome = evaluate(context, trigger, condition);
            if (outcome == ConditionOutcome.UNMET) return ConditionOutcome.UNMET;
            unknown |= outcome == ConditionOutcome.UNKNOWN;
        }
        return unknown ? ConditionOutcome.UNKNOWN : ConditionOutcome.MATCHED;
    }

    public static ConditionOutcome evaluate(ConditionContext context, QuestTriggerContext trigger,
            DialogueCondition condition) {
        if (condition == null) return ConditionOutcome.UNKNOWN;
        if (condition instanceof DialogueCondition.Invalid) return ConditionOutcome.UNMET;
        if (condition instanceof DialogueCondition.AllOf all) return all(context, trigger, all.conditions());
        if (condition instanceof DialogueCondition.AnyOf any) {
            boolean unknown = false;
            for (DialogueCondition child : any.conditions()) {
                ConditionOutcome outcome = evaluate(context, trigger, child);
                if (outcome == ConditionOutcome.MATCHED) return ConditionOutcome.MATCHED;
                unknown |= outcome == ConditionOutcome.UNKNOWN;
            }
            return unknown ? ConditionOutcome.UNKNOWN : ConditionOutcome.UNMET;
        }
        if (condition instanceof DialogueCondition.Not not) {
            if (not.condition() instanceof DialogueCondition.Invalid) return ConditionOutcome.UNMET;
            return negate(evaluate(context, trigger, not.condition()));
        }
        if (context == null && trigger != null && condition instanceof DialogueCondition.TriggerPayload payload)
            return payload.matches(null, trigger) ? ConditionOutcome.MATCHED : ConditionOutcome.UNMET;
        return context == null ? ConditionOutcome.UNKNOWN : context.queries().evaluate(condition, trigger);
    }

    public static ConditionEvaluationTrace trace(ConditionContext context, QuestTriggerContext trigger,
            DialogueCondition condition) {
        if (condition == null) return new ConditionEvaluationTrace("unknown", ConditionOutcome.UNKNOWN,
                "condition is missing", List.of());
        if (condition instanceof DialogueCondition.Invalid invalid) return new ConditionEvaluationTrace(
                "invalid", ConditionOutcome.UNMET, invalid.reason(), List.of());
        if (condition instanceof DialogueCondition.Not not) {
            ConditionEvaluationTrace child = trace(context, trigger, not.condition());
            ConditionOutcome outcome = not.condition() instanceof DialogueCondition.Invalid
                    ? ConditionOutcome.UNMET : negate(child.outcome());
            return new ConditionEvaluationTrace("not", outcome,
                    "negated " + child.outcome().name().toLowerCase(Locale.ROOT), List.of(child));
        }
        if (condition instanceof DialogueCondition.AllOf || condition instanceof DialogueCondition.AnyOf) {
            boolean all = condition instanceof DialogueCondition.AllOf;
            List<DialogueCondition> conditions = all ? ((DialogueCondition.AllOf) condition).conditions()
                    : ((DialogueCondition.AnyOf) condition).conditions();
            List<ConditionEvaluationTrace> children = traceAll(context, trigger, conditions);
            ConditionOutcome decisive = all ? ConditionOutcome.UNMET : ConditionOutcome.MATCHED;
            ConditionOutcome outcome = children.stream().anyMatch(child -> child.outcome() == decisive) ? decisive
                    : children.stream().anyMatch(child -> child.outcome() == ConditionOutcome.UNKNOWN) ? ConditionOutcome.UNKNOWN
                    : all ? ConditionOutcome.MATCHED : ConditionOutcome.UNMET;
            String message = all
                    ? outcome == ConditionOutcome.MATCHED ? "all conditions matched" : "one or more conditions did not match"
                    : outcome == ConditionOutcome.MATCHED ? "at least one condition matched" : "no conditions matched";
            return new ConditionEvaluationTrace(all ? "all" : "any", outcome, message, children);
        }
        ConditionOutcome outcome = evaluate(context, trigger, condition);
        String message = outcome == ConditionOutcome.UNKNOWN
                ? context == null ? "live context unavailable" : context.queries().unavailableReason(condition, trigger)
                : outcome == ConditionOutcome.MATCHED ? "matched" : "condition returned false";
        return new ConditionEvaluationTrace(DialogueCondition.canonicalTypeId(condition), outcome, message, List.of());
    }

    public static List<ConditionEvaluationTrace> traceAll(ConditionContext context, QuestTriggerContext trigger,
            List<DialogueCondition> conditions) {
        if (conditions == null || conditions.isEmpty()) return List.of();
        List<ConditionEvaluationTrace> traces = new ArrayList<>();
        for (DialogueCondition condition : conditions) traces.add(trace(context, trigger, condition));
        return List.copyOf(traces);
    }

    public static String unavailableReason(ConditionEvaluationTrace trace) {
        if (trace.outcome() != ConditionOutcome.UNKNOWN) return "";
        for (ConditionEvaluationTrace child : trace.children()) {
            String reason = unavailableReason(child);
            if (!reason.isBlank()) return reason;
        }
        return trace.message();
    }

    private static ConditionOutcome negate(ConditionOutcome outcome) {
        return switch (outcome) {
            case MATCHED -> ConditionOutcome.UNMET;
            case UNMET -> ConditionOutcome.MATCHED;
            case UNKNOWN -> ConditionOutcome.UNKNOWN;
        };
    }
}
