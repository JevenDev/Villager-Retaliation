package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.brewing.BrewingWorker;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionService;
import com.jvn.villagerretaliation.interaction.work.production.ProductionManagementService;
import com.jvn.villagerretaliation.network.ProductionRequestPayload;
import com.jvn.villagerretaliation.network.ProductionSnapshotPayload;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

public final class SettlementProductionService {
    private SettlementProductionService() {
    }

    public static String append(ServerPlayer player, SettlementRecord town, UUID areaId, long revision, HiredProductionOrder requested) {
        if (!permits(player, town, areaId)) return "Worksite permission or assignment changed";
        var role = town.areas().get(areaId).type().role();
        var error = HiredProductionService.validate(player.serverLevel(), role, requested);
        if (!error.isEmpty()) return error;
        if (requested.storage() != null && !SettlementSupplyStock.permitsOutput(player.serverLevel(), town, areaId, requested.storage())) {
            return "Choose permitted output storage";
        }
        var queue = town.productionQueues().get(areaId);
        if (queue == null ? revision != 0L : queue.configurationRevision() != revision) return "Production orders changed";
        if (queue == null && role == com.jvn.villagerretaliation.interaction.HiredVillagerRole.BREWING) {
            return SettlementBrewingMigration.append(player, town, areaId, requested);
        }
        if (queue == null) queue = town.beginProduction(player.getUUID(), areaId);
        if (queue == null || !queue.append(requested)) return "Production queue is full or the order already exists";
        return "";
    }

    public static String edit(ServerPlayer player, SettlementRecord town, UUID areaId, long revision,
                              UUID order, HiredProductionService.Edit edit, int index) {
        if (!permits(player, town, areaId)) return "Worksite permission or assignment changed";
        var queue = town.productionQueues().get(areaId);
        if (queue == null || queue.configurationRevision() != revision) return "Production orders changed";
        return HiredProductionService.edit(queue, order, edit, index) ? "" : "Order has unfinished production or is unavailable";
    }

    public static ProductionSnapshotPayload snapshot(ServerPlayer player,
            ProductionRequestPayload request, boolean applied, String feedback) {
        var level = player.serverLevel();
        var target = request.target();
        var data = SettlementSavedData.get(level);
        var town = data.get(new VillageAllegianceId(target.town())).orElse(null);
        if (town == null || !town.id().value().equals(target.town()) || !town.dimension().equals(level.dimension().location())
                || !town.permits(player.getUUID(), SettlementPermission.VIEW) || town.workplace(target.subject()) == null) {
            return ProductionManagementService.unavailable(player, request, "Worksite is unavailable");
        }
        var area = town.workplace(target.subject());
        if (!ProductionManagementService.supports(area.type().role())) {
            return ProductionManagementService.unavailable(player, request, "This worksite does not have production orders");
        }
        var queue = town.productionQueues().get(area.id());
        var storages = SettlementSupplyStock.storages(level, town, area.id(), true).stream().limit(128)
                .map(position -> new ProductionSnapshotPayload.Storage(position, position.toShortString(),
                        SettlementSupplyStock.contents(level, town, position) != null)).toList();
        var legacy = new ArrayList<ProductionSnapshotPayload.Legacy>();
        int legacyCount = 0;
        String blocker = data.readOnly() ? "Settlement data requires recovery"
                : !town.worksitePlans().containsKey(area.id()) ? "Adopt worksite settings before adding shared orders" : "";
        if (queue == null && area.type().role() == HiredVillagerRole.BREWING) {
            for (var resident : town.duties().entrySet().stream().filter(entry -> area.id().equals(entry.getValue()))
                    .map(Map.Entry::getKey).sorted().toList()) {
                var worker = town.workers().get(resident);
                var state = worker == null ? null : worker.settings();
                if (level.getEntity(resident) instanceof Villager villager) {
                    if (SettlementWorkforceService.hasPersonalCommitment(villager)) {
                        blocker = "Staff assignments changed";
                        continue;
                    }
                    state = HiredWorkStateStore.state(villager);
                    if (worker != null && worker.brewingVersion() > SettlementBrewingService.version(villager)) state = worker.settings();
                }
                if (state == null || !BrewingWorker.hasOrder(state)) continue;
                legacyCount++;
                int bottles = ProductionManagementService.activeBottles(level, state);
                if (bottles < 0 && state.contains("BrewingCachedStandPos", Tag.TAG_LONG)) {
                    var stand = town.brewingStands().get(BlockPos.of(state.getLong("BrewingCachedStandPos")));
                    if (stand != null) bottles = (int) stand.items(level.registryAccess()).subList(0, 3).stream().filter(stack -> !stack.isEmpty()).count();
                }
                var preview = ProductionManagementService.legacy(level, resident, state, bottles);
                if (preview == null) blocker = "A legacy brewing order requires recovery";
                else if (legacy.size() < 32) legacy.add(preview);
            }
            if (legacyCount >= 32) blocker = "Converting the legacy orders would leave no room for the new order";
        }
        var stock = new LinkedHashMap<UUID, Long>();
        var supply = new SettlementSupplyStock.Snapshot(level, town);
        if (queue != null) queue.orders().forEach(order -> stock.put(order.id(), SettlementProductionStock.count(level, town, queue, order, supply)));
        return new ProductionSnapshotPayload(level.dimension().location(), target, request.request(),
                applied, area.type().role(), area.name(), permits(player, town, area.id()) && blocker.isEmpty(), blocker, feedback,
                queue == null ? null : queue.save(level.registryAccess()), legacyCount, legacy, storages, stock,
                SettlementDemandService.inspect(level, town, player).demands().stream()
                        .filter(demand -> demand.area().equals(area.id()) && demand.kind()
                                == com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.Kind.PRODUCTION)
                        .map(demand -> com.jvn.villagerretaliation.interaction.work.production.HiredProductionDemand.Requirement.from(demand.supply())).limit(544).toList());
    }

    private static boolean permits(ServerPlayer player, SettlementRecord town, UUID areaId) {
        return player != null && player.isAlive() && !player.isSpectator() && town != null
                && town.dimension().equals(player.level().dimension().location())
                && !SettlementSavedData.get(player.serverLevel()).readOnly()
                && SettlementSavedData.get(player.serverLevel()).get(town.id()).orElse(null) == town
                && town.permits(player.getUUID(), SettlementPermission.STAFF)
                && town.workplace(areaId) != null && town.worksitePlans().containsKey(areaId);
    }
}
