package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.item.VillagerAttributeFilterData;
import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
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

/** Server-authoritative editor for a single selected item attribute. */
public final class VillagerAttributeFilterMenu extends AbstractContainerMenu
        implements VillagerFilterPolicyMenu {
    public static final int REFERENCE_SLOT = 0;
    private static final int PLAYER_INVENTORY_COUNT = 27;
    private static final int PLAYER_HOTBAR_COUNT = 9;
    private static final int PLAYER_SLOT_START = 1;
    private static final int PLAYER_HOTBAR_START = PLAYER_SLOT_START + PLAYER_INVENTORY_COUNT;
    private static final int SLOT_SIZE = 18;

    private final Inventory playerInventory;
    private final SimpleContainer referenceInventory = new SimpleContainer(1);
    private final ItemStack contentHolder;

    public VillagerAttributeFilterMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf data) {
        this(containerId, playerInventory, decodeOpeningStack(data));
    }

    public VillagerAttributeFilterMenu(int containerId, Inventory playerInventory, ItemStack openingStack) {
        super(VillagerRetaliationMenus.ATTRIBUTE_FILTER.get(), containerId);
        this.playerInventory = playerInventory;
        ItemStack selected = playerInventory.getSelected();
        this.contentHolder = VillagerRetaliationItems.isAttributeFilter(selected) ? selected : openingStack;
        addSlot(new ReferenceSlot(this.referenceInventory, 0, 8, 18));
        addPlayerSlots(playerInventory);
    }

    @Override
    public VillagerFilterPolicy.Policy filterPolicy() {
        return VillagerFilterPolicy.read(this.contentHolder);
    }

    @Override
    public boolean applyPolicyChange(VillagerFilterPolicy.PolicyField field, int value) {
        if (!isEditingHeldFilter()) {
            return false;
        }
        boolean changed = VillagerFilterPolicy.applyChange(this.contentHolder, field, value);
        if (changed) {
            this.playerInventory.setChanged();
            broadcastChanges();
        }
        return changed;
    }

    @Override
    public void applyClientPolicyChange(VillagerFilterPolicy.PolicyField field, int value) {
        if (isEditingHeldFilter()) {
            VillagerFilterPolicy.applyChange(this.contentHolder, field, value);
        }
    }

    public ItemStack referenceItem() {
        return this.referenceInventory.getItem(0);
    }

    public VillagerAttributeFilterData.Configuration configuration() {
        return VillagerAttributeFilterData.read(this.contentHolder);
    }

    public void setClientSelection(VillagerAttributeFilterData.Attribute attribute, boolean inverted) {
        VillagerAttributeFilterData.toggleSelected(this.contentHolder, attribute);
    }

    public boolean isEditingHeldFilter() {
        return VillagerRetaliationItems.isAttributeFilter(this.contentHolder)
                && this.playerInventory.getSelected() == this.contentHolder;
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
        if (slotId == REFERENCE_SLOT) {
            if (clickType == ClickType.QUICK_MOVE) {
                setReference(ItemStack.EMPTY);
            } else if (clickType == ClickType.PICKUP) {
                setReference(getCarried());
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
        if (index == REFERENCE_SLOT) {
            setReference(ItemStack.EMPTY);
            return ItemStack.EMPTY;
        }
        if (isHeldFilterMenuSlot(index)) {
            return ItemStack.EMPTY;
        }
        ItemStack source = this.slots.get(index).getItem();
        if (!source.isEmpty()) {
            setReference(source);
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

    public boolean setReference(ItemStack entry) {
        if (!isEditingHeldFilter()) {
            return false;
        }
        ItemStack normalized = entry == null || entry.isEmpty() ? ItemStack.EMPTY : entry.copyWithCount(1);
        if (ItemStack.matches(this.referenceInventory.getItem(0), normalized)) {
            return false;
        }
        this.referenceInventory.setItem(0, normalized);
        this.referenceInventory.setChanged();
        broadcastChanges();
        return true;
    }

    public boolean select(
            VillagerAttributeFilterData.Attribute attribute,
            boolean inverted,
            Player player) {
        if (!isEditingHeldFilter() || attribute == null || player == null) {
            return false;
        }
        boolean offeredByReference = VillagerAttributeFilterData
                .availableAttributes(referenceItem(), player.level())
                .contains(attribute);
        if (!offeredByReference) {
            return false;
        }
        boolean changed = VillagerAttributeFilterData.toggleSelected(this.contentHolder, attribute);
        if (changed) {
            this.playerInventory.setChanged();
            broadcastChanges();
        }
        return changed;
    }

    private boolean isHeldFilterMenuSlot(int menuSlot) {
        int selectedMenuSlot = PLAYER_HOTBAR_START + this.playerInventory.selected;
        return menuSlot == selectedMenuSlot;
    }

    private void addPlayerSlots(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, 9 + row * 9 + column,
                        8 + column * SLOT_SIZE, 84 + row * SLOT_SIZE));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * SLOT_SIZE, 142));
        }
    }

    private static ItemStack decodeOpeningStack(RegistryFriendlyByteBuf data) {
        return data == null || !data.isReadable() ? ItemStack.EMPTY : ItemStack.STREAM_CODEC.decode(data);
    }

    private static final class ReferenceSlot extends Slot {
        private ReferenceSlot(Container container, int slot, int x, int y) {
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
