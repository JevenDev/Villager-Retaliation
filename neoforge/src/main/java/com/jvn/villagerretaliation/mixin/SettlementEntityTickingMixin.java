package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.settlement.SettlementSimulationHandoff;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.level.ServerLevel$EntityCallbacks")
public abstract class SettlementEntityTickingMixin {
    @Inject(method = "onTickingEnd(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"))
    private void villagerretaliation$captureFinalActiveState(Entity entity, CallbackInfo callback) {
        SettlementSimulationHandoff.stopTicking(entity);
    }
}
