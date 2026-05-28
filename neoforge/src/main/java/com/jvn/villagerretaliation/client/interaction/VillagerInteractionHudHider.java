package com.jvn.villagerretaliation.client.interaction;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

public final class VillagerInteractionHudHider {
    private VillagerInteractionHudHider() {
    }

    public static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (!ClientVillagerConversationState.active() && !VillagerInteractionExperimentalChrome.exitAnimationRunning()) {
            return;
        }

        if (VanillaGuiLayers.CHAT.equals(event.getName())
                && Minecraft.getInstance().screen instanceof VillagerInteractionScreen screen) {
            screen.renderPositionedHudChat(event.getGuiGraphics());
            event.setCanceled(true);
            return;
        }

        if (VanillaGuiLayers.CHAT.equals(event.getName())
                && VillagerInteractionExperimentalChrome.exitAnimationRunning()) {
            Minecraft minecraft = Minecraft.getInstance();
            int scaledMouseX = (int) Math.round(minecraft.mouseHandler.xpos()
                    * minecraft.getWindow().getGuiScaledWidth()
                    / minecraft.getWindow().getScreenWidth());
            int scaledMouseY = (int) Math.round(minecraft.mouseHandler.ypos()
                    * minecraft.getWindow().getGuiScaledHeight()
                    / minecraft.getWindow().getScreenHeight());
            VillagerInteractionExperimentalChrome.renderBackdrop(
                    event.getGuiGraphics(),
                    minecraft.getWindow().getGuiScaledWidth(),
                    minecraft.getWindow().getGuiScaledHeight(),
                    0.0F,
                    scaledMouseX,
                    scaledMouseY);
        }

        if (ClientVillagerConversationState.active() && VanillaGuiLayers.CROSSHAIR.equals(event.getName())) {
            event.setCanceled(true);
        }
    }
}
