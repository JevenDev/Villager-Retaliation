package com.jvn.villagerretaliation.exploration;

import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Random durable world identity; a server address alone cannot distinguish replaced worlds. */
public final class MapWorldIdentity extends SavedData {
    private final UUID id;
    private MapWorldIdentity() { id = UUID.randomUUID(); setDirty(); }
    private MapWorldIdentity(CompoundTag tag) { id = tag.hasUUID("World") ? tag.getUUID("World") : UUID.randomUUID(); if (!tag.hasUUID("World")) setDirty(); }
    public static UUID get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(MapWorldIdentity::new, (tag, lookup) -> new MapWorldIdentity(tag)), "vr_explored_map_identity").id;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) { tag.putUUID("World", id); return tag; }
}
