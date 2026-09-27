package com.jvn.villagerretaliation.settlement;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

record SettlementHomeRelease(UUID id, UUID resident, GlobalPos home) {
    SettlementHomeRelease {
        if (id == null || resident == null || home == null) throw new IllegalArgumentException("Home release is missing its identity or position");
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putUUID("Id", this.id);
        tag.putUUID("Resident", this.resident);
        tag.putString("Dimension", this.home.dimension().location().toString());
        tag.putLong("Bed", this.home.pos().asLong());
        return tag;
    }

    static SettlementHomeRelease load(CompoundTag tag) {
        var dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (!tag.hasUUID("Id") || !tag.hasUUID("Resident") || dimension == null || !tag.contains("Bed", Tag.TAG_LONG)) {
            throw new IllegalArgumentException("Home release has an invalid identity or position");
        }
        return new SettlementHomeRelease(tag.getUUID("Id"), tag.getUUID("Resident"),
                GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dimension), BlockPos.of(tag.getLong("Bed"))));
    }
}
