package com.jvn.villagerretaliation.client.settlement;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SettlementSelectionSet<T> {
    public enum Operation {
        REPLACE, ADD, TOGGLE, REMOVE;

        public static Operation modifiers(boolean shift, boolean control) {
            return control ? shift ? REMOVE : TOGGLE : shift ? ADD : REPLACE;
        }
    }

    private final int limit;
    private final LinkedHashSet<T> values = new LinkedHashSet<>();

    public SettlementSelectionSet(int limit) {
        if (limit < 1) throw new IllegalArgumentException("Invalid selection limit");
        this.limit = limit;
    }

    public boolean apply(Collection<T> hits, Operation operation) {
        if (operation == Operation.REPLACE) this.values.clear();
        boolean fits = true;
        for (T hit : new LinkedHashSet<>(hits)) {
            if (operation == Operation.REMOVE || operation == Operation.TOGGLE && this.values.contains(hit)) this.values.remove(hit);
            else if (this.values.size() < this.limit || this.values.contains(hit)) this.values.add(hit);
            else fits = false;
        }
        return fits;
    }

    public void retain(Set<T> valid) { this.values.retainAll(valid); }
    public boolean contains(T value) { return this.values.contains(value); }
    public int size() { return this.values.size(); }
    public List<T> values() { return List.copyOf(this.values); }
}
