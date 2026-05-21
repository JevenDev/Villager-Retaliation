package com.jvn.villagerretaliation.client.renderer;

import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import com.jvn.villagerretaliation.client.pose.DefaultVillagerPoseProvider;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.VillagerProfessionLayer;
import net.minecraft.world.entity.npc.Villager;

public class VillagerRetaliationVillagerRenderer extends AbstractVillagerRetaliationVillagerRenderer<Villager> {
    public VillagerRetaliationVillagerRenderer(EntityRendererProvider.Context context) {
        super(
                context,
                ModelLayers.VILLAGER,
                DefaultVillagerPoseProvider.INSTANCE,
                VillagerRetaliationClientAssets.VANILLA_VILLAGER_SKIN,
                VillagerRetaliationClientAssets.COMBAT_VILLAGER_SKIN
        );
        this.addLayer(new VillagerProfessionLayer<>(this, context.getResourceManager(), "villager"));
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
