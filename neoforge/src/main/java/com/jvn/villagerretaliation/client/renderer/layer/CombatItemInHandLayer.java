package com.jvn.villagerretaliation.client.renderer.layer;

import com.jvn.villagerretaliation.client.model.BaseVillagerModel;
import com.jvn.villagerretaliation.client.model.VillagerRetaliationVillagerModel;
import com.jvn.villagerretaliation.client.pose.VillagerPoseProvider;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerEquipment;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class CombatItemInHandLayer<T extends AbstractVillager> extends RenderLayer<T, BaseVillagerModel<T>> {
    private final ItemInHandRenderer itemInHandRenderer;
    private final VillagerPoseProvider<T> poseProvider;

    public CombatItemInHandLayer(
            RenderLayerParent<T, BaseVillagerModel<T>> renderer,
            ItemInHandRenderer itemInHandRenderer,
            VillagerPoseProvider<T> poseProvider
    ) {
        super(renderer);
        this.itemInHandRenderer = itemInHandRenderer;
        this.poseProvider = poseProvider;
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
        if (!(this.getParentModel() instanceof VillagerRetaliationVillagerModel<?> combatModel)) {
            return;
        }
        if (!this.poseProvider.shouldRenderHeldItem(villager)) {
            return;
        }

        boolean mainHandIsRight = villager.getMainArm() == HumanoidArm.RIGHT;
        ItemStack mainHandItem = VillagerRetaliationVillagerEquipment.visibleMainHand(villager);
        ItemStack leftHandItem = mainHandIsRight ? villager.getOffhandItem() : mainHandItem;
        ItemStack rightHandItem = mainHandIsRight ? mainHandItem : villager.getOffhandItem();
        if (leftHandItem.isEmpty() && rightHandItem.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        if (combatModel.young) {
            poseStack.translate(0.0F, 0.75F, 0.0F);
            poseStack.scale(0.5F, 0.5F, 0.5F);
        }

        @SuppressWarnings("unchecked")
        VillagerRetaliationVillagerModel<T> typedModel = (VillagerRetaliationVillagerModel<T>) combatModel;
        this.renderArmWithItem(villager, typedModel, rightHandItem, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, HumanoidArm.RIGHT, poseStack, buffer, packedLight);
        this.renderArmWithItem(villager, typedModel, leftHandItem, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, HumanoidArm.LEFT, poseStack, buffer, packedLight);
        poseStack.popPose();
    }

    private void renderArmWithItem(
            T villager,
            VillagerRetaliationVillagerModel<T> model,
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
