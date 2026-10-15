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
    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private static void villagerretaliation$tutorialBatch(Level level, BlockPos position, BlockState state, BrewingStandBlockEntity stand, CallbackInfo ci) {
        if(!com.jvn.villagerretaliation.quest.tutorial.TutorialBrewingService.beforeTick(level,stand))ci.cancel();
    }
    @Inject(method = "serverTick", at = @At("RETURN"))
    private static void villagerretaliation$recordSettlementBrewingTick(
            Level level, BlockPos position, BlockState state, BrewingStandBlockEntity stand, CallbackInfo callback) {
        com.jvn.villagerretaliation.quest.tutorial.TutorialBrewingService.afterTick(level,stand);
        SettlementBrewingStandState.onVanillaTick(level, stand);
    }
}
