package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.client.interaction.DialogueCameraOcclusion;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public abstract class DialogueCameraEntityMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$hideObstruction(Entity entity, double x, double y, double z, float yaw,
            float partialTick, PoseStack poses, MultiBufferSource buffers, int light, CallbackInfo callback) {
        if (DialogueCameraOcclusion.hides(entity)) callback.cancel();
    }
}
