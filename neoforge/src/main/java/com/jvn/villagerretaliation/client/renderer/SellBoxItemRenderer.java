package com.jvn.villagerretaliation.client.renderer;

import com.jvn.villagerretaliation.block.SellBoxBlockEntity;
import com.jvn.villagerretaliation.block.VillagerRetaliationBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class SellBoxItemRenderer extends BlockEntityWithoutLevelRenderer {
    private final BlockEntityRenderDispatcher dispatcher;
    private final SellBoxBlockEntity sellBox = new SellBoxBlockEntity(
            BlockPos.ZERO,
            VillagerRetaliationBlocks.SELL_BOX.get().defaultBlockState());

    public SellBoxItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet modelSet) {
        super(dispatcher, modelSet);
        this.dispatcher = dispatcher;
    }

    @Override
    public void renderByItem(
            ItemStack stack,
            ItemDisplayContext displayContext,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay) {
        this.dispatcher.renderItem(this.sellBox, poseStack, buffer, packedLight, packedOverlay);
    }
}
