package com.jvn.villagerretaliation.client.renderer;

import com.jvn.villagerretaliation.client.model.BaseVillagerModel;
import com.jvn.villagerretaliation.client.model.VillagerRetaliationEntityModelLoader;
import com.jvn.villagerretaliation.client.model.VillagerRetaliationVillagerModel;
import com.jvn.villagerretaliation.client.model.VanillaVillagerModelAdapter;
import com.jvn.villagerretaliation.client.interaction.VillagerDialogueMouthAnimation;
import com.jvn.villagerretaliation.client.pose.VillagerPoseProvider;
import com.jvn.villagerretaliation.client.reputation.FearedVillagerAnimationClientCache;
import com.jvn.villagerretaliation.client.renderer.layer.CombatItemInHandLayer;
import com.jvn.villagerretaliation.client.renderer.layer.VillagerCrossedArmsItemLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.AbstractVillager;

public abstract class AbstractVillagerRetaliationVillagerRenderer<T extends AbstractVillager> extends MobRenderer<T, BaseVillagerModel<T>> {
    private static final long MODEL_REFRESH_INTERVAL_MILLIS = 1000L;

    private final EntityRendererProvider.Context context;
    private final VanillaVillagerModelAdapter<T> vanillaModel;
    private BaseVillagerModel<T> nonCombatModel;
    private VillagerRetaliationVillagerModel<T> combatModel;
    private String combatModelSource;
    private String nonCombatModelSource;
    private boolean preferVanillaCemDefaultPose;
    private long nextModelRefreshMillis;
    private final VillagerPoseProvider<T> poseProvider;
    private final ResourceLocation vanillaTexture;
    private final ResourceLocation combatTexture;
    private final boolean useCombatModelForAllPoses;
    private final boolean useVanillaCemModelForDefaultPose;

    protected AbstractVillagerRetaliationVillagerRenderer(
            EntityRendererProvider.Context context,
            ModelLayerLocation vanillaLayer,
            VillagerPoseProvider<T> poseProvider,
            ResourceLocation vanillaTexture,
            ResourceLocation combatTexture
    ) {
        this(context, vanillaLayer, poseProvider, vanillaTexture, combatTexture, false);
    }

    protected AbstractVillagerRetaliationVillagerRenderer(
            EntityRendererProvider.Context context,
            ModelLayerLocation vanillaLayer,
            VillagerPoseProvider<T> poseProvider,
            ResourceLocation vanillaTexture,
            ResourceLocation combatTexture,
            boolean useCombatModelForAllPoses
    ) {
        this(context, vanillaLayer, poseProvider, vanillaTexture, combatTexture, useCombatModelForAllPoses, false);
    }

    protected AbstractVillagerRetaliationVillagerRenderer(
            EntityRendererProvider.Context context,
            ModelLayerLocation vanillaLayer,
            VillagerPoseProvider<T> poseProvider,
            ResourceLocation vanillaTexture,
            ResourceLocation combatTexture,
            boolean useCombatModelForAllPoses,
            boolean useVanillaCemModelForDefaultPose
    ) {
        super(context, new VanillaVillagerModelAdapter<>(context.bakeLayer(vanillaLayer)), 0.5F);
        this.context = context;
        this.vanillaModel = (VanillaVillagerModelAdapter<T>) this.model;
        this.poseProvider = poseProvider;
        this.vanillaTexture = vanillaTexture;
        this.combatTexture = combatTexture;
        this.useCombatModelForAllPoses = useCombatModelForAllPoses;
        this.useVanillaCemModelForDefaultPose = useVanillaCemModelForDefaultPose;
        this.reloadCombatModel();
        this.reloadNonCombatModel();
        this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
        this.addLayer(new VillagerCrossedArmsItemLayer<>(this, context.getItemInHandRenderer()));
        this.addLayer(new CombatItemInHandLayer<>(this, context.getItemInHandRenderer(), poseProvider));
    }

    @Override
    public void render(T villager, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        this.refreshModels();
        this.model = shouldUseCombatTextureAndModel(villager, this.getAttackAnim(villager, partialTick)) ? this.combatModel : this.nonCombatModel;
        boolean previousSprinting = villager.isSprinting();
        boolean talking = VillagerDialogueMouthAnimation.isTalking(villager);
        if (talking) {
            villager.setSprinting(true);
        }
        try {
            super.render(villager, entityYaw, partialTick, poseStack, buffer, packedLight);
        } finally {
            if (talking) {
                villager.setSprinting(previousSprinting);
            }
        }
    }

    @Override
    public ResourceLocation getTextureLocation(T villager) {
        return this.model == this.combatModel ? this.combatTexture : this.vanillaTexture;
    }

    @Override
    protected boolean isShaking(T villager) {
        return super.isShaking(villager) || FearedVillagerAnimationClientCache.isShaking(villager);
    }

    private void refreshModels() {
        long now = Util.getMillis();
        if (now < this.nextModelRefreshMillis) {
            return;
        }
        this.nextModelRefreshMillis = now + MODEL_REFRESH_INTERVAL_MILLIS;

        String currentModelSource = VillagerRetaliationEntityModelLoader.combatVillagerModelSource(this.context.getResourceManager());
        if (!currentModelSource.equals(this.combatModelSource)) {
            this.reloadCombatModel();
        }

        String currentNonCombatModelSource = VillagerRetaliationEntityModelLoader.nonCombatVillagerModelSource(this.context.getResourceManager());
        if (!currentNonCombatModelSource.equals(this.nonCombatModelSource)) {
            this.reloadNonCombatModel();
        }

        this.refreshVanillaCemDefaultPosePreference();
    }

    private boolean shouldUseCombatTextureAndModel(T villager, float attackTime) {
        boolean needsSideArmModel = this.poseProvider.shouldUseCombatModel(villager)
                || attackTime > 0.0F
                || VillagerRenderEquipmentState.hasArmorEquipped(villager)
                || !VillagerRenderEquipmentState.visibleMainHand(villager).isEmpty()
                || !villager.getOffhandItem().isEmpty();
        if (needsSideArmModel) {
            return true;
        }

        return this.useCombatModelForAllPoses && !this.shouldPreferVanillaCemDefaultPose();
    }

    private boolean shouldPreferVanillaCemDefaultPose() {
        return this.preferVanillaCemDefaultPose;
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
        this.refreshVanillaCemDefaultPosePreference();
    }

    private void refreshVanillaCemDefaultPosePreference() {
        this.preferVanillaCemDefaultPose = this.useVanillaCemModelForDefaultPose
                && VillagerRetaliationEntityModelLoader.hasVanillaVillagerCemModel(this.context.getResourceManager());
    }
}
