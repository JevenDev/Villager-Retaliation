package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.duel.DuelService;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDuelDropMixin {
    @Inject(method = "drop(Z)Z", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$blockDuelDrop(
            boolean dropStack,
            CallbackInfoReturnable<Boolean> callbackInfo) {
        if (DuelService.isParticipant((ServerPlayer) (Object) this)) {
            callbackInfo.setReturnValue(false);
        }
    }
}
