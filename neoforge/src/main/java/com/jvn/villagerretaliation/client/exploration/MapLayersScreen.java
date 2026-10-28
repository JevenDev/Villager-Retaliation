package com.jvn.villagerretaliation.client.exploration;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.network.chat.Component;

final class MapLayersScreen extends Screen {
    private final ExploredMapScreen parent;
    private boolean minimap;
    private int left, top, panelWidth;

    MapLayersScreen(ExploredMapScreen parent) { super(MapLayers.text("layers")); this.parent = parent; }
    @Override protected void init() {
        panelWidth = Math.min(240, width - 24); left = (width - panelWidth) / 2; top = (height - 208) / 2;
        addRenderableWidget(Button.builder(MapLayers.text("full_map"), b -> { minimap = false; rebuildWidgets(); })
                .bounds(left + 8, top + 24, (panelWidth - 20) / 2, 20).build()).active = minimap;
        addRenderableWidget(Button.builder(MapLayers.text("minimap"), b -> { minimap = true; rebuildWidgets(); })
                .bounds(left + panelWidth / 2 + 2, top + 24, (panelWidth - 20) / 2, 20).build()).active = !minimap;
        var layers = new MapLayers(minimap);
        int y = top + 52;
        for (var layer : MapLayers.Layer.values()) {
            addRenderableWidget(Button.builder(label(layers, layer), b -> {
                layers.toggle(layer); b.setMessage(label(layers, layer));
            }).bounds(left + 8, y, panelWidth - 16, 20).build());
            y += 24;
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + 8, top + 180, panelWidth - 16, 20).build());
    }
    private Component label(MapLayers layers, MapLayers.Layer layer) {
        return MapLayers.text(layers.visible(layer) ? "layer_on" : "layer_off", layer.title());
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float partial) { }
    @Override public void render(GuiGraphics graphics, int x, int y, float partial) {
        parent.render(graphics, -1, -1, partial);
        graphics.pose().pushPose(); graphics.pose().translate(0, 0, 200);
        graphics.fill(0, 0, width, height, 0x80000000);
        graphics.fill(left - 3, top - 3, left + panelWidth + 3, top + 211, 0xff100010);
        TooltipRenderUtil.renderTooltipBackground(graphics, left, top, panelWidth, 208, 0);
        graphics.drawCenteredString(font, title, width / 2, top + 8, 0xffffff);
        super.render(graphics, x, y, partial);
        graphics.pose().popPose();
    }
}
