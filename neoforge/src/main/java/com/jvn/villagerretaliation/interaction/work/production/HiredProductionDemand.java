package com.jvn.villagerretaliation.interaction.work.production;

import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

public final class HiredProductionDemand {
    private HiredProductionDemand() { }

    public record Requirement(UUID order, String name, WorkSupplyRequirement.Unit unit, long required, long supplied, long missing) {
        public Requirement {
            if (order == null || name == null || name.length() > 128 || unit == null || required < 0 || supplied < 0
                    || supplied > required || missing < -1 || missing > required) throw new IllegalArgumentException("Invalid production requirement");
        }
    }

    public static List<Requirement> inspect(ServerLevel level, Villager worker, net.minecraft.server.level.ServerPlayer owner, HiredProductionQueue queue, java.util.Map<UUID, Long> stock) {
        if (queue == null) return List.of();
        var supplies = new ArrayList<ItemStack>();
        var inventory = HiredJobInventory.getJobInventory(worker);
        for (int slot : inventory.supplySlots()) supplies.add(inventory.getItem(slot).copy());
        var state = com.jvn.villagerretaliation.interaction.HiredWorkStateStore.state(worker);
        boolean assigned = !state.contains("UseAssignedStorageForSupplies") || state.getBoolean("UseAssignedStorageForSupplies");
        if (assigned) supplies.addAll(AssignedStorageService.productionSupplies(worker));
        boolean enabled = !state.contains("Enabled") || state.getBoolean("Enabled");
        var waterPosition = net.minecraft.core.BlockPos.of(state.getLong("BrewingCachedWaterPos"));
        boolean water = state.contains("BrewingCachedWaterPos") && level.hasChunkAt(waterPosition)
                && level.getFluidState(waterPosition).is(net.minecraft.tags.FluidTags.WATER) && level.getFluidState(waterPosition).isSource();
        boolean unknown = assigned && AssignedStorageService.assignedStorage(level, worker).stream()
                .filter(record -> record.dimension().equals(level.dimension()) && !record.purpose().equals(AssignedStorageService.OUTPUT_PURPOSE))
                .anyMatch(record -> {
                    String status = AssignedStorageService.containerStatus(level, record.pos(), record.purpose(), owner);
                    return !status.equals("available") && !status.equals("full");
                });
        var result = new ArrayList<Requirement>();
        for (var order : queue.orders()) {
            for (var allocation : queue.allocations()) {
                if (!allocation.order().equals(order.id())) continue;
                if (!allocation.started()) {
                    describe(level, order, allocation.remaining(), supplies, new ArrayList<>(), 0, 0, unknown, water, result);
                } else if (allocation.station() != null) {
                    var machine = level.hasChunkAt(allocation.station()) ? level.getBlockEntity(allocation.station()) : null;
                    var privateSupplies = new ArrayList<ItemStack>();
                    long amount = allocation.remaining();
                    long burn = 0;
                    long uses = 0;
                    if (machine instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity furnace) {
                        var tag = furnace.saveWithoutMetadata(level.registryAccess());
                        var items = net.minecraft.core.NonNullList.withSize(3, ItemStack.EMPTY);
                        net.minecraft.world.ContainerHelper.loadAllItems(tag, items, level.registryAccess());
                        amount = Math.max(0, amount - (order.matches(items.get(2)) ? items.get(2).getCount() : 0));
                        privateSupplies.add(items.get(0).copy());
                        privateSupplies.add(items.get(1).copy());
                        boolean fast = !furnace.getBlockState().is(net.minecraft.world.level.block.Blocks.FURNACE);
                        burn = (Math.max(0, tag.getInt("BurnTime")) + Math.max(0, tag.getInt("CookTime"))) * (fast ? 2L : 1L);
                    } else if (machine instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity stand) {
                        var tag = stand.saveWithoutMetadata(level.registryAccess());
                        var items = net.minecraft.core.NonNullList.withSize(5, ItemStack.EMPTY);
                        net.minecraft.world.ContainerHelper.loadAllItems(tag, items, level.registryAccess());
                        var route = com.jvn.villagerretaliation.interaction.work.brewing.HiredBrewingRecipeCatalog.find(level, order.recipe().brewingItem(), order.recipe().potion()).orElse(null);
                        int step = route == null ? -1 : com.jvn.villagerretaliation.interaction.work.brewing.HiredBrewingRecipeCatalog.currentStep(level, items, route);
                        if (step < 0) { result.add(unavailable(order)); continue; }
                        int bottles = (int) items.subList(0, 3).stream().filter(stack -> !stack.isEmpty()).count();
                        if (bottles > 0) privateSupplies.add(net.minecraft.world.item.alchemy.PotionContents.createItemStack(net.minecraft.world.item.Items.POTION,
                                net.minecraft.world.item.alchemy.Potions.WATER).copyWithCount(bottles));
                        for (int index = 0; index < step; index++) privateSupplies.add(new ItemStack(route.ingredients().get(index)));
                        privateSupplies.add(items.get(3).copy());
                        privateSupplies.add(items.get(4).copy());
                        uses = step + Math.max(0, tag.getInt("Fuel")) + (tag.getInt("BrewTime") > 0 ? 1 : 0);
                    } else { result.add(unavailable(order)); continue; }
                    describe(level, order, amount, supplies, privateSupplies, burn, uses, unknown, water, result);
                }
            }
            long amount = enabled && order.runnable() ? order.mode() == HiredProductionOrder.Mode.CONTINUOUS ? order.output().getCount()
                    : queue.available(order.id(), stock.getOrDefault(order.id(), -1L)) : 0;
            describe(level, order, amount, supplies, new ArrayList<>(), 0, 0, unknown, water, result);
        }
        return List.copyOf(result);
    }
    private static Requirement unavailable(HiredProductionOrder order) {
        return new Requirement(order.id(), "Station contents unavailable", WorkSupplyRequirement.Unit.ITEMS, 0, 0, -1);
    }

    private static void describe(ServerLevel level, HiredProductionOrder order, long amount, List<ItemStack> supplies,
                                 List<ItemStack> privateSupplies, long burn, long uses, boolean unknown, boolean water, List<Requirement> result) {
        if (amount <= 0) return;
        var available = new ArrayList<>(privateSupplies);
        available.addAll(supplies);
        var plan = WorkProductionDemand.describe(level, order.recipe(), amount, available, water);
        if (!plan.blocker().isEmpty()) { result.add(unavailable(order)); return; }
        int count = 0;
        for (var need : plan.requirements()) {
            if (++count > 15) {
                result.add(new Requirement(order.id(), "Additional input groups", WorkSupplyRequirement.Unit.ITEMS, 0, 0, -1));
                break;
            }
            long remaining = Math.max(0, need.quantity() - (need.unit() == WorkSupplyRequirement.Unit.BURN_TICKS ? burn
                    : need.unit() == WorkSupplyRequirement.Unit.BREW_USES ? uses : need.unit() == WorkSupplyRequirement.Unit.WATER_FILLS && water ? need.quantity() : 0));
            for (var pool : List.of(privateSupplies, supplies)) for (var sample : pool) {
                long value = need.value(sample);
                if (value <= 0) continue;
                int used = (int) Math.min(sample.getCount(), (remaining + value - 1) / value);
                sample.shrink(used);
                remaining = Math.max(0, remaining - used * value);
            }
            String name = need.alternatives().isEmpty() ? need.key() : need.alternatives().getFirst().getHoverName().getString()
                    + (need.alternatives().size() > 1 ? " or compatible alternative" : "");
            result.add(new Requirement(order.id(), name.length() > 128 ? name.substring(0, 128) : name, need.unit(), need.quantity(),
                    need.quantity() - remaining, unknown && remaining > 0 ? -1 : remaining));
        }
    }
}
