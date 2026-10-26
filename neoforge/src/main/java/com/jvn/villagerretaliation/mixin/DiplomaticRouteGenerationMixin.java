package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.diplomacy.route.RouteGenerationMetrics;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkStep.class)
public abstract class DiplomaticRouteGenerationMixin {
    @Inject(method = "apply", at = @At("HEAD"))
    private void villagerretaliation$recordRouteGeneration(WorldGenContext context, StaticCache2D<GenerationChunkHolder> cache,
                                                          ChunkAccess chunk, CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callback) {
        RouteGenerationMetrics.observe(context.level(), chunk, ((ChunkStep) (Object) this).targetStatus());
    }
}
