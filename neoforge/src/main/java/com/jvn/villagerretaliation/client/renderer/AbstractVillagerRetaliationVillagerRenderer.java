package com.jvn.villagerretaliation.client.renderer;

import com.jvn.villagerretaliation.client.model.BaseVillagerModel;
import com.jvn.villagerretaliation.client.model.HumanoidCompatVillagerModel;
import com.jvn.villagerretaliation.client.model.VillagerRetaliationEntityModelLoader;
import com.jvn.villagerretaliation.client.model.VillagerRetaliationVillagerModel;
import com.jvn.villagerretaliation.client.model.VanillaVillagerModelAdapter;
import com.jvn.villagerretaliation.client.interaction.VillagerDialogueMouthAnimation;
import com.jvn.villagerretaliation.client.pose.VillagerPoseProvider;
import com.jvn.villagerretaliation.client.reputation.FearedVillagerAnimationClientCache;
import com.jvn.villagerretaliation.config.VillagerRenderMode;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.client.villager.VillagerDownedClientCache;
import com.jvn.villagerretaliation.client.villager.VillagerNameClientCache;
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
    private HumanoidCompatVillagerModel<T> humanoidModel;
    private boolean preferVanillaCemDefaultPose;
    private final VillagerPoseProvider<T> poseProvider;
    private final ResourceLocation vanillaTexture;
    private final ResourceLocation combatTexture;
    private final boolean useCombatModelForAllPoses;
    private final boolean useVanillaCemModelForDefaultPose;
    private boolean allowConfiguredRenderModes;

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
        // EntityRenderDispatcher reconstructs entity renderers during resource reloads, so model
        // resource selection is intentionally performed here instead of polling from render().
        this.reloadCombatModel();
        this.reloadNonCombatModel();
        this.reloadHumanoidModel();
        this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
        this.addLayer(new VillagerCrossedArmsItemLayer<>(this, context.getItemInHandRenderer()));
        this.addLayer(new CombatItemInHandLayer<>(this, context.getItemInHandRenderer(), poseProvider));
    }

    protected AbstractVillagerRetaliationVillagerRenderer(
            EntityRendererProvider.Context context,
            ModelLayerLocation vanillaLayer,
            VillagerPoseProvider<T> poseProvider,
            ResourceLocation vanillaTexture,
            ResourceLocation combatTexture,
            boolean useCombatModelForAllPoses,
            boolean useVanillaCemModelForDefaultPose,
            boolean allowConfiguredRenderModes
    ) {
        this(context, vanillaLayer, poseProvider, vanillaTexture, combatTexture,
                useCombatModelForAllPoses, useVanillaCemModelForDefaultPose);
        this.allowConfiguredRenderModes = allowConfiguredRenderModes;
    }

    @Override
    public void render(T villager, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        this.model = switch (this.currentRenderMode()) {
            case VR_DEFAULT -> shouldUseCombatTextureAndModel(villager, this.getAttackAnim(villager, partialTick)) ? this.combatModel : this.nonCombatModel;
            case HUMANOID_COMPAT -> this.humanoidModel;
            case PACK_NATIVE -> this.vanillaModel;
        };
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

    private boolean shouldUseCombatTextureAndModel(T villager, float attackTime) {
        boolean needsSideArmModel = VillagerDownedClientCache.isDowned(villager)
                || this.poseProvider.shouldUseCombatModel(villager)
                || attackTime > 0.0F
                || VillagerRenderEquipmentState.hasArmorEquipped(villager)
                || !VillagerRenderEquipmentState.visibleMainHand(villager).isEmpty()
                || !villager.getOffhandItem().isEmpty()
                || VillagerNameClientCache.isHired(villager.getId());
        if (needsSideArmModel) {
            return true;
        }

        return this.useCombatModelForAllPoses && !this.shouldPreferVanillaCemDefaultPose();
    }

    private VillagerRenderMode currentRenderMode() {
        return this.allowConfiguredRenderModes
                ? VillagerRetaliationConfig.VILLAGER_RENDER_MODE.get() : VillagerRenderMode.VR_DEFAULT;
    }

    private boolean shouldPreferVanillaCemDefaultPose() {
        return this.preferVanillaCemDefaultPose;
    }

    private void reloadCombatModel() {
        this.combatModel = new VillagerRetaliationVillagerModel<>(
                VillagerRetaliationEntityModelLoader.loadCombatVillagerModel(this.context),
                this.poseProvider
        );
    }

    private void reloadHumanoidModel() {
        this.humanoidModel = new HumanoidCompatVillagerModel<>(
                VillagerRetaliationEntityModelLoader.loadHumanoidVillagerModel(this.context),
                this.poseProvider,
                VillagerRetaliationEntityModelLoader.shouldUseHumanoidFreshAnimationProfile(this.context.getResourceManager())
        );
    }

    private void reloadNonCombatModel() {
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
