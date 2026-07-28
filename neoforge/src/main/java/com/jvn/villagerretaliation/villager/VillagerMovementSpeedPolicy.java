package com.jvn.villagerretaliation.villager;

import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.interaction.VillagerAssignmentStore;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;

/** Canonical movement speeds for every unmounted villager activity. */
public final class VillagerMovementSpeedPolicy {
    /** Vanilla villager task navigation speed. */
    public static final double WALK_SPEED_MODIFIER = 0.5D;
    /** Vindicator-equivalent run speed: 0.5 villager movement attribute * 0.7 = 0.35. */
    public static final double RUN_SPEED_MODIFIER = 0.7D;
    public static final double FOLLOW_RUN_DISTANCE = 8.0D;
    private static final double FOLLOW_RUN_DISTANCE_SQR = FOLLOW_RUN_DISTANCE * FOLLOW_RUN_DISTANCE;

    private VillagerMovementSpeedPolicy() {
    }

    public static double following(double distanceSqr) {
        return distanceSqr > FOLLOW_RUN_DISTANCE_SQR ? RUN_SPEED_MODIFIER : WALK_SPEED_MODIFIER;
    }

    /** Enforces the final intent after vanilla AI and all mod movement owners run for the tick. */
    public static void enforce(ServerLevel level, Villager villager) {
        double speed = intendedSpeed(level, villager);
        if (!villager.getNavigation().isDone()) {
            villager.getNavigation().setSpeedModifier(speed);
        }
        Brain<Villager> brain = villager.getBrain();
        brain.getMemory(MemoryModuleType.WALK_TARGET).ifPresent(walkTarget -> {
            float modifier = (float) speed;
            if (Float.compare(walkTarget.getSpeedModifier(), modifier) != 0) {
                brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(
                        walkTarget.getTarget(), modifier, walkTarget.getCloseEnoughDist()));
            }
        });
    }

    private static double intendedSpeed(ServerLevel level, Villager villager) {
        if (VillagerRetaliationHandler.hasActiveRetaliationTarget(villager)
                || VillagerRetaliationVillagerBrainUtil.hasVanillaFleeState(level, villager.getBrain())) {
            return RUN_SPEED_MODIFIER;
        }
        if (!VillagerAssignmentStore.isFollowing(villager)) {
            return WALK_SPEED_MODIFIER;
        }
        UUID ownerId = VillagerAssignmentStore.commandOwner(villager).orElse(null);
        ServerPlayer owner = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
        return owner != null && owner.level() == level
                ? following(villager.distanceToSqr(owner))
                : WALK_SPEED_MODIFIER;
    }
}
