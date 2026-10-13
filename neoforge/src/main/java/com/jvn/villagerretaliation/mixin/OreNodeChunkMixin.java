package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.interaction.work.mining.OreNodes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class OreNodeChunkMixin {
    @Inject(method = "<init>(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ProtoChunk;Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;)V", at = @At("RETURN"))
    private void villagerretaliation$recordGeneratedOres(ServerLevel level, ProtoChunk source, LevelChunk.PostLoadProcessor postLoad, CallbackInfo callback) {
        OreNodes.generated((LevelChunk) (Object) this);
    }

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void villagerretaliation$invalidateOre(BlockPos position, BlockState state, boolean moving, CallbackInfoReturnable<BlockState> callback) {
        OreNodes.changed((LevelChunk) (Object) this, position, callback.getReturnValue(), state);
    }
}
