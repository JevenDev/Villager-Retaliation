package com.jvn.villagerretaliation.client.renderer.layer;

import com.jvn.villagerretaliation.client.model.BaseVillagerModel;
import com.jvn.villagerretaliation.client.model.VillagerRetaliationVillagerModel;
import com.jvn.villagerretaliation.client.renderer.VillagerRenderEquipmentState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class VillagerCrossedArmsItemLayer<T extends AbstractVillager> extends RenderLayer<T, BaseVillagerModel<T>> {
    private final ItemInHandRenderer itemInHandRenderer;

    public VillagerCrossedArmsItemLayer(
            RenderLayerParent<T, BaseVillagerModel<T>> renderer,
            ItemInHandRenderer itemInHandRenderer
    ) {
        super(renderer);
        this.itemInHandRenderer = itemInHandRenderer;
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            T villager,
            float limbSwing,
            float limbSwingAmount,
            float partialTicks,
            float ageInTicks,
            float netHeadYaw,
            float headPitch
    ) {
        if (this.getParentModel() instanceof VillagerRetaliationVillagerModel<?>) {
            return;
        }

        ItemStack mainHand = VillagerRenderEquipmentState.visibleMainHand(villager);
        if (mainHand.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.0F, 0.4F, -0.4F);
        poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
        this.itemInHandRenderer.renderItem(villager, mainHand, ItemDisplayContext.GROUND, false, poseStack, buffer, packedLight);
        poseStack.popPose();
    }
}
