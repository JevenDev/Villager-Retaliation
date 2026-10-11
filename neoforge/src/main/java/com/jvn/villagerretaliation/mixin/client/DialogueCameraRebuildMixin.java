package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.client.interaction.DialogueCameraOcclusion;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.renderer.chunk.SectionRenderDispatcher$RenderSection$RebuildTask")
public abstract class DialogueCameraRebuildMixin {
    @Inject(method = "doTask", at = @At("HEAD"))
    private void villagerretaliation$beginMask(CallbackInfoReturnable<CompletableFuture<?>> callback) {
        DialogueCameraOcclusion.beginBuild();
    }

    @Inject(method = "doTask", at = @At("RETURN"))
    private void villagerretaliation$finishMask(CallbackInfoReturnable<CompletableFuture<?>> callback) {
        DialogueCameraOcclusion.finishBuild(callback.getReturnValue());
    }
}
