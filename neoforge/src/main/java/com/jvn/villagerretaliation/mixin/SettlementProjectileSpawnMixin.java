package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.settlement.SettlementProjectileCapture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PersistentEntitySectionManager.class)
public abstract class SettlementProjectileSpawnMixin {
    @Inject(method = "addEntityWithoutEvent", at = @At("HEAD"), cancellable = true, remap = false)
    private void villagerretaliation$captureProjectile(EntityAccess entity, boolean loading, CallbackInfoReturnable<Boolean> callback) {
        if (!loading && entity instanceof Entity body && SettlementProjectileCapture.capture(body)) callback.setReturnValue(true);
    }
}
