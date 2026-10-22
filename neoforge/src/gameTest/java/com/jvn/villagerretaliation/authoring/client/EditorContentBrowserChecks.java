package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;

final class EditorContentBrowserChecks {
    private EditorContentBrowserChecks() { }

    static int run() throws IOException {
        readableTitlesUseScopedLocalesAndFallbacks();
        groupingAndSearchKeepEveryMatchAccessible();
        cachedRowsFollowEditsUndoAndMembershipChanges();
        validationCacheTracksDefinitionsAndSupportingFiles();
        worldPreparationOwnsResourcesAndSavesDraftsOffThread();
        benchmarkLargeLibrary();
        return 6;
    }

    private static AuthoringWorkspace workspace() throws IOException { return AuthoringWorkspace.create(Files.createTempDirectory("editor-content-browser")); }
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }

    private static AuthoringDocument quest(AuthoringWorkspace workspace) {
        var quest = new AuthoringDocument(DefinitionKind.QUEST, "data/test/world_campaigns/story/quests/maps/blank_map/quest.json", json("""
                {"id":"test:blank_map","localization_prefix":"quest.maps.blank_map","metadata":{"questline":"cartographers_atlas"},
                "display":{"title":{"key":"#title"}},"hint":"Bring ink and paper"}
                """));
        workspace.add(quest);
        workspace.add(new AuthoringDocument(DefinitionKind.RESOURCE, "data/test/world_campaigns/story/quests/maps/blank_map/locales/en_us.json",
                json("{\"messages\":{\"quest.maps.blank_map.title\":{\"lines\":[\"<b>Blank Map Promise</b>\"]}}}")));
        workspace.createDocument(DefinitionKind.WORLD_CAMPAIGN, "test:story");
        return quest;
    }

    private static void readableTitlesUseScopedLocalesAndFallbacks() throws IOException {
        var workspace = workspace(); var quest = quest(workspace);
        var browser = new EditorContentBrowser();
        var index = browser.get(workspace, "fr_fr");
        var entry = index.entries().stream().filter(value -> value.document() == quest).findFirst().orElseThrow();
        check(entry.title().equals("Blank Map Promise"), "quest titles resolve relative keys with English fallback and remove formatting");
        check(entry.id().equals("test:blank_map") && entry.path().equals(quest.resourcePath()), "readable labels retain full resource identity");
        check(entry.groups().stream().anyMatch(group -> group.label().equals("Cartographers Atlas")), "quests group by readable questline names");
        check(index.entries().stream().anyMatch(value -> value.title().equals("English (United States)")), "translations use language names");
        check(index == browser.get(workspace, "fr_fr"), "idle frames reuse their index");
    }

    private static void groupingAndSearchKeepEveryMatchAccessible() throws IOException {
        var workspace = workspace(); var quest = quest(workspace);
        var browser = new EditorContentBrowser();
        var collapsed = browser.rows(workspace, "en_us", "", true, "All", true, null);
        check(collapsed.rows().size() == 1 && collapsed.rows().getFirst().count() == 3, "collapsed campaign rows show counts instead of hundreds of files");
        var revealed = browser.rows(workspace, "en_us", "", true, "All", true, quest);
        check(revealed.rows().stream().anyMatch(row -> row.entry() != null && row.entry().document() == quest), "selecting a file reveals its ancestors");
        browser.collapse();
        check(browser.rows(workspace, "en_us", "", true, "All", true, quest).rows().size() == 1, "collapse all remains collapsed with an existing selection");
        var search = browser.rows(workspace, "en_us", "INK", false, "Quest", true, quest);
        check(search.matches() == 1 && search.rows().stream().anyMatch(row -> row.entry() != null && row.label().equals("Blank Map Promise")), "body search opens every matching ancestor");
        check(browser.rows(workspace, "en_us", "test:blank_map", false, "All", false, null).matches() == 1, "IDs remain searchable");
        check(browser.rows(workspace, "en_us", "", false, "All", false, null).matches() == 2, "supporting files can be hidden without hiding campaign cards");
        var rows = browser.rows(workspace, "en_us", "", true, "All", false, null);
        check(rows.rows().size() == 3 && rows == browser.rows(workspace, "en_us", "", true, "All", false, null), "unchanged filters reuse the visible row list");
    }

    private static void cachedRowsFollowEditsUndoAndMembershipChanges() throws IOException {
        var workspace = workspace(); var quest = quest(workspace);
        var browser = new EditorContentBrowser(); var first = browser.get(workspace, "en_us");
        var locale = workspace.documents().stream().filter(document -> document.kind() == DefinitionKind.RESOURCE).findFirst().orElseThrow();
        locale.edit("Rename title", root -> root.getAsJsonObject("messages").addProperty("quest.maps.blank_map.title", "A Better Map"));
        check(browser.get(workspace, "en_us") != first && browser.get(workspace, "en_us").entries().stream().anyMatch(entry -> entry.title().equals("A Better Map")), "translation edits invalidate dependent titles");
        locale.undo();
        check(browser.get(workspace, "en_us").entries().stream().anyMatch(entry -> entry.document() == quest && entry.title().equals("Blank Map Promise")), "translation undo restores cached titles");
        var added = workspace.createDocument(DefinitionKind.TREE, "test:a_new_dialogue");
        check(browser.get(workspace, "en_us").entries().stream().anyMatch(entry -> entry.title().equals("A New Dialogue")), "new definitions receive readable fallback names");
        workspace.remove(added);
        check(browser.get(workspace, "en_us").entries().size() == first.entries().size(), "removed files leave the index");
        check(browser.get(workspace(), "en_us").entries().isEmpty(), "a new workspace never reuses another library");
    }

    private static void validationCacheTracksDefinitionsAndSupportingFiles() throws IOException {
        var workspace = workspace(); var document = workspace.createDocument(DefinitionKind.TREE, "test:validation_cache");
        document.edit("Break entry", root -> root.getAsJsonArray("entries").get(0).getAsJsonObject().addProperty("start", "missing"));
        var cache = new EditorValidationCache(); var invalid = cache.get(workspace);
        check(!invalid.isEmpty() && invalid == cache.get(workspace), "unchanged workspaces reuse diagnostics");
        document.editMetadata("Move node", root -> root.addProperty("position", 20));
        check(cache.get(workspace) == invalid, "layout changes do not repeat content validation");
        document.edit("Repair entry", root -> root.getAsJsonArray("entries").get(0).getAsJsonObject().addProperty("start", "start"));
        check(cache.get(workspace).equals(workspace.validate()) && !cache.get(workspace).equals(invalid), "definition edits revalidate the workspace");
        document.undo(); check(cache.get(workspace).equals(invalid), "undo restores the corresponding diagnostics");
        var campaign = workspace.createDocument(DefinitionKind.WORLD_CAMPAIGN, "test:described");
        campaign.edit("External description", root -> root.addProperty("description_file", "test:descriptions/story.md"));
        var external = cache.get(workspace);
        workspace.importResources(Map.of("data/test/descriptions/story.md", "Story".getBytes(StandardCharsets.UTF_8)));
        check(!cache.get(workspace).equals(external) && cache.get(workspace).equals(workspace.validate()), "supporting file changes invalidate description diagnostics");
    }

    private static void worldPreparationOwnsResourcesAndSavesDraftsOffThread() throws IOException {
        var directory = Files.createTempDirectory("editor-world-source");
        Files.writeString(directory.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":48,\"description\":\"Test\"}}");
        var root = directory.resolve("data/test/world_campaigns/story"); Files.createDirectories(root.resolve("dialogue_trees/en_us"));
        Files.writeString(root.resolve("campaign.json"), "{\"schema\":\"villagerretaliation:world_campaign/v1\",\"id\":\"test:story\",\"name\":\"Story\",\"description\":\"Test campaign\"}");
        Files.writeString(root.resolve("dialogue_trees/en_us/hello.json"), "{\"id\":\"test:hello\",\"nodes\":{\"start\":{\"lines\":[\"Hello\"]}},\"entries\":[{\"start\":\"start\"}]}");
        var pack = new Pack(new PackLocationInfo("test", Component.literal("Test"), PackSource.DEFAULT, Optional.empty()),
                new PathPackResources.PathResourcesSupplier(directory), new Pack.Metadata(Component.literal("Test"),
                net.minecraft.server.packs.repository.PackCompatibility.COMPATIBLE, net.minecraft.world.flag.FeatureFlagSet.of(), List.of(), false),
                new PackSelectionConfig(false, Pack.Position.TOP, false));
        var source = new EditorWorldContent.Source(new com.jvn.villagerretaliation.campaign.selection.WorldCampaignSelection.Selection(ResourceLocation.parse("test:story"), false), List.of(pack));
        try (var first = source.open(); var second = source.open()) {
            check(first.listPacks().findFirst().orElseThrow() != second.listPacks().findFirst().orElseThrow(), "imports open independent pack handles");
        }
        var previous = workspace(); var draft = previous.createDocument(DefinitionKind.TREE, "test:keep_draft");
        var progress = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var threads = new java.util.concurrent.CopyOnWriteArrayList<Thread>();
        var executor = Executors.newSingleThreadExecutor();
        try {
            var future = CompletableFuture.supplyAsync(() -> EditorWorldContent.prepare(source, directory.resolve("workspaces"), previous, "en_us", stage -> {
                progress.add(stage); threads.add(Thread.currentThread());
            }), executor);
            var prepared = future.join();
            check(threads.stream().noneMatch(thread -> thread == Thread.currentThread()), "save, import and validation run on the worker");
            check(progress.size() == 4 && progress.getLast().startsWith("4/4"), "loading reports each preparation phase");
            check(AuthoringWorkspace.open(previous.directory()).documents().getFirst().json().equals(draft.json()), "switching libraries preserves earlier drafts");
            check(prepared.workspace().documents().size() == 2 && prepared.browser().get(prepared.workspace(), "en_us").entries().size() == 2, "the returned workspace and browser are ready to adopt");
            check(prepared.validation().get(prepared.workspace()).equals(prepared.workspace().validate()), "prepared diagnostics match full validation");
            Files.writeString(root.resolve("dialogue_trees/en_us/broken.json"), "[");
            try { EditorWorldContent.prepare(source, directory.resolve("workspaces"), previous, "en_us", ignored -> { }); throw new AssertionError("Invalid imports must fail"); }
            catch (java.io.UncheckedIOException expected) { check(previous.documents().size() == 1, "failed imports leave previous drafts usable"); }
        } finally { executor.shutdown(); }
    }

    private static void benchmarkLargeLibrary() throws IOException {
        var workspace = workspace();
        for (int i = 0; i < 1000; i++) {
            var root = new JsonObject(); root.addProperty("id", "test:item_" + i);
            var entries = new com.google.gson.JsonArray();
            for (int row = 0; row < 24; row++) entries.add(json("{\"text\":\"A longer conversation about the current quest and its available routes\",\"weight\":1}"));
            root.add("entries", entries);
            workspace.add(new AuthoringDocument(DefinitionKind.RESOURCE, "data/test/dialogue/en_us/item_" + i + ".json", root));
        }
        var browser = new EditorContentBrowser();
        var rows = browser.rows(workspace, "en_us", "", true, "All", false, null);
        for (int i = 0; i < 100; i++) browser.rows(workspace, "en_us", "", true, "All", false, null);
        long start = System.nanoTime();
        for (int i = 0; i < 200; i++) check(rows == browser.rows(workspace, "en_us", "", true, "All", false, null), "idle frames keep the same rows");
        double cached = (System.nanoTime() - start) / 200.0;
        start = System.nanoTime();
        for (int i = 0; i < 10; i++) workspace.documents().stream().sorted(java.util.Comparator.comparing(document ->
                com.jvn.villagerretaliation.authoring.core.DialogueGraph.string(document.json(), "world_campaign"))).toList();
        double uncached = (System.nanoTime() - start) / 10.0;
        System.out.printf(java.util.Locale.ROOT, "Content browser (1000 files): uncached %.3f ms, cached %.6f ms per frame%n", uncached / 1e6, cached / 1e6);
        check(cached * 10 < uncached, "cached library frames avoid repeated document copies and sorting");
        check(rows.rows().size() == 1000, "large libraries retain every entry for clipped rendering");
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
