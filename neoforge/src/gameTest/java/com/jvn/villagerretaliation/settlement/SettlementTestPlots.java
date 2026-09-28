package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

public final class SettlementTestPlots {
    private SettlementTestPlots() {
    }

    public static void claim(ServerLevel level, SettlementRecord town, BlockPos min, BlockPos max) {
        claim(SettlementSavedData.get(level), VillageAllegianceRegistrySavedData.get(level), town, min, max);
    }

    static void claim(SettlementSavedData data, VillageAllegianceRegistrySavedData registry, SettlementRecord town, BlockPos min, BlockPos max) {
        if (town.contains(min, max)) return;
        var desired = new TreeSet<>(town.territory().plots());
        for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++) {
            for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++) desired.add(ChunkPos.asLong(x, z));
        }
        var connected = SettlementTerritoryMigration.connect(desired, java.util.Set.of(), town.territory().foundingChunk().toLong());
        for (var stage : SettlementStage.values()) town.territory().advance(stage);
        var remaining = new TreeSet<>(connected);
        remaining.removeAll(town.territory().plots());
        while (!remaining.isEmpty()) {
            long next = remaining.stream().filter(plot -> town.territory().adjacent(ChunkPos.getX(plot), ChunkPos.getZ(plot))).findFirst().orElseThrow();
            int x = ChunkPos.getX(next), z = ChunkPos.getZ(next);
            if (data.reserved(registry, town, x, z)) throw new IllegalStateException("Fixture plots conflict with another village");
            town.territory().claim(x, z, town.territory().freeClaims() > 0);
            remaining.remove(next);
        }
        data.claimed(registry, town);
    }
}
