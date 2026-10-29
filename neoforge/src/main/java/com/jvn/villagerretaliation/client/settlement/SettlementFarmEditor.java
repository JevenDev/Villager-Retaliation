package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.network.SettlementFarmRequestPayload;
import com.jvn.villagerretaliation.network.SettlementFarmResultPayload;
import com.jvn.villagerretaliation.settlement.SettlementFarm;
import com.jvn.villagerretaliation.settlement.SettlementFarmTerrain;
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
    private BlockPos first;
    private BlockPos second;
    private long revision;
    private long changedAt;
    private long sentAt;
    private long previewAt;
    private SettlementFarm shown;
    private SettlementFarmRequestPayload pending;
    private SettlementFarmResultPayload result;
    private List<SettlementFarmResultPayload.Cell> cells = List.of();
    private String feedback = "";

    public SettlementFarmEditor() { this(Util::getMillis); }
    public SettlementFarmEditor(LongSupplier clock) { this.clock = clock; }
    public boolean active() { return this.active; }
    public boolean confirming() { return this.confirming; }
    public boolean dragging() { return this.dragging >= 0; }
    public BlockPos first() { return this.first; }
    public BlockPos second() { return this.second; }
    public List<SettlementFarmResultPayload.Cell> cells() { return this.cells; }

    public void begin() {
        this.active = true;
        this.selecting = true;
        this.confirming = false;
        this.first = null;
        this.second = null;
        this.dragging = -1;
        this.cells = List.of();
        changed();
    }

    public void click(BlockPos pos, Function<Vec3, double[]> project, double x, double y) {
        if (!active() || confirming() || pos == null) return;
        if (this.first == null) {
            this.first = pos;
            this.second = pos;
        } else if (this.selecting) {
            this.second = pos;
            this.selecting = false;
        } else {
            var a = project.apply(Vec3.atCenterOf(this.first).add(0, 0.5, 0));
            var b = project.apply(Vec3.atCenterOf(this.second).add(0, 0.5, 0));
            double da = a == null ? Double.MAX_VALUE : Math.hypot(a[0] - x, a[1] - y);
            double db = b == null ? Double.MAX_VALUE : Math.hypot(b[0] - x, b[1] - y);
            this.dragging = da < db ? 0 : 1;
            drag(pos);
        }
        changed();
    }

    public void hover(BlockPos pos) {
        if (active() && !confirming() && this.selecting && this.first != null && pos != null && !pos.equals(this.second)) {
            this.second = pos;
            changed();
        }
    }

    public void drag(BlockPos pos) {
        if (!dragging() || confirming() || pos == null) return;
        if (this.dragging == 0) this.first = pos;
        else this.second = pos;
        changed();
    }

    public void release() { this.dragging = -1; this.changedAt = this.clock.getAsLong(); }
    public void refresh() { if (active() && !confirming()) changed(); }

    private void changed() {
        this.revision++;
        this.changedAt = this.clock.getAsLong();
        this.shown = null;
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
        if (active() && !confirming() && this.first != null && this.second != null) {
            SettlementFarm farm;
            try { farm = new SettlementFarm(this.first, this.second); }
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
                send(new SettlementFarmRequestPayload(session, this.revision, SettlementFarmRequestPayload.Action.PREVIEW, EMPTY, farm), send);
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

    public String hint() {
        if (confirming()) return "Assigning farmland work...";
        if (this.first == null) return "Farmland: click the first corner of the area.";
        long till = this.cells.stream().filter(cell -> cell.kind() == SettlementFarmTerrain.Kind.TILL).count();
        long water = this.cells.stream().filter(cell -> cell.kind() == SettlementFarmTerrain.Kind.WATER).count();
        long skipped = this.cells.stream().filter(cell -> cell.kind() == SettlementFarmTerrain.Kind.SKIPPED).count();
        return "Farmland / " + till + " soil / " + water + " water / " + skipped + " skipped\n"
                + (this.selecting ? "Move to resize, then click the opposite corner. " : "Drag either corner to resize. ")
                + this.feedback;
    }
}
