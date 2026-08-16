package com.jvn.villagerretaliation.client.item;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.item.ConstructionBlueprintItem;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

public final class ConstructionBlueprintItemProperties {
    private static final ResourceLocation PLAN_PROPERTY = VillagerRetaliation.id("plan");

    private ConstructionBlueprintItemProperties() {
    }

    public static void register(FMLClientSetupEvent event) {
        event.enqueueWork(() ->
                ItemProperties.register(
                        VillagerRetaliationItems.CONSTRUCTION_BLUEPRINT.get(),
                        PLAN_PROPERTY,
                        (stack, level, entity, seed) ->
                                ConstructionBlueprintItem.isStarted(stack) ? 1.0F : 0.0F));
    }
}
