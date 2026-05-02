package com.jvn.villagerretaliation.client;

import com.jvn.villagerretaliation.client.model.VillagerRetaliationVillagerModel;
import com.jvn.villagerretaliation.client.renderer.VillagerRetaliationVillagerRenderer;
import com.jvn.villagerretaliation.client.renderer.VillagerRetaliationWanderingTraderRenderer;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class VillagerRetaliationClientRenderers {
    private VillagerRetaliationClientRenderers() {
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EntityType.VILLAGER, VillagerRetaliationVillagerRenderer::new);
        event.registerEntityRenderer(EntityType.WANDERING_TRADER, VillagerRetaliationWanderingTraderRenderer::new);
    }

    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(VillagerRetaliationVillagerModel.LAYER_LOCATION, VillagerRetaliationVillagerModel::createBodyLayer);
    }
}
