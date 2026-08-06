package com.jvn.villagerretaliation;

import io.wispforest.owo.config.ui.ConfigScreenProviders;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = VillagerRetaliation.MOD_ID, dist = Dist.CLIENT)
public final class VillagerRetaliationClient {
    public VillagerRetaliationClient(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, (IConfigScreenFactory) VillagerRetaliationClient::createConfigScreen);
        modEventBus.addListener(com.jvn.villagerretaliation.client.VillagerRetaliationClientRenderers::registerRenderers);
        modEventBus.addListener(VillagerRetaliationClient::registerClientExtensions);
        modEventBus.addListener(com.jvn.villagerretaliation.client.VillagerRetaliationClientRenderers::registerLayerDefinitions);
        modEventBus.addListener(com.jvn.villagerretaliation.client.quest.VillagerQuestKeyMappings::register);
        modEventBus.addListener(com.jvn.villagerretaliation.client.item.ConstructionBlueprintKeyMappings::register);
        modEventBus.addListener(com.jvn.villagerretaliation.client.villager.VillagerNameTagKeyMappings::register);
        modEventBus.addListener(com.jvn.villagerretaliation.client.party.PartyQuickCommandKeyMappings::register);
        modEventBus.addListener(com.jvn.villagerretaliation.client.party.PartyQuickCommandWheel::registerGuiLayer);
        modEventBus.addListener(com.jvn.villagerretaliation.client.mount.VillagerMountTargetClient::registerGuiLayer);
        com.jvn.villagerretaliation.client.item.VillagerFishingRodItemProperties.register();
        modEventBus.addListener(com.jvn.villagerretaliation.client.item.ConstructionBlueprintItemProperties::register);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.villager.VillagerNameTagOverlay::onRenderNameTag);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.villager.VillagerStatNameTagOverlay::onRenderNameTag);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.villager.VillagerNameTagOverlay::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.villager.VillagerNameTagOverlay::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.villager.VillagerDownedClientCache::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.villager.VillagerHungerClientCache::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.villager.VillagerDownedClientCache::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.villager.VillagerWorkAnimationClientCache::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.villager.VillagerWorkAnimationClientCache::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.villager.VillagerDownedClientCache::onEntitySize);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.reputation.VillagerReputationDebugOverlay::onRenderNameTag);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.reputation.VillagerReputationTradeScreenOverlay::onScreenRender);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.trade.VillagerTradeRefreshButtons::onScreenRender);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.trade.VillagerTradeRefreshButtons::onMousePressed);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.reputation.VillagerReputationHoverTooltipOverlay::onRenderGuiLayer);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.reputation.VillagerReputationHoverTooltipOverlay::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.reputation.VillagerReputationDebugOverlay::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.reputation.VillagerReputationDebugOverlay::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.reputation.VillagerReputationNotificationOverlay::onRenderGuiLayer);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.reputation.VillagerReputationNotificationOverlay::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.reputation.VillagerReputationNotificationOverlay::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.quest.VillagerQuestTrackerOverlay::onRenderGuiLayer);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.quest.VillagerQuestTrackerOverlay::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.quest.VillagerQuestTrackerOverlay::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.interaction.BuilderStructureCatalogClient::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.quest.VillagerQuestItemHighlightClient::onRenderGuiLayer);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.quest.VillagerQuestItemHighlightClient::onContainerForeground);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.quest.VillagerQuestItemHighlightClient::onItemTooltip);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.item.BannerHelmetTooltip::onItemTooltip);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.reputation.FearedVillagerAnimationClientCache::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.reputation.VillagerWorldTextIndicatorClient::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.profile.VillagerProfileClientCache::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.profile.VillagerProfileClientCache::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.interaction.VillagerInteractionHudHider::onRenderGuiPre);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.interaction.VillagerInteractionHudHider::onRenderGuiPost);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.interaction.VillagerInteractionHudHider::onRenderGuiLayerPre);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.interaction.VillagerInteractionHudHider::onRenderGuiLayerPost);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.interaction.VillagerChatTextFormatter::onClientChatReceived);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.interaction.VillagerInteractionClientHandler::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.duel.DuelInventoryClientState::onScreenClosing);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.duel.DuelInventoryClientState::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.inventory.GeneratedContainerTooltipClient::onItemTooltip);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.inventory.SellBoxClientState::onItemTooltip);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.inventory.SellBoxClientState::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.inventory.GeneratedContainerTooltipClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.inventory.GeneratedContainerTooltipClient::onKeyPressed);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.inventory.ClipboardStorageOutlineRenderer::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.inventory.ClipboardStorageOutlineRenderer::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.inventory.ClipboardStorageOutlineRenderer::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.allegiance.VillageBoundsDebugRenderer::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.allegiance.VillageBoundsDebugRenderer::onRenderGuiLayer);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.allegiance.VillageBoundsDebugRenderer::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.allegiance.VillageBoundsDebugRenderer::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.item.ConstructionBlueprintPreviewRenderer::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.item.ConstructionBlueprintPlacementClient::onMouseScroll);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.item.ConstructionBlueprintPlacementClient::onMouseButtonPressed);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.item.ConstructionBlueprintPlacementClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.inventory.ClipboardModeClient::onMouseScroll);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.inventory.ClipboardModeClient::onMouseButtonPressed);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.inventory.ItemFilterModeClient::onMouseButtonPressed);
        NeoForge.EVENT_BUS.addListener(
                net.neoforged.bus.api.EventPriority.HIGHEST,
                com.jvn.villagerretaliation.client.party.PartyInventoryOverlay::onMousePressed);
        com.jvn.villagerretaliation.client.party.AccessoriesPartyTabsCompat.register();
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.party.PartyRosterClient::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.party.PartyQuickCommandWheel::onClientTickPre);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.party.PartyQuickCommandWheel::onClientTickPost);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.party.PartyQuickCommandWheel::onMouseButton);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.party.PartyQuickCommandWheel::onMouseScroll);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.party.PartyQuickCommandTargetRenderer::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.mount.VillagerMountTargetClient::onKey);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.mount.VillagerMountTargetClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.mount.VillagerMountTargetClient::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.interaction.VillagerDialogueCameraFocus::onComputeFov);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.interaction.VillagerDialogueCameraFocus::onComputeCameraAngles);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.client.interaction.VillagerDialogueCameraFocus::onClientTick);
        modEventBus.addListener(VillagerRetaliationClient::registerTooltipComponents);
        modEventBus.addListener(VillagerRetaliationClient::registerMenuScreens);
        modEventBus.addListener(com.jvn.villagerretaliation.client.interaction.VillagerInteractionScreenShaderRenderer::registerShaders);
        modEventBus.addListener(com.jvn.villagerretaliation.client.villager.VillagerStatNameTagOverlay::registerReloadListener);
    }


    private static void registerClientExtensions(
            net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer renderer =
                new com.jvn.villagerretaliation.client.renderer.SellBoxItemRenderer(
                        minecraft.getBlockEntityRenderDispatcher(),
                        minecraft.getEntityModels());
        event.registerItem(
                new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
                    @Override
                    public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                        return renderer;
                    }
                },
                com.jvn.villagerretaliation.item.VillagerRetaliationItems.SELL_BOX.get());
    }
    private static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(
                com.jvn.villagerretaliation.inventory.VillagerRetaliationMenus.VILLAGER_INVENTORY.get(),
                com.jvn.villagerretaliation.client.inventory.VillagerInventoryScreen::new
        );
        event.register(
                com.jvn.villagerretaliation.inventory.VillagerRetaliationMenus.PAYMENT_BOX.get(),
                com.jvn.villagerretaliation.client.inventory.PaymentBoxScreen::new
        );
        event.register(
                com.jvn.villagerretaliation.inventory.VillagerRetaliationMenus.SELL_BOX.get(),
                com.jvn.villagerretaliation.client.inventory.SellBoxScreen::new
        );
        event.register(
                com.jvn.villagerretaliation.inventory.VillagerRetaliationMenus.ITEM_FILTER.get(),
                com.jvn.villagerretaliation.client.inventory.VillagerItemFilterScreen::new
        );
        event.register(
                com.jvn.villagerretaliation.inventory.VillagerRetaliationMenus.ATTRIBUTE_FILTER.get(),
                com.jvn.villagerretaliation.client.inventory.VillagerAttributeFilterScreen::new
        );
        event.register(
                com.jvn.villagerretaliation.inventory.VillagerRetaliationMenus.RECIPE_FILTER.get(),
                com.jvn.villagerretaliation.client.inventory.VillagerRecipeFilterScreen::new
        );
    }

    private static void registerTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(
                com.jvn.villagerretaliation.client.reputation.VillagerReputationTooltipComponent.class,
                com.jvn.villagerretaliation.client.reputation.VillagerReputationClientTooltipComponent::new
        );
    }

    private static Screen createConfigScreen(ModContainer container, Screen parent) {
        var provider = ConfigScreenProviders.get(VillagerRetaliation.MOD_ID);
        if (provider == null) {
            throw new IllegalStateException("Missing owo config screen provider for " + VillagerRetaliation.MOD_ID);
        }
        return provider.apply(parent);
    }
}
