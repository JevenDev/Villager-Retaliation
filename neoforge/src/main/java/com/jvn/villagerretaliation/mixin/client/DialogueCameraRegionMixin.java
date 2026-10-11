package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.client.interaction.DialogueCameraMaskView;
import com.jvn.villagerretaliation.client.interaction.DialogueCameraOcclusion;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderChunkRegion.class)
public abstract class DialogueCameraRegionMixin implements DialogueCameraMaskView {
    @Shadow @Final protected Level level;
    @Unique private DialogueCameraOcclusion.Snapshot villagerretaliation$mask;

    @Inject(method = "<init>(Lnet/minecraft/world/level/Level;II[Lnet/minecraft/client/renderer/chunk/RenderChunk;Lit/unimi/dsi/fastutil/longs/Long2ObjectFunction;)V", at = @At("RETURN"))
    private void villagerretaliation$captureMask(CallbackInfo callback) {
        villagerretaliation$mask = DialogueCameraOcclusion.snapshot(level);
    }

    @Override
    public DialogueCameraOcclusion.Snapshot villagerretaliation$cameraMask() { return villagerretaliation$mask; }

    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$hideBlock(BlockPos position, CallbackInfoReturnable<BlockState> callback) {
        if (villagerretaliation$mask != null && villagerretaliation$mask.hides(position)) callback.setReturnValue(Blocks.AIR.defaultBlockState());
    }

    @Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$hideFluid(BlockPos position, CallbackInfoReturnable<FluidState> callback) {
        if (villagerretaliation$mask != null && villagerretaliation$mask.hides(position)) callback.setReturnValue(Fluids.EMPTY.defaultFluidState());
    }
}
