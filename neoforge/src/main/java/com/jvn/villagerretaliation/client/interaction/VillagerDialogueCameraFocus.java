package com.jvn.villagerretaliation.client.interaction;

import com.jvn.toucanlib.client.camera.ToucanCameraBasis;
import com.jvn.toucanlib.client.camera.ToucanCameraFov;
import com.jvn.toucanlib.client.camera.ToucanCameraTargetPoints;
import com.jvn.toucanlib.client.camera.ToucanCameraTurnSmoother;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

public final class VillagerDialogueCameraFocus {
    private static final float CAMERA_TURN_MIN_TICK_LERP = 0.08F;
    private static final float CAMERA_TURN_MAX_TICK_LERP = 0.25F;
    private static final float NORMAL_CAMERA_TURN_STRENGTH = 0.5F;
    private static final double CAMERA_TURN_FIRST_FRAME_DELTA_TICKS = 1.0D / 3.0D;
    private static final double CAMERA_DISTANCE_ZOOM_START = 4.0D;
    private static final double CAMERA_DISTANCE_ZOOM_END = 12.0D;
    private static final double CAMERA_DISTANCE_ZOOM_BONUS = 0.10D;
    private static final double CAMERA_MAX_ZOOM_AMOUNT = 0.45D;

    private static final ToucanCameraTurnSmoother TURN_SMOOTHER = new ToucanCameraTurnSmoother(CAMERA_TURN_FIRST_FRAME_DELTA_TICKS);

    private VillagerDialogueCameraFocus() {
    }

    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (!VillagerRetaliationConfig.ENABLE_DIALOGUE_CAMERA_FOCUS.get()
                || !ClientVillagerConversationState.cameraActive()) {
            return;
        }

        double zoomAmount = distanceAdjustedZoomAmount(event);
        if (zoomAmount <= 0.0D) {
            return;
        }

        int transitionTicks = Math.max(1, VillagerRetaliationConfig.DIALOGUE_CAMERA_TRANSITION_TICKS.get());
        float activeProgress = (ClientVillagerConversationState.cameraFocusTicks() + (float) event.getPartialTick()) / transitionTicks;
        double progress = Minecraft.getInstance().screen instanceof VillagerInteractionSessionScreen
                ? activeProgress
                : ClientVillagerConversationState.cameraReleaseProgress((float) event.getPartialTick());
        event.setFOV(ToucanCameraFov.zoomedFov(event.getFOV(), zoomAmount, progress));
    }

    private static double distanceAdjustedZoomAmount(ViewportEvent event) {
        double zoomAmount = dialogueCameraZoomAmount();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !ClientVillagerConversationState.active()) {
            return zoomAmount;
        }

        Entity villager = minecraft.level.getEntity(ClientVillagerConversationState.focusedVillagerEntityId());
        if (villager == null || !villager.isAlive()) {
            return zoomAmount;
        }

        double distance = event.getCamera().getPosition().distanceTo(
                ToucanCameraTargetPoints.focusPoint(villager, (float) event.getPartialTick()));
        return ToucanCameraFov.distanceAdjustedZoomAmount(
                zoomAmount,
                distance,
                CAMERA_DISTANCE_ZOOM_START,
                CAMERA_DISTANCE_ZOOM_END,
                CAMERA_DISTANCE_ZOOM_BONUS,
                CAMERA_MAX_ZOOM_AMOUNT);
    }

    private static double dialogueCameraZoomAmount() {
        if (ClientVillagerConversationState.usesForcedCameraZoom()) {
            return VillagerRetaliationConfig.DIALOGUE_CAMERA_ZOOM_AMOUNT.get();
        }
        if (!VillagerRetaliationConfig.ENABLE_NORMAL_DIALOGUE_CAMERA_FOCUS.get()) {
            return 0.0D;
        }
        return VillagerRetaliationConfig.NORMAL_DIALOGUE_CAMERA_ZOOM_AMOUNT.get();
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        ClientVillagerConversationState.tickCameraRelease();
    }

    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!VillagerRetaliationConfig.ENABLE_DIALOGUE_CAMERA_FOCUS.get() || !shouldTurnCameraTowardsVillager()) {
            resetCameraTurn();
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            resetCameraTurn();
            return;
        }

        Entity villager = minecraft.level.getEntity(ClientVillagerConversationState.focusedVillagerEntityId());
        if (villager == null || !villager.isAlive()) {
            resetCameraTurn();
            return;
        }

        Vec3 from = event.getCamera().getPosition();
        Vec3 to = ToucanCameraTargetPoints.focusPoint(villager, (float) event.getPartialTick());
        ToucanCameraBasis.Rotation targetRotation = ToucanCameraBasis.rotationToTarget(
                from,
                to,
                event.getYaw(),
                event.getPitch());
        int transitionTicks = Math.max(1, VillagerRetaliationConfig.DIALOGUE_CAMERA_TRANSITION_TICKS.get());
        float turnStrength = cameraTurnStrength();
        ToucanCameraBasis.Rotation rotation = TURN_SMOOTHER.update(
                event.getYaw(),
                event.getPitch(),
                targetRotation.yaw(),
                targetRotation.pitch(),
                event.getPartialTick(),
                adjustedCameraTurnTransitionTicks(transitionTicks),
                CAMERA_TURN_MIN_TICK_LERP * turnStrength,
                CAMERA_TURN_MAX_TICK_LERP * turnStrength);
        event.setYaw(rotation.yaw());
        event.setPitch(rotation.pitch());
        minecraft.player.setYRot(rotation.yaw());
        minecraft.player.setXRot(rotation.pitch());
    }

    private static boolean shouldTurnCameraTowardsVillager() {
        if (ClientVillagerConversationState.forceCameraTowardsVillager()) {
            return true;
        }
        return ClientVillagerConversationState.active()
                && VillagerRetaliationConfig.ENABLE_NORMAL_DIALOGUE_CAMERA_FOCUS.get();
    }

    private static float cameraTurnStrength() {
        return ClientVillagerConversationState.forceCameraTowardsVillager()
                ? 1.0F
                : NORMAL_CAMERA_TURN_STRENGTH;
    }

    private static int adjustedCameraTurnTransitionTicks(int transitionTicks) {
        return ClientVillagerConversationState.forceCameraTowardsVillager()
                ? transitionTicks
                : Math.max(transitionTicks + 3, transitionTicks * 2);
    }

    private static void resetCameraTurn() {
        TURN_SMOOTHER.reset();
    }
}
