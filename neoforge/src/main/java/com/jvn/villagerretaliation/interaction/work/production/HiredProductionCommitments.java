package com.jvn.villagerretaliation.interaction.work.production;

import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue;
import com.jvn.villagerretaliation.interaction.work.courier.HiredTransferService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.settlement.SettlementWorkforceService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

public final class HiredProductionCommitments extends SavedData {
    private static final int MAX_WORKERS = 4096;
    private static final int MAX_DELIVERIES = 128;
    private final Map<UUID, Snapshot> workers = new LinkedHashMap<>();
    private CompoundTag rejected;

    public static HiredProductionCommitments get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(HiredProductionCommitments::new,
                HiredProductionCommitments::load, DataFixTypes.LEVEL), "villagerretaliation_production_commitments");
    }

    public boolean available(UUID worker) {
        return this.rejected == null && (this.workers.size() < MAX_WORKERS || this.workers.containsKey(worker));
    }

    public static void observe(Villager villager) {
        if (villager.level() instanceof ServerLevel) observe(villager, HiredJobInventory.getJobInventory(villager));
    }

    public static void observe(Villager villager, HiredJobInventory inventory) {
        if (!(villager.level() instanceof ServerLevel level)) return;
        var data = get(level);
        if (data.rejected != null) return;
        var contract = HiredVillagerContractService.currentContractId(villager).orElse(null);
        var removal = villager.getRemovalReason();
        if (contract == null || SettlementWorkforceService.duty(villager).isPresent()
                || removal == Entity.RemovalReason.KILLED || removal == Entity.RemovalReason.DISCARDED
                || removal == Entity.RemovalReason.CHANGED_DIMENSION) {
            data.workerRemoved(villager.getUUID());
            return;
        }
        var state = HiredWorkStateStore.state(villager);
        var incoming = new ArrayList<Incoming>();
        boolean producing = false;
        try {
            if (state.contains(HiredProductionService.STATE_TAG)) {
                if (!state.contains(HiredProductionService.STATE_TAG, Tag.TAG_COMPOUND)) return;
                var queue = HiredProductionQueue.load(state.getCompound(HiredProductionService.STATE_TAG), level.registryAccess(), ignored -> {});
                if (queue.scope() == HiredProductionQueue.Scope.PERSONAL && contract.equals(queue.owner())) {
                    producing = true;
                    for (var allocation : queue.allocations()) {
                        var order = queue.order(allocation.order());
                        add(incoming, order.storage(), order.output(), allocation.remaining());
                    }
                }
            }
            var destination = HiredProductionStock.destination(state);
            if (producing && destination != null) for (int slot : inventory.outputSlots()) {
                var stack = inventory.getItem(slot);
                add(incoming, destination, stack, stack.getCount());
            }
            if (state.contains(HiredTransferService.STATE_TAG)) {
                if (!state.contains(HiredTransferService.STATE_TAG, Tag.TAG_COMPOUND)) return;
                var queue = HiredTransferQueue.load(state.getCompound(HiredTransferService.STATE_TAG), level.registryAccess(), ignored -> {});
                if (queue.scope() == HiredTransferQueue.Scope.PERSONAL && contract.equals(queue.owner())) {
                    for (var allocation : queue.allocations()) {
                        var order = queue.order(allocation.order());
                        add(incoming, order.destination().position(), order.sample(), allocation.amount());
                    }
                }
            }
        } catch (IllegalArgumentException invalid) {
            return;
        }
        if (incoming.isEmpty()) {
            data.workerRemoved(villager.getUUID());
        } else if (data.available(villager.getUUID())) {
            var snapshot = new Snapshot(contract, List.copyOf(incoming));
            if (!snapshot.same(data.workers.get(villager.getUUID()))) {
                data.workers.put(villager.getUUID(), snapshot);
                data.setDirty();
            }
        }
    }

    public void workerRemoved(UUID worker) {
        if (this.rejected == null && this.workers.remove(worker) != null) setDirty();
    }

    public void refresh(ServerLevel level, BlockPos destination) {
        for (var id : this.workers.entrySet().stream().filter(entry -> entry.getValue().incoming().stream()
                .anyMatch(incoming -> incoming.destination().equals(destination))).map(Map.Entry::getKey).toList()) {
            if (level.getEntity(id) instanceof Villager villager) observe(villager);
        }
    }

    public long count(BlockPos destination, ItemStack sample, UUID excludedWorker) {
        if (this.rejected != null || this.workers.size() >= MAX_WORKERS) return -1L;
        long count = 0L;
        for (var entry : this.workers.entrySet()) {
            if (entry.getKey().equals(excludedWorker)) continue;
            for (var incoming : entry.getValue().incoming()) {
                if (incoming.matches(destination, sample)) count += incoming.amount();
            }
        }
        return count;
    }

    public Set<UUID> contributors(BlockPos destination, ItemStack sample) {
        return this.workers.entrySet().stream().filter(entry -> entry.getValue().incoming().stream()
                .anyMatch(incoming -> incoming.matches(destination, sample))).map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());
    }

    private static void add(List<Incoming> incoming, BlockPos destination, ItemStack sample, int amount) {
        if (destination == null || sample.isEmpty() || amount <= 0) return;
        var product = HiredProductionStock.product(sample).copyWithCount(1);
        for (int index = 0; index < incoming.size(); index++) {
            var previous = incoming.get(index);
            if (previous.matches(destination, product)) {
                incoming.set(index, new Incoming(destination, product, Math.addExact(previous.amount(), amount)));
                return;
            }
        }
        if (incoming.size() >= MAX_DELIVERIES) throw new IllegalArgumentException("Too many production deliveries");
        incoming.add(new Incoming(destination, product, amount));
    }

    public static HiredProductionCommitments load(CompoundTag tag, HolderLookup.Provider provider) {
        var data = new HiredProductionCommitments();
        try {
            if (tag.getInt("Version") != 1 || !(tag.get("Workers") instanceof ListTag workers)
                    || workers.size() > MAX_WORKERS || !workers.isEmpty() && workers.getElementType() != Tag.TAG_COMPOUND) {
                throw new IllegalArgumentException("Invalid production commitments");
            }
            for (var raw : workers) {
                var worker = (CompoundTag) raw;
                if (!worker.hasUUID("Worker") || !worker.hasUUID("Contract") || !(worker.get("Incoming") instanceof ListTag incoming)
                        || incoming.isEmpty() || incoming.size() > MAX_DELIVERIES || incoming.getElementType() != Tag.TAG_COMPOUND) {
                    throw new IllegalArgumentException("Invalid production commitment owner");
                }
                var deliveries = new ArrayList<Incoming>();
                for (var rawDelivery : incoming) {
                    var delivery = (CompoundTag) rawDelivery;
                    if (!delivery.contains("Position", Tag.TAG_LONG) || !delivery.contains("Amount", Tag.TAG_INT)
                            || !delivery.contains("Sample", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Invalid production delivery");
                    var sample = ItemStack.parseOptional(provider, delivery.getCompound("Sample"));
                    if (sample.isEmpty() || sample.getCount() != 1) throw new IllegalArgumentException("Invalid production delivery item");
                    var value = new Incoming(BlockPos.of(delivery.getLong("Position")), sample, delivery.getInt("Amount"));
                    if (deliveries.stream().anyMatch(existing -> existing.matches(value.destination(), value.sample()))) {
                        throw new IllegalArgumentException("Duplicate production delivery");
                    }
                    deliveries.add(value);
                }
                if (data.workers.putIfAbsent(worker.getUUID("Worker"), new Snapshot(worker.getUUID("Contract"), List.copyOf(deliveries))) != null) {
                    throw new IllegalArgumentException("Duplicate production commitment owner");
                }
            }
        } catch (IllegalArgumentException invalid) {
            data.workers.clear();
            data.rejected = tag.copy();
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        if (this.rejected != null) return this.rejected.copy();
        tag.putInt("Version", 1);
        var workers = new ListTag();
        this.workers.forEach((id, snapshot) -> {
            var worker = new CompoundTag();
            worker.putUUID("Worker", id);
            worker.putUUID("Contract", snapshot.contract());
            var incoming = new ListTag();
            for (var value : snapshot.incoming()) {
                var delivery = new CompoundTag();
                delivery.putLong("Position", value.destination().asLong());
                delivery.putInt("Amount", value.amount());
                delivery.put("Sample", value.sample().save(provider));
                incoming.add(delivery);
            }
            worker.put("Incoming", incoming);
            workers.add(worker);
        });
        tag.put("Workers", workers);
        return tag;
    }

    private record Incoming(BlockPos destination, ItemStack sample, int amount) {
        private Incoming {
            if (amount < 1 || amount > 1_048_576) throw new IllegalArgumentException("Invalid production delivery quantity");
        }

        boolean matches(BlockPos position, ItemStack item) {
            return this.destination.equals(position) && HiredProductionStock.matches(this.sample, item);
        }
    }

    private record Snapshot(UUID contract, List<Incoming> incoming) {
        boolean same(Snapshot other) {
            if (other == null || !this.contract.equals(other.contract) || this.incoming.size() != other.incoming.size()) return false;
            for (int index = 0; index < this.incoming.size(); index++) {
                var first = this.incoming.get(index);
                var second = other.incoming.get(index);
                if (first.amount() != second.amount() || !first.matches(second.destination(), second.sample())) return false;
            }
            return true;
        }
    }
}
