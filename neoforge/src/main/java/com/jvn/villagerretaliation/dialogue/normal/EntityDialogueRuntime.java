package com.jvn.villagerretaliation.dialogue.normal;

import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueRuntimeAdapter;
import com.jvn.villagerretaliation.dialogue.DialogueRuntimeContext;
import com.jvn.villagerretaliation.dialogue.DialogueSpeech;
import com.jvn.villagerretaliation.dialogue.NarrativeActors;
import com.jvn.villagerretaliation.scene.SceneContinuationService;
import com.jvn.villagerretaliation.scene.runtime.SceneContinuation;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public class EntityDialogueRuntime implements DialogueRuntimeAdapter {
    public static final EntityDialogueRuntime INSTANCE = new EntityDialogueRuntime();
    private static final Map<net.minecraft.server.MinecraftServer, Integer> CURSORS = new java.util.WeakHashMap<>();

    @Override public boolean forced(DialogueRuntimeContext context) { return false; }
    @Override public DialogueDisposition disposition(DialogueRuntimeContext context) { return DialogueDisposition.NEUTRAL; }
    @Override public boolean pending(DialogueRuntimeContext context) { return SceneContinuationService.hasPendingDialogue(context); }

    @Override
    public SceneContinuationService.RunResult actions(DialogueRuntimeContext context, List<VillagerActionDefinition> actions,
            Map<String, String> replacements, String source, SceneContinuation.DialogueResume resume) {
        return SceneContinuationService.run(context, actions, replacements, () -> flashTracker(context), source, resume);
    }

    @Override
    public void present(DialogueRuntimeContext context, VillagerDialogueService.DialogueResult result, boolean forced) {
        finish(context, result.exchanges(), result.text(), forced);
    }

    @Override
    public void finish(DialogueRuntimeContext context, List<DialogueSpeech> exchanges, String text, boolean forced) {
        if (exchanges.isEmpty() && !text.isBlank()) context.player().sendSystemMessage(Component.literal(text));
        for (var exchange : exchanges) {
            context.player().sendSystemMessage(Component.literal(exchange.name().isBlank() ? exchange.text()
                    : exchange.name() + ": " + exchange.text()));
        }
    }

    @Override
    public void tick(DialogueRuntimeContext context, Optional<VillagerDialogueService.DialogueResult> result) {
        result.ifPresent(dialogue -> present(context, dialogue, forced(context)));
    }

    @Override public void flashTracker(DialogueRuntimeContext context) { }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        if (!NarrativeActors.hasRegistrations()) return;
        var server = event.getServer();
        var keys = DialogueTreeSessionSavedData.get(server.overworld()).keys();
        if (keys.isEmpty()) return;
        int cursor = CURSORS.getOrDefault(server, 0);
        for (int inspected = 0; inspected < Math.min(keys.size(), 64); inspected++) {
            int index = Math.floorMod(cursor + inspected, keys.size());
            CURSORS.put(server, (index + 1) % keys.size());
            var key = keys.get(index);
            var player = server.getPlayerList().getPlayer(key.playerId());
            if (player == null) continue;
            var entity = player.serverLevel().getEntity(key.villagerId());
            if (entity instanceof LivingEntity living) {
                NarrativeActors.context(player.serverLevel(), player, living).ifPresent(DialogueTreeService::tick);
            }
        }
    }
}
