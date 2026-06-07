package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.inventory.AssignedStorageService.StoragePosition;
import com.jvn.villagerretaliation.item.HiredStorageClipboardItem;
import com.jvn.villagerretaliation.item.HiredStorageClipboardItem.ClipboardMode;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import com.jvn.villagerretaliation.network.ClipboardAssignedStorageSyncPayload;
import com.jvn.villagerretaliation.network.ClipboardWorkAreaEntry;
import com.jvn.villagerretaliation.network.ClipboardWorkAreaSyncPayload;
import com.jvn.villagerretaliation.network.HiredDebugPreviewSyncPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

public final class ClipboardStorageOutlineRenderer {
    private static final int SELECTED_COLOR = 0xFF3FA7FF;
    private static final int ASSIGNED_COLOR = 0xFFFFD54A;
    private static final int PAYMENT_COLOR = 0xFF46E06E;
    private static final int WORK_AREA_COLOR = 0xFF65D889;
    private static final int WORK_AREA_CENTER_COLOR = 0xFFFFF176;
    private static final int WORK_AREA_CORNER_COLOR = 0xFFFF8A65;
    private static final List<OutlinedStoragePosition> ASSIGNED_POSITIONS = new ArrayList<>();
    private static final List<WorkAreaPosition> WORK_AREAS = new ArrayList<>();
    private static final List<OutlinedStoragePosition> DEBUG_ASSIGNED_POSITIONS = new ArrayList<>();
    private static final List<WorkAreaPosition> DEBUG_WORK_AREAS = new ArrayList<>();
    private static long assignedVisibleUntilGameTime;
    private static long workAreasVisibleUntilGameTime;
    private static long debugPreviewVisibleUntilGameTime;
    private static boolean debugPreviewEnabled;

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
                ASSIGNED_POSITIONS.add(new OutlinedStoragePosition(
                        ResourceKey.create(Registries.DIMENSION, entry.dimension()),
                        entry.pos(),
                        entry.payment(),
                        ""
                ));
            }
            assignedVisibleUntilGameTime = minecraft.level.getGameTime() + payload.ticks();
        });
    }

    public static void accept(ClipboardWorkAreaSyncPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            WORK_AREAS.clear();
            if (minecraft.level == null) {
                workAreasVisibleUntilGameTime = 0L;
                return;
            }
            for (ClipboardWorkAreaEntry entry : payload.entries()) {
                WORK_AREAS.add(new WorkAreaPosition(
                        ResourceKey.create(Registries.DIMENSION, entry.dimension()),
                        entry.min(),
                        entry.max(),
                        entry.center(),
                        entry.showCenter(),
                        entry.firstCorner(),
                        entry.showFirstCorner(),
                        entry.secondCorner(),
                        entry.showSecondCorner(),
                        "",
                        ""
                ));
            }
            workAreasVisibleUntilGameTime = minecraft.level.getGameTime() + payload.ticks();
        });
    }

    public static void acceptDebugPreview(HiredDebugPreviewSyncPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            DEBUG_WORK_AREAS.clear();
            DEBUG_ASSIGNED_POSITIONS.clear();
            debugPreviewEnabled = payload.enabled();
            if (minecraft.level == null || !payload.enabled()) {
                debugPreviewVisibleUntilGameTime = 0L;
                return;
            }
            for (HiredDebugPreviewSyncPayload.WorkAreaEntry entry : payload.workAreas()) {
                DEBUG_WORK_AREAS.add(new WorkAreaPosition(
                        ResourceKey.create(Registries.DIMENSION, entry.dimension()),
                        entry.min(),
                        entry.max(),
                        entry.center(),
                        entry.showCenter(),
                        entry.firstCorner(),
                        entry.showFirstCorner(),
                        entry.secondCorner(),
                        entry.showSecondCorner(),
                        entry.ownerName(),
                        entry.jobName()
                ));
            }
            for (HiredDebugPreviewSyncPayload.StorageEntry entry : payload.storage()) {
                DEBUG_ASSIGNED_POSITIONS.add(new OutlinedStoragePosition(
                        ResourceKey.create(Registries.DIMENSION, entry.dimension()),
                        entry.pos(),
                        entry.payment(),
                        entry.ownerName()
                ));
            }
            debugPreviewVisibleUntilGameTime = minecraft.level.getGameTime() + payload.ticks();
        });
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            ASSIGNED_POSITIONS.clear();
            DEBUG_ASSIGNED_POSITIONS.clear();
            DEBUG_WORK_AREAS.clear();
            debugPreviewEnabled = false;
            return;
        }

        renderDebugPreview(event, minecraft);

        if (!isHoldingClipboard(minecraft)) {
            ASSIGNED_POSITIONS.clear();
            return;
        }

        ItemStack clipboard = clipboardStack(minecraft);
        ClipboardMode mode = HiredStorageClipboardItem.mode(clipboard);
        if (mode == ClipboardMode.ASSIGN_STORAGE || mode == ClipboardMode.ASSIGN_PAYMENT) {
            List<StoragePosition> selected = HiredStorageClipboardItem.selectedContainers(clipboard);
            renderPositions(event, selected, mode == ClipboardMode.ASSIGN_PAYMENT ? PAYMENT_COLOR : SELECTED_COLOR);
            if (minecraft.level.getGameTime() <= assignedVisibleUntilGameTime) {
                renderAssignedPositions(event, ASSIGNED_POSITIONS);
            } else {
                ASSIGNED_POSITIONS.clear();
            }
            return;
        }

        if (mode == ClipboardMode.WORK_AREA || mode == ClipboardMode.SET_WORK_AREA) {
            if (minecraft.level.getGameTime() <= workAreasVisibleUntilGameTime) {
                renderWorkAreas(event, WORK_AREAS, WORK_AREA_COLOR);
            } else {
                WORK_AREAS.clear();
            }
            if (minecraft.level.getGameTime() <= assignedVisibleUntilGameTime) {
                renderAssignedPositions(event, ASSIGNED_POSITIONS);
            } else {
                ASSIGNED_POSITIONS.clear();
            }
        }
    }

    private static void renderDebugPreview(RenderLevelStageEvent event, Minecraft minecraft) {
        if (!debugPreviewEnabled) {
            return;
        }
        if (minecraft.level.getGameTime() > debugPreviewVisibleUntilGameTime) {
            DEBUG_WORK_AREAS.clear();
            DEBUG_ASSIGNED_POSITIONS.clear();
            debugPreviewEnabled = false;
            return;
        }
        renderWorkAreas(event, DEBUG_WORK_AREAS, WORK_AREA_COLOR);
        renderAssignedPositions(event, DEBUG_ASSIGNED_POSITIONS);
        renderDebugLabels(event, DEBUG_WORK_AREAS, DEBUG_ASSIGNED_POSITIONS);
    }

    private static void renderDebugLabels(
            RenderLevelStageEvent event,
            List<WorkAreaPosition> workAreas,
            List<OutlinedStoragePosition> storagePositions) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        List<DebugLabelPosition> labels = new ArrayList<>();
        ResourceKey<Level> currentDimension = minecraft.level.dimension();
        for (WorkAreaPosition area : workAreas) {
            if (!area.dimension().equals(currentDimension)
                    || area.ownerName().isBlank()
                    || !minecraft.level.hasChunkAt(area.center())) {
                continue;
            }
            labels.add(new DebugLabelPosition(
                    new Vec3(area.center().getX() + 0.5D, area.max().getY() + 1.35D, area.center().getZ() + 0.5D),
                    area.ownerName(),
                    area.jobName()
            ));
        }
        for (OutlinedStoragePosition position : storagePositions) {
            if (!position.dimension().equals(currentDimension)
                    || position.ownerName().isBlank()
                    || !minecraft.level.hasChunkAt(position.pos())) {
                continue;
            }
            labels.add(new DebugLabelPosition(
                    new Vec3(position.pos().getX() + 0.5D, position.pos().getY() + 1.25D, position.pos().getZ() + 0.5D),
                    position.ownerName(),
                    ""
            ));
        }
        renderLabels(event, labels);
    }

    private static void renderLabels(RenderLevelStageEvent event, List<DebugLabelPosition> labels) {
        Minecraft minecraft = Minecraft.getInstance();
        if (labels.isEmpty()) {
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        Font font = minecraft.font;
        int background = ((int) (minecraft.options.getBackgroundOpacity(0.25F) * 255.0F)) << 24;
        for (DebugLabelPosition label : labels) {
            poseStack.pushPose();
            poseStack.translate(label.pos().x - camera.x, label.pos().y - camera.y, label.pos().z - camera.z);
            poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
            poseStack.scale(0.025F, -0.025F, 0.025F);
            Matrix4f pose = poseStack.last().pose();
            renderLabelLine(font, bufferSource, pose, label.ownerName(), 0.0F, background);
            if (!label.jobName().isBlank()) {
                renderLabelLine(font, bufferSource, pose, label.jobName(), font.lineHeight + 1.0F, background);
            }
            poseStack.popPose();
        }
        bufferSource.endBatch();
    }

    private static void renderLabelLine(
            Font font,
            MultiBufferSource bufferSource,
            Matrix4f pose,
            String text,
            float y,
            int background) {
        Component component = Component.literal(text);
        float x = -font.width(component) / 2.0F;
        font.drawInBatch(component, x, y, 0xFFDDDDDD, false, pose, bufferSource, Font.DisplayMode.SEE_THROUGH, background, 15728880);
        font.drawInBatch(component, x, y, 0xFFFFFFFF, false, pose, bufferSource, Font.DisplayMode.NORMAL, 0, 15728880);
    }

    private static void renderAssignedPositions(RenderLevelStageEvent event, List<OutlinedStoragePosition> positions) {
        List<StoragePosition> normal = new ArrayList<>();
        List<StoragePosition> payment = new ArrayList<>();
        for (OutlinedStoragePosition position : positions) {
            StoragePosition storagePosition = new StoragePosition(position.dimension(), position.pos());
            if (position.payment()) {
                payment.add(storagePosition);
            } else {
                normal.add(storagePosition);
            }
        }
        renderPositions(event, normal, ASSIGNED_COLOR);
        renderPositions(event, payment, PAYMENT_COLOR);
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

    private static void renderWorkAreas(RenderLevelStageEvent event, List<WorkAreaPosition> areas, int color) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || areas.isEmpty()) {
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
        for (WorkAreaPosition area : areas) {
            if (!area.dimension().equals(currentDimension) || !minecraft.level.hasChunkAt(area.min()) || !minecraft.level.hasChunkAt(area.max())) {
                continue;
            }
            LevelRenderer.renderLineBox(poseStack, consumer, workAreaBox(area), red, green, blue, alpha);
            if (area.showCenter() && minecraft.level.hasChunkAt(area.center())) {
                renderColoredBox(poseStack, consumer, markerBox(area.center()), WORK_AREA_CENTER_COLOR);
            }
            if (area.showFirstCorner() && minecraft.level.hasChunkAt(area.firstCorner())) {
                renderColoredBox(poseStack, consumer, markerBox(area.firstCorner()), WORK_AREA_CORNER_COLOR);
            }
            if (area.showSecondCorner() && minecraft.level.hasChunkAt(area.secondCorner())) {
                renderColoredBox(poseStack, consumer, markerBox(area.secondCorner()), WORK_AREA_CORNER_COLOR);
            }
        }
        poseStack.popPose();
        bufferSource.endBatch(RenderType.lines());
    }

    private static AABB workAreaBox(WorkAreaPosition area) {
        BlockPos min = area.min();
        BlockPos max = area.max();
        return new AABB(
                min.getX(),
                min.getY(),
                min.getZ(),
                max.getX() + 1.0D,
                max.getY() + 1.0D,
                max.getZ() + 1.0D
        ).inflate(0.003D);
    }

    private static AABB markerBox(BlockPos pos) {
        return new AABB(pos).inflate(0.01D);
    }

    private static void renderColoredBox(PoseStack poseStack, VertexConsumer consumer, AABB box, int color) {
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float alpha = ((color >> 24) & 0xFF) / 255.0F;
        LevelRenderer.renderLineBox(poseStack, consumer, box, red, green, blue, alpha);
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

    private record WorkAreaPosition(
            ResourceKey<Level> dimension,
            BlockPos min,
            BlockPos max,
            BlockPos center,
            boolean showCenter,
            BlockPos firstCorner,
            boolean showFirstCorner,
            BlockPos secondCorner,
            boolean showSecondCorner,
            String ownerName,
            String jobName) {
    }

    private record OutlinedStoragePosition(ResourceKey<Level> dimension, BlockPos pos, boolean payment, String ownerName) {
    }

    private record DebugLabelPosition(Vec3 pos, String ownerName, String jobName) {
    }
}
