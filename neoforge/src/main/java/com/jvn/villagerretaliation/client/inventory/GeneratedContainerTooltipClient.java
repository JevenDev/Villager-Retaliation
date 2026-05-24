package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.network.GeneratedContainerTooltipPayload;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public final class GeneratedContainerTooltipClient {
    private static int generatedContainerId = -1;

    private GeneratedContainerTooltipClient() {
    }

    public static void accept(GeneratedContainerTooltipPayload payload) {
        generatedContainerId = payload.generated() ? payload.containerId() : -1;
    }

    public static void onItemTooltip(ItemTooltipEvent event) {
        if (event.getItemStack().isEmpty() || !isHoveringGeneratedContainerSlot()) {
            return;
        }

        List<Component> tooltip = event.getToolTip();
        Component marker = Component.translatable("villagerretaliation.tooltip.world_generated")
                .withStyle(ChatFormatting.GRAY);
        if (!tooltip.contains(marker)) {
            tooltip.add(marker);
        }
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.containerMenu.containerId != generatedContainerId) {
            generatedContainerId = -1;
        }
    }

    private static boolean isHoveringGeneratedContainerSlot() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || minecraft.player.containerMenu.containerId != generatedContainerId
                || !(minecraft.screen instanceof AbstractContainerScreen<?> screen)) {
            return false;
        }

        Slot hoveredSlot = screen.getSlotUnderMouse();
        return hoveredSlot != null
                && hoveredSlot.hasItem()
                && hoveredSlot.container != minecraft.player.getInventory()
                && !(hoveredSlot.container instanceof Inventory);
    }
}
