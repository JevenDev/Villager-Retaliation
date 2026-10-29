package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.network.SettlementFarmRequestPayload;
import com.jvn.villagerretaliation.network.SettlementFarmResultPayload;
import com.jvn.villagerretaliation.network.SettlementFarmResultPayload.Status;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

public final class SettlementFarmSession {
    private long revision;
    private UUID token = new UUID(0, 0);
    private SettlementFarm farm;
    private SettlementFarmTerrain.Preview preview;
    private SettlementFarmResultPayload result;

    public SettlementFarmResultPayload request(ServerPlayer player, SettlementRecord town, UUID session, boolean permitted,
                                               Predicate<BlockPos> allowed, SettlementFarmRequestPayload request) {
        if (!session.equals(request.session()) || !permitted)
            return result(request, Status.REJECTED, List.of(), "Farmland placement is no longer permitted");
        if (request.revision() < this.revision)
            return result(request, Status.REJECTED, List.of(), "Farmland draft is no longer current");
        if (request.action() == SettlementFarmRequestPayload.Action.CANCEL) {
            this.revision = request.revision();
            this.farm = null;
            this.preview = null;
            return this.result = result(request, Status.CANCELLED, List.of(), "");
        }
        if (request.action() == SettlementFarmRequestPayload.Action.PREVIEW) {
            if (request.revision() == this.revision) {
                if (this.result != null && java.util.Objects.equals(this.farm, request.farm())) return this.result;
                return result(request, Status.REJECTED, List.of(), "Use a new revision for changed farmland");
            }
            this.revision = request.revision();
            this.farm = request.farm();
            return refresh(player, town, allowed, request, "");
        }
        if (request.revision() != this.revision || this.result == null || !this.token.equals(request.token()))
            return result(request, Status.REJECTED, List.of(), "Review the farmland preview before confirming");
        if (this.result.status() == Status.APPLIED) return this.result;
        if (this.preview == null || !this.preview.error().isEmpty())
            return result(request, Status.REJECTED, List.of(), "The farmland preview is not valid");
        var current = SettlementFarmTerrain.preview(player.serverLevel(), this.farm, allowed);
        if (!this.preview.equals(current)) return refresh(player, town, allowed, request, "Terrain changed. Review the refreshed irrigation and confirm again. ");
        String error = SettlementFarmWork.start(player, town, current, this.farm);
        if (!error.isEmpty()) return refresh(player, town, allowed, request, error + ". ");
        this.preview = null;
        return this.result = result(request, Status.APPLIED, List.of(), "Farmer assigned to prepare irrigated farmland");
    }

    private SettlementFarmResultPayload refresh(ServerPlayer player, SettlementRecord town, Predicate<BlockPos> allowed,
                                                SettlementFarmRequestPayload request, String message) {
        this.preview = SettlementFarmTerrain.preview(player.serverLevel(), this.farm, allowed);
        this.token = UUID.randomUUID();
        var assignment = this.preview.error().isEmpty() ? SettlementFarmWork.worker(player.serverLevel(), town, this.preview) : null;
        String detail = !this.preview.error().isEmpty() ? this.preview.error()
                : assignment.worker() == null ? assignment.error() : "Farmer: " + assignment.worker().getName().getString() + ". " + this.preview.materials();
        String feedback = message + detail;
        this.result = result(request, this.preview.error().isEmpty() ? Status.PREVIEW : Status.REJECTED,
                this.preview.cells().stream().map(cell -> new SettlementFarmResultPayload.Cell(cell.position(), cell.kind())).toList(),
                feedback.substring(0, Math.min(512, feedback.length())));
        return this.result;
    }

    private SettlementFarmResultPayload result(SettlementFarmRequestPayload request, Status status,
                                               List<SettlementFarmResultPayload.Cell> cells, String message) {
        return new SettlementFarmResultPayload(request.session(), request.revision(), this.token, status, cells, message);
    }
}
