package com.jvn.villagerretaliation.settlement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

final class SettlementGroundItem {
    private static final String VERSION_TAG = "VillagerRetaliationSettlementItemVersion";
    private final SettlementItemDrop drop;
    private net.minecraft.core.BlockPos nativePosition;
    private long version;
    private boolean removed;

    private SettlementGroundItem(SettlementItemDrop drop, long version, boolean removed) {
        this.drop = drop;
        this.nativePosition = drop.position();
        this.version = version;
        this.removed = removed;
    }

    static SettlementGroundItem capture(ItemEntity item, java.util.UUID area, long now) {
        return new SettlementGroundItem(SettlementItemDrop.create(item, area, now), version(item), false);
    }

    SettlementItemDrop drop() {
        return this.drop;
    }

    net.minecraft.core.BlockPos nativePosition() {
        return this.nativePosition;
    }

    boolean removed() {
        return this.removed;
    }

    long version() {
        return this.version;
    }

    static long version(ItemEntity item) {
        return Math.max(0L, item.getPersistentData().getLong(VERSION_TAG));
    }

    void observe(ItemEntity item, long now) {
        if (item.getItem().isEmpty()) throw new IllegalArgumentException("Cannot observe an empty ground item");
        this.drop.observe(item, now);
        this.nativePosition = item.blockPosition().immutable();
        this.version = Math.max(this.version, version(item));
        this.removed = false;
    }

    void advance(ServerLevel level, long now) {
        if (this.removed || now <= this.drop.checkedAt()) return;
        this.removed = !this.drop.advance(level, now);
        this.version++;
    }

    void consume(ItemStack remaining, net.minecraft.core.HolderLookup.Provider provider) {
        if (this.removed) throw new IllegalStateException("A removed ground item cannot be consumed twice");
        if (remaining.isEmpty()) this.removed = true;
        else this.drop.remaining(remaining, provider);
        this.version++;
    }

    boolean restore(ItemEntity item) {
        if (!this.drop.id().equals(item.getUUID())) throw new IllegalArgumentException("Ground item identity differs from its native entity");
        if (version(item) > this.version) return false;
        if (this.removed) {
            item.getPersistentData().putLong(VERSION_TAG, this.version);
            item.discard();
            return true;
        }
        if (version(item) == this.version) return false;
        item.load(this.drop.entityTag());
        item.getPersistentData().putLong(VERSION_TAG, this.version);
        return true;
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.put("Drop", this.drop.save());
        tag.putLong("NativePosition", this.nativePosition.asLong());
        tag.putLong("Version", this.version);
        tag.putBoolean("Removed", this.removed);
        return tag;
    }

    static SettlementGroundItem load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        if (tag.getLong("Version") < 0L) throw new IllegalArgumentException("Ground item version is invalid");
        var item = new SettlementGroundItem(SettlementItemDrop.load(tag.getCompound("Drop"), provider),
                tag.getLong("Version"), tag.getBoolean("Removed"));
        if (tag.contains("NativePosition", net.minecraft.nbt.Tag.TAG_LONG)) {
            item.nativePosition = net.minecraft.core.BlockPos.of(tag.getLong("NativePosition"));
        }
        return item;
    }
}
