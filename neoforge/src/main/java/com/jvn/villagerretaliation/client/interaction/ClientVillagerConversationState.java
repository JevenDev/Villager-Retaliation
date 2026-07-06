package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.Mth;

public final class ClientVillagerConversationState {
    private static final int MIN_CAMERA_RELEASE_TICKS = 8;

    private static final Map<Integer, String> SPEAKER_LABELS = new HashMap<>();
    private static int focusedVillagerEntityId = -1;
    private static int cameraFocusTicks;
    private static int cameraReleaseTicks;
    private static int cameraReleaseTotalTicks = MIN_CAMERA_RELEASE_TICKS;
    private static boolean forceCameraTowardsVillager;
    private static boolean releaseUsesForcedCameraZoom;

    private ClientVillagerConversationState() {
    }

    public static void start(int entityId, boolean forceCamera) {
        focusedVillagerEntityId = entityId;
        cameraFocusTicks = 0;
        cameraReleaseTicks = 0;
        releaseUsesForcedCameraZoom = false;
        forceCameraTowardsVillager = forceCamera;
    }

    public static void retarget(int entityId, boolean forceCamera) {
        focusedVillagerEntityId = entityId;
        cameraReleaseTicks = 0;
        releaseUsesForcedCameraZoom = false;
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

    public static boolean usesForcedCameraZoom() {
        return active() ? forceCameraTowardsVillager : cameraReleaseTicks > 0 && releaseUsesForcedCameraZoom;
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
            if (cameraReleaseTicks <= 0) {
                releaseUsesForcedCameraZoom = false;
            }
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
        float progress = Mth.clamp(
                (cameraReleaseTicks - partialTick) / (float) Math.max(1, cameraReleaseTotalTicks),
                0.0F,
                1.0F);
        return progress * progress * (3.0F - 2.0F * progress);
    }

    public static void clear() {
        SPEAKER_LABELS.clear();
        focusedVillagerEntityId = -1;
        if (cameraFocusTicks > 0) {
            releaseUsesForcedCameraZoom = forceCameraTowardsVillager;
            cameraReleaseTotalTicks = cameraReleaseDurationTicks();
            cameraReleaseTicks = cameraReleaseTotalTicks;
        } else {
            releaseUsesForcedCameraZoom = false;
        }
        forceCameraTowardsVillager = false;
        cameraFocusTicks = 0;
    }

    private static int cameraReleaseDurationTicks() {
        return Math.max(MIN_CAMERA_RELEASE_TICKS, VillagerRetaliationConfig.DIALOGUE_CAMERA_TRANSITION_TICKS.get());
    }
}
