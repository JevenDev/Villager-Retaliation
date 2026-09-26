package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.settlement.SettlementChunkReceipts;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class SettlementTerrainTickMixin {
    @Inject(method = "tickChunk", at = @At("RETURN"))
    private void villagerretaliation$recordTerrainTick(LevelChunk chunk, int randomTickSpeed, CallbackInfo callback) {
        SettlementChunkReceipts.onVanillaTick(chunk, ((ServerLevel) (Object) this).getGameTime());
    }
}
