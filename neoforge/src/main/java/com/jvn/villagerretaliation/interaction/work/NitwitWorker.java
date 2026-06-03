package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

public final class NitwitWorker implements HiredRoleWorker {
    private static final String[] LINES = {
            "Nitwit report: I found the sky. Still up there.",
            "Nitwit report: I am supervising the general direction of things.",
            "Nitwit report: danger check complete. I disliked several shadows.",
            "Nitwit report: I can point at work, if that helps."
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
            return WorkResult.idle("Nitwit is wandering productively-adjacent.");
        }
        context.state().putLong("NitwitNoticeTick", level.getGameTime());
        String line = LINES[Math.floorMod((int) (level.getGameTime() / 1200L + villager.getId()), LINES.length)];
        HiredWorkerBrain.setLastTargetScanResult(context, "nitwit_report_ready");
        HiredWorkerBrain.setState(context, HiredWorkerTaskState.WORKING);
        return WorkResult.completed(line);
    }
}
