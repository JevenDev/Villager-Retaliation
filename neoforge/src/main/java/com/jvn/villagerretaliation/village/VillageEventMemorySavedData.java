package com.jvn.villagerretaliation.village;

import com.jvn.villagerretaliation.interaction.VillagerGiftPreferences;
import java.util.ArrayDeque;
import java.util.HashMap;
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
    private static final String TAG_ENTRIES = "Entries";
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

    private final Map<ResourceKey<Level>, ArrayDeque<VillageEventMemory.MemoryEvent>> eventsByDimension = new HashMap<>();

    public static VillageEventMemorySavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(VillageEventMemorySavedData::new, VillageEventMemorySavedData::load, DataFixTypes.LEVEL),
                DATA_NAME
        );
    }

    public static VillageEventMemorySavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        VillageEventMemorySavedData data = new VillageEventMemorySavedData();
        ListTag entriesTag = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        for (Tag rawEntry : entriesTag) {
            if (!(rawEntry instanceof CompoundTag entryTag)) {
                continue;
            }
            Optional<ResourceKey<Level>> dimension = readDimension(entryTag);
            Optional<VillageEventMemory.MemoryEvent> event = readEvent(entryTag);
            if (dimension.isEmpty() || event.isEmpty()) {
                continue;
            }
            data.eventsByDimension
                    .computeIfAbsent(dimension.get(), ignored -> new ArrayDeque<>())
                    .addLast(event.get());
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag entriesTag = new ListTag();
        for (Map.Entry<ResourceKey<Level>, ArrayDeque<VillageEventMemory.MemoryEvent>> dimensionEntry : this.eventsByDimension.entrySet()) {
            ResourceKey<Level> dimension = dimensionEntry.getKey();
            for (VillageEventMemory.MemoryEvent event : dimensionEntry.getValue()) {
                if (event.tagId() == null || event.pos() == null) {
                    continue;
                }
                CompoundTag entryTag = new CompoundTag();
                entryTag.putString(TAG_DIMENSION, dimension.location().toString());
                writeEvent(entryTag, event);
                entriesTag.add(entryTag);
            }
        }
        tag.put(TAG_ENTRIES, entriesTag);
        return tag;
    }

    ArrayDeque<VillageEventMemory.MemoryEvent> events(ResourceKey<Level> dimension) {
        return this.eventsByDimension.get(dimension);
    }

    ArrayDeque<VillageEventMemory.MemoryEvent> eventsForWrite(ResourceKey<Level> dimension) {
        return this.eventsByDimension.computeIfAbsent(dimension, ignored -> new ArrayDeque<>());
    }

    void removeDimensionIfEmpty(ResourceKey<Level> dimension) {
        ArrayDeque<VillageEventMemory.MemoryEvent> events = this.eventsByDimension.get(dimension);
        if (events != null && events.isEmpty()) {
            this.eventsByDimension.remove(dimension);
        }
    }

    void markChanged() {
        setDirty();
    }

    private static Optional<ResourceKey<Level>> readDimension(CompoundTag tag) {
        if (!tag.contains(TAG_DIMENSION, Tag.TAG_STRING)) {
            return Optional.empty();
        }
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(TAG_DIMENSION));
        return id == null ? Optional.empty() : Optional.of(ResourceKey.create(Registries.DIMENSION, id));
    }

    private static Optional<VillageEventMemory.MemoryEvent> readEvent(CompoundTag tag) {
        Optional<ResourceLocation> tagId = readTagId(tag);
        if (tagId.isEmpty() || !tag.contains(TAG_GAME_TIME, Tag.TAG_LONG) || !hasPos(tag)) {
            return Optional.empty();
        }
        VillageEventMemory.EventTag legacyTag = readLegacyTag(tag).or(() -> VillageEventMemory.legacyTag(tagId.get())).orElse(null);
        return Optional.of(new VillageEventMemory.MemoryEvent(
                legacyTag,
                tagId.get(),
                tag.getLong(TAG_GAME_TIME),
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
