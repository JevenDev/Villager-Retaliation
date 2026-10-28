package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredVillagerRoles;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

final class SettlementAutonomyService {
    private SettlementAutonomyService() {
    }

    static void fill(ServerLevel level, SettlementRecord town, java.util.UUID selectedArea) {
        var missing = SettlementProgressionService.assess(level, town).missing();
        var areas = town.areas().values().stream()
                .filter(area -> area.enabled() && area.type().role() != null && area.type() != SettlementAreaType.CONSTRUCTION)
                .filter(area -> selectedArea == null || area.id().equals(selectedArea))
                .filter(area -> staffingNeeded(town, area) > 0)
                .filter(area -> !SettlementBuildingSurvey.requiresFixture(area.type())
                        || town.surveys().containsKey(area.id()) && !town.surveys().get(area.id()).workstations().isEmpty())
                .sorted(Comparator.comparingInt((SettlementArea area) -> priority(area, missing))
                        .thenComparing(Comparator.comparingInt(SettlementArea::priority).reversed())
                        .thenComparing(SettlementArea::id)).toList();
        if (areas.isEmpty()) return;
        var candidates = new java.util.ArrayList<Villager>();
        for (var member : VillageAllegianceRegistrySavedData.get(level).residentRoster(town.id(), level.getGameTime())) {
            if (!member.adult() || town.duties().containsKey(member.id()) || town.manuallyStaffed(member.id())
                    || town.departures().containsKey(member.id())) continue;
            Villager candidate = null;
            boolean found = false;
            for (var dimension : level.getServer().getAllLevels()) {
                var entity = dimension.getEntity(member.id());
                if (entity == null) continue;
                found = true;
                if (dimension == level && entity instanceof Villager resident) candidate = resident;
                break;
            }
            if (!found && town.autonomous()==null) {
                var state = town.residents().get(member.id());
                if (state != null && state.availableForWork()) candidate = state.context(level);
            }
            if (candidate != null && candidate.isAlive() && !candidate.isBaby()
                    && !SettlementWorkforceService.hasPersonalCommitment(candidate)) candidates.add(candidate);
        }
        for (var area : areas) {
            int needed = staffingNeeded(town, area);
            for (int index = 0; index < needed && !candidates.isEmpty(); index++) {
                var candidate = candidates.stream()
                        .filter(resident -> SettlementWorkforceService.availableRoles(level, resident).contains(area.type().role()))
                        .min(Comparator.comparingInt((Villager resident) -> HiredVillagerRoles.roleScore(level, resident, area.type().role())).reversed()
                                .thenComparingDouble(resident -> resident.blockPosition().distSqr(area.center()))
                                .thenComparing(Villager::getUUID)).orElse(null);
                if (candidate == null) break;
                if (SettlementWorkforceService.fillAssignment(level, town, candidate, area.id())) {
                    candidates.remove(candidate);
                } else {
                    break;
                }
            }
        }
    }

    private static int staffingNeeded(SettlementRecord town, SettlementArea area) {
        var plan = town.worksitePlans().get(area.id());
        if (plan != null && !plan.settings().enabled()) return 0;
        int target = plan == null ? 1 : plan.desiredStaff();
        return Math.max(0, target - (int) town.duties().values().stream().filter(area.id()::equals).count());
    }

    private static int priority(SettlementArea area, List<SettlementProgressionService.Requirement> missing) {
        if (area.type().foodProduction() && (missing.contains(SettlementProgressionService.Requirement.FOOD_PRODUCTION)
                || missing.contains(SettlementProgressionService.Requirement.FOOD_STOCK))) return 0;
        return switch (area.type()) {
            case KITCHEN -> missing.contains(SettlementProgressionService.Requirement.KITCHEN) ? 1 : 6;
            case GUARD_POST -> missing.contains(SettlementProgressionService.Requirement.GUARD_POST) ? 2 : 6;
            case WORKSHOP -> missing.contains(SettlementProgressionService.Requirement.WORKSHOP) ? 3 : 6;
            case TRADING_POST -> missing.contains(SettlementProgressionService.Requirement.TRADING_POST) ? 4 : 6;
            case DEPOT -> 5;
            default -> 6;
        };
    }
}
