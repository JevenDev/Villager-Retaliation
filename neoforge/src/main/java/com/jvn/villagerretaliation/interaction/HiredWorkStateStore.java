package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.work.HiredAnimalHandlingOptions;
import com.jvn.villagerretaliation.interaction.work.HiredFarmingOptions;
import com.jvn.villagerretaliation.interaction.work.HiredHuntingTargets;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState;
import com.jvn.villagerretaliation.interaction.work.logging.HiredLoggingOptions;
import com.jvn.villagerretaliation.interaction.work.mining.MiningHorizontalOptions;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.Villager;

/** Owns the durable NBT state shared by hired-worker services. */
public final class HiredWorkStateStore {
    private static final String TAG = "VillagerRetaliationHiredWork";
    private static final String DEFAULTS_VERSION_TAG = "DefaultsVersion";
    private static final int DEFAULTS_VERSION = 1;
    private static final String STATUS_REPLACEMENTS_TAG = "StatusReplacements";
    private static final String COMPLETED_TASKS_TAG = "CompletedTasks";
    private static final int MIN_WORK_RADIUS = 4;
    private static final int MAX_SKILLED_WORK_RADIUS = 32;

    private HiredWorkStateStore() {
    }

    public static CompoundTag state(Villager villager) {
        CompoundTag persistentData = villager.getPersistentData();
        if (!persistentData.contains(TAG, Tag.TAG_COMPOUND)) {
            persistentData.put(TAG, new CompoundTag());
        }
        return persistentData.getCompound(TAG);
    }

    public static void initializeDefaults(CompoundTag state, Villager villager) {
        if (state.getInt(DEFAULTS_VERSION_TAG) >= DEFAULTS_VERSION) {
            return;
        }
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
            state.putBoolean("UseAssignedStorageForSupplies", true);
        }
        if (!state.contains("AutoDepositOutputs", Tag.TAG_BYTE)) {
            state.putBoolean("AutoDepositOutputs", true);
        }
        if (!state.contains("LoggingFilter", Tag.TAG_STRING)) {
            state.putString("LoggingFilter", "any");
        }
        HiredLoggingOptions.initializeDefaults(state);
        HiredFarmingOptions.initializeDefaults(state);
        HiredAnimalHandlingOptions.initializeDefaults(state);
        if (!state.contains("NavigationTargetType", Tag.TAG_STRING)) {
            state.putString("NavigationTargetType", "interesting");
        }
        if (!state.contains(HiredCombatMode.STATE_TAG, Tag.TAG_STRING)) {
            state.putString(HiredCombatMode.STATE_TAG, HiredCombatMode.GUARD.serializedName());
        }
        if (!state.contains(HiredHuntingMode.STATE_TAG, Tag.TAG_STRING)) {
            state.putString(HiredHuntingMode.STATE_TAG, HiredHuntingMode.fromState(state).serializedName());
        }
        HiredHuntingTargets.initializeDefaults(state);
        MiningHorizontalOptions.initializeDefaults(state);
        if (!state.contains("Status", Tag.TAG_STRING)) {
            setStatus(state, "interaction.work.status.waiting_tick");
        }
        HiredWorkerBrain.initialize(state);
        if (!hadStoredArea) {
            int radius = Mth.clamp(state.getInt(HiredWorkArea.RADIUS_TAG), MIN_WORK_RADIUS, baseWorkRadiusCap());
            HiredWorkArea.fromCenter(
                    villager.blockPosition(),
                    radius,
                    Math.min(radius, 8),
                    state.getBoolean(HiredWorkArea.WORK_AREA_ASSIGNED_TAG)).save(state);
        }
        state.putInt(DEFAULTS_VERSION_TAG, DEFAULTS_VERSION);
    }

    public static boolean isActivelyWorking(Villager villager) {
        if (villager == null || !villager.getPersistentData().contains(TAG, Tag.TAG_COMPOUND)) {
            return false;
        }
        CompoundTag state = villager.getPersistentData().getCompound(TAG);
        if (!state.getBoolean("Enabled") || !state.contains("WorkerTaskState", Tag.TAG_STRING)) {
            return false;
        }
        return !HiredWorkerTaskState.byId(state.getString("WorkerTaskState")).isWaitingState();
    }

    public static void clearInheritedStateForNewborn(Villager child) {
        if (child != null) {
            child.getPersistentData().remove(TAG);
        }
    }

    static HiredWorkArea workArea(CompoundTag state, Villager villager) {
        if (!state.contains(HiredWorkArea.WORK_MIN_POS_TAG, Tag.TAG_LONG)
                || !state.contains(HiredWorkArea.WORK_MAX_POS_TAG, Tag.TAG_LONG)) {
            initializeDefaults(state, villager);
        }
        return HiredWorkArea.fromState(state, workCenter(state, villager));
    }

    static HiredWorkArea workAreaWithinMax(CompoundTag state, Villager villager, int maxRadius) {
        int safeMaxRadius = Math.max(MIN_WORK_RADIUS, maxRadius);
        HiredWorkArea area = workArea(state, villager);
        HiredWorkArea clamped = area.clampedTo(safeMaxRadius);
        if (!clamped.min().equals(area.min())
                || !clamped.max().equals(area.max())
                || state.getInt(HiredWorkArea.RADIUS_TAG) > safeMaxRadius) {
            clamped.save(state);
            return clamped;
        }
        state.putInt(HiredWorkArea.RADIUS_TAG, Mth.clamp(area.horizontalRadius(), MIN_WORK_RADIUS, safeMaxRadius));
        return area;
    }

    public static void pauseWork(
            ServerLevel level,
            Villager villager,
            HiredVillagerRole role,
            String status,
            Map<String, String> replacements) {
        updateWorkLifecycle(level, villager, role, status, replacements, true);
    }

    public static void cancelWork(
            ServerLevel level,
            Villager villager,
            HiredVillagerRole role,
            String status,
            Map<String, String> replacements) {
        updateWorkLifecycle(level, villager, role, status, replacements, false);
    }

    public static void finishWork(
            ServerLevel level,
            Villager villager,
            HiredVillagerRole role,
            String status,
            Map<String, String> replacements) {
        updateWorkLifecycle(level, villager, role, status, replacements, false);
    }

    private static void updateWorkLifecycle(
            ServerLevel level,
            Villager villager,
            HiredVillagerRole role,
            String status,
            Map<String, String> replacements,
            boolean pause) {
        HiredWorkSession session = HiredWorkSession.create(level, villager, role);
        if (session.worker() != null) {
            if (pause) {
                session.worker().pause(level, villager, session.context());
            } else {
                session.worker().stop(level, villager, session.context());
            }
        } else {
            session.context().setProgressTicks(0);
        }
        session.state().remove("NextWorkGameTime");
        setStatus(session.state(), status, replacements);
    }

    static void setStatus(CompoundTag state, String status) {
        setStatus(state, status, Map.of());
    }

    static void setStatus(CompoundTag state, String status, Map<String, String> replacements) {
        String safeStatus = status == null ? "" : status;
        boolean statusMatches = state.contains("Status", Tag.TAG_STRING)
                && safeStatus.equals(state.getString("Status"));
        if (replacements == null || replacements.isEmpty()) {
            if (statusMatches && !state.contains(STATUS_REPLACEMENTS_TAG, Tag.TAG_COMPOUND)) {
                return;
            }
            if (!statusMatches) {
                state.putString("Status", safeStatus);
            }
            state.remove(STATUS_REPLACEMENTS_TAG);
            return;
        }
        if (statusMatches && replacementsMatch(state, replacements)) {
            return;
        }
        if (!statusMatches) {
            state.putString("Status", safeStatus);
        }
        CompoundTag replacementTag = new CompoundTag();
        replacements.forEach((key, value) -> replacementTag.putString(key, value == null ? "" : value));
        state.put(STATUS_REPLACEMENTS_TAG, replacementTag);
    }

    static Map<String, String> statusReplacements(CompoundTag state) {
        if (!state.contains(STATUS_REPLACEMENTS_TAG, Tag.TAG_COMPOUND)) {
            return Map.of();
        }
        CompoundTag replacementTag = state.getCompound(STATUS_REPLACEMENTS_TAG);
        Map<String, String> replacements = new LinkedHashMap<>();
        for (String key : replacementTag.getAllKeys()) {
            replacements.put(key, replacementTag.getString(key));
        }
        return replacements;
    }

    static void recordCompletedTask(CompoundTag state) {
        state.putInt(COMPLETED_TASKS_TAG, Math.max(0, state.getInt(COMPLETED_TASKS_TAG)) + 1);
    }

    public static void resetReportProgress(Villager villager) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        resetReportProgress(state);
    }

    static void resetReportProgress(CompoundTag state) {
        state.remove(COMPLETED_TASKS_TAG);
    }

    static int completedTasks(CompoundTag state) {
        return Math.max(0, state.getInt(COMPLETED_TASKS_TAG));
    }

    private static BlockPos workCenter(CompoundTag state, Villager villager) {
        if (!state.contains(HiredWorkArea.WORK_CENTER_POS_TAG, Tag.TAG_LONG)) {
            state.putLong(HiredWorkArea.WORK_CENTER_POS_TAG, villager.blockPosition().asLong());
        }
        return BlockPos.of(state.getLong(HiredWorkArea.WORK_CENTER_POS_TAG));
    }

    private static boolean replacementsMatch(CompoundTag state, Map<String, String> replacements) {
        if (!state.contains(STATUS_REPLACEMENTS_TAG, Tag.TAG_COMPOUND)) {
            return false;
        }
        CompoundTag replacementTag = state.getCompound(STATUS_REPLACEMENTS_TAG);
        if (replacementTag.getAllKeys().size() != replacements.size()) {
            return false;
        }
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            String safeValue = entry.getValue() == null ? "" : entry.getValue();
            if (!replacementTag.contains(entry.getKey(), Tag.TAG_STRING)
                    || !safeValue.equals(replacementTag.getString(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    private static int baseWorkRadiusCap() {
        return Mth.clamp(
                VillagerRetaliationConfig.HIRED_WORK_DEFAULT_RADIUS.get(),
                MIN_WORK_RADIUS,
                MAX_SKILLED_WORK_RADIUS);
    }
}
