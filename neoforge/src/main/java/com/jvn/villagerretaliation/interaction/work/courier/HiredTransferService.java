package com.jvn.villagerretaliation.interaction.work.courier;

import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.VillagerWorkAuthority;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionService;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.settlement.SettlementDepotService;
import com.jvn.villagerretaliation.settlement.SettlementWorkforceService;
import java.util.UUID;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

public final class HiredTransferService {
    public static final String STATE_TAG = "CourierTransferQueue";

    private HiredTransferService() {
    }

    public static Access access(ServerLevel level, Villager villager) {
        var duty = SettlementWorkforceService.duty(villager).orElse(null);
        if (duty != null) {
            var queue = com.jvn.villagerretaliation.settlement.SettlementTransferService.pending(duty.town(), villager.getUUID());
            if (queue == null) queue = duty.town().transferQueues().get(duty.area().id());
            return new Access(queue != null, queue, "");
        }
        var state = HiredWorkStateStore.state(villager);
        if (!state.contains(STATE_TAG)) return new Access(false, null, "");
        if (!com.jvn.villagerretaliation.interaction.work.production.HiredProductionCommitments.get(level).available(villager.getUUID())) {
            return new Access(true, null, "production_stock_recovery_required");
        }
        try {
            if (!state.contains(STATE_TAG, Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Invalid personal transfer queue");
            var queue = HiredTransferQueue.load(state.getCompound(STATE_TAG), level.registryAccess(),
                    updated -> savePersonal(level, villager, updated));
            if (queue.scope() != HiredTransferQueue.Scope.PERSONAL) return new Access(true, null, "transfer_ownership_changed");
            if (HiredVillagerContractService.currentContractId(villager).filter(queue.owner()::equals).isEmpty()) {
                return new Access(true, null, "transfer_contract_changed");
            }
            if (VillagerWorkAuthority.resolve(level, villager).role() != HiredVillagerRole.COURIER) {
                return new Access(true, null, "transfer_role_changed");
            }
            return new Access(true, queue, "");
        } catch (IllegalArgumentException invalid) {
            return new Access(true, null, "transfer_queue_invalid");
        }
    }

    public static boolean pendingCivic(Villager villager) {
        return SettlementWorkforceService.duty(villager).map(duty ->
                com.jvn.villagerretaliation.settlement.SettlementTransferService.pending(duty.town(), villager.getUUID()) != null).orElse(false);
    }

    public static String appendPersonal(ServerPlayer player, Villager villager, long revision, HiredTransferQueue.Order requested) {
        if (!owns(player, villager)) return "Courier permission changed";
        if (!com.jvn.villagerretaliation.interaction.work.production.HiredProductionCommitments.get(player.serverLevel()).available(villager.getUUID())) {
            return "Production stock data requires recovery";
        }
        if (com.jvn.villagerretaliation.settlement.SettlementSupplyStock.sameStorage(player.serverLevel(), requested.source().position(), requested.destination().position())
                || requested.source().depot() != null || requested.destination().depot() != null
                || !personalEndpoint(player.serverLevel(), villager, requested.source(), true)
                || !personalEndpoint(player.serverLevel(), villager, requested.destination(), false)) return "Choose assigned source and destination storage";
        var access = access(player.serverLevel(), villager);
        if (!access.blocker().isEmpty()) return access.blocker();
        var queue = access.queue();
        if (queue == null) {
            if (revision != 0L) return "Transfer orders changed";
            var contract = HiredVillagerContractService.currentContractId(villager).orElse(null);
            if (contract == null) return "Courier contract changed";
            var state = HiredWorkStateStore.state(villager);
            queue = new HiredTransferQueue(contract, HiredTransferQueue.Scope.PERSONAL,
                    updated -> savePersonal(player.serverLevel(), villager, updated));
        } else if (queue.revision() != revision) return "Transfer orders changed";
        if (!queue.append(requested)) return "Transfer queue is full or the order is invalid";
        HiredWorkSession.invalidate(villager);
        return "";
    }

    private static void savePersonal(ServerLevel level, Villager villager, HiredTransferQueue queue) {
        HiredWorkStateStore.state(villager).put(STATE_TAG, queue.save(level.registryAccess()));
        com.jvn.villagerretaliation.interaction.work.production.HiredProductionCommitments.observe(villager);
    }

    public static String editPersonal(ServerPlayer player, Villager villager, long revision, UUID id, HiredTransferQueue.Edit edit, int index) {
        if (!owns(player, villager)) return "Courier permission changed";
        var access = access(player.serverLevel(), villager);
        if (!access.blocker().isEmpty()) return access.blocker();
        if (access.queue() == null || access.queue().revision() != revision) return "Transfer orders changed";
        if (!access.queue().edit(id, edit, index)) return "Transfer has undelivered cargo or is unavailable";
        if (access.queue().allocation(villager.getUUID()) == null) AssignedStorageService.releaseCourierClaims(villager);
        return "";
    }

    public static boolean owns(ServerPlayer player, Villager villager) {
        return HiredProductionService.owns(player, villager)
                && VillagerWorkAuthority.resolve(player.serverLevel(), villager).role() == HiredVillagerRole.COURIER;
    }

    public static boolean personalEndpoint(ServerLevel level, Villager villager, HiredTransferQueue.Endpoint endpoint, boolean source) {
        if (endpoint.depot() != null || level.isOutsideBuildHeight(endpoint.position()) || !level.getWorldBorder().isWithinBounds(endpoint.position())) return false;
        return AssignedStorageService.assignedStorage(level, villager).stream().anyMatch(record ->
                record.dimension().equals(level.dimension()) && com.jvn.villagerretaliation.settlement.SettlementSupplyStock.sameStorage(level, record.pos(), endpoint.position())
                        && HiredVillagerContractService.currentContractHirer(villager).filter(record.hirerId()::equals).isPresent()
                        && (record.purpose().equals(source ? AssignedStorageService.SUPPLY_PURPOSE : AssignedStorageService.OUTPUT_PURPOSE)
                                || record.purpose().equals(AssignedStorageService.GENERAL_PURPOSE))
                        && SettlementDepotService.permittedForWorker(level, villager, record));
    }

    public static int allowance(ServerLevel level, Villager villager, net.minecraft.core.BlockPos position,
                                net.minecraft.world.item.ItemStack sample,
                                com.jvn.villagerretaliation.item.VillagerFilterPolicy.TransferOperation operation,
                                java.util.List<net.minecraft.world.item.ItemStack> additional) {
        if (VillagerWorkAuthority.resolve(level, villager).role() != HiredVillagerRole.COURIER && !pendingCivic(villager)) return Integer.MAX_VALUE;
        var access = access(level, villager);
        if (!access.blocker().isEmpty()) return 0;
        var allocation = access.queue() == null ? null : access.queue().allocation(villager.getUUID());
        if (allocation == null) return Integer.MAX_VALUE;
        var order = access.queue().order(allocation.order());
        boolean providing = operation == com.jvn.villagerretaliation.item.VillagerFilterPolicy.TransferOperation.PROVIDE;
        if (providing && allocation.started() || !order.matches(sample)
                || !com.jvn.villagerretaliation.settlement.SettlementSupplyStock.sameStorage(level, (providing ? order.source() : order.destination()).position(), position)) return 0;
        int limit = allocation.amount();
        var duty = SettlementWorkforceService.duty(villager).orElse(null);
        if (providing && duty != null) {
            var depot = com.jvn.villagerretaliation.settlement.SettlementTransferService.depot(duty.town(), order.source());
            var facts = com.jvn.villagerretaliation.settlement.SettlementSupplyStock.observe(level, duty.town(), position, sample, null);
            if (!facts.known()) return 0;
            long other = Math.max(0, facts.outgoing() - allocation.amount());
            limit = (int) Math.min(limit, Math.max(0, facts.stored() - other - (depot == null ? 0 : Math.max(0, depot.stockTarget(sample)))));
        }
        return Math.max(0, limit - additional.stream().filter(order::matches)
                .mapToInt(net.minecraft.world.item.ItemStack::getCount).sum());
    }

    public static long stock(ServerLevel level, Villager villager, HiredTransferQueue.Order order) {
        if (order.mode() == HiredTransferQueue.Mode.FINITE) return 0;
        var facts = facts(level, villager, order);
        return facts.known() ? facts.stored() + facts.incoming() : -1;
    }

    public static com.jvn.villagerretaliation.settlement.SettlementSupplyStock.Facts facts(ServerLevel level, Villager villager, HiredTransferQueue.Order order) {
        var duty = SettlementWorkforceService.duty(villager).orElse(null);
        if (duty != null) return com.jvn.villagerretaliation.settlement.SettlementSupplyStock.observe(level, duty.town(), order.destination().position(), order.sample(), null);
        var commitments = com.jvn.villagerretaliation.interaction.work.production.HiredProductionCommitments.get(level);
        com.jvn.villagerretaliation.interaction.work.production.HiredProductionCommitments.observe(villager);
        long incoming = 0;
        var owners = new java.util.HashSet<UUID>();
        for (var position : com.jvn.villagerretaliation.settlement.SettlementSupplyStock.positions(level, order.destination().position())) {
            commitments.refresh(level, position);
            long count = commitments.count(position, order.sample(), null);
            if (count < 0) return new com.jvn.villagerretaliation.settlement.SettlementSupplyStock.Facts(-1, -1, -1, "transfer_storage_unobserved");
            incoming += count;
            owners.addAll(commitments.contributors(position, order.sample()));
        }
        long stored = AssignedStorageService.productionStockAt(villager, order.destination().position(), order::matches, owners);
        if (stored < 0) return new com.jvn.villagerretaliation.settlement.SettlementSupplyStock.Facts(-1, -1, -1, "transfer_storage_unobserved");
        int claims = AssignedStorageService.reservedItemsExcluding(level, order.destination().position(), owners,
                com.jvn.villagerretaliation.item.VillagerFilterPolicy.TransferOperation.RECEIVE, order::matches);
        return new com.jvn.villagerretaliation.settlement.SettlementSupplyStock.Facts(Math.max(0, stored - claims), 0, incoming + claims, "available");
    }

    public static void releasePlanned(ServerLevel level, Villager villager) {
        var access = access(level, villager);
        if (access.queue() != null) access.queue().releasePlanned(villager.getUUID());
        AssignedStorageService.releaseCourierClaims(villager);
    }

    public record Access(boolean configured, HiredTransferQueue queue, String blocker) {
    }
}
