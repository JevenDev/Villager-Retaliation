package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.exploration.*;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class ExploredMapScreen extends Screen {
    private static final ExploredMapRenderer TILES = new ExploredMapRenderer();
    private final Screen parent;
    private final MapLocationMenus locations = new MapLocationMenus(this);
    private final VillageRelationsMap relations = ExploredMapClient.relations();
    private final MapLayers layers = new MapLayers(false);
    private String selectedMarker = "";
    private java.util.List<MapMarkerLayout.Group> renderedMarkers = java.util.List.of();
    private final MapNavigation navigation = new MapNavigation();
    private final MapScreenTransition transition = new MapScreenTransition();
    private boolean centered, following;
    private Button followButton;
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
    void focusLocation(MapLocation location) {
        follow(false);
        navigation.focus(location.x(), location.z()); centered = true;
    }
    @Override protected void init() {
        locations.close();
        relations.closePopup();
        MapLocationsClient.refresh();
        transition.open();
        navigation.resize(width, height);
        if (!centered && minecraft.player != null) { center(); centered = true; }
        addRenderableWidget(Button.builder(MapLocationsClient.text("title"), b -> { relations.closePopup(); locations.close(); minecraft.setScreen(new MapSearchScreen(this)); })
                .bounds(154, height - 26, 80, 20).tooltip(net.minecraft.client.gui.components.Tooltip.create(MapLayers.text("search_shortcut"))).build());
        addRenderableWidget(Button.builder(com.jvn.villagerretaliation.client.settlement.VillageRelationsScreen.text("title"),
                b -> { locations.close(); relations.toggle(); }).bounds(10, 8, 80, 20).build());
        addRenderableWidget(Button.builder(com.jvn.villagerretaliation.client.settlement.VillageRelationsScreen.text("villages"),
                b -> minecraft.setScreen(new com.jvn.villagerretaliation.client.settlement.VillageRelationsScreen(this, null, null, null)))
                .bounds(width - 90, 8, 80, 20).build());
        followButton = addRenderableWidget(Button.builder(MapLayers.text(following ? "follow_on" : "follow_off"), b -> follow(!following))
                .bounds(10, 34, 90, 20).tooltip(net.minecraft.client.gui.components.Tooltip.create(MapLayers.text("follow_hint"))).build());
        addRenderableWidget(Button.builder(MapLayers.text("layers"), b -> minecraft.setScreen(new MapLayersScreen(this)))
                .bounds(width - 76, 34, 66, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width - 76, height - 26, 66, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("map.villagerretaliation.center"), b -> center()).bounds(10, height - 26, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("\u2212"), b -> zoomAt(width / 2.0, height / 2.0, view().scale() / 1.25)).bounds(96, height - 26, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> zoomAt(width / 2.0, height / 2.0, view().scale() * 1.25)).bounds(124, height - 26, 24, 20).build());
    }
    private void follow(boolean enabled) {
        following = enabled;
        if (followButton != null) followButton.setMessage(MapLayers.text(following ? "follow_on" : "follow_off"));
        if (following) center();
    }
    private void center() {
        if (minecraft.player != null) navigation.focus(minecraft.player.getX(), minecraft.player.getZ());
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float partial) { }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        navigation.update(System.nanoTime(), minecraft.isWindowActive());
        if (following && minecraft.screen == this && minecraft.player != null) navigation.focus(minecraft.player.getX(), minecraft.player.getZ());
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
            graphics.disableScissor(); renderedFrames++;
            if (!repository.error().isEmpty()) graphics.drawString(font, Component.translatable("map.villagerretaliation.storage_error"), 160, height - 20, 0xff9999);
        } else {
            graphics.fill(0, 0, width, height, lighting.background());
            graphics.drawCenteredString(font, Component.translatable("map.villagerretaliation.connecting"), width / 2, height / 2, 0xddd7cc);
        }
        if (opening < 1) MapFogRenderer.renderOpening(graphics, width, height, lighting, opening);
        float controlsAlpha = MapScreenTransition.ease(Math.clamp((opening - .8f) / .2f, 0, 1));
        for (var child : children()) if (child instanceof AbstractWidget widget) widget.setAlpha(controlsAlpha);
        if (controlsAlpha < .02f) return;
        int titleColor = repository != null && lighting.red() + lighting.green() + lighting.blue() > 2.1f ? 0x40464f : 0xffffff;
        graphics.drawCenteredString(font, title, width / 2, 12, titleColor | ((int)(controlsAlpha * 255) << 24));
        super.render(graphics, mouseX, mouseY, partial);
        relations.renderPopup(graphics, font, mouseX, mouseY, partial);
        if (!relations.hasPopup()) locations.render(graphics, font, mouseX, mouseY, partial);
    }
    float transitionProgress() { return transition.progress(VillagerRetaliationConfig.ANIMATE_EXPLORED_MAP_OPENING.get()); }
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
    MapMarkerLayout.Group hoveredMarker(double x, double y) { return MapMarkerOverlay.at(renderedMarkers, x, y); }
    MapMarkerLayout.Group markerAt(double x, double y) { return MapMarkerOverlay.at(markerGroups(), x, y); }
    void showDestination(MapDestination destination) { locations.open(destination, width / 2, height / 2); }
    void shareLocations() { locations.shareAll(); }
    void selectMarker(MapDestination destination) { selectedMarker = destination.key(); }
    void focusDestination(MapDestination destination) {
        follow(false);
        navigation.focus(destination.x(), destination.z()); centered = true; selectMarker(destination);
    }
    boolean openVillage(MapDestination destination, double x, double y, int button) { return relations.click(this, destination, x, y, button); }
    private void zoomAt(double x, double y, double target) {
        finishOpening();
        navigation.zoomAt(following ? width / 2.0 : x, following ? height / 2.0 : y, target);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (transition.closing()) return true;
        finishOpening();
        if (locations.scroll(x, y, vertical) || relations.scroll(x, y, vertical)) return true;
        zoomAt(x, y, view().scale() * Math.pow(1.15, vertical)); return true;
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (ExploredMapClient.OPEN.matchesMouse(button)) { onClose(); return true; }
        if (transition.closing()) return true;
        finishOpening();
        if (locations.clickOpen(x, y, button)) return true;
        if (relations.clickOpen(x, y, button)) return true;
        if (super.mouseClicked(x, y, button)) return true;
        if (locations.clickMarker(x, y, button)) return true;
        if (relations.clickCourier(view(), layers, x, y, button)) return true;
        if (locations.place(x, y, button)) return true;
        if (button == 0) { navigation.beginDrag(); return true; } return false;
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0) navigation.endDrag(); return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && navigation.drag(dx, dy)) { follow(false); return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public void mouseMoved(double x, double y) {
        locations.hover(x, y);
        super.mouseMoved(x, y);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (transition.closing()) {
            if (ExploredMapClient.OPEN.matches(key, scan) || key == GLFW.GLFW_KEY_ESCAPE) onClose();
            return true;
        }
        if (locations.key(key)) { finishOpening(); return true; }
        if (relations.key(key)) { finishOpening(); return true; }
        if (ExploredMapClient.OPEN.matches(key, scan) || key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        finishOpening();
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_F) {
            minecraft.setScreen(new MapSearchScreen(this)); return true;
        }
        if (key == GLFW.GLFW_KEY_HOME) { center(); return true; }
        if (navigation.press(key)) { follow(false); return true; } return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean keyReleased(int key, int scan, int modifiers) {
        if (navigation.release(key)) return true; return super.keyReleased(key, scan, modifiers);
    }
    @Override public void removed() { navigation.clear(); transition.suspend(); relations.closePopup(); }
}
