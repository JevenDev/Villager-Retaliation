package com.jvn.villagerretaliation.settlement;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.ChunkPos;

final class SettlementGroundExperience {
    private static final String VERSION_TAG = "VillagerRetaliationSettlementExperienceVersion";
    private final UUID id;
    private CompoundTag entity;
    private BlockPos nativePosition;
    private final Set<Long> nativeChunks = new LinkedHashSet<>();
    private final Set<Long> acknowledgedChunks = new LinkedHashSet<>();
    private long checkedAt;
    private long version;
    private boolean removed;

    private SettlementGroundExperience(CompoundTag entity, long checkedAt, long version, boolean removed) {
        var pos = entity.getList("Pos", Tag.TAG_DOUBLE);
        if (!entity.hasUUID("UUID") || pos.size() != 3 || !Double.isFinite(pos.getDouble(0))
                || !Double.isFinite(pos.getDouble(1)) || !Double.isFinite(pos.getDouble(2))
                || entity.getShort("Value") <= 0 || entity.getInt("Count") <= 0 || version < 0L) {
            throw new IllegalArgumentException("Ground experience identity, value, count or version is invalid");
        }
        this.id = entity.getUUID("UUID");
        this.entity = entity.copy();
        this.checkedAt = Math.max(0L, checkedAt);
        this.version = version;
        this.removed = removed;
        nativePosition(position());
    }

    static SettlementGroundExperience capture(ExperienceOrb orb, long now) {
        SettlementExperienceSimulation.remember(orb);
        return new SettlementGroundExperience(orb.saveWithoutId(new CompoundTag()), now, version(orb), false);
    }

    UUID id() {
        return this.id;
    }

    BlockPos position() {
        var pos = this.entity.getList("Pos", Tag.TAG_DOUBLE);
        return BlockPos.containing(pos.getDouble(0), pos.getDouble(1), pos.getDouble(2));
    }

    BlockPos nativePosition() {
        return this.nativePosition;
    }

    void nativePosition(BlockPos position) {
        this.nativePosition = position.immutable();
        this.nativeChunks.add(ChunkPos.asLong(position));
    }

    Set<Long> nativeChunks() {
        return Set.copyOf(this.nativeChunks);
    }

    void retainAnchors(SettlementGroundExperience other) {
        this.nativeChunks.addAll(other.nativeChunks);
        if (this.removed && other.removed && this.version == other.version) this.acknowledgedChunks.addAll(other.acknowledgedChunks);
    }

    void acknowledge(long chunk) {
        if (this.removed && this.nativeChunks.contains(chunk)) this.acknowledgedChunks.add(chunk);
    }

    boolean acknowledged() {
        return this.removed && this.acknowledgedChunks.containsAll(this.nativeChunks);
    }

    long checkedAt() {
        return this.checkedAt;
    }

    long version() {
        return this.version;
    }

    boolean removed() {
        return this.removed;
    }

    int count() {
        return this.entity.getInt("Count");
    }

    static long version(ExperienceOrb orb) {
        return Math.max(0L, orb.getPersistentData().getLong(VERSION_TAG));
    }

    void observe(ExperienceOrb orb, long now) {
        if (!this.id.equals(orb.getUUID())) throw new IllegalArgumentException("Ground experience identity differs from its native orb");
        SettlementExperienceSimulation.remember(orb);
        nativePosition(orb.blockPosition());
        var observed = orb.saveWithoutId(new CompoundTag());
        if (observed.getShort("Value") <= 0 || observed.getInt("Count") <= 0) {
            remove();
            return;
        }
        this.version = Math.max(this.version, version(orb));
        if (!this.entity.equals(observed)) {
            this.version++;
            orb.getPersistentData().putLong(VERSION_TAG, this.version);
            observed = orb.saveWithoutId(new CompoundTag());
        }
        this.entity = observed;
        this.checkedAt = now;
        this.removed = false;
    }

    void advance(ServerLevel level, SettlementExperienceSimulation simulation, long now) {
        if (this.removed || now <= this.checkedAt) return;
        int elapsed = (int) Math.clamp(now - this.checkedAt, 0L, 200L);
        this.checkedAt = now;
        var orb = context(level);
        simulation.advance(orb, elapsed);
        this.entity = orb.saveWithoutId(new CompoundTag());
        this.removed = orb.isRemoved();
        if (this.removed && this.entity.getInt("Count") <= 0) this.entity.putInt("Count", 1);
        this.version++;
    }

    void remove() {
        if (this.removed) return;
        this.removed = true;
        this.version++;
    }

    ExperienceOrb context(ServerLevel level) {
        var orb = new ExperienceOrb(level, 0, 0, 0, 1);
        orb.load(this.entity.copy());
        return orb;
    }

    boolean restore(ExperienceOrb orb) {
        if (!this.id.equals(orb.getUUID())) throw new IllegalArgumentException("Ground experience identity differs from its native orb");
        if (version(orb) > this.version) return false;
        if (this.removed) {
            orb.getPersistentData().putLong(VERSION_TAG, this.version);
            orb.discard();
            return true;
        }
        if (version(orb) == this.version) return false;
        orb.load(this.entity.copy());
        SettlementExperienceSimulation.restore(orb);
        orb.getPersistentData().putLong(VERSION_TAG, this.version);
        nativePosition(orb.blockPosition());
        this.entity = orb.saveWithoutId(new CompoundTag());
        return true;
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.put("Entity", this.entity.copy());
        tag.putLong("CheckedAt", this.checkedAt);
        tag.putLong("Version", this.version);
        tag.putBoolean("Removed", this.removed);
        tag.putLong("NativePosition", this.nativePosition.asLong());
        tag.putLongArray("NativeChunks", this.nativeChunks.stream().mapToLong(Long::longValue).toArray());
        tag.putLongArray("AcknowledgedChunks", this.acknowledgedChunks.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }

    static SettlementGroundExperience load(CompoundTag tag) {
        var state = new SettlementGroundExperience(tag.getCompound("Entity"), tag.getLong("CheckedAt"),
                tag.getLong("Version"), tag.getBoolean("Removed"));
        if (tag.contains("NativePosition", Tag.TAG_LONG)) {
            state.nativeChunks.clear();
            state.nativePosition(BlockPos.of(tag.getLong("NativePosition")));
        }
        for (long chunk : tag.getLongArray("NativeChunks")) state.nativeChunks.add(chunk);
        for (long chunk : tag.getLongArray("AcknowledgedChunks")) state.acknowledge(chunk);
        return state;
    }
}
