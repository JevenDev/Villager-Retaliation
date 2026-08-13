package com.jvn.villagerretaliation.client.renderer.layer;

import com.jvn.villagerretaliation.client.model.BaseVillagerModel;
import com.jvn.villagerretaliation.client.villager.VillagerStudyClientCache;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.BookModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.EnchantTableRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.Villager;

/**
 * Renders the vanilla enchanting book in villager model space
 */
public final class VillagerStudyBookLayer extends RenderLayer<Villager, BaseVillagerModel<Villager>> {
    private static final int PAGE_TURN_CYCLE_TICKS = 200;
    private static final int PAGE_TURN_DURATION_TICKS = 8;
    private static final int PAGE_TURN_EARLIEST_TICK = 40;
    private static final int PAGE_TURN_START_VARIANCE = 120;
    private final BookModel bookModel;

    public VillagerStudyBookLayer(
            RenderLayerParent<Villager, BaseVillagerModel<Villager>> renderer,
            EntityRendererProvider.Context context
    ) {
        super(renderer);
        this.bookModel = new BookModel(context.bakeLayer(ModelLayers.BOOK));
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            Villager villager,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch
    ) {
        if (!VillagerStudyClientCache.isActive(villager)) {
            return;
        }

        poseStack.pushPose();
        this.getParentModel().translateRoot(poseStack);
        poseStack.translate(0.0F, 0.43F, -0.6F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(0.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(135.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(0.78F, 0.78F, 0.78F);

        float pageFlip = pageFlip(villager, ageInTicks);
        float rightPage = Mth.clamp(Mth.frac(pageFlip + 0.25F) * 1.6F - 0.3F, 0.0F, 1.0F);
        float leftPage = Mth.clamp(Mth.frac(pageFlip + 0.75F) * 1.6F - 0.3F, 0.0F, 1.0F);
        this.bookModel.setupAnim(ageInTicks, rightPage, leftPage, 1.0F);
        VertexConsumer vertices =
                EnchantTableRenderer.BOOK_LOCATION.buffer(buffer, RenderType::entitySolid);
        this.bookModel.render(
                poseStack,
                vertices,
                packedLight,
                LivingEntityRenderer.getOverlayCoords(villager, 0.0F),
                -1);
        poseStack.popPose();
    }

    /**
     * Mirrors the enchanting-table renderer's accumulated flip value while spacing
     * changes into irregular-looking idle windows. Each actual turn is intentionally
     * short; between turns the two page meshes remain still.
     */
    private static float pageFlip(Villager villager, float ageInTicks) {
        int cycle = Math.max(0, Mth.floor(ageInTicks / PAGE_TURN_CYCLE_TICKS));
        float cycleTick = ageInTicks - cycle * PAGE_TURN_CYCLE_TICKS;
        long hash = villager.getUUID().getMostSignificantBits()
                ^ villager.getUUID().getLeastSignificantBits()
                ^ (cycle * 0x9E3779B97F4A7C15L);
        hash ^= hash >>> 30;
        hash *= 0xBF58476D1CE4E5B9L;
        hash ^= hash >>> 27;
        int turnStart = PAGE_TURN_EARLIEST_TICK
                + (int) Math.floorMod(hash, PAGE_TURN_START_VARIANCE);
        float progress = Mth.clamp(
                (cycleTick - turnStart) / PAGE_TURN_DURATION_TICKS,
                0.0F,
                1.0F);
        float easedProgress = progress * progress * (3.0F - 2.0F * progress);
        return cycle + easedProgress;
    }
}
