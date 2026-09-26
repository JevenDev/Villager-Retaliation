package com.jvn.villagerretaliation.settlement;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

public final class SettlementArrival {
    private final UUID id;
    private final CompoundTag entity;

    SettlementArrival(UUID id, CompoundTag entity) {
        if (id == null || entity == null || !entity.hasUUID("UUID") || !id.equals(entity.getUUID("UUID"))) {
            throw new IllegalArgumentException("Arrival identity or entity data is invalid");
        }
        this.id = id;
        this.entity = entity.copy();
    }

    public UUID id() {
        return this.id;
    }

    CompoundTag entity() {
        return this.entity.copy();
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", this.id);
        tag.put("Entity", this.entity.copy());
        return tag;
    }

    static SettlementArrival load(CompoundTag tag) {
        if (!tag.hasUUID("Id")) {
            throw new IllegalArgumentException("Arrival identity is missing");
        }
        return new SettlementArrival(tag.getUUID("Id"), tag.getCompound("Entity"));
    }
}
