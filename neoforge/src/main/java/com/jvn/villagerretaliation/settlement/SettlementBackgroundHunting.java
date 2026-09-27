package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.HiredRangedAmmo;
import com.jvn.villagerretaliation.interaction.work.HuntingWorker;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.inventory.VillagerItemFilterService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

final class SettlementBackgroundHunting {
    private SettlementBackgroundHunting() {
    }

    static void tick(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, int elapsed, double multiplier) {
        var area = town.areas().get(worker.area());
        if (area == null || !area.enabled() || !area.contains(worker.position())) return;
        var owner = worker.context(level, town);
        if (owner == null) {
            worker.status("awaiting_worker_observation");
            return;
        }
        var inventory = HiredJobInventory.getJobInventory(owner);
        var allDepots = SettlementStock.depots(town).stream().filter(SettlementDepot::rulesObserved).toList();
        var routes = new SettlementFamilyRoutes(level, town);
        var access = new java.util.HashMap<UUID, Integer>();
        var depots = allDepots.stream().filter(depot -> {
            var path = routes.route(worker.position(), depot.position());
            if (path.isEmpty()) return false;
            access.put(depot.id(), Math.max(0, path.size() - 1));
            return true;
        }).sorted(Comparator.comparingInt(SettlementDepot::priority).reversed()
                .thenComparingInt(depot -> access.get(depot.id())).thenComparing(SettlementDepot::id)).toList();
        if (!worker.huntingEngaged() && (!worker.settings().contains("AutoDepositOutputs") || worker.settings().getBoolean("AutoDepositOutputs"))
                && SettlementBackgroundHauling.depositCarried(level, town, worker, elapsed, multiplier, depots, false)) {
            if (worker.status().equals("storage_full") && depots.stream().noneMatch(depot -> depot.purpose().acceptsOutputs())
                    && allDepots.stream().anyMatch(depot -> depot.purpose().acceptsOutputs())) worker.status("hunting_storage_unreachable");
            return;
        }
        var budget = new SettlementWorkBudget(worker, elapsed, multiplier);
        try {
            var weapon = inventory.equipBestTool(HuntingWorker::isHuntingWeapon, HuntingWorker::huntingWeaponScore);
            if (weapon.isEmpty()) {
                Predicate<ItemStack> allowed =
                        stack -> HuntingWorker.isHuntingWeapon(stack) && VillagerItemFilterService.mayWithdraw(owner, stack);
                SettlementBackgroundTools.fetchTool(level, worker, inventory, depots, allowed,
                        HuntingWorker::huntingWeaponScore, "hunting_weapon", budget);
                if (worker.status().equals("missing_hunting_weapon") && inaccessibleSupply(level, worker, inventory,
                        allDepots, depots, allowed)) worker.status("hunting_storage_unreachable");
                return;
            }
            if (!worker.huntingEngaged() && SettlementItemDropService.collect(level, town, worker, owner, inventory, budget, HuntingWorker::isHuntingLoot)) {
                worker.status(switch (worker.status()) {
                    case "collected_animal_drops" -> "collected_hunting_loot";
                    case "collecting_animal_drops" -> "collecting_hunting_loot";
                    case "animal_drop_unreachable" -> "hunting_loot_unreachable";
                    default -> worker.status();
                });
                return;
            }
            if (HiredRangedAmmo.isWeaponRequiringAmmo(weapon) && inventory.findSupply(HiredRangedAmmo::isAmmo).isEmpty()) {
                if (inventory.promoteOutputToSupply(HiredRangedAmmo::isAmmo, 64) > 0) {
                    worker.status("restocked_hunting_ammo");
                    return;
                }
                Predicate<ItemStack> allowed =
                        stack -> HiredRangedAmmo.isAmmo(stack) && VillagerItemFilterService.mayWithdraw(owner, stack);
                restockAmmo(level, worker, inventory, depots, access, budget, allowed);
                if (worker.status().equals("missing_ranged_ammo") && inaccessibleSupply(level, worker, inventory,
                        allDepots, depots, allowed)) worker.status("hunting_storage_unreachable");
                return;
            }
            var target = SettlementHuntingTargetService.select(level, town, worker, owner);
            worker.huntingTarget(target == null ? null : target.getUUID());
            worker.status(target == null ? "no_observed_hunting_target" : "tracking_hunting_target");
            if (target != null) SettlementHuntingCombat.tick(level, town, worker, owner, target, elapsed);
            town.changed();
        } finally {
            var snapshot = inventory.captureSnapshot();
            if (!snapshot.equals(worker.inventory())) worker.changed();
            worker.inventorySnapshot(snapshot);
        }
    }

    private static boolean inaccessibleSupply(ServerLevel level, SettlementWorkerState worker, HiredJobInventory inventory,
                                               List<SettlementDepot> all, List<SettlementDepot> accessible,
                                               Predicate<ItemStack> filter) {
        return SettlementProductionInventory.supplies(level, worker, SettlementBackgroundTools.items(inventory), all).stream()
                .anyMatch(source -> source.depot() != null && !accessible.contains(source.depot()) && filter.test(source.available()));
    }

    private static void restockAmmo(ServerLevel level, SettlementWorkerState worker, HiredJobInventory inventory,
                                    List<SettlementDepot> depots, Map<UUID, Integer> access,
                                    SettlementWorkBudget budget,
                                    Predicate<ItemStack> allowed) {
        var supplies = SettlementProductionInventory.supplies(level, worker, SettlementBackgroundTools.items(inventory), depots);
        boolean full = false;
        for (int index = 0; index < supplies.size(); index++) {
            var source = supplies.get(index);
            if (source.depot() == null || !allowed.test(source.available())) continue;
            int requested = Math.min(64, source.available().getCount());
            var before = inventory.captureSnapshot();
            var remainder = inventory.insertSupplyFromStorage(source.available().copyWithCount(requested));
            int moved = requested - remainder.getCount();
            var after = inventory.captureSnapshot();
            inventory.restoreSnapshot(before);
            if (moved <= 0) {
                full = true;
                continue;
            }
            var consumed = new ArrayList<>(java.util.Collections.nCopies(supplies.size(), 0));
            consumed.set(index, moved);
            if (!SettlementProductionInventory.allowed(level, supplies, consumed)) continue;
            if (!budget.spend(40.0D + access.get(source.depot().id()) * 16.0D)) {
                worker.status("fetching_hunting_ammo");
                return;
            }
            source.depot().removeItem(source.slot(), moved);
            inventory.restoreSnapshot(after);
            worker.status("restocked_hunting_ammo");
            return;
        }
        worker.status(full ? "supply_full" : "missing_ranged_ammo");
    }
}
