package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import com.jvn.villagerretaliation.inventory.VillagerItemFilterMenu;
import com.jvn.villagerretaliation.network.ItemFilterModeChangePayload;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class ItemFilterModeClient {
    private ItemFilterModeClient() {
    }

    public static void onMouseButtonPressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.isCanceled()
                || event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT
                || !(event.getScreen() instanceof AbstractContainerScreen<?> screen)) {
            return;
        }
        Slot slot = screen.getSlotUnderMouse();
        if (slot == null
                || !VillagerRetaliationItems.isItemFilter(slot.getItem())
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
}
