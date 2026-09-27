package com.jvn.villagerretaliation.settlement;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public record SettlementHome(UUID resident, BlockPos bed, long version) {
    public SettlementHome {
        if (resident == null || version <= 0L) throw new IllegalArgumentException("Home assignment has no resident or revision");
        if (bed != null) bed = bed.immutable();
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putUUID("Resident", this.resident);
        if (this.bed != null) tag.putLong("Bed", this.bed.asLong());
        tag.putLong("Version", this.version);
        return tag;
    }

    static SettlementHome load(CompoundTag tag) {
        if (!tag.hasUUID("Resident")) throw new IllegalArgumentException("Home assignment is missing its resident");
        return new SettlementHome(tag.getUUID("Resident"), tag.contains("Bed", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("Bed")) : null,
                tag.getLong("Version"));
    }
}
