package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.combat.WanderingTraderRetaliationHandler;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.npc.WanderingTrader;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AvoidEntityGoal.class)
public abstract class AvoidEntityGoalMixin {
    @Shadow
    @Final
    protected PathfinderMob mob;

    @Inject(method = "canUse()Z", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$suppressTraderAvoidanceStart(CallbackInfoReturnable<Boolean> cir) {
        if (this.mob instanceof WanderingTrader trader
                && WanderingTraderRetaliationHandler.shouldSuppressVanillaAvoidance(trader)) {
            VillagerRetaliationVillagerBrainUtil.stopNavigationAndClearMovement(trader);
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "canContinueToUse()Z", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$suppressTraderAvoidanceContinue(CallbackInfoReturnable<Boolean> cir) {
        if (this.mob instanceof WanderingTrader trader
                && WanderingTraderRetaliationHandler.shouldSuppressVanillaAvoidance(trader)) {
            VillagerRetaliationVillagerBrainUtil.stopNavigationAndClearMovement(trader);
            cir.setReturnValue(false);
        }
    }
}
