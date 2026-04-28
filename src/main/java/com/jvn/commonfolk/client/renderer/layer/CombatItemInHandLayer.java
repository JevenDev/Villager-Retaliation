package com.jvn.commonfolk.client.renderer.layer;

import com.jvn.commonfolk.client.model.BaseVillagerModel;
import com.jvn.commonfolk.client.model.CommonfolkVillagerModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class CombatItemInHandLayer extends RenderLayer<Villager, BaseVillagerModel<Villager>> {
    private final ItemInHandRenderer itemInHandRenderer;

    public CombatItemInHandLayer(RenderLayerParent<Villager, BaseVillagerModel<Villager>> renderer, ItemInHandRenderer itemInHandRenderer) {
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
        if (!(this.getParentModel() instanceof CommonfolkVillagerModel<?> combatModel)) {
            return;
        }

        boolean mainHandIsRight = villager.getMainArm() == HumanoidArm.RIGHT;
        ItemStack leftHandItem = mainHandIsRight ? villager.getOffhandItem() : villager.getMainHandItem();
        ItemStack rightHandItem = mainHandIsRight ? villager.getMainHandItem() : villager.getOffhandItem();
        if (leftHandItem.isEmpty() && rightHandItem.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        if (combatModel.young) {
            poseStack.translate(0.0F, 0.75F, 0.0F);
            poseStack.scale(0.5F, 0.5F, 0.5F);
        }

        this.renderArmWithItem(villager, combatModel, rightHandItem, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, HumanoidArm.RIGHT, poseStack, buffer, packedLight);
        this.renderArmWithItem(villager, combatModel, leftHandItem, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, HumanoidArm.LEFT, poseStack, buffer, packedLight);
        poseStack.popPose();
    }

    private void renderArmWithItem(
            Villager villager,
            CommonfolkVillagerModel<?> model,
            ItemStack itemStack,
            ItemDisplayContext displayContext,
            HumanoidArm arm,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight
    ) {
        if (itemStack.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        model.translateToHand(arm, poseStack);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        boolean leftArm = arm == HumanoidArm.LEFT;
        poseStack.translate((float) (leftArm ? -1 : 1) / 16.0F, 0.125F, -0.625F);
        this.itemInHandRenderer.renderItem(villager, itemStack, displayContext, leftArm, poseStack, buffer, packedLight);
        poseStack.popPose();
    }
}
