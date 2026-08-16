package com.jvn.villagerretaliation.reputation;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Compact, event-driven counters for advancement conditions that vanilla criteria cannot express.
 */
public final class AdvancementProgressSavedData extends SavedData {
    private static final String DATA_NAME = "villagerretaliation_advancement_progress";
    private static final String TAG_PLAYERS = "Players";
    private static final String TAG_PLAYER = "Player";
    private static final String TAG_BLUEPRINTS = "Blueprints";
    private static final String TAG_WAGES = "Wages";
    private static final String TAG_COMPLETED_VILLAGERS = "CompletedVillagers";
    private static final int MAX_BLUEPRINT_IDS = 256;
    private static final int MAX_COMPLETED_VILLAGERS = 512;

    private final Map<UUID, PlayerProgress> progressByPlayer = new HashMap<>();

    public static AdvancementProgressSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        AdvancementProgressSavedData::new,
                        AdvancementProgressSavedData::load,
                        DataFixTypes.LEVEL),
                DATA_NAME);
    }

    public static AdvancementProgressSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        AdvancementProgressSavedData data = new AdvancementProgressSavedData();
        for (Tag rawPlayer : tag.getList(TAG_PLAYERS, Tag.TAG_COMPOUND)) {
            if (!(rawPlayer instanceof CompoundTag playerTag) || !playerTag.hasUUID(TAG_PLAYER)) {
                continue;
            }
            PlayerProgress progress = new PlayerProgress();
            for (Tag rawBlueprint : playerTag.getList(TAG_BLUEPRINTS, Tag.TAG_STRING)) {
                ResourceLocation id = ResourceLocation.tryParse(rawBlueprint.getAsString());
                if (id != null) {
                    addBounded(progress.blueprints, id, MAX_BLUEPRINT_IDS);
                }
            }
            progress.wages = Math.max(0L, playerTag.getLong(TAG_WAGES));
            for (Tag rawVillager : playerTag.getList(TAG_COMPLETED_VILLAGERS, Tag.TAG_COMPOUND)) {
                if (rawVillager instanceof CompoundTag villagerTag && villagerTag.hasUUID(TAG_PLAYER)) {
                    addBounded(
                            progress.completedVillagers,
                            villagerTag.getUUID(TAG_PLAYER),
                            MAX_COMPLETED_VILLAGERS);
                }
            }
            data.progressByPlayer.put(playerTag.getUUID(TAG_PLAYER), progress);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag players = new ListTag();
        for (Map.Entry<UUID, PlayerProgress> entry : this.progressByPlayer.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID(TAG_PLAYER, entry.getKey());
            PlayerProgress progress = entry.getValue();

            ListTag blueprints = new ListTag();
            progress.blueprints.forEach(id -> blueprints.add(StringTag.valueOf(id.toString())));
            playerTag.put(TAG_BLUEPRINTS, blueprints);
            playerTag.putLong(TAG_WAGES, progress.wages);

            ListTag completedVillagers = new ListTag();
            for (UUID villagerId : progress.completedVillagers) {
                CompoundTag villagerTag = new CompoundTag();
                villagerTag.putUUID(TAG_PLAYER, villagerId);
                completedVillagers.add(villagerTag);
            }
            playerTag.put(TAG_COMPLETED_VILLAGERS, completedVillagers);
            players.add(playerTag);
        }
        tag.put(TAG_PLAYERS, players);
        return tag;
    }

    public int recordBlueprint(UUID playerId, ResourceLocation structureId) {
        if (playerId == null || structureId == null) {
            return 0;
        }
        PlayerProgress progress = progress(playerId);
        if (progress.blueprints.add(structureId)) {
            trim(progress.blueprints, MAX_BLUEPRINT_IDS);
            setDirty();
        }
        return progress.blueprints.size();
    }

    public long addWages(UUID playerId, int amount) {
        if (playerId == null || amount <= 0) {
            return wages(playerId);
        }
        PlayerProgress progress = progress(playerId);
        progress.wages = Math.min(Long.MAX_VALUE, progress.wages + (long) amount);
        setDirty();
        return progress.wages;
    }

    public long wages(UUID playerId) {
        PlayerProgress progress = playerId == null ? null : this.progressByPlayer.get(playerId);
        return progress == null ? 0L : progress.wages;
    }

    public Set<UUID> recordCompletedVillager(UUID playerId, UUID villagerId) {
        if (playerId == null || villagerId == null) {
            return completedVillagers(playerId);
        }
        PlayerProgress progress = progress(playerId);
        if (progress.completedVillagers.add(villagerId)) {
            trim(progress.completedVillagers, MAX_COMPLETED_VILLAGERS);
            setDirty();
        }
        return Set.copyOf(progress.completedVillagers);
    }

    public Set<UUID> completedVillagers(UUID playerId) {
        PlayerProgress progress = playerId == null ? null : this.progressByPlayer.get(playerId);
        return progress == null ? Set.of() : Set.copyOf(progress.completedVillagers);
    }

    private PlayerProgress progress(UUID playerId) {
        return this.progressByPlayer.computeIfAbsent(playerId, ignored -> new PlayerProgress());
    }

    private static <T> void addBounded(LinkedHashSet<T> values, T value, int maximum) {
        values.add(value);
        trim(values, maximum);
    }

    private static <T> void trim(LinkedHashSet<T> values, int maximum) {
        while (values.size() > maximum) {
            values.remove(values.getFirst());
        }
    }

    private static final class PlayerProgress {
        private final LinkedHashSet<ResourceLocation> blueprints = new LinkedHashSet<>();
        private final LinkedHashSet<UUID> completedVillagers = new LinkedHashSet<>();
        private long wages;
    }
}
