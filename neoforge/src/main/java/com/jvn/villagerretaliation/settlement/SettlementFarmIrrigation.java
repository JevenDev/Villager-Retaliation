package com.jvn.villagerretaliation.settlement;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import net.minecraft.core.BlockPos;

final class SettlementFarmIrrigation {
    private SettlementFarmIrrigation() {}

    static boolean reaches(BlockPos water, BlockPos soil) {
        return water.getY() >= soil.getY() && water.getY() <= soil.getY() + 1
                && Math.abs(water.getX() - soil.getX()) <= 4 && Math.abs(water.getZ() - soil.getZ()) <= 4;
    }

    static List<BlockPos> plan(List<BlockPos> dry, List<BlockPos> candidates) {
        if (dry.isEmpty()) return List.of();
        var indices = new HashMap<BlockPos, Integer>();
        for (int i = 0; i < dry.size(); i++) indices.put(dry.get(i), i);
        var coverage = new ArrayList<BitSet>();
        for (var water : candidates) {
            var covered = new BitSet(dry.size());
            for (var pos : BlockPos.betweenClosed(water.offset(-4, -1, -4), water.offset(4, 0, 4))) {
                var index = indices.get(pos);
                if (index != null) covered.set(index);
            }
            coverage.add(covered);
        }
        var all = new BitSet(dry.size());
        all.set(0, dry.size());
        var covering = new ArrayList<List<Integer>>();
        for (int i = 0; i < dry.size(); i++) covering.add(new ArrayList<>());
        for (int i = 0; i < coverage.size(); i++)
            for (int bit = coverage.get(i).nextSetBit(0); bit >= 0; bit = coverage.get(i).nextSetBit(bit + 1)) covering.get(bit).add(i);
        List<Integer> best = greedy(all, coverage, covering, List.of());
        List<Integer> partial = List.of();
        int partialMissing = dry.size();
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                var grid = new ArrayList<Integer>();
                var missing = (BitSet) all.clone();
                for (int i = 0; i < candidates.size(); i++) {
                    var pos = candidates.get(i);
                    if (Math.floorMod(pos.getX(), 9) != x || Math.floorMod(pos.getZ(), 9) != z || !missing.intersects(coverage.get(i))) continue;
                    grid.add(i);
                    missing.andNot(coverage.get(i));
                }
                if (missing.isEmpty() && (best == null || grid.size() < best.size())) best = grid;
                if (missing.cardinality() < partialMissing || missing.cardinality() == partialMissing && grid.size() < partial.size()) {
                    partial = grid;
                    partialMissing = missing.cardinality();
                }
            }
        }
        var completed = greedy(all, coverage, covering, partial);
        if (completed != null && (best == null || completed.size() < best.size())) best = completed;
        if (best == null) return null;
        var reduced = new ArrayList<>(best);
        for (int i = reduced.size() - 1; i >= 0; i--) {
            var union = new BitSet(dry.size());
            for (int j = 0; j < reduced.size(); j++) if (i != j) union.or(coverage.get(reduced.get(j)));
            if (union.equals(all)) reduced.remove(i);
        }
        return reduced.stream().map(candidates::get).toList();
    }

    private static List<Integer> greedy(BitSet all, List<BitSet> coverage, List<List<Integer>> covering, List<Integer> seed) {
        var missing = (BitSet) all.clone();
        var result = new ArrayList<>(seed);
        seed.forEach(index -> missing.andNot(coverage.get(index)));
        int[] scores = new int[coverage.size()];
        for (int bit = missing.nextSetBit(0); bit >= 0; bit = missing.nextSetBit(bit + 1))
            for (int candidate : covering.get(bit)) scores[candidate]++;
        while (!missing.isEmpty()) {
            int best = -1, count = 0;
            for (int i = 0; i < scores.length; i++)
                if (scores[i] > count) { best = i; count = scores[i]; }
            if (best < 0) return null;
            result.add(best);
            var covered = coverage.get(best);
            for (int bit = covered.nextSetBit(0); bit >= 0; bit = covered.nextSetBit(bit + 1)) {
                if (!missing.get(bit)) continue;
                missing.clear(bit);
                for (int candidate : covering.get(bit)) scores[candidate]--;
            }
        }
        return result;
    }
}
