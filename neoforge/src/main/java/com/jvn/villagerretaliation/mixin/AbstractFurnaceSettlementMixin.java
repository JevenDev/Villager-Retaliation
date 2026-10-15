package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.settlement.SettlementFurnaceState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceSettlementMixin {
    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private static void villagerretaliation$boundTutorialBatch(Level level, BlockPos position, BlockState state, AbstractFurnaceBlockEntity furnace, CallbackInfo ci) {
        if (!com.jvn.villagerretaliation.quest.tutorial.TutorialFurnaceService.beforeTick(level, furnace)) ci.cancel();
    }

    @Inject(method = "serverTick", at = @At("RETURN"))
    private static void villagerretaliation$recordSettlementFurnaceTick(
            Level level, BlockPos position, BlockState state, AbstractFurnaceBlockEntity furnace, CallbackInfo ci) {
        com.jvn.villagerretaliation.quest.tutorial.TutorialFurnaceService.afterTick(level, furnace);
        SettlementFurnaceState.onVanillaTick(level, furnace);
    }
}
