package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand;
import net.minecraft.server.level.ServerLevel;

public final class SettlementNeeds {
    private SettlementNeeds() { }

    public static Long value(ServerLevel level, SettlementRecord town, String need) {
        if (need.equals("population")) return (long) VillageAllegianceRegistrySavedData.get(level)
                .residentRoster(town.id(), level.getGameTime()).size();
        if (need.equals("workers")) {
            long missing = 0;
            for (var area : town.workplaces()) {
                if (!area.enabled() || !town.owns(area) || area.type().role() == null) continue;
                var plan = town.worksitePlans().get(area.id());
                int desired = plan == null ? 1 : plan.desiredStaff();
                long assigned = town.duties().values().stream().filter(area.id()::equals).count();
                missing += Math.max(0, desired - assigned);
            }
            return missing;
        }
        if (need.equals("housing") || need.equals("housing_capacity")) {
            for (var area : town.areas().values()) if (area.enabled() && town.owns(area) && area.type() == SettlementAreaType.HOME) {
                var survey = town.surveys().get(area.id());
                if (survey == null || !survey.fullyLoaded()) return null;
            }
            if (need.equals("housing_capacity")) return (long) town.housingCapacity();
            long missing = 0;
            for (var member : VillageAllegianceRegistrySavedData.get(level).residentRoster(town.id(), level.getGameTime())) {
                var home = town.homes().get(member.id());
                if (home == null || home.bed() == null || !town.usableBed(home.bed())) { missing++; continue; }
                Boolean available = SettlementHousingService.availability(town, home.bed());
                if (available == null) return null;
                if (!available) missing++;
            }
            return missing;
        }
        var kind = switch (need) {
            case "food" -> WorkSupplyDemand.Kind.FOOD;
            case "equipment" -> WorkSupplyDemand.Kind.EQUIPMENT;
            case "materials" -> WorkSupplyDemand.Kind.PROJECT;
            case "production" -> WorkSupplyDemand.Kind.PRODUCTION;
            case "stock" -> WorkSupplyDemand.Kind.STOCK_RESERVE;
            case "planting" -> WorkSupplyDemand.Kind.PLANTING;
            default -> throw new IllegalArgumentException("Unknown settlement need: " + need);
        };
        long missing = 0;
        for (var demand : SettlementDemandService.inspect(level, town).demands()) {
            if (demand.kind() != kind) continue;
            if (!demand.supply().known()) return null;
            missing = Math.addExact(missing, demand.missing());
        }
        return missing;
    }
}
