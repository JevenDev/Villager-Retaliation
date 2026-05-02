package com.jvn.villagerretaliation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = VillagerRetaliation.MOD_ID, dist = Dist.CLIENT)
public final class VillagerRetaliationClient {
    public VillagerRetaliationClient(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(com.jvn.villagerretaliation.client.VillagerRetaliationClientRenderers::registerRenderers);
        modEventBus.addListener(com.jvn.villagerretaliation.client.VillagerRetaliationClientRenderers::registerLayerDefinitions);
    }
}
