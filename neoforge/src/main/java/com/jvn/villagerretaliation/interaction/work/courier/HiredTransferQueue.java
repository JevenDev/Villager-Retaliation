package com.jvn.villagerretaliation.interaction.work.courier;

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

public final class HiredTransferQueue {
    public static final int MAX_ORDERS = 32;
    public static final int MAX_AMOUNT = 4096;
    private final UUID owner;
    private final Scope scope;
    private final Consumer<HiredTransferQueue> changed;
    private final List<Order> orders = new ArrayList<>();
    private final LinkedHashMap<UUID, Allocation> allocations = new LinkedHashMap<>();
    private java.util.function.Predicate<UUID> executionAllowed = ignored -> true;
    private java.util.function.ToIntFunction<UUID> executionPriority = ignored -> 0;
    private java.util.function.ToLongFunction<UUID> executionCeiling = ignored -> Long.MAX_VALUE;
    private long revision;

    public HiredTransferQueue(UUID owner, Scope scope, Consumer<HiredTransferQueue> changed) {
        this.owner = Objects.requireNonNull(owner);
        this.scope = Objects.requireNonNull(scope);
        this.changed = Objects.requireNonNull(changed);
    }

    public UUID owner() { return this.owner; }
    public Scope scope() { return this.scope; }
    public long revision() { return this.revision; }
    public List<Order> orders() { return List.copyOf(this.orders); }
    public List<Allocation> allocations() { return List.copyOf(this.allocations.values()); }
    public Order order(UUID id) { return this.orders.stream().filter(order -> order.id().equals(id)).findFirst().orElse(null); }
    public Allocation allocation(UUID worker) {
        return this.allocations.values().stream().filter(allocation -> allocation.worker().equals(worker)).findFirst().orElse(null);
    }

    public void executionPolicy(java.util.function.Predicate<UUID> allowed, java.util.function.ToIntFunction<UUID> priority) {
        executionPolicy(allowed, priority, ignored -> Long.MAX_VALUE);
    }

    public void executionPolicy(java.util.function.Predicate<UUID> allowed, java.util.function.ToIntFunction<UUID> priority,
                                java.util.function.ToLongFunction<UUID> ceiling) {
        this.executionCeiling = Objects.requireNonNull(ceiling);
        this.executionAllowed = Objects.requireNonNull(allowed);
        this.executionPriority = Objects.requireNonNull(priority);
    }

    public boolean runnable(UUID id) {
        var order = order(id);
        return order != null && order.runnable() && this.executionAllowed.test(id);
    }

    public List<Order> executionOrders() {
        return this.orders.stream().filter(order -> runnable(order.id()))
                .sorted(java.util.Comparator.comparingInt((Order order) -> this.executionPriority.applyAsInt(order.id())).reversed()).toList();
    }

    public boolean maintainTarget(UUID id, int amount) {
        var order = order(id);
        if (order == null || order.mode() != Mode.MAINTAIN_STOCK || amount < 1 || amount > MAX_AMOUNT
                || order.cancelled() || this.revision == Long.MAX_VALUE) return false;
        if (amount == order.amount()) return true;
        if (amount < order.amount()) releasePlannedOrder(id);
        replace(new Order(order.id(), order.source(), order.destination(), order.sample(), amount, order.picked(),
                order.delivered(), order.paused(), order.cancelled(), order.blocker(), order.mode()));
        configured();
        return true;
    }

    public boolean append(Order order) {
        if (this.revision == Long.MAX_VALUE || order == null || this.orders.size() >= MAX_ORDERS || order(order.id()) != null
                || order.picked() != 0 || order.delivered() != 0 || order.paused() || order.cancelled() || !order.blocker().isEmpty()
                || this.scope == Scope.PERSONAL && (order.source().depot() != null || order.destination().depot() != null)) return false;
        this.orders.add(order);
        configured();
        return true;
    }

    public boolean edit(UUID id, Edit edit, int index) {
        var order = order(id);
        if (order == null || this.revision == Long.MAX_VALUE) return false;
        switch (edit) {
            case MOVE -> {
                if (index < 0 || index >= this.orders.size()) return false;
                this.orders.remove(order);
                this.orders.add(index, order);
            }
            case PAUSE, RESUME -> {
                if (order.cancelled()) return false;
                replace(order.state(order.picked(), order.delivered(), edit == Edit.PAUSE, false, order.blocker()));
                if (edit == Edit.PAUSE) releasePlannedOrder(id);
            }
            case CANCEL -> {
                replace(order.state(order.picked(), order.delivered(), order.paused(), true, ""));
                releasePlannedOrder(id);
            }
            case REMOVE -> {
                if (this.allocations.values().stream().anyMatch(allocation -> allocation.order().equals(id) && allocation.started())) return false;
                releasePlannedOrder(id);
                this.orders.remove(order);
            }
        }
        configured();
        return true;
    }

    public int available(UUID id) { return available(id, -1); }

    public int available(UUID id, long destinationStock) {
        var order = order(id);
        if (!runnable(id)) return 0;
        int reserved = this.allocations.values().stream().filter(allocation -> allocation.order().equals(id) && !allocation.started())
                .mapToInt(Allocation::amount).sum();
        long deficit = order.mode() == Mode.FINITE ? order.amount() - order.picked() - reserved
                : destinationStock < 0 ? 0 : Math.max(0, order.amount() - destinationStock);
        long allowance = Math.max(0, Math.max(0, this.executionCeiling.applyAsLong(id) - order.picked()) - reserved);
        return (int) Math.max(0, Math.min(MAX_AMOUNT, Math.min(deficit, allowance)));
    }

    public Allocation reserve(UUID worker, UUID id, int maximum) { return reserve(worker, id, maximum, -1); }

    public Allocation reserve(UUID worker, UUID id, int maximum, long destinationStock) {
        Objects.requireNonNull(worker);
        int amount = Math.min(Math.min(MAX_AMOUNT, maximum), available(id, destinationStock));
        if (amount < 1 || allocation(worker) != null || this.allocations.size() >= (this.scope == Scope.PERSONAL ? 1 : MAX_ORDERS)) return null;
        var allocation = new Allocation(UUID.randomUUID(), worker, id, amount, false);
        this.allocations.put(allocation.id(), allocation);
        this.changed.accept(this);
        return allocation;
    }

    public boolean resizePlanned(UUID id, int amount) {
        var allocation = this.allocations.get(id);
        if (allocation == null || allocation.started() || amount < 1 || amount > allocation.amount()) return false;
        if (amount != allocation.amount()) {
            this.allocations.put(id, new Allocation(id, allocation.worker(), allocation.order(), amount, false));
            this.changed.accept(this);
        }
        return true;
    }

    public boolean picked(UUID id, int amount) {
        var allocation = this.allocations.get(id);
        if (allocation == null || allocation.started() || amount < 1 || amount > allocation.amount()) return false;
        var order = order(allocation.order());
        if (!runnable(order.id()) || amount > Math.max(0, this.executionCeiling.applyAsLong(order.id()) - order.picked())) return false;
        replace(order.state(order.picked() + amount, order.delivered(), order.paused(), order.cancelled(), ""));
        this.allocations.put(id, new Allocation(id, allocation.worker(), allocation.order(), amount, true));
        this.changed.accept(this);
        return true;
    }

    public boolean delivered(UUID id, int amount) {
        var allocation = this.allocations.get(id);
        if (allocation == null || !allocation.started() || amount < 1 || amount > allocation.amount()) return false;
        var order = order(allocation.order());
        replace(order.state(order.picked(), order.delivered() + amount, order.paused(), order.cancelled(), ""));
        if (allocation.amount() == amount) this.allocations.remove(id);
        else this.allocations.put(id, new Allocation(id, allocation.worker(), allocation.order(), allocation.amount() - amount, true));
        this.changed.accept(this);
        return true;
    }

    public void releasePlanned(UUID worker) {
        var allocation = allocation(worker);
        if (allocation != null && !allocation.started()) {
            this.allocations.remove(allocation.id());
            this.changed.accept(this);
        }
    }

    public void detach(UUID worker) {
        var allocation = allocation(worker);
        if (allocation == null) return;
        if (!allocation.started()) releasePlanned(worker);
        else blocker(allocation.order(), "transfer_worker_changed");
    }

    public void blocker(UUID id, String blocker) {
        var order = order(id);
        if (order != null && !order.blocker().equals(blocker)) {
            replace(order.state(order.picked(), order.delivered(), order.paused(), order.cancelled(), blocker));
            this.changed.accept(this);
        }
    }

    public int pending(ItemStack sample, BlockPos destination) {
        return this.allocations.values().stream().filter(allocation -> {
            var order = order(allocation.order());
            return order.destination().position().equals(destination) && order.matches(sample);
        }).mapToInt(Allocation::amount).sum();
    }

    private void releasePlannedOrder(UUID id) {
        this.allocations.values().removeIf(allocation -> allocation.order().equals(id) && !allocation.started());
    }

    private void replace(Order order) {
        this.orders.set(this.orders.indexOf(order(order.id())), order);
    }

    private void configured() {
        this.revision++;
        this.changed.accept(this);
    }

    public CompoundTag save(HolderLookup.Provider provider) {
        var tag = new CompoundTag();
        tag.putInt("Version", 2);
        tag.putUUID("Owner", this.owner);
        tag.putString("Scope", this.scope.name());
        tag.putLong("Revision", this.revision);
        var orders = new ListTag();
        for (var order : this.orders) {
            var saved = new CompoundTag();
            saved.putUUID("Id", order.id());
            saved.put("Source", order.source().save());
            saved.put("Destination", order.destination().save());
            saved.put("Sample", order.sample().save(provider));
            saved.putInt("Amount", order.amount());
            saved.putString("Mode", order.mode().name());
            saved.putLong("Picked", order.picked());
            saved.putLong("Delivered", order.delivered());
            saved.putBoolean("Paused", order.paused());
            saved.putBoolean("Cancelled", order.cancelled());
            saved.putString("Blocker", order.blocker());
            orders.add(saved);
        }
        tag.put("Orders", orders);
        var allocations = new ListTag();
        this.allocations.values().forEach(allocation -> {
            var saved = new CompoundTag();
            saved.putUUID("Id", allocation.id());
            saved.putUUID("Worker", allocation.worker());
            saved.putUUID("Order", allocation.order());
            saved.putInt("Amount", allocation.amount());
            saved.putBoolean("Started", allocation.started());
            allocations.add(saved);
        });
        tag.put("Allocations", allocations);
        return tag;
    }

    public static HiredTransferQueue load(CompoundTag tag, HolderLookup.Provider provider, Consumer<HiredTransferQueue> changed) {
        if (!tag.contains("Version", Tag.TAG_INT) || (tag.getInt("Version") < 1 || tag.getInt("Version") > 2) || !tag.hasUUID("Owner")
                || !tag.contains("Scope", Tag.TAG_STRING) || !tag.contains("Revision", Tag.TAG_LONG) || tag.getLong("Revision") < 0L) {
            throw new IllegalArgumentException("Invalid transfer queue");
        }
        int version = tag.getInt("Version");
        var queue = new HiredTransferQueue(tag.getUUID("Owner"), Scope.valueOf(tag.getString("Scope")), changed);
        queue.revision = tag.getLong("Revision");
        for (String key : List.of("Orders", "Allocations")) {
            if (!(tag.get(key) instanceof ListTag list) || list.size() > MAX_ORDERS
                    || !list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) throw new IllegalArgumentException("Invalid transfer queue entries");
        }
        for (Tag raw : tag.getList("Orders", Tag.TAG_COMPOUND)) {
            var saved = (CompoundTag) raw;
            if (!saved.hasUUID("Id") || queue.order(saved.getUUID("Id")) != null || !saved.contains("Source", Tag.TAG_COMPOUND)
                    || !saved.contains("Destination", Tag.TAG_COMPOUND) || !saved.contains("Sample", Tag.TAG_COMPOUND)
                    || !saved.contains("Amount", Tag.TAG_INT) || !saved.contains("Picked", version == 1 ? Tag.TAG_INT : Tag.TAG_LONG)
                    || !saved.contains("Delivered", version == 1 ? Tag.TAG_INT : Tag.TAG_LONG) || !saved.contains("Paused", Tag.TAG_BYTE)
                    || !saved.contains("Cancelled", Tag.TAG_BYTE) || !saved.contains("Blocker", Tag.TAG_STRING)
                    || version == 2 && !saved.contains("Mode", Tag.TAG_STRING)) {
                throw new IllegalArgumentException("Invalid saved transfer order");
            }
            var order = new Order(saved.getUUID("Id"), Endpoint.load(saved.getCompound("Source")), Endpoint.load(saved.getCompound("Destination")),
                    ItemStack.parseOptional(provider, saved.getCompound("Sample")), saved.getInt("Amount"), saved.getLong("Picked"),
                    saved.getLong("Delivered"), saved.getBoolean("Paused"), saved.getBoolean("Cancelled"), saved.getString("Blocker"),
                    version == 1 ? Mode.FINITE : Mode.valueOf(saved.getString("Mode")));
            if (queue.scope == Scope.PERSONAL && (order.source().depot() != null || order.destination().depot() != null)) throw new IllegalArgumentException("Transfer endpoint ownership changed");
            queue.orders.add(order);
        }
        for (Tag raw : tag.getList("Allocations", Tag.TAG_COMPOUND)) {
            var saved = (CompoundTag) raw;
            if (!saved.hasUUID("Id") || !saved.hasUUID("Worker") || !saved.hasUUID("Order")
                    || !saved.contains("Amount", Tag.TAG_INT) || !saved.contains("Started", Tag.TAG_BYTE)) {
                throw new IllegalArgumentException("Invalid transfer allocation");
            }
            var allocation = new Allocation(saved.getUUID("Id"), saved.getUUID("Worker"), saved.getUUID("Order"),
                    saved.getInt("Amount"), saved.getBoolean("Started"));
            var order = queue.order(allocation.order());
            if (order == null || queue.allocation(allocation.worker()) != null
                    || !allocation.started() && !order.runnable() || queue.allocations.putIfAbsent(allocation.id(), allocation) != null) {
                throw new IllegalArgumentException("Invalid transfer allocation ownership");
            }
        }
        if (queue.scope == Scope.PERSONAL && queue.allocations.size() > 1) throw new IllegalArgumentException("Personal transfers have one courier");
        for (var order : queue.orders) {
            int pending = queue.allocations.values().stream().filter(allocation -> allocation.order().equals(order.id()) && allocation.started())
                    .mapToInt(Allocation::amount).sum();
            int planned = queue.allocations.values().stream().filter(allocation -> allocation.order().equals(order.id()) && !allocation.started())
                    .mapToInt(Allocation::amount).sum();
            if (order.picked() - order.delivered() != pending || order.mode() == Mode.FINITE && planned + order.picked() > order.amount()
                    || planned > Long.MAX_VALUE - order.picked()) {
                throw new IllegalArgumentException("Transfer quantities do not match their receipts");
            }
        }
        return queue;
    }

    public enum Scope { PERSONAL, WORKSITE }
    public enum Mode { FINITE, MAINTAIN_STOCK }
    public enum Edit { MOVE, PAUSE, RESUME, CANCEL, REMOVE }

    public record Endpoint(BlockPos position, UUID depot) {
        public Endpoint { position = Objects.requireNonNull(position).immutable(); }
        CompoundTag save() {
            var tag = new CompoundTag();
            tag.putLong("Position", this.position.asLong());
            if (this.depot != null) tag.putUUID("Depot", this.depot);
            return tag;
        }
        static Endpoint load(CompoundTag tag) {
            if (!tag.contains("Position", Tag.TAG_LONG) || tag.contains("Depot") && !tag.hasUUID("Depot")) throw new IllegalArgumentException("Invalid transfer endpoint");
            return new Endpoint(BlockPos.of(tag.getLong("Position")), tag.hasUUID("Depot") ? tag.getUUID("Depot") : null);
        }
    }

    public record Order(UUID id, Endpoint source, Endpoint destination, ItemStack sample, int amount, long picked, long delivered,
                        boolean paused, boolean cancelled, String blocker, Mode mode) {
        public Order {
            Objects.requireNonNull(id);
            Objects.requireNonNull(mode);
            Objects.requireNonNull(source);
            Objects.requireNonNull(destination);
            if (source.position().equals(destination.position()) || source.depot() != null && source.depot().equals(destination.depot()) || sample == null || sample.isEmpty() || amount < 1 || amount > MAX_AMOUNT
                    || picked < 0 || mode == Mode.FINITE && picked > amount || delivered < 0 || delivered > picked || blocker == null || blocker.length() > 128) {
                throw new IllegalArgumentException("Invalid transfer order");
            }
            sample = com.jvn.villagerretaliation.interaction.work.production.HiredProductionStock.product(sample).copyWithCount(1);
        }
        public Order(UUID id, Endpoint source, Endpoint destination, ItemStack sample, int amount, long picked, long delivered,
                     boolean paused, boolean cancelled, String blocker) {
            this(id, source, destination, sample, amount, picked, delivered, paused, cancelled, blocker, Mode.FINITE);
        }
        public static Order create(Endpoint source, Endpoint destination, ItemStack sample, int amount) {
            return create(source, destination, sample, amount, Mode.FINITE);
        }
        public static Order create(Endpoint source, Endpoint destination, ItemStack sample, int amount, Mode mode) {
            return new Order(UUID.randomUUID(), source, destination, sample, amount, 0, 0, false, false, "", mode);
        }
        @Override
        public ItemStack sample() { return this.sample.copy(); }
        public boolean matches(ItemStack stack) { return com.jvn.villagerretaliation.interaction.work.production.HiredProductionStock.matches(this.sample, stack); }
        public boolean runnable() { return !this.paused && !this.cancelled && (this.mode == Mode.MAINTAIN_STOCK || this.picked < this.amount); }
        public boolean complete() { return this.mode == Mode.FINITE && this.delivered >= this.amount; }
        public int pending() { return Math.toIntExact(this.picked - this.delivered); }
        Order state(long picked, long delivered, boolean paused, boolean cancelled, String blocker) {
            return new Order(this.id, this.source, this.destination, this.sample, this.amount, picked, delivered, paused, cancelled, blocker, this.mode);
        }
    }

    public record Allocation(UUID id, UUID worker, UUID order, int amount, boolean started) {
        public Allocation {
            Objects.requireNonNull(id);
            Objects.requireNonNull(worker);
            Objects.requireNonNull(order);
            if (amount < 1 || amount > MAX_AMOUNT) throw new IllegalArgumentException("Invalid transfer reservation");
        }
    }
}
