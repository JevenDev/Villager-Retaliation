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
            QuestPlaytestSession.Selection provider, Integer reputation, CompoundTag environment,
            com.jvn.villagerretaliation.quest.discovery.QuestDiscoverySavedData.Offer discovery) { }

    public static String name(String value) {
        String name = value.trim();
        if (name.isEmpty() || name.length() > 128) throw new IllegalArgumentException("Use a baseline name of 1 to 128 characters");
        return name;
    }

    private static LinkedHashMap<String, Snapshot> snapshots(ServerPlayer player, ResourceLocation quest) {
        if (!player.server.isSameThread()) throw new IllegalStateException("Playtest baselines require the server thread");
        if (!player.server.getPackRepository().getSelectedIds().contains("file/test_content"))
            throw new IllegalStateException("Baselines require the editor test pack");
        return BASELINES.computeIfAbsent(player.server, ignored -> new java.util.HashMap<>())
                .computeIfAbsent(new Key(player.getUUID(), quest), ignored -> {
                    var loaded = new LinkedHashMap<String, Snapshot>();
                    var data = QuestPlaytestBaselineData.get(player.server);
                    for (String name : data.names(player.getUUID(), quest)) loaded.put(name, decode(player, quest, data.read(player.getUUID(), quest, name)));
                    return loaded;
                });
    }

    public static List<String> names(ServerPlayer player, ResourceLocation quest) { return List.copyOf(snapshots(player, quest).keySet()); }

    public static void capture(ServerPlayer player, ResourceLocation quest, String value) {
        String name = name(value);
        var saved = snapshots(player, quest);
        if (!saved.containsKey(name) && saved.size() >= 8) throw new IllegalStateException("Delete a baseline before saving another. Each quest supports eight baselines.");
        var snapshot = snapshot(player, quest);
        QuestPlaytestBaselineData.get(player.server).put(player.getUUID(), quest, name, encode(player, snapshot));
        saved.put(name, snapshot);
    }

    public static Runnable checkpoint(ServerPlayer player, ResourceLocation quest) {
        var baseline = snapshot(player, quest);
        boolean hold = QuestPlaytestSession.require(player, quest).hold();
        return () -> { prepare(player, quest, baseline); restore(player, quest, baseline); QuestPlaytestSession.require(player, quest).hold(hold); };
    }

    private static Snapshot snapshot(ServerPlayer player, ResourceLocation quest) {
        snapshots(player, quest);
        QuestPlaytestSession.require(player, quest);
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
                progress, scopes, selection, reputation == null ? null : reputation.reputation(), environment(player),
                com.jvn.villagerretaliation.quest.discovery.QuestDiscoverySavedData.get(player.serverLevel()).offers(player.getUUID()).get(quest));
    }

    public static void restore(ServerPlayer player, ResourceLocation quest, String value) {
        var baseline = snapshots(player, quest).get(name(value));
        if (baseline == null) throw new IllegalArgumentException("Choose a saved baseline");
        restore(player, quest, baseline);
    }

    private static void restore(ServerPlayer player, ResourceLocation quest, Snapshot baseline) {
        snapshots(player, quest);
        QuestPlaytestSession.require(player, quest);
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
            restoreEnvironment(player, level, baseline.environment());
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

    public static void remove(ServerPlayer player, ResourceLocation quest, String value) {
        snapshots(player, quest).remove(name(value));
        QuestPlaytestBaselineData.get(player.server).remove(player.getUUID(), quest, name(value));
    }

    public static void prepare(ServerPlayer player, ResourceLocation quest, String name) {
        var baseline = snapshots(player, quest).get(name(name));
        if (baseline == null) throw new IllegalArgumentException("Missing baseline: " + name);
        prepare(player, quest, baseline);
    }

    private static void prepare(ServerPlayer player, ResourceLocation quest, Snapshot baseline) {
        var selection = baseline.provider();
        var level = player.server.getLevel(selection.dimension());
        if (level == null) throw new IllegalStateException("Baseline provider dimension is unavailable");
        level.getChunkAt(selection.position());
        if (selection.provider() == null) QuestPlaytestSession.bindDiscovery(player, quest, true);
        else {
            var provider = com.jvn.villagerretaliation.util.VillagerEntityResolver.loaded(player.server, selection.provider());
            if (provider == null || !provider.isAlive()) throw new IllegalStateException("Baseline provider is no longer available. Restore a world checkpoint or save a new baseline.");
            QuestPlaytestSession.bind(player, quest, provider, true);
        }
    }

    private static CompoundTag environment(ServerPlayer player) {
        var tag = new CompoundTag();
        tag.putLong("DayTime", player.serverLevel().getDayTime());
        tag.putBoolean("Rain", player.serverLevel().isRaining()); tag.putBoolean("Thunder", player.serverLevel().isThundering());
        tag.putInt("Difficulty", player.server.getWorldData().getDifficulty().getId());
        tag.putInt("GameMode", player.gameMode.getGameModeForPlayer().getId());
        tag.putFloat("Health", player.getHealth());
        player.getFoodData().addAdditionalSaveData(tag);
        return tag;
    }

    private static void restoreEnvironment(ServerPlayer player, net.minecraft.server.level.ServerLevel level, CompoundTag tag) {
        if (tag.isEmpty()) return;
        level.setDayTime(tag.getLong("DayTime"));
        level.setWeatherParameters(tag.getBoolean("Rain") ? 0 : 12000, tag.getBoolean("Rain") ? 12000 : 0, tag.getBoolean("Rain"), tag.getBoolean("Thunder"));
        player.server.setDifficulty(net.minecraft.world.Difficulty.byId(tag.getInt("Difficulty")), true);
        player.setGameMode(net.minecraft.world.level.GameType.byId(tag.getInt("GameMode")));
        player.setHealth(tag.getFloat("Health")); player.getFoodData().readAdditionalSaveData(tag);
    }

    private static CompoundTag encode(ServerPlayer player, Snapshot value) {
        var tag = new CompoundTag();
        tag.putInt("Version", 1); tag.putString("Dimension", value.dimension().location().toString());
        tag.putDouble("X", value.position().x); tag.putDouble("Y", value.position().y); tag.putDouble("Z", value.position().z);
        tag.putFloat("Yaw", value.yaw()); tag.putFloat("Pitch", value.pitch());
        var inventory = new net.minecraft.nbt.ListTag();
        for (var stack : value.inventory()) inventory.add(stack.saveOptional(player.registryAccess()));
        tag.put("Inventory", inventory); tag.putInt("Selected", value.selected());
        tag.putInt("ExperienceLevel", value.experienceLevel()); tag.putInt("TotalExperience", value.totalExperience());
        tag.putFloat("ExperienceProgress", value.experienceProgress()); tag.put("Progress", value.progress().copy());
        var facts = new CompoundTag();
        value.facts().forEach((scope, snapshot) -> {
            var entry = new CompoundTag(); var tags = new net.minecraft.nbt.ListTag();
            snapshot.tags().forEach(id -> tags.add(net.minecraft.nbt.StringTag.valueOf(id.toString())));
            entry.put("Tags", tags);
            var variables = new CompoundTag(); snapshot.variables().forEach(variables::putString); entry.put("Variables", variables);
            var counters = new CompoundTag(); snapshot.counters().forEach(counters::putInt); entry.put("Counters", counters);
            facts.put(scope, entry);
        });
        tag.put("Facts", facts);
        tag.putString("ProviderDimension", value.provider().dimension().location().toString());
        tag.putLong("ProviderPosition", value.provider().position().asLong());
        if (value.provider().provider() != null) tag.putUUID("Provider", value.provider().provider());
        if (value.reputation() != null) tag.putInt("Reputation", value.reputation());
        tag.put("Environment", value.environment().copy());
        if (value.discovery() != null) {
            var discovery = new CompoundTag(); discovery.putString("Route", value.discovery().discovery());
            if (value.discovery().provider() != null) discovery.putUUID("Provider", value.discovery().provider());
            var payload = new CompoundTag(); value.discovery().payload().forEach(payload::putString);
            discovery.put("Payload", payload); tag.put("Discovery", discovery);
        }
        return tag;
    }

    private static Snapshot decode(ServerPlayer player, ResourceLocation quest, CompoundTag tag) {
        if (tag.getInt("Version") != 1) throw new IllegalStateException("Unsupported playtest baseline version");
        var inventory = new java.util.ArrayList<ItemStack>();
        var saved = tag.getList("Inventory", net.minecraft.nbt.Tag.TAG_COMPOUND);
        if (saved.size() != player.getInventory().getContainerSize()) throw new IllegalStateException("Baseline inventory size changed");
        for (int i = 0; i < saved.size(); i++) inventory.add(ItemStack.parseOptional(player.registryAccess(), saved.getCompound(i)));
        var facts = new LinkedHashMap<String, VillagerQuestFacts.FactSnapshot>();
        var scopes = tag.getCompound("Facts");
        for (String scope : scopes.getAllKeys()) {
            var entry = scopes.getCompound(scope); var tags = new java.util.HashSet<ResourceLocation>();
            var ids = entry.getList("Tags", net.minecraft.nbt.Tag.TAG_STRING);
            for (int i = 0; i < ids.size(); i++) tags.add(ResourceLocation.parse(ids.getString(i)));
            var variables = new LinkedHashMap<String, String>(); var counters = new LinkedHashMap<String, Integer>();
            var values = entry.getCompound("Variables"); values.getAllKeys().forEach(key -> variables.put(key, values.getString(key)));
            var counts = entry.getCompound("Counters"); counts.getAllKeys().forEach(key -> counters.put(key, counts.getInt(key)));
            facts.put(scope, new VillagerQuestFacts.FactSnapshot(tags, variables, counters));
        }
        com.jvn.villagerretaliation.quest.discovery.QuestDiscoverySavedData.Offer discovery = null;
        if (tag.contains("Discovery")) {
            var entry = tag.getCompound("Discovery"); var payload = new LinkedHashMap<String, String>();
            var values = entry.getCompound("Payload"); values.getAllKeys().forEach(key -> payload.put(key, values.getString(key)));
            discovery = new com.jvn.villagerretaliation.quest.discovery.QuestDiscoverySavedData.Offer(entry.getString("Route"),
                    entry.hasUUID("Provider") ? entry.getUUID("Provider") : null, payload);
        }
        return new Snapshot(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, ResourceLocation.parse(tag.getString("Dimension"))),
                new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")), tag.getFloat("Yaw"), tag.getFloat("Pitch"),
                inventory, tag.getInt("Selected"), tag.getInt("ExperienceLevel"), tag.getInt("TotalExperience"), tag.getFloat("ExperienceProgress"),
                tag.getCompound("Progress").copy(), facts,
                new QuestPlaytestSession.Selection(quest, tag.hasUUID("Provider") ? tag.getUUID("Provider") : null,
                        ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, ResourceLocation.parse(tag.getString("ProviderDimension"))),
                        net.minecraft.core.BlockPos.of(tag.getLong("ProviderPosition"))),
                tag.contains("Reputation") ? tag.getInt("Reputation") : null, tag.getCompound("Environment").copy(), discovery);
    }
    public static synchronized void clear(MinecraftServer server, UUID player) {
        var saved = BASELINES.get(server);
        if (saved != null) {
            saved.keySet().removeIf(key -> key.player().equals(player));
            if (saved.isEmpty()) BASELINES.remove(server);
        }
    }
}
