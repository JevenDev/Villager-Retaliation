package com.jvn.villagerretaliation.settlement;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

public record SettlementDeparture(UUID id, String name, long departedAt, Reason reason, SettlementResidentState care) {
    public SettlementDeparture {
        if (id == null || name == null || reason == null || care == null || !id.equals(care.id()) || departedAt < 0L) {
            throw new IllegalArgumentException("Departure requires a resident, reason and care snapshot");
        }
        name = name.substring(0, Math.min(name.length(), 128));
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", this.id);
        tag.putString("Name", this.name);
        tag.putLong("DepartedAt", this.departedAt);
        tag.putString("Reason", this.reason.name());
        tag.put("Care", this.care.save());
        return tag;
    }

    static SettlementDeparture load(CompoundTag tag) {
        return new SettlementDeparture(tag.getUUID("Id"), tag.getString("Name"), tag.getLong("DepartedAt"),
                Reason.valueOf(tag.getString("Reason")), SettlementResidentState.load(tag.getCompound("Care")));
    }

    public enum Reason {
        FOOD,
        HOUSING
    }
}
