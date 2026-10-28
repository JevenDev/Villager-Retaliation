package com.jvn.villagerretaliation.client.exploration;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

final class MapSearchScreen extends Screen {
    private final ExploredMapScreen parent;
    private String query = "";
    private ResourceLocation dimension;
    private MapDestination.Kind kind;
    private boolean favoritesOnly, nearest;
    private EditBox search;
    private Rows rows;
    private int left, top, panelWidth, panelHeight, ticks;
    private Component tooltip;

    MapSearchScreen(ExploredMapScreen parent) {
        super(MapLayers.text("search")); this.parent = parent;
        var level = net.minecraft.client.Minecraft.getInstance().level;
        dimension = level == null ? null : level.dimension().location();
    }
    @Override protected void init() {
        panelWidth = Math.min(420, width - 24); panelHeight = Math.min(330, height - 16);
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
        search = addRenderableWidget(new EditBox(font, left + 8, top + 24, panelWidth - 16, 20, MapLayers.text("search")));
        search.setMaxLength(128); search.setValue(query); search.setHint(MapLayers.text("search_hint"));
        search.setResponder(value -> { query = value; populate(false); });
        int half = (panelWidth - 20) / 2;
        var dimensionButton = addRenderableWidget(Button.builder(dimensionLabel(), b -> {
            var dimensions = new ArrayList<ResourceLocation>();
            dimensions.add(null);
            parent.destinations().stream().map(MapDestination::dimension).distinct().sorted(java.util.Comparator.comparing(ResourceLocation::toString))
                    .forEach(dimensions::add);
            dimension = dimensions.get((dimensions.indexOf(dimension) + 1) % dimensions.size());
            b.setMessage(dimensionLabel()); b.setTooltip(Tooltip.create(dimensionLabel())); populate(false);
        }).bounds(left + 8, top + 48, half, 20).build());
        dimensionButton.setTooltip(Tooltip.create(dimensionLabel()));
        addRenderableWidget(Button.builder(kindLabel(), b -> {
            kind = kind == null ? MapDestination.Kind.VILLAGE : kind == MapDestination.Kind.QUEST ? null : MapDestination.Kind.values()[kind.ordinal() + 1];
            b.setMessage(kindLabel()); populate(false);
        }).bounds(left + half + 12, top + 48, half, 20).build());
        addRenderableWidget(Button.builder(MapLayers.text(nearest ? "sort_distance" : "sort_name"), b -> {
            nearest = !nearest; b.setMessage(MapLayers.text(nearest ? "sort_distance" : "sort_name")); populate(false);
        }).bounds(left + 8, top + 72, half, 20).build());
        addRenderableWidget(Button.builder(MapLayers.text(favoritesOnly ? "favorites_only" : "favorites_all"), b -> {
            favoritesOnly = !favoritesOnly; b.setMessage(MapLayers.text(favoritesOnly ? "favorites_only" : "favorites_all")); populate(false);
        }).bounds(left + half + 12, top + 72, half, 20).build());
        rows = addRenderableWidget(new Rows(left + 8, top + 100, panelWidth - 16, panelHeight - 152));
        addRenderableWidget(Button.builder(MapLocationsClient.text("share_all"), b -> {
            minecraft.setScreen(parent); parent.shareLocations();
        }).bounds(left + 8, top + panelHeight - 44, half, 20).build()).active = !MapLocationsClient.all().isEmpty();
        addRenderableWidget(Button.builder(MapLayers.text("stop_tracking"), b -> { MapDestinationTracker.clear(); b.active = false; })
                .bounds(left + half + 12, top + panelHeight - 44, half, 20).build()).active = MapDestinationTracker.target() != null;
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(left + 8, top + panelHeight - 20, panelWidth - 16, 20).build());
        populate(false); setInitialFocus(search);
    }
    private Component dimensionLabel() { return dimension == null ? MapLayers.text("all_dimensions") : Component.literal(dimension.toString()); }
    private Component kindLabel() { return kind == null ? MapLayers.text("all_types") : MapLayers.text("kind." + kind.name().toLowerCase(java.util.Locale.ROOT)); }
    private void populate(boolean preserve) {
        if (rows == null || minecraft.level == null || minecraft.player == null) return;
        String selected = preserve && rows.getSelected() != null ? rows.getSelected().destination.key() : "";
        double scroll = preserve ? rows.getScrollAmount() : 0;
        var catalog = parent.destinations();
        rows.clearRows();
        for (var destination : MapSearchModel.filter(catalog, query, dimension, kind, favoritesOnly, MapDestinationTracker::favorite,
                nearest, minecraft.level.dimension().location(), minecraft.player.getX(), minecraft.player.getZ())) rows.add(destination, selected);
        rows.setClampedScrollAmount(scroll);
    }
    private void choose(MapDestination destination) {
        minecraft.setScreen(parent);
        if (MapLocationMenus.sameDimension(destination)) parent.focusDestination(destination);
        parent.showDestination(destination);
    }
    @Override public void tick() {
        ExploredMapClient.relations().tick(new MapLayers(false), true);
        if (++ticks % 10 == 0) populate(true);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float partial) { }
    @Override public void render(GuiGraphics graphics, int x, int y, float partial) {
        parent.render(graphics, -1, -1, partial);
        graphics.pose().pushPose(); graphics.pose().translate(0, 0, 200);
        graphics.fill(0, 0, width, height, 0x80000000);
        graphics.fill(left - 3, top - 3, left + panelWidth + 3, top + panelHeight + 3, 0xff100010);
        TooltipRenderUtil.renderTooltipBackground(graphics, left, top, panelWidth, panelHeight, 0);
        graphics.drawCenteredString(font, title, width / 2, top + 8, 0xffffff);
        tooltip = null;
        super.render(graphics, x, y, partial);
        if (rows.children().isEmpty()) graphics.drawCenteredString(font, MapLayers.text("empty_search"), width / 2, top + 108, 0xffbfbfbf);
        if (tooltip != null) graphics.renderTooltip(font, tooltip, x, y);
        graphics.pose().popPose();
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            var row = rows.getSelected();
            if (row == null && !rows.children().isEmpty()) row = rows.children().getFirst();
            if (row != null) choose(row.destination);
            return true;
        }
        if ((key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP) && search.isFocused()) {
            search.setFocused(false); setFocused(rows);
            if (rows.getSelected() == null && !rows.children().isEmpty()) rows.setSelected(rows.children().getFirst());
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    private final class Rows extends ObjectSelectionList<Row> {
        Rows(int x, int y, int width, int height) { super(MapSearchScreen.this.minecraft, width, height, y, 30); setX(x); centerListVertically = false; }
        void clearRows() { clearEntries(); }
        void add(MapDestination destination, String selected) {
            var row = new Row(destination); addEntry(row);
            if (destination.key().equals(selected)) setSelected(row);
        }
        @Override public int getRowWidth() { return getWidth() - 12; }
        @Override protected int getScrollbarPosition() { return getRight() - 6; }
        @Override protected void renderListBackground(GuiGraphics graphics) { }
        @Override protected void renderListSeparators(GuiGraphics graphics) { }
    }
    private final class Row extends ObjectSelectionList.Entry<Row> {
        private final MapDestination destination;
        Row(MapDestination destination) { this.destination = destination; }
        @Override public Component getNarration() { return Component.literal(destination.name()).append(". ").append(MapDestinationTracker.distance(destination)); }
        @Override public boolean mouseClicked(double x, double y, int button) { if (button != 0) return false; choose(destination); return true; }
        @Override public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                                     int mouseX, int mouseY, boolean hovered, float partial) {
            String name = (MapDestinationTracker.favorite(destination) ? "★ " : "") + destination.name();
            graphics.drawString(font, font.plainSubstrByWidth(name, width - 6), x + 3, y + 2, 0xffffffff);
            var detail = MapLayers.text("kind." + destination.kind().name().toLowerCase(java.util.Locale.ROOT)).copy()
                    .append(" / ").append(MapDestinationTracker.distance(destination));
            graphics.drawString(font, font.plainSubstrByWidth(detail.getString(), width - 6), x + 3, y + 14, 0xffbfbfbf);
            if (hovered) tooltip = getNarration();
        }
    }
}
