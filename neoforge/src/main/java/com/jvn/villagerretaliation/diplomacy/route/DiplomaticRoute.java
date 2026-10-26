package com.jvn.villagerretaliation.diplomacy.route;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public record DiplomaticRoute(List<Step> steps) {
    public enum Mode { WALK, LAUNCH, SAIL, LAND }
    public record Step(BlockPos position, Mode mode) {
        public Step { position = position.immutable(); }
    }
    public DiplomaticRoute {
        steps = List.copyOf(steps);
        if (steps.isEmpty() || steps.size() > 65536 || steps.getFirst().mode() != Mode.WALK
                || steps.getLast().mode() != Mode.WALK && steps.getLast().mode() != Mode.LAND)
            throw new IllegalArgumentException("Invalid diplomatic route endpoints");
        boolean sailing = false;
        for (int index = 1; index < steps.size(); index++) {
            var previous = steps.get(index - 1).position();
            var step = steps.get(index);
            int distance = Math.abs(previous.getX() - step.position().getX()) + Math.abs(previous.getZ() - step.position().getZ());
            if (Math.abs(previous.getY() - step.position().getY()) > 1
                    || distance != (step.mode() == Mode.LAUNCH || step.mode() == Mode.LAND ? 2 : 1))
                throw new IllegalArgumentException("Discontinuous diplomatic route");
            if (step.mode() == Mode.LAUNCH) {
                if (sailing) throw new IllegalArgumentException("Duplicate boat launch");
                sailing = true;
            } else if (step.mode() == Mode.LAND) {
                if (!sailing) throw new IllegalArgumentException("Landing without a boat");
                sailing = false;
            } else if (sailing != (step.mode() == Mode.SAIL)) throw new IllegalArgumentException("Invalid route travel mode");
        }
        if (sailing) throw new IllegalArgumentException("Route ends at sea");
    }
    public boolean needsBoat() { return this.steps.stream().anyMatch(step -> step.mode() == Mode.LAUNCH); }
    public int travelCost() {
        return this.steps.stream().mapToInt(step -> step.mode() == Mode.LAUNCH || step.mode() == Mode.LAND ? 20 : 1).sum();
    }
    public DiplomaticRoute reversed() {
        var reversed = new ArrayList<Step>();
        reversed.add(new Step(this.steps.getLast().position(), Mode.WALK));
        for (int index = this.steps.size() - 1; index > 0; index--) {
            var mode = switch (this.steps.get(index).mode()) {
                case LAUNCH -> Mode.LAND;
                case LAND -> Mode.LAUNCH;
                default -> this.steps.get(index).mode();
            };
            reversed.add(new Step(this.steps.get(index - 1).position(), mode));
        }
        return new DiplomaticRoute(reversed);
    }
    public CompoundTag save() {
        var tag = new CompoundTag();
        var list = new ListTag();
        this.steps.forEach(step -> {
            var entry = new CompoundTag();
            entry.putLong("Position", step.position().asLong());
            entry.putString("Mode", step.mode().name());
            list.add(entry);
        });
        tag.put("Steps", list);
        return tag;
    }
    public static DiplomaticRoute load(CompoundTag tag) {
        var raw = tag.getList("Steps", Tag.TAG_COMPOUND);
        if (!raw.equals(tag.get("Steps")) || raw.size() > 65536) throw new IllegalArgumentException("Invalid route steps");
        var steps = new ArrayList<Step>();
        for (var value : raw) {
            var entry = (CompoundTag) value;
            if (!entry.contains("Position", Tag.TAG_LONG) || !entry.contains("Mode", Tag.TAG_STRING))
                throw new IllegalArgumentException("Missing route step fields");
            steps.add(new Step(BlockPos.of(entry.getLong("Position")), Mode.valueOf(entry.getString("Mode"))));
        }
        return new DiplomaticRoute(steps);
    }
}
