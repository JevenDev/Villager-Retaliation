package com.jvn.villagerretaliation.action;

import com.jvn.villagerretaliation.dialogue.DialogueContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public final class ActionSequenceRunner {
    private ActionSequenceRunner() {
    }

    public static Result run(DialogueContext context, List<VillagerActionDefinition> actions,
            int firstAction, Map<String, String> inheritedReplacements,
            Runnable trackerFlashHandler, BiConsumer<Integer, ActionResult> observer) {
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
            ActionResult outcome = VillagerActionRegistry.execute(context, action, replacements);
            VillagerActionResult result = outcome.legacyResult();
            replacements.putAll(result.replacements());
            capabilities.addAll(outcome.capabilities());
            if (!result.text().isBlank()) text.add(result.text());
            if (!result.lineId().isBlank()) lineId = result.lineId();
            if (result.flashTracker() && trackerFlashHandler != null) trackerFlashHandler.run();
            ran |= result.ran();
            flash |= result.flashTracker();
            if (observer != null) observer.accept(index + 1, outcome);
            if (outcome.suspended() || (action.required() && !outcome.success())) {
                return new Result(new ActionResult(outcome.status(), outcome.message(), capabilities,
                        new VillagerActionResult(ran, lineId, String.join(" ", text), replacements, flash),
                        outcome.suspensionId()), index + 1);
            }
        }
        VillagerActionResult result = new VillagerActionResult(
                ran, lineId, String.join(" ", text), replacements, flash);
        return new Result(ran ? ActionResult.success(result, capabilities)
                : ActionResult.alreadySatisfied("sequence complete", capabilities, result), sequence.size());
    }

    public record Result(ActionResult outcome, int nextActionIndex) {
    }
}
