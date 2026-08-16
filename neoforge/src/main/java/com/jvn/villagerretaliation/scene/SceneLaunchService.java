package com.jvn.villagerretaliation.scene;

import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Small quest-to-scene boundary. The persistent runtime installs the launcher during server startup. */
public final class SceneLaunchService {
    private static volatile Launcher launcher = request -> LaunchResult.rejected("persistent scene runtime is not initialized");

    private SceneLaunchService() {
    }

    public static LaunchResult launch(DialogueContext context, VillagerActionDefinition action) {
        if (context == null || action == null || action.kind() != VillagerActionDefinition.Kind.START_SCENE
                || action.sceneId() == null || action.sceneOperationId().isBlank()) {
            return LaunchResult.rejected("start_scene requires live context, scene_id, and stable operation_id");
        }
        if (SceneResources.scene(context.level().getServer(), action.sceneId()).isEmpty()) {
            return LaunchResult.rejected("unknown compiled scene " + action.sceneId());
        }
        UUID runId = null;
        if (action.questId() != null) {
            VillagerQuestSavedData.QuestProgress progress = VillagerQuestSavedData.get(context.level())
                    .get(context.player().getUUID(), action.questId());
            runId = progress == null ? null : progress.questRunId();
        }
        return launcher.launch(new LaunchRequest(context.level().getServer(), action.sceneId(), action.sceneOperationId(),
                action.waitForScene(), context.player().getUUID(), context.villager().getUUID(), action.questId(), runId));
    }

    public static void install(Launcher implementation) {
        launcher = implementation == null
                ? request -> LaunchResult.rejected("persistent scene runtime is not initialized")
                : implementation;
    }

    @FunctionalInterface
    public interface Launcher {
        LaunchResult launch(LaunchRequest request);
    }

    public record LaunchRequest(net.minecraft.server.MinecraftServer server, ResourceLocation sceneId,
                                String operationId, boolean waitForResult, UUID playerId, UUID providerId,
                                ResourceLocation questId, UUID questRunId) {
    }

    public record LaunchResult(boolean accepted, boolean created, UUID instanceId, String diagnostic) {
        public static LaunchResult rejected(String diagnostic) {
            return new LaunchResult(false, false, null, diagnostic);
        }

        public static LaunchResult accepted(UUID instanceId, boolean created) {
            return new LaunchResult(true, created, instanceId, created ? "scene created" : "existing scene reused");
        }
    }
}
