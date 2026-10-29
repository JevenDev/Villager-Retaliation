package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.network.SettlementInfrastructureRequestPayload;
import com.jvn.villagerretaliation.network.SettlementInfrastructureResultPayload;
import com.jvn.villagerretaliation.network.SettlementInfrastructureResultPayload.Status;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

public final class SettlementInfrastructureSession {
    private long revision;
    private UUID token = new UUID(0, 0);
    private SettlementInfrastructure infrastructure;
    private SettlementInfrastructureTerrain.Preview preview;
    private SettlementInfrastructureResultPayload result;

    public SettlementInfrastructureResultPayload request(ServerPlayer player, SettlementRecord town, UUID session, boolean permitted,
                                               Predicate<BlockPos> allowed, SettlementInfrastructureRequestPayload request) {
        if (!session.equals(request.session()) || !permitted)
            return result(request, Status.REJECTED, List.of(), "Construction placement is no longer permitted");
        if (request.revision() < this.revision)
            return result(request, Status.REJECTED, List.of(), "Construction draft is no longer current");
        if (request.action() == SettlementInfrastructureRequestPayload.Action.CANCEL) {
            this.revision = request.revision();
            this.infrastructure = null;
            this.preview = null;
            return this.result = result(request, Status.CANCELLED, List.of(), "");
        }
        if (request.action() == SettlementInfrastructureRequestPayload.Action.PREVIEW) {
            if (request.revision() == this.revision) {
                if (this.result != null && java.util.Objects.equals(this.infrastructure, request.infrastructure())) return this.result;
                return result(request, Status.REJECTED, List.of(), "Use a new revision for changed construction");
            }
            this.revision = request.revision();
            this.infrastructure = request.infrastructure();
            return refresh(player, town, allowed, request, "");
        }
        if (request.revision() != this.revision || this.result == null || !this.token.equals(request.token()))
            return result(request, Status.REJECTED, List.of(), "Review the construction preview before confirming");
        if (this.result.status() == Status.APPLIED) return this.result;
        if (this.preview == null || !this.preview.error().isEmpty())
            return result(request, Status.REJECTED, List.of(), "The construction preview is not valid");
        var current = SettlementInfrastructureTerrain.preview(player.serverLevel(), this.infrastructure, allowed);
        if (!this.preview.equals(current)) return refresh(player, town, allowed, request, "Terrain changed. Review the refreshed plan and confirm again. ");
        String error = SettlementInfrastructureTerrain.order(this.infrastructure, current, this.token).commission(player, town, this.token);
        if (!error.isEmpty()) return refresh(player, town, allowed, request, error + ". ");
        this.preview = null;
        return this.result = result(request, Status.APPLIED, List.of(), "Construction queued. Add builders from the project Staff panel");
    }

    private SettlementInfrastructureResultPayload refresh(ServerPlayer player, SettlementRecord town, Predicate<BlockPos> allowed,
                                                SettlementInfrastructureRequestPayload request, String message) {
        this.preview = SettlementInfrastructureTerrain.preview(player.serverLevel(), this.infrastructure, allowed);
        this.token = UUID.randomUUID();
        String detail = !this.preview.error().isEmpty() ? this.preview.error() : this.preview.materials();
        String feedback = message + detail;
        this.result = result(request, this.preview.error().isEmpty() ? Status.PREVIEW : Status.REJECTED,
                this.preview.cells().stream().map(cell -> new SettlementInfrastructureResultPayload.Cell(cell.position(), cell.target(), cell.kind())).toList(),
                feedback.substring(0, Math.min(512, feedback.length())));
        return this.result;
    }

    private SettlementInfrastructureResultPayload result(SettlementInfrastructureRequestPayload request, Status status,
                                               List<SettlementInfrastructureResultPayload.Cell> cells, String message) {
        return new SettlementInfrastructureResultPayload(request.session(), request.revision(), this.token, status, cells, message);
    }
}
