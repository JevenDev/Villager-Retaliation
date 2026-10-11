package com.jvn.villagerretaliation.client.trade;

import com.jvn.villagerretaliation.client.interaction.DialogueCameraTimeline.Pose;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class TradeCameraMotion {
    private static final double SPEED = 10;
    private final double[] values;
    private final double[] velocities = new double[6];

    public TradeCameraMotion(Pose source) {
        values = coordinates(source);
    }

    public Pose pose() {
        return new Pose(new Vec3(values[0], values[1], values[2]), (float) values[3], (float) values[4], values[5]);
    }

    public Pose update(double seconds, Pose target) {
        if (!Double.isFinite(seconds) || seconds < 0) throw new IllegalArgumentException("Invalid camera elapsed time");
        var destination = coordinates(target);
        destination[3] = values[3] + Mth.wrapDegrees(destination[3] - values[3]);
        double decay = Math.exp(-SPEED * seconds);
        for (int index = 0; index < values.length; index++) {
            double change = values[index] - destination[index];
            // exact critically damped spring preserves easing across frame rates and retargets
            double step = (velocities[index] + SPEED * change) * seconds;
            values[index] = destination[index] + (change + step) * decay;
            velocities[index] = (velocities[index] - SPEED * step) * decay;
        }
        return pose();
    }

    private static double[] coordinates(Pose pose) {
        return new double[] {pose.position().x, pose.position().y, pose.position().z, pose.yaw(), pose.pitch(), pose.fov()};
    }
}
