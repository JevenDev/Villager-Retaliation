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
    private static final Map<Location, String> restored = new LinkedHashMap<>();
    private static Pending pending;
    private static boolean applying;
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private EditorTestWorld() { }

    static Path snapshot(AuthoringWorkspace workspace) throws IOException {
        Path directory = Files.createTempDirectory("vr-test-world-");
        try {
            com.jvn.villagerretaliation.authoring.core.ManagedPackExport.export(workspace, directory.resolve("test_content"));
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
                var create = CreateWorldScreen.createFromExisting(minecraft, new TitleScreen(), settings, context, pack);
                pending = new Pending(create, workspace.directory());
                minecraft.setScreen(create);
            } catch (IOException | CompletionException exception) {
                try { if (Files.exists(pack)) deleteSnapshot(pack); }
                catch (IOException cleanup) { exception.addSuppressed(cleanup); }
                LOGGER.error("Unable to prepare authoring test world", exception);
                minecraft.setScreen(new AlertScreen(() -> minecraft.setScreen(minecraft.level == null ? new TitleScreen() : editor),
                        Component.literal("Test world setup failed"), Component.literal(exception.getMessage() == null ? exception.toString() : exception.getMessage())));
            }
        }, Component.literal("Create a separate test world?"),
                Component.literal("Your workspace is saved. This leaves the current world and opens a creative test world with test_content enabled. Choose your campaign and addons before creating the world. Live tests execute real actions and rewards there. Return to the editor with F9 after entering.")));
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

    static void tick(AuthoringWorkspace workspace) {
        if (pending != null && workspace != null) session(workspace);
    }

    static String problem(AuthoringWorkspace workspace) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null || workspace == null) return "";
        var location = new Location(server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize(), workspace.directory());
        return restored.getOrDefault(location, "");
    }

    static boolean active(AuthoringWorkspace workspace) {
        return session(workspace) != null;
    }

    static Map<String, JsonObject> appliedContent(AuthoringWorkspace workspace) {
        Session session = session(workspace);
        return session == null ? Map.of() : session.snapshot().content();
    }

    static boolean currentLogic(AuthoringWorkspace workspace) {
        Session session = session(workspace);
        return session != null && session.snapshot().current(workspace, true);
    }

    static java.util.concurrent.CompletableFuture<Void> prepareCurrent(AuthoringWorkspace workspace) throws IOException {
        workspace.save();
        return apply(workspace);
    }

    static boolean applying() { return applying; }

    static java.util.concurrent.CompletableFuture<Void> apply(AuthoringWorkspace workspace) throws IOException {
        return apply(workspace, EditorTestWorld::reload);
    }

    static java.util.concurrent.CompletableFuture<Void> apply(AuthoringWorkspace workspace,
            java.util.function.Function<MinecraftServer, java.util.concurrent.CompletableFuture<Void>> reloader) throws IOException {
        var minecraft = Minecraft.getInstance();
        var server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.level == null || minecraft.player == null) throw new IllegalStateException("Enter a singleplayer world to set up a quest test");
        if (applying) throw new IllegalStateException("A draft is already being applied");
        Path world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        EditorTestSnapshot.checkAssociation(world, workspace.directory());
        EditorTestSnapshot draft = EditorTestSnapshot.capture(workspace);
        Path pack = world.resolve("datapacks/test_content");
        Path backup = com.jvn.villagerretaliation.authoring.core.ManagedPackExport.export(workspace, pack);
        applying = true;
        var playerId = minecraft.player.getUUID();
        var result = new java.util.concurrent.CompletableFuture<Void>();
        server.execute(() -> {
            Runnable resume = com.jvn.villagerretaliation.authoring.preview.QuestPlaytestSession.suspend(server, playerId);
            var baseline = com.jvn.villagerretaliation.quest.content.QuestContentCatalogs.loadReport(server).entries();
            var repository = server.getPackRepository();
            var selected = List.copyOf(repository.getSelectedIds());
            repository.reload();
            var enabled = new ArrayList<>(selected);
            if (!enabled.contains("file/test_content")) enabled.add("file/test_content");
            repository.setSelected(enabled);
            reloader.apply(server).whenComplete((ignored, failure) -> server.execute(() -> {
                Throwable issue = failure;
                if (issue == null) {
                    var report = com.jvn.villagerretaliation.quest.content.QuestContentCatalogs.loadReport(server);
                    var error = report.entries().stream().filter(entry -> entry.severity()
                            == com.jvn.villagerretaliation.quest.content.QuestContentLoadReport.Severity.ERROR && !baseline.contains(entry)).findFirst();
                    if (error.isPresent()) issue = new IllegalStateException(error.get().source() + ": " + error.get().message());
                }
                if (issue == null) {
                    try { EditorTestSnapshot.remember(world, workspace.directory()); }
                    catch (IOException exception) { issue = exception; }
                }
                if (issue == null) {
                    resume.run();
                    minecraft.execute(() -> {
                        sessions.removeIf(existing -> existing.world().equals(world) && existing.workspace().equals(workspace.directory()));
                        sessions.add(new Session(world, workspace.directory(), draft));
                        restored.remove(new Location(world, workspace.directory()));
                        applying = false;
                        result.complete(null);
                    });
                } else rollback(server, minecraft, pack, backup, selected, result, issue, reloader, resume);
            }));
        });
        return result;
    }

    private static java.util.concurrent.CompletableFuture<Void> reload(MinecraftServer server) {
        try { return server.reloadResources(server.getPackRepository().getSelectedIds()); }
        catch (IllegalArgumentException | IllegalStateException exception) { return java.util.concurrent.CompletableFuture.failedFuture(exception); }
    }

    private static void rollback(MinecraftServer server, Minecraft minecraft, Path pack, Path backup, List<String> selected,
            java.util.concurrent.CompletableFuture<Void> result, Throwable failure,
            java.util.function.Function<MinecraftServer, java.util.concurrent.CompletableFuture<Void>> reloader, Runnable resume) {
        try {
            if (backup == null) Files.move(pack, pack.resolveSibling("test_content.rejected-" + java.util.UUID.randomUUID()));
            else com.jvn.villagerretaliation.authoring.core.ManagedPackExport.restore(pack, backup);
            server.getPackRepository().reload();
            server.getPackRepository().setSelected(selected);
        }
        catch (IOException recovery) {
            failure.addSuppressed(recovery);
            resume.run();
            minecraft.execute(() -> { applying = false; result.completeExceptionally(new IllegalStateException("Could not restore the previous test pack: " + recovery.getMessage(), failure)); });
            return;
        }
        reloader.apply(server).whenComplete((ignored, recovery) -> server.execute(() -> {
            resume.run();
            minecraft.execute(() -> {
                if (recovery != null) failure.addSuppressed(recovery);
                applying = false;
                result.completeExceptionally(new IllegalStateException(recovery == null
                        ? (backup == null ? "Original world content restored. " : "Previous test pack restored. ") + failure.getMessage()
                        : "Pack files and selection restored, but the resource reload failed: " + recovery.getMessage(), failure));
            });
        }));
    }

    static boolean currentSnapshot(AuthoringWorkspace workspace) {
        Session session = session(workspace);
        return session != null && session.snapshot().current(workspace, false);
    }

    private static Session session(AuthoringWorkspace workspace) {
        var minecraft = Minecraft.getInstance();
        var server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.level == null || !server.getPackRepository().getSelectedIds().contains("file/test_content")) return null;
        Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        if (pending != null && root.equals(minecraft.getLevelSource().getBaseDir()
                .resolve(pending.screen().getUiState().getTargetFolder()).toAbsolutePath().normalize())) {
            var created = pending;
            pending = null;
            try {
                var snapshot = EditorTestSnapshot.readPack(root.resolve("datapacks/test_content"), created.workspace());
                EditorTestSnapshot.remember(root, created.workspace());
                EditorLiveQuestTest.reset();
                sessions.add(new Session(root, created.workspace(), snapshot));
            } catch (IOException exception) {
                LOGGER.error("Unable to register authoring test world", exception);
                restored.put(new Location(root, created.workspace()), exception.getMessage());
            }
        }
        var existing = sessions.stream().filter(session -> session.workspace().equals(workspace.directory()) && session.world().equals(root)).findFirst().orElse(null);
        var location = new Location(root, workspace.directory());
        if (existing != null || restored.containsKey(location)) return existing;
        restored.put(location, "");
        try {
            var snapshot = EditorTestSnapshot.restore(root, workspace.directory());
            if (snapshot == null) return null;
            EditorLiveQuestTest.reset();
            var session = new Session(root, workspace.directory(), snapshot);
            sessions.add(session);
            return session;
        } catch (IOException exception) {
            LOGGER.error("Unable to restore authoring test world", exception);
            restored.put(location, exception.getMessage());
            return null;
        }
    }

    static void disconnect() {
        sessions.clear();
        restored.clear();
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
    private record Pending(CreateWorldScreen screen, Path workspace) { }
    private record Location(Path world, Path workspace) { }
    private record Session(Path world, Path workspace, EditorTestSnapshot snapshot) { }
}
