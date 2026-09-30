package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionRecipe;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

public final class SettlementSupplyPolicy {
    public static final int MAX_POLICIES = 64;
    public static final int MAX_STEPS = 16;
    public static final int MAX_CONNECTIONS = 64;
    public static final int MAX_BINDINGS = 48;
    private static final int MAX_HISTORY = 32;
    private final UUID id;
    private final Runnable changed;
    private final List<Binding> bindings = new ArrayList<>();
    private final List<Event> history = new ArrayList<>();
    private Configuration configuration;
    private UUID authority;
    private long revision;
    private State state = State.PAUSED;
    private String blocker = "";

    public SettlementSupplyPolicy(UUID id, Configuration configuration, UUID authority, Runnable changed) {
        this.id = Objects.requireNonNull(id);
        this.configuration = Objects.requireNonNull(configuration);
        this.authority = Objects.requireNonNull(authority);
        this.changed = Objects.requireNonNull(changed);
        if (configuration.steps().stream().anyMatch(step -> step.id().equals(id)))
            throw new IllegalArgumentException("Policy and step identities must differ");
    }

    public UUID id() { return this.id; }
    public Configuration configuration() { return this.configuration; }
    public UUID authority() { return this.authority; }
    public long revision() { return this.revision; }
    public State state() { return this.state; }
    public String blocker() { return this.blocker; }
    public List<Binding> bindings() { return List.copyOf(this.bindings); }
    public List<Event> history() { return List.copyOf(this.history); }

    void configure(Configuration configuration, UUID authority, long now) {
        if (configuration.steps().stream().anyMatch(step -> step.id().equals(this.id)))
            throw new IllegalArgumentException("Policy and step identities must differ");
        this.configuration = configuration;
        this.authority = Objects.requireNonNull(authority);
        this.revision = Math.addExact(this.revision, 1);
        event(now, EventKind.EDITED, "");
    }

    void state(State state, UUID authority, String blocker, long now) {
        Objects.requireNonNull(state);
        Objects.requireNonNull(authority);
        if (blocker == null || blocker.length() > 128) throw new IllegalArgumentException("Invalid policy blocker");
        if (this.state == state && this.authority.equals(authority) && this.blocker.equals(blocker)) return;
        this.state = state;
        this.authority = authority;
        this.blocker = blocker;
        this.revision = Math.addExact(this.revision, 1);
        event(now, switch (state) {
            case ACTIVE -> EventKind.ACTIVATED;
            case PAUSED, REMOVING -> EventKind.PAUSED;
            case SUSPENDED -> EventKind.SUSPENDED;
            case COMPLETE -> EventKind.COMPLETED;
        }, blocker);
    }

    void blocker(String blocker) {
        if (blocker == null || blocker.length() > 128) throw new IllegalArgumentException("Invalid policy blocker");
        if (!this.blocker.equals(blocker)) {
            this.blocker = blocker;
            this.changed.run();
        }
    }

    boolean bind(Binding binding) {
        if (this.bindings.size() >= MAX_BINDINGS || this.bindings.stream().anyMatch(existing -> existing.id().equals(binding.id())
                || binding.order() != null && binding.order().equals(existing.order()))) return false;
        this.bindings.add(binding);
        this.changed.run();
        return true;
    }

    void binding(Binding binding) {
        for (int index = 0; index < this.bindings.size(); index++) {
            if (this.bindings.get(index).id().equals(binding.id())) {
                if (!this.bindings.get(index).equals(binding)) {
                    this.bindings.set(index, binding);
                    this.changed.run();
                }
                return;
            }
        }
        throw new IllegalArgumentException("Unknown managed execution binding");
    }

    void removeBinding(UUID binding) {
        if (this.bindings.removeIf(value -> value.id().equals(binding))) this.changed.run();
    }

    void event(long now, EventKind kind, String detail) {
        if (this.history.size() == MAX_HISTORY) this.history.removeFirst();
        this.history.add(new Event(now, kind, detail));
        this.changed.run();
    }

    public CompoundTag save(HolderLookup.Provider provider) {
        var tag = new CompoundTag();
        tag.putInt("Version", 1);
        tag.putUUID("Id", this.id);
        tag.putUUID("Authority", this.authority);
        tag.putLong("Revision", this.revision);
        tag.putString("State", this.state.name());
        tag.putString("Blocker", this.blocker);
        tag.put("Configuration", this.configuration.save(provider));
        var bindings = new ListTag();
        this.bindings.forEach(binding -> bindings.add(binding.save(provider)));
        tag.put("Bindings", bindings);
        var history = new ListTag();
        this.history.forEach(event -> {
            var entry = new CompoundTag();
            entry.putLong("At", event.at());
            entry.putString("Kind", event.kind().name());
            entry.putString("Detail", event.detail());
            history.add(entry);
        });
        tag.put("History", history);
        return tag;
    }

    public static SettlementSupplyPolicy load(CompoundTag tag, HolderLookup.Provider provider, Runnable changed) {
        if (!tag.contains("Version", Tag.TAG_INT) || tag.getInt("Version") != 1 || !tag.hasUUID("Id") || !tag.hasUUID("Authority")
                || !tag.contains("Revision", Tag.TAG_LONG) || tag.getLong("Revision") < 0 || !tag.contains("State", Tag.TAG_STRING)
                || !tag.contains("Blocker", Tag.TAG_STRING) || tag.getString("Blocker").length() > 128
                || !tag.contains("Configuration", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Invalid saved supply policy");
        var policy = new SettlementSupplyPolicy(tag.getUUID("Id"), Configuration.load(tag.getCompound("Configuration"), provider),
                tag.getUUID("Authority"), changed);
        policy.revision = tag.getLong("Revision");
        policy.state = State.valueOf(tag.getString("State"));
        policy.blocker = tag.getString("Blocker");
        var identities = new HashSet<UUID>();
        var orders = new HashSet<UUID>();
        for (var raw : list(tag, "Bindings", MAX_BINDINGS)) {
            var binding = Binding.load((CompoundTag) raw, provider);
            if (!identities.add(binding.id()) || binding.order() != null && !orders.add(binding.order()))
                throw new IllegalArgumentException("Duplicate managed execution binding");
            policy.bindings.add(binding);
        }
        long previous = -1;
        for (var raw : list(tag, "History", MAX_HISTORY)) {
            var entry = (CompoundTag) raw;
            if (!entry.contains("At", Tag.TAG_LONG) || !entry.contains("Kind", Tag.TAG_STRING) || !entry.contains("Detail", Tag.TAG_STRING))
                throw new IllegalArgumentException("Invalid saved policy event");
            var event = new Event(entry.getLong("At"), EventKind.valueOf(entry.getString("Kind")), entry.getString("Detail"));
            if (event.at() < previous) throw new IllegalArgumentException("Policy events are out of order");
            previous = event.at();
            policy.history.add(event);
        }
        return policy;
    }

    private static ListTag list(CompoundTag tag, String key, int maximum) {
        if (!(tag.get(key) instanceof ListTag list) || list.size() > maximum || !list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)
            throw new IllegalArgumentException("Invalid policy " + key);
        return list;
    }

    public enum State { PAUSED, ACTIVE, SUSPENDED, COMPLETE, REMOVING }
    public enum TargetKind { STOCK, CONSUMER, FOOD, EQUIPMENT }
    public enum StepKind { GATHER, PRODUCTION, DELIVERY }
    public enum BindingState { ACTIVE, PARKED, SUSPENDED, DRAINING }
    public enum EventKind { EDITED, ACTIVATED, PAUSED, SUSPENDED, COMPLETED }

    public record Configuration(String name, Target target, int priority, List<Step> steps, List<Connection> connections) {
        public Configuration {
            Objects.requireNonNull(target);
            steps = List.copyOf(steps);
            connections = List.copyOf(connections);
            if (name == null || name.isBlank() || name.length() > 64 || priority < 0 || priority > 9
                    || steps.size() > MAX_STEPS || connections.size() > MAX_CONNECTIONS
                    || steps.stream().map(Step::id).distinct().count() != steps.size()
                    || connections.stream().distinct().count() != connections.size())
                throw new IllegalArgumentException("Invalid supply policy configuration");
        }

        public CompoundTag save(HolderLookup.Provider provider) {
            var tag = new CompoundTag();
            tag.putInt("Version", 1);
            tag.putString("Name", name);
            tag.putInt("Priority", priority);
            tag.put("Target", target.save(provider));
            var steps = new ListTag();
            this.steps.forEach(step -> steps.add(step.save(provider)));
            tag.put("Steps", steps);
            var connections = new ListTag();
            this.connections.forEach(connection -> {
                var entry = new CompoundTag();
                entry.putUUID("Producer", connection.producer());
                if (connection.consumer() != null) entry.putUUID("Consumer", connection.consumer());
                entry.putUUID("Requirement", connection.requirement());
                connections.add(entry);
            });
            tag.put("Connections", connections);
            return tag;
        }

        public static Configuration load(CompoundTag tag, HolderLookup.Provider provider) {
            if (!tag.contains("Version", Tag.TAG_INT) || tag.getInt("Version") != 1 || !tag.contains("Name", Tag.TAG_STRING)
                    || !tag.contains("Priority", Tag.TAG_INT) || !tag.contains("Target", Tag.TAG_COMPOUND))
                throw new IllegalArgumentException("Invalid saved policy configuration");
            var steps = new ArrayList<Step>();
            for (var raw : list(tag, "Steps", MAX_STEPS)) steps.add(Step.load((CompoundTag) raw, provider));
            var connections = new ArrayList<Connection>();
            for (var raw : list(tag, "Connections", MAX_CONNECTIONS)) {
                var entry = (CompoundTag) raw;
                if (!entry.hasUUID("Producer") || !entry.hasUUID("Requirement") || entry.contains("Consumer") && !entry.hasUUID("Consumer"))
                    throw new IllegalArgumentException("Invalid saved policy connection");
                connections.add(new Connection(entry.getUUID("Producer"), entry.contains("Consumer") ? entry.getUUID("Consumer") : null,
                        entry.getUUID("Requirement")));
            }
            return new Configuration(tag.getString("Name"), Target.load(tag.getCompound("Target"), provider), tag.getInt("Priority"), steps, connections);
        }
    }

    public record Target(TargetKind kind, UUID consumer, UUID requirement, BlockPos storage, ItemStack sample, int amount) {
        public Target {
            Objects.requireNonNull(kind);
            sample = Objects.requireNonNull(sample).copyWithCount(sample.isEmpty() ? 0 : 1);
            storage = storage == null ? null : storage.immutable();
            if (kind == TargetKind.STOCK
                    ? consumer != null || requirement != null || storage == null || sample.isEmpty() || amount < 1 || amount > HiredProductionOrder.MAX_AMOUNT
                    : consumer == null || storage != null || !sample.isEmpty() || amount != 0)
                throw new IllegalArgumentException("Invalid policy target");
        }
        @Override public ItemStack sample() { return sample.copy(); }
        @Override public boolean equals(Object other) {
            return other instanceof Target target && kind == target.kind && Objects.equals(consumer, target.consumer)
                    && Objects.equals(requirement, target.requirement) && Objects.equals(storage, target.storage)
                    && ItemStack.isSameItemSameComponents(sample, target.sample) && amount == target.amount;
        }
        @Override public int hashCode() {
            return Objects.hash(kind, consumer, requirement, storage, ItemStack.hashItemAndComponents(sample), amount);
        }

        CompoundTag save(HolderLookup.Provider provider) {
            var tag = new CompoundTag();
            tag.putString("Kind", kind.name());
            if (consumer != null) tag.putUUID("Consumer", consumer);
            if (requirement != null) tag.putUUID("Requirement", requirement);
            if (storage != null) tag.putLong("Storage", storage.asLong());
            if (!sample.isEmpty()) tag.put("Sample", sample.save(provider));
            tag.putInt("Amount", amount);
            return tag;
        }

        static Target load(CompoundTag tag, HolderLookup.Provider provider) {
            if (!tag.contains("Kind", Tag.TAG_STRING) || !tag.contains("Amount", Tag.TAG_INT)
                    || tag.contains("Consumer") && !tag.hasUUID("Consumer") || tag.contains("Requirement") && !tag.hasUUID("Requirement")
                    || tag.contains("Storage") && !tag.contains("Storage", Tag.TAG_LONG)
                    || tag.contains("Sample") && !tag.contains("Sample", Tag.TAG_COMPOUND))
                throw new IllegalArgumentException("Invalid saved policy target");
            return new Target(TargetKind.valueOf(tag.getString("Kind")), tag.contains("Consumer") ? tag.getUUID("Consumer") : null,
                    tag.contains("Requirement") ? tag.getUUID("Requirement") : null, tag.contains("Storage") ? BlockPos.of(tag.getLong("Storage")) : null,
                    tag.contains("Sample") ? ItemStack.parse(provider, tag.getCompound("Sample")).orElseThrow() : ItemStack.EMPTY, tag.getInt("Amount"));
        }
    }

    public record Step(UUID id, StepKind kind, UUID area, HiredProductionRecipe recipe, ItemStack sample, BlockPos source, BlockPos destination) {
        public Step {
            Objects.requireNonNull(id);
            Objects.requireNonNull(kind);
            Objects.requireNonNull(area);
            Objects.requireNonNull(destination);
            sample = Objects.requireNonNull(sample).copyWithCount(sample.isEmpty() ? 0 : 1);
            source = source == null ? null : source.immutable();
            destination = destination.immutable();
            if (kind == StepKind.PRODUCTION ? recipe == null || !sample.isEmpty() || source != null
                    : recipe != null || sample.isEmpty() || (kind == StepKind.DELIVERY) != (source != null))
                throw new IllegalArgumentException("Invalid approved supply step");
        }
        @Override public ItemStack sample() { return sample.copy(); }
        @Override public boolean equals(Object other) {
            return other instanceof Step step && id.equals(step.id) && kind == step.kind && area.equals(step.area)
                    && Objects.equals(recipe, step.recipe) && ItemStack.isSameItemSameComponents(sample, step.sample)
                    && Objects.equals(source, step.source) && destination.equals(step.destination);
        }
        @Override public int hashCode() {
            return Objects.hash(id, kind, area, recipe, ItemStack.hashItemAndComponents(sample), source, destination);
        }

        CompoundTag save(HolderLookup.Provider provider) {
            var tag = new CompoundTag();
            tag.putUUID("Id", id);
            tag.putString("Kind", kind.name());
            tag.putUUID("Area", area);
            if (recipe != null) tag.put("Recipe", recipe.save());
            if (!sample.isEmpty()) tag.put("Sample", sample.save(provider));
            if (source != null) tag.putLong("Source", source.asLong());
            tag.putLong("Destination", destination.asLong());
            return tag;
        }

        static Step load(CompoundTag tag, HolderLookup.Provider provider) {
            if (!tag.hasUUID("Id") || !tag.contains("Kind", Tag.TAG_STRING) || !tag.hasUUID("Area")
                    || !tag.contains("Destination", Tag.TAG_LONG) || tag.contains("Source") && !tag.contains("Source", Tag.TAG_LONG)
                    || tag.contains("Recipe") && !tag.contains("Recipe", Tag.TAG_COMPOUND)
                    || tag.contains("Sample") && !tag.contains("Sample", Tag.TAG_COMPOUND))
                throw new IllegalArgumentException("Invalid saved approved supply step");
            return new Step(tag.getUUID("Id"), StepKind.valueOf(tag.getString("Kind")), tag.getUUID("Area"),
                    tag.contains("Recipe") ? HiredProductionRecipe.load(tag.getCompound("Recipe")) : null,
                    tag.contains("Sample") ? ItemStack.parse(provider, tag.getCompound("Sample")).orElseThrow() : ItemStack.EMPTY,
                    tag.contains("Source") ? BlockPos.of(tag.getLong("Source")) : null, BlockPos.of(tag.getLong("Destination")));
        }
    }

    public record Connection(UUID producer, UUID consumer, UUID requirement) {
        public Connection {
            Objects.requireNonNull(producer);
            Objects.requireNonNull(requirement);
        }
    }

    public record Binding(UUID id, Step step, UUID order, BindingState state, int target, long ceiling, long gathered) {
        public Binding(UUID id, Step step, UUID order, BindingState state, int target) {
            this(id, step, order, state, target, Long.MAX_VALUE, 0);
        }

        Binding execution(BindingState state, int target, long ceiling) {
            return new Binding(id, step, order, state, target, ceiling, gathered);
        }

        Binding gathered(long amount) {
            if (step.kind() != StepKind.GATHER || amount < 0) throw new IllegalArgumentException("Invalid gathering progress");
            return new Binding(id, step, order, state, target, ceiling, Math.addExact(gathered, amount));
        }

        public Binding {
            Objects.requireNonNull(id);
            Objects.requireNonNull(step);
            Objects.requireNonNull(state);
            if ((step.kind() == StepKind.GATHER) != (order == null) || target < 0 || target > HiredProductionOrder.MAX_AMOUNT
                    || ceiling < 0 || gathered < 0 || step.kind() != StepKind.GATHER && gathered != 0)
                throw new IllegalArgumentException("Invalid managed execution binding");
        }

        CompoundTag save(HolderLookup.Provider provider) {
            var tag = new CompoundTag();
            tag.putUUID("Id", id);
            tag.put("Step", step.save(provider));
            if (order != null) tag.putUUID("Order", order);
            tag.putString("State", state.name());
            tag.putInt("Target", target);
            tag.putLong("Ceiling", ceiling);
            tag.putLong("Gathered", gathered);
            return tag;
        }

        static Binding load(CompoundTag tag, HolderLookup.Provider provider) {
            if (!tag.hasUUID("Id") || !tag.contains("Step", Tag.TAG_COMPOUND) || tag.contains("Order") && !tag.hasUUID("Order")
                    || !tag.contains("State", Tag.TAG_STRING) || !tag.contains("Target", Tag.TAG_INT)
                    || !tag.contains("Ceiling", Tag.TAG_LONG) || !tag.contains("Gathered", Tag.TAG_LONG))
                throw new IllegalArgumentException("Invalid saved managed execution binding");
            return new Binding(tag.getUUID("Id"), Step.load(tag.getCompound("Step"), provider), tag.contains("Order") ? tag.getUUID("Order") : null,
                    BindingState.valueOf(tag.getString("State")), tag.getInt("Target"), tag.getLong("Ceiling"), tag.getLong("Gathered"));
        }
    }

    public record Event(long at, EventKind kind, String detail) {
        public Event {
            Objects.requireNonNull(kind);
            if (at < 0 || detail == null || detail.length() > 128) throw new IllegalArgumentException("Invalid policy event");
        }
    }
}
