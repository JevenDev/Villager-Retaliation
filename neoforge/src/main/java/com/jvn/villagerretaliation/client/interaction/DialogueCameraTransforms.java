package com.jvn.villagerretaliation.client.interaction;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class DialogueCameraTransforms {
    private DialogueCameraTransforms() { }

    public static Vec3 forward(Vec3 focus, Vec3 principalEye, float fallbackYaw) {
        Vec3 direction = focus.subtract(principalEye).multiply(1, 0, 1);
        return direction.lengthSqr() < 1.0E-8 ? Vec3.directionFromRotation(0, fallbackYaw) : direction.normalize();
    }

    public static Vec3 offset(Vec3 position, Vec3 pivot, Vec3 forward) {
        Vec3 delta = position.subtract(pivot);
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        return new Vec3(delta.dot(right), delta.y, delta.dot(forward));
    }

    public static Vec3 position(Vec3 offset, Vec3 pivot, Vec3 forward) {
        return pivot.add(new Vec3(-forward.z, 0, forward.x).scale(offset.x))
                .add(0, offset.y, 0).add(forward.scale(offset.z));
    }

    public static float yaw(Vec3 forward) {
        return (float) Math.toDegrees(Math.atan2(-forward.x, forward.z));
    }

    public static float relativeYaw(float yaw, Vec3 forward) {
        return Mth.wrapDegrees(yaw - yaw(forward));
    }
}
