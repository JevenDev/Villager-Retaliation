package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.client.interaction.DialoguePreviewParticles;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public abstract class DialoguePreviewParticleMixin {
    @Inject(method = "tickParticle", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$previewTick(Particle particle, CallbackInfo callback) {
        if (DialoguePreviewParticles.controls(particle)) callback.cancel();
    }

    @Redirect(method = "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V",
            remap = false, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/particle/Particle;render(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V"))
    private void villagerretaliation$previewRender(Particle particle, VertexConsumer vertices, Camera camera, float partialTick) {
        if (DialoguePreviewParticles.controls(particle) && !particle.isAlive()) return;
        particle.render(vertices, camera, DialoguePreviewParticles.partialTick(particle, partialTick));
    }
}
