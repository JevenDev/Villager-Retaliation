package com.jvn.villagerretaliation;

import com.jvn.toucanlib.neoforge.config.ToucanConfigScreens;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = VillagerRetaliation.MOD_ID, dist = Dist.CLIENT)
public final class VillagerRetaliationClient {
    public VillagerRetaliationClient(IEventBus modEventBus, ModContainer modContainer) {
        ToucanConfigScreens.register(modContainer, (IConfigScreenFactory) ConfigurationScreen::new);
        modEventBus.addListener(com.jvn.villagerretaliation.client.VillagerRetaliationClientRenderers::registerRenderers);
        modEventBus.addListener(com.jvn.villagerretaliation.client.VillagerRetaliationClientRenderers::registerLayerDefinitions);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.reputation.VillagerReputationDebugOverlay::onRenderNameTag);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.reputation.VillagerReputationTradeScreenOverlay::onScreenRender);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.reputation.VillagerReputationDebugOverlay::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.reputation.VillagerReputationDebugOverlay::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.reputation.FearedVillagerAnimationClientCache::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.interaction.VillagerDialogueCameraFocus::onComputeFov);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.interaction.VillagerDialogueCameraFocus::onClientTick);
    }
}
