package com.jvn.villagerretaliation.item;

import com.jvn.villagerretaliation.inventory.VillagerItemFilterMenu;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public final class VillagerItemFilterItem extends Item implements MenuProvider {
    public VillagerItemFilterItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.addAll(VillagerItemFilterData.tooltip(stack));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack heldItem = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(heldItem);
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(this, buffer -> ItemStack.STREAM_CODEC.encode(buffer, heldItem));
        }
        return InteractionResultHolder.sidedSuccess(heldItem, level.isClientSide);
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new VillagerItemFilterMenu(containerId, inventory, player.getMainHandItem());
    }

    @Override
    public Component getDisplayName() {
        return getDescription();
    }

    public static void handleModeChange(
            ServerPlayer player,
            int menuSlotIndex,
            int modeId) {
        if (modeId < -1 || modeId > 1) {
            return;
        }
        VillagerItemFilterData.Mode requestedMode = modeId == 0
                ? VillagerItemFilterData.Mode.ALLOWLIST
                : modeId == 1 ? VillagerItemFilterData.Mode.DENYLIST : null;

        ItemStack filter;
        Slot hoveredSlot = null;
        if (menuSlotIndex == -1) {
            if (!(player.containerMenu instanceof VillagerItemFilterMenu menu) || !menu.isEditingHeldFilter()) {
                return;
            }
            filter = player.getMainHandItem();
        } else {
            if (menuSlotIndex < 0 || menuSlotIndex >= player.containerMenu.slots.size()) {
                return;
            }
            if (player.containerMenu instanceof VillagerItemFilterMenu
                    && menuSlotIndex < VillagerItemFilterMenu.GHOST_SLOT_COUNT) {
                return;
            }
            hoveredSlot = player.containerMenu.getSlot(menuSlotIndex);
            filter = hoveredSlot.getItem();
            if (requestedMode != null) {
                return;
            }
        }
        if (!VillagerRetaliationItems.isItemFilter(filter)) {
            return;
        }
        if (requestedMode == null && filter.getCount() != 1) {
            return;
        }

        VillagerItemFilterData.Mode nextMode = requestedMode == null
                ? VillagerItemFilterData.mode(filter).opposite()
                : requestedMode;
        if (VillagerFilterPolicy.hasStoredPolicy(filter)) {
            VillagerFilterPolicy.applyChange(
                    filter,
                    VillagerFilterPolicy.PolicyField.LIST_MODE,
                    nextMode == VillagerItemFilterData.Mode.DENYLIST ? 1 : 0);
        } else {
            VillagerItemFilterData.setMode(filter, nextMode);
        }
        if (hoveredSlot != null) {
            hoveredSlot.setChanged();
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    public static void handleCombinationChange(ServerPlayer player, int combinationId) {
        VillagerItemFilterData.EntryCombination requested =
                VillagerItemFilterData.EntryCombination.fromNetworkId(combinationId);
        if (requested == null
                || !(player.containerMenu instanceof VillagerItemFilterMenu menu)
                || !menu.isEditingHeldFilter()) {
            return;
        }
        ItemStack filter = player.getMainHandItem();
        if (!VillagerRetaliationItems.isItemFilter(filter)) {
            return;
        }
        boolean changed = VillagerFilterPolicy.hasStoredPolicy(filter)
                ? VillagerFilterPolicy.applyChange(
                        filter,
                        VillagerFilterPolicy.PolicyField.COMBINATION,
                        requested == VillagerItemFilterData.EntryCombination.ALL ? 1 : 0)
                : VillagerItemFilterData.setEntryCombination(filter, requested);
        if (!changed) {
            return;
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }
}
