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
    private SettlementWallsSavedData.Entry upgrade;
    private SettlementWallUpgrade upgradePlan;
    private boolean demolishing;
    private boolean repairing;
    private SettlementWallTerrain.Preview preview;
    private SettlementWallResultPayload result;

    public SettlementWallResultPayload request(Level level, UUID session, boolean permitted, Predicate<BlockPos> allowed,
                                               SettlementWallRequestPayload request) {
        return request(level, session, permitted, allowed, request, null);
    }

    public SettlementWallResultPayload request(Level level, UUID session, boolean permitted, Predicate<BlockPos> allowed,
                                               SettlementWallRequestPayload request, com.jvn.villagerretaliation.allegiance.VillageAllegianceId town) {
        return request(level, session, permitted, allowed, request, town, null);
    }

    public SettlementWallResultPayload request(Level level, UUID session, boolean permitted, Predicate<BlockPos> allowed,
                                               SettlementWallRequestPayload request, com.jvn.villagerretaliation.allegiance.VillageAllegianceId town,
                                               net.minecraft.server.level.ServerPlayer player) {
        if (!session.equals(request.session()) || !permitted)
            return result(request, Status.REJECTED, List.of(), "Wall placement is no longer permitted");
        if (town != null && level instanceof net.minecraft.server.level.ServerLevel server && SettlementWallsSavedData.get(server).readOnly())
            return result(request, Status.REJECTED, List.of(), "Wall records could not be read. Restore the saved wall data before changing walls");
        if (request.revision() < this.revision)
            return result(request, Status.REJECTED, List.of(), "Wall draft is no longer current");
        if (request.action() == SettlementWallRequestPayload.Action.SELECT_UPGRADE || request.action() == SettlementWallRequestPayload.Action.SELECT_DESTROY
                || request.action() == SettlementWallRequestPayload.Action.SELECT_REPAIR) {
            if (request.revision() == this.revision && this.result != null && this.result.status() == Status.SELECTED) return this.result;
            if (request.revision() <= this.revision || town == null || !(level instanceof net.minecraft.server.level.ServerLevel server)
                    || !allowed.test(request.pick()) || !level.hasChunkAt(request.pick()))
                return result(request, Status.REJECTED, List.of(), "Select a wall inside this settlement");
            this.revision = request.revision();
            this.demolishing = request.action() == SettlementWallRequestPayload.Action.SELECT_DESTROY;
            this.repairing = request.action() == SettlementWallRequestPayload.Action.SELECT_REPAIR;
            this.upgrade = SettlementWallsSavedData.get(server).at(server, town, request.pick());
            this.wall = null; this.preview = null; this.upgradePlan = null; this.token = UUID.randomUUID();
            if (this.upgrade == null) return this.result = result(request, Status.REJECTED, List.of(), "No recorded wall here. Only walls placed after the upgrade update can be selected");
            return this.result = new SettlementWallResultPayload(request.session(), request.revision(), this.token, Status.SELECTED,
                    this.upgrade.blocks().entrySet().stream().map(entry -> new SettlementWallResultPayload.Cell(entry.getKey(),
                            entry.getValue(), SettlementWallTerrain.Kind.EXISTING)).toList(),
                    this.demolishing ? "Review this wall piece before destroying it" : "Choose a type for this " + this.upgrade.wall().part().name().toLowerCase(java.util.Locale.ROOT), this.upgrade.id(), this.upgrade.wall());
        }
        if (request.action() == SettlementWallRequestPayload.Action.CANCEL) {
            this.upgrade = null; this.upgradePlan = null; this.demolishing = false; this.repairing = false;
            this.revision = request.revision();
            this.wall = null;
            this.preview = null;
            this.token = UUID.randomUUID();
            this.result = result(request, Status.CANCELLED, List.of(), "");
            return this.result;
        }
        if (request.action() == SettlementWallRequestPayload.Action.PREVIEW || request.action() == SettlementWallRequestPayload.Action.PREVIEW_DESTROY) {
            if (request.revision() == this.revision) {
                if (this.result != null && request.upgrade().equals(this.upgrade == null ? SettlementWallRequestPayload.NONE : this.upgrade.id())
                        && (this.demolishing && request.action() == SettlementWallRequestPayload.Action.PREVIEW_DESTROY
                        || !this.demolishing && request.action() == SettlementWallRequestPayload.Action.PREVIEW && java.util.Objects.equals(this.wall, request.wall()))) return this.result;
                return result(request, Status.REJECTED, List.of(), "Use a new revision for changed wall settings");
            }
            if ((request.action() == SettlementWallRequestPayload.Action.PREVIEW_DESTROY) != this.demolishing && !request.upgrade().equals(SettlementWallRequestPayload.NONE)
                    || request.action() == SettlementWallRequestPayload.Action.PREVIEW_DESTROY && this.upgrade == null)
                return result(request, Status.REJECTED, List.of(), "Select a wall piece to destroy");
            if (request.upgrade().equals(SettlementWallRequestPayload.NONE)) {
                if (request.action() == SettlementWallRequestPayload.Action.PREVIEW_DESTROY)
                    return result(request, Status.REJECTED, List.of(), "Select a wall piece to destroy");
                if (request.wall().part() != SettlementWall.Part.COMPLETE)
                    return result(request, Status.REJECTED, List.of(), "Select an existing wall before editing a piece");
                this.upgrade = null; this.upgradePlan = null; this.demolishing = false; this.repairing = false;
            }
            else if (this.upgrade == null || !this.upgrade.id().equals(request.upgrade())
                    || !(level instanceof net.minecraft.server.level.ServerLevel server)
                    || !this.upgrade.equals(SettlementWallsSavedData.get(server).get(this.upgrade.id())))
                return result(request, Status.REJECTED, List.of(), "Select the wall again before upgrading");
            if (this.upgrade != null && !this.demolishing && (!this.upgrade.wall().nodes().equals(request.wall().nodes())
                    || this.upgrade.wall().part() != request.wall().part() || this.upgrade.wall().closed() != request.wall().closed()))
                return result(request, Status.REJECTED, List.of(), "Upgrades must preserve the placed wall route");
            if (this.upgrade != null && !this.demolishing && this.wallSpanJunctionsChanged(request.wall()))
                return result(request, Status.REJECTED, List.of(), "Select the span again before changing its junctions");
            this.revision = request.revision();
            this.wall = this.demolishing ? this.upgrade.wall() : request.wall();
            this.upgradePlan = this.upgrade != null && !this.demolishing && !this.repairing
                    ? new SettlementWallUpgrade(SettlementWallsSavedData.get((net.minecraft.server.level.ServerLevel) level), this.upgrade, this.wall) : null;
            return refresh(level, allowed, request, "", town);
        }
        if (request.revision() != this.revision || this.result == null || !this.token.equals(request.token()))
            return result(request, Status.REJECTED, List.of(), "Review the wall before confirming");
        if (this.result.status() == Status.APPLIED) return this.result;
        if (this.preview == null || !this.preview.error().isEmpty())
            return result(request, Status.REJECTED, List.of(), "The wall preview is not valid");
        if (this.upgrade != null && (!(level instanceof net.minecraft.server.level.ServerLevel server)
                || !this.upgrade.equals(SettlementWallsSavedData.get(server).get(this.upgrade.id()))
                || this.upgradePlan != null && !this.upgradePlan.current(SettlementWallsSavedData.get(server))))
            return result(request, Status.REJECTED, List.of(), "Another upgrade changed this wall. Select it again");
        var current = terrain(level, allowed);
        if (!this.preview.equals(current)) return refresh(level, allowed, request, "Terrain changed. Review the wall and confirm again", town);
        if (player != null && town != null && this.upgrade != null) {
            if (current.cells().stream().noneMatch(cell -> cell.kind() == SettlementWallTerrain.Kind.PLACE))
                return refresh(level, allowed, request, "No changes are needed", town);
            var settlement = SettlementSavedData.get(player.serverLevel()).get(town).orElse(null);
            var originals = this.upgradePlan == null ? List.of(this.upgrade) : this.upgradePlan.originals();
            var replacements = this.demolishing ? List.<SettlementWallsSavedData.Entry>of()
                    : this.repairing ? List.of(this.upgrade) : this.upgradePlan.replacements(current);
            var cost = this.demolishing ? SettlementWallMaterials.Cost.ZERO
                    : this.repairing ? SettlementWallMaterials.cost(this.upgrade.wall(), current, null) : this.upgradePlan.cost(current);
            var order = SettlementConstructionOrder.wall(this.demolishing ? "Wall demolition" : this.repairing ? "Wall repair" : "Wall upgrade",
                    current, cost, originals, replacements);
            String error = order.commission(player, settlement, this.token);
            if (!error.isEmpty()) return refresh(level, allowed, request, error, town);
            this.preview = null;
            return this.result = result(request, Status.APPLIED, List.of(), "Wall work queued. Cancelling keeps finished work and refunds unspent supplies");
        }
        if (player != null && town != null && this.upgrade == null && !this.demolishing) {
            var settlement = SettlementSavedData.get(player.serverLevel()).get(town).orElse(null);
            String error = SettlementWallConstruction.commission(player, settlement, this.token, this.wall, current);
            if (!error.isEmpty()) return refresh(level, allowed, request, error, town);
            this.preview = null;
            this.wall = null;
            return this.result = result(request, Status.APPLIED, List.of(), "Wall construction queued. Add builders from the project's Staff panel to work faster");
        }
        if (!SettlementWallTerrain.entitiesClear(level, current))
            return result(request, Status.REJECTED, List.of(), "Move entities out of the wall before placing it instantly");
        if (!this.demolishing && town != null && level instanceof net.minecraft.server.level.ServerLevel server) {
            var payment = payment(server, town, session);
            if (!payment.pay()) return refresh(level, allowed, request, "", town);
        }
        int count = SettlementWallTerrain.apply(level, current);
        if (town != null && level instanceof net.minecraft.server.level.ServerLevel server) {
            if (this.demolishing) SettlementWallsSavedData.get(server).remove(this.upgrade.id());
            else if (this.upgradePlan != null) this.upgradePlan.record(server, current);
            else SettlementWallsSavedData.get(server).record(this.upgrade == null ? UUID.randomUUID() : this.upgrade.id(), town, server, this.wall, current);
        }
        this.preview = null;
        this.wall = null;
        this.result = result(request, Status.APPLIED, List.of(), this.demolishing ? "Wall piece destroyed" : count + " wall blocks placed");
        return this.result;
    }

    private boolean wallSpanJunctionsChanged(SettlementWall replacement) {
        var original = this.upgrade.wall();
        return original.part() == SettlementWall.Part.SPAN && original.nodes().stream()
                .anyMatch(node -> !original.towerAt(node).equals(replacement.towerAt(node)));
    }

    private SettlementWallTerrain.Preview terrain(Level level, Predicate<BlockPos> allowed) {
        if (this.repairing && this.upgrade != null) return SettlementWallTerrain.repair(level, this.upgrade.blocks(), allowed);
        if (this.demolishing) return SettlementWallTerrain.demolition(level, this.upgrade.blocks(), allowed);
        if (this.upgradePlan != null) return this.upgradePlan.preview((net.minecraft.server.level.ServerLevel) level, allowed);
        return SettlementWallTerrain.preview(level, this.wall, allowed);
    }

    private SettlementWallMaterials.Payment payment(net.minecraft.server.level.ServerLevel level,
                                                     com.jvn.villagerretaliation.allegiance.VillageAllegianceId town, UUID consumer) {
        return SettlementWallMaterials.prepare(level, SettlementSavedData.get(level).get(town).orElse(null), consumer,
                this.upgradePlan == null ? SettlementWallMaterials.cost(this.repairing ? this.upgrade.wall() : this.wall, this.preview, this.repairing ? null : this.upgrade) : this.upgradePlan.cost(this.preview));
    }

    private SettlementWallResultPayload refresh(Level level, Predicate<BlockPos> allowed, SettlementWallRequestPayload request, String message,
                                                 com.jvn.villagerretaliation.allegiance.VillageAllegianceId town) {
        this.preview = terrain(level, allowed);
        if (this.preview.error().isEmpty() && !this.demolishing && town != null && level instanceof net.minecraft.server.level.ServerLevel server) {
            var materials = payment(server, town, request.session()).message();
            message = message.isEmpty() ? materials : message + "\n" + materials;
        }
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
