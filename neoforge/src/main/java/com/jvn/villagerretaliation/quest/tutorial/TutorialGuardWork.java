package com.jvn.villagerretaliation.quest.tutorial;

import com.jvn.villagerretaliation.interaction.*;
import com.jvn.villagerretaliation.interaction.work.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/** A defensive circuit uses the existing navigator and commits physical arrivals, in order. */
public final class TutorialGuardWork {
    private TutorialGuardWork() { }
    public static WorkResult tick(ServerLevel level, Villager worker, HiredWorkContext context) {
        var run = TutorialWorkService.run(worker).orElse(null);
        if (run == null || !TutorialWorkService.canTick(worker, HiredVillagerRole.COMBAT)
                || !TutorialGuardPlan.equipped(worker) || HiredCombatMode.fromState(context.state()) != HiredCombatMode.GUARD
                || !TutorialGuardPlan.matches(level, context, run)) return WorkResult.idle("interaction.work.tutorial.paused");
        if (worker.getTarget() != null || worker.getLastHurtByMob() != null) return WorkResult.idle("interaction.work.tutorial.paused");
        int index = run.committedUnits("patrol");
        if (index >= 4) return WorkResult.idle("interaction.work.tutorial.paused");
        var state = context.state();
        var current = worker.position();
        if (state.contains("TutorialGuardX")) {
            double dx = current.x - state.getDouble("TutorialGuardX"), dy = current.y - state.getDouble("TutorialGuardY"), dz = current.z - state.getDouble("TutorialGuardZ");
            if (dx * dx + dy * dy + dz * dz > 16) {
                run.pause(); TutorialSavedData.get(level).setDirty();
                state.remove("TutorialGuardX");
                return WorkResult.idle("interaction.work.tutorial.paused");
            }
        }
        state.putDouble("TutorialGuardX", current.x); state.putDouble("TutorialGuardY", current.y); state.putDouble("TutorialGuardZ", current.z);
        BlockPos target = context.route().nodes().get(index % 3);
        var movement = HiredRouteNavigator.moveToRouteNode(level, worker, target, 0.45D, 1.0D);
        HiredWorkerBrain.setState(context, movement == HiredRouteNavigator.NodeMovement.FAILED ? HiredWorkerTaskState.FAILED_COOLDOWN : HiredWorkerTaskState.MOVING_TO_TARGET, target);
        if (movement == HiredRouteNavigator.NodeMovement.ARRIVED) {
            var permit = TutorialWorkService.reserve(worker, "patrol", index + ":" + TutorialWorkService.position(level, target), 1);
            if (permit != null) TutorialWorkService.committed(worker, permit, "", 0, "");
        }
        return WorkResult.idle(movement == HiredRouteNavigator.NodeMovement.FAILED ? "interaction.work.tutorial.paused" : "interaction.work.combat.guarding");
    }
}
