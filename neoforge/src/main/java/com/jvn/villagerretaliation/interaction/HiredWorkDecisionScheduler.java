package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.debug.HiredStressGridService;
import com.jvn.villagerretaliation.interaction.work.HiredRoleWorkerRegistry;
import com.jvn.villagerretaliation.villager.VillagerRecoveryService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

/**
 * Per-level admission control for expensive hired-worker decisions. The due set is resolved once
 * per level tick, then the least recently selected workers receive the available slots.
 */
public final class HiredWorkDecisionScheduler {
    private static final Map<ServerLevel, TickBudget> LEVEL_BUDGETS = new IdentityHashMap<>();
    private static final Map<ServerLevel, Map<UUID, Villager>> ROSTERS = new IdentityHashMap<>();
    private static final Map<UUID, Long> LAST_SELECTED = new HashMap<>();
    private static final Map<UUID, Long> DEFERRED_BY_WORKER = new HashMap<>();

    private HiredWorkDecisionScheduler() {
    }

    public static boolean isDecisionOpportunity(ServerLevel level, Villager villager) {
        if (level == null || villager == null || !level.isPositionEntityTicking(villager.blockPosition())) {
            return false;
        }
        HiredVillagerRole role = VillagerWorkAuthority.activeRole(level, villager);
        int interval = HiredWorkSession.cachedDecisionInterval(level, villager, role);
        return Math.floorMod(level.getGameTime() + villager.getUUID().getLeastSignificantBits(), interval) == 0L;
    }

    public static boolean tryAcquire(ServerLevel level, Villager villager) {
        if (level == null || villager == null || !level.isPositionEntityTicking(villager.blockPosition())) {
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
        ROSTERS.clear();
        DEFERRED_BY_WORKER.clear();
        LAST_SELECTED.clear();
    }

    private static TickBudget resetForTick(ServerLevel level, TickBudget previous) {
        List<UUID> due = new ArrayList<>();
        for (Villager candidate : ROSTERS.computeIfAbsent(level, HiredWorkDecisionScheduler::roster).values()) {
            if (isDecisionOpportunity(level, candidate) && canRequestDecision(level, candidate)) {
                due.add(candidate.getUUID());
            }
        }

        int limit = Math.max(1, VillagerRetaliationConfig.HIRED_WORK_DECISION_BUDGET.get());
        int eligible = due.size();
        int admitted = Math.min(limit, eligible);
        Set<UUID> winners = select(due, LAST_SELECTED, admitted, level.getGameTime());

        int deferred = Math.max(0, eligible - admitted);
        if (deferred > 0) {
            for (int index = admitted; index < eligible; index++) {
                UUID deferredWorker = due.get(index);
                DEFERRED_BY_WORKER.merge(deferredWorker, 1L, Long::sum);
            }
        }
        long totalDeferred = (previous == null ? 0L : previous.totalDeferred) + deferred;
        return new TickBudget(level.getGameTime(), limit, eligible, deferred, totalDeferred, winners);
    }

    static Set<UUID> select(List<UUID> due, Map<UUID, Long> lastSelected, int limit, long now) {
        // a shared index can starve workers when staggered groups have different sizes
        due.sort(java.util.Comparator.<UUID>comparingLong(id -> lastSelected.getOrDefault(id, Long.MIN_VALUE))
                .thenComparing(java.util.Comparator.naturalOrder()));
        Set<UUID> winners = new HashSet<>();
        for (int index = 0; index < Math.min(limit, due.size()); index++) {
            UUID id = due.get(index);
            winners.add(id);
            lastSelected.put(id, now);
        }
        return winners;
    }

    public static void onJoined(ServerLevel level, Villager villager) {
        var roster = ROSTERS.get(level);
        if (roster != null) roster.put(villager.getUUID(), villager);
    }

    public static void onLeft(ServerLevel level, Villager villager) {
        var roster = ROSTERS.get(level);
        if (roster != null) roster.remove(villager.getUUID(), villager);
        DEFERRED_BY_WORKER.remove(villager.getUUID());
        LAST_SELECTED.remove(villager.getUUID());
    }

    private static Map<UUID, Villager> roster(ServerLevel level) {
        var roster = new TreeMap<UUID, Villager>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Villager villager) roster.put(villager.getUUID(), villager);
        }
        return roster;
    }

    private static boolean canRequestDecision(ServerLevel level, Villager villager) {
        boolean stressWorker = HiredStressGridService.isStressWorker(villager);
        if (!villager.isAlive()
                || villager.isBaby()
                || villager.isTrading()
                || VillagerConversationService.isConversing(villager)
                || VillagerRecoveryService.isForcingRecovery(villager)
                || (!stressWorker && villager.getTarget() != null)
                || villager.getLastHurtByMob() != null
                || VillagerRecruitmentService.isFollowingAnyPlayer(villager)
                || (!stressWorker && HiredVillagerFocusService.isVanillaRestActive(villager)
                        && HiredVillagerFocusService.shouldUseVanillaRest(level, villager))) {
            return false;
        }
        VillagerWorkAuthority contract = VillagerWorkAuthority.resolve(level, villager);
        if (!contract.canWork(level, villager)) {
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
        private final Set<UUID> winners;
        private int granted;

        private TickBudget(long gameTime, int limit, int eligible, int deferred, long totalDeferred,
                Set<UUID> winners) {
            this.gameTime = gameTime;
            this.limit = limit;
            this.eligible = eligible;
            this.deferred = deferred;
            this.totalDeferred = totalDeferred;
            this.winners = winners;
        }
    }
}
