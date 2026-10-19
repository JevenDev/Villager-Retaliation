package com.jvn.villagerretaliation.progress;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class PlayerHistorySavedData extends SavedData {
    private final Map<UUID, History> players = new HashMap<>();
    private final Set<ResourceLocation> worldDimensions = new HashSet<>();
    private final Set<ResourceLocation> worldAdvancements = new HashSet<>();

    public static PlayerHistorySavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new Factory<>(PlayerHistorySavedData::new, PlayerHistorySavedData::load), "villagerretaliation_player_history");
    }

    public Set<ResourceLocation> dimensions(UUID player) { return historyValues(player, 0); }
    public Set<ResourceLocation> biomes(UUID player) { return historyValues(player, 1); }
    public Set<ResourceLocation> deathCauses(UUID player) { return historyValues(player, 2); }
    public Set<ResourceLocation> worldDimensions() { return Set.copyOf(worldDimensions); }
    public Set<ResourceLocation> worldAdvancements() { return Set.copyOf(worldAdvancements); }
    public Death latestDeath(UUID player) {
        History history = players.get(player);
        return history == null ? null : history.latestDeath;
    }

    private Set<ResourceLocation> historyValues(UUID player, int kind) {
        History history = players.get(player);
        return history == null ? Set.of() : Set.copyOf(switch (kind) {
            case 0 -> history.dimensions;
            case 1 -> history.biomes;
            case 2 -> history.deathCauses;
            default -> throw new IllegalArgumentException("Unknown history collection");
        });
    }

    public void visitDimension(UUID player, ResourceLocation dimension) {
        boolean changed = players.computeIfAbsent(player, ignored -> new History()).dimensions.add(dimension);
        changed |= worldDimensions.add(dimension);
        if (changed) setDirty();
    }

    public void visitBiome(UUID player, ResourceLocation biome) {
        discoverBiome(player, biome);
    }

    public boolean discoverBiome(UUID player, ResourceLocation biome) {
        boolean changed = players.computeIfAbsent(player, ignored -> new History()).biomes.add(biome);
        if (changed) setDirty();
        return changed;
    }

    public void obtainWorldAdvancement(ResourceLocation advancement) {
        if (worldAdvancements.add(advancement)) setDirty();
    }

    public void recordDeath(UUID player, Death death) {
        History history = players.computeIfAbsent(player, ignored -> new History());
        history.deathCauses.add(death.damageType());
        history.latestDeath = death;
        history.pendingDeath = true;
        setDirty();
    }

    public boolean hasPendingDeath(UUID player) {
        History history = players.get(player);
        return history != null && history.pendingDeath && history.latestDeath != null;
    }

    public Death takePendingDeath(UUID player) {
        if (!hasPendingDeath(player)) return null;
        History history = players.get(player);
        history.pendingDeath = false;
        setDirty();
        return history.latestDeath;
    }

    public static PlayerHistorySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new PlayerHistorySavedData();
        data.worldDimensions.addAll(readIds(tag, "WorldDimensions"));
        data.worldAdvancements.addAll(readIds(tag, "WorldAdvancements"));
        for (Tag entry : tag.getList("Players", Tag.TAG_COMPOUND)) {
            var player = (CompoundTag) entry;
            if (!player.hasUUID("Player")) continue;
            var history = new History();
            history.dimensions.addAll(readIds(player, "Dimensions"));
            history.biomes.addAll(readIds(player, "Biomes"));
            history.deathCauses.addAll(readIds(player, "DeathCauses"));
            if (player.contains("LatestDeath", Tag.TAG_COMPOUND)) history.latestDeath = Death.load(player.getCompound("LatestDeath"));
            history.pendingDeath = player.getBoolean("PendingDeath") && history.latestDeath != null;
            data.players.put(player.getUUID("Player"), history);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("WorldDimensions", writeIds(worldDimensions));
        tag.put("WorldAdvancements", writeIds(worldAdvancements));
        var entries = new ListTag();
        players.forEach((id, history) -> {
            var player = new CompoundTag();
            player.putUUID("Player", id);
            player.put("Dimensions", writeIds(history.dimensions));
            player.put("Biomes", writeIds(history.biomes));
            player.put("DeathCauses", writeIds(history.deathCauses));
            player.putBoolean("PendingDeath", history.pendingDeath);
            if (history.latestDeath != null) player.put("LatestDeath", history.latestDeath.save());
            entries.add(player);
        });
        tag.put("Players", entries);
        return tag;
    }

    private static Set<ResourceLocation> readIds(CompoundTag tag, String name) {
        var ids = new HashSet<ResourceLocation>();
        for (Tag value : tag.getList(name, Tag.TAG_STRING)) {
            var id = value.getAsString().isBlank() ? null : ResourceLocation.tryParse(value.getAsString());
            if (id != null) ids.add(id);
        }
        return ids;
    }

    private static ListTag writeIds(Set<ResourceLocation> ids) {
        var list = new ListTag();
        ids.stream().sorted().forEach(id -> list.add(StringTag.valueOf(id.toString())));
        return list;
    }

    private static final class History {
        private final Set<ResourceLocation> dimensions = new HashSet<>();
        private final Set<ResourceLocation> biomes = new HashSet<>();
        private final Set<ResourceLocation> deathCauses = new HashSet<>();
        private Death latestDeath;
        private boolean pendingDeath;
    }

    public record Death(ResourceLocation damageType, ResourceLocation killerType, UUID killerId, String killerName,
            ResourceLocation dimension, BlockPos pos, long gameTime, String message) {
        public Death {
            java.util.Objects.requireNonNull(damageType);
            java.util.Objects.requireNonNull(dimension);
            pos = pos.immutable();
            killerName = killerName == null ? "" : killerName;
            message = message == null ? "" : message;
        }

        public Map<String, String> payload() {
            var values = new java.util.LinkedHashMap<String, String>();
            values.put("damage_type", damageType.toString());
            values.put("killer_type", killerType == null ? "" : killerType.toString());
            values.put("killer_id", killerId == null ? "" : killerId.toString());
            values.put("killer_name", killerName);
            values.put("dimension", dimension.toString());
            values.put("x", Integer.toString(pos.getX()));
            values.put("y", Integer.toString(pos.getY()));
            values.put("z", Integer.toString(pos.getZ()));
            values.put("game_time", Long.toString(gameTime));
            values.put("death_message", message);
            return Map.copyOf(values);
        }

        public CompoundTag save() {
            var tag = new CompoundTag();
            tag.putString("DamageType", damageType.toString());
            if (killerType != null) tag.putString("KillerType", killerType.toString());
            if (killerId != null) tag.putUUID("KillerId", killerId);
            tag.putString("KillerName", killerName);
            tag.putString("Dimension", dimension.toString());
            tag.putLong("Position", pos.asLong());
            tag.putLong("GameTime", gameTime);
            tag.putString("Message", message);
            return tag;
        }

        private static Death load(CompoundTag tag) {
            var type = tag.getString("DamageType").isBlank() ? null : ResourceLocation.tryParse(tag.getString("DamageType"));
            var dimension = tag.getString("Dimension").isBlank() ? null : ResourceLocation.tryParse(tag.getString("Dimension"));
            if (type == null || dimension == null) return null;
            return new Death(type, tag.contains("KillerType", Tag.TAG_STRING) ? ResourceLocation.tryParse(tag.getString("KillerType")) : null,
                    tag.hasUUID("KillerId") ? tag.getUUID("KillerId") : null, tag.getString("KillerName"),
                    dimension, BlockPos.of(tag.getLong("Position")), tag.getLong("GameTime"), tag.getString("Message"));
        }
    }
}
