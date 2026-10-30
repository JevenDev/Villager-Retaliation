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
    private SettlementArea previous;
    private SettlementCropPlan crops = SettlementCropPlan.NONE;
    private UUID areaId;
    private boolean acceptedDraft;
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
                if (this.result != null && java.util.Objects.equals(this.farm, request.farm())
                        && java.util.Objects.equals(this.areaId, request.area()) && this.crops.equals(request.crops())) return this.result;
                return result(request, Status.REJECTED, List.of(), "Use a new revision for changed farmland");
            }
            this.revision = request.revision();
            this.acceptedDraft = false;
            this.farm = request.farm();
            this.areaId = request.area();
            this.crops = request.crops();
            this.previous = this.areaId == null ? null : town.areas().get(this.areaId);
            if (this.areaId != null && (this.previous == null || this.previous.type() != SettlementAreaType.FARM
                    || !java.util.Objects.equals(this.previous.farm(), request.original())))
                return this.result = result(request, Status.REJECTED, List.of(), "Farm plot changed. Reopen it before editing.");
            this.acceptedDraft = true;
            return refresh(player, town, allowed, request, "");
        }
        if (!this.acceptedDraft) return result(request, Status.REJECTED, List.of(), "Refresh the farmland draft before confirming");
        if (request.revision() != this.revision || this.result == null || !this.token.equals(request.token()))
            return result(request, Status.REJECTED, List.of(), "Review the farmland preview before confirming");
        if (this.result.status() == Status.APPLIED) return this.result;
        if (this.preview == null || !this.preview.error().isEmpty())
            return result(request, Status.REJECTED, List.of(), "The farmland preview is not valid");
        if (this.areaId != null && !java.util.Objects.equals(this.previous, town.areas().get(this.areaId)))
            return this.result = result(request, Status.REJECTED, List.of(), "Farm plot settings changed. Reopen it before confirming.");
        var current = SettlementFarmTerrain.preview(player.serverLevel(), this.farm, allowed);
        if (!this.preview.equals(current)) return refresh(player, town, allowed, request, "Terrain changed. Review the refreshed irrigation and confirm again. ");
        String error = SettlementFarmWork.start(player, town, current, this.farm, this.areaId, this.crops);
        if (!error.isEmpty()) return refresh(player, town, allowed, request, error + ". ");
        this.preview = null;
        var area = SettlementFarmWork.previous(town, current, this.farm, this.areaId);
        return this.result = new SettlementFarmResultPayload(request.session(), request.revision(), this.token, Status.APPLIED, List.of(),
                "Farm preparation requested. Open the selected farm settings for workers and supplies.", area == null ? null : area.id());
    }

    private SettlementFarmResultPayload refresh(ServerPlayer player, SettlementRecord town, Predicate<BlockPos> allowed,
                                                SettlementFarmRequestPayload request, String message) {
        this.preview = SettlementFarmTerrain.preview(player.serverLevel(), this.farm, allowed);
        this.token = UUID.randomUUID();
        String plotError = "";
        if (this.preview.error().isEmpty()) {
            try {
                var area = SettlementFarmWork.plot(this.preview, this.farm,
                        SettlementFarmWork.previous(town, this.preview, this.farm, this.areaId), this.crops);
                if (!town.canPutArea(player.getUUID(), area)) plotError = "Farm plot overlaps another workplace or leaves settlement land";
                else if (town.farmJobs().values().stream().anyMatch(job -> job.unfinished() && job.area().equals(area.id())))
                    plotError = "Cancel the current farm preparation before changing its boundary";
                else if (town.farmJobs().values().stream().filter(SettlementFarmJob::unfinished).count() >= SettlementFarmJob.MAX_JOBS)
                    plotError = "Farm preparation queue is full";
            } catch (IllegalArgumentException exception) {
                plotError = "Farm plot exceeds workplace size or height limits";
            }
        }
        var assignment = this.preview.error().isEmpty() ? SettlementFarmWork.worker(player.serverLevel(), town, this.preview, true, SettlementFarmJob.AUTOMATIC) : null;
        String detail = !this.preview.error().isEmpty() ? this.preview.error()
                : assignment.worker() == null ? assignment.error() : "Farmer: " + assignment.worker().getName().getString() + ". " + this.preview.materials();
        if (!plotError.isEmpty()) detail = plotError;
        if (this.preview.error().isEmpty() && plotError.isEmpty()) {
            long capacity = this.preview.count(SettlementFarmTerrain.Kind.TILL) + this.preview.count(SettlementFarmTerrain.Kind.EXISTING);
            detail = capacity + " planting cells. " + cropRequirements(player, town, assignment.worker()) + " " + detail;
            if (this.previous != null) {
                long released = 0;
                for (int x = this.previous.min().getX(); x <= this.previous.max().getX(); x++)
                    for (int z = this.previous.min().getZ(); z <= this.previous.max().getZ(); z++)
                        if ((this.previous.farm() == null || this.previous.farm().contains(x, z)) && !this.farm.contains(x, z)) released++;
                if (released > 0) detail = "Releasing " + released + " columns. Terrain is kept. " + detail;
            }
        }
        String feedback = message + detail;
        this.result = result(request, this.preview.error().isEmpty() && plotError.isEmpty() && assignment.worker() != null ? Status.PREVIEW : Status.REJECTED,
                this.preview.cells().stream().map(cell -> new SettlementFarmResultPayload.Cell(cell.position(), cell.kind())).toList(),
                feedback.substring(0, Math.min(512, feedback.length())));
        return this.result;
    }

    private String cropRequirements(ServerPlayer player, SettlementRecord town, net.minecraft.world.entity.npc.Villager worker) {
        if (!this.crops.enabled() && !this.crops.demand()) return "Prepare only.";
        if (this.crops.demand()) return "Plant for settlement demand. Keep suitable seeds in supplies.";
        var positions = this.preview.cells().stream()
                .filter(cell -> cell.kind() == SettlementFarmTerrain.Kind.TILL || cell.kind() == SettlementFarmTerrain.Kind.EXISTING)
                .map(SettlementFarmTerrain.Cell::position)
                .sorted(java.util.Comparator.<BlockPos>comparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getY)).toList();
        var supplies = new java.util.ArrayList<net.minecraft.world.item.ItemStack>();
        if (worker != null) {
            var inventory = com.jvn.villagerretaliation.inventory.HiredJobInventory.getJobInventory(worker);
            inventory.supplySlots().forEach(slot -> supplies.add(inventory.getItem(slot)));
            if (com.jvn.villagerretaliation.interaction.HiredJobSetting.USE_STORAGE.read(
                    com.jvn.villagerretaliation.interaction.HiredWorkStateStore.state(worker)) != 0) {
                if (town.duties().containsKey(worker.getUUID()))
                    supplies.addAll(com.jvn.villagerretaliation.inventory.AssignedStorageService.productionSupplies(worker));
                else for (var depot : SettlementStock.supplies(town, com.jvn.villagerretaliation.interaction.HiredVillagerRole.FARMING)) {
                    if (!player.serverLevel().hasChunkAt(depot.position())
                            || SettlementSupplyStock.contents(player.serverLevel(), town, depot.position()) == null) continue;
                    for (int slot = 0; slot < depot.getContainerSize(); slot++) {
                        var stack = depot.getItem(slot);
                        if (stack.isEmpty() || !com.jvn.villagerretaliation.inventory.VillagerItemFilterService.mayWithdraw(worker, stack)) continue;
                        int available = (int) Math.min(stack.getCount(), SettlementLogisticsService.framedAllowance(player.serverLevel(), depot, stack,
                                com.jvn.villagerretaliation.item.VillagerFilterPolicy.TransferOperation.PROVIDE, false));
                        if (available > 0) supplies.add(stack.copyWithCount(available));
                    }
                }
            }
        }
        var requirements = new java.util.ArrayList<String>();
        var counts = new long[com.jvn.villagerretaliation.interaction.work.HiredFarmingOptions.Crop.values().length];
        for (int index = 0; index < positions.size(); index++) counts[this.crops.crop(index, positions.size()).ordinal()]++;
        for (var crop : com.jvn.villagerretaliation.interaction.work.HiredFarmingOptions.Crop.values()) {
            if (crop == com.jvn.villagerretaliation.interaction.work.HiredFarmingOptions.Crop.ANY) continue;
            long required = counts[crop.ordinal()];
            if (required == 0) continue;
            int available = supplies.stream().filter(stack -> stack.is(crop.seed())).mapToInt(net.minecraft.world.item.ItemStack::getCount).sum();
            requirements.add(crop.seed().getDescription().getString() + " " + available + "/" + required);
        }
        return "Seeds available/first planting: " + String.join(", ", requirements) + ".";
    }

    private SettlementFarmResultPayload result(SettlementFarmRequestPayload request, Status status,
                                               List<SettlementFarmResultPayload.Cell> cells, String message) {
        return new SettlementFarmResultPayload(request.session(), request.revision(), this.token, status, cells, message);
    }
}
