package com.jvn.villagerretaliation.allegiance;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

public final class VillageAllegianceRelations {
    private VillageAllegianceRelations() {
    }

    public static boolean sameCanonical(ServerLevel level, Entity first, Entity second) {
        Set<VillageAllegianceId> firstIds = communities(level, first);
        Set<VillageAllegianceId> secondIds = communities(level, second);
        return firstIds.stream().anyMatch(secondIds::contains);
    }

    public static boolean sharesCommunity(ServerLevel level, Entity source, Entity receiver) {
        return source != null && receiver != null && sameCanonical(level, source, receiver);
    }

    private static Set<VillageAllegianceId> communities(ServerLevel level, Entity entity) {
        VillageAllegianceData data = VillageAllegianceApi.get(entity).orElse(null);
        if (data == null) {
            return Set.of();
        }
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        Set<VillageAllegianceId> ids = new HashSet<>();
        if (data.isKnown()) {
            registry.canonical(data.primary()).ifPresent(ids::add);
        }
        if (entity instanceof Villager villager && villager.isBaby()
                && data.assignmentSource() == AllegianceAssignmentSource.BIRTH
                && data.confidence() == AllegianceConfidence.INHERITED) {
            data.protectedParents().forEach(parent -> registry.canonical(parent).ifPresent(ids::add));
        }
        return Set.copyOf(ids);
    }
}
