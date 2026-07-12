package com.jvn.villagerretaliation.client.renderer.layer;

import com.jvn.villagerretaliation.client.model.BaseVillagerModel;
import com.jvn.villagerretaliation.client.model.VillagerArmorModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.armortrim.ArmorTrim;

public class VillagerArmorLayer<T extends AbstractVillager> extends RenderLayer<T, BaseVillagerModel<T>> {
    private final VillagerArmorModel<T> innerModel;
    private final VillagerArmorModel<T> outerModel;
    private final TextureAtlas armorTrimAtlas;

    public VillagerArmorLayer(RenderLayerParent<T, BaseVillagerModel<T>> renderer, EntityRendererProvider.Context context) {
        super(renderer);
        this.innerModel = new VillagerArmorModel<>(context.bakeLayer(VillagerArmorModel.INNER_ARMOR));
        this.outerModel = new VillagerArmorModel<>(context.bakeLayer(VillagerArmorModel.OUTER_ARMOR));
        this.armorTrimAtlas = context.getModelManager().getAtlas(Sheets.ARMOR_TRIMS_SHEET);
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            T villager,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch
    ) {
        poseStack.pushPose();
        this.getParentModel().translateRoot(poseStack);
        this.renderArmorPiece(poseStack, buffer, villager, EquipmentSlot.CHEST, packedLight,
                this.getArmorModel(EquipmentSlot.CHEST), limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
        this.renderArmorPiece(poseStack, buffer, villager, EquipmentSlot.LEGS, packedLight,
                this.getArmorModel(EquipmentSlot.LEGS), limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
        this.renderArmorPiece(poseStack, buffer, villager, EquipmentSlot.FEET, packedLight,
                this.getArmorModel(EquipmentSlot.FEET), limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
        this.renderArmorPiece(poseStack, buffer, villager, EquipmentSlot.HEAD, packedLight,
                this.getArmorModel(EquipmentSlot.HEAD), limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
        poseStack.popPose();
    }

    private void renderArmorPiece(
            PoseStack poseStack,
            MultiBufferSource buffer,
            T villager,
            EquipmentSlot slot,
            int packedLight,
            VillagerArmorModel<T> armorModel,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch
    ) {
        ItemStack stack = villager.getItemBySlot(slot);
        if (!(stack.getItem() instanceof ArmorItem armorItem) || armorItem.getEquipmentSlot() != slot) {
            return;
        }

        this.getParentModel().copyPropertiesToHumanoidArmor(armorModel);
        this.setPartVisibility(armorModel, slot);
        net.minecraft.client.model.Model model = this.getArmorModelHook(villager, stack, slot, armorModel);
        boolean innerTexture = this.usesInnerModel(slot);
        ArmorMaterial armorMaterial = armorItem.getMaterial().value();

        net.neoforged.neoforge.client.extensions.common.IClientItemExtensions extensions =
                net.neoforged.neoforge.client.extensions.common.IClientItemExtensions.of(stack);
        extensions.setupModelAnimations(villager, stack, slot, model, limbSwing, limbSwingAmount,
                partialTick, ageInTicks, netHeadYaw, headPitch);
        int fallbackColor = extensions.getDefaultDyeColor(stack);
        for (int layerIndex = 0; layerIndex < armorMaterial.layers().size(); layerIndex++) {
            ArmorMaterial.Layer layer = armorMaterial.layers().get(layerIndex);
            int tintColor = extensions.getArmorLayerTintColor(stack, villager, layer, layerIndex, fallbackColor);
            if (tintColor != 0) {
                ResourceLocation texture = net.neoforged.neoforge.client.ClientHooks.getArmorTexture(
                        villager, stack, layer, innerTexture, slot);
                this.renderModel(poseStack, buffer, packedLight, model, tintColor, texture);
            }
        }

        ArmorTrim trim = stack.get(DataComponents.TRIM);
        if (trim != null) {
            this.renderTrim(armorItem.getMaterial(), poseStack, buffer, packedLight, trim, model, innerTexture);
        }

        if (stack.hasFoil()) {
            this.renderGlint(poseStack, buffer, packedLight, model);
        }
    }

    private void setPartVisibility(HumanoidModel<T> armorModel, EquipmentSlot slot) {
        armorModel.setAllVisible(false);
        switch (slot) {
            case HEAD:
                armorModel.head.visible = true;
                armorModel.hat.visible = true;
                break;
            case CHEST:
                armorModel.body.visible = true;
                armorModel.rightArm.visible = true;
                armorModel.leftArm.visible = true;
                break;
            case LEGS:
                armorModel.body.visible = true;
                armorModel.rightLeg.visible = true;
                armorModel.leftLeg.visible = true;
                break;
            case FEET:
                armorModel.rightLeg.visible = true;
                armorModel.leftLeg.visible = true;
                break;
            default:
                break;
        }
    }

    private void renderModel(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            net.minecraft.client.model.Model model,
            int tintColor,
            ResourceLocation texture
    ) {
        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.armorCutoutNoCull(texture));
        model.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, tintColor);
    }

    private void renderTrim(
            Holder<ArmorMaterial> armorMaterial,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            ArmorTrim trim,
            net.minecraft.client.model.Model model,
            boolean innerTexture
    ) {
        TextureAtlasSprite sprite = this.armorTrimAtlas.getSprite(
                innerTexture ? trim.innerTexture(armorMaterial) : trim.outerTexture(armorMaterial));
        VertexConsumer vertexConsumer = sprite.wrap(buffer.getBuffer(Sheets.armorTrimsSheet(trim.pattern().value().decal())));
        model.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY);
    }

    private void renderGlint(PoseStack poseStack, MultiBufferSource buffer, int packedLight, net.minecraft.client.model.Model model) {
        model.renderToBuffer(poseStack, buffer.getBuffer(RenderType.armorEntityGlint()), packedLight, OverlayTexture.NO_OVERLAY);
    }

    private VillagerArmorModel<T> getArmorModel(EquipmentSlot slot) {
        return this.usesInnerModel(slot) ? this.innerModel : this.outerModel;
    }

    private boolean usesInnerModel(EquipmentSlot slot) {
        return slot == EquipmentSlot.LEGS;
    }

    protected net.minecraft.client.model.Model getArmorModelHook(
            T villager,
            ItemStack stack,
            EquipmentSlot slot,
            VillagerArmorModel<T> armorModel
    ) {
        return net.neoforged.neoforge.client.ClientHooks.getArmorModel(villager, stack, slot, armorModel);
    }
}
