package com.jvn.commonfolk.client.renderer;

import com.jvn.commonfolk.client.model.BaseVillagerModel;
import com.jvn.commonfolk.client.model.CommonfolkVillagerModel;
import com.jvn.commonfolk.client.model.VanillaVillagerModelAdapter;
import com.jvn.commonfolk.client.pose.VillagerPoseProvider;
import com.jvn.commonfolk.client.renderer.layer.CombatItemInHandLayer;
import com.jvn.commonfolk.client.renderer.layer.VillagerCrossedArmsItemLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.AbstractVillager;

public abstract class AbstractCommonfolkVillagerRenderer<T extends AbstractVillager> extends MobRenderer<T, BaseVillagerModel<T>> {
    private final VanillaVillagerModelAdapter<T> vanillaModel;
    private final CommonfolkVillagerModel<T> combatModel;
    private final VillagerPoseProvider<T> poseProvider;
    private final ResourceLocation vanillaTexture;
    private final ResourceLocation combatTexture;

    protected AbstractCommonfolkVillagerRenderer(
            EntityRendererProvider.Context context,
            ModelLayerLocation vanillaLayer,
            VillagerPoseProvider<T> poseProvider,
            ResourceLocation vanillaTexture,
            ResourceLocation combatTexture
    ) {
        super(context, new VanillaVillagerModelAdapter<>(context.bakeLayer(vanillaLayer)), 0.5F);
        this.vanillaModel = (VanillaVillagerModelAdapter<T>) this.model;
        this.poseProvider = poseProvider;
        this.vanillaTexture = vanillaTexture;
        this.combatTexture = combatTexture;
        this.combatModel = new CommonfolkVillagerModel<>(context.bakeLayer(CommonfolkVillagerModel.LAYER_LOCATION), poseProvider);
        this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
        this.addLayer(new VillagerCrossedArmsItemLayer<>(this, context.getItemInHandRenderer()));
        this.addLayer(new CombatItemInHandLayer<>(this, context.getItemInHandRenderer(), poseProvider));
    }

    @Override
    public void render(T villager, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        this.model = this.poseProvider.shouldUseCombatModel(villager) ? this.combatModel : this.vanillaModel;
        super.render(villager, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(T villager) {
        return this.poseProvider.shouldUseCombatModel(villager) ? this.combatTexture : this.vanillaTexture;
    }
}
