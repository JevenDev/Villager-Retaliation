package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.interaction.ClipboardWorkforceSnapshot.WarningSummary;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceSnapshot.WarningType;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceSnapshot.WorkerRow;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceSnapshot.WorkerStatus;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.interaction.work.HiredHuntingTargets;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.network.ClipboardWorkforceSyncPayload;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import com.jvn.villagerretaliation.util.WorldLocation;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ClipboardWorkforceService {
    private static final int LIVE_UPDATE_INTERVAL_TICKS = 20;
    private static final long LIVE_UPDATE_HEARTBEAT_TICKS = 20L * 30L;
    private static final Map<UUID, LiveSyncState> LIVE_SYNC_STATES = new HashMap<>();
    private ClipboardWorkforceService() {
    }

    public static void openClipboard(ServerPlayer player) {
        if (player == null) {
            return;
        }
        ClipboardWorkforceSnapshot snapshot = snapshot(player);
        LIVE_SYNC_STATES.put(player.getUUID(), new LiveSyncState(snapshot, player.serverLevel().getGameTime()));
        sendSnapshot(player, snapshot, true);
    }

    /**
     * Updates only dashboards that are currently open. Work is spread across the update interval by player UUID,
     * and packets are sent only when the presentation snapshot changes (or on a low-frequency heartbeat).
     */
    public static void onPlayerTick(ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator()) {
            return;
        }
        LiveSyncState previous = LIVE_SYNC_STATES.get(player.getUUID());
        if (previous == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }

        long gameTime = level.getGameTime();
        if (gameTime == previous.gameTime() || !isLiveUpdateTick(player, gameTime)) {
            return;
        }

        ClipboardWorkforceSnapshot updated = snapshot(player);
        boolean heartbeatDue = gameTime < previous.gameTime()
                || gameTime - previous.gameTime() >= LIVE_UPDATE_HEARTBEAT_TICKS;
        if (!heartbeatDue && updated.equals(previous.snapshot())) {
            return;
        }

        sendSnapshot(player, updated, false);
        LIVE_SYNC_STATES.put(player.getUUID(), new LiveSyncState(updated, gameTime));
    }

    public static void closeClipboard(ServerPlayer player) {
        if (player != null) {
            LIVE_SYNC_STATES.remove(player.getUUID());
        }
    }

    public static void clearRuntimeState(ServerPlayer player) {
        closeClipboard(player);
    }

    public static void clearRuntimeState() {
        LIVE_SYNC_STATES.clear();
    }

    private static boolean isLiveUpdateTick(ServerPlayer player, long gameTime) {
        return Math.floorMod(gameTime + player.getUUID().hashCode(), LIVE_UPDATE_INTERVAL_TICKS) == 0L;
    }

    private static void sendSnapshot(ServerPlayer player, ClipboardWorkforceSnapshot snapshot, boolean openScreen) {
        try {
            PacketDistributor.sendToPlayer(player, new ClipboardWorkforceSyncPayload(snapshot, openScreen));
        } catch (UnsupportedOperationException ignored) {
            // The client can lack the payload while a server is being reloaded or disconnected.
        }
    }

    public static ClipboardWorkforceSnapshot snapshot(ServerPlayer player) {
        if (player == null || player.server == null) {
            return ClipboardWorkforceSnapshot.empty();
        }

        Map<HiredVillagerRole, Integer> jobCounts = new EnumMap<>(HiredVillagerRole.class);
        Map<WarningKey, Integer> warningCounts = new java.util.LinkedHashMap<>();
        List<WorkerRow> workers = new ArrayList<>();
        int working = 0;
        int idle = 0;
        int assignedStorage = 0;
        int paymentContainers = 0;
        int dailyWages = 0;

        HiredVillagerIndex.reconcileLoadedFor(player);
        for (HiredVillagerIndex.Target target : HiredVillagerIndex.targetsFor(player)) {
            ServerLevel level = target.level();
            Villager villager = target.villager();

                HiredWorkSession session = HiredWorkSession.active(level, villager);
                HiredVillagerRole role = session.role();
                HiredWorkerBrain.Snapshot brain = HiredWorkerBrain.snapshot(session.state(), level.getGameTime());
                boolean routeAssigned = HiredVillagerWorkService.usesRouteAssignment(role, session.context());
                boolean hasEffectiveWorkAssignment = HiredVillagerWorkService.hasEffectiveWorkArea(level, villager, session);
                int storageCount = AssignedStorageService.assignedStorage(level, villager).size();
                int paymentStorageCount = AssignedStorageService.assignedPaymentStorage(level, villager).size();
                boolean storageAssigned = storageCount > 0;
                boolean storageFull = brain.taskState() == HiredWorkerTaskState.PAUSED_STORAGE_FULL;
                boolean requiresWorkAssignment = requiresWorkAssignment(role);
                boolean noWorkArea = requiresWorkAssignment
                        && !hasEffectiveWorkAssignment;
                var facts = HiredWorkerFacts.observe(session, brain);
                boolean waitingForCrops = !noWorkArea && facts.waitingForCrops();
                boolean noTargets = !noWorkArea && !waitingForCrops && facts.noTargets();
                boolean tooFar = requiresWorkAssignment
                        && !noWorkArea
                        && !isExpectedWorkExcursion(brain.taskState())
                        && !HiredVillagerWorkService.isInsideEffectiveWorkArea(level, villager, role, session.context(), villager.blockPosition());
                boolean missingTools = brain.taskState() == HiredWorkerTaskState.PAUSED_MISSING_TOOL;
                boolean materialStorageUnreachable = facts.materialStorageUnreachable();
                boolean materialInventoryFull = facts.materialInventoryFull();
                boolean buildSiteUnreachable = facts.buildSiteUnreachable();
                boolean noStorage = facts.noStorage();
                boolean missingMaterials = facts.missingMaterials();
                boolean inventoryFull = facts.inventoryFull();
                int dailyWage = HiredVillagerContractService.getContractDailyCost(level, villager, player);
                int contractDays = HiredVillagerContractService.getRemainingHireDays(level, villager);
                boolean recurringPayment = HiredVillagerContractService.isAutoPaymentEnabled(level, villager);
                boolean unpaid = HiredVillagerContractService.isAwaitingAutoPayment(level, villager);
                boolean workerIsWorking = isWorking(brain.taskState(), session.state().getBoolean("Enabled"));
                WorkerStatus status = unpaid ? WorkerStatus.UNPAID : noWorkArea ? WorkerStatus.NO_WORK_AREA
                        : tooFar ? WorkerStatus.TOO_FAR : facts.status();
                String diagnostic = PersonalWorkerDiagnostics.describe(session, brain, facts);

                jobCounts.merge(role, 1, Integer::sum);
                assignedStorage += storageCount;
                paymentContainers += paymentStorageCount;
                dailyWages += dailyWage;
                if (workerIsWorking) {
                    working++;
                } else {
                    idle++;
                }
                addWarning(warningCounts, WarningType.NO_STORAGE, role, noStorage);
                addWarning(warningCounts, WarningType.STORAGE_FULL, role, storageFull);
                addWarning(warningCounts, WarningType.INVENTORY_FULL, role, inventoryFull);
                addWarning(warningCounts, WarningType.NO_WORK_AREA, role, noWorkArea);
                addWarning(warningCounts, WarningType.NO_TARGETS, role, noTargets);
                addWarning(warningCounts, WarningType.TOO_FAR, role, tooFar);
                addWarning(warningCounts, WarningType.MISSING_TOOLS, role, missingTools);
                addWarning(warningCounts, WarningType.MISSING_MATERIALS, role, missingMaterials);
                addWarning(warningCounts, WarningType.MATERIAL_STORAGE_UNREACHABLE, role, materialStorageUnreachable);
                addWarning(warningCounts, WarningType.MATERIAL_INVENTORY_FULL, role, materialInventoryFull);
                addWarning(warningCounts, WarningType.BUILD_SITE_UNREACHABLE, role, buildSiteUnreachable);
                addWarning(warningCounts, WarningType.UNPAID, role, unpaid);
                workers.add(new WorkerRow(
                        villager.getUUID(),
                        VillagerPresetNameRegistry.resolveDisplayName(villager).getString(),
                        role,
                        status,
                        targetText(brain),
                        diagnostic,
                        storageAssigned,
                        storageCount,
                        routeAssigned ? session.route().nodes().size() : session.area().horizontalRadius(),
                        routeAssigned || session.area().usable(),
                        routeAssigned ? routeDescription(session.route()) : session.area().centerDescription(),
                        routeAssigned ? session.route().nodes().size() : session.area().horizontalRadius(),
                        routeAssigned ? 0 : session.area().verticalRadius(),
                        routeAssigned ? "route" : session.area().usable() ? session.jobSite().sourceId() : "missing",
                        workModeText(role, session.state()),
                        dailyWage,
                        VillagerCurrencyResources.format(level.getServer(), dailyWage),
                        VillagerCurrencyResources.text(level.getServer()).textColor(),
                        contractDays,
                        recurringPayment,
                        workerIsWorking,
                        WorldLocation.of(level.dimension(), villager.blockPosition()),
                        storageFull,
                        missingMaterials,
                        materialStorageUnreachable,
                        materialInventoryFull,
                        buildSiteUnreachable,
                        inventoryFull,
                        unpaid,
                        noStorage,
                        noWorkArea,
                        noTargets,
                        tooFar,
                        missingTools
                ));
        }

        List<ClipboardWorkforceSnapshot.JobSummary> jobs = new ArrayList<>();
        for (HiredVillagerRole role : HiredVillagerRole.values()) {
            jobs.add(new ClipboardWorkforceSnapshot.JobSummary(role, jobCounts.getOrDefault(role, 0)));
        }
        List<WarningSummary> warnings = warningCounts.entrySet().stream()
                .map(entry -> new WarningSummary(entry.getKey().type(), entry.getKey().role(), entry.getValue()))
                .toList();
        return new ClipboardWorkforceSnapshot(
                workers.size(),
                -1,
                working,
                idle,
                warnings.stream().mapToInt(WarningSummary::count).sum(),
                assignedStorage,
                paymentContainers,
                dailyWages,
                jobs,
                workers,
                warnings
        );
    }

    private static boolean isWorking(HiredWorkerTaskState state, boolean enabled) {
        if (!enabled) {
            return false;
        }
        return !state.isWaitingState();
    }

    private record LiveSyncState(ClipboardWorkforceSnapshot snapshot, long gameTime) {
    }
    private static boolean isExpectedWorkExcursion(HiredWorkerTaskState state) {
        return state.keepsStorageTarget() || state == HiredWorkerTaskState.RETURNING_TO_WORK_AREA;
    }

    private static boolean requiresWorkAssignment(HiredVillagerRole role) {
        return role != HiredVillagerRole.BUILDER && role != HiredVillagerRole.NITWIT;
    }

    /** Lightweight status used by persistent world markers between clipboard snapshots. */
    public static WorkerStatus previewStatus(
            HiredVillagerRole role,
            HiredWorkerBrain.Snapshot brain,
            HiredJobInventory inventory) {
        return HiredWorkerFacts.previewStatus(role, brain, inventory);
    }

    public static WorkPreview workPreview(HiredWorkSession session, HiredWorkerBrain.Snapshot brain) {
        var facts = HiredWorkerFacts.observe(session, brain);
        return new WorkPreview(facts.status(), PersonalWorkerDiagnostics.preview(session, brain, facts));
    }

    public record WorkPreview(WorkerStatus status, String diagnostic) {
    }

    private static String targetText(HiredWorkerBrain.Snapshot brain) {
        if (brain.targetPos() != null) {
            return HiredWorkerBrain.formatPos(brain.targetPos());
        }
        if (brain.storageTargetPos() != null) {
            return HiredWorkerBrain.formatPos(brain.storageTargetPos());
        }
        return "";
    }

    private static String workModeText(HiredVillagerRole role, net.minecraft.nbt.CompoundTag state) {
        String temporary = HiredTemporaryWorkService.summary(state);
        if (!temporary.isEmpty()) return temporary;
        return switch (role) {
            case MINING -> HiredMiningMode.fromState(state).label();
            case COMBAT -> HiredCombatMode.fromState(state).label();
            case HUNTING -> HiredHuntingTargets.selectionLabel(state);
            default -> "";
        };
    }

    private static String routeDescription(HiredRoute route) {
        int count = route == null ? 0 : route.nodes().size();
        int branches = route == null ? 0 : route.branches().size();
        return count + " node" + (count == 1 ? "" : "s")
                + (branches == 0 ? "" : ", " + branches + " branch" + (branches == 1 ? "" : "es"))
                + (route != null && route.loop() ? ", loop" : ", back-and-forth");
    }

    private static void addWarning(Map<WarningKey, Integer> warnings, WarningType type, HiredVillagerRole role, boolean active) {
        if (active) {
            warnings.merge(new WarningKey(type, role), 1, Integer::sum);
        }
    }

    private record WarningKey(WarningType type, HiredVillagerRole role) {
    }
}
