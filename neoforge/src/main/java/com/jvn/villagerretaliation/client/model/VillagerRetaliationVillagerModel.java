package com.jvn.villagerretaliation.client.model;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.client.pose.VillagerArmPose;
import com.jvn.villagerretaliation.client.pose.VillagerPoseAnimator;
import com.jvn.villagerretaliation.client.pose.VillagerPoseProvider;
import com.jvn.villagerretaliation.client.renderer.VillagerRenderEquipmentState;
import com.jvn.villagerretaliation.client.villager.VillagerDownedClientCache;
import com.jvn.villagerretaliation.client.villager.VillagerNameClientCache;
import com.jvn.villagerretaliation.client.villager.VillagerStudyClientCache;
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
    private static final int STUDY_LOOK_CYCLE_TICKS = 220;
    private static final int STUDY_GLANCE_DURATION_TICKS = 30;
    private static final int STUDY_GLANCE_FADE_TICKS = 5;
    private static final int STUDY_GLANCE_EARLIEST_TICK = 80;
    private static final int STUDY_GLANCE_START_VARIANCE = 70;
    private static final float STUDY_BOOK_HEAD_PITCH = 0.55F;
    private static final float STUDY_EMF_MARKER_X = 0.01F;

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
    private final ModelPart[] resettableParts;
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
        this.resettableParts = root.getAllParts().toArray(ModelPart[]::new);
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
        for (ModelPart part : this.resettableParts) {
            part.resetPose();
        }
        this.head.yRot = netHeadYaw * ((float) Math.PI / 180F);
        this.head.xRot = headPitch * ((float) Math.PI / 180F);

        if (VillagerDownedClientCache.isDowned(villager)) {
            this.setArmLayout(true);
            VillagerPoseAnimator.applyDownedPose(
                    villager,
                    this.root,
                    this.body,
                    this.head,
                    this.rightArm,
                    this.leftArm,
                    this.rightLeg,
                    this.leftLeg);
            this.syncRobe(villager);
            return;
        }

        if (villager instanceof net.minecraft.world.entity.npc.Villager studyingVillager
                && VillagerStudyClientCache.isActive(studyingVillager)) {
            // EMF exposes root transforms to pack expressions, as used by the downed poses.
            // A sub-pixel offset lets the compatibility pack preserve this pose without visible movement.
            this.root.x = STUDY_EMF_MARKER_X;
            float bookLookAmount = studyBookLookAmount(studyingVillager, ageInTicks);
            this.head.xRot = Mth.lerp(bookLookAmount, this.head.xRot, STUDY_BOOK_HEAD_PITCH);
            this.head.yRot = Mth.lerp(bookLookAmount, this.head.yRot, 0.0F);
            this.setArmLayout(true);
            this.rightArm.xRot = -1.08F;
            this.rightArm.yRot = -0.22F;
            this.rightArm.zRot = 0.16F;
            this.leftArm.xRot = -1.08F;
            this.leftArm.yRot = 0.22F;
            this.leftArm.zRot = -0.16F;
            this.syncRobe(villager);
            this.dialogueMouthParts.apply(villager, ageInTicks);
            return;
        }

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
            VillagerArmPose pose = this.poseProvider == null
                    ? VillagerArmPose.NONE
                    : this.poseProvider.getArmPose(villager, this.attackTime);
            if (pose != VillagerArmPose.NONE) {
                VillagerPoseAnimator.applyPose(
                        pose,
                        villager,
                        this.body,
                        this.head,
                        this.rightArm,
                        this.leftArm,
                        this.attackTime,
                        ageInTicks);
            }
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
        this.setArmLayout(pose != VillagerArmPose.NONE
                || VillagerRenderEquipmentState.hasArmorEquipped(villager)
                || VillagerNameClientCache.isHired(villager.getId()));
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
        this.root.translateAndRotate(poseStack);
        this.getArm(arm).translateAndRotate(poseStack);
    }

    @Override
    public void translateRoot(PoseStack poseStack) {
        this.root.translateAndRotate(poseStack);
    }

    private static float studyBookLookAmount(
            net.minecraft.world.entity.npc.Villager villager,
            float ageInTicks
    ) {
        int cycle = Math.max(0, Mth.floor(ageInTicks / STUDY_LOOK_CYCLE_TICKS));
        float cycleTick = ageInTicks - cycle * STUDY_LOOK_CYCLE_TICKS;
        long hash = villager.getUUID().getMostSignificantBits()
                ^ villager.getUUID().getLeastSignificantBits()
                ^ (cycle * 0xD1B54A32D192ED03L);
        hash ^= hash >>> 29;
        hash *= 0x94D049BB133111EBL;
        hash ^= hash >>> 31;
        int glanceStart = STUDY_GLANCE_EARLIEST_TICK
                + (int) Math.floorMod(hash, STUDY_GLANCE_START_VARIANCE);
        float glanceTick = cycleTick - glanceStart;
        if (glanceTick < 0.0F || glanceTick >= STUDY_GLANCE_DURATION_TICKS) {
            return 1.0F;
        }
        if (glanceTick < STUDY_GLANCE_FADE_TICKS) {
            return 1.0F - smoothStep(glanceTick / STUDY_GLANCE_FADE_TICKS);
        }
        float fadeBackStart = STUDY_GLANCE_DURATION_TICKS - STUDY_GLANCE_FADE_TICKS;
        if (glanceTick >= fadeBackStart) {
            return smoothStep((glanceTick - fadeBackStart) / STUDY_GLANCE_FADE_TICKS);
        }
        return 0.0F;
    }

    private static float smoothStep(float value) {
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
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
