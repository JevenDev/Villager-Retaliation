package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.settlement.SettlementHuntingTargetService;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntitySettlementCombatMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$reconcileBeforeNativeDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> callback) {
        if (!SettlementHuntingTargetService.beforeNativeDamage((LivingEntity) (Object) this)) callback.setReturnValue(false);
    }

    @Inject(method = "hurt", at = @At("RETURN"))
    private void villagerretaliation$observeNativeDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> callback) {
        SettlementHuntingTargetService.afterNativeDamage((LivingEntity) (Object) this);
    }
}
