package com.jvn.villagerretaliation.client.model;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.client.pose.VillagerArmPose;
import com.jvn.villagerretaliation.client.pose.VillagerPoseAnimator;
import com.jvn.villagerretaliation.client.pose.VillagerPoseProvider;
import com.jvn.villagerretaliation.client.renderer.VillagerRenderEquipmentState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.npc.AbstractVillager;
import com.mojang.blaze3d.vertex.PoseStack;

public class VillagerRetaliationVillagerModel<T extends AbstractVillager> extends BaseVillagerModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(VillagerRetaliation.id("villager"), "main");

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart robe;
    private final ModelPart crossedArms;
    private final ModelPart head;
    private final ModelPart helmet;
    private final ModelPart brim;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    private final VillagerPoseProvider<T> poseProvider;
    private final VillagerDialogueMouthParts dialogueMouthParts;

    public VillagerRetaliationVillagerModel(ModelPart root) {
        this(root, null);
    }

    public VillagerRetaliationVillagerModel(ModelPart root, VillagerPoseProvider<T> poseProvider) {
        this.root = root;
        this.body = root.getChild("body");
        this.robe = getOptionalChild(root, "robe");
        this.crossedArms = root.getChild("arms");
        this.head = root.getChild("head");
        this.helmet = getOptionalChild(this.head, "helmet");
        this.brim = getOptionalChild(this.head, "brim");
        this.rightArm = root.getChild("RightArm");
        this.leftArm = root.getChild("LeftArm");
        this.rightLeg = root.getChild("RightLeg");
        this.leftLeg = root.getChild("LeftLeg");
        this.poseProvider = poseProvider;
        this.dialogueMouthParts = VillagerDialogueMouthParts.find(root);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        partdefinition.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 20).addBox(-4.0F, 0.0F, -3.0F, 8.0F, 12.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        partdefinition.addOrReplaceChild("robe", CubeListBuilder.create().texOffs(0, 38).addBox(-4.0F, 0.0F, -3.0F, 8.0F, 18.0F, 6.0F, new CubeDeformation(0.5F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        partdefinition.addOrReplaceChild("arms", CubeListBuilder.create().texOffs(44, 22).addBox(-8.0F, -2.0F, -2.0F, 4.0F, 8.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(44, 22).addBox(4.0F, -2.0F, -2.0F, 4.0F, 8.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(40, 38).addBox(-4.0F, 2.0F, -2.0F, 8.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, -0.7854F, 0.0F, 0.0F));

        partdefinition.addOrReplaceChild("RightLeg", CubeListBuilder.create().texOffs(0, 22).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-2.0F, 12.0F, 0.0F));

        partdefinition.addOrReplaceChild("LeftLeg", CubeListBuilder.create().texOffs(0, 22).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(2.0F, 12.0F, 0.0F));

        partdefinition.addOrReplaceChild("RightArm", CubeListBuilder.create().texOffs(64, 22).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-5.0F, 2.0F, 0.0F));

        partdefinition.addOrReplaceChild("LeftArm", CubeListBuilder.create().texOffs(64, 22).mirror().addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(5.0F, 2.0F, 0.0F));

        PartDefinition head = partdefinition.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        head.addOrReplaceChild("helmet", CubeListBuilder.create().texOffs(32, 0).addBox(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F, new CubeDeformation(0.5F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        head.addOrReplaceChild("brim", CubeListBuilder.create().texOffs(30, 47).addBox(-8.0F, -8.0F, -6.0F, 16.0F, 16.0F, 1.0F, new CubeDeformation(0.1F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -1.5708F, 0.0F, 0.0F));

        head.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(24, 0).addBox(-1.0F, -1.0F, -6.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -2.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 128, 128);
    }

    @Override
    public void setupAnim(T villager, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.root.getAllParts().forEach(ModelPart::resetPose);
        this.head.yRot = netHeadYaw * ((float) Math.PI / 180F);
        this.head.xRot = headPitch * ((float) Math.PI / 180F);

        if (this.riding) {
            this.setArmLayout(true);
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
            this.syncRobe(villager);
            this.dialogueMouthParts.apply(villager, ageInTicks);
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

        VillagerArmPose pose = this.poseProvider == null
                ? VillagerArmPose.NONE
                : this.poseProvider.getArmPose(villager, this.attackTime);
        this.setArmLayout(pose != VillagerArmPose.NONE || VillagerRenderEquipmentState.hasArmorEquipped(villager));
        if (pose == VillagerArmPose.NONE) {
            this.syncRobe(villager);
            this.dialogueMouthParts.apply(villager, ageInTicks);
            return;
        }
        VillagerPoseAnimator.applyPose(pose, villager, this.body, this.head, this.rightArm, this.leftArm, this.attackTime, ageInTicks);
        this.syncRobe(villager);
        this.dialogueMouthParts.apply(villager, ageInTicks);
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
        setVisible(this.helmet, visible);
        setVisible(this.brim, visible);
    }

    @Override
    public void copyPropertiesToHumanoidArmor(HumanoidModel<T> armorModel) {
        super.copyPropertiesToHumanoidArmor(armorModel);
        armorModel.head.copyFrom(this.head);
        armorModel.hat.copyFrom(this.head);
        armorModel.body.copyFrom(this.body);
        armorModel.rightArm.copyFrom(this.rightArm);
        armorModel.leftArm.copyFrom(this.leftArm);
        armorModel.rightLeg.copyFrom(this.rightLeg);
        armorModel.leftLeg.copyFrom(this.leftLeg);
    }

    public void translateToHand(HumanoidArm arm, PoseStack poseStack) {
        this.getArm(arm).translateAndRotate(poseStack);
    }

    private void setArmLayout(boolean sideArmsVisible) {
        this.crossedArms.visible = !sideArmsVisible;
        this.rightArm.visible = sideArmsVisible;
        this.leftArm.visible = sideArmsVisible;
    }

    private ModelPart getArm(HumanoidArm arm) {
        return arm == HumanoidArm.LEFT ? this.leftArm : this.rightArm;
    }

    private void syncRobe(T villager) {
        if (this.robe == null) {
            return;
        }
        this.robe.copyFrom(this.body);
        this.robe.visible = !VillagerRenderEquipmentState.hasBodyArmorEquipped(villager);
    }

    private static ModelPart getOptionalChild(ModelPart parent, String name) {
        try {
            return parent.getChild(name);
        } catch (Exception exception) {
            return null;
        }
    }

    private static void setVisible(ModelPart part, boolean visible) {
        if (part != null) {
            part.visible = visible;
        }
    }
}
