package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.settlement.ChunkGenerationCompletion;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ChunkHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkHolder.class)
public abstract class ChunkGenerationSaveMixin {
    @Inject(method = "getSaveSyncFuture", at = @At("RETURN"), cancellable = true)
    private void villagerretaliation$awaitGeneration(CallbackInfoReturnable<CompletableFuture<?>> callback) {
        CompletableFuture<Void> generation = ((ChunkGenerationCompletion) this).villagerretaliation$generationCompletion();
        if (!generation.isDone()) {
            // unloading must await generation releases to avoid retrying forever during shutdown
            callback.setReturnValue(callback.getReturnValue().thenCombine(generation, (saved, generated) -> null));
        }
    }
}
