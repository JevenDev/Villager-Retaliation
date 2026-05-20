package com.jvn.villagerretaliation;

import com.jvn.toucanlib.neoforge.config.ToucanConfigScreens;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = VillagerRetaliation.MOD_ID, dist = Dist.CLIENT)
public final class VillagerRetaliationClient {
    public VillagerRetaliationClient(IEventBus modEventBus, ModContainer modContainer) {
        ToucanConfigScreens.register(modContainer, (IConfigScreenFactory) ConfigurationScreen::new);
        modEventBus.addListener(com.jvn.villagerretaliation.client.VillagerRetaliationClientRenderers::registerRenderers);
        modEventBus.addListener(com.jvn.villagerretaliation.client.VillagerRetaliationClientRenderers::registerLayerDefinitions);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.villager.VillagerNameTagOverlay::onRenderNameTag);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.villager.VillagerNameTagOverlay::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.villager.VillagerNameTagOverlay::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.reputation.VillagerReputationDebugOverlay::onRenderNameTag);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.reputation.VillagerReputationTradeScreenOverlay::onScreenRender);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.reputation.VillagerReputationHoverTooltipOverlay::onRenderGuiLayer);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.reputation.VillagerReputationHoverTooltipOverlay::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.reputation.VillagerReputationDebugOverlay::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.reputation.VillagerReputationDebugOverlay::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.reputation.VillagerReputationNotificationOverlay::onRenderGuiLayer);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.reputation.VillagerReputationNotificationOverlay::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.reputation.VillagerReputationNotificationOverlay::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.reputation.FearedVillagerAnimationClientCache::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.reputation.VillagerWorldTextIndicatorClient::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.interaction.VillagerInteractionHudHider::onRenderGuiLayer);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.interaction.VillagerDialogueCameraFocus::onComputeFov);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.interaction.VillagerDialogueCameraFocus::onClientTick);
        modEventBus.addListener(VillagerRetaliationClient::registerTooltipComponents);
        modEventBus.addListener(VillagerRetaliationClient::registerMenuScreens);
        modEventBus.addListener(com.jvn.villagerretaliation.client.interaction.VillagerInteractionScreenShaderRenderer::registerShaders);
    }

    private static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(
                com.jvn.villagerretaliation.inventory.VillagerRetaliationMenus.VILLAGER_INVENTORY.get(),
                com.jvn.villagerretaliation.client.inventory.VillagerInventoryScreen::new
        );
    }

    private static void registerTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(
                com.jvn.villagerretaliation.client.reputation.VillagerReputationTooltipComponent.class,
                com.jvn.villagerretaliation.client.reputation.VillagerReputationClientTooltipComponent::new
        );
    }
}
