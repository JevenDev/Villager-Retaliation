package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.debug.HiredStressGridService;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderTaskState;
import com.jvn.villagerretaliation.interaction.work.HiredRoleWorkerRegistry;
import com.jvn.villagerretaliation.interaction.work.brewing.BrewingWorker;
import com.jvn.villagerretaliation.interaction.work.HiredPathMemory;
import com.jvn.villagerretaliation.interaction.work.HiredRouteNavigator;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.HiredWorkPlan;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState;
import com.jvn.villagerretaliation.interaction.work.logging.LoggingWorker;
import com.jvn.villagerretaliation.interaction.work.WorkResult;
import com.jvn.villagerretaliation.reputation.VillagerAggressionPolicy;
import com.jvn.villagerretaliation.reputation.VillagerReputationAdvancements;
import com.jvn.villagerretaliation.skill.HiredWorkSkillGrowthService;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil;
import com.jvn.villagerretaliation.villager.VillagerRecoveryService;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

public final class HiredVillagerWorkService {
    private static final String STORAGE_FULL_NOTICE_SHOWN_TAG = "StorageFullNoticeShown";
    private static final String VANILLA_REST_PAUSED_TAG = "VanillaRestPaused";
    private static final String DISABLED_WORK_PAUSED_TAG = "DisabledWorkPaused";
    private static final String DESPISED_HIRER_WORK_PAUSED_TAG = "DespisedHirerWorkPaused";
    public static final String WAITING_FOR_HIRER_STATUS = "interaction.work.status.waiting_for_hirer";
    private static final String STORAGE_FULL_NOTICE = "interaction.work.status.storage_full";
    private static final String PAUSED_FOR_COMMAND_STATUS = "interaction.work.status.paused_for_command";
    private static final String REFUSING_DESPISED_HIRER_STATUS = "interaction.work.status.refusing_despised_hirer";
    private static final String TIRED_STATUS = "interaction.work.status.tired";
    private static final String SLEEPING_STATUS = "interaction.work.status.sleeping";
    private static final double HIRED_WORK_NOTICE_RADIUS = 32.0D;
    private static final double HIRED_WORK_NOTICE_RADIUS_SQR = HIRED_WORK_NOTICE_RADIUS * HIRED_WORK_NOTICE_RADIUS;
    private static final int MIN_ROUTINE_REPORT_COOLDOWN_SECONDS = 600;

    private HiredVillagerWorkService() {
    }

    public static void onVillagerTickPost(Villager villager) {
        if (com.jvn.villagerretaliation.quest.follower.QuestFollowerService.controls(villager)) return;
        if (!(villager.level() instanceof ServerLevel level) || shouldSkipHiredWorkTick(villager)) {
            return;
        }
        com.jvn.villagerretaliation.quest.tutorial.TutorialWorkService.reconcileContinuation(villager);
        HiredVillagerContractService.expireHireContractIfNeeded(level, villager);
        VillagerWorkAuthority contract = VillagerWorkAuthority.resolve(level, villager);
        HiredTemporaryWorkService.validate(level, villager, contract);
        if (!contract.active()) {
            return;
        }
        if (!com.jvn.villagerretaliation.quest.tutorial.TutorialWorkService.canTick(villager, contract.role())) {
            VillagerTaskNavigationUtil.stopNavigationAndClearTargets(villager);
            setStatus(state(villager), "interaction.work.tutorial.paused");
            return;
        }
        HiredStressGridService.keepStressWorkerAwake(villager);
        HiredStressGridService.maintainStressWorker(level, villager);

        boolean decisionOpportunity = HiredWorkDecisionScheduler.isDecisionOpportunity(level, villager);
        boolean decisionTick = decisionOpportunity && HiredWorkDecisionScheduler.tryAcquire(level, villager);

        HiredWorkSession session = HiredWorkSession.create(level, villager, contract.role());
        if (contract.civic() && !contract.canWork(level, villager)) {
            pauseForVanillaRest(level, villager, session);
            return;
        }
        if (VillagerRecruitmentService.isFollowingAnyPlayer(villager)) {
            VillagerTaskNavigationUtil.restoreHiredWaterTraversal(villager);
            pauseForRecruitmentCommand(level, villager, session);
            return;
        }
        if (HiredVillagerFocusService.isVanillaRestActive(villager)
                && HiredVillagerFocusService.shouldUseVanillaRest(level, villager)) {
            pauseForVanillaRest(level, villager, session);
            return;
        }
        clearVanillaRestPause(session.state());

        if (contract.awaitingAutoPayment()) {
            VillagerTaskNavigationUtil.enableHiredWaterTraversal(villager);
            VillagerTaskNavigationUtil.moveInWaterTowardNavigationTarget(level, villager, HiredWorkAreaNavigation.WORK_AREA_RETURN_WALK_SPEED);
            return;
        }

        ServerPlayer hirer = contract.onlineOwner(level);
        if (hirer == null && !contract.civic()) {
            CompoundTag waitingState = state(villager);
            initializeDefaults(waitingState, villager);
            VillagerTaskNavigationUtil.restoreHiredWaterTraversal(villager);
            VillagerTaskNavigationUtil.stopNavigationAndClearTargets(villager);
            HiredWorkerBrain.setState(waitingState, HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
            setStatus(waitingState, WAITING_FOR_HIRER_STATUS);
            return;
        }
        if (hirer != null && VillagerAggressionPolicy.shouldAttackOnSight(villager, hirer)) {
            pauseForDespisedHirer(level, villager, session);
            return;
        }
        session.state().remove(DESPISED_HIRER_WORK_PAUSED_TAG);
        if (!HiredTemporaryWorkService.ready(level, villager, session)) {
            VillagerTaskNavigationUtil.stopHiredNavigation(villager);
            return;
        }
        if (VillagerRetaliationVillagerBrainUtil.hasThreatMemories(villager.getBrain())) {
            VillagerTaskNavigationUtil.restoreHiredWaterTraversal(villager);
            return;
        }

        if (session.worker() == null) {
            VillagerTaskNavigationUtil.restoreHiredWaterTraversal(villager);
            VillagerTaskNavigationUtil.stopNavigationAndClearTargets(villager);
            HiredWorkerBrain.setState(session.state(), HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
            setStatus(session.state(), "interaction.work.status.no_routine");
            return;
        }
        if (!session.state().getBoolean("Enabled")) {
            VillagerTaskNavigationUtil.restoreHiredWaterTraversal(villager);
            if (!session.state().getBoolean(DISABLED_WORK_PAUSED_TAG)) {
                if (session.worker() != null) {
                    session.worker().pause(level, villager, session.context());
                } else {
                    HiredWorkerBrain.setState(session.state(), HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
                }
                VillagerTaskNavigationUtil.stopNavigationAndClearTargets(villager);
                session.state().putBoolean(DISABLED_WORK_PAUSED_TAG, true);
            }
            setStatus(session.state(), "interaction.work.status.paused");
            return;
        }
        session.state().remove(DISABLED_WORK_PAUSED_TAG);
        if (session.role() == HiredVillagerRole.FARMING) {
            VillagerTaskNavigationUtil.enableHiredFarmingWaterTraversal(villager);
        } else {
            VillagerTaskNavigationUtil.enableHiredWaterTraversal(villager);
        }
        VillagerTaskNavigationUtil.moveInWaterTowardNavigationTarget(level, villager, HiredWorkAreaNavigation.WORK_AREA_RETURN_WALK_SPEED);
        HiredVillagerFocusService.suppressNonWorkAi(level, villager, session.context());
        if (com.jvn.villagerretaliation.interaction.work.courier.HiredTransferService.pendingCivic(villager)) {
            if (decisionTick) {
                WorkResult delivery = com.jvn.villagerretaliation.interaction.work.CourierWorker.resumePending(level, villager, session.context());
                setStatus(session.state(), delivery.status(), delivery.replacements());
                HiredWorkSkillGrowthService.onPractice(level, villager, hirer, HiredVillagerRole.COURIER, session.state(), delivery.practice());
            }
            return;
        }
        if (HiredWorkAreaNavigation.shouldReturnToWorkArea(session) && HiredWorkAreaNavigation.returnVillagerToWorkArea(level, villager, session, decisionTick)) {
            return;
        }
        session.worker().maintain(level, villager, session.context());

        if (!decisionTick) {
            return;
        }

        long nextWorkGameTime = session.state().getLong("NextWorkGameTime");
        if (nextWorkGameTime > level.getGameTime()) {
            setStatus(session.state(), "interaction.work.status.cooldown");
            return;
        }

        if (isOutputBackpressured(villager, session)) {
            com.jvn.villagerretaliation.interaction.work.production.HiredProductionService.releasePlanned(level, villager, session.role());
            VillagerTaskNavigationUtil.stopHiredNavigation(villager);
            HiredWorkerBrain.clearFailure(session.context());
            HiredWorkerBrain.setState(session.context(), HiredWorkerTaskState.PAUSED_OUTPUT_BACKPRESSURE);
            setStatus(session.state(), "interaction.work.status.paused");
            return;
        }
        if (HiredWorkerBrain.snapshot(session.state(), level.getGameTime()).taskState()
                == HiredWorkerTaskState.PAUSED_OUTPUT_BACKPRESSURE) {
            HiredWorkerBrain.clearFailure(session.context());
        }

        WorkResult result = session.worker() instanceof com.jvn.villagerretaliation.interaction.work.AbstractBlockWorker worker
                ? worker.supplyQuota(level, villager, session.context()) : null;
        if (result == null) result = session.worker().tick(level, villager, hirer, session.context());
        setStatus(session.state(), result.status(), result.replacements());
        result.blocker().save(session.state());
        if (hirer != null) {
            maybeNotifyStorageFull(level, villager, hirer, session.context(), session.state());
        }
        HiredWorkSkillGrowthService.onPractice(
                level, villager, hirer, session.role(), session.state(), result.practice());
        HiredTemporaryWorkService.completed(level, villager, session, result);
        if (result.completed()) {
            if (hirer != null) {
                VillagerReputationAdvancements.onHiredAssignmentCompleted(hirer, villager);
            }
            recordCompletedTask(session.state());
            session.state().putLong("NextWorkGameTime", level.getGameTime() + completedTaskCooldownTicks(session.efficiency()));
            HiredWorkerBrain.Snapshot snapshot = HiredWorkerBrain.snapshot(session.state(), level.getGameTime());
            if (hirer != null) {
                maybeNotify(
                        level,
                        villager,
                        hirer,
                        session.state(),
                        HiredVillagerRoleSettings.workFinalReportMessageKey(session.role()),
                        workReportReplacements(hirer, level, villager, session, snapshot),
                        hiredWorkReportNoticeCooldownTicks());
            }
            if (session.role() == HiredVillagerRole.BUILDER
                    && contract.oneOffBuilderJob()
                    && !BuilderTaskState.hasTask(session.state())) {
                HiredVillagerContractService.finishOneOffBuilderJob(level, villager, result.status());
            }
        }
    }

    private static boolean isOutputBackpressured(
            Villager villager,
            HiredWorkSession session) {
        if (session.role() == HiredVillagerRole.COURIER
                || !session.inventory().hasOutputItems()) {
            return false;
        }
        List<ItemStack> outputs = session.inventory().collectOutputItems().stream()
                .map(HiredJobInventory.OutputStack::stack)
                .toList();
        return AssignedStorageService.assignedOutputStateFor(villager, outputs)
                == AssignedStorageService.AssignedOutputState.BACKPRESSURED;
    }

    private static boolean shouldSkipHiredWorkTick(Villager villager) {
        boolean stressWorker = HiredStressGridService.isStressWorker(villager);
        return villager.isBaby()
                || !villager.isAlive()
                || villager.isTrading()
                || VillagerConversationService.isConversing(villager)
                || VillagerRecoveryService.isForcingRecovery(villager)
                || (!stressWorker && villager.getTarget() != null)
                || villager.getLastHurtByMob() != null
                || !VillagerWorkAuthority.hasWork(villager);
    }

    private static void pauseForVanillaRest(ServerLevel level, Villager villager, HiredWorkSession session) {
        VillagerTaskNavigationUtil.restoreHiredWaterTraversal(villager);
        if (!session.state().getBoolean(VANILLA_REST_PAUSED_TAG)) {
            if (session.worker() != null) {
                session.worker().pause(level, villager, session.context());
            } else {
                HiredWorkerBrain.setState(session.state(), HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
            }
            if (!villager.getNavigation().isDone() && !isNavigatingToHome(level, villager)) {
                villager.getNavigation().stop();
            }
            session.state().putBoolean(VANILLA_REST_PAUSED_TAG, true);
        }
        setStatus(session.state(), villager.isSleeping() ? SLEEPING_STATUS : TIRED_STATUS);
        com.jvn.villagerretaliation.interaction.work.WorkBlocker.RESTING.save(session.state());
    }

    private static boolean isNavigatingToHome(ServerLevel level, Villager villager) {
        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        if (navigationTarget == null) {
            return false;
        }
        return villager.getBrain().getMemory(MemoryModuleType.HOME)
                .filter(home -> home.dimension() == level.dimension())
                .map(GlobalPos::pos)
                .map(homePos -> homePos.distSqr(navigationTarget) <= 4.0D)
                .orElse(false);
    }

    private static void clearVanillaRestPause(CompoundTag state) {
        if (!state.getBoolean(VANILLA_REST_PAUSED_TAG)) {
            return;
        }
        state.remove(VANILLA_REST_PAUSED_TAG);
        HiredWorkerBrain.setState(state, HiredWorkerTaskState.IDLE, null);
        String status = state.getString("Status");
        if (TIRED_STATUS.equals(status) || SLEEPING_STATUS.equals(status)) {
            setStatus(state, "interaction.work.status.waiting_tick");
        }
    }

    private static void pauseForRecruitmentCommand(ServerLevel level, Villager villager, HiredWorkSession session) {
        if (!PAUSED_FOR_COMMAND_STATUS.equals(session.state().getString("Status"))) {
            if (session.role() == HiredVillagerRole.BUILDER && BuilderTaskState.hasTask(session.state())) {
                session.context().setProgressTicks(0);
                HiredWorkPlan.clear(session.context());
                HiredWorkerBrain.clearFailure(session.context());
                HiredWorkerBrain.setState(session.context(), HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
            } else if (session.worker() != null) {
                session.worker().pause(level, villager, session.context());
            }
        }
        setStatus(session.state(), PAUSED_FOR_COMMAND_STATUS);
    }

    public static void pauseForRecruitmentCommand(ServerLevel level, Villager villager) {
        if (level == null || villager == null || !HiredVillagerContractService.isHired(level, villager)) {
            return;
        }
        VillagerTaskNavigationUtil.restoreHiredWaterTraversal(villager);
        pauseForRecruitmentCommand(level, villager, HiredWorkSession.active(level, villager));
    }

    public static boolean hasEffectiveWorkArea(ServerLevel level, Villager villager, HiredWorkSession session) {
        return HiredWorkAreaNavigation.hasEffectiveWorkArea(level, villager, session);
    }

    public static boolean hasEffectiveWorkArea(
            ServerLevel level,
            Villager villager,
            HiredVillagerRole role,
            HiredWorkContext context) {
        return HiredWorkAreaNavigation.hasEffectiveWorkArea(level, villager, role, context);
    }

    public static boolean isInsideEffectiveWorkArea(
            ServerLevel level,
            Villager villager,
            HiredVillagerRole role,
            HiredWorkContext context,
            BlockPos pos) {
        return HiredWorkAreaNavigation.isInsideEffectiveWorkArea(level, villager, role, context, pos);
    }

    public static boolean isInsideEffectiveWorkArea(HiredVillagerRole role, HiredWorkContext context, BlockPos pos) {
        return HiredWorkAreaNavigation.isInsideEffectiveWorkArea(role, context, pos);
    }

    public static boolean usesRouteAssignment(HiredVillagerRole role, HiredWorkContext context) {
        return HiredWorkAreaNavigation.usesRouteAssignment(role, context);
    }

    public static boolean hasClaimedJobSiteInLevel(ServerLevel level, Villager villager) {
        return HiredWorkAreaNavigation.hasClaimedJobSiteInLevel(level, villager);
    }

    public static BlockPos claimedJobSitePos(ServerLevel level, Villager villager) {
        return HiredWorkAreaNavigation.claimedJobSitePos(level, villager);
    }

    public static void clearRuntimeState() {
        HiredRoleWorkerRegistry.clearRuntimeState();
        HiredWorkSession.clearRuntimeState();
        HiredWorkDecisionScheduler.clearRuntimeState();
    }

    private static void pauseForDespisedHirer(ServerLevel level, Villager villager, HiredWorkSession session) {
        VillagerTaskNavigationUtil.restoreHiredWaterTraversal(villager);
        if (!session.state().getBoolean(DESPISED_HIRER_WORK_PAUSED_TAG)) {
            if (session.worker() != null) {
                session.worker().pause(level, villager, session.context());
            }
            VillagerTaskNavigationUtil.stopNavigationAndClearTargets(villager);
            session.state().putBoolean(DESPISED_HIRER_WORK_PAUSED_TAG, true);
        }
        setStatus(session.state(), REFUSING_DESPISED_HIRER_STATUS);
    }

    /** Returns whether durable worker state currently describes an active work operation. */
    public static boolean isActivelyWorking(Villager villager) {
        return HiredWorkStateStore.isActivelyWorking(villager);
    }

    public static void clearInheritedStateForNewborn(Villager child) {
        HiredWorkStateStore.clearInheritedStateForNewborn(child);
    }

    public static void onVillagerLeaveLevel(ServerLevel level, Villager villager) {
        if (!HiredVillagerContractService.isHired(level, villager)
                && VillagerAssignmentStore.snapshot(villager).state() != VillagerAssignmentState.CIVIC) {
            return;
        }

        VillagerTaskNavigationUtil.stopNavigationAndClearTargets(villager);
        HiredRoleWorkerRegistry.clearRuntimeState(villager);
        HiredWorkSession.invalidate(villager);
    }

    public static void sendStatus(ServerPlayer player, ServerLevel level, Villager villager) {
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        HiredWorkerBrain.Snapshot snapshot = HiredWorkerBrain.snapshot(session.state(), level.getGameTime());
        VillagerInteractionService.sendVillagerNotice(
                player,
                villager,
                HiredVillagerRoleSettings.workReportMessageKey(session.role()),
                workReportReplacements(player, level, villager, session, snapshot)
        );
    }

    public static List<String> debugLines(ServerLevel level, Villager villager) {
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        HiredWorkerBrain.Snapshot snapshot = HiredWorkerBrain.snapshot(session.state(), level.getGameTime());
        List<HiredJobInventory.OutputStack> outputs = session.inventory().collectOutputItems();
        int outputStacks = outputs.size();
        int outputItems = outputs.stream().mapToInt(output -> output.stack().getCount()).sum();
        boolean hasAssignedStorage = AssignedStorageService.hasAssignedStorage(level, villager);
        boolean canDepositNow = AssignedStorageService.canInteractWithAssignedStorage(villager, pos -> session.area().contains(pos));
        HiredPathMemory.PathCreationDebug pathDebug = HiredPathMemory.pathCreationDebug(level, villager);

        List<String> lines = new ArrayList<>();
        lines.add("Hired worker debug: role=" + session.role().serializedName()
                + ", hired=" + HiredVillagerContractService.isHired(level, villager)
                + ", enabled=" + session.state().getBoolean("Enabled")
                + ", efficiency=" + session.efficiency() + "%");
        HiredWorkDecisionScheduler.DebugSnapshot decisionDebug =
                HiredWorkDecisionScheduler.debugSnapshot(level, villager);
        lines.add("Decision budget: limit=" + decisionDebug.budget()
                + ", eligible=" + decisionDebug.eligible()
                + ", granted=" + decisionDebug.granted()
                + ", deferredThisTick=" + decisionDebug.deferredThisTick()
                + ", deferredTotal=" + decisionDebug.totalDeferred()
                + ", workerDeferred=" + decisionDebug.workerDeferred());
        lines.add("State: task=" + snapshot.taskState().id()
                + ", progress=" + snapshot.progressTicks()
                + ", target=" + HiredWorkerBrain.formatPos(snapshot.targetPos())
                + ", storageTarget=" + HiredWorkerBrain.formatPos(snapshot.storageTargetPos()));
        lines.add("Plan: objective=" + HiredWorkPlan.objectiveType(session.context())
                + ", anchor=" + HiredWorkerBrain.formatPos(HiredWorkPlan.objectiveAnchor(session.context()))
                + ", queuedTargets=" + HiredWorkPlan.size(session.context()));
        if (session.role() == HiredVillagerRole.LOGGING) {
            lines.add(LoggingWorker.debugSummary(session.context()));
        }
        lines.add("Failure: reason=" + valueOrNone(snapshot.failureReason())
                + ", retryCooldown=" + snapshot.retryCooldownTicks()
                + ", lastScan=" + valueOrNone(snapshot.lastTargetScanResult()));
        lines.add("Pathing: createdThisTick=" + pathDebug.currentTickCount()
                + ", lastTick=" + pathDebug.lastTickCount()
                + ", total=" + pathDebug.totalCount()
                + ", cacheHitsThisTick=" + pathDebug.cacheHitsThisTick()
                + ", cacheHitsLastTick=" + pathDebug.cacheHitsLastTick()
                + ", cacheHitTotal=" + pathDebug.cacheHitTotal()
                + ", failureStreak=" + pathDebug.failureStreak()
                + ", pathRetryCooldown=" + pathDebug.retryCooldownTicks()
                + ", cachedApproaches=" + pathDebug.unreachableApproaches());
        lines.add("Work area: " + areaDescription(session.area())
                + ", assigned=" + session.area().explicitlyAssigned()
                + ", usable=" + session.area().usable()
                + ", source=" + session.jobSite().sourceLabel()
                + ", anchor=" + HiredWorkerBrain.formatPos(session.jobSite().anchor())
                + ", radius=" + session.area().horizontalRadius()
                + ", verticalRadius=" + session.area().verticalRadius()
                + ", maxRadius=" + session.maxRadius());
        lines.add("Route: nodes=" + session.route().nodes().size()
                + ", loop=" + session.route().loop()
                + ", supportsRoutes=" + HiredVillagerRoleSettings.supportsRoutes(session.role()));
        lines.add("Inventory/storage: outputStacks=" + outputStacks
                + ", outputItems=" + outputItems
                + ", autoDeposit=" + session.state().getBoolean("AutoDepositOutputs")
                + ", assignedSupplies=" + session.state().getBoolean("UseAssignedStorageForSupplies")
                + ", assignedStorage=" + hasAssignedStorage
                + ", canDepositNow=" + canDepositNow);
        lines.add("Status: " + valueOrNone(snapshot.status()));
        return lines;
    }

    public static void resetForNewContract(ServerLevel level, Villager villager) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        BrewingWorker.clearOrder(state);
        state.putBoolean("Enabled", true);
        state.remove("NextWorkGameTime");
        state.remove("ProgressTicks");
        HiredRoute.clear(state);
        HiredRouteNavigator.clearProgress(state);
        resetReportProgress(state);
        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        int radius = HiredVillagerRoleSettings.defaultHorizontalRadius(role, HiredWorkParameters.MIN_WORK_RADIUS, maxWorkRadius(level, villager, role));
        int verticalRadius = HiredVillagerRoleSettings.defaultVerticalRadius(role, maxWorkRadius(level, villager, role));
        BlockPos center = villager.blockPosition();
        HiredWorkArea.fromCenter(center, radius, verticalRadius, false).save(state);
        stopWork(level, villager, role, "interaction.work.status.no_work_area");
    }

    public static void resetReportProgress(ServerLevel level, Villager villager) {
        HiredWorkStateStore.resetReportProgress(villager);
    }

    public static void stopWork(ServerLevel level, Villager villager, HiredVillagerRole role, String status) {
        stopWork(level, villager, role, status, Map.of());
    }

    public static void stopWork(
            ServerLevel level,
            Villager villager,
            HiredVillagerRole role,
            String status,
            Map<String, String> replacements) {
        cancelWork(level, villager, role, status, replacements);
    }

    public static void pauseWork(ServerLevel level, Villager villager, HiredVillagerRole role, String status) {
        pauseWork(level, villager, role, status, Map.of());
    }

    public static void pauseWork(
            ServerLevel level,
            Villager villager,
            HiredVillagerRole role,
            String status,
            Map<String, String> replacements) {
        HiredWorkLifecycle.pauseWork(level, villager, role, status, replacements);
    }

    public static void cancelWork(ServerLevel level, Villager villager, HiredVillagerRole role, String status) {
        cancelWork(level, villager, role, status, Map.of());
    }

    public static void cancelWork(
            ServerLevel level,
            Villager villager,
            HiredVillagerRole role,
            String status,
            Map<String, String> replacements) {
        HiredWorkLifecycle.cancelWork(level, villager, role, status, replacements);
    }

    public static void finishWork(ServerLevel level, Villager villager, HiredVillagerRole role, String status) {
        finishWork(level, villager, role, status, Map.of());
    }

    public static void finishWork(
            ServerLevel level,
            Villager villager,
            HiredVillagerRole role,
            String status,
            Map<String, String> replacements) {
        HiredWorkLifecycle.finishWork(level, villager, role, status, replacements);
    }
    public static void toggleEnabled(ServerPlayer player, ServerLevel level, Villager villager) {
        HiredWorkConfigurationService.toggleEnabled(player, level, villager);
    }

    public static void changeRadius(ServerPlayer player, ServerLevel level, Villager villager, int delta) {
        HiredWorkConfigurationService.changeRadius(player, level, villager, delta);
    }

    public static void changeVerticalRadius(ServerPlayer player, ServerLevel level, Villager villager, int delta) {
        HiredWorkConfigurationService.changeVerticalRadius(player, level, villager, delta);
    }

    public static void changeBounds(ServerPlayer player, ServerLevel level, Villager villager, Direction direction, int delta) {
        HiredWorkConfigurationService.changeBounds(player, level, villager, direction, delta);
    }

    public static void setWorkCenterHere(ServerPlayer player, ServerLevel level, Villager villager) {
        HiredWorkConfigurationService.setWorkCenterHere(player, level, villager);
    }

    public static void resetWorkCenterToVillager(ServerPlayer player, ServerLevel level, Villager villager) {
        HiredWorkConfigurationService.resetWorkCenterToVillager(player, level, villager);
    }

    public static void previewWorkArea(ServerPlayer player, ServerLevel level, Villager villager) {
        HiredWorkConfigurationService.previewWorkArea(player, level, villager);
    }

    public static void toggleAssignedSupplies(ServerPlayer player, ServerLevel level, Villager villager) {
        HiredWorkConfigurationService.toggleAssignedSupplies(player, level, villager);
    }

    public static void toggleAutoDeposit(ServerPlayer player, ServerLevel level, Villager villager) {
        HiredWorkConfigurationService.toggleAutoDeposit(player, level, villager);
    }

    public static void configureRole(ServerPlayer player, ServerLevel level, Villager villager, HiredVillagerRole role) {
        HiredWorkConfigurationService.configureRole(player, level, villager, role);
    }

    public static void toggleLoggingFilter(ServerPlayer player, ServerLevel level, Villager villager, String filterId) {
        HiredWorkConfigurationService.toggleLoggingFilter(player, level, villager, filterId);
    }

    public static void toggleLoggingOption(ServerPlayer player, ServerLevel level, Villager villager, String optionId) {
        HiredWorkConfigurationService.toggleLoggingOption(player, level, villager, optionId);
    }

    public static void toggleHorizontalMiningFloorPatching(ServerPlayer player, ServerLevel level, Villager villager) {
        HiredWorkConfigurationService.toggleHorizontalMiningFloorPatching(player, level, villager);
    }

    public static void toggleFarmingOption(ServerPlayer player, ServerLevel level, Villager villager, String optionId) {
        HiredWorkConfigurationService.toggleFarmingOption(player, level, villager, optionId);
    }

    public static void toggleHuntingTarget(ServerPlayer player, ServerLevel level, Villager villager, String targetId) {
        HiredWorkConfigurationService.toggleHuntingTarget(player, level, villager, targetId);
    }

    public static void toggleAnimalBreedingTarget(ServerPlayer player, ServerLevel level, Villager villager, String targetId) {
        HiredWorkConfigurationService.toggleAnimalBreedingTarget(player, level, villager, targetId);
    }

    public static void toggleAnimalHandlingOption(ServerPlayer player, ServerLevel level, Villager villager, String optionId) {
        HiredWorkConfigurationService.toggleAnimalHandlingOption(player, level, villager, optionId);
    }

    public static void setAnimalCullCap(ServerPlayer player, ServerLevel level, Villager villager, int cap) {
        HiredWorkConfigurationService.setAnimalCullCap(player, level, villager, cap);
    }

    public static boolean canManageWork(ServerLevel level, Villager villager, ServerPlayer player) {
        return HiredWorkConfigurationService.canManageWork(level, villager, player);
    }

    public static void initializeWorkArea(ServerLevel level, Villager villager) {
        HiredWorkConfigurationService.initializeWorkArea(level, villager);
    }

    public static HiredWorkArea workArea(ServerLevel level, Villager villager) {
        return HiredWorkConfigurationService.workArea(level, villager);
    }

    public static HiredJobSite jobSite(ServerLevel level, Villager villager) {
        return HiredWorkConfigurationService.jobSite(level, villager);
    }

    public static HiredRoute route(ServerLevel level, Villager villager) {
        return HiredWorkConfigurationService.route(level, villager);
    }

    public static boolean setRoute(ServerPlayer player, ServerLevel level, Villager villager, HiredRoute route) {
        return HiredWorkConfigurationService.setRoute(player, level, villager, route);
    }

    public static boolean clearRoute(ServerPlayer player, ServerLevel level, Villager villager) {
        return HiredWorkConfigurationService.clearRoute(player, level, villager);
    }

    static HiredJobSite jobSite(
            ServerLevel level,
            Villager villager,
            HiredVillagerRole role,
            CompoundTag state,
            int maxRadius) {
        return HiredWorkParameters.jobSite(level, villager, role, state, maxRadius);
    }

    public static boolean setWorkArea(ServerPlayer player, ServerLevel level, Villager villager, BlockPos first, BlockPos second) {
        return HiredWorkConfigurationService.setWorkArea(player, level, villager, first, second);
    }

    static CompoundTag state(Villager villager) {
        return HiredWorkStateStore.state(villager);
    }

    static void initializeDefaults(CompoundTag state, Villager villager) {
        HiredWorkStateStore.initializeDefaults(state, villager);
    }

    private static HiredWorkArea workArea(CompoundTag state, Villager villager) {
        return HiredWorkStateStore.workArea(state, villager);
    }

    static HiredWorkArea workAreaWithinMax(CompoundTag state, Villager villager, int maxRadius) {
        return HiredWorkStateStore.workAreaWithinMax(state, villager, maxRadius);
    }

    static int maxWorkRadius(ServerLevel level, Villager villager, HiredVillagerRole role) {
        return HiredWorkParameters.maxWorkRadius(level, villager, role);
    }

    static int maxWorkRadius(ServerLevel level, Villager villager, HiredVillagerRole role, int roleScore) {
        return HiredWorkParameters.maxWorkRadius(level, villager, role, roleScore);
    }

    public static int maxWorkRadiusForScore(
            HiredVillagerRole role,
            int roleScore,
            int configuredDefaultRadius,
            int configuredMaxRadius) {
        return HiredWorkParameters.maxWorkRadiusForScore(role, roleScore, configuredDefaultRadius, configuredMaxRadius);
    }

    private static String areaDescription(HiredWorkArea area) {
        return area.usable() ? area.boundsDescription() : "No work area assigned";
    }

    private static void setStatus(CompoundTag state, String status) {
        HiredWorkStateStore.setStatus(state, status);
    }

    private static void setStatus(CompoundTag state, String status, Map<String, String> replacements) {
        HiredWorkStateStore.setStatus(state, status, replacements);
    }

    private static Map<String, String> statusReplacements(CompoundTag state) {
        return HiredWorkStateStore.statusReplacements(state);
    }

    private static void recordCompletedTask(CompoundTag state) {
        HiredWorkStateStore.recordCompletedTask(state);
    }

    private static void resetReportProgress(CompoundTag state) {
        HiredWorkStateStore.resetReportProgress(state);
    }

    private static int completedTasks(CompoundTag state) {
        return HiredWorkStateStore.completedTasks(state);
    }

    private static String completedTasksPhrase(int count) {
        return count == 1 ? "1 completed task" : count + " completed tasks";
    }

    private static String outputSummary(HiredWorkSession session) {
        List<HiredJobInventory.OutputStack> outputs = session.inventory().collectOutputItems();
        int outputItems = outputs.stream().mapToInt(output -> output.stack().getCount()).sum();
        if (outputItems <= 0) {
            return "no output waiting";
        }
        return outputItems == 1 ? "1 output item waiting" : outputItems + " output items waiting";
    }

    private static Map<String, String> workReportReplacements(
            ServerPlayer player,
            ServerLevel level,
            Villager villager,
            HiredWorkSession session,
            HiredWorkerBrain.Snapshot snapshot) {
        String targetDescription = describeCurrentTarget(player, level, villager, snapshot);
        int completedTasks = completedTasks(session.state());
        Map<String, String> replacements = new LinkedHashMap<>();
        replacements.put("activity", describeWorkActivity(player, level, villager, session, snapshot, targetDescription));
        replacements.put("status_detail", describeStatusDetail(player, level, villager, session, targetDescription));
        replacements.put("work_area", describeWorkArea(player, level, villager, session.area()));
        replacements.put("efficiency", Integer.toString(session.efficiency()));
        replacements.put("target", targetDescription);
        replacements.put("role", session.role().label());
        replacements.put("completed_tasks", Integer.toString(completedTasks));
        replacements.put("completed_tasks_phrase", completedTasksPhrase(completedTasks));
        replacements.put("completed_tasks_plural", completedTasks == 1 ? "" : "s");
        replacements.put("progress_summary", "So far I have finished " + completedTasksPhrase(completedTasks) + ".");
        replacements.put("output_summary", outputSummary(session));
        return replacements;
    }

    private static String valueOrNone(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }

    private static String describeWorkActivity(
            ServerPlayer player,
            ServerLevel level,
            Villager villager,
            HiredWorkSession session,
            HiredWorkerBrain.Snapshot snapshot,
            String targetDescription) {
        if (!session.state().getBoolean("Enabled")) {
            return resolveWorkMessage(player, level, villager, "interaction.work.activity.paused", Map.of());
        }
        String key = switch (snapshot.taskState()) {
            case IDLE, AWAITING_INSTRUCTION -> "interaction.work.activity.waiting";
            case SELECTING_TARGET -> "interaction.work.activity.selecting_target";
            case MOVING_TO_TARGET -> "interaction.work.activity.moving_to_target";
            case VALIDATING_TARGET -> "interaction.work.activity.validating_target";
            case WORKING -> "interaction.work.activity.working";
            case COLLECTING_OUTPUT -> "interaction.work.activity.collecting_output";
            case FINDING_CHAIN_TARGET -> "interaction.work.activity.finding_chain_target";
            case MOVING_TO_STORAGE -> "interaction.work.activity.moving_to_storage";
            case RETURNING_TO_WORK_AREA -> "interaction.work.activity.returning_to_work_area";
            case DEPOSITING -> "interaction.work.activity.depositing";
            case WAITING_FOR_MATERIALS -> "interaction.work.activity.waiting_for_materials";
            case PAUSED_OUTPUT_BACKPRESSURE -> "interaction.work.activity.waiting";
            case PAUSED_STORAGE_FULL -> "interaction.work.activity.paused_storage_full";
            case NO_WORK_AREA -> "interaction.work.activity.no_work_area";
            case PAUSED_FULL_INVENTORY -> "interaction.work.activity.paused_full_inventory";
            case PAUSED_NO_STORAGE -> "interaction.work.activity.paused_no_storage";
            case FAILED_COOLDOWN -> "interaction.work.activity.failed_cooldown";
            case PAUSED_MISSING_TOOL -> "interaction.work.activity.paused_missing_tool";
        };
        return resolveWorkMessage(player, level, villager, key, Map.of("target", targetDescription));
    }

    private static String describeStatusDetail(
            ServerPlayer player,
            ServerLevel level,
            Villager villager,
            HiredWorkSession session,
            String targetDescription) {
        String status = session.state().getString("Status");
        if (status == null || status.isBlank()) {
            return resolveWorkMessage(player, level, villager, "interaction.work.status.none", Map.of());
        }
        Map<String, String> replacements = new java.util.LinkedHashMap<>(statusReplacements(session.state()));
        replacements.put("efficiency", Integer.toString(session.efficiency()));
        replacements.put("work_area", describeWorkArea(player, level, villager, session.area()));
        replacements.put("target", targetDescription);
        replacements.put("role", session.role().label());
        String detail = resolveWorkMessage(player, level, villager, status, replacements);
        var brain = HiredWorkerBrain.snapshot(session.state(), level.getGameTime());
        var facts = HiredWorkerFacts.observe(session, brain);
        String diagnostic = VillagerWorkAuthority.resolve(level, villager).civic()
                ? com.jvn.villagerretaliation.settlement.SettlementWorkerDiagnostics.describe(facts)
                : PersonalWorkerDiagnostics.preview(session, brain, facts);
        return diagnostic.isBlank() ? detail : detail + " " + diagnostic;
    }

    private static String describeWorkArea(ServerPlayer player, ServerLevel level, Villager villager, HiredWorkArea area) {
        return area.usable()
                ? area.boundsDescription()
                : resolveWorkMessage(player, level, villager, "interaction.work.area.none", Map.of());
    }

    private static String describeCurrentTarget(ServerPlayer player, ServerLevel level, Villager villager, HiredWorkerBrain.Snapshot snapshot) {
        BlockPos target = currentTarget(snapshot);
        return target == null
                ? resolveWorkMessage(player, level, villager, "interaction.work.target.none", Map.of())
                : HiredWorkerBrain.formatPos(target);
    }

    private static String describeCurrentTarget(HiredWorkerBrain.Snapshot snapshot) {
        BlockPos target = currentTarget(snapshot);
        return target == null ? "none" : HiredWorkerBrain.formatPos(target);
    }

    private static BlockPos currentTarget(HiredWorkerBrain.Snapshot snapshot) {
        BlockPos target = snapshot.taskState().keepsStorageTarget() ? snapshot.storageTargetPos() : snapshot.targetPos();
        if (target == null && snapshot.storageTargetPos() != null) {
            target = snapshot.storageTargetPos();
        }
        if (target == null && snapshot.targetPos() != null) {
            target = snapshot.targetPos();
        }
        return target;
    }

    private static String resolveWorkMessage(
            ServerPlayer player,
            ServerLevel level,
            Villager villager,
            String key,
            Map<String, String> replacements) {
        DialogueContext context = VillagerInteractionService.createDialogueContext(level, player, villager);
        return VillagerDialogueResources.message(context, key, replacements).orElse(key);
    }

    static int efficiencyPercent(ServerLevel level, Villager villager, HiredVillagerRole role, CompoundTag state, HiredJobInventory inventory) {
        return HiredWorkParameters.efficiencyPercent(level, villager, role, state, inventory);
    }

    static int efficiencyPercent(
            ServerLevel level,
            Villager villager,
            HiredVillagerRole role,
            CompoundTag state,
            HiredJobInventory inventory,
            int roleScore) {
        return HiredWorkParameters.efficiencyPercent(level, villager, role, state, inventory, roleScore);
    }

    public static int calculateEfficiencyPercent(
            int configuredBase,
            int skillWorkSpeedPercent,
            int moodModifier,
            boolean missingRequiredTool,
            int configuredMinimum,
            int configuredMaximum) {
        return HiredWorkParameters.calculateEfficiencyPercent(configuredBase, skillWorkSpeedPercent, moodModifier, missingRequiredTool, configuredMinimum, configuredMaximum);
    }

    public static int effectiveWorkTickInterval(int efficiency) {
        return HiredWorkParameters.effectiveWorkTickInterval(efficiency);
    }

    public static int effectiveWorkTickInterval(int baseInterval, int efficiency) {
        return HiredWorkParameters.effectiveWorkTickInterval(baseInterval, efficiency);
    }

    public static int completedTaskCooldownTicks(int efficiency) {
        return HiredWorkParameters.completedTaskCooldownTicks(efficiency);
    }

    private static boolean maybeNotify(ServerLevel level, Villager villager, ServerPlayer hirer, CompoundTag state, String message, long cooldownTicks) {
        return maybeNotify(level, villager, hirer, state, message, Map.of(), cooldownTicks);
    }

    private static boolean maybeNotify(
            ServerLevel level,
            Villager villager,
            ServerPlayer hirer,
            CompoundTag state,
            String message,
            Map<String, String> replacements,
            long cooldownTicks) {
        long now = level.getGameTime();
        if (!canHearHiredWorkNotice(level, villager, hirer)) {
            return false;
        }
        if (state.contains("LastNoticeTick", Tag.TAG_LONG) && now - state.getLong("LastNoticeTick") < cooldownTicks) {
            return false;
        }
        state.putLong("LastNoticeTick", now);
        VillagerInteractionService.sendVillagerNotice(hirer, villager, message, replacements, HIRED_WORK_NOTICE_RADIUS);
        return true;
    }

    private static boolean canHearHiredWorkNotice(ServerLevel level, Villager villager, ServerPlayer hirer) {
        return hirer.serverLevel() == level && hirer.distanceToSqr(villager) <= HIRED_WORK_NOTICE_RADIUS_SQR;
    }

    private static void maybeNotifyStorageFull(
            ServerLevel level,
            Villager villager,
            ServerPlayer hirer,
            HiredWorkContext context,
            CompoundTag state) {
        HiredWorkerBrain.Snapshot brain = HiredWorkerBrain.snapshot(state, level.getGameTime());
        if (brain.taskState() != HiredWorkerTaskState.PAUSED_STORAGE_FULL) {
            if (!context.hasOutputToDeposit()) {
                state.remove(STORAGE_FULL_NOTICE_SHOWN_TAG);
                state.remove(HiredWorkContext.OUTPUT_DEPOSITED_THIS_STORAGE_TRIP_TAG);
            }
            return;
        }
        if (state.getBoolean(HiredWorkContext.OUTPUT_DEPOSITED_THIS_STORAGE_TRIP_TAG)) {
            return;
        }
        if (state.getBoolean(STORAGE_FULL_NOTICE_SHOWN_TAG)) {
            return;
        }
        if (maybeNotify(level, villager, hirer, state, STORAGE_FULL_NOTICE, hiredWorkNoticeCooldownTicks())) {
            state.putBoolean(STORAGE_FULL_NOTICE_SHOWN_TAG, true);
        }
    }

    private static long hiredWorkNoticeCooldownTicks() {
        int seconds = Mth.clamp(VillagerRetaliationConfig.HIRED_WORK_NOTICE_COOLDOWN_SECONDS.get(), 0, 600);
        return 20L * seconds;
    }

    private static long hiredWorkReportNoticeCooldownTicks() {
        int configuredSeconds = Mth.clamp(VillagerRetaliationConfig.HIRED_WORK_NOTICE_COOLDOWN_SECONDS.get(), 0, 600);
        int seconds = configuredSeconds <= 0 ? 0 : Math.max(MIN_ROUTINE_REPORT_COOLDOWN_SECONDS, configuredSeconds);
        return 20L * seconds;
    }
}
