package com.jvn.commonfolk.client.renderer;

import com.jvn.commonfolk.Commonfolk;
import com.jvn.commonfolk.client.model.BaseVillagerModel;
import com.jvn.commonfolk.client.model.CommonfolkVillagerModel;
import com.jvn.commonfolk.client.model.VanillaVillagerModelAdapter;
import com.jvn.commonfolk.client.pose.DefaultVillagerPoseProvider;
import com.jvn.commonfolk.client.pose.VillagerPoseProvider;
import com.jvn.commonfolk.client.renderer.layer.CombatItemInHandLayer;
import com.jvn.commonfolk.client.renderer.layer.VillagerCrossedArmsItemLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.VillagerProfessionLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;

public class CommonfolkVillagerRenderer extends MobRenderer<Villager, BaseVillagerModel<Villager>> {
    private static final ResourceLocation VANILLA_VILLAGER_SKIN = ResourceLocation.withDefaultNamespace("textures/entity/villager/villager.png");
    private static final ResourceLocation COMBAT_VILLAGER_SKIN =
            ResourceLocation.fromNamespaceAndPath(Commonfolk.MOD_ID, "textures/entity/villager/villager.png");
    private final VanillaVillagerModelAdapter<Villager> vanillaModel;
    private final CommonfolkVillagerModel<Villager> combatModel;
    private final VillagerPoseProvider<Villager> poseProvider;

    public CommonfolkVillagerRenderer(EntityRendererProvider.Context context) {
        super(context, new VanillaVillagerModelAdapter<>(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
        this.vanillaModel = (VanillaVillagerModelAdapter<Villager>) this.model;
        this.poseProvider = DefaultVillagerPoseProvider.INSTANCE;
        this.combatModel = new CommonfolkVillagerModel<>(context.bakeLayer(CommonfolkVillagerModel.LAYER_LOCATION), this.poseProvider);
        this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
        this.addLayer(new VillagerProfessionLayer<>(this, context.getResourceManager(), "villager"));
        this.addLayer(new VillagerCrossedArmsItemLayer(this, context.getItemInHandRenderer()));
        this.addLayer(new CombatItemInHandLayer(this, context.getItemInHandRenderer(), this.poseProvider));
    }

    @Override
    public void render(Villager villager, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        this.model = this.poseProvider.shouldUseCombatModel(villager) ? this.combatModel : this.vanillaModel;
        super.render(villager, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(Villager villager) {
        return this.poseProvider.shouldUseCombatModel(villager) ? COMBAT_VILLAGER_SKIN : VANILLA_VILLAGER_SKIN;
    }

    @Override
    protected void scale(Villager villager, PoseStack poseStack, float partialTickTime) {
        float scale = 0.9375F * villager.getAgeScale();
        poseStack.scale(scale, scale, scale);
    }

    @Override
    protected float getShadowRadius(Villager villager) {
        float radius = super.getShadowRadius(villager);
        return villager.isBaby() ? radius * 0.5F : radius;
    }
}
