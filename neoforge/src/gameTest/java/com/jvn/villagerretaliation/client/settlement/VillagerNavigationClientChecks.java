package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.event.VillagerRetaliationEvents;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.pathfinder.PathType;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

final class VillagerNavigationClientChecks {
    private VillagerNavigationClientChecks() { }

    static CompletableFuture<Void> verify() {
        var minecraft = Minecraft.getInstance();
        var server = minecraft.getSingleplayerServer();
        return server.submit(() -> {
            var villager = EntityType.VILLAGER.create(server.overworld());
            villager.setPathfindingMalus(PathType.WATER, 3.0F);
            villager.setPathfindingMalus(PathType.FENCE, 9.0F);
            ((GroundPathNavigation) villager.getNavigation()).setCanFloat(false);
            VillagerTaskNavigationUtil.enableHiredWaterTraversal(villager);
            return villager;
        }).thenCompose(villager -> minecraft.submit(() -> {
            var tracked = EntityType.VILLAGER.create(minecraft.level);
            tracked.setUUID(villager.getUUID());
            VillagerRetaliationEvents.onEntityLeaveLevel(new EntityLeaveLevelEvent(tracked, minecraft.level));
            return villager;
        })).thenAcceptAsync(villager -> {
            try {
                VillagerTaskNavigationUtil.restoreHiredWaterTraversal(villager);
                require(villager.getPathfindingMalus(PathType.WATER) == 3.0F,
                        "client unload preserves the server's original water path cost");
                require(villager.getPathfindingMalus(PathType.FENCE) == 9.0F,
                        "client unload preserves the server's original obstacle path cost");
                require(!((GroundPathNavigation) villager.getNavigation()).canFloat(),
                        "client unload preserves the server's original flotation setting");
            } finally {
                VillagerTaskNavigationUtil.clearRuntimeState(villager);
            }
        }, server);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
