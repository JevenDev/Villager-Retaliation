package com.jvn.villagerretaliation.client.pose;

import com.jvn.villagerretaliation.combat.downed.VillagerDownedPose;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.Items;
import com.jvn.villagerretaliation.client.villager.VillagerDownedClientCache;

public final class VillagerPoseAnimator {
    private VillagerPoseAnimator() {
    }

    public static void applyDownedPose(
            AbstractVillager villager,
            ModelPart root,
            ModelPart body,
            ModelPart head,
            ModelPart rightArm,
            ModelPart leftArm,
            ModelPart rightLeg,
            ModelPart leftLeg) {
        switch (VillagerDownedClientCache.pose(villager)) {
            case SITTING -> applyDownedSittingPose(body, head, rightArm, leftArm, rightLeg, leftLeg);
            case SIDE_LYING -> applyDownedSideLyingPose(villager, root, body, head, rightArm, leftArm, rightLeg, leftLeg);
            case SECOND_WIND_CRAWL -> applySecondWindCrawlPose(root, body, head, rightArm, leftArm, rightLeg, leftLeg);
        }
    }

    private static void applySecondWindCrawlPose(
            ModelPart root, ModelPart body, ModelPart head, ModelPart rightArm, ModelPart leftArm,
            ModelPart rightLeg, ModelPart leftLeg) {
        root.y = 18.0F;
        root.z = 2.0F;
        body.xRot = ((float) Math.PI / 2.0F) - 0.12F;
        head.y = 4.0F;
        head.z = -5.0F;
        head.xRot = 0.34F;
        rightArm.y = 5.0F;
        rightArm.z = -3.5F;
        rightArm.xRot = -0.45F;
        rightArm.yRot = -0.12F;
        leftArm.y = 5.0F;
        leftArm.z = -3.5F;
        leftArm.xRot = -0.15F;
        leftArm.yRot = 0.12F;
        rightLeg.y = 9.0F;
        rightLeg.z = 4.5F;
        rightLeg.xRot = 0.48F;
        leftLeg.y = 9.0F;
        leftLeg.z = 4.5F;
        leftLeg.xRot = 0.18F;
    }

    private static void applyDownedSittingPose(
            ModelPart body,
            ModelPart head,
            ModelPart rightArm,
            ModelPart leftArm,
            ModelPart rightLeg,
            ModelPart leftLeg) {
        body.y = 10.0F;
        body.xRot = -0.12F;
        head.y = 10.0F;
        head.xRot = 0.18F;

        rightArm.y = 12.0F;
        rightArm.xRot = -0.18F;
        rightArm.yRot = -0.12F;
        rightArm.zRot = 0.1F;
        leftArm.y = 12.0F;
        leftArm.xRot = -0.18F;
        leftArm.yRot = 0.12F;
        leftArm.zRot = -0.1F;

        rightLeg.y = 22.0F;
        rightLeg.xRot = -1.42F;
        rightLeg.yRot = 0.16F;
        rightLeg.zRot = 0.04F;
        leftLeg.y = 22.0F;
        leftLeg.xRot = -1.32F;
        leftLeg.yRot = -0.16F;
        leftLeg.zRot = -0.04F;
    }

    private static void applyDownedSideLyingPose(
            AbstractVillager villager,
            ModelPart root,
            ModelPart body,
            ModelPart head,
            ModelPart rightArm,
            ModelPart leftArm,
            ModelPart rightLeg,
            ModelPart leftLeg) {
        boolean liesOnRightSide = (villager.getUUID().getLeastSignificantBits() & 1L) == 0L;
        float side = liesOnRightSide ? 1.0F : -1.0F;
        root.x = side * 7.0F;
        root.y = 20.0F;
        root.zRot = side * ((float) Math.PI / 2.0F);

        body.xRot = 0.08F;
        head.xRot = 0.16F;
        head.yRot = -side * 0.18F;

        rightArm.x = -5.0F;
        rightArm.xRot = -0.22F;
        rightArm.yRot = -0.3F;
        rightArm.zRot = 0.12F;
        leftArm.x = 5.0F;
        leftArm.xRot = -0.52F;
        leftArm.yRot = 0.28F;
        leftArm.zRot = -0.12F;

        rightLeg.xRot = -0.3F;
        rightLeg.yRot = 0.12F;
        rightLeg.zRot = 0.08F;
        leftLeg.xRot = 0.18F;
        leftLeg.yRot = -0.12F;
        leftLeg.zRot = -0.08F;
    }

    public static <T extends AbstractVillager> void applyPose(
            VillagerArmPose pose,
            T villager,
            ModelPart body,
            ModelPart head,
            ModelPart rightArm,
            ModelPart leftArm,
            float attackTime,
            float ageInTicks
    ) {
        switch (pose) {
            case MELEE_WEAPON -> applyMeleePose(villager, body, rightArm, leftArm, attackTime, ageInTicks);
            case BOW_AND_ARROW -> applyBowPose(villager, head, rightArm, leftArm);
            case CROSSBOW_HOLD -> applyCrossbowPose(head, rightArm, leftArm, villager.getMainArm() == HumanoidArm.RIGHT);
            case CROSSBOW_CHARGE -> applyCrossbowCharge(villager, rightArm, leftArm, villager.getMainArm() == HumanoidArm.RIGHT);
            case SHIELD_BLOCK -> applyShieldBlockPose(villager, head, rightArm, leftArm);
            case SHIELD_LOWERED -> applyShieldLoweredPose(villager, head, rightArm, leftArm);
            case THROWING_ITEM -> applyThrowingPose(villager, body, head, rightArm, leftArm, villager.getMainArm() == HumanoidArm.RIGHT, attackTime);
            case CASTING_OR_POTION -> applyPotionPose(head, rightArm, leftArm, villager.getMainArm() == HumanoidArm.RIGHT);
            case WORK_ITEM_USE -> applyWorkItemUsePose(head, rightArm, leftArm, villager.getMainArm() == HumanoidArm.RIGHT, ageInTicks);
            case NONE, HOLDING_ITEM -> {
            }
        }
    }

    private static void applyWorkItemUsePose(
            ModelPart head,
            ModelPart rightArm,
            ModelPart leftArm,
            boolean rightHanded,
            float ageInTicks
    ) {
        ModelPart useArm = rightHanded ? rightArm : leftArm;
        ModelPart supportArm = rightHanded ? leftArm : rightArm;
        float direction = rightHanded ? 1.0F : -1.0F;
        float motion = Mth.sin(ageInTicks * 0.65F) * 0.18F;
        useArm.xRot = -1.05F + head.xRot * 0.35F + motion;
        useArm.yRot = head.yRot - direction * 0.22F;
        useArm.zRot = direction * 0.06F;
        supportArm.xRot = -0.28F - motion * 0.35F;
        supportArm.yRot = head.yRot + direction * 0.12F;
    }

    public static <T extends AbstractVillager> void applyMeleePose(
            T villager,
            ModelPart body,
            ModelPart rightArm,
            ModelPart leftArm,
            float attackTime,
            float ageInTicks
    ) {
        if (isUnarmed(villager)) {
            if (villager.swinging || attackTime > 0.0F) {
                animateUnarmedPunch(villager, body, rightArm, leftArm, attackTime);
            }
            return;
        }

        applyPlayerLikeSwing(villager, body, rightArm, leftArm, attackTime);
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

    public static void applyThrowingPose(
            AbstractVillager villager,
            ModelPart body,
            ModelPart head,
            ModelPart rightArm,
            ModelPart leftArm,
            boolean rightHanded,
            float attackTime
    ) {
        if (villager.swinging || attackTime > 0.0F) {
            applyPlayerLikeSwing(villager, body, rightArm, leftArm, attackTime);
            return;
        }

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

    private static void animateUnarmedPunch(
            AbstractVillager villager,
            ModelPart body,
            ModelPart rightArm,
            ModelPart leftArm,
            float attackProgress
    ) {
        applyBodySwing(villager, body, rightArm, leftArm, attackProgress);

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

    private static void applyPlayerLikeSwing(
            AbstractVillager villager,
            ModelPart body,
            ModelPart rightArm,
            ModelPart leftArm,
            float attackProgress
    ) {
        applyBodySwing(villager, body, rightArm, leftArm, attackProgress);

        ModelPart swingArm = isAttackingWithMainHand(villager)
                ? (villager.getMainArm() == HumanoidArm.RIGHT ? rightArm : leftArm)
                : (villager.getMainArm() == HumanoidArm.RIGHT ? leftArm : rightArm);
        float direction = swingArm == rightArm ? 1.0F : -1.0F;

        float eased = 1.0F - attackProgress;
        eased *= eased;
        eased *= eased;
        eased = 1.0F - eased;
        float forwardSwing = Mth.sin(eased * (float) Math.PI);
        float recovery = Mth.sin(attackProgress * (float) Math.PI) * -(swingArm.xRot - 0.7F) * 0.75F;

        swingArm.yRot += body.yRot * 2.0F + direction * (0.08F - forwardSwing * 0.25F);
        swingArm.xRot -= forwardSwing * 1.2F + recovery;
        swingArm.zRot += Mth.sin(attackProgress * (float) Math.PI) * -0.4F * direction;
    }

    private static void applyBodySwing(
            AbstractVillager villager,
            ModelPart body,
            ModelPart rightArm,
            ModelPart leftArm,
            float attackProgress
    ) {
        if (attackProgress <= 0.0F) {
            return;
        }

        boolean swingingRightArm = isAttackingWithMainHand(villager)
                ? villager.getMainArm() == HumanoidArm.RIGHT
                : villager.getMainArm() != HumanoidArm.RIGHT;
        float bodySwing = Mth.sin(Mth.sqrt(attackProgress) * ((float) Math.PI * 2.0F)) * 0.2F;
        body.yRot = swingingRightArm ? bodySwing : -bodySwing;

        rightArm.z = Mth.sin(body.yRot) * 5.0F;
        rightArm.x = -Mth.cos(body.yRot) * 5.0F;
        leftArm.z = -Mth.sin(body.yRot) * 5.0F;
        leftArm.x = Mth.cos(body.yRot) * 5.0F;
        rightArm.yRot += body.yRot;
        leftArm.yRot += body.yRot;
        leftArm.xRot += body.yRot;
    }
}
