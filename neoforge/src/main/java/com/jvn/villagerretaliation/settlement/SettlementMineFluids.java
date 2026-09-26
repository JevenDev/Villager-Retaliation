package com.jvn.villagerretaliation.settlement;

import java.util.Comparator;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

final class SettlementMineFluids {
    private SettlementMineFluids() {
    }

    static void advance(ServerLevel level, SettlementMine mine, long now) {
        int remaining = mine.fluidElapsed(now);
        if (remaining <= 0 || mine.conflict()) return;
        var deferred = new HashSet<BlockPos>();
        SettlementMinePhysics world = null;
        for (int work = 0; work < 128; work++) {
            var next = mine.sections().values().stream()
                    .filter(section -> !level.shouldTickBlocksAt(new ChunkPos(section.min()).toLong()))
                    .flatMap(section -> section.fluidTicks().stream().map(tick -> new Pending(section, tick)))
                    .filter(pending -> !deferred.contains(pending.tick().position()))
                    .min(Comparator.comparingInt((Pending pending) -> pending.tick().delay())
                            .thenComparingInt(pending -> pending.tick().priority().getValue())).orElse(null);
            if (next == null) return;
            int elapsed = Math.min(remaining, next.tick().delay());
            for (var section : mine.sections().values()) {
                if (!level.shouldTickBlocksAt(new ChunkPos(section.min()).toLong())) section.elapseFluids(elapsed);
            }
            remaining -= elapsed;
            if (next.tick().delay() > elapsed) return;
            var due = new SettlementTerrainTick(next.tick().position(), next.tick().type(), true, 0, next.tick().priority());
            next.section().consumeFluid(due);
            boolean applied = false;
            try {
                if (world == null) world = new SettlementMinePhysics(level, mine);
                applied = world.fluidTick(due);
            } catch (SettlementMiningView.Unavailable unavailable) {
                deferred.add(due.position());
            }
            if (!applied) {
                next.section().schedule(due);
                deferred.add(due.position());
            } else {
                SettlementMinePhysics.advance(level, mine);
            }
        }
    }

    private record Pending(SettlementTerrainSection section, SettlementTerrainTick tick) {
    }
}
