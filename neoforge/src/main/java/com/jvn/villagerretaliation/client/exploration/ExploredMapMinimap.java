package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.exploration.*;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = "villagerretaliation", value = Dist.CLIENT)
public final class ExploredMapMinimap {
    private static final ExploredMapRenderer TILES = new ExploredMapRenderer();
    @SubscribeEvent public static void render(RenderGuiLayerEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (!VanillaGuiLayers.SAVING_INDICATOR.equals(event.getName()) || !VillagerRetaliationConfig.SHOW_EXPLORED_MAP_MINIMAP.get()
                || mc.level == null || mc.player == null || mc.options.hideGui || mc.screen != null) return;
        var repository = ExploredMapClient.repository(); if (repository == null) return;
        int width = width(), height = Math.max(1, width * 3 / 4);
        int left = mc.getWindow().getGuiScaledWidth() - width - 8, top = 8;
        var graphics = event.getGuiGraphics();
        double zoom = Math.clamp(VillagerRetaliationConfig.EXPLORED_MAP_MINIMAP_ZOOM.get(), .75, 4);
        var view = new MapView(width, height, mc.player.getX(), mc.player.getZ(), zoom);
        var lighting = MapLighting.at(mc.level.getTimeOfDay(event.getPartialTick().getGameTimeDeltaPartialTick(false)), mc.level.dimensionType().hasSkyLight());
        graphics.fill(left - 1, top - 1, left + width + 1, top + height + 1, 0xff85837e);
        graphics.enableScissor(left, top, left + width, top + height);
        graphics.pose().pushPose(); graphics.pose().translate(left, top, 0);
        TILES.render(graphics, view, lighting);
        var layers = new MapLayers(true);
        var relations = ExploredMapClient.relations();
        relations.renderTerrain(graphics, view, layers);
        relations.render(graphics, view, mc.font, layers, true);
        var destinations = MapMarkerOverlay.visible(MapMarkerOverlay.destinations(TILES.markers(), relations.destinations(layers)), layers);
        MapMarkerOverlay.render(graphics, view, mc.font, MapMarkerLayout.groups(destinations, view, ""), layers, "", -1, -1, true);
        MapPlayerIcon.render(graphics, width / 2f, height / 2f, mc.player.getViewYRot(event.getPartialTick().getGameTimeDeltaPartialTick(false)));
        graphics.pose().popPose(); graphics.disableScissor();
    }
    static int width() {
        var window = Minecraft.getInstance().getWindow();
        return Math.max(1, Math.min(Math.clamp(VillagerRetaliationConfig.EXPLORED_MAP_MINIMAP_SIZE.get(), 80, 320),
                Math.min(window.getGuiScaledWidth() - 16, (window.getGuiScaledHeight() - 16) * 4 / 3)));
    }
    static void reset() { TILES.close(); }
}
