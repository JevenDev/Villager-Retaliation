package com.jvn.villagerretaliation.client.renderer.layer;

import com.jvn.villagerretaliation.client.model.BaseVillagerModel;
import com.jvn.villagerretaliation.client.model.HumanoidCompatVillagerModel;
import com.jvn.villagerretaliation.client.model.VillagerArmorModel;
import com.jvn.villagerretaliation.client.model.VillagerRetaliationVillagerModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.ItemStack;

public class VillagerArmorLayer<T extends AbstractVillager> extends RenderLayer<T, BaseVillagerModel<T>> {
    private final HumanoidModel<T> posedModel;
    private final HumanoidArmorLayer<T, HumanoidModel<T>, VillagerArmorModel<T>> villagerArmor;
    private final HumanoidArmorLayer<T, HumanoidModel<T>, VillagerArmorModel<T>> humanoidArmor;

    public VillagerArmorLayer(RenderLayerParent<T, BaseVillagerModel<T>> renderer, EntityRendererProvider.Context context) {
        super(renderer);
        this.posedModel = new HumanoidModel<>(context.bakeLayer(VillagerArmorModel.HUMANOID_OUTER_ARMOR));
        RenderLayerParent<T, HumanoidModel<T>> armorParent = new RenderLayerParent<>() {
            @Override
            public HumanoidModel<T> getModel() {
                return VillagerArmorLayer.this.posedModel;
            }

            @Override
            public ResourceLocation getTextureLocation(T villager) {
                return renderer.getTextureLocation(villager);
            }
        };
        this.villagerArmor = this.createArmorLayer(armorParent, context,
                VillagerArmorModel.INNER_ARMOR, VillagerArmorModel.OUTER_ARMOR);
        this.humanoidArmor = this.createArmorLayer(armorParent, context,
                VillagerArmorModel.HUMANOID_INNER_ARMOR, VillagerArmorModel.HUMANOID_OUTER_ARMOR);
    }

    private HumanoidArmorLayer<T, HumanoidModel<T>, VillagerArmorModel<T>> createArmorLayer(
            RenderLayerParent<T, HumanoidModel<T>> armorParent,
            EntityRendererProvider.Context context,
            ModelLayerLocation innerLayer,
            ModelLayerLocation outerLayer
    ) {
        // custom armor libraries inject into the standard layer's per-slot render path
        return new HumanoidArmorLayer<>(armorParent,
                new VillagerArmorModel<T>(context.bakeLayer(innerLayer)),
                new VillagerArmorModel<T>(context.bakeLayer(outerLayer)), context.getModelManager()) {
            @Override
            protected Model getArmorModelHook(T villager, ItemStack stack, EquipmentSlot slot, VillagerArmorModel<T> armorModel) {
                return VillagerArmorLayer.this.getArmorModelHook(villager, stack, slot, armorModel);
            }
        };
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            T villager,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch
    ) {
        if (!(this.getParentModel() instanceof VillagerRetaliationVillagerModel<?>)) {
            return;
        }
        this.getParentModel().copyPropertiesToHumanoidArmor(this.posedModel);
        HumanoidArmorLayer<T, HumanoidModel<T>, VillagerArmorModel<T>> armorLayer =
                this.getParentModel() instanceof HumanoidCompatVillagerModel<?> ? this.humanoidArmor : this.villagerArmor;
        poseStack.pushPose();
        try {
            this.getParentModel().translateRoot(poseStack);
            armorLayer.render(poseStack, buffer, packedLight, villager, limbSwing, limbSwingAmount,
                    partialTick, ageInTicks, netHeadYaw, headPitch);
        } finally {
            poseStack.popPose();
        }
    }

    protected Model getArmorModelHook(
            T villager,
            ItemStack stack,
            EquipmentSlot slot,
            VillagerArmorModel<T> armorModel
    ) {
        return net.neoforged.neoforge.client.ClientHooks.getArmorModel(villager, stack, slot, armorModel);
    }
}
