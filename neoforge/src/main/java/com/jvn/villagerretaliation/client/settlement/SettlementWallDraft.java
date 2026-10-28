package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.settlement.SettlementWall;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

public final class SettlementWallDraft {
    private record State(SettlementWall wall, int spacing) {}
    private final List<BlockPos> nodes = new ArrayList<>();
    private final ArrayDeque<State> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private SettlementWall.Type type;
    private int spacing;
    private boolean automatic;
    private boolean closed;
    private boolean extending = true;
    private int selected = -1;
    private int version;
    private State gesture;

    public SettlementWallDraft(SettlementWall.Type type, boolean automatic, int spacing) {
        this.type = type;
        this.automatic = automatic;
        this.spacing = spacing;
    }
    public SettlementWall wall() { return new SettlementWall(this.type, this.nodes, this.closed, this.automatic ? this.spacing : 0); }
    public int version() { return this.version; }
    public int selected() { return this.selected; }
    public int spacing() { return this.spacing; }
    public boolean automatic() { return this.automatic; }
    public boolean extending() { return this.extending && !this.closed; }
    public boolean dragging() { return this.gesture != null; }
    public boolean canUndo() { return !this.undo.isEmpty() && !dragging(); }
    public boolean canRedo() { return !this.redo.isEmpty() && !dragging(); }
    private State state() { return new State(wall(), this.spacing); }
    private void remember(State state) {
        this.undo.addLast(state);
        if (this.undo.size() > 128) this.undo.removeFirst();
        this.redo.clear();
    }
    private void restore(State state) {
        this.nodes.clear();
        this.nodes.addAll(state.wall().nodes());
        this.type = state.wall().type();
        this.closed = state.wall().closed();
        this.automatic = state.wall().towerSpacing() != 0;
        this.spacing = state.spacing();
        this.selected = Math.min(this.selected, this.nodes.size() - 1);
        this.version++;
    }
    public void type(SettlementWall.Type type) {
        if (dragging() || this.type == type) return;
        remember(state()); this.type = type; this.version++;
    }
    public void toggleAutomatic() {
        if (dragging()) return;
        remember(state()); this.automatic = !this.automatic; this.version++;
    }
    public void spacing(int spacing) {
        if (dragging() || spacing < 6 || spacing > 64 || this.spacing == spacing) return;
        remember(state()); this.spacing = spacing; this.version++;
    }
    public boolean place(BlockPos position) {
        if (!extending() || dragging() || this.nodes.size() >= SettlementWall.MAX_NODES) return false;
        remember(state()); this.nodes.add(position.immutable()); this.selected = this.nodes.size() - 1; this.version++;
        return true;
    }
    public SettlementWall preview(BlockPos hover) {
        if (hover == null || !extending() || dragging() || this.nodes.size() >= SettlementWall.MAX_NODES) return wall();
        var shown = new ArrayList<>(this.nodes);
        shown.add(hover);
        return new SettlementWall(this.type, shown, false, this.automatic ? this.spacing : 0);
    }
    public void begin(int index) {
        if (dragging() || index < 0 || index >= this.nodes.size()) return;
        this.selected = index;
        this.gesture = state();
    }
    public void drag(BlockPos position) {
        if (!dragging() || this.nodes.get(this.selected).equals(position)) return;
        this.nodes.set(this.selected, position.immutable());
        this.version++;
    }
    public void finish() {
        if (!dragging()) return;
        if (!this.gesture.equals(state())) remember(this.gesture);
        this.gesture = null;
    }
    public void cancelDrag() {
        if (!dragging()) return;
        restore(this.gesture); this.gesture = null;
    }
    public void stop() { finish(); this.extending = false; }
    public void extend() { if (!this.closed) this.extending = true; }
    public void loop() {
        if (dragging() || !this.closed && this.nodes.size() < 3) return;
        remember(state()); this.closed = !this.closed; this.extending = !this.closed; this.version++;
    }
    public void remove() {
        if (dragging() || this.selected < 0) return;
        remember(state()); this.nodes.remove(this.selected);
        if (this.nodes.size() < 3) this.closed = false;
        this.selected = Math.min(this.selected, this.nodes.size() - 1); this.version++;
    }
    public void undo() { if (canUndo()) { this.redo.addLast(state()); restore(this.undo.removeLast()); } }
    public void redo() { if (canRedo()) { this.undo.addLast(state()); restore(this.redo.removeLast()); } }
}
