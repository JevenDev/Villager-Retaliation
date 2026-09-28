package com.jvn.villagerretaliation.client.settlement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class SettlementViewSelection {
    public static final int LIMIT = 128;
    private final LinkedHashSet<UUID> residents = new LinkedHashSet<>();
    private final List<List<UUID>> groups = new ArrayList<>();
    private int lastGroup = -1;
    private long lastRecall;

    public SettlementViewSelection() {
        for (int i = 0; i < 9; i++) this.groups.add(List.of());
    }

    public boolean select(Collection<UUID> ids, boolean add) {
        if (!add) this.residents.clear();
        boolean fits = true;
        for (UUID id : ids) {
            if (this.residents.size() < LIMIT || this.residents.contains(id)) this.residents.add(id);
            else fits = false;
        }
        return fits;
    }

    public boolean toggle(UUID id) {
        if (this.residents.remove(id)) return true;
        return select(List.of(id), true);
    }

    public void store(int group) { this.groups.set(group, List.copyOf(this.residents)); }

    public boolean recall(int group, long now) {
        select(this.groups.get(group), false);
        boolean focus = this.lastGroup == group && now - this.lastRecall < 400;
        this.lastGroup = group;
        this.lastRecall = now;
        return focus;
    }

    public void prune(Set<UUID> roster) {
        this.residents.retainAll(roster);
        this.groups.replaceAll(group -> group.stream().filter(roster::contains).toList());
    }

    public void restore(List<List<UUID>> saved) {
        if (saved.size() != 9 || saved.stream().anyMatch(group -> group.size() > LIMIT)) {
            throw new IllegalArgumentException("Invalid control groups");
        }
        for (int i = 0; i < 9; i++) this.groups.set(i, List.copyOf(new LinkedHashSet<>(saved.get(i))));
    }

    public List<UUID> residents() { return List.copyOf(this.residents); }
    public List<List<UUID>> groups() { return List.copyOf(this.groups); }
    public UUID single() { return this.residents.size() == 1 ? this.residents.getFirst() : null; }
}
