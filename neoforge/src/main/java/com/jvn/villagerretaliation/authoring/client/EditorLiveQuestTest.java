package com.jvn.villagerretaliation.authoring.client;

import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.storage.LevelResource;

final class EditorLiveQuestTest {
    private static volatile View view = new View("", "", "Not started", List.of(), false);

    private EditorLiveQuestTest() { }

    static View view() { return view; }

    static void reset() { view = new View("", "", "Not started", List.of(), false); }

    static void start(AuthoringWorkspace workspace, AuthoringDocument document) {
        submit(workspace, document, (player, quest) -> {
            Villager provider = player.serverLevel().getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(24)).stream()
                    .min(java.util.Comparator.comparingDouble(entity -> entity.distanceToSqr(player))).orElse(null);
            if (provider == null) {
                provider = EntityType.VILLAGER.create(player.serverLevel());
                if (provider == null) throw new IllegalStateException("Unable to create a test provider");
                provider.setPos(player.getX() + 2, player.getY(), player.getZ());
                provider.setNoAi(true);
                provider.setInvulnerable(true);
                player.serverLevel().addFreshEntity(provider);
            }
            VillagerQuestService.debugTraceSetEnabled(player, true);
            var result = VillagerQuestService.debugStartQuest(player, provider, quest, true);
            return result.message();
        });
    }

    static void inspect(AuthoringWorkspace workspace, AuthoringDocument document) {
        submit(workspace, document, (player, quest) -> "Live quest state refreshed");
    }

    static void clear(AuthoringWorkspace workspace, AuthoringDocument document) {
        submit(workspace, document, (player, quest) -> VillagerQuestService.debugRemoveQuest(player, quest).message());
    }

    static void stage(AuthoringWorkspace workspace, AuthoringDocument document, String stage) {
        submit(workspace, document, (player, quest) -> {
            var result = VillagerQuestService.debugSetQuestStage(player, quest, stage);
            return result.found() ? String.join("\n", result.lines()) : result.message();
        });
    }

    static void turnIn(AuthoringWorkspace workspace, AuthoringDocument document) {
        submit(workspace, document, (player, quest) -> {
            var data = com.jvn.villagerretaliation.quest.VillagerQuestSavedData.get(player.serverLevel());
            var progress = data.get(player.getUUID(), quest);
            var provider = progress == null || progress.startedVillagerId() == null ? null : player.serverLevel().getEntity(progress.startedVillagerId());
            if (!(provider instanceof Villager villager)) return "The quest provider is not loaded. Start a live test first.";
            var context = com.jvn.villagerretaliation.interaction.VillagerInteractionService.createDialogueContext(player.serverLevel(), player, villager);
            return VillagerQuestService.performAction(context, quest, com.jvn.villagerretaliation.action.VillagerActionDefinition.QuestAction.TURN_IN)
                    .map(result -> result.status() + ": " + result.text()).orElse("Quest is unavailable");
        });
    }

    private static void submit(AuthoringWorkspace workspace, AuthoringDocument document, Action action) {
        var minecraft = Minecraft.getInstance();
        var server = minecraft.getSingleplayerServer();
        if (document.kind() != DefinitionKind.QUEST || minecraft.player == null || !EditorTestWorld.active(workspace))
            throw new IllegalArgumentException("Create and enter a separate editor test world first");
        if (!EditorTestWorld.currentSnapshot(workspace)) throw new IllegalArgumentException("Content changed after export. Create a new test world to load this draft.");
        UUID playerId = minecraft.player.getUUID();
        ResourceLocation quest = ResourceLocation.parse(document.id());
        Path world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        view = new View(quest.toString(), "Running live test...", "", List.of(), true);
        server.execute(() -> {
            if (!EditorTestWorld.sameWorld(server, world)) return;
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) { view = new View(quest.toString(), "Player left the test world", "", List.of(), false); return; }
            String message = action.run(player, quest);
            List<String> lines = new ArrayList<>();
            var state = VillagerQuestService.debugInspectQuest(player, quest);
            lines.addAll(state.lines());
            if (!state.message().isBlank()) lines.add(state.message());
            lines.addAll(VillagerQuestService.debugObjectives(player, quest).lines());
            lines.addAll(VillagerQuestService.debugTraceRecent(player, 48).lines());
            var progress = com.jvn.villagerretaliation.quest.VillagerQuestSavedData.get(player.serverLevel()).get(playerId, quest);
            String summary = progress == null ? "Not started" : "State: " + progress.state().name().toLowerCase(java.util.Locale.ROOT)
                    + " | Stage: " + progress.currentStage();
            view = new View(quest.toString(), message, summary, List.copyOf(lines), false);
        });
    }

    private interface Action { String run(ServerPlayer player, ResourceLocation quest); }
    record View(String quest, String message, String summary, List<String> lines, boolean busy) { }
}
