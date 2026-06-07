package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerEquipment;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.BlockPos;
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
    public static final int SLOT_COUNT = 33;
    public static final int MAINHAND_SLOT = 4;
    public static final int OFFHAND_SLOT = 5;
    private static final int ARMOR_SLOT_COUNT = 4;
    private static final String TAG = "VillagerRetaliationJobInventory";
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

    public static void maintainEquipmentSlots(Villager villager) {
        new HiredJobInventory(villager).maintainEquipmentAuthority();
    }

    public static boolean hasJobEquipmentForSlot(Villager villager, EquipmentSlot equipmentSlot) {
        return !new HiredJobInventory(villager).jobEquipmentItem(equipmentSlot).isEmpty();
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

    public void refreshFromVillager() {
        load();
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
        return isValidSlot(slot) ? this.items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (!isValidSlot(slot) || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = this.items.get(slot);
        if (stack.isEmpty() || !canHirerRemoveFromJobInventory(stack)) {
            return ItemStack.EMPTY;
        }
        ItemStack previousStack = stack.copy();
        ItemStack removed = ContainerHelper.removeItem(this.items, slot, amount);
        if (!removed.isEmpty()) {
            EquipmentSlot equipmentSlot = equipmentSlotForJobSlot(slot);
            if (equipmentSlot != null) {
                updateLiveEquipmentAfterJobSlotChange(equipmentSlot, previousStack);
            }
            resetEmptySlotType(slot);
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (!isValidSlot(slot)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = this.items.get(slot);
        if (!canHirerRemoveFromJobInventory(stack)) {
            return ItemStack.EMPTY;
        }
        ItemStack previousStack = stack.copy();
        ItemStack removed = ContainerHelper.takeItem(this.items, slot);
        EquipmentSlot equipmentSlot = equipmentSlotForJobSlot(slot);
        if (equipmentSlot != null && !removed.isEmpty()) {
            updateLiveEquipmentAfterJobSlotChange(equipmentSlot, previousStack);
            setChanged();
        } else if (!removed.isEmpty()) {
            resetEmptySlotType(slot);
            setChanged();
        }
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (!isValidSlot(slot)) {
            return;
        }
        EquipmentSlot equipmentSlot = equipmentSlotForJobSlot(slot);
        if (equipmentSlot != null) {
            setEquipmentItem(slot, equipmentSlot, stack);
            return;
        }

        this.items.set(slot, stack);
        if (stack.isEmpty()) {
            this.slotTypes[slot] = defaultType(slot);
        } else if (ProtectedVillagerProperty.isProtected(stack)) {
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
                clearLiveEquipmentIfOwnedByJob(equipmentSlot, this.items.get(slot));
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

    public boolean isSupplySlot(int slot) {
        return slotType(slot) == HiredJobInventorySlotType.SUPPLY;
    }

    public List<Integer> supplySlots() {
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (isSupplySlot(slot)) {
                slots.add(slot);
            }
        }
        return slots;
    }

    public List<Integer> outputSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (isOutputSlot(slot)) {
                slots.add(slot);
            }
        }
        return slots;
    }

    public boolean hasOutput(Predicate<ItemStack> predicate) {
        Predicate<ItemStack> safePredicate = predicate == null ? ignored -> true : predicate;
        for (int slot : outputSlots()) {
            ItemStack stack = this.items.get(slot);
            if (!stack.isEmpty()
                    && !ProtectedVillagerProperty.isProtected(stack)
                    && safePredicate.test(stack)) {
                return true;
            }
        }
        return false;
    }

    public ItemStack consumeOutput(Predicate<ItemStack> predicate, int count) {
        int remaining = Math.max(0, count);
        if (remaining <= 0) {
            return ItemStack.EMPTY;
        }
        Predicate<ItemStack> safePredicate = predicate == null ? ignored -> true : predicate;
        for (int slot : outputSlots()) {
            ItemStack stack = this.items.get(slot);
            if (stack.isEmpty()
                    || ProtectedVillagerProperty.isProtected(stack)
                    || !safePredicate.test(stack)) {
                continue;
            }
            int removed = Math.min(remaining, stack.getCount());
            ItemStack consumed = stack.copyWithCount(removed);
            stack.shrink(removed);
            if (stack.isEmpty()) {
                this.items.set(slot, ItemStack.EMPTY);
                resetEmptySlotType(slot);
            }
            setChanged();
            return consumed;
        }
        return ItemStack.EMPTY;
    }

    public ItemStack findSupply(Predicate<ItemStack> predicate) {
        for (int slot : supplySlots()) {
            ItemStack stack = this.items.get(slot);
            if (!stack.isEmpty() && predicate.test(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public ItemStack findTool(Predicate<ItemStack> predicate) {
        ItemStack mainhand = getItem(MAINHAND_SLOT);
        if (!mainhand.isEmpty() && predicate.test(mainhand)) {
            return mainhand;
        }
        return findSupply(predicate);
    }

    public ItemStack equipBestTool(Predicate<ItemStack> predicate, ToDoubleFunction<ItemStack> scorer) {
        int bestSlot = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (slot != MAINHAND_SLOT && !isSupplySlot(slot)) {
                continue;
            }
            ItemStack stack = this.items.get(slot);
            if (stack.isEmpty() || !predicate.test(stack)) {
                continue;
            }
            double score = scorer.applyAsDouble(stack);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }
        if (bestSlot < 0) {
            return ItemStack.EMPTY;
        }
        if (bestSlot == MAINHAND_SLOT) {
            return this.items.get(MAINHAND_SLOT);
        }
        ItemStack selected = this.items.get(bestSlot);
        ItemStack previousMainhand = this.items.get(MAINHAND_SLOT);
        this.items.set(MAINHAND_SLOT, selected);
        this.items.set(bestSlot, previousMainhand);
        VillagerRetaliationVillagerEquipment.setInventoryEquipment(this.villager, EquipmentSlot.MAINHAND, selected);
        setChanged();
        return selected;
    }

    public void syncMainHandEquipment() {
        VillagerRetaliationVillagerEquipment.setInventoryEquipment(this.villager, EquipmentSlot.MAINHAND, this.items.get(MAINHAND_SLOT));
        setChanged();
    }

    public int consumeSupply(Predicate<ItemStack> predicate, int count) {
        int remaining = Math.max(0, count);
        if (remaining <= 0) {
            return 0;
        }
        int consumed = 0;
        for (int slot : supplySlots()) {
            ItemStack stack = this.items.get(slot);
            if (stack.isEmpty() || !predicate.test(stack)) {
                continue;
            }
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            if (stack.isEmpty()) {
                this.items.set(slot, ItemStack.EMPTY);
                resetEmptySlotType(slot);
            }
            remaining -= removed;
            consumed += removed;
            if (remaining <= 0) {
                setChanged();
                return consumed;
            }
        }
        if (consumed > 0) {
            setChanged();
        }
        return consumed;
    }

    public ItemStack insertOutput(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = stack.copy();
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (!canInsertOutputIntoSlot(this.items, slot)) {
                continue;
            }
            ItemStack current = this.items.get(slot);
            if (current.isEmpty()) {
                int moved = Math.min(remainder.getCount(), remainder.getMaxStackSize());
                this.items.set(slot, remainder.copyWithCount(moved));
                this.slotTypes[slot] = HiredJobInventorySlotType.OUTPUT;
                remainder.shrink(moved);
            } else if (ItemStack.isSameItemSameComponents(current, remainder)
                    && current.getCount() < current.getMaxStackSize()) {
                int moved = Math.min(remainder.getCount(), current.getMaxStackSize() - current.getCount());
                current.grow(moved);
                remainder.shrink(moved);
            }
            if (remainder.isEmpty()) {
                setChanged();
                return ItemStack.EMPTY;
            }
        }
        if (remainder.getCount() != stack.getCount()) {
            setChanged();
        }
        return remainder;
    }

    public boolean hasOutputSpace() {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (canInsertOutputIntoSlot(this.items, slot)
                    && this.items.get(slot).getCount() < this.items.get(slot).getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    public boolean canStoreOutputs(List<ItemStack> stacks) {
        NonNullList<ItemStack> simulated = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            simulated.set(slot, this.items.get(slot).copy());
        }
        for (ItemStack stack : stacks) {
            if (!simulateOutputInsert(simulated, stack.copy()).isEmpty()) {
                return false;
            }
        }
        return true;
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

    public boolean hasOutputItems() {
        return !collectOutputItems().isEmpty();
    }

    public boolean depositOutputToAssignedStorage() {
        return depositOneOutputStack(output -> AssignedStorageService.depositStack(this.villager, output.stack()));
    }

    public boolean depositOutputToNearbyAssignedStorage() {
        return depositOutputToNearbyAssignedStorage(ignored -> true);
    }

    public boolean depositOutputToNearbyAssignedStorage(Predicate<BlockPos> positionFilter) {
        Predicate<BlockPos> safeFilter = positionFilter == null ? ignored -> true : positionFilter;
        return depositOneOutputStack(output -> AssignedStorageService.depositStackNearVillager(
                this.villager,
                output.stack(),
                safeFilter));
    }

    public boolean depositOutputToAssignedStorageAt(BlockPos storagePos) {
        if (storagePos == null) {
            return false;
        }
        return depositOneOutputStack(output -> AssignedStorageService.depositStackAtAssignedStorage(
                this.villager,
                storagePos,
                output.stack()));
    }

    private boolean depositOneOutputStack(Function<OutputStack, ItemStack> depositor) {
        for (OutputStack output : collectOutputItems()) {
            ItemStack remainder = depositor.apply(output);
            int moved = output.stack().getCount() - remainder.getCount();
            if (moved <= 0) {
                continue;
            }
            ItemStack current = this.items.get(output.slot());
            current.shrink(moved);
            if (current.isEmpty()) {
                this.items.set(output.slot(), ItemStack.EMPTY);
                resetEmptySlotType(output.slot());
            }
            setChanged();
            return true;
        }
        return false;
    }

    private ItemStack simulateOutputInsert(NonNullList<ItemStack> simulated, ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = stack.copy();
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (!canInsertOutputIntoSlot(simulated, slot)) {
                continue;
            }
            ItemStack current = simulated.get(slot);
            if (current.isEmpty()) {
                int moved = Math.min(remainder.getCount(), remainder.getMaxStackSize());
                simulated.set(slot, remainder.copyWithCount(moved));
                remainder.shrink(moved);
            } else if (ItemStack.isSameItemSameComponents(current, remainder)
                    && current.getCount() < current.getMaxStackSize()) {
                int moved = Math.min(remainder.getCount(), current.getMaxStackSize() - current.getCount());
                current.grow(moved);
                remainder.shrink(moved);
            }
            if (remainder.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }
        return remainder;
    }

    private boolean canInsertOutputIntoSlot(NonNullList<ItemStack> inventory, int slot) {
        if (!isValidSlot(slot) || ProtectedVillagerProperty.isProtected(inventory.get(slot))) {
            return false;
        }
        HiredJobInventorySlotType type = slotType(slot);
        return type == HiredJobInventorySlotType.OUTPUT
                || type == HiredJobInventorySlotType.SUPPLY && this.items.get(slot).isEmpty();
    }

    private void resetEmptySlotType(int slot) {
        if (isValidSlot(slot) && this.items.get(slot).isEmpty()) {
            this.slotTypes[slot] = defaultType(slot);
        }
    }

    private void load() {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            this.items.set(slot, ItemStack.EMPTY);
            this.slotTypes[slot] = defaultType(slot);
        }
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
        reconcileStoredEquipmentWithLiveItems();
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

    private void reconcileStoredEquipmentWithLiveItems() {
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
            ItemStack liveStack = this.villager.getItemBySlot(equipmentSlot);
            if (!liveStack.isEmpty() && ItemStack.isSameItem(liveStack, storedStack) && !sameStack(liveStack, storedStack)) {
                this.items.set(slot, liveStack.copy());
                changed = true;
            }
        }
        if (changed) {
            save();
        }
    }

    private void setEquipmentItem(int slot, EquipmentSlot equipmentSlot, ItemStack stack) {
        ItemStack previousJobStack = this.items.get(slot).copy();
        ItemStack current = this.villager.getItemBySlot(equipmentSlot);
        if (stack.isEmpty()) {
            this.items.set(slot, ItemStack.EMPTY);
            if (this.slotTypes[slot] == HiredJobInventorySlotType.PROTECTED_PROPERTY) {
                this.slotTypes[slot] = defaultType(slot);
            }
            clearLiveEquipmentIfOwnedByJob(equipmentSlot, previousJobStack);
            setChanged();
            return;
        }
        if (!current.isEmpty()
                && !isCurrentJobEquipment(current, previousJobStack)) {
            storeDisplacedEquipment(current);
        }
        this.items.set(slot, stack);
        if (!stack.isEmpty() && ProtectedVillagerProperty.isProtected(stack)) {
            this.slotTypes[slot] = HiredJobInventorySlotType.PROTECTED_PROPERTY;
        } else if (this.slotTypes[slot] == HiredJobInventorySlotType.PROTECTED_PROPERTY) {
            this.slotTypes[slot] = defaultType(slot);
        }
        VillagerRetaliationVillagerEquipment.setInventoryEquipment(this.villager, equipmentSlot, stack);
        setChanged();
    }

    private void updateLiveEquipmentAfterJobSlotChange(EquipmentSlot equipmentSlot, ItemStack previousJobStack) {
        ItemStack remainingJobStack = jobEquipmentItem(equipmentSlot);
        if (!remainingJobStack.isEmpty()) {
            VillagerRetaliationVillagerEquipment.setInventoryEquipment(this.villager, equipmentSlot, remainingJobStack);
            return;
        }
        clearLiveEquipmentIfOwnedByJob(equipmentSlot, previousJobStack);
    }

    private void clearLiveEquipmentIfOwnedByJob(EquipmentSlot equipmentSlot, ItemStack previousJobStack) {
        ItemStack current = this.villager.getItemBySlot(equipmentSlot);
        if (current.isEmpty() || isCurrentJobEquipment(current, previousJobStack)) {
            VillagerRetaliationVillagerEquipment.setInventoryEquipment(this.villager, equipmentSlot, ItemStack.EMPTY);
        }
    }

    private boolean maintainEquipmentAuthority() {
        boolean changed = false;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            EquipmentSlot equipmentSlot = equipmentSlotForJobSlot(slot);
            if (equipmentSlot == null) {
                continue;
            }

            ItemStack jobStack = this.items.get(slot);
            if (jobStack.isEmpty()) {
                continue;
            }

            ItemStack current = this.villager.getItemBySlot(equipmentSlot);
            if (current.isEmpty()) {
                VillagerRetaliationVillagerEquipment.setInventoryEquipment(this.villager, equipmentSlot, jobStack);
                continue;
            }
            if (ItemStack.isSameItem(current, jobStack)) {
                if (!sameStack(current, jobStack)) {
                    this.items.set(slot, current.copy());
                    changed = true;
                }
                continue;
            }

            storeDisplacedEquipment(current);
            VillagerRetaliationVillagerEquipment.setInventoryEquipment(this.villager, equipmentSlot, jobStack);
        }
        if (changed) {
            save();
        }
        return changed;
    }

    private ItemStack jobEquipmentItem(EquipmentSlot equipmentSlot) {
        int slot = jobSlotForEquipmentSlot(equipmentSlot);
        return slot >= 0 ? this.items.get(slot) : ItemStack.EMPTY;
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

    private static int jobSlotForEquipmentSlot(EquipmentSlot equipmentSlot) {
        for (int slot = 0; slot < ARMOR_SLOTS.length; slot++) {
            if (ARMOR_SLOTS[slot] == equipmentSlot) {
                return slot;
            }
        }
        if (equipmentSlot == EquipmentSlot.MAINHAND) {
            return MAINHAND_SLOT;
        }
        if (equipmentSlot == EquipmentSlot.OFFHAND) {
            return OFFHAND_SLOT;
        }
        return -1;
    }

    private static HiredJobInventorySlotType defaultType(int slot) {
        if (equipmentSlotForJobSlot(slot) != null) {
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

    private static boolean isCurrentJobEquipment(ItemStack current, ItemStack jobStack) {
        return !jobStack.isEmpty() && ItemStack.isSameItem(current, jobStack);
    }

    public record OutputStack(int slot, ItemStack stack) {
    }
}
