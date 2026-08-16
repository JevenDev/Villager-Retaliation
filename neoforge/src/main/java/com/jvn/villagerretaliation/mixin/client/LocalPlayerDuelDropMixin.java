package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.client.duel.DuelInventoryClientState;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerDuelDropMixin {
    @Inject(method = "drop(Z)Z", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$blockDuelDrop(
            boolean fullStack,
            CallbackInfoReturnable<Boolean> callbackInfo) {
        if (DuelInventoryClientState.active()) {
            callbackInfo.setReturnValue(false);
        }
    }
}
