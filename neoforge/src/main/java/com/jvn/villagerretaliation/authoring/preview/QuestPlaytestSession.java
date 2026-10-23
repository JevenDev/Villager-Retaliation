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

    public static void bind(ServerPlayer player, ResourceLocation quest, net.minecraft.world.entity.npc.Villager provider, boolean hold) {
        var selected = selection(player);
        if (selected == null || !selected.quest().equals(quest) || !java.util.Objects.equals(selected.provider(), provider.getUUID())) {
            close(player.server, player.getUUID());
            open(player, quest, hold);
        }
        var controls = require(player, quest);
        com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService.leaveActiveSession(
                com.jvn.villagerretaliation.interaction.VillagerInteractionService.createDialogueContext(
                        (net.minecraft.server.level.ServerLevel) provider.level(), player, provider));
        controls.clear();
        controls.hold(hold);
        controls.provider(provider);
    }

    public static void bindDiscovery(ServerPlayer player, ResourceLocation quest, boolean hold) {
        var selected = selection(player);
        if (selected == null || !selected.quest().equals(quest) || selected.provider() != null) {
            close(player.server, player.getUUID());
            open(player, quest, hold);
        }
        var controls = require(player, quest);
        controls.clear();
        controls.hold(hold);
        controls.provider = null;
        controls.dimension = player.level().dimension();
        controls.providerPosition = player.blockPosition();
    }

    public static synchronized Controls controls(ServerPlayer player, ResourceLocation quest) {
        if (player == null) return null;
        var sessions = SESSIONS.get(player.server);
        var session = sessions == null ? null : sessions.get(new Key(player.getUUID(), quest));
        if (session == null || !player.server.getPackRepository().getSelectedIds().contains("file/test_content")
                || !session.world().equals(player.server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize())) return null;
        session.controls().definition(com.jvn.villagerretaliation.quest.VillagerQuestResources.quest(player.server, quest).orElse(null));
        return session.controls();
    }

    public static synchronized Selection selection(ServerPlayer player) {
        if (player == null) return null;
        var sessions = SESSIONS.get(player.server);
        if (sessions == null) return null;
        for (var key : sessions.keySet()) {
            if (!key.player().equals(player.getUUID())) continue;
            var controls = controls(player, key.quest());
            if (controls != null && controls.dimension != null)
                return new Selection(key.quest(), controls.provider, controls.dimension, controls.providerPosition);
        }
        return null;
    }

    public static boolean selected(ServerPlayer player, ResourceLocation quest) {
        var selected = selection(player);
        return selected != null && selected.quest().equals(quest);
    }

    public record Selection(ResourceLocation quest, UUID provider, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, net.minecraft.core.BlockPos position) { }

    public static Controls require(ServerPlayer player, ResourceLocation quest) {
        var controls = controls(player, quest);
        if (controls == null) throw new IllegalStateException("Start a quest playtest in the current world first");
        return controls;
    }

    public static synchronized void close(MinecraftServer server, UUID player) {
        var sessions = SESSIONS.get(server);
        if (sessions != null) {
            sessions.keySet().removeIf(key -> key.player().equals(player));
            if (sessions.isEmpty()) SESSIONS.remove(server);
        }
    }

    public static synchronized Runnable suspend(MinecraftServer server, UUID player) {
        var sessions = SESSIONS.get(server);
        var restore = new java.util.ArrayList<Runnable>();
        if (sessions != null) sessions.forEach((key, session) -> {
            if (key.player().equals(player)) {
                var controls = session.controls();
                boolean prior = controls.suspended;
                controls.suspended = true;
                restore.add(() -> controls.suspended = prior);
            }
        });
        return () -> restore.forEach(Runnable::run);
    }

    public static void recordStage(ServerPlayer player, ResourceLocation quest, String stage) {
        var controls = controls(player, quest); if (controls != null && !stage.isBlank()) controls.visitedStages.add(stage);
    }

    public static void recordChoice(ServerPlayer player, ResourceLocation quest, String pointer) {
        var controls = controls(player, quest); if (controls != null && !pointer.isBlank()) controls.chosenPaths.add(pointer);
    }

    public static boolean suspended(ServerPlayer player, ResourceLocation quest) {
        var controls = controls(player, quest);
        return controls != null && controls.suspended;
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
        private final Set<String> visitedStages = new HashSet<>();
        private final Set<String> chosenPaths = new HashSet<>();
        public PreviewCoverage drainCoverage() {
            var result = new PreviewCoverage(visitedStages, chosenPaths); visitedStages.clear(); chosenPaths.clear(); return result;
        }

        private net.minecraft.core.BlockPos providerPosition;
        private UUID provider;
        private net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        private final Map<String, com.jvn.villagerretaliation.quest.tracking.VillagerQuestTargets.LocatedTarget> targets = new HashMap<>();

        public void provider(net.minecraft.world.entity.npc.Villager villager) {
            providerPosition = villager.blockPosition();
            provider = villager.getUUID();
            dimension = villager.level().dimension();
        }
        public com.jvn.villagerretaliation.quest.tracking.VillagerQuestTargets.LocatedTarget target(String objective) { return targets.get(objective); }
        public void target(com.jvn.villagerretaliation.quest.tracking.VillagerQuestTargets.LocatedTarget target) { targets.put(target.objectiveId(), target); }

        private com.jvn.villagerretaliation.quest.QuestDefinition definition;
        private void definition(com.jvn.villagerretaliation.quest.QuestDefinition next) {
            if (definition == next) return;
            targets.keySet().removeIf(objective -> !java.util.Objects.equals(targetDefinition(definition, objective), targetDefinition(next, objective)));
            definition = next;
        }
        private static TargetDefinition targetDefinition(com.jvn.villagerretaliation.quest.QuestDefinition definition, String objective) {
            if (definition == null) return null;
            if (objective.isBlank()) return new TargetDefinition(definition.target().structure(), definition.target().dimension(), definition.target().pieces());
            return definition.objectives().stream().filter(value -> value.id().equals(objective)).findFirst()
                    .map(value -> new TargetDefinition(value.structure(), value.dimension(), value.pieces())).orElse(null);
        }
        private record TargetDefinition(ResourceLocation structure, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                java.util.List<String> pieces) { }

        private int structureAttempts;
        public int nextStructureAttempt() { return structureAttempts++; }

        private boolean focusDialogue;
        public boolean focusDialogue() { return focusDialogue; }
        public void focusDialogue(boolean value) { focusDialogue = value; }

        private boolean hold = true;
        private boolean suspended;
        private String stepping;
        private final Set<String> readyStages = new HashSet<>();
        private final Set<String> objectives = new HashSet<>();

        private final java.util.List<String> log = new java.util.ArrayList<>();

        public java.util.List<String> log() { return java.util.List.copyOf(log); }
        public void log(String message) { if (message.isBlank()) return; log.add(message); if (log.size() > 64) log.removeFirst(); }

        public boolean hold() { return hold; }
        public void hold(boolean value) { hold = value; }
        public boolean held(String stage) { return suspended || hold && (stepping == null || !stepping.equals(stage)); }
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
