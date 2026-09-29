package com.jvn.villagerretaliation.interaction.work.production;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

public final class HiredProductionQueue {
    public static final int MAX_ORDERS = 32;
    public static final int MAX_ALLOCATIONS = 32;
    private final UUID owner;
    private final Scope scope;
    private final HiredVillagerRole role;
    private final Consumer<HiredProductionQueue> changed;
    private final List<HiredProductionOrder> orders = new ArrayList<>();
    private final LinkedHashMap<UUID, Allocation> allocations = new LinkedHashMap<>();
    private long revision;
    private long configurationRevision;

    public HiredProductionQueue(UUID owner, Scope scope, HiredVillagerRole role, Consumer<HiredProductionQueue> changed) {
        this.owner = Objects.requireNonNull(owner);
        this.scope = Objects.requireNonNull(scope);
        this.role = Objects.requireNonNull(role);
        this.changed = Objects.requireNonNull(changed);
        if (role != HiredVillagerRole.CRAFTSMAN && role != HiredVillagerRole.COOK
                && role != HiredVillagerRole.SMELTER && role != HiredVillagerRole.BREWING) {
            throw new IllegalArgumentException("Role cannot own production orders");
        }
    }

    public UUID owner() { return this.owner; }
    public Scope scope() { return this.scope; }
    public HiredVillagerRole role() { return this.role; }
    public long revision() { return this.revision; }
    public long configurationRevision() { return this.configurationRevision; }
    public List<HiredProductionOrder> orders() { return List.copyOf(this.orders); }
    public List<Allocation> allocations() { return List.copyOf(this.allocations.values()); }

    public HiredProductionOrder order(UUID id) {
        return this.orders.stream().filter(order -> order.id().equals(id)).findFirst().orElse(null);
    }

    public Allocation allocation(UUID worker) {
        return this.allocations.values().stream().filter(allocation -> worker.equals(allocation.worker())).findFirst().orElse(null);
    }

    public boolean append(HiredProductionOrder order) {
        if (order == null || this.orders.size() >= MAX_ORDERS || order(order.id()) != null
                || order.produced() != 0L || order.cancelled() || !order.blocker().isEmpty()) return false;
        if ((this.role == HiredVillagerRole.BREWING) != (order.recipe().recipe() == null)) return false;
        this.orders.add(order);
        configured();
        return true;
    }

    public boolean reorder(UUID id, int index) {
        var order = order(id);
        if (order == null || index < 0 || index >= this.orders.size()) return false;
        if (this.orders.indexOf(order) == index) return true;
        this.orders.remove(order);
        this.orders.add(index, order);
        configured();
        return true;
    }

    public boolean pause(UUID id, boolean paused) {
        var order = order(id);
        if (order == null || order.cancelled()) return false;
        if (order.paused() == paused) return true;
        replace(order.state(order.produced(), paused, false, order.blocker()));
        if (paused) releaseUnstarted(id);
        configured();
        return true;
    }

    public boolean cancel(UUID id) {
        var order = order(id);
        if (order == null) return false;
        if (order.cancelled()) return true;
        replace(order.state(order.produced(), order.paused(), true, ""));
        releaseUnstarted(id);
        configured();
        return true;
    }

    public boolean remove(UUID id) {
        var order = order(id);
        if (order == null || this.allocations.values().stream().anyMatch(allocation -> allocation.order().equals(id) && allocation.started())) return false;
        releaseUnstarted(id);
        this.orders.remove(order);
        configured();
        return true;
    }

    public void blocker(UUID id, String blocker) {
        var order = order(id);
        if (order != null && !order.blocker().equals(blocker)) {
            replace(order.state(order.produced(), order.paused(), order.cancelled(), blocker));
            changed();
        }
    }

    public int pending(UUID order) {
        return this.allocations.values().stream().filter(allocation -> allocation.order().equals(order)).mapToInt(Allocation::remaining).sum();
    }

    public int pending(ItemStack output, BlockPos storage) {
        return this.allocations.values().stream().filter(allocation -> {
            var order = order(allocation.order());
            return order.matches(output) && Objects.equals(order.storage(), storage);
        }).mapToInt(Allocation::remaining).sum();
    }

    public long available(UUID id, long stock) {
        if (stock < 0L) throw new IllegalArgumentException("Negative production stock");
        var order = order(id);
        if (order == null || !order.runnable()) return 0L;
        return switch (order.mode()) {
            case CONTINUOUS -> HiredProductionOrder.MAX_AMOUNT;
            case BATCH -> Math.max(0L, order.amount() - order.produced() - pending(id));
            case MAINTAIN_STOCK -> Math.max(0L, order.amount() - Math.min(stock, Integer.MAX_VALUE)
                    - pending(order.output(), order.storage()));
        };
    }

    public Allocation reserve(UUID worker, UUID orderId, int batchSize, int maxBatches, long stock, BlockPos station) {
        Objects.requireNonNull(worker);
        var order = order(orderId);
        if (order == null || this.allocations.size() >= (this.scope == Scope.PERSONAL ? 1 : MAX_ALLOCATIONS)
                || allocation(worker) != null || batchSize < 1 || batchSize > HiredProductionOrder.MAX_BATCH
                || order.recipe().recipe() != null && batchSize != order.output().getCount()
                || order.recipe().recipe() == null && batchSize > 3 || maxBatches < 1 || maxBatches > HiredProductionOrder.MAX_AMOUNT
                || station != null && this.allocations.values().stream().anyMatch(allocation -> station.equals(allocation.station()))) return null;
        long available = available(orderId, stock);
        if (available <= 0L) return null;
        long batches = Math.min(maxBatches, (available + batchSize - 1L) / batchSize);
        int amount = Math.toIntExact(batches * batchSize);
        var allocation = new Allocation(UUID.randomUUID(), worker, orderId, amount, false, station, null);
        this.allocations.put(allocation.id(), allocation);
        blockerWithoutNotification(orderId, "");
        changed();
        return allocation;
    }

    public boolean bindMachine(UUID id, UUID machine) {
        var allocation = this.allocations.get(id);
        if (allocation == null || allocation.station() == null || machine == null) return false;
        if (allocation.machine() != null) return allocation.machine().equals(machine);
        this.allocations.put(id, new Allocation(id, allocation.worker(), allocation.order(), allocation.remaining(),
                allocation.started(), allocation.station(), machine));
        changed();
        return true;
    }

    public boolean start(UUID id) {
        var allocation = this.allocations.get(id);
        if (allocation == null) return false;
        if (allocation.started()) return true;
        var order = order(allocation.order());
        if (!order.runnable()) return false;
        this.allocations.put(id, new Allocation(id, allocation.worker(), allocation.order(), allocation.remaining(), true, allocation.station(), allocation.machine()));
        changed();
        return true;
    }

    public boolean complete(UUID id, ItemStack produced) {
        var allocation = this.allocations.get(id);
        if (allocation == null || !allocation.started() || produced.isEmpty() || produced.getCount() > allocation.remaining()) return false;
        var order = order(allocation.order());
        if (!order.matches(produced) || order.produced() > Long.MAX_VALUE - produced.getCount()) return false;
        replace(order.state(order.produced() + produced.getCount(), order.paused(), order.cancelled(), ""));
        int remaining = allocation.remaining() - produced.getCount();
        if (remaining == 0) this.allocations.remove(id);
        else this.allocations.put(id, new Allocation(id, allocation.worker(), allocation.order(), remaining, true, allocation.station(), allocation.machine()));
        changed();
        return true;
    }

    public boolean release(UUID id) {
        var allocation = this.allocations.get(id);
        if (allocation == null || allocation.started()) return false;
        this.allocations.remove(id);
        changed();
        return true;
    }

    public void detach(UUID worker) {
        var allocation = allocation(worker);
        if (allocation == null) return;
        if (allocation.started()) this.allocations.put(allocation.id(),
                new Allocation(allocation.id(), null, allocation.order(), allocation.remaining(), true, allocation.station(), allocation.machine()));
        else this.allocations.remove(allocation.id());
        changed();
    }

    public boolean resume(UUID id, UUID worker) {
        Objects.requireNonNull(worker);
        var allocation = this.allocations.get(id);
        if (allocation == null || allocation.worker() != null || !allocation.started() || allocation(worker) != null) return false;
        this.allocations.put(id, new Allocation(id, worker, allocation.order(), allocation.remaining(), true, allocation.station(), allocation.machine()));
        changed();
        return true;
    }

    public boolean lost(UUID id) {
        if (this.allocations.remove(id) == null) return false;
        changed();
        return true;
    }

    private void releaseUnstarted(UUID order) {
        this.allocations.values().removeIf(allocation -> allocation.order().equals(order) && !allocation.started());
    }

    private void blockerWithoutNotification(UUID id, String blocker) {
        var order = order(id);
        replace(order.state(order.produced(), order.paused(), order.cancelled(), blocker));
    }

    private void replace(HiredProductionOrder updated) {
        for (int index = 0; index < this.orders.size(); index++) {
            if (this.orders.get(index).id().equals(updated.id())) {
                this.orders.set(index, updated);
                return;
            }
        }
        throw new IllegalArgumentException("Unknown production order");
    }

    private void configured() {
        this.configurationRevision = Math.addExact(this.configurationRevision, 1L);
        changed();
    }

    private void changed() {
        this.revision = Math.addExact(this.revision, 1L);
        this.changed.accept(this);
    }

    public CompoundTag save(HolderLookup.Provider provider) {
        var tag = new CompoundTag();
        tag.putInt("Version", 2);
        tag.putUUID("Owner", this.owner);
        tag.putString("Scope", this.scope.name());
        tag.putString("Role", this.role.name());
        tag.putLong("Revision", this.revision);
        tag.putLong("ConfigurationRevision", this.configurationRevision);
        var orders = new ListTag();
        this.orders.forEach(order -> orders.add(order.save(provider)));
        tag.put("Orders", orders);
        var allocations = new ListTag();
        this.allocations.values().forEach(allocation -> {
            var entry = new CompoundTag();
            entry.putUUID("Id", allocation.id());
            if (allocation.worker() != null) entry.putUUID("Worker", allocation.worker());
            entry.putUUID("Order", allocation.order());
            entry.putInt("Remaining", allocation.remaining());
            entry.putBoolean("Started", allocation.started());
            if (allocation.station() != null) entry.putLong("Station", allocation.station().asLong());
            if (allocation.machine() != null) entry.putUUID("Machine", allocation.machine());
            allocations.add(entry);
        });
        tag.put("Allocations", allocations);
        return tag;
    }

    public static HiredProductionQueue load(CompoundTag tag, HolderLookup.Provider provider, Consumer<HiredProductionQueue> changed) {
        if (!tag.contains("Version", Tag.TAG_INT) || (tag.getInt("Version") < 1 || tag.getInt("Version") > 2) || !tag.hasUUID("Owner")
                || !tag.contains("Scope", Tag.TAG_STRING) || !tag.contains("Role", Tag.TAG_STRING)
                || !tag.contains("Revision", Tag.TAG_LONG) || tag.getLong("Revision") < 0L
                || !tag.contains("ConfigurationRevision", Tag.TAG_LONG) || tag.getLong("ConfigurationRevision") < 0L
                || tag.getLong("ConfigurationRevision") > tag.getLong("Revision")
                || !(tag.get("Orders") instanceof ListTag orders) || !orders.isEmpty() && orders.getElementType() != Tag.TAG_COMPOUND
                || orders.size() > MAX_ORDERS || !(tag.get("Allocations") instanceof ListTag allocations)
                || !allocations.isEmpty() && allocations.getElementType() != Tag.TAG_COMPOUND || allocations.size() > MAX_ALLOCATIONS) {
            throw new IllegalArgumentException("Invalid saved production queue");
        }
        var queue = new HiredProductionQueue(tag.getUUID("Owner"), Scope.valueOf(tag.getString("Scope")),
                HiredVillagerRole.valueOf(tag.getString("Role")), changed);
        queue.revision = tag.getLong("Revision");
        queue.configurationRevision = tag.getLong("ConfigurationRevision");
        for (Tag raw : orders) {
            var order = HiredProductionOrder.load((CompoundTag) raw, provider);
            if (queue.order(order.id()) != null || (queue.role == HiredVillagerRole.BREWING) != (order.recipe().recipe() == null)) {
                throw new IllegalArgumentException("Duplicate or incompatible production order");
            }
            queue.orders.add(order);
        }
        if (queue.scope == Scope.PERSONAL && allocations.size() > 1) throw new IllegalArgumentException("Personal production has multiple workers");
        for (Tag raw : allocations) {
            var entry = (CompoundTag) raw;
            if (!entry.hasUUID("Id") || entry.contains("Worker") && !entry.hasUUID("Worker") || !entry.hasUUID("Order")
                    || !entry.contains("Remaining", Tag.TAG_INT) || !entry.contains("Started", Tag.TAG_BYTE)
                    || entry.contains("Station") && !entry.contains("Station", Tag.TAG_LONG)
                    || entry.contains("Machine") && !entry.hasUUID("Machine")) {
                throw new IllegalArgumentException("Invalid saved production allocation");
            }
            var allocation = new Allocation(entry.getUUID("Id"), entry.hasUUID("Worker") ? entry.getUUID("Worker") : null,
                    entry.getUUID("Order"), entry.getInt("Remaining"), entry.getBoolean("Started"),
                    entry.contains("Station") ? BlockPos.of(entry.getLong("Station")) : null,
                    entry.hasUUID("Machine") ? entry.getUUID("Machine") : null);
            var order = queue.order(allocation.order());
            if (order == null || allocation.worker() != null && queue.allocation(allocation.worker()) != null
                    || !allocation.started() && !order.runnable() || allocation.station() != null
                        && queue.allocations.values().stream().anyMatch(other -> allocation.station().equals(other.station()))
                    || queue.allocations.putIfAbsent(allocation.id(), allocation) != null) {
                throw new IllegalArgumentException("Invalid or duplicate production allocation");
            }
        }
        for (var order : queue.orders) {
            if (order.mode() == HiredProductionOrder.Mode.BATCH
                    && (order.produced() > (long) order.amount() + HiredProductionOrder.MAX_BATCH - 1
                        || queue.pending(order.id()) > (long) order.amount() + HiredProductionOrder.MAX_BATCH - 1 - order.produced())) {
                throw new IllegalArgumentException("Production allocations exceed the shared quantity");
            }
        }
        return queue;
    }

    public enum Scope {
        PERSONAL,
        WORKSITE
    }

    public record Allocation(UUID id, UUID worker, UUID order, int remaining, boolean started, BlockPos station, UUID machine) {
        public Allocation {
            Objects.requireNonNull(id);
            Objects.requireNonNull(order);
            station = station == null ? null : station.immutable();
            if (remaining < 1 || remaining >= HiredProductionOrder.MAX_AMOUNT + HiredProductionOrder.MAX_BATCH
                    || worker == null && !started || machine != null && station == null) throw new IllegalArgumentException("Invalid production allocation");
        }
    }
}
