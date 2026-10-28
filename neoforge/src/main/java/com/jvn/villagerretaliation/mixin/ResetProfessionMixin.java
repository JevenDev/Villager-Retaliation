package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.settlement.SettlementWorkforceService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.ResetProfession;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ResetProfession.class)
public abstract class ResetProfessionMixin {
    @Inject(method = "lambda$create$0", at = @At("HEAD"), cancellable = true)
    private static void villagerretaliation$retainWorkProfession(
            ServerLevel level, Villager villager, long gameTime, CallbackInfoReturnable<Boolean> cir) {
        if (HiredVillagerContractService.hasActiveOrPendingContract(villager)
                || SettlementWorkforceService.duty(villager).isPresent()) cir.setReturnValue(false);
    }
}
