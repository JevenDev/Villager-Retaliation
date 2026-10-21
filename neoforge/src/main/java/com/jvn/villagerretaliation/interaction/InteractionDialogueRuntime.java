package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.dialogue.DialogueRuntimeContext;
import com.jvn.villagerretaliation.dialogue.DialogueSpeech;
import com.jvn.villagerretaliation.dialogue.normal.EntityDialogueRuntime;
import com.jvn.villagerretaliation.dialogue.normal.VillagerDialogueService.DialogueResult;
import java.util.List;
import java.util.Optional;

public final class InteractionDialogueRuntime extends EntityDialogueRuntime {
    public static final InteractionDialogueRuntime INSTANCE = new InteractionDialogueRuntime();

    private InteractionDialogueRuntime() { }

    @Override public boolean forced(DialogueRuntimeContext context) {
        return context.speaker() != null && InteractionSessions.isForced(context.player(), context.speaker());
    }

    @Override public void present(DialogueRuntimeContext context, DialogueResult result, boolean forced) {
        if (!InteractionService.deliver(context, result, forced)) super.present(context, result, forced);
    }

    @Override public void finish(DialogueRuntimeContext context, List<DialogueSpeech> exchanges, String text, boolean forced) {
        if (!InteractionService.finish(context, exchanges, text, forced)) super.finish(context, exchanges, text, forced);
    }

    @Override public void tick(DialogueRuntimeContext context, Optional<DialogueResult> result) {
        if (result.isPresent()) present(context, result.orElseThrow(), forced(context));
        else InteractionService.refresh(context);
    }

    @Override public void flashTracker(DialogueRuntimeContext context) { InteractionService.flashTracker(context); }
}
