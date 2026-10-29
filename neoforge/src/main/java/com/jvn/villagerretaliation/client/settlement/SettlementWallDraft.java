package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.settlement.SettlementWall;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

public final class SettlementWallDraft {
    private record State(SettlementWall wall, int spacing, boolean level, boolean snapping, boolean extending, List<Integer> ground, List<Integer> offsets) {}
    private final List<BlockPos> nodes = new ArrayList<>();
    private final List<SettlementWall.Tower> towers = new ArrayList<>();
    private final List<Integer> ground = new ArrayList<>(), offsets = new ArrayList<>();
    private final ArrayDeque<State> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private SettlementWall.Type type;
    private SettlementWall.Part part = SettlementWall.Part.COMPLETE;
    private int spacing;
    private boolean automatic;
    private boolean level;
    private boolean snapping;
    private boolean closed;
    private boolean extending = true;
    private int selected = -1;
    private int version;
    private State gesture;

    public SettlementWallDraft(SettlementWall.Type type, boolean automatic, int spacing) {
        this(type, automatic, spacing, false, true);
    }
    public SettlementWallDraft(SettlementWall.Type type, boolean automatic, int spacing, boolean level, boolean snapping) {
        this.type = type;
        this.automatic = automatic;
        this.spacing = spacing;
        this.level = level;
        this.snapping = snapping;
    }
    public static SettlementWallDraft edit(SettlementWall wall) {
        var draft = new SettlementWallDraft(wall.type(), wall.towerSpacing() != 0, wall.towerSpacing() == 0 ? 16 : wall.towerSpacing());
        draft.part = wall.part();
        draft.nodes.addAll(wall.nodes());
        // span upgrades retain the junction footprints owned by neighboring pieces
        draft.towers.addAll(wall.part() == SettlementWall.Part.SPAN
                ? wall.nodeTowers().stream().map(tower -> tower.resolved(wall.type())).toList() : wall.nodeTowers());
        wall.nodes().forEach(node -> { draft.ground.add(node.getY()); draft.offsets.add(0); });
        draft.closed = wall.closed(); draft.extending = false; draft.selected = wall.nodes().isEmpty() ? -1 : 0;
        return draft;
    }
    public SettlementWall wall() { return new SettlementWall(this.type, this.nodes, this.closed, this.automatic ? this.spacing : 0, this.towers, this.part); }
    public boolean level() { return this.level; }
    public boolean snapping() { return this.snapping; }
    public int version() { return this.version; }
    public int selected() { return this.selected; }
    public int spacing() { return this.spacing; }
    public boolean automatic() { return this.automatic; }
    public boolean extending() { return this.extending && !this.closed; }
    public boolean dragging() { return this.gesture != null; }
    public boolean canUndo() { return !this.undo.isEmpty() && !dragging(); }
    public boolean canRedo() { return !this.redo.isEmpty() && !dragging(); }
    private State state() { return new State(wall(), this.spacing, this.level, this.snapping, this.extending, List.copyOf(this.ground), List.copyOf(this.offsets)); }
    private void remember(State state) {
        this.undo.addLast(state);
        if (this.undo.size() > 128) this.undo.removeFirst();
        this.redo.clear();
    }
    private void restore(State state) {
        this.nodes.clear();
        this.nodes.addAll(state.wall().nodes());
        this.towers.clear(); this.towers.addAll(state.wall().nodeTowers());
        this.ground.clear(); this.ground.addAll(state.ground()); this.offsets.clear(); this.offsets.addAll(state.offsets());
        this.level = state.level(); this.snapping = state.snapping(); this.extending = state.extending();
        this.type = state.wall().type(); this.part = state.wall().part();
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
        int groundY = position.getY();
        position = placement(position);
        int joined = this.nodes.indexOf(position);
        if (joined == this.nodes.size() - 1 && joined >= 0) return false;
        if (joined == 0 && this.nodes.size() >= 3) { loop(); return true; }
        remember(state()); this.nodes.add(position.immutable());
        this.towers.add(joined < 0 ? SettlementWall.Tower.DEFAULT : this.towers.get(joined));
        this.ground.add(joined < 0 ? groundY : this.ground.get(joined));
        this.offsets.add(joined < 0 ? 0 : this.offsets.get(joined));
        this.selected = this.nodes.size() - 1;
        if (joined >= 0) this.extending = false;
        this.version++;
        return true;
    }
    public SettlementWall preview(BlockPos hover) {
        if (hover == null || !extending() || dragging() || this.nodes.size() >= SettlementWall.MAX_NODES) return wall();
        hover = placement(hover);
        int joined = this.nodes.indexOf(hover);
        if (joined == this.nodes.size() - 1 && joined >= 0) return wall();
        if (joined == 0 && this.nodes.size() >= 3)
            return new SettlementWall(this.type, this.nodes, true, this.automatic ? this.spacing : 0, this.towers);
        var shown = new ArrayList<>(this.nodes);
        var choices = new ArrayList<>(this.towers);
        shown.add(hover);
        choices.add(joined < 0 ? SettlementWall.Tower.DEFAULT : this.towers.get(joined));
        return new SettlementWall(this.type, shown, false, this.automatic ? this.spacing : 0, choices);
    }
    public BlockPos placement(BlockPos position) {
        if (this.level && !this.nodes.isEmpty()) position = position.atY(this.nodes.getFirst().getY());
        return snap(position, -1);
    }
    private BlockPos snap(BlockPos position, int excluded) {
        if (!this.snapping) return position;
        BlockPos closest = position;
        double distance = 9.01;
        for (int i = 0; i < this.nodes.size(); i++) {
            var node = this.nodes.get(i);
            if (i == excluded || excluded >= 0 && node.equals(this.nodes.get(excluded))) continue;
            double next = node.distSqr(position);
            if (next < distance) { closest = node; distance = next; }
        }
        return closest;
    }
    public void toggleSnapping() {
        if (dragging()) return;
        remember(state()); this.snapping = !this.snapping; this.version++;
    }
    public void toggleLevel() {
        if (dragging()) return;
        remember(state()); this.level = !this.level;
        if (this.level && !this.nodes.isEmpty()) {
            int y = this.nodes.getFirst().getY();
            this.nodes.replaceAll(node -> node.atY(y));
            weldTowers();
        } else if (!this.level) {
            for (int i = 0; i < this.nodes.size(); i++) this.nodes.set(i, this.nodes.get(i).atY(this.ground.get(i) + this.offsets.get(i)));
            weldTowers();
        }
        this.version++;
    }
    public void height(int delta) {
        if (dragging() || this.selected < 0 || delta == 0) return;
        var selected = this.nodes.get(this.selected);
        int y = selected.getY() + delta;
        if (y < -2048 || y > 2048) return;
        remember(state());
        for (int i = 0; i < this.nodes.size(); i++) if (this.level || this.nodes.get(i).equals(selected)) {
            this.nodes.set(i, this.nodes.get(i).atY(y)); this.offsets.set(i, this.offsets.get(i) + delta);
        }
        weldTowers();
        this.version++;
    }
    private void weldTowers() {
        for (int i = 0; i < this.nodes.size(); i++) {
            int first = this.nodes.indexOf(this.nodes.get(i));
            this.towers.set(i, this.towers.get(first)); this.ground.set(i, this.ground.get(first)); this.offsets.set(i, this.offsets.get(first));
        }
    }
    public void tower(SettlementWall.Tower choice) {
        if (dragging() || this.selected < 0 || this.towers.get(this.selected) == choice) return;
        remember(state());
        var selected = this.nodes.get(this.selected);
        for (int i = 0; i < this.nodes.size(); i++) if (this.nodes.get(i).equals(selected)) this.towers.set(i, choice);
        this.version++;
    }
    public void begin(int index) {
        if (dragging() || index < 0 || index >= this.nodes.size()) return;
        this.selected = index;
        this.gesture = state();
    }
    public void drag(BlockPos position) {
        if (!dragging()) return;
        restore(this.gesture);
        var old = this.nodes.get(this.selected);
        int groundY = position.getY(), offset = this.offsets.get(this.selected);
        position = snap(position.atY(this.level ? old.getY() : groundY + offset), this.selected);
        int joined = this.nodes.indexOf(position);
        var choice = joined < 0 ? this.towers.get(this.selected) : this.towers.get(joined);
        if (joined >= 0) { groundY = this.ground.get(joined); offset = this.offsets.get(joined); }
        for (int i = 0; i < this.nodes.size(); i++) if (this.nodes.get(i).equals(old)) {
            this.nodes.set(i, position.immutable()); this.towers.set(i, choice);
            this.ground.set(i, groundY); this.offsets.set(i, offset);
        }
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
        remember(state()); this.nodes.remove(this.selected); this.towers.remove(this.selected);
        this.ground.remove(this.selected); this.offsets.remove(this.selected);
        if (this.nodes.size() < 3) this.closed = false;
        this.selected = Math.min(this.selected, this.nodes.size() - 1); this.version++;
    }
    public void undo() { if (canUndo()) { this.redo.addLast(state()); restore(this.undo.removeLast()); } }
    public void redo() { if (canRedo()) { this.undo.addLast(state()); restore(this.redo.removeLast()); } }
}
