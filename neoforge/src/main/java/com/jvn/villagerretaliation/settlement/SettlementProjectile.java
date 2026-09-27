package com.jvn.villagerretaliation.settlement;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ChunkPos;

final class SettlementProjectile {
    private static final String VERSION_TAG = "VillagerRetaliationSettlementProjectileVersion";
    private final UUID id;
    private final UUID owner;
    private final UUID area;
    private final ResourceLocation type;
    private CompoundTag entity;
    private final CompoundTag shooter;
    private final Set<Long> nativeChunks = new LinkedHashSet<>();
    private final Set<Long> acknowledgedChunks = new LinkedHashSet<>();
    private BlockPos nativePosition;
    private long checkedAt;
    private long version;
    private boolean pendingSpawn;
    private boolean removed;

    private SettlementProjectile(ResourceLocation type, CompoundTag entity, UUID owner, UUID area,
                                 CompoundTag shooter, long now) {
        var pos = entity.getList("Pos", Tag.TAG_DOUBLE);
        if (!entity.hasUUID("UUID") || pos.size() != 3 || !Double.isFinite(pos.getDouble(0))
                || !Double.isFinite(pos.getDouble(1)) || !Double.isFinite(pos.getDouble(2))
                || !shooter.hasUUID("UUID") || !shooter.getUUID("UUID").equals(owner)) {
            throw new IllegalArgumentException("Settlement projectile requires finite position and matching ownership");
        }
        this.id = entity.getUUID("UUID");
        this.owner = owner;
        this.area = area;
        this.type = type;
        this.entity = entity.copy();
        this.shooter = shooter.copy();
        this.checkedAt = Math.max(0L, now);
        this.nativePosition = position();
    }

    static SettlementProjectile launched(Projectile projectile, SettlementWorkerState worker, long now) {
        var owner = projectile.getOwner();
        if (owner == null || !owner.getUUID().equals(worker.id())) throw new IllegalArgumentException("Projectile launcher differs from its worker");
        var state = new SettlementProjectile(BuiltInRegistries.ENTITY_TYPE.getKey(projectile.getType()),
                projectile.saveWithoutId(new CompoundTag()), worker.id(), worker.area(), owner.saveWithoutId(new CompoundTag()), now);
        state.anchor(projectile.blockPosition());
        state.pendingSpawn = true;
        state.version = 1L;
        return state;
    }

    UUID id() { return this.id; }
    UUID owner() { return this.owner; }
    UUID area() { return this.area; }
    long checkedAt() { return this.checkedAt; }
    long version() { return this.version; }
    boolean pendingSpawn() { return this.pendingSpawn; }
    boolean removed() { return this.removed; }
    BlockPos nativePosition() { return this.nativePosition; }
    Set<Long> nativeChunks() { return Set.copyOf(this.nativeChunks); }

    BlockPos position() {
        var pos = this.entity.getList("Pos", Tag.TAG_DOUBLE);
        return BlockPos.containing(pos.getDouble(0), pos.getDouble(1), pos.getDouble(2));
    }

    void anchor(BlockPos position) {
        this.nativePosition = position.immutable();
        this.nativeChunks.add(ChunkPos.asLong(position));
    }

    void retainAnchors(SettlementProjectile other) {
        this.nativeChunks.addAll(other.nativeChunks);
        if (this.removed && other.removed && this.version == other.version) this.acknowledgedChunks.addAll(other.acknowledgedChunks);
    }

    void acknowledge(long chunk) {
        if (this.removed && this.nativeChunks.contains(chunk)) this.acknowledgedChunks.add(chunk);
    }

    boolean acknowledged() {
        return this.removed && this.acknowledgedChunks.containsAll(this.nativeChunks);
    }

    static long version(Projectile projectile) {
        return Math.max(0L, projectile.getPersistentData().getLong(VERSION_TAG));
    }

    void observe(Projectile projectile, long now) {
        verify(projectile);
        anchor(projectile.blockPosition());
        this.version = Math.max(this.version, version(projectile)) + 1L;
        projectile.getPersistentData().putLong(VERSION_TAG, this.version);
        this.entity = projectile.saveWithoutId(new CompoundTag());
        this.checkedAt = now;
        this.pendingSpawn = false;
        this.removed = false;
        this.acknowledgedChunks.clear();
    }

    void advance(Projectile projectile, long now) {
        verify(projectile);
        this.entity = projectile.saveWithoutId(new CompoundTag());
        this.checkedAt = now;
        this.removed = projectile.isRemoved();
        this.version++;
    }

    void prepareSpawn() {
        if (!this.pendingSpawn) {
            this.pendingSpawn = true;
            this.version++;
        }
        anchor(position());
    }

    void checked(long now) {
        this.checkedAt = Math.max(this.checkedAt, now);
    }

    void remove() {
        if (!this.removed) {
            this.removed = true;
            this.version++;
        }
    }

    Projectile context(ServerLevel level) {
        var type = BuiltInRegistries.ENTITY_TYPE.getOptional(this.type).orElse(null);
        if (type == null || !(type.create(level) instanceof Projectile projectile)) return null;
        projectile.load(this.entity.copy());
        return projectile;
    }

    net.minecraft.world.entity.npc.Villager shooter(ServerLevel level, SettlementRecord town) {
        var worker = town.workers().get(this.owner);
        if (worker != null) return worker.context(level, town);
        var owner = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        if (owner != null) {
            owner.load(this.shooter.copy());
            owner.setRemoved(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        }
        return owner;
    }

    boolean restore(Projectile projectile) {
        verify(projectile);
        if (version(projectile) > this.version) return false;
        if (this.removed) {
            projectile.getPersistentData().putLong(VERSION_TAG, this.version);
            projectile.discard();
            return true;
        }
        if (version(projectile) == this.version) return false;
        projectile.load(this.entity.copy());
        projectile.getPersistentData().putLong(VERSION_TAG, this.version);
        anchor(projectile.blockPosition());
        this.entity = projectile.saveWithoutId(new CompoundTag());
        return true;
    }

    private void verify(Projectile projectile) {
        if (!this.id.equals(projectile.getUUID()) || !this.type.equals(BuiltInRegistries.ENTITY_TYPE.getKey(projectile.getType()))) {
            throw new IllegalArgumentException("Settlement projectile identity differs from its native body");
        }
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putString("Type", this.type.toString());
        tag.put("Entity", this.entity.copy());
        tag.putUUID("Owner", this.owner);
        tag.putUUID("Area", this.area);
        tag.put("Shooter", this.shooter.copy());
        tag.putLong("CheckedAt", this.checkedAt);
        tag.putLong("Version", this.version);
        tag.putBoolean("PendingSpawn", this.pendingSpawn);
        tag.putBoolean("Removed", this.removed);
        tag.putLong("NativePosition", this.nativePosition.asLong());
        tag.putLongArray("NativeChunks", this.nativeChunks.stream().mapToLong(Long::longValue).toArray());
        tag.putLongArray("AcknowledgedChunks", this.acknowledgedChunks.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }

    static SettlementProjectile load(CompoundTag tag) {
        var state = new SettlementProjectile(ResourceLocation.parse(tag.getString("Type")), tag.getCompound("Entity"),
                tag.getUUID("Owner"), tag.getUUID("Area"), tag.getCompound("Shooter"), tag.getLong("CheckedAt"));
        state.version = Math.max(0L, tag.getLong("Version"));
        state.pendingSpawn = tag.getBoolean("PendingSpawn");
        state.removed = tag.getBoolean("Removed");
        state.nativePosition = BlockPos.of(tag.getLong("NativePosition"));
        for (long chunk : tag.getLongArray("NativeChunks")) state.nativeChunks.add(chunk);
        for (long chunk : tag.getLongArray("AcknowledgedChunks")) state.acknowledge(chunk);
        return state;
    }
}
