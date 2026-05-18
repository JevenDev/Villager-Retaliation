package com.jvn.villagerretaliation.client.interaction;

import net.minecraft.util.Mth;

public final class ClientVillagerConversationState {
    private static final int CAMERA_RELEASE_TICKS = 2;

    private static int focusedVillagerEntityId = -1;
    private static String responseText = "Choose an option.";
    private static int cameraFocusTicks;
    private static int cameraReleaseTicks;

    private ClientVillagerConversationState() {
    }

    public static void start(int entityId, String initialResponseText) {
        focusedVillagerEntityId = entityId;
        responseText = initialResponseText == null || initialResponseText.isBlank() ? "Choose an option." : initialResponseText;
        cameraFocusTicks = 0;
        cameraReleaseTicks = 0;
    }

    public static void setResponseText(String text) {
        responseText = text;
    }

    public static String responseText() {
        return responseText;
    }

    public static int focusedVillagerEntityId() {
        return focusedVillagerEntityId;
    }

    public static boolean active() {
        return focusedVillagerEntityId >= 0;
    }

    public static boolean cameraActive() {
        return active() || cameraReleaseTicks > 0;
    }

    public static void tickCameraFocus() {
        if (active()) {
            cameraFocusTicks++;
        }
    }

    public static void tickCameraRelease() {
        if (!active() && cameraReleaseTicks > 0) {
            cameraReleaseTicks--;
        }
    }

    public static int cameraFocusTicks() {
        return cameraFocusTicks;
    }

    public static double cameraReleaseProgress(float partialTick) {
        if (active() || cameraReleaseTicks <= 0) {
            return 1.0D;
        }
        return Mth.clamp((cameraReleaseTicks - partialTick) / CAMERA_RELEASE_TICKS, 0.0F, 1.0F);
    }

    public static void clear() {
        focusedVillagerEntityId = -1;
        responseText = "Choose an option.";
        if (cameraFocusTicks > 0) {
            cameraReleaseTicks = CAMERA_RELEASE_TICKS;
        }
        cameraFocusTicks = 0;
    }
}
