package com.jvn.commonfolk;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = Commonfolk.MOD_ID, dist = Dist.CLIENT)
public final class CommonfolkClient {
    public CommonfolkClient(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(com.jvn.commonfolk.client.CommonfolkClientRenderers::registerRenderers);
        modEventBus.addListener(com.jvn.commonfolk.client.CommonfolkClientRenderers::registerLayerDefinitions);
    }
}
