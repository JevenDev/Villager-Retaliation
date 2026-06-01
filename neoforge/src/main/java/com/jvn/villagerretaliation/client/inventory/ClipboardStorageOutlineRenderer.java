package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.inventory.AssignedStorageService.StoragePosition;
import com.jvn.villagerretaliation.item.HiredStorageClipboardItem;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import com.jvn.villagerretaliation.network.ClipboardAssignedStorageSyncPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class ClipboardStorageOutlineRenderer {
    private static final int SELECTED_COLOR = 0xFF3FA7FF;
    private static final int ASSIGNED_COLOR = 0xFFFFD54A;
    private static final List<StoragePosition> ASSIGNED_POSITIONS = new ArrayList<>();
    private static long assignedVisibleUntilGameTime;

    private ClipboardStorageOutlineRenderer() {
    }

    public static void accept(ClipboardAssignedStorageSyncPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            ASSIGNED_POSITIONS.clear();
            if (minecraft.level == null) {
                assignedVisibleUntilGameTime = 0L;
                return;
            }
            for (ClipboardAssignedStorageSyncPayload.Entry entry : payload.entries()) {
                ASSIGNED_POSITIONS.add(new StoragePosition(
                        ResourceKey.create(Registries.DIMENSION, entry.dimension()),
                        entry.pos()
                ));
            }
            assignedVisibleUntilGameTime = minecraft.level.getGameTime() + payload.ticks();
        });
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !isHoldingClipboard(minecraft)) {
            ASSIGNED_POSITIONS.clear();
            return;
        }

        ItemStack clipboard = clipboardStack(minecraft);
        List<StoragePosition> selected = HiredStorageClipboardItem.selectedContainers(clipboard);
        renderPositions(event, selected, SELECTED_COLOR);
        if (minecraft.level.getGameTime() <= assignedVisibleUntilGameTime) {
            renderPositions(event, ASSIGNED_POSITIONS, ASSIGNED_COLOR);
        } else {
            ASSIGNED_POSITIONS.clear();
        }
    }

    private static void renderPositions(RenderLevelStageEvent event, List<StoragePosition> positions, int color) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || positions.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float alpha = ((color >> 24) & 0xFF) / 255.0F;
        ResourceKey<Level> currentDimension = minecraft.level.dimension();
        for (StoragePosition position : positions) {
            if (!position.dimension().equals(currentDimension) || !minecraft.level.hasChunkAt(position.pos())) {
                continue;
            }
            AABB box = outlineBox(minecraft.level, position.pos());
            LevelRenderer.renderLineBox(poseStack, consumer, box, red, green, blue, alpha);
        }
        poseStack.popPose();
        bufferSource.endBatch(RenderType.lines());
    }

    private static AABB outlineBox(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.isAir()) {
            AABB bounds = state.getShape(level, pos).bounds();
            if (bounds.getSize() > 0.0D) {
                return bounds.move(pos).inflate(0.003D);
            }
        }
        return new AABB(pos).inflate(0.003D);
    }

    private static boolean isHoldingClipboard(Minecraft minecraft) {
        return VillagerRetaliationItems.isClipboard(minecraft.player.getMainHandItem())
                || VillagerRetaliationItems.isClipboard(minecraft.player.getOffhandItem());
    }

    private static ItemStack clipboardStack(Minecraft minecraft) {
        ItemStack mainHand = minecraft.player.getMainHandItem();
        return VillagerRetaliationItems.isClipboard(mainHand) ? mainHand : minecraft.player.getOffhandItem();
    }
}
