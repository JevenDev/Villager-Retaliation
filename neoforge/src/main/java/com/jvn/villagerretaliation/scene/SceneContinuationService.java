package com.jvn.villagerretaliation.scene;

import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionExecutor;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneContinuation;
import com.jvn.villagerretaliation.scene.runtime.SceneInstance;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

public final class SceneContinuationService {
    private static final int MAX_CONTINUATIONS_PER_TICK = 16;

    private SceneContinuationService() {
    }

    public static RunResult run(
            DialogueContext context,
            List<VillagerActionDefinition> actions,
            Map<String, String> baseReplacements,
            Runnable trackerFlashHandler,
            String sourcePointer) {
        if (context == null || actions == null || actions.isEmpty()) return new RunResult(false, false);
        Map<String, String> replacements = baseReplacements == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(baseReplacements);
        boolean ran = false;
        for (int index = 0; index < actions.size(); index++) {
            VillagerActionDefinition action = actions.get(index);
            if (action.kind() == VillagerActionDefinition.Kind.START_SCENE && action.waitForScene()) {
                SceneLaunchService.LaunchResult launch = SceneLaunchService.launch(context, action);
                if (!launch.accepted()) {
                    if (action.required()) return new RunResult(ran, false);
                    continue;
                }
                SceneSavedData data = SceneSavedData.get(context.level());
                SceneInstance scene = data.get(launch.instanceId()).orElse(null);
                if (scene == null) {
                    if (action.required()) return new RunResult(ran, false);
                    continue;
                }
                int nextActionIndex = index + 1;
                if (nextActionIndex < actions.size()
                        && actions.get(nextActionIndex).kind() == VillagerActionDefinition.Kind.QUEST_TRANSITION) {
                    VillagerActionResult transition = VillagerActionExecutor.execute(
                            context, actions.get(nextActionIndex), replacements);
                    replacements.putAll(transition.replacements());
                    if (transition.flashTracker() && trackerFlashHandler != null) trackerFlashHandler.run();
                    ran |= transition.ran();
                    nextActionIndex++;
                }
                data.suspendContinuation(scene, context.player().getUUID(), context.villager().getUUID(),
                        pointer(sourcePointer, action, index), actions, nextActionIndex, replacements);
                data.changed();
                return new RunResult(true, true);
            }
            VillagerActionResult result = VillagerActionExecutor.execute(context, action, replacements);
            replacements.putAll(result.replacements());
            if (result.flashTracker() && trackerFlashHandler != null) trackerFlashHandler.run();
            ran |= result.ran();
        }
        return new RunResult(ran, false);
    }

    public static void maintain(MinecraftServer server, SceneSavedData data) {
        int work = 0;
        for (SceneContinuation continuation : data.continuations()) {
            if (work >= MAX_CONTINUATIONS_PER_TICK) break;
            if (continuation.completionReceipt()) continue;
            SceneInstance scene = data.get(continuation.sceneInstanceId()).orElse(null);
            if (scene == null || !scene.state().terminal()) continue;
            work++;
            if (continuation.state() == SceneContinuation.State.WAITING) {
                continuation.resuming(scene.completionResult());
                data.changed();
                if (scene.completionResult() != SceneInstance.CompletionResult.SUCCESS) {
                    continuation.complete();
                    data.changed();
                    continue;
                }
            }
            resume(server, data, continuation);
        }
    }

    private static void resume(MinecraftServer server, SceneSavedData data, SceneContinuation continuation) {
        ServerPlayer player = server.getPlayerList().getPlayer(continuation.playerId());
        Villager provider = findVillager(server, continuation.providerId());
        if (player == null || provider == null || provider.level() != player.serverLevel()) return;
        if (continuation.questId() != null && continuation.questRunId() != null) {
            var progress = VillagerQuestSavedData.get(player.serverLevel())
                    .get(player.getUUID(), continuation.questId());
            if (progress == null || !continuation.questRunId().equals(progress.questRunId())) {
                continuation.complete();
                data.changed();
                return;
            }
        }
        DialogueContext context = VillagerInteractionService.createDialogueContext(player.serverLevel(), player, provider);
        while (continuation.nextActionIndex() < continuation.actions().size()) {
            VillagerActionDefinition action = continuation.actions().get(continuation.nextActionIndex());
            continuation.advance();
            data.changed();
            if (action.kind() == VillagerActionDefinition.Kind.START_SCENE && action.waitForScene()) {
                SceneLaunchService.LaunchResult launch = SceneLaunchService.launch(context, action);
                if (!launch.accepted()) {
                    if (action.required()) {
                        continuation.complete();
                        data.changed();
                        return;
                    }
                    continue;
                }
                if (continuation.nextActionIndex() < continuation.actions().size()
                        && continuation.actions().get(continuation.nextActionIndex()).kind()
                        == VillagerActionDefinition.Kind.QUEST_TRANSITION) {
                    VillagerActionDefinition transition =
                            continuation.actions().get(continuation.nextActionIndex());
                    continuation.advance();
                    VillagerActionResult result =
                            VillagerActionExecutor.execute(context, transition, continuation.replacements());
                    continuation.replacements().putAll(result.replacements());
                }
                continuation.waitOn(launch.instanceId());
                data.changed();
                return;
            }
            VillagerActionResult result = VillagerActionExecutor.execute(context, action, continuation.replacements());
            continuation.replacements().putAll(result.replacements());
        }
        continuation.complete();
        data.changed();
    }

    private static Villager findVillager(MinecraftServer server, java.util.UUID id) {
        if (id == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof Villager villager) return villager;
        }
        return null;
    }

    private static String pointer(String source, VillagerActionDefinition action, int index) {
        String base = source == null || source.isBlank() ? "quest_action_sequence" : source;
        return base + "/" + index + "/" + action.sceneOperationId();
    }

    public record RunResult(boolean ran, boolean suspended) {
    }
}
