package com.jvn.villagerretaliation.dialogue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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

public class GeneratedContainerSavedData extends SavedData {
    private static final String DATA_NAME = "villagerretaliation_generated_containers";
    private static final String TAG_ENTRIES = "Entries";
    private static final String TAG_DIMENSION = "Dimension";
    private static final String TAG_POS = "Pos";
    private static final String TAG_LOOT_TABLE = "LootTable";

    private final Map<GeneratedContainerKey, ResourceLocation> lootTables = new HashMap<>();

    public static GeneratedContainerSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(GeneratedContainerSavedData::new, GeneratedContainerSavedData::load, DataFixTypes.LEVEL),
                DATA_NAME
        );
    }

    public static GeneratedContainerSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        GeneratedContainerSavedData data = new GeneratedContainerSavedData();
        ListTag entriesTag = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        for (Tag rawEntry : entriesTag) {
            if (!(rawEntry instanceof CompoundTag entryTag)) {
                continue;
            }

            ResourceLocation dimensionId = ResourceLocation.tryParse(entryTag.getString(TAG_DIMENSION));
            ResourceLocation lootTable = ResourceLocation.tryParse(entryTag.getString(TAG_LOOT_TABLE));
            if (dimensionId == null || lootTable == null || !entryTag.contains(TAG_POS, Tag.TAG_LONG)) {
                continue;
            }

            data.lootTables.put(
                    new GeneratedContainerKey(ResourceKey.create(Registries.DIMENSION, dimensionId), BlockPos.of(entryTag.getLong(TAG_POS))),
                    lootTable
            );
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag entriesTag = new ListTag();
        for (Map.Entry<GeneratedContainerKey, ResourceLocation> entry : this.lootTables.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString(TAG_DIMENSION, entry.getKey().dimension().location().toString());
            entryTag.putLong(TAG_POS, entry.getKey().pos().asLong());
            entryTag.putString(TAG_LOOT_TABLE, entry.getValue().toString());
            entriesTag.add(entryTag);
        }
        tag.put(TAG_ENTRIES, entriesTag);
        return tag;
    }

    public Optional<ResourceLocation> lootTable(ResourceKey<Level> dimension, BlockPos pos) {
        return Optional.ofNullable(this.lootTables.get(new GeneratedContainerKey(dimension, pos.immutable())));
    }

    public void remember(ResourceKey<Level> dimension, BlockPos pos, ResourceLocation lootTable) {
        if (lootTable == null) {
            return;
        }
        this.lootTables.put(new GeneratedContainerKey(dimension, pos.immutable()), lootTable);
        setDirty();
    }

    private record GeneratedContainerKey(ResourceKey<Level> dimension, BlockPos pos) {
    }
}
