package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.client.config.VillagerRetaliationClientPreferences;
import com.jvn.villagerretaliation.client.quest.VillagerQuestTrackerOverlay;
import com.jvn.villagerretaliation.exploration.MapView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class MapDestinationTracker {
    private static MapDestination target;
    record Edge(double x, double y, double angle) { }

    static MapDestination target() { return target; }
    static String key() { return target == null ? "" : target.key(); }
    static boolean tracking(MapDestination destination) { return target != null && target.key().equals(destination.key()); }
    static void toggle(MapDestination destination) { target = tracking(destination) ? null : destination; }
    static void clear() { target = null; }
    static boolean favorite(MapDestination destination) {
        return VillagerRetaliationClientPreferences.mapFavorite(favoriteKey(destination));
    }
    static void toggleFavorite(MapDestination destination) {
        VillagerRetaliationClientPreferences.toggleMapFavorite(favoriteKey(destination));
    }
    private static String favoriteKey(MapDestination destination) {
        return ExploredMapClient.worldKey() + "/" + destination.key();
    }
    static void refresh() {
        if (target == null) return;
        if (target.kind() == MapDestination.Kind.LOCATION) target = MapLocationsClient.all().stream()
                .filter(location -> location.id().toString().equals(target.id())).findFirst().map(MapDestination::location).orElse(null);
        else if (target.kind() == MapDestination.Kind.QUEST) target = VillagerQuestTrackerOverlay.journalEntries().stream()
                .filter(entry -> entry.questId().equals(target.id()) && entry.state().equals("active") && !entry.journal().hidden()
                        && entry.journal().waypoint().present() && net.minecraft.resources.ResourceLocation.tryParse(entry.journal().waypoint().dimension()) != null)
                .findFirst().map(MapDestination::quest).orElse(null);
    }
    static Component distance(MapDestination destination) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !destination.dimension().equals(mc.level.dimension().location()))
            return MapLayers.text("other_dimension", destination.dimension().toString());
        return MapLayers.text("distance", Math.round(destination.distance(mc.player.getX(), mc.player.getZ())));
    }
    static void render(GuiGraphics graphics, MapView view, Font font, boolean minimap) {
        var mc = Minecraft.getInstance();
        if (target == null || mc.level == null || mc.player == null) return;
        boolean sameDimension = target.dimension().equals(mc.level.dimension().location());
        if (sameDimension) {
            double x = view.screenX(target.x()), y = view.screenY(target.z());
            int top = minimap ? 8 : 66, bottom = view.height() - (minimap ? 22 : 70);
            var edge = edge(x, y, view.width(), top, bottom, 10);
            if (edge != null) {
                graphics.pose().pushPose(); graphics.pose().translate(edge.x(), edge.y(), 0);
                graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotation((float) edge.angle()));
                for (int row = -6; row <= 3; row++) {
                    int half = (row + 6) / 2;
                    graphics.fill(-half, row, half + 1, row + 1, 0xffffe8a3);
                }
                graphics.pose().popPose();
            } else graphics.renderOutline((int) x - 11, (int) y - 11, 23, 23, 0xffffe8a3);
        }
        Component label = minimap ? (sameDimension ? distance(target) : MapLayers.text("other_dimension_short"))
                : MapLayers.text("tracking", target.name(), distance(target));
        String text = font.plainSubstrByWidth(label.getString(), view.width() - (minimap ? 8 : 190));
        int x = (view.width() - font.width(text)) / 2, y = view.height() - (minimap ? 13 : 54);
        graphics.fill(x - 3, y - 2, x + font.width(text) + 3, y + font.lineHeight + 2, 0xbb161a20);
        graphics.drawString(font, text, x, y, 0xffffe8a3);
    }
    static Edge edge(double x, double y, int width, int top, int bottom, int inset) {
        if (x >= inset && x <= width - inset && y >= top && y <= bottom) return null;
        double cx = width / 2.0, cy = (top + bottom) / 2.0, dx = x - cx, dy = y - cy;
        double scale = Math.min(dx == 0 ? Double.POSITIVE_INFINITY : (width / 2.0 - inset) / Math.abs(dx),
                dy == 0 ? Double.POSITIVE_INFINITY : (bottom - top) / 2.0 / Math.abs(dy));
        return new Edge(cx + dx * scale, cy + dy * scale, Math.atan2(dx, -dy));
    }
}
