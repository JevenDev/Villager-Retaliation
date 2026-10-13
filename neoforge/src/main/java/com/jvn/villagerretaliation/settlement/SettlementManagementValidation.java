package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.network.SettlementManagementActionPayload.*;
import net.minecraft.server.level.ServerPlayer;

final class SettlementManagementValidation {
    private SettlementManagementValidation() { }

    static String permission(ServerPlayer player, SettlementRecord town, Command command) {
        SettlementPermission permission = switch (command) {
            case Management ignored -> null;
            case Advance ignored -> null;
            case Release ignored -> null;
            case Permissions ignored -> null;
            case Transfer ignored -> null;
            case Home ignored -> SettlementPermission.STAFF;
            case AutomaticStaffing ignored -> SettlementPermission.STAFF;
            case Assign ignored -> SettlementPermission.STAFF;
            case Applicant ignored -> SettlementPermission.STAFF;
            case Guard ignored -> SettlementPermission.STAFF;
            case SaveRoute ignored -> SettlementPermission.STAFF;
            case SaveStorage ignored -> SettlementPermission.LOGISTICS;
            case StorageFilter ignored -> SettlementPermission.LOGISTICS;
            case SaveStorageFilter ignored -> SettlementPermission.LOGISTICS;
            case Brew ignored -> SettlementPermission.STAFF;
            case SaveWorker ignored -> SettlementPermission.STAFF;
            case SaveWorksite ignored -> SettlementPermission.STAFF;
            case SaveEquipment ignored -> SettlementPermission.STAFF;
            case ResetWorker ignored -> SettlementPermission.STAFF;
            case FillStaffing ignored -> SettlementPermission.STAFF;
            case WorkerEnabled ignored -> SettlementPermission.STAFF;
            case DraftBlueprint ignored -> SettlementPermission.BUILD;
            case PlaceBlueprint ignored -> SettlementPermission.BUILD;
            case CommissionBuilding ignored -> SettlementPermission.BUILD;
            case StartProject ignored -> SettlementPermission.BUILD;
            case CancelProject ignored -> SettlementPermission.BUILD;
            case ConfigureProject ignored -> SettlementPermission.BUILD;
            case ReorderProject ignored -> SettlementPermission.BUILD;
            case SaveArea ignored -> SettlementPermission.BUILD;
            case RemoveArea ignored -> SettlementPermission.BUILD;
            case ClaimPlot ignored -> SettlementPermission.BUILD;
            case ConfigureDepot ignored -> SettlementPermission.LOGISTICS;
            case StockTarget ignored -> SettlementPermission.LOGISTICS;
            case Inventory ignored -> SettlementPermission.LOGISTICS;
            case Treasury ignored -> SettlementPermission.TREASURY;
            case Budget ignored -> SettlementPermission.TREASURY;
            case SaveOrder ignored -> SettlementPermission.TRADE;
            case RemoveOrder ignored -> SettlementPermission.TRADE;
            case View ignored -> SettlementPermission.VIEW;
        };
        if (permission == null) return town.leader().equals(player.getUUID()) ? "" : "leader_required";
        return town.permits(player.getUUID(), permission) ? "" : "permission_" + permission.name().toLowerCase(java.util.Locale.ROOT);
    }

    static String selection(ServerPlayer player, SettlementRecord town, Command command) {
        return switch (command) {
            case Release release -> town.duties().containsKey(release.resident()) ? "" : "duty_changed";
            case WorkerEnabled worker -> duty(town, worker.resident(), worker.area());
            case SaveWorker worker -> duty(town, worker.resident(), worker.area());
            case ResetWorker worker -> duty(town, worker.resident(), worker.area());
            case SaveEquipment equipment -> equipment(town, equipment);
            case SaveWorksite worksite -> town.areas().containsKey(worksite.area()) ? "" : "area_missing";
            case FillStaffing worksite -> town.worksitePlans().containsKey(worksite.area()) ? "" : "area_missing";
            case Brew worker -> duty(town, worker.resident(), worker.area());
            case DraftBlueprint ignored -> town.stage().projects() == 0 ? "stage_required"
                    : java.util.stream.IntStream.range(0, player.getInventory().getContainerSize()).noneMatch(
                            slot -> player.getInventory().getItem(slot).is(net.minecraft.world.item.Items.PAPER)) ? "paper_required" : "";
            case PlaceBlueprint ignored -> town.stage().projects() == 0 ? "stage_required" : "";
            case StartProject start -> town.stage().projects() == 0 ? "stage_required"
                    : town.projects().values().stream().filter(SettlementProject::active).count() >= town.stage().projects() ? "project_limit"
                    : town.activeProject(start.resident()) != null ? "builder_busy" : "";
            case SaveArea save -> !town.owns(save.area()) ? "area_outside_territory"
                    : player.serverLevel().isOutsideBuildHeight(save.area().min()) || player.serverLevel().isOutsideBuildHeight(save.area().max())
                            || !player.serverLevel().getWorldBorder().isWithinBounds(save.area().min())
                            || !player.serverLevel().getWorldBorder().isWithinBounds(save.area().max()) ? "area_outside_world"
                    : town.areas().values().stream().anyMatch(area -> !area.id().equals(save.area().id()) && area.conflictsWith(save.area())) ? "area_overlap" : "";
            case RemoveArea area -> town.areas().containsKey(area.id()) ? "" : "area_missing";
            case ConfigureDepot depot -> town.depots().containsKey(depot.id()) ? "" : "depot_missing";
            case StockTarget target -> !town.depots().containsKey(target.depot()) ? "depot_missing"
                    : player.getMainHandItem().isEmpty() ? "held_item_required" : "";
            case RemoveOrder order -> town.economy().orders().containsKey(order.id()) ? "" : "order_missing";
            case Applicant applicant -> town.applicants().containsKey(applicant.id()) ? "" : "applicant_missing";
            case CancelProject cancel -> !town.knowsProject(cancel.project()) ? "project_missing"
                    : town.project(cancel.project()) == null || !town.project(cancel.project()).unfinished() ? "project_finished" : "";
            case ReorderProject project -> town.constructionQueueRevision() != project.revision() ? "state_changed"
                    : town.project(project.project()) == null || town.project(project.before()) == null ? "project_missing"
                    : !town.project(project.project()).pending() || !town.project(project.before()).pending() ? "project_finished" : "";
            case ConfigureProject project -> !town.permits(player.getUUID(), SettlementPermission.STAFF) ? "permission_staff"
                    : town.project(project.project()) == null ? "project_missing"
                    : !town.project(project.project()).active() ? "project_finished"
                    : town.project(project.project()).crewRevision() != project.revision() ? "state_changed" : "";
            case Treasury treasury -> treasury.amount() <= 0 || treasury.amount() > 4_096 ? "invalid_amount"
                    : treasury.deposit() && com.jvn.villagerretaliation.interaction.VillagerCurrencyPayment.count(player) < treasury.amount()
                            ? "player_funds" : !treasury.deposit() && town.economy().balance().compareTo(
                                    com.jvn.villagerretaliation.sell.CurrencyAmount.of(treasury.amount(), 1)) < 0 ? "treasury_funds" : "";
            case Assign assign -> assignment(player, town, assign);
            case AutomaticStaffing automatic -> automatic.resident() == null ? "" : member(player, town, automatic.resident());
            case Inventory inventory -> !(player.serverLevel().getEntity(inventory.resident()) instanceof net.minecraft.world.entity.npc.Villager)
                    ? "resident_unloaded" : "";
            case Home home -> home.assign() ? member(player, town, home.resident()) : "";
            default -> "";
        };
    }

    private static String equipment(SettlementRecord town, SaveEquipment request) {
        var area = town.workplace(request.area());
        if (area == null || !town.worksitePlans().containsKey(area.id())) return "area_missing";
        var existing = town.equipmentPlans().get(area.id());
        if (request.revision() != (existing == null ? -1 : existing.revision())) return "state_changed";
        return request.configuration() == null || request.configuration().validFor(area.type().role()) ? "" : "worker_settings";
    }

    private static String duty(SettlementRecord town, java.util.UUID resident, java.util.UUID area) {
        return area.equals(town.duties().get(resident)) ? "" : "duty_changed";
    }

    private static String member(ServerPlayer player, SettlementRecord town, java.util.UUID resident) {
        return com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData.get(player.serverLevel())
                .residentRoster(town.id(), player.serverLevel().getGameTime()).stream().anyMatch(value -> value.id().equals(resident))
                ? "" : "resident_missing";
    }

    private static String assignment(ServerPlayer player, SettlementRecord town, Assign assign) {
        String membership = member(player, town, assign.resident());
        if (!membership.isEmpty()) return membership;
        var area = town.workplace(assign.area());
        if (area == null || area.type().role() == null) return "area_missing";
        if (town.departures().containsKey(assign.resident())) return "resident_departing";
        if (area.type() == SettlementAreaType.CONSTRUCTION && town.stage().projects() == 0
                || area.type() == SettlementAreaType.TRADING_POST && town.stage().ordinal() < SettlementStage.VILLAGE.ordinal()) return "stage_required";
        for (var level : player.server.getAllLevels()) {
            var entity = level.getEntity(assign.resident());
            if (entity == null) continue;
            if (level != player.serverLevel()) return "resident_away";
            if (!(entity instanceof net.minecraft.world.entity.npc.Villager villager) || !villager.isAlive() || villager.isBaby()) return "resident_unavailable";
            if (SettlementWorkforceService.hasPersonalCommitment(villager)) return "personal_commitment";
            return area.type() == SettlementAreaType.CONSTRUCTION || com.jvn.villagerretaliation.interaction.HiredVillagerRoles.availableRoles(level, villager).contains(area.type().role())
                    ? "" : "role_unavailable";
        }
        var resident = town.residents().get(assign.resident());
        if (resident == null || !resident.availableForWork()) return "resident_unavailable";
        return area.type() == SettlementAreaType.CONSTRUCTION || resident.availableRoles(player.serverLevel()).contains(area.type().role()) ? "" : "role_unavailable";
    }

    static String rejected(Command command) {
        return switch (command) {
            case Home ignored -> "home_unavailable";
            case Advance ignored -> "advancement_requirements";
            case View ignored -> "view_unavailable";
            case DraftBlueprint ignored -> "blueprint_requirements";
            case PlaceBlueprint ignored -> "blueprint_placement";
            case StartProject ignored -> "project_requirements";
            case Brew ignored -> "brewing_requirements";
            case SaveWorker ignored -> "worker_settings";
            case WorkerEnabled ignored -> "worker_settings";
            case Guard ignored -> "guard_order";
            case Assign ignored -> "resident_unavailable";
            case Applicant ignored -> "growth_requirements";
            case SaveArea ignored -> "area_requirements";
            case RemoveArea ignored -> "area_in_use";
            case ConfigureDepot ignored -> "depot_settings";
            case StockTarget ignored -> "stock_target";
            case Treasury ignored -> "treasury_funds";
            case Budget ignored -> "budget_invalid";
            case SaveOrder ignored -> "order_unsupported";
            case Permissions ignored -> "delegate_invalid";
            case Transfer ignored -> "successor_invalid";
            case Inventory ignored -> "inventory_unavailable";
            default -> "state_changed";
        };
    }
}
