package com.jvn.villagerretaliation.interaction.work;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;

public final class HiredWorkPlan {
    private static final String PLAN_TARGETS_TAG = "PlannedWorkTargets";
    private static final String PLAN_OBJECTIVE_TYPE_TAG = "PlannedWorkObjectiveType";
    private static final String PLAN_OBJECTIVE_ANCHOR_TAG = "PlannedWorkObjectiveAnchor";

    private HiredWorkPlan() {
    }

    public static void clear(HiredWorkContext context) {
        context.state().remove(PLAN_TARGETS_TAG);
        context.state().remove(PLAN_OBJECTIVE_TYPE_TAG);
        context.state().remove(PLAN_OBJECTIVE_ANCHOR_TAG);
    }

    public static int size(HiredWorkContext context) {
        return targets(context).size();
    }

    static List<BlockPos> targets(HiredWorkContext context) {
        List<BlockPos> targets = new ArrayList<>();
        CompoundTag state = context.state();
        if (!state.contains(PLAN_TARGETS_TAG, Tag.TAG_LIST)) {
            return targets;
        }
        ListTag list = state.getList(PLAN_TARGETS_TAG, Tag.TAG_LONG);
        for (Tag entry : list) {
            if (entry instanceof LongTag longTag) {
                targets.add(BlockPos.of(longTag.getAsLong()));
            }
        }
        return targets;
    }

    static void replace(HiredWorkContext context, List<BlockPos> targets, int maxTargets) {
        ListTag serialized = new ListTag();
        Set<Long> seen = new LinkedHashSet<>();
        int safeMaxTargets = Math.max(1, maxTargets);
        for (BlockPos target : targets) {
            if (target == null || !seen.add(target.asLong())) {
                continue;
            }
            serialized.add(LongTag.valueOf(target.asLong()));
            if (serialized.size() >= safeMaxTargets) {
                break;
            }
        }
        if (serialized.isEmpty()) {
            clear(context);
            return;
        }
        context.state().put(PLAN_TARGETS_TAG, serialized);
    }

    static void replaceWithObjective(
            HiredWorkContext context,
            String objectiveType,
            BlockPos objectiveAnchor,
            List<BlockPos> targets,
            int maxTargets) {
        replace(context, targets, maxTargets);
        if (targets(context).isEmpty()) {
            return;
        }
        if (objectiveType == null || objectiveType.isBlank()) {
            context.state().remove(PLAN_OBJECTIVE_TYPE_TAG);
        } else {
            context.state().putString(PLAN_OBJECTIVE_TYPE_TAG, objectiveType);
        }
        if (objectiveAnchor == null) {
            context.state().remove(PLAN_OBJECTIVE_ANCHOR_TAG);
        } else {
            context.state().putLong(PLAN_OBJECTIVE_ANCHOR_TAG, objectiveAnchor.asLong());
        }
    }

    static void removeTarget(HiredWorkContext context, BlockPos target) {
        if (target == null) {
            return;
        }
        List<BlockPos> remaining = new ArrayList<>();
        for (BlockPos planned : targets(context)) {
            if (!planned.equals(target)) {
                remaining.add(planned);
            }
        }
        replace(context, remaining, Math.max(1, remaining.size()));
    }

    static void prioritize(HiredWorkContext context, BlockPos target, int maxTargets) {
        if (target == null) {
            return;
        }
        List<BlockPos> reordered = new ArrayList<>();
        reordered.add(target.immutable());
        for (BlockPos planned : targets(context)) {
            if (!planned.equals(target)) {
                reordered.add(planned);
            }
        }
        replace(context, reordered, maxTargets);
    }

    static void retainMatching(HiredWorkContext context, Predicate<BlockPos> predicate, int maxTargets) {
        Predicate<BlockPos> safePredicate = predicate == null ? ignored -> true : predicate;
        List<BlockPos> retained = new ArrayList<>();
        for (BlockPos planned : targets(context)) {
            if (safePredicate.test(planned)) {
                retained.add(planned);
            }
        }
        replace(context, retained, Math.max(1, maxTargets));
    }

    public static String objectiveType(HiredWorkContext context) {
        String objectiveType = context.state().getString(PLAN_OBJECTIVE_TYPE_TAG);
        return objectiveType == null || objectiveType.isBlank() ? "none" : objectiveType;
    }

    public static BlockPos objectiveAnchor(HiredWorkContext context) {
        return context.state().contains(PLAN_OBJECTIVE_ANCHOR_TAG, Tag.TAG_LONG)
                ? BlockPos.of(context.state().getLong(PLAN_OBJECTIVE_ANCHOR_TAG))
                : null;
    }

    static List<BlockPos> routeOrder(BlockPos origin, Iterable<BlockPos> candidates, int maxTargets) {
        List<BlockPos> remaining = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        for (BlockPos candidate : candidates) {
            if (candidate != null && seen.add(candidate.asLong())) {
                remaining.add(candidate.immutable());
            }
        }
        List<BlockPos> ordered = new ArrayList<>();
        if (remaining.isEmpty()) {
            return ordered;
        }

        BlockPos cursor = origin == null ? remaining.getFirst() : origin.immutable();
        int safeMaxTargets = Math.max(1, maxTargets);
        while (!remaining.isEmpty() && ordered.size() < safeMaxTargets) {
            int bestIndex = 0;
            double bestScore = Double.MAX_VALUE;
            for (int i = 0; i < remaining.size(); i++) {
                BlockPos candidate = remaining.get(i);
                double score = routeScore(cursor, candidate, ordered.size());
                if (score < bestScore) {
                    bestScore = score;
                    bestIndex = i;
                }
            }
            BlockPos next = remaining.remove(bestIndex);
            ordered.add(next);
            cursor = next;
        }
        return ordered;
    }

    private static double routeScore(BlockPos from, BlockPos to, int depth) {
        double distance = from.distSqr(to);
        double verticalPenalty = Math.abs(from.getY() - to.getY()) * 6.0D;
        return distance + verticalPenalty + depth * 0.1D;
    }
}
