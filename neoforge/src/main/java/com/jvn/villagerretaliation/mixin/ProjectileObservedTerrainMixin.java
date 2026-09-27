package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.settlement.SettlementProjectileSimulation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Projectile.class)
public abstract class ProjectileObservedTerrainMixin {
    @Redirect(method = {"onHit", "onHitBlock"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState villagerretaliation$observedBlock(Level level, BlockPos position) {
        return SettlementProjectileSimulation.blockState((Projectile) (Object) this, level, position);
    }
}
