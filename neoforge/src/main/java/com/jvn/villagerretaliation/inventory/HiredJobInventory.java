package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerEquipment;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

public final class HiredJobInventory implements Container {
    public static final int SLOT_COUNT = 27;
    public static final int MAINHAND_SLOT = 4;
    public static final int OFFHAND_SLOT = 5;
    private static final int ARMOR_SLOT_COUNT = 4;
    private static final String TAG = "VillagerRetaliationJobInventory";
    private static final String EQUIPMENT_SLOTS_MIGRATED_TAG = "VillagerRetaliationJobEquipmentSlotsMigrated";
    private static final String SLOT_TYPES_TAG = "SlotTypes";
    private static final String SLOT_TAG = "Slot";
    private static final String TYPE_TAG = "Type";
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    private final Villager villager;
    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final HiredJobInventorySlotType[] slotTypes = new HiredJobInventorySlotType[SLOT_COUNT];

    public HiredJobInventory(Villager villager) {
        this.villager = villager;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            this.slotTypes[slot] = defaultType(slot);
        }
        load();
    }

    public static boolean isJobInventoryAvailable(Villager villager) {
        return villager != null && !villager.isBaby();
    }

    public static HiredJobInventory getJobInventory(Villager villager) {
        return new HiredJobInventory(villager);
    }

    public static void migrateEquipmentSlotsIfNeeded(Villager villager) {
        if (villager.getPersistentData().getBoolean(EQUIPMENT_SLOTS_MIGRATED_TAG)) {
            return;
        }
        new HiredJobInventory(villager);
        villager.getPersistentData().putBoolean(EQUIPMENT_SLOTS_MIGRATED_TAG, true);
    }

    public static boolean isProtectedVillagerProperty(ItemStack stack) {
        return ProtectedVillagerProperty.isProtected(stack);
    }

    public static boolean canHirerRemoveFromJobInventory(ItemStack stack) {
        return !isProtectedVillagerProperty(stack);
    }

    public static ItemStack markAsProtectedVillagerProperty(ItemStack stack, Villager owner, String reason) {
        return ProtectedVillagerProperty.mark(stack, owner, reason);
    }

    public static void dropAll(Villager villager, LivingDropsEvent event) {
        HiredJobInventory inventory = new HiredJobInventory(villager);
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                com.jvn.toucanlib.neoforge.loot.ToucanLivingDrops.addDrop(event, stack.copy());
            }
        }
        inventory.clearContent();
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        for (int slot = 0; slot < getContainerSize(); slot++) {
            if (!getItem(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        EquipmentSlot equipmentSlot = equipmentSlotForJobSlot(slot);
        if (equipmentSlot != null) {
            return this.villager.getItemBySlot(equipmentSlot);
        }
        return isValidSlot(slot) ? this.items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (!isValidSlot(slot) || amount <= 0) {
            return ItemStack.EMPTY;
        }
        EquipmentSlot equipmentSlot = equipmentSlotForJobSlot(slot);
        if (equipmentSlot != null) {
            return removeEquipmentItem(equipmentSlot, amount);
        }

        ItemStack stack = this.items.get(slot);
        if (stack.isEmpty() || !canHirerRemoveFromJobInventory(stack)) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = ContainerHelper.removeItem(this.items, slot, amount);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (!isValidSlot(slot)) {
            return ItemStack.EMPTY;
        }
        EquipmentSlot equipmentSlot = equipmentSlotForJobSlot(slot);
        if (equipmentSlot != null) {
            ItemStack stack = this.villager.getItemBySlot(equipmentSlot);
            if (!canHirerRemoveFromJobInventory(stack)) {
                return ItemStack.EMPTY;
            }
            VillagerRetaliationVillagerEquipment.setInventoryEquipment(this.villager, equipmentSlot, ItemStack.EMPTY);
            return stack.copy();
        }

        ItemStack stack = this.items.get(slot);
        if (!canHirerRemoveFromJobInventory(stack)) {
            return ItemStack.EMPTY;
        }
        return ContainerHelper.takeItem(this.items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (!isValidSlot(slot)) {
            return;
        }
        EquipmentSlot equipmentSlot = equipmentSlotForJobSlot(slot);
        if (equipmentSlot != null) {
            setEquipmentItem(equipmentSlot, stack);
            return;
        }

        this.items.set(slot, stack);
        if (!stack.isEmpty() && ProtectedVillagerProperty.isProtected(stack)) {
            this.slotTypes[slot] = HiredJobInventorySlotType.PROTECTED_PROPERTY;
        } else if (this.slotTypes[slot] == HiredJobInventorySlotType.PROTECTED_PROPERTY) {
            this.slotTypes[slot] = defaultType(slot);
        }
        setChanged();
    }

    @Override
    public void setChanged() {
        save();
    }

    @Override
    public boolean stillValid(Player player) {
        return this.villager.isAlive()
                && player.isAlive()
                && !player.isSpectator()
                && player.distanceToSqr(this.villager) <= 64.0D;
    }

    @Override
    public void clearContent() {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            EquipmentSlot equipmentSlot = equipmentSlotForJobSlot(slot);
            if (equipmentSlot != null) {
                VillagerRetaliationVillagerEquipment.setInventoryEquipment(this.villager, equipmentSlot, ItemStack.EMPTY);
            }
            this.items.set(slot, ItemStack.EMPTY);
            this.slotTypes[slot] = defaultType(slot);
        }
        setChanged();
    }

    public HiredJobInventorySlotType slotType(int slot) {
        if (!isValidSlot(slot)) {
            return HiredJobInventorySlotType.NORMAL;
        }
        ItemStack stack = getItem(slot);
        return ProtectedVillagerProperty.isProtected(stack) ? HiredJobInventorySlotType.PROTECTED_PROPERTY : this.slotTypes[slot];
    }

    public boolean isOutputSlot(int slot) {
        return slotType(slot) == HiredJobInventorySlotType.OUTPUT;
    }

    public boolean hasOutputSpace() {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (isOutputSlot(slot) && this.items.get(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public List<OutputStack> collectOutputItems() {
        List<OutputStack> output = new ArrayList<>();
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = this.items.get(slot);
            if (stack.isEmpty()
                    || !isOutputSlot(slot)
                    || ProtectedVillagerProperty.isProtected(stack)
                    || slotType(slot) == HiredJobInventorySlotType.GEAR
                    || slotType(slot) == HiredJobInventorySlotType.SUPPLY) {
                continue;
            }
            output.add(new OutputStack(slot, stack.copy()));
        }
        return output;
    }

    public boolean depositOutputToAssignedStorage() {
        if (this.collectOutputItems().isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (OutputStack output : collectOutputItems()) {
            ItemStack remainder = AssignedStorageService.depositStack(this.villager, output.stack());
            int moved = output.stack().getCount() - remainder.getCount();
            if (moved <= 0) {
                continue;
            }
            ItemStack current = this.items.get(output.slot());
            current.shrink(moved);
            if (current.isEmpty()) {
                this.items.set(output.slot(), ItemStack.EMPTY);
            }
            changed = true;
        }
        if (changed) {
            setChanged();
        }
        return changed;
    }

    private void load() {
        CompoundTag tag = this.villager.getPersistentData().getCompound(TAG);
        if (tag.isEmpty()) {
            return;
        }
        ContainerHelper.loadAllItems(tag, this.items, this.villager.level().registryAccess());
        ListTag slotTypesTag = tag.getList(SLOT_TYPES_TAG, Tag.TAG_COMPOUND);
        for (Tag rawType : slotTypesTag) {
            if (!(rawType instanceof CompoundTag typeTag)) {
                continue;
            }
            int slot = typeTag.getInt(SLOT_TAG);
            if (isValidSlot(slot)) {
                this.slotTypes[slot] = HiredJobInventorySlotType.byId(typeTag.getString(TYPE_TAG));
            }
        }
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (equipmentSlotForJobSlot(slot) != null) {
                this.slotTypes[slot] = defaultType(slot);
                continue;
            }
            if (ProtectedVillagerProperty.isProtected(this.items.get(slot))) {
                this.slotTypes[slot] = HiredJobInventorySlotType.PROTECTED_PROPERTY;
            }
        }
        migrateStoredEquipmentSlots();
    }

    private void save() {
        CompoundTag tag = ContainerHelper.saveAllItems(new CompoundTag(), this.items, true, this.villager.level().registryAccess());
        ListTag slotTypesTag = new ListTag();
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (equipmentSlotForJobSlot(slot) != null) {
                continue;
            }
            HiredJobInventorySlotType type = slotType(slot);
            if (type == defaultType(slot) && type != HiredJobInventorySlotType.PROTECTED_PROPERTY) {
                continue;
            }
            CompoundTag typeTag = new CompoundTag();
            typeTag.putInt(SLOT_TAG, slot);
            typeTag.putString(TYPE_TAG, type.id());
            slotTypesTag.add(typeTag);
        }
        tag.put(SLOT_TYPES_TAG, slotTypesTag);
        this.villager.getPersistentData().put(TAG, tag);
    }

    private void migrateStoredEquipmentSlots() {
        boolean changed = false;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            EquipmentSlot equipmentSlot = equipmentSlotForJobSlot(slot);
            if (equipmentSlot == null) {
                continue;
            }

            ItemStack storedStack = this.items.get(slot);
            if (storedStack.isEmpty()) {
                continue;
            }
            setEquipmentItem(equipmentSlot, storedStack);
            this.items.set(slot, ItemStack.EMPTY);
            this.slotTypes[slot] = defaultType(slot);
            changed = true;
        }
        if (changed) {
            save();
        }
    }

    private ItemStack removeEquipmentItem(EquipmentSlot equipmentSlot, int amount) {
        ItemStack current = this.villager.getItemBySlot(equipmentSlot);
        if (current.isEmpty() || !canHirerRemoveFromJobInventory(current)) {
            return ItemStack.EMPTY;
        }

        ItemStack remaining = current.copy();
        ItemStack removed;
        if (amount >= remaining.getCount()) {
            removed = remaining.copy();
            remaining = ItemStack.EMPTY;
        } else {
            removed = remaining.split(amount);
        }
        VillagerRetaliationVillagerEquipment.setInventoryEquipment(this.villager, equipmentSlot, remaining);
        return removed;
    }

    private void setEquipmentItem(EquipmentSlot equipmentSlot, ItemStack stack) {
        ItemStack current = this.villager.getItemBySlot(equipmentSlot);
        if (!current.isEmpty() && !stack.isEmpty() && !sameStack(current, stack)) {
            storeDisplacedEquipment(current);
        }
        VillagerRetaliationVillagerEquipment.setInventoryEquipment(this.villager, equipmentSlot, stack);
    }

    private void storeDisplacedEquipment(ItemStack stack) {
        ItemStack remainder = VillagerInventoryContainer.addItem(this.villager, stack.copy());
        if (!remainder.isEmpty()) {
            remainder = AssignedStorageService.depositStack(this.villager, remainder);
        }
        if (!remainder.isEmpty()) {
            this.villager.spawnAtLocation(remainder);
        }
    }

    private static boolean isValidSlot(int slot) {
        return slot >= 0 && slot < SLOT_COUNT;
    }

    private static EquipmentSlot equipmentSlotForJobSlot(int slot) {
        if (slot >= 0 && slot < ARMOR_SLOT_COUNT) {
            return ARMOR_SLOTS[slot];
        }
        if (slot == MAINHAND_SLOT) {
            return EquipmentSlot.MAINHAND;
        }
        if (slot == OFFHAND_SLOT) {
            return EquipmentSlot.OFFHAND;
        }
        return null;
    }

    private static HiredJobInventorySlotType defaultType(int slot) {
        if (slot < 9) {
            return HiredJobInventorySlotType.GEAR;
        }
        if (slot < 18) {
            return HiredJobInventorySlotType.SUPPLY;
        }
        return HiredJobInventorySlotType.OUTPUT;
    }

    private static boolean sameStack(ItemStack first, ItemStack second) {
        return first.getCount() == second.getCount() && ItemStack.isSameItemSameComponents(first, second);
    }

    public record OutputStack(int slot, ItemStack stack) {
    }
}
