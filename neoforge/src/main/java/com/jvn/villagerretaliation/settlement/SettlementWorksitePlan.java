package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public record SettlementWorksitePlan(SettlementWorkerSettings settings, int desiredStaff, SettlementWorksiteLinks links, SettlementGuardOrder route) {
    public SettlementWorksitePlan(SettlementWorkerSettings settings, int desiredStaff, SettlementWorksiteLinks links) {
        this(settings, desiredStaff, links, null);
    }

    public static final int MAX_STAFF = 32;

    public SettlementWorksitePlan(SettlementWorkerSettings settings, int desiredStaff) {
        this(settings, desiredStaff, SettlementWorksiteLinks.AUTOMATIC);
    }

    public SettlementWorksitePlan {
        Objects.requireNonNull(settings);
        Objects.requireNonNull(links);
        if (desiredStaff < 0 || desiredStaff > MAX_STAFF
                || settings.overrides() != SettlementWorkerSettings.allOverrides(settings.role())
                || route != null && !route.validFor(settings.role())) {
            throw new IllegalArgumentException("Invalid worksite settings or staffing target");
        }
    }

    public static SettlementWorksitePlan defaults(HiredVillagerRole role) {
        return new SettlementWorksitePlan(SettlementWorkerSettings.capture(role, new CompoundTag()), 1);
    }

    public SettlementWorksitePlan withRoute(SettlementGuardOrder route) {
        return new SettlementWorksitePlan(this.settings, this.desiredStaff, this.links, route);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", 3);
        tag.put("Settings", settings.save());
        tag.putInt("DesiredStaff", desiredStaff);
        tag.put("Links", links.save());
        if (route != null) tag.put("Route", route.save());
        return tag;
    }

    static SettlementWorksitePlan load(CompoundTag tag) {
        if (!tag.contains("Version", Tag.TAG_INT) || (tag.getInt("Version") < 1 || tag.getInt("Version") > 3) || !tag.contains("Settings", Tag.TAG_COMPOUND)
                || !tag.contains("DesiredStaff", Tag.TAG_INT) || tag.contains("Route") && !tag.contains("Route", Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Invalid worksite plan");
        }
        return new SettlementWorksitePlan(SettlementWorkerSettings.load(tag.getCompound("Settings")), tag.getInt("DesiredStaff"),
                tag.getInt("Version") == 1 ? SettlementWorksiteLinks.AUTOMATIC : SettlementWorksiteLinks.load(tag.getCompound("Links")),
                tag.contains("Route") ? SettlementGuardOrder.load(tag.getCompound("Route")) : null);
    }
}
