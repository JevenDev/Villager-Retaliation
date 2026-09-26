package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredVillagerRoles;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.inventory.HiredJobInventorySlotType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

public final class SettlementWorkerState {
    private static final String VERSION_TAG = "VillagerRetaliationSettlementWorkVersion";
    private final UUID id;
    private UUID area;
    private HiredVillagerRole role;
    private BlockPos position;
    private CompoundTag inventory = new CompoundTag();
    private CompoundTag settings = new CompoundTag();
    private long checkedAt;
    private long version;
    private double progress;
    private int aptitude;
    private String status = "idle";

    private SettlementWorkerState(UUID id) {
        this.id = id;
    }

    public UUID id() {
        return this.id;
    }

    public UUID area() {
        return this.area;
    }

    public HiredVillagerRole role() {
        return this.role;
    }

    public BlockPos position() {
        return this.position;
    }

    public int aptitude() {
        return this.aptitude;
    }

    public String status() {
        return this.status;
    }

    public long version() {
        return this.version;
    }

    public double progress() {
        return this.progress;
    }

    public CompoundTag inventory() {
        return this.inventory.copy();
    }

    CompoundTag settings() {
        return this.settings;
    }

    static SettlementWorkerState capture(ServerLevel level, Villager villager, SettlementWorkforceService.Duty duty, long now) {
        var state = new SettlementWorkerState(villager.getUUID());
        state.observe(level, villager, duty, now);
        return state;
    }

    void observe(ServerLevel level, Villager villager, SettlementWorkforceService.Duty duty, long now) {
        this.area = duty.area().id();
        this.role = duty.area().type().role();
        this.position = villager.blockPosition().immutable();
        this.inventory = HiredJobInventory.getJobInventory(villager).captureSnapshot();
        this.settings = HiredWorkStateStore.state(villager).copy();
        this.aptitude = HiredVillagerRoles.roleScore(level, villager, this.role);
        this.version = Math.max(this.version, villager.getPersistentData().getLong(VERSION_TAG));
        this.checkedAt = now;
        this.progress = 0.0D;
        this.status = "active";
    }

    boolean restore(Villager villager) {
        if (villager.getPersistentData().getLong(VERSION_TAG) >= this.version) {
            return false;
        }
        HiredJobInventory.getJobInventory(villager).restoreSnapshot(this.inventory);
        villager.getPersistentData().putLong(VERSION_TAG, this.version);
        if (this.settings.contains("CraftsmanCursor", Tag.TAG_INT)) {
            HiredWorkStateStore.state(villager).putInt("CraftsmanCursor", this.settings.getInt("CraftsmanCursor"));
        }
        HiredWorkSession.invalidate(villager);
        return true;
    }

    int elapsed(long now) {
        int elapsed = (int) Math.clamp(now - this.checkedAt, 0L, 200L);
        this.checkedAt = now;
        return elapsed;
    }

    void work(int elapsed, double multiplier) {
        this.progress = Math.min(24_000.0D, this.progress + elapsed * multiplier);
    }

    boolean spend(double cost) {
        if (cost <= 0.0D || this.progress < cost) {
            return false;
        }
        this.progress -= cost;
        this.version++;
        return true;
    }

    void status(String status) {
        this.status = status;
    }

    NonNullList<ItemStack> items(HolderLookup.Provider provider) {
        var items = NonNullList.withSize(HiredJobInventory.SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(this.inventory, items, provider);
        return items;
    }

    void updateInventory(HolderLookup.Provider provider, NonNullList<ItemStack> items) {
        ContainerHelper.saveAllItems(this.inventory, items, true, provider);
    }

    List<Integer> outputSlots() {
        return slots(HiredJobInventorySlotType.OUTPUT);
    }

    List<Integer> supplySlots() {
        return slots(HiredJobInventorySlotType.SUPPLY);
    }

    private List<Integer> slots(HiredJobInventorySlotType type) {
        boolean[] outputs = new boolean[HiredJobInventory.SLOT_COUNT];
        if (type == HiredJobInventorySlotType.OUTPUT) {
            java.util.Arrays.fill(outputs, HiredJobInventory.MAIN_GRID_START, HiredJobInventory.HOTBAR_START, true);
        } else if (type == HiredJobInventorySlotType.SUPPLY) {
            java.util.Arrays.fill(outputs, HiredJobInventory.HOTBAR_START, HiredJobInventory.FILTER_SLOT, true);
        }
        for (Tag raw : this.inventory.getList("SlotTypes", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) raw;
            int slot = tag.getInt("Slot");
            if (slot >= HiredJobInventory.MAIN_GRID_START && slot < HiredJobInventory.FILTER_SLOT) {
                outputs[slot] = type.id().equals(tag.getString("Type"));
            }
        }
        List<Integer> result = new ArrayList<>();
        for (int slot = HiredJobInventory.MAIN_GRID_START; slot < HiredJobInventory.FILTER_SLOT; slot++) {
            if (outputs[slot]) {
                result.add(slot);
            }
        }
        return List.copyOf(result);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", this.id);
        tag.putUUID("Area", this.area);
        tag.putString("Role", this.role.name());
        tag.putLong("Position", this.position.asLong());
        tag.put("Inventory", this.inventory.copy());
        tag.put("Settings", this.settings.copy());
        tag.putLong("CheckedAt", this.checkedAt);
        tag.putLong("Version", this.version);
        tag.putDouble("Progress", this.progress);
        tag.putInt("Aptitude", this.aptitude);
        tag.putString("Status", this.status);
        return tag;
    }

    static SettlementWorkerState load(CompoundTag tag) {
        if (!tag.hasUUID("Id") || !tag.hasUUID("Area")) {
            throw new IllegalArgumentException("Background worker requires a resident and workplace");
        }
        var state = new SettlementWorkerState(tag.getUUID("Id"));
        state.area = tag.getUUID("Area");
        state.role = HiredVillagerRole.valueOf(tag.getString("Role"));
        state.position = BlockPos.of(tag.getLong("Position"));
        state.inventory = tag.getCompound("Inventory").copy();
        state.settings = tag.getCompound("Settings").copy();
        state.checkedAt = Math.max(0L, tag.getLong("CheckedAt"));
        state.version = Math.max(0L, tag.getLong("Version"));
        state.progress = Math.clamp(tag.getDouble("Progress"), 0.0D, 24_000.0D);
        state.aptitude = Math.clamp(tag.getInt("Aptitude"), 0, 100);
        state.status = tag.getString("Status");
        return state;
    }
}
