package com.jvn.villagerretaliation.quest.runtime;

import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionExecutor;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class QuestActionSequenceRunner {
    private QuestActionSequenceRunner() {
    }

    public static boolean run(
            DialogueContext context,
            List<VillagerActionDefinition> actions,
            Map<String, String> baseReplacements,
            Runnable trackerFlashHandler) {
        if (actions == null || actions.isEmpty()) {
            return false;
        }

        boolean ranAction = false;
        Map<String, String> replacements = baseReplacements == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(baseReplacements);
        for (VillagerActionDefinition action : actions) {
            VillagerActionResult result = VillagerActionExecutor.execute(context, action, replacements);
            replacements.putAll(result.replacements());
            if (result.flashTracker() && trackerFlashHandler != null) {
                trackerFlashHandler.run();
            }
            ranAction |= result.ran();
        }
        return ranAction;
    }
}
