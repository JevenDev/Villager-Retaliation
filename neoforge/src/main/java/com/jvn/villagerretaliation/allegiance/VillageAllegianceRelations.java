package com.jvn.villagerretaliation.allegiance;

import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

public final class VillageAllegianceRelations {
    private VillageAllegianceRelations() {
    }

    public static boolean sameCanonical(ServerLevel level, Entity first, Entity second) {
        Optional<VillageAllegianceId> firstId = VillageAllegianceApi.canonicalPrimary(level, first);
        Optional<VillageAllegianceId> secondId = VillageAllegianceApi.canonicalPrimary(level, second);
        return firstId.isPresent() && secondId.isPresent() && firstId.get().equals(secondId.get());
    }

    public static boolean sharesCommunity(ServerLevel level, Entity source, Entity receiver) {
        return source != null && receiver != null && sameCanonical(level, source, receiver);
    }
}
