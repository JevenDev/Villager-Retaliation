package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.block.SettlementDepotBlockEntity;
import com.jvn.villagerretaliation.inventory.AssignedStorageSavedData;
import com.jvn.villagerretaliation.inventory.AssignedStorageSavedData.AssignedContainerRecord;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.npc.Villager;

public final class SettlementDepotService {
    private SettlementDepotService() {
    }

    public static boolean register(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (!player.isAlive() || player.isSpectator() || player.distanceToSqr(pos.getCenter()) > 64.0D
                || !level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof SettlementDepotBlockEntity block)) {
            return false;
        }
        restoreBinding(level, block);
        if (block.inventory() != null) {
            return block.stillValid(player);
        }
        var town = SettlementSavedData.get(level).at(level.dimension().location(), pos).orElse(null);
        if (town == null) {
            return false;
        }
        var area = town.areas().values().stream()
                .filter(candidate -> candidate.type() != SettlementAreaType.HOME && candidate.contains(pos))
                .min(Comparator.comparing((SettlementArea candidate) -> candidate.type() != SettlementAreaType.DEPOT)
                        .thenComparing(Comparator.comparingInt(SettlementArea::priority).reversed())
                        .thenComparing(SettlementArea::id)).orElse(null);
        if (area == null) {
            return false;
        }
        var depot = town.registerDepot(player.getUUID(), area.id(), pos, SettlementDepotPurpose.GENERAL);
        if (depot == null) {
            return false;
        }
        block.bind(town.id(), depot.id());
        observeRules(level, depot);
        return true;
    }

    public static void observeRules(ServerLevel level, SettlementDepot depot) {
        SettlementDepotRules.observe(level, depot);
    }

    public static void restoreBinding(ServerLevel level, SettlementDepotBlockEntity block) {
        if (block.inventory() != null) {
            return;
        }
        SettlementSavedData.get(level).at(level.dimension().location(), block.getBlockPos()).ifPresent(town ->
                town.depots().values().stream().filter(depot -> depot.position().equals(block.getBlockPos()))
                        .findFirst().ifPresent(depot -> block.bind(town.id(), depot.id())));
    }

    public static void destroy(ServerLevel level, SettlementDepotBlockEntity block) {
        restoreBinding(level, block);
        SettlementDepot depot = block.inventory();
        if (depot == null) {
            return;
        }
        Containers.dropContents(level, block.getBlockPos(), depot);
        SettlementSavedData.get(level).at(level.dimension().location(), block.getBlockPos())
                .ifPresent(town -> town.removeDepot(depot.id()));
        AssignedStorageService.removeAssignedContainer(level, block.getBlockPos());
    }

    public static boolean canAssign(ServerPlayer player, Villager villager, ServerLevel target, BlockPos pos) {
        var duty = SettlementWorkforceService.duty(villager).orElse(null);
        if (target.getBlockEntity(pos) instanceof SettlementDepotBlockEntity block) {
            SettlementDepot depot = block.inventory();
            var town = SettlementSavedData.get(target).at(target.dimension().location(), pos).orElse(null);
            return depot != null && town != null && town.permits(player.getUUID(), SettlementPermission.LOGISTICS)
                    && (duty == null || duty.town().id().equals(town.id()));
        }
        return duty == null;
    }

    public static boolean permittedForWorker(ServerLevel level, Villager villager, AssignedContainerRecord record) {
        var duty = SettlementWorkforceService.duty(villager).orElse(null);
        SettlementRecord town = duty == null ? null : duty.town();
        if (duty != null) {
            var configured = SettlementStorageService.effective(town, villager.getUUID());
            if (!configured.automatic()) {
                return record.dimension().equals(level.dimension()) && record.hirerId().equals(town.leader())
                        && configured.entries().stream().anyMatch(link -> link.position().equals(record.pos())
                        && link.purpose().id().equals(record.purpose())
                        && SettlementStorageService.permitted(level, duty.town(), duty.area().type().role(), link));
            }
        }
        if (town == null && level.hasChunkAt(record.pos())
                && level.getBlockEntity(record.pos()) instanceof SettlementDepotBlockEntity) {
            town = SettlementSavedData.get(level).at(level.dimension().location(), record.pos()).orElse(null);
        }
        SettlementDepot depot = town == null ? null : town.depots().values().stream()
                .filter(candidate -> candidate.position().equals(record.pos())).findFirst().orElse(null);
        if (depot == null) {
            return duty == null;
        }
        if (!depot.purpose().permitsRole(com.jvn.villagerretaliation.interaction.VillagerWorkAuthority.assignedRole(level, villager))) {
            return false;
        }
        SettlementArea area = town.areas().get(depot.area());
        if (area == null || !area.enabled() || !town.permits(record.hirerId(), SettlementPermission.LOGISTICS)) {
            return false;
        }
        if (duty != null) {
            var links = SettlementWorksiteService.links(town, duty.area().id(), villager.getUUID());
            boolean permitted = switch (AssignedStorageService.normalizePurpose(record.purpose())) {
                case AssignedStorageService.SUPPLY_PURPOSE -> links.permitsSupply(depot.id());
                case AssignedStorageService.OUTPUT_PURPOSE -> links.permitsOutput(depot.id());
                case AssignedStorageService.GENERAL_PURPOSE -> links.permitsSupply(depot.id()) && links.permitsOutput(depot.id());
                default -> false;
            };
            if (!permitted) return false;
        }
        if (duty != null && SettlementLogisticsService.isHauler(villager)) {
            return SettlementLogisticsService.permitsStorage(villager, depot, AssignedStorageService.normalizePurpose(record.purpose()));
        }
        return switch (AssignedStorageService.normalizePurpose(record.purpose())) {
            case AssignedStorageService.SUPPLY_PURPOSE -> depot.purpose().providesSupplies();
            case AssignedStorageService.OUTPUT_PURPOSE -> depot.purpose().acceptsOutputs();
            case AssignedStorageService.GENERAL_PURPOSE -> depot.purpose().providesSupplies() && depot.purpose().acceptsOutputs();
            default -> false;
        };
    }

    public static void synchronizeStorage(ServerLevel level, Villager villager, SettlementRecord town) {
        if (SettlementWorkforceService.hasPersonalCommitment(villager)
                || SettlementWorkforceService.duty(villager).filter(duty -> duty.town() == town).isEmpty()) return;
        AssignedStorageSavedData storage = AssignedStorageSavedData.get(level);
        var configured = SettlementStorageService.effective(town, villager.getUUID());
        boolean hauler = configured.automatic() && SettlementLogisticsService.isHauler(villager);
        var links = SettlementWorksiteService.links(town, town.duties().get(villager.getUUID()), villager.getUUID());
        List<AssignedContainerRecord> desired = new ArrayList<>(hauler
                ? SettlementLogisticsService.assignments(level, villager, town) : List.of());
        if (!configured.automatic()) {
            for (var link : configured.entries()) {
                desired.add(new AssignedContainerRecord(level.dimension(), link.position(), villager.getUUID(), town.leader(),
                        link.purpose().id(), link.priority(), "unknown"));
            }
        }
        for (SettlementDepot depot : hauler || !configured.automatic() ? List.<SettlementDepot>of() : town.depots().values()) {
            SettlementArea area = town.areas().get(depot.area());
            if (area == null || !area.enabled()) {
                continue;
            }
            if (depot.purpose().providesSupplies() && links.permitsSupply(depot.id())) {
                desired.add(reference(level, villager, town, depot, AssignedStorageService.SUPPLY_PURPOSE));
            }
            if (depot.purpose().acceptsOutputs() && links.permitsOutput(depot.id())) {
                desired.add(reference(level, villager, town, depot, AssignedStorageService.OUTPUT_PURPOSE));
            }
        }
        if (configured.automatic()) desired.removeIf(record -> !permittedForWorker(level, villager, record));
        List<AssignedContainerRecord> existing = storage.assignedTo(villager.getUUID());
        for (AssignedContainerRecord record : existing) {
            if (desired.stream().noneMatch(candidate -> sameAssignment(record, candidate))) {
                storage.removeAssignment(record);
            }
        }
        for (AssignedContainerRecord record : desired) {
            if (existing.stream().noneMatch(candidate -> sameAssignment(record, candidate))) {
                storage.assign(record);
            }
        }
    }

    private static AssignedContainerRecord reference(ServerLevel level, Villager villager, SettlementRecord town,
                                                     SettlementDepot depot, String purpose) {
        return new AssignedContainerRecord(level.dimension(), depot.position(), villager.getUUID(), town.leader(),
                purpose, 9 - depot.priority(), "unknown");
    }

    private static boolean sameAssignment(AssignedContainerRecord first, AssignedContainerRecord second) {
        return first.dimension().equals(second.dimension()) && first.pos().equals(second.pos())
                && first.purpose().equals(second.purpose()) && second.hirerId().equals(first.hirerId())
                && first.priority() == second.priority();
    }
}
