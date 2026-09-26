package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.client.settlement.SettlementViewChunks;
import java.util.function.Consumer;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientChunkCache.class)
public abstract class SettlementClientChunkMixin {
    @Shadow @Final private ClientLevel level;

    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/LevelChunk;",
            at = @At("RETURN"), cancellable = true)
    private void villagerretaliation$findSettlementChunk(int x, int z, ChunkStatus status, boolean required,
                                                       CallbackInfoReturnable<LevelChunk> callback) {
        if (callback.getReturnValue() == null || callback.getReturnValue() instanceof EmptyLevelChunk) {
            LevelChunk remote = SettlementViewChunks.get(this.level, x, z);
            if (remote != null) {
                callback.setReturnValue(remote);
            }
        }
    }

    @Inject(method = "replaceWithPacketData", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$readSettlementChunk(int x, int z, FriendlyByteBuf buffer, CompoundTag heightmaps,
                                                       Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> blockEntities,
                                                       CallbackInfoReturnable<LevelChunk> callback) {
        LevelChunk remote = SettlementViewChunks.replace(this.level, x, z, buffer, heightmaps, blockEntities);
        if (remote != null) {
            callback.setReturnValue(remote);
        }
    }

    @Inject(method = "replaceWithPacketData", at = @At("RETURN"))
    private void villagerretaliation$releaseDuplicateChunk(int x, int z, FriendlyByteBuf buffer, CompoundTag heightmaps,
                                                         Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> blockEntities,
                                                         CallbackInfoReturnable<LevelChunk> callback) {
        SettlementViewChunks.replacedNormally(this.level, callback.getReturnValue());
    }

    @Inject(method = "replaceBiomes", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$readSettlementBiomes(int x, int z, FriendlyByteBuf buffer, CallbackInfo callback) {
        LevelChunk remote = SettlementViewChunks.get(this.level, x, z);
        if (remote != null && this.level.getChunkSource().getChunk(x, z, false) == remote) {
            remote.replaceBiomes(buffer);
            callback.cancel();
        }
    }
}
