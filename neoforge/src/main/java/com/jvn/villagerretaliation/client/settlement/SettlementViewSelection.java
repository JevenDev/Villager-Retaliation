package com.jvn.villagerretaliation.client.settlement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import com.jvn.villagerretaliation.client.settlement.SettlementSelectionSet.Operation;

public final class SettlementViewSelection {
    public static final int LIMIT = 128;
    private final SettlementSelectionSet<UUID> residents = new SettlementSelectionSet<>(LIMIT);
    private final SettlementSelectionSet<BlockPos> beds = new SettlementSelectionSet<>(LIMIT);
    private final List<List<UUID>> groups = new ArrayList<>();
    private int lastGroup = -1;
    private long lastRecall;

    public SettlementViewSelection() {
        for (int i = 0; i < 9; i++) this.groups.add(List.of());
    }

    public boolean select(Collection<UUID> ids, boolean add) {
        return apply(ids, List.of(), add ? Operation.ADD : Operation.REPLACE);
    }

    public boolean apply(Collection<UUID> residents, Collection<BlockPos> beds, Operation operation) {
        boolean fits = this.residents.apply(residents, operation);
        return this.beds.apply(beds, operation) && fits;
    }

    public boolean toggle(UUID id) {
        return this.residents.apply(List.of(id), Operation.TOGGLE);
    }

    public void retainBeds(Set<BlockPos> beds) { this.beds.retain(beds); }
    public List<BlockPos> beds() { return this.beds.values(); }
    public boolean containsBed(BlockPos bed) { return this.beds.contains(bed); }
    public int totalSize() { return this.residents.size() + this.beds.size(); }

    public void store(int group) { this.groups.set(group, this.residents.values()); }

    public boolean recall(int group, long now) {
        select(this.groups.get(group), false);
        boolean focus = this.lastGroup == group && now - this.lastRecall < 400;
        this.lastGroup = group;
        this.lastRecall = now;
        return focus;
    }

    public void prune(Set<UUID> roster) {
        this.residents.retain(roster);
        pruneGroups(roster);
    }

    public void pruneGroups(Set<UUID> roster) {
        this.groups.replaceAll(group -> group.stream().filter(roster::contains).toList());
    }

    public void restore(List<List<UUID>> saved) {
        if (saved.size() != 9 || saved.stream().anyMatch(group -> group.size() > LIMIT)) {
            throw new IllegalArgumentException("Invalid control groups");
        }
        for (int i = 0; i < 9; i++) this.groups.set(i, List.copyOf(new LinkedHashSet<>(saved.get(i))));
    }

    public boolean contains(UUID resident) { return this.residents.contains(resident); }
    public int size() { return this.residents.size(); }

    public List<UUID> residents() { return this.residents.values(); }
    public List<List<UUID>> groups() { return List.copyOf(this.groups); }
    public UUID single() { return this.residents.size() == 1 ? this.residents.values().getFirst() : null; }
}
