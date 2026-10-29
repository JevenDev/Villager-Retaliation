package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.network.SettlementInfrastructureRequestPayload;
import com.jvn.villagerretaliation.network.SettlementInfrastructureResultPayload;
import com.jvn.villagerretaliation.settlement.SettlementInfrastructure;
import com.jvn.villagerretaliation.settlement.SettlementInfrastructureTerrain;
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

public final class SettlementInfrastructureEditor {
    private static final UUID EMPTY = new UUID(0, 0);
    private final LongSupplier clock;
    private boolean active;
    private SettlementInfrastructure.Type type;
    private boolean selecting;
    private boolean confirming;
    private int dragging = -1;
    private BlockPos cursor;
    private BlockPos first;
    private BlockPos second;
    private long revision;
    private long changedAt;
    private long sentAt;
    private long previewAt;
    private SettlementInfrastructure shown;
    private SettlementInfrastructureRequestPayload pending;
    private SettlementInfrastructureResultPayload result;
    private List<SettlementInfrastructureResultPayload.Cell> cells = List.of();
    private String feedback = "";
    private List<SettlementInfrastructureResultPayload.Cell> ghostCells = List.of();
    private com.jvn.villagerretaliation.item.ConstructionBlueprintItem.PreviewData ghost;

    public SettlementInfrastructureEditor() { this(Util::getMillis); }
    public SettlementInfrastructureEditor(LongSupplier clock) { this.clock = clock; }
    public boolean active() { return this.active; }
    public boolean confirming() { return this.confirming; }
    public boolean dragging() { return this.dragging >= 0; }
    public BlockPos cursor() { return this.cursor; }
    public BlockPos first() { return this.first; }
    public BlockPos second() { return this.second; }
    public List<SettlementInfrastructureResultPayload.Cell> cells() { return this.cells; }

    public void begin(SettlementInfrastructure.Type type) {
        this.type = type;
        this.active = true;
        this.selecting = true;
        this.confirming = false;
        this.cursor = null;
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
        if (!active() || confirming()) return;
        this.cursor = pos;
        if (this.selecting && this.first != null && pos != null && !pos.equals(this.second)) {
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

    public void cancel(UUID session, Consumer<SettlementInfrastructureRequestPayload> send) {
        if (!active() || confirming()) return;
        this.active = false;
        this.cursor = null;
        this.cells = List.of();
        this.result = null;
        this.dragging = -1;
        send(new SettlementInfrastructureRequestPayload(session, ++this.revision, SettlementInfrastructureRequestPayload.Action.CANCEL, EMPTY, null), send);
    }

    public void tick(Level level, UUID session, Predicate<BlockPos> allowed, Consumer<SettlementInfrastructureRequestPayload> send) {
        long now = this.clock.getAsLong();
        if (active() && !confirming() && this.first != null && this.second != null) {
            SettlementInfrastructure infrastructure;
            try { infrastructure = new SettlementInfrastructure(this.type, this.first, this.second); }
            catch (IllegalArgumentException limit) {
                this.feedback = limit.getMessage();
                this.cells = List.of();
                return;
            }
            if (!infrastructure.equals(this.shown) || now - this.previewAt >= 250) {
                this.shown = infrastructure;
                this.previewAt = now;
                var preview = SettlementInfrastructureTerrain.preview(level, infrastructure, allowed);
                var cells = preview.cells().stream().map(cell -> new SettlementInfrastructureResultPayload.Cell(cell.position(), cell.target(), cell.kind())).toList();
                var displayed = this.result != null && !this.result.cells().isEmpty() ? this.result.cells() : cells;
                if (!this.cells.equals(displayed)) this.cells = displayed;
                this.feedback = this.result != null ? this.result.message()
                        : !preview.error().isEmpty() ? preview.error() : preview.materials();
            }
            if (!this.selecting && !dragging() && this.result == null && this.pending == null && now - this.changedAt >= 150)
                send(new SettlementInfrastructureRequestPayload(session, this.revision, SettlementInfrastructureRequestPayload.Action.PREVIEW, EMPTY, infrastructure), send);
        }
        if (this.pending != null && now - this.sentAt >= 2000) send(this.pending, send);
    }

    public boolean canConfirm() {
        return active() && !confirming() && !this.selecting && !dragging() && this.result != null
                && this.result.revision() == this.revision && this.result.status() == SettlementInfrastructureResultPayload.Status.PREVIEW;
    }

    public void confirm(UUID session, Consumer<SettlementInfrastructureRequestPayload> send) {
        if (!canConfirm()) return;
        this.confirming = true;
        send(new SettlementInfrastructureRequestPayload(session, this.revision, SettlementInfrastructureRequestPayload.Action.CONFIRM, this.result.token(), null), send);
    }

    private void send(SettlementInfrastructureRequestPayload request, Consumer<SettlementInfrastructureRequestPayload> send) {
        this.pending = request;
        this.sentAt = this.clock.getAsLong();
        send.accept(request);
    }

    public boolean accept(SettlementInfrastructureResultPayload response) {
        if (!active() || response.revision() != this.revision) return false;
        this.pending = null;
        this.result = response;
        this.confirming = false;
        this.feedback = response.message();
        if (!response.cells().isEmpty()) this.cells = response.cells();
        if (response.status() == SettlementInfrastructureResultPayload.Status.APPLIED) {
            this.active = false;
            this.cells = List.of();
            return true;
        }
        return false;
    }

    public com.jvn.villagerretaliation.item.ConstructionBlueprintItem.PreviewData ghost(Level level) {
        if (this.ghostCells.equals(this.cells)) return this.ghost;
        this.ghostCells = this.cells;
        var output = this.cells.stream().filter(cell -> cell.kind() == SettlementInfrastructureTerrain.Kind.PLACE).toList();
        if (output.isEmpty()) return this.ghost = null;
        var origin = output.getFirst().position();
        var min = origin;
        var max = origin;
        for (var cell : output) {
            var pos = cell.position();
            min = new BlockPos(Math.min(min.getX(), pos.getX()), Math.min(min.getY(), pos.getY()), Math.min(min.getZ(), pos.getZ()));
            max = new BlockPos(Math.max(max.getX(), pos.getX()), Math.max(max.getY(), pos.getY()), Math.max(max.getZ(), pos.getZ()));
        }
        var blocks = output.stream().map(cell -> new com.jvn.villagerretaliation.item.ConstructionBlueprintItem.PreviewBlock(
                cell.position().subtract(origin), cell.state())).toList();
        return this.ghost = new com.jvn.villagerretaliation.item.ConstructionBlueprintItem.PreviewData(EMPTY, EMPTY, level.dimension(),
                net.minecraft.resources.ResourceLocation.parse("villagerretaliation:infrastructure"), this.type.label(), origin,
                net.minecraft.world.level.block.Rotation.NONE, min.subtract(origin), max.subtract(origin), blocks.size(), 0, 0, "", "", "",
                false, false, false, false, blocks);
    }

    public String hint() {
        if (confirming()) return "Queueing construction...";
        String points = this.type == SettlementInfrastructure.Type.BRIDGE || this.type == SettlementInfrastructure.Type.STAIRS
                ? "endpoint" : "corner";
        if (this.first == null) return this.type.label() + ": move over ground and click the first " + points + ".\nWASD/arrows pan, Q/E rotate.";
        return this.type.label() + " / " + this.cells.stream().filter(cell -> cell.kind() != SettlementInfrastructureTerrain.Kind.EXISTING).count()
                + " blocks\n" + (this.selecting ? "Click the second " + points + ". " : "Drag either handle to adjust. ")
                + (this.type == SettlementInfrastructure.Type.LEVEL || this.type == SettlementInfrastructure.Type.FILL
                        ? "Target height: " + this.first.getY() + ". " : "") + this.feedback;
    }
}
