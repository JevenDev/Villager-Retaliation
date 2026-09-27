package com.jvn.villagerretaliation.settlement;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.CompoundTag;

public final class SettlementArrival {
    private final UUID id;
    private final CompoundTag entity;
    private final BlockPos origin;

    SettlementArrival(UUID id, CompoundTag entity) {
        this(id, entity, null);
    }

    SettlementArrival(UUID id, CompoundTag entity, BlockPos origin) {
        if (id == null || entity == null || !entity.hasUUID("UUID") || !id.equals(entity.getUUID("UUID"))) {
            throw new IllegalArgumentException("Arrival identity or entity data is invalid");
        }
        this.id = id;
        this.entity = entity.copy();
        this.origin = origin == null ? null : origin.immutable();
    }

    public UUID id() {
        return this.id;
    }

    BlockPos origin(SettlementRecord town) {
        return this.origin == null ? town.center() : this.origin;
    }

    CompoundTag entity() {
        return this.entity.copy();
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", this.id);
        tag.put("Entity", this.entity.copy());
        if (this.origin != null) tag.putLong("Origin", this.origin.asLong());
        return tag;
    }

    static SettlementArrival load(CompoundTag tag) {
        if (!tag.hasUUID("Id")) {
            throw new IllegalArgumentException("Arrival identity is missing");
        }
        if (tag.contains("Origin") && !tag.contains("Origin", Tag.TAG_LONG)) {
            throw new IllegalArgumentException("Arrival origin has an invalid position");
        }
        return new SettlementArrival(tag.getUUID("Id"), tag.getCompound("Entity"),
                tag.contains("Origin", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("Origin")) : null);
    }
}
