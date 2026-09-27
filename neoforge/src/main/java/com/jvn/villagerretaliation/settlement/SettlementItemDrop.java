package com.jvn.villagerretaliation.settlement;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

final class SettlementItemDrop {
    private final UUID id;
    private final UUID area;
    private BlockPos position;
    private final Map<UUID, Access> access = new LinkedHashMap<>();
    private CompoundTag entity;
    private long checkedAt;

    private SettlementItemDrop(UUID id, UUID area, BlockPos position, CompoundTag entity, long checkedAt) {
        this.id = id;
        this.area = area;
        this.position = position.immutable();
        this.entity = entity.copy();
        this.checkedAt = checkedAt;
    }

    static SettlementItemDrop create(ItemEntity entity, UUID area, long now) {
        if (entity.getItem().isEmpty()) throw new IllegalArgumentException("A pending item drop must contain an item");
        return new SettlementItemDrop(entity.getUUID(), area, entity.blockPosition(), entity.saveWithoutId(new CompoundTag()), now);
    }

    long checkedAt() {
        return this.checkedAt;
    }

    CompoundTag entityTag() {
        return this.entity.copy();
    }

    void observe(ItemEntity item, long now) {
        var observed = item.saveWithoutId(new CompoundTag());
        if (!observed.getList("Pos", Tag.TAG_DOUBLE).equals(this.entity.getList("Pos", Tag.TAG_DOUBLE))) this.access.clear();
        this.position = item.blockPosition().immutable();
        this.entity = observed;
        this.checkedAt = now;
    }

    UUID id() {
        return this.id;
    }

    UUID area() {
        return this.area;
    }

    BlockPos position() {
        return this.position;
    }

    ItemStack item(HolderLookup.Provider provider) {
        return ItemStack.parseOptional(provider, this.entity.getCompound("Item"));
    }

    ItemEntity context(ServerLevel level) {
        var entity = new ItemEntity(level, this.position.getX(), this.position.getY(), this.position.getZ(), item(level.registryAccess()));
        entity.load(this.entity.copy());
        return entity;
    }

    void remaining(ItemStack item, HolderLookup.Provider provider) {
        if (item.isEmpty()) throw new IllegalArgumentException("Empty pending drops must be removed");
        this.entity.put("Item", item.save(provider));
    }

    void access(SettlementWorkerState worker, int steps) {
        if (steps < 0) this.access.remove(worker.id());
        else this.access.put(worker.id(), new Access(worker.area(), worker.position(), steps));
    }

    int steps(SettlementWorkerState worker) {
        var route = this.access.get(worker.id());
        return route != null && route.area().equals(worker.area()) && route.start().equals(worker.position()) ? route.steps() : -1;
    }

    boolean advance(ServerLevel level, long now) {
        int elapsed = (int) Math.clamp(now - this.checkedAt, 0L, 200L);
        this.checkedAt = now;
        int age = this.entity.getShort("Age");
        int delay = this.entity.getShort("PickupDelay");
        int lifespan = this.entity.getInt("Lifespan");
        for (int tick = 0; tick < elapsed; tick++) {
            if (delay > 0 && delay != Short.MAX_VALUE) delay--;
            if (age != Short.MIN_VALUE) age++;
            this.entity.putShort("Age", (short) age);
            this.entity.putShort("PickupDelay", (short) delay);
            if (age >= lifespan) {
                var expired = context(level);
                lifespan = Math.clamp((long) lifespan + net.neoforged.neoforge.event.EventHooks.onItemExpire(expired),
                        0, Short.MAX_VALUE - 1);
                if (!this.id.equals(expired.getUUID())) throw new IllegalStateException("An item-expiry hook changed a pending drop identity");
                if (expired.isRemoved() || expired.getItem().isEmpty()) return false;
                if (!this.position.equals(expired.blockPosition())) this.access.clear();
                this.position = expired.blockPosition().immutable();
                this.entity = expired.saveWithoutId(new CompoundTag());
                age = this.entity.getShort("Age");
                delay = this.entity.getShort("PickupDelay");
                this.entity.putInt("Lifespan", lifespan);
                if (age >= lifespan) return false;
            }
        }
        return true;
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putUUID("Id", this.id);
        tag.putUUID("Area", this.area);
        tag.putLong("Position", this.position.asLong());
        tag.put("Entity", this.entity.copy());
        tag.putLong("CheckedAt", this.checkedAt);
        var routes = new ListTag();
        this.access.forEach((worker, route) -> {
            var entry = new CompoundTag();
            entry.putUUID("Worker", worker);
            entry.putUUID("Area", route.area());
            entry.putLong("Start", route.start().asLong());
            entry.putInt("Steps", route.steps());
            routes.add(entry);
        });
        tag.put("Access", routes);
        return tag;
    }

    static SettlementItemDrop load(CompoundTag tag, HolderLookup.Provider provider) {
        var entity = tag.getCompound("Entity");
        var position = entity.getList("Pos", Tag.TAG_DOUBLE);
        if (!tag.hasUUID("Id") || !tag.hasUUID("Area") || !tag.contains("Position", Tag.TAG_LONG)
                || !entity.hasUUID("UUID") || !entity.getUUID("UUID").equals(tag.getUUID("Id"))
                || position.size() != 3 || !Double.isFinite(position.getDouble(0)) || !Double.isFinite(position.getDouble(1))
                || !Double.isFinite(position.getDouble(2))
                || !BlockPos.containing(position.getDouble(0), position.getDouble(1), position.getDouble(2)).equals(BlockPos.of(tag.getLong("Position")))
                || ItemStack.parseOptional(provider, entity.getCompound("Item")).isEmpty()) {
            throw new IllegalArgumentException("Pending item drop identity, position or item is invalid");
        }
        var drop = new SettlementItemDrop(tag.getUUID("Id"), tag.getUUID("Area"), BlockPos.of(tag.getLong("Position")),
                entity, Math.max(0L, tag.getLong("CheckedAt")));
        for (var raw : tag.getList("Access", Tag.TAG_COMPOUND)) {
            var route = (CompoundTag) raw;
            if (!route.hasUUID("Worker") || !route.contains("Start", Tag.TAG_LONG) || route.getInt("Steps") < 0
                    || route.getInt("Steps") > 4096 || drop.access.putIfAbsent(route.getUUID("Worker"),
                            new Access(route.hasUUID("Area") ? route.getUUID("Area") : drop.area(),
                                    BlockPos.of(route.getLong("Start")), route.getInt("Steps"))) != null) {
                throw new IllegalArgumentException("Pending item drop access is duplicated or invalid");
            }
        }
        return drop;
    }

    private record Access(UUID area, BlockPos start, int steps) {
    }
}
