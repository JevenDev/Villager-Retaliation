package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.network.SettlementPathRequestPayload;
import com.jvn.villagerretaliation.network.SettlementPathResultPayload;
import com.jvn.villagerretaliation.settlement.SettlementPath;
import com.jvn.villagerretaliation.settlement.SettlementPathTerrain;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class SettlementPathEditor {
    private static final UUID EMPTY = new UUID(0, 0);
    private SettlementPathDraft draft;
    private int width = 3;
    private SettlementPath.Style style = SettlementPath.Style.CLEAN;
    private boolean smooth;
    private long revision;
    private int version = -1;
    private long changedAt;
    private long sentAt;
    private SettlementPathRequestPayload pending;
    private SettlementPathResultPayload result;
    private Vec3 hover;
    private SettlementPath shown;
    private List<SettlementPathResultPayload.Cell> cells = List.of();
    private List<Vec3> centerline = List.of();
    private String feedback = "";
    private boolean confirming;
    private int dragPart;
    private SettlementPathGizmo.Handle hoveredHandle;
    private double dragX;
    private double dragY;

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
        this.draft = new SettlementPathDraft(this.width, this.style, UUID.randomUUID().getLeastSignificantBits(), branch, this.smooth);
        this.version = -1;
        this.hoveredHandle = null;
        this.result = null;
        this.pending = null;
        this.shown = null;
        this.centerline = List.of();
        this.hover = null;
        this.cells = List.of();
        this.feedback = branch ? "Select an existing dirt path to branch from" : "Click valid ground to start a path";
    }
    public void cancel(UUID session, Consumer<SettlementPathRequestPayload> send) {
        if (!active() || this.confirming) return;
        rememberSettings();
        this.draft = null;
        this.hover = null;
        this.shown = null;
        this.centerline = List.of();
        this.cells = List.of();
        this.result = null;
        send(new SettlementPathRequestPayload(session, ++this.revision, SettlementPathRequestPayload.Action.CANCEL, EMPTY, null), send);
    }
    private void rememberSettings() {
        if (!active()) return;
        this.width = this.draft.path().width();
        this.style = this.draft.path().style();
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
        long now = Util.getMillis();
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
            if (!shown.equals(this.shown)) {
                this.shown = shown;
                var preview = SettlementPathTerrain.preview(level, shown, allowed);
                var geometry = com.jvn.villagerretaliation.settlement.SettlementPathGeometry.rasterize(shown);
                this.centerline = geometry.error().isEmpty() ? com.jvn.villagerretaliation.settlement.SettlementPathGeometry.samples(shown) : List.of();
                this.cells = preview.cells().stream().map(cell -> new SettlementPathResultPayload.Cell(cell.position(), cell.kind())).toList();
                if (this.result == null) this.feedback = preview.error();
            }
            if (shown.equals(path) && this.result != null) this.cells = this.result.cells();
            if (!this.draft.dragging() && path.nodes().size() >= 2 && this.pending == null && this.result == null && now - this.changedAt >= 150)
                send(new SettlementPathRequestPayload(session, this.revision, SettlementPathRequestPayload.Action.PREVIEW, EMPTY, path), send);
        }
        if (this.pending != null && now - this.sentAt >= 2000) send(this.pending, send);
    }

    private void send(SettlementPathRequestPayload request, Consumer<SettlementPathRequestPayload> send) {
        this.pending = request;
        this.sentAt = Util.getMillis();
        send.accept(request);
    }
    public void refresh() {
        if (!active() || this.confirming || this.draft.dragging()) return;
        this.revision++;
        this.result = null;
        this.pending = null;
        this.shown = null;
        this.changedAt = Util.getMillis();
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
        this.feedback = result.message();
        this.confirming = false;
        if (result.status() == SettlementPathResultPayload.Status.APPLIED) {
            rememberSettings();
            this.draft = null;
            this.shown = null;
            this.centerline = List.of();
            this.cells = List.of();
            this.hover = null;
            return true;
        }
        return false;
    }
    public String hint() {
        if (!active()) return this.feedback;
        if (this.confirming) return "Confirming path placement...";
        long converted = this.cells.stream().filter(cell -> cell.kind() == SettlementPathTerrain.Kind.CONVERT).count();
        long skipped = this.cells.stream().filter(cell -> cell.kind() == SettlementPathTerrain.Kind.SKIPPED).count();
        String state = this.draft.path().nodes().isEmpty()
                ? this.draft.path().branch() ? "Select an existing dirt path to branch from" : "Click valid ground to start. Click-drag draws a curve."
                : this.feedback.isEmpty() ? this.draft.path().closed() ? "Loop closed. Edit nodes or Confirm. Cancel discards the draft."
                        : !this.draft.extending() ? "Draft kept. Extend resumes drawing. Confirm applies it. Cancel discards it."
                        : "Click adds a node. Click the first node to close a loop. Escape stops extending. Cancel discards." : this.feedback;
        return (this.draft.path().branch() ? "Branch" : "Path") + " / " + this.draft.path().nodes().size() + "/64 nodes / "
                + converted + " convert / " + skipped + " skipped\n" + state;
    }
}
