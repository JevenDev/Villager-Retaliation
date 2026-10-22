package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.settlement.SettlementScheduleService;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerBrainTickMixin {
    @Inject(
            method = "registerBrainGoals",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/Brain;updateActivityFromSchedule(JJ)V"
            )
    )
    private void villagerretaliation$applyCivicScheduleBeforeActivitySelection(Brain<Villager> brain, CallbackInfo ci) {
        SettlementScheduleService.reconcile((Villager) (Object) this, brain);
    }

}
