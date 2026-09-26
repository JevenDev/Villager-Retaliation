package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.settlement.SettlementBrewingStandState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingStandSettlementMixin {
    @Inject(method = "serverTick", at = @At("RETURN"))
    private static void villagerretaliation$recordSettlementBrewingTick(
            Level level, BlockPos position, BlockState state, BrewingStandBlockEntity stand, CallbackInfo callback) {
        SettlementBrewingStandState.onVanillaTick(level, stand);
    }
}
