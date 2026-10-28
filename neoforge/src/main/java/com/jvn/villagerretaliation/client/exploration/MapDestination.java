package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.exploration.MapLocation;
import com.jvn.villagerretaliation.exploration.MapSnapshot;
import com.jvn.villagerretaliation.quest.tracking.QuestJournalData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

record MapDestination(Kind kind, String id, ResourceLocation dimension, double x, double z,
                      String name, String label, MapLocation.Icon icon, boolean showName, int color, int radius) {
    enum Kind { VILLAGE, LOCATION, QUEST }

    String key() { return kind.name() + ":" + dimension + ":" + id; }
    double distance(double x, double z) { return Math.hypot(this.x - x, this.z - z); }

    static MapDestination location(MapLocation location) {
        return new MapDestination(Kind.LOCATION, location.id().toString(), location.dimension(), location.x(), location.z(),
                location.name(), location.label(), location.icon(), location.alwaysShowName(), 0xffffffff, 0);
    }

    static MapDestination village(MapSnapshot.Marker marker, ResourceLocation dimension) {
        String name = marker.name().isBlank() ? Component.translatable("map.villagerretaliation.village").getString() : marker.name();
        return new MapDestination(Kind.VILLAGE, marker.id(), dimension, marker.x(), marker.z(), name, name, null, true, 0xffffe8c0, 0);
    }

    static MapDestination quest(QuestJournalData.Entry entry) {
        var waypoint = entry.journal().waypoint();
        return new MapDestination(Kind.QUEST, entry.questId(), ResourceLocation.parse(waypoint.dimension()), waypoint.x(), waypoint.z(),
                entry.title(), entry.title(), null, true, com.jvn.villagerretaliation.client.quest.VillagerQuestUi.ACCENT_COLOR, waypoint.radius());
    }
}
