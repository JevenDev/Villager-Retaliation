package com.jvn.villagerretaliation.client.interaction;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

public final class VillagerInteractionHudHider {
    private VillagerInteractionHudHider() {
    }

    public static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (!ClientVillagerConversationState.active()) {
            return;
        }

        if (VanillaGuiLayers.CHAT.equals(event.getName())
                && Minecraft.getInstance().screen instanceof VillagerInteractionScreen screen) {
            screen.renderBackdropBehindChat(event.getGuiGraphics());
        }

        if (VanillaGuiLayers.CROSSHAIR.equals(event.getName())) {
            event.setCanceled(true);
        }
    }
}
