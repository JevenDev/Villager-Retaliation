package com.jvn.villagerretaliation.village;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.VillagerGiftPreferences;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

public class VillageEventMemorySavedData extends SavedData {
    private static final String DATA_NAME = "villagerretaliation_village_events";
    private static final int CURRENT_FORMAT_VERSION = 2;
    private static final String TAG_FORMAT_VERSION = "FormatVersion";
    private static final String TAG_ENTRIES = "Entries";
    private static final String TAG_VILLAGE_ENTRIES = "VillageEntries";
    private static final String TAG_VILLAGER_ENTRIES = "VillagerEntries";
    private static final String TAG_VILLAGE_ID = "VillageId";
    private static final String TAG_VILLAGER_ID = "VillagerId";
    private static final String TAG_DIMENSION = "Dimension";
    private static final String TAG_TAG = "Tag";
    private static final String TAG_TAG_ID = "TagId";
    private static final String TAG_GAME_TIME = "GameTime";
    private static final String TAG_X = "X";
    private static final String TAG_Y = "Y";
    private static final String TAG_Z = "Z";
    private static final String TAG_SOURCE_ID = "SourceId";
    private static final String TAG_PLAYER_ID = "PlayerId";
    private static final String TAG_GIFT = "Gift";
    private static final String TAG_CONTAINER_THEFT = "ContainerTheft";
    private static final String TAG_RETALIATION = "Retaliation";
    private static final String TAG_CURED_VILLAGER = "CuredVillager";
    private static final String TAG_KILLED_VILLAGER = "KilledVillager";
    private static final String TAG_VILLAGER_NAME = "VillagerName";
    private static final String TAG_ITEM_NAME = "ItemName";
    private static final String TAG_ITEM_ID = "ItemId";
    private static final String TAG_ITEM_COUNT = "ItemCount";
    private static final String TAG_CONTAINER_NAME = "ContainerName";
    private static final String TAG_LOOT_TABLE = "LootTable";
    private static final String TAG_REACTION = "Reaction";
    private static final String TAG_REPUTATION_VALUE = "ReputationValue";
    private static final String TAG_TARGET_NAME = "TargetName";
    private static final String TAG_TARGET_TYPE_ID = "TargetTypeId";

    private final Map<VillageAllegianceId, ArrayDeque<VillageEventMemory.MemoryEvent>> eventsByVillage = new HashMap<>();
    private final Map<UUID, ArrayDeque<VillageEventMemory.MemoryEvent>> eventsByVillager = new HashMap<>();
    private final List<VillageEventMemory.MemoryEvent> legacyEvents = new ArrayList<>();
    private boolean legacyMigrationPending;

    public static VillageEventMemorySavedData get(ServerLevel level) {
        VillageEventMemorySavedData data = level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(VillageEventMemorySavedData::new, VillageEventMemorySavedData::load, DataFixTypes.LEVEL),
                DATA_NAME
        );
        if (data.migrateLegacy(level)) {
            VillageEventMemory.onLegacyMigration();
        }
        return data;
    }

    public static VillageEventMemorySavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        VillageEventMemorySavedData data = new VillageEventMemorySavedData();
        int version = tag.getInt(TAG_FORMAT_VERSION);
        if (version >= CURRENT_FORMAT_VERSION) {
            readVillageEntries(tag.getList(TAG_VILLAGE_ENTRIES, Tag.TAG_COMPOUND), data);
            readVillagerEntries(tag.getList(TAG_VILLAGER_ENTRIES, Tag.TAG_COMPOUND), data);
            return data;
        }

        for (Tag rawEntry : tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND)) {
            if (rawEntry instanceof CompoundTag entryTag) {
                readEvent(entryTag).ifPresent(data.legacyEvents::add);
            }
        }
        data.legacyMigrationPending = !data.legacyEvents.isEmpty();
        return data;
    }

    private static void readVillageEntries(ListTag entries, VillageEventMemorySavedData data) {
        for (Tag rawEntry : entries) {
            if (!(rawEntry instanceof CompoundTag entryTag) || !entryTag.hasUUID(TAG_VILLAGE_ID)) {
                continue;
            }
            readEvent(entryTag).ifPresent(event -> data.eventsByVillage
                    .computeIfAbsent(new VillageAllegianceId(entryTag.getUUID(TAG_VILLAGE_ID)), ignored -> new ArrayDeque<>())
                    .addLast(event));
        }
    }

    private static void readVillagerEntries(ListTag entries, VillageEventMemorySavedData data) {
        for (Tag rawEntry : entries) {
            if (!(rawEntry instanceof CompoundTag entryTag) || !entryTag.hasUUID(TAG_VILLAGER_ID)) {
                continue;
            }
            readEvent(entryTag).ifPresent(event -> data.eventsByVillager
                    .computeIfAbsent(entryTag.getUUID(TAG_VILLAGER_ID), ignored -> new ArrayDeque<>())
                    .addLast(event));
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt(TAG_FORMAT_VERSION, CURRENT_FORMAT_VERSION);
        ListTag villageEntries = new ListTag();
        for (Map.Entry<VillageAllegianceId, ArrayDeque<VillageEventMemory.MemoryEvent>> bucket : this.eventsByVillage.entrySet()) {
            for (VillageEventMemory.MemoryEvent event : bucket.getValue()) {
                CompoundTag entryTag = writeEntry(event);
                entryTag.putUUID(TAG_VILLAGE_ID, bucket.getKey().value());
                villageEntries.add(entryTag);
            }
        }
        tag.put(TAG_VILLAGE_ENTRIES, villageEntries);

        ListTag villagerEntries = new ListTag();
        for (Map.Entry<UUID, ArrayDeque<VillageEventMemory.MemoryEvent>> bucket : this.eventsByVillager.entrySet()) {
            for (VillageEventMemory.MemoryEvent event : bucket.getValue()) {
                CompoundTag entryTag = writeEntry(event);
                entryTag.putUUID(TAG_VILLAGER_ID, bucket.getKey());
                villagerEntries.add(entryTag);
            }
        }
        tag.put(TAG_VILLAGER_ENTRIES, villagerEntries);
        return tag;
    }

    private static CompoundTag writeEntry(VillageEventMemory.MemoryEvent event) {
        CompoundTag tag = new CompoundTag();
        writeEvent(tag, event);
        return tag;
    }

    ArrayDeque<VillageEventMemory.MemoryEvent> villageEvents(VillageAllegianceId villageId) {
        return this.eventsByVillage.get(villageId);
    }

    ArrayDeque<VillageEventMemory.MemoryEvent> villageEventsForWrite(VillageAllegianceId villageId) {
        return this.eventsByVillage.computeIfAbsent(villageId, ignored -> new ArrayDeque<>());
    }

    ArrayDeque<VillageEventMemory.MemoryEvent> villagerEvents(UUID villagerId) {
        return this.eventsByVillager.get(villagerId);
    }

    ArrayDeque<VillageEventMemory.MemoryEvent> villagerEventsForWrite(UUID villagerId) {
        return this.eventsByVillager.computeIfAbsent(villagerId, ignored -> new ArrayDeque<>());
    }

    Collection<ArrayDeque<VillageEventMemory.MemoryEvent>> villageBuckets() {
        return this.eventsByVillage.values();
    }

    Collection<ArrayDeque<VillageEventMemory.MemoryEvent>> villagerBuckets() {
        return this.eventsByVillager.values();
    }

    Map<VillageAllegianceId, ArrayDeque<VillageEventMemory.MemoryEvent>> villageBucketsById() {
        return this.eventsByVillage;
    }

    void removeEmptyBuckets() {
        this.eventsByVillage.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        this.eventsByVillager.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    void markChanged() {
        setDirty();
    }

    boolean migrateLegacy(ServerLevel accessLevel) {
        if (!this.legacyMigrationPending) {
            return false;
        }
        for (VillageEventMemory.MemoryEvent event : this.legacyEvents) {
            if (event.sourceId() != null) {
                this.eventsByVillager.computeIfAbsent(event.sourceId(), ignored -> new ArrayDeque<>()).addLast(event);
            }
            ServerLevel eventLevel = accessLevel.getServer().getLevel(event.dimension());
            if (eventLevel == null) {
                continue;
            }
            VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(eventLevel);
            registry.resolveAt(eventLevel, event.pos())
                    .flatMap(registry::canonical)
                    .ifPresent(id -> this.eventsByVillage.computeIfAbsent(id, ignored -> new ArrayDeque<>()).addLast(event));
        }
        this.legacyEvents.clear();
        this.legacyMigrationPending = false;
        setDirty();
        return true;
    }

    private static Optional<VillageEventMemory.MemoryEvent> readEvent(CompoundTag tag) {
        Optional<ResourceLocation> tagId = readTagId(tag);
        Optional<ResourceKey<Level>> dimension = readDimension(tag);
        if (tagId.isEmpty() || dimension.isEmpty() || !tag.contains(TAG_GAME_TIME, Tag.TAG_LONG) || !hasPos(tag)) {
            return Optional.empty();
        }
        VillageEventMemory.EventTag legacyTag = readLegacyTag(tag).or(() -> VillageEventMemory.legacyTag(tagId.get())).orElse(null);
        return Optional.of(new VillageEventMemory.MemoryEvent(
                legacyTag,
                tagId.get(),
                tag.getLong(TAG_GAME_TIME),
                dimension.get(),
                new BlockPos(tag.getInt(TAG_X), tag.getInt(TAG_Y), tag.getInt(TAG_Z)),
                readUuid(tag, TAG_SOURCE_ID),
                readUuid(tag, TAG_PLAYER_ID),
                readGift(tag),
                readContainerTheft(tag),
                readRetaliation(tag),
                readCuredVillager(tag),
                readKilledVillager(tag)
        ));
    }

    private static Optional<ResourceKey<Level>> readDimension(CompoundTag tag) {
        if (!tag.contains(TAG_DIMENSION, Tag.TAG_STRING)) {
            return Optional.empty();
        }
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(TAG_DIMENSION));
        return id == null ? Optional.empty() : Optional.of(ResourceKey.create(Registries.DIMENSION, id));
    }

    private static Optional<ResourceLocation> readTagId(CompoundTag tag) {
        if (tag.contains(TAG_TAG_ID, Tag.TAG_STRING)) {
            ResourceLocation tagId = ResourceLocation.tryParse(tag.getString(TAG_TAG_ID));
            if (tagId != null) {
                return Optional.of(tagId);
            }
        }
        return readLegacyTag(tag).map(VillageEventMemory::idFor);
    }

    private static Optional<VillageEventMemory.EventTag> readLegacyTag(CompoundTag tag) {
        if (!tag.contains(TAG_TAG, Tag.TAG_STRING)) {
            return Optional.empty();
        }
        try {
            return Optional.of(VillageEventMemory.EventTag.valueOf(tag.getString(TAG_TAG)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static boolean hasPos(CompoundTag tag) {
        return tag.contains(TAG_X, Tag.TAG_INT)
                && tag.contains(TAG_Y, Tag.TAG_INT)
                && tag.contains(TAG_Z, Tag.TAG_INT);
    }

    private static UUID readUuid(CompoundTag tag, String key) {
        return tag.hasUUID(key) ? tag.getUUID(key) : null;
    }

    private static VillageEventMemory.GiftMemory readGift(CompoundTag tag) {
        if (!tag.contains(TAG_GIFT, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag giftTag = tag.getCompound(TAG_GIFT);
        try {
            return new VillageEventMemory.GiftMemory(
                    giftTag.getString(TAG_VILLAGER_NAME),
                    giftTag.getString(TAG_ITEM_NAME),
                    VillagerGiftPreferences.GiftReaction.valueOf(giftTag.getString(TAG_REACTION)),
                    giftTag.getInt(TAG_REPUTATION_VALUE)
            );
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static VillageEventMemory.ContainerTheftMemory readContainerTheft(CompoundTag tag) {
        if (!tag.contains(TAG_CONTAINER_THEFT, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag theftTag = tag.getCompound(TAG_CONTAINER_THEFT);
        return new VillageEventMemory.ContainerTheftMemory(
                theftTag.getString(TAG_VILLAGER_NAME),
                theftTag.getString(TAG_ITEM_NAME),
                theftTag.getString(TAG_ITEM_ID),
                theftTag.getInt(TAG_ITEM_COUNT),
                theftTag.getString(TAG_CONTAINER_NAME),
                theftTag.getString(TAG_LOOT_TABLE)
        );
    }

    private static VillageEventMemory.RetaliationMemory readRetaliation(CompoundTag tag) {
        if (!tag.contains(TAG_RETALIATION, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag retaliationTag = tag.getCompound(TAG_RETALIATION);
        return new VillageEventMemory.RetaliationMemory(
                retaliationTag.getString(TAG_VILLAGER_NAME),
                retaliationTag.getString(TAG_TARGET_NAME),
                retaliationTag.getString(TAG_TARGET_TYPE_ID)
        );
    }

    private static VillageEventMemory.CuredVillagerMemory readCuredVillager(CompoundTag tag) {
        if (!tag.contains(TAG_CURED_VILLAGER, Tag.TAG_COMPOUND)) {
            return null;
        }
        return new VillageEventMemory.CuredVillagerMemory(tag.getCompound(TAG_CURED_VILLAGER).getString(TAG_VILLAGER_NAME));
    }

    private static VillageEventMemory.KilledVillagerMemory readKilledVillager(CompoundTag tag) {
        if (!tag.contains(TAG_KILLED_VILLAGER, Tag.TAG_COMPOUND)) {
            return null;
        }
        return new VillageEventMemory.KilledVillagerMemory(tag.getCompound(TAG_KILLED_VILLAGER).getString(TAG_VILLAGER_NAME));
    }

    private static void writeEvent(CompoundTag tag, VillageEventMemory.MemoryEvent event) {
        if (event.tag() != null) {
            tag.putString(TAG_TAG, event.tag().name());
        }
        tag.putString(TAG_TAG_ID, event.tagId().toString());
        tag.putLong(TAG_GAME_TIME, event.gameTime());
        tag.putString(TAG_DIMENSION, event.dimension().location().toString());
        tag.putInt(TAG_X, event.pos().getX());
        tag.putInt(TAG_Y, event.pos().getY());
        tag.putInt(TAG_Z, event.pos().getZ());
        writeUuid(tag, TAG_SOURCE_ID, event.sourceId());
        writeUuid(tag, TAG_PLAYER_ID, event.playerId());
        writeGift(tag, event.gift());
        writeContainerTheft(tag, event.containerTheft());
        writeRetaliation(tag, event.retaliation());
        writeCuredVillager(tag, event.curedVillager());
        writeKilledVillager(tag, event.killedVillager());
    }

    private static void writeUuid(CompoundTag tag, String key, UUID value) {
        if (value != null) {
            tag.putUUID(key, value);
        }
    }

    private static void writeGift(CompoundTag tag, VillageEventMemory.GiftMemory gift) {
        if (gift == null) {
            return;
        }
        CompoundTag giftTag = new CompoundTag();
        giftTag.putString(TAG_VILLAGER_NAME, safe(gift.villagerName()));
        giftTag.putString(TAG_ITEM_NAME, safe(gift.itemName()));
        giftTag.putString(TAG_REACTION, gift.reaction().name());
        giftTag.putInt(TAG_REPUTATION_VALUE, gift.reputationValue());
        tag.put(TAG_GIFT, giftTag);
    }

    private static void writeContainerTheft(CompoundTag tag, VillageEventMemory.ContainerTheftMemory theft) {
        if (theft == null) {
            return;
        }
        CompoundTag theftTag = new CompoundTag();
        theftTag.putString(TAG_VILLAGER_NAME, safe(theft.villagerName()));
        theftTag.putString(TAG_ITEM_NAME, safe(theft.itemName()));
        theftTag.putString(TAG_ITEM_ID, safe(theft.itemId()));
        theftTag.putInt(TAG_ITEM_COUNT, theft.itemCount());
        theftTag.putString(TAG_CONTAINER_NAME, safe(theft.containerName()));
        theftTag.putString(TAG_LOOT_TABLE, safe(theft.lootTable()));
        tag.put(TAG_CONTAINER_THEFT, theftTag);
    }

    private static void writeRetaliation(CompoundTag tag, VillageEventMemory.RetaliationMemory retaliation) {
        if (retaliation == null) {
            return;
        }
        CompoundTag retaliationTag = new CompoundTag();
        retaliationTag.putString(TAG_VILLAGER_NAME, safe(retaliation.villagerName()));
        retaliationTag.putString(TAG_TARGET_NAME, safe(retaliation.targetName()));
        retaliationTag.putString(TAG_TARGET_TYPE_ID, safe(retaliation.targetTypeId()));
        tag.put(TAG_RETALIATION, retaliationTag);
    }

    private static void writeCuredVillager(CompoundTag tag, VillageEventMemory.CuredVillagerMemory curedVillager) {
        if (curedVillager == null) {
            return;
        }
        CompoundTag curedTag = new CompoundTag();
        curedTag.putString(TAG_VILLAGER_NAME, safe(curedVillager.villagerName()));
        tag.put(TAG_CURED_VILLAGER, curedTag);
    }

    private static void writeKilledVillager(CompoundTag tag, VillageEventMemory.KilledVillagerMemory killedVillager) {
        if (killedVillager == null) {
            return;
        }
        CompoundTag killedTag = new CompoundTag();
        killedTag.putString(TAG_VILLAGER_NAME, safe(killedVillager.villagerName()));
        tag.put(TAG_KILLED_VILLAGER, killedTag);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
