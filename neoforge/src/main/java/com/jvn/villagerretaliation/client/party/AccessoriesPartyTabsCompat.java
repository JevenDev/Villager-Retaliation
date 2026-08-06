package com.jvn.villagerretaliation.client.party;

import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Adds the party inventory tabs to Accessories' experimental inventory without linking
 * Accessories classes when the optional mod is absent.
 */
public final class AccessoriesPartyTabsCompat {
    private static final String ACCESSORIES_MOD_ID = "accessories";

    private AccessoriesPartyTabsCompat() {
    }

    public static void register() {
        if (ModList.get().isLoaded(ACCESSORIES_MOD_ID)) {
            Loaded.register();
        }
    }

    /** Loaded only after Accessories' mod id has been confirmed present. */
    private static final class Loaded {
        private static final String ENTITY_COMPONENT_ID = "entity_rendering_component";
        private static final int ENTITY_PANEL_PADDING = 6;

        private Loaded() {
        }

        private static void register() {
            NeoForge.EVENT_BUS.addListener(Loaded::onScreenRender);
            NeoForge.EVENT_BUS.addListener(Loaded::onInventoryScreenRender);
            NeoForge.EVENT_BUS.addListener(Loaded::onMousePressed);
        }

        private static void onScreenRender(
                net.neoforged.neoforge.client.event.ScreenEvent.Render.Post event) {
            if (!(event.getScreen()
                    instanceof io.wispforest.accessories.client.gui.AccessoriesExperimentalScreen screen)
                    || !PartyInventoryOverlay.externalTabsAvailable()) {
                return;
            }
            TabAnchor anchor = tabAnchor(screen);
            if (anchor == null) return;

            PartyInventoryOverlay.renderExternalTabs(
                    event.getGuiGraphics(),
                    anchor.right(),
                    anchor.top(),
                    event.getMouseX(),
                    event.getMouseY());
        }

        private static void onInventoryScreenRender(
                net.neoforged.neoforge.client.event.ScreenEvent.Render.Pre event) {
            if (!(event.getScreen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen screen)) {
                return;
            }
            boolean visible =
                    PartyInventoryOverlay.page(screen) != PartyInventoryOverlay.Page.SETTINGS;
            updateAccessoriesButtonVisibility(screen, visible);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static void updateAccessoriesButtonVisibility(
                net.minecraft.client.gui.screens.inventory.InventoryScreen screen,
                boolean visible) {
            java.util.Collection<?> creativeInventoryLayers =
                    io.wispforest.owo.ui.layers.Layers.getLayers(
                            net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen.class);
            for (Object candidate :
                    io.wispforest.owo.ui.layers.Layers.getLayers(
                            net.minecraft.client.gui.screens.inventory.InventoryScreen.class)) {
                if (!creativeInventoryLayers.contains(candidate)) continue;

                io.wispforest.owo.ui.layers.Layer layer =
                        (io.wispforest.owo.ui.layers.Layer) candidate;
                io.wispforest.owo.ui.layers.Layer.Instance instance = layer.getInstance(screen);
                if (instance == null) continue;

                for (Object child : instance.adapter.rootComponent.children()) {
                    if (child instanceof io.wispforest.owo.ui.component.ButtonComponent button) {
                        button.visible = visible;
                        button.active(visible);
                    }
                }
            }
        }

        private static void onMousePressed(
                net.neoforged.neoforge.client.event.ScreenEvent.MouseButtonPressed.Pre event) {
            if (event.isCanceled()
                    || event.getButton() != org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT
                    || !(event.getScreen()
                            instanceof io.wispforest.accessories.client.gui.AccessoriesExperimentalScreen screen)
                    || !PartyInventoryOverlay.externalTabsAvailable()) {
                return;
            }
            TabAnchor anchor = tabAnchor(screen);
            if (anchor == null) return;

            PartyInventoryOverlay.Page clicked = PartyInventoryOverlay.externalTabAt(
                    anchor.right(),
                    anchor.top(),
                    event.getMouseX(),
                    event.getMouseY());
            if (clicked == null) return;

            event.setCanceled(true);
            PartyInventoryOverlay.openFromExternalInventory(clicked, screen::switchToBaseInventory);
        }

        private static TabAnchor tabAnchor(
                io.wispforest.accessories.client.gui.AccessoriesExperimentalScreen screen) {
            io.wispforest.owo.ui.core.Component entity = screen.component(
                    io.wispforest.owo.ui.core.Component.class,
                    ENTITY_COMPONENT_ID);
            if (entity == null) return null;

            return new TabAnchor(
                    entity.x() + entity.width() + ENTITY_PANEL_PADDING,
                    entity.y() - ENTITY_PANEL_PADDING);
        }

        private record TabAnchor(int right, int top) {
        }
    }
}
