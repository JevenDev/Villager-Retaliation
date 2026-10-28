package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.client.quest.VillagerQuestTrackerOverlay;
import com.jvn.villagerretaliation.exploration.MapSnapshot;
import com.jvn.villagerretaliation.exploration.MapView;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

final class MapMarkerOverlay {
    static List<MapDestination> destinations(List<MapSnapshot.Marker> terrain, List<MapDestination> relations) {
        var level = Minecraft.getInstance().level;
        if (level == null) return List.of();
        var destinations = new LinkedHashMap<String, MapDestination>();
        for (var marker : terrain) {
            var destination = MapDestination.village(marker, level.dimension().location());
            destinations.put(destination.key(), destination);
        }
        for (var destination : relations) destinations.put(destination.key(), destination);
        for (var location : MapLocationsClient.all()) {
            var destination = MapDestination.location(location); destinations.put(destination.key(), destination);
        }
        for (var entry : VillagerQuestTrackerOverlay.journalEntries()) {
            if (!entry.state().equals("active") || entry.journal().hidden() || !entry.journal().waypoint().present()
                    || net.minecraft.resources.ResourceLocation.tryParse(entry.journal().waypoint().dimension()) == null) continue;
            var destination = MapDestination.quest(entry); destinations.put(destination.key(), destination);
        }
        return List.copyOf(destinations.values());
    }

    static List<MapDestination> visible(List<MapDestination> destinations, MapLayers layers) {
        var level = Minecraft.getInstance().level;
        if (level == null) return List.of();
        return destinations.stream().filter(destination -> destination.dimension().equals(level.dimension().location()))
                .filter(destination -> switch (destination.kind()) {
                    case VILLAGE -> true;
                    case LOCATION -> layers.visible(MapLayers.Layer.LOCATIONS);
                    case QUEST -> layers.visible(MapLayers.Layer.QUESTS);
                }).toList();
    }

    static void render(GuiGraphics graphics, MapView view, Font font, List<MapMarkerLayout.Group> groups,
                       MapLayers layers, String selected, double mouseX, double mouseY, boolean minimap) {
        if (!minimap) graphics.enableScissor(0, 64, view.width(), Math.max(64, view.height() - 64));
        for (var group : groups) {
            int x = group.x(), y = group.y();
            if (group.members().size() > 1) {
                graphics.fill(x - 9, y - 8, x + 10, y + 9, 0xdd252a30);
                graphics.drawCenteredString(font, Integer.toString(group.members().size()), x, y - 4, 0xfff0d8a0);
                continue;
            }
            var destination = group.members().getFirst();
            switch (destination.kind()) {
                case VILLAGE -> MapVillageIcon.render(graphics, x, y);
                case LOCATION -> graphics.renderItem(MapLocationsClient.icon(destination.icon()), x - 8, y - 8);
                case QUEST -> {
                    for (int i = -4; i <= 4; i++) {
                        graphics.fill(x + i, y + i, x + i + 2, y + i + 2, destination.color());
                        graphics.fill(x + i, y - i, x + i + 2, y - i + 2, destination.color());
                    }
                }
            }
            if (destination.key().equals(selected)) graphics.renderOutline(x - 10, y - 10, 21, 21, 0xffffe8a3);
        }
        var reserved = new java.util.ArrayList<MapMarkerLayout.Box>();
        if (!minimap) {
            reserved.add(new MapMarkerLayout.Box(0, 0, view.width(), 64));
            reserved.add(new MapMarkerLayout.Box(view.width() - 32, 64, view.width() - 12, 94));
            reserved.add(new MapMarkerLayout.Box(0, view.height() - 64, view.width(), view.height()));
        }
        var player = Minecraft.getInstance().player;
        if (player != null) {
            int x = (int) view.screenX(player.getX()), y = (int) view.screenY(player.getZ());
            reserved.add(new MapMarkerLayout.Box(x - 9, y - 9, x + 10, y + 10));
        }
        for (var label : MapMarkerLayout.labels(groups, view, selected, mouseX, mouseY,
                layers.visible(MapLayers.Layer.VILLAGE_NAMES), font::width, font.lineHeight, reserved)) {
            graphics.fill(label.x() - 2, label.y() - 1, label.x() + label.width() + 2, label.y() + font.lineHeight + 1, 0x99000000);
            graphics.drawString(font, label.destination().label(), label.x(), label.y(), label.destination().color());
        }
        if (!minimap) graphics.disableScissor();
    }

    static MapMarkerLayout.Group at(List<MapMarkerLayout.Group> groups, double x, double y) {
        for (int i = groups.size() - 1; i >= 0; i--) {
            var group = groups.get(i);
            if (Math.abs(group.x() - x) <= 10 && Math.abs(group.y() - y) <= 10) return group;
        }
        return null;
    }
}
