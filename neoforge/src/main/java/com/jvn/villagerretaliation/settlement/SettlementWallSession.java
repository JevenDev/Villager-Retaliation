package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.network.SettlementWallRequestPayload;
import com.jvn.villagerretaliation.network.SettlementWallResultPayload;
import com.jvn.villagerretaliation.network.SettlementWallResultPayload.Status;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class SettlementWallSession {
    private long revision;
    private UUID token = new UUID(0, 0);
    private SettlementWall wall;
    private SettlementWallTerrain.Preview preview;
    private SettlementWallResultPayload result;

    public SettlementWallResultPayload request(Level level, UUID session, boolean permitted, Predicate<BlockPos> allowed,
                                               SettlementWallRequestPayload request) {
        if (!session.equals(request.session()) || !permitted)
            return result(request, Status.REJECTED, List.of(), "Wall placement is no longer permitted");
        if (request.revision() < this.revision)
            return result(request, Status.REJECTED, List.of(), "Wall draft is no longer current");
        if (request.action() == SettlementWallRequestPayload.Action.CANCEL) {
            this.revision = request.revision();
            this.wall = null;
            this.preview = null;
            this.token = UUID.randomUUID();
            this.result = result(request, Status.CANCELLED, List.of(), "");
            return this.result;
        }
        if (request.action() == SettlementWallRequestPayload.Action.PREVIEW) {
            if (request.revision() == this.revision) {
                if (this.result != null && java.util.Objects.equals(this.wall, request.wall())) return this.result;
                return result(request, Status.REJECTED, List.of(), "Use a new revision for changed wall settings");
            }
            this.revision = request.revision();
            this.wall = request.wall();
            return refresh(level, allowed, request, "");
        }
        if (request.revision() != this.revision || this.result == null || !this.token.equals(request.token()))
            return result(request, Status.REJECTED, List.of(), "Review the wall before confirming");
        if (this.result.status() == Status.APPLIED) return this.result;
        if (this.preview == null || !this.preview.error().isEmpty())
            return result(request, Status.REJECTED, List.of(), "The wall preview is not valid");
        var current = SettlementWallTerrain.preview(level, this.wall, allowed);
        if (!this.preview.equals(current)) return refresh(level, allowed, request, "Terrain changed. Review the wall and confirm again");
        int count = SettlementWallTerrain.apply(level, current);
        this.preview = null;
        this.wall = null;
        this.result = result(request, Status.APPLIED, List.of(), count + " wall blocks placed");
        return this.result;
    }

    private SettlementWallResultPayload refresh(Level level, Predicate<BlockPos> allowed, SettlementWallRequestPayload request, String message) {
        this.preview = SettlementWallTerrain.preview(level, this.wall, allowed);
        this.token = UUID.randomUUID();
        this.result = result(request, this.preview.error().isEmpty() ? Status.PREVIEW : Status.REJECTED,
                this.preview.cells().stream().map(cell -> new SettlementWallResultPayload.Cell(cell.position(), cell.target(), cell.kind())).toList(),
                this.preview.error().isEmpty() ? message : this.preview.error());
        return this.result;
    }
    private SettlementWallResultPayload result(SettlementWallRequestPayload request, Status status,
                                               List<SettlementWallResultPayload.Cell> cells, String message) {
        return new SettlementWallResultPayload(request.session(), request.revision(), this.token, status, cells, message);
    }
}
