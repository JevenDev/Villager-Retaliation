package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.network.SettlementFarmRequestPayload;
import com.jvn.villagerretaliation.network.SettlementFarmResultPayload;
import com.jvn.villagerretaliation.settlement.SettlementFarm;
import com.jvn.villagerretaliation.settlement.SettlementFarmTerrain;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class SettlementFarmEditor {
    private static final UUID EMPTY = new UUID(0, 0);
    private final LongSupplier clock;
    private boolean active;
    private boolean selecting;
    private boolean confirming;
    private int dragging = -1;
    private SettlementFarm.Shape shape = SettlementFarm.Shape.RECTANGLE;
    private final List<BlockPos> nodes = new ArrayList<>();
    private BlockPos hover;
    private long revision;
    private long changedAt;
    private long sentAt;
    private long previewAt;
    private SettlementFarm shown;
    private SettlementFarmRequestPayload pending;
    private SettlementFarmResultPayload result;
    private List<SettlementFarmResultPayload.Cell> cells = List.of();
    private String feedback = "";
    private UUID area;
    private SettlementFarm original;
    private com.jvn.villagerretaliation.settlement.SettlementCropPlan crops = com.jvn.villagerretaliation.settlement.SettlementCropPlan.NONE;

    public com.jvn.villagerretaliation.settlement.SettlementCropPlan crops() { return this.crops; }
    public void crops(com.jvn.villagerretaliation.settlement.SettlementCropPlan crops) {
        if (!active() || confirming() || this.crops.equals(crops)) return;
        this.crops = crops;
        changed();
    }

    public void begin(com.jvn.villagerretaliation.network.SettlementManagementPayload.Area area) {
        begin();
        this.area = area.id();
        this.original = area.farm();
        this.crops = area.crops();
        var farm = area.farm() == null ? new SettlementFarm(area.min(), area.max()) : area.farm();
        this.shape = farm.shape();
        this.nodes.addAll(farm.nodes());
        this.selecting = false;
        changed();
    }

    public SettlementFarmEditor() { this(Util::getMillis); }
    public SettlementFarmEditor(LongSupplier clock) { this.clock = clock; }
    public boolean active() { return this.active; }
    public boolean confirming() { return this.confirming; }
    public boolean dragging() { return this.dragging >= 0; }
    public BlockPos first() { return this.nodes.isEmpty() ? null : this.nodes.getFirst(); }
    public BlockPos second() { return this.nodes.size() < 2 ? first() : this.nodes.get(1); }
    public List<BlockPos> nodes() { return List.copyOf(this.nodes); }
    public List<BlockPos> outlineNodes() {
        var points = new ArrayList<>(this.nodes);
        if (this.shape == SettlementFarm.Shape.POLYGON && this.selecting && this.hover != null
                && points.stream().noneMatch(node -> sameColumn(node, this.hover))) points.add(this.hover);
        return points;
    }
    public SettlementFarm.Shape shape() { return this.shape; }
    public boolean selecting() { return this.selecting; }
    public boolean canFinish() { return active() && !confirming() && this.selecting && this.shape == SettlementFarm.Shape.POLYGON && this.nodes.size() >= 3; }
    public void shape(SettlementFarm.Shape shape) {
        if (!active() || confirming() || this.shape == shape) return;
        resetGeometry();
        this.shape = shape;
    }
    public void finish() {
        if (!canFinish()) return;
        this.selecting = false;
        this.hover = null;
        changed();
    }
    public void removeNode() {
        if (!active() || confirming() || this.shape != SettlementFarm.Shape.POLYGON || this.nodes.isEmpty()) return;
        this.nodes.removeLast();
        this.selecting = true;
        this.hover = null;
        this.dragging = -1;
        changed();
    }
    public List<SettlementFarmResultPayload.Cell> cells() { return this.cells; }

    public void begin() {
        this.area = null;
        this.original = null;
        this.crops = com.jvn.villagerretaliation.settlement.SettlementCropPlan.NONE;
        resetGeometry();
    }

    private void resetGeometry() {
        this.active = true;
        this.selecting = true;
        this.confirming = false;
        this.shape = SettlementFarm.Shape.RECTANGLE;
        this.nodes.clear();
        this.hover = null;
        this.dragging = -1;
        this.cells = List.of();
        changed();
    }

    public void click(BlockPos pos, Function<Vec3, double[]> project, double x, double y) {
        if (!active() || confirming() || pos == null) return;
        if (this.shape == SettlementFarm.Shape.POLYGON && this.selecting) {
            if (canFinish() && sameColumn(pos, first())) { finish(); return; }
            if (this.nodes.size() == SettlementFarm.MAX_NODES) {
                this.feedback = "Polygon has 32 nodes. Close the polygon or remove the last node.";
                return;
            }
            if (this.nodes.stream().anyMatch(node -> sameColumn(node, pos))) return;
            this.nodes.add(pos.immutable());
            this.hover = null;
        } else if (this.nodes.isEmpty()) {
            this.nodes.add(pos.immutable());
            this.nodes.add(pos.immutable());
        } else if (this.selecting) {
            this.nodes.set(1, pos.immutable());
            this.selecting = false;
        } else {
            double nearest = Double.MAX_VALUE;
            int handle = -1;
            for (int i = 0; i < this.nodes.size(); i++) {
                var screen = project.apply(Vec3.atCenterOf(this.nodes.get(i)).add(0, 0.5, 0));
                double distance = screen == null ? Double.MAX_VALUE : Math.hypot(screen[0] - x, screen[1] - y);
                if (distance < nearest) { nearest = distance; handle = i; }
            }
            if (handle < 0 || this.shape == SettlementFarm.Shape.POLYGON && nearest > 12) return;
            this.dragging = handle;
            drag(pos);
        }
        changed();
    }

    public void hover(BlockPos pos) {
        if (!active() || confirming() || !this.selecting || this.nodes.isEmpty()) return;
        if (this.shape == SettlementFarm.Shape.POLYGON) {
            if (!java.util.Objects.equals(this.hover, pos)) { this.hover = pos; changed(); }
        } else if (pos != null && !pos.equals(second())) {
            this.nodes.set(1, pos.immutable());
            changed();
        }
    }

    public void drag(BlockPos pos) {
        if (!dragging() || confirming() || pos == null) return;
        if (pos.equals(this.nodes.get(this.dragging))) return;
        if (this.shape == SettlementFarm.Shape.CIRCLE && this.dragging == 0) {
            var offset = pos.subtract(first());
            this.nodes.set(1, second().offset(offset));
        }
        this.nodes.set(this.dragging, pos.immutable());
        changed();
    }

    private static boolean sameColumn(BlockPos a, BlockPos b) {
        return a != null && b != null && a.getX() == b.getX() && a.getZ() == b.getZ();
    }

    private SettlementFarm farm() {
        var points = new ArrayList<>(this.nodes);
        if (this.shape == SettlementFarm.Shape.POLYGON && this.selecting && this.hover != null
                && points.size() < SettlementFarm.MAX_NODES && points.stream().noneMatch(node -> sameColumn(node, this.hover)))
            points.add(this.hover);
        if (this.shape == SettlementFarm.Shape.POLYGON && points.size() < 3)
            throw new IllegalArgumentException("Add at least three polygon corners");
        return new SettlementFarm(this.shape, points);
    }

    public void release() { this.dragging = -1; this.changedAt = this.clock.getAsLong(); }
    public void refresh() { if (active() && !confirming()) changed(); }

    private void changed() {
        this.revision++;
        this.changedAt = this.clock.getAsLong();
        this.shown = null;
        this.cells = List.of();
        this.result = null;
        this.pending = null;
        this.feedback = "";
    }

    public void cancel(UUID session, Consumer<SettlementFarmRequestPayload> send) {
        if (!active() || confirming()) return;
        this.active = false;
        this.cells = List.of();
        this.result = null;
        this.dragging = -1;
        send(new SettlementFarmRequestPayload(session, ++this.revision, SettlementFarmRequestPayload.Action.CANCEL, EMPTY, null), send);
    }

    public void tick(Level level, UUID session, Predicate<BlockPos> allowed, Consumer<SettlementFarmRequestPayload> send) {
        long now = this.clock.getAsLong();
        if (active() && !confirming() && !this.nodes.isEmpty()) {
            SettlementFarm farm;
            try { farm = farm(); }
            catch (IllegalArgumentException limit) {
                this.feedback = limit.getMessage();
                this.cells = List.of();
                return;
            }
            if (!farm.equals(this.shown) || now - this.previewAt >= 250) {
                this.shown = farm;
                this.previewAt = now;
                var preview = SettlementFarmTerrain.preview(level, farm, allowed);
                var cells = preview.cells().stream().map(cell -> new SettlementFarmResultPayload.Cell(cell.position(), cell.kind())).toList();
                var displayed = this.result != null && !this.result.cells().isEmpty() ? this.result.cells() : cells;
                if (!this.cells.equals(displayed)) this.cells = displayed;
                this.feedback = this.result != null ? this.result.message()
                        : !preview.error().isEmpty() ? preview.error() : preview.materials();
            }
            if (!this.selecting && !dragging() && this.result == null && this.pending == null && now - this.changedAt >= 150)
                send(new SettlementFarmRequestPayload(session, this.revision, SettlementFarmRequestPayload.Action.PREVIEW, EMPTY, farm, this.area, this.original, this.crops), send);
        }
        if (this.pending != null && now - this.sentAt >= 2000) send(this.pending, send);
    }

    public boolean canConfirm() {
        return active() && !confirming() && !this.selecting && !dragging() && this.result != null
                && this.result.revision() == this.revision && this.result.status() == SettlementFarmResultPayload.Status.PREVIEW;
    }

    public void confirm(UUID session, Consumer<SettlementFarmRequestPayload> send) {
        if (!canConfirm()) return;
        this.confirming = true;
        send(new SettlementFarmRequestPayload(session, this.revision, SettlementFarmRequestPayload.Action.CONFIRM, this.result.token(), null), send);
    }

    private void send(SettlementFarmRequestPayload request, Consumer<SettlementFarmRequestPayload> send) {
        this.pending = request;
        this.sentAt = this.clock.getAsLong();
        send.accept(request);
    }

    public boolean accept(SettlementFarmResultPayload response) {
        if (response.revision() != this.revision) return false;
        this.pending = null;
        this.result = response;
        this.confirming = false;
        this.feedback = response.message();
        if (!response.cells().isEmpty()) this.cells = response.cells();
        if (response.status() == SettlementFarmResultPayload.Status.APPLIED) {
            this.active = false;
            this.cells = List.of();
            return true;
        }
        return false;
    }

    String requirements() {
        return this.result != null && !this.result.message().isBlank()
                ? this.result.message() : "Awaiting validated requirements";
    }

    String placementStatus() {
        if (confirming()) return "Submitting farmland work...";
        if (canConfirm()) return "Valid plan: Confirm requests farm preparation";
        if (this.result != null && this.result.status() == SettlementFarmResultPayload.Status.REJECTED) return "Cannot place: " + this.result.message();
        if (this.shown == null && !this.feedback.isBlank() && !this.selecting && this.pending == null) return "Cannot place: " + this.feedback;
        return "Finish the draft, then wait for validation";
    }

    public String hint() {
        if (confirming()) return "Assigning farmland work...";
        if (this.nodes.isEmpty()) return this.shape == SettlementFarm.Shape.POLYGON ? "Farmland: click to add polygon corners."
                : this.shape == SettlementFarm.Shape.CIRCLE ? "Farmland: click the centre of the circle."
                : "Farmland: click the first corner of the area.";
        long till = this.cells.stream().filter(cell -> cell.kind() == SettlementFarmTerrain.Kind.TILL).count();
        long water = this.cells.stream().filter(cell -> cell.kind() == SettlementFarmTerrain.Kind.WATER).count();
        long skipped = this.cells.stream().filter(cell -> cell.kind() == SettlementFarmTerrain.Kind.SKIPPED).count();
        long dry = this.cells.stream().filter(cell -> cell.kind() == SettlementFarmTerrain.Kind.DRY).count();
        String instructions = this.shape == SettlementFarm.Shape.POLYGON
                ? this.selecting ? "Click corners, then click the first node or Close polygon. " : "Drag a node to reshape. "
                : this.shape == SettlementFarm.Shape.CIRCLE
                ? this.selecting ? "Move to resize, then click the radius. " : "Drag the centre or radius handle. "
                : this.selecting ? "Move to resize, then click the opposite corner. " : "Drag either corner to resize. ";
        return this.shape.label() + " / " + till + " soil / " + water + " water / " + skipped + " skipped / " + dry + " dry\n"
                + instructions + (this.result != null && this.result.status() == SettlementFarmResultPayload.Status.REJECTED || this.shown == null
                        ? this.feedback : canConfirm() ? "Review requirements, then confirm." : this.selecting ? "" : "Waiting for validation.");
    }
}
