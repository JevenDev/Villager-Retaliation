package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerEquipment;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

final class VillagerInventoryContainer implements Container {
    static final int ARMOR_SLOT_COUNT = 4;
    static final int INVENTORY_SLOT_COUNT = 27;
    private static final int LEGACY_INVENTORY_SLOT_COUNT = 36;
    static final int HELD_SLOT = ARMOR_SLOT_COUNT + INVENTORY_SLOT_COUNT;
    static final int OFFHAND_SLOT = HELD_SLOT + 1;
    static final int SLOT_COUNT = OFFHAND_SLOT + 1;

    private static final String EXTRA_INVENTORY_TAG = "VillagerRetaliationExtraInventory";
    private static final String BORROWED_COMBAT_WEAPON_TAG = "VillagerRetaliationBorrowedCombatWeapon";
    private static final String BORROWED_COMBAT_WEAPON_SLOT_TAG = "Slot";
    private static final String BORROWED_COMBAT_WEAPON_STACK_TAG = "Stack";
    private static final String BORROWED_COMBAT_WEAPON_DISPLACED_MAINHAND_TAG = "DisplacedMainhand";
    private static final String BORROWED_COMBAT_WEAPON_RETURN_FAILURES_TAG = "ReturnFailures";
    private static final long USABLE_WEAPON_CACHE_TICKS = 20L;
    private static final Map<UUID, Integer> OPEN_INVENTORIES = new HashMap<>();
    private static final Map<UUID, CachedInventoryWeaponCheck> USABLE_WEAPON_CACHE = new HashMap<>();
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    private final Villager villager;
    private final NonNullList<ItemStack> inventory;

    VillagerInventoryContainer(Villager villager) {
        this.villager = villager;
        this.inventory = NonNullList.withSize(INVENTORY_SLOT_COUNT, ItemStack.EMPTY);
        VillagerRetaliationHandler.releaseTemporaryWeaponForInventory(villager);
        VillagerRetaliationVillagerWeapons.prepareTrackedPickupForInventory(villager);
        loadInventory();
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
        if (isArmorSlot(slot)) {
            if (jobEquipmentControls(ARMOR_SLOTS[slot])) {
                return ItemStack.EMPTY;
            }
            return this.villager.getItemBySlot(ARMOR_SLOTS[slot]);
        }
        if (slot == HELD_SLOT) {
            if (jobEquipmentControls(EquipmentSlot.MAINHAND)) {
                return ItemStack.EMPTY;
            }
            return canAccessMainHand(this.villager) ? this.villager.getMainHandItem() : ItemStack.EMPTY;
        }
        if (slot == OFFHAND_SLOT) {
            if (jobEquipmentControls(EquipmentSlot.OFFHAND)) {
                return ItemStack.EMPTY;
            }
            return this.villager.getOffhandItem();
        }
        int inventorySlot = slot - ARMOR_SLOT_COUNT;
        if (isInventorySlot(inventorySlot)) {
            return getInventoryItem(inventorySlot);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (amount <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = getItem(slot);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack removed;
        if (stack.getCount() <= amount) {
            removed = stack.copy();
            setItem(slot, ItemStack.EMPTY);
        } else {
            removed = stack.split(amount);
            setItem(slot, stack);
        }
        setChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = getItem(slot);
        if (!stack.isEmpty()) {
            setItem(slot, ItemStack.EMPTY);
        }
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (isArmorSlot(slot)) {
            if (jobEquipmentControls(ARMOR_SLOTS[slot])) {
                return;
            }
            setEquipment(ARMOR_SLOTS[slot], stack);
            return;
        }
        if (slot == HELD_SLOT) {
            if (jobEquipmentControls(EquipmentSlot.MAINHAND)) {
                return;
            }
            if (stack.isEmpty() && !canAccessMainHand(this.villager)) {
                return;
            }
            setEquipment(EquipmentSlot.MAINHAND, stack);
            return;
        }
        if (slot == OFFHAND_SLOT) {
            if (jobEquipmentControls(EquipmentSlot.OFFHAND)) {
                return;
            }
            setEquipment(EquipmentSlot.OFFHAND, stack);
            return;
        }

        int inventorySlot = slot - ARMOR_SLOT_COUNT;
        if (isInventorySlot(inventorySlot)) {
            setInventoryItem(inventorySlot, stack);
            setChanged();
        }
    }

    @Override
    public void setChanged() {
        saveInventory();
        saveExtraInventory();
        invalidateUsableWeaponCache(this.villager);
    }

    @Override
    public boolean stillValid(Player player) {
        return this.villager.isAlive()
                && player.isAlive()
                && !player.isSpectator()
                && player.distanceToSqr(this.villager) <= 64.0D;
    }

    @Override
    public void startOpen(Player player) {
        OPEN_INVENTORIES.merge(this.villager.getUUID(), 1, Integer::sum);
    }

    @Override
    public void stopOpen(Player player) {
        setChanged();
        OPEN_INVENTORIES.computeIfPresent(this.villager.getUUID(), (uuid, openCount) -> openCount <= 1 ? null : openCount - 1);
    }

    @Override
    public void clearContent() {
        for (int slot = 0; slot < getContainerSize(); slot++) {
            setItem(slot, ItemStack.EMPTY);
        }
        setChanged();
    }

    void refreshFromVillager() {
        loadInventory();
    }

    static boolean isArmorSlot(int slot) {
        return slot >= 0 && slot < ARMOR_SLOT_COUNT;
    }

    static boolean isVillagerInventorySlot(int slot) {
        return slot >= ARMOR_SLOT_COUNT && slot < HELD_SLOT;
    }

    static boolean isInventorySlot(int inventorySlot) {
        return inventorySlot >= 0 && inventorySlot < INVENTORY_SLOT_COUNT;
    }

    static EquipmentSlot armorEquipmentSlot(int slot) {
        return ARMOR_SLOTS[slot];
    }

    static boolean hasOpenInventory(Villager villager) {
        return OPEN_INVENTORIES.getOrDefault(villager.getUUID(), 0) > 0;
    }

    static void dropExtraInventory(Villager villager) {
        NonNullList<ItemStack> extraInventory = loadExtraInventory(villager, Math.max(0, LEGACY_INVENTORY_SLOT_COUNT - vanillaInventorySlots(villager)));
        for (ItemStack stack : extraInventory) {
            if (!stack.isEmpty()) {
                villager.spawnAtLocation(stack.copy());
            }
        }
        villager.getPersistentData().remove(EXTRA_INVENTORY_TAG);
    }

    static ItemStack addItem(Villager villager, ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        NonNullList<ItemStack> inventory = loadFullInventory(villager);
        ItemStack remainder = stack.copy();
        mergeIntoExistingStacks(inventory, remainder);
        fillEmptySlots(inventory, remainder);
        saveFullInventory(villager, inventory);
        return remainder;
    }

    static boolean canAddItems(Villager villager, List<ItemStack> stacks) {
        NonNullList<ItemStack> inventory = loadFullInventory(villager);
        for (ItemStack stack : stacks) {
            ItemStack remainder = stack.copy();
            mergeIntoExistingStacks(inventory, remainder);
            fillEmptySlots(inventory, remainder);
            if (!remainder.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    static boolean hasUsableWeapon(Villager villager) {
        UUID villagerId = villager.getUUID();
        long gameTime = villager.level().getGameTime();
        CachedInventoryWeaponCheck cached = USABLE_WEAPON_CACHE.get(villagerId);
        if (cached != null && cached.expiresGameTime() > gameTime) {
            return cached.hasUsableWeapon();
        }

        NonNullList<ItemStack> inventory = loadFullInventory(villager);
        boolean hasUsableWeapon = hasUsableWeapon(inventory);
        USABLE_WEAPON_CACHE.put(villagerId, new CachedInventoryWeaponCheck(hasUsableWeapon, gameTime + USABLE_WEAPON_CACHE_TICKS));
        return hasUsableWeapon;
    }

    private static boolean hasUsableWeapon(NonNullList<ItemStack> inventory) {
        for (ItemStack stack : inventory) {
            if (VillagerRetaliationVillagerWeapons.isUsableWeapon(stack)) {
                return true;
            }
        }
        return false;
    }

    static boolean hasBorrowedCombatWeapon(Villager villager) {
        return villager.getPersistentData().contains(BORROWED_COMBAT_WEAPON_TAG, CompoundTag.TAG_COMPOUND);
    }

    static boolean maintainBorrowedCombatWeapon(Villager villager) {
        BorrowedCombatWeapon borrowedWeapon = borrowedCombatWeapon(villager);
        if (borrowedWeapon == null) {
            return false;
        }

        ItemStack mainHand = villager.getMainHandItem();
        if (ItemStack.isSameItem(mainHand, borrowedWeapon.stack())) {
            if (!sameStack(mainHand, borrowedWeapon.stack())) {
                persistBorrowedCombatWeapon(
                        villager,
                        borrowedWeapon.slot(),
                        mainHand.copy(),
                        borrowedWeapon.displacedMainHand(),
                        borrowedWeapon.returnFailures()
                );
            }
            return true;
        }

        VillagerRetaliationVillagerEquipment.setTemporaryMainHand(villager, borrowedWeapon.stack(), 0.0F);
        return true;
    }

    static boolean tryBorrowCombatWeapon(Villager villager) {
        if (hasBorrowedCombatWeapon(villager)) {
            return maintainBorrowedCombatWeapon(villager);
        }
        ItemStack displacedMainHand = villager.getMainHandItem().copy();
        if (VillagerRetaliationVillagerWeapons.isUsableWeapon(displacedMainHand)) {
            return false;
        }

        NonNullList<ItemStack> inventory = loadFullInventory(villager);
        int selectedSlot = selectBestWeaponSlot(inventory);
        if (selectedSlot < 0) {
            return false;
        }

        ItemStack borrowedStack = inventory.get(selectedSlot).copy();
        inventory.set(selectedSlot, ItemStack.EMPTY);
        if (!displacedMainHand.isEmpty()) {
            inventory.set(selectedSlot, displacedMainHand.copy());
            VillagerRetaliationVillagerEquipment.clearPlayerManagedMainHand(villager);
        }
        saveFullInventory(villager, inventory);
        persistBorrowedCombatWeapon(villager, selectedSlot, borrowedStack, displacedMainHand);
        VillagerRetaliationVillagerEquipment.setTemporaryMainHand(villager, borrowedStack, 0.0F);
        return true;
    }

    static void returnBorrowedCombatWeapon(Villager villager) {
        BorrowedCombatWeapon borrowedWeapon = borrowedCombatWeapon(villager);
        if (borrowedWeapon == null) {
            return;
        }

        ItemStack mainHand = villager.getMainHandItem();
        ItemStack returnedStack = mainHand;
        if (returnedStack.isEmpty() || !ItemStack.isSameItem(returnedStack, borrowedWeapon.stack())) {
            if (!mainHand.isEmpty()) {
                ItemStack displacedRemainder = addItem(villager, mainHand.copy());
                if (!displacedRemainder.isEmpty()) {
                    villager.spawnAtLocation(displacedRemainder);
                }
            }
            returnedStack = borrowedWeapon.stack();
        } else {
            returnedStack = returnedStack.copy();
        }

        if (returnBorrowedCombatWeaponBySwap(villager, borrowedWeapon, returnedStack)) {
            return;
        }

        ItemStack remainder = addItemToPreferredSlot(villager, returnedStack, borrowedWeapon.slot());
        if (remainder.isEmpty()) {
            villager.getPersistentData().remove(BORROWED_COMBAT_WEAPON_TAG);
            VillagerRetaliationVillagerEquipment.restoreMainHand(villager, ItemStack.EMPTY);
            return;
        }

        if (borrowedWeapon.returnFailures() <= 0) {
            persistBorrowedCombatWeapon(
                    villager,
                    borrowedWeapon.slot(),
                    remainder,
                    borrowedWeapon.displacedMainHand(),
                    borrowedWeapon.returnFailures() + 1
            );
            VillagerRetaliationVillagerEquipment.setTemporaryMainHand(villager, remainder, 0.0F);
            return;
        }

        villager.getPersistentData().remove(BORROWED_COMBAT_WEAPON_TAG);
        VillagerRetaliationVillagerEquipment.restoreMainHand(villager, ItemStack.EMPTY);
        villager.spawnAtLocation(remainder);
    }

    private static boolean returnBorrowedCombatWeaponBySwap(
            Villager villager,
            BorrowedCombatWeapon borrowedWeapon,
            ItemStack returnedStack
    ) {
        ItemStack displacedMainHand = borrowedWeapon.displacedMainHand();
        if (displacedMainHand.isEmpty() || returnedStack.isEmpty()) {
            return false;
        }

        NonNullList<ItemStack> inventory = loadFullInventory(villager);
        int slot = borrowedWeapon.slot();
        if (slot < 0 || slot >= inventory.size()) {
            return false;
        }

        ItemStack slotStack = inventory.get(slot);
        if (!slotStack.isEmpty() && !sameStack(slotStack, displacedMainHand)) {
            return false;
        }

        inventory.set(slot, returnedStack.copy());
        saveFullInventory(villager, inventory);
        villager.getPersistentData().remove(BORROWED_COMBAT_WEAPON_TAG);
        VillagerRetaliationVillagerEquipment.setInventoryEquipment(villager, EquipmentSlot.MAINHAND, displacedMainHand);
        return true;
    }

    static void clearBorrowedCombatWeapon(Villager villager) {
        villager.getPersistentData().remove(BORROWED_COMBAT_WEAPON_TAG);
    }

    static int countStoredGiftItem(Villager villager, UUID playerId, ItemStack target) {
        if (target.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            count += matchingGiftItemCount(villager.getItemBySlot(slot), playerId, target);
        }
        if (canAccessMainHand(villager)) {
            count += matchingGiftItemCount(villager.getMainHandItem(), playerId, target);
        }
        count += matchingGiftItemCount(villager.getOffhandItem(), playerId, target);

        NonNullList<ItemStack> inventory = loadFullInventory(villager);
        for (ItemStack stack : inventory) {
            count += matchingGiftItemCount(stack, playerId, target);
        }
        return count;
    }

    static int countStoredTradePayment(Villager villager, UUID playerId, ItemStack target) {
        if (target.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            count += matchingTradePaymentCount(villager.getItemBySlot(slot), playerId, target);
        }
        if (canAccessMainHand(villager)) {
            count += matchingTradePaymentCount(villager.getMainHandItem(), playerId, target);
        }
        count += matchingTradePaymentCount(villager.getOffhandItem(), playerId, target);

        NonNullList<ItemStack> inventory = loadFullInventory(villager);
        for (ItemStack stack : inventory) {
            count += matchingTradePaymentCount(stack, playerId, target);
        }
        return count;
    }

    static int countConfiscatedStolenItem(Villager villager, UUID playerId, ItemStack target) {
        if (target.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            count += matchingConfiscatedStolenItemCount(villager.getItemBySlot(slot), playerId, target);
        }
        if (canAccessMainHand(villager)) {
            count += matchingConfiscatedStolenItemCount(villager.getMainHandItem(), playerId, target);
        }
        count += matchingConfiscatedStolenItemCount(villager.getOffhandItem(), playerId, target);

        NonNullList<ItemStack> inventory = loadFullInventory(villager);
        for (ItemStack stack : inventory) {
            count += matchingConfiscatedStolenItemCount(stack, playerId, target);
        }
        return count;
    }

    static void dropAllInventoryAndEquipment(Villager villager, LivingDropsEvent event) {
        BorrowedCombatWeapon borrowedWeapon = borrowedCombatWeapon(villager);
        boolean borrowedWeaponInMainHand = borrowedWeapon != null
                && ItemStack.isSameItem(villager.getMainHandItem(), borrowedWeapon.stack());
        dropEquipment(villager, event);
        if (borrowedWeapon != null && !borrowedWeaponInMainHand) {
            com.jvn.toucanlib.neoforge.loot.ToucanLivingDrops.addDrop(event, borrowedWeapon.stack().copy());
        }

        NonNullList<ItemStack> inventory = loadFullInventory(villager);
        for (ItemStack stack : inventory) {
            if (!stack.isEmpty()) {
                com.jvn.toucanlib.neoforge.loot.ToucanLivingDrops.addDrop(event, stack.copy());
            }
        }
        dropLegacyOverflowInventory(villager, event);
        HiredJobInventory.dropAll(villager, event);

        clearFullInventory(villager);
        clearBorrowedCombatWeapon(villager);
    }

    private ItemStack getInventoryItem(int inventorySlot) {
        return this.inventory.get(inventorySlot);
    }

    private void setInventoryItem(int inventorySlot, ItemStack stack) {
        this.inventory.set(inventorySlot, stack);
    }

    private boolean jobEquipmentControls(EquipmentSlot slot) {
        return HiredJobInventory.hasJobEquipmentForSlot(this.villager, slot);
    }

    private void setEquipment(EquipmentSlot slot, ItemStack stack) {
        ItemStack previous = this.villager.getItemBySlot(slot);
        if (shouldStoreDisplacedEquipment(slot, previous, stack)
                && !storeDisplacedEquipment(previous)) {
            return;
        }
        VillagerRetaliationVillagerEquipment.setInventoryEquipment(this.villager, slot, stack);
        setChanged();
    }

    private boolean shouldStoreDisplacedEquipment(EquipmentSlot slot, ItemStack previous, ItemStack stack) {
        return isPersonalEquipmentSlot(slot)
                && !previous.isEmpty()
                && !stack.isEmpty()
                && !sameStack(previous, stack)
                && (slot != EquipmentSlot.MAINHAND || canAccessMainHand(this.villager));
    }

    private boolean storeDisplacedEquipment(ItemStack stack) {
        ItemStack remainder = stack.copy();
        NonNullList<ItemStack> updatedInventory = copyInventory(this.inventory);
        mergeIntoExistingStacks(updatedInventory, remainder);
        fillEmptySlots(updatedInventory, remainder);
        if (!remainder.isEmpty()) {
            if (!canAssignedStorageAccept(remainder)) {
                return false;
            }
            remainder = AssignedStorageService.depositStack(this.villager, remainder);
        }
        if (remainder.isEmpty()) {
            for (int slot = 0; slot < this.inventory.size(); slot++) {
                this.inventory.set(slot, updatedInventory.get(slot));
            }
        }
        return remainder.isEmpty();
    }

    private boolean canAssignedStorageAccept(ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        if (!(this.villager.level() instanceof ServerLevel level)) {
            return false;
        }

        ItemStack remainder = stack.copy();
        for (VillagerInventoryOverflowService.ContainerCandidate candidate
                : AssignedStorageService.liveContainerCandidates(level, this.villager)) {
            remainder = simulateInsertIntoContainer(candidate.container(), remainder);
            if (remainder.isEmpty()) {
                return true;
            }
        }
        return remainder.isEmpty();
    }

    private static ItemStack simulateInsertIntoContainer(Container container, ItemStack stack) {
        ItemStack remainder = stack.copy();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (remainder.isEmpty()) {
                return ItemStack.EMPTY;
            }

            ItemStack existing = container.getItem(slot);
            if (existing.isEmpty()
                    || !ItemStack.isSameItemSameComponents(existing, remainder)
                    || !container.canPlaceItem(slot, remainder)) {
                continue;
            }

            int maxStackSize = Math.min(existing.getMaxStackSize(), container.getMaxStackSize());
            int moveCount = Math.min(remainder.getCount(), maxStackSize - existing.getCount());
            if (moveCount > 0) {
                remainder.shrink(moveCount);
            }
        }

        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (remainder.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (!container.getItem(slot).isEmpty() || !container.canPlaceItem(slot, remainder)) {
                continue;
            }

            int moveCount = Math.min(remainder.getCount(),
                    Math.min(remainder.getMaxStackSize(), container.getMaxStackSize()));
            remainder.shrink(moveCount);
        }
        return remainder;
    }

    private static NonNullList<ItemStack> copyInventory(NonNullList<ItemStack> inventory) {
        NonNullList<ItemStack> copy = NonNullList.withSize(inventory.size(), ItemStack.EMPTY);
        for (int slot = 0; slot < inventory.size(); slot++) {
            copy.set(slot, inventory.get(slot).copy());
        }
        return copy;
    }

    private static boolean isPersonalEquipmentSlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND
                || slot == EquipmentSlot.OFFHAND
                || slot == EquipmentSlot.HEAD
                || slot == EquipmentSlot.CHEST
                || slot == EquipmentSlot.LEGS
                || slot == EquipmentSlot.FEET;
    }

    private int vanillaInventorySlots() {
        return vanillaInventorySlots(this.villager);
    }

    private static int vanillaInventorySlots(Villager villager) {
        return Math.min(INVENTORY_SLOT_COUNT, villager.getInventory().getContainerSize());
    }

    private void loadInventory() {
        boolean cleanedInvalidTradePayments = false;
        boolean migratedLegacyOverflow = false;
        int vanillaSlots = vanillaInventorySlots();
        for (int slot = 0; slot < vanillaSlots; slot++) {
            ItemStack stack = this.villager.getInventory().getItem(slot).copy();
            if (VillagerTradePaymentTracker.isInvalidStoredTradePayment(stack)) {
                stack = ItemStack.EMPTY;
                this.villager.getInventory().setItem(slot, ItemStack.EMPTY);
                cleanedInvalidTradePayments = true;
            }
            this.inventory.set(slot, stack);
        }

        int currentExtraSlots = Math.max(0, INVENTORY_SLOT_COUNT - vanillaSlots);
        int legacyExtraSlots = Math.max(currentExtraSlots, LEGACY_INVENTORY_SLOT_COUNT - vanillaSlots);
        NonNullList<ItemStack> loaded = loadExtraInventory(this.villager, legacyExtraSlots);
        for (int slot = 0; slot < Math.min(loaded.size(), currentExtraSlots); slot++) {
            ItemStack stack = loaded.get(slot);
            if (VillagerTradePaymentTracker.isInvalidStoredTradePayment(stack)) {
                stack = ItemStack.EMPTY;
                cleanedInvalidTradePayments = true;
            }
            this.inventory.set(vanillaSlots + slot, stack);
        }
        for (int slot = currentExtraSlots; slot < loaded.size(); slot++) {
            ItemStack overflow = loaded.get(slot);
            if (VillagerTradePaymentTracker.isInvalidStoredTradePayment(overflow)) {
                cleanedInvalidTradePayments = true;
                continue;
            }
            if (!overflow.isEmpty()) {
                this.villager.spawnAtLocation(overflow.copy());
                migratedLegacyOverflow = true;
            }
        }
        if (cleanedInvalidTradePayments || migratedLegacyOverflow) {
            setChanged();
        }
    }

    private static NonNullList<ItemStack> loadExtraInventory(Villager villager, int size) {
        NonNullList<ItemStack> inventory = NonNullList.withSize(size, ItemStack.EMPTY);
        CompoundTag tag = villager.getPersistentData().getCompound(EXTRA_INVENTORY_TAG);
        if (!tag.isEmpty()) {
            ContainerHelper.loadAllItems(tag, inventory, villager.level().registryAccess());
        }
        return inventory;
    }

    static NonNullList<ItemStack> loadFullInventory(Villager villager) {
        NonNullList<ItemStack> inventory = NonNullList.withSize(INVENTORY_SLOT_COUNT, ItemStack.EMPTY);
        int vanillaSlots = vanillaInventorySlots(villager);
        for (int slot = 0; slot < vanillaSlots; slot++) {
            inventory.set(slot, villager.getInventory().getItem(slot).copy());
        }

        NonNullList<ItemStack> extraInventory = loadExtraInventory(villager, Math.max(0, INVENTORY_SLOT_COUNT - vanillaSlots));
        for (int slot = 0; slot < extraInventory.size(); slot++) {
            ItemStack stack = extraInventory.get(slot).copy();
            inventory.set(vanillaSlots + slot, VillagerTradePaymentTracker.isInvalidStoredTradePayment(stack) ? ItemStack.EMPTY : stack);
        }
        return inventory;
    }

    static void saveFullInventory(Villager villager, NonNullList<ItemStack> inventory) {
        int vanillaSlots = vanillaInventorySlots(villager);
        for (int slot = 0; slot < vanillaSlots; slot++) {
            villager.getInventory().setItem(slot, inventory.get(slot).copy());
        }
        villager.getInventory().setChanged();

        NonNullList<ItemStack> extraInventory = NonNullList.withSize(Math.max(0, INVENTORY_SLOT_COUNT - vanillaSlots), ItemStack.EMPTY);
        for (int slot = 0; slot < extraInventory.size(); slot++) {
            extraInventory.set(slot, inventory.get(vanillaSlots + slot).copy());
        }
        saveExtraInventory(villager, extraInventory);
        invalidateUsableWeaponCache(villager);
    }

    private static void clearFullInventory(Villager villager) {
        NonNullList<ItemStack> emptyInventory = NonNullList.withSize(INVENTORY_SLOT_COUNT, ItemStack.EMPTY);
        saveFullInventory(villager, emptyInventory);
        villager.getPersistentData().remove(EXTRA_INVENTORY_TAG);
        invalidateUsableWeaponCache(villager);
    }

    private static ItemStack addItemToPreferredSlot(Villager villager, ItemStack stack, int preferredSlot) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        NonNullList<ItemStack> inventory = loadFullInventory(villager);
        ItemStack remainder = stack.copy();
        if (preferredSlot >= 0 && preferredSlot < inventory.size()) {
            ItemStack preferredStack = inventory.get(preferredSlot);
            if (preferredStack.isEmpty()) {
                inventory.set(preferredSlot, remainder.copy());
                remainder = ItemStack.EMPTY;
            } else if (ItemStack.isSameItemSameComponents(preferredStack, remainder)) {
                int moveCount = Math.min(remainder.getCount(), preferredStack.getMaxStackSize() - preferredStack.getCount());
                if (moveCount > 0) {
                    preferredStack.grow(moveCount);
                    remainder.shrink(moveCount);
                }
            }
        }
        mergeIntoExistingStacks(inventory, remainder);
        fillEmptySlots(inventory, remainder);
        saveFullInventory(villager, inventory);
        return remainder;
    }

    private static void dropLegacyOverflowInventory(Villager villager, LivingDropsEvent event) {
        int vanillaSlots = vanillaInventorySlots(villager);
        int currentExtraSlots = Math.max(0, INVENTORY_SLOT_COUNT - vanillaSlots);
        int legacyExtraSlots = Math.max(currentExtraSlots, LEGACY_INVENTORY_SLOT_COUNT - vanillaSlots);
        NonNullList<ItemStack> legacyExtraInventory = loadExtraInventory(villager, legacyExtraSlots);
        for (int slot = currentExtraSlots; slot < legacyExtraInventory.size(); slot++) {
            ItemStack stack = legacyExtraInventory.get(slot);
            if (!stack.isEmpty()) {
                com.jvn.toucanlib.neoforge.loot.ToucanLivingDrops.addDrop(event, stack.copy());
            }
        }
    }

    private static void mergeIntoExistingStacks(NonNullList<ItemStack> inventory, ItemStack remainder) {
        for (ItemStack existingStack : inventory) {
            if (remainder.isEmpty()) {
                return;
            }
            if (existingStack.isEmpty() || !ItemStack.isSameItemSameComponents(existingStack, remainder)) {
                continue;
            }

            int moveCount = Math.min(remainder.getCount(), existingStack.getMaxStackSize() - existingStack.getCount());
            if (moveCount > 0) {
                existingStack.grow(moveCount);
                remainder.shrink(moveCount);
            }
        }
    }

    private static void fillEmptySlots(NonNullList<ItemStack> inventory, ItemStack remainder) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (remainder.isEmpty()) {
                return;
            }
            if (!inventory.get(slot).isEmpty()) {
                continue;
            }

            int moveCount = Math.min(remainder.getCount(), remainder.getMaxStackSize());
            inventory.set(slot, remainder.copyWithCount(moveCount));
            remainder.shrink(moveCount);
        }
    }

    private static int selectBestWeaponSlot(NonNullList<ItemStack> inventory) {
        int bestSlot = -1;
        ItemStack bestWeapon = ItemStack.EMPTY;
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack candidate = inventory.get(slot);
            if (VillagerRetaliationVillagerWeapons.isBetterWeaponChoice(candidate, bestWeapon)) {
                bestSlot = slot;
                bestWeapon = candidate;
            }
        }
        return bestSlot;
    }

    private static void persistBorrowedCombatWeapon(Villager villager, int slot, ItemStack stack) {
        persistBorrowedCombatWeapon(villager, slot, stack, ItemStack.EMPTY, 0);
    }

    private static void persistBorrowedCombatWeapon(Villager villager, int slot, ItemStack stack, ItemStack displacedMainHand) {
        persistBorrowedCombatWeapon(villager, slot, stack, displacedMainHand, 0);
    }

    private static void persistBorrowedCombatWeapon(
            Villager villager,
            int slot,
            ItemStack stack,
            ItemStack displacedMainHand,
            int returnFailures
    ) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(BORROWED_COMBAT_WEAPON_SLOT_TAG, slot);
        tag.put(BORROWED_COMBAT_WEAPON_STACK_TAG, stack.saveOptional(villager.level().registryAccess()));
        if (!displacedMainHand.isEmpty()) {
            tag.put(BORROWED_COMBAT_WEAPON_DISPLACED_MAINHAND_TAG, displacedMainHand.saveOptional(villager.level().registryAccess()));
        }
        tag.putInt(BORROWED_COMBAT_WEAPON_RETURN_FAILURES_TAG, returnFailures);
        villager.getPersistentData().put(BORROWED_COMBAT_WEAPON_TAG, tag);
    }

    private static BorrowedCombatWeapon borrowedCombatWeapon(Villager villager) {
        CompoundTag tag = villager.getPersistentData().getCompound(BORROWED_COMBAT_WEAPON_TAG);
        if (tag.isEmpty() || !tag.contains(BORROWED_COMBAT_WEAPON_STACK_TAG, CompoundTag.TAG_COMPOUND)) {
            clearBorrowedCombatWeapon(villager);
            return null;
        }

        ItemStack stack = ItemStack.parseOptional(
                villager.level().registryAccess(),
                tag.getCompound(BORROWED_COMBAT_WEAPON_STACK_TAG)
        );
        if (stack.isEmpty()) {
            clearBorrowedCombatWeapon(villager);
            return null;
        }
        ItemStack displacedMainHand = tag.contains(BORROWED_COMBAT_WEAPON_DISPLACED_MAINHAND_TAG, CompoundTag.TAG_COMPOUND)
                ? ItemStack.parseOptional(villager.level().registryAccess(), tag.getCompound(BORROWED_COMBAT_WEAPON_DISPLACED_MAINHAND_TAG))
                : ItemStack.EMPTY;
        return new BorrowedCombatWeapon(
                tag.getInt(BORROWED_COMBAT_WEAPON_SLOT_TAG),
                stack,
                displacedMainHand,
                tag.getInt(BORROWED_COMBAT_WEAPON_RETURN_FAILURES_TAG)
        );
    }

    private static void dropEquipment(Villager villager, LivingDropsEvent event) {
        dropEquipmentSlot(villager, event, EquipmentSlot.HEAD);
        dropEquipmentSlot(villager, event, EquipmentSlot.CHEST);
        dropEquipmentSlot(villager, event, EquipmentSlot.LEGS);
        dropEquipmentSlot(villager, event, EquipmentSlot.FEET);
        dropEquipmentSlot(villager, event, EquipmentSlot.MAINHAND);
        dropEquipmentSlot(villager, event, EquipmentSlot.OFFHAND);
    }

    private static void dropEquipmentSlot(Villager villager, LivingDropsEvent event, EquipmentSlot slot) {
        ItemStack stack = villager.getItemBySlot(slot);
        if (stack.isEmpty()) {
            return;
        }
        if (HiredJobInventory.hasJobEquipmentForSlot(villager, slot)) {
            removeOneMatchingDrop(event, stack);
            VillagerRetaliationVillagerEquipment.setInventoryEquipment(villager, slot, ItemStack.EMPTY);
            return;
        }
        if (slot == EquipmentSlot.MAINHAND && !canAccessMainHand(villager)) {
            removeOneMatchingDrop(event, stack);
            return;
        }

        removeOneMatchingDrop(event, stack);
        com.jvn.toucanlib.neoforge.loot.ToucanLivingDrops.addDrop(event, stack.copy());
        VillagerRetaliationVillagerEquipment.setInventoryEquipment(villager, slot, ItemStack.EMPTY);
    }

    private static boolean canAccessMainHand(Villager villager) {
        return villager.getMainHandItem().isEmpty()
                || mainHandMatchesBorrowedCombatWeapon(villager)
                || VillagerRetaliationVillagerEquipment.hasManagedMainHand(villager);
    }

    private static boolean mainHandMatchesBorrowedCombatWeapon(Villager villager) {
        BorrowedCombatWeapon borrowedWeapon = borrowedCombatWeapon(villager);
        return borrowedWeapon != null && ItemStack.isSameItem(villager.getMainHandItem(), borrowedWeapon.stack());
    }

    private static void invalidateUsableWeaponCache(Villager villager) {
        USABLE_WEAPON_CACHE.remove(villager.getUUID());
    }

    private static boolean sameStack(ItemStack first, ItemStack second) {
        return first.getCount() == second.getCount() && ItemStack.isSameItemSameComponents(first, second);
    }

    private static int matchingGiftItemCount(ItemStack stack, UUID playerId, ItemStack target) {
        return VillagerGiftReturnTracker.isStoredGiftFrom(stack, playerId)
                && VillagerGiftReturnTracker.isSameTrackedGiftItem(stack, target)
                ? stack.getCount()
                : 0;
    }

    private static int matchingTradePaymentCount(ItemStack stack, UUID playerId, ItemStack target) {
        return VillagerTradePaymentTracker.isStoredTradePaymentFrom(stack, playerId)
                && VillagerTradePaymentTracker.isSameTrackedTradePayment(stack, target)
                ? stack.getCount()
                : 0;
    }

    private static int matchingConfiscatedStolenItemCount(ItemStack stack, UUID playerId, ItemStack target) {
        return VillagerConfiscatedStolenItemTracker.isConfiscatedStolenItemFrom(stack, playerId)
                && VillagerConfiscatedStolenItemTracker.isSameConfiscatedStolenItem(stack, target)
                ? stack.getCount()
                : 0;
    }

    private static void removeOneMatchingDrop(LivingDropsEvent event, ItemStack stack) {
        Iterator<ItemEntity> drops = event.getDrops().iterator();
        while (drops.hasNext()) {
            if (ItemStack.isSameItemSameComponents(drops.next().getItem(), stack)) {
                drops.remove();
                return;
            }
        }
    }

    private record BorrowedCombatWeapon(int slot, ItemStack stack, ItemStack displacedMainHand, int returnFailures) {
    }

    private record CachedInventoryWeaponCheck(boolean hasUsableWeapon, long expiresGameTime) {
    }

    private void saveExtraInventory() {
        int vanillaSlots = vanillaInventorySlots();
        NonNullList<ItemStack> extraInventory = NonNullList.withSize(Math.max(0, INVENTORY_SLOT_COUNT - vanillaSlots), ItemStack.EMPTY);
        for (int slot = 0; slot < extraInventory.size(); slot++) {
            extraInventory.set(slot, this.inventory.get(vanillaSlots + slot).copy());
        }

        saveExtraInventory(this.villager, extraInventory);
    }

    private void saveInventory() {
        int vanillaSlots = vanillaInventorySlots();
        for (int slot = 0; slot < vanillaSlots; slot++) {
            this.villager.getInventory().setItem(slot, this.inventory.get(slot).copy());
        }
        this.villager.getInventory().setChanged();
    }

    private static void saveExtraInventory(Villager villager, NonNullList<ItemStack> extraInventory) {
        if (isEmpty(extraInventory)) {
            villager.getPersistentData().remove(EXTRA_INVENTORY_TAG);
            return;
        }
        CompoundTag tag = ContainerHelper.saveAllItems(new CompoundTag(), extraInventory, true, villager.level().registryAccess());
        villager.getPersistentData().put(EXTRA_INVENTORY_TAG, tag);
    }

    private static boolean isEmpty(NonNullList<ItemStack> inventory) {
        for (ItemStack stack : inventory) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
