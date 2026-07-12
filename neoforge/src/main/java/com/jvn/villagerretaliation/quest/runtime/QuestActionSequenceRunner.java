package com.jvn.villagerretaliation.quest.runtime;

import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.scene.SceneContinuationService;
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

        return SceneContinuationService.run(context, actions, baseReplacements, trackerFlashHandler,
                "quest_action_sequence").ran();
    }
}
