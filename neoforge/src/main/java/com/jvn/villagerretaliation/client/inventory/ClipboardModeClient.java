package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import com.jvn.villagerretaliation.network.ClipboardModeChangePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ClipboardModeClient {
    private ClipboardModeClient() {
    }

    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || minecraft.screen != null
                || !Screen.hasControlDown()
                || !isHoldingClipboard(minecraft)) {
            return;
        }

        int delta = event.getScrollDeltaY() > 0.0D ? 1 : event.getScrollDeltaY() < 0.0D ? -1 : 0;
        if (delta == 0) {
            return;
        }
        PacketDistributor.sendToServer(new ClipboardModeChangePayload(delta));
        event.setCanceled(true);
    }

    private static boolean isHoldingClipboard(Minecraft minecraft) {
        ItemStack mainHand = minecraft.player.getMainHandItem();
        ItemStack offhand = minecraft.player.getOffhandItem();
        return VillagerRetaliationItems.isClipboard(mainHand) || VillagerRetaliationItems.isClipboard(offhand);
    }
}
