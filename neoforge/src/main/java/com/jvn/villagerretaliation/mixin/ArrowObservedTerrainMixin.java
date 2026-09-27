package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.settlement.SettlementProjectileSimulation;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractArrow.class)
public abstract class ArrowObservedTerrainMixin {
    @Shadow protected int inGroundTime;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$observedFlight(CallbackInfo callback) {
        if (SettlementProjectileSimulation.tickObserved((AbstractArrow) (Object) this)) callback.cancel();
    }

    @Redirect(method = "onHitBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState villagerretaliation$observedBlock(Level level, BlockPos position) {
        return SettlementProjectileSimulation.blockState((AbstractArrow) (Object) this, level, position);
    }

    @Redirect(method = "hitBlockEnchantmentEffects", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState villagerretaliation$observedEnchantmentBlock(net.minecraft.server.level.ServerLevel level, BlockPos position) {
        return SettlementProjectileSimulation.blockState((AbstractArrow) (Object) this, level, position);
    }

    @Inject(method = "onHitEntity", at = @At("RETURN"))
    private void villagerretaliation$rememberPiercedTarget(net.minecraft.world.phys.EntityHitResult hit, CallbackInfo callback) {
        var arrow = (AbstractArrow) (Object) this;
        var ignored = ((AbstractArrowAccessor) arrow).villagerretaliation$piercingIgnoreEntityIds();
        if (ignored == null || !ignored.contains(hit.getEntity().getId())
                || SettlementProjectileSimulation.pierced(arrow).contains(hit.getEntity().getUUID())) return;
        var ids = arrow.getPersistentData().getList("VillagerRetaliationPiercedTargets", net.minecraft.nbt.Tag.TAG_INT_ARRAY);
        ids.add(net.minecraft.nbt.NbtUtils.createUUID(hit.getEntity().getUUID()));
        arrow.getPersistentData().put("VillagerRetaliationPiercedTargets", ids);
    }

    @Inject(method = "canHitEntity", at = @At("RETURN"), cancellable = true)
    private void villagerretaliation$excludeSavedPiercedTarget(net.minecraft.world.entity.Entity target,
                                                            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ() && SettlementProjectileSimulation.pierced((AbstractArrow) (Object) this).contains(target.getUUID())) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "onHitBlock", at = @At("RETURN"))
    private void villagerretaliation$clearPiercedTargets(net.minecraft.world.phys.BlockHitResult hit, CallbackInfo callback) {
        ((AbstractArrow) (Object) this).getPersistentData().remove("VillagerRetaliationPiercedTargets");
    }

    @Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
    private void villagerretaliation$saveGroundTime(CompoundTag tag, CallbackInfo callback) {
        tag.putInt("VillagerRetaliationGroundTime", this.inGroundTime);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
    private void villagerretaliation$loadGroundTime(CompoundTag tag, CallbackInfo callback) {
        this.inGroundTime = Math.max(0, tag.getInt("VillagerRetaliationGroundTime"));
        ((AbstractArrowAccessor) this).villagerretaliation$setPiercingIgnoreEntityIds(null);
    }
}
