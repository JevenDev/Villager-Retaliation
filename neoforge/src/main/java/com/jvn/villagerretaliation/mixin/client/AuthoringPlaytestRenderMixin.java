package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.authoring.client.EditorPlaytestViewport;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class AuthoringPlaytestRenderMixin {
    @Redirect(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V"))
    private void villagerretaliation$renderPlaytest(GameRenderer renderer, DeltaTracker delta, boolean renderLevel) {
        EditorPlaytestViewport.render(renderer, delta, renderLevel);
    }

    @Inject(method = "getMainRenderTarget", at = @At("RETURN"), cancellable = true)
    private void villagerretaliation$gameTarget(CallbackInfoReturnable<RenderTarget> callback) {
        if (EditorPlaytestViewport.rendering()) callback.setReturnValue(EditorPlaytestViewport.target(callback.getReturnValue()));
    }

    @Inject(method = "resizeDisplay", at = @At("RETURN"))
    private void villagerretaliation$invalidatePlaytest(CallbackInfo callback) {
        EditorPlaytestViewport.invalidate();
    }

    @Inject(method = "setScreen", at = @At("RETURN"))
    private void villagerretaliation$resizePlaytestScreen(CallbackInfo callback) {
        EditorPlaytestViewport.resizeScreen();
    }
}
