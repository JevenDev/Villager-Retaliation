package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.settlement.ChunkGenerationCompletion;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.level.GenerationChunkHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GenerationChunkHolder.class)
public abstract class ChunkGenerationCompletionMixin implements ChunkGenerationCompletion {
    @Unique
    private CompletableFuture<Void> villagerretaliation$generationCompletion = CompletableFuture.completedFuture(null);

    @Redirect(method = "increaseGenerationRefCount", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/atomic/AtomicInteger;incrementAndGet()I"))
    private int villagerretaliation$claimGeneration(AtomicInteger references) {
        synchronized (this) {
            if (references.get() == 0) {
                villagerretaliation$generationCompletion = new CompletableFuture<>();
            }
            return references.incrementAndGet();
        }
    }

    @Redirect(method = "decreaseGenerationRefCount", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/atomic/AtomicInteger;decrementAndGet()I"))
    private int villagerretaliation$releaseGeneration(AtomicInteger references) {
        CompletableFuture<Void> completion;
        int remaining;
        synchronized (this) {
            remaining = references.decrementAndGet();
            completion = remaining == 0 ? villagerretaliation$generationCompletion : null;
        }
        if (completion != null) {
            completion.complete(null);
        }
        return remaining;
    }

    @Override
    public synchronized CompletableFuture<Void> villagerretaliation$generationCompletion() {
        return villagerretaliation$generationCompletion;
    }
}
