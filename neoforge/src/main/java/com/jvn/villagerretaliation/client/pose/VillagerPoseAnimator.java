package com.jvn.villagerretaliation.client.pose;

import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.Items;

public final class VillagerPoseAnimator {
    private VillagerPoseAnimator() {
    }

    public static <T extends AbstractVillager> void applyPose(
            VillagerArmPose pose,
            T villager,
            ModelPart head,
            ModelPart rightArm,
            ModelPart leftArm,
            float attackTime,
            float ageInTicks
    ) {
        switch (pose) {
            case MELEE_WEAPON -> applyMeleePose(villager, rightArm, leftArm, attackTime, ageInTicks);
            case BOW_AND_ARROW -> applyBowPose(villager, head, rightArm, leftArm);
            case CROSSBOW_HOLD -> applyCrossbowPose(head, rightArm, leftArm, villager.getMainArm() == HumanoidArm.RIGHT);
            case CROSSBOW_CHARGE -> applyCrossbowCharge(villager, rightArm, leftArm, villager.getMainArm() == HumanoidArm.RIGHT);
            case SHIELD_BLOCK -> applyShieldBlockPose(villager, head, rightArm, leftArm);
            case SHIELD_LOWERED -> applyShieldLoweredPose(villager, head, rightArm, leftArm);
            case THROWING_ITEM -> applyThrowingPose(head, rightArm, leftArm, villager.getMainArm() == HumanoidArm.RIGHT);
            case CASTING_OR_POTION -> applyPotionPose(head, rightArm, leftArm, villager.getMainArm() == HumanoidArm.RIGHT);
            case NONE, HOLDING_ITEM -> {
            }
        }
    }

    public static <T extends AbstractVillager> void applyMeleePose(
            T villager,
            ModelPart rightArm,
            ModelPart leftArm,
            float attackTime,
            float ageInTicks
    ) {
        if (isUnarmed(villager)) {
            if (villager.swinging || attackTime > 0.0F) {
                animateUnarmedPunch(villager, rightArm, leftArm, attackTime);
            }
            return;
        }

        applyPlayerLikeSwing(villager, rightArm, leftArm, attackTime);
    }

    public static void applyBowPose(
            AbstractVillager villager,
            ModelPart head,
            ModelPart rightArm,
            ModelPart leftArm
    ) {
        if (villager.getMainArm() == HumanoidArm.RIGHT) {
            // Adapted from vanilla illusioner bow pose so villager ranged posture reads naturally.
            rightArm.yRot = -0.1F + head.yRot;
            rightArm.xRot = (-(float) Math.PI / 2F) + head.xRot;
            leftArm.xRot = -0.9424779F + head.xRot;
            leftArm.yRot = head.yRot - 0.4F;
            leftArm.zRot = (float) (Math.PI / 2.0);
            return;
        }

        leftArm.yRot = 0.1F + head.yRot;
        leftArm.xRot = (-(float) Math.PI / 2F) + head.xRot;
        rightArm.xRot = -0.9424779F + head.xRot;
        rightArm.yRot = head.yRot + 0.4F;
        rightArm.zRot = (float) (-Math.PI / 2.0);
    }

    public static void applyCrossbowPose(ModelPart head, ModelPart rightArm, ModelPart leftArm, boolean rightHanded) {
        AnimationUtils.animateCrossbowHold(rightArm, leftArm, head, rightHanded);
    }

    public static void applyCrossbowCharge(AbstractVillager villager, ModelPart rightArm, ModelPart leftArm, boolean rightHanded) {
        AnimationUtils.animateCrossbowCharge(rightArm, leftArm, villager, rightHanded);
    }

    public static void applyShieldBlockPose(
            AbstractVillager villager,
            ModelPart head,
            ModelPart rightArm,
            ModelPart leftArm
    ) {
        boolean usingOffhand = isShieldInOffhand(villager);
        boolean mainArmRight = villager.getMainArm() == HumanoidArm.RIGHT;
        boolean blockArmRight = usingOffhand ? !mainArmRight : mainArmRight;

        ModelPart blockArm = blockArmRight ? rightArm : leftArm;
        ModelPart supportArm = blockArmRight ? leftArm : rightArm;
        float yawDirection = blockArmRight ? -1.0F : 1.0F;

        blockArm.xRot = -0.95F + head.xRot * 0.35F;
        blockArm.yRot = head.yRot + yawDirection * 0.55F;
        blockArm.zRot = yawDirection * 0.08F;

        supportArm.xRot = -0.25F;
        supportArm.yRot = head.yRot - yawDirection * 0.2F;
        supportArm.zRot = -yawDirection * 0.05F;
    }

    public static void applyShieldLoweredPose(
            AbstractVillager villager,
            ModelPart head,
            ModelPart rightArm,
            ModelPart leftArm
    ) {
        boolean shieldOnOffhand = isShieldInOffhand(villager);
        boolean mainArmRight = villager.getMainArm() == HumanoidArm.RIGHT;
        boolean shieldArmRight = shieldOnOffhand ? !mainArmRight : mainArmRight;

        ModelPart shieldArm = shieldArmRight ? rightArm : leftArm;
        ModelPart weaponArm = shieldArmRight ? leftArm : rightArm;
        float shieldDirection = shieldArmRight ? -1.0F : 1.0F;
        float weaponDirection = -shieldDirection;

        shieldArm.xRot = -0.08F;
        shieldArm.yRot = shieldDirection * 0.12F;
        shieldArm.zRot = shieldDirection * 0.04F;

        weaponArm.xRot = -0.35F + head.xRot * 0.15F;
        weaponArm.yRot = head.yRot + weaponDirection * 0.18F;
        weaponArm.zRot = weaponDirection * 0.03F;
    }

    private static boolean isShieldInOffhand(AbstractVillager villager) {
        return villager.getOffhandItem().is(Items.SHIELD)
                || !villager.getMainHandItem().is(Items.SHIELD) && villager.getUsedItemHand() == InteractionHand.OFF_HAND;
    }

    public static void applyPotionPose(ModelPart head, ModelPart rightArm, ModelPart leftArm, boolean rightHanded) {
        // Keep the bottle close to the mouth like vanilla potion drinkers.
        ModelPart useArm = rightHanded ? rightArm : leftArm;
        ModelPart supportArm = rightHanded ? leftArm : rightArm;
        float direction = rightHanded ? 1.0F : -1.0F;
        useArm.xRot = -1.2F + head.xRot;
        useArm.yRot = head.yRot - direction * 0.35F;
        supportArm.xRot = -0.35F + Mth.cos(head.yRot * 0.5F) * 0.1F;
        supportArm.yRot = head.yRot + direction * 0.2F;
    }

    public static void applyThrowingPose(ModelPart head, ModelPart rightArm, ModelPart leftArm, boolean rightHanded) {
        // Inspired by witch-style overhead lob posture for splash potion throws.
        ModelPart throwArm = rightHanded ? rightArm : leftArm;
        ModelPart supportArm = rightHanded ? leftArm : rightArm;
        float direction = rightHanded ? 1.0F : -1.0F;
        throwArm.xRot = -1.6F + head.xRot * 0.4F;
        throwArm.yRot = head.yRot - direction * 0.35F;
        throwArm.zRot = direction * 0.15F;
        supportArm.xRot = -0.2F;
        supportArm.yRot = head.yRot + direction * 0.15F;
        supportArm.zRot = -direction * 0.1F;
    }

    private static boolean isAttackingWithMainHand(AbstractVillager villager) {
        return villager.swingingArm != InteractionHand.OFF_HAND;
    }

    private static boolean isUnarmed(AbstractVillager villager) {
        return villager.getMainHandItem().isEmpty() && villager.getOffhandItem().isEmpty();
    }

    private static void animateUnarmedPunch(AbstractVillager villager, ModelPart rightArm, ModelPart leftArm, float attackProgress) {
        ModelPart punchArm = isAttackingWithMainHand(villager)
                ? (villager.getMainArm() == HumanoidArm.RIGHT ? rightArm : leftArm)
                : (villager.getMainArm() == HumanoidArm.RIGHT ? leftArm : rightArm);
        ModelPart supportArm = punchArm == rightArm ? leftArm : rightArm;

        float punch = Mth.sin(attackProgress * (float) Math.PI);
        float punchRecovery = Mth.sin((1.0F - (1.0F - attackProgress) * (1.0F - attackProgress)) * (float) Math.PI);
        float yawDirection = punchArm == rightArm ? 1.0F : -1.0F;

        punchArm.yRot += yawDirection * (0.1F - punch * 0.6F);
        punchArm.xRot -= punch * 1.2F + punchRecovery * 0.4F;
        supportArm.yRot = yawDirection * 0.2F;
        supportArm.xRot *= 0.5F;
    }

    private static void applyPlayerLikeSwing(AbstractVillager villager, ModelPart rightArm, ModelPart leftArm, float attackProgress) {
        ModelPart swingArm = isAttackingWithMainHand(villager)
                ? (villager.getMainArm() == HumanoidArm.RIGHT ? rightArm : leftArm)
                : (villager.getMainArm() == HumanoidArm.RIGHT ? leftArm : rightArm);
        ModelPart supportArm = swingArm == rightArm ? leftArm : rightArm;
        float direction = swingArm == rightArm ? 1.0F : -1.0F;

        float tailFade = 1.0F - smoothStep(0.72F, 1.0F, attackProgress);
        float bodySwing = Mth.sin(Mth.sqrt(attackProgress) * ((float) Math.PI * 2.0F)) * 0.2F * direction * tailFade;
        float eased = 1.0F - attackProgress;
        eased *= eased;
        eased *= eased;
        eased = 1.0F - eased;
        float forwardSwing = Mth.sin(eased * (float) Math.PI) * tailFade;
        float recovery = Mth.sin(attackProgress * (float) Math.PI) * -(swingArm.xRot - 0.7F) * 0.55F * tailFade;

        swingArm.yRot += bodySwing * 2.0F + direction * (0.08F - forwardSwing * 0.25F);
        swingArm.xRot -= forwardSwing * 1.2F + recovery;
        swingArm.zRot += Mth.sin(attackProgress * (float) Math.PI) * -0.4F * direction;

        supportArm.yRot -= bodySwing * 0.5F;
        supportArm.xRot *= 0.65F;
    }

    private static float smoothStep(float edge0, float edge1, float value) {
        float t = Mth.clamp((value - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}
