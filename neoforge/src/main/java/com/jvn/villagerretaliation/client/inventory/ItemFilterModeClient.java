package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import com.jvn.villagerretaliation.inventory.VillagerItemFilterMenu;
import com.jvn.villagerretaliation.network.ItemFilterModeChangePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class ItemFilterModeClient {
    private ItemFilterModeClient() {
    }

    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (event.isCanceled() || !Screen.hasControlDown() || event.getScrollDeltaY() == 0.0D) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            return;
        }
        ItemStack filter = heldFilter(minecraft);
        if (filter.isEmpty() || filter.getCount() != 1) {
            return;
        }
        PacketDistributor.sendToServer(new ItemFilterModeChangePayload(-1));
        event.setCanceled(true);
    }

    private static ItemStack heldFilter(Minecraft minecraft) {
        ItemStack mainHand = minecraft.player.getMainHandItem();
        if (isModeFilter(mainHand)) {
            return mainHand;
        }
        ItemStack offhand = minecraft.player.getOffhandItem();
        return isModeFilter(offhand) ? offhand : ItemStack.EMPTY;
    }

    public static void onMouseButtonPressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.isCanceled()
                || event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT
                || !(event.getScreen() instanceof AbstractContainerScreen<?> screen)) {
            return;
        }
        Slot slot = screen.getSlotUnderMouse();
        if (slot == null
                || !isModeFilter(slot.getItem())
                || slot.getItem().getCount() != 1) {
            return;
        }
        int menuSlotIndex = screen.getMenu().slots.indexOf(slot);
        if (menuSlotIndex < 0) {
            return;
        }
        if (screen.getMenu() instanceof VillagerItemFilterMenu
                && menuSlotIndex < VillagerItemFilterMenu.GHOST_SLOT_COUNT) {
            return;
        }
        PacketDistributor.sendToServer(new ItemFilterModeChangePayload(menuSlotIndex));
        event.setCanceled(true);
    }

    private static boolean isModeFilter(ItemStack stack) {
        return VillagerRetaliationItems.isItemFilter(stack)
                || VillagerRetaliationItems.isAttributeFilter(stack);
    }
}
