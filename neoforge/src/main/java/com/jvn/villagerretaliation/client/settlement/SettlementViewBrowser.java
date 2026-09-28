package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class SettlementViewBrowser {
    Page page = Page.RESIDENTS;
    String query = "";
    String filter = "All";
    int scroll;
    java.util.Set<java.util.UUID> attention = java.util.Set.of();

    List<String> filters(List<SettlementManagementPayload.Entry> entries) {
        var values = new ArrayList<String>();
        values.add("All");
        switch (this.page) {
            case RESIDENTS -> values.addAll(List.of("Assigned", "Unassigned", "Work enabled", "Work paused",
                    "Here", "Away", "Unloaded", "Needs attention"));
            case AREAS -> values.addAll(List.of("Enabled", "Paused"));
            default -> { }
        }
        if (this.page == Page.RESIDENTS) entries.stream().filter(SettlementManagementPayload.Resident.class::isInstance)
                .map(SettlementManagementPayload.Resident.class::cast).flatMap(r -> r.roles().stream())
                .map(role -> role.name().toLowerCase(Locale.ROOT)).distinct().sorted().forEach(values::add);
        entries.stream().map(SettlementViewBrowser::kind).filter(value -> !value.isBlank()).distinct().sorted().forEach(values::add);
        return values;
    }

    List<SettlementManagementPayload.Entry> rows(List<SettlementManagementPayload.Entry> entries) {
        String needle = this.query.strip().toLowerCase(Locale.ROOT);
        return entries.stream().filter(entry -> (entry.name() + " " + kind(entry) + " " + detail(entry))
                .toLowerCase(Locale.ROOT).contains(needle)).filter(entry -> matches(entry, this.filter)).toList();
    }

    private boolean matches(SettlementManagementPayload.Entry entry, String filter) {
        if (filter.equals("All") || kind(entry).equals(filter)) return true;
        if (entry instanceof SettlementManagementPayload.Resident r) return switch (filter) {
            case "Assigned" -> r.area() != null;
            case "Unassigned" -> r.area() == null;
            case "Work enabled" -> r.area() != null && !r.status().equals("paused");
            case "Work paused" -> r.status().equals("paused");
            case "Here", "Away", "Unloaded" -> r.presence().name().equalsIgnoreCase(filter);
            case "Needs attention" -> this.attention.contains(r.id());
            default -> r.roles().stream().anyMatch(role -> role.name().equalsIgnoreCase(filter));
        };
        if (entry instanceof SettlementManagementPayload.Area area) return filter.equals(area.enabled() ? "Enabled" : "Paused");
        return false;
    }

    static String kind(SettlementManagementPayload.Entry entry) {
        return switch (entry) {
            case SettlementManagementPayload.Resident r -> r.profession().getPath();
            case SettlementManagementPayload.Area a -> a.kind().name().toLowerCase(Locale.ROOT);
            case SettlementManagementPayload.Project p -> p.status().name().toLowerCase(Locale.ROOT);
            case SettlementManagementPayload.Issue i -> i.kind().name().toLowerCase(Locale.ROOT);
            default -> "";
        };
    }

    static String detail(SettlementManagementPayload.Entry entry) {
        return switch (entry) {
            case SettlementManagementPayload.Resident r -> r.status() + " " + r.presence().name() + " " + r.diagnostic();
            case SettlementManagementPayload.Project p -> p.blocker();
            case SettlementManagementPayload.Issue i -> i.cause() + " " + i.detail();
            default -> "";
        };
    }
}
