package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.HiredWorkServices;
import com.jvn.villagerretaliation.interaction.work.WorkResult;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionService;
import com.jvn.villagerretaliation.settlement.SettlementEquipmentService;
import com.jvn.villagerretaliation.settlement.SettlementGatheringPolicy;
import com.jvn.villagerretaliation.settlement.SettlementGuardService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

public record HiredWorkEnvironment(ServerLevel level, Villager villager) implements HiredWorkServices {
    public static HiredWorkServices resolve(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) {
            throw new IllegalArgumentException("Worker services require a server level");
        }
        return new HiredWorkEnvironment(level, villager);
    }

    @Override
    public GatheringDecision gathering() {
        var decision = SettlementGatheringPolicy.inspect(level, villager);
        return new GatheringDecision(decision.managed(), decision.allowed(), decision.blocker());
    }

    @Override
    public HiredProductionService.Access production(HiredVillagerRole role) {
        return HiredProductionService.access(level, villager, role);
    }

    @Override
    public void releaseProduction(HiredVillagerRole role) {
        HiredProductionService.releasePlanned(level, villager, role);
    }

    @Override
    public boolean maintainGuard(HiredWorkContext context) {
        return SettlementGuardService.maintain(level, villager, context);
    }

    @Override
    public boolean hasGuardOrder() {
        return SettlementGuardService.order(villager).isPresent();
    }

    @Override
    public boolean allowsGuardTarget(BlockPos position) {
        return SettlementGuardService.allowsTarget(villager, position);
    }

    @Override
    public WorkResult equipGuard(HiredWorkContext context, double speed) {
        return SettlementEquipmentService.tick(level, villager, context, speed);
    }
}
