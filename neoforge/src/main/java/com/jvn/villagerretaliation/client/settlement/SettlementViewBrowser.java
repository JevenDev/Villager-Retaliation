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
    SettlementViewPreferences.BrowserSort sort = SettlementViewPreferences.BrowserSort.DEFAULT;

    void remember(SettlementViewPreferences preferences) {
        preferences.browserPage = this.page;
        preferences.browsers.put(this.page, new SettlementViewPreferences.BrowserState(this.query, this.filter, this.sort));
    }

    void restore(SettlementViewPreferences preferences, Page page) {
        this.page = page;
        var state = preferences.browsers.getOrDefault(page, new SettlementViewPreferences.BrowserState("", "All", SettlementViewPreferences.BrowserSort.DEFAULT));
        this.query = state.query();
        this.filter = state.filter();
        this.sort = state.sort();
        this.scroll = 0;
    }

    String sortLabel() {
        return switch (this.sort) {
            case DEFAULT -> "Default order";
            case NAME -> "Name";
            case KIND -> "Type / status";
            case ATTENTION -> "Needs attention first";
        };
    }

    java.util.Set<java.util.UUID> attention = java.util.Set.of();

    void updateAttention(List<SettlementManagementPayload.Entry> issues) {
        this.attention = issues.stream().filter(SettlementManagementPayload.Issue.class::isInstance)
                .map(SettlementManagementPayload.Issue.class::cast).filter(SettlementManagementPayload.Issue::needsAttention)
                .map(SettlementManagementPayload.Issue::resident).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
    }

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
        var rows = entries.stream().filter(entry -> (entry.name() + " " + kind(entry) + " " + detail(entry))
                .toLowerCase(Locale.ROOT).contains(needle)).filter(entry -> matches(entry, this.filter));
        var names = java.util.Comparator.comparing(SettlementManagementPayload.Entry::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(SettlementManagementPayload.Entry::id);
        return switch (this.sort) {
            case DEFAULT -> rows.toList();
            case NAME -> rows.sorted(names).toList();
            case KIND -> rows.sorted(java.util.Comparator.comparing(SettlementViewBrowser::kind).thenComparing(names)).toList();
            case ATTENTION -> rows.sorted(java.util.Comparator.comparing((SettlementManagementPayload.Entry entry) -> !needsAttention(entry)).thenComparing(names)).toList();
        };
    }

    private boolean needsAttention(SettlementManagementPayload.Entry entry) {
        return switch (entry) {
            case SettlementManagementPayload.Resident resident -> this.attention.contains(resident.id());
            case SettlementManagementPayload.Project project -> !project.blocker().isBlank();
            case SettlementManagementPayload.Issue issue -> issue.needsAttention();
            default -> false;
        };
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

    static String kindFilter(com.jvn.villagerretaliation.settlement.SettlementAreaType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }

    static String kind(SettlementManagementPayload.Entry entry) {
        return switch (entry) {
            case SettlementManagementPayload.Resident r -> r.profession().getPath();
            case SettlementManagementPayload.Area a -> a.kind().name().toLowerCase(Locale.ROOT);
            case SettlementManagementPayload.Project p -> p.status().name().toLowerCase(Locale.ROOT);
            case SettlementManagementPayload.Issue i -> i.kind().name().toLowerCase(Locale.ROOT);
            case SettlementManagementPayload.Depot d -> d.purpose().name().toLowerCase(Locale.ROOT);
            case SettlementManagementPayload.Order o -> o.direction().name().toLowerCase(Locale.ROOT);
            default -> "";
        };
    }

    static String detail(SettlementManagementPayload.Entry entry) {
        return switch (entry) {
            case SettlementManagementPayload.Resident r -> r.status() + " " + r.presence().name() + " " + r.diagnostic();
            case SettlementManagementPayload.Project p -> p.blocker();
            case SettlementManagementPayload.Issue i -> i.cause() + " " + i.detail();
            case SettlementManagementPayload.Order o -> o.result() + " " + o.item();
            default -> "";
        };
    }
}
