package com.jvn.villagerretaliation.diplomacy.route;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public final class RoutePreparation {
    public enum State { PREPARING, READY, BLOCKED, CANCELLED }
    private final UUID id;
    private final ResourceLocation dimension;
    private final BlockPos start, end;
    private final boolean boatAllowed;
    State state = State.PREPARING;
    String blocker = "";
    int width = 1;
    int attempts;
    final Set<Long> dependencies = new LinkedHashSet<>();
    DiplomaticRoute route;

    RoutePreparation(UUID id, ResourceLocation dimension, BlockPos start, BlockPos end, boolean boatAllowed) {
        this.id = id;
        this.dimension = dimension;
        this.start = start.immutable();
        this.end = end.immutable();
        this.boatAllowed = boatAllowed;
    }
    public UUID id() { return this.id; }
    public ResourceLocation dimension() { return this.dimension; }
    public BlockPos start() { return this.start; }
    public BlockPos end() { return this.end; }
    public boolean boatAllowed() { return this.boatAllowed; }
    public State state() { return this.state; }
    public String blocker() { return this.blocker; }
    public DiplomaticRoute route() { return this.route; }
    public int generationFootprint() { return this.dependencies.size(); }
    public int attempts() { return this.attempts; }
    void block(String reason) { this.state = State.BLOCKED; this.blocker = reason; }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putUUID("Id", this.id);
        tag.putString("Dimension", this.dimension.toString());
        tag.putLong("Start", this.start.asLong());
        tag.putLong("End", this.end.asLong());
        tag.putBoolean("BoatAllowed", this.boatAllowed);
        tag.putString("State", this.state.name());
        tag.putString("Blocker", this.blocker);
        tag.putInt("Width", this.width);
        tag.putInt("Attempts", this.attempts);
        tag.putLongArray("Dependencies", this.dependencies.stream().mapToLong(Long::longValue).toArray());
        if (this.route != null) tag.put("Route", this.route.save());
        return tag;
    }
    static RoutePreparation load(CompoundTag tag) {
        if (!tag.hasUUID("Id") || !tag.contains("Dimension", Tag.TAG_STRING) || !tag.contains("Start", Tag.TAG_LONG)
                || !tag.contains("End", Tag.TAG_LONG) || !tag.contains("BoatAllowed", Tag.TAG_BYTE) || !tag.contains("State", Tag.TAG_STRING)
                || !tag.contains("Blocker", Tag.TAG_STRING) || !tag.contains("Width", Tag.TAG_INT) || !tag.contains("Attempts", Tag.TAG_INT)
                || !tag.contains("Dependencies", Tag.TAG_LONG_ARRAY)) throw new IllegalArgumentException("Missing route preparation fields");
        var result = new RoutePreparation(tag.getUUID("Id"), ResourceLocation.parse(tag.getString("Dimension")),
                BlockPos.of(tag.getLong("Start")), BlockPos.of(tag.getLong("End")), tag.getBoolean("BoatAllowed"));
        result.state = State.valueOf(tag.getString("State"));
        result.blocker = tag.getString("Blocker");
        result.width = tag.getInt("Width");
        result.attempts = tag.getInt("Attempts");
        var dependencies = tag.getLongArray("Dependencies");
        if (result.width < 1 || result.width > 64 || result.attempts < 0 || result.attempts > 32
                || result.blocker.length() > 80 || dependencies.length > 16384) throw new IllegalArgumentException("Invalid preparation bounds");
        for (long dependency : dependencies) if (!result.dependencies.add(dependency)) throw new IllegalArgumentException("Duplicate route dependency");
        if (tag.contains("Route")) result.route = DiplomaticRoute.load(tag.getCompound("Route"));
        if (result.state == State.READY && result.route == null) throw new IllegalArgumentException("Ready route missing geometry");
        if (result.route != null && (!result.route.steps().getFirst().position().equals(result.start)
                || !result.route.steps().getLast().position().equals(result.end)
                || !result.boatAllowed && result.route.needsBoat())) throw new IllegalArgumentException("Route does not match preparation");
        return result;
    }
}
