package com.jvn.commonfolk.client.renderer;

import com.jvn.commonfolk.Commonfolk;
import com.jvn.commonfolk.client.pose.DefaultVillagerPoseProvider;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.VillagerProfessionLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;

public class CommonfolkVillagerRenderer extends AbstractCommonfolkVillagerRenderer<Villager> {
    private static final ResourceLocation VANILLA_VILLAGER_SKIN = ResourceLocation.withDefaultNamespace("textures/entity/villager/villager.png");
    private static final ResourceLocation COMBAT_VILLAGER_SKIN =
            ResourceLocation.fromNamespaceAndPath(Commonfolk.MOD_ID, "textures/entity/villager/villager.png");

    public CommonfolkVillagerRenderer(EntityRendererProvider.Context context) {
        super(context, ModelLayers.VILLAGER, DefaultVillagerPoseProvider.INSTANCE, VANILLA_VILLAGER_SKIN, COMBAT_VILLAGER_SKIN);
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
