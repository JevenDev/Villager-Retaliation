package com.jvn.villagerretaliation.authoring.preview;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = VillagerRetaliation.MOD_ID)
public final class QuestPlaytestScenarios {
    public record Result(String name, boolean passed, List<String> details) {
        public Result { details = List.copyOf(details); }
    }
    public record View(boolean running, String name, int step, int steps, String message, List<Result> results) {
        public View { results = List.copyOf(results); }
        public static View empty() { return new View(false, "", 0, 0, "", List.of()); }
    }
    private static final Map<MinecraftServer, Map<UUID, Run>> RUNS = new java.util.WeakHashMap<>();
    private QuestPlaytestScenarios() { }

    public static void start(ServerPlayer player, ResourceLocation quest, JsonObject saved) {
        QuestPlaytestSession.require(player, quest);
        var previous = runs(player.server).get(player.getUUID());
        if (previous != null && previous.running) throw new IllegalStateException("Stop the current live test suite first");
        if (saved.isEmpty() || saved.size() > 32) throw new IllegalArgumentException("Save 1 to 32 live tests before running");
        var tests = new LinkedHashMap<String, QuestLiveTest>();
        var baselines = QuestPlaytestBaseline.names(player, quest);
        for (var entry : saved.entrySet()) {
            if (!entry.getValue().isJsonObject()) throw new IllegalArgumentException("Invalid saved live test: " + entry.getKey());
            var test = QuestLiveTest.read(entry.getValue().getAsJsonObject());
            if (!baselines.contains(test.baseline())) throw new IllegalArgumentException("Missing baseline for " + entry.getKey() + ": " + test.baseline());
            tests.put(entry.getKey(), test);
        }
        runs(player.server).put(player.getUUID(), new Run(player, quest, tests));
    }

    private static Map<UUID, Run> runs(MinecraftServer server) { return RUNS.computeIfAbsent(server, ignored -> new LinkedHashMap<>()); }

    public static View view(ServerPlayer player, ResourceLocation quest) {
        var run = runs(player.server).get(player.getUUID());
        return run == null || !run.quest.equals(quest) ? View.empty() : run.view;
    }

    public static void cancel(MinecraftServer server, UUID player) {
        var run = runs(server).get(player);
        if (run != null && run.running) run.finish("Cancelled");
    }

    public static void close(MinecraftServer server, UUID player) {
        cancel(server, player);
        runs(server).remove(player);
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) { tick(event.getServer()); }

    public static void tick(MinecraftServer server) {
        for (var run : List.copyOf(runs(server).values())) if (run.running) run.tick();
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        for (var run : List.copyOf(runs(event.getServer()).values())) if (run.running) run.finish("Server stopping");
        RUNS.remove(event.getServer());
    }

    private static final class Run {
        final ServerPlayer player;
        final ResourceLocation quest;
        final List<Map.Entry<String, QuestLiveTest>> tests;
        final Runnable checkpoint;
        final List<Result> results = new ArrayList<>();
        final List<String> details = new ArrayList<>();
        boolean running = true;
        int testIndex, stepIndex, initialExperience, initialCompletions, initialStarts;
        long nextTick, started;
        QuestLiveTest test;
        String name = "";
        View view = View.empty();

        Run(ServerPlayer player, ResourceLocation quest, Map<String, QuestLiveTest> tests) {
            this.player = player; this.quest = quest; this.tests = List.copyOf(tests.entrySet());
            checkpoint = QuestPlaytestBaseline.checkpoint(player, quest);
            publish("Starting live tests");
        }

        void publish(String message) { view = new View(running, name, stepIndex, test == null ? 0 : test.steps().size(), message, results); }

        void tick() {
            try {
                if (player.isRemoved() || !QuestPlaytestSession.selected(player, quest)) { finish("Playtest session ended"); return; }
                long now = player.server.overworld().getGameTime();
                if (test == null) {
                    if (testIndex >= tests.size()) { finish("Live tests finished"); return; }
                    var entry = tests.get(testIndex++);
                    name = entry.getKey(); test = entry.getValue(); stepIndex = 0; details.clear();
                    QuestPlaytestBaseline.restore(player, quest, test.baseline());
                    QuestPlaytestSession.require(player, quest).hold(test.manual());
                    initialExperience = player.totalExperience;
                    var progress = progress();
                    initialCompletions = progress == null ? 0 : progress.completionCount();
                    initialStarts = progress == null ? 0 : progress.startCount();
                    started = now; nextTick = now + 2; publish("Baseline restored");
                    return;
                }
                if (now - started > test.waitingTicks() + test.steps().size() * 40L + 200) throw new IllegalStateException("Live test timed out");
                if (now < nextTick) return;
                if (stepIndex < test.steps().size()) {
                    var step = test.steps().get(stepIndex++);
                    if (step.action().equals("wait")) {
                        nextTick = now + Integer.parseInt(step.value()); details.add("Wait " + step.value() + " ticks");
                    } else {
                        String result = action(step);
                        if (!step.value().isBlank() && !step.action().equals("prepare") && !result.equals(step.value()))
                            throw new IllegalStateException(step.action() + " expected " + step.value() + ", got " + result);
                        details.add(step.action() + (step.target().isBlank() ? "" : " " + step.target()) + ": " + result);
                        nextTick = now + 2;
                    }
                    publish("Running step " + stepIndex);
                    return;
                }
                boolean passed = true;
                for (var expected : test.expectations()) {
                    String actual = actual(expected);
                    boolean numeric = List.of("experience_delta", "completion_delta", "start_delta", "item_count").contains(expected.kind());
                    boolean matched = numeric ? Integer.parseInt(actual) == Integer.parseInt(expected.expected()) : actual.equals(expected.expected());
                    passed &= matched;
                    details.add((matched ? "PASS " : "FAIL ") + expected.kind() + (expected.target().isBlank() ? "" : " " + expected.target())
                            + ": expected " + expected.expected() + ", got " + actual);
                }
                results.add(new Result(name, passed, details)); test = null; publish(passed ? "Passed" : "Failed");
            } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException exception) {
                details.add("Step " + stepIndex + ": " + exception.getMessage());
                results.add(new Result(name, false, details));
                test = null;
                publish("Failed");
            }
        }

        void finish(String message) {
            if (!running) return;
            running = false;
            if (test != null) { details.add(message); results.add(new Result(name, false, details)); }
            test = null;
            try { checkpoint.run(); publish(message + ". Playtest state restored."); }
            catch (IllegalArgumentException | IllegalStateException exception) {
                results.add(new Result("Restore playtest state", false, List.of(exception.getMessage())));
                publish(message + ". Could not restore playtest state: " + exception.getMessage());
            }
        }

        VillagerQuestSavedData.QuestProgress progress() { return VillagerQuestSavedData.get(player.serverLevel()).get(player.getUUID(), quest); }

        DialogueContext context() {
            var selection = QuestPlaytestSession.selection(player);
            if (selection == null || !selection.quest().equals(quest)) throw new IllegalStateException("Quest playtest selection changed");
            if (selection.provider() == null) return DialogueContext.forPlayer(player);
            var provider = com.jvn.villagerretaliation.util.VillagerEntityResolver.loaded(player.server, selection.provider());
            if (provider == null || !provider.isAlive()) throw new IllegalStateException("The quest provider is not loaded");
            return VillagerInteractionService.createDialogueContext((net.minecraft.server.level.ServerLevel) provider.level(), player, provider);
        }

        QuestDefinition.Objective objective(String id) {
            var definition = VillagerQuestResources.quest(player.server, quest).orElseThrow(() -> new IllegalStateException("Quest is unavailable"));
            String local = progress() == null ? "" : progress().currentStage() + "." + id;
            return definition.objectives().stream().filter(value -> value.id().equals(id) || value.id().equals(local)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown objective " + id));
        }

        String action(QuestLiveTest.Step step) {
            return switch (step.action()) {
                case "start", "turn_in", "fail", "abandon" -> {
                    var action = switch (step.action()) {
                        case "start" -> VillagerActionDefinition.QuestAction.START;
                        case "turn_in" -> VillagerActionDefinition.QuestAction.TURN_IN;
                        case "fail" -> VillagerActionDefinition.QuestAction.FAIL;
                        default -> VillagerActionDefinition.QuestAction.ABANDON;
                    };
                    var context = context();
                    if (action == VillagerActionDefinition.QuestAction.START || action == VillagerActionDefinition.QuestAction.TURN_IN) requireNearby(context);
                    yield VillagerQuestService.performAction(context, quest, action).map(VillagerQuestService.QuestActionOutcome::status).orElse("unavailable");
                }
                case "prepare" -> QuestPlaytestSetup.prepare(VillagerQuestService.debugPlaytestContext(player, quest), objective(step.target()), step.value());
                case "travel" -> QuestPlaytestSetup.travel(VillagerQuestService.debugPlaytestContext(player, quest), objective(step.target()));
                case "criterion" -> QuestPlaytestSetup.simulateCriterion(VillagerQuestService.debugPlaytestContext(player, quest), objective(step.target()));
                case "next" -> {
                    if (!VillagerQuestService.debugPlaytestView(player, quest).ready()) throw new IllegalStateException("Current stage requirements are unmet");
                    VillagerQuestService.debugPlaytestAdvance(player, quest); yield "advanced";
                }
                case "return_provider" -> {
                    var provider = context().villager();
                    if (provider == null) throw new IllegalStateException("This quest has no villager provider");
                    QuestPlaytestSetup.teleport(player, (net.minecraft.server.level.ServerLevel) provider.level(), provider.blockPosition());
                    yield "returned";
                }
                case "discovery_item", "discovery_travel" -> QuestPlaytestDiscovery.prepare(player, quest, step.target(), step.action().equals("discovery_travel"));
                case "accept_discovery" -> com.jvn.villagerretaliation.quest.discovery.QuestDiscoveryService.accept(player, quest) ? "accepted" : "unavailable";
                case "choice" -> {
                    var context = context();
                    if (context.villager() == null) throw new IllegalStateException("Quest choices require a villager provider");
                    requireNearby(context);
                    var choice = java.util.stream.Stream.concat(VillagerQuestService.stageBranchOptions(context).stream(),
                            VillagerQuestService.embeddedDialogueOptions(context, com.jvn.villagerretaliation.dialogue.normal.DialogueDisposition.NEUTRAL).stream())
                            .filter(value -> value.id().equals(step.target())).findFirst().orElseThrow(() -> new IllegalStateException("Choice is unavailable: " + step.target()));
                    if (!choice.available()) throw new IllegalStateException(choice.unavailableReason());
                    VillagerInteractionService.openInteractionScreen(player, context.villager());
                    com.jvn.villagerretaliation.interaction.VillagerDialogueRequestHandler.handle(player, context.villager().getId(), choice.id());
                    yield "selected";
                }
                default -> throw new IllegalArgumentException("Unknown live test step");
            };
        }

        void requireNearby(DialogueContext context) {
            if (context.villager() != null && (context.villager().level() != player.serverLevel() || context.villager().distanceToSqr(player) > 36))
                throw new IllegalStateException("Return to the quest provider before starting, choosing dialogue or turning in");
        }

        String actual(QuestLiveTest.Expectation check) {
            var progress = progress();
            return switch (check.kind()) {
                case "state" -> progress == null ? "not_started" : progress.state().name().toLowerCase(java.util.Locale.ROOT);
                case "stage" -> progress == null ? "" : progress.currentStage();
                case "experience_delta" -> Integer.toString(player.totalExperience - initialExperience);
                case "completion_delta" -> Integer.toString((progress == null ? 0 : progress.completionCount()) - initialCompletions);
                case "start_delta" -> Integer.toString((progress == null ? 0 : progress.startCount()) - initialStarts);
                case "performance_tier" -> progress == null || progress.performance().tier().isEmpty() ? "base" : progress.performance().tier();
                case "objective_complete" -> {
                    var objective = objective(check.target());
                    yield Boolean.toString(progress != null && progress.objectiveComplete(objective.id()));
                }
                case "item_count" -> {
                    var id = ResourceLocation.tryParse(check.target());
                    if (id == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)) throw new IllegalArgumentException("Unknown item " + check.target());
                    var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
                    int count = 0;
                    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                        var stack = player.getInventory().getItem(i); if (stack.is(item)) count += stack.getCount();
                    }
                    yield Integer.toString(count);
                }
                default -> throw new IllegalArgumentException("Unknown live test expectation");
            };
        }
    }
}
