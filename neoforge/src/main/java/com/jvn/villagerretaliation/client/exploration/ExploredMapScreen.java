package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.client.ui.VillagerTooltips;
import com.jvn.villagerretaliation.exploration.*;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.client.ui.VillagerClientUiUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class ExploredMapScreen extends Screen implements VillagerTooltips.Provider {
    private static final ExploredMapRenderer TILES = new ExploredMapRenderer();
    private final Screen parent;
    private final MapLocationMenus locations = new MapLocationMenus(this);
    private final VillageRelationsMap relations = ExploredMapClient.relations();
    private final MapLayers layers = new MapLayers(false);
    private String selectedMarker = "";
    private java.util.List<MapMarkerLayout.Group> renderedMarkers = java.util.List.of();
    private final MapNavigation navigation = new MapNavigation();
    private final MapScreenTransition transition = new MapScreenTransition();
    private final MapPages pages = new MapPages();
    private boolean centered, hudKeyDown;
    private MapZoomScale zoomScale;
    private long renderedFrames;
    public ExploredMapScreen() { this(null); }
    public ExploredMapScreen(Screen parent) { super(Component.translatable("map.villagerretaliation.title")); this.parent = parent; }
    public void focusRelations(java.util.UUID perspective, java.util.UUID village, com.jvn.villagerretaliation.network.VillageRelationsPayload.Entry entry) {
        navigation.focus(entry.position().getX(), entry.position().getZ()); centered = true;
        relations.focus(perspective, village);
    }
    @Override public void tick() {
        if (transition.closing() && (!VillagerRetaliationConfig.ANIMATE_EXPLORED_MAP_OPENING.get() || transitionProgress() == 0)) {
            minecraft.setScreen(parent);
            return;
        }
        relations.tick(layers);
    }
    @Override public void onClose() {
        if (transition.close(VillagerRetaliationConfig.ANIMATE_EXPLORED_MAP_OPENING.get())) {
            minecraft.setScreen(parent);
            return;
        }
        navigation.clear();
    }
    static void resetTiles() { TILES.close(); }
    long renderedFrames() { return renderedFrames; }
    @Override protected void init() {
        closePopups();
        MapLocationsClient.refresh();
        transition.open();
        navigation.resize(width, height);
        if (!centered && minecraft.player != null) { center(); centered = true; }
        zoomScale = addRenderableWidget(new MapZoomScale(height, () -> view().scale(),
                scale -> zoomAt(width / 2.0, height / 2.0, scale)));
        updateHudVisibility();
    }
    MapBottomPage settingsPage() { return pages.bottom(); }
    private void updateHudVisibility() {
        boolean visible = !minecraft.options.hideGui;
        for (var child : children()) if (child instanceof AbstractWidget widget) widget.visible = visible;
        if (!visible) {
            setFocused(null);
            closePopups();
            renderedMarkers = java.util.List.of();
        }
    }
    private void center() {
        if (minecraft.player != null) navigation.focus(minecraft.player.getX(), minecraft.player.getZ());
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float partial) { }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        updateHudVisibility();
        if (!minecraft.isWindowActive()) hudKeyDown = false;
        navigation.update(System.nanoTime(), minecraft.isWindowActive());
        var repository = ExploredMapClient.repository();
        var lighting = minecraft.level == null ? MapLighting.DAY : MapLighting.at(minecraft.level.getTimeOfDay(partial), minecraft.level.dimensionType().hasSkyLight());
        float opening = transitionProgress();
        if (opening < .4f) {
            TILES.prepare(view());
            MapFogRenderer.renderOpening(graphics, width, height, lighting, opening);
            return;
        }
        if (repository != null) {
            var view = view();
            TILES.render(graphics, view, lighting);
            MapFogRenderer.renderAtmosphere(graphics, view, lighting);
            renderedFrames++;
            if (!minecraft.options.hideGui) {
                graphics.enableScissor(0, 0, width, height);
                relations.renderTerrain(graphics, view, layers);
                relations.render(graphics, view, font, layers, false);
                var destinations = MapMarkerOverlay.visible(destinations(), layers);
                for (var destination : destinations)
                    if (destination.kind() == MapDestination.Kind.QUEST) QuestMapOverlay.renderArea(graphics, view, destination);
                renderedMarkers = MapMarkerLayout.groups(destinations, view, priorityMarker());
                MapMarkerOverlay.render(graphics, view, font, renderedMarkers, layers, priorityMarker(), mouseX, mouseY, false);
                if (minecraft.player != null) MapPlayerIcon.render(graphics, (float)view.screenX(minecraft.player.getX()),
                        (float)view.screenY(minecraft.player.getZ()), minecraft.player.getViewYRot(partial));
                MapDestinationTracker.render(graphics, view, font, false);
                MapOrientation.render(graphics, view, font, mouseX, mouseY, false);
                graphics.disableScissor();
                if (!repository.error().isEmpty()) graphics.drawString(font, Component.translatable("map.villagerretaliation.storage_error"), 160, height - 20, 0xff9999);
            }
        } else {
            graphics.fill(0, 0, width, height, lighting.background());
            if (!minecraft.options.hideGui) graphics.drawCenteredString(font, Component.translatable("map.villagerretaliation.connecting"), width / 2, height / 2, 0xddd7cc);
        }
        if (opening < 1) MapFogRenderer.renderOpening(graphics, width, height, lighting, opening);
        if (minecraft.options.hideGui) return;
        float hudOpening = hudProgress();
        float fade = MapScreenTransition.ease(Math.clamp((opening - .8f) / .2f, 0, 1));
        float controlsAlpha = fade * MapScreenTransition.ease(hudOpening);
        for (var child : children()) if (child instanceof AbstractWidget widget) widget.setAlpha(controlsAlpha);
        if (fade < .02f) return;
        pages.render(graphics, width, height, mouseX, mouseY, view().scale(), fade, VillagerClientUiUtil.easeOutCubic(hudOpening));
        if (minecraft.player != null) MapCompass.render(graphics, width, height, minecraft.player.getViewYRot(partial), fade, pages.compassX(), pages.compassY());
        if (controlsAlpha < .02f) return;
        super.render(graphics, mouseX, mouseY, partial);
        relations.renderPopup(graphics, font, mouseX, mouseY, partial);
        if (!relations.hasPopup()) {
            locations.render(graphics, font, mouseX, mouseY, partial);
            if (!locations.hasPopup()) {
                var control = hoveredControl(mouseX, mouseY);
                if (control != null) graphics.renderComponentTooltip(font, control.tooltip(), mouseX, mouseY);
                else if (minecraft.player != null && overCompass(mouseX, mouseY))
                    graphics.renderComponentTooltip(font, MapCompass.tooltip(minecraft.player, minecraft.player.getViewYRot(partial)), mouseX, mouseY);
            }
        }
    }
    float transitionProgress() { return transition.progress(VillagerRetaliationConfig.ANIMATE_EXPLORED_MAP_OPENING.get()); }
    float hudProgress() { return transition.hudProgress(VillagerRetaliationConfig.ANIMATE_EXPLORED_MAP_OPENING.get()); }
    public boolean coversWorld() { return transitionProgress() >= .4f; }
    float transitionPitch(float pitch) { return transition.pitch(pitch, VillagerRetaliationConfig.ANIMATE_EXPLORED_MAP_OPENING.get()); }
    private void finishOpening() { transition.finishOpening(); }
    MapView view() { return navigation.view(); }
    java.util.List<MapDestination> destinations() {
        var repository = ExploredMapClient.repository();
        return MapMarkerOverlay.destinations(repository == null ? java.util.List.of() : repository.knownMarkers(), relations.destinations(layers));
    }
    java.util.List<MapMarkerLayout.Group> markerGroups() {
        return MapMarkerLayout.groups(MapMarkerOverlay.visible(destinations(), layers), view(), priorityMarker());
    }
    private String priorityMarker() { return MapDestinationTracker.key().isEmpty() ? selectedMarker : MapDestinationTracker.key(); }
    MapMarkerLayout.Group hoveredMarker(double x, double y) { return minecraft.options.hideGui || overMapControl(x, y) || overCompass(x, y) ? null : MapMarkerOverlay.at(renderedMarkers, x, y); }
    MapMarkerLayout.Group markerAt(double x, double y) { return minecraft.options.hideGui || overMapControl(x, y) ? null : MapMarkerOverlay.at(markerGroups(), x, y); }
    private boolean overWidget(double x, double y) {
        for (var child : children()) if (child instanceof AbstractWidget widget && widget.isMouseOver(x, y)) return true;
        return false;
    }
    private MapPages.Control hoveredControl(double x, double y) { return overWidget(x, y) ? null : pages.controlAt(x, y); }
    private void closePopups() { locations.close(); relations.closePopup(); }
    private void openControl(MapPages.Control control) {
        closePopups();
        switch (control) {
            case ROUTES -> minecraft.setScreen(new SettlementRoutesScreen(this));
            case VILLAGES -> minecraft.setScreen(new com.jvn.villagerretaliation.client.settlement.VillageRelationsScreen(this, null, null, null));
            case SEARCH -> minecraft.setScreen(new MapSearchScreen(this));
            case RELATIONS -> relations.toggle();
            case SETTINGS -> minecraft.setScreen(new MapLayersScreen(this));
        }
    }
    private boolean overCompass(double x, double y) { return MapCompass.hovered(width, height, x, y, pages.compassX(), pages.compassY()); }
    private boolean overMapControl(double x, double y) { return overWidget(x, y) || pages.controlAt(x, y) != null; }
    void showDestination(MapDestination destination) { locations.open(destination, width / 2, height / 2); }
    void shareLocations() { locations.shareAll(); }
    void selectMarker(MapDestination destination) { selectedMarker = destination.key(); }
    void focusDestination(MapDestination destination) {
        navigation.focus(destination.x(), destination.z()); centered = true; selectMarker(destination);
    }
    boolean openVillage(MapDestination destination, double x, double y, int button) { return relations.click(this, destination, x, y, button); }
    private void zoomAt(double x, double y, double target) {
        finishOpening();
        navigation.zoomAt(x, y, target);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (transition.closing()) return true;
        finishOpening();
        if (!minecraft.options.hideGui && (locations.scroll(x, y, vertical) || relations.scroll(x, y, vertical)
                || zoomScale.mouseScrolled(x, y, horizontal, vertical) || pages.controlAt(x, y) != null)) return true;
        zoomAt(x, y, view().scale() * Math.pow(1.15, vertical)); return true;
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (ExploredMapClient.OPEN.matchesMouse(button)) { onClose(); return true; }
        if (transition.closing()) return true;
        finishOpening();
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && minecraft.player != null) {
            navigation.resetTo(minecraft.player.getX(), minecraft.player.getZ());
            return true;
        }
        if (minecraft.options.hideGui) {
            if (button == 0) { navigation.beginDrag(); return true; }
            return false;
        }
        if (locations.clickOpen(x, y, button)) return true;
        if (relations.clickOpen(x, y, button)) return true;
        if (super.mouseClicked(x, y, button)) {
            if (getFocused() == zoomScale) navigation.clear();
            return true;
        }
        var control = hoveredControl(x, y);
        if (control != null) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) openControl(control);
            return true;
        }
        if (overMapControl(x, y)) return true;
        if (locations.clickMarker(x, y, button)) return true;
        if (relations.clickCourier(view(), layers, x, y, button)) return true;
        if (locations.place(x, y, button)) return true;
        if (button == 0) { setFocused(null); navigation.beginDrag(); return true; } return false;
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0) navigation.endDrag(); return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && navigation.drag(dx, dy)) return true;
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public void mouseMoved(double x, double y) {
        if (!minecraft.options.hideGui) {
            locations.hover(x, y);
            super.mouseMoved(x, y);
        }
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (transition.closing()) {
            if (ExploredMapClient.OPEN.matches(key, scan) || key == GLFW.GLFW_KEY_ESCAPE) onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_F1) {
            if (!hudKeyDown) {
                hudKeyDown = true;
                minecraft.options.hideGui = !minecraft.options.hideGui;
                updateHudVisibility();
            }
            return true;
        }
        if (!minecraft.options.hideGui && locations.key(key)) { finishOpening(); return true; }
        if (!minecraft.options.hideGui && relations.key(key)) { finishOpening(); return true; }
        if (ExploredMapClient.OPEN.matches(key, scan) || key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        finishOpening();
        if (!minecraft.options.hideGui && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_F) {
            openControl(MapPages.Control.SEARCH); return true;
        }
        if (!minecraft.options.hideGui && getFocused() == zoomScale && zoomScale.keyPressed(key, scan, modifiers)) return true;
        if (key == GLFW.GLFW_KEY_HOME) { center(); return true; }
        if (navigation.press(key)) return true; return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean keyReleased(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_F1) { hudKeyDown = false; return true; }
        if (navigation.release(key)) return true; return super.keyReleased(key, scan, modifiers);
    }
    @Override public void removed() { hudKeyDown = false; navigation.clear(); transition.suspend(); relations.closePopup(); }
}
