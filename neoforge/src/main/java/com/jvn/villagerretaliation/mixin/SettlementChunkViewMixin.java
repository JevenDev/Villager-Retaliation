package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.settlement.SettlementViewService;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkMap.class)
public abstract class SettlementChunkViewMixin {
    @Shadow @Final private ServerLevel level;

    @Inject(method = "getPlayers(Lnet/minecraft/world/level/ChunkPos;Z)Ljava/util/List;", at = @At("RETURN"), cancellable = true)
    private void villagerretaliation$includeSettlementViewers(ChunkPos position, boolean boundaryOnly,
                                                             CallbackInfoReturnable<List<ServerPlayer>> callback) {
        var viewers = SettlementViewService.viewers(this.level, position);
        if (viewers.isEmpty()) {
            return;
        }
        var players = new ArrayList<>(callback.getReturnValue());
        for (var player : viewers) {
            if (!players.contains(player)) {
                players.add(player);
            }
        }
        callback.setReturnValue(List.copyOf(players));
    }
}
