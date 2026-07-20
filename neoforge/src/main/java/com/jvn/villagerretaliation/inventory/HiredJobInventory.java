package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerEquipment;
import com.jvn.villagerretaliation.villager.VillagerRecoveryService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

public final class HiredJobInventory implements Container {
    public static final int MAINHAND_SLOT = 4;
    public static final int OFFHAND_SLOT = 5;
    public static final int MAIN_GRID_START = OFFHAND_SLOT + 1;
    public static final int MAIN_GRID_SLOT_COUNT = 27;
    public static final int HOTBAR_START = MAIN_GRID_START + MAIN_GRID_SLOT_COUNT;
    public static final int HOTBAR_SLOT_COUNT = 9;
    public static final int FILTER_SLOT = HOTBAR_START + HOTBAR_SLOT_COUNT;
    public static final int PARTY_SLOT_COUNT = FILTER_SLOT;
    public static final int SLOT_COUNT = FILTER_SLOT + 1;
    private static final int ARMOR_SLOT_COUNT = 4;
    private static final int LEGACY_FILTER_SLOT = 33;
    private static final int LEGACY_SUPPLY_SLOT_END = 18;
    private static final int CURRENT_LAYOUT_VERSION = 2;
    private static final String TAG = "VillagerRetaliationJobInventory";
    private static final String ITEMS_TAG = "Items";
    private static final String SLOT_TYPES_TAG = "SlotTypes";
    private static final String SLOT_TAG = "Slot";
    private static final String TYPE_TAG = "Type";
    private static final String LAYOUT_VERSION_TAG = "LayoutVersion";
    private static final String JOB_ITEM_TAG = "VillagerRetaliationJobItem";
    private static final String JOB_ITEM_KIND_TAG = "Kind";
    private static final String JOB_ITEM_SOURCE_TAG = "Source";
    private static final String JOB_ITEM_CONTRACT_ID_TAG = "ContractId";
    private static final String JOB_ITEM_SOURCE_STORAGE = "assigned_storage";
    private static final String JOB_ITEM_SOURCE_CONTRACT = "contract";
    private static final String JOB_ITEM_KIND_SUPPLY = "supply";
    private static final String JOB_ITEM_KIND_TOOL = "tool";
    private static final long EQUIPMENT_MAINTENANCE_INTERVAL_TICKS = 20L;
    private static final Map<UUID, Long> NEXT_EQUIPMENT_MAINTENANCE_TICKS = new HashMap<>();
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };
    private static final int[] TOOL_SLOT_SEARCH_ORDER = createToolSlotSearchOrder();

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
        return ((HiredJobInventoryHolder) villager).villagerretaliation$getOrCreateJobInventory();
    }

    public static void maintainEquipmentSlots(Villager villager) {
        if (villager == null || VillagerRecoveryService.isForcingRecovery(villager)) {
            return;
        }
        CompoundTag tag = jobInventoryTag(villager);
        if (tag.isEmpty()) {
            return;
        }
        if (!hasPersistedItemStacks(tag) && tag.getList(SLOT_TYPES_TAG, Tag.TAG_COMPOUND).isEmpty()) {
            villager.getPersistentData().remove(TAG);
            return;
        }
        if (!hasPersistedJobEquipment(tag)) {
            return;
        }
        if (!shouldMaintainEquipmentAuthority(villager)) {
            return;
        }
        getJobInventory(villager).maintainEquipmentAuthority();
    }

    private static boolean shouldMaintainEquipmentAuthority(Villager villager) {
        UUID villagerId = villager.getUUID();
        long gameTime = villager.level().getGameTime();
        Long nextTick = NEXT_EQUIPMENT_MAINTENANCE_TICKS.get(villagerId);
        if (nextTick != null && gameTime < nextTick) {
            return false;
        }
        NEXT_EQUIPMENT_MAINTENANCE_TICKS.put(
                villagerId,
                gameTime + Math.max(1L, EQUIPMENT_MAINTENANCE_INTERVAL_TICKS));
        return true;
    }

    public static void clearRuntimeState() {
        NEXT_EQUIPMENT_MAINTENANCE_TICKS.clear();
    }

    public static void clearRuntimeState(Villager villager) {
        if (villager != null) {
            NEXT_EQUIPMENT_MAINTENANCE_TICKS.remove(villager.getUUID());
            ((HiredJobInventoryHolder) villager).villagerretaliation$clearJobInventory();
        }
    }

    Villager villager() {
        return this.villager;
    }

    public static void clearInheritedStateForNewborn(Villager child) {
        if (child == null) return;
        child.getPersistentData().remove(TAG);
        clearRuntimeState(child);
    }

    public static boolean hasJobEquipmentForSlot(Villager villager, EquipmentSlot equipmentSlot) {
        if (villager == null || equipmentSlot == null) {
            return false;
        }
        int slot = jobSlotForEquipmentSlot(equipmentSlot);
        return slot >= 0 && hasPersistedJobStack(villager, slot);
    }

    static boolean consumeEquippedTotem(Villager villager, EquipmentSlot equipmentSlot, ItemStack usedTotem) {
        if (villager == null || equipmentSlot == null || usedTotem == null
                || !usedTotem.is(Items.TOTEM_OF_UNDYING)
                || !isJobInventoryAvailable(villager)) {
            return false;
        }
        int slot = jobSlotForEquipmentSlot(equipmentSlot);
        if (slot < 0 || !hasPersistedJobStack(villager, slot)) {
            return false;
        }

        HiredJobInventory inventory = getJobInventory(villager);
        ItemStack authoritativeStack = inventory.items.get(slot);
        if (!ItemStack.isSameItemSameComponents(authoritativeStack, usedTotem)) {
            return false;
        }
        authoritativeStack.shrink(1);
        if (authoritativeStack.isEmpty()) {
            inventory.items.set(slot, ItemStack.EMPTY);
            inventory.slotTypes[slot] = defaultType(slot);
        }
        inventory.save();
        return true;
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

    public static boolean isJobItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return !customData.isEmpty() && customData.contains(JOB_ITEM_TAG);
    }

    public static Optional<UUID> jobItemContractId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (customData.isEmpty() || !customData.contains(JOB_ITEM_TAG)) {
            return Optional.empty();
        }
        CompoundTag jobItemTag = customData.copyTag().getCompound(JOB_ITEM_TAG);
        return jobItemTag.hasUUID(JOB_ITEM_CONTRACT_ID_TAG)
                ? Optional.of(jobItemTag.getUUID(JOB_ITEM_CONTRACT_ID_TAG))
                : Optional.empty();
    }

    public static void dropAll(Villager villager, LivingDropsEvent event) {
        HiredJobInventory inventory = getJobInventory(villager);
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
        if (slot == FILTER_SLOT) {
            if (!stack.isEmpty() && !VillagerRetaliationItems.isItemFilter(stack)) {
                return;
            }
            this.items.set(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
            this.slotTypes[slot] = HiredJobInventorySlotType.NORMAL;
            setChanged();
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
        markWithActiveContract(stack);
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

    public void markPlayerPlacedSupply(int slot) {
        if (!isJobGridSlot(slot)) {
            return;
        }
        ItemStack stack = this.items.get(slot);
        if (stack.isEmpty()) {
            resetEmptySlotType(slot);
            setChanged();
            return;
        }
        if (ProtectedVillagerProperty.isProtected(stack)) {
            this.slotTypes[slot] = HiredJobInventorySlotType.PROTECTED_PROPERTY;
        } else {
            this.slotTypes[slot] = HiredJobInventorySlotType.SUPPLY;
            markWithActiveContract(stack);
        }
        setChanged();
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

    public int promoteOutputToSupply(Predicate<ItemStack> predicate, int maxCount) {
        int remaining = Math.max(0, maxCount);
        if (remaining <= 0) {
            return 0;
        }
        Predicate<ItemStack> safePredicate = predicate == null ? ignored -> true : predicate;
        int moved = 0;

        for (int slot : outputSlots()) {
            ItemStack stack = this.items.get(slot);
            if (stack.isEmpty()
                    || ProtectedVillagerProperty.isProtected(stack)
                    || defaultType(slot) != HiredJobInventorySlotType.SUPPLY
                    || !safePredicate.test(stack)) {
                continue;
            }
            if (stack.getCount() > remaining) {
                ItemStack offered = stack.copyWithCount(remaining);
                ItemStack remainder = insertSupply(offered);
                int inserted = offered.getCount() - remainder.getCount();
                if (inserted > 0) {
                    stack.shrink(inserted);
                    moved += inserted;
                    remaining -= inserted;
                    setChanged();
                    if (remaining <= 0) {
                        return moved;
                    }
                }
                // A partially moved stack must remain output. Reclassifying the
                // entire source here would promote more than maxCount requested.
                continue;
            }
            this.slotTypes[slot] = HiredJobInventorySlotType.SUPPLY;
            moved += stack.getCount();
            remaining -= stack.getCount();
            if (remaining <= 0) {
                setChanged();
                return moved;
            }
        }

        for (int slot : outputSlots()) {
            ItemStack stack = this.items.get(slot);
            if (stack.isEmpty()
                    || ProtectedVillagerProperty.isProtected(stack)
                    || !safePredicate.test(stack)) {
                continue;
            }
            ItemStack offered = stack.copyWithCount(Math.min(remaining, stack.getCount()));
            ItemStack remainder = insertSupply(offered);
            int inserted = offered.getCount() - remainder.getCount();
            if (inserted <= 0) {
                continue;
            }
            stack.shrink(inserted);
            moved += inserted;
            remaining -= inserted;
            if (stack.isEmpty()) {
                this.items.set(slot, ItemStack.EMPTY);
                resetEmptySlotType(slot);
            }
            setChanged();
            if (remaining <= 0) {
                break;
            }
        }
        if (moved > 0) {
            setChanged();
        }
        return moved;
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
        for (int slot : TOOL_SLOT_SEARCH_ORDER) {
            if (slot != MAINHAND_SLOT && !isSupplySlot(slot)) {
                continue;
            }
            ItemStack stack = getItem(slot);
            if (!stack.isEmpty() && predicate.test(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public ItemStack equipBestTool(Predicate<ItemStack> predicate, ToDoubleFunction<ItemStack> scorer) {
        int bestSlot = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int slot : TOOL_SLOT_SEARCH_ORDER) {
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

    /**
     * Consumes the full requested amount or leaves the inventory unchanged.
     */
    public boolean consumeSupplyExactly(Predicate<ItemStack> predicate, int count) {
        int requested = Math.max(0, count);
        if (requested == 0) {
            return true;
        }

        int available = 0;
        for (int slot : supplySlots()) {
            ItemStack stack = this.items.get(slot);
            if (!stack.isEmpty() && predicate.test(stack)) {
                available += stack.getCount();
                if (available >= requested) {
                    return consumeSupply(predicate, requested) == requested;
                }
            }
        }
        return false;
    }

    public int countRemovableItemsForContract(UUID contractId) {
        if (contractId == null) {
            return 0;
        }
        int count = 0;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (slot == FILTER_SLOT) {
                continue;
            }
            ItemStack stack = this.items.get(slot);
            if (!stack.isEmpty()
                    && canHirerRemoveFromJobInventory(stack)
                    && jobItemContractId(stack).filter(contractId::equals).isPresent()) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public boolean hasRemovableItemsForContract(UUID contractId) {
        return countRemovableItemsForContract(contractId) > 0;
    }

    public int countRemovableItemsWithoutContract() {
        int count = 0;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (slot == FILTER_SLOT) {
                continue;
            }
            ItemStack stack = this.items.get(slot);
            if (!stack.isEmpty() && canHirerRemoveFromJobInventory(stack) && jobItemContractId(stack).isEmpty()) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public int markRemovableItemsForContract(UUID contractId) {
        if (contractId == null) {
            return 0;
        }
        int marked = 0;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (slot == FILTER_SLOT) {
                continue;
            }
            ItemStack stack = this.items.get(slot);
            if (stack.isEmpty() || !canHirerRemoveFromJobInventory(stack)) {
                continue;
            }
            markAsContractJobItem(stack, contractId, JOB_ITEM_KIND_SUPPLY);
            marked += stack.getCount();
        }
        if (marked > 0) {
            setChanged();
        }
        return marked;
    }

    public ItemStack insertSupply(ItemStack stack) {
        return insertSupply(stack, false, JOB_ITEM_KIND_SUPPLY);
    }

    public ItemStack insertPlainSupply(ItemStack stack) {
        return insertSupply(stack, false, JOB_ITEM_KIND_SUPPLY, false);
    }

    public ItemStack insertSupplyFromStorage(ItemStack stack) {
        return insertSupply(stack, true, JOB_ITEM_KIND_SUPPLY);
    }

    private ItemStack insertSupply(ItemStack stack, boolean markJobItem, String jobItemKind) {
        return insertSupply(stack, markJobItem, jobItemKind, true);
    }

    private ItemStack insertSupply(ItemStack stack, boolean markJobItem, String jobItemKind, boolean markContractItem) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = stack.copy();
        if (markContractItem) {
            if (markJobItem) {
                markAsStorageJobItem(remainder, jobItemKind, activeContractId().orElse(null));
            } else {
                markWithActiveContract(remainder);
            }
        } else {
            removeJobItemMarker(remainder);
        }
        boolean changed = insertSupplyIntoPreferredRegions(remainder);
        if (remainder.isEmpty()) {
            setChanged();
            return ItemStack.EMPTY;
        }
        if (remainder.getCount() != stack.getCount()) {
            setChanged();
        } else if (changed) {
            setChanged();
        }
        return remainder;
    }

    public ItemStack insertTool(ItemStack stack) {
        return insertTool(stack, false, JOB_ITEM_KIND_TOOL);
    }

    public ItemStack insertToolFromStorage(ItemStack stack) {
        return insertTool(stack, true, JOB_ITEM_KIND_TOOL);
    }

    private ItemStack insertTool(ItemStack stack, boolean markJobItem, String jobItemKind) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = stack.copy();
        if (markJobItem) {
            markAsStorageJobItem(remainder, jobItemKind, activeContractId().orElse(null));
        } else {
            markWithActiveContract(remainder);
        }
        ItemStack mainhand = this.items.get(MAINHAND_SLOT);
        if (!ProtectedVillagerProperty.isProtected(mainhand)) {
            if (mainhand.isEmpty()) {
                int moved = Math.min(remainder.getCount(), remainder.getMaxStackSize());
                setItem(MAINHAND_SLOT, remainder.copyWithCount(moved));
                remainder.shrink(moved);
                if (remainder.isEmpty()) {
                    return ItemStack.EMPTY;
                }
            } else if (ItemStack.isSameItemSameComponents(mainhand, remainder)
                    && mainhand.getCount() < mainhand.getMaxStackSize()) {
                int moved = Math.min(remainder.getCount(), mainhand.getMaxStackSize() - mainhand.getCount());
                mainhand.grow(moved);
                remainder.shrink(moved);
                syncMainHandEquipment();
                if (remainder.isEmpty()) {
                    return ItemStack.EMPTY;
                }
            }
        }

        boolean changed = insertToolIntoPreferredRegions(remainder);
        if (remainder.isEmpty()) {
            setChanged();
            return ItemStack.EMPTY;
        }
        if (changed) {
            setChanged();
        }
        return remainder;
    }

    public ItemStack insertOutput(ItemStack stack) {
        return insertOutput(stack, true);
    }

    public ItemStack insertPlainOutput(ItemStack stack) {
        return insertOutput(stack, false);
    }

    private ItemStack insertOutput(ItemStack stack, boolean markJobItem) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = stack.copy();
        if (markJobItem) {
            markWithActiveContract(remainder);
        } else {
            removeJobItemMarker(remainder);
        }
        boolean changed = insertOutputIntoPreferredRegions(remainder);
        if (remainder.isEmpty()) {
            setChanged();
            return ItemStack.EMPTY;
        }
        if (remainder.getCount() != stack.getCount()) {
            setChanged();
        } else if (changed) {
            setChanged();
        }
        return remainder;
    }

    public boolean hasOutputSpace() {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (canInsertOutputIntoSlot(this.items, this.slotTypes, slot, true)
                    && this.items.get(slot).getCount() < this.items.get(slot).getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasEmptyOutputSpace() {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (canInsertOutputIntoSlot(this.items, this.slotTypes, slot, true) && this.items.get(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public boolean canStoreOutputs(List<ItemStack> stacks) {
        return canStoreOutputs(stacks, true);
    }

    public boolean canStorePlainOutputs(List<ItemStack> stacks) {
        return canStoreOutputs(stacks, false);
    }

    private boolean canStoreOutputs(List<ItemStack> stacks, boolean markContractItems) {
        NonNullList<ItemStack> simulatedItems = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        HiredJobInventorySlotType[] simulatedTypes = new HiredJobInventorySlotType[SLOT_COUNT];
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            simulatedItems.set(slot, this.items.get(slot).copy());
            simulatedTypes[slot] = slotType(slot);
        }
        for (ItemStack stack : stacks) {
            if (!simulateOutputInsert(simulatedItems, simulatedTypes, stack.copy(), markContractItems).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Applies a supply-only item transformation as one transaction. Ingredients are
     * only consumed when every produced stack, including container remainders, fits.
     */
    public boolean tryTransformSupplies(Map<Item, Integer> consumed, List<ItemStack> produced) {
        return transformSupplies(consumed, produced, false, true);
    }

    /** Applies a supply-to-output transformation without exposing a partial state. */
    public boolean tryTransformSuppliesToOutputs(Map<Item, Integer> consumed, List<ItemStack> produced) {
        return transformSupplies(consumed, produced, true, true);
    }

    public boolean canTransformSuppliesToOutputs(Map<Item, Integer> consumed, List<ItemStack> produced) {
        return transformSupplies(consumed, produced, true, false);
    }

    private boolean transformSupplies(
            Map<Item, Integer> consumed,
            List<ItemStack> produced,
            boolean produceAsOutput,
            boolean commit) {
        if (consumed == null || consumed.isEmpty() || produced == null || produced.isEmpty()) {
            return false;
        }
        boolean hasProducedItem = produced.stream().anyMatch(stack -> stack != null && !stack.isEmpty());
        if (!hasProducedItem) {
            return false;
        }
        NonNullList<ItemStack> simulatedItems = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        HiredJobInventorySlotType[] simulatedTypes = new HiredJobInventorySlotType[SLOT_COUNT];
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            simulatedItems.set(slot, this.items.get(slot).copy());
            simulatedTypes[slot] = slotType(slot);
        }

        for (Map.Entry<Item, Integer> entry : consumed.entrySet()) {
            Item item = entry.getKey();
            Integer requested = entry.getValue();
            if (item == null || requested == null || requested <= 0) {
                return false;
            }
            int remaining = requested;
            for (int slot = 0; slot < SLOT_COUNT && remaining > 0; slot++) {
                if (simulatedTypes[slot] != HiredJobInventorySlotType.SUPPLY) {
                    continue;
                }
                ItemStack stack = simulatedItems.get(slot);
                if (stack.isEmpty() || ProtectedVillagerProperty.isProtected(stack) || !stack.is(item)) {
                    continue;
                }
                int removed = Math.min(remaining, stack.getCount());
                stack.shrink(removed);
                remaining -= removed;
                if (stack.isEmpty()) {
                    simulatedItems.set(slot, ItemStack.EMPTY);
                    simulatedTypes[slot] = defaultType(slot);
                }
            }
            if (remaining > 0) {
                return false;
            }
        }

        for (ItemStack stack : produced) {
            if (stack == null) {
                continue;
            }
            ItemStack remainder = produceAsOutput
                    ? simulateOutputInsert(simulatedItems, simulatedTypes, stack.copy(), true)
                    : simulateSupplyInsert(simulatedItems, simulatedTypes, stack.copy());
            if (!remainder.isEmpty()) {
                return false;
            }
        }

        if (commit) {
            for (int slot = 0; slot < SLOT_COUNT; slot++) {
                this.items.set(slot, simulatedItems.get(slot));
                this.slotTypes[slot] = simulatedTypes[slot];
            }
            setChanged();
        }
        return true;
    }

    public void returnSupplyOrDrop(ItemStack stack) {
        ItemStack remainder = insertSupply(stack);
        if (!remainder.isEmpty()) {
            this.villager.spawnAtLocation(remainder);
        }
    }

    public void returnOutputOrDrop(ItemStack stack) {
        ItemStack remainder = insertOutput(stack);
        if (!remainder.isEmpty()) {
            this.villager.spawnAtLocation(remainder);
        }
    }

    public boolean canStoreSuppliesAfterDepositingOutputs(List<ItemStack> stacks) {
        NonNullList<ItemStack> simulatedItems = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        HiredJobInventorySlotType[] simulatedTypes = new HiredJobInventorySlotType[SLOT_COUNT];
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            simulatedItems.set(slot, this.items.get(slot).copy());
            simulatedTypes[slot] = slotType(slot);
            if (simulatedTypes[slot] == HiredJobInventorySlotType.OUTPUT
                    && canHirerRemoveFromJobInventory(simulatedItems.get(slot))) {
                simulatedItems.set(slot, ItemStack.EMPTY);
                simulatedTypes[slot] = defaultType(slot);
            }
        }
        for (ItemStack stack : stacks) {
            if (!simulateSupplyInsert(simulatedItems, simulatedTypes, stack.copy()).isEmpty()) {
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
        return depositOutputToAssignedStorageAt(storagePos, ignored -> true);
    }

    public boolean depositOutputToAssignedStorageAt(BlockPos storagePos, Predicate<ItemStack> outputFilter) {
        if (storagePos == null) {
            return false;
        }
        Predicate<ItemStack> safeFilter = outputFilter == null ? ignored -> true : outputFilter;
        return depositOneOutputStack(output -> {
            if (!safeFilter.test(output.stack())) {
                return output.stack();
            }
            return AssignedStorageService.depositStackAtAssignedStorage(
                    this.villager,
                    storagePos,
                    output.stack());
        });
    }

    public int depositRemovableItemsToAssignedStorage() {
        int movedTotal = 0;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = this.items.get(slot);
            if (stack.isEmpty() || !canHirerRemoveFromJobInventory(stack)) {
                continue;
            }
            ItemStack previousStack = stack.copy();
            ItemStack remainder = AssignedStorageService.depositStack(this.villager, stack.copy());
            int moved = stack.getCount() - remainder.getCount();
            if (moved <= 0) {
                continue;
            }

            stack.shrink(moved);
            if (stack.isEmpty()) {
                this.items.set(slot, ItemStack.EMPTY);
                resetEmptySlotType(slot);
            }
            EquipmentSlot equipmentSlot = equipmentSlotForJobSlot(slot);
            if (equipmentSlot != null) {
                updateLiveEquipmentAfterJobSlotChange(equipmentSlot, previousStack);
            }
            movedTotal += moved;
        }
        if (movedTotal > 0) {
            setChanged();
        }
        return movedTotal;
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

    private ItemStack simulateOutputInsert(
            NonNullList<ItemStack> simulatedItems,
            HiredJobInventorySlotType[] simulatedTypes,
            ItemStack stack,
            boolean markContractItem) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = stack.copy();
        if (markContractItem) {
            markWithActiveContract(remainder);
        } else {
            removeJobItemMarker(remainder);
        }
        simulateOutputInsertIntoPreferredRegions(simulatedItems, simulatedTypes, remainder);
        return remainder;
    }

    private ItemStack simulateSupplyInsert(
            NonNullList<ItemStack> simulatedItems,
            HiredJobInventorySlotType[] simulatedTypes,
            ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = stack.copy();
        markWithActiveContract(remainder);
        simulateSupplyInsertIntoPreferredRegions(simulatedItems, simulatedTypes, remainder);
        return remainder;
    }

    private boolean insertOutputIntoPreferredRegions(ItemStack remainder) {
        boolean changed = insertOutputIntoSlots(remainder, false, MAIN_GRID_START, HOTBAR_START);
        if (!remainder.isEmpty()) {
            changed |= insertOutputIntoSlots(remainder, true, MAIN_GRID_START, HOTBAR_START);
        }
        if (!remainder.isEmpty()) {
            changed |= insertOutputIntoSlots(remainder, false, HOTBAR_START, FILTER_SLOT);
        }
        if (!remainder.isEmpty()) {
            changed |= insertOutputIntoSlots(remainder, true, HOTBAR_START, FILTER_SLOT);
        }
        return changed;
    }

    private boolean insertSupplyIntoPreferredRegions(ItemStack remainder) {
        boolean changed = insertSupplyIntoSlots(remainder, false, MAIN_GRID_START, HOTBAR_START);
        if (!remainder.isEmpty()) {
            changed |= insertSupplyIntoSlots(remainder, true, MAIN_GRID_START, HOTBAR_START);
        }
        if (!remainder.isEmpty()) {
            changed |= insertSupplyIntoSlots(remainder, false, HOTBAR_START, FILTER_SLOT);
        }
        if (!remainder.isEmpty()) {
            changed |= insertSupplyIntoSlots(remainder, true, HOTBAR_START, FILTER_SLOT);
        }
        return changed;
    }

    private boolean insertToolIntoPreferredRegions(ItemStack remainder) {
        boolean changed = insertToolIntoSlots(remainder, false, HOTBAR_START, FILTER_SLOT);
        if (!remainder.isEmpty()) {
            changed |= insertToolIntoSlots(remainder, true, HOTBAR_START, FILTER_SLOT);
        }
        if (!remainder.isEmpty()) {
            changed |= insertToolIntoSlots(remainder, false, MAIN_GRID_START, HOTBAR_START);
        }
        if (!remainder.isEmpty()) {
            changed |= insertToolIntoSlots(remainder, true, MAIN_GRID_START, HOTBAR_START);
        }
        return changed;
    }

    private boolean insertOutputIntoSlots(
            ItemStack remainder,
            boolean allowClaimEmptyGridSlot,
            int startInclusive,
            int endExclusive) {
        boolean changed = false;
        for (int slot = startInclusive; slot < endExclusive && !remainder.isEmpty(); slot++) {
            if (canInsertOutputIntoSlot(this.items, this.slotTypes, slot, allowClaimEmptyGridSlot)) {
                changed |= insertStackIntoSlot(this.items, this.slotTypes, slot, remainder, HiredJobInventorySlotType.OUTPUT);
            }
        }
        return changed;
    }

    private boolean insertSupplyIntoSlots(
            ItemStack remainder,
            boolean allowClaimEmptyGridSlot,
            int startInclusive,
            int endExclusive) {
        boolean changed = false;
        for (int slot = startInclusive; slot < endExclusive && !remainder.isEmpty(); slot++) {
            if (canInsertSupplyIntoSlot(this.items, this.slotTypes, slot, allowClaimEmptyGridSlot)) {
                changed |= insertStackIntoSlot(this.items, this.slotTypes, slot, remainder, HiredJobInventorySlotType.SUPPLY);
            }
        }
        return changed;
    }

    private boolean insertToolIntoSlots(
            ItemStack remainder,
            boolean allowClaimEmptyGridSlot,
            int startInclusive,
            int endExclusive) {
        boolean changed = false;
        for (int slot = startInclusive; slot < endExclusive && !remainder.isEmpty(); slot++) {
            if (canInsertToolIntoSlot(this.items, this.slotTypes, slot, allowClaimEmptyGridSlot)) {
                changed |= insertStackIntoSlot(this.items, this.slotTypes, slot, remainder, HiredJobInventorySlotType.SUPPLY);
            }
        }
        return changed;
    }

    private void simulateOutputInsertIntoPreferredRegions(
            NonNullList<ItemStack> inventory,
            HiredJobInventorySlotType[] types,
            ItemStack remainder) {
        simulateOutputInsertIntoSlots(inventory, types, remainder, false, MAIN_GRID_START, HOTBAR_START);
        if (!remainder.isEmpty()) {
            simulateOutputInsertIntoSlots(inventory, types, remainder, true, MAIN_GRID_START, HOTBAR_START);
        }
        if (!remainder.isEmpty()) {
            simulateOutputInsertIntoSlots(inventory, types, remainder, false, HOTBAR_START, FILTER_SLOT);
        }
        if (!remainder.isEmpty()) {
            simulateOutputInsertIntoSlots(inventory, types, remainder, true, HOTBAR_START, FILTER_SLOT);
        }
    }

    private void simulateSupplyInsertIntoPreferredRegions(
            NonNullList<ItemStack> inventory,
            HiredJobInventorySlotType[] types,
            ItemStack remainder) {
        simulateSupplyInsertIntoSlots(inventory, types, remainder, false, MAIN_GRID_START, HOTBAR_START);
        if (!remainder.isEmpty()) {
            simulateSupplyInsertIntoSlots(inventory, types, remainder, true, MAIN_GRID_START, HOTBAR_START);
        }
        if (!remainder.isEmpty()) {
            simulateSupplyInsertIntoSlots(inventory, types, remainder, false, HOTBAR_START, FILTER_SLOT);
        }
        if (!remainder.isEmpty()) {
            simulateSupplyInsertIntoSlots(inventory, types, remainder, true, HOTBAR_START, FILTER_SLOT);
        }
    }

    private void simulateOutputInsertIntoSlots(
            NonNullList<ItemStack> inventory,
            HiredJobInventorySlotType[] types,
            ItemStack remainder,
            boolean allowClaimEmptyGridSlot,
            int startInclusive,
            int endExclusive) {
        for (int slot = startInclusive; slot < endExclusive && !remainder.isEmpty(); slot++) {
            if (canInsertOutputIntoSlot(inventory, types, slot, allowClaimEmptyGridSlot)) {
                insertStackIntoSlot(inventory, types, slot, remainder, HiredJobInventorySlotType.OUTPUT);
            }
        }
    }

    private void simulateSupplyInsertIntoSlots(
            NonNullList<ItemStack> inventory,
            HiredJobInventorySlotType[] types,
            ItemStack remainder,
            boolean allowClaimEmptyGridSlot,
            int startInclusive,
            int endExclusive) {
        for (int slot = startInclusive; slot < endExclusive && !remainder.isEmpty(); slot++) {
            if (canInsertSupplyIntoSlot(inventory, types, slot, allowClaimEmptyGridSlot)) {
                insertStackIntoSlot(inventory, types, slot, remainder, HiredJobInventorySlotType.SUPPLY);
            }
        }
    }

    private static boolean insertStackIntoSlot(
            NonNullList<ItemStack> inventory,
            HiredJobInventorySlotType[] types,
            int slot,
            ItemStack remainder,
            HiredJobInventorySlotType insertedType) {
        ItemStack current = inventory.get(slot);
        if (current.isEmpty()) {
            int moved = Math.min(remainder.getCount(), remainder.getMaxStackSize());
            inventory.set(slot, remainder.copyWithCount(moved));
            types[slot] = insertedType;
            remainder.shrink(moved);
            return moved > 0;
        }
        if (ItemStack.isSameItemSameComponents(current, remainder)
                && current.getCount() < current.getMaxStackSize()) {
            int moved = Math.min(remainder.getCount(), current.getMaxStackSize() - current.getCount());
            current.grow(moved);
            remainder.shrink(moved);
            return moved > 0;
        }
        return false;
    }

    private static boolean canInsertOutputIntoSlot(
            NonNullList<ItemStack> inventory,
            HiredJobInventorySlotType[] types,
            int slot,
            boolean allowClaimEmptyGridSlot) {
        if (!isJobGridSlot(slot) || ProtectedVillagerProperty.isProtected(inventory.get(slot))) {
            return false;
        }
        if (types[slot] == HiredJobInventorySlotType.OUTPUT) {
            return true;
        }
        return allowClaimEmptyGridSlot && canClaimEmptyGridSlot(inventory, types, slot);
    }

    private static boolean canInsertSupplyIntoSlot(
            NonNullList<ItemStack> inventory,
            HiredJobInventorySlotType[] types,
            int slot,
            boolean allowClaimEmptyGridSlot) {
        if (!isJobGridSlot(slot) || ProtectedVillagerProperty.isProtected(inventory.get(slot))) {
            return false;
        }
        if (types[slot] == HiredJobInventorySlotType.SUPPLY) {
            return true;
        }
        return allowClaimEmptyGridSlot && canClaimEmptyGridSlot(inventory, types, slot);
    }

    private static boolean canInsertToolIntoSlot(
            NonNullList<ItemStack> inventory,
            HiredJobInventorySlotType[] types,
            int slot,
            boolean allowClaimEmptyGridSlot) {
        if (!isJobGridSlot(slot) || ProtectedVillagerProperty.isProtected(inventory.get(slot))) {
            return false;
        }
        if (types[slot] == HiredJobInventorySlotType.SUPPLY) {
            return true;
        }
        return allowClaimEmptyGridSlot && canClaimEmptyGridSlot(inventory, types, slot);
    }

    private static boolean canClaimEmptyGridSlot(
            NonNullList<ItemStack> inventory,
            HiredJobInventorySlotType[] types,
            int slot) {
        return isJobGridSlot(slot)
                && inventory.get(slot).isEmpty()
                && types[slot] != HiredJobInventorySlotType.GEAR
                && types[slot] != HiredJobInventorySlotType.PROTECTED_PROPERTY;
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
        boolean migrateLegacyLayout = tag.getInt(LAYOUT_VERSION_TAG) < CURRENT_LAYOUT_VERSION;
        ContainerHelper.loadAllItems(tag, this.items, this.villager.level().registryAccess());
        boolean[] persistedSlotTypes = new boolean[SLOT_COUNT];
        ListTag slotTypesTag = tag.getList(SLOT_TYPES_TAG, Tag.TAG_COMPOUND);
        for (Tag rawType : slotTypesTag) {
            if (!(rawType instanceof CompoundTag typeTag)) {
                continue;
            }
            int slot = typeTag.getInt(SLOT_TAG);
            if (isValidSlot(slot)) {
                this.slotTypes[slot] = HiredJobInventorySlotType.byId(typeTag.getString(TYPE_TAG));
                persistedSlotTypes[slot] = true;
            }
        }
        if (migrateLegacyLayout) {
            migrateLegacyLayout(persistedSlotTypes);
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
        if (migrateLegacyLayout) {
            save();
        }
    }

    private void save() {
        CompoundTag tag = ContainerHelper.saveAllItems(new CompoundTag(), this.items, true, this.villager.level().registryAccess());
        tag.putInt(LAYOUT_VERSION_TAG, CURRENT_LAYOUT_VERSION);
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

        if (!hasAnyItem() && slotTypesTag.isEmpty()) {
            this.villager.getPersistentData().remove(TAG);
            return;
        }
        if (!slotTypesTag.isEmpty()) {
            tag.put(SLOT_TYPES_TAG, slotTypesTag);
        }
        this.villager.getPersistentData().put(TAG, tag);
    }

    private void migrateLegacyLayout(boolean[] persistedSlotTypes) {
        for (int slot = MAIN_GRID_START; slot < LEGACY_SUPPLY_SLOT_END; slot++) {
            if (!persistedSlotTypes[slot] && !this.items.get(slot).isEmpty()) {
                this.slotTypes[slot] = HiredJobInventorySlotType.SUPPLY;
            }
        }
        ItemStack legacyFilter = this.items.get(LEGACY_FILTER_SLOT);
        if (!VillagerRetaliationItems.isItemFilter(legacyFilter) || !this.items.get(FILTER_SLOT).isEmpty()) {
            return;
        }
        this.items.set(FILTER_SLOT, legacyFilter.copyWithCount(1));
        this.items.set(LEGACY_FILTER_SLOT, ItemStack.EMPTY);
        this.slotTypes[FILTER_SLOT] = HiredJobInventorySlotType.NORMAL;
        this.slotTypes[LEGACY_FILTER_SLOT] = defaultType(LEGACY_FILTER_SLOT);
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
        if (equipmentSlot == EquipmentSlot.MAINHAND
                && VillagerInventoryContainer.hasBorrowedCombatWeapon(this.villager)) {
            // Finish the personal-inventory loan before party/job gear takes main-hand
            // authority. Otherwise the borrowed stack and the new authoritative job
            // stack can each be returned to personal storage while the job slot keeps
            // its own copy.
            VillagerInventoryContainer.returnBorrowedCombatWeapon(this.villager);
        }
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
        markWithActiveContract(stack);
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

    private boolean hasAnyItem() {
        for (ItemStack stack : this.items) {
            if (!stack.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static CompoundTag jobInventoryTag(Villager villager) {
        return villager.getPersistentData().getCompound(TAG);
    }

    private static boolean hasPersistedJobEquipment(CompoundTag tag) {
        ListTag itemsTag = tag.getList(ITEMS_TAG, Tag.TAG_COMPOUND);
        for (Tag rawItem : itemsTag) {
            if (rawItem instanceof CompoundTag itemTag
                    && equipmentSlotForJobSlot(savedItemSlot(itemTag)) != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPersistedJobStack(Villager villager, int targetSlot) {
        ListTag itemsTag = jobInventoryTag(villager).getList(ITEMS_TAG, Tag.TAG_COMPOUND);
        for (Tag rawItem : itemsTag) {
            if (rawItem instanceof CompoundTag itemTag && savedItemSlot(itemTag) == targetSlot) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPersistedItemStacks(CompoundTag tag) {
        return !tag.getList(ITEMS_TAG, Tag.TAG_COMPOUND).isEmpty();
    }

    private static int savedItemSlot(CompoundTag itemTag) {
        return itemTag.getByte(SLOT_TAG) & 255;
    }

    private static boolean isValidSlot(int slot) {
        return slot >= 0 && slot < SLOT_COUNT;
    }

    private static boolean isJobGridSlot(int slot) {
        return slot >= OFFHAND_SLOT + 1 && slot < FILTER_SLOT;
    }

    private static ItemStack removeJobItemMarker(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return stack;
        }
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (customData.isEmpty() || !customData.contains(JOB_ITEM_TAG)) {
            return stack;
        }
        CompoundTag tag = customData.copyTag();
        tag.remove(JOB_ITEM_TAG);
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        return stack;
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
        if (slot == FILTER_SLOT) {
            return HiredJobInventorySlotType.NORMAL;
        }
        if (equipmentSlotForJobSlot(slot) != null) {
            return HiredJobInventorySlotType.GEAR;
        }
        if (slot >= HOTBAR_START && slot < FILTER_SLOT) {
            return HiredJobInventorySlotType.SUPPLY;
        }
        return HiredJobInventorySlotType.OUTPUT;
    }

    private static int[] createToolSlotSearchOrder() {
        int[] slots = new int[1 + HOTBAR_SLOT_COUNT + MAIN_GRID_SLOT_COUNT];
        int index = 0;
        slots[index++] = MAINHAND_SLOT;
        for (int slot = HOTBAR_START; slot < FILTER_SLOT; slot++) {
            slots[index++] = slot;
        }
        for (int slot = MAIN_GRID_START; slot < HOTBAR_START; slot++) {
            slots[index++] = slot;
        }
        return slots;
    }

    private void markWithActiveContract(ItemStack stack) {
        activeContractId().ifPresent(contractId -> markAsContractJobItem(stack, contractId, JOB_ITEM_KIND_SUPPLY));
    }

    private Optional<UUID> activeContractId() {
        if (!(this.villager.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return Optional.empty();
        }
        return VillagerJobInventoryAuthorization.activeContractId(level, this.villager);
    }

    private static ItemStack markAsStorageJobItem(ItemStack stack, String kind, UUID contractId) {
        if (stack == null || stack.isEmpty()) {
            return stack;
        }
        String safeKind = kind == null || kind.isBlank() ? JOB_ITEM_KIND_SUPPLY : kind;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            CompoundTag jobItemTag = tag.contains(JOB_ITEM_TAG, Tag.TAG_COMPOUND)
                    ? tag.getCompound(JOB_ITEM_TAG)
                    : new CompoundTag();
            jobItemTag.putString(JOB_ITEM_KIND_TAG, safeKind);
            jobItemTag.putString(JOB_ITEM_SOURCE_TAG, JOB_ITEM_SOURCE_STORAGE);
            if (contractId != null) {
                jobItemTag.putUUID(JOB_ITEM_CONTRACT_ID_TAG, contractId);
            }
            tag.put(JOB_ITEM_TAG, jobItemTag);
        });
        return stack;
    }

    private static ItemStack markAsContractJobItem(ItemStack stack, UUID contractId, String kind) {
        if (stack == null || stack.isEmpty() || contractId == null || ProtectedVillagerProperty.isProtected(stack)) {
            return stack;
        }
        String safeKind = kind == null || kind.isBlank() ? JOB_ITEM_KIND_SUPPLY : kind;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            CompoundTag jobItemTag = tag.contains(JOB_ITEM_TAG, Tag.TAG_COMPOUND)
                    ? tag.getCompound(JOB_ITEM_TAG)
                    : new CompoundTag();
            jobItemTag.putString(JOB_ITEM_KIND_TAG, jobItemTag.getString(JOB_ITEM_KIND_TAG).isBlank()
                    ? safeKind
                    : jobItemTag.getString(JOB_ITEM_KIND_TAG));
            if (!jobItemTag.contains(JOB_ITEM_SOURCE_TAG, Tag.TAG_STRING)) {
                jobItemTag.putString(JOB_ITEM_SOURCE_TAG, JOB_ITEM_SOURCE_CONTRACT);
            }
            jobItemTag.putUUID(JOB_ITEM_CONTRACT_ID_TAG, contractId);
            tag.put(JOB_ITEM_TAG, jobItemTag);
        });
        return stack;
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
