package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.authoring.client.EditorPlaytestViewport;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Screen.class)
public abstract class AuthoringPlaytestScreenMixin {
    @Redirect(method = "renderBlurredBackground", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;bindWrite(Z)V"))
    private void villagerretaliation$restorePlaytestViewport(RenderTarget target, boolean setViewport) {
        target.bindWrite(setViewport || EditorPlaytestViewport.rendering());
    }
}
