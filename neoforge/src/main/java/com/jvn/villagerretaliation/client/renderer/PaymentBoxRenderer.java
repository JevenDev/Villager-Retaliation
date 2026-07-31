package com.jvn.villagerretaliation.client.renderer;

import com.jvn.villagerretaliation.block.PaymentBoxBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

public final class PaymentBoxRenderer implements BlockEntityRenderer<PaymentBoxBlockEntity> {
    private final CurrencyHoverRenderer currencyHoverRenderer;

    public PaymentBoxRenderer(BlockEntityRendererProvider.Context context) {
        this.currencyHoverRenderer = new CurrencyHoverRenderer(context);
    }

    @Override
    public void render(
            PaymentBoxBlockEntity paymentBox,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay) {
        this.currencyHoverRenderer.render(
                paymentBox,
                paymentBox.displayCurrency(),
                paymentBox.displayCurrencyAmount(),
                paymentBox.displayCurrencyColor(),
                partialTick,
                poseStack,
                bufferSource,
                packedLight,
                packedOverlay);
    }
}
