package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.settlement.SettlementPath;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

public final class SettlementPathDraft {
    private record State(List<SettlementPath.Node> nodes, int width, SettlementPath.Style style, int selected, boolean smooth, boolean closed) {}
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
    private boolean smooth;
    private boolean closed;
    private boolean extending = true;

    public SettlementPathDraft(int width, SettlementPath.Style style, long seed, boolean branch) {
        this(width, style, seed, branch, false);
    }
    public SettlementPathDraft(int width, SettlementPath.Style style, long seed, boolean branch, boolean smooth) {
        this.width = width;
        this.style = style;
        this.smooth = smooth;
        this.seed = seed;
        this.branch = branch;
    }
    public SettlementPath path() { return new SettlementPath(this.nodes, this.width, this.style, SettlementPath.Material.DIRT, this.seed, this.branch, this.closed); }
    public int selected() { return this.selected; }
    public int version() { return this.version; }
    public boolean dragging() { return this.gesture != null; }
    public boolean inserting() { return this.inserting; }
    public boolean smoothMode() { return this.smooth; }
    public boolean extending() { return this.extending && !this.closed; }
    public boolean canClose() { return !dragging() && !this.closed && this.nodes.size() >= 3; }
    public boolean closingClick() { return extending() && !this.inserting && !this.creating && this.selected == 0 && this.part == 0 && this.nodes.size() >= 3; }
    public void stopExtending() { finish(); this.extending = false; this.inserting = false; }
    public void extend() { if (!this.closed) this.extending = true; }
    public void toggleLoop() {
        if (dragging() || !this.closed && !canClose()) return;
        remember(state());
        this.closed = !this.closed;
        this.extending = !this.closed;
        this.inserting = false;
        this.version++;
    }
    public SettlementPath preview(Vec3 point) {
        var preview = appended(point);
        return new SettlementPath(preview, this.width, this.style, SettlementPath.Material.DIRT, this.seed, this.branch, this.closed);
    }
    public SettlementPath loopPreview() {
        return new SettlementPath(this.nodes, this.width, this.style, SettlementPath.Material.DIRT, this.seed, this.branch, true);
    }
    public boolean canUndo() { return !this.undo.isEmpty() && !dragging(); }
    public boolean canRedo() { return !this.redo.isEmpty() && !dragging(); }
    public void inserting(boolean value) { this.inserting = value; }
    public void select(int index) { this.selected = index >= 0 && index < this.nodes.size() ? index : -1; }

    private State state() { return new State(List.copyOf(this.nodes), this.width, this.style, this.selected, this.smooth, this.closed); }
    private void restore(State state) {
        this.nodes.clear();
        this.nodes.addAll(state.nodes());
        this.width = state.width();
        this.style = state.style();
        this.selected = state.selected();
        this.smooth = state.smooth();
        this.closed = state.closed();
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
        if (this.nodes.size() >= SettlementPath.MAX_NODES || !extending() && !this.inserting) return false;
        this.gesture = state();
        int index = this.inserting && this.selected >= 0 ? this.selected + 1 : this.nodes.size();
        var updated = appended(position);
        this.nodes.clear();
        this.nodes.addAll(updated);
        this.selected = index;
        this.part = 1;
        this.creating = true;
        this.inserting = false;
        this.version++;
        return true;
    }
    private List<SettlementPath.Node> appended(Vec3 position) {
        var updated = new ArrayList<>(this.nodes);
        int index = this.inserting && this.selected >= 0 ? this.selected + 1 : updated.size();
        Vec3 tangent = Vec3.ZERO;
        if (this.smooth && index > 0) {
            var previous = updated.get(index - 1);
            var delta = position.subtract(previous.position()).multiply(1, 0, 1);
            tangent = delta.normalize().scale(Math.min(2, delta.length() / 3));
            if (updated.size() == 1 && previous.incoming().lengthSqr() == 0 && previous.outgoing().lengthSqr() == 0)
                updated.set(0, new SettlementPath.Node(previous.position(), tangent.scale(-1), tangent));
        }
        updated.add(index, new SettlementPath.Node(position, tangent.scale(-1), tangent));
        return updated;
    }
    public void begin(int index, int part) {
        this.selected = index;
        this.gesture = state();
        this.part = part;
        this.creating = false;
        if (this.smooth && part == 0) initializeCurve();
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
        if (!node.equals(old)) {
            this.nodes.set(this.selected, node);
            if (this.creating && node.outgoing().lengthSqr() > 0) this.smooth = true;
            this.version++;
        }
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
        if (this.nodes.size() < 3) this.closed = false;
        this.selected = Math.min(this.selected, this.nodes.size() - 1);
        this.version++;
    }
    public void smooth() {
        if (dragging()) return;
        remember(state());
        this.smooth = !this.smooth;
        this.version++;
        if (this.selected < 0) return;
        var node = this.nodes.get(this.selected);
        if (!this.smooth) this.nodes.set(this.selected, new SettlementPath.Node(node.position()));
        else initializeCurve();
    }
    private void initializeCurve() {
        var node = this.nodes.get(this.selected);
        if (node.incoming().lengthSqr() > 0 || node.outgoing().lengthSqr() > 0) return;
        var before = this.selected > 0 ? this.nodes.get(this.selected - 1).position()
                : this.closed ? this.nodes.getLast().position() : node.position();
        var after = this.selected + 1 < this.nodes.size() ? this.nodes.get(this.selected + 1).position()
                : this.closed ? this.nodes.getFirst().position() : node.position();
        var tangent = after.subtract(before).multiply(1, 0, 1).normalize().scale(2);
        if (tangent.lengthSqr() == 0) tangent = new Vec3(2, 0, 0);
        this.nodes.set(this.selected, new SettlementPath.Node(node.position(), tangent.scale(-1), tangent));
        this.version++;
    }
    public void undo() { if (canUndo()) { this.redo.addLast(state()); restore(this.undo.removeLast()); } }
    public void redo() { if (canRedo()) { this.undo.addLast(state()); restore(this.redo.removeLast()); } }
}
