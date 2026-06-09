package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

public final class NitwitWorker implements HiredRoleWorker {
    private static final String[] REPORT_KEYS = {
            "interaction.work.nitwit.report.sky",
            "interaction.work.nitwit.report.supervising",
            "interaction.work.nitwit.report.shadows",
            "interaction.work.nitwit.report.pointing"
    };

    @Override
    public HiredVillagerRole role() {
        return HiredVillagerRole.NITWIT;
    }

    @Override
    public WorkResult tick(ServerLevel level, Villager villager, ServerPlayer hirer, HiredWorkContext context) {
        long lastNotice = context.state().getLong("NitwitNoticeTick");
        if (level.getGameTime() - lastNotice < 20L * 60L) {
            HiredWorkerBrain.setLastTargetScanResult(context, "nitwit_cooldown");
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.IDLE);
            return WorkResult.idle("interaction.work.nitwit.cooldown");
        }
        context.state().putLong("NitwitNoticeTick", level.getGameTime());
        String line = REPORT_KEYS[Math.floorMod((int) (level.getGameTime() / 1200L + villager.getId()), REPORT_KEYS.length)];
        HiredWorkerBrain.setLastTargetScanResult(context, "nitwit_report_ready");
        HiredWorkerBrain.setState(context, HiredWorkerTaskState.WORKING);
        return WorkResult.completed(line);
    }
}
