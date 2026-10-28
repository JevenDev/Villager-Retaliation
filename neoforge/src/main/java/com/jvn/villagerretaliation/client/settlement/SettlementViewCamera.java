package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.settlement.SettlementViewWindow;
import net.minecraft.world.phys.Vec3;

public final class SettlementViewCamera {
    public static final float PITCH = 35.26439F;
    private static final double TERRAIN_RESPONSE = 5.0D;
    private static final double RECOVERY_RESPONSE = 3.0D;
    private float yaw = 45.0F;
    private double terrain = Double.NaN;
    private double clearance = Double.NaN;
    private double lift;

    public float yaw() {
        return this.yaw;
    }

    public void rotate(double direction, double elapsed) {
        this.yaw = (float) ((this.yaw + direction * 90.0D * elapsed) % 360.0D);
        if (this.yaw < 0.0F) {
            this.yaw += 360.0F;
        }
    }

    public static Vec3 movement(float yaw, double right, double forward) {
        double angle = Math.toRadians(yaw);
        return new Vec3(-right * Math.cos(angle) - forward * Math.sin(angle), 0.0D,
                -right * Math.sin(angle) + forward * Math.cos(angle));
    }

    public Pose position(SettlementViewWindow window, double x, double z, double height,
                         double visibleDistance, Heights heights, double elapsed) {
        double sample = interpolate(heights, x, z);
        if (!Double.isFinite(this.terrain)) {
            this.terrain = Double.isFinite(sample) ? sample : window.center().getY();
        } else if (Double.isFinite(sample)) {
            this.terrain += (sample - this.terrain) * (1.0D - Math.exp(-TERRAIN_RESPONSE * elapsed));
        }
        double pitch = Math.max(PITCH, Math.toDegrees(Math.asin(Math.min(1.0D, height / Math.max(1.0D, visibleDistance)))));
        double offset = height / Math.tan(Math.toRadians(pitch));
        double angle = Math.toRadians(this.yaw);
        double dx = Math.sin(angle);
        double dz = -Math.cos(angle);
        offset = Math.min(offset, boundaryOffset(window, x, z, dx, dz));
        double cameraX = x + dx * offset;
        double cameraZ = z + dz * offset;
        double obstruction = clearance(heights, cameraX, cameraZ);
        if (Double.isFinite(obstruction)) {
            this.clearance = obstruction + 4.0D;
        }
        double base = this.terrain + height;
        double requiredLift = Double.isFinite(this.clearance) ? Math.max(0.0D, this.clearance - base) : 0.0D;
        this.lift += (requiredLift - this.lift) * (1.0D - Math.exp(-RECOVERY_RESPONSE * elapsed));
        this.lift = Math.max(requiredLift, this.lift);
        double y = base + this.lift;
        return new Pose(new Vec3(cameraX, y, cameraZ), this.yaw,
                (float) Math.toDegrees(Math.atan2(y - this.terrain, offset)));
    }

    public static double transitionProgress(double elapsed) {
        double t = Math.clamp(elapsed / 0.85D, 0.0D, 1.0D);
        return t * t * t * (t * (t * 6.0D - 15.0D) + 10.0D);
    }

    public static Pose transition(Pose start, Pose target, double progress) {
        if (progress <= 0) return start;
        if (progress >= 1) return target;
        return new Pose(start.position().lerp(target.position(), progress),
                start.yaw() + net.minecraft.util.Mth.wrapDegrees(target.yaw() - start.yaw()) * (float) progress,
                start.pitch() + (target.pitch() - start.pitch()) * (float) progress);
    }

    public static double boundaryOffset(SettlementViewWindow window, double x, double z, double dx, double dz) {
        double limit = Math.max(0.0D, Math.min(96.0D, Math.min(axisLimit(x, window.center().getX(), window.radius(), dx),
                axisLimit(z, window.center().getZ(), window.radius(), dz))));
        double distance = 0.0D;
        while (distance < limit) {
            var chunk = new net.minecraft.world.level.ChunkPos(net.minecraft.core.BlockPos.containing(
                    x + dx * (distance + 0.0001D), 0, z + dz * (distance + 0.0001D)));
            if (!window.chunks().contains(chunk)) return Math.max(0.0D, distance - 0.001D);
            double endX = Math.abs(dx) < 1.0E-8D ? Double.POSITIVE_INFINITY
                    : ((dx > 0 ? chunk.getMaxBlockX() + 1.0D : chunk.getMinBlockX()) - x) / dx;
            double endZ = Math.abs(dz) < 1.0E-8D ? Double.POSITIVE_INFINITY
                    : ((dz > 0 ? chunk.getMaxBlockZ() + 1.0D : chunk.getMinBlockZ()) - z) / dz;
            distance = Math.max(distance + 0.0001D, Math.min(endX, endZ));
        }
        return Math.max(0.0D, limit - 0.001D);
    }

    private static double axisLimit(double focus, int center, int radius, double direction) {
        if (Math.abs(direction) < 1.0E-8D) {
            return Double.POSITIVE_INFINITY;
        }
        return (center + Math.copySign(radius, direction) - focus) / direction;
    }

    public static double interpolate(Heights heights, double x, double z) {
        int minX = (int) Math.floor(x - 0.5D);
        int minZ = (int) Math.floor(z - 0.5D);
        double a = heights.sample(minX, minZ);
        double b = heights.sample(minX + 1, minZ);
        double c = heights.sample(minX, minZ + 1);
        double d = heights.sample(minX + 1, minZ + 1);
        double fx = x - 0.5D - minX;
        double fz = z - 0.5D - minZ;
        return (a + (b - a) * fx) * (1.0D - fz) + (c + (d - c) * fx) * fz;
    }

    private static double clearance(Heights heights, double x, double z) {
        double result = Double.NEGATIVE_INFINITY;
        for (int bx = (int) Math.floor(x - 0.4D); bx <= (int) Math.floor(x + 0.4D); bx++) {
            for (int bz = (int) Math.floor(z - 0.4D); bz <= (int) Math.floor(z + 0.4D); bz++) {
                double sample = heights.sample(bx, bz);
                if (!Double.isFinite(sample)) {
                    return Double.NaN;
                }
                result = Math.max(result, sample);
            }
        }
        return result;
    }

    @FunctionalInterface
    public interface Heights {
        double sample(int x, int z);
    }

    public record Pose(Vec3 position, float yaw, float pitch) {
    }
}
