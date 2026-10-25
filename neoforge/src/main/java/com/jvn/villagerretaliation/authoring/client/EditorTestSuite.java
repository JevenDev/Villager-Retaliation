package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringDiagnostic;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;
import com.jvn.villagerretaliation.authoring.preview.QuestPlaytestSuite;
import imgui.ImGui;
import imgui.type.ImString;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

final class EditorTestSuite {
    record Result(AuthoringDocument document, String pointer, String name, String state, String message) { }
    private AuthoringWorkspace owner;
    private String scope = "", status = "";
    private final List<Result> results = new ArrayList<>();
    private Map<AuthoringDocument, Long> tested = Map.of();
    private boolean failuresOnly;
    private final ImString checkpointName = new ImString("Before test run", 128);
    private List<EditorWorldCheckpoints.Checkpoint> checkpoints = List.of();
    private CompletableFuture<QuestPlaytestSuite.View> live;
    private QuestPlaytestSuite.View liveView = QuestPlaytestSuite.View.empty();
    private Map<ResourceLocation, AuthoringDocument> liveDocuments = Map.of();
    private net.minecraft.server.MinecraftServer liveServer;
    private java.util.UUID livePlayer;
    private long nextPoll;
    private int liveResults;

    static List<AuthoringDocument> documents(AuthoringWorkspace workspace, String scope) {
        return workspace.documents().stream().filter(document -> scope.isEmpty() || DefinitionKind.contentRoot(document.resourcePath()).equals(scope))
                .sorted(java.util.Comparator.comparing(AuthoringDocument::resourcePath)).toList();
    }

    static List<Result> run(AuthoringWorkspace workspace, String scope, String locale, EditorCoverage coverage) {
        var documents = documents(workspace, scope);
        var results = new ArrayList<Result>();
        for (var issue : workspace.validate()) {
            var document = documents.stream().filter(value -> value.resourcePath().equals(issue.resourcePath())).findFirst().orElse(null);
            if (document != null) results.add(new Result(document, issue.pointer(), "Validation",
                    issue.severity() == AuthoringDiagnostic.Severity.ERROR ? "FAIL" : "WARN", issue.message()));
        }
        for (var document : documents) {
            if (document.kind() == DefinitionKind.RESOURCE || document.kind().campaignManifest()) continue;
            var saved = EditorSchema.object(document.metadata().get("preview_scenarios"));
            if (saved.isEmpty()) {
                results.add(new Result(document, "", "Saved scenarios", "SKIP", "No saved preview scenarios"));
                continue;
            }
            for (var result : EditorScenarioTests.run(document, (key, conditions) -> {
                var reference = new JsonObject(); reference.addProperty("key", key);
                String value = workspace.resolveText(document, reference, locale, conditions.variant(),
                        candidate -> conditions.evaluate(ResourceLocation.parse(document.id()), candidate)
                                == com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome.MATCHED);
                return value.equals(key) ? null : value;
            }, coverage.observe(document))) {
                String pointer = DialogueGraph.nodes(document).stream().filter(node -> node.id().equals(result.node()))
                        .map(DialogueGraph.Node::pointer).findFirst().orElse("");
                results.add(new Result(document, pointer, result.name(), result.skipped() ? "SKIP" : result.passed() ? "PASS" : "FAIL", result.message()));
            }
        }
        return List.copyOf(results);
    }

    void render(AuthoringWorkspace workspace, String locale, EditorCoverage coverage, net.minecraft.client.gui.screens.Screen screen,
                Consumer<EditorReferences.Location> navigate, Consumer<ImGuiAuthoringEditor.EditorOperation> run) {
        if (workspace == null) { ImGui.textWrapped("Open a workspace to run tests."); return; }
        if (owner != workspace) {
            cancel(); live = null; liveView = QuestPlaytestSuite.View.empty(); liveDocuments = Map.of();
            owner = workspace; results.clear(); tested = Map.of(); scope = ""; status = ""; checkpoints = List.of();
            run.accept(() -> checkpoints = EditorWorldCheckpoints.list(EditorWorldCheckpoints.directory(), workspace));
        }
        poll();
        ImGui.textUnformatted("Tests");
        ImGui.textWrapped("Run saved scenarios across the pack or one campaign. Preview tests simulate branches. Live tests use the loaded quest runtime and each test's saved world baseline.");
        boolean busy = live != null || liveView.running();
        ImGui.beginDisabled(busy);
        var scopes = workspace.documents().stream().filter(document -> document.kind().campaignManifest()).toList();
        if (ImGui.beginCombo(EditorWidgets.field("Scope"), scope.isEmpty() ? "Whole pack" : scopes.stream()
                .filter(document -> DefinitionKind.contentRoot(document.resourcePath()).equals(scope)).map(AuthoringDocument::id).findFirst().orElse(scope))) {
            if (ImGui.selectable("Whole pack", scope.isEmpty())) scope = "";
            for (var campaign : scopes) if (ImGui.selectable(campaign.id(), DefinitionKind.contentRoot(campaign.resourcePath()).equals(scope)))
                scope = DefinitionKind.contentRoot(campaign.resourcePath());
            ImGui.endCombo();
        }
        if (ImGui.button("Run validation and previews")) run.accept(() -> {
            results.clear(); results.addAll(run(workspace, scope, locale, coverage)); remember(workspace); status = "Preview suite finished.";
        });
        EditorWidgets.sameLineFor("Run saved live tests");
        ImGui.beginDisabled(!EditorTestWorld.active(workspace) || !EditorTestWorld.currentLogic(workspace));
        if (ImGui.button("Run saved live tests")) run.accept(() -> startLive(workspace));
        ImGui.endDisabled(); ImGui.endDisabled();
        if (busy && ImGui.button("Stop live suite and restore")) cancel();
        if (!EditorTestWorld.active(workspace)) ImGui.textWrapped("Set up quest testing in Playtest to enable live suites.");
        else if (!EditorTestWorld.currentLogic(workspace)) ImGui.textWrapped("Apply current logic in Playtest before running live tests.");
        if (!status.isEmpty()) ImGui.textWrapped(status);
        if (!tested.isEmpty() && (tested.size() != workspace.documents().size()
                || workspace.documents().stream().anyMatch(document -> tested.getOrDefault(document, -1L) != document.revision())))
            ImGui.textWrapped("Content or tests changed. Run again for current results.");
        ImGui.separatorText("Results");
        long passed = results.stream().filter(result -> result.state().equals("PASS")).count();
        long failed = results.stream().filter(result -> result.state().equals("FAIL")).count();
        long skipped = results.stream().filter(result -> result.state().equals("SKIP")).count();
        ImGui.textUnformatted(passed + " passed, " + failed + " failed, " + skipped + " skipped");
        if (ImGui.checkbox("Failures and warnings only", failuresOnly)) failuresOnly = !failuresOnly;
        for (int i = 0; i < results.size(); i++) {
            var result = results.get(i);
            if (failuresOnly && !List.of("FAIL", "WARN").contains(result.state())) continue;
            ImGui.pushID(i);
            if (ImGui.selectable(result.state() + "  " + result.document().id() + " / " + result.name()))
                navigate.accept(new EditorReferences.Location(result.document(), result.pointer()));
            ImGui.textWrapped(result.message());
            ImGui.popID();
        }
        ImGui.separatorText("World checkpoints");
        ImGui.textWrapped("A full checkpoint includes terrain, entities, player data, campaign state and the applied test pack. Restore opens a new world copy with the saved state. Your previous test world remains available.");
        ImGui.inputText(EditorWidgets.field("Checkpoint name"), checkpointName);
        ImGui.beginDisabled(busy || EditorWorldCheckpoints.busy() || !EditorTestWorld.active(workspace));
        if (ImGui.button("Save full world checkpoint")) run.accept(() -> EditorWorldCheckpoints.save(workspace, checkpointName.get(), screen));
        ImGui.endDisabled();
        EditorWidgets.sameLineFor("Refresh checkpoints");
        if (ImGui.button("Refresh checkpoints")) run.accept(() -> checkpoints = EditorWorldCheckpoints.list(EditorWorldCheckpoints.directory(), workspace));
        ImGui.beginDisabled(busy || EditorWorldCheckpoints.busy());
        for (var checkpoint : checkpoints) {
            ImGui.pushID(checkpoint.directory().toString());
            if (ImGui.button("Restore copy: " + checkpoint.name())) run.accept(() -> EditorWorldCheckpoints.open(checkpoint));
            ImGui.popID();
        }
        ImGui.endDisabled();
    }

    private void remember(AuthoringWorkspace workspace) {
        var snapshot = new LinkedHashMap<AuthoringDocument, Long>();
        workspace.documents().forEach(document -> snapshot.put(document, document.revision()));
        tested = Map.copyOf(snapshot);
    }

    private void startLive(AuthoringWorkspace workspace) {
        if (!EditorTestWorld.active(workspace) || !EditorTestWorld.currentLogic(workspace)) throw new IllegalStateException("Apply this workspace in a test world first");
        var minecraft = Minecraft.getInstance();
        var server = minecraft.getSingleplayerServer();
        var selection = com.jvn.villagerretaliation.campaign.selection.WorldCampaignSelection.get(server);
        var tests = new LinkedHashMap<ResourceLocation, JsonObject>(); var sources = new LinkedHashMap<ResourceLocation, AuthoringDocument>();
        for (var document : documents(workspace, scope)) if (document.kind() == DefinitionKind.QUEST) {
            var saved = EditorSchema.object(document.metadata().get("live_scenario_tests"));
            if (saved.isEmpty()) continue;
            String problem = EditorContentTools.playtestProblem(document, selection);
            if (problem != null) continue;
            var id = ResourceLocation.parse(document.id());
            if (tests.putIfAbsent(id, saved) != null) throw new IllegalArgumentException("Multiple active quest files use " + id);
            sources.put(id, document);
        }
        if (tests.isEmpty()) throw new IllegalArgumentException("No saved live tests in the selected scope and active world campaign");
        liveDocuments = Map.copyOf(sources); liveResults = 0; remember(workspace);
        results.removeIf(result -> result.name().startsWith("Live: "));
        liveServer = server; livePlayer = minecraft.player.getUUID();
        var playerId = livePlayer;
        live = server.submit(() -> {
            var player = server.getPlayerList().getPlayer(playerId);
            if (player == null) throw new IllegalStateException("Player left the test world");
            QuestPlaytestSuite.start(player, tests); return QuestPlaytestSuite.view(player);
        });
        status = "Starting live tests for " + tests.size() + " quests in the active campaign.";
    }

    private void poll() {
        if (live != null && live.isDone()) {
            try {
                liveView = live.join(); status = liveView.message();
                for (int i = liveResults; i < liveView.results().size(); i++) {
                    var result = liveView.results().get(i); var document = liveDocuments.get(result.quest());
                    if (document != null) results.add(new Result(document, "", "Live: " + result.name(), result.passed() ? "PASS" : "FAIL", String.join("\n", result.details())));
                }
                liveResults = liveView.results().size();
            } catch (java.util.concurrent.CompletionException exception) {
                status = exception.getCause() == null ? exception.toString() : exception.getCause().getMessage();
                liveView = QuestPlaytestSuite.View.empty();
            }
            live = null; nextPoll = System.currentTimeMillis() + 250;
        }
        if (live == null && liveView.running() && System.currentTimeMillis() >= nextPoll) {
            var server = liveServer; var id = livePlayer;
            if (server == null || server.isStopped()) { liveView = QuestPlaytestSuite.View.empty(); status = "Test world closed."; return; }
            live = server.submit(() -> {
                var player = server.getPlayerList().getPlayer(id);
                return player == null ? new QuestPlaytestSuite.View(false, "Player left the test world", List.of()) : QuestPlaytestSuite.view(player);
            });
        }
    }

    private void cancel() {
        var server = liveServer; var id = livePlayer;
        if (server != null && id != null && !server.isStopped()) server.execute(() -> QuestPlaytestSuite.cancel(server, id));
    }
}
