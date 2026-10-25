package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;

final class EditorWorldCheckpoints {
    record Checkpoint(Path directory, String name, String workspace) { }
    private static boolean busy;

    static boolean busy() { return busy; }
    static Path directory() { return Minecraft.getInstance().gameDirectory.toPath().resolve("authoring-world-checkpoints"); }

    static List<Checkpoint> list(Path root, AuthoringWorkspace workspace) throws IOException {
        rejectLinks(root);
        if (!Files.exists(root)) return List.of();
        var result = new java.util.ArrayList<Checkpoint>();
        try (var paths = Files.list(root)) {
            for (var path : paths.sorted().toList()) {
                rejectLinks(path);
                Path manifest = path.resolve("checkpoint.json");
                if (!Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS)) continue;
                if (Files.size(manifest) > 16384) throw new IOException("Invalid checkpoint manifest: " + path);
                try {
                    var value = JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
                    if (value.get("version").getAsInt() != 1) throw new IOException("Unsupported checkpoint version: " + path);
                    String owner = value.get("workspace").getAsString();
                    if (owner.equals(workspace.directory().toAbsolutePath().normalize().toString()))
                        result.add(new Checkpoint(path, value.get("name").getAsString(), owner));
                } catch (com.google.gson.JsonParseException | IllegalArgumentException | IllegalStateException | NullPointerException exception) {
                    throw new IOException("Invalid checkpoint manifest: " + path, exception);
                }
            }
        }
        return List.copyOf(result);
    }

    static Checkpoint capture(Path world, Path root, String name, AuthoringWorkspace workspace) throws IOException {
        if (name.isBlank() || name.length() > 128) throw new IllegalArgumentException("Use a checkpoint name of 1 to 128 characters");
        rejectLinks(root);
        Path target = root.resolve(UUID.randomUUID().toString());
        copyWorld(world, target.resolve("world"));
        var metadata = new JsonObject(); metadata.addProperty("version", 1); metadata.addProperty("name", name);
        String owner = workspace.directory().toAbsolutePath().normalize().toString(); metadata.addProperty("workspace", owner);
        Files.writeString(target.resolve("checkpoint.json"), metadata.toString(), java.nio.file.StandardOpenOption.CREATE_NEW);
        return new Checkpoint(target, name, owner);
    }

    static Path restore(Checkpoint checkpoint, Path saves) throws IOException {
        rejectLinks(saves); rejectLinks(checkpoint.directory());
        Path target = saves.resolve("AuthoringReset-" + UUID.randomUUID());
        copyWorld(checkpoint.directory().resolve("world"), target);
        return target;
    }

    static void copyWorld(Path source, Path target) throws IOException {
        source = source.toAbsolutePath().normalize(); target = target.toAbsolutePath().normalize();
        rejectLinks(source); rejectLinks(target);
        if (source.startsWith(target) || target.startsWith(source)) throw new IOException("A checkpoint must be outside its source world");
        if (!Files.isRegularFile(source.resolve("level.dat"), LinkOption.NOFOLLOW_LINKS)) throw new IOException("The source is not a saved world");
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Checkpoint destination already exists");
        var files = new java.util.ArrayList<Path>();
        try (var paths = Files.walk(source)) {
            for (var path : paths.toList()) {
                rejectLinks(path);
                if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("Unsupported world file: " + path);
                files.add(path);
            }
        }
        Files.createDirectories(target);
        for (var path : files) {
            var relative = source.relativize(path);
            if (relative.toString().equals("session.lock")) continue;
            var destination = target.resolve(relative);
            if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(destination);
            else Files.copy(path, destination);
        }
    }

    private static void rejectLinks(Path path) throws IOException {
        for (Path current = path.toAbsolutePath(); current != null; current = current.getParent())
            if (Files.isSymbolicLink(current)) throw new IOException("World checkpoints cannot traverse symbolic links: " + current);
    }

    static void save(AuthoringWorkspace workspace, String name, net.minecraft.client.gui.screens.Screen editor) throws IOException {
        var minecraft = Minecraft.getInstance(); var server = minecraft.getSingleplayerServer();
        if (busy || server == null || !EditorTestWorld.active(workspace)) throw new IllegalStateException("Set up a singleplayer test world first");
        if (name.isBlank() || name.length() > 128) throw new IllegalArgumentException("Use a checkpoint name of 1 to 128 characters");
        workspace.save();
        Path world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (!confirmed) { minecraft.setScreen(editor); return; }
            busy = true;
            EditorLiveQuestTest.stop();
            minecraft.level.disconnect();
            minecraft.disconnect(new GenericMessageScreen(Component.literal("Saving world checkpoint...")));
            CompletableFuture.supplyAsync(() -> {
                try { return capture(world, directory(), name, workspace); }
                catch (IOException exception) { throw new CompletionException(exception); }
            }, net.minecraft.Util.ioPool()).whenComplete((checkpoint, error) -> minecraft.execute(() -> {
                busy = false;
                if (error != null) failure(error);
                else minecraft.createWorldOpenFlows().openWorld(world.getFileName().toString(), () -> minecraft.setScreen(new TitleScreen()));
            }));
        }, Component.literal("Save full world checkpoint?"),
                Component.literal("This saves and closes the world, copies all dimensions, entities and player data, then reopens it. Return to the editor with F9. Restoring a checkpoint creates a separate world copy.")));
    }

    static void open(Checkpoint checkpoint) {
        var minecraft = Minecraft.getInstance();
        if (busy) throw new IllegalStateException("Wait for the current checkpoint operation");
        busy = true;
        EditorLiveQuestTest.stop();
        if (minecraft.level != null) minecraft.level.disconnect();
        minecraft.disconnect(new GenericMessageScreen(Component.literal("Restoring world checkpoint...")));
        CompletableFuture.supplyAsync(() -> {
            try { return restore(checkpoint, minecraft.getLevelSource().getBaseDir()); }
            catch (IOException exception) { throw new CompletionException(exception); }
        }, net.minecraft.Util.ioPool()).whenComplete((world, error) -> minecraft.execute(() -> {
            busy = false;
            if (error != null) failure(error);
            else minecraft.createWorldOpenFlows().openWorld(world.getFileName().toString(), () -> minecraft.setScreen(new TitleScreen()));
        }));
    }

    private static void failure(Throwable error) {
        var cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        com.mojang.logging.LogUtils.getLogger().error("World checkpoint failed", cause);
        Minecraft.getInstance().setScreen(new AlertScreen(() -> Minecraft.getInstance().setScreen(new TitleScreen()),
                Component.literal("World checkpoint failed"), Component.literal(cause.getMessage() == null ? cause.toString() : cause.getMessage())));
    }
}
