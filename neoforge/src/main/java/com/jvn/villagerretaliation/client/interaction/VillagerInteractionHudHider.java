package com.jvn.villagerretaliation.client.interaction;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

public final class VillagerInteractionHudHider {
    private VillagerInteractionHudHider() {
    }

    public static void onRenderGuiPre(RenderGuiEvent.Pre event) {
        if (Minecraft.getInstance().screen instanceof VillagerInteractionSessionScreen) {
            event.setCanceled(true);
        }
    }

    public static void onRenderGuiLayerPre(RenderGuiLayerEvent.Pre event) {
        if (Minecraft.getInstance().screen instanceof VillagerInteractionSessionScreen) {
            event.setCanceled(true);
            return;
        }

        boolean chatLayer = VanillaGuiLayers.CHAT.equals(event.getName());
        boolean exitAnimationRunning = VillagerInteractionExperimentalChrome.exitAnimationRunning();
        if (chatLayer) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof VillagerInteractionScreen screen) {
                event.setCanceled(true);
                return;
            }
        }

        if (!ClientVillagerConversationState.active() && !exitAnimationRunning) {
            return;
        }

        if (ClientVillagerConversationState.active() && VanillaGuiLayers.CROSSHAIR.equals(event.getName())) {
            event.setCanceled(true);
        }
    }

    public static void onRenderGuiLayerPost(RenderGuiLayerEvent.Post event) {
        if (!VanillaGuiLayers.SAVING_INDICATOR.equals(event.getName())) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof VillagerInteractionScreen screen) {
            screen.renderPositionedHudChat(event.getGuiGraphics());
            return;
        }

        if (!VillagerInteractionExperimentalChrome.exitAnimationRunning()) {
            return;
        }

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
}
