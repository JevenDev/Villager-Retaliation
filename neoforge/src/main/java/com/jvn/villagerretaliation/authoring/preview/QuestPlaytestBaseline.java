package com.jvn.villagerretaliation.authoring.preview;

import com.jvn.villagerretaliation.quest.QuestScopeKey;
import com.jvn.villagerretaliation.quest.VillagerQuestFacts;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.reputation.VillagerReputationSavedData;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class QuestPlaytestBaseline {
    private static final Map<MinecraftServer, Map<Key, LinkedHashMap<String, Snapshot>>> BASELINES = new WeakHashMap<>();
    private QuestPlaytestBaseline() { }
    private record Key(UUID player, ResourceLocation quest) { }
    private record Snapshot(ResourceKey<Level> dimension, Vec3 position, float yaw, float pitch,
            List<ItemStack> inventory, int selected, int experienceLevel, int totalExperience, float experienceProgress,
            CompoundTag progress, Map<String, VillagerQuestFacts.FactSnapshot> facts,
            QuestPlaytestSession.Selection provider, Integer reputation,
            com.jvn.villagerretaliation.quest.discovery.QuestDiscoverySavedData.Offer discovery) { }

    public static String name(String value) {
        String name = value.trim();
        if (name.isEmpty() || name.length() > 128) throw new IllegalArgumentException("Use a baseline name of 1 to 128 characters");
        return name;
    }

    private static LinkedHashMap<String, Snapshot> snapshots(ServerPlayer player, ResourceLocation quest) {
        if (!player.server.isSameThread()) throw new IllegalStateException("Playtest baselines require the server thread");
        QuestPlaytestSession.require(player, quest);
        return BASELINES.computeIfAbsent(player.server, ignored -> new java.util.HashMap<>())
                .computeIfAbsent(new Key(player.getUUID(), quest), ignored -> new LinkedHashMap<>());
    }

    public static List<String> names(ServerPlayer player, ResourceLocation quest) { return List.copyOf(snapshots(player, quest).keySet()); }

    public static void capture(ServerPlayer player, ResourceLocation quest, String value) {
        String name = name(value);
        var saved = snapshots(player, quest);
        if (!saved.containsKey(name) && saved.size() >= 8) throw new IllegalStateException("Delete a baseline before saving another. Each quest supports eight baselines.");
        saved.put(name, snapshot(player, quest));
    }

    public static Runnable checkpoint(ServerPlayer player, ResourceLocation quest) {
        var baseline = snapshot(player, quest);
        boolean hold = QuestPlaytestSession.require(player, quest).hold();
        return () -> { restore(player, quest, baseline); QuestPlaytestSession.require(player, quest).hold(hold); };
    }

    private static Snapshot snapshot(ServerPlayer player, ResourceLocation quest) {
        snapshots(player, quest);
        var selection = QuestPlaytestSession.selection(player);
        if (selection == null || !selection.quest().equals(quest)) throw new IllegalStateException("Prepare a quest offer before saving a baseline");
        var progress = VillagerQuestSavedData.get(player.serverLevel()).debugSnapshotQuest(player.getUUID(), quest);
        var facts = VillagerQuestFacts.get(player.serverLevel());
        var scopes = Map.of(QuestScopeKey.player(player.getUUID()).asString(), facts.debugSnapshot(QuestScopeKey.player(player.getUUID()).asString()),
                QuestScopeKey.quest(player.getUUID(), quest).asString(), facts.debugSnapshot(QuestScopeKey.quest(player.getUUID(), quest).asString()));
        var reputation = selection.provider() == null ? null : VillagerReputationSavedData.get(player.serverLevel()).get(selection.provider(), player.getUUID());
        var inventory = new java.util.ArrayList<ItemStack>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) inventory.add(player.getInventory().getItem(i).copy());
        return new Snapshot(player.level().dimension(), player.position(), player.getYRot(), player.getXRot(),
                inventory, player.getInventory().selected, player.experienceLevel, player.totalExperience, player.experienceProgress,
                progress, scopes, selection, reputation == null ? null : reputation.reputation(),
                com.jvn.villagerretaliation.quest.discovery.QuestDiscoverySavedData.get(player.serverLevel()).offers(player.getUUID()).get(quest));
    }

    public static void restore(ServerPlayer player, ResourceLocation quest, String value) {
        var baseline = snapshots(player, quest).get(name(value));
        if (baseline == null) throw new IllegalArgumentException("Choose a saved baseline");
        restore(player, quest, baseline);
    }

    private static void restore(ServerPlayer player, ResourceLocation quest, Snapshot baseline) {
        snapshots(player, quest);
        var level = player.server.getLevel(baseline.dimension());
        var providerLevel = player.server.getLevel(baseline.provider().dimension());
        if (level == null || providerLevel == null) throw new IllegalStateException("A baseline dimension is unavailable");
        providerLevel.getChunkAt(baseline.provider().position());
        var provider = com.jvn.villagerretaliation.util.VillagerEntityResolver.loaded(player.server, baseline.provider().provider());
        if (baseline.provider().provider() != null && (provider == null || !provider.isAlive()))
            throw new IllegalStateException("The baseline provider is no longer alive or loaded");
        var data = VillagerQuestSavedData.get(player.serverLevel());
        var facts = VillagerQuestFacts.get(player.serverLevel());
        data.debugSnapshotQuest(player.getUUID(), quest);
        var suspend = QuestPlaytestSession.suspend(player.server, player.getUUID());
        try {
            baseline.facts().forEach(facts::debugRestoreScope);
            var restored = baseline.progress().copy();
            var timers = restored.getCompound("Progress").getCompound("Timers");
            for (String key : timers.getAllKeys()) timers.getCompound(key).putLong("UpdatedAt", level.getGameTime());
            data.debugRestoreQuest(player.getUUID(), quest, restored);
            player.closeContainer();
            for (int i = 0; i < baseline.inventory().size(); i++) player.getInventory().setItem(i, baseline.inventory().get(i).copy());
            player.getInventory().selected = baseline.selected();
            player.experienceLevel = baseline.experienceLevel();
            player.totalExperience = baseline.totalExperience();
            player.experienceProgress = baseline.experienceProgress();
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetExperiencePacket(player.experienceProgress, player.totalExperience, player.experienceLevel));
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            var discoveries = com.jvn.villagerretaliation.quest.discovery.QuestDiscoverySavedData.get(player.serverLevel());
            discoveries.remove(player.getUUID(), quest);
            if (baseline.discovery() != null) discoveries.offer(player.getUUID(), quest, baseline.discovery());
            if (provider == null) QuestPlaytestSession.bindDiscovery(player, quest, QuestPlaytestSession.require(player, quest).hold());
            else {
                VillagerReputationSavedData.get(player.serverLevel()).debugRestoreEntry(provider.getUUID(), player.getUUID(), baseline.reputation());
                QuestPlaytestSession.bind(player, quest, provider, QuestPlaytestSession.require(player, quest).hold());
            }
            player.teleportTo(level, baseline.position().x, baseline.position().y, baseline.position().z, baseline.yaw(), baseline.pitch());
            com.jvn.villagerretaliation.quest.VillagerQuestService.refreshTracker(player);
        } finally { suspend.run(); }
    }

    public static void remove(ServerPlayer player, ResourceLocation quest, String value) { snapshots(player, quest).remove(name(value)); }
    public static synchronized void clear(MinecraftServer server, UUID player) {
        var saved = BASELINES.get(server);
        if (saved != null) {
            saved.keySet().removeIf(key -> key.player().equals(player));
            if (saved.isEmpty()) BASELINES.remove(server);
        }
    }
}
