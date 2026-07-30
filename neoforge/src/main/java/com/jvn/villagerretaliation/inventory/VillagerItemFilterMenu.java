package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.item.VillagerItemFilterData;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class VillagerItemFilterMenu extends AbstractContainerMenu {
    public static final int GHOST_SLOT_COUNT = VillagerItemFilterData.ENTRY_COUNT;
    private static final int PLAYER_INVENTORY_COUNT = 27;
    private static final int PLAYER_HOTBAR_COUNT = 9;
    private static final int PLAYER_SLOT_START = GHOST_SLOT_COUNT;
    private static final int PLAYER_HOTBAR_START = PLAYER_SLOT_START + PLAYER_INVENTORY_COUNT;
    private static final int PLAYER_SLOT_END = PLAYER_HOTBAR_START + PLAYER_HOTBAR_COUNT;
    private static final int GHOST_X = 8;
    private static final int GHOST_Y = 18;
    private static final int PLAYER_X = 8;
    private static final int PLAYER_Y = 84;
    private static final int HOTBAR_Y = 142;
    private static final int SLOT_SIZE = 18;

    private final Inventory playerInventory;
    private final SimpleContainer ghostInventory;
    private final int editingHotbarSlot;

    public VillagerItemFilterMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf data) {
        this(containerId, playerInventory, decodeOpeningStack(data));
    }

    public VillagerItemFilterMenu(int containerId, Inventory playerInventory, ItemStack openingStack) {
        super(VillagerRetaliationMenus.ITEM_FILTER.get(), containerId);
        this.playerInventory = playerInventory;
        this.editingHotbarSlot = playerInventory.selected;
        ItemStack selected = filterStack();
        ItemStack initialFilter = selected.isEmpty() ? openingStack : selected;
        this.ghostInventory = new SimpleContainer(GHOST_SLOT_COUNT);
        for (int slot = 0; slot < GHOST_SLOT_COUNT; slot++) {
            this.ghostInventory.setItem(slot, VillagerItemFilterData.entry(initialFilter, slot));
            addSlot(new GhostSlot(this.ghostInventory, slot, GHOST_X + slot * SLOT_SIZE, GHOST_Y));
        }
        addPlayerSlots(playerInventory);
    }

    public VillagerItemFilterData.Mode mode() {
        return VillagerItemFilterData.mode(filterStack());
    }

    public void setClientMode(VillagerItemFilterData.Mode mode) {
        VillagerItemFilterData.setMode(filterStack(), mode);
    }

    public VillagerItemFilterData.EntryCombination entryCombination() {
        return VillagerItemFilterData.entryCombination(filterStack());
    }

    public boolean setEntryCombination(VillagerItemFilterData.EntryCombination entryCombination) {
        if (!isEditingHeldFilter()) {
            return false;
        }
        boolean changed = VillagerItemFilterData.setEntryCombination(filterStack(), entryCombination);
        if (changed) {
            markFilterChanged();
        }
        return changed;
    }

    public void setClientEntryCombination(VillagerItemFilterData.EntryCombination entryCombination) {
        VillagerItemFilterData.setEntryCombination(filterStack(), entryCombination);
    }

    public int amount(int slot) {
        return VillagerItemFilterData.amount(filterStack(), slot);
    }

    public int minimumAmount(int slot) {
        return VillagerItemFilterData.minimumAmount(filterStack(), slot);
    }

    public int identityEntryCount(int slot) {
        return VillagerItemFilterData.identityEntryCount(filterStack(), slot);
    }

    public int combinedAmount(int slot) {
        return VillagerItemFilterData.combinedAmountForSlot(filterStack(), slot);
    }

    public boolean isAmountEntry(int slot) {
        return slot >= 0
                && slot < GHOST_SLOT_COUNT
                && VillagerItemFilterData.isAmountEntry(this.ghostInventory.getItem(slot));
    }

    public boolean isEditingHeldFilter() {
        return this.playerInventory.selected == this.editingHotbarSlot
                && VillagerRetaliationItems.isItemFilter(filterStack());
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive() && isEditingHeldFilter();
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (!isEditingHeldFilter()) {
            return;
        }
        if (slotId >= 0 && slotId < GHOST_SLOT_COUNT) {
            if (clickType == ClickType.QUICK_MOVE) {
                setGhostEntry(slotId, ItemStack.EMPTY);
            } else if (clickType == ClickType.PICKUP) {
                setGhostEntry(slotId, getCarried());
            }
            return;
        }
        if (isHeldFilterMenuSlot(slotId)) {
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!isEditingHeldFilter() || index < 0 || index >= this.slots.size()) {
            return ItemStack.EMPTY;
        }
        if (index < GHOST_SLOT_COUNT) {
            setGhostEntry(index, ItemStack.EMPTY);
            return ItemStack.EMPTY;
        }
        if (isHeldFilterMenuSlot(index)) {
            return ItemStack.EMPTY;
        }
        ItemStack source = this.slots.get(index).getItem();
        if (source.isEmpty()) {
            return ItemStack.EMPTY;
        }
        for (int ghostSlot = 0; ghostSlot < GHOST_SLOT_COUNT; ghostSlot++) {
            if (this.ghostInventory.getItem(ghostSlot).isEmpty()
                    && setGhostEntry(ghostSlot, source)) {
                break;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    protected boolean moveItemStackTo(ItemStack stack, int startIndex, int endIndex, boolean reverseDirection) {
        return false;
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return slot.container == this.playerInventory && !isHeldFilterMenuSlot(this.slots.indexOf(slot));
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        int menuSlot = this.slots.indexOf(slot);
        return slot.container == this.playerInventory && !isHeldFilterMenuSlot(menuSlot);
    }

    /**
     * Updates a ghost entry from a client-side item source such as a drag-and-drop action.
     * The server validates that this is still the held filter before applying the update.
     */
    public boolean setGhostEntry(int slot, ItemStack entry) {
        if (slot < 0 || slot >= GHOST_SLOT_COUNT || !isEditingHeldFilter()) {
            return false;
        }
        ItemStack normalized = entry == null || entry.isEmpty() ? ItemStack.EMPTY : entry.copyWithCount(1);
        ItemStack filter = filterStack();
        boolean changed = VillagerItemFilterData.setEntry(filter, slot, normalized);
        this.ghostInventory.setItem(slot, VillagerItemFilterData.entry(filter, slot));
        this.ghostInventory.setChanged();
        if (changed) {
            markFilterChanged();
        }
        return changed;
    }

    public VillagerItemFilterData.AmountAdjustment adjustEntryAmount(int slot, int delta) {
        if (!isEditingHeldFilter()
                || slot < 0
                || slot >= GHOST_SLOT_COUNT
                || (delta != -100 && delta != -10 && delta != -5 && delta != -1
                && delta != 1 && delta != 5 && delta != 10 && delta != 100)) {
            return new VillagerItemFilterData.AmountAdjustment(false, 0, 0, false, false);
        }
        VillagerItemFilterData.AmountAdjustment adjustment =
                VillagerItemFilterData.adjustAmount(filterStack(), slot, delta);
        if (adjustment.changed()) {
            markFilterChanged();
        }
        return adjustment;
    }

    private void markFilterChanged() {
        this.playerInventory.setChanged();
        broadcastChanges();
    }

    private ItemStack filterStack() {
        ItemStack stack = this.playerInventory.getItem(this.editingHotbarSlot);
        return VillagerRetaliationItems.isItemFilter(stack) ? stack : ItemStack.EMPTY;
    }

    private boolean isHeldFilterMenuSlot(int menuSlot) {
        return menuSlot == PLAYER_HOTBAR_START + this.editingHotbarSlot;
    }

    private void addPlayerSlots(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(
                        inventory,
                        9 + row * 9 + column,
                        PLAYER_X + column * SLOT_SIZE,
                        PLAYER_Y + row * SLOT_SIZE));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, PLAYER_X + column * SLOT_SIZE, HOTBAR_Y));
        }
    }

    private static ItemStack decodeOpeningStack(RegistryFriendlyByteBuf data) {
        return data == null || !data.isReadable() ? ItemStack.EMPTY : ItemStack.STREAM_CODEC.decode(data);
    }

    private static final class GhostSlot extends Slot {
        private GhostSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
