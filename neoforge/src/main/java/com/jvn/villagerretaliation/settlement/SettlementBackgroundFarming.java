package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.FarmerHoeRequirement;
import com.jvn.villagerretaliation.interaction.work.HiredFarmingOptions;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.skill.HiredWorkPractice;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

final class SettlementBackgroundFarming {
    private SettlementBackgroundFarming() {
    }

    static void tick(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, int elapsed, double multiplier) {
        var depots = SettlementStock.depots(town).stream().filter(SettlementDepot::rulesObserved).toList();
        if ((!worker.settings().contains("AutoDepositOutputs") || worker.settings().getBoolean("AutoDepositOutputs"))
                && SettlementBackgroundHauling.depositCarried(level, town, worker, elapsed, multiplier, depots, false)) {
            return;
        }
        var carried = worker.items(level.registryAccess());
        if (!FarmerHoeRequirement.isHoe(carried.get(HiredJobInventory.MAINHAND_SLOT))) {
            equipHoe(level, worker, carried, depots, elapsed, multiplier);
            return;
        }
        var area = town.areas().get(worker.area());
        var plots = town.fields().values().stream().filter(plot -> area != null && area.enabled()
                        && area.type() == SettlementAreaType.FARM && SettlementFieldService.serves(area, plot))
                .sorted(Comparator.comparingInt(SettlementBackgroundFarming::priority)
                        .thenComparingDouble(plot -> plot.position().distSqr(worker.position()))).toList();
        for (var plot : plots) {
            if (!SettlementFieldService.available(level, plot)) {
                continue;
            }
            BlockState crop = plot.crop();
            boolean harvest = plot.harvestable();
            boolean plant = plot.canPlant();
            boolean till = crop.isAir() && !plot.soil().is(Blocks.FARMLAND) && SettlementFieldPlot.supportedSoil(plot.soil())
                    && HiredFarmingOptions.tillSoil(worker.settings());
            if (!harvest && !plant && !till) {
                continue;
            }
            var updated = worker.items(level.registryAccess());
            double cost = 40.0D + 16.0D * Math.sqrt(worker.position().distSqr(plot.position().above()));
            if (till) {
                worker.work(elapsed, multiplier);
                if (!worker.spend(cost)) {
                    worker.status("tilling");
                    return;
                }
                updated.get(HiredJobInventory.MAINHAND_SLOT).hurtAndBreak(1, level, (net.minecraft.world.entity.LivingEntity) null, item -> {});
                plot.change(Blocks.FARMLAND.defaultBlockState(), crop);
                commit(level, worker, plot, updated);
                worker.practice(level, HiredWorkPractice.farming("till"));
                worker.status("tilled");
                return;
            }
            List<ItemStack> drops = harvest
                    ? new ArrayList<>(Block.getDrops(crop, level, plot.position().above(), null, null, updated.get(HiredJobInventory.MAINHAND_SLOT)))
                    : new ArrayList<>();
            var supplies = SettlementProductionInventory.supplies(level, worker, updated, depots);
            int selected = -1;
            ItemStack seed = ItemStack.EMPTY;
            boolean replant = plot.canReplant() && (!harvest || crop.getBlock() instanceof CropBlock);
            if (harvest && replant) {
                for (ItemStack drop : drops) {
                    if (plantFor(drop) != null) {
                        seed = drop.copyWithCount(1);
                        drop.shrink(1);
                        break;
                    }
                }
            }
            if (seed.isEmpty() && replant) {
                List<Integer> consumed = new ArrayList<>(java.util.Collections.nCopies(supplies.size(), 0));
                for (int index = 0; index < supplies.size(); index++) {
                    var source = supplies.get(index);
                    if (plantFor(source.available()) == null) {
                        continue;
                    }
                    consumed.set(index, 1);
                    if (SettlementProductionInventory.allowed(level, supplies, consumed)) {
                        selected = index;
                        seed = source.available().copyWithCount(1);
                        if (source.depot() == null) {
                            updated.get(source.slot()).shrink(1);
                        } else {
                            cost += 16.0D * Math.sqrt(plot.position().distSqr(source.depot().position()));
                        }
                        break;
                    }
                    consumed.set(index, 0);
                }
            }
            if (!harvest && seed.isEmpty()) {
                continue;
            }
            if (!storeDrops(worker, updated, drops)) {
                worker.status("output_full");
                return;
            }
            worker.work(elapsed, multiplier);
            if (!worker.spend(cost)) {
                worker.status(harvest ? "harvesting" : "planting");
                return;
            }
            if (selected >= 0 && supplies.get(selected).depot() != null) {
                var source = supplies.get(selected);
                source.depot().removeItem(source.slot(), 1);
            }
            plot.change(plot.soil(), seed.isEmpty() ? Blocks.AIR.defaultBlockState() : plantFor(seed));
            commit(level, worker, plot, updated);
            if (SettlementFarmPlants.fruit(crop)) SettlementFieldService.releaseFruit(level, town, plot);
            worker.practice(level, HiredWorkPractice.farming(harvest ? seed.isEmpty() ? "harvest" : "harvest_replant" : "plant"));
            worker.status(harvest ? "harvested" : "planted");
            return;
        }
        worker.status(plots.isEmpty() ? "no_observed_field" : "waiting_for_crops");
    }

    private static int priority(SettlementFieldPlot plot) {
        return plot.harvestable() ? 0
                : plot.soil().is(Blocks.FARMLAND) && plot.crop().isAir() ? 1 : 2;
    }

    private static void equipHoe(ServerLevel level, SettlementWorkerState worker, NonNullList<ItemStack> carried,
                                 List<SettlementDepot> depots, int elapsed, double multiplier) {
        var supplies = SettlementProductionInventory.supplies(level, worker, carried, depots);
        for (int index = 0; index < supplies.size(); index++) {
            var source = supplies.get(index);
            if (!FarmerHoeRequirement.isHoe(source.available())) {
                continue;
            }
            List<Integer> consumed = new ArrayList<>(java.util.Collections.nCopies(supplies.size(), 0));
            consumed.set(index, 1);
            if (!SettlementProductionInventory.allowed(level, supplies, consumed)) {
                continue;
            }
            var updated = worker.items(level.registryAccess());
            if (source.depot() == null) {
                updated.get(source.slot()).shrink(1);
            }
            if (!SettlementProductionInventory.insertSupplies(worker, updated, List.of(updated.get(HiredJobInventory.MAINHAND_SLOT)))) {
                worker.status("supply_full");
                return;
            }
            double cost = 40.0D + (source.depot() == null ? 0.0D : 16.0D * Math.sqrt(worker.position().distSqr(source.depot().position())));
            worker.work(elapsed, multiplier);
            if (!worker.spend(cost)) {
                worker.status("fetching_hoe");
                return;
            }
            if (source.depot() != null) {
                source.depot().removeItem(source.slot(), 1);
            }
            updated.set(HiredJobInventory.MAINHAND_SLOT, source.available().copyWithCount(1));
            worker.updateInventory(level.registryAccess(), updated);
            worker.status("equipped_hoe");
            return;
        }
        worker.status("missing_hoe");
    }

    private static boolean storeDrops(SettlementWorkerState worker, NonNullList<ItemStack> items, List<ItemStack> drops) {
        int reserved = worker.supplySlots().stream().map(items::get).filter(stack -> plantFor(stack) != null).mapToInt(ItemStack::getCount).sum();
        for (ItemStack drop : drops) {
            if (plantFor(drop) != null && reserved < 8) {
                int count = Math.min(drop.getCount(), 8 - reserved);
                var trial = NonNullList.withSize(items.size(), ItemStack.EMPTY);
                for (int slot = 0; slot < items.size(); slot++) {
                    trial.set(slot, items.get(slot).copy());
                }
                if (SettlementProductionInventory.insertSupplies(worker, trial, List.of(drop.copyWithCount(count)))) {
                    for (int slot = 0; slot < items.size(); slot++) {
                        items.set(slot, trial.get(slot));
                    }
                    drop.shrink(count);
                    reserved += count;
                }
            }
        }
        return SettlementProductionInventory.insertOutputs(worker, items, drops);
    }

    private static BlockState plantFor(ItemStack seed) {
        return SettlementFarmPlants.plant(seed);
    }

    private static void commit(ServerLevel level, SettlementWorkerState worker, SettlementFieldPlot plot, NonNullList<ItemStack> items) {
        worker.updateInventory(level.registryAccess(), items);
        SettlementFieldService.applyLoaded(level, plot);
    }
}
