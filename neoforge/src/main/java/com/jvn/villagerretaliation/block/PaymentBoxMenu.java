package com.jvn.villagerretaliation.block;

import com.jvn.villagerretaliation.inventory.VillagerRetaliationMenus;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyResources;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class PaymentBoxMenu extends AbstractContainerMenu {
    private static final int ROW_COUNT = 3;
    private static final int SLOT_SIZE = 18;
    private static final int BOX_SLOT_COUNT = PaymentBoxBlockEntity.SLOT_COUNT;
    private static final int PLAYER_INVENTORY_SLOT_COUNT = 27;
    private static final int PLAYER_HOTBAR_SLOT_COUNT = 9;
    private static final int PLAYER_INVENTORY_START = BOX_SLOT_COUNT;
    private static final int PLAYER_HOTBAR_START = PLAYER_INVENTORY_START + PLAYER_INVENTORY_SLOT_COUNT;
    private static final int TOTAL_SLOT_COUNT = PLAYER_HOTBAR_START + PLAYER_HOTBAR_SLOT_COUNT;
    private static final int BOX_X = 8;
    private static final int BOX_Y = 18;
    private static final int PLAYER_INVENTORY_X = 8;
    private static final int PLAYER_INVENTORY_Y = 85;
    private static final int PLAYER_HOTBAR_Y = 143;

    private final Container container;
    private final MinecraftServer server;

    public PaymentBoxMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf data) {
        this(containerId, playerInventory, new SimpleContainer(BOX_SLOT_COUNT));
    }

    public PaymentBoxMenu(int containerId, Inventory playerInventory, Container container) {
        super(VillagerRetaliationMenus.PAYMENT_BOX.get(), containerId);
        checkContainerSize(container, BOX_SLOT_COUNT);
        this.container = container;
        this.server = playerInventory.player.level().getServer();
        container.startOpen(playerInventory.player);
        addPaymentSlots(container);
        addPlayerInventory(playerInventory);
        addPlayerHotbar(playerInventory);
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= this.slots.size()) {
            return ItemStack.EMPTY;
        }

        Slot sourceSlot = this.slots.get(index);
        if (!sourceSlot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack originalStack = sourceStack.copy();
        if (index < BOX_SLOT_COUNT) {
            if (!moveItemStackTo(sourceStack, PLAYER_INVENTORY_START, TOTAL_SLOT_COUNT, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!VillagerCurrencyResources.isCurrency(this.server, sourceStack) || !moveItemStackTo(sourceStack, 0, BOX_SLOT_COUNT, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (sourceStack.isEmpty()) {
            sourceSlot.setByPlayer(ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }

        return originalStack;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }

    public int rowCount() {
        return ROW_COUNT;
    }

    boolean isContainer(Container container) {
        return this.container == container;
    }

    private void addPaymentSlots(Container container) {
        for (int row = 0; row < ROW_COUNT; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new PaymentSlot(
                        container,
                        column + row * 9,
                        BOX_X + column * SLOT_SIZE,
                        BOX_Y + row * SLOT_SIZE,
                        this.server
                ));
            }
        }
    }

    private void addPlayerInventory(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(
                        playerInventory,
                        column + row * 9 + 9,
                        PLAYER_INVENTORY_X + column * SLOT_SIZE,
                        PLAYER_INVENTORY_Y + row * SLOT_SIZE
                ));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(
                    playerInventory,
                    column,
                    PLAYER_INVENTORY_X + column * SLOT_SIZE,
                    PLAYER_HOTBAR_Y
            ));
        }
    }

    private static final class PaymentSlot extends Slot {
        private final MinecraftServer server;

        private PaymentSlot(Container container, int slot, int x, int y, MinecraftServer server) {
            super(container, slot, x, y);
            this.server = server;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return VillagerCurrencyResources.isCurrency(this.server, stack);
        }
    }
}
