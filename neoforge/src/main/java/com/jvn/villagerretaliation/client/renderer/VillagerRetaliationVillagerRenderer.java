package com.jvn.villagerretaliation.client.renderer;

import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import com.jvn.villagerretaliation.client.pose.DefaultVillagerPoseProvider;
import com.jvn.villagerretaliation.client.renderer.layer.VillagerArmorLayer;
import com.jvn.villagerretaliation.client.renderer.layer.VillagerRetaliationProfessionLayer;
import com.jvn.villagerretaliation.client.renderer.layer.VillagerStudyBookLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.npc.Villager;
import com.jvn.villagerretaliation.client.villager.VillagerDownedClientCache;

public class VillagerRetaliationVillagerRenderer extends AbstractVillagerRetaliationVillagerRenderer<Villager> {
    public VillagerRetaliationVillagerRenderer(EntityRendererProvider.Context context) {
        super(
                context,
                ModelLayers.VILLAGER,
                DefaultVillagerPoseProvider.INSTANCE,
                VillagerRetaliationClientAssets.VANILLA_VILLAGER_SKIN,
                VillagerRetaliationClientAssets.COMBAT_VILLAGER_SKIN,
                true,
                true,
                true
        );
        this.addLayer(new VillagerRetaliationProfessionLayer<>(this, context.getResourceManager(), "villager"));
        this.addLayer(new VillagerArmorLayer<>(this, context));
        this.addLayer(new VillagerStudyBookLayer(this, context));
    }

    @Override
    protected void scale(Villager villager, PoseStack poseStack, float partialTickTime) {
        float scale = 0.9375F * villager.getAgeScale();
        poseStack.scale(scale, scale, scale);
    }

    @Override
    protected float getShadowRadius(Villager villager) {
        float radius = super.getShadowRadius(villager);
        if (VillagerDownedClientCache.isDowned(villager)) {
            radius *= switch (VillagerDownedClientCache.pose(villager)) {
                case SIDE_LYING -> 1.55F;
                case SECOND_WIND_CRAWL, HANDS_AND_KNEES -> 1.3F;
                case SITTING -> 1.05F;
            };
        }
        return villager.isBaby() ? radius * 0.5F : radius;
    }
}
