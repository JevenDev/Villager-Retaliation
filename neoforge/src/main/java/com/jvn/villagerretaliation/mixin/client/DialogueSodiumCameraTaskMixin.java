package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.client.interaction.DialogueCameraOcclusion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask", remap = false)
public abstract class DialogueSodiumCameraTaskMixin {
    @Inject(method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;", at = @At("HEAD"), remap = false)
    private void villagerretaliation$beginMask(CallbackInfoReturnable<Object> callback) {
        DialogueCameraOcclusion.beginBuild();
    }

    @Inject(method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;", at = @At("RETURN"), remap = false)
    private void villagerretaliation$finishMask(CallbackInfoReturnable<Object> callback) {
        DialogueCameraOcclusion.finishSodiumBuild(callback.getReturnValue());
    }
}
