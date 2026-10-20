package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueDisposition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.normal.VillagerDialogueService.DialogueResult;
import com.jvn.villagerretaliation.scene.SceneContinuationService;
import com.jvn.villagerretaliation.scene.runtime.SceneContinuation;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface DialogueRuntimeAdapter {
    boolean forced(DialogueRuntimeContext context);
    DialogueDisposition disposition(DialogueRuntimeContext context);
    boolean pending(DialogueRuntimeContext context);
    SceneContinuationService.RunResult actions(DialogueRuntimeContext context, List<VillagerActionDefinition> actions,
            Map<String, String> replacements, String source, SceneContinuation.DialogueResume resume);
    void present(DialogueRuntimeContext context, DialogueResult result, boolean forced);
    void finish(DialogueRuntimeContext context, List<DialogueSpeech> exchanges, String text, boolean forced);
    void tick(DialogueRuntimeContext context, Optional<DialogueResult> result);
    void flashTracker(DialogueRuntimeContext context);
}
