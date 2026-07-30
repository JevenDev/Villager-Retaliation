package com.jvn.villagerretaliation.client.renderer;

import com.jvn.toucanlib.client.ToucanWorldTextIndicators;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.block.SellBoxBlock;
import com.jvn.villagerretaliation.block.SellBoxBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.Util;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class SellBoxRenderer implements BlockEntityRenderer<SellBoxBlockEntity> {
    private static final ResourceLocation TEXTURE =
            VillagerRetaliation.id("textures/entity/chest/sell_box.png");
    private static final float ITEM_BASE_SCALE = 0.55F;
    private static final float TEXT_BASE_SCALE = 0.018F;

    private final ModelPart lid;
    private final ModelPart bottom;
    private final ModelPart lock;
    private final ItemRenderer itemRenderer;
    private final EntityRenderDispatcher entityRenderer;
    private final Font font;
    private final Map<SellBoxBlockEntity, HoverAnimation> hoverAnimations = new WeakHashMap<>();

    public SellBoxRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart model = context.bakeLayer(ModelLayers.CHEST);
        this.bottom = model.getChild("bottom");
        this.lid = model.getChild("lid");
        this.lock = model.getChild("lock");
        this.itemRenderer = context.getItemRenderer();
        this.entityRenderer = context.getEntityRenderer();
        this.font = context.getFont();
    }

    @Override
    public void render(
            SellBoxBlockEntity sellBox,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay) {
        BlockState state = sellBox.getBlockState();
        Direction facing = sellBox.getLevel() != null && state.hasProperty(SellBoxBlock.FACING)
                ? state.getValue(SellBoxBlock.FACING)
                : Direction.SOUTH;

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        poseStack.translate(-0.5F, -0.5F, -0.5F);

        float closed = 1.0F - sellBox.getOpenNess(partialTick);
        float lidProgress = 1.0F - closed * closed * closed;
        float lidAngle = -(lidProgress * (float) (Math.PI / 2.0));
        this.lid.xRot = lidAngle;
        this.lock.xRot = lidAngle;

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutout(TEXTURE));
        this.lid.render(poseStack, consumer, packedLight, packedOverlay);
        this.lock.render(poseStack, consumer, packedLight, packedOverlay);
        this.bottom.render(poseStack, consumer, packedLight, packedOverlay);
        poseStack.popPose();

        renderCurrencyHover(sellBox, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
    }

    private void renderCurrencyHover(
            SellBoxBlockEntity sellBox,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay) {
        ItemStack currency = sellBox.displayCurrency();
        boolean visible = !currency.isEmpty() && !sellBox.displayCurrencyAmount().isBlank();
        boolean hovered = visible && Minecraft.getInstance().hitResult instanceof BlockHitResult hit
                && hit.getBlockPos().equals(sellBox.getBlockPos());
        HoverAnimation animation = this.hoverAnimations.computeIfAbsent(sellBox, ignored -> new HoverAnimation());
        float progress = animation.sample(hovered, Util.getMillis());
        if (!visible || progress <= 0.001F) {
            return;
        }

        float eased = smoothstep(progress);
        float popScale = eased * (1.0F + 0.12F * Mth.sin(progress * Mth.PI));
        float time = sellBox.getLevel() == null ? 0.0F : sellBox.getLevel().getGameTime() + partialTick;
        float hoverY = Mth.sin(time * 0.12F) * 0.035F;

        poseStack.pushPose();
        poseStack.translate(0.5F, 1.18F + hoverY, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(time * 4.0F));
        poseStack.scale(ITEM_BASE_SCALE * popScale, ITEM_BASE_SCALE * popScale, ITEM_BASE_SCALE * popScale);
        this.itemRenderer.renderStatic(
                currency,
                ItemDisplayContext.FIXED,
                packedLight,
                packedOverlay,
                poseStack,
                bufferSource,
                sellBox.getLevel(),
                (int) sellBox.getBlockPos().asLong());
        poseStack.popPose();

        String amount = sellBox.displayCurrencyAmount();
        int alpha = Mth.clamp((int) (eased * 255.0F), 0, 255);
        int color = (sellBox.displayCurrencyColor() & 0x00FFFFFF) | (alpha << 24);
        float width = this.font.width(amount);

        poseStack.pushPose();
        poseStack.translate(0.5F, 1.58F + hoverY, 0.5F);
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
