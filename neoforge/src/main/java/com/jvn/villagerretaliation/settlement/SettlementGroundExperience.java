package com.jvn.villagerretaliation.settlement;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;

final class SettlementGroundExperience {
    private static final String VERSION_TAG = "VillagerRetaliationSettlementExperienceVersion";
    private final UUID id;
    private CompoundTag entity;
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
    }

    static SettlementGroundExperience capture(ExperienceOrb orb, long now) {
        return new SettlementGroundExperience(orb.saveWithoutId(new CompoundTag()), now, version(orb), false);
    }

    UUID id() {
        return this.id;
    }

    BlockPos position() {
        var pos = this.entity.getList("Pos", Tag.TAG_DOUBLE);
        return BlockPos.containing(pos.getDouble(0), pos.getDouble(1), pos.getDouble(2));
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

    void advance(long now) {
        if (this.removed || now <= this.checkedAt) return;
        int elapsed = (int) Math.clamp(now - this.checkedAt, 0L, 200L);
        this.checkedAt = now;
        int age = Math.min(6000, this.entity.getShort("Age") + elapsed);
        this.entity.putShort("Age", (short) age);
        this.removed = age >= 6000;
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
        orb.getPersistentData().putLong(VERSION_TAG, this.version);
        this.entity = orb.saveWithoutId(new CompoundTag());
        return true;
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.put("Entity", this.entity.copy());
        tag.putLong("CheckedAt", this.checkedAt);
        tag.putLong("Version", this.version);
        tag.putBoolean("Removed", this.removed);
        return tag;
    }

    static SettlementGroundExperience load(CompoundTag tag) {
        return new SettlementGroundExperience(tag.getCompound("Entity"), tag.getLong("CheckedAt"),
                tag.getLong("Version"), tag.getBoolean("Removed"));
    }
}
