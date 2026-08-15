package com.jvn.villagerretaliation.client.model;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.client.pose.VillagerArmPose;
import com.jvn.villagerretaliation.client.pose.VillagerPoseProvider;
import com.jvn.villagerretaliation.client.renderer.VillagerRenderEquipmentState;
import com.jvn.villagerretaliation.client.villager.VillagerDownedClientCache;
import com.jvn.villagerretaliation.client.villager.VillagerStudyClientCache;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;

/**
 * Player-proportioned villager model for packs whose villager textures use a humanoid UV layout.
 * The crossed-arms part is intentionally empty and the independent arms remain visible at rest.
 */
public final class HumanoidCompatVillagerModel<T extends AbstractVillager> extends VillagerRetaliationVillagerModel<T> {
    /**
     * Separate from the combat model so EMF packs can animate the humanoid bridge without
     * replacing VR's default villager geometry.
     */
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(VillagerRetaliation.id("humanoid_villager"), "main");

    private final ModelPart root;
    private final ModelPart crossedArms;
    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart helmet;
    private final ModelPart brim;
    private final VillagerPoseProvider<T> poseProvider;
    private final boolean freshAnimationProfile;

    public HumanoidCompatVillagerModel(ModelPart root, VillagerPoseProvider<T> poseProvider) {
        this(root, poseProvider, false);
    }

    public HumanoidCompatVillagerModel(
            ModelPart root,
            VillagerPoseProvider<T> poseProvider,
            boolean freshAnimationProfile
    ) {
        super(root, poseProvider);
        this.root = root;
        this.crossedArms = root.getChild("arms");
        this.rightArm = root.getChild("RightArm");
        this.leftArm = root.getChild("LeftArm");
        this.head = root.getChild("head");
        this.helmet = this.head.getChild("helmet");
        this.brim = this.head.getChild("brim");
        this.poseProvider = poseProvider;
        this.freshAnimationProfile = freshAnimationProfile;
    }

    @Override
    public void setupAnim(T villager, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(villager, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        this.applyFreshAnimationProfile(villager, limbSwing, limbSwingAmount, ageInTicks);
        this.crossedArms.visible = false;
        this.rightArm.visible = true;
        this.leftArm.visible = true;
    }

    @Override
    public void hatVisible(boolean visible) {
        this.helmet.visible = visible;
        this.brim.visible = visible;
    }

    private void applyFreshAnimationProfile(T villager, float limbSwing, float limbSwingAmount, float ageInTicks) {
        if (!this.freshAnimationProfile
                || !villager.isAlive()
                || this.riding
                || VillagerDownedClientCache.isDowned(villager)
                || villager instanceof Villager studyingVillager && VillagerStudyClientCache.isActive(studyingVillager)) {
            return;
        }

        float movement = Mth.clamp(limbSwingAmount, 0.0F, 1.0F);
        float idle = 1.0F - movement;
        float phase = (villager.getId() * 0.7548777F) % Mth.TWO_PI;

        // The shared root keeps body geometry, armor, and held-item anchors together.
        this.root.y -= Mth.abs(Mth.cos(limbSwing * 0.6662F)) * movement * 0.55F;
        this.root.xRot += movement * 0.035F;
        this.root.zRot += Mth.sin(limbSwing * 0.3331F) * movement * 0.025F
                + Mth.sin(ageInTicks * 0.045F + phase) * idle * 0.009F;

        // HEVI retains its painted face; the head cube moves without incompatible facial planes.
        this.head.xRot += Mth.sin(ageInTicks * 0.075F + phase) * (0.018F - movement * 0.006F);
        this.head.yRot += Mth.sin(ageInTicks * 0.047F + phase * 1.7F) * idle * 0.022F;
        this.head.zRot += Mth.sin(ageInTicks * 0.062F + phase * 0.6F) * idle * 0.018F
                + Mth.sin(limbSwing * 0.3331F) * movement * 0.03F;

        VillagerArmPose armPose = this.poseProvider == null
                ? VillagerArmPose.NONE
                : this.poseProvider.getArmPose(villager, this.attackTime);
        boolean handsFree = armPose == VillagerArmPose.NONE
                && VillagerRenderEquipmentState.visibleMainHand(villager).isEmpty()
                && villager.getOffhandItem().isEmpty();
        if (handsFree) {
            float armSway = Mth.sin(ageInTicks / 13.0F + phase) * idle * 0.035F;
            float armPitch = Mth.sin(ageInTicks / 15.0F + phase) * idle * 0.025F;
            this.rightArm.zRot -= armSway;
            this.leftArm.zRot += armSway;
            this.rightArm.xRot += armPitch;
            this.leftArm.xRot -= armPitch;
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(16, 16)
                        .addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F),
                PartPose.ZERO);
        body.addOrReplaceChild("jacket",
                CubeListBuilder.create().texOffs(16, 32)
                        .addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(0.25F)),
                PartPose.ZERO);
        root.addOrReplaceChild("arms", CubeListBuilder.create(), PartPose.ZERO);

        PartDefinition rightArm = root.addOrReplaceChild("RightArm",
                CubeListBuilder.create().texOffs(40, 16)
                        .addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(-5.0F, 2.0F, 0.0F));
        rightArm.addOrReplaceChild("sleeve",
                CubeListBuilder.create().texOffs(40, 32)
                        .addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.25F)),
                PartPose.ZERO);
        PartDefinition leftArm = root.addOrReplaceChild("LeftArm",
                CubeListBuilder.create().texOffs(32, 48)
                        .addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(5.0F, 2.0F, 0.0F));
        leftArm.addOrReplaceChild("sleeve",
                CubeListBuilder.create().texOffs(48, 48)
                        .addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.25F)),
                PartPose.ZERO);

        addLeg(root, "RightLeg", -2.0F, 0, 16, 0, 32);
        addLeg(root, "LeftLeg", 2.0F, 16, 48, 0, 48);

        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F),
                PartPose.ZERO);
        head.addOrReplaceChild("helmet",
                CubeListBuilder.create().texOffs(32, 0)
                        .addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.5F)),
                PartPose.ZERO);
        head.addOrReplaceChild("brim", CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /**
     * Base geometry used when EMF decorates {@link #LAYER_LOCATION}. The 128x128 atlas and
     * combined wide/slim arm regions match the HEVI bridge pack; the CEM extension adds
     * animation and facial parts without taking ownership of VR's arm rotations.
     */
    public static LayerDefinition createEmfBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(16, 16)
                        .addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F),
                PartPose.ZERO);
        body.addOrReplaceChild("jacket",
                CubeListBuilder.create().texOffs(16, 32)
                        .addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(0.25F)),
                PartPose.ZERO);
        root.addOrReplaceChild("arms", CubeListBuilder.create(), PartPose.ZERO);

        PartDefinition rightArm = root.addOrReplaceChild("RightArm",
                CubeListBuilder.create()
                        .texOffs(40, 16).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F)
                        .texOffs(24, 64).addBox(-2.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F),
                PartPose.offset(-5.0F, 2.0F, 0.0F));
        rightArm.addOrReplaceChild("sleeve",
                CubeListBuilder.create()
                        .texOffs(40, 32).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.25F))
                        .texOffs(24, 80).addBox(-2.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, new CubeDeformation(0.25F)),
                PartPose.ZERO);
        PartDefinition leftArm = root.addOrReplaceChild("LeftArm",
                CubeListBuilder.create()
                        .texOffs(32, 48).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F)
                        .texOffs(38, 64).addBox(-1.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F),
                PartPose.offset(5.0F, 2.0F, 0.0F));
        leftArm.addOrReplaceChild("sleeve",
                CubeListBuilder.create()
                        .texOffs(48, 48).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.25F))
                        .texOffs(38, 80).addBox(-1.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, new CubeDeformation(0.25F)),
                PartPose.ZERO);

        addLeg(root, "RightLeg", -2.0F, 0, 16, 0, 32);
        addLeg(root, "LeftLeg", 2.0F, 16, 48, 0, 48);

        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F),
                PartPose.ZERO);
        head.addOrReplaceChild("helmet",
                CubeListBuilder.create().texOffs(32, 0)
                        .addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.5F)),
                PartPose.ZERO);
        head.addOrReplaceChild("brim", CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 128);
    }

    private static void addLeg(PartDefinition root, String name, float x, int baseU, int baseV, int layerU, int layerV) {
        PartDefinition leg = root.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(baseU, baseV)
                        .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(x, 12.0F, 0.0F));
        leg.addOrReplaceChild("pants",
                CubeListBuilder.create().texOffs(layerU, layerV)
                        .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.25F)),
                PartPose.ZERO);
    }
}
