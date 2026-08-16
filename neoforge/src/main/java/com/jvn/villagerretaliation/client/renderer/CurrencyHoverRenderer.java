package com.jvn.villagerretaliation.client.renderer;

import com.jvn.toucanlib.client.ToucanWorldTextIndicators;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

final class CurrencyHoverRenderer {
    private static final float ITEM_BASE_SCALE = 0.55F;
    private static final float TEXT_BASE_SCALE = 0.018F;
    private static final float SELL_BOX_HEIGHT = 14.0F / 16.0F;

    private final ItemRenderer itemRenderer;
    private final EntityRenderDispatcher entityRenderer;
    private final Font font;
    private final Map<BlockEntity, HoverAnimation> hoverAnimations = new WeakHashMap<>();

    CurrencyHoverRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
        this.entityRenderer = context.getEntityRenderer();
        this.font = context.getFont();
    }

    void render(
            BlockEntity blockEntity,
            ItemStack currency,
            String amount,
            int textColor,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay) {
        boolean visible = !currency.isEmpty() && !amount.isBlank();
        boolean hovered = visible && Minecraft.getInstance().hitResult instanceof BlockHitResult hit
                && hit.getBlockPos().equals(blockEntity.getBlockPos());
        HoverAnimation animation = this.hoverAnimations.computeIfAbsent(blockEntity, ignored -> new HoverAnimation());
        float progress = animation.sample(hovered, Util.getMillis());
        if (!visible || progress <= 0.001F) {
            return;
        }

        float eased = smoothstep(progress);
        float popScale = eased * (1.0F + 0.12F * Mth.sin(progress * Mth.PI));
        float time = blockEntity.getLevel() == null ? 0.0F : blockEntity.getLevel().getGameTime() + partialTick;
        float hoverY = Mth.sin(time * 0.12F) * 0.035F;
        int hoverPackedLight = packedLight;
        float boxHeightOffset = 0.0F;
        if (blockEntity.getLevel() != null) {
            hoverPackedLight = LevelRenderer.getLightColor(blockEntity.getLevel(), blockEntity.getBlockPos().above());
            VoxelShape shape = blockEntity.getBlockState().getShape(
                    blockEntity.getLevel(), blockEntity.getBlockPos());
            if (!shape.isEmpty()) {
                boxHeightOffset = (float) shape.max(Direction.Axis.Y) - SELL_BOX_HEIGHT;
            }
        }

        poseStack.pushPose();
        poseStack.translate(0.5F, 1.18F + boxHeightOffset + hoverY, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(time * 4.0F));
        poseStack.scale(ITEM_BASE_SCALE * popScale, ITEM_BASE_SCALE * popScale, ITEM_BASE_SCALE * popScale);
        this.itemRenderer.renderStatic(
                currency,
                ItemDisplayContext.FIXED,
                hoverPackedLight,
                packedOverlay,
                poseStack,
                bufferSource,
                blockEntity.getLevel(),
                (int) blockEntity.getBlockPos().asLong());
        poseStack.popPose();

        int alpha = Mth.clamp((int) (eased * 255.0F), 0, 255);
        int color = (textColor & 0x00FFFFFF) | (alpha << 24);
        float width = this.font.width(amount);

        poseStack.pushPose();
        poseStack.translate(0.5F, 1.58F + boxHeightOffset + hoverY, 0.5F);
        poseStack.mulPose(this.entityRenderer.cameraOrientation());
        float textScale = TEXT_BASE_SCALE * popScale;
        poseStack.scale(textScale, -textScale, textScale);
        ToucanWorldTextIndicators.drawOutlinedText(
                this.font,
                bufferSource,
                poseStack.last().pose(),
                amount,
                -width / 2.0F,
                0.0F,
                color,
                false);
        poseStack.popPose();
    }

    private static float smoothstep(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    private static final class HoverAnimation {
        private float progress;
        private long lastMillis;

        private float sample(boolean hovered, long nowMillis) {
            if (this.lastMillis == 0L) {
                this.lastMillis = nowMillis;
                return this.progress;
            }
            long elapsed = Math.min(50L, Math.max(0L, nowMillis - this.lastMillis));
            this.lastMillis = nowMillis;
            float duration = hovered ? 150.0F : 190.0F;
            this.progress = Mth.clamp(
                    this.progress + (hovered ? elapsed : -elapsed) / duration,
                    0.0F,
                    1.0F);
            return this.progress;
        }
    }
}
