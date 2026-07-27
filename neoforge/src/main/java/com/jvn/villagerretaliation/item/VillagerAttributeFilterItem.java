package com.jvn.villagerretaliation.item;

import com.jvn.villagerretaliation.inventory.VillagerAttributeFilterMenu;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public final class VillagerAttributeFilterItem extends Item implements MenuProvider {
    public VillagerAttributeFilterItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.addAll(VillagerAttributeFilterData.tooltip(stack));
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
        return new VillagerAttributeFilterMenu(containerId, inventory, player.getMainHandItem());
    }

    @Override
    public Component getDisplayName() {
        return getDescription();
    }
}
