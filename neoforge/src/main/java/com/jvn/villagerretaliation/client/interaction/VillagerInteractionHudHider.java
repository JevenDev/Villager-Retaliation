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

        if (VanillaGuiLayers.CHAT.equals(event.getName())) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof VillagerInteractionScreen) {
                event.setCanceled(true);
                return;
            }
        }

        if (!ClientVillagerConversationState.active()) {
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
        if (minecraft.screen instanceof VillagerInteractionScreen) {
            return;
        }
    }
}
