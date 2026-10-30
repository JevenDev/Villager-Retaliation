package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.network.SettlementPathRequestPayload;
import com.jvn.villagerretaliation.network.SettlementPathResultPayload;
import com.jvn.villagerretaliation.settlement.SettlementPath;
import com.jvn.villagerretaliation.settlement.SettlementPathTerrain;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.LongSupplier;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class SettlementPathEditor {
    private static final UUID EMPTY = new UUID(0, 0);
    private final LongSupplier clock;
    private SettlementPathDraft draft;
    private int width = 3;
    private SettlementPath.Style style = SettlementPath.Style.CLEAN;
    private SettlementPath.Material material = SettlementPath.Material.DIRT;
    private boolean smooth;
    private long revision;
    private int version = -1;
    private long changedAt;
    private long sentAt;
    private SettlementPathRequestPayload pending;
    private SettlementPathResultPayload result;
    private Vec3 hover;
    private SettlementPath shown;
    private SettlementPath planned;
    private com.jvn.villagerretaliation.settlement.SettlementPathGeometry.Footprint footprint;
    private long previewAt;
    private List<SettlementPathResultPayload.Cell> localCells = List.of();
    private List<SettlementPathResultPayload.Cell> cells = List.of();
    private List<Vec3> centerline = List.of();
    private String feedback = "";
    private boolean confirming;
    private int dragPart;
    private SettlementPathGizmo.Handle hoveredHandle;
    private double dragX;
    private double dragY;

    public SettlementPathEditor() { this(Util::getMillis); }
    public SettlementPathEditor(LongSupplier clock) { this.clock = java.util.Objects.requireNonNull(clock); }

    public SettlementPathDraft draft() { return this.draft; }
    public boolean active() { return this.draft != null; }
    public boolean confirming() { return this.confirming; }
    public List<SettlementPathResultPayload.Cell> cells() { return this.cells; }
    public Vec3 hover() { return this.hover; }
    public List<Vec3> centerline() { return this.centerline; }
    public int dragPart() { return this.dragPart; }
    public SettlementPathGizmo.Handle hoveredHandle() { return this.hoveredHandle; }
    public void hoveredHandle(SettlementPathGizmo.Handle handle) { this.hoveredHandle = handle; }
    public void feedback(String message) { this.feedback = message; }
    public void begin(boolean branch) {
        if (this.confirming) return;
        rememberSettings();
        this.draft = new SettlementPathDraft(this.width, this.style, this.material, UUID.randomUUID().getLeastSignificantBits(), branch, this.smooth);
        this.planned = null;
        this.footprint = null;
        this.version = -1;
        this.hoveredHandle = null;
        this.result = null;
        this.pending = null;
        this.shown = null;
        this.centerline = List.of();
        this.hover = null;
        this.cells = List.of();
        this.localCells = List.of();
        this.feedback = branch ? "Select an existing path or road to branch from" : "Click valid ground to start a path";
    }
    public void cancel(UUID session, Consumer<SettlementPathRequestPayload> send) {
        if (!active() || this.confirming) return;
        rememberSettings();
        this.draft = null;
        this.planned = null;
        this.footprint = null;
        this.hover = null;
        this.shown = null;
        this.centerline = List.of();
        this.cells = List.of();
        this.result = null;
        this.localCells = List.of();
        send(new SettlementPathRequestPayload(session, ++this.revision, SettlementPathRequestPayload.Action.CANCEL, EMPTY, null), send);
    }
    private void rememberSettings() {
        if (!active()) return;
        this.width = this.draft.path().width();
        this.style = this.draft.path().style();
        this.material = this.draft.path().material();
        this.smooth = this.draft.smoothMode();
    }
    public void stopExtending() {
        if (!active() || this.confirming) return;
        this.draft.stopExtending();
        this.hover = null;
        this.hoveredHandle = null;
        this.shown = null;
        this.feedback = "";
    }
    public void hover(Vec3 point) {
        this.hover = this.confirming || active() && (this.draft.dragging() || !this.draft.extending()) ? null : point;
    }
    public void startDrag(double x, double y, int part) { this.dragX = x; this.dragY = y; this.dragPart = part; this.hover = null; }
    public boolean moved(double x, double y) { return Math.hypot(x - this.dragX, y - this.dragY) >= 3; }

    public void tick(Level level, UUID session, Predicate<BlockPos> allowed, Consumer<SettlementPathRequestPayload> send) {
        long now = this.clock.getAsLong();
        if (active() && !this.confirming) {
            if (this.version != this.draft.version()) {
                this.version = this.draft.version();
                this.revision++;
                this.changedAt = now;
                this.result = null;
                this.pending = null;
                this.shown = null;
                this.feedback = "";
            }
            var path = this.draft.path();
            rememberSettings();
            var shown = path;
            if (this.draft.extending() && !this.draft.inserting()) {
                if (this.hoveredHandle != null && this.hoveredHandle.node() == 0 && this.hoveredHandle.part() == 0 && this.draft.canClose())
                    shown = this.draft.loopPreview();
                else if (this.hover != null && !path.nodes().isEmpty() && path.nodes().size() < SettlementPath.MAX_NODES)
                    shown = this.draft.preview(this.hover);
            }
            if (!shown.equals(this.shown) || now - this.previewAt >= 250) {
                this.previewAt = now;
                this.shown = shown;
                if (!shown.equals(this.planned)) {
                    this.planned = shown;
                    this.footprint = com.jvn.villagerretaliation.settlement.SettlementPathGeometry.rasterize(shown);
                    try { this.centerline = com.jvn.villagerretaliation.settlement.SettlementPathGeometry.samples(shown); }
                    catch (IllegalArgumentException limit) { this.centerline = shown.nodes().stream().map(SettlementPath.Node::position).toList(); }
                }
                var preview = SettlementPathTerrain.preview(level, shown, allowed, this.footprint);
                this.localCells = preview.cells().stream().map(cell -> new SettlementPathResultPayload.Cell(cell.position(), cell.kind())).toList();
                this.feedback = shown.equals(path) && this.result != null && !this.result.message().isEmpty()
                        ? this.result.message() : preview.error();
                if (shown.equals(path) && this.result != null && this.result.status() == SettlementPathResultPayload.Status.REJECTED
                        && this.result.cells().stream().anyMatch(cell -> cell.kind() == SettlementPathTerrain.Kind.UNAVAILABLE)
                        && !this.localCells.isEmpty() && this.localCells.stream().noneMatch(cell -> cell.kind() == SettlementPathTerrain.Kind.UNAVAILABLE))
                    refresh();
                var cells = displayCells(level, shown.equals(path) && this.result != null && !this.result.cells().isEmpty()
                        ? this.result.cells() : this.localCells);
                if (!this.cells.equals(cells)) this.cells = cells;
            }
            if (!this.draft.dragging() && path.nodes().size() >= 2 && this.pending == null && this.result == null && now - this.changedAt >= 150)
                send(new SettlementPathRequestPayload(session, this.revision, SettlementPathRequestPayload.Action.PREVIEW, EMPTY, path), send);
        }
        if (this.pending != null && now - this.sentAt >= 2000) send(this.pending, send);
    }

    private List<SettlementPathResultPayload.Cell> displayCells(Level level, List<SettlementPathResultPayload.Cell> source) {
        return source.stream().map(cell -> {
            if ((cell.kind() == SettlementPathTerrain.Kind.OUTSIDE || cell.kind() == SettlementPathTerrain.Kind.UNAVAILABLE)
                    && level.hasChunkAt(cell.position())) {
                var surface = SettlementPathTerrain.surface(level, cell.position().getX(), cell.position().getZ());
                if (!level.isOutsideBuildHeight(surface)) return new SettlementPathResultPayload.Cell(surface, cell.kind());
            }
            return cell;
        }).toList();
    }

    private void send(SettlementPathRequestPayload request, Consumer<SettlementPathRequestPayload> send) {
        this.pending = request;
        this.sentAt = this.clock.getAsLong();
        send.accept(request);
    }
    public void refresh() {
        if (!active() || this.confirming || this.draft.dragging()) return;
        this.revision++;
        this.result = null;
        this.pending = null;
        this.shown = null;
        this.changedAt = this.clock.getAsLong();
        this.feedback = "";
    }
    public boolean canConfirm() {
        return active() && !this.confirming && !this.draft.dragging() && this.version == this.draft.version()
                && this.result != null && this.result.revision() == this.revision
                && this.result.status() == SettlementPathResultPayload.Status.PREVIEW;
    }
    public void confirm(UUID session, Consumer<SettlementPathRequestPayload> send) {
        if (!canConfirm()) return;
        rememberSettings();
        this.hover = null;
        this.cells = this.result.cells();
        this.centerline = com.jvn.villagerretaliation.settlement.SettlementPathGeometry.samples(this.draft.path());
        this.confirming = true;
        send(new SettlementPathRequestPayload(session, this.revision, SettlementPathRequestPayload.Action.CONFIRM, this.result.token(), null), send);
    }
    public boolean accept(SettlementPathResultPayload result) {
        if (result.revision() != this.revision || active() && this.version != this.draft.version()) return false;
        this.pending = null;
        this.result = result;
        this.shown = null;
        this.feedback = result.message();
        this.confirming = false;
        if (result.status() == SettlementPathResultPayload.Status.APPLIED) {
            rememberSettings();
            this.draft = null;
            this.shown = null;
            this.centerline = List.of();
            this.cells = List.of();
            this.localCells = List.of();
            this.hover = null;
            return true;
        }
        return false;
    }
    String requirements() {
        return this.result != null && this.result.status() == SettlementPathResultPayload.Status.PREVIEW && !this.result.message().isBlank()
                ? this.result.message().replace("Confirm queues builder work. ", "") : "Awaiting validated requirements";
    }

    String placementStatus() {
        if (confirming()) return "Submitting builder work...";
        if (canConfirm()) return "Valid placement: Confirm requests queued builder work";
        if (this.result != null && this.result.status() == SettlementPathResultPayload.Status.REJECTED) return "Cannot place: " + this.result.message();
        return "Finish the draft, then wait for validation";
    }

    public String hint() {
        if (!active()) return this.feedback;
        if (this.confirming) return "Confirming path placement...";
        long converted = this.cells.stream().filter(cell -> cell.kind() == SettlementPathTerrain.Kind.CONVERT).count();
        long skipped = this.cells.stream().filter(cell -> cell.kind() == SettlementPathTerrain.Kind.SKIPPED).count();
        long outside = this.cells.stream().filter(cell -> cell.kind() == SettlementPathTerrain.Kind.OUTSIDE).count();
        long unavailable = this.cells.stream().filter(cell -> cell.kind() == SettlementPathTerrain.Kind.UNAVAILABLE).count();
        String state = this.draft.path().nodes().isEmpty()
                ? this.draft.path().branch() ? "Select an existing path or road to branch from" : "Click valid ground to start. Click-drag draws a curve."
                : this.feedback.isEmpty() ? this.draft.path().closed() ? "Loop closed. Edit nodes or Confirm. Cancel discards the draft."
                        : !this.draft.extending() ? "Draft kept. Extend resumes drawing. Confirm queues builder work. Cancel discards it."
                        : "Click adds a node. Click the first node to close a loop. Escape stops extending. Cancel discards." : this.feedback;
        return (this.draft.path().branch() ? "Branch" : this.draft.path().material().label()) + " / " + this.draft.path().nodes().size() + "/64 nodes / "
                + converted + " convert / " + skipped + " skipped" + (outside == 0 ? "" : " / " + outside + " outside")
                + (unavailable == 0 ? "" : " / " + unavailable + " unloaded") + "\n" + state;
    }
}
