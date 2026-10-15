package com.jvn.villagerretaliation.interaction.work.mining;

import com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService;
import com.jvn.villagerretaliation.interaction.HiredMiningMode;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

public final class MiningTaskRules {
    private MiningTaskRules() { }

    public static boolean permits(BlockGetter level, HiredWorkContext context, BlockPos position) {
        if (!com.jvn.villagerretaliation.quest.tutorial.TutorialWorkService.permitsTarget(level, context.state(), "mine", position)) return false;
        var queue = HiredTemporaryWorkService.queue(context.state());
        if (queue == null) return true;
        var task = queue.current();
        return task.includes(position) && (HiredMiningMode.fromState(context.state()).excavatesArea() || task.matches(level.getBlockState(position)));
    }
}
