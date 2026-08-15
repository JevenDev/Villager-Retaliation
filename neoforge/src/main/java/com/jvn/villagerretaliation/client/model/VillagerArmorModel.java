package com.jvn.villagerretaliation.client.model;

import com.jvn.villagerretaliation.VillagerRetaliation;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.npc.AbstractVillager;

public class VillagerArmorModel<T extends AbstractVillager> extends HumanoidModel<T> {
    public static final ModelLayerLocation INNER_ARMOR =
            new ModelLayerLocation(VillagerRetaliation.id("villager_armor"), "inner");
    public static final ModelLayerLocation OUTER_ARMOR =
            new ModelLayerLocation(VillagerRetaliation.id("villager_armor"), "outer");
    public static final ModelLayerLocation HUMANOID_INNER_ARMOR =
            new ModelLayerLocation(VillagerRetaliation.id("humanoid_villager_armor"), "inner");
    public static final ModelLayerLocation HUMANOID_OUTER_ARMOR =
            new ModelLayerLocation(VillagerRetaliation.id("humanoid_villager_armor"), "outer");
    private static final float EXTRA_ARMOR_DEFORMATION = 0.25F;
    private static final CubeDeformation INNER_ARMOR_DEFORMATION = new CubeDeformation(0.5F);
    private static final CubeDeformation OUTER_ARMOR_DEFORMATION = new CubeDeformation(1.0F);
    public VillagerArmorModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createInnerArmorLayer() {
        return createArmorLayer(INNER_ARMOR_DEFORMATION);
    }

    public static LayerDefinition createOuterArmorLayer() {
        return createArmorLayer(OUTER_ARMOR_DEFORMATION);
    }

    public static LayerDefinition createHumanoidInnerArmorLayer() {
        return createHumanoidArmorLayer(INNER_ARMOR_DEFORMATION);
    }

    public static LayerDefinition createHumanoidOuterArmorLayer() {
        return createHumanoidArmorLayer(OUTER_ARMOR_DEFORMATION);
    }

    private static LayerDefinition createHumanoidArmorLayer(CubeDeformation deformation) {
        return LayerDefinition.create(HumanoidModel.createMesh(deformation, 0.0F), 64, 32);
    }

    private static LayerDefinition createArmorLayer(CubeDeformation baseDeformation) {
        CubeDeformation deformation = baseDeformation.extend(EXTRA_ARMOR_DEFORMATION);
        MeshDefinition mesh = HumanoidModel.createMesh(deformation, 0.0F);
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild(
                "head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -10.0F, -4.0F, 8.0F, 8.0F, 8.0F, deformation),
                PartPose.ZERO
        );
        root.addOrReplaceChild(
                "body",
                CubeListBuilder.create().texOffs(16, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, deformation.extend(0.1F)),
                PartPose.ZERO
        );
        root.addOrReplaceChild(
                "right_leg",
                CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, deformation.extend(0.1F)),
                PartPose.offset(-2.0F, 12.0F, 0.0F)
        );
        root.addOrReplaceChild(
                "left_leg",
                CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, deformation.extend(0.1F)),
                PartPose.offset(2.0F, 12.0F, 0.0F)
        );
        root.getChild("hat").addOrReplaceChild("hat_rim", CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }
}
