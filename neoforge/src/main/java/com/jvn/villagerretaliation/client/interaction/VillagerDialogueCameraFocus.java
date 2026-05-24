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
        turnCameraTowardsFocusedVillager();
        ClientVillagerConversationState.tickCameraRelease();
    }

    private static void turnCameraTowardsFocusedVillager() {
        if (!VillagerRetaliationConfig.ENABLE_DIALOGUE_CAMERA_FOCUS.get()
                || !ClientVillagerConversationState.forceCameraTowardsVillager()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        Entity villager = minecraft.level.getEntity(ClientVillagerConversationState.focusedVillagerEntityId());
        if (villager == null || !villager.isAlive()) {
            return;
        }

        Vec3 from = minecraft.player.getEyePosition();
        Vec3 to = targetPosition(villager);
        Vec3 delta = to.subtract(from);
        double horizontalDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontalDistance < 1.0E-4D) {
            return;
        }

        float targetYaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
        float targetPitch = (float) -(Mth.atan2(delta.y, horizontalDistance) * Mth.RAD_TO_DEG);
        int transitionTicks = Math.max(1, VillagerRetaliationConfig.DIALOGUE_CAMERA_TRANSITION_TICKS.get());
        float turnAmount = Mth.clamp(1.0F / transitionTicks, 0.05F, 1.0F);
        minecraft.player.setYRot(Mth.rotLerp(turnAmount, minecraft.player.getYRot(), targetYaw));
        minecraft.player.setXRot(Mth.rotLerp(turnAmount, minecraft.player.getXRot(), targetPitch));
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
