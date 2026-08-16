package com.jvn.villagerretaliation.inventory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;

/** Shared access to the logical inventory behind a container, including both halves of a double chest. */
public final class ConnectedContainerResolver {
    private ConnectedContainerResolver() {
    }

    public static Container resolve(ServerLevel level, BlockPos pos) {
        VillagerInventoryOverflowService.ContainerCandidate candidate =
                VillagerInventoryOverflowService.ContainerCandidate.resolve(level, pos);
        return candidate == null ? null : candidate.container();
    }
}
