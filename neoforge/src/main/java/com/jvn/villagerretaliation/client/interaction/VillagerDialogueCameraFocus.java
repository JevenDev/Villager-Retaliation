package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

public final class VillagerDialogueCameraFocus {
    private static final float CAMERA_TURN_MIN_TICK_LERP = 0.08F;
    private static final float CAMERA_TURN_MAX_TICK_LERP = 0.25F;
    private static final double CAMERA_TURN_FIRST_FRAME_DELTA_TICKS = 1.0D / 3.0D;

    private static boolean cameraTurnInitialized;
    private static float cameraTurnYaw;
    private static float cameraTurnPitch;
    private static double lastCameraTurnPartialTick;

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

    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!VillagerRetaliationConfig.ENABLE_DIALOGUE_CAMERA_FOCUS.get()
                || !ClientVillagerConversationState.forceCameraTowardsVillager()) {
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
        Vec3 to = targetPosition(villager);
        Vec3 delta = to.subtract(from);
        double horizontalDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontalDistance < 1.0E-4D) {
            resetCameraTurn();
            return;
        }

        float targetYaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
        float targetPitch = (float) -(Mth.atan2(delta.y, horizontalDistance) * Mth.RAD_TO_DEG);
        updateCameraTurn(event, targetYaw, targetPitch);
        event.setYaw(cameraTurnYaw);
        event.setPitch(cameraTurnPitch);
        minecraft.player.setYRot(cameraTurnYaw);
        minecraft.player.setXRot(cameraTurnPitch);
    }

    private static void updateCameraTurn(ViewportEvent.ComputeCameraAngles event, float targetYaw, float targetPitch) {
        boolean firstFrame = !cameraTurnInitialized;
        if (!cameraTurnInitialized) {
            cameraTurnYaw = event.getYaw();
            cameraTurnPitch = event.getPitch();
            lastCameraTurnPartialTick = event.getPartialTick();
            cameraTurnInitialized = true;
        }

        double deltaTicks = firstFrame
                ? CAMERA_TURN_FIRST_FRAME_DELTA_TICKS
                : event.getPartialTick() - lastCameraTurnPartialTick;
        if (!firstFrame && deltaTicks < 0.0D) {
            deltaTicks += 1.0D;
        }
        deltaTicks = Mth.clamp(deltaTicks, 0.0D, 1.0D);
        lastCameraTurnPartialTick = event.getPartialTick();

        int transitionTicks = Math.max(1, VillagerRetaliationConfig.DIALOGUE_CAMERA_TRANSITION_TICKS.get());
        float tickLerp = Mth.clamp(1.0F / (transitionTicks * 2.0F), CAMERA_TURN_MIN_TICK_LERP, CAMERA_TURN_MAX_TICK_LERP);
        float frameLerp = (float) (1.0D - Math.pow(1.0D - tickLerp, deltaTicks));
        cameraTurnYaw = Mth.rotLerp(frameLerp, cameraTurnYaw, targetYaw);
        cameraTurnPitch = Mth.rotLerp(frameLerp, cameraTurnPitch, targetPitch);
    }

    private static void resetCameraTurn() {
        cameraTurnInitialized = false;
    }

    private static Vec3 targetPosition(Entity entity) {
        if (entity instanceof LivingEntity livingEntity) {
            return livingEntity.getEyePosition();
        }
        return entity.position().add(0.0D, entity.getBbHeight() * 0.65D, 0.0D);
    }

    private static double smoothstep(double value) {
        return value * value * (3.0D - 2.0D * value);
    }
}
