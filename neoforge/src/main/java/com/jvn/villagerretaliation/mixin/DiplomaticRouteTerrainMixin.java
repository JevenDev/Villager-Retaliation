package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.diplomacy.route.DiplomaticRouteService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class DiplomaticRouteTerrainMixin {
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void villagerretaliation$invalidateRoute(BlockPos position, BlockState state, boolean moving,
                                                    CallbackInfoReturnable<BlockState> callback) {
        if (callback.getReturnValue() != null && ((LevelChunk) (Object) this).getLevel() instanceof ServerLevel level)
            DiplomaticRouteService.terrainChanged(level, position);
    }
}
