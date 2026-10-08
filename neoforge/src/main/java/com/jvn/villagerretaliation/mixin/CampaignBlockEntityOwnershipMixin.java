package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.campaign.CampaignBlockOwnership;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntity.class)
public abstract class CampaignBlockEntityOwnershipMixin {
    @Inject(method = "setChanged(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V", at = @At("HEAD"))
    private static void villagerretaliation$recordExternalBlockEntityChange(Level level, BlockPos position, BlockState state, CallbackInfo callback) {
        CampaignBlockOwnership.blockEntityChanged(level, position);
    }
}
