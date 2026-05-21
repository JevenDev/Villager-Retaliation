package com.jvn.villagerretaliation.client.renderer;

import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import com.jvn.villagerretaliation.client.pose.DefaultWanderingTraderPoseProvider;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.npc.WanderingTrader;

public class VillagerRetaliationWanderingTraderRenderer extends AbstractVillagerRetaliationVillagerRenderer<WanderingTrader> {
    public VillagerRetaliationWanderingTraderRenderer(EntityRendererProvider.Context context) {
        super(
                context,
                ModelLayers.WANDERING_TRADER,
                DefaultWanderingTraderPoseProvider.INSTANCE,
                VillagerRetaliationClientAssets.VANILLA_TRADER_SKIN,
                VillagerRetaliationClientAssets.COMBAT_TRADER_SKIN
        );
    }

    @Override
    protected void scale(WanderingTrader trader, PoseStack poseStack, float partialTickTime) {
        float scale = 0.9375F;
        poseStack.scale(scale, scale, scale);
    }
}
