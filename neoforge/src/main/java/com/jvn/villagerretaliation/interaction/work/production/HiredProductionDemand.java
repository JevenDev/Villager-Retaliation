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

    public record Requirement(UUID id, UUID order, String name, WorkSupplyRequirement.Unit unit, long required, long supplied, long missing,
                              long availableNow, long committed, long incoming) {
        public Requirement {
            if (id == null || order == null || name == null || name.length() > 128 || unit == null || required < 0 || supplied < 0
                    || availableNow < 0 || committed < 0 || incoming < 0 || availableNow > required || committed > required || incoming > required
                    || supplied > required || missing < -1 || missing > required) throw new IllegalArgumentException("Invalid production requirement");
        }

        public Requirement(UUID order, String name, WorkSupplyRequirement.Unit unit, long required, long supplied, long missing) {
            this(UUID.nameUUIDFromBytes((order + ":diagnostic:" + unit + ":" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    order, name, unit, required, supplied, missing, supplied, 0, 0);
        }

        public static Requirement from(WorkSupplyDemand demand) {
            String name = WorkSupplyEvaluation.name(demand.requirement());
            return new Requirement(demand.id(), demand.consumer(), name.length() > 128 ? name.substring(0, 128) : name,
                    demand.requirement().unit(), demand.requirement().quantity(), demand.supplied(), demand.missing(),
                    demand.availableNow(), demand.committed(), demand.incoming());
        }

        private Requirement merge(Requirement other) {
            return new Requirement(id, order, name, unit, required + other.required, supplied + other.supplied,
                    missing < 0 || other.missing < 0 ? -1 : missing + other.missing,
                    availableNow + other.availableNow, committed + other.committed, incoming + other.incoming);
        }
    }

    public static List<Requirement> inspect(ServerLevel level, Villager worker, net.minecraft.server.level.ServerPlayer owner, HiredProductionQueue queue, java.util.Map<UUID, Long> stock) {
        if (queue == null) return List.of();
        var supplies = new ArrayList<ItemStack>();
        var carried = new ArrayList<ItemStack>();
        var inventory = HiredJobInventory.getJobInventory(worker);
        for (int slot : inventory.supplySlots()) carried.add(inventory.getItem(slot).copy());
        var state = com.jvn.villagerretaliation.interaction.HiredWorkStateStore.state(worker);
        boolean assigned = !state.contains("UseAssignedStorageForSupplies") || state.getBoolean("UseAssignedStorageForSupplies");
        if (assigned) supplies.addAll(AssignedStorageService.productionSupplies(worker));
        var sources = AssignedStorageService.assignedStorage(level, worker).stream()
                .filter(record -> assigned && record.dimension().equals(level.dimension()) && !record.purpose().equals(AssignedStorageService.OUTPUT_PURPOSE))
                .map(record -> com.jvn.villagerretaliation.settlement.SettlementSupplyStock.positions(level, record.pos()).getFirst()).distinct().toList();
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
                    describe(level, order, allocation.remaining(), supplies, carried, sources, new ArrayList<>(), 0, 0, unknown, water, result);
                } else if (allocation.station() != null) {
                    var machine = level.hasChunkAt(allocation.station()) ? level.getBlockEntity(allocation.station()) : null;
                    WorkProductionProgress progress;
                    if (machine instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity furnace) {
                        var tag = furnace.saveWithoutMetadata(level.registryAccess());
                        var items = net.minecraft.core.NonNullList.withSize(3, ItemStack.EMPTY);
                        net.minecraft.world.ContainerHelper.loadAllItems(tag, items, level.registryAccess());
                        progress = WorkProductionProgress.furnace(order, allocation.remaining(), items, tag,
                                !furnace.getBlockState().is(net.minecraft.world.level.block.Blocks.FURNACE));
                    } else if (machine instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity stand) {
                        var tag = stand.saveWithoutMetadata(level.registryAccess());
                        var items = net.minecraft.core.NonNullList.withSize(5, ItemStack.EMPTY);
                        net.minecraft.world.ContainerHelper.loadAllItems(tag, items, level.registryAccess());
                        progress = WorkProductionProgress.brewing(level, order, allocation.remaining(), items, tag);
                    } else { result.add(unavailable(order)); continue; }
                    if (!progress.blocker().isEmpty()) { result.add(unavailable(order)); continue; }
                    describe(level, order, progress.remaining(), supplies, carried, sources, new ArrayList<>(progress.supplies()), progress.burn(), progress.uses(), unknown, water, result);
                }
            }
            long amount = enabled && order.runnable() ? order.mode() == HiredProductionOrder.Mode.CONTINUOUS ? order.output().getCount()
                    : queue.available(order.id(), stock.getOrDefault(order.id(), -1L)) : 0;
            describe(level, order, amount, supplies, carried, sources, new ArrayList<>(), 0, 0, unknown, water, result);
        }
        var combined = new java.util.LinkedHashMap<UUID, Requirement>();
        for (var requirement : result) combined.merge(requirement.id(), requirement, Requirement::merge);
        return List.copyOf(combined.values());
    }
    private static Requirement unavailable(HiredProductionOrder order) {
        return new Requirement(order.id(), "Station contents unavailable", WorkSupplyRequirement.Unit.ITEMS, 0, 0, -1);
    }

    private static void describe(ServerLevel level, HiredProductionOrder order, long amount, List<ItemStack> supplies,
                                 List<ItemStack> carried, List<net.minecraft.core.BlockPos> sources,
                                 List<ItemStack> privateSupplies, long burn, long uses, boolean unknown, boolean water, List<Requirement> result) {
        if (amount <= 0) return;
        var available = new ArrayList<>(privateSupplies);
        available.addAll(carried);
        available.addAll(supplies);
        var plan = WorkProductionDemand.describe(level, order.recipe(), amount, available, water);
        if (!plan.blocker().isEmpty()) { result.add(unavailable(order)); return; }
        int count = 0;
        for (var need : plan.requirements()) {
            if (++count > 15) {
                result.add(new Requirement(order.id(), "Additional input groups", WorkSupplyRequirement.Unit.ITEMS, 0, 0, -1));
                break;
            }
            long remaining = need.quantity() - WorkSupplyEvaluation.progress(need, burn, uses, water);
            remaining = WorkSupplyEvaluation.consume(privateSupplies, need, remaining);
            remaining = WorkSupplyEvaluation.consume(carried, need, remaining);
            long committed = need.quantity() - remaining;
            remaining = WorkSupplyEvaluation.consume(supplies, need, remaining);
            var demand = new WorkSupplyDemand(WorkSupplyDemand.identity(level.registryAccess(), order.id(), WorkSupplyDemand.Kind.PRODUCTION, need),
                    order.id(), WorkSupplyDemand.Kind.PRODUCTION, need, need.quantity(), 0, 0, 5,
                    need.quantity() - remaining - committed, committed, 0, 0, !unknown || remaining == 0, sources, order.storage(),
                    WorkSupplyDemand.Blocker.of(unknown && remaining > 0 ? "storage_unavailable" : remaining > 0 ? "missing_materials" : ""));
            result.add(Requirement.from(demand));
        }
    }
}
