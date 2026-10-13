package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class SettlementExpeditions {
    public static final int RANGE = 128;

    private SettlementExpeditions() { }

    public static boolean permits(ServerLevel level, SettlementRecord town, HiredTemporaryTask task) {
        if (!task.kind().expedition()) return town.contains(task.min(), task.max());
        if (level.isOutsideBuildHeight(task.min()) || level.isOutsideBuildHeight(task.max())
                || !level.getWorldBorder().isWithinBounds(task.min()) || !level.getWorldBorder().isWithinBounds(task.max())) return false;
        var data = SettlementSavedData.get(level);
        for (int x = task.min().getX() >> 4; x <= task.max().getX() >> 4; x++) {
            for (int z = task.min().getZ() >> 4; z <= task.max().getZ() >> 4; z++) {
                if (!near(town, x, z) || data.owner(town.dimension(), x, z).filter(owner -> !owner.equals(town.id())).isPresent()) return false;
            }
        }
        if (task.kind() == HiredTemporaryTask.Kind.STAIRCASE_PROSPECT) return permits(level, town, task.consume());
        return true;
    }

    static boolean near(SettlementRecord town, int x, int z) {
        for (long packed : town.territory().plots()) {
            var plot = new net.minecraft.world.level.ChunkPos(packed);
            if (Math.abs((long) x - plot.x) <= RANGE / 16 && Math.abs((long) z - plot.z) <= RANGE / 16) return true;
        }
        return false;
    }
}
