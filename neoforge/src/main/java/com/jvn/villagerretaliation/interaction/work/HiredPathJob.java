package com.jvn.villagerretaliation.interaction.work;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

public abstract class HiredPathJob {
    private static final int MAX_REACHABLE_RESULTS_TO_COMPARE = 4;
    protected final ServerLevel level;
    protected final Villager villager;
    private final int maxCandidates;

    protected HiredPathJob(ServerLevel level, Villager villager, int maxCandidates) {
        this.level = level;
        this.villager = villager;
        this.maxCandidates = Math.max(1, maxCandidates);
    }

    public final HiredPathResult search() {
        List<BlockPos> candidates = uniqueCandidates();
        candidates.sort(Comparator.comparingDouble(this::candidateScore));

        int evaluated = 0;
        int reachableResults = 0;
        HiredPathResult bestResult = null;
        for (BlockPos candidate : candidates) {
            if (evaluated >= this.maxCandidates) {
                break;
            }
            evaluated++;
            HiredPathResult result = evaluate(candidate);
            if (result.reachesDestination()) {
                if (bestResult == null || result.score() < bestResult.score()) {
                    bestResult = result;
                }
                reachableResults++;
                if (reachableResults >= MAX_REACHABLE_RESULTS_TO_COMPARE) {
                    break;
                }
            }
        }
        return bestResult != null ? bestResult : HiredPathResult.blocked();
    }

    private List<BlockPos> uniqueCandidates() {
        List<BlockPos> candidates = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        collectCandidates(pos -> {
            BlockPos immutable = pos.immutable();
            if (seen.add(immutable.asLong())) {
                candidates.add(immutable);
            }
        });
        return candidates;
    }

    protected double candidateScore(BlockPos pos) {
        double distance = this.villager.distanceToSqr(pos.getCenter());
        int vertical = Math.abs(pos.getY() - this.villager.blockPosition().getY());
        return distance + vertical * vertical * 4.0D + HiredPathMemory.recentCost(this.villager, pos);
    }

    protected abstract void collectCandidates(CandidateSink sink);

    protected abstract HiredPathResult evaluate(BlockPos candidate);

    @FunctionalInterface
    interface CandidateSink {
        void add(BlockPos pos);
    }
}
