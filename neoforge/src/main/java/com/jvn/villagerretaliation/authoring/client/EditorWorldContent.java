package com.jvn.villagerretaliation.authoring.client;

import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.campaign.selection.WorldCampaignCatalog;
import com.jvn.villagerretaliation.campaign.selection.WorldCampaignResources;
import com.jvn.villagerretaliation.campaign.selection.WorldCampaignSelection;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.MinecraftServer;

final class EditorWorldContent {
    private static final int MAX_FILE_BYTES = 32 * 1024 * 1024;
    private static final long MAX_PACK_BYTES = 256L * 1024 * 1024;

    private EditorWorldContent() { }

    record Selection(String campaign, List<String> addons, boolean defaults) { }
    record Snapshot(Selection selection, Map<String, byte[]> files) { }

    static Selection selection(MinecraftServer server) {
        var selected = WorldCampaignSelection.get(server);
        var catalog = WorldCampaignCatalog.load(server.getResourceManager());
        catalog.validate(selected, false);
        String campaign = selected.id() == null ? "Legacy world (default content)"
                : catalog.require(selected.id(), false).name() + " (" + selected.id() + ")";
        return new Selection(campaign, selected.addons().stream().map(id -> catalog.addons().get(id).name() + " (" + id + ")").toList(),
                selected.id() == null || com.jvn.villagerretaliation.campaign.selection.WorldCampaign.DEFAULT.equals(selected.id())
                        || selected.includeDefaultContent());
    }

    static Snapshot capture(MinecraftServer server) {
        try {
            var files = new LinkedHashMap<String, byte[]>();
            long total = 0;
            for (var entry : WorldCampaignResources.authoringResources(server.getResourceManager(), WorldCampaignSelection.get(server)).entrySet()) {
                String path = "data/" + entry.getKey().getNamespace() + "/" + entry.getKey().getPath();
                try (var input = entry.getValue().open()) {
                    byte[] bytes = input.readNBytes(MAX_FILE_BYTES + 1);
                    if (bytes.length > MAX_FILE_BYTES) throw new IOException("World content file is too large: " + path);
                    total += bytes.length;
                    if (total > MAX_PACK_BYTES) throw new IOException("World content exceeds the 256 MiB authoring import limit");
                    files.put(path, bytes);
                }
            }
            return new Snapshot(selection(server), java.util.Collections.unmodifiableMap(files));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not copy current world content", exception);
        }
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
