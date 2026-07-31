package com.jvn.villagerretaliation.client;
import com.jvn.villagerretaliation.block.VillagerRetaliationBlockEntityTypes;

import com.jvn.villagerretaliation.client.model.VillagerArmorModel;
import com.jvn.villagerretaliation.client.model.VillagerRetaliationVillagerModel;
import com.jvn.villagerretaliation.client.renderer.VillagerFishingHookRenderer;
import com.jvn.villagerretaliation.client.renderer.VillagerRetaliationVillagerRenderer;
import com.jvn.villagerretaliation.client.renderer.PaymentBoxRenderer;
import com.jvn.villagerretaliation.client.renderer.SellBoxRenderer;
import com.jvn.villagerretaliation.client.renderer.VillagerRetaliationWanderingTraderRenderer;
import com.jvn.villagerretaliation.entity.VillagerRetaliationEntityTypes;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class VillagerRetaliationClientRenderers {
    private VillagerRetaliationClientRenderers() {
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EntityType.VILLAGER, VillagerRetaliationVillagerRenderer::new);
        event.registerEntityRenderer(EntityType.WANDERING_TRADER, VillagerRetaliationWanderingTraderRenderer::new);
        event.registerEntityRenderer(VillagerRetaliationEntityTypes.VILLAGER_FISHING_HOOK.get(), VillagerFishingHookRenderer::new);
        event.registerBlockEntityRenderer(VillagerRetaliationBlockEntityTypes.PAYMENT_BOX.get(), PaymentBoxRenderer::new);
        event.registerBlockEntityRenderer(VillagerRetaliationBlockEntityTypes.SELL_BOX.get(), SellBoxRenderer::new);
    }

    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(VillagerRetaliationVillagerModel.LAYER_LOCATION, VillagerRetaliationVillagerModel::createBodyLayer);
        event.registerLayerDefinition(VillagerArmorModel.INNER_ARMOR, VillagerArmorModel::createInnerArmorLayer);
        event.registerLayerDefinition(VillagerArmorModel.OUTER_ARMOR, VillagerArmorModel::createOuterArmorLayer);
    }
}
