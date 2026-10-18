package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelResource;

final class EditorTestWorld {
    private static final List<Session> sessions = new ArrayList<>();
    private static Pending pending;
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private EditorTestWorld() { }

    static Path snapshot(AuthoringWorkspace workspace) throws IOException {
        Path directory = Files.createTempDirectory("vr-test-world-");
        try {
            workspace.exportPack(directory.resolve("test_content"));
            return directory;
        } catch (IOException exception) {
            try { deleteSnapshot(directory); }
            catch (IOException cleanup) { exception.addSuppressed(cleanup); }
            throw exception;
        }
    }

    static void prepare(AuthoringWorkspace workspace, Screen editor) throws IOException {
        workspace.save();
        Path pack = snapshot(workspace);
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            try {
                if (!confirmed) {
                    deleteSnapshot(pack);
                    minecraft.setScreen(editor);
                    return;
                }
                if (minecraft.level != null) minecraft.level.disconnect();
                minecraft.disconnect(new TitleScreen());
                minecraft.setScreen(new GenericMessageScreen(Component.literal("Loading test content...")));
                WorldCreationContext context = load(minecraft, pack);
                var settings = new LevelSettings("Authoring Test", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                        new GameRules(), context.dataConfiguration());
                Map<String, JsonObject> content = new LinkedHashMap<>();
                workspace.documents().forEach(document -> content.put(document.resourcePath(), document.json()));
                var create = CreateWorldScreen.createFromExisting(minecraft, new TitleScreen(), settings, context, pack);
                pending = new Pending(create, workspace.directory(), Map.copyOf(content));
                minecraft.setScreen(create);
            } catch (IOException | CompletionException exception) {
                try { if (Files.exists(pack)) deleteSnapshot(pack); }
                catch (IOException cleanup) { exception.addSuppressed(cleanup); }
                LOGGER.error("Unable to prepare authoring test world", exception);
                minecraft.setScreen(new AlertScreen(() -> minecraft.setScreen(minecraft.level == null ? new TitleScreen() : editor),
                        Component.literal("Test world setup failed"), Component.literal(exception.getMessage() == null ? exception.toString() : exception.getMessage())));
            }
        }, Component.literal("Create a separate test world?"),
                Component.literal("Your workspace is saved. This leaves the current world and opens a creative test world with test_content enabled. Live tests execute real actions and rewards there. Return to the editor with F9 after entering.")));
    }

    private static WorldCreationContext load(Minecraft minecraft, Path pack) {
        var repository = ServerPacksSource.createPackRepository(pack, minecraft.directoryValidator());
        var enabled = new ArrayList<>(WorldDataConfiguration.DEFAULT.dataPacks().getEnabled());
        enabled.add("file/test_content");
        var configuration = new WorldDataConfiguration(new DataPackConfig(enabled, List.of()), WorldDataConfiguration.DEFAULT.enabledFeatures());
        var init = new WorldLoader.InitConfig(new WorldLoader.PackConfig(repository, configuration, false, true),
                Commands.CommandSelection.INTEGRATED, 2);
        var loaded = WorldLoader.load(init, context -> new WorldLoader.DataLoadOutput<>(
                new Loaded(new WorldGenSettings(WorldOptions.defaultWithRandomSeed(), WorldPresets.createNormalWorldDimensions(context.datapackWorldgen())),
                        context.dataConfiguration()), context.datapackDimensions()), (manager, resources, registries, settings) -> {
                    manager.close();
                    return new WorldCreationContext(settings.settings(), registries, resources, settings.configuration());
                }, Util.backgroundExecutor(), minecraft);
        minecraft.managedBlock(loaded::isDone);
        return loaded.join();
    }

    static boolean active(AuthoringWorkspace workspace) {
        return session(workspace) != null;
    }

    static boolean currentSnapshot(AuthoringWorkspace workspace) {
        Session session = session(workspace);
        return session != null && session.content().size() == workspace.documents().size()
                && workspace.documents().stream().allMatch(document -> document.json().equals(session.content().get(document.resourcePath())));
    }

    private static Session session(AuthoringWorkspace workspace) {
        var minecraft = Minecraft.getInstance();
        var server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.level == null || !server.getPackRepository().getSelectedIds().contains("file/test_content")) return null;
        Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        if (pending != null && root.equals(minecraft.getLevelSource().getBaseDir()
                .resolve(pending.screen().getUiState().getTargetFolder()).toAbsolutePath().normalize())) {
            EditorLiveQuestTest.reset();
            sessions.add(new Session(root, pending.workspace(), pending.content()));
            pending = null;
        }
        return sessions.stream().filter(session -> session.workspace().equals(workspace.directory()) && session.world().equals(root)).findFirst().orElse(null);
    }

    static boolean sameWorld(MinecraftServer server, Path expected) {
        return server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().equals(expected);
    }

    private static void deleteSnapshot(Path directory) throws IOException {
        try (var entries = Files.walk(directory)) {
            for (Path path : entries.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }

    private record Loaded(WorldGenSettings settings, WorldDataConfiguration configuration) { }
    private record Pending(CreateWorldScreen screen, Path workspace, Map<String, JsonObject> content) { }
    private record Session(Path world, Path workspace, Map<String, JsonObject> content) { }
}
