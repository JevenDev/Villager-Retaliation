package com.jvn.villagerretaliation.client.interaction;

import com.jvn.toucanlib.client.camera.ToucanCameraTransforms;
import net.minecraft.world.phys.Vec3;

public final class DialogueCameraTransforms {
    private DialogueCameraTransforms() { }
    public static Vec3 forward(Vec3 focus, Vec3 principalEye, float fallbackYaw) {
        return ToucanCameraTransforms.forward(focus, principalEye, fallbackYaw);
    }
    public static Vec3 offset(Vec3 position, Vec3 pivot, Vec3 forward) {
        return ToucanCameraTransforms.offset(position, pivot, forward);
    }
    public static Vec3 position(Vec3 offset, Vec3 pivot, Vec3 forward) {
        return ToucanCameraTransforms.position(offset, pivot, forward);
    }
    public static float yaw(Vec3 forward) { return ToucanCameraTransforms.yaw(forward); }
    public static float relativeYaw(float yaw, Vec3 forward) { return ToucanCameraTransforms.relativeYaw(yaw, forward); }
}
