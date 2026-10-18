package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.authoring.client.EditorPlaytestViewport;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Window.class)
public abstract class AuthoringPlaytestWindowMixin {
    @Inject(method = "getWidth", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$getWidth(CallbackInfoReturnable<Integer> callback) {
        if (EditorPlaytestViewport.rendering()) callback.setReturnValue(EditorPlaytestViewport.layout().width());
    }

    @Inject(method = "getHeight", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$getHeight(CallbackInfoReturnable<Integer> callback) {
        if (EditorPlaytestViewport.rendering()) callback.setReturnValue(EditorPlaytestViewport.layout().height());
    }

    @Inject(method = "getGuiScaledWidth", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$getGuiScaledWidth(CallbackInfoReturnable<Integer> callback) {
        if (EditorPlaytestViewport.rendering()) callback.setReturnValue(EditorPlaytestViewport.layout().guiWidth());
    }

    @Inject(method = "getGuiScaledHeight", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$getGuiScaledHeight(CallbackInfoReturnable<Integer> callback) {
        if (EditorPlaytestViewport.rendering()) callback.setReturnValue(EditorPlaytestViewport.layout().guiHeight());
    }

    @Inject(method = "getScreenWidth", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$getScreenWidth(CallbackInfoReturnable<Integer> callback) {
        if (EditorPlaytestViewport.rendering()) callback.setReturnValue(EditorPlaytestViewport.layout().screenWidth());
    }

    @Inject(method = "getScreenHeight", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$getScreenHeight(CallbackInfoReturnable<Integer> callback) {
        if (EditorPlaytestViewport.rendering()) callback.setReturnValue(EditorPlaytestViewport.layout().screenHeight());
    }

    @Inject(method = "getGuiScale", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$getGuiScale(CallbackInfoReturnable<Double> callback) {
        if (EditorPlaytestViewport.rendering()) callback.setReturnValue((double) EditorPlaytestViewport.layout().scale());
    }

}
