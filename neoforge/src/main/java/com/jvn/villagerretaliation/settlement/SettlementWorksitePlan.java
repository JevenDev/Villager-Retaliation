package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public record SettlementWorksitePlan(SettlementWorkerSettings settings, int desiredStaff) {
    public static final int MAX_STAFF = 32;

    public SettlementWorksitePlan {
        Objects.requireNonNull(settings);
        if (desiredStaff < 0 || desiredStaff > MAX_STAFF
                || settings.overrides() != SettlementWorkerSettings.allOverrides(settings.role())) {
            throw new IllegalArgumentException("Invalid worksite settings or staffing target");
        }
    }

    public static SettlementWorksitePlan defaults(HiredVillagerRole role) {
        return new SettlementWorksitePlan(SettlementWorkerSettings.capture(role, new CompoundTag()), 1);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", 1);
        tag.put("Settings", settings.save());
        tag.putInt("DesiredStaff", desiredStaff);
        return tag;
    }

    static SettlementWorksitePlan load(CompoundTag tag) {
        if (!tag.contains("Version", Tag.TAG_INT) || tag.getInt("Version") != 1 || !tag.contains("Settings", Tag.TAG_COMPOUND)
                || !tag.contains("DesiredStaff", Tag.TAG_INT)) {
            throw new IllegalArgumentException("Invalid worksite plan");
        }
        return new SettlementWorksitePlan(SettlementWorkerSettings.load(tag.getCompound("Settings")), tag.getInt("DesiredStaff"));
    }
}
