package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData.AllegianceRecord;
import com.jvn.villagerretaliation.block.VillagerRetaliationBlocks;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.villager.VillagerRecoveryService;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;

public final class SettlementLeadershipService {
    private SettlementLeadershipService() {
    }

    public static void tick(MinecraftServer server) {
        if (server.overworld().getGameTime() % 200L != 0L) {
            return;
        }
        var registry = VillageAllegianceRegistrySavedData.get(server.overworld());
        for (ServerLevel level : server.getAllLevels()) {
            for (AllegianceRecord village : registry.activeRecords(level.dimension().location())) {
                if (!registry.isManaged(village.id()) && !village.residents().isEmpty()) {
                    observe(level, village);
                }
            }
        }
    }

    static void observe(ServerLevel level, AllegianceRecord village) {
        var residents = village.activeResidents(level.getGameTime());
        boolean fullyLoaded = !residents.isEmpty();
        boolean nourished = true;
        long nutrition = 0L;
        for (var resident : residents) {
            if (!(level.getEntity(resident.id()) instanceof Villager villager) || !villager.isAlive()) {
                fullyLoaded = false;
                continue;
            }
            nourished &= VillagerRecoveryService.foodLevel(villager) > 0;
            for (var stack : VillagerInventoryAccess.captureFullInventory(villager)) {
                if (SettlementFoodService.isMeal(stack)) {
                    nutrition += (long) stack.getCount() * stack.get(DataComponents.FOOD).nutrition();
                }
            }
        }
        Set<ChunkPos> chunks = new HashSet<>();
        for (long section : village.footprintSections()) {
            chunks.add(new ChunkPos(SectionPos.x(section), SectionPos.z(section)));
        }
        Set<BlockPos> beds = new HashSet<>();
        for (ChunkPos chunk : chunks) {
            if (!level.hasChunk(chunk.x, chunk.z)) {
                fullyLoaded = false;
                continue;
            }
            level.getPoiManager().getInChunk(type -> type.is(PoiTypes.HOME), chunk, PoiManager.Occupancy.ANY)
                    .map(poi -> poi.getPos())
                    .filter(pos -> village.footprintSections().contains(SectionPos.asLong(pos)))
                    .filter(pos -> SettlementBuildingSurvey.usableBed(level, pos))
                    .forEach(beds::add);
        }
        int population = residents.size();
        long requiredNutrition = (long) population * SettlementFoodService.dailyNutrition()
                * Math.clamp(VillagerRetaliationConfig.SETTLEMENT_FOOD_RESERVE_DAYS.get(), 1, 30);
        SettlementLeadershipSavedData.get(level).observe(village.id(), level.getGameTime(), population, beds.size(),
                nutrition, fullyLoaded, population > 0 && beds.size() >= population && nourished && nutrition >= requiredNutrition);
    }

    public static Optional<Status> status(ServerPlayer player, VillageAllegianceId id) {
        var level = player.serverLevel();
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var village = registry.canonicalRecord(id).orElse(null);
        if (village == null || village.archived() || registry.isManaged(village.id())
                || !level.dimension().location().equals(village.originDimension())) {
            return Optional.empty();
        }
        var adults = village.activeAdultResidents(level.getGameTime());
        int trusted = (int) adults.stream().filter(resident -> VillagerReputationLevel.fromReputation(
                        VillagerReputationManager.getReputation(level, resident.id(), player.getUUID()))
                .trustRank() >= VillagerReputationLevel.REVERED.trustRank()).count();
        var support = SettlementLeadershipSavedData.get(level).observation(village.id());
        long required = SettlementProgressionService.requiredTicks();
        boolean eligible = !adults.isEmpty() && trusted > adults.size() / 2 && support.fullyLoaded()
                && support.sustainedTicks() >= required;
        return Optional.of(new Status(village.id(), adults.size(), trusted, support, required, eligible));
    }

    public static Optional<SettlementRecord> accept(ServerPlayer player, VillageAllegianceId id, BlockPos table) {
        var level = player.serverLevel();
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var village = registry.canonicalRecord(id).orElse(null);
        if (village == null || !player.isAlive() || player.isSpectator() || player.distanceToSqr(table.getCenter()) > 64.0D
                || !level.hasChunkAt(table)
                || !level.getBlockState(table).is(VillagerRetaliationBlocks.SETTLEMENT_MANAGEMENT.get())
                || !registry.discoverAt(level, table).flatMap(registry::canonical).filter(village.id()::equals).isPresent()
                || !SettlementSavedData.get(level).canEstablish(level.dimension().location(), table)) {
            return Optional.empty();
        }
        observe(level, village);
        if (!status(player, village.id()).map(Status::eligible).orElse(false)) {
            return Optional.empty();
        }
        var town = SettlementSavedData.get(level).establish(registry, village.id(), player.getUUID(), table, level.getGameTime());
        if (town.isPresent()) {
            SettlementLeadershipSavedData.get(level).remove(village.id());
        }
        return town;
    }

    public record Status(VillageAllegianceId village, int adults, int reveredAdults,
                         SettlementLeadershipSavedData.Observation support, long requiredTicks, boolean eligible) {
    }
}
