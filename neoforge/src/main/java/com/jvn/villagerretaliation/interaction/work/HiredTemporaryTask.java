package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkArea;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;

public record HiredTemporaryTask(UUID id, Kind kind, BlockPos min, BlockPos max, int remaining) {
    public static final int MAX_SPAN = 64;
    public static final int MAX_CATCHES = 4096;

    public HiredTemporaryTask {
        Objects.requireNonNull(id);
        Objects.requireNonNull(kind);
        Objects.requireNonNull(min);
        Objects.requireNonNull(max);
        min = min.immutable();
        max = max.immutable();
        if (min.getX() > max.getX() || min.getY() > max.getY() || min.getZ() > max.getZ()
                || (long) max.getX() - min.getX() >= MAX_SPAN || (long) max.getY() - min.getY() >= MAX_SPAN
                || (long) max.getZ() - min.getZ() >= MAX_SPAN || remaining < 1
                || remaining > (kind == Kind.FISH ? MAX_CATCHES : 1)) {
            throw new IllegalArgumentException("Invalid temporary task bounds or quantity");
        }
    }

    public HiredWorkArea area() {
        return HiredWorkArea.fromBounds(min, max, true);
    }

    public HiredTemporaryTask consume() {
        return remaining == 1 ? null : new HiredTemporaryTask(id, kind, min, max, remaining - 1);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", 1);
        tag.putUUID("Id", id);
        tag.putString("Kind", kind.name());
        tag.putLong("Min", min.asLong());
        tag.putLong("Max", max.asLong());
        tag.putInt("Remaining", remaining);
        return tag;
    }

    public static HiredTemporaryTask load(CompoundTag tag) {
        if (!tag.contains("Version", Tag.TAG_INT) || tag.getInt("Version") != 1 || !tag.hasUUID("Id")
                || !tag.contains("Kind", Tag.TAG_STRING) || !tag.contains("Min", Tag.TAG_LONG)
                || !tag.contains("Max", Tag.TAG_LONG) || !tag.contains("Remaining", Tag.TAG_INT)) {
            throw new IllegalArgumentException("Invalid temporary task");
        }
        return new HiredTemporaryTask(tag.getUUID("Id"), Kind.valueOf(tag.getString("Kind")),
                BlockPos.of(tag.getLong("Min")), BlockPos.of(tag.getLong("Max")), tag.getInt("Remaining"));
    }

    public void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(id);
        buffer.writeEnum(kind);
        buffer.writeBlockPos(min);
        buffer.writeBlockPos(max);
        buffer.writeVarInt(remaining);
    }

    public static HiredTemporaryTask read(RegistryFriendlyByteBuf buffer) {
        return new HiredTemporaryTask(buffer.readUUID(), buffer.readEnum(Kind.class),
                buffer.readBlockPos(), buffer.readBlockPos(), buffer.readVarInt());
    }

    public enum Kind {
        FARM_PATCH(HiredVillagerRole.FARMING, "Work farm patch"),
        EXCAVATE(HiredVillagerRole.MINING, "Excavate area"),
        FISH(HiredVillagerRole.FISHING, "Catch fish");

        private final HiredVillagerRole role;
        private final String label;

        Kind(HiredVillagerRole role, String label) {
            this.role = role;
            this.label = label;
        }

        public HiredVillagerRole role() { return this.role; }
        public String label() { return this.label; }

        public static Kind forRole(HiredVillagerRole role) {
            for (Kind kind : values()) if (kind.role == role) return kind;
            return null;
        }
    }
}
