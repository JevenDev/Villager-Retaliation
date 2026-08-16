package com.jvn.villagerretaliation.client.quest;

import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import com.jvn.villagerretaliation.client.config.VillagerRetaliationServerConfigClient;
import com.jvn.villagerretaliation.client.villager.VillagerModelPreviewRenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.joml.Matrix4f;

public final class VillagerQuestIndicatorRenderer {
    private static final double MAX_RENDER_DISTANCE = 64.0D;
    private static final float PIXEL_SCALE = 0.025F;
    private static final float TEXTURE_WIDTH = 6.0F;
    private static final float TEXTURE_HEIGHT = 13.0F;
    private static final int FRAME_TICKS = 6;
    private static final double BOUNCE_HEIGHT = 0.08D;
    private static final double NAME_TAG_CLEARANCE = 0.70D;

    private VillagerQuestIndicatorRenderer() {
    }

    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (!(event.getEntity() instanceof Villager villager)
                || VillagerModelPreviewRenderContext.isRendering(villager)
                || !VillagerRetaliationServerConfigClient.showQuestIndicators()
                || !VillagerQuestTrackerOverlay.hasQuestIndicator(villager.getUUID())) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || minecraft.options.hideGui
                || villager.isInvisibleTo(minecraft.player)
                || minecraft.player.distanceToSqr(villager) > MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE) {
            return;
        }

        double bounce = ((villager.level().getGameTime() / FRAME_TICKS) & 1L) == 0L
                ? 0.0D
                : BOUNCE_HEIGHT;
        Vec3 nameTagAttachment = villager.getAttachments().getNullable(
                EntityAttachment.NAME_TAG,
                0,
                villager.getViewYRot(event.getPartialTick()));
        double anchorX = nameTagAttachment == null ? 0.0D : nameTagAttachment.x;
        double anchorY = nameTagAttachment == null ? villager.getBbHeight() : nameTagAttachment.y;
        double anchorZ = nameTagAttachment == null ? 0.0D : nameTagAttachment.z;
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(anchorX, anchorY + NAME_TAG_CLEARANCE + bounce, anchorZ);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(PIXEL_SCALE, -PIXEL_SCALE, PIXEL_SCALE);

        Matrix4f pose = poseStack.last().pose();
        VertexConsumer consumer = event.getMultiBufferSource().getBuffer(
                RenderType.entityCutoutNoCull(VillagerRetaliationClientAssets.QUEST_JOURNAL_ICON_UPDATE_TEXTURE));
        float halfWidth = TEXTURE_WIDTH / 2.0F;
        float halfHeight = TEXTURE_HEIGHT / 2.0F;
        vertex(consumer, pose, -halfWidth, halfHeight, 0.0F, 1.0F);
        vertex(consumer, pose, halfWidth, halfHeight, 1.0F, 1.0F);
        vertex(consumer, pose, halfWidth, -halfHeight, 1.0F, 0.0F);
        vertex(consumer, pose, -halfWidth, -halfHeight, 0.0F, 0.0F);
        poseStack.popPose();
    }

    private static void vertex(
            VertexConsumer consumer,
            Matrix4f pose,
            float x,
            float y,
            float u,
            float v) {
        consumer.addVertex(pose, x, y, 0.0F)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0.0F, 0.0F, 1.0F);
    }
}
