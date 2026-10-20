package com.jvn.villagerretaliation.action;

import com.jvn.villagerretaliation.dialogue.DialogueContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;

public final class ActionSequenceRunner {
    private ActionSequenceRunner() {
    }

    public static Result run(DialogueContext context, List<VillagerActionDefinition> actions,
            int firstAction, Map<String, String> inheritedReplacements,
            Runnable trackerFlashHandler, BiConsumer<Integer, ActionResult> observer) {
        return run(context, actions, firstAction, inheritedReplacements, trackerFlashHandler, observer, null);
    }

    public static Result run(DialogueContext context, List<VillagerActionDefinition> actions,
            int firstAction, Map<String, String> inheritedReplacements, Runnable trackerFlashHandler,
            BiConsumer<Integer, ActionResult> observer, IntConsumer beforeAction) {
        return run(context, actions, firstAction, inheritedReplacements, trackerFlashHandler, observer, beforeAction,
                (action, replacements) -> VillagerActionRegistry.execute(context, action, replacements));
    }

    public static Result run(DialogueContext context, List<VillagerActionDefinition> actions,
            int firstAction, Map<String, String> inheritedReplacements, Runnable trackerFlashHandler,
            BiConsumer<Integer, ActionResult> observer, IntConsumer beforeAction,
            java.util.function.BiFunction<VillagerActionDefinition, Map<String, String>, ActionResult> executor) {
        return run(actions, firstAction, inheritedReplacements, trackerFlashHandler, observer, beforeAction, executor);
    }

    public static Result run(List<VillagerActionDefinition> actions,
            int firstAction, Map<String, String> inheritedReplacements, Runnable trackerFlashHandler,
            BiConsumer<Integer, ActionResult> observer, IntConsumer beforeAction,
            java.util.function.BiFunction<VillagerActionDefinition, Map<String, String>, ActionResult> executor) {
        List<VillagerActionDefinition> sequence = actions == null ? List.of() : actions;
        if (firstAction < 0 || firstAction > sequence.size()) {
            return new Result(ActionResult.failed("saved action cursor is outside the sequence", Set.of()), firstAction);
        }
        Map<String, String> replacements = new LinkedHashMap<>();
        if (inheritedReplacements != null) replacements.putAll(inheritedReplacements);
        List<String> text = new ArrayList<>();
        Set<ActionCapability> capabilities = new LinkedHashSet<>();
        String lineId = "";
        boolean ran = false;
        boolean flash = false;
        for (int index = firstAction; index < sequence.size(); index++) {
            VillagerActionDefinition action = sequence.get(index);
            try { if (beforeAction != null) beforeAction.accept(index); }
            catch (Pause pause) {
                return paused(pause, index, ran, lineId, text, replacements, flash, capabilities);
            }
            ActionResult outcome = executor.apply(action, replacements);
            VillagerActionResult result = outcome.legacyResult();
            replacements.putAll(result.replacements());
            capabilities.addAll(outcome.capabilities());
            if (!result.text().isBlank()) text.add(result.text());
            if (!result.lineId().isBlank()) lineId = result.lineId();
            if (result.flashTracker() && trackerFlashHandler != null) trackerFlashHandler.run();
            ran |= result.ran();
            flash |= result.flashTracker();
            int next = outcome.suspended() && outcome.retryOnResume() ? index : index + 1;
            try { if (observer != null) observer.accept(next, outcome); }
            catch (Pause pause) {
                return paused(pause, next, ran, lineId, text, replacements, flash, capabilities);
            }
            if (outcome.suspended() || (action.required() && !outcome.success())) {
                return new Result(new ActionResult(outcome.status(), outcome.message(), capabilities,
                        new VillagerActionResult(ran, lineId, String.join(" ", text), replacements, flash),
                        outcome.suspensionId(), outcome.suspensionKind(), outcome.retryOnResume()), next);
            }
        }
        VillagerActionResult result = new VillagerActionResult(
                ran, lineId, String.join(" ", text), replacements, flash);
        return new Result(ran ? ActionResult.success(result, capabilities)
                : ActionResult.alreadySatisfied("sequence complete", capabilities, result), sequence.size());
    }

    public static Result run(com.jvn.villagerretaliation.dialogue.DialogueRuntimeContext context,
            List<VillagerActionDefinition> actions, int firstAction, Map<String, String> replacements,
            Runnable trackerFlashHandler, BiConsumer<Integer, ActionResult> observer, IntConsumer beforeAction) {
        return run(actions, firstAction, replacements, trackerFlashHandler, observer, beforeAction,
                (action, inherited) -> context.actions().execute(context, action, inherited));
    }

    private static Result paused(Pause pause, int next, boolean ran, String lineId, List<String> text,
            Map<String, String> replacements, boolean flash, Set<ActionCapability> capabilities) {
        var result = pause.outcome;
        return new Result(new ActionResult(result.status(), result.message(), capabilities,
                new VillagerActionResult(ran, lineId, String.join(" ", text), replacements, flash),
                result.suspensionId(), result.suspensionKind(), result.retryOnResume()), next);
    }

    public static final class Pause extends RuntimeException {
        private final ActionResult outcome;
        public Pause(ActionResult outcome) {
            if (outcome == null || !outcome.suspended()) throw new IllegalArgumentException("an action pause requires a suspension result");
            this.outcome = outcome;
        }
    }

    public record Result(ActionResult outcome, int nextActionIndex) { }
}
