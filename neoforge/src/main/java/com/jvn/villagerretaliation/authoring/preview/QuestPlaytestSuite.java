package com.jvn.villagerretaliation.authoring.preview;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.VillagerRetaliation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = VillagerRetaliation.MOD_ID)
public final class QuestPlaytestSuite {
    public record Result(ResourceLocation quest, String name, boolean passed, List<String> details) {
        public Result { details = List.copyOf(details); }
    }
    public record View(boolean running, String message, List<Result> results) {
        public View { results = List.copyOf(results); }
        public static View empty() { return new View(false, "", List.of()); }
    }
    private static final Map<MinecraftServer, Map<UUID, Run>> RUNS = new java.util.WeakHashMap<>();
    private QuestPlaytestSuite() { }

    public static void start(ServerPlayer player, Map<ResourceLocation, JsonObject> tests) {
        if (!player.server.isSameThread()) throw new IllegalStateException("Suites require the server thread");
        if (!player.server.getPackRepository().getSelectedIds().contains("file/test_content")) throw new IllegalStateException("Set up the editor test pack first");
        if (view(player).running()) throw new IllegalStateException("Stop the current workspace suite first");
        if (tests.isEmpty() || tests.size() > 256) throw new IllegalArgumentException("Choose 1 to 256 quests with saved live tests");
        var selection = QuestPlaytestSession.selection(player);
        if (selection != null && QuestPlaytestScenarios.view(player, selection.quest()).running()) throw new IllegalStateException("Stop the current quest tests first");
        var copy = new java.util.LinkedHashMap<ResourceLocation, JsonObject>();
        tests.forEach((quest, saved) -> copy.put(quest, saved.deepCopy()));
        var run = new Run(player, List.copyOf(copy.entrySet()), selection == null ? null : QuestPlaytestBaseline.checkpoint(player, selection.quest()));
        RUNS.computeIfAbsent(player.server, ignored -> new java.util.HashMap<>()).put(player.getUUID(), run);
    }

    public static View view(ServerPlayer player) {
        var run = RUNS.getOrDefault(player.server, Map.of()).get(player.getUUID());
        return run == null ? View.empty() : run.view;
    }

    public static void cancel(MinecraftServer server, UUID player) {
        var run = RUNS.getOrDefault(server, Map.of()).get(player);
        if (run != null) run.finish("Cancelled");
    }

    @SubscribeEvent
    public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        for (var run : List.copyOf(RUNS.getOrDefault(event.getServer(), Map.of()).values())) if (run.running) run.tick();
    }

    @SubscribeEvent
    public static void stopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        for (var run : List.copyOf(RUNS.getOrDefault(event.getServer(), Map.of()).values())) run.finish("Server stopping");
        RUNS.remove(event.getServer());
    }

    private static final class Run {
        final ServerPlayer player;
        final List<Map.Entry<ResourceLocation, JsonObject>> pending;
        final Runnable original;
        final List<Result> results = new ArrayList<>();
        ResourceLocation active;
        int index;
        boolean running = true;
        View view = new View(true, "Starting workspace live tests", List.of());

        Run(ServerPlayer player, List<Map.Entry<ResourceLocation, JsonObject>> pending, Runnable original) {
            this.player = player; this.pending = pending; this.original = original;
        }

        void tick() {
            if (player.isRemoved() || !player.server.getPackRepository().getSelectedIds().contains("file/test_content")) { finish("Test world closed"); return; }
            if (active != null) {
                var current = QuestPlaytestScenarios.view(player, active);
                view = new View(true, active + ": " + current.message(), results);
                if (current.running()) return;
                for (var result : current.results()) results.add(new Result(active, result.name(), result.passed(), result.details()));
                if (current.results().isEmpty()) results.add(new Result(active, "Live suite", false, List.of("Quest test session ended before reporting a result")));
                active = null;
            }
            if (index >= pending.size()) { finish("Workspace live tests finished"); return; }
            var next = pending.get(index++);
            try {
                if (com.jvn.villagerretaliation.quest.VillagerQuestResources.quest(player.server, next.getKey()).isEmpty())
                    throw new IllegalStateException("Quest is not loaded in this world's campaign");
                if (next.getValue().isEmpty()) throw new IllegalArgumentException("No saved live tests");
                var first = QuestLiveTest.read(next.getValue().entrySet().iterator().next().getValue().getAsJsonObject());
                QuestPlaytestBaseline.prepare(player, next.getKey(), first.baseline());
                QuestPlaytestScenarios.start(player, next.getKey(), next.getValue());
                active = next.getKey();
                view = new View(true, "Running " + active, results);
            } catch (IllegalArgumentException | IllegalStateException exception) {
                results.add(new Result(next.getKey(), "Prepare live tests", false, List.of(exception.getMessage())));
                view = new View(true, "Continuing after failed setup", results);
            }
        }

        void finish(String message) {
            if (!running) return;
            running = false;
            if (active != null) {
                QuestPlaytestScenarios.cancel(player.server, player.getUUID());
                for (var result : QuestPlaytestScenarios.view(player, active).results())
                    results.add(new Result(active, result.name(), result.passed(), result.details()));
                active = null;
            }
            try {
                if (original != null) original.run();
                else QuestPlaytestSession.close(player.server, player.getUUID());
            } catch (IllegalArgumentException | IllegalStateException exception) {
                results.add(new Result(pending.getFirst().getKey(), "Restore original playtest", false, List.of(exception.getMessage())));
            }
            view = new View(false, message, results);
        }
    }
}
