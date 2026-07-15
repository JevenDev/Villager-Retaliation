package com.jvn.villagerretaliation.interaction.work.mining;

import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.HiredWorkAreaScan;
import com.jvn.villagerretaliation.interaction.HiredMiningMode;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

final class MiningWorkerState {
    static final String NEXT_FULL_SCAN_GAME_TIME_TAG = "NextMiningFullScanGameTime";
    static final String EXCAVATION_SCAN_CURSOR_TAG = "MiningExcavationScanCursor";

    private static final String MINING_STATE_TAG = "MiningState";
    private static final String STATE_VERSION_TAG = "MiningStateVersion";
    private static final String STATE_MODE_TAG = "MiningStateMode";
    private static final String STATE_WORK_MIN_TAG = "MiningStateWorkMin";
    private static final String STATE_WORK_MAX_TAG = "MiningStateWorkMax";
    private static final int STATE_VERSION = 2;
    private static final String LAST_MINED_BLOCK_POS_TAG = "LastMinedBlockPos";
    private static final String MINING_ANCHOR_POS_TAG = "MiningAnchorPos";
    private static final String MINING_ANCHOR_EXPIRES_GAME_TIME_TAG = "MiningAnchorExpiresGameTime";
    private static final String LAST_BREAK_PROGRESS_GAME_TIME_TAG = "LastMiningBreakProgressGameTime";
    private static final String OUTPUT_CAPACITY_CHECKED_TARGET_TAG = "MiningOutputCapacityCheckedTarget";
    private static final String CURRENT_EXCAVATION_LAYER_PRESENT_TAG = "CurrentExcavationLayerPresent";
    private static final String CURRENT_EXCAVATION_LAYER_Y_TAG = "CurrentExcavationLayerY";
    private static final String CURRENT_EXCAVATION_LAYER_EXPIRES_GAME_TIME_TAG = "CurrentExcavationLayerExpiresGameTime";
    private static final int MINING_POCKET_RADIUS = 6;
    private static final long MINING_ANCHOR_TICKS = 20L * 90L;
    private static final long EXCAVATION_LAYER_CACHE_TICKS = 20L;
    private static final int NO_TARGET_SCAN_COOLDOWN_TICKS = 100;

    private MiningWorkerState() {
    }

    static int noTargetScanCooldownTicks() {
        return NO_TARGET_SCAN_COOLDOWN_TICKS;
    }

    static void set(HiredWorkContext context, Phase phase) {
        context.state().putString(MINING_STATE_TAG, (phase == null ? Phase.FIND_TARGET : phase).id);
    }

    static Phase phase(HiredWorkContext context) {
        return Phase.byId(context.state().getString(MINING_STATE_TAG));
    }

    static Change synchronize(HiredWorkContext context, HiredMiningMode mode) {
        if (context == null || mode == null || !context.hasWorkArea()) {
            return Change.NONE;
        }
        boolean initialized = context.state().contains(STATE_VERSION_TAG)
                && context.state().contains(STATE_MODE_TAG)
                && context.state().contains(STATE_WORK_MIN_TAG)
                && context.state().contains(STATE_WORK_MAX_TAG);
        if (!initialized) {
            rememberConfiguration(context, mode);
            if (!context.state().contains(MINING_STATE_TAG)) {
                set(context, Phase.FIND_TARGET);
            }
            return Change.NONE;
        }

        boolean areaChanged = context.state().getLong(STATE_WORK_MIN_TAG) != context.workMin().asLong()
                || context.state().getLong(STATE_WORK_MAX_TAG) != context.workMax().asLong();
        boolean modeChanged = !mode.serializedName().equals(context.state().getString(STATE_MODE_TAG));
        boolean versionChanged = context.state().getInt(STATE_VERSION_TAG) != STATE_VERSION;
        if (!areaChanged && !modeChanged && !versionChanged) {
            return Change.NONE;
        }
        Change change = areaChanged ? Change.WORK_AREA_CHANGED : Change.MODE_CHANGED;
        resetForConfigurationChange(context, mode, change);
        return change;
    }

    static void resetForModeChange(HiredWorkContext context, HiredMiningMode mode) {
        resetForConfigurationChange(context, mode, Change.MODE_CHANGED);
    }

    static void resetForWorkAreaChange(HiredWorkContext context, HiredMiningMode mode) {
        resetForConfigurationChange(context, mode, Change.WORK_AREA_CHANGED);
    }

    static void resetForOptionChange(HiredWorkContext context, HiredMiningMode mode) {
        resetForConfigurationChange(context, mode, Change.OPTIONS_CHANGED);
    }

    private static void resetForConfigurationChange(
            HiredWorkContext context,
            HiredMiningMode mode,
            Change change) {
        resetTransient(context);
        MiningHazardManager.reset(context, change == Change.WORK_AREA_CHANGED);
        if (change == Change.WORK_AREA_CHANGED) {
            MiningExcavationShaft.clear(context);
        }
        if (change == Change.MODE_CHANGED || change == Change.WORK_AREA_CHANGED) {
            MiningHorizontalStairPlan.reset(context);
        }
        rememberConfiguration(context, mode);
    }

    private static void resetTransient(HiredWorkContext context) {
        context.state().remove(LAST_MINED_BLOCK_POS_TAG);
        context.state().remove(LAST_BREAK_PROGRESS_GAME_TIME_TAG);
        context.state().remove(OUTPUT_CAPACITY_CHECKED_TARGET_TAG);
        context.state().remove(NEXT_FULL_SCAN_GAME_TIME_TAG);
        context.state().remove(EXCAVATION_SCAN_CURSOR_TAG);
        clearMiningAnchor(context);
        clearExcavationLayerCache(context);
        set(context, Phase.FIND_TARGET);
    }

    private static void rememberConfiguration(HiredWorkContext context, HiredMiningMode mode) {
        context.state().putInt(STATE_VERSION_TAG, STATE_VERSION);
        context.state().putString(STATE_MODE_TAG, mode.serializedName());
        if (context.hasWorkArea()) {
            context.state().putLong(STATE_WORK_MIN_TAG, context.workMin().asLong());
            context.state().putLong(STATE_WORK_MAX_TAG, context.workMax().asLong());
        } else {
            context.state().remove(STATE_WORK_MIN_TAG);
            context.state().remove(STATE_WORK_MAX_TAG);
        }
    }

    static void rememberLastMined(HiredWorkContext context, BlockPos pos) {
        context.state().putLong(LAST_MINED_BLOCK_POS_TAG, pos.asLong());
    }

    static boolean hasCheckedOutputCapacity(HiredWorkContext context, BlockPos target) {
        return context.state().contains(OUTPUT_CAPACITY_CHECKED_TARGET_TAG)
                && context.state().getLong(OUTPUT_CAPACITY_CHECKED_TARGET_TAG) == target.asLong();
    }

    static void rememberOutputCapacityCheck(HiredWorkContext context, BlockPos target) {
        context.state().putLong(OUTPUT_CAPACITY_CHECKED_TARGET_TAG, target.asLong());
    }

    static BlockPos lastMinedBlock(HiredWorkContext context) {
        return context.state().contains(LAST_MINED_BLOCK_POS_TAG)
                ? BlockPos.of(context.state().getLong(LAST_MINED_BLOCK_POS_TAG))
                : null;
    }

    static void rememberMiningAnchor(ServerLevel level, HiredWorkContext context, BlockPos pos) {
        context.state().putLong(MINING_ANCHOR_POS_TAG, pos.asLong());
        context.state().putLong(MINING_ANCHOR_EXPIRES_GAME_TIME_TAG, level.getGameTime() + MINING_ANCHOR_TICKS);
    }

    static BlockPos miningAnchor(ServerLevel level, HiredWorkContext context) {
        if (!context.state().contains(MINING_ANCHOR_POS_TAG)) {
            return null;
        }
        if (context.state().getLong(MINING_ANCHOR_EXPIRES_GAME_TIME_TAG) <= level.getGameTime()) {
            clearMiningAnchor(context);
            return null;
        }
        return BlockPos.of(context.state().getLong(MINING_ANCHOR_POS_TAG));
    }

    static void clearMiningAnchor(HiredWorkContext context) {
        context.state().remove(MINING_ANCHOR_POS_TAG);
        context.state().remove(MINING_ANCHOR_EXPIRES_GAME_TIME_TAG);
    }

    static int pocketRadius(HiredWorkContext context) {
        return Math.min(Math.max(1, context.radius()), MINING_POCKET_RADIUS);
    }

    static int elapsedBreakProgressTicks(ServerLevel level, HiredWorkContext context) {
        long now = level.getGameTime();
        long previous = context.progressTicks() <= 0 || !context.state().contains(LAST_BREAK_PROGRESS_GAME_TIME_TAG)
                ? now - 1L
                : context.state().getLong(LAST_BREAK_PROGRESS_GAME_TIME_TAG);
        context.state().putLong(LAST_BREAK_PROGRESS_GAME_TIME_TAG, now);
        return (int) Math.clamp(now - previous, 1L, 200L);
    }

    static boolean hasFreshExcavationLayerCache(ServerLevel level, HiredWorkContext context) {
        return context.state().contains(CURRENT_EXCAVATION_LAYER_PRESENT_TAG)
                && context.state().getLong(CURRENT_EXCAVATION_LAYER_EXPIRES_GAME_TIME_TAG) > level.getGameTime();
    }

    static Integer cachedExcavationLayer(HiredWorkContext context) {
        if (!context.state().getBoolean(CURRENT_EXCAVATION_LAYER_PRESENT_TAG)) {
            return null;
        }
        return context.state().getInt(CURRENT_EXCAVATION_LAYER_Y_TAG);
    }

    static void rememberExcavationLayer(ServerLevel level, HiredWorkContext context, Integer layerY) {
        context.state().putBoolean(CURRENT_EXCAVATION_LAYER_PRESENT_TAG, layerY != null);
        if (layerY == null) {
            context.state().remove(CURRENT_EXCAVATION_LAYER_Y_TAG);
        } else {
            context.state().putInt(CURRENT_EXCAVATION_LAYER_Y_TAG, layerY);
        }
        context.state().putLong(CURRENT_EXCAVATION_LAYER_EXPIRES_GAME_TIME_TAG, level.getGameTime() + EXCAVATION_LAYER_CACHE_TICKS);
    }

    static void clearExcavationLayerCache(HiredWorkContext context) {
        context.state().remove(CURRENT_EXCAVATION_LAYER_PRESENT_TAG);
        context.state().remove(CURRENT_EXCAVATION_LAYER_Y_TAG);
        context.state().remove(CURRENT_EXCAVATION_LAYER_EXPIRES_GAME_TIME_TAG);
    }

    static boolean isExcavationScanInProgress(HiredWorkContext context, HiredMiningMode mode) {
        return mode.excavatesArea() && HiredWorkAreaScan.isInProgress(context, EXCAVATION_SCAN_CURSOR_TAG);
    }

    static void ensureNoTargetScanCooldown(ServerLevel level, HiredWorkContext context) {
        if (context.state().getLong(NEXT_FULL_SCAN_GAME_TIME_TAG) <= level.getGameTime()) {
            context.state().putLong(NEXT_FULL_SCAN_GAME_TIME_TAG, level.getGameTime() + NO_TARGET_SCAN_COOLDOWN_TICKS);
        }
    }

    enum Phase {
        FIND_TARGET("find_target"),
        GATHER_SUPPLIES("gather_supplies"),
        ASSESS_HAZARDS("assess_hazards"),
        GATHER_HAZARD_BLOCKS("gather_hazard_blocks"),
        REMEDIATE_HAZARD("remediate_hazard"),
        PATH_TO_TARGET("path_to_target"),
        MINE_TARGET("mine_target"),
        DEPOSIT_OUTPUT("deposit_output"),
        WAITING_NO_TARGETS("waiting_no_targets"),
        BLOCKED_OUTPUT_FULL("blocked_output_full"),
        BLOCKED_MISSING_TOOL("blocked_missing_tool"),
        BLOCKED_MISSING_SUPPLIES("blocked_missing_supplies");

        private final String id;

        Phase(String id) {
            this.id = id;
        }

        String id() {
            return this.id;
        }

        static Phase byId(String id) {
            for (Phase phase : values()) {
                if (phase.id.equals(id)) {
                    return phase;
                }
            }
            return FIND_TARGET;
        }
    }

    enum Change {
        NONE,
        MODE_CHANGED,
        WORK_AREA_CHANGED,
        OPTIONS_CHANGED;

        boolean changed() {
            return this != NONE;
        }
    }
}
