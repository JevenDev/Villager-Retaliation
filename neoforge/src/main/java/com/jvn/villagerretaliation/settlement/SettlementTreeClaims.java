package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class SettlementTreeClaims {
    private SettlementTreeClaims() {
    }

    public static boolean claimedByOther(ServerLevel level, UUID worker, List<BlockPos> logs) {
        var positions = new HashSet<Long>();
        logs.forEach(position -> positions.add(position.asLong()));
        for (var town : SettlementSavedData.get(level).settlements()) {
            if (!town.dimension().equals(level.dimension().location())) continue;
            var own = town.workers().get(worker);
            boolean continuing = own != null && java.util.Arrays.stream(settings(level, own).getLongArray("PendingLoggingTreeLogs"))
                    .anyMatch(positions::contains);
            for (var other : town.workers().values()) {
                if (other.id().equals(worker) || other.role() != HiredVillagerRole.LOGGING
                        || !other.area().equals(town.duties().get(other.id()))) continue;
                for (long position : settings(level, other).getLongArray("PendingLoggingTreeLogs")) {
                    if (positions.contains(position) && (!continuing || other.id().compareTo(worker) < 0)) return true;
                }
            }
        }
        return false;
    }

    private static net.minecraft.nbt.CompoundTag settings(ServerLevel level, SettlementWorkerState worker) {
        return level.getEntity(worker.id()) instanceof net.minecraft.world.entity.npc.Villager villager
                ? com.jvn.villagerretaliation.interaction.HiredWorkStateStore.state(villager) : worker.settings();
    }
}
