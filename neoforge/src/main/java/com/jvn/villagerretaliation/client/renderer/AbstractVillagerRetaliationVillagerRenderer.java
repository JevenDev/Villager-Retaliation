package com.jvn.villagerretaliation.client.renderer;

import com.jvn.villagerretaliation.client.model.BaseVillagerModel;
import com.jvn.villagerretaliation.client.inventory.VillagerInventoryScreen;
import com.jvn.villagerretaliation.client.model.VillagerRetaliationEntityModelLoader;
import com.jvn.villagerretaliation.client.model.VillagerRetaliationVillagerModel;
import com.jvn.villagerretaliation.client.model.VanillaVillagerModelAdapter;
import com.jvn.villagerretaliation.client.pose.VillagerPoseProvider;
import com.jvn.villagerretaliation.client.reputation.FearedVillagerAnimationClientCache;
import com.jvn.villagerretaliation.client.renderer.layer.CombatItemInHandLayer;
import com.jvn.villagerretaliation.client.renderer.layer.VillagerCrossedArmsItemLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.AbstractVillager;

public abstract class AbstractVillagerRetaliationVillagerRenderer<T extends AbstractVillager> extends MobRenderer<T, BaseVillagerModel<T>> {
    private final EntityRendererProvider.Context context;
    private final VanillaVillagerModelAdapter<T> vanillaModel;
    private BaseVillagerModel<T> nonCombatModel;
    private VillagerRetaliationVillagerModel<T> combatModel;
    private String combatModelSource;
    private String nonCombatModelSource;
    private final VillagerPoseProvider<T> poseProvider;
    private final ResourceLocation vanillaTexture;
    private final ResourceLocation combatTexture;

    protected AbstractVillagerRetaliationVillagerRenderer(
            EntityRendererProvider.Context context,
            ModelLayerLocation vanillaLayer,
            VillagerPoseProvider<T> poseProvider,
            ResourceLocation vanillaTexture,
            ResourceLocation combatTexture
    ) {
        super(context, new VanillaVillagerModelAdapter<>(context.bakeLayer(vanillaLayer)), 0.5F);
        this.context = context;
        this.vanillaModel = (VanillaVillagerModelAdapter<T>) this.model;
        this.poseProvider = poseProvider;
        this.vanillaTexture = vanillaTexture;
        this.combatTexture = combatTexture;
        this.reloadCombatModel();
        this.reloadNonCombatModel();
        this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
        this.addLayer(new VillagerCrossedArmsItemLayer<>(this, context.getItemInHandRenderer()));
        this.addLayer(new CombatItemInHandLayer<>(this, context.getItemInHandRenderer(), poseProvider));
    }

    @Override
    public void render(T villager, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        this.refreshModels();
        this.model = shouldUseHandItemModel(villager) ? this.combatModel : this.nonCombatModel;
        super.render(villager, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(T villager) {
        return this.poseProvider.shouldUseCombatModel(villager) ? this.combatTexture : this.vanillaTexture;
    }

    @Override
    protected boolean isShaking(T villager) {
        return super.isShaking(villager) || FearedVillagerAnimationClientCache.isShaking(villager);
    }

    private void refreshModels() {
        String currentModelSource = VillagerRetaliationEntityModelLoader.combatVillagerModelSource(this.context.getResourceManager());
        if (!currentModelSource.equals(this.combatModelSource)) {
            this.reloadCombatModel();
        }

        String currentNonCombatModelSource = VillagerRetaliationEntityModelLoader.nonCombatVillagerModelSource(this.context.getResourceManager());
        if (!currentNonCombatModelSource.equals(this.nonCombatModelSource)) {
            this.reloadNonCombatModel();
        }
    }

    private boolean shouldUseHandItemModel(T villager) {
        return this.poseProvider.shouldUseCombatModel(villager)
                || VillagerInventoryScreen.isRenderingInventoryPreview(villager)
                && (!villager.getMainHandItem().isEmpty() || !villager.getOffhandItem().isEmpty());
    }

    private void reloadCombatModel() {
        this.combatModelSource = VillagerRetaliationEntityModelLoader.combatVillagerModelSource(this.context.getResourceManager());
        this.combatModel = new VillagerRetaliationVillagerModel<>(
                VillagerRetaliationEntityModelLoader.loadCombatVillagerModel(this.context),
                this.poseProvider
        );
    }

    private void reloadNonCombatModel() {
        this.nonCombatModelSource = VillagerRetaliationEntityModelLoader.nonCombatVillagerModelSource(this.context.getResourceManager());
        this.nonCombatModel = VillagerRetaliationEntityModelLoader.loadNonCombatVillagerModel(this.context.getResourceManager())
                .<BaseVillagerModel<T>>map(VillagerRetaliationVillagerModel::new)
                .orElse(this.vanillaModel);
    }
}
