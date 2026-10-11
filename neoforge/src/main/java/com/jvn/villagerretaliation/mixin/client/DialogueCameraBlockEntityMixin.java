package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.client.interaction.DialogueCameraOcclusion;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class DialogueCameraBlockEntityMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$hideObstruction(BlockEntity entity, float partialTick, PoseStack poses,
            MultiBufferSource buffers, CallbackInfo callback) {
        if (DialogueCameraOcclusion.hides(entity.getBlockPos())) callback.cancel();
    }
}
