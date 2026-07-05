package com.jvn.villagerretaliation.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.GoToPotentialJobSite;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GoToPotentialJobSite.class)
public abstract class GoToPotentialJobSiteMixin {
    @Inject(method = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;J)V", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$keepActiveWalkTarget(
            ServerLevel level,
            Villager villager,
            long gameTime,
            CallbackInfo ci) {
        if (villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)
                && !villager.getNavigation().isDone()) {
            ci.cancel();
        }
    }
}
