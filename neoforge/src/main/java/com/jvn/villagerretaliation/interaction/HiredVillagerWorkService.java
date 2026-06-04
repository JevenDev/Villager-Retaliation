package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.interaction.work.FarmingWorker;
import com.jvn.villagerretaliation.interaction.work.HiredRoleWorker;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState;
import com.jvn.villagerretaliation.interaction.work.LoggingWorker;
import com.jvn.villagerretaliation.interaction.work.MiningWorker;
import com.jvn.villagerretaliation.interaction.work.NitwitWorker;
import com.jvn.villagerretaliation.interaction.work.StatusOnlyWorker;
import com.jvn.villagerretaliation.interaction.work.WorkResult;
import com.jvn.villagerretaliation.mood.VillagerMood;
import com.jvn.villagerretaliation.mood.VillagerMoodService;
import com.jvn.villagerretaliation.mood.VillagerMoodState;
import com.jvn.villagerretaliation.reputation.VillagerAggressionPolicy;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.skill.VillagerProfessionSkills;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.pathfinder.Path;

public final class HiredVillagerWorkService {
    private static final String TAG = "VillagerRetaliationHiredWork";
    private static final String WORK_CENTER_POS_TAG = "WorkCenterPos";
    private static final String WORK_MIN_POS_TAG = "WorkMinPos";
    private static final String WORK_MAX_POS_TAG = "WorkMaxPos";
    private static final String NEXT_PROFESSION_SUPPRESSION_GAME_TIME_TAG = "NextProfessionSuppressionGameTime";
    private static final String NEXT_WORK_AREA_RETURN_PATH_GAME_TIME_TAG = "NextWorkAreaReturnPathGameTime";
    private static final long DAY_TICKS = 24000L;
    private static final int MIN_WORK_RADIUS = 4;
    private static final int SKILL_RADIUS_BASELINE = 50;
    private static final int MAX_SKILLED_WORK_RADIUS = 32;
    private static final int PROFESSION_SUPPRESSION_INTERVAL_TICKS = 20;
    private static final int WORK_AREA_RETURN_PATH_RETRY_TICKS = 20;
    private static final int MAX_RETURN_TARGETS_TO_PATHFIND = 32;
    private static final Map<HiredVillagerRole, HiredRoleWorker> WORKERS = new EnumMap<>(HiredVillagerRole.class);

    static {
        register(new LoggingWorker());
        register(new MiningWorker());
        register(new FarmingWorker());
        register(new NitwitWorker());
        register(new StatusOnlyWorker(HiredVillagerRole.BREWING, "Brewing automation is waiting for a configured brewing stand and supplies."));
        register(new StatusOnlyWorker(HiredVillagerRole.NAVIGATION, "Navigation work is ready for target discovery configuration."));
        register(new StatusOnlyWorker(HiredVillagerRole.ANIMAL_HANDLING, "Animal handling is waiting for lure supplies and a safe pen."));
        register(new StatusOnlyWorker(HiredVillagerRole.COMBAT, "Guard duty active. Combat is handled by existing retaliation systems."));
    }

    private HiredVillagerWorkService() {
    }

    public static void onVillagerTickPost(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)
                || villager.isBaby()
                || !villager.isAlive()
                || villager.isSleeping()
                || villager.isTrading()
                || VillagerConversationService.isConversing(villager)
                || villager.getTarget() != null
                || villager.getLastHurtByMob() != null
                || !HiredVillagerContractService.isHired(level, villager)) {
            return;
        }

        UUID hirerId = HiredVillagerContractService.getHirer(level, villager).orElse(null);
        if (hirerId == null || !(level.getServer().getPlayerList().getPlayer(hirerId) instanceof ServerPlayer hirer)) {
            CompoundTag waitingState = state(villager);
            initializeDefaults(waitingState, villager);
            VillagerTaskNavigationUtil.stopNavigationAndClearTargets(villager);
            HiredWorkerBrain.setState(waitingState, HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
            setStatus(waitingState, "Waiting for hirer to be online.");
            return;
        }
        if (VillagerAggressionPolicy.shouldAttackOnSight(villager, hirer)) {
            return;
        }

        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        HiredRoleWorker worker = WORKERS.get(role);
        if (worker == null) {
            VillagerTaskNavigationUtil.stopNavigationAndClearTargets(villager);
            HiredWorkerBrain.setState(state, HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
            setStatus(state, "Paused: no worker exists for " + role.label() + ".");
            return;
        }
        if (!state.getBoolean("Enabled")) {
            VillagerTaskNavigationUtil.stopNavigationAndClearTargets(villager);
            HiredWorkerBrain.setState(state, HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
            setStatus(state, "Paused by hirer.");
            return;
        }

        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
        int maxRadius = maxWorkRadius(level, villager, role);
        WorkArea area = workAreaWithinMax(state, villager, maxRadius);
        int radius = area.radius();
        int efficiency = efficiencyPercent(level, villager, role, state, inventory);
        HiredWorkContext context = new HiredWorkContext(
                inventory,
                state,
                area.center(),
                area.min(),
                area.max(),
                radius,
                efficiency,
                state.getBoolean("AutoDepositOutputs"),
                state.getBoolean("UseAssignedStorageForSupplies"));

        suppressProfessionJobSiteBehavior(level, villager, context);
        if (returnVillagerToWorkArea(level, villager, context, state)) {
            return;
        }
        worker.maintain(level, villager, context);

        int interval = Math.max(10, VillagerRetaliationConfig.HIRED_WORK_TICK_INTERVAL.get());
        if (Math.floorMod(level.getGameTime() + villager.getUUID().getLeastSignificantBits(), interval) != 0L) {
            return;
        }

        long nextWorkGameTime = state.getLong("NextWorkGameTime");
        if (nextWorkGameTime > level.getGameTime()) {
            setStatus(state, "Preparing for next task. Cooldown: " + (nextWorkGameTime - level.getGameTime()) + " ticks.");
            return;
        }

        handleDailyFood(level, villager, hirer, role, worker, state, inventory, area);
        WorkResult result = worker.tick(level, villager, hirer, context);
        setStatus(state, result.status() + " Efficiency: " + efficiency + "%.");
        if (result.completed()) {
            state.putLong("NextWorkGameTime", level.getGameTime() + nextTaskCooldownTicks(efficiency));
            maybeNotify(level, villager, hirer, state, result.status(), 20L * 30L);
        }
    }

    private static void suppressProfessionJobSiteBehavior(ServerLevel level, Villager villager, HiredWorkContext context) {
        Brain<Villager> brain = villager.getBrain();
        BlockPos jobSite = brain.getMemory(MemoryModuleType.JOB_SITE)
                .filter(pos -> pos.dimension().equals(level.dimension()))
                .map(GlobalPos::pos)
                .orElse(null);
        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        boolean stopNavigation = navigationTarget != null
                && (!context.isInsideWorkArea(navigationTarget) || navigationTarget.equals(jobSite));
        if (stopNavigation) {
            villager.getNavigation().stop();
        }

        long gameTime = level.getGameTime();
        if (!stopNavigation
                && !brain.isActive(Activity.WORK)
                && gameTime < context.state().getLong(NEXT_PROFESSION_SUPPRESSION_GAME_TIME_TAG)) {
            return;
        }
        context.state().putLong(
                NEXT_PROFESSION_SUPPRESSION_GAME_TIME_TAG,
                gameTime + PROFESSION_SUPPRESSION_INTERVAL_TICKS);

        if (brain.getMemory(MemoryModuleType.WALK_TARGET).isPresent()) {
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        }
        if (brain.getMemory(MemoryModuleType.PATH).isPresent()) {
            brain.eraseMemory(MemoryModuleType.PATH);
        }
        if (brain.isActive(Activity.WORK)) {
            brain.setDefaultActivity(Activity.IDLE);
            brain.setActiveActivityIfPossible(Activity.IDLE);
        }
    }

    private static boolean returnVillagerToWorkArea(ServerLevel level, Villager villager, HiredWorkContext context, CompoundTag state) {
        if (context.isInsideWorkArea(villager.blockPosition())) {
            state.remove(NEXT_WORK_AREA_RETURN_PATH_GAME_TIME_TAG);
            return false;
        }

        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        if (!villager.getNavigation().isDone() && navigationTarget != null && context.isInsideWorkArea(navigationTarget)) {
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, navigationTarget);
            setStatus(state, "Outside assigned area. Returning to work area.");
            return true;
        }

        long gameTime = level.getGameTime();
        if (gameTime < state.getLong(NEXT_WORK_AREA_RETURN_PATH_GAME_TIME_TAG)) {
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, null);
            setStatus(state, "Outside assigned area. Waiting before retrying path back.");
            return true;
        }

        ReturnPath returnPath = findWorkAreaReturnPath(level, villager, context);
        if (returnPath == null) {
            villager.getNavigation().stop();
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
            state.putLong(NEXT_WORK_AREA_RETURN_PATH_GAME_TIME_TAG, gameTime + WORK_AREA_RETURN_PATH_RETRY_TICKS);
            setStatus(state, "Outside assigned area, but no reachable path back was found yet.");
            return true;
        }

        if (villager.getNavigation().moveTo(returnPath.path(), 1.0D)) {
            state.remove(NEXT_WORK_AREA_RETURN_PATH_GAME_TIME_TAG);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, returnPath.target());
            setStatus(state, "Outside assigned area. Returning to work area.");
        } else {
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
            state.putLong(NEXT_WORK_AREA_RETURN_PATH_GAME_TIME_TAG, gameTime + WORK_AREA_RETURN_PATH_RETRY_TICKS);
            setStatus(state, "Outside assigned area, but movement back could not start.");
        }
        return true;
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
        MiningWorker.clearRuntimeState();
    }

    public static void sendStatus(ServerPlayer player, ServerLevel level, Villager villager) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        String status = state.getString("Status");
        HiredWorkerBrain.Snapshot snapshot = HiredWorkerBrain.snapshot(state, level.getGameTime());
        int efficiency = efficiencyPercent(level, villager, role, state, HiredJobInventory.getJobInventory(villager));
        int maxRadius = maxWorkRadius(level, villager, role);
        WorkArea area = workAreaWithinMax(state, villager, maxRadius);
        VillagerInteractionService.sendVillagerNotice(player, villager,
                "Work: " + (state.getBoolean("Enabled") ? "running" : "paused")
                        + ", role " + role.label()
                        + ", area " + areaDescription(area)
                        + ", max radius " + maxRadius
                        + ", supplies " + (state.getBoolean("UseAssignedStorageForSupplies") ? "job+assigned" : "job")
                        + ", auto-deposit " + (state.getBoolean("AutoDepositOutputs") ? "on" : "off")
                        + ", starvation days " + state.getInt("StarvationDays")
                        + ", efficiency " + efficiency + "%"
                        + ", task " + snapshot.taskState().label()
                        + ", target " + HiredWorkerBrain.formatPos(snapshot.targetPos())
                        + ". "
                        + (status.isBlank() ? "No task yet." : status));
    }

    public static List<String> debugLines(ServerLevel level, Villager villager) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
        int efficiency = efficiencyPercent(level, villager, role, state, inventory);
        int maxRadius = maxWorkRadius(level, villager, role);
        WorkArea area = workAreaWithinMax(state, villager, maxRadius);
        HiredWorkerBrain.Snapshot snapshot = HiredWorkerBrain.snapshot(state, level.getGameTime());
        int outputStacks = inventory.collectOutputItems().size();
        int outputItems = inventory.collectOutputItems().stream().mapToInt(output -> output.stack().getCount()).sum();
        boolean hasAssignedStorage = AssignedStorageService.hasAssignedStorage(level, villager);
        boolean canDepositNow = AssignedStorageService.canInteractWithAssignedStorage(villager, pos -> isInsideWorkArea(area, pos));

        List<String> lines = new ArrayList<>();
        lines.add("Hired worker debug: role=" + role.serializedName()
                + ", hired=" + HiredVillagerContractService.isHired(level, villager)
                + ", enabled=" + state.getBoolean("Enabled")
                + ", efficiency=" + efficiency + "%");
        lines.add("State: task=" + snapshot.taskState().id()
                + ", progress=" + snapshot.progressTicks()
                + ", target=" + HiredWorkerBrain.formatPos(snapshot.targetPos())
                + ", storageTarget=" + HiredWorkerBrain.formatPos(snapshot.storageTargetPos()));
        lines.add("Failure: reason=" + valueOrNone(snapshot.failureReason())
                + ", retryCooldown=" + snapshot.retryCooldownTicks()
                + ", lastScan=" + valueOrNone(snapshot.lastTargetScanResult()));
        lines.add("Work area: " + areaDescription(area)
                + ", radius=" + area.radius()
                + ", verticalRadius=" + area.verticalRadius()
                + ", maxRadius=" + maxRadius);
        lines.add("Inventory/storage: outputStacks=" + outputStacks
                + ", outputItems=" + outputItems
                + ", autoDeposit=" + state.getBoolean("AutoDepositOutputs")
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
        int radius = Mth.clamp(
                VillagerRetaliationConfig.HIRED_WORK_DEFAULT_RADIUS.get(),
                MIN_WORK_RADIUS,
                maxWorkRadius(level, villager, role));
        state.putInt("Radius", radius);
        BlockPos center = villager.blockPosition();
        setWorkAreaBounds(state, defaultMin(center, radius), defaultMax(center, radius));
        stopWork(level, villager, role, "Waiting for work assignment.");
    }

    public static void stopWork(ServerLevel level, Villager villager, HiredVillagerRole role, String status) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        HiredVillagerRole safeRole = role == null ? HiredVillagerRoles.defaultRole(level, villager) : role;
        HiredRoleWorker worker = WORKERS.get(safeRole);
        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
        int maxRadius = maxWorkRadius(level, villager, safeRole);
        WorkArea area = workAreaWithinMax(state, villager, maxRadius);
        HiredWorkContext context = new HiredWorkContext(
                inventory,
                state,
                area.center(),
                area.min(),
                area.max(),
                area.radius(),
                efficiencyPercent(level, villager, safeRole, state, inventory),
                state.getBoolean("AutoDepositOutputs"),
                state.getBoolean("UseAssignedStorageForSupplies"));
        if (worker != null) {
            worker.stop(level, villager, context);
        } else {
            context.setProgressTicks(0);
        }
        state.remove("NextWorkGameTime");
        setStatus(state, status);
    }

    public static void toggleEnabled(ServerPlayer player, ServerLevel level, Villager villager) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        state.putBoolean("Enabled", !state.getBoolean("Enabled"));
        setStatus(state, state.getBoolean("Enabled") ? "Work resumed." : "Work paused by hirer.");
        VillagerInteractionService.sendVillagerNotice(player, villager, state.getString("Status"));
    }

    public static void changeRadius(ServerPlayer player, ServerLevel level, Villager villager, int delta) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        int max = maxWorkRadius(level, villager, role);
        int radius = Mth.clamp(state.getInt("Radius") + delta, MIN_WORK_RADIUS, max);
        state.putInt("Radius", radius);
        BlockPos center = workCenter(state, villager);
        setWorkAreaBounds(state, defaultMin(center, radius), defaultMax(center, radius));
        setStatus(state, "Work radius set to " + radius + ". Skill cap: " + max + ".");
        VillagerInteractionService.sendVillagerNotice(player, villager, state.getString("Status"));
    }

    public static void toggleAssignedSupplies(ServerPlayer player, ServerLevel level, Villager villager) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        state.putBoolean("UseAssignedStorageForSupplies", !state.getBoolean("UseAssignedStorageForSupplies"));
        setStatus(state, "Assigned storage supplies " + (state.getBoolean("UseAssignedStorageForSupplies") ? "enabled." : "disabled."));
        VillagerInteractionService.sendVillagerNotice(player, villager, state.getString("Status"));
    }

    public static void toggleAutoDeposit(ServerPlayer player, ServerLevel level, Villager villager) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        state.putBoolean("AutoDepositOutputs", !state.getBoolean("AutoDepositOutputs"));
        setStatus(state, "Auto-deposit outputs " + (state.getBoolean("AutoDepositOutputs") ? "enabled." : "disabled."));
        VillagerInteractionService.sendVillagerNotice(player, villager, state.getString("Status"));
    }

    public static void configureRole(ServerPlayer player, ServerLevel level, Villager villager, HiredVillagerRole role) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        switch (role) {
            case LOGGING -> {
                String current = state.getString("LoggingFilter");
                state.putString("LoggingFilter", current == null || current.isBlank() || "any".equals(current) ? "oak_log" : "any");
                setStatus(state, "Logging filter cycled to " + state.getString("LoggingFilter") + ".");
            }
            case FARMING -> {
                String current = state.getString("CropMode");
                state.putString("CropMode", "harvest_replant".equals(current) ? "harvest_only" : "harvest_replant");
                setStatus(state, "Farming mode set to " + state.getString("CropMode") + ".");
            }
            case BREWING -> setStatus(state, "Brewing route cycled. MVP supports status-only brewing setup.");
            case NAVIGATION -> setStatus(state, "Navigation target cycled. MVP stores configuration and reports status.");
            case ANIMAL_HANDLING -> setStatus(state, "Animal handling target cycled. MVP waits for lures and safe pen support.");
            case NITWIT -> setStatus(state, "Nitwit focus cycled. Expect occasional deeply questionable reports.");
            default -> setStatus(state, role.label() + " has no extra configuration yet.");
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

    public static WorkArea workArea(ServerLevel level, Villager villager) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        return workAreaWithinMax(state, villager, maxWorkRadius(level, villager, role));
    }

    public static boolean setWorkArea(ServerPlayer player, ServerLevel level, Villager villager, BlockPos first, BlockPos second) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        BlockPos min = minPos(first, second);
        BlockPos max = maxPos(first, second);
        BlockPos center = centerPos(min, max);
        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        int maxRadius = maxWorkRadius(level, villager, role);
        int horizontalRadius = horizontalRadius(center, min, max);
        int verticalRadius = verticalRadius(center, min, max);
        boolean capped = horizontalRadius > maxRadius || verticalRadius > maxRadius;
        if (capped) {
            min = clampedMin(center, min, maxRadius);
            max = clampedMax(center, max, maxRadius);
            horizontalRadius = horizontalRadius(center, min, max);
            verticalRadius = verticalRadius(center, min, max);
        }
        int radius = Math.max(MIN_WORK_RADIUS, horizontalRadius);
        state.putInt("Radius", radius);
        setWorkAreaBounds(state, min, max);
        String capNotice = capped
                ? role.label() + " skill caps this villager at " + maxRadius + " blocks from center, so the assigned bounds were trimmed inward. "
                : "";
        setStatus(state, capNotice + "Work area set to " + areaDescription(new WorkArea(center, min, max, radius, verticalRadius)) + ". Skill cap: " + maxRadius + ".");
        VillagerInteractionService.sendVillagerNotice(player, villager, state.getString("Status"));
        return true;
    }

    private static void register(HiredRoleWorker worker) {
        WORKERS.put(worker.role(), worker);
    }

    private static CompoundTag state(Villager villager) {
        CompoundTag persistentData = villager.getPersistentData();
        if (!persistentData.contains(TAG, Tag.TAG_COMPOUND)) {
            persistentData.put(TAG, new CompoundTag());
        }
        return persistentData.getCompound(TAG);
    }

    private static void initializeDefaults(CompoundTag state, Villager villager) {
        if (!state.contains("Enabled", Tag.TAG_BYTE)) {
            state.putBoolean("Enabled", true);
        }
        if (!state.contains("Radius", Tag.TAG_INT)) {
            state.putInt("Radius", Mth.clamp(
                    VillagerRetaliationConfig.HIRED_WORK_DEFAULT_RADIUS.get(),
                    MIN_WORK_RADIUS,
                    baseWorkRadiusCap()));
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
        if (!state.contains("Status", Tag.TAG_STRING)) {
            state.putString("Status", "Waiting for work tick.");
        }
        HiredWorkerBrain.initialize(state);
        if (!state.contains(WORK_CENTER_POS_TAG, Tag.TAG_LONG)) {
            state.putLong(WORK_CENTER_POS_TAG, villager.blockPosition().asLong());
        }
        if (!state.contains(WORK_MIN_POS_TAG, Tag.TAG_LONG) || !state.contains(WORK_MAX_POS_TAG, Tag.TAG_LONG)) {
            int radius = Mth.clamp(state.getInt("Radius"), MIN_WORK_RADIUS, baseWorkRadiusCap());
            BlockPos center = workCenter(state, villager);
            setWorkAreaBounds(state, defaultMin(center, radius), defaultMax(center, radius));
        }
    }

    private static BlockPos workCenter(CompoundTag state, Villager villager) {
        if (!state.contains(WORK_CENTER_POS_TAG, Tag.TAG_LONG)) {
            state.putLong(WORK_CENTER_POS_TAG, villager.blockPosition().asLong());
        }
        return BlockPos.of(state.getLong(WORK_CENTER_POS_TAG));
    }

    private static WorkArea workArea(CompoundTag state, Villager villager) {
        if (!state.contains(WORK_MIN_POS_TAG, Tag.TAG_LONG) || !state.contains(WORK_MAX_POS_TAG, Tag.TAG_LONG)) {
            initializeDefaults(state, villager);
        }
        BlockPos min = BlockPos.of(state.getLong(WORK_MIN_POS_TAG));
        BlockPos max = BlockPos.of(state.getLong(WORK_MAX_POS_TAG));
        BlockPos center = workCenter(state, villager);
        int radius = Math.max(1, horizontalRadius(center, min, max));
        int verticalRadius = Math.max(1, verticalRadius(center, min, max));
        return new WorkArea(center, min, max, radius, verticalRadius);
    }

    private static WorkArea workAreaWithinMax(CompoundTag state, Villager villager, int maxRadius) {
        int safeMaxRadius = Math.max(MIN_WORK_RADIUS, maxRadius);
        WorkArea area = workArea(state, villager);
        BlockPos center = area.center();
        BlockPos min = area.min();
        BlockPos max = area.max();
        BlockPos clampedMin = clampedMin(center, min, safeMaxRadius);
        BlockPos clampedMax = clampedMax(center, max, safeMaxRadius);
        if (!clampedMin.equals(min) || !clampedMax.equals(max) || state.getInt("Radius") > safeMaxRadius) {
            setWorkAreaBounds(state, clampedMin, clampedMax);
            WorkArea clamped = workArea(state, villager);
            state.putInt("Radius", Mth.clamp(clamped.radius(), MIN_WORK_RADIUS, safeMaxRadius));
            return clamped;
        }
        state.putInt("Radius", Mth.clamp(area.radius(), MIN_WORK_RADIUS, safeMaxRadius));
        return area;
    }

    private static int maxWorkRadius(ServerLevel level, Villager villager, HiredVillagerRole role) {
        int base = baseWorkRadiusCap();
        int max = Mth.clamp(VillagerRetaliationConfig.HIRED_WORK_MAX_RADIUS.get(), base, MAX_SKILLED_WORK_RADIUS);
        int score = Mth.clamp(HiredVillagerRoles.roleScore(level, villager, role), 0, 100);
        double progress = Math.max(0.0D, Math.min(1.0D, (score - SKILL_RADIUS_BASELINE) / 50.0D));
        return Mth.clamp(base + (int) Math.round((max - base) * progress), MIN_WORK_RADIUS, max);
    }

    private static int baseWorkRadiusCap() {
        return Mth.clamp(VillagerRetaliationConfig.HIRED_WORK_DEFAULT_RADIUS.get(), MIN_WORK_RADIUS, MAX_SKILLED_WORK_RADIUS);
    }

    private static BlockPos clampedMin(BlockPos center, BlockPos min, int maxRadius) {
        int safeRadius = Math.max(MIN_WORK_RADIUS, maxRadius);
        return new BlockPos(
                Math.max(min.getX(), center.getX() - safeRadius),
                Math.max(min.getY(), center.getY() - safeRadius),
                Math.max(min.getZ(), center.getZ() - safeRadius));
    }

    private static BlockPos clampedMax(BlockPos center, BlockPos max, int maxRadius) {
        int safeRadius = Math.max(MIN_WORK_RADIUS, maxRadius);
        return new BlockPos(
                Math.min(max.getX(), center.getX() + safeRadius),
                Math.min(max.getY(), center.getY() + safeRadius),
                Math.min(max.getZ(), center.getZ() + safeRadius));
    }

    private static void setWorkAreaBounds(CompoundTag state, BlockPos min, BlockPos max) {
        BlockPos normalizedMin = minPos(min, max);
        BlockPos normalizedMax = maxPos(min, max);
        BlockPos center = centerPos(normalizedMin, normalizedMax);
        state.putLong(WORK_CENTER_POS_TAG, center.asLong());
        state.putLong(WORK_MIN_POS_TAG, normalizedMin.asLong());
        state.putLong(WORK_MAX_POS_TAG, normalizedMax.asLong());
    }

    private static BlockPos defaultMin(BlockPos center, int radius) {
        int safeRadius = Math.max(1, radius);
        int verticalRadius = Math.min(safeRadius, 8);
        return center.offset(-safeRadius, -verticalRadius, -safeRadius);
    }

    private static BlockPos defaultMax(BlockPos center, int radius) {
        int safeRadius = Math.max(1, radius);
        int verticalRadius = Math.min(safeRadius, 8);
        return center.offset(safeRadius, verticalRadius, safeRadius);
    }

    private static BlockPos minPos(BlockPos first, BlockPos second) {
        return new BlockPos(
                Math.min(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()));
    }

    private static BlockPos maxPos(BlockPos first, BlockPos second) {
        return new BlockPos(
                Math.max(first.getX(), second.getX()),
                Math.max(first.getY(), second.getY()),
                Math.max(first.getZ(), second.getZ()));
    }

    private static BlockPos centerPos(BlockPos min, BlockPos max) {
        return new BlockPos(
                Math.floorDiv(min.getX() + max.getX(), 2),
                Math.floorDiv(min.getY() + max.getY(), 2),
                Math.floorDiv(min.getZ() + max.getZ(), 2));
    }

    private static int horizontalRadius(BlockPos center, BlockPos min, BlockPos max) {
        return Math.max(
                Math.max(Math.abs(center.getX() - min.getX()), Math.abs(max.getX() - center.getX())),
                Math.max(Math.abs(center.getZ() - min.getZ()), Math.abs(max.getZ() - center.getZ())));
    }

    private static int verticalRadius(BlockPos center, BlockPos min, BlockPos max) {
        return Math.max(Math.abs(center.getY() - min.getY()), Math.abs(max.getY() - center.getY()));
    }

    private static String areaDescription(WorkArea area) {
        BlockPos min = area.min();
        BlockPos max = area.max();
        return min.getX() + " " + min.getY() + " " + min.getZ()
                + " to " + max.getX() + " " + max.getY() + " " + max.getZ();
    }

    private static void setStatus(CompoundTag state, String status) {
        state.putString("Status", status == null ? "" : status);
    }

    private static String valueOrNone(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }

    private static void handleDailyFood(
            ServerLevel level,
            Villager villager,
            ServerPlayer hirer,
            HiredVillagerRole role,
            HiredRoleWorker worker,
            CompoundTag state,
            HiredJobInventory inventory,
            WorkArea area) {
        long day = level.getDayTime() / DAY_TICKS;
        if (!VillagerRetaliationConfig.HIRED_WORK_FOOD_ENABLED.get()
                || !worker.requiresFood()
                || state.getLong("LastFoodCheckDay") == day) {
            return;
        }
        state.putLong("LastFoodCheckDay", day);
        int needed = Math.max(0, roleFoodCost(role));
        if (needed <= 0 || consumeFood(villager, inventory, needed, state.getBoolean("UseAssignedStorageForSupplies"), area) >= needed) {
            int starvationDays = Math.max(0, state.getInt("StarvationDays") - 1);
            state.putInt("StarvationDays", starvationDays);
            if (starvationDays == 0) {
                VillagerMoodService.setMood(level, villager, VillagerMood.CONTENT, 14, "hired_work_fed", hirer.getUUID(), hirer.getUUID(), VillagerMoodService.SHORT_DECAY_TICKS);
            }
            return;
        }

        int maxDays = Math.max(0, VillagerRetaliationConfig.HIRED_WORK_MAX_STARVATION_PENALTY_DAYS.get());
        int starvationDays = Mth.clamp(state.getInt("StarvationDays") + 1, 0, maxDays);
        state.putInt("StarvationDays", starvationDays);
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
        maybeNotify(level, villager, hirer, state, "No food for hired work. Mood and reputation suffered.", DAY_TICKS);
    }

    private static int consumeFood(
            Villager villager,
            HiredJobInventory inventory,
            int neededNutrition,
            boolean assignedSupplies,
            WorkArea area) {
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
                    pos -> isInsideWorkArea(area, pos));
        }
        return nutrition;
    }

    private static boolean isInsideWorkArea(WorkArea area, BlockPos pos) {
        return pos.getX() >= area.min().getX()
                && pos.getX() <= area.max().getX()
                && pos.getY() >= area.min().getY()
                && pos.getY() <= area.max().getY()
                && pos.getZ() >= area.min().getZ()
                && pos.getZ() <= area.max().getZ();
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

    private static int efficiencyPercent(ServerLevel level, Villager villager, HiredVillagerRole role, CompoundTag state, HiredJobInventory inventory) {
        int min = Math.max(1, VillagerRetaliationConfig.HIRED_WORK_MINIMUM_EFFICIENCY_PERCENT.get());
        int max = Math.max(min, VillagerRetaliationConfig.HIRED_WORK_MAXIMUM_EFFICIENCY_PERCENT.get());
        int efficiency = VillagerRetaliationConfig.HIRED_WORK_BASE_EFFICIENCY_PERCENT.get();
        efficiency += (HiredVillagerRoles.roleScore(level, villager, role) - 50) / 2;
        if (HiredVillagerRoles.availableRoles(level, villager).contains(role)) {
            efficiency += 10;
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

    public record WorkArea(BlockPos center, BlockPos min, BlockPos max, int radius, int verticalRadius) {
    }
}
