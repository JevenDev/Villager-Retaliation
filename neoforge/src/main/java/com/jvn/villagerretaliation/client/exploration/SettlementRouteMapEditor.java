package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.client.settlement.SettlementPathDraft;
import com.jvn.villagerretaliation.client.settlement.SettlementPathGizmo;
import com.jvn.villagerretaliation.settlement.SettlementPath;
import com.jvn.villagerretaliation.settlement.SettlementRouteShape;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

final class SettlementRouteMapEditor {
    private final SettlementPathDraft draft;
    private SettlementRouteShape before;
    private SettlementPathGizmo.Handle handle;
    private double dragX, dragY;
    private boolean moved, independentHandles;
    private int handleNode = -1;
    private SettlementPath shown;
    private List<Vec3> samples = List.of();

    SettlementRouteMapEditor(SettlementRouteShape shape) { draft = new SettlementPathDraft(shape.path(), shape.smooth()); }
    SettlementPathDraft draft() { return draft; }
    SettlementRouteShape shape() { return new SettlementRouteShape(grid(draft.path()).nodes(), draft.smoothMode()); }
    private static Vec3 snap(Vec3 point) { return new Vec3(Math.floor(point.x) + .5, point.y, Math.floor(point.z) + .5); }
    private static SettlementPath grid(SettlementPath path) {
        var nodes = path.nodes().stream().map(node -> {
            var position = snap(node.position());
            var outgoing = snap(node.position().add(node.outgoing())).subtract(position);
            var incoming = node.incoming().add(node.outgoing()).lengthSqr() < 1E-12 ? outgoing.scale(-1)
                    : snap(node.position().add(node.incoming())).subtract(position);
            return new SettlementPath.Node(position, incoming, outgoing);
        }).toList();
        return new SettlementPath(nodes, path.width(), path.style(), path.material(), path.seed(), path.branch(), path.closed());
    }
    List<BlockPos> points() { return shape().points(); }
    boolean dragging() { return draft.dragging(); }
    SettlementPathGizmo.Handle hit(Function<Vec3, double[]> project, double x, double y) { return SettlementPathGizmo.hit(draft, project, x, y); }
    boolean removable() { return draft.selected() > 0 && draft.selected() < draft.path().nodes().size() - 1; }
    boolean insertable() { return draft.selected() >= 0 && draft.selected() < draft.path().nodes().size() - 1 && points().size() < SettlementRouteShape.MAX_POINTS; }
    void select(int index) { draft.select(index); syncHandleMode(); }
    private void syncHandleMode() {
        if (handleNode == draft.selected()) return;
        handleNode = draft.selected();
        independentHandles = handleNode >= 0 && draft.path().nodes().get(handleNode).incoming().add(draft.path().nodes().get(handleNode).outgoing()).lengthSqr() > 1E-12;
    }
    boolean independentHandles() { syncHandleMode(); return independentHandles; }
    boolean hasHandles() {
        int selected = draft.selected();
        return selected >= 0 && (draft.path().nodes().get(selected).incoming().lengthSqr() > 0 || draft.path().nodes().get(selected).outgoing().lengthSqr() > 0);
    }
    void curveSelected(boolean curved) { draft.curveSelected(curved); handleNode = -1; }
    void toggleHandles() {
        syncHandleMode();
        if (independentHandles) draft.linkSelectedHandles();
        independentHandles = !independentHandles;
    }
    void undo() { draft.undo(); handleNode = -1; }
    void redo() { draft.redo(); handleNode = -1; }
    void begin(SettlementPathGizmo.Handle hit, double x, double y) {
        select(hit.node()); before = shape(); handle = hit; dragX = x; dragY = y; moved = false; draft.begin(hit.node(), hit.part());
    }
    boolean place(Vec3 point, double x, double y) {
        if (points().size() >= SettlementRouteShape.MAX_POINTS || !draft.extending() && !draft.inserting()) return false;
        before = shape();
        if (!draft.inserting()) draft.select(draft.path().nodes().size() - 2);
        draft.inserting(true);
        if (!draft.place(point)) { before = null; return false; }
        handleNode = -1; handle = new SettlementPathGizmo.Handle(draft.selected(), 1); dragX = x; dragY = y; moved = false; return true;
    }
    void drag(Vec3 point, double x, double y, boolean independent) {
        moved |= Math.hypot(x - dragX, y - dragY) >= 3;
        if (!moved || point == null || !dragging()) return;
        if (handle.part() == 0 && (handle.node() == 0 || handle.node() == draft.path().nodes().size() - 1)) return;
        draft.drag(snap(point), independent || independentHandles());
    }
    int dragPart() { return handle == null ? 0 : handle.part(); }
    boolean finish(boolean valid) {
        if (!dragging()) return false;
        if (valid) draft.finish(); else draft.cancelDrag();
        boolean changed = !shape().equals(before); before = null; handle = null; return changed;
    }
    void cancel() { if (dragging()) draft.cancelDrag(); before = null; handle = null; }
    void remove() { if (removable()) draft.remove(); }
    void insert() { if (insertable()) draft.inserting(!draft.inserting()); }
    void extend() { if (draft.extending()) draft.stopExtending(); else { draft.inserting(false); draft.extend(); } }
    List<Vec3> samples(SettlementPath path) {
        if (!path.equals(shown)) { samples = com.jvn.villagerretaliation.settlement.SettlementPathGeometry.samples(path); shown = path; }
        return samples;
    }
}
