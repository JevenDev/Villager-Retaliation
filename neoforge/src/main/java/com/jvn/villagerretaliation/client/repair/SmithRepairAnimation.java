package com.jvn.villagerretaliation.client.repair;

import com.jvn.villagerretaliation.repair.SmithRepairAnimationState;
import com.jvn.villagerretaliation.repair.SmithRepairWorkstation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

public final class SmithRepairAnimation {
    private SmithRepairAnimation() {
    }

    public static float elapsed(Villager villager, float partialTick) {
        return villager.level().getGameTime() - ((SmithRepairAnimationState) villager).villagerretaliation$repairStarted() + partialTick;
    }

    public static float lift(float elapsed) {
        if (elapsed >= SmithRepairWorkstation.WORK_TICKS - 6) return 0;
        float phase = Mth.positiveModulo(elapsed - 10, 16) / 16;
        float rise = phase < 0.7F ? phase / 0.7F : (1 - phase) / 0.3F;
        return rise * rise * (3 - 2 * rise);
    }

    public static Vec3 forward(Villager villager, float partialTick) {
        float yaw = Mth.rotLerp(partialTick, villager.yBodyRotO, villager.yBodyRot) * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
    }

    public static void apply(AbstractVillager entity, ModelPart head, ModelPart rightArm, ModelPart leftArm, float ageInTicks) {
        if (!(entity instanceof Villager villager) || !SmithRepairAnimationState.isWorking(villager)) return;
        float partialTick = ageInTicks - villager.tickCount;
        float elapsed = elapsed(villager, partialTick);
        float blend = Mth.clamp((SmithRepairWorkstation.WORK_TICKS + 8 - elapsed) / 8, 0, 1);
        if (blend <= 0) return;
        var state = (SmithRepairAnimationState) villager;
        Vec3 item = SmithRepairWorkstation.itemPosition(villager, state.villagerretaliation$repairStation());
        Vec3 forward = forward(villager, partialTick);
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        boolean rightHanded = villager.getMainArm() == HumanoidArm.RIGHT;
        Vec3 brace = SmithRepairWorkstation.surfacePosition(villager, state.villagerretaliation$repairStation(),
                item.subtract(forward.scale(0.12)).add(right.scale(rightHanded ? -0.22 : 0.22)));
        ModelPart hammerArm = rightHanded ? rightArm : leftArm;
        hammerArm.xRot = Mth.lerp(blend, hammerArm.xRot, Mth.lerp(lift(elapsed), -0.45F, -1.9F));
        hammerArm.yRot = Mth.lerp(blend, hammerArm.yRot, rightHanded ? -0.35F : 0.35F);
        hammerArm.zRot = Mth.lerp(blend, hammerArm.zRot, 0);
        hammerArm.yScale = Mth.lerp(blend, hammerArm.yScale, 1);
        aim(villager, rightHanded ? leftArm : rightArm, brace, partialTick, blend);
        Vec3 eye = villager.getPosition(partialTick).add(0, villager.getEyeHeight(), 0);
        Vec3 look = item.subtract(eye);
        float pitch = (float) Math.atan2(-look.y, look.horizontalDistance());
        float yaw = (float) Math.atan2(-look.x, look.z) * Mth.RAD_TO_DEG;
        float bodyYaw = Mth.rotLerp(partialTick, villager.yBodyRotO, villager.yBodyRot);
        head.xRot = Mth.lerp(blend, head.xRot, pitch);
        head.yRot = Mth.lerp(blend, head.yRot, Mth.wrapDegrees(yaw - bodyYaw) * Mth.DEG_TO_RAD);
    }

    private static void aim(Villager villager, ModelPart arm, Vec3 target, float partialTick, float blend) {
        Vec3 delta = target.subtract(villager.getPosition(partialTick));
        float yaw = Mth.rotLerp(partialTick, villager.yBodyRotO, villager.yBodyRot) * Mth.DEG_TO_RAD;
        double scale = 0.9375 * villager.getScale();
        double x = (delta.x * Mth.cos(yaw) + delta.z * Mth.sin(yaw)) * 16 / scale - arm.x;
        double y = (1.501 - delta.y / scale) * 16 - arm.y;
        double z = (delta.x * Mth.sin(yaw) - delta.z * Mth.cos(yaw)) * 16 / scale - arm.z;
        arm.xRot = Mth.lerp(blend, arm.xRot, (float) Math.atan2(-Math.sqrt(x * x + z * z), y));
        arm.yRot = Mth.lerp(blend, arm.yRot, (float) Math.atan2(-x, -z));
        arm.zRot = Mth.lerp(blend, arm.zRot, 0);
        arm.yScale = Mth.lerp(blend, arm.yScale, (float) Math.sqrt(x * x + y * y + z * z) / 10);
    }
}
