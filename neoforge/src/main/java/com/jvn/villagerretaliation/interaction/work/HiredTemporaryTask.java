package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkArea;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;

public record HiredTemporaryTask(UUID id, Kind kind, BlockPos min, BlockPos max, int remaining, BlockPos target, UUID entity, String material) {
    public HiredTemporaryTask(UUID id, Kind kind, BlockPos min, BlockPos max, int remaining, BlockPos target, UUID entity) {
        this(id, kind, min, max, remaining, target, entity, "");
    }
    public HiredTemporaryTask(UUID id, Kind kind, BlockPos min, BlockPos max, int remaining, BlockPos target) {
        this(id, kind, min, max, remaining, target, null);
    }

    public HiredTemporaryTask(UUID id, Kind kind, BlockPos min, BlockPos max, int remaining) {
        this(id, kind, min, max, remaining, null);
    }

    public static final int MAX_SPAN = 64;
    public static final int MAX_CATCHES = 4096;

    public HiredTemporaryTask {
        Objects.requireNonNull(id);
        Objects.requireNonNull(kind);
        Objects.requireNonNull(material);
        if (material.length() > 128 || !material.isEmpty() && (kind.role() != HiredVillagerRole.MINING
                || net.minecraft.resources.ResourceLocation.tryParse(material.startsWith("#") ? material.substring(1) : material) == null))
            throw new IllegalArgumentException("Invalid mining material");
        Objects.requireNonNull(min);
        Objects.requireNonNull(max);
        min = min.immutable();
        max = max.immutable();
        target = target == null ? null : target.immutable();
        if (kind.staircase() && target != null) {
            int x = max.getX() - min.getX(), z = max.getZ() - min.getZ();
            if (x == z || target.getY() != max.getY() - 2 || min.getY() != target.getY() - Math.max(x, z)
                    || !(x > z ? target.getX() == min.getX() || target.getX() == max.getX()
                    : target.getZ() == min.getZ() || target.getZ() == max.getZ())) throw new IllegalArgumentException("Invalid staircase geometry");
        }
        if (min.getX() > max.getX() || min.getY() > max.getY() || min.getZ() > max.getZ()
                || (long) max.getX() - min.getX() >= MAX_SPAN || (long) max.getY() - min.getY() >= MAX_SPAN
                || (long) max.getZ() - min.getZ() >= MAX_SPAN || remaining < 1
                || remaining > (kind == Kind.FISH ? MAX_CATCHES : 1)
                || kind.pointTarget() != (target != null)
                || kind.entityTarget() != (entity != null)
                || target != null && (target.getX() < min.getX() || target.getY() < min.getY() || target.getZ() < min.getZ()
                        || target.getX() > max.getX() || target.getY() > max.getY() || target.getZ() > max.getZ())) {
            throw new IllegalArgumentException("Invalid temporary task bounds or quantity");
        }
    }

    public boolean includes(BlockPos position) {
        if (position.getX() < min.getX() || position.getX() > max.getX()
                || position.getY() < min.getY() || position.getY() > max.getY()
                || position.getZ() < min.getZ() || position.getZ() > max.getZ()) return false;
        if (!kind.staircase()) return kind != Kind.ORE || position.equals(target);
        boolean xAxis = max.getX() - min.getX() >= max.getZ() - min.getZ();
        int start = xAxis ? target.getX() : target.getZ();
        int end = xAxis ? (start == min.getX() ? max.getX() : min.getX()) : (start == min.getZ() ? max.getZ() : min.getZ());
        int depth = Math.abs((xAxis ? position.getX() : position.getZ()) - start);
        int floor = target.getY() - Math.min(depth, Math.abs(end - start));
        return position.getY() >= floor && position.getY() <= floor + 2;
    }

    public boolean matches(net.minecraft.world.level.block.state.BlockState state) {
        if (material.isEmpty()) return true;
        var id = net.minecraft.resources.ResourceLocation.parse(material.startsWith("#") ? material.substring(1) : material);
        return material.startsWith("#") ? state.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, id))
                : net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(id);
    }

    public HiredWorkArea area() {
        return HiredWorkArea.fromBounds(min, max, true);
    }

    public HiredTemporaryTask consume() {
        if (kind == Kind.STAIRCASE_PROSPECT) {
            boolean xAxis = max.getX() - min.getX() >= max.getZ() - min.getZ();
            var end = new BlockPos(xAxis ? (target.getX() == min.getX() ? max.getX() : min.getX()) : target.getX(),
                    min.getY(), xAxis ? target.getZ() : (target.getZ() == min.getZ() ? max.getZ() : min.getZ()));
            return new HiredTemporaryTask(UUID.randomUUID(), Kind.ORE_AREA, end.offset(-8, -2, -8), end.offset(8, 4, 8), 1, null, null, material);
        }
        return remaining == 1 ? null : new HiredTemporaryTask(id, kind, min, max, remaining - 1, target, entity, material);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", 4);
        if (!material.isEmpty()) tag.putString("Material", material);
        tag.putUUID("Id", id);
        tag.putString("Kind", kind.name());
        tag.putLong("Min", min.asLong());
        tag.putLong("Max", max.asLong());
        tag.putInt("Remaining", remaining);
        if (target != null) tag.putLong("Target", target.asLong());
        if (entity != null) tag.putUUID("Entity", entity);
        return tag;
    }

    public static HiredTemporaryTask load(CompoundTag tag) {
        if (!tag.contains("Version", Tag.TAG_INT) || (tag.getInt("Version") < 1 || tag.getInt("Version") > 4) || !tag.hasUUID("Id")
                || !tag.contains("Kind", Tag.TAG_STRING) || !tag.contains("Min", Tag.TAG_LONG)
                || !tag.contains("Max", Tag.TAG_LONG) || !tag.contains("Remaining", Tag.TAG_INT)
                || tag.contains("Target") && !tag.contains("Target", Tag.TAG_LONG)
                || tag.contains("Entity") && !tag.hasUUID("Entity")
                || tag.contains("Material") && !tag.contains("Material", Tag.TAG_STRING)) {
            throw new IllegalArgumentException("Invalid temporary task");
        }
        return new HiredTemporaryTask(tag.getUUID("Id"), Kind.valueOf(tag.getString("Kind")),
                BlockPos.of(tag.getLong("Min")), BlockPos.of(tag.getLong("Max")), tag.getInt("Remaining"),
                tag.contains("Target") ? BlockPos.of(tag.getLong("Target")) : null, tag.hasUUID("Entity") ? tag.getUUID("Entity") : null, tag.getString("Material"));
    }

    public void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(id);
        buffer.writeEnum(kind);
        buffer.writeBlockPos(min);
        buffer.writeBlockPos(max);
        buffer.writeVarInt(remaining);
        buffer.writeBoolean(target != null);
        if (target != null) buffer.writeBlockPos(target);
        buffer.writeBoolean(entity != null);
        if (entity != null) buffer.writeUUID(entity);
        buffer.writeUtf(material, 128);
    }

    public static HiredTemporaryTask read(RegistryFriendlyByteBuf buffer) {
        return new HiredTemporaryTask(buffer.readUUID(), buffer.readEnum(Kind.class),
                buffer.readBlockPos(), buffer.readBlockPos(), buffer.readVarInt(), buffer.readBoolean() ? buffer.readBlockPos() : null,
                buffer.readBoolean() ? buffer.readUUID() : null, buffer.readUtf(128));
    }

    public enum Kind {
        FARM_PATCH(HiredVillagerRole.FARMING, "Work farm patch"),
        EXCAVATE(HiredVillagerRole.MINING, "Excavate area"),
        FISH(HiredVillagerRole.FISHING, "Catch fish"),
        TREE(HiredVillagerRole.LOGGING, "Harvest selected tree"),
        HUNT(HiredVillagerRole.HUNTING, "Pursue selected target"),
        BREED(HiredVillagerRole.ANIMAL_HANDLING, "Breed selected animal"),
        SHEAR(HiredVillagerRole.ANIMAL_HANDLING, "Shear selected sheep"),
        MILK(HiredVillagerRole.ANIMAL_HANDLING, "Milk selected animal"),
        CULL(HiredVillagerRole.ANIMAL_HANDLING, "Cull selected excess animal"),
        COLLECT(HiredVillagerRole.ANIMAL_HANDLING, "Collect animal products"),
        QUARRY(HiredVillagerRole.MINING, "Excavate volume"),
        FOREST(HiredVillagerRole.LOGGING, "Harvest forest"),
        FOREST_REPLANT(HiredVillagerRole.LOGGING, "Harvest and replant forest"),
        ORE(HiredVillagerRole.MINING, "Mine selected deposit"),
        ORE_AREA(HiredVillagerRole.MINING, "Mine exposed ores"),
        TUNNEL(HiredVillagerRole.MINING, "Dig tunnel"),
        STAIRCASE(HiredVillagerRole.MINING, "Dig staircase"),
        STAIRCASE_PROSPECT(HiredVillagerRole.MINING, "Dig staircase and seek ores");

        private final HiredVillagerRole role;
        private final String label;

        Kind(HiredVillagerRole role, String label) {
            this.role = role;
            this.label = label;
        }

        public boolean staircase() { return this == STAIRCASE || this == STAIRCASE_PROSPECT; }
        public boolean forest() { return this == FOREST || this == FOREST_REPLANT; }
        public boolean pointTarget() { return this == TREE || this == ORE || staircase(); }
        public boolean expedition() { return role == HiredVillagerRole.MINING || role == HiredVillagerRole.LOGGING; }

        public HiredVillagerRole role() { return this.role; }
        public String label() { return this.label; }
        public boolean entityTarget() { return this == HUNT || this == BREED || this == SHEAR || this == MILK || this == CULL; }

        public static Kind forRole(HiredVillagerRole role) {
            for (Kind kind : values()) if (kind.role == role) return kind;
            return null;
        }
    }
}
