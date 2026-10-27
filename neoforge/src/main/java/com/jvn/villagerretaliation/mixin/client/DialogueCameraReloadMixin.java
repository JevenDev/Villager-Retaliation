package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.client.interaction.DialogueCameraOcclusion;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class DialogueCameraReloadMixin {
    @Inject(method = "allChanged", at = @At("HEAD"))
    private void villagerretaliation$releaseMask(CallbackInfo callback) {
        DialogueCameraOcclusion.settlementMask(java.util.Set.of());
        DialogueCameraOcclusion.clear();
    }
}
