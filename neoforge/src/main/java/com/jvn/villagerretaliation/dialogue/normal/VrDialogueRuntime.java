package com.jvn.villagerretaliation.dialogue.normal;

import static com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService.activeOptions;
import static com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService.leaveActiveSession;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.DialogueRuntimeContext;
import com.jvn.villagerretaliation.dialogue.DialogueSpeech;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionScreenOpener;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import com.jvn.villagerretaliation.scene.SceneContinuationService;
import com.jvn.villagerretaliation.scene.runtime.SceneContinuation;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class VrDialogueRuntime extends EntityDialogueRuntime {
    public static final VrDialogueRuntime INSTANCE = new VrDialogueRuntime();
    private VrDialogueRuntime() { }

    private static DialogueContext context(DialogueRuntimeContext runtime) {
        if (!(runtime instanceof DialogueContext context)) throw new IllegalArgumentException("Villager dialogue context required");
        return context;
    }

    @Override public boolean forced(DialogueRuntimeContext runtime) {
        var context = context(runtime);
        return VillagerConversationService.isForced(context.player(), context.villager());
    }

    @Override public DialogueDisposition disposition(DialogueRuntimeContext runtime) {
        return VillagerDialogueService.moodFor(context(runtime));
    }

    @Override public boolean pending(DialogueRuntimeContext runtime) {
        return SceneContinuationService.hasPendingDialogue(context(runtime));
    }

    @Override public SceneContinuationService.RunResult actions(DialogueRuntimeContext runtime,
            List<VillagerActionDefinition> actions, Map<String, String> replacements, String source, SceneContinuation.DialogueResume resume) {
        var context = context(runtime);
        return SceneContinuationService.run(context, actions, replacements,
                () -> VillagerQuestService.flashTracker(context.player(), true), source, resume);
    }

    @Override public void flashTracker(DialogueRuntimeContext runtime) {
        VillagerQuestService.flashTracker(runtime.player(), true);
    }

    @Override public void present(DialogueRuntimeContext runtime, VillagerDialogueService.DialogueResult result, boolean forced) {
        var context = context(runtime);
        if (forced) {
            List<DialogueOptionDefinition> options = activeOptions(context).orElse(List.of());
            if (options.stream().noneMatch(DialogueOptionDefinition::available)) options = List.of();
            if (options.isEmpty()) {
                if (result.exchanges().isEmpty() && !result.text().isBlank())
                    VillagerInteractionService.sendVillagerNotice(context.player(), context.villager(), result.text());
            } else if (!VillagerInteractionService.openForcedDialogue(context.player(), context.villager(), result.text(),
                    options, options.stream().anyMatch(DialogueOptionDefinition::forceCameraTowardsVillager))) {
                leaveActiveSession(context);
            }
            if (!result.exchanges().isEmpty()) VillagerInteractionService.sendDialogueExchanges(context.player(), context.villager(), result.exchanges());
            if (options.isEmpty()) VillagerConversationService.endForPlayer(context.player(), true, !result.exchanges().isEmpty());
        } else if (VillagerConversationService.validate(context.player(), context.villager())
                && !VillagerConversationService.isForced(context.player(), context.villager())) {
            VillagerInteractionScreenOpener.refreshNormal(context.player(), context.villager());
            if (!result.exchanges().isEmpty()) VillagerInteractionService.sendDialogueExchanges(context.player(), context.villager(), result.exchanges());
            else VillagerInteractionService.sendPersonalVillagerChat(context.player(), context.villager(), result.text(), result.textSegments());
        }
    }

    @Override public void finish(DialogueRuntimeContext runtime, List<DialogueSpeech> exchanges, String text, boolean forced) {
        var context = context(runtime);
        if (!exchanges.isEmpty()) VillagerInteractionService.sendDialogueExchanges(context.player(), context.villager(), exchanges);
        else if (!text.isBlank()) VillagerInteractionService.sendPersonalVillagerChat(context.player(), context.villager(), text);
        if (forced) VillagerConversationService.endForPlayer(context.player(), true, !exchanges.isEmpty());
    }

    @Override public void tick(DialogueRuntimeContext runtime, Optional<VillagerDialogueService.DialogueResult> result) {
        var context = context(runtime);
        if (result.isEmpty()) {
            VillagerInteractionService.sendForcedDialogueReputation(context.player(), context.villager(),
                    activeOptions(context).orElse(List.of()));
            return;
        }
        boolean forced = VillagerConversationService.isForced(context.player(), context.villager());
        var options = activeOptions(context).orElse(List.of());
        if (forced) VillagerInteractionService.sendForcedDialogueReputation(context.player(), context.villager(), options);
        else VillagerInteractionScreenOpener.refreshNormal(context.player(), context.villager());
        if (!result.get().exchanges().isEmpty()) VillagerInteractionService.sendDialogueExchanges(
                context.player(), context.villager(), result.get().exchanges());
        else if (!result.get().text().isBlank()) VillagerInteractionService.sendPersonalVillagerChat(
                context.player(), context.villager(), result.get().text());
        if (forced && options.stream().noneMatch(DialogueOptionDefinition::available))
            VillagerConversationService.endForPlayer(context.player(), true, !result.get().exchanges().isEmpty());
    }
}
