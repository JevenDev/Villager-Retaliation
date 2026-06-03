package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

public final class StatusOnlyWorker implements HiredRoleWorker {
    private final HiredVillagerRole role;
    private final String status;

    public StatusOnlyWorker(HiredVillagerRole role, String status) {
        this.role = role;
        this.status = status;
    }

    @Override
    public HiredVillagerRole role() {
        return this.role;
    }

    @Override
    public WorkResult tick(ServerLevel level, Villager villager, ServerPlayer hirer, HiredWorkContext context) {
        context.setProgressTicks(0);
        HiredWorkerBrain.setLastTargetScanResult(context, "status_only");
        HiredWorkerBrain.setState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
        return WorkResult.idle(this.status);
    }
}
