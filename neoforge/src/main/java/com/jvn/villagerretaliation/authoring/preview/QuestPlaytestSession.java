package com.jvn.villagerretaliation.authoring.preview;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

public final class QuestPlaytestSession {
    private static final Map<MinecraftServer, Map<Key, Session>> SESSIONS = new WeakHashMap<>();

    private QuestPlaytestSession() { }

    public static synchronized void open(ServerPlayer player, ResourceLocation quest, boolean hold) {
        var server = player.server;
        if (!server.getPackRepository().getSelectedIds().contains("file/test_content"))
            throw new IllegalStateException("Live playtesting requires the editor test pack");
        var controls = new Controls();
        controls.hold(hold);
        SESSIONS.computeIfAbsent(server, ignored -> new HashMap<>()).put(new Key(player.getUUID(), quest),
                new Session(server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize(), controls));
    }

    public static synchronized Controls controls(ServerPlayer player, ResourceLocation quest) {
        if (player == null) return null;
        var sessions = SESSIONS.get(player.server);
        var session = sessions == null ? null : sessions.get(new Key(player.getUUID(), quest));
        if (session == null || !player.server.getPackRepository().getSelectedIds().contains("file/test_content")
                || !session.world().equals(player.server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize())) return null;
        return session.controls();
    }

    public static Controls require(ServerPlayer player, ResourceLocation quest) {
        var controls = controls(player, quest);
        if (controls == null) throw new IllegalStateException("Start a quest playtest in the editor test world first");
        return controls;
    }

    public static synchronized void close(MinecraftServer server, UUID player) {
        var sessions = SESSIONS.get(server);
        if (sessions != null) {
            sessions.keySet().removeIf(key -> key.player().equals(player));
            if (sessions.isEmpty()) SESSIONS.remove(server);
        }
    }

    public static boolean held(ServerPlayer player, ResourceLocation quest, String stage) {
        var controls = controls(player, quest);
        return controls != null && controls.held(stage);
    }

    public static boolean forced(ServerPlayer player, ResourceLocation quest, String stage, String objective) {
        var controls = controls(player, quest);
        return controls != null && controls.forced(stage, objective);
    }

    public static boolean stageForced(ServerPlayer player, ResourceLocation quest, String stage) {
        var controls = controls(player, quest);
        return controls != null && controls.stageForced(stage);
    }

    private record Key(UUID player, ResourceLocation quest) { }
    private record Session(Path world, Controls controls) { }

    public static final class Controls {
        private boolean hold = true;
        private String stepping;
        private final Set<String> readyStages = new HashSet<>();
        private final Set<String> objectives = new HashSet<>();

        private final java.util.List<String> log = new java.util.ArrayList<>();

        public java.util.List<String> log() { return java.util.List.copyOf(log); }
        public void log(String message) { if (message.isBlank()) return; log.add(message); if (log.size() > 64) log.removeFirst(); }

        public boolean hold() { return hold; }
        public void hold(boolean value) { hold = value; }
        public boolean held(String stage) { return hold && (stepping == null || !stepping.equals(stage)); }
        public void advance(String stage, Runnable action) {
            if (stepping != null) throw new IllegalStateException("A quest step is already advancing");
            stepping = stage;
            try { action.run(); } finally { stepping = null; }
        }
        public void clear() { objectives.clear(); readyStages.clear(); }
        public void force(String stage, String objective) {
            objectives.add(objective);
        }
        public void forceStage(String stage) { readyStages.add(stage); }
        public boolean forced(String stage, String objective) { return readyStages.contains(stage) || objectives.contains(objective); }
        public boolean stageForced(String stage) { return readyStages.contains(stage); }
    }
}
