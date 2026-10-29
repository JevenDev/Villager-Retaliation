package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.network.SettlementPathRequestPayload;
import com.jvn.villagerretaliation.network.SettlementPathResultPayload;
import com.jvn.villagerretaliation.network.SettlementPathResultPayload.Status;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class SettlementPathSession {
    private long revision;
    private UUID token = new UUID(0, 0);
    private SettlementPath path;
    private SettlementPathTerrain.Preview preview;
    private SettlementPathResultPayload result;

    public SettlementPathResultPayload request(Level level, UUID session, boolean permitted, Predicate<BlockPos> allowed,
                                               SettlementPathRequestPayload request) {
        return request(level, session, permitted, allowed, request, null, null);
    }

    public SettlementPathResultPayload request(Level level, UUID session, boolean permitted, Predicate<BlockPos> allowed,
                                               SettlementPathRequestPayload request, SettlementRecord town,
                                               net.minecraft.server.level.ServerPlayer player) {
        if (!session.equals(request.session()) || !permitted)
            return result(request, Status.REJECTED, List.of(), "Path placement is no longer permitted");
        if (request.revision() < this.revision)
            return result(request, Status.REJECTED, List.of(), "Path draft is no longer current");
        if (request.action() == SettlementPathRequestPayload.Action.CANCEL) {
            this.revision = request.revision();
            this.path = null;
            this.preview = null;
            this.result = result(request, Status.CANCELLED, List.of(), "");
            return this.result;
        }
        if (request.action() == SettlementPathRequestPayload.Action.PREVIEW) {
            if (request.revision() == this.revision) {
                if (this.result != null && java.util.Objects.equals(this.path, request.path())) return this.result;
                return result(request, Status.REJECTED, List.of(), "Use a new revision for changed path settings");
            }
            this.revision = request.revision();
            this.path = request.path();
            return refresh(level, allowed, request, "");
        }
        if (request.revision() != this.revision || this.result == null || !this.token.equals(request.token()))
            return result(request, Status.REJECTED, List.of(), "Review the path preview before confirming");
        if (this.result.status() == Status.APPLIED) return this.result;
        if (this.preview == null || !this.preview.error().isEmpty())
            return result(request, Status.REJECTED, List.of(), "The path preview is not valid");
        var current = SettlementPathTerrain.preview(level, this.path, allowed);
        if (!this.preview.equals(current)) return refresh(level, allowed, request, "Terrain changed. Review the refreshed preview and confirm again");
        if (player == null || town == null || player.serverLevel() != level)
            return result(request, Status.REJECTED, List.of(), "Path construction requires a settlement and its builder crew");
        String error = SettlementPathTerrain.order(current).commission(player, town, this.token);
        if (!error.isEmpty()) return refresh(level, allowed, request, error);
        this.preview = null;
        this.path = null;
        this.result = result(request, Status.APPLIED, List.of(), "Path construction queued. Builders need shovels. Cancelling keeps finished work");
        return this.result;
    }

    private SettlementPathResultPayload refresh(Level level, Predicate<BlockPos> allowed, SettlementPathRequestPayload request, String message) {
        this.preview = SettlementPathTerrain.preview(level, this.path, allowed);
        this.token = UUID.randomUUID();
        if (message.isEmpty()) message = "Confirm queues builder work. Shovels required, no construction materials";
        this.result = result(request, this.preview.error().isEmpty() ? Status.PREVIEW : Status.REJECTED,
                this.preview.cells().stream().map(cell -> new SettlementPathResultPayload.Cell(cell.position(), cell.kind())).toList(),
                this.preview.error().isEmpty() ? message : this.preview.error());
        return this.result;
    }

    private SettlementPathResultPayload result(SettlementPathRequestPayload request, Status status,
                                               List<SettlementPathResultPayload.Cell> cells, String message) {
        return new SettlementPathResultPayload(request.session(), request.revision(), this.token, status, cells, message);
    }
}
