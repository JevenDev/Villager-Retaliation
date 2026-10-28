package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.item.ConstructionBlueprintItem;
import com.jvn.villagerretaliation.network.SettlementWallRequestPayload;
import com.jvn.villagerretaliation.network.SettlementWallResultPayload;
import com.jvn.villagerretaliation.settlement.SettlementWall;
import com.jvn.villagerretaliation.settlement.SettlementWallTerrain;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;

public final class SettlementWallEditor {
    private static final UUID EMPTY = new UUID(0, 0);
    private SettlementWallDraft draft;
    private SettlementWall.Type type = SettlementWall.Type.LOW_STONE;
    private boolean automatic;
    private int spacing = 16;
    private long revision;
    private int version = -1;
    private long changedAt, sentAt, previewAt;
    private SettlementWallRequestPayload pending;
    private SettlementWallResultPayload result;
    private SettlementWall shown;
    private BlockPos hover;
    private List<SettlementWallResultPayload.Cell> cells = List.of();
    private ConstructionBlueprintItem.PreviewData ghost;
    private boolean confirming;
    private String feedback = "";

    public boolean active() { return this.draft != null; }
    public boolean confirming() { return this.confirming; }
    public SettlementWallDraft draft() { return this.draft; }
    public List<SettlementWallResultPayload.Cell> cells() { return this.cells; }
    public ConstructionBlueprintItem.PreviewData ghost() { return this.ghost; }
    public SettlementWall shown() { return this.shown; }
    public void feedback(String value) { this.feedback = value; }
    public void begin() {
        if (this.confirming) return;
        rememberSettings();
        this.draft = new SettlementWallDraft(this.type, this.automatic, this.spacing);
        this.version = -1;
        clearPreview();
    }
    private void rememberSettings() {
        if (!active()) return;
        this.type = this.draft.wall().type(); this.automatic = this.draft.automatic(); this.spacing = this.draft.spacing();
    }
    private void clearPreview() {
        this.pending = null; this.result = null; this.shown = null; this.hover = null;
        this.cells = List.of(); this.ghost = null; this.feedback = "";
    }
    public void cancel(UUID session, Consumer<SettlementWallRequestPayload> send) {
        if (!active() || this.confirming) return;
        rememberSettings(); this.draft = null; clearPreview();
        send.accept(new SettlementWallRequestPayload(session, ++this.revision, SettlementWallRequestPayload.Action.CANCEL, EMPTY, null));
    }
    public void stop() { if (active() && !this.confirming) { this.draft.stop(); hover(null); } }
    public void hover(BlockPos point) { this.hover = this.confirming || !active() || !this.draft.extending() || this.draft.dragging() ? null : point; }
    public void refresh() {
        if (!active() || this.confirming) return;
        this.revision++; this.result = null; this.pending = null; this.shown = null; this.changedAt = Util.getMillis();
    }
    public void tick(Level level, UUID session, Predicate<BlockPos> allowed, Consumer<SettlementWallRequestPayload> send) {
        long now = Util.getMillis();
        if (active() && !this.confirming) {
            if (this.version != this.draft.version()) {
                this.version = this.draft.version(); refresh();
            }
            rememberSettings();
            var shown = this.draft.preview(this.hover);
            if (!shown.equals(this.shown) || now - this.previewAt >= 250) {
                this.shown = shown; this.previewAt = now;
                var preview = SettlementWallTerrain.preview(level, shown, allowed);
                this.cells = shown.equals(this.draft.wall()) && this.result != null && !this.result.cells().isEmpty()
                        ? this.result.cells() : preview.cells().stream().map(cell ->
                                new SettlementWallResultPayload.Cell(cell.position(), cell.target(), cell.kind())).toList();
                this.feedback = shown.equals(this.draft.wall()) && this.result != null && !this.result.message().isEmpty()
                        ? this.result.message() : preview.error();
                this.ghost = ghost(level, this.cells);
            }
            if (!this.draft.dragging() && this.draft.wall().nodes().size() >= 2 && this.pending == null && this.result == null
                    && now - this.changedAt >= 150)
                send(new SettlementWallRequestPayload(session, this.revision, SettlementWallRequestPayload.Action.PREVIEW, EMPTY, this.draft.wall()), send);
        }
        if (this.pending != null && now - this.sentAt >= 2000) send(this.pending, send);
    }
    private ConstructionBlueprintItem.PreviewData ghost(Level level, List<SettlementWallResultPayload.Cell> cells) {
        if (cells.isEmpty()) return null;
        var origin = cells.getFirst().position();
        var min = origin;
        var max = origin;
        for (var cell : cells) {
            var pos = cell.position();
            min = new BlockPos(Math.min(min.getX(), pos.getX()), Math.min(min.getY(), pos.getY()), Math.min(min.getZ(), pos.getZ()));
            max = new BlockPos(Math.max(max.getX(), pos.getX()), Math.max(max.getY(), pos.getY()), Math.max(max.getZ(), pos.getZ()));
        }
        var blocks = cells.stream().map(cell -> new ConstructionBlueprintItem.PreviewBlock(cell.position().subtract(origin), cell.state())).toList();
        return new ConstructionBlueprintItem.PreviewData(EMPTY, EMPTY, level.dimension(),
                ResourceLocation.fromNamespaceAndPath("villagerretaliation", "walls/" + this.type.tower()), this.type.label(),
                origin, Rotation.NONE, min.subtract(origin), max.subtract(origin), blocks.size(), 0, 0, "", "", "",
                false, false, false, false, blocks);
    }
    private void send(SettlementWallRequestPayload request, Consumer<SettlementWallRequestPayload> send) {
        this.pending = request; this.sentAt = Util.getMillis(); send.accept(request);
    }
    public boolean canConfirm() {
        return active() && !this.confirming && !this.draft.dragging() && this.version == this.draft.version()
                && this.result != null && this.result.revision() == this.revision && this.result.status() == SettlementWallResultPayload.Status.PREVIEW;
    }
    public void confirm(Level level, UUID session, Consumer<SettlementWallRequestPayload> send) {
        if (!canConfirm()) return;
        rememberSettings(); this.hover = null; this.shown = this.draft.wall(); this.cells = this.result.cells();
        this.ghost = ghost(level, this.cells); this.confirming = true;
        send(new SettlementWallRequestPayload(session, this.revision, SettlementWallRequestPayload.Action.CONFIRM, this.result.token(), null), send);
    }
    public boolean accept(SettlementWallResultPayload result) {
        if (!active() || result.revision() != this.revision || this.version != this.draft.version()) return false;
        this.pending = null; this.result = result; this.shown = null; this.feedback = result.message(); this.confirming = false;
        if (result.status() == SettlementWallResultPayload.Status.APPLIED) {
            rememberSettings(); this.draft = null; clearPreview(); return true;
        }
        return false;
    }
    public String hint() {
        if (!active()) return this.feedback;
        if (this.confirming) return "Placing wall...";
        long blocks = this.cells.stream().filter(cell -> !cell.state().isAir() && cell.kind() == SettlementWallTerrain.Kind.PLACE).count();
        return this.type.label() + " / " + blocks + " blocks / Free, instant placement\n"
                + (!this.feedback.isEmpty() ? this.feedback : this.draft.wall().nodes().isEmpty() ? "Click ground to place the first tower."
                : !this.draft.extending() ? "Draft kept. Extend resumes drawing. Confirm places it. Cancel discards."
                : "Click adds a tower. Drag nodes to move them. Click the first node to close. Escape stops extending.");
    }
}
