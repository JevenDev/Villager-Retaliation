package com.jvn.villagerretaliation.client.interaction;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.Mth;

public final class ClientVillagerConversationState {
    private static final int CAMERA_RELEASE_TICKS = 2;

    private static final Map<Integer, String> SPEAKER_LABELS = new HashMap<>();
    private static int focusedVillagerEntityId = -1;
    private static int cameraFocusTicks;
    private static int cameraReleaseTicks;
    private static boolean forceCameraTowardsVillager;

    private ClientVillagerConversationState() {
    }

    public static void start(int entityId, boolean forceCamera) {
        focusedVillagerEntityId = entityId;
        cameraFocusTicks = 0;
        cameraReleaseTicks = 0;
        forceCameraTowardsVillager = forceCamera;
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

    public static boolean forceCameraTowardsVillager() {
        return active() && forceCameraTowardsVillager;
    }

    public static void setForceCameraTowardsVillager(boolean forceCamera) {
        forceCameraTowardsVillager = forceCamera;
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

    public static void rememberSpeakerLabel(int entityId, String speakerLabel) {
        if (speakerLabel == null || speakerLabel.isBlank()) {
            SPEAKER_LABELS.remove(entityId);
            return;
        }
        SPEAKER_LABELS.put(entityId, speakerLabel);
    }

    public static String resolveSpeakerLabel(int entityId) {
        return SPEAKER_LABELS.get(entityId);
    }

    public static void forgetSpeakerLabel(int entityId) {
        SPEAKER_LABELS.remove(entityId);
    }

    public static double cameraReleaseProgress(float partialTick) {
        if (active() || cameraReleaseTicks <= 0) {
            return 1.0D;
        }
        return Mth.clamp((cameraReleaseTicks - partialTick) / CAMERA_RELEASE_TICKS, 0.0F, 1.0F);
    }

    public static void clear() {
        focusedVillagerEntityId = -1;
        forceCameraTowardsVillager = false;
        if (cameraFocusTicks > 0) {
            cameraReleaseTicks = CAMERA_RELEASE_TICKS;
        }
        cameraFocusTicks = 0;
    }
}
