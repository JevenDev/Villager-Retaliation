package com.jvn.villagerretaliation.client.duel;

import com.jvn.villagerretaliation.network.DuelInventoryStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

public final class DuelInventoryClientState {
    private static boolean active;
    private static boolean assignedLoadout;

    private DuelInventoryClientState() {}

    public static void accept(DuelInventoryStatePayload payload) {
        boolean closingAssignedInventory = assignedLoadout()
                && !payload.active()
                && Minecraft.getInstance().screen instanceof InventoryScreen;
        active = payload.active();
        assignedLoadout = payload.active() && payload.assignedLoadout();
        if (closingAssignedInventory) Minecraft.getInstance().setScreen(null);
    }

    public static boolean active() {
        return active;
    }

    public static boolean assignedLoadout() {
        return active && assignedLoadout;
    }

    public static boolean allowsInventoryClick(int slotId, ClickType clickType) {
        if (!active) return true;
        if (assignedLoadout || slotId < InventoryMenu.ARMOR_SLOT_START || slotId > InventoryMenu.SHIELD_SLOT) {
            return false;
        }
        return clickType != ClickType.THROW
                && clickType != ClickType.CLONE
                && clickType != ClickType.QUICK_CRAFT;
    }

    public static boolean visibleAssignedSlot(int slotId) {
        return !assignedLoadout()
                || slotId >= InventoryMenu.ARMOR_SLOT_START && slotId < InventoryMenu.ARMOR_SLOT_END
                || slotId >= InventoryMenu.USE_ROW_SLOT_START && slotId < InventoryMenu.USE_ROW_SLOT_END
                || slotId == InventoryMenu.SHIELD_SLOT;
    }

    public static void onScreenClosing(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof DuelInventoryScreenAccess screen) {
            screen.villagerretaliation$restoreDuelInventorySlots();
        }
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        active = false;
        assignedLoadout = false;
    }
}
