package com.jvn.villagerretaliation.client.party;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class PartyQuickCommandTargetRenderer {
    private static final float RED = 0.66F;
    private static final float GREEN = 0.77F;
    private static final float BLUE = 0.82F;
    private static final float ALPHA = 1.0F;

    private PartyQuickCommandTargetRenderer() {
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        BlockPos target = PartyRosterClient.roster().quickCommandMoveTarget();
        if (minecraft.level == null
                || minecraft.player == null
                || target == null
                || PartyRosterClient.roster().quickCommandMoveDimension() == null
                || !PartyRosterClient.roster().quickCommandMoveDimension().equals(
                        minecraft.level.dimension().location())
                || !minecraft.level.hasChunkAt(target)) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(
                poseStack,
                consumer,
                outlineBox(minecraft, target),
                RED,
                GREEN,
                BLUE,
                ALPHA);
        poseStack.popPose();
        bufferSource.endBatch(RenderType.lines());
    }

    private static AABB outlineBox(Minecraft minecraft, BlockPos pos) {
        var state = minecraft.level.getBlockState(pos);
        if (!state.isAir()) {
            var shape = state.getShape(minecraft.level, pos);
            if (!shape.isEmpty()) {
                AABB bounds = shape.bounds();
                if (bounds.getSize() > 0.0D) {
                    return bounds.move(pos).inflate(0.003D);
                }
            }
        }
        return new AABB(pos).inflate(0.003D);
    }
}
