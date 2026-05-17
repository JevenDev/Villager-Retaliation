package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

public final class VillagerDialogueCameraFocus {
    private VillagerDialogueCameraFocus() {
    }

    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (!VillagerRetaliationConfig.ENABLE_DIALOGUE_CAMERA_FOCUS.get()
                || !ClientVillagerConversationState.cameraActive()) {
            return;
        }

        double zoomAmount = VillagerRetaliationConfig.DIALOGUE_CAMERA_ZOOM_AMOUNT.get();
        if (zoomAmount <= 0.0D) {
            return;
        }

        int transitionTicks = Math.max(1, VillagerRetaliationConfig.DIALOGUE_CAMERA_TRANSITION_TICKS.get());
        float activeProgress = (ClientVillagerConversationState.cameraFocusTicks() + (float) event.getPartialTick()) / transitionTicks;
        double progress = Minecraft.getInstance().screen instanceof VillagerInteractionScreen
                ? activeProgress
                : ClientVillagerConversationState.cameraReleaseProgress((float) event.getPartialTick());
        double easedProgress = smoothstep(Mth.clamp(progress, 0.0D, 1.0D));
        double zoomMultiplier = 1.0D - zoomAmount * easedProgress;
        event.setFOV(event.getFOV() * zoomMultiplier);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        ClientVillagerConversationState.tickCameraRelease();
    }

    private static double smoothstep(double value) {
        return value * value * (3.0D - 2.0D * value);
    }
}
