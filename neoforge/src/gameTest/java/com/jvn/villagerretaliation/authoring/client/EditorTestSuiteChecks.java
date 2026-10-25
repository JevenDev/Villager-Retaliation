package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

final class EditorTestSuiteChecks {
    static int run() throws IOException {
        suitesCombineFilesAndKeepSourceLocations();
        campaignSuitesStayInsideTheirScope();
        checkpointsRestoreExactCopiesWithoutOverwriting();
        checkpointFailuresPreserveExistingFiles();
        baselineStorageRoundTripsAndSeparatesOwners();
        return 5;
    }

    static void scenario(AuthoringDocument document, String name, String target) {
        document.editMetadata("Save scenario", metadata -> {
            var scenarios = EditorSchema.object(metadata.get("preview_scenarios"));
            scenarios.add(name, new com.jvn.villagerretaliation.authoring.preview.PreviewConditions().snapshot());
            metadata.add("preview_scenarios", scenarios);
            var tests = EditorSchema.object(metadata.get("preview_scenario_tests"));
            tests.add(name, EditorScenarioTests.config("start", "Node or stage", target, List.of()));
            metadata.add("preview_scenario_tests", tests);
        });
    }

    private static void suitesCombineFilesAndKeepSourceLocations() throws IOException {
        var workspace = AuthoringWorkspace.create(Files.createTempDirectory("suite-check"));
        var first = workspace.createDocument(DefinitionKind.TREE, "test:first");
        var second = workspace.createDocument(DefinitionKind.TREE, "test:second");
        scenario(first, "Entry", "start"); scenario(second, "Wrong branch", "missing");
        var original = second.json(); var metadata = second.metadata();
        var coverage = new EditorCoverage();
        var results = EditorTestSuite.run(workspace, "", "en_us", coverage);
        check(results.stream().anyMatch(result -> result.document() == first && result.state().equals("PASS")), "workspace suite runs the first file");
        check(results.stream().anyMatch(result -> result.document() == second && result.state().equals("FAIL") && result.pointer().equals("/nodes/start")), "failed results link to the actual source node");
        check(second.json().equals(original) && second.metadata().equals(metadata), "running tests preserves authored content and setup");
        check(coverage.visited(first, "/nodes/start"), "suite coverage reaches the campaign overview");
    }

    private static void campaignSuitesStayInsideTheirScope() throws IOException {
        var workspace = AuthoringWorkspace.create(Files.createTempDirectory("suite-scope-check"));
        var campaign = workspace.createDocument(DefinitionKind.WORLD_CAMPAIGN, "test:campaign");
        var included = workspace.createDocument(DefinitionKind.TREE, "test:inside", campaign);
        var excluded = workspace.createDocument(DefinitionKind.TREE, "test:outside");
        scenario(included, "Entry", "start"); scenario(excluded, "Entry", "missing");
        var results = EditorTestSuite.run(workspace, DefinitionKind.contentRoot(campaign.resourcePath()), "en_us", new EditorCoverage());
        check(results.stream().anyMatch(result -> result.document() == included && result.state().equals("PASS"))
                && results.stream().noneMatch(result -> result.document() == excluded), "campaign filters apply to scenarios and validation");
        workspace.save();
        var reopened = AuthoringWorkspace.open(workspace.directory());
        check(EditorTestSuite.run(reopened, DefinitionKind.contentRoot(campaign.resourcePath()), "en_us", new EditorCoverage())
                .stream().anyMatch(result -> result.name().equals("Entry") && result.state().equals("PASS")), "saved setups survive reopening");
    }

    private static void checkpointsRestoreExactCopiesWithoutOverwriting() throws IOException {
        var root = Files.createTempDirectory("world-checkpoint-check");
        var workspace = AuthoringWorkspace.create(root.resolve("drafts"));
        var world = Files.createDirectories(root.resolve("original"));
        Files.writeString(world.resolve("level.dat"), "saved metadata"); Files.writeString(world.resolve("session.lock"), "lock");
        var region = Files.createDirectories(world.resolve("DIM-1/region"));
        Files.write(region.resolve("r.0.0.mca"), new byte[] {1, 2, 3});
        var checkpoint = EditorWorldCheckpoints.capture(world, root.resolve("checkpoints"), "Before", workspace);
        Files.write(region.resolve("r.0.0.mca"), new byte[] {4, 5});
        var copy = EditorWorldCheckpoints.restore(checkpoint, root.resolve("saves"));
        check(java.util.Arrays.equals(Files.readAllBytes(copy.resolve("DIM-1/region/r.0.0.mca")), new byte[] {1, 2, 3}), "full copies restore dimension data exactly");
        check(java.util.Arrays.equals(Files.readAllBytes(region.resolve("r.0.0.mca")), new byte[] {4, 5}), "restoring preserves the previous test world");
        check(!Files.exists(copy.resolve("session.lock")), "restored worlds acquire their own session locks");
        check(EditorWorldCheckpoints.list(root.resolve("checkpoints"), workspace).size() == 1, "checkpoints remain discoverable across sessions");
    }

    private static void checkpointFailuresPreserveExistingFiles() throws IOException {
        var root = Files.createTempDirectory("checkpoint-guard-check");
        var world = Files.createDirectories(root.resolve("world")); Files.writeString(world.resolve("level.dat"), "original");
        var target = Files.createDirectories(root.resolve("occupied")); Files.writeString(target.resolve("kept"), "user data");
        try { EditorWorldCheckpoints.copyWorld(world, target); throw new AssertionError("Existing destination accepted"); }
        catch (IOException expected) { }
        try { EditorWorldCheckpoints.copyWorld(world, world.resolve("nested")); throw new AssertionError("Nested destination accepted"); }
        catch (IOException expected) { }
        check(Files.readString(target.resolve("kept")).equals("user data") && !Files.exists(world.resolve("nested")), "invalid copies leave source and destinations unchanged");
    }

    private static void baselineStorageRoundTripsAndSeparatesOwners() {
        var data = new com.jvn.villagerretaliation.authoring.preview.QuestPlaytestBaselineData();
        var player = java.util.UUID.randomUUID(); var quest = net.minecraft.resources.ResourceLocation.parse("test:quest");
        var tag = new net.minecraft.nbt.CompoundTag(); tag.putString("Value", "before");
        data.put(player, quest, "Start", tag); tag.putString("Value", "after");
        var reloaded = com.jvn.villagerretaliation.authoring.preview.QuestPlaytestBaselineData.load(data.save(new net.minecraft.nbt.CompoundTag(), null), null);
        check(reloaded.read(player, quest, "Start").getString("Value").equals("before"), "persisted baselines own their data");
        check(reloaded.names(java.util.UUID.randomUUID(), quest).isEmpty(), "baselines are isolated by player");
        reloaded.remove(player, quest, "Start");
        check(reloaded.names(player, quest).isEmpty() && data.names(player, quest).size() == 1, "deleting a loaded baseline does not mutate the saved snapshot");
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
