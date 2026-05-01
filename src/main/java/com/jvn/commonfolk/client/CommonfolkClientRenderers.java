package com.jvn.commonfolk.client;

import com.jvn.commonfolk.client.model.CommonfolkVillagerModel;
import com.jvn.commonfolk.client.renderer.CommonfolkVillagerRenderer;
import com.jvn.commonfolk.client.renderer.CommonfolkWanderingTraderRenderer;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class CommonfolkClientRenderers {
    private CommonfolkClientRenderers() {
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EntityType.VILLAGER, CommonfolkVillagerRenderer::new);
        event.registerEntityRenderer(EntityType.WANDERING_TRADER, CommonfolkWanderingTraderRenderer::new);
    }

    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(CommonfolkVillagerModel.LAYER_LOCATION, CommonfolkVillagerModel::createBodyLayer);
    }
}
