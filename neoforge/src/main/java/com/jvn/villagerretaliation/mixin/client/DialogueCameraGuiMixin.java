package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.client.interaction.DialogueCameraController;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class DialogueCameraGuiMixin {
    @Inject(method = "renderSpyglassOverlay", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$hideScope(CallbackInfo callback) {
        if (DialogueCameraController.active()) callback.cancel();
    }
}
