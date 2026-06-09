package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.villager.VillagerContainerClimbGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WalkNodeEvaluator.class)
public abstract class WalkNodeEvaluatorMixin {
    @Inject(method = "getPathTypeOfMob", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$blockVillagerContainerTopPathNodes(
            PathfindingContext context,
            int x,
            int y,
            int z,
            Mob mob,
            CallbackInfoReturnable<PathType> cir) {
        if (mob instanceof Villager
                && VillagerContainerClimbGuard.isForbiddenStandingFloor(context.level(), new BlockPos(x, y - 1, z))) {
            cir.setReturnValue(PathType.BLOCKED);
        }
    }
}
