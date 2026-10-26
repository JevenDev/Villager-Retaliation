package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.settlement.SettlementCourierInterference;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VehicleEntity.class)
public abstract class DiplomaticBoatDamageMixin {
    @Inject(method = "hurt", at = @At("RETURN"))
    private void villagerretaliation$recordBoatInterference(DamageSource source, float amount, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValue() && amount > 0 && (Object) this instanceof Boat boat && !boat.level().isClientSide)
            SettlementCourierInterference.boatHurt(boat, source);
    }
}
