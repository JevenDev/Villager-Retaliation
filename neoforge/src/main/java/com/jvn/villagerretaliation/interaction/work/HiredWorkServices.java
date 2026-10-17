package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionService;
import net.minecraft.core.BlockPos;

public interface HiredWorkServices {
    GatheringDecision gathering();

    HiredProductionService.Access production(HiredVillagerRole role);

    void releaseProduction(HiredVillagerRole role);

    boolean maintainGuard(HiredWorkContext context);

    boolean hasGuardOrder();

    boolean allowsGuardTarget(BlockPos position);

    WorkResult equipGuard(HiredWorkContext context, double speed);

    record GatheringDecision(boolean managed, boolean allowed, String blocker) { }
}
