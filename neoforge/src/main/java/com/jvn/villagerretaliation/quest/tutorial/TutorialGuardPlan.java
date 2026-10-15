package com.jvn.villagerretaliation.quest.tutorial;

import com.jvn.villagerretaliation.interaction.*;
import com.jvn.villagerretaliation.interaction.work.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.npc.Villager;

public record TutorialGuardPlan(TutorialRun.Scope scope, String blocker) {
    public static boolean equipped(Villager worker) {
        var tool = worker.getMainHandItem();
        return !tool.isEmpty() && tool.is(ItemTags.SWORDS) && (!tool.isDamageableItem() || tool.getDamageValue() < tool.getMaxDamage());
    }
    public static TutorialGuardPlan inspect(ServerLevel level, Villager worker) {
        var session = HiredWorkSession.create(level, worker, HiredVillagerRole.COMBAT);
        var route = session.context().route();
        if (!session.area().explicitlyAssigned()) return blocked("Apply the safe post area with Edit Job Site first.");
        if (HiredCombatMode.fromState(session.state()) != HiredCombatMode.GUARD) return blocked("Choose Cycle Combat Mode until Guard is reported.");
        if (!equipped(worker)) return blocked("In Job > Job Inventory, equip a usable melee sword. A sword sitting in Supplies is not equipped.");
        if (route == null || !route.loop() || route.nodes().size() != 3 || !route.branches().isEmpty()
                || !session.area().contains(route.first())) return blocked("Apply a loop with the return post first and two distinct safe checkpoints, then close the loop on the post.");
        for (int i = 0; i < 3; i++) {
            var node = route.nodes().get(i);
            if (!level.hasChunkAt(node) || !HiredMoveToBlockFaceJob.isValidApproachPosition(level, node)) return blocked("A route node needs loaded, clear standing room.");
            for (int j = 0; j < i; j++) if (node.distSqr(route.nodes().get(j)) <= 16) return blocked("Keep each checkpoint more than four blocks from the others so each arrival is distinct.");
            var path = worker.getNavigation().createPath(node, 0);
            if (path == null || !path.canReach()) return blocked("Clear a walkable path to each checkpoint before authorizing the watch.");
        }
        var targets = new LinkedHashSet<String>();
        for (int i = 0; i < 4; i++) targets.add(i + ":" + TutorialWorkService.position(level, route.nodes().get(i % 3)));
        return new TutorialGuardPlan(new TutorialRun.Scope(Map.of("patrol", targets), Map.of("patrol", 4), Map.of("patrol", 4), ""), "");
    }
    public static boolean matches(ServerLevel level, HiredWorkContext context, TutorialRun run) {
        var route = context.route();
        if (route == null || !route.loop() || route.nodes().size() != 3 || !route.branches().isEmpty()) return false;
        for (int i = 0; i < 4; i++) if (!run.scope().targets().get("patrol").contains(i + ":" + TutorialWorkService.position(level, route.nodes().get(i % 3)))) return false;
        return true;
    }
    private static TutorialGuardPlan blocked(String reason) { return new TutorialGuardPlan(null, reason); }
}
