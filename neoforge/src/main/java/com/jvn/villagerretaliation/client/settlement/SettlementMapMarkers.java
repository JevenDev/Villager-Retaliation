package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.settlement.SettlementAreaType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

final class SettlementMapMarkers {
    private List<SettlementManagementPayload.Area> areas = List.of();
    private List<SettlementManagementPayload.Project> projects = List.of();
    private List<SettlementOverviewMap.Marker> candidates = List.of();
    private List<SettlementOverviewMap.Marker> visible = List.of();
    private UUID selected;
    private boolean ready;

    void update(Collection<SettlementManagementPayload.Area> areas, Collection<SettlementManagementPayload.Project> projects) {
        this.areas = List.copyOf(areas);
        this.projects = List.copyOf(projects);
        var markers = new ArrayList<SettlementOverviewMap.Marker>();
        for (var area : areas) {
            if (area.kind() == SettlementAreaType.CONSTRUCTION) continue;
            markers.add(new SettlementOverviewMap.Marker(area, area.min().offset((area.max().getX() - area.min().getX()) / 2,
                    0, (area.max().getZ() - area.min().getZ()) / 2), false, 0xFF63879C));
        }
        for (var project : projects) markers.add(new SettlementOverviewMap.Marker(project,
                project.min().offset((project.max().getX() - project.min().getX()) / 2,
                        0, (project.max().getZ() - project.min().getZ()) / 2), false, 0xFFE7B64E));
        this.candidates = List.copyOf(markers);
        this.ready = false;
    }

    List<SettlementOverviewMap.Marker> markers(UUID selected) {
        if (!this.ready || !Objects.equals(this.selected, selected)) {
            var markers = new ArrayList<SettlementOverviewMap.Marker>();
            for (var marker : this.candidates) {
                boolean highlighted = marker.entry().id().equals(selected);
                if (!highlighted && marker.entry() instanceof SettlementManagementPayload.Area area && area.kind() == SettlementAreaType.HOME) continue;
                markers.add(highlighted ? new SettlementOverviewMap.Marker(marker.entry(), marker.position(), false, 0xFF70CBFF) : marker);
            }
            this.visible = List.copyOf(markers);
            this.selected = selected;
            this.ready = true;
        }
        return this.visible;
    }

    List<SettlementManagementPayload.Area> areas() { return this.areas; }
    List<SettlementManagementPayload.Project> projects() { return this.projects; }
}
