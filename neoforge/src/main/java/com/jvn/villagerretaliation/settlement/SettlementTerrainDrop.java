package com.jvn.villagerretaliation.settlement;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

record SettlementTerrainDrop(UUID id, BlockPos position, CompoundTag item) {
    SettlementTerrainDrop {
        position = position.immutable();
        item = item.copy();
        if (item.isEmpty()) throw new IllegalArgumentException("Terrain drops require an item");
    }

    static SettlementTerrainDrop create(ServerLevel level, BlockPos position, ItemStack item) {
        if (item.isEmpty()) throw new IllegalArgumentException("Terrain drops require a nonempty stack");
        return new SettlementTerrainDrop(UUID.randomUUID(), position, (CompoundTag) item.save(level.registryAccess()));
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putUUID("Id", this.id);
        tag.putLong("Position", this.position.asLong());
        tag.put("Item", this.item.copy());
        return tag;
    }

    static SettlementTerrainDrop load(CompoundTag tag) {
        if (!tag.hasUUID("Id") || !tag.contains("Position", Tag.TAG_LONG) || !tag.contains("Item", Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Terrain drops require their identity, position and item");
        }
        return new SettlementTerrainDrop(tag.getUUID("Id"), BlockPos.of(tag.getLong("Position")), tag.getCompound("Item"));
    }
}
