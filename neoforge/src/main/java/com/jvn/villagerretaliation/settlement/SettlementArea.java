package com.jvn.villagerretaliation.settlement;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public record SettlementArea(
        UUID id, SettlementAreaType type, String name, BlockPos min, BlockPos max,
        int priority, boolean enabled, SettlementCropPlan crops, boolean wall) {
    public SettlementArea(UUID id, SettlementAreaType type, String name, BlockPos min, BlockPos max,
                          int priority, boolean enabled, SettlementCropPlan crops) {
        this(id, type, name, min, max, priority, enabled, crops, false);
    }
    public SettlementArea(UUID id, SettlementAreaType type, String name, BlockPos min, BlockPos max,
                          int priority, boolean enabled) {
        this(id, type, name, min, max, priority, enabled, SettlementCropPlan.NONE);
    }

    public static final int MAX_VOLUME = 65_536;
    public static final int MAX_NAME_LENGTH = 48;

    public SettlementArea {
        Objects.requireNonNull(crops, "crops");
        if (type != SettlementAreaType.FARM && (crops.enabled() || crops.demand() || crops.cycles() > 0)) throw new IllegalArgumentException("Crop plans require a farm");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(min, "min");
        Objects.requireNonNull(max, "max");
        name = Objects.requireNonNull(name, "name").strip();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH
                || name.codePoints().anyMatch(Character::isISOControl) || name.indexOf('\u00a7') >= 0) {
            throw new IllegalArgumentException("Invalid settlement area name");
        }
        min = min.immutable();
        max = max.immutable();
        long width = (long) max.getX() - min.getX() + 1L;
        long height = (long) max.getY() - min.getY() + 1L;
        long depth = (long) max.getZ() - min.getZ() + 1L;
        if (wall && type != SettlementAreaType.CONSTRUCTION) throw new IllegalArgumentException("Wall bounds require construction");
        long wallExtent = (long) SettlementWall.MAX_NODES * SettlementWall.MAX_SPAN + 32;
        if (width <= 0 || height <= 0 || depth <= 0 || priority < 0 || priority > 9
                || (wall ? width > wallExtent || height > 4096 || depth > wallExtent
                        : width > 128 || height > 64 || depth > 128 || width * height * depth > MAX_VOLUME)) {
            throw new IllegalArgumentException("Invalid settlement area bounds or priority");
        }
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    public boolean intersects(SettlementArea other) {
        return min.getX() <= other.max.getX() && max.getX() >= other.min.getX()
                && min.getY() <= other.max.getY() && max.getY() >= other.min.getY()
                && min.getZ() <= other.max.getZ() && max.getZ() >= other.min.getZ();
    }

    boolean conflictsWith(SettlementArea other) {
        return intersects(other) && (type == other.type
                || type.changesTerrain() && other.type.changesTerrain() && !(type.usesFields() && other.type.usesFields()));
    }

    public boolean within(BlockPos center, int radius) {
        return (long) min.getX() >= (long) center.getX() - radius
                && (long) max.getX() <= (long) center.getX() + radius
                && (long) min.getZ() >= (long) center.getZ() - radius
                && (long) max.getZ() <= (long) center.getZ() + radius;
    }

    public BlockPos center() {
        return new BlockPos(min.getX() + (max.getX() - min.getX()) / 2,
                min.getY() + (max.getY() - min.getY()) / 2,
                min.getZ() + (max.getZ() - min.getZ()) / 2);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Type", type.name());
        tag.putString("Name", name);
        tag.putLong("Min", min.asLong());
        tag.putLong("Max", max.asLong());
        tag.putInt("Priority", priority);
        tag.putBoolean("Enabled", enabled);
        tag.put("Crops", crops.save());
        if (wall) tag.putBoolean("Wall", true);
        return tag;
    }

    public static SettlementArea load(CompoundTag tag) {
        return new SettlementArea(tag.getUUID("Id"), SettlementAreaType.valueOf(tag.getString("Type")),
                tag.getString("Name"), BlockPos.of(tag.getLong("Min")), BlockPos.of(tag.getLong("Max")),
                tag.getInt("Priority"), tag.getBoolean("Enabled"),
                tag.contains("Crops") ? SettlementCropPlan.load(tag.getCompound("Crops")) : SettlementCropPlan.NONE, tag.getBoolean("Wall"));
    }
}
