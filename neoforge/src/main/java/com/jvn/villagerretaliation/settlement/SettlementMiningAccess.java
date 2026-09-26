package com.jvn.villagerretaliation.settlement;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

record SettlementMiningAccess(UUID area, BlockPos min, BlockPos max, BlockPos position) {
    static SettlementMiningAccess observe(SettlementMiningAccess previous, SettlementMine mine,
                                          SettlementArea area, BlockPos position) {
        if (mine != null && mine.matches(area) && mine.containsAccess(position) && SettlementMiningRoutes.safe(mine, position)) {
            return new SettlementMiningAccess(area.id(), area.min(), area.max(), position.immutable());
        }
        return previous != null && previous.matches(area) ? previous : null;
    }

    BlockPos entry(SettlementMine mine, SettlementArea area) {
        return matches(area) && mine.matches(area) && mine.containsAccess(this.position) && SettlementMiningRoutes.safe(mine, this.position)
                ? this.position : null;
    }

    boolean matches(SettlementArea area) {
        return this.area.equals(area.id()) && this.min.equals(area.min()) && this.max.equals(area.max());
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putUUID("Area", this.area);
        tag.putLong("Min", this.min.asLong());
        tag.putLong("Max", this.max.asLong());
        tag.putLong("Position", this.position.asLong());
        return tag;
    }

    static SettlementMiningAccess load(CompoundTag tag) {
        if (!tag.hasUUID("Area") || !tag.contains("Min", Tag.TAG_LONG) || !tag.contains("Max", Tag.TAG_LONG)
                || !tag.contains("Position", Tag.TAG_LONG)) return null;
        return new SettlementMiningAccess(tag.getUUID("Area"), BlockPos.of(tag.getLong("Min")),
                BlockPos.of(tag.getLong("Max")), BlockPos.of(tag.getLong("Position")));
    }
}
