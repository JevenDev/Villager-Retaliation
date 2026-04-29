package com.jvn.commonfolk.client.model;

import com.jvn.commonfolk.Commonfolk;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import com.mojang.blaze3d.vertex.PoseStack;

public class CommonfolkVillagerModel<T extends Villager> extends BaseVillagerModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Commonfolk.MOD_ID, "villager"), "main");

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart helmet;
    private final ModelPart brim;
    @SuppressWarnings("unused")
    private final ModelPart nose;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    private enum ArmPose {
        NEUTRAL,
        ATTACKING,
        BOW_AND_ARROW,
        CROSSBOW_HOLD,
        CROSSBOW_CHARGE
    }

    public CommonfolkVillagerModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.head = this.body.getChild("head");
        this.helmet = this.head.getChild("helmet");
        this.brim = this.head.getChild("brim");
        this.nose = this.head.getChild("nose");
        this.rightArm = this.body.getChild("RightArm");
        this.leftArm = this.body.getChild("LeftArm");
        this.rightLeg = this.body.getChild("RightLeg");
        this.leftLeg = this.body.getChild("LeftLeg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition body = partdefinition.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 20).addBox(-4.0F, -24.0F, -3.0F, 8.0F, 12.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(0, 38).addBox(-4.0F, -24.0F, -3.0F, 8.0F, 20.0F, 6.0F, new CubeDeformation(0.5F)), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -24.0F, 0.0F));

        head.addOrReplaceChild("helmet", CubeListBuilder.create().texOffs(32, 0).addBox(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F, new CubeDeformation(0.5F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        head.addOrReplaceChild("brim", CubeListBuilder.create().texOffs(30, 47).addBox(-8.0F, -8.0F, -6.0F, 16.0F, 16.0F, 1.0F, new CubeDeformation(0.1F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -1.5708F, 0.0F, 0.0F));

        head.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(24, 0).addBox(-1.0F, -1.0F, -6.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -2.0F, 0.0F));

        body.addOrReplaceChild("RightArm", CubeListBuilder.create().texOffs(44, 22).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-5.0F, -22.0F, 0.0F));

        body.addOrReplaceChild("LeftArm", CubeListBuilder.create().texOffs(44, 22).mirror().addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(5.0F, -22.0F, 0.0F));

        body.addOrReplaceChild("RightLeg", CubeListBuilder.create().texOffs(0, 22).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-2.0F, -12.0F, 0.0F));

        body.addOrReplaceChild("LeftLeg", CubeListBuilder.create().texOffs(0, 22).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(2.0F, -12.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 64, 64);
    }

    @Override
    public void setupAnim(T villager, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.root.getAllParts().forEach(ModelPart::resetPose);
        this.head.yRot = netHeadYaw * ((float) Math.PI / 180F);
        this.head.xRot = headPitch * ((float) Math.PI / 180F);

        if (this.riding) {
            this.rightArm.xRot = (-(float) Math.PI / 5F);
            this.rightArm.yRot = 0.0F;
            this.rightArm.zRot = 0.0F;
            this.leftArm.xRot = (-(float) Math.PI / 5F);
            this.leftArm.yRot = 0.0F;
            this.leftArm.zRot = 0.0F;
            this.rightLeg.xRot = -1.4137167F;
            this.rightLeg.yRot = ((float) Math.PI / 10F);
            this.rightLeg.zRot = 0.07853982F;
            this.leftLeg.xRot = -1.4137167F;
            this.leftLeg.yRot = (-(float) Math.PI / 10F);
            this.leftLeg.zRot = -0.07853982F;
            return;
        }

        this.rightArm.xRot = Mth.cos(limbSwing * 0.6662F + (float) Math.PI) * 2.0F * limbSwingAmount * 0.5F;
        this.rightArm.yRot = 0.0F;
        this.rightArm.zRot = 0.0F;
        this.leftArm.xRot = Mth.cos(limbSwing * 0.6662F) * 2.0F * limbSwingAmount * 0.5F;
        this.leftArm.yRot = 0.0F;
        this.leftArm.zRot = 0.0F;

        this.rightLeg.xRot = Mth.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount * 0.5F;
        this.rightLeg.yRot = 0.0F;
        this.rightLeg.zRot = 0.0F;
        this.leftLeg.xRot = Mth.cos(limbSwing * 0.6662F + (float) Math.PI) * 1.4F * limbSwingAmount * 0.5F;
        this.leftLeg.yRot = 0.0F;
        this.leftLeg.zRot = 0.0F;

        ArmPose pose = this.resolvePose(villager);
        if (pose == ArmPose.ATTACKING) {
            float attackProgress = this.attackTime;
            if (this.isUnarmed(villager)) {
                if (villager.swinging || attackProgress > 0.0F) {
                    this.animateUnarmedPunch(villager, attackProgress);
                }
            } else {
                if (this.isAttackingWithMainHand(villager)) {
                    AnimationUtils.swingWeaponDown(this.rightArm, this.leftArm, villager, attackProgress, ageInTicks);
                } else {
                    AnimationUtils.swingWeaponDown(this.leftArm, this.rightArm, villager, attackProgress, ageInTicks);
                }
            }
        } else if (pose == ArmPose.BOW_AND_ARROW) {
            this.animateBowAndArrow(villager);
        } else if (pose == ArmPose.CROSSBOW_HOLD) {
            AnimationUtils.animateCrossbowHold(this.rightArm, this.leftArm, this.head, this.isRightHanded(villager));
        } else if (pose == ArmPose.CROSSBOW_CHARGE) {
            AnimationUtils.animateCrossbowCharge(this.rightArm, this.leftArm, villager, this.isRightHanded(villager));
        }
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public ModelPart getHead() {
        return this.head;
    }

    @Override
    public void hatVisible(boolean visible) {
        this.head.visible = visible;
        this.helmet.visible = visible;
        this.brim.visible = visible;
    }

    public void translateToHand(HumanoidArm arm, PoseStack poseStack) {
        this.body.translateAndRotate(poseStack);
        this.getArm(arm).translateAndRotate(poseStack);
    }

    private ArmPose resolvePose(T villager) {
        if (villager.isUsingItem()
                && villager.getUseItem().is(Items.CROSSBOW)
                && villager.getUsedItemHand() == InteractionHand.MAIN_HAND) {
            return ArmPose.CROSSBOW_CHARGE;
        }
        if (villager.isUsingItem()
                && villager.getUseItem().is(Items.BOW)
                && villager.getUsedItemHand() == InteractionHand.MAIN_HAND) {
            return ArmPose.BOW_AND_ARROW;
        }
        if (villager.isHolding(stack -> stack.is(Items.CROSSBOW))) {
            return ArmPose.CROSSBOW_HOLD;
        }
        if (villager.swinging || villager.isAggressive() || villager.isChasing() || villager.getTarget() != null) {
            return ArmPose.ATTACKING;
        }
        return ArmPose.NEUTRAL;
    }

    private boolean isRightHanded(T villager) {
        return villager.getMainArm() == HumanoidArm.RIGHT;
    }

    private boolean isAttackingWithMainHand(T villager) {
        return villager.swingingArm != InteractionHand.OFF_HAND;
    }

    private boolean isUnarmed(T villager) {
        return villager.getMainHandItem().isEmpty() && villager.getOffhandItem().isEmpty();
    }

    private void animateUnarmedPunch(T villager, float attackProgress) {
        ModelPart punchArm = this.isAttackingWithMainHand(villager)
                ? (villager.getMainArm() == HumanoidArm.RIGHT ? this.rightArm : this.leftArm)
                : (villager.getMainArm() == HumanoidArm.RIGHT ? this.leftArm : this.rightArm);
        ModelPart supportArm = punchArm == this.rightArm ? this.leftArm : this.rightArm;

        float punch = Mth.sin(attackProgress * (float) Math.PI);
        float punchRecovery = Mth.sin((1.0F - (1.0F - attackProgress) * (1.0F - attackProgress)) * (float) Math.PI);
        float yawDirection = punchArm == this.rightArm ? 1.0F : -1.0F;

        punchArm.yRot += yawDirection * (0.1F - punch * 0.6F);
        punchArm.xRot -= punch * 1.2F + punchRecovery * 0.4F;

        supportArm.yRot = yawDirection * 0.2F;
        supportArm.xRot *= 0.5F;
    }

    private void animateBowAndArrow(T villager) {
        if (this.isRightHanded(villager)) {
            // Match Illusioner bow-use pose so draw/aim reads like vanilla ranged mobs.
            this.rightArm.yRot = -0.1F + this.head.yRot;
            this.rightArm.xRot = (-(float) Math.PI / 2F) + this.head.xRot;
            this.leftArm.xRot = -0.9424779F + this.head.xRot;
            this.leftArm.yRot = this.head.yRot - 0.4F;
            this.leftArm.zRot = (float) (Math.PI / 2.0);
            return;
        }

        this.leftArm.yRot = 0.1F + this.head.yRot;
        this.leftArm.xRot = (-(float) Math.PI / 2F) + this.head.xRot;
        this.rightArm.xRot = -0.9424779F + this.head.xRot;
        this.rightArm.yRot = this.head.yRot + 0.4F;
        this.rightArm.zRot = (float) (-Math.PI / 2.0);
    }

    private ModelPart getArm(HumanoidArm arm) {
        return arm == HumanoidArm.LEFT ? this.leftArm : this.rightArm;
    }
}
