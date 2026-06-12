package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.item.HiredStorageClipboardItem;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import com.jvn.villagerretaliation.network.ClipboardModeChangePayload;
import com.jvn.villagerretaliation.network.ClipboardWorkAreaDraftPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class ClipboardModeClient {
    private ClipboardModeClient() {
    }

    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (event.isCanceled()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || minecraft.screen != null
                || !isHoldingClipboard(minecraft)) {
            return;
        }

        ItemStack clipboard = heldClipboard(minecraft);
        int delta = event.getScrollDeltaY() > 0.0D ? 1 : event.getScrollDeltaY() < 0.0D ? -1 : 0;
        if (delta == 0) {
            return;
        }
        if (Screen.hasControlDown()
                && HiredStorageClipboardItem.mode(clipboard).isStorageAssignmentMode()) {
            PacketDistributor.sendToServer(new ClipboardModeChangePayload(delta, -1, true));
            event.setCanceled(true);
            return;
        }
        if (HiredStorageClipboardItem.mode(clipboard) != HiredStorageClipboardItem.ClipboardMode.SET_WORK_AREA) {
            return;
        }

        ClipboardWorkAreaDraftPayload.Action action;
        if (Screen.hasControlDown() && Screen.hasAltDown()) {
            action = delta > 0
                    ? ClipboardWorkAreaDraftPayload.Action.EXPAND_VERTICAL
                    : ClipboardWorkAreaDraftPayload.Action.CONTRACT_VERTICAL;
        } else if (Screen.hasControlDown()) {
            action = delta > 0
                    ? ClipboardWorkAreaDraftPayload.Action.EXPAND_HORIZONTAL
                    : ClipboardWorkAreaDraftPayload.Action.CONTRACT_HORIZONTAL;
        } else if (Screen.hasShiftDown()) {
            action = delta > 0
                    ? ClipboardWorkAreaDraftPayload.Action.MOVE_UP
                    : ClipboardWorkAreaDraftPayload.Action.MOVE_DOWN;
        } else {
            Direction direction = minecraft.player.getDirection();
            if (Screen.hasAltDown()) {
                direction = direction.getClockWise();
            }
            if (delta < 0) {
                direction = direction.getOpposite();
            }
            action = actionFor(direction);
        }
        PacketDistributor.sendToServer(new ClipboardWorkAreaDraftPayload(action, 1));
        event.setCanceled(true);
    }

    public static void onMouseButtonPressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT
                || !(event.getScreen() instanceof AbstractContainerScreen<?> screen)) {
            return;
        }
        Slot slot = screen.getSlotUnderMouse();
        if (slot == null || !VillagerRetaliationItems.isClipboard(slot.getItem())) {
            return;
        }
        int menuSlotIndex = screen.getMenu().slots.indexOf(slot);
        if (menuSlotIndex < 0) {
            return;
        }
        PacketDistributor.sendToServer(new ClipboardModeChangePayload(1, menuSlotIndex));
        event.setCanceled(true);
    }

    private static ClipboardWorkAreaDraftPayload.Action actionFor(Direction direction) {
        return switch (direction) {
            case NORTH -> ClipboardWorkAreaDraftPayload.Action.MOVE_NORTH;
            case SOUTH -> ClipboardWorkAreaDraftPayload.Action.MOVE_SOUTH;
            case EAST -> ClipboardWorkAreaDraftPayload.Action.MOVE_EAST;
            case WEST -> ClipboardWorkAreaDraftPayload.Action.MOVE_WEST;
            case UP -> ClipboardWorkAreaDraftPayload.Action.MOVE_UP;
            case DOWN -> ClipboardWorkAreaDraftPayload.Action.MOVE_DOWN;
        };
    }

    private static boolean isHoldingClipboard(Minecraft minecraft) {
        return !heldClipboard(minecraft).isEmpty();
    }

    private static ItemStack heldClipboard(Minecraft minecraft) {
        ItemStack mainHand = minecraft.player.getMainHandItem();
        if (VillagerRetaliationItems.isClipboard(mainHand)) {
            return mainHand;
        }
        ItemStack offhand = minecraft.player.getOffhandItem();
        return VillagerRetaliationItems.isClipboard(offhand) ? offhand : ItemStack.EMPTY;
    }
}
