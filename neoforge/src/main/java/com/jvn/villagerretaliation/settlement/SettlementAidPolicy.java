package com.jvn.villagerretaliation.settlement;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public record SettlementAidPolicy(boolean automatic, UUID authority, int reserveDays, int maximumItems,
                                  int minimumStanding, int intervalTicks) {
    public SettlementAidPolicy {
        if (authority == null || reserveDays < 1 || reserveDays > 30 || maximumItems < 1 || maximumItems > 1728
                || minimumStanding < 0 || minimumStanding > 100 || intervalTicks < 24000 || intervalTicks > 2400000)
            throw new IllegalArgumentException("Invalid food aid policy");
    }
    public static SettlementAidPolicy defaults(SettlementRecord town) {
        return new SettlementAidPolicy(town.autonomous() != null, town.leader(), 3, 128, 0, 72000);
    }
    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putBoolean("Automatic", this.automatic);
        tag.putUUID("Authority", this.authority);
        tag.putInt("ReserveDays", this.reserveDays);
        tag.putInt("MaximumItems", this.maximumItems);
        tag.putInt("MinimumStanding", this.minimumStanding);
        tag.putInt("IntervalTicks", this.intervalTicks);
        return tag;
    }
    static SettlementAidPolicy load(CompoundTag tag) {
        if (!tag.contains("Automatic", Tag.TAG_BYTE) || !tag.hasUUID("Authority") || !tag.contains("ReserveDays", Tag.TAG_INT)
                || !tag.contains("MaximumItems", Tag.TAG_INT) || !tag.contains("MinimumStanding", Tag.TAG_INT)
                || !tag.contains("IntervalTicks", Tag.TAG_INT)) throw new IllegalArgumentException("Missing food aid policy fields");
        return new SettlementAidPolicy(tag.getBoolean("Automatic"), tag.getUUID("Authority"), tag.getInt("ReserveDays"),
                tag.getInt("MaximumItems"), tag.getInt("MinimumStanding"), tag.getInt("IntervalTicks"));
    }
}
