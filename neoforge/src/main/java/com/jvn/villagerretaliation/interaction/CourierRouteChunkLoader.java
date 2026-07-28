package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.inventory.AssignedStorageSavedData.AssignedContainerRecord;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.util.TickThrottle;
import com.jvn.villagerretaliation.villager.VillagerRecoveryService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;

/**
 * Keeps a bounded rolling window around working couriers, their next route node, and their live
 * input/output endpoints.
 *
 * <p>Tickets are transient. Route phase, cargo, and endpoint positions remain persisted on the
 * villager or in assigned-storage saved data, so an expired ticket or restart pauses rather than
 * duplicating a container inventory. When the villager is loaded again, the window is rebuilt.</p>
 */
public final class CourierRouteChunkLoader {
    private static final String ROUTE_INDEX_TAG = "CourierRouteIndex";
    private static final String STORAGE_TARGET_TAG = "CourierStorageTarget";
    private static final String VISITED_STORAGE_TAG = "CourierVisitedStorage";
    private static final int ENTITY_TICKING_TICKET_DISTANCE = 2;
    private static final int TICKET_TIMEOUT_TICKS = 20 * 15;
    private static final int RECONCILE_INTERVAL_TICKS = 20;
    private static final int TICKET_REFRESH_INTERVAL_TICKS = 20 * 10;
    private static final int MAX_CHUNKS_PER_COURIER = 4;
    private static final int MAX_ACTIVE_COURIERS_PER_SERVER = 16;
    private static final int MAX_TICKETS_PER_SERVER =
            MAX_CHUNKS_PER_COURIER * MAX_ACTIVE_COURIERS_PER_SERVER;
    private static final TicketType<UUID> COURIER_TICKET = TicketType.create(
            "villagerretaliation_courier",
            UUID::compareTo,
            TICKET_TIMEOUT_TICKS);
    private static final Map<MinecraftServer, ServerTickets> ACTIVE_TICKETS = new WeakHashMap<>();

    private CourierRouteChunkLoader() {
    }

    /**
     * Reconciles this courier's window on a UUID-staggered cadence. Unchanged tickets are only
     * refreshed with a safety margin before timeout, avoiding per-tick chunk distance-manager work.
     */
    public static void onVillagerTick(ServerLevel level, Villager villager) {
        if (level == null
                || villager == null
                || !shouldReconcile(villager.getUUID(), level.getGameTime())) {
            return;
        }
        if (!isEligible(level, villager)) {
            release(level, villager);
            return;
        }

        CompoundTag state = HiredVillagerWorkService.state(villager);
        HiredRoute route = HiredRoute.fromState(state);
        Set<ChunkPos> desired = desiredChunks(level, villager, state, route);
        if (desired.isEmpty()) {
            release(level, villager);
            return;
        }
        reconcile(level, villager.getUUID(), desired);
    }

    static boolean shouldReconcile(UUID villagerId, long gameTime) {
        return villagerId != null
                && TickThrottle.isSpreadTick(villagerId, gameTime, RECONCILE_INTERVAL_TICKS);
    }

    static boolean shouldRefreshTicket(long gameTime, long nextRefreshGameTime) {
        return gameTime >= nextRefreshGameTime;
    }

    static long nextTicketRefreshGameTime(long gameTime) {
        return gameTime + TICKET_REFRESH_INTERVAL_TICKS;
    }

    public static void onVillagerLeaveLevel(ServerLevel level, Villager villager) {
        release(level, villager);
    }

    public static void clearRuntimeState(MinecraftServer server) {
        if (server == null) {
            return;
        }
        ServerTickets tracked = ACTIVE_TICKETS.remove(server);
        if (tracked == null) {
            return;
        }
        for (Map.Entry<LevelOwner, TicketWindow> entry : tracked.byOwner.entrySet()) {
            removeTickets(entry.getKey().level(), entry.getKey().villagerId(), entry.getValue().chunks());
        }
    }

    public static Set<ChunkPos> desiredChunks(
            ServerLevel level,
            Villager villager,
            CompoundTag state,
            HiredRoute route) {
        LinkedHashSet<ChunkPos> desired = new LinkedHashSet<>();
        desired.add(villager.chunkPosition());
        if (route == null || !route.usableForNavigation()) {
            return Set.copyOf(desired);
        }

        BlockPos routeTarget = routeTarget(state, route);
        if (routeTarget != null) {
            desired.add(new ChunkPos(routeTarget));
        }

        BlockPos storageTarget = readPos(state, STORAGE_TARGET_TAG);
        if (storageTarget != null) {
            desired.add(new ChunkPos(storageTarget));
        }

        List<AssignedContainerRecord> records = activeAssignedStorage(level, villager);
        if (routeTarget != null) {
            addNearbyStorageChunks(state, routeTarget, records, desired);
        }
        addFirstPurposeChunk(records, AssignedStorageService.INPUT_PURPOSE, desired);
        addFirstPurposeChunk(records, AssignedStorageService.OUTPUT_PURPOSE, desired);
        return limit(desired, MAX_CHUNKS_PER_COURIER);
    }

    private static boolean isEligible(ServerLevel level, Villager villager) {
        if (level == null
                || villager == null
                || villager.isBaby()
                || !villager.isAlive()
                || villager.isTrading()
                || villager.getTarget() != null
                || villager.getLastHurtByMob() != null
                || VillagerConversationService.isConversing(villager)
                || VillagerRecoveryService.isForcingRecovery(villager)
                || VillagerRecruitmentService.isFollowingAnyPlayer(villager)
                || HiredVillagerFocusService.isVanillaRestActive(villager)
                || HiredVillagerContractService.isAwaitingAutoPayment(level, villager)
                || !HiredVillagerContractService.hasContract(villager)
                || !HiredVillagerContractService.isHired(level, villager)
                || HiredVillagerContractService.activeRoleWithoutMaintenance(level, villager)
                        != HiredVillagerRole.COURIER) {
            return false;
        }

        UUID hirerId = HiredVillagerContractService.getHirer(level, villager).orElse(null);
        ServerPlayer hirer = hirerId == null
                ? null
                : level.getServer().getPlayerList().getPlayer(hirerId);
        if (hirer == null || VillagerAggressionPolicy.shouldAttackOnSight(villager, hirer)) {
            return false;
        }

        CompoundTag state = HiredVillagerWorkService.state(villager);
        HiredVillagerWorkService.initializeDefaults(state, villager);
        return state.getBoolean("Enabled") && HiredRoute.fromState(state).usableForNavigation();
    }

    private static BlockPos routeTarget(CompoundTag state, HiredRoute route) {
        List<BlockPos> traversalNodes = route.traversalNodes();
        int lastIndex = traversalNodes.size() - 1;
        int index = state.contains(ROUTE_INDEX_TAG, Tag.TAG_INT)
                ? Math.clamp(state.getInt(ROUTE_INDEX_TAG), 0, lastIndex)
                : 0;
        return traversalNodes.get(index);
    }

    private static List<AssignedContainerRecord> activeAssignedStorage(
            ServerLevel level,
            Villager villager) {
        UUID hirerId = HiredVillagerContractService.currentContractHirer(villager).orElse(null);
        List<AssignedContainerRecord> records =
                new ArrayList<>(AssignedStorageService.assignedStorage(level, villager));
        records.removeIf(record -> !record.dimension().equals(level.dimension())
                || hirerId != null && !hirerId.equals(record.hirerId()));
        records.sort(Comparator.comparingInt(AssignedContainerRecord::priority));
        return records;
    }

    private static void addFirstPurposeChunk(
            List<AssignedContainerRecord> records,
            String purpose,
            LinkedHashSet<ChunkPos> desired) {
        for (AssignedContainerRecord record : records) {
            if (purpose.equals(AssignedStorageService.normalizePurpose(record.purpose()))) {
                desired.add(new ChunkPos(record.pos()));
                return;
            }
        }
    }

    private static void addNearbyStorageChunks(
            CompoundTag state,
            BlockPos routeTarget,
            List<AssignedContainerRecord> records,
            LinkedHashSet<ChunkPos> desired) {
        records.stream()
                .filter(record -> !visited(state, record.pos()))
                .filter(record -> record.pos().distSqr(routeTarget) <= 16.0D * 16.0D)
                .sorted(Comparator
                        .comparingDouble((AssignedContainerRecord record) ->
                                record.pos().distSqr(routeTarget))
                        .thenComparingInt(AssignedContainerRecord::priority))
                .forEach(record -> {
                    if (desired.size() < MAX_CHUNKS_PER_COURIER) {
                        desired.add(new ChunkPos(record.pos()));
                    }
                });
    }

    private static boolean visited(CompoundTag state, BlockPos pos) {
        long packed = pos.asLong();
        for (long visited : state.getLongArray(VISITED_STORAGE_TAG)) {
            if (visited == packed) {
                return true;
            }
        }
        return false;
    }

    private static BlockPos readPos(CompoundTag state, String key) {
        return state.contains(key, Tag.TAG_LONG) ? BlockPos.of(state.getLong(key)) : null;
    }

    private static Set<ChunkPos> limit(Set<ChunkPos> chunks, int limit) {
        LinkedHashSet<ChunkPos> limited = new LinkedHashSet<>();
        for (ChunkPos chunk : chunks) {
            limited.add(chunk);
            if (limited.size() >= limit) {
                break;
            }
        }
        return Set.copyOf(limited);
    }

    private static void reconcile(ServerLevel level, UUID villagerId, Set<ChunkPos> desired) {
        MinecraftServer server = level.getServer();
        ServerTickets serverTickets =
                ACTIVE_TICKETS.computeIfAbsent(server, ignored -> new ServerTickets());
        LevelOwner owner = new LevelOwner(level, villagerId);
        TicketWindow previousWindow = serverTickets.byOwner.get(owner);
        if (previousWindow == null && serverTickets.byOwner.size() >= MAX_ACTIVE_COURIERS_PER_SERVER) {
            return;
        }
        Set<ChunkPos> previous = previousWindow == null ? Set.of() : previousWindow.chunks();
        long gameTime = level.getGameTime();
        boolean refreshDue = previousWindow == null
                || shouldRefreshTicket(gameTime, previousWindow.nextRefreshGameTime());

        LinkedHashSet<ChunkPos> actual = new LinkedHashSet<>();
        int trackedTicketCount = serverTickets.ticketCount();
        if (previousWindow != null) {
            for (ChunkPos chunk : previous) {
                if (!desired.contains(chunk)) {
                    removeTicket(level, villagerId, chunk);
                    trackedTicketCount--;
                }
            }
        }

        for (ChunkPos chunk : desired) {
            boolean alreadyTracked = previous.contains(chunk);
            if (!alreadyTracked && trackedTicketCount >= MAX_TICKETS_PER_SERVER) {
                continue;
            }
            if (!alreadyTracked || refreshDue) {
                level.getChunkSource().addRegionTicket(
                        COURIER_TICKET,
                        chunk,
                        ENTITY_TICKING_TICKET_DISTANCE,
                        villagerId,
                        true);
            }
            actual.add(chunk);
            if (!alreadyTracked) {
                trackedTicketCount++;
            }
        }

        if (actual.isEmpty()) {
            serverTickets.byOwner.remove(owner);
        } else {
            long nextRefresh = refreshDue
                    ? nextTicketRefreshGameTime(gameTime)
                    : previousWindow.nextRefreshGameTime();
            serverTickets.byOwner.put(owner, new TicketWindow(Set.copyOf(actual), nextRefresh));
        }
        if (serverTickets.byOwner.isEmpty()) {
            ACTIVE_TICKETS.remove(server);
        }
    }

    private static void release(ServerLevel level, Villager villager) {
        if (level == null || villager == null) {
            return;
        }
        MinecraftServer server = level.getServer();
        ServerTickets serverTickets = ACTIVE_TICKETS.get(server);
        if (serverTickets == null) {
            return;
        }
        TicketWindow window =
                serverTickets.byOwner.remove(new LevelOwner(level, villager.getUUID()));
        removeTickets(level, villager.getUUID(), window == null ? null : window.chunks());
        if (serverTickets.byOwner.isEmpty()) {
            ACTIVE_TICKETS.remove(server);
        }
    }

    private static void removeTickets(
            ServerLevel level,
            UUID villagerId,
            Set<ChunkPos> chunks) {
        if (chunks == null) {
            return;
        }
        for (ChunkPos chunk : chunks) {
            removeTicket(level, villagerId, chunk);
        }
    }

    private static void removeTicket(
            ServerLevel level,
            UUID villagerId,
            ChunkPos chunk) {
        level.getChunkSource().removeRegionTicket(
                COURIER_TICKET,
                chunk,
                ENTITY_TICKING_TICKET_DISTANCE,
                villagerId,
                true);
    }

    private record LevelOwner(ServerLevel level, UUID villagerId) {
    }

    private record TicketWindow(Set<ChunkPos> chunks, long nextRefreshGameTime) {
    }

    private static final class ServerTickets {
        private final Map<LevelOwner, TicketWindow> byOwner = new java.util.HashMap<>();

        private int ticketCount() {
            return byOwner.values().stream().mapToInt(window -> window.chunks().size()).sum();
        }
    }
}
