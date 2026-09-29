package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class SettlementProgressionService {
    private SettlementProgressionService() {
    }

    static void update(ServerLevel level, SettlementRecord town, long now) {
        Status status = assess(level, town);
        town.support().observe(now, status.missing().isEmpty(),
                status.population() > 0 && (status.housing() < status.population() || status.foodDays() < 0.25D));
        town.changed();
        if (!town.manualManagement() && town.stage().next().isPresent() && status.missing().isEmpty()
                && town.support().sustainedTicks() >= status.requiredTicks()) advance(town, now);
    }

    public static Status assess(ServerLevel level, SettlementRecord town) {
        return assess(VillageAllegianceRegistrySavedData.get(level), town, level.getGameTime());
    }

    static Status assess(VillageAllegianceRegistrySavedData registry, SettlementRecord town, long gameTime) {
        var residents = registry.residentRoster(town.id(), gameTime);
        Set<UUID> adults = residents.stream().filter(VillageAllegianceRegistrySavedData.ResidentRecord::adult)
                .map(VillageAllegianceRegistrySavedData.ResidentRecord::id).collect(Collectors.toSet());
        Set<UUID> staffedAreas = town.duties().entrySet().stream()
                .filter(duty -> adults.contains(duty.getKey())
                        && (!town.workers().containsKey(duty.getKey()) || town.workers().get(duty.getKey()).supportsService())
                        && (!town.workerSettings().containsKey(duty.getKey()) || town.workerSettings().get(duty.getKey()).enabled()))
                .map(java.util.Map.Entry::getValue).collect(Collectors.toSet());
        int population = residents.size();
        int housing = town.housingCapacity();
        double foodDays = SettlementFoodService.reserveDays(town, population);
        SettlementStage target = town.stage().next().orElse(town.stage());
        List<Requirement> missing = new ArrayList<>();
        if (population < target.population()) {
            missing.add(Requirement.POPULATION);
        }
        if (housing < Math.max(population, target.population())) {
            missing.add(Requirement.HOUSING);
        }
        if (foodDays < Math.clamp(VillagerRetaliationConfig.SETTLEMENT_FOOD_RESERVE_DAYS.get(), 1, 30)) {
            missing.add(Requirement.FOOD_STOCK);
        }
        if (SettlementStock.supplies(town).isEmpty()) {
            missing.add(Requirement.DEPOT);
        }
        var services = SettlementStageRules.forStage(target).services();
        for (var requirement : Requirement.values()) {
            if (!services.contains(requirement)) continue;
            boolean operating = switch (requirement) {
                case FOOD_PRODUCTION -> town.areas().values().stream()
                        .anyMatch(area -> area.type().foodProduction() && staffed(town, area, staffedAreas));
                case KITCHEN -> staffed(town, SettlementAreaType.KITCHEN, staffedAreas);
                case GUARD_POST -> staffed(town, SettlementAreaType.GUARD_POST, staffedAreas);
                case WORKSHOP -> staffed(town, SettlementAreaType.WORKSHOP, staffedAreas);
                case TRADING_POST -> staffed(town, SettlementAreaType.TRADING_POST, staffedAreas);
                default -> false;
            };
            if (!operating) missing.add(requirement);
        }
        long requiredTicks = requiredTicks();
        return new Status(target, population, housing, foodDays, List.copyOf(missing),
                town.support().sustainedTicks(), requiredTicks,
                town.stage().next().isPresent() && missing.isEmpty() && town.support().sustainedTicks() >= requiredTicks);
    }

    public static boolean advance(ServerPlayer player, VillageAllegianceId id) {
        var town = SettlementSavedData.get(player.serverLevel()).get(id).orElse(null);
        if (town == null || !player.isAlive() || player.isSpectator() || !town.leader().equals(player.getUUID())
                || !town.dimension().equals(player.level().dimension().location())) {
            return false;
        }
        town.areas().values().forEach(area -> SettlementBuildingService.refresh(player.serverLevel(), town, area));
        Status status = assess(player.serverLevel(), town);
        if (!status.ready()) {
            if (!status.missing().isEmpty()) {
                town.support().observe(player.serverLevel().getGameTime(), false,
                        status.population() > status.housing() || status.foodDays() < 0.25D);
                town.changed();
            }
            return false;
        }
        advance(town, player.serverLevel().getGameTime());
        return true;
    }

    private static void advance(SettlementRecord town, long now) {
        town.support().resetAdvancement();
        town.advance();
        town.recordEvent(now, SettlementEvent.Type.STAGE_ADVANCED, town.stage().name());
    }

    private static boolean staffed(SettlementRecord town, SettlementAreaType type, Set<UUID> staffedAreas) {
        return town.areas().values().stream().anyMatch(area -> area.type() == type && staffed(town, area, staffedAreas));
    }

    private static boolean staffed(SettlementRecord town, SettlementArea area, Set<UUID> staffedAreas) {
        var survey = town.surveys().get(area.id());
        boolean equipped = !SettlementBuildingSurvey.requiresFixture(area.type())
                || survey != null && !survey.workstations().isEmpty();
        return area.enabled() && equipped && staffedAreas.contains(area.id());
    }

    public static long requiredTicks() {
        return 24_000L * Math.clamp(VillagerRetaliationConfig.SETTLEMENT_ADVANCEMENT_DAYS.get(), 1, 30);
    }

    public enum Requirement {
        POPULATION,
        HOUSING,
        FOOD_STOCK,
        DEPOT,
        FOOD_PRODUCTION,
        KITCHEN,
        GUARD_POST,
        WORKSHOP,
        TRADING_POST
    }

    public record Status(SettlementStage target, int population, int housing, double foodDays,
                         List<Requirement> missing, long sustainedTicks, long requiredTicks, boolean ready) {
    }
}
