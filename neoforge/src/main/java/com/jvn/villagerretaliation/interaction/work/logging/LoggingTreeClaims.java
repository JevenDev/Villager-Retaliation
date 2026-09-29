package com.jvn.villagerretaliation.interaction.work.logging;

import com.jvn.villagerretaliation.interaction.work.HiredPathMemory;
import com.jvn.villagerretaliation.settlement.SettlementTreeClaims;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

public final class LoggingTreeClaims {
    private LoggingTreeClaims() {
    }

    public static boolean available(ServerLevel level, Villager villager, List<BlockPos> logs) {
        return logs.stream().noneMatch(position -> HiredPathMemory.isReservedByOther(level, villager, position))
                && !SettlementTreeClaims.claimedByOther(level, villager.getUUID(), logs);
    }

    public static boolean reserve(ServerLevel level, Villager villager, List<BlockPos> logs) {
        if (!available(level, villager, logs)) return false;
        logs.forEach(position -> HiredPathMemory.reserveTarget(level, villager, position));
        return true;
    }
}
