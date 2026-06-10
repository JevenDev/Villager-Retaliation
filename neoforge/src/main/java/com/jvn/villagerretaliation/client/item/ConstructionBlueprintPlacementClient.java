package com.jvn.villagerretaliation.client.item;

import com.jvn.villagerretaliation.item.ConstructionBlueprintItem;
import com.jvn.villagerretaliation.item.ConstructionBlueprintItem.PreviewData;
import com.jvn.villagerretaliation.network.ConstructionBlueprintPlacementPayload;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class ConstructionBlueprintPlacementClient {
    private ConstructionBlueprintPlacementClient() {
    }

    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (event.isCanceled()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }
        Optional<PreviewData> optionalPreview = heldBlueprintPreview(minecraft);
        if (optionalPreview.isEmpty() || optionalPreview.get().locked() || optionalPreview.get().placementLocked()) {
            return;
        }
        int delta = event.getScrollDeltaY() > 0.0D ? 1 : event.getScrollDeltaY() < 0.0D ? -1 : 0;
        if (delta == 0) {
            return;
        }

        ConstructionBlueprintPlacementPayload.Action action;
        if (Screen.hasShiftDown() && Screen.hasAltDown()) {
            action = delta > 0
                    ? ConstructionBlueprintPlacementPayload.Action.ROTATE_CLOCKWISE
                    : ConstructionBlueprintPlacementPayload.Action.ROTATE_COUNTERCLOCKWISE;
        } else if (Screen.hasShiftDown()) {
            action = delta > 0
                    ? ConstructionBlueprintPlacementPayload.Action.MOVE_UP
                    : ConstructionBlueprintPlacementPayload.Action.MOVE_DOWN;
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
        PacketDistributor.sendToServer(new ConstructionBlueprintPlacementPayload(optionalPreview.get().jobId(), action, 1));
        event.setCanceled(true);
    }

    public static void onMouseButtonPressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT
                || !(event.getScreen() instanceof AbstractContainerScreen<?> screen)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        Slot slot = screen.getSlotUnderMouse();
        if (slot == null || slot.container != minecraft.player.getInventory()) {
            return;
        }
        Optional<PreviewData> preview = ConstructionBlueprintItem.previewData(slot.getItem());
        if (preview.isEmpty() || preview.get().locked()) {
            return;
        }
        sendToggleLock(preview.get());
        event.setCanceled(true);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }
        while (ConstructionBlueprintKeyMappings.TOGGLE_PLACEMENT_LOCK.consumeClick()) {
            heldBlueprintPreview(minecraft)
                    .filter(preview -> !preview.locked())
                    .ifPresent(ConstructionBlueprintPlacementClient::sendToggleLock);
        }
    }

    private static void sendToggleLock(PreviewData preview) {
        PacketDistributor.sendToServer(new ConstructionBlueprintPlacementPayload(
                preview.jobId(),
                ConstructionBlueprintPlacementPayload.Action.TOGGLE_LOCK,
                1));
    }

    private static ConstructionBlueprintPlacementPayload.Action actionFor(Direction direction) {
        return switch (direction) {
            case NORTH -> ConstructionBlueprintPlacementPayload.Action.MOVE_NORTH;
            case SOUTH -> ConstructionBlueprintPlacementPayload.Action.MOVE_SOUTH;
            case EAST -> ConstructionBlueprintPlacementPayload.Action.MOVE_EAST;
            case WEST -> ConstructionBlueprintPlacementPayload.Action.MOVE_WEST;
            case UP -> ConstructionBlueprintPlacementPayload.Action.MOVE_UP;
            case DOWN -> ConstructionBlueprintPlacementPayload.Action.MOVE_DOWN;
        };
    }

    private static Optional<PreviewData> heldBlueprintPreview(Minecraft minecraft) {
        ItemStack mainHand = minecraft.player.getMainHandItem();
        Optional<PreviewData> mainPreview = ConstructionBlueprintItem.previewData(mainHand);
        if (mainPreview.isPresent()) {
            return mainPreview;
        }
        return ConstructionBlueprintItem.previewData(minecraft.player.getOffhandItem());
    }
}
