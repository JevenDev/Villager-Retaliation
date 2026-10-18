package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.authoring.client.EditorPlaytestViewport;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MouseHandler.class)
public abstract class AuthoringPlaytestMouseMixin {
    @Redirect(method = {"onPress", "onScroll", "onMove"}, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;getGuiScaledWidth()I"))
    private int villagerretaliation$getGuiScaledWidth(Window window) {
        return EditorPlaytestViewport.active() && EditorPlaytestViewport.layout() != null
                ? EditorPlaytestViewport.layout().guiWidth() : window.getGuiScaledWidth();
    }

    @Redirect(method = {"onPress", "onScroll", "onMove"}, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;getGuiScaledHeight()I"))
    private int villagerretaliation$getGuiScaledHeight(Window window) {
        return EditorPlaytestViewport.active() && EditorPlaytestViewport.layout() != null
                ? EditorPlaytestViewport.layout().guiHeight() : window.getGuiScaledHeight();
    }

    @Redirect(method = {"onPress", "onScroll", "onMove"}, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;getScreenWidth()I"))
    private int villagerretaliation$getScreenWidth(Window window) {
        return EditorPlaytestViewport.active() && EditorPlaytestViewport.layout() != null
                ? EditorPlaytestViewport.layout().screenWidth() : window.getScreenWidth();
    }

    @Redirect(method = {"onPress", "onScroll", "onMove"}, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;getScreenHeight()I"))
    private int villagerretaliation$getScreenHeight(Window window) {
        return EditorPlaytestViewport.active() && EditorPlaytestViewport.layout() != null
                ? EditorPlaytestViewport.layout().screenHeight() : window.getScreenHeight();
    }

}
