package com.jvn.villagerretaliation.interaction.work;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;

public final class BuilderTaskState {
    public static final String TASK_TAG = "BuilderTask";
    private static final String PENDING_STRUCTURE_TAG = "BuilderPendingStructure";
    private static final String STRUCTURE_ID_TAG = "StructureId";
    private static final String STRUCTURE_LABEL_TAG = "StructureLabel";
    private static final String ORIGIN_TAG = "Origin";
    private static final String ROTATION_TAG = "Rotation";
    private static final String PHASE_TAG = "Phase";
    private static final String PLACED_INDEX_TAG = "PlacedIndex";
    private static final String TOTAL_BLOCKS_TAG = "TotalBlocks";
    private static final String PAID_CURRENCY_TAG = "PaidCurrency";
    private static final String STARTED_GAME_TIME_TAG = "StartedGameTime";
    private static final String BLOCKED_REASON_TAG = "BlockedReason";
    private static final String MATERIAL_SUMMARY_TAG = "MaterialSummary";
    private static final String JOB_ID_TAG = "JobId";

    private BuilderTaskState() {
    }

    public static boolean hasTask(CompoundTag state) {
        return state.contains(TASK_TAG, Tag.TAG_COMPOUND)
                && task(state).contains(STRUCTURE_ID_TAG, Tag.TAG_STRING);
    }

    public static CompoundTag task(CompoundTag state) {
        if (!state.contains(TASK_TAG, Tag.TAG_COMPOUND)) {
            state.put(TASK_TAG, new CompoundTag());
        }
        return state.getCompound(TASK_TAG);
    }

    public static void start(
            CompoundTag state,
            BuilderStructureCatalog.Entry entry,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin,
            Rotation rotation,
            int paidCurrency,
            long gameTime) {
        start(state, entry, plan, origin, rotation, paidCurrency, gameTime, UUID.randomUUID());
    }

    public static void start(
            CompoundTag state,
            BuilderStructureCatalog.Entry entry,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin,
            Rotation rotation,
            int paidCurrency,
            long gameTime,
            UUID jobId) {
        CompoundTag task = new CompoundTag();
        task.putString(JOB_ID_TAG, (jobId == null ? UUID.randomUUID() : jobId).toString());
        task.putString(STRUCTURE_ID_TAG, entry.id().toString());
        task.putString(STRUCTURE_LABEL_TAG, entry.menuLabel());
        task.putLong(ORIGIN_TAG, origin.asLong());
        task.putString(ROTATION_TAG, (rotation == null ? Rotation.NONE : rotation).name());
        task.putString(PHASE_TAG, BuilderBuildPhase.COLLECTING_MATERIALS.id());
        task.putInt(PLACED_INDEX_TAG, 0);
        task.putInt(TOTAL_BLOCKS_TAG, plan.blocks().size());
        task.putInt(PAID_CURRENCY_TAG, Math.max(0, paidCurrency));
        task.putLong(STARTED_GAME_TIME_TAG, gameTime);
        task.putString(MATERIAL_SUMMARY_TAG, plan.materialSummary(5));
        state.put(TASK_TAG, task);
        clearPendingStructure(state);
    }

    public static void clearTask(CompoundTag state) {
        state.remove(TASK_TAG);
    }

    public static void setPendingStructure(CompoundTag state, ResourceLocation structureId) {
        if (structureId == null) {
            clearPendingStructure(state);
            return;
        }
        state.putString(PENDING_STRUCTURE_TAG, structureId.toString());
    }

    public static Optional<ResourceLocation> pendingStructure(CompoundTag state) {
        if (!state.contains(PENDING_STRUCTURE_TAG, Tag.TAG_STRING)) {
            return Optional.empty();
        }
        return Optional.ofNullable(ResourceLocation.tryParse(state.getString(PENDING_STRUCTURE_TAG)));
    }

    public static void clearPendingStructure(CompoundTag state) {
        state.remove(PENDING_STRUCTURE_TAG);
    }

    public static Optional<ResourceLocation> structureId(CompoundTag state) {
        CompoundTag task = task(state);
        if (!task.contains(STRUCTURE_ID_TAG, Tag.TAG_STRING)) {
            return Optional.empty();
        }
        return Optional.ofNullable(ResourceLocation.tryParse(task.getString(STRUCTURE_ID_TAG)));
    }

    public static String structureLabel(CompoundTag state) {
        return task(state).getString(STRUCTURE_LABEL_TAG);
    }

    public static BlockPos origin(CompoundTag state) {
        CompoundTag task = task(state);
        return task.contains(ORIGIN_TAG, Tag.TAG_LONG) ? BlockPos.of(task.getLong(ORIGIN_TAG)) : BlockPos.ZERO;
    }

    public static Rotation rotation(CompoundTag state) {
        String value = task(state).getString(ROTATION_TAG);
        try {
            return value == null || value.isBlank() ? Rotation.NONE : Rotation.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return Rotation.NONE;
        }
    }

    public static BuilderBuildPhase phase(CompoundTag state) {
        return BuilderBuildPhase.byId(task(state).getString(PHASE_TAG));
    }

    public static void setPhase(CompoundTag state, BuilderBuildPhase phase) {
        task(state).putString(PHASE_TAG, (phase == null ? BuilderBuildPhase.IDLE : phase).id());
    }

    public static int placedIndex(CompoundTag state) {
        return Math.max(0, task(state).getInt(PLACED_INDEX_TAG));
    }

    public static void setPlacedIndex(CompoundTag state, int index) {
        task(state).putInt(PLACED_INDEX_TAG, Math.max(0, index));
    }

    public static int totalBlocks(CompoundTag state) {
        return Math.max(0, task(state).getInt(TOTAL_BLOCKS_TAG));
    }

    public static int paidCurrency(CompoundTag state) {
        return Math.max(0, task(state).getInt(PAID_CURRENCY_TAG));
    }

    public static String materialSummary(CompoundTag state) {
        return task(state).getString(MATERIAL_SUMMARY_TAG);
    }

    public static void setPlacement(
            CompoundTag state,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin,
            Rotation rotation) {
        CompoundTag task = task(state);
        task.putLong(ORIGIN_TAG, origin.asLong());
        task.putString(ROTATION_TAG, (rotation == null ? Rotation.NONE : rotation).name());
        if (plan != null) {
            task.putInt(TOTAL_BLOCKS_TAG, plan.blocks().size());
            task.putString(MATERIAL_SUMMARY_TAG, plan.materialSummary(5));
        }
        task.remove(BLOCKED_REASON_TAG);
    }

    public static Optional<UUID> jobId(CompoundTag state) {
        CompoundTag task = task(state);
        if (!task.contains(JOB_ID_TAG, Tag.TAG_STRING)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(task.getString(JOB_ID_TAG)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public static void setBlocked(CompoundTag state, String reason) {
        setPhase(state, BuilderBuildPhase.BLOCKED);
        task(state).putString(BLOCKED_REASON_TAG, reason == null ? "" : reason);
    }

    public static String blockedReason(CompoundTag state) {
        return task(state).getString(BLOCKED_REASON_TAG);
    }

    public static Map<String, String> replacements(CompoundTag state) {
        int placed = placedIndex(state);
        int total = totalBlocks(state);
        return Map.of(
                "structure", structureLabel(state),
                "placed", Integer.toString(placed),
                "total", Integer.toString(total),
                "blocks", Integer.toString(total),
                "remaining", Integer.toString(Math.max(0, total - placed)),
                "materials", materialSummary(state),
                "reason", blockedReason(state)
        );
    }
}
