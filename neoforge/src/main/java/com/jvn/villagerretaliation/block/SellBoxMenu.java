package com.jvn.villagerretaliation.block;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.inventory.VillagerRetaliationMenus;
import com.jvn.villagerretaliation.sell.SellBoxMarketSyncService;
import com.jvn.villagerretaliation.sell.VillageSellMarket;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class SellBoxMenu extends AbstractContainerMenu {
    public static final int SELL_BUTTON = 0;
    public static final int COLLECT_BUTTON = 1;
    private static final int PLAYER_INVENTORY_START = 1;
    private static final int TOTAL_SLOT_COUNT = 37;
    private static final Map<SellBoxBlockEntity, Set<ServerPlayer>> VIEWERS = new WeakHashMap<>();

    private final Container container;

    public SellBoxMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf data) {
        this(containerId, playerInventory, new SimpleContainer(SellBoxBlockEntity.SLOT_COUNT));
    }

    public SellBoxMenu(int containerId, Inventory playerInventory, Container container) {
        super(VillagerRetaliationMenus.SELL_BOX.get(), containerId);
        checkContainerSize(container, SellBoxBlockEntity.SLOT_COUNT);
        this.container = container;
        container.startOpen(playerInventory.player);
        if (container instanceof SellBoxBlockEntity sellBox
                && playerInventory.player instanceof ServerPlayer serverPlayer) {
            VIEWERS.computeIfAbsent(
                    sellBox,
                    ignored -> Collections.newSetFromMap(new WeakHashMap<>()))
                    .add(serverPlayer);
        }
        addSlot(new SellSlot(container, 0, 80, 22, playerInventory.player));
        addPlayerInventory(playerInventory);
        addPlayerHotbar(playerInventory);
        if (playerInventory.player instanceof ServerPlayer player) {
            sync(player);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(container instanceof SellBoxBlockEntity sellBox) || !stillValid(player)) {
            return false;
        }
        boolean handled = switch (id) {
            case SELL_BUTTON -> sellBox.sellPending();
            case COLLECT_BUTTON -> sellBox.collect(player) > 0;
            default -> false;
        };
        return handled;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId == 0
                && clickType == ClickType.PICKUP
                && !getCarried().isEmpty()
                && container instanceof SellBoxBlockEntity sellBox
                && !sellBox.getItem(0).isEmpty()) {
            if (player.level().isClientSide) {
                return;
            }
            ItemStack carried = getCarried();
            int offeredCount = button == 1 ? 1 : carried.getCount();
            ItemStack offered = carried.copyWithCount(offeredCount);
            ItemStack remainder = sellBox.insertForSale(offered, false);
            int accepted = offeredCount - remainder.getCount();
            if (accepted > 0) {
                carried.shrink(accepted);
                setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
                broadcastFullState();
            }
            return;
        }
        if (slotId == 0
                && clickType == ClickType.SWAP
                && container instanceof SellBoxBlockEntity sellBox
                && !sellBox.getItem(0).isEmpty()) {
            ItemStack hotbarStack = player.getInventory().getItem(button);
            if (hotbarStack.isEmpty()) {
                super.clicked(slotId, button, clickType, player);
                return;
            }
            if (player.level().isClientSide) {
                return;
            }
            ItemStack remainder = sellBox.insertForSale(hotbarStack.copy(), false);
            int accepted = hotbarStack.getCount() - remainder.getCount();
            if (accepted > 0) {
                player.getInventory().setItem(button, remainder);
                broadcastFullState();
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return slot.index != 0 && super.canDragTo(slot);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot source = slots.get(index);
        if (!source.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack original = source.getItem().copy();
        ItemStack sourceStack = source.getItem();
        if (index == 0) {
            if (!moveItemStackTo(sourceStack, PLAYER_INVENTORY_START, TOTAL_SLOT_COUNT, true)) {
                return ItemStack.EMPTY;
            }
        } else if (container instanceof SellBoxBlockEntity sellBox && !player.level().isClientSide) {
            ItemStack remainder = sellBox.insertForSale(sourceStack, false);
            int accepted = sourceStack.getCount() - remainder.getCount();
            if (accepted <= 0) {
                return ItemStack.EMPTY;
            }
            sourceStack.shrink(accepted);
        } else {
            return ItemStack.EMPTY;
        }

        if (sourceStack.isEmpty()) {
            source.setByPlayer(ItemStack.EMPTY);
        } else {
            source.setChanged();
        }
        return original;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
        if (container instanceof SellBoxBlockEntity sellBox && player instanceof ServerPlayer serverPlayer) {
            Set<ServerPlayer> viewers = VIEWERS.get(sellBox);
            if (viewers != null) {
                viewers.remove(serverPlayer);
                if (viewers.isEmpty()) {
                    VIEWERS.remove(sellBox);
                }
            }
        }
    }

    public SellBoxBlockEntity sellBox() {
        return this.container instanceof SellBoxBlockEntity sellBox ? sellBox : null;
    }

    public boolean isContainer(Container candidate) {
        return this.container == candidate;
    }

    public static void syncViewers(SellBoxBlockEntity sellBox) {
        if (sellBox.getLevel() == null || sellBox.getLevel().isClientSide) {
            return;
        }
        Set<ServerPlayer> viewers = VIEWERS.get(sellBox);
        if (viewers == null) {
            return;
        }
        viewers.removeIf(player -> !(player.containerMenu instanceof SellBoxMenu menu)
                || !menu.isContainer(sellBox));
        for (ServerPlayer viewer : List.copyOf(viewers)) {
            SellBoxMenu menu = (SellBoxMenu) viewer.containerMenu;
            menu.broadcastFullState();
            menu.sync(viewer);
        }
        if (viewers.isEmpty()) {
            VIEWERS.remove(sellBox);
        }
    }

    public static void syncVillage(ServerLevel level, VillageAllegianceId village) {
        if (level == null || village == null) {
            return;
        }
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        Optional<VillageAllegianceId> canonical = registry.canonical(village);
        if (canonical.isEmpty()) {
            return;
        }
        for (Player player : level.players()) {
            if (player instanceof ServerPlayer serverPlayer
                    && serverPlayer.containerMenu instanceof SellBoxMenu menu
                    && menu.container instanceof SellBoxBlockEntity openBox
                    && VillageSellMarket.resolveVillage(registry, level, openBox.getBlockPos())
                            .filter(canonical.get()::equals)
                            .isPresent()) {
                menu.broadcastFullState();
                menu.sync(serverPlayer);
            }
        }
    }

    public void sync(ServerPlayer player) {
        com.jvn.villagerretaliation.network.SellBoxSyncPayload.send(
                player,
                this.containerId,
                (SellBoxBlockEntity) container);
        SellBoxMarketSyncService.markSynced(player, (SellBoxBlockEntity) container, this.containerId);
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory inventory) {
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        }
    }

    private static final class SellSlot extends Slot {
        private final Player player;

        private SellSlot(Container container, int slot, int x, int y, Player player) {
            super(container, slot, x, y);
            this.player = player;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !(container instanceof SellBoxBlockEntity sellBox)
                    || !(player.level() instanceof ServerLevel serverLevel)
                    || VillageSellMarket.canAcceptSale(serverLevel, sellBox.getBlockPos(), stack);
        }
    }
}
