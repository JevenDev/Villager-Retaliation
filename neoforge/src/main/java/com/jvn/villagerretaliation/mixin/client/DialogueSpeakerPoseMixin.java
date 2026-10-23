package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.client.interaction.VillagerDialoguePerformance;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class DialogueSpeakerPoseMixin<T extends LivingEntity> {
    @Shadow protected EntityModel<T> model;

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V",
                    shift = At.Shift.AFTER))
    private void villagerretaliation$animateDialogueSpeaker(T entity, float yaw, float partialTick,
            PoseStack poses, MultiBufferSource buffers, int light, CallbackInfo callback) {
        if (model instanceof HeadedModel headed) VillagerDialoguePerformance.apply(entity, headed.getHead(), partialTick);
    }
}
