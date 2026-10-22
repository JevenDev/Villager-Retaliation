package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.villager.VillagerBehaviorSuppressionPolicy;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Brain.class)
public abstract class BrainVillagerBehaviorMixin {
    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/Brain;startEachNonRunningBehavior(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V"),
            cancellable = true)
    private void villagerretaliation$preserveSensingBeforeSuppressingBehavior(
            ServerLevel level, LivingEntity entity, CallbackInfo callback) {
        if (entity instanceof Villager villager
                && (VillagerBehaviorSuppressionPolicy.shouldSuppressVanillaBrainTick(level, villager)
                || VillagerRetaliationVillagerBrainUtil.shouldSuppressVanillaBrainTickForCombat(villager))) {
            callback.cancel();
        }
    }
}
