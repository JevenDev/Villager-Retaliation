package com.jvn.villagerretaliation.interaction.work;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

final class HiredTargetSearch {
    private HiredTargetSearch() {
    }

    static <T> T find(
            ServerLevel level,
            HiredWorkContext context,
            Supplier<T> activeTarget,
            Predicate<T> activeValidator,
            Function<Predicate<BlockPos>, T> plannedTarget,
            Predicate<BlockPos> candidateFilter,
            String nextScanGameTimeTag,
            String scanCursorTag,
            int maxScanPositions,
            Function<List<BlockPos>, T> objectiveRebuilder,
            Messages messages) {
        T active = activeTarget == null ? null : activeTarget.get();
        if (active != null && (activeValidator == null || activeValidator.test(active))) {
            HiredWorkerBrain.setLastTargetScanResult(context, messages.activeTarget());
            return active;
        }

        Predicate<BlockPos> safeFilter = candidateFilter == null ? ignored -> true : candidateFilter;
        T planned = plannedTarget == null ? null : plannedTarget.apply(safeFilter);
        if (planned != null) {
            HiredWorkerBrain.setLastTargetScanResult(context, messages.plannedTarget());
            return planned;
        }

        if (!HiredWorkAreaScan.isInProgress(context, scanCursorTag)
                && level.getGameTime() < context.state().getLong(nextScanGameTimeTag)) {
            HiredWorkerBrain.setLastTargetScanResult(context, messages.scanCooldown());
            return null;
        }

        HiredWorkAreaScan.Result scan = HiredWorkAreaScan.collect(
                context,
                scanCursorTag,
                maxScanPositions,
                safeFilter);
        T target = objectiveRebuilder.apply(scan.candidates());
        if (target == null) {
            if (scan.completedFullPass()) {
                context.state().putLong(nextScanGameTimeTag, level.getGameTime() + messages.noTargetCooldownTicks());
                HiredWorkerBrain.setLastTargetScanResult(context, messages.fullScanNoReachableTargets());
            } else {
                HiredWorkerBrain.setLastTargetScanResult(context, messages.partialScanPrefix() + scan.visitedPositions());
            }
        } else {
            context.state().remove(nextScanGameTimeTag);
            HiredWorkerBrain.setLastTargetScanResult(context, messages.targetFound());
        }
        return target;
    }

    record Messages(
            String activeTarget,
            String plannedTarget,
            String scanCooldown,
            String fullScanNoReachableTargets,
            String partialScanPrefix,
            String targetFound,
            int noTargetCooldownTicks) {
    }
}
