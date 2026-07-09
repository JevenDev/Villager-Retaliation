package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.inventory.AssignedStorageService.StoragePosition;
import com.jvn.villagerretaliation.interaction.HiredRoute;
import com.jvn.villagerretaliation.interaction.HiredWorkArea;
import com.jvn.villagerretaliation.item.HiredStorageClipboardItem;
import com.jvn.villagerretaliation.item.HiredStorageClipboardItem.ClipboardMode;
import com.jvn.villagerretaliation.item.HiredStorageClipboardItem.RouteDraft;
import com.jvn.villagerretaliation.item.HiredStorageClipboardItem.WorkAreaDraft;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import com.jvn.villagerretaliation.network.ClipboardAssignedStorageSyncPayload;
import com.jvn.villagerretaliation.network.ClipboardRouteEntry;
import com.jvn.villagerretaliation.network.ClipboardRouteSyncPayload;
import com.jvn.villagerretaliation.network.ClipboardWorkAreaEntry;
import com.jvn.villagerretaliation.network.ClipboardWorkAreaSyncPayload;
import com.jvn.villagerretaliation.network.HiredDebugPreviewSyncPayload;
import com.jvn.villagerretaliation.network.HiredHitboxDebugPreviewPayload;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

public final class ClipboardStorageOutlineRenderer {
    private static final int SELECTED_COLOR = 0xFF3FA7FF;
    private static final int ASSIGNED_COLOR = 0xFFFFD54A;
    private static final int PAYMENT_COLOR = 0xFF3FA7FF;
    private static final int WORK_AREA_COLOR = 0xFFFF4A3F;
    private static final int WORK_AREA_CENTER_COLOR = 0xFFFFF176;
    private static final int WORK_AREA_CORNER_COLOR = 0xFFFF8A65;
    private static final int ROUTE_COLOR = 0xFF40E0C8;
    private static final int ROUTE_NODE_COLOR = 0xFF80FFF0;
    private static final int ROUTE_FIRST_COLOR = 0xFF7CFF6B;
    private static final int ROUTE_LAST_COLOR = 0xFFFFB74D;
    private static final int ROUTE_LOOP_COLOR = 0xFFFFF176;
    private static final int ROUTE_INVALID_COLOR = 0xFFFF4A4A;
    private static final List<OutlinedStoragePosition> ASSIGNED_POSITIONS = new ArrayList<>();
    private static final List<WorkAreaPosition> WORK_AREAS = new ArrayList<>();
    private static final List<RoutePosition> ROUTES = new ArrayList<>();
    private static final List<OutlinedStoragePosition> DEBUG_ASSIGNED_POSITIONS = new ArrayList<>();
    private static final List<WorkAreaPosition> DEBUG_WORK_AREAS = new ArrayList<>();
    private static final List<RoutePosition> DEBUG_ROUTES = new ArrayList<>();
    private static long assignedVisibleUntilGameTime;
    private static long workAreasVisibleUntilGameTime;
    private static long routesVisibleUntilGameTime;
    private static long debugPreviewVisibleUntilGameTime;
    private static long nextHitboxDebugPreviewPingGameTime;
    private static boolean debugPreviewEnabled;
    private static boolean hitboxDebugPreviewSent;
    private static boolean nearbyWorkAreaPreviewsEnabled;
    private static boolean nearbyStoragePreviewsEnabled;
    private static boolean nearbyPaymentPreviewsEnabled;

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
                        "",
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

    public static void accept(ClipboardRouteSyncPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            ROUTES.clear();
            if (minecraft.level == null) {
                routesVisibleUntilGameTime = 0L;
                return;
            }
            for (ClipboardRouteEntry entry : payload.entries()) {
                ROUTES.add(routePosition(entry));
            }
            routesVisibleUntilGameTime = minecraft.level.getGameTime() + payload.ticks();
        });
    }

    public static void acceptDebugPreview(HiredDebugPreviewSyncPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            DEBUG_WORK_AREAS.clear();
            DEBUG_ASSIGNED_POSITIONS.clear();
            DEBUG_ROUTES.clear();
            debugPreviewEnabled = payload.enabled();
            if (minecraft.level == null || !payload.enabled()) {
                debugPreviewVisibleUntilGameTime = 0L;
                nearbyWorkAreaPreviewsEnabled = false;
                nearbyStoragePreviewsEnabled = false;
                nearbyPaymentPreviewsEnabled = false;
                return;
            }
            if (!anyNearbyPreviewEnabled()) {
                nearbyWorkAreaPreviewsEnabled = true;
                nearbyStoragePreviewsEnabled = true;
                nearbyPaymentPreviewsEnabled = true;
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
                        entry.ownerName(),
                        entry.storageType()
                ));
            }
            for (ClipboardRouteEntry entry : payload.routes()) {
                DEBUG_ROUTES.add(routePosition(entry));
            }
            debugPreviewVisibleUntilGameTime = minecraft.level.getGameTime() + payload.ticks();
        });
    }

    private static RoutePosition routePosition(ClipboardRouteEntry entry) {
        return new RoutePosition(
                ResourceKey.create(Registries.DIMENSION, entry.dimension()),
                entry.nodes(),
                entry.loop(),
                entry.ownerName(),
                entry.jobName()
        );
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
            DEBUG_ROUTES.clear();
            debugPreviewEnabled = false;
            hitboxDebugPreviewSent = false;
            nearbyWorkAreaPreviewsEnabled = false;
            nearbyStoragePreviewsEnabled = false;
            nearbyPaymentPreviewsEnabled = false;
            return;
        }

        renderDebugPreview(event, minecraft);

        if (!isHoldingClipboard(minecraft)) {
            ASSIGNED_POSITIONS.clear();
            ROUTES.clear();
            return;
        }

        ItemStack clipboard = clipboardStack(minecraft);
        ClipboardMode mode = HiredStorageClipboardItem.mode(clipboard);
        if (mode.isStorageAssignmentMode() || mode == ClipboardMode.ASSIGN_PAYMENT) {
            List<StoragePosition> selected = HiredStorageClipboardItem.selectedContainers(clipboard);
            renderPositions(event, selected, mode == ClipboardMode.ASSIGN_PAYMENT ? PAYMENT_COLOR : SELECTED_COLOR);
            if (minecraft.level.getGameTime() <= assignedVisibleUntilGameTime) {
                renderAssignedPositions(event, ASSIGNED_POSITIONS);
            } else {
                ASSIGNED_POSITIONS.clear();
            }
            return;
        }

        if (mode == ClipboardMode.ROUTE) {
            boolean renderedHeldDraft = renderHeldRouteDraft(event, clipboard);
            renderRoutePlacementPreview(event, clipboard);
            if (!renderedHeldDraft && minecraft.level.getGameTime() <= routesVisibleUntilGameTime) {
                renderRoutes(event, ROUTES, ROUTE_COLOR);
                renderRouteLabels(event, ROUTES, false, false);
            } else if (minecraft.level.getGameTime() > routesVisibleUntilGameTime) {
                ROUTES.clear();
            }
            return;
        }

        if (mode == ClipboardMode.WORK_AREA || mode == ClipboardMode.SET_WORK_AREA) {
            boolean renderedHeldDraft = mode == ClipboardMode.SET_WORK_AREA && renderHeldWorkAreaDraft(event, clipboard);
            if (!renderedHeldDraft && minecraft.level.getGameTime() <= workAreasVisibleUntilGameTime) {
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

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            hitboxDebugPreviewSent = false;
            nextHitboxDebugPreviewPingGameTime = 0L;
            return;
        }
        boolean debugVisible = minecraft.getEntityRenderDispatcher().shouldRenderHitBoxes();
        long gameTime = minecraft.level.getGameTime();
        if (debugVisible) {
            if (!hitboxDebugPreviewSent || gameTime >= nextHitboxDebugPreviewPingGameTime) {
                PacketDistributor.sendToServer(new HiredHitboxDebugPreviewPayload(true));
                hitboxDebugPreviewSent = true;
                nextHitboxDebugPreviewPingGameTime = gameTime + 40L;
            }
            return;
        }
        if (hitboxDebugPreviewSent) {
            PacketDistributor.sendToServer(new HiredHitboxDebugPreviewPayload(false));
            hitboxDebugPreviewSent = false;
            nextHitboxDebugPreviewPingGameTime = 0L;
        }
    }

    private static boolean renderHeldWorkAreaDraft(RenderLevelStageEvent event, ItemStack clipboard) {
        WorkAreaDraft draft = HiredStorageClipboardItem.selectedWorkArea(clipboard);
        if (draft.dimension() == null || draft.first() == null && draft.second() == null) {
            return false;
        }
        BlockPos first = draft.first() == null ? draft.second() : draft.first();
        BlockPos second = draft.second() == null ? first : draft.second();
        BlockPos min = HiredWorkArea.minPos(first, second);
        BlockPos max = HiredWorkArea.maxPos(first, second);
        BlockPos center = HiredWorkArea.centerPos(min, max);
        renderWorkAreas(event, List.of(new WorkAreaPosition(
                draft.dimension(),
                min,
                max,
                center,
                draft.complete(),
                draft.first() == null ? first : draft.first(),
                draft.first() != null,
                draft.second() == null ? second : draft.second(),
                draft.second() != null,
                "",
                "")), WORK_AREA_COLOR);
        return true;
    }

    private static boolean renderHeldRouteDraft(RenderLevelStageEvent event, ItemStack clipboard) {
        RouteDraft draft = HiredStorageClipboardItem.selectedRoute(clipboard);
        if (draft.isEmpty()) {
            return false;
        }
        RoutePosition route = new RoutePosition(draft.dimension(), draft.route().nodes(), draft.route().loop(), "", "");
        renderRoutes(event, List.of(route), ROUTE_COLOR);
        renderRouteLabels(event, List.of(route), true, false);
        return true;
    }

    private static void renderRoutePlacementPreview(RenderLevelStageEvent event, ItemStack clipboard) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !(minecraft.hitResult instanceof BlockHitResult hitResult)
                || hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }
        RoutePreview preview = routePreview(minecraft, clipboard, hitResult.getBlockPos());
        if (preview == null) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());
        int color = preview.valid() ? ROUTE_LOOP_COLOR : ROUTE_INVALID_COLOR;
        renderColoredBox(poseStack, consumer, markerBox(preview.target()), color);
        if (preview.from() != null) {
            renderRouteLine(poseStack, consumer, preview.from(), preview.target(), color);
        }
        poseStack.popPose();
        bufferSource.endBatch(RenderType.lines());
    }

    private static RoutePreview routePreview(Minecraft minecraft, ItemStack clipboard, BlockPos target) {
        RouteDraft draft = HiredStorageClipboardItem.selectedRoute(clipboard);
        BlockPos safeTarget = target.immutable();
        if (draft.isEmpty()) {
            return new RoutePreview(safeTarget, null, true);
        }
        if (!minecraft.level.dimension().equals(draft.dimension())) {
            return new RoutePreview(safeTarget, null, false);
        }
        HiredRoute route = draft.route();
        List<BlockPos> nodes = route.nodes();
        if (nodes.isEmpty()) {
            return new RoutePreview(safeTarget, null, true);
        }
        if (safeTarget.equals(nodes.getFirst()) && nodes.size() >= 2) {
            return new RoutePreview(safeTarget, nodes.getLast(), route.loop() || HiredRoute.canConnect(nodes.getLast(), nodes.getFirst()));
        }
        if (route.contains(safeTarget) || route.loop() || nodes.size() >= HiredRoute.MAX_NODES) {
            return new RoutePreview(safeTarget, nodes.getLast(), false);
        }
        return new RoutePreview(safeTarget, nodes.getLast(), HiredRoute.canConnect(nodes.getLast(), safeTarget));
    }

    private static void renderDebugPreview(RenderLevelStageEvent event, Minecraft minecraft) {
        if (!debugPreviewEnabled || !anyNearbyPreviewEnabled()) {
            return;
        }
        if (minecraft.level.getGameTime() > debugPreviewVisibleUntilGameTime) {
            DEBUG_WORK_AREAS.clear();
            DEBUG_ASSIGNED_POSITIONS.clear();
            DEBUG_ROUTES.clear();
            debugPreviewEnabled = false;
            nearbyWorkAreaPreviewsEnabled = false;
            nearbyStoragePreviewsEnabled = false;
            nearbyPaymentPreviewsEnabled = false;
            return;
        }
        if (nearbyWorkAreaPreviewsEnabled) {
            renderWorkAreas(event, DEBUG_WORK_AREAS, WORK_AREA_COLOR);
            renderRoutes(event, DEBUG_ROUTES, ROUTE_COLOR);
        }
        renderAssignedPositions(event, DEBUG_ASSIGNED_POSITIONS, nearbyStoragePreviewsEnabled, nearbyPaymentPreviewsEnabled);
        renderDebugLabels(
                event,
                nearbyWorkAreaPreviewsEnabled ? DEBUG_WORK_AREAS : List.of(),
                nearbyWorkAreaPreviewsEnabled ? DEBUG_ROUTES : List.of(),
                DEBUG_ASSIGNED_POSITIONS,
                nearbyStoragePreviewsEnabled,
                nearbyPaymentPreviewsEnabled);
    }

    public static boolean toggleNearbyWorkAreaPreviews() {
        nearbyWorkAreaPreviewsEnabled = !nearbyWorkAreaPreviewsEnabled;
        return nearbyWorkAreaPreviewsEnabled;
    }

    public static boolean toggleNearbyStoragePreviews() {
        nearbyStoragePreviewsEnabled = !nearbyStoragePreviewsEnabled;
        return nearbyStoragePreviewsEnabled;
    }

    public static boolean toggleNearbyPaymentPreviews() {
        nearbyPaymentPreviewsEnabled = !nearbyPaymentPreviewsEnabled;
        return nearbyPaymentPreviewsEnabled;
    }

    public static boolean nearbyWorkAreaPreviewsEnabled() {
        return nearbyWorkAreaPreviewsEnabled;
    }

    public static boolean nearbyStoragePreviewsEnabled() {
        return nearbyStoragePreviewsEnabled;
    }

    public static boolean nearbyPaymentPreviewsEnabled() {
        return nearbyPaymentPreviewsEnabled;
    }

    public static boolean anyNearbyPreviewEnabled() {
        return nearbyWorkAreaPreviewsEnabled || nearbyStoragePreviewsEnabled || nearbyPaymentPreviewsEnabled;
    }

    private static void renderDebugLabels(
            RenderLevelStageEvent event,
            List<WorkAreaPosition> workAreas,
            List<RoutePosition> routes,
            List<OutlinedStoragePosition> storagePositions,
            boolean includeNormalStorage,
            boolean includePaymentStorage) {
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
                    area.jobName(),
                    WORK_AREA_COLOR
            ));
        }
        addRouteOwnerLabels(minecraft, currentDimension, routes, labels);
        for (OutlinedStoragePosition position : storagePositions) {
            if (!position.dimension().equals(currentDimension)
                    || position.payment() && !includePaymentStorage
                    || !position.payment() && !includeNormalStorage
                    || position.ownerName().isBlank()
                    || !minecraft.level.hasChunkAt(position.pos())) {
                continue;
            }
            labels.add(new DebugLabelPosition(
                    new Vec3(position.pos().getX() + 0.5D, position.pos().getY() + 1.25D, position.pos().getZ() + 0.5D),
                    position.ownerName(),
                    position.storageType(),
                    position.payment() ? PAYMENT_COLOR : ASSIGNED_COLOR
            ));
        }
        renderLabels(event, labels);
    }

    private static void renderRouteLabels(
            RenderLevelStageEvent event,
            List<RoutePosition> routes,
            boolean includeNodeNumbers,
            boolean includeOwnerLabels) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || routes.isEmpty()) {
            return;
        }
        List<DebugLabelPosition> labels = new ArrayList<>();
        ResourceKey<Level> currentDimension = minecraft.level.dimension();
        if (includeNodeNumbers) {
            for (RoutePosition route : routes) {
                if (!route.dimension().equals(currentDimension)) {
                    continue;
                }
                for (int index = 0; index < route.nodes().size(); index++) {
                    BlockPos node = route.nodes().get(index);
                    if (!minecraft.level.hasChunkAt(node)) {
                        continue;
                    }
                    labels.add(new DebugLabelPosition(
                            new Vec3(node.getX() + 0.5D, node.getY() + 1.35D, node.getZ() + 0.5D),
                            Integer.toString(index + 1),
                            "",
                            routeNodeColor(route, index)
                    ));
                }
            }
        }
        if (includeOwnerLabels) {
            addRouteOwnerLabels(minecraft, currentDimension, routes, labels);
        }
        renderLabels(event, labels);
    }

    private static void addRouteOwnerLabels(
            Minecraft minecraft,
            ResourceKey<Level> currentDimension,
            List<RoutePosition> routes,
            List<DebugLabelPosition> labels) {
        for (RoutePosition route : routes) {
            if (!route.dimension().equals(currentDimension)
                    || route.ownerName().isBlank()
                    || route.nodes().isEmpty()
                    || !minecraft.level.hasChunkAt(route.nodes().getFirst())) {
                continue;
            }
            BlockPos labelPos = route.nodes().getFirst();
            labels.add(new DebugLabelPosition(
                    new Vec3(labelPos.getX() + 0.5D, labelPos.getY() + 1.75D, labelPos.getZ() + 0.5D),
                    route.ownerName(),
                    route.jobName().isBlank() ? routeDescription(route) : route.jobName() + " route",
                    ROUTE_COLOR
            ));
        }
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
            renderLabelLine(font, bufferSource, pose, label.ownerName(), 0.0F, background, label.color());
            if (!label.jobName().isBlank()) {
                renderLabelLine(font, bufferSource, pose, label.jobName(), font.lineHeight + 1.0F, background, label.color());
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
            int background,
            int color) {
        Component component = Component.literal(text);
        float x = -font.width(component) / 2.0F;
        font.drawInBatch(component, x, y, color, false, pose, bufferSource, Font.DisplayMode.SEE_THROUGH, background, 15728880);
        font.drawInBatch(component, x, y, color, false, pose, bufferSource, Font.DisplayMode.NORMAL, 0, 15728880);
    }

    private static void renderAssignedPositions(RenderLevelStageEvent event, List<OutlinedStoragePosition> positions) {
        renderAssignedPositions(event, positions, true, true);
    }

    private static void renderAssignedPositions(
            RenderLevelStageEvent event,
            List<OutlinedStoragePosition> positions,
            boolean includeNormalStorage,
            boolean includePaymentStorage) {
        renderOutlinedStoragePositions(event, positions, ASSIGNED_COLOR, includeNormalStorage, false);
        renderOutlinedStoragePositions(event, positions, PAYMENT_COLOR, false, includePaymentStorage);
    }

    private static void renderOutlinedStoragePositions(
            RenderLevelStageEvent event,
            List<OutlinedStoragePosition> positions,
            int color,
            boolean includeNormalStorage,
            boolean includePaymentStorage) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || positions.isEmpty() || !includeNormalStorage && !includePaymentStorage) {
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
        for (OutlinedStoragePosition position : positions) {
            if (position.payment() && !includePaymentStorage
                    || !position.payment() && !includeNormalStorage
                    || !position.dimension().equals(currentDimension)
                    || !minecraft.level.hasChunkAt(position.pos())) {
                continue;
            }
            LevelRenderer.renderLineBox(poseStack, consumer, outlineBox(minecraft.level, position.pos()), red, green, blue, alpha);
        }
        poseStack.popPose();
        bufferSource.endBatch(RenderType.lines());
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

    private static void renderRoutes(RenderLevelStageEvent event, List<RoutePosition> routes, int color) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || routes.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());
        ResourceKey<Level> currentDimension = minecraft.level.dimension();
        for (RoutePosition route : routes) {
            if (!route.dimension().equals(currentDimension) || route.nodes().isEmpty()) {
                continue;
            }
            for (int index = 0; index < route.nodes().size(); index++) {
                BlockPos node = route.nodes().get(index);
                if (minecraft.level.hasChunkAt(node)) {
                    renderColoredBox(poseStack, consumer, markerBox(node), routeNodeColor(route, index));
                }
                if (index > 0) {
                    renderRouteSegmentIfLoaded(minecraft, poseStack, consumer, route.nodes().get(index - 1), node, color);
                }
            }
            if (route.loop() && route.nodes().size() > 1) {
                renderRouteSegmentIfLoaded(minecraft, poseStack, consumer, route.nodes().getLast(), route.nodes().getFirst(), ROUTE_LOOP_COLOR);
            }
        }
        poseStack.popPose();
        bufferSource.endBatch(RenderType.lines());
    }

    private static void renderRouteSegmentIfLoaded(
            Minecraft minecraft,
            PoseStack poseStack,
            VertexConsumer consumer,
            BlockPos first,
            BlockPos second,
            int color) {
        if (minecraft.level.hasChunkAt(first) && minecraft.level.hasChunkAt(second)) {
            renderRouteLine(poseStack, consumer, first, second, color);
        }
    }

    private static void renderRouteLine(PoseStack poseStack, VertexConsumer consumer, BlockPos first, BlockPos second, int color) {
        Vec3 from = routeNodeCenter(first);
        Vec3 to = routeNodeCenter(second);
        Matrix4f pose = poseStack.last().pose();
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        int alpha = (color >> 24) & 0xFF;
        consumer.addVertex(pose, (float) from.x, (float) from.y, (float) from.z)
                .setColor(red, green, blue, alpha)
                .setNormal(0.0F, 1.0F, 0.0F);
        consumer.addVertex(pose, (float) to.x, (float) to.y, (float) to.z)
                .setColor(red, green, blue, alpha)
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    private static Vec3 routeNodeCenter(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5D, pos.getY() + 1.05D, pos.getZ() + 0.5D);
    }

    private static int routeNodeColor(RoutePosition route, int index) {
        if (route.loop() && index == 0) {
            return ROUTE_LOOP_COLOR;
        }
        if (index == 0) {
            return ROUTE_FIRST_COLOR;
        }
        if (!route.loop() && index == route.nodes().size() - 1) {
            return ROUTE_LAST_COLOR;
        }
        return ROUTE_NODE_COLOR;
    }

    private static String routeDescription(RoutePosition route) {
        int count = route.nodes().size();
        return count + " node" + (count == 1 ? "" : "s") + (route.loop() ? " loop" : " route");
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

    private record OutlinedStoragePosition(ResourceKey<Level> dimension, BlockPos pos, boolean payment, String ownerName, String storageType) {
    }

    private record RoutePosition(ResourceKey<Level> dimension, List<BlockPos> nodes, boolean loop, String ownerName, String jobName) {
        private RoutePosition {
            List<BlockPos> safeNodes = new ArrayList<>();
            if (nodes != null) {
                for (BlockPos node : nodes) {
                    if (node != null && safeNodes.size() < HiredRoute.MAX_NODES) {
                        safeNodes.add(node.immutable());
                    }
                }
            }
            nodes = List.copyOf(safeNodes);
            loop = loop && nodes.size() > 1 && HiredRoute.canConnect(nodes.getLast(), nodes.getFirst());
            ownerName = ownerName == null ? "" : ownerName;
            jobName = jobName == null ? "" : jobName;
        }
    }

    private record RoutePreview(BlockPos target, BlockPos from, boolean valid) {
    }

    private record DebugLabelPosition(Vec3 pos, String ownerName, String jobName, int color) {
    }
}
