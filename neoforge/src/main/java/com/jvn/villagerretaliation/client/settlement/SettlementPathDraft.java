package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.settlement.SettlementPath;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

public final class SettlementPathDraft {
    private record State(List<SettlementPath.Node> nodes, int width, SettlementPath.Style style, int selected) {}
    private final List<SettlementPath.Node> nodes = new ArrayList<>();
    private final ArrayDeque<State> undo = new ArrayDeque<>();
    private final ArrayDeque<State> redo = new ArrayDeque<>();
    private final long seed;
    private final boolean branch;
    private int width;
    private SettlementPath.Style style;
    private int selected = -1;
    private int version;
    private State gesture;
    private int part;
    private boolean creating;
    private boolean inserting;

    public SettlementPathDraft(int width, SettlementPath.Style style, long seed, boolean branch) {
        this.width = width;
        this.style = style;
        this.seed = seed;
        this.branch = branch;
    }
    public SettlementPath path() { return new SettlementPath(this.nodes, this.width, this.style, SettlementPath.Material.DIRT, this.seed, this.branch); }
    public int selected() { return this.selected; }
    public int version() { return this.version; }
    public boolean dragging() { return this.gesture != null; }
    public boolean inserting() { return this.inserting; }
    public boolean canUndo() { return !this.undo.isEmpty() && !dragging(); }
    public boolean canRedo() { return !this.redo.isEmpty() && !dragging(); }
    public void inserting(boolean value) { this.inserting = value; }
    public void select(int index) { this.selected = index >= 0 && index < this.nodes.size() ? index : -1; }

    private State state() { return new State(List.copyOf(this.nodes), this.width, this.style, this.selected); }
    private void restore(State state) {
        this.nodes.clear();
        this.nodes.addAll(state.nodes());
        this.width = state.width();
        this.style = state.style();
        this.selected = state.selected();
        this.version++;
    }
    private void remember(State state) {
        this.undo.addLast(state);
        if (this.undo.size() > 128) this.undo.removeFirst();
        this.redo.clear();
    }
    public void settings(int width, SettlementPath.Style style) {
        if (dragging() || width < 1 || width > 7 || this.width == width && this.style == style) return;
        remember(state());
        this.width = width;
        this.style = style;
        this.version++;
    }
    public boolean place(Vec3 position) {
        if (this.nodes.size() >= SettlementPath.MAX_NODES) return false;
        this.gesture = state();
        int index = this.inserting && this.selected >= 0 ? this.selected + 1 : this.nodes.size();
        this.nodes.add(index, new SettlementPath.Node(position));
        this.selected = index;
        this.part = 1;
        this.creating = true;
        this.inserting = false;
        this.version++;
        return true;
    }
    public void begin(int index, int part) {
        this.selected = index;
        this.gesture = state();
        this.part = part;
        this.creating = false;
    }
    public void drag(Vec3 point, boolean independent) {
        if (!dragging() || this.selected < 0) return;
        var old = this.nodes.get(this.selected);
        SettlementPath.Node node;
        if (this.part == 0 && !this.creating) {
            if (this.branch && this.selected == 0) return;
            node = old.at(point);
        } else {
            Vec3 delta = point.subtract(old.position()).multiply(1, 0, 1);
            if (Math.abs(delta.x) > 256 || Math.abs(delta.z) > 256) return;
            node = this.part < 0 ? new SettlementPath.Node(old.position(), delta, independent ? old.outgoing() : delta.scale(-1))
                    : new SettlementPath.Node(old.position(), independent ? old.incoming() : delta.scale(-1), delta);
        }
        if (!node.equals(old)) { this.nodes.set(this.selected, node); this.version++; }
    }
    public void finish() {
        if (this.gesture == null) return;
        if (!this.gesture.equals(state())) remember(this.gesture);
        this.gesture = null;
    }
    public void cancelDrag() {
        if (this.gesture == null) return;
        restore(this.gesture);
        this.gesture = null;
    }
    public void remove() {
        if (dragging() || this.selected < 0 || this.branch && this.selected == 0) return;
        remember(state());
        this.nodes.remove(this.selected);
        this.selected = Math.min(this.selected, this.nodes.size() - 1);
        this.version++;
    }
    public void smooth() {
        if (dragging() || this.selected < 0) return;
        remember(state());
        var node = this.nodes.get(this.selected);
        if (node.incoming().lengthSqr() > 0 || node.outgoing().lengthSqr() > 0) this.nodes.set(this.selected, new SettlementPath.Node(node.position()));
        else {
            var before = this.selected > 0 ? this.nodes.get(this.selected - 1).position() : node.position();
            var after = this.selected + 1 < this.nodes.size() ? this.nodes.get(this.selected + 1).position() : node.position();
            var tangent = after.subtract(before).multiply(1, 0, 1).normalize().scale(2);
            if (tangent.lengthSqr() == 0) tangent = new Vec3(2, 0, 0);
            this.nodes.set(this.selected, new SettlementPath.Node(node.position(), tangent.scale(-1), tangent));
        }
        this.version++;
    }
    public void undo() { if (canUndo()) { this.redo.addLast(state()); restore(this.undo.removeLast()); } }
    public void redo() { if (canRedo()) { this.undo.addLast(state()); restore(this.redo.removeLast()); } }
}
