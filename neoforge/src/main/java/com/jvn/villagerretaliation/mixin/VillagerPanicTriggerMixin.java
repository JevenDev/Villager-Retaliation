package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerRules;
import com.jvn.villagerretaliation.villager.VillagerBehaviorSuppressionPolicy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.VillagerPanicTrigger;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VillagerPanicTrigger.class)
public abstract class VillagerPanicTriggerMixin {
    @Inject(method = "start(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;J)V", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$suppressVanillaPanicStart(
            ServerLevel level,
            Villager villager,
            long gameTime,
            CallbackInfo ci) {
        if (!shouldSuppress(villager)) {
            return;
        }

        VillagerRetaliationVillagerBrainUtil.suppressVanillaFleeState(level, villager);
        ci.cancel();
    }

    @Inject(method = "canStillUse(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;J)Z", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$suppressVanillaPanicContinue(
            ServerLevel level,
            Villager villager,
            long gameTime,
            CallbackInfoReturnable<Boolean> cir) {
        if (!shouldSuppress(villager)) {
            return;
        }

        VillagerRetaliationVillagerBrainUtil.suppressVanillaFleeState(level, villager);
        cir.setReturnValue(false);
    }

    @Inject(method = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;J)V", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$suppressVanillaPanicTick(
            ServerLevel level,
            Villager villager,
            long gameTime,
            CallbackInfo ci) {
        if (shouldSuppress(villager)) {
            ci.cancel();
        }
    }

    private static boolean shouldSuppress(Villager villager) {
        return VillagerBehaviorSuppressionPolicy.suppresses(
                        villager, VillagerBehaviorSuppressionPolicy.Behavior.VANILLA_PANIC)
                || VillagerRetaliationVillagerRules.shouldSuppressVanillaFleeBehavior(villager);
    }
}
