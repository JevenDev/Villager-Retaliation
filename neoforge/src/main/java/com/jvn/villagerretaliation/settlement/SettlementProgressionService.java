package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class SettlementProgressionService {
    private SettlementProgressionService() {
    }

    public static void tick(MinecraftServer server) {
        long gameTime = server.overworld().getGameTime();
        if (gameTime % 200L != 0L) {
            return;
        }
        var registry = VillageAllegianceRegistrySavedData.get(server.overworld());
        for (SettlementRecord town : SettlementSavedData.get(server.overworld()).settlements()) {
            Status status = assess(registry, town, gameTime);
            town.support().observe(gameTime, status.missing().isEmpty(),
                    status.population() > 0 && (status.housing() < status.population() || status.foodDays() < 0.25D));
            town.changed();
        }
    }

    public static Status assess(ServerLevel level, SettlementRecord town) {
        return assess(VillageAllegianceRegistrySavedData.get(level), town, level.getGameTime());
    }

    static Status assess(VillageAllegianceRegistrySavedData registry, SettlementRecord town, long gameTime) {
        var residents = registry.residentRoster(town.id(), gameTime);
        Set<UUID> adults = residents.stream().filter(VillageAllegianceRegistrySavedData.ResidentRecord::adult)
                .map(VillageAllegianceRegistrySavedData.ResidentRecord::id).collect(Collectors.toSet());
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
                        .anyMatch(area -> area.type().foodProduction() && staffed(town, area, adults));
                case KITCHEN -> staffed(town, SettlementAreaType.KITCHEN, adults);
                case GUARD_POST -> staffed(town, SettlementAreaType.GUARD_POST, adults);
                case WORKSHOP -> staffed(town, SettlementAreaType.WORKSHOP, adults);
                case TRADING_POST -> staffed(town, SettlementAreaType.TRADING_POST, adults);
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
        town.support().resetAdvancement();
        town.advance();
        return true;
    }

    private static boolean staffed(SettlementRecord town, SettlementAreaType type, Set<UUID> adults) {
        return town.areas().values().stream().anyMatch(area -> area.type() == type && staffed(town, area, adults));
    }

    private static boolean staffed(SettlementRecord town, SettlementArea area, Set<UUID> adults) {
        var survey = town.surveys().get(area.id());
        boolean equipped = !SettlementBuildingSurvey.requiresFixture(area.type())
                || survey != null && !survey.workstations().isEmpty();
        return area.enabled() && equipped && town.duties().entrySet().stream()
                .anyMatch(duty -> duty.getValue().equals(area.id()) && adults.contains(duty.getKey())
                        && (!town.workerSettings().containsKey(duty.getKey()) || town.workerSettings().get(duty.getKey()).enabled()));
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
