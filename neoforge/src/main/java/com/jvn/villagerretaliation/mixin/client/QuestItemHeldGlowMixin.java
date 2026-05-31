package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.client.quest.VillagerQuestItemHighlightClient;
import com.jvn.villagerretaliation.client.quest.VillagerQuestOutlineBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class QuestItemHeldGlowMixin {
    private static final VillagerQuestOutlineBufferSource VILLAGERRETALIATION_OUTLINE_BUFFER =
            new VillagerQuestOutlineBufferSource();

    @Shadow
    public abstract BakedModel getModel(
            ItemStack stack,
            @Nullable Level level,
            @Nullable LivingEntity entity,
            int seed);

    @Shadow
    public abstract void render(
            ItemStack itemStack,
            ItemDisplayContext displayContext,
            boolean leftHand,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int combinedLight,
            int combinedOverlay,
            BakedModel model);

    @Inject(
            method = "renderStatic(Lnet/minecraft/world/entity/LivingEntity;"
                    + "Lnet/minecraft/world/item/ItemStack;"
                    + "Lnet/minecraft/world/item/ItemDisplayContext;"
                    + "ZLcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;"
                    + "Lnet/minecraft/world/level/Level;III)V",
            at = @At("HEAD"))
    private void villagerretaliation$renderHeldQuestItemOutlineMask(
            @Nullable LivingEntity entity,
            ItemStack stack,
            ItemDisplayContext displayContext,
            boolean leftHand,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            @Nullable Level level,
            int packedLight,
            int packedOverlay,
            int seed,
            CallbackInfo callbackInfo) {
        if (!VillagerQuestItemHighlightClient.shouldRenderHeldQuestGlow(entity, stack, displayContext)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || minecraft.player == null
                || !minecraft.levelRenderer.shouldShowEntityOutlines()) {
            return;
        }

        VILLAGERRETALIATION_OUTLINE_BUFFER.setColor(
                VillagerQuestItemHighlightClient.QUEST_OUTLINE_RED,
                VillagerQuestItemHighlightClient.QUEST_OUTLINE_GREEN,
                VillagerQuestItemHighlightClient.QUEST_OUTLINE_BLUE,
                VillagerQuestItemHighlightClient.QUEST_OUTLINE_ALPHA);
        BakedModel model = this.getModel(stack, level, entity, seed);
        this.render(
                stack,
                displayContext,
                leftHand,
                poseStack,
                VILLAGERRETALIATION_OUTLINE_BUFFER,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                model);
        VILLAGERRETALIATION_OUTLINE_BUFFER.endOutlineBatch();
        if (displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
            minecraft.levelRenderer.requestOutlineEffect();
        }
    }
}
