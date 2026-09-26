package com.jvn.villagerretaliation.settlement;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

public final class SettlementDepot extends SimpleContainer {
    public static final int SLOT_COUNT = 54;
    private final UUID id;
    private final UUID area;
    private final BlockPos position;
    private final Runnable dirty;
    private SettlementDepotPurpose purpose;
    private int priority;
    private boolean loading;

    SettlementDepot(UUID id, UUID area, BlockPos position, SettlementDepotPurpose purpose, int priority, Runnable dirty) {
        super(SLOT_COUNT);
        this.id = Objects.requireNonNull(id, "id");
        this.area = Objects.requireNonNull(area, "area");
        this.position = Objects.requireNonNull(position, "position").immutable();
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        this.dirty = Objects.requireNonNull(dirty, "dirty");
        if (priority < 0 || priority > 9) {
            throw new IllegalArgumentException("Depot priority must be between zero and nine");
        }
        this.priority = priority;
    }

    public UUID id() {
        return this.id;
    }

    public UUID area() {
        return this.area;
    }

    public BlockPos position() {
        return this.position;
    }

    public SettlementDepotPurpose purpose() {
        return this.purpose;
    }

    public int priority() {
        return this.priority;
    }

    boolean configure(SettlementDepotPurpose purpose, int priority) {
        if (purpose == null || priority < 0 || priority > 9) {
            return false;
        }
        if (this.purpose != purpose || this.priority != priority) {
            this.purpose = purpose;
            this.priority = priority;
            setChanged();
        }
        return true;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot >= 0 && slot < SLOT_COUNT && this.purpose.accepts(stack);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (!this.loading && this.dirty != null) {
            this.dirty.run();
        }
    }

    public ItemStack deposit(ItemStack stack) {
        return this.purpose.accepts(stack) ? addItem(stack.copy()) : stack.copy();
    }

    public ItemStack withdraw(Predicate<ItemStack> filter, int count) {
        if (count <= 0) {
            return ItemStack.EMPTY;
        }
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = getItem(slot);
            if (!stack.isEmpty() && filter.test(stack)) {
                return removeItem(slot, Math.min(count, stack.getMaxStackSize()));
            }
        }
        return ItemStack.EMPTY;
    }

    public int count(Predicate<ItemStack> filter) {
        int count = 0;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = getItem(slot);
            if (!stack.isEmpty() && filter.test(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    CompoundTag save(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", this.id);
        tag.putUUID("Area", this.area);
        tag.putLong("Position", this.position.asLong());
        tag.putString("Purpose", this.purpose.name());
        tag.putInt("Priority", this.priority);
        ListTag items = new ListTag();
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = getItem(slot);
            if (!stack.isEmpty()) {
                CompoundTag entry = new CompoundTag();
                entry.putInt("Slot", slot);
                entry.put("Stack", stack.save(provider));
                items.add(entry);
            }
        }
        tag.put("Items", items);
        return tag;
    }

    static SettlementDepot load(CompoundTag tag, HolderLookup.Provider provider, Runnable dirty) {
        if (!tag.hasUUID("Id") || !tag.hasUUID("Area") || !tag.contains("Position", Tag.TAG_LONG)) {
            throw new IllegalArgumentException("Depot identity, area or position is missing");
        }
        SettlementDepot depot = new SettlementDepot(tag.getUUID("Id"), tag.getUUID("Area"),
                BlockPos.of(tag.getLong("Position")), SettlementDepotPurpose.valueOf(tag.getString("Purpose")),
                tag.getInt("Priority"), dirty);
        depot.loading = true;
        Set<Integer> slots = new HashSet<>();
        for (Tag raw : tag.getList("Items", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            int slot = entry.getInt("Slot");
            ItemStack stack = ItemStack.parseOptional(provider, entry.getCompound("Stack"));
            if (slot < 0 || slot >= SLOT_COUNT || !slots.add(slot) || stack.getCount() > stack.getMaxStackSize()) {
                throw new IllegalArgumentException("Depot item slot is invalid, duplicated or over capacity");
            }
            depot.setItem(slot, stack);
        }
        depot.loading = false;
        return depot;
    }
}
