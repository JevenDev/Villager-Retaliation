package com.jvn.villagerretaliation.client.interaction;

import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

public final class VillagerInteractionHudHider {
    private VillagerInteractionHudHider() {
    }

    public static void onRenderGuiPre(RenderGuiEvent.Pre event) {
        event.getGuiGraphics().setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void onRenderGuiLayerPre(RenderGuiLayerEvent.Pre event) {
        if (VanillaGuiLayers.CAMERA_OVERLAYS.equals(event.getName())) {
            event.getGuiGraphics().setColor(1.0F, 1.0F, 1.0F, 1.0F);
            return;
        }

        float alpha = VillagerInteractionVisibilityFade.alpha();
        if (alpha <= 0.001F) {
            event.setCanceled(true);
            return;
        }
        if (alpha < 0.999F) {
            event.getGuiGraphics().setColor(1.0F, 1.0F, 1.0F, alpha);
        }

        if (!ClientVillagerConversationState.active()) {
            return;
        }

        if (ClientVillagerConversationState.active() && VanillaGuiLayers.CROSSHAIR.equals(event.getName())) {
            event.setCanceled(true);
        }
    }

    public static void onRenderGuiLayerPost(RenderGuiLayerEvent.Post event) {
        event.getGuiGraphics().setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        event.getGuiGraphics().setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
