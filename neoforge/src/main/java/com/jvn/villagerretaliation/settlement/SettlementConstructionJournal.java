package com.jvn.villagerretaliation.settlement;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/** Write-ahead barrier: material debits and pending world receipts reach disk before any block changes. */
final class SettlementConstructionJournal {
    private SettlementConstructionJournal() { }

    static boolean persist(ServerLevel level, UUID project, UUID placement) {
        var data = SettlementSavedData.get(level);
        if (data.readOnly()) return false;
        var owner = data.settlements().stream().filter(town -> {
            var job = town.projects().get(project);
            return job != null && job.site() != null && (job.site().placements().containsKey(placement)
                    || job.site().awaitingCheckpoint().containsKey(placement));
        }).findFirst().orElse(null);
        if (owner == null) return false;
        if (persist(level)) return true;
        owner.projects().get(project).materialEscrow().putString("Blocker", "construction_checkpoint_failed");
        owner.changed();
        return false;
    }

    static boolean persist(ServerLevel level) {
        var data = SettlementSavedData.get(level);
        if (data.readOnly()) return false;
        try {
            // Uses the same synchronous checked writer as settlement lifecycle/migration transactions.
            // Drain older asynchronous saves first so none can overwrite the write-ahead snapshot.
            var root = new CompoundTag();
            root.put("data", data.save(new CompoundTag(), level.registryAccess()));
            net.minecraft.nbt.NbtUtils.addCurrentDataVersion(root);
            var file = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("data").resolve("villagerretaliation_settlements.dat");
            net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();
            net.neoforged.neoforge.common.IOUtilities.writeNbtCompressed(root, file);
            // IOUtilities force(true)s its temporary file before atomically replacing the target.
            for (var town : data.settlements()) for (var job : town.projects().values()) if (job.site() != null) {
                job.site().placements().values().forEach(SettlementConstructionPlacement::markJournaled);
                job.site().awaitingCheckpoint().values().forEach(SettlementConstructionPlacement::markJournaled);
            }
            return true;
        } catch (java.io.IOException | RuntimeException failure) {
            com.mojang.logging.LogUtils.getLogger().error("Construction write-ahead checkpoint failed; world placement is retained without application", failure);
            return false;
        }
    }
}
