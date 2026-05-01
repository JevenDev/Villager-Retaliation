package com.jvn.commonfolk.client.renderer;

import com.jvn.commonfolk.Commonfolk;
import com.jvn.commonfolk.client.pose.DefaultWanderingTraderPoseProvider;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.WanderingTrader;

public class CommonfolkWanderingTraderRenderer extends AbstractCommonfolkVillagerRenderer<WanderingTrader> {
    private static final ResourceLocation VANILLA_TRADER_SKIN =
            ResourceLocation.withDefaultNamespace("textures/entity/wandering_trader.png");
    private static final ResourceLocation TRADER_SKIN =
            ResourceLocation.fromNamespaceAndPath(Commonfolk.MOD_ID, "textures/entity/wandering_trader/wandering_trader.png");

    public CommonfolkWanderingTraderRenderer(EntityRendererProvider.Context context) {
        super(context, ModelLayers.WANDERING_TRADER, DefaultWanderingTraderPoseProvider.INSTANCE, VANILLA_TRADER_SKIN, TRADER_SKIN);
    }

    @Override
    protected void scale(WanderingTrader trader, PoseStack poseStack, float partialTickTime) {
        float scale = 0.9375F;
        poseStack.scale(scale, scale, scale);
    }
}
