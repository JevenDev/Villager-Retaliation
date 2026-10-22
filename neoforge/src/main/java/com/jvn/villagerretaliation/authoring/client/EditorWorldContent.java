package com.jvn.villagerretaliation.authoring.client;

import com.jvn.villagerretaliation.authoring.core.AuthoringDiagnostic;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.campaign.selection.WorldCampaignCatalog;
import com.jvn.villagerretaliation.campaign.selection.WorldCampaignResources;
import com.jvn.villagerretaliation.campaign.selection.WorldCampaignSelection;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;

final class EditorWorldContent {
    private static final int MAX_FILE_BYTES = 32 * 1024 * 1024;
    private static final long MAX_PACK_BYTES = 256L * 1024 * 1024;

    private EditorWorldContent() { }

    record Selection(String campaign, List<String> addons, boolean defaults) { }
    record Snapshot(Selection selection, Map<String, byte[]> files) { }
    record Source(WorldCampaignSelection.Selection selected, List<Pack> packs, net.minecraft.core.HolderLookup.Provider registries) {
        Source(WorldCampaignSelection.Selection selected, List<Pack> packs) { this(selected, packs, null); }
        CloseableResourceManager open() {
            var opened = new ArrayList<PackResources>();
            try {
                for (var pack : packs) opened.add(pack.open());
                return new MultiPackResourceManager(PackType.SERVER_DATA, opened);
            } catch (RuntimeException exception) {
                opened.forEach(PackResources::close);
                throw exception;
            }
        }
    }
    record Prepared(AuthoringWorkspace workspace, Selection selection, EditorContentBrowser browser, EditorValidationCache validation, EditorGraphModel graph) { }

    static Source source(MinecraftServer server) {
        return new Source(WorldCampaignSelection.get(server), List.copyOf(server.getPackRepository().getSelectedPacks()),
                com.jvn.villagerretaliation.quest.content.reward.QuestRewardRegistryContext.create(server));
    }

    static Selection identify(Source source) {
        try (var resources = source.open()) { return selection(resources, source.selected()); }
    }

    private static Selection selection(ResourceManager resources, WorldCampaignSelection.Selection selected) {
        var catalog = WorldCampaignCatalog.load(resources);
        catalog.validate(selected, false);
        String campaign = selected.id() == null ? "Legacy world (default content)"
                : catalog.require(selected.id(), false).name() + " (" + selected.id() + ")";
        return new Selection(campaign, selected.addons().stream().map(id -> catalog.addons().get(id).name() + " (" + id + ")").toList(),
                selected.id() == null || com.jvn.villagerretaliation.campaign.selection.WorldCampaign.DEFAULT.equals(selected.id())
                        || selected.includeDefaultContent());
    }

    static Snapshot capture(Source source) {
        try (var resources = source.open()) {
            var files = new LinkedHashMap<String, byte[]>();
            long total = 0;
            for (var entry : WorldCampaignResources.authoringResources(resources, source.selected()).entrySet()) {
                String path = "data/" + entry.getKey().getNamespace() + "/" + entry.getKey().getPath();
                try (var input = entry.getValue().open()) {
                    byte[] bytes = input.readNBytes(MAX_FILE_BYTES + 1);
                    if (bytes.length > MAX_FILE_BYTES) throw new IOException("World content file is too large: " + path);
                    total += bytes.length;
                    if (total > MAX_PACK_BYTES) throw new IOException("World content exceeds the 256 MiB authoring import limit");
                    files.put(path, bytes);
                }
            }
            return new Snapshot(selection(resources, source.selected()), java.util.Collections.unmodifiableMap(files));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not copy current world content: " + exception.getMessage(), exception);
        }
    }

    static Prepared prepare(Source source, Path parent, AuthoringWorkspace previous, String locale, Consumer<String> progress) {
        try {
            if (previous != null) { progress.accept("1/4 Saving current drafts"); previous.save(); }
            progress.accept("2/4 Reading selected datapacks");
            var snapshot = capture(source);
            progress.accept("3/4 Creating draft workspace");
            var workspace = create(parent, snapshot);
            workspace.validationRegistries(source.registries());
            progress.accept("4/4 Indexing and checking content");
            var browser = new EditorContentBrowser();
            browser.get(workspace, locale);
            var validation = new EditorValidationCache();
            var diagnostics = validation.get(workspace);
            var graph = new EditorGraphModel();
            var first = firstDocument(workspace);
            if (first != null && !first.kind().campaignManifest() && diagnostics.stream().noneMatch(issue -> issue.resourcePath().equals(first.resourcePath())
                    && issue.severity() == AuthoringDiagnostic.Severity.ERROR)) graph.get(workspace, first, locale);
            return new Prepared(workspace, snapshot.selection(), browser, validation, graph);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not prepare world content: " + exception.getMessage(), exception);
        }
    }

    static AuthoringDocument firstDocument(AuthoringWorkspace workspace) {
        return workspace.documents().stream().filter(value -> value.kind() == DefinitionKind.WORLD_CAMPAIGN).findFirst()
                .orElseGet(() -> workspace.documents().stream().filter(value -> value.kind() == DefinitionKind.QUEST).findFirst().orElse(null));
    }

    static AuthoringWorkspace create(Path parent, Snapshot snapshot) throws IOException {
        Files.createDirectories(parent);
        Path directory = Files.createTempDirectory(parent, "current-world-");
        var workspace = AuthoringWorkspace.create(directory);
        workspace.importResources(snapshot.files());
        workspace.save();
        return workspace;
    }
}
