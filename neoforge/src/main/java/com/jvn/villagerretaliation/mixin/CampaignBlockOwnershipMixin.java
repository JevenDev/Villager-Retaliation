package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.campaign.CampaignBlockOwnership;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class CampaignBlockOwnershipMixin {
    @Inject(method = "setBlockState", at = @At("HEAD"))
    private void villagerretaliation$recordExternalBlockChange(BlockPos position, BlockState state, boolean moving,
            CallbackInfoReturnable<BlockState> callback) {
        CampaignBlockOwnership.changed((LevelChunk) (Object) this, position, state);
    }
}
