package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.client.quest.VillagerQuestItemHighlightClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class QuestItemHeldGlowMixin {
    @Shadow
    @Final
    private ItemRenderer itemRenderer;

    @Inject(method = "renderItem", at = @At("TAIL"))
    private void villagerretaliation$renderQuestItemOutline(
            LivingEntity entity,
            ItemStack stack,
            ItemDisplayContext displayContext,
            boolean leftHand,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            CallbackInfo callbackInfo) {
        if (!VillagerQuestItemHighlightClient.shouldRenderHeldQuestGlow(entity, stack, displayContext)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        OutlineBufferSource outline = minecraft.renderBuffers().outlineBufferSource();
        outline.setColor(255, 209, 102, 255);
        this.itemRenderer.renderStatic(
                entity,
                stack,
                displayContext,
                leftHand,
                poseStack,
                outline,
                entity.level(),
                packedLight,
                OverlayTexture.NO_OVERLAY,
                entity.getId() + displayContext.ordinal());
        outline.endOutlineBatch();
    }
}
