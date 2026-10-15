package com.jvn.villagerretaliation.quest.tutorial;

import com.jvn.villagerretaliation.interaction.*;
import com.jvn.villagerretaliation.interaction.work.*;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/** The applied persistent site and the subsequent one-catch Task are separate prerequisites. */
public record TutorialFishingPlan(TutorialRun.Scope scope, String blocker) {
    public static TutorialFishingPlan inspect(ServerLevel level, Villager worker) {
        var session = HiredWorkSession.create(level, worker, HiredVillagerRole.FISHING);
        var state = session.state();
        var stored = HiredWorkArea.fromState(state, worker.blockPosition());
        var queue = HiredTemporaryWorkService.queue(state);
        if (!stored.explicitlyAssigned() || queue == null || queue.tasks().size() != 1
                || queue.current().kind() != HiredTemporaryTask.Kind.FISH || queue.current().remaining() != 1
                || !queue.current().min().equals(stored.min()) || !queue.current().max().equals(stored.max()))
            return new TutorialFishingPlan(null, "Apply the shore area first. That clears the draft. Draw a fresh matching Edit Job Site draft, open Draft > Task, choose Catch fish, set Quantity to 1 and Apply.");
        if (!session.context().useAssignedStorageForSupplies() || !session.context().autoDepositOutputs())
            return new TutorialFishingPlan(null, "Enable assigned supplies and automatic output delivery in Work Settings.");
        if (session.inventory().findTool(FishingWorker::isFishingRod).isEmpty()
                && AssignedStorageService.countItems(worker, FishingWorker::isFishingRod) == 0)
            return new TutorialFishingPlan(null, "Put a usable fishing rod in this worker's tool slot or accessible Supplies.");
        String output = TutorialPlanChecks.output(level, worker);
        if (output == null) return new TutorialFishingPlan(null, TutorialPlanChecks.STORAGE_HELP);
        return new TutorialFishingPlan(new TutorialRun.Scope(Map.of("catch", Set.of(queue.current().id().toString()),
                "deposit", Set.of(output),"area_min",Set.of(TutorialWorkService.position(level,stored.min())),"area_max",Set.of(TutorialWorkService.position(level,stored.max()))), Map.of("catch", 1, "deposit", 65536), Map.of("catch", 1), output, true), "");
    }
    public static boolean matches(Villager worker, TutorialRun run) {
        var queue = HiredTemporaryWorkService.queue(HiredWorkStateStore.state(worker));
        return queue != null && queue.tasks().size() == 1 && queue.current().kind() == HiredTemporaryTask.Kind.FISH
                && queue.current().remaining() == 1
                && run.scope().targets().getOrDefault("catch", Set.of()).contains(queue.current().id().toString())
                && queue.current().min().equals(TutorialFarmingPlan.target(run,"area_min")) && queue.current().max().equals(TutorialFarmingPlan.target(run,"area_max"));
    }
}
