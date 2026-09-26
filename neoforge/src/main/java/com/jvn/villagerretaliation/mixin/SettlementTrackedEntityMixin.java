package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.settlement.SettlementViewService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class SettlementTrackedEntityMixin {
    @Shadow @Final private Entity entity;

    @ModifyVariable(method = "updatePlayer", at = @At("STORE"), ordinal = 0)
    private boolean villagerretaliation$includeSettlementView(boolean tracked, ServerPlayer player) {
        return tracked || SettlementViewService.tracks(player, this.entity) && this.entity.broadcastToPlayer(player);
    }
}
