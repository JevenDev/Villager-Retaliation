package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

public final class SettlementMembershipService {
    private SettlementMembershipService() {
    }

    public static boolean permitsAutomaticMembership(ServerLevel level, Entity entity, VillageAllegianceId id,
                                                       AllegianceAssignmentSource source,
                                                       List<VillageAllegianceId> parents) {
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var canonical = registry.canonical(id).orElse(id);
        if (!(entity instanceof Villager) || !registry.isManaged(canonical)
                || VillageAllegianceApi.canonicalPrimary(level, entity).filter(canonical::equals).isPresent()
                || registry.canonicalRecord(canonical).map(village -> village.residents().containsKey(entity.getUUID())).orElse(false)) {
            return true;
        }
        return switch (source) {
            case ADMIN, COMMAND, QUEST, EXPLICIT_API -> true;
            case BIRTH -> parents.stream().map(registry::canonical).flatMap(java.util.Optional::stream)
                    .anyMatch(canonical::equals);
            default -> false;
        };
    }
}
