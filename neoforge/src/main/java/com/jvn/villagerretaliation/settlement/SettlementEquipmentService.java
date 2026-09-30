package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.work.HiredEquipmentNeed;
import com.jvn.villagerretaliation.interaction.work.HiredRangedAmmo;
import com.jvn.villagerretaliation.interaction.work.HiredStorageNavigationGoal;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState;
import com.jvn.villagerretaliation.interaction.work.WorkResult;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.inventory.VillagerDefensiveLoadoutService;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.inventory.VillagerItemFilterService;
import com.jvn.villagerretaliation.party.PartyQuickCommandService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

public final class SettlementEquipmentService {
    private SettlementEquipmentService() { }

    public static WorkResult tick(ServerLevel level, Villager villager, HiredWorkContext context, double speed) {
        var duty = SettlementWorkforceService.duty(villager).orElse(null);
        var configuration = duty == null ? null : configuration(duty.town(), duty.area());
        if (configuration == null || villager.getTarget() != null || villager.getLastHurtByMob() != null) return null;
        if (VillagerInventoryAccess.hasOpenInventory(villager)) return blocked(context, "equipment_busy");
        for (var selection : configuration.selections()) {
            if (!selection.equip() || selection.kind().guardSlot() != EquipmentSlot.MAINHAND) continue;
            var result = equip(level, villager, context, selection, speed);
            if (result != null) return result;
        }
        var ammunition = configuration.selection(HiredEquipmentNeed.Kind.AMMUNITION);
        WorkResult reserveBlocker = null;
        if (ammunition != null) {
            reserveBlocker = HiredRangedAmmo.maintainReserve(level, villager, context, speed, ammunition.spares());
            if (reserveBlocker != null && (reserveBlocker.progressed() || !HiredRangedAmmo.hasAmmoForEquippedWeapon(context, villager)))
                return reserveBlocker;
        }
        for (var selection : configuration.selections()) {
            if (!selection.equip() || selection.kind().guardSlot() == EquipmentSlot.MAINHAND) continue;
            var result = equip(level, villager, context, selection, speed);
            if (result != null) return result;
        }
        return reserveBlocker;
    }

    private static WorkResult equip(ServerLevel level, Villager villager, HiredWorkContext context,
                                    SettlementEquipmentPlan.Selection selection, double speed) {
        var slot = selection.kind().guardSlot();
        if (slot == EquipmentSlot.MAINHAND && PartyQuickCommandService.hasWeaponsUnequipped(villager))
            return blocked(context, "equipment_manual");
        if (retained(villager, selection)) return null;
        var inventory = context.inventory();
        var blocker = inventory.equipmentBlocker(slot);
        if (blocker != HiredJobInventory.EquipmentBlocker.NONE)
            return blocked(context, "equipment_" + blocker.name().toLowerCase(Locale.ROOT));
        var need = HiredEquipmentNeed.of(selection.kind());
        if (!inventory.equipBestEquipment(slot, need, ignored -> 1).isEmpty()) return equipped(context);
        if (!inventory.findSupply(need).isEmpty()) return blocked(context, "equipment_capacity");
        if (!context.useAssignedStorageForSupplies()) return blocked(context, "missing_equipment");
        var storage = AssignedStorageService.nearestAssignedToolStoragePosContaining(level, villager, need);
        if (storage == null) return blocked(context, "missing_equipment");
        HiredWorkerBrain.setStorageTarget(context, storage);
        var movement = HiredStorageNavigationGoal.moveToStorageTarget(level, context, villager, storage, speed);
        if (movement == HiredStorageNavigationGoal.Result.MOVING) {
            HiredWorkerBrain.clearFailure(context);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.MOVING_TO_STORAGE, storage);
            return WorkResult.progressed("interaction.work.status.fetching_equipment");
        }
        if (movement == HiredStorageNavigationGoal.Result.FAILED) {
            AssignedStorageService.rememberToolStorageFailure(level, villager, storage, "equipment_storage_unreachable");
            return blocked(context, "equipment_storage_unreachable");
        }
        boolean[] capacity = {false};
        int moved = AssignedStorageService.transferToolAtAssignedStorage(villager, storage, need, stack -> {
            if (!inventory.equipReplacement(slot, stack)) {
                capacity[0] = true;
                return stack;
            }
            return ItemStack.EMPTY;
        });
        if (moved <= 0) return blocked(context, capacity[0] ? "equipment_capacity" : "missing_equipment");
        AssignedStorageService.clearStorageFailure(level, villager, storage);
        return equipped(context);
    }

    private static WorkResult equipped(HiredWorkContext context) {
        HiredStorageNavigationGoal.clearStorageTarget(context);
        HiredWorkerBrain.clearFailure(context);
        HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, context.workCenter());
        return WorkResult.progressed("interaction.work.status.equipped_equipment");
    }

    private static WorkResult blocked(HiredWorkContext context, String status) {
        HiredStorageNavigationGoal.clearStorageTarget(context);
        HiredWorkerBrain.setFailure(context, status, 0L);
        HiredWorkerBrain.setState(context, status.equals("equipment_capacity")
                ? HiredWorkerTaskState.PAUSED_FULL_INVENTORY : HiredWorkerTaskState.AWAITING_INSTRUCTION);
        return WorkResult.idle("interaction.work.status." + status);
    }

    static void tick(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, int elapsed, double multiplier) {
        var area = town.workplace(worker.area());
        if (area == null || !area.enabled() || area.type().role() != HiredVillagerRole.COMBAT || worker.unsafe()) return;
        var configuration = configuration(town, area);
        var owner = worker.context(level, town);
        if (owner == null) {
            worker.status("awaiting_worker_observation");
            return;
        }
        var inventory = HiredJobInventory.getJobInventory(owner);
        var sources = SettlementEquipmentDemand.civicSources(level, town, worker.id(), worker.area(), worker.role());
        var depots = SettlementStock.depots(town).stream().filter(SettlementDepot::rulesObserved)
                .filter(depot -> sources.contains(SettlementSupplyStock.positions(level, depot.position()).getFirst())).toList();
        var budget = new SettlementWorkBudget(worker, elapsed, multiplier);
        var selections = configuration == null ? List.<SettlementEquipmentPlan.Selection>of() : configuration.selections();
        try {
            for (var selection : selections)
                if (selection.equip() && selection.kind().guardSlot() == EquipmentSlot.MAINHAND
                        && equip(level, town, worker, inventory, depots, selection, budget)) return;
            var ammunition = configuration == null ? null : configuration.selection(HiredEquipmentNeed.Kind.AMMUNITION);
            var weapon = inventory.getItem(HiredJobInventory.MAINHAND_SLOT);
            if (!HiredRangedAmmo.isWeaponRequiringAmmo(weapon)) weapon = owner.getMainHandItem();
            int target = ammunition != null ? ammunition.spares()
                    : HiredRangedAmmo.canUseRangedAttack(owner, weapon) ? 0 : HiredRangedAmmo.RESTOCK_COUNT;
            String reserveBlocker = "";
            if (target > 0 && HiredRangedAmmo.isWeaponRequiringAmmo(weapon)
                    && restock(level, town, worker, inventory, depots, target, budget,
                            depot -> 16.0D * Math.sqrt(worker.position().distSqr(depot.position())))) {
                if (!worker.status().equals("missing_ranged_ammo") && !worker.status().equals("supply_full")
                        || !HiredRangedAmmo.canUseRangedAttack(owner, weapon)) return;
                reserveBlocker = worker.status();
            }
            for (var selection : selections)
                if (selection.equip() && selection.kind().guardSlot() != EquipmentSlot.MAINHAND
                        && equip(level, town, worker, inventory, depots, selection, budget)) return;
            worker.status(reserveBlocker.isEmpty() ? "equipment_ready" : reserveBlocker);
        } finally {
            var snapshot = inventory.captureSnapshot();
            if (!snapshot.equals(worker.inventory())) worker.changed();
            worker.inventorySnapshot(snapshot);
        }
    }

    private static boolean equip(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, HiredJobInventory inventory,
                                 List<SettlementDepot> depots, SettlementEquipmentPlan.Selection selection, SettlementWorkBudget budget) {
        var owner = inventory.villager();
        var slot = selection.kind().guardSlot();
        if (slot == EquipmentSlot.MAINHAND && PartyQuickCommandService.hasWeaponsUnequipped(owner)) {
            worker.status("equipment_manual");
            return true;
        }
        if (retained(owner, selection)) return false;
        var need = HiredEquipmentNeed.of(selection.kind());
        SettlementBackgroundTools.fetchEquipment(level, town, worker, inventory, depots,
                need, ignored -> 1, "equipment", slot, budget);
        return true;
    }

    static boolean restock(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, HiredJobInventory inventory,
                           List<SettlementDepot> depots, int target, SettlementWorkBudget budget,
                           java.util.function.ToDoubleFunction<SettlementDepot> travelCost) {
        int missing = Math.max(0, target - HiredRangedAmmo.carriedAmmo(inventory.villager()));
        if (missing == 0) return false;
        if (inventory.promoteOutputToSupply(HiredRangedAmmo::isAmmo, missing) > 0) {
            worker.status("restocked_ammo");
            return true;
        }
        var supplies = SettlementProductionInventory.supplies(level, town, worker, SettlementBackgroundTools.items(inventory), depots);
        boolean full = false;
        for (int index = 0; index < supplies.size(); index++) {
            var source = supplies.get(index);
            if (source.depot() == null || !HiredRangedAmmo.isAmmo(source.available())
                    || !VillagerItemFilterService.mayWithdraw(inventory.villager(), source.available())) continue;
            int allowance = SettlementAllocationService.inspect(level, town, worker.id(), null,
                    source.depot().position(), source.available()).allowance();
            int requested = Math.min(missing, Math.min(allowance, source.available().getCount()));
            if (requested <= 0) continue;
            int accepted = inventory.supplyCapacityFromStorage(source.available(), requested);
            if (accepted == 0) {
                full = true;
                continue;
            }
            var consumed = new ArrayList<>(java.util.Collections.nCopies(supplies.size(), 0));
            consumed.set(index, accepted);
            if (!SettlementProductionInventory.allowed(level, supplies, consumed)) continue;
            if (!budget.spend(40.0D + travelCost.applyAsDouble(source.depot()))) {
                worker.status("fetching_ammo");
                return true;
            }
            var remainder = inventory.insertSupplyFromStorage(source.available().copyWithCount(accepted));
            source.depot().removeItem(source.slot(), accepted - remainder.getCount());
            worker.status("restocked_ammo");
            return true;
        }
        worker.status(full ? "supply_full" : "missing_ranged_ammo");
        return true;
    }

    static boolean retained(Villager owner, SettlementEquipmentPlan.Selection selection) {
        var need = HiredEquipmentNeed.of(selection.kind());
        return need.test(owner.getItemBySlot(selection.kind().guardSlot()))
                || selection.kind() == HiredEquipmentNeed.Kind.SHIELD
                && VillagerDefensiveLoadoutService.hasBorrowedGuardShield(owner) && need.test(owner.getMainHandItem());
    }

    public static int ammunitionTarget(Villager villager) {
        var duty = SettlementWorkforceService.duty(villager).orElse(null);
        return duty == null ? 0 : ammunitionTarget(duty.town(), duty.area().id());
    }

    static int ammunitionTarget(SettlementRecord town, java.util.UUID area) {
        var configuration = configuration(town, town.workplace(area));
        var ammunition = configuration == null ? null : configuration.selection(HiredEquipmentNeed.Kind.AMMUNITION);
        return ammunition == null ? 0 : ammunition.spares();
    }

    private static SettlementEquipmentPlan.Configuration configuration(SettlementRecord town, SettlementArea area) {
        if (area == null || !area.enabled()
                || area.type().role() != HiredVillagerRole.COMBAT && area.type().role() != HiredVillagerRole.HUNTING) return null;
        var plan = town.equipmentPlans().get(area.id());
        return plan != null && plan.authorized(town) ? plan.configuration() : null;
    }
}
