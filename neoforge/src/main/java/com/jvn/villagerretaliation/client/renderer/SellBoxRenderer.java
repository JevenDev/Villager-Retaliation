package com.jvn.villagerretaliation.client.renderer;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.block.SellBoxBlock;
import com.jvn.villagerretaliation.block.SellBoxBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

public final class SellBoxRenderer implements BlockEntityRenderer<SellBoxBlockEntity> {
    private static final ResourceLocation TEXTURE =
            VillagerRetaliation.id("textures/entity/chest/sell_box.png");
    private final ModelPart lid;
    private final ModelPart bottom;
    private final ModelPart lock;
    private final CurrencyHoverRenderer currencyHoverRenderer;

    public SellBoxRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart model = context.bakeLayer(ModelLayers.CHEST);
        this.bottom = model.getChild("bottom");
        this.lid = model.getChild("lid");
        this.lock = model.getChild("lock");
        this.currencyHoverRenderer = new CurrencyHoverRenderer(context);
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

        this.currencyHoverRenderer.render(
                sellBox,
                sellBox.displayCurrency(),
                sellBox.displayCurrencyAmount(),
                sellBox.displayCurrencyColor(),
                partialTick,
                poseStack,
                bufferSource,
                packedLight,
                packedOverlay);
    }
}
