package com.jvn.villagerretaliation.allegiance;

import net.minecraft.resources.ResourceLocation;

public final class VillageEntryTracker {
    private ResourceLocation dimension;
    private VillageAllegianceId current;
    private long outsideSince = -1;

    public boolean update(ResourceLocation dimension, VillageAllegianceId village, long time) {
        if (!dimension.equals(this.dimension)) {
            this.dimension = dimension;
            this.current = null;
            this.outsideSince = -1;
        }
        if (this.outsideSince >= 0 && time - this.outsideSince >= 40) this.current = null;
        if (village == null) {
            if (this.outsideSince < 0) this.outsideSince = time;
            return false;
        }
        this.outsideSince = -1;
        if (village.equals(this.current)) return false;
        this.current = village;
        return true;
    }
}
