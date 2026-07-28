package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.work.HiredRoleWorkerRegistry;
import com.jvn.villagerretaliation.villager.VillagerRecoveryService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

/**
 * Per-level admission control for expensive hired-worker decisions. The due set is resolved once
 * per level tick, then a rotating window is selected before entity iteration can influence who wins.
 */
public final class HiredWorkDecisionScheduler {
    private static final Map<ServerLevel, TickBudget> LEVEL_BUDGETS = new IdentityHashMap<>();
    private static final Map<UUID, Long> DEFERRED_BY_WORKER = new HashMap<>();

    private HiredWorkDecisionScheduler() {
    }

    public static boolean isDecisionOpportunity(ServerLevel level, Villager villager) {
        if (level == null || villager == null) {
            return false;
        }
        HiredVillagerRole role = HiredVillagerContractService.activeRoleWithoutMaintenance(level, villager);
        int interval = HiredWorkSession.cachedDecisionInterval(level, villager, role);
        return Math.floorMod(level.getGameTime() + villager.getUUID().getLeastSignificantBits(), interval) == 0L;
    }

    public static boolean tryAcquire(ServerLevel level, Villager villager) {
        if (level == null || villager == null) {
            return false;
        }
        TickBudget current = LEVEL_BUDGETS.get(level);
        if (current == null || current.gameTime != level.getGameTime()) {
            current = resetForTick(level, current);
            LEVEL_BUDGETS.put(level, current);
        }
        if (!current.winners.remove(villager.getUUID())) {
            return false;
        }
        current.granted++;
        return true;
    }

    public static DebugSnapshot debugSnapshot(ServerLevel level, Villager villager) {
        TickBudget current = LEVEL_BUDGETS.get(level);
        int configuredBudget = Math.max(1, VillagerRetaliationConfig.HIRED_WORK_DECISION_BUDGET.get());
        if (current == null || current.gameTime != level.getGameTime()) {
            return new DebugSnapshot(configuredBudget, 0, 0, 0, 0L,
                    villager == null ? 0L : DEFERRED_BY_WORKER.getOrDefault(villager.getUUID(), 0L));
        }
        return new DebugSnapshot(
                current.limit,
                current.eligible,
                current.granted,
                current.deferred,
                current.totalDeferred,
                villager == null ? 0L : DEFERRED_BY_WORKER.getOrDefault(villager.getUUID(), 0L));
    }

    public static void clearRuntimeState() {
        LEVEL_BUDGETS.clear();
        DEFERRED_BY_WORKER.clear();
    }

    private static TickBudget resetForTick(ServerLevel level, TickBudget previous) {
        List<UUID> due = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Villager candidate
                    && canRequestDecision(level, candidate)
                    && isDecisionOpportunity(level, candidate)) {
                due.add(candidate.getUUID());
            }
        }
        due.sort(Comparator.naturalOrder());

        int limit = Math.max(1, VillagerRetaliationConfig.HIRED_WORK_DECISION_BUDGET.get());
        int eligible = due.size();
        int admitted = Math.min(limit, eligible);
        int cursor = previous == null ? 0 : previous.nextCursor;
        int start = eligible == 0 ? 0 : Math.floorMod(cursor, eligible);
        Set<UUID> winners = new HashSet<>();
        for (int index = 0; index < admitted; index++) {
            winners.add(due.get((start + index) % eligible));
        }

        int deferred = Math.max(0, eligible - admitted);
        if (deferred > 0) {
            for (int index = admitted; index < eligible; index++) {
                UUID deferredWorker = due.get((start + index) % eligible);
                DEFERRED_BY_WORKER.merge(deferredWorker, 1L, Long::sum);
            }
        }
        long totalDeferred = (previous == null ? 0L : previous.totalDeferred) + deferred;
        int nextCursor = eligible == 0 ? cursor : (start + Math.max(1, admitted)) % eligible;
        return new TickBudget(level.getGameTime(), limit, eligible, deferred, totalDeferred, nextCursor, winners);
    }

    private static boolean canRequestDecision(ServerLevel level, Villager villager) {
        if (!villager.isAlive()
                || villager.isBaby()
                || villager.isTrading()
                || VillagerConversationService.isConversing(villager)
                || VillagerRecoveryService.isForcingRecovery(villager)
                || villager.getTarget() != null
                || villager.getLastHurtByMob() != null
                || VillagerRecruitmentService.isFollowingAnyPlayer(villager)
                || HiredVillagerFocusService.isVanillaRestActive(villager)) {
            return false;
        }
        HireContractSnapshot contract = HiredVillagerContractService.snapshot(level, villager);
        if (!contract.hired() || contract.awaitingAutoPayment() || contract.hirer().isEmpty()) {
            return false;
        }
        if (level.getServer().getPlayerList().getPlayer(contract.hirer().orElseThrow()) == null) {
            return false;
        }
        return HiredWorkStateStore.state(villager).getBoolean("Enabled")
                && HiredRoleWorkerRegistry.get(contract.role()) != null;
    }

    public record DebugSnapshot(
            int budget,
            int eligible,
            int granted,
            int deferredThisTick,
            long totalDeferred,
            long workerDeferred) {
    }

    private static final class TickBudget {
        private final long gameTime;
        private final int limit;
        private final int eligible;
        private final int deferred;
        private final long totalDeferred;
        private final int nextCursor;
        private final Set<UUID> winners;
        private int granted;

        private TickBudget(long gameTime, int limit, int eligible, int deferred, long totalDeferred,
                int nextCursor, Set<UUID> winners) {
            this.gameTime = gameTime;
            this.limit = limit;
            this.eligible = eligible;
            this.deferred = deferred;
            this.totalDeferred = totalDeferred;
            this.nextCursor = nextCursor;
            this.winners = winners;
        }
    }
}
