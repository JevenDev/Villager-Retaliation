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
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.storage.LevelResource;

public final class EditorLiveQuestTest {
    private static volatile View view = new View("", "", "Not started", List.of(), false);

    private static volatile com.jvn.villagerretaliation.authoring.preview.QuestPlaytestView snapshot = com.jvn.villagerretaliation.authoring.preview.QuestPlaytestView.empty();
    private static net.minecraft.server.MinecraftServer testServer;
    private static volatile boolean focused;
    private static UUID testPlayer;
    private static final java.util.concurrent.atomic.AtomicLong requests = new java.util.concurrent.atomic.AtomicLong();

    interface CoverageObserver {
        void accept(AuthoringDocument document, com.google.gson.JsonObject source, com.jvn.villagerretaliation.authoring.preview.PreviewCoverage batch);
    }
    static CoverageObserver coverage = (document, source, batch) -> { };

    private EditorLiveQuestTest() { }

    static com.jvn.villagerretaliation.authoring.preview.QuestPlaytestView snapshot() { return snapshot; }

    static void stop() {
        focused = false;
        requests.incrementAndGet();
        var server = testServer;
        var player = testPlayer;
        testServer = null;
        testPlayer = null;
        if (server != null && player != null && !server.isStopped()) server.execute(() ->
                com.jvn.villagerretaliation.authoring.preview.QuestPlaytestSession.close(server, player));
        snapshot = com.jvn.villagerretaliation.authoring.preview.QuestPlaytestView.empty();
        view = new View("", "", "Not started", List.of(), false);
    }

    static View view() { return view; }

    static void reset() { stop(); }

    static void start(AuthoringWorkspace workspace, AuthoringDocument document) { start(workspace, document, null); }

    static void start(AuthoringWorkspace workspace, AuthoringDocument document, Boolean hold) { startAt(workspace, document, hold, ""); }

    static void startAt(AuthoringWorkspace workspace, AuthoringDocument document, Boolean hold, String stage) {
        submit(workspace, document, (player, quest) -> {
            Villager provider = com.jvn.villagerretaliation.authoring.preview.QuestPlaytestOffers.provider(player, quest, null);
            com.jvn.villagerretaliation.authoring.preview.QuestPlaytestSession.bind(player, quest, provider, Boolean.TRUE.equals(hold));
            focused = true;
            VillagerQuestService.debugTraceSetEnabled(player, true);
            var result = VillagerQuestService.debugStartQuest(player, provider, quest, true);
            if (result.started() && !stage.isBlank()) {
                var jump = VillagerQuestService.debugSetQuestStage(player, quest, stage);
                if (!jump.found()) throw new IllegalStateException(jump.message());
            }
            return result.message();
        });
    }

    public static boolean focusedInteractions() {
        return focused && testServer == Minecraft.getInstance().getSingleplayerServer();
    }

    public static boolean requiresInteraction(String type) {
        return focusedInteractions() && snapshot.objectives().stream().anyMatch(objective -> objective.type().equals(type));
    }

    static void offer(AuthoringWorkspace workspace, AuthoringDocument document, boolean hold, boolean restart) {
        UUID requested = Minecraft.getInstance().hitResult instanceof net.minecraft.world.phys.EntityHitResult hit
                && hit.getEntity() instanceof Villager ? hit.getEntity().getUUID() : null;
        submit(workspace, document, (player, quest) -> {
            var provider = com.jvn.villagerretaliation.authoring.preview.QuestPlaytestOffers.provider(player, quest, requested);
            if (restart) VillagerQuestService.debugRemoveQuest(player, quest);
            com.jvn.villagerretaliation.authoring.preview.QuestPlaytestSession.bind(player, quest, provider, hold);
            focused = true;
            VillagerQuestService.debugTraceSetEnabled(player, true);
            return "Quest provider: " + provider.getName().getString() + ". Talk to this villager to accept the quest.";
        });
    }

    static void returnToProvider(AuthoringWorkspace workspace, AuthoringDocument document) {
        submit(workspace, document, (player, quest) -> {
            var selection = com.jvn.villagerretaliation.authoring.preview.QuestPlaytestSession.selection(player);
            if (selection == null || !selection.quest().equals(quest)) throw new IllegalStateException("Prepare a quest offer first");
            var level = player.server.getLevel(selection.dimension());
            if (level == null) throw new IllegalStateException("The provider dimension is unavailable");
            level.getChunkAt(selection.position());
            var provider = com.jvn.villagerretaliation.util.VillagerEntityResolver.loaded(player.server, selection.provider());
            if (provider == null || !provider.isAlive()) throw new IllegalStateException("The bound quest provider is not loaded or is no longer alive");
            var destination = provider.blockPosition();
            var offset = java.util.stream.Stream.of(destination.north(), destination.south(), destination.east(), destination.west())
                    .filter(pos -> provider.level().getBlockState(pos).getCollisionShape(provider.level(), pos).isEmpty()
                            && provider.level().getBlockState(pos.above()).getCollisionShape(provider.level(), pos.above()).isEmpty()
                            && provider.level().getFluidState(pos).isEmpty()
                            && provider.level().getBlockState(pos.below()).isFaceSturdy(provider.level(), pos.below(), net.minecraft.core.Direction.UP))
                    .findFirst().orElseThrow(() -> new IllegalStateException("There is no clear space beside the quest provider"));
            player.teleportTo((net.minecraft.server.level.ServerLevel) provider.level(), offset.getX() + .5, offset.getY(), offset.getZ() + .5,
                    player.getYRot(), player.getXRot());
            return "Returned to the quest provider. Talk to them to continue or turn in the quest.";
        });
    }

    static void travelTarget(AuthoringWorkspace workspace, AuthoringDocument document) {
        submit(workspace, document, (player, quest) -> {
            var controls = com.jvn.villagerretaliation.authoring.preview.QuestPlaytestSession.require(player, quest);
            var progress = com.jvn.villagerretaliation.quest.VillagerQuestSavedData.get(player.serverLevel()).get(player.getUUID(), quest);
            var target = progress != null && progress.targetPos() != null
                    ? new com.jvn.villagerretaliation.quest.tracking.VillagerQuestTargets.LocatedTarget(progress.targetDimension(), progress.targetPos(), progress.targetObjectiveId())
                    : controls.target("");
            if (target == null) throw new IllegalStateException("Accept the quest or prepare a structure to resolve its target first");
            var level = player.server.getLevel(target.dimension());
            if (level == null) throw new IllegalStateException("The target dimension is unavailable");
            level.getChunkAt(target.pos());
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, target.pos().getX(), target.pos().getZ());
            player.teleportTo(level, target.pos().getX() + .5, y, target.pos().getZ() + .5, player.getYRot(), player.getXRot());
            return "Travelled to the quest target. Return to the provider when you are ready to turn it in.";
        });
    }

    static void structure(AuthoringWorkspace workspace, AuthoringDocument document, String objective) {
        submit(workspace, document, (player, quest) -> com.jvn.villagerretaliation.authoring.preview.QuestPlaytestStructures.spawn(player, quest, objective));
    }

    static void inspect(AuthoringWorkspace workspace, AuthoringDocument document) {
        submit(workspace, document, (player, quest) -> "", false);
    }

    static void clear(AuthoringWorkspace workspace, AuthoringDocument document) {
        submit(workspace, document, (player, quest) -> VillagerQuestService.debugRemoveQuest(player, quest).message());
    }

    static void stage(AuthoringWorkspace workspace, AuthoringDocument document, String stage) {
        submit(workspace, document, (player, quest) -> {
            var controls = com.jvn.villagerretaliation.authoring.preview.QuestPlaytestSession.controls(player, quest);
            if (controls != null) controls.clear();
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

    static void transition(AuthoringWorkspace workspace, AuthoringDocument document, String option) {
        submit(workspace, document, (player, quest) -> {
            var context = VillagerQuestService.debugPlaytestContext(player, quest).dialogueContext();
            if (context == null) throw new IllegalStateException("Return to the quest provider before choosing a dialogue transition");
            var choice = java.util.stream.Stream.concat(VillagerQuestService.stageBranchOptions(context).stream(),
                    VillagerQuestService.embeddedDialogueOptions(context, com.jvn.villagerretaliation.dialogue.normal.DialogueDisposition.NEUTRAL).stream())
                    .filter(candidate -> candidate.id().equals(option)).findFirst().orElseThrow(() -> new IllegalStateException("The transition is no longer available"));
            if (!choice.available()) throw new IllegalStateException(choice.unavailableReason());
            com.jvn.villagerretaliation.interaction.VillagerInteractionService.openInteractionScreen(player, context.villager());
            com.jvn.villagerretaliation.interaction.VillagerDialogueRequestHandler.handle(player, context.villager().getId(), option);
            return "Selected " + choice.label();
        });
    }

    static void hold(AuthoringWorkspace workspace, AuthoringDocument document, boolean value) {
        submit(workspace, document, (player, quest) -> {
            com.jvn.villagerretaliation.authoring.preview.QuestPlaytestSession.require(player, quest).hold(value);
            return value ? "Automatic transitions wait for Next" : "Normal automatic progression enabled";
        }, false);
    }

    static void next(AuthoringWorkspace workspace, AuthoringDocument document) {
        submit(workspace, document, (player, quest) -> { VillagerQuestService.debugPlaytestAdvance(player, quest); return "Advanced ready stage"; });
    }

    static void replay(AuthoringWorkspace workspace, AuthoringDocument document) {
        submit(workspace, document, (player, quest) -> { VillagerQuestService.debugPlaytestReplay(player, quest); return "Replayed stage entry actions"; });
    }

    static void force(AuthoringWorkspace workspace, AuthoringDocument document, String objective) {
        submit(workspace, document, (player, quest) -> {
            var context = VillagerQuestService.debugPlaytestContext(player, quest);
            var controls = com.jvn.villagerretaliation.authoring.preview.QuestPlaytestSession.require(player, quest);
            if (objective.isBlank()) {
                controls.forceStage(context.progress().currentStage());
                context.definition().stages().get(context.progress().currentStage()).objectives()
                        .forEach(id -> controls.force(context.progress().currentStage(), id));
            }
            else {
                if (!context.definition().stages().get(context.progress().currentStage()).objectives().contains(objective))
                    throw new IllegalArgumentException("Choose an objective in the current stage");
                controls.force(context.progress().currentStage(), objective);
            }
            return "Test bypass: " + (objective.isBlank() ? "stage ready" : "objective " + objective + " complete");
        });
    }

    static void prepare(AuthoringWorkspace workspace, AuthoringDocument document, String objective, String value) {
        submit(workspace, document, (player, quest) -> {
            var context = VillagerQuestService.debugPlaytestContext(player, quest);
            var definition = context.definition().objectives().stream().filter(candidate -> candidate.id().equals(objective))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown objective " + objective));
            return com.jvn.villagerretaliation.authoring.preview.QuestPlaytestSetup.prepare(context, definition, value);
        });
    }

    private static void submit(AuthoringWorkspace workspace, AuthoringDocument document, Action action) {
        submit(workspace, document, action, true);
    }

    private static void submit(AuthoringWorkspace workspace, AuthoringDocument document, Action action, boolean requireCurrent) {
        var minecraft = Minecraft.getInstance();
        var server = minecraft.getSingleplayerServer();
        if (document.kind() != DefinitionKind.QUEST || minecraft.player == null || !EditorTestWorld.active(workspace))
            throw new IllegalArgumentException("Set up quest testing in the current world first");
        if (requireCurrent && !EditorTestWorld.currentLogic(workspace)) throw new IllegalArgumentException("Quest logic changed. Use Apply & replay in Playtest to load this draft.");
        boolean trackCoverage = EditorTestWorld.currentLogic(workspace);
        var source = document.json();
        UUID playerId = minecraft.player.getUUID();
        ResourceLocation quest = ResourceLocation.parse(document.id());
        Path world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        long request = requests.incrementAndGet();
        testServer = server;
        testPlayer = playerId;
        var previous = view;
        view = new View(quest.toString(), previous.message(), previous.summary(), previous.lines(), true);
        server.execute(() -> {
            if (requests.get() != request || !EditorTestWorld.sameWorld(server, world)) return;
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) { view = new View(quest.toString(), "Player left the test world", "", List.of(), false); return; }
            String message;
            try {
                String problem = EditorContentTools.playtestProblem(document, com.jvn.villagerretaliation.campaign.selection.WorldCampaignSelection.get(server));
                if (problem != null) throw new IllegalStateException(problem);
                message = action.run(player, quest);
            }
            catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException exception) {
                if (requests.get() == request) view = new View(quest.toString(), exception.getMessage(), previous.summary(), previous.lines(), false);
                return;
            }
            var controls = com.jvn.villagerretaliation.authoring.preview.QuestPlaytestSession.controls(player, quest);
            if (controls != null) controls.log(message);
            var nextSnapshot = VillagerQuestService.debugPlaytestView(player, quest);
            List<String> lines = new ArrayList<>();
            var state = VillagerQuestService.debugInspectQuest(player, quest);
            lines.addAll(state.lines());
            if (!state.message().isBlank()) lines.add(state.message());
            var progress = com.jvn.villagerretaliation.quest.VillagerQuestSavedData.get(player.serverLevel()).get(playerId, quest);
            if (progress != null) lines.addAll(VillagerQuestService.debugObjectives(player, quest).lines());
            lines.addAll(VillagerQuestService.debugTraceRecent(player, 48).lines());
            String summary = progress == null ? "Not started" : "State: " + progress.state().name().toLowerCase(java.util.Locale.ROOT)
                    + " | Stage: " + progress.currentStage();
            if (requests.get() != request) return;
            if (trackCoverage && controls != null) {
                var batch = controls.drainCoverage();
                minecraft.execute(() -> coverage.accept(document, source, batch));
            }
            snapshot = nextSnapshot;
            view = new View(quest.toString(), message.isBlank() ? previous.message() : message, summary, List.copyOf(lines), false);
        });
    }

    private interface Action { String run(ServerPlayer player, ResourceLocation quest); }
    record View(String quest, String message, String summary, List<String> lines, boolean busy) { }
}
