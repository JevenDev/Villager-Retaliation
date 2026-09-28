package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.client.settlement.SettlementViewClient;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class SettlementViewProjectionMixin {
    @Inject(method = "getProjectionMatrix", at = @At("RETURN"))
    private void villagerretaliation$centerViewInViewport(double fov, CallbackInfoReturnable<Matrix4f> callback) {
        float offset = SettlementViewClient.projectionOffset();
        if (offset != 0) {
            var matrix = callback.getReturnValue();
            matrix.m20(matrix.m20() + offset);
        }
    }
}
