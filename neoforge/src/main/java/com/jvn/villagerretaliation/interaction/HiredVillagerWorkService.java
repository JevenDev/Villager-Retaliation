package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.interaction.work.HiredRoleWorkerRegistry;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.HiredWorkPlan;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState;
import com.jvn.villagerretaliation.interaction.work.WorkResult;
import com.jvn.villagerretaliation.mood.VillagerMood;
import com.jvn.villagerretaliation.mood.VillagerMoodService;
import com.jvn.villagerretaliation.mood.VillagerMoodState;
import com.jvn.villagerretaliation.reputation.VillagerAggressionPolicy;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.skill.HiredWorkSkillGrowthService;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.pathfinder.Path;

public final class HiredVillagerWorkService {
    private static final String TAG = "VillagerRetaliationHiredWork";
    private static final String NEXT_WORK_AREA_RETURN_PATH_GAME_TIME_TAG = "NextWorkAreaReturnPathGameTime";
    private static final String STORAGE_FULL_NOTICE_SHOWN_TAG = "StorageFullNoticeShown";
    private static final String STORAGE_FULL_NOTICE = "I have no where left to deposit these collected items.";
    private static final long DAY_TICKS = 24000L;
    private static final int MIN_WORK_RADIUS = 4;
    private static final int SKILL_RADIUS_BASELINE = 50;
    private static final int MAX_SKILLED_WORK_RADIUS = 32;
    private static final int WORK_AREA_RETURN_PATH_RETRY_TICKS = 20;
    private static final int MAX_RETURN_TARGETS_TO_PATHFIND = 32;
    private static final float WORK_AREA_RETURN_WALK_SPEED = 0.5F;
    private static final int WORK_AREA_RETURN_CLOSE_ENOUGH = 2;

    private HiredVillagerWorkService() {
    }

    public static void onVillagerTickPost(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)
                || HiredVillagerFocusService.shouldSkipHiredFocus(level, villager)) {
            return;
        }

        UUID hirerId = HiredVillagerContractService.getHirer(level, villager).orElse(null);
        if (hirerId == null || !(level.getServer().getPlayerList().getPlayer(hirerId) instanceof ServerPlayer hirer)) {
            CompoundTag waitingState = state(villager);
            initializeDefaults(waitingState, villager);
            VillagerTaskNavigationUtil.stopNavigationAndClearTargets(villager);
            HiredWorkerBrain.setState(waitingState, HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
            setStatus(waitingState, "I am waiting for the one who hired me to return.");
            return;
        }
        if (VillagerAggressionPolicy.shouldAttackOnSight(villager, hirer)) {
            return;
        }
        if (VillagerRetaliationVillagerBrainUtil.hasThreatMemories(villager.getBrain())) {
            return;
        }

        HiredWorkSession session = HiredWorkSession.active(level, villager);
        if (session.worker() == null) {
            VillagerTaskNavigationUtil.stopNavigationAndClearTargets(villager);
            HiredWorkerBrain.setState(session.state(), HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
            setStatus(session.state(), "I have no proper work routine for " + session.role().label() + " yet.");
            return;
        }
        if (!session.state().getBoolean("Enabled")) {
            VillagerTaskNavigationUtil.stopNavigationAndClearTargets(villager);
            HiredWorkerBrain.setState(session.state(), HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
            setStatus(session.state(), "You have told me to hold for now.");
            return;
        }
        if (VillagerRecruitmentService.isFollowingAnyPlayer(villager)) {
            pauseForRecruitmentCommand(level, villager, session);
            return;
        }

        HiredVillagerFocusService.suppressNonWorkAi(level, villager, session.context());
        if (returnVillagerToWorkArea(level, villager, session)) {
            return;
        }
        session.worker().maintain(level, villager, session.context());

        int interval = Math.max(10, VillagerRetaliationConfig.HIRED_WORK_TICK_INTERVAL.get());
        if (Math.floorMod(level.getGameTime() + villager.getUUID().getLeastSignificantBits(), interval) != 0L) {
            return;
        }

        long nextWorkGameTime = session.state().getLong("NextWorkGameTime");
        if (nextWorkGameTime > level.getGameTime()) {
            setStatus(session.state(), "I am catching my breath before the next task.");
            return;
        }

        handleDailyFood(level, villager, hirer, session);
        WorkResult result = session.worker().tick(level, villager, hirer, session.context());
        setStatus(session.state(), result.status() + " Efficiency: " + session.efficiency() + "%.");
        maybeNotifyStorageFull(level, villager, hirer, session.context(), session.state());
        if (result.awardsSkillGrowth()) {
            HiredWorkSkillGrowthService.onWorkCompleted(level, villager, hirer, session.role(), session.state());
        }
        if (result.completed()) {
            session.state().putLong("NextWorkGameTime", level.getGameTime() + nextTaskCooldownTicks(session.efficiency()));
            maybeNotify(level, villager, hirer, session.state(), result.status(), 20L * 30L);
        }
    }

    private static void pauseForRecruitmentCommand(ServerLevel level, Villager villager, HiredWorkSession session) {
        session.worker().stop(level, villager, session.context());
        VillagerTaskNavigationUtil.stopNavigationAndClearTargets(villager);
        HiredWorkPlan.clear(session.context());
        HiredWorkerBrain.clearFailure(session.context());
        HiredWorkerBrain.setState(session.context(), HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
        setStatus(session.state(), "I am following your command, so I have paused my hired work.");
    }

    private static boolean returnVillagerToWorkArea(ServerLevel level, Villager villager, HiredWorkSession session) {
        HiredWorkContext context = session.context();
        CompoundTag state = session.state();
        if (!context.hasWorkArea()) {
            state.remove(NEXT_WORK_AREA_RETURN_PATH_GAME_TIME_TAG);
            return false;
        }
        if (context.isInsideWorkArea(villager.blockPosition())) {
            state.remove(NEXT_WORK_AREA_RETURN_PATH_GAME_TIME_TAG);
            return false;
        }
        HiredWorkerBrain.Snapshot brain = HiredWorkerBrain.snapshot(state, level.getGameTime());
        if ((brain.taskState() == HiredWorkerTaskState.MOVING_TO_STORAGE
                || brain.taskState() == HiredWorkerTaskState.DEPOSITING
                || brain.taskState() == HiredWorkerTaskState.PAUSED_STORAGE_FULL)
                && brain.storageTargetPos() != null) {
            state.remove(NEXT_WORK_AREA_RETURN_PATH_GAME_TIME_TAG);
            return false;
        }
        if (canWorkFromOutsideArea(session, villager.blockPosition())) {
            state.remove(NEXT_WORK_AREA_RETURN_PATH_GAME_TIME_TAG);
            return false;
        }

        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        if (!villager.getNavigation().isDone() && navigationTarget != null && context.isInsideWorkArea(navigationTarget)) {
            setWorkAreaReturnWalkTarget(villager, navigationTarget);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, navigationTarget);
            setStatus(state, "I have drifted beyond the work bounds, so I am heading back.");
            return true;
        }

        long gameTime = level.getGameTime();
        if (gameTime < state.getLong(NEXT_WORK_AREA_RETURN_PATH_GAME_TIME_TAG)) {
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, null);
            setStatus(state, "I am outside the work bounds and waiting a moment before trying the path back again.");
            return true;
        }

        ReturnPath returnPath = findWorkAreaReturnPath(level, villager, context);
        if (returnPath == null) {
            if (VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, context.workCenter(), WORK_AREA_RETURN_WALK_SPEED)) {
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, context.workCenter());
                setStatus(state, "I am using the ladder to get back into the work area.");
            } else {
                villager.getNavigation().stop();
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
                state.putLong(NEXT_WORK_AREA_RETURN_PATH_GAME_TIME_TAG, gameTime + WORK_AREA_RETURN_PATH_RETRY_TICKS);
                setStatus(state, "I am outside the work bounds, and I have not found a good way back yet.");
            }
            return true;
        }

        if (Math.abs(villager.blockPosition().getY() - returnPath.target().getY()) > 2
                && VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, returnPath.target(), WORK_AREA_RETURN_WALK_SPEED)) {
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, returnPath.target());
            setStatus(state, "I am using the ladder to get back into the work area.");
            return true;
        }

        if (villager.getNavigation().moveTo(returnPath.path(), WORK_AREA_RETURN_WALK_SPEED)) {
            setWorkAreaReturnWalkTarget(villager, returnPath.target());
            state.remove(NEXT_WORK_AREA_RETURN_PATH_GAME_TIME_TAG);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, returnPath.target());
            setStatus(state, "I have drifted beyond the work bounds, so I am heading back.");
        } else {
            if (VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, returnPath.target(), WORK_AREA_RETURN_WALK_SPEED)) {
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, returnPath.target());
                setStatus(state, "I am using the ladder to get back into the work area.");
            } else {
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
                state.putLong(NEXT_WORK_AREA_RETURN_PATH_GAME_TIME_TAG, gameTime + WORK_AREA_RETURN_PATH_RETRY_TICKS);
                setStatus(state, "I know I must return to the work bounds, but I could not get moving yet.");
            }
        }
        return true;
    }

    private static void setWorkAreaReturnWalkTarget(Villager villager, BlockPos target) {
        villager.getBrain().eraseMemory(MemoryModuleType.PATH);
        villager.getBrain().setMemory(
                MemoryModuleType.WALK_TARGET,
                new WalkTarget(new BlockPosTracker(target), WORK_AREA_RETURN_WALK_SPEED, WORK_AREA_RETURN_CLOSE_ENOUGH));
    }

    private static boolean canWorkFromOutsideArea(HiredWorkSession session, BlockPos pos) {
        return session.role() == HiredVillagerRole.MINING
                && HiredMiningMode.fromState(session.state()).excavatesArea()
                && isNearWorkArea(session.context(), pos);
    }

    private static boolean isNearWorkArea(HiredWorkContext context, BlockPos pos) {
        if (pos == null) {
            return false;
        }
        int dx = distanceOutside(pos.getX(), context.workMin().getX(), context.workMax().getX());
        int dy = distanceOutside(pos.getY(), context.workMin().getY(), context.workMax().getY());
        int dz = distanceOutside(pos.getZ(), context.workMin().getZ(), context.workMax().getZ());
        return dx <= 4 && dy <= 2 && dz <= 4;
    }

    private static int distanceOutside(int value, int min, int max) {
        if (value < min) {
            return min - value;
        }
        if (value > max) {
            return value - max;
        }
        return 0;
    }

    private static ReturnPath findWorkAreaReturnPath(ServerLevel level, Villager villager, HiredWorkContext context) {
        BlockPos clamped = new BlockPos(
                Mth.clamp(villager.blockPosition().getX(), context.workMin().getX(), context.workMax().getX()),
                Mth.clamp(villager.blockPosition().getY(), context.workMin().getY(), context.workMax().getY()),
                Mth.clamp(villager.blockPosition().getZ(), context.workMin().getZ(), context.workMax().getZ()));

        List<BlockPos> candidates = new ArrayList<>();
        candidates.add(clamped);
        candidates.add(context.workCenter());
        for (BlockPos raw : BlockPos.betweenClosed(
                clamped.offset(-3, -2, -3),
                clamped.offset(3, 2, 3))) {
            BlockPos candidate = raw.immutable();
            if (context.isInsideWorkArea(candidate)) {
                candidates.add(candidate);
            }
        }

        candidates.sort(Comparator.comparingDouble(pos -> returnTargetScore(villager, context, pos)));
        int evaluated = 0;
        for (BlockPos candidate : candidates) {
            if (evaluated >= MAX_RETURN_TARGETS_TO_PATHFIND) {
                break;
            }
            if (!context.isLoaded(level, candidate)) {
                continue;
            }
            evaluated++;
            Path path = villager.getNavigation().createPath(candidate, 0);
            if (path != null && path.canReach()) {
                return new ReturnPath(candidate, path);
            }
        }
        return null;
    }

    private static double returnTargetScore(Villager villager, HiredWorkContext context, BlockPos pos) {
        double villagerDistance = villager.distanceToSqr(pos.getCenter());
        double centerDistance = pos.distSqr(context.workCenter());
        return villagerDistance + centerDistance * 0.25D;
    }

    private record ReturnPath(BlockPos target, Path path) {
    }

    public static void clearRuntimeState() {
        HiredRoleWorkerRegistry.clearRuntimeState();
    }

    public static void onVillagerLeaveLevel(ServerLevel level, Villager villager) {
        if (!HiredVillagerContractService.isHired(level, villager)) {
            return;
        }

        VillagerTaskNavigationUtil.stopNavigationAndClearTargets(villager);
        HiredRoleWorkerRegistry.clearRuntimeState(villager);
    }

    public static void sendStatus(ServerPlayer player, ServerLevel level, Villager villager) {
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        HiredWorkerBrain.Snapshot snapshot = HiredWorkerBrain.snapshot(session.state(), level.getGameTime());
        VillagerInteractionService.sendVillagerNotice(
                player,
                villager,
                workReportMessageKey(session.role()),
                Map.of(
                        "activity", describeWorkActivity(session, snapshot),
                        "status_detail", describeStatusDetail(session),
                        "work_area", areaDescription(session.area()),
                        "efficiency", Integer.toString(session.efficiency()),
                        "target", describeCurrentTarget(snapshot)
                )
        );
    }

    public static List<String> debugLines(ServerLevel level, Villager villager) {
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        HiredWorkerBrain.Snapshot snapshot = HiredWorkerBrain.snapshot(session.state(), level.getGameTime());
        int outputStacks = session.inventory().collectOutputItems().size();
        int outputItems = session.inventory().collectOutputItems().stream().mapToInt(output -> output.stack().getCount()).sum();
        boolean hasAssignedStorage = AssignedStorageService.hasAssignedStorage(level, villager);
        boolean canDepositNow = AssignedStorageService.canInteractWithAssignedStorage(villager, pos -> session.area().contains(pos));

        List<String> lines = new ArrayList<>();
        lines.add("Hired worker debug: role=" + session.role().serializedName()
                + ", hired=" + HiredVillagerContractService.isHired(level, villager)
                + ", enabled=" + session.state().getBoolean("Enabled")
                + ", efficiency=" + session.efficiency() + "%");
        lines.add("State: task=" + snapshot.taskState().id()
                + ", progress=" + snapshot.progressTicks()
                + ", target=" + HiredWorkerBrain.formatPos(snapshot.targetPos())
                + ", storageTarget=" + HiredWorkerBrain.formatPos(snapshot.storageTargetPos()));
        lines.add("Plan: objective=" + HiredWorkPlan.objectiveType(session.context())
                + ", anchor=" + HiredWorkerBrain.formatPos(HiredWorkPlan.objectiveAnchor(session.context()))
                + ", queuedTargets=" + HiredWorkPlan.size(session.context()));
        lines.add("Failure: reason=" + valueOrNone(snapshot.failureReason())
                + ", retryCooldown=" + snapshot.retryCooldownTicks()
                + ", lastScan=" + valueOrNone(snapshot.lastTargetScanResult()));
        lines.add("Work area: " + areaDescription(session.area())
                + ", assigned=" + session.area().explicitlyAssigned()
                + ", usable=" + session.area().usable()
                + ", radius=" + session.area().horizontalRadius()
                + ", verticalRadius=" + session.area().verticalRadius()
                + ", maxRadius=" + session.maxRadius());
        lines.add("Inventory/storage: outputStacks=" + outputStacks
                + ", outputItems=" + outputItems
                + ", autoDeposit=" + session.state().getBoolean("AutoDepositOutputs")
                + ", assignedStorage=" + hasAssignedStorage
                + ", canDepositNow=" + canDepositNow);
        lines.add("Status: " + valueOrNone(snapshot.status()));
        return lines;
    }

    public static void resetForNewContract(ServerLevel level, Villager villager) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        state.putBoolean("Enabled", true);
        state.remove("NextWorkGameTime");
        state.remove("ProgressTicks");
        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        int radius = roleDefaultHorizontalRadius(role, maxWorkRadius(level, villager, role));
        int verticalRadius = roleDefaultVerticalRadius(role, maxWorkRadius(level, villager, role));
        BlockPos center = villager.blockPosition();
        HiredWorkArea.fromCenter(center, radius, verticalRadius, false).save(state);
        stopWork(level, villager, role, "No work area assigned.");
    }

    public static void stopWork(ServerLevel level, Villager villager, HiredVillagerRole role, String status) {
        HiredWorkSession session = HiredWorkSession.create(level, villager, role);
        if (session.worker() != null) {
            session.worker().stop(level, villager, session.context());
        } else {
            session.context().setProgressTicks(0);
        }
        session.state().remove("NextWorkGameTime");
        setStatus(session.state(), status);
    }

    public static void toggleEnabled(ServerPlayer player, ServerLevel level, Villager villager) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        state.putBoolean("Enabled", !state.getBoolean("Enabled"));
        setStatus(state, state.getBoolean("Enabled") ? "I am back to work." : "You have asked me to pause my work.");
        VillagerInteractionService.sendVillagerNotice(player, villager, state.getString("Status"));
    }

    public static void changeRadius(ServerPlayer player, ServerLevel level, Villager villager, int delta) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        int max = maxWorkRadius(level, villager, role);
        HiredWorkArea area = workAreaWithinMax(state, villager, max);
        int radius = HiredWorkArea.clampRadius(area.horizontalRadius() + delta, MIN_WORK_RADIUS, max);
        HiredWorkArea.fromCenter(area.center(), radius, area.verticalRadius(), true).clampedTo(max).save(state);
        setStatus(state, "My job site horizontal range is now " + radius + " blocks. I cannot manage beyond " + max + ".");
        VillagerInteractionService.sendVillagerNotice(player, villager, state.getString("Status"));
    }

    public static void changeVerticalRadius(ServerPlayer player, ServerLevel level, Villager villager, int delta) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        int max = maxWorkRadius(level, villager, role);
        HiredWorkArea area = workAreaWithinMax(state, villager, max);
        int verticalRadius = HiredWorkArea.clampRadius(area.verticalRadius() + delta, 1, max);
        HiredWorkArea.fromCenter(area.center(), area.horizontalRadius(), verticalRadius, true).clampedTo(max).save(state);
        setStatus(state, "My job site vertical range is now " + verticalRadius + " blocks. I cannot manage beyond " + max + ".");
        VillagerInteractionService.sendVillagerNotice(player, villager, state.getString("Status"));
    }

    public static void changeBounds(ServerPlayer player, ServerLevel level, Villager villager, Direction direction, int delta) {
        if (direction == null || delta == 0) {
            return;
        }
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        int max = maxWorkRadius(level, villager, role);
        HiredWorkArea area = workAreaWithinMax(state, villager, max);
        BlockPos center = area.center();
        BlockPos min = area.min();
        BlockPos maxPos = area.max();
        switch (direction) {
            case NORTH -> min = new BlockPos(min.getX(), min.getY(), Mth.clamp(min.getZ() - delta, center.getZ() - max, center.getZ()));
            case SOUTH -> maxPos = new BlockPos(maxPos.getX(), maxPos.getY(), Mth.clamp(maxPos.getZ() + delta, center.getZ(), center.getZ() + max));
            case WEST -> min = new BlockPos(Mth.clamp(min.getX() - delta, center.getX() - max, center.getX()), min.getY(), min.getZ());
            case EAST -> maxPos = new BlockPos(Mth.clamp(maxPos.getX() + delta, center.getX(), center.getX() + max), maxPos.getY(), maxPos.getZ());
            case DOWN -> min = new BlockPos(min.getX(), Mth.clamp(min.getY() - delta, center.getY() - max, center.getY()), min.getZ());
            case UP -> maxPos = new BlockPos(maxPos.getX(), Mth.clamp(maxPos.getY() + delta, center.getY(), center.getY() + max), maxPos.getZ());
        }
        new HiredWorkArea(center, min, maxPos, area.horizontalRadius(), area.verticalRadius(), true, true)
                .clampedTo(max)
                .save(state);
        HiredWorkArea updated = workArea(state, villager);
        setStatus(state, "My job site bounds are now " + dimensions(updated) + " (" + updated.boundsDescription() + ").");
        VillagerInteractionService.sendVillagerNotice(player, villager, state.getString("Status"));
    }

    public static void setWorkCenterHere(ServerPlayer player, ServerLevel level, Villager villager) {
        assignCenteredWorkArea(player, level, villager, player.blockPosition(), false);
    }

    public static void resetWorkCenterToVillager(ServerPlayer player, ServerLevel level, Villager villager) {
        assignCenteredWorkArea(player, level, villager, villager.blockPosition(), true);
    }

    public static void previewWorkArea(ServerPlayer player, ServerLevel level, Villager villager) {
        HiredWorkArea area = workArea(level, villager);
        if (!area.usable()) {
            VillagerInteractionService.sendVillagerNotice(player, villager, "No work area assigned.");
            return;
        }
        com.jvn.villagerretaliation.item.HiredStorageClipboardItem.sendWorkAreaOutline(player, level, villager);
    }

    public static void toggleAssignedSupplies(ServerPlayer player, ServerLevel level, Villager villager) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        state.putBoolean("UseAssignedStorageForSupplies", !state.getBoolean("UseAssignedStorageForSupplies"));
        setStatus(state, state.getBoolean("UseAssignedStorageForSupplies")
                ? "I may draw supplies from the assigned storage now."
                : "I will stick to what I carry and what is in my work gear.");
        VillagerInteractionService.sendVillagerNotice(player, villager, state.getString("Status"));
    }

    public static void toggleAutoDeposit(ServerPlayer player, ServerLevel level, Villager villager) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        state.putBoolean("AutoDepositOutputs", !state.getBoolean("AutoDepositOutputs"));
        setStatus(state, state.getBoolean("AutoDepositOutputs")
                ? "I will put away my finished goods on my own."
                : "I will keep what I gather until told otherwise.");
        VillagerInteractionService.sendVillagerNotice(player, villager, state.getString("Status"));
    }

    public static void configureRole(ServerPlayer player, ServerLevel level, Villager villager, HiredVillagerRole role) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        switch (role) {
            case COMBAT -> {
                HiredCombatMode current = HiredCombatMode.fromState(state);
                HiredCombatMode next = current.next();
                state.putString(HiredCombatMode.STATE_TAG, next.serializedName());
                setStatus(state, "My combat orders are now " + next.label() + ".");
            }
            case MINING -> {
                HiredMiningMode current = HiredMiningMode.fromState(state);
                HiredMiningMode next = current.next();
                state.putString(HiredMiningMode.STATE_TAG, next.serializedName());
                HiredWorkSession session = HiredWorkSession.active(level, villager);
                HiredWorkPlan.clear(session.context());
                session.context().setProgressTicks(0);
                setStatus(state, "My mining orders are now " + next.label() + ".");
            }
            case LOGGING -> {
                String current = state.getString("LoggingFilter");
                state.putString("LoggingFilter", current == null || current.isBlank() || "any".equals(current) ? "oak_log" : "any");
                setStatus(state, "I will now favor " + state.getString("LoggingFilter") + " when I look for timber.");
            }
            case FARMING -> {
                String current = state.getString("CropMode");
                state.putString("CropMode", "harvest_replant".equals(current) ? "harvest_only" : "harvest_replant");
                setStatus(state, "My farming approach is now " + state.getString("CropMode") + ".");
            }
            case BREWING -> setStatus(state, "I have adjusted my brewing routine as much as I can for now.");
            case NAVIGATION -> setStatus(state, "I have shifted my navigation focus for the next route.");
            case ANIMAL_HANDLING -> setStatus(state, "I have changed what I am watching for with the animals.");
            case NITWIT -> setStatus(state, "I have rearranged my very particular style of helping.");
            default -> setStatus(state, "There is no extra setup for " + role.label() + " just yet.");
        }
        VillagerInteractionService.sendVillagerNotice(player, villager, state.getString("Status"));
    }

    public static boolean canManageWork(ServerLevel level, Villager villager, ServerPlayer player) {
        return HiredVillagerContractService.isHiredBy(level, villager, player);
    }

    public static void initializeWorkArea(ServerLevel level, Villager villager) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        workAreaWithinMax(state, villager, maxWorkRadius(level, villager, role));
    }

    public static HiredWorkArea workArea(ServerLevel level, Villager villager) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        return workAreaWithinMax(state, villager, maxWorkRadius(level, villager, role));
    }

    public static boolean setWorkArea(ServerPlayer player, ServerLevel level, Villager villager, BlockPos first, BlockPos second) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        int maxRadius = maxWorkRadius(level, villager, role);
        HiredWorkArea requested = HiredWorkArea.fromBounds(first, second, true);
        HiredWorkArea area = requested.clampedTo(maxRadius);
        boolean capped = requested.horizontalRadius() > area.horizontalRadius()
                || requested.verticalRadius() > area.verticalRadius();
        area.save(state);
        String capNotice = capped
                ? "This area is too large for this worker; clamped to their current skill range. "
                : "";
        setStatus(state, capNotice + "Custom work box selected: " + dimensions(area) + " blocks.");
        VillagerInteractionService.sendVillagerNotice(player, villager, state.getString("Status"));
        return true;
    }

    private static void assignCenteredWorkArea(ServerPlayer player, ServerLevel level, Villager villager, BlockPos center, boolean villagerCenter) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        int maxRadius = maxWorkRadius(level, villager, role);
        HiredWorkArea current = workAreaWithinMax(state, villager, maxRadius);
        int horizontalRadius = current.explicitlyAssigned()
                ? current.horizontalRadius()
                : roleDefaultHorizontalRadius(role, maxRadius);
        int verticalRadius = current.explicitlyAssigned()
                ? current.verticalRadius()
                : roleDefaultVerticalRadius(role, maxRadius);
        HiredWorkArea.fromCenter(center, horizontalRadius, verticalRadius, true).clampedTo(maxRadius).save(state);
        setStatus(state, (villagerCenter ? "My job site is reset to my position: " : "My job site center is now here: ")
                + workArea(state, villager).rangeDescription() + ".");
        VillagerInteractionService.sendVillagerNotice(player, villager, state.getString("Status"));
    }

    static CompoundTag state(Villager villager) {
        CompoundTag persistentData = villager.getPersistentData();
        if (!persistentData.contains(TAG, Tag.TAG_COMPOUND)) {
            persistentData.put(TAG, new CompoundTag());
        }
        return persistentData.getCompound(TAG);
    }

    static void initializeDefaults(CompoundTag state, Villager villager) {
        boolean hadStoredArea = state.contains(HiredWorkArea.WORK_CENTER_POS_TAG, Tag.TAG_LONG)
                && state.contains(HiredWorkArea.WORK_MIN_POS_TAG, Tag.TAG_LONG)
                && state.contains(HiredWorkArea.WORK_MAX_POS_TAG, Tag.TAG_LONG);
        if (!state.contains("Enabled", Tag.TAG_BYTE)) {
            state.putBoolean("Enabled", true);
        }
        if (!state.contains(HiredWorkArea.RADIUS_TAG, Tag.TAG_INT)) {
            state.putInt(HiredWorkArea.RADIUS_TAG, Mth.clamp(
                    VillagerRetaliationConfig.HIRED_WORK_DEFAULT_RADIUS.get(),
                    MIN_WORK_RADIUS,
                    baseWorkRadiusCap()));
        }
        if (!state.contains(HiredWorkArea.WORK_AREA_ASSIGNED_TAG, Tag.TAG_BYTE)) {
            state.putBoolean(HiredWorkArea.WORK_AREA_ASSIGNED_TAG, false);
        }
        if (!state.contains("UseAssignedStorageForSupplies", Tag.TAG_BYTE)) {
            state.putBoolean("UseAssignedStorageForSupplies", false);
        }
        if (!state.contains("AutoDepositOutputs", Tag.TAG_BYTE)) {
            state.putBoolean("AutoDepositOutputs", true);
        }
        if (!state.contains("LoggingFilter", Tag.TAG_STRING)) {
            state.putString("LoggingFilter", "any");
        }
        if (!state.contains("CropMode", Tag.TAG_STRING)) {
            state.putString("CropMode", "harvest_replant");
        }
        if (!state.contains("NavigationTargetType", Tag.TAG_STRING)) {
            state.putString("NavigationTargetType", "interesting");
        }
        if (!state.contains(HiredCombatMode.STATE_TAG, Tag.TAG_STRING)) {
            state.putString(HiredCombatMode.STATE_TAG, HiredCombatMode.GUARD.serializedName());
        }
        if (!state.contains("Status", Tag.TAG_STRING)) {
            state.putString("Status", "Waiting for work tick.");
        }
        HiredWorkerBrain.initialize(state);
        if (!hadStoredArea) {
            int radius = Mth.clamp(state.getInt(HiredWorkArea.RADIUS_TAG), MIN_WORK_RADIUS, baseWorkRadiusCap());
            HiredWorkArea.fromCenter(villager.blockPosition(), radius, Math.min(radius, 8), state.getBoolean(HiredWorkArea.WORK_AREA_ASSIGNED_TAG)).save(state);
        }
    }

    private static BlockPos workCenter(CompoundTag state, Villager villager) {
        if (!state.contains(HiredWorkArea.WORK_CENTER_POS_TAG, Tag.TAG_LONG)) {
            state.putLong(HiredWorkArea.WORK_CENTER_POS_TAG, villager.blockPosition().asLong());
        }
        return BlockPos.of(state.getLong(HiredWorkArea.WORK_CENTER_POS_TAG));
    }

    private static HiredWorkArea workArea(CompoundTag state, Villager villager) {
        if (!state.contains(HiredWorkArea.WORK_MIN_POS_TAG, Tag.TAG_LONG) || !state.contains(HiredWorkArea.WORK_MAX_POS_TAG, Tag.TAG_LONG)) {
            initializeDefaults(state, villager);
        }
        return HiredWorkArea.fromState(state, workCenter(state, villager));
    }

    static HiredWorkArea workAreaWithinMax(CompoundTag state, Villager villager, int maxRadius) {
        int safeMaxRadius = Math.max(MIN_WORK_RADIUS, maxRadius);
        HiredWorkArea area = workArea(state, villager);
        HiredWorkArea clamped = area.clampedTo(safeMaxRadius);
        if (!clamped.min().equals(area.min()) || !clamped.max().equals(area.max()) || state.getInt(HiredWorkArea.RADIUS_TAG) > safeMaxRadius) {
            clamped.save(state);
            return clamped;
        }
        state.putInt(HiredWorkArea.RADIUS_TAG, Mth.clamp(area.horizontalRadius(), MIN_WORK_RADIUS, safeMaxRadius));
        return area;
    }

    static int maxWorkRadius(ServerLevel level, Villager villager, HiredVillagerRole role) {
        int base = baseWorkRadiusCap();
        int max = Mth.clamp(VillagerRetaliationConfig.HIRED_WORK_MAX_RADIUS.get(), base, MAX_SKILLED_WORK_RADIUS);
        int score = Mth.clamp(HiredVillagerRoles.roleScore(level, villager, role), 0, 100);
        double progress = Math.max(0.0D, Math.min(1.0D, (score - SKILL_RADIUS_BASELINE) / 50.0D));
        return Mth.clamp(base + (int) Math.round((max - base) * progress), MIN_WORK_RADIUS, max);
    }

    private static int baseWorkRadiusCap() {
        return Mth.clamp(VillagerRetaliationConfig.HIRED_WORK_DEFAULT_RADIUS.get(), MIN_WORK_RADIUS, MAX_SKILLED_WORK_RADIUS);
    }

    private static int roleDefaultHorizontalRadius(HiredVillagerRole role, int maxRadius) {
        int preferred = switch (role) {
            case MINING -> 24;
            case LOGGING -> 32;
            case FARMING -> 24;
            default -> 24;
        };
        return HiredWorkArea.clampRadius(preferred, MIN_WORK_RADIUS, maxRadius);
    }

    private static int roleDefaultVerticalRadius(HiredVillagerRole role, int maxRadius) {
        int preferred = switch (role) {
            case LOGGING -> 16;
            case FARMING -> 6;
            default -> 8;
        };
        return HiredWorkArea.clampRadius(preferred, 1, maxRadius);
    }

    private static String dimensions(HiredWorkArea area) {
        return (area.max().getX() - area.min().getX() + 1)
                + "x" + (area.max().getY() - area.min().getY() + 1)
                + "x" + (area.max().getZ() - area.min().getZ() + 1);
    }

    private static String areaDescription(HiredWorkArea area) {
        return area.usable() ? area.boundsDescription() : "No work area assigned";
    }

    private static void setStatus(CompoundTag state, String status) {
        state.putString("Status", status == null ? "" : status);
    }

    private static String valueOrNone(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }

    private static String workReportMessageKey(HiredVillagerRole role) {
        return switch (role) {
            case COMBAT -> "interaction.work_report.combat";
            case MINING -> "interaction.work_report.mining";
            case LOGGING -> "interaction.work_report.logging";
            case FARMING -> "interaction.work_report.farming";
            case BREWING -> "interaction.work_report.brewing";
            case NAVIGATION -> "interaction.work_report.navigation";
            case ANIMAL_HANDLING -> "interaction.work_report.animal_handling";
            case NITWIT -> "interaction.work_report.nitwit";
        };
    }

    private static String describeWorkActivity(HiredWorkSession session, HiredWorkerBrain.Snapshot snapshot) {
        if (!session.state().getBoolean("Enabled")) {
            return "resting until you ask me to resume";
        }
        return switch (snapshot.taskState()) {
            case IDLE, AWAITING_INSTRUCTION -> "waiting for the next bit of work";
            case SELECTING_TARGET -> "looking over the area for the next task";
            case MOVING_TO_TARGET -> "heading toward " + describeCurrentTarget(snapshot);
            case VALIDATING_TARGET -> "checking the spot at " + describeCurrentTarget(snapshot);
            case WORKING -> "working at " + describeCurrentTarget(snapshot);
            case COLLECTING_OUTPUT -> "gathering what I finished at " + describeCurrentTarget(snapshot);
            case FINDING_CHAIN_TARGET -> "looking for the next useful spot near " + describeCurrentTarget(snapshot);
            case MOVING_TO_STORAGE -> "carrying goods toward " + describeCurrentTarget(snapshot);
            case RETURNING_TO_WORK_AREA -> "making my way back into the work area";
            case DEPOSITING -> "putting supplies away at " + describeCurrentTarget(snapshot);
            case PAUSED_STORAGE_FULL -> "waiting near full storage at " + describeCurrentTarget(snapshot);
            case NO_WORK_AREA -> "waiting for you to assign a job site";
            case PAUSED_FULL_INVENTORY -> "stopped because my inventory is full";
            case PAUSED_NO_STORAGE -> "waiting because there is nowhere proper to store things";
            case FAILED_COOLDOWN -> "waiting a moment before I try that again";
            case PAUSED_MISSING_TOOL -> "waiting because I do not have the right tool";
        };
    }

    private static String describeStatusDetail(HiredWorkSession session) {
        String status = session.state().getString("Status");
        if (status == null || status.isBlank()) {
            return "Nothing needs doing just now.";
        }
        return status;
    }

    private static String describeCurrentTarget(HiredWorkerBrain.Snapshot snapshot) {
        BlockPos target = snapshot.taskState().keepsStorageTarget() ? snapshot.storageTargetPos() : snapshot.targetPos();
        if (target == null && snapshot.storageTargetPos() != null) {
            target = snapshot.storageTargetPos();
        }
        if (target == null && snapshot.targetPos() != null) {
            target = snapshot.targetPos();
        }
        return target == null ? "no fixed spot just now" : HiredWorkerBrain.formatPos(target);
    }

    private static void handleDailyFood(
            ServerLevel level,
            Villager villager,
            ServerPlayer hirer,
            HiredWorkSession session) {
        long day = level.getDayTime() / DAY_TICKS;
        if (!VillagerRetaliationConfig.HIRED_WORK_FOOD_ENABLED.get()
                || !session.worker().requiresFood()
                || session.state().getLong("LastFoodCheckDay") == day) {
            return;
        }
        session.state().putLong("LastFoodCheckDay", day);
        int needed = Math.max(0, roleFoodCost(session.role()));
        if (needed <= 0 || consumeFood(
                villager,
                session.inventory(),
                needed,
                session.state().getBoolean("UseAssignedStorageForSupplies"),
                session.area()) >= needed) {
            int starvationDays = Math.max(0, session.state().getInt("StarvationDays") - 1);
            session.state().putInt("StarvationDays", starvationDays);
            if (starvationDays == 0) {
                VillagerMoodService.setMood(level, villager, VillagerMood.CONTENT, 14, "hired_work_fed", hirer.getUUID(), hirer.getUUID(), VillagerMoodService.SHORT_DECAY_TICKS);
            }
            return;
        }

        int maxDays = Math.max(0, VillagerRetaliationConfig.HIRED_WORK_MAX_STARVATION_PENALTY_DAYS.get());
        int starvationDays = Mth.clamp(session.state().getInt("StarvationDays") + 1, 0, maxDays);
        session.state().putInt("StarvationDays", starvationDays);
        VillagerMood mood = starvationDays >= Math.max(2, maxDays / 2) ? VillagerMood.ANGRY : VillagerMood.STRESSED;
        VillagerMoodService.setMood(
                level,
                villager,
                mood,
                VillagerRetaliationConfig.HIRED_WORK_NO_FOOD_MOOD_INTENSITY.get(),
                "hired_work_no_food",
                hirer.getUUID(),
                hirer.getUUID(),
                VillagerMoodService.MEDIUM_DECAY_TICKS);
        VillagerReputationManager.addHiredWorkReputation(
                level,
                villager,
                hirer.getUUID(),
                VillagerRetaliationConfig.HIRED_WORK_NO_FOOD_REPUTATION_PENALTY.get());
        maybeNotify(level, villager, hirer, session.state(), "No food for hired work. Mood and reputation suffered.", DAY_TICKS);
    }

    private static int consumeFood(
            Villager villager,
            HiredJobInventory inventory,
            int neededNutrition,
            boolean assignedSupplies,
            HiredWorkArea area) {
        int nutrition = 0;
        for (int slot : inventory.supplySlots()) {
            ItemStack stack = inventory.getItem(slot);
            FoodProperties food = stack.get(DataComponents.FOOD);
            if (stack.isEmpty() || food == null) {
                continue;
            }
            while (!stack.isEmpty() && nutrition < neededNutrition) {
                nutrition += Math.max(1, food.nutrition());
                inventory.consumeSupply(candidate -> candidate == stack, 1);
            }
            if (nutrition >= neededNutrition) {
                return nutrition;
            }
        }
        if (assignedSupplies && nutrition < neededNutrition) {
            nutrition += AssignedStorageService.consumeItems(
                    villager,
                    stack -> stack.get(DataComponents.FOOD) != null,
                    neededNutrition - nutrition,
                    area::contains);
        }
        return nutrition;
    }

    private static int roleFoodCost(HiredVillagerRole role) {
        int base = Math.max(0, VillagerRetaliationConfig.HIRED_WORK_BASE_FOOD_PER_DAY.get());
        int roleCost = switch (role) {
            case COMBAT -> VillagerRetaliationConfig.HIRED_WORK_FOOD_COST_COMBAT.get();
            case MINING -> VillagerRetaliationConfig.HIRED_WORK_FOOD_COST_MINING.get();
            case LOGGING -> VillagerRetaliationConfig.HIRED_WORK_FOOD_COST_LOGGING.get();
            case FARMING -> VillagerRetaliationConfig.HIRED_WORK_FOOD_COST_FARMING.get();
            case BREWING -> VillagerRetaliationConfig.HIRED_WORK_FOOD_COST_BREWING.get();
            case NAVIGATION -> VillagerRetaliationConfig.HIRED_WORK_FOOD_COST_NAVIGATION.get();
            case ANIMAL_HANDLING -> VillagerRetaliationConfig.HIRED_WORK_FOOD_COST_ANIMAL_HANDLING.get();
            case NITWIT -> VillagerRetaliationConfig.HIRED_WORK_FOOD_COST_NITWIT.get();
        };
        return Math.max(0, Math.max(base, roleCost));
    }

    static int efficiencyPercent(ServerLevel level, Villager villager, HiredVillagerRole role, CompoundTag state, HiredJobInventory inventory) {
        int min = Math.max(1, VillagerRetaliationConfig.HIRED_WORK_MINIMUM_EFFICIENCY_PERCENT.get());
        int max = Math.max(min, VillagerRetaliationConfig.HIRED_WORK_MAXIMUM_EFFICIENCY_PERCENT.get());
        int efficiency = VillagerRetaliationConfig.HIRED_WORK_BASE_EFFICIENCY_PERCENT.get();
        efficiency += (HiredVillagerRoles.roleScore(level, villager, role) - 50) / 2;
        if (HiredVillagerRoles.isSkillUnlocked(level, villager, role)) {
            efficiency += 10;
        } else if (HiredVillagerRoles.isProfessionPreferred(villager, role)) {
            efficiency += 3;
        }
        VillagerMoodState mood = VillagerMoodService.mood(level, villager);
        efficiency += switch (mood.primaryMood()) {
            case CONTENT, GRATEFUL, PROUD, HOPEFUL -> 8;
            case ANGRY, AFRAID, STRESSED, GRIEVING -> -15;
            case SUSPICIOUS, LONELY -> -8;
            default -> 0;
        };
        efficiency -= 15 * Math.max(0, state.getInt("StarvationDays"));
        ItemStack tool = inventory.getItem(HiredJobInventory.MAINHAND_SLOT);
        if (!tool.isEmpty()) {
            efficiency += toolTierBonus(tool);
        }
        if ((role == HiredVillagerRole.MINING || role == HiredVillagerRole.LOGGING || role == HiredVillagerRole.FARMING) && tool.isEmpty()) {
            efficiency -= 20;
        }
        return Mth.clamp(efficiency, min, max);
    }

    private static int toolTierBonus(ItemStack stack) {
        if (stack.getItem() instanceof TieredItem tieredItem) {
            String name = tieredItem.getTier().toString().toLowerCase(Locale.ROOT);
            if (name.contains("netherite")) {
                return 28;
            }
            if (name.contains("diamond")) {
                return 20;
            }
            if (name.contains("iron")) {
                return 12;
            }
            if (name.contains("stone")) {
                return 5;
            }
        }
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (path.contains("netherite")) {
            return 28;
        }
        if (path.contains("diamond")) {
            return 20;
        }
        if (path.contains("iron")) {
            return 12;
        }
        if (path.contains("stone")) {
            return 5;
        }
        return 0;
    }

    private static int nextTaskCooldownTicks(int efficiency) {
        return Mth.clamp(Math.round(80.0F * 100.0F / Math.max(25.0F, efficiency)), 10, 200);
    }

    private static void maybeNotify(ServerLevel level, Villager villager, ServerPlayer hirer, CompoundTag state, String message, long cooldownTicks) {
        long now = level.getGameTime();
        if (now - state.getLong("LastNoticeTick") < cooldownTicks) {
            return;
        }
        state.putLong("LastNoticeTick", now);
        VillagerInteractionService.sendVillagerNotice(hirer, villager, message);
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
            }
            return;
        }
        if (state.getBoolean(STORAGE_FULL_NOTICE_SHOWN_TAG)) {
            return;
        }
        state.putBoolean(STORAGE_FULL_NOTICE_SHOWN_TAG, true);
        VillagerInteractionService.sendVillagerNotice(hirer, villager, STORAGE_FULL_NOTICE);
    }
}
