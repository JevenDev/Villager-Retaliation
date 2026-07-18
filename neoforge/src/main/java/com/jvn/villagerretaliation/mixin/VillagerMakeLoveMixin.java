package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.social.VillagerBreedingPolicy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.VillagerMakeLove;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VillagerMakeLove.class)
public abstract class VillagerMakeLoveMixin {
    @Inject(
            method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void villagerretaliation$blockIneligibleStart(
            ServerLevel level,
            Villager villager,
            CallbackInfoReturnable<Boolean> callback) {
        if (!pairAllowed(level, villager)) {
            VillagerBreedingPolicy.cancelActiveAttempt(level, villager);
            callback.setReturnValue(false);
        }
    }

    @Inject(
            method = "canStillUse(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;J)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void villagerretaliation$interruptIneligibleCourtship(
            ServerLevel level,
            Villager villager,
            long gameTime,
            CallbackInfoReturnable<Boolean> callback) {
        if (!pairAllowed(level, villager)) {
            VillagerBreedingPolicy.cancelActiveAttempt(level, villager);
            callback.setReturnValue(false);
        }
    }

    @Inject(
            method = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;J)V",
            at = @At("HEAD"),
            cancellable = true)
    private void villagerretaliation$guardFinalCourtshipTick(
            ServerLevel level,
            Villager villager,
            long gameTime,
            CallbackInfo callback) {
        if (!pairAllowed(level, villager)) {
            VillagerBreedingPolicy.cancelActiveAttempt(level, villager);
            callback.cancel();
        }
    }

    private static boolean pairAllowed(ServerLevel level, Villager villager) {
        return villager.getBrain().getMemory(MemoryModuleType.BREED_TARGET)
                .filter(Villager.class::isInstance)
                .map(Villager.class::cast)
                .map(partner -> VillagerBreedingPolicy.evaluatePair(level, villager, partner).allowed())
                .orElse(false);
    }
}
