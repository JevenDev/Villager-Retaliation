package com.jvn.commonfolk.client.renderer.layer;

import com.jvn.commonfolk.client.model.BaseVillagerModel;
import com.jvn.commonfolk.client.model.CommonfolkVillagerModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class VillagerCrossedArmsItemLayer extends RenderLayer<Villager, BaseVillagerModel<Villager>> {
    private final ItemInHandRenderer itemInHandRenderer;

    public VillagerCrossedArmsItemLayer(
            RenderLayerParent<Villager, BaseVillagerModel<Villager>> renderer,
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
            Villager villager,
            float limbSwing,
            float limbSwingAmount,
            float partialTicks,
            float ageInTicks,
            float netHeadYaw,
            float headPitch
    ) {
        if (this.getParentModel() instanceof CommonfolkVillagerModel<?>) {
            return;
        }

        ItemStack mainHand = villager.getItemBySlot(EquipmentSlot.MAINHAND);
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
