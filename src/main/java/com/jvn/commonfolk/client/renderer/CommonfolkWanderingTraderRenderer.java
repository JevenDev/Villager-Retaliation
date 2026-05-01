package com.jvn.commonfolk.client.renderer;

import com.jvn.commonfolk.Commonfolk;
import com.jvn.commonfolk.client.model.BaseVillagerModel;
import com.jvn.commonfolk.client.model.CommonfolkVillagerModel;
import com.jvn.commonfolk.client.model.VanillaVillagerModelAdapter;
import com.jvn.commonfolk.client.pose.DefaultWanderingTraderPoseProvider;
import com.jvn.commonfolk.client.pose.VillagerPoseProvider;
import com.jvn.commonfolk.client.renderer.layer.CombatItemInHandLayer;
import com.jvn.commonfolk.client.renderer.layer.VillagerCrossedArmsItemLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.WanderingTrader;

public class CommonfolkWanderingTraderRenderer extends MobRenderer<WanderingTrader, BaseVillagerModel<WanderingTrader>> {
    private static final ResourceLocation VANILLA_TRADER_SKIN =
            ResourceLocation.withDefaultNamespace("textures/entity/wandering_trader.png");
    private static final ResourceLocation TRADER_SKIN =
            ResourceLocation.fromNamespaceAndPath(Commonfolk.MOD_ID, "textures/entity/wandering_trader/wandering_trader.png");
    private final VanillaVillagerModelAdapter<WanderingTrader> vanillaModel;
    private final CommonfolkVillagerModel<WanderingTrader> combatModel;
    private final VillagerPoseProvider<WanderingTrader> poseProvider;

    public CommonfolkWanderingTraderRenderer(EntityRendererProvider.Context context) {
        super(context, new VanillaVillagerModelAdapter<>(context.bakeLayer(ModelLayers.WANDERING_TRADER)), 0.5F);
        this.vanillaModel = (VanillaVillagerModelAdapter<WanderingTrader>) this.model;
        this.poseProvider = DefaultWanderingTraderPoseProvider.INSTANCE;
        this.combatModel = new CommonfolkVillagerModel<>(context.bakeLayer(CommonfolkVillagerModel.LAYER_LOCATION), this.poseProvider);
        this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
        this.addLayer(new VillagerCrossedArmsItemLayer<>(this, context.getItemInHandRenderer()));
        this.addLayer(new CombatItemInHandLayer<>(this, context.getItemInHandRenderer(), this.poseProvider));
    }

    @Override
    public void render(WanderingTrader trader, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        this.model = this.poseProvider.shouldUseCombatModel(trader) ? this.combatModel : this.vanillaModel;
        super.render(trader, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(WanderingTrader trader) {
        return this.poseProvider.shouldUseCombatModel(trader) ? TRADER_SKIN : VANILLA_TRADER_SKIN;
    }

    @Override
    protected void scale(WanderingTrader trader, PoseStack poseStack, float partialTickTime) {
        float scale = 0.9375F;
        poseStack.scale(scale, scale, scale);
    }
}
