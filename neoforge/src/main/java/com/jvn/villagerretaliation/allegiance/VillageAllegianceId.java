package com.jvn.villagerretaliation.allegiance;

import java.util.Objects;
import java.util.UUID;

public record VillageAllegianceId(UUID value) implements Comparable<VillageAllegianceId> {
    public VillageAllegianceId {
        Objects.requireNonNull(value, "value");
    }

    public static VillageAllegianceId random() {
        return new VillageAllegianceId(UUID.randomUUID());
    }

    @Override
    public int compareTo(VillageAllegianceId other) {
        return this.value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return this.value.toString();
    }
}
