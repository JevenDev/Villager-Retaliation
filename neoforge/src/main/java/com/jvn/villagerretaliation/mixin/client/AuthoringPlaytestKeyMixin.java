package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.authoring.client.EditorPlaytestViewport;
import net.minecraft.client.KeyboardHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = KeyboardHandler.class, priority = 1100)
public abstract class AuthoringPlaytestKeyMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$releasePlaytestInput(long window, int key, int scan, int action, int modifiers, CallbackInfo callback) {
        if (key == GLFW.GLFW_KEY_F9 && EditorPlaytestViewport.active()) {
            if (action == GLFW.GLFW_PRESS) EditorPlaytestViewport.releaseInput();
            callback.cancel();
        }
    }
}
