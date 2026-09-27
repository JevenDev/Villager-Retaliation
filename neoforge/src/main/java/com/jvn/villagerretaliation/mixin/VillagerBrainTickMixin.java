package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.settlement.SettlementScheduleService;
import com.jvn.villagerretaliation.villager.VillagerBehaviorSuppressionPolicy;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
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

    @Redirect(
            method = "customServerAiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/Brain;tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V"
            )
    )
    private void villagerretaliation$suppressHiredIdleBrainTick(
            Brain<Villager> brain,
            ServerLevel level,
            LivingEntity entity) {
        if (entity instanceof Villager villager) {
            if (VillagerBehaviorSuppressionPolicy.shouldSuppressVanillaBrainTick(level, villager)
                    || VillagerRetaliationVillagerBrainUtil.shouldSuppressVanillaBrainTickForCombat(villager)) {
                return;
            }
            brain.tick(level, villager);
        }
    }
}
