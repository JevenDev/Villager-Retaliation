package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.settlement.SettlementWorkforceService;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;

final class SettlementTradingWorker {
    private SettlementTradingWorker() {
    }

    static WorkResult tick(ServerLevel level, Villager villager, HiredWorkContext context,
                           SettlementWorkforceService.Duty duty) {
        var survey = duty.town().surveys().get(duty.area().id());
        BlockPos station = survey == null ? null : survey.workstations().stream()
                .filter(level::hasChunkAt)
                .filter(pos -> level.getBlockState(pos).is(Blocks.LECTERN))
                .min(Comparator.comparingDouble(pos -> villager.distanceToSqr(
                        pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D))).orElse(null);
        if (station == null) {
            VillagerTaskNavigationUtil.stopHiredNavigation(villager);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
            return WorkResult.idle("interaction.work.trading.missing_station");
        }
        if (villager.distanceToSqr(station.getX() + 0.5D, station.getY(), station.getZ() + 0.5D) <= 9.0D) {
            VillagerTaskNavigationUtil.stopHiredNavigation(villager);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.WORKING, station);
            return WorkResult.idle("interaction.work.trading.ready");
        }
        if (villager.getNavigation().isDone()) {
            var path = villager.getNavigation().createPath(station, 1);
            if (!VillagerTaskNavigationUtil.moveToHiredPath(villager, path, station, 0.6D, 1)) {
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, station);
                return WorkResult.idle("interaction.work.trading.unreachable");
            }
        }
        HiredWorkerBrain.setState(context, HiredWorkerTaskState.MOVING_TO_TARGET, station);
        return WorkResult.progressed("interaction.work.trading.moving");
    }
}
