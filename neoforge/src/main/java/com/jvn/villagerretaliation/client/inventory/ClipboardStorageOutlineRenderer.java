package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.inventory.AssignedStorageService.StoragePosition;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceSnapshot.WorkerStatus;
import com.jvn.villagerretaliation.client.config.VillagerRetaliationClientPreferences;
import com.jvn.villagerretaliation.client.config.VillagerRetaliationServerConfigClient;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
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
import com.jvn.villagerretaliation.network.ClipboardPreviewMarkerSyncPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
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
    private static final int ROUTE_COLOR = 0xFF95CD41;
    private static final int ROUTE_NODE_COLOR = 0xFFA9E85D;
    private static final int ROUTE_FIRST_COLOR = 0xFF7CFF6B;
    private static final int ROUTE_LAST_COLOR = 0xFFC8F06A;
    private static final int ROUTE_LOOP_COLOR = 0xFFE1FF6E;
    private static final int ROUTE_INVALID_COLOR = 0xFFEA5C2B;
    private static final int ROUTE_REACH_COLOR = 0xFFFFA43A;
    private static final int PLAYER_ROUTE_COLOR = 0xFF55C8FF;
    private static final int PLAYER_ROUTE_NODE_COLOR = 0xFF68CEFA;
    private static final int PLAYER_ROUTE_FIRST_COLOR = 0xFF82DBFF;
    private static final int PLAYER_ROUTE_LAST_COLOR = 0xFFB0EAFF;
    private static final int PLAYER_ROUTE_LOOP_COLOR = 0xFF91E5FF;
    private static final int PLAYER_ROUTE_CREATION_COLOR = 0xFFFFA43A;
    private static final int PLAYER_ROUTE_ACTIVE_COLOR = 0xFFFFE16A;
    private static final int PLAYER_BRANCH_COLOR = 0xFF9BE3FF;
    private static final int PLAYER_BRANCH_NODE_COLOR = 0xFFBDEEFF;
    private static final float DEBUG_ROUTE_NODE_HALF_WIDTH = 0.5F;
    private static final float DEBUG_ROUTE_NODE_HEIGHT = 0.025F;
    private static final float DEBUG_ROUTE_NODE_ALPHA = 0.5F;
    private static final int DEBUG_ROUTE_LABEL_COLOR = 0xFFFFFFFF;
    private static final float DEBUG_LABEL_SCALE = 0.025F;
    private static final int OWNER_NAMES_PER_LINE = 5;
    private static final double DEBUG_ROUTE_LABEL_HEIGHT = 1.36D;
    private static final double ROUTE_GUIDE_HEIGHT_ABOVE_SURFACE = 0.55D;
    private static final int ROUTE_GUIDE_SURFACE_SEARCH_UP = 3;
    private static final int ROUTE_GUIDE_SURFACE_SEARCH_DOWN = 6;
    private static final double MAX_LABEL_DISTANCE_SQR = 96.0D * 96.0D;
    private static final long ROUTE_GUIDE_CACHE_TICKS = 20L;
    private static final int MAX_ROUTE_GUIDE_CACHE_ENTRIES = 4096;
    private static final RenderType DEBUG_ROUTE_GUIDE_TYPE = RenderType.debugLineStrip(2.0D);
    private static final RenderType PLAYER_ROUTE_QUADS_TYPE = RenderType.debugQuads();
    private static final RenderType ROUTE_REACH_TYPE = RenderType.debugLineStrip(4.0D);
    private static final double ASSIGNED_ROUTE_OUTLINE_HALF_WIDTH = 0.075D;
    private static final double ASSIGNED_ROUTE_CORE_HALF_WIDTH = 0.045D;
    private static final double CREATION_ROUTE_OUTLINE_HALF_WIDTH = 0.085D;
    private static final double CREATION_ROUTE_CORE_HALF_WIDTH = 0.0525D;
    private static final double PLAYER_ROUTE_HEIGHT_ABOVE_SURFACE = 0.08D;
    private static final double PLAYER_ROUTE_SEGMENT_OVERLAP = 0.012D;
    private static final int ROUTE_REACH_SEGMENTS = 64;
    private static final int MAX_OWNER_NAME_LAYOUT_CACHE_ENTRIES = 1024;
    private static final List<OutlinedStoragePosition> ASSIGNED_POSITIONS = new ArrayList<>();
    private static final List<WorkAreaPosition> WORK_AREAS = new ArrayList<>();
    private static final List<RoutePosition> ROUTES = new ArrayList<>();
    private static final List<OutlinedStoragePosition> DEBUG_ASSIGNED_POSITIONS = new ArrayList<>();
    private static final List<WorkAreaPosition> DEBUG_WORK_AREAS = new ArrayList<>();
    private static final List<RoutePosition> DEBUG_ROUTES = new ArrayList<>();
    private static final Map<RouteGuideCacheKey, CachedRouteGuide> ROUTE_GUIDE_CACHE = new HashMap<>();
    private static final Map<String, OwnerNameLayout> OWNER_NAME_LAYOUT_CACHE = new HashMap<>();
    private static RoutePreview retainedRoutePreview;
    private static RoutePreviewKey retainedRoutePreviewKey;
    private static RoutePosition synchronizedRouteDraft;
    private static boolean routeDraftSynchronized;
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
    private static ClipboardPreviewLens clipboardPreviewLens = ClipboardPreviewLens.NONE;
    private static String clipboardScopeOwner = "";
    private static String clipboardScopeJob = "";
    private static Set<String> clipboardScopeOwners = Set.of();
    private static Set<String> clipboardTrackedJobs = Set.of();
    private static List<WorkforceMarker> clipboardWorkforceMarkers = List.of();
    private static Set<String> clipboardProblemOwners = Set.of();

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
                        entry.ownerName(),
                        entry.storageType()
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
                        entry.ownerName(),
                        entry.jobName()
                ));
            }
            workAreasVisibleUntilGameTime = minecraft.level.getGameTime() + payload.ticks();
        });
    }

    public static void accept(ClipboardRouteSyncPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            ROUTE_GUIDE_CACHE.clear();
            if (payload.draft()) {
                routeDraftSynchronized = true;
                synchronizedRouteDraft = payload.entries().isEmpty()
                        ? null
                        : routePosition(payload.entries().getFirst());
                clearRetainedRoutePreview();
                return;
            }
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
            ROUTE_GUIDE_CACHE.clear();
            debugPreviewEnabled = payload.enabled();
            if (minecraft.level == null || !payload.enabled()) {
                debugPreviewVisibleUntilGameTime = 0L;
                nearbyWorkAreaPreviewsEnabled = false;
                nearbyStoragePreviewsEnabled = false;
                nearbyPaymentPreviewsEnabled = false;
                if (!payload.enabled()) {
                    clearClipboardPreview();
                }
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
                entry.branches(),
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
            WORK_AREAS.clear();
            ROUTES.clear();
            DEBUG_ASSIGNED_POSITIONS.clear();
            DEBUG_WORK_AREAS.clear();
            DEBUG_ROUTES.clear();
            ROUTE_GUIDE_CACHE.clear();
            OWNER_NAME_LAYOUT_CACHE.clear();
            clearRetainedRoutePreview();
            synchronizedRouteDraft = null;
            routeDraftSynchronized = false;
            debugPreviewEnabled = false;
            hitboxDebugPreviewSent = false;
            nearbyWorkAreaPreviewsEnabled = false;
            nearbyStoragePreviewsEnabled = false;
            nearbyPaymentPreviewsEnabled = false;
            return;
        }

        renderDebugPreview(event, minecraft);
        renderTimedClipboardPreviews(event, minecraft);

        if (!isHoldingClipboard(minecraft)) {
            clearRetainedRoutePreview();
            return;
        }

        ItemStack clipboard = clipboardStack(minecraft);
        ClipboardMode mode = HiredStorageClipboardItem.mode(clipboard);
        boolean jobSiteEditorOpen = minecraft.screen instanceof ClipboardWorkforceScreen screen
                && screen.isJobSitePage();
        if (jobSiteEditorOpen) {
            clearRetainedRoutePreview();
            return;
        }
        if (mode.isStorageAssignmentMode() || mode == ClipboardMode.ASSIGN_PAYMENT) {
            List<StoragePosition> selected = HiredStorageClipboardItem.selectedContainers(clipboard);
            renderPositions(event, selected, mode == ClipboardMode.ASSIGN_PAYMENT ? PAYMENT_COLOR : SELECTED_COLOR);
            return;
        }

        if (mode == ClipboardMode.ROUTE) {
            renderHeldRouteDraft(event, clipboard);
            renderRoutePlacementPreview(event, clipboard);
            return;
        }
        if (mode == ClipboardMode.BRANCH) {
            renderHeldRouteDraft(event, clipboard);
            renderBranchPlacementPreview(event, clipboard);
            return;
        }
        clearRetainedRoutePreview();

        if (mode == ClipboardMode.SET_WORK_AREA) {
            renderHeldWorkAreaDraft(event, clipboard);
        }
    }

    private static void renderTimedClipboardPreviews(RenderLevelStageEvent event, Minecraft minecraft) {
        long gameTime = minecraft.level.getGameTime();
        if (gameTime <= workAreasVisibleUntilGameTime) {
            renderWorkAreas(event, WORK_AREAS, WORK_AREA_COLOR);
        } else {
            WORK_AREAS.clear();
        }
        if (gameTime <= assignedVisibleUntilGameTime) {
            renderAssignedPositions(event, ASSIGNED_POSITIONS);
        } else {
            ASSIGNED_POSITIONS.clear();
        }
        if (gameTime <= routesVisibleUntilGameTime) {
            if (minecraft.getEntityRenderDispatcher().shouldRenderHitBoxes()) {
                renderDebugRoutes(event, ROUTES, ROUTE_COLOR);
            } else {
                renderAssignedRoutes(event, ROUTES, ROUTE_COLOR);
                renderRouteLabels(event, ROUTES, false, false);
            }
        } else {
            ROUTES.clear();
        }
        renderDebugLabels(event, WORK_AREAS, List.of(), ASSIGNED_POSITIONS, true, true);
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
        RouteDraft draft = clientRouteDraft(clipboard);
        if (draft.isEmpty()) {
            return false;
        }
        RoutePosition route = new RoutePosition(draft.dimension(), draft.route().nodes(), draft.route().loop(), draft.route().branches(), "", "");
        renderCreationRoute(event, route);
        renderRouteLabels(event, List.of(route), true, false);
        if (!route.loop() && route.nodes().size() < HiredRoute.MAX_NODES) {
            renderRouteReach(event, route.nodes().getLast());
            renderRouteContinuationLabel(event, route.nodes().getLast());
        }
        return true;
    }

    private static void renderBranchPlacementPreview(RenderLevelStageEvent event, ItemStack clipboard) {
        Minecraft minecraft = Minecraft.getInstance();
        RouteDraft draft = clientRouteDraft(clipboard);
        if (minecraft.level == null || draft.isEmpty()
                || !minecraft.level.dimension().equals(draft.dimension())
                || !(minecraft.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos target = hit.getBlockPos().immutable();
        BlockPos pendingAnchor = HiredStorageClipboardItem.selectedBranchAnchor(clipboard);
        BlockPos anchor = pendingAnchor == null
                ? draft.route().nearestBaseAttachment(target, 2, 2)
                : pendingAnchor;
        int color = anchor != null && (pendingAnchor == null || HiredRoute.canConnect(anchor, target))
                ? PLAYER_BRANCH_COLOR
                : ROUTE_INVALID_COLOR;
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer boxConsumer = bufferSource.getBuffer(RenderType.lines());
        renderColoredBox(poseStack, boxConsumer, markerBox(target), color);
        if (anchor != null) {
            renderCreationRouteSegment(poseStack, bufferSource, anchor, target, color);
            renderColoredBox(poseStack, boxConsumer, markerBox(anchor), PLAYER_BRANCH_NODE_COLOR);
        }
        poseStack.popPose();
        bufferSource.endBatch(RenderType.lines());
    }

    private static void renderRoutePlacementPreview(RenderLevelStageEvent event, ItemStack clipboard) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        RoutePreview preview = routePreview(minecraft, clipboard);
        if (preview == null) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer boxConsumer = bufferSource.getBuffer(RenderType.lines());
        int color = preview.valid() ? PLAYER_ROUTE_CREATION_COLOR : ROUTE_INVALID_COLOR;
        if (preview.showTargetMarker()) {
            renderColoredBox(poseStack, boxConsumer, markerBox(preview.target()), color);
        }
        if (preview.from() != null) {
            renderCreationRouteSegment(poseStack, bufferSource, preview.from(), preview.target(), color);
        }
        poseStack.popPose();
        bufferSource.endBatch(RenderType.lines());
        renderRoutePreviewLabel(event, preview);
    }

    private static RoutePreview routePreview(Minecraft minecraft, ItemStack clipboard) {
        RouteDraft draft = clientRouteDraft(clipboard);
        RoutePreviewKey previewKey = RoutePreviewKey.of(draft);
        HitResult hitResult = minecraft.hitResult;
        BlockHitResult blockHitResult = hitResult instanceof BlockHitResult result
                && result.getType() == HitResult.Type.BLOCK ? result : null;
        if (blockHitResult == null) {
            if (previewKey.equals(retainedRoutePreviewKey)) {
                return retainedRoutePreview;
            }
            clearRetainedRoutePreview();
            return null;
        }
        BlockPos target = blockHitResult.getBlockPos().immutable();
        RoutePreview preview;
        if (draft.isEmpty()) {
            preview = new RoutePreview(target, null, true, true, 1,
                    Component.translatable("villagerretaliation.clipboard.route_preview.start").getString());
        } else if (!minecraft.level.dimension().equals(draft.dimension())) {
            preview = new RoutePreview(target, null, false, true, 1,
                    Component.translatable("villagerretaliation.clipboard.route_preview.wrong_dimension").getString());
        } else {
            preview = routePreview(draft.route(), target);
        }
        retainedRoutePreview = preview;
        retainedRoutePreviewKey = previewKey;
        return preview;
    }

    private static RoutePreview routePreview(HiredRoute route, BlockPos target) {
        List<BlockPos> nodes = route.nodes();
        if (target.equals(nodes.getFirst()) && nodes.size() >= 2) {
            if (route.loop()) {
                return new RoutePreview(target, null, true, true, 1,
                        Component.translatable("villagerretaliation.clipboard.route_preview.open_loop").getString());
            }
            BlockPos last = nodes.getLast();
            boolean valid = HiredRoute.canConnect(last, nodes.getFirst());
            return new RoutePreview(target, last, valid, true, 1,
                    routeDistanceHint(valid ? "close_loop" : "too_far", last, target, 1));
        }
        int existingIndex = route.indexOf(target);
        if (existingIndex >= 0) {
            return new RoutePreview(target, null, false, true, existingIndex + 1,
                    Component.translatable(
                            "villagerretaliation.clipboard.route_preview.existing", existingIndex + 1).getString());
        }
        if (route.loop()) {
            return new RoutePreview(target, null, false, true, nodes.size() + 1,
                    Component.translatable("villagerretaliation.clipboard.route_preview.loop_closed").getString());
        }
        if (nodes.size() >= HiredRoute.MAX_NODES) {
            return new RoutePreview(target, null, false, true, nodes.size() + 1,
                    Component.translatable(
                            "villagerretaliation.clipboard.route_preview.full", HiredRoute.MAX_NODES).getString());
        }
        BlockPos last = nodes.getLast();
        boolean valid = HiredRoute.canConnect(last, target);
        return new RoutePreview(target, last, valid, true, nodes.size() + 1,
                routeDistanceHint(valid ? "add" : "too_far", last, target, nodes.size() + 1));
    }

    private static String routeDistanceHint(String action, BlockPos from, BlockPos target, int nodeIndex) {
        String distance = String.format(java.util.Locale.ROOT, "%.1f", Math.sqrt(from.distSqr(target)));
        return Component.translatable(
                "villagerretaliation.clipboard.route_preview." + action,
                nodeIndex,
                distance,
                HiredRoute.MAX_NODE_DISTANCE).getString();
    }

    private static void clearRetainedRoutePreview() {
        retainedRoutePreview = null;
        retainedRoutePreviewKey = null;
    }

    private static RouteDraft clientRouteDraft(ItemStack clipboard) {
        if (routeDraftSynchronized) {
            if (synchronizedRouteDraft == null) {
                return new RouteDraft(null, HiredRoute.empty());
            }
            return new RouteDraft(
                    synchronizedRouteDraft.dimension(),
                    new HiredRoute(
                            synchronizedRouteDraft.nodes(),
                            synchronizedRouteDraft.loop(),
                            synchronizedRouteDraft.branches()));
        }
        return HiredStorageClipboardItem.selectedRoute(clipboard);
    }

    private static void renderDebugPreview(RenderLevelStageEvent event, Minecraft minecraft) {
        if (clipboardPreviewLens == ClipboardPreviewLens.WORKFORCE) {
            renderWorkforceMarkers(event, clipboardWorkforceMarkers, false);
            return;
        }
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
        if (clipboardPreviewLens == ClipboardPreviewLens.ASSIGNMENTS
                || clipboardPreviewLens == ClipboardPreviewLens.PROBLEMS) {
            boolean problemsOnly = clipboardPreviewLens == ClipboardPreviewLens.PROBLEMS;
            List<WorkAreaPosition> workAreas = DEBUG_WORK_AREAS.stream()
                    .filter(area -> matchesClipboardScope(area.ownerName(), area.jobName(), problemsOnly))
                    .toList();
            List<OutlinedStoragePosition> storage = DEBUG_ASSIGNED_POSITIONS.stream()
                    .filter(position -> matchesClipboardScope(position.ownerName(), "", problemsOnly))
                    .toList();
            List<RoutePosition> routes = DEBUG_ROUTES.stream()
                    .filter(route -> matchesClipboardScope(route.ownerName(), route.jobName(), problemsOnly))
                    .toList();
            renderWorkAreas(event, workAreas, problemsOnly ? ROUTE_INVALID_COLOR : WORK_AREA_COLOR);
            renderAssignedOrDebugRoutes(
                    event,
                    minecraft,
                    routes,
                    problemsOnly ? ROUTE_INVALID_COLOR : ROUTE_COLOR);
            renderAssignedPositions(
                    event,
                    storage,
                    true,
                    true,
                    problemsOnly ? ROUTE_INVALID_COLOR : ASSIGNED_COLOR,
                    problemsOnly ? ROUTE_INVALID_COLOR : PAYMENT_COLOR);
            renderAssignmentTargets(event, clipboardWorkforceMarkers, problemsOnly);
            renderDebugLabels(event, workAreas, routes, storage, true, true);
            if (problemsOnly) {
                renderWorkforceMarkers(event, clipboardWorkforceMarkers, true);
            }
            return;
        }
        if (nearbyWorkAreaPreviewsEnabled) {
            renderWorkAreas(event, DEBUG_WORK_AREAS, WORK_AREA_COLOR);
            renderAssignedOrDebugRoutes(event, minecraft, DEBUG_ROUTES, ROUTE_COLOR);
        }
        renderAssignedPositions(event, DEBUG_ASSIGNED_POSITIONS, nearbyStoragePreviewsEnabled, nearbyPaymentPreviewsEnabled);
        renderDebugLabels(
                event,
                nearbyWorkAreaPreviewsEnabled ? DEBUG_WORK_AREAS : List.of(),
                List.of(),
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
        return clipboardPreviewLens != ClipboardPreviewLens.NONE
                || nearbyWorkAreaPreviewsEnabled
                || nearbyStoragePreviewsEnabled
                || nearbyPaymentPreviewsEnabled;
    }

    public static void acceptClipboardPreviewMarkers(ClipboardPreviewMarkerSyncPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            Map<UUID, WorkforceMarker> existing = new HashMap<>();
            for (WorkforceMarker marker : clipboardWorkforceMarkers) {
                existing.put(marker.villagerId(), marker);
            }
            List<WorkforceMarker> merged = new ArrayList<>();
            Set<String> selectedOwners = new HashSet<>();
            for (ClipboardPreviewMarkerSyncPayload.Entry entry : payload.entries()) {
                WorkforceMarker previous = existing.get(entry.villagerId());
                merged.add(new WorkforceMarker(
                        entry.villagerId(),
                        ResourceKey.create(Registries.DIMENSION, entry.dimension()),
                        entry.position(),
                        entry.ownerName(),
                        entry.jobName(),
                        workforceStatusName(entry.status()),
                        entry.target(),
                        previous != null && previous.warning()));
                selectedOwners.add(entry.ownerName());
            }
            clipboardWorkforceMarkers = List.copyOf(merged);
            if (clipboardPreviewLens == ClipboardPreviewLens.ASSIGNMENTS) {
                clipboardScopeOwners = Set.copyOf(selectedOwners);
            }
        });
    }

    public static void setClipboardPreview(
            ClipboardPreviewLens lens,
            String scopeOwner,
            String scopeJob,
            Set<String> scopeOwners,
            Set<String> trackedJobs,
            List<WorkforceMarker> markers,
            Set<String> problemOwners) {
        ClipboardPreviewLens nextLens = lens == null ? ClipboardPreviewLens.NONE : lens;
        String nextOwner = scopeOwner == null ? "" : scopeOwner.trim();
        String nextJob = scopeJob == null ? "" : scopeJob.trim();
        Set<String> nextOwners = scopeOwners == null ? Set.of() : Set.copyOf(scopeOwners);
        Set<String> nextTrackedJobs = trackedJobs == null ? Set.of() : Set.copyOf(trackedJobs);
        clipboardPreviewLens = nextLens;
        clipboardScopeOwner = nextOwner;
        clipboardScopeJob = nextJob;
        clipboardScopeOwners = nextOwners;
        clipboardTrackedJobs = nextTrackedJobs;
        clipboardWorkforceMarkers = markers == null ? List.of() : List.copyOf(markers);
        clipboardProblemOwners = problemOwners == null ? Set.of() : Set.copyOf(problemOwners);
    }

    public static void clearClipboardPreview() {
        clipboardPreviewLens = ClipboardPreviewLens.NONE;
        clipboardScopeOwner = "";
        clipboardScopeJob = "";
        clipboardScopeOwners = Set.of();
        clipboardTrackedJobs = Set.of();
        clipboardWorkforceMarkers = List.of();
        clipboardProblemOwners = Set.of();
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clearClipboardPreview();
        DEBUG_ASSIGNED_POSITIONS.clear();
        DEBUG_WORK_AREAS.clear();
        DEBUG_ROUTES.clear();
        ROUTE_GUIDE_CACHE.clear();
        clearRetainedRoutePreview();
        synchronizedRouteDraft = null;
        routeDraftSynchronized = false;
        debugPreviewEnabled = false;
        nearbyWorkAreaPreviewsEnabled = false;
        nearbyStoragePreviewsEnabled = false;
        nearbyPaymentPreviewsEnabled = false;
    }

    public static ClipboardPreviewLens clipboardPreviewLens() {
        return clipboardPreviewLens;
    }

    public static Set<String> clipboardTrackedJobs() {
        return clipboardTrackedJobs;
    }

    private static String workforceStatusName(WorkerStatus status) {
        WorkerStatus safeStatus = status == null ? WorkerStatus.UNKNOWN : status;
        return Component.translatable(
                "villagerretaliation.gui.clipboard_workforce.status."
                        + safeStatus.name().toLowerCase(java.util.Locale.ROOT)).getString();
    }

    private static boolean matchesClipboardScope(String ownerNames, String jobName, boolean problemsOnly) {
        Set<String> owners = splitOwnerNames(ownerNames);
        if (problemsOnly && owners.stream().noneMatch(clipboardProblemOwners::contains)) {
            return false;
        }
        if (!clipboardScopeOwner.isBlank() && !owners.contains(clipboardScopeOwner)) {
            return false;
        }
        if (clipboardPreviewLens == ClipboardPreviewLens.ASSIGNMENTS
                && clipboardScopeOwner.isBlank()
                && clipboardScopeJob.isBlank()
                && clipboardScopeOwners.isEmpty()
                && clipboardTrackedJobs.isEmpty()) {
            return false;
        }
        if (clipboardPreviewLens == ClipboardPreviewLens.ASSIGNMENTS && !clipboardTrackedJobs.isEmpty()) {
            if (clipboardTrackedJobs.stream().anyMatch(job -> job.equalsIgnoreCase(jobName))) {
                return true;
            }
            return owners.stream().anyMatch(clipboardScopeOwners::contains);
        }
        if (clipboardScopeJob.isBlank()) {
            return true;
        }
        if (clipboardScopeJob.equalsIgnoreCase(jobName)) {
            return true;
        }
        return owners.stream().anyMatch(clipboardScopeOwners::contains);
    }

    private static Set<String> splitOwnerNames(String ownerNames) {
        if (ownerNames == null || ownerNames.isBlank()) {
            return Set.of();
        }
        Set<String> names = new HashSet<>();
        for (String name : ownerNames.split(",\\s*")) {
            if (!name.isBlank()) {
                names.add(name.trim());
            }
        }
        return names;
    }

    private static ActiveRouteTargets activeRouteTargets(
            RoutePosition route,
            ResourceKey<Level> currentDimension) {
        Set<String> owners = splitOwnerNames(route.ownerName());
        if (owners.isEmpty()) {
            return ActiveRouteTargets.EMPTY;
        }
        Set<Integer> activeNodes = new HashSet<>();
        Set<RouteEdge> activeEdges = new HashSet<>();
        Set<Integer> activeBranches = new HashSet<>();
        for (WorkforceMarker marker : clipboardWorkforceMarkers) {
            if (!marker.dimension().equals(currentDimension)
                    || marker.targetPos() == null
                    || owners.stream().noneMatch(owner -> owner.equalsIgnoreCase(marker.ownerName()))
                    || !route.jobName().isBlank() && !route.jobName().equalsIgnoreCase(marker.jobName())) {
                continue;
            }
            int branchIndex = activeBranchIndex(route, marker);
            if (branchIndex >= 0) {
                activeBranches.add(branchIndex);
                continue;
            }
            int targetIndex = route.nodes().indexOf(marker.targetPos());
            if (targetIndex < 0) {
                continue;
            }
            activeNodes.add(targetIndex);
            int sourceIndex = nearestConnectedRouteNode(route, targetIndex, marker.pos());
            if (sourceIndex >= 0) {
                activeEdges.add(RouteEdge.of(sourceIndex, targetIndex));
            }
        }
        if (activeNodes.isEmpty() && activeBranches.isEmpty()) {
            return ActiveRouteTargets.EMPTY;
        }
        return new ActiveRouteTargets(
                Set.copyOf(activeNodes),
                Set.copyOf(activeEdges),
                Set.copyOf(activeBranches));
    }

    private static int activeBranchIndex(RoutePosition route, WorkforceMarker marker) {
        for (int index = 0; index < route.branches().size(); index++) {
            HiredRoute.Branch branch = route.branches().get(index);
            if (marker.targetPos().equals(branch.end())) {
                return index;
            }
            double branchDistance = squaredDistanceToSegment(marker.pos(), branch.anchor(), branch.end());
            if (marker.targetPos().equals(branch.anchor())
                    && branchDistance <= 4.0D
                    && branchDistance + 0.01D < squaredDistanceToBaseRoute(marker.pos(), route)) {
                return index;
            }
        }
        return -1;
    }

    private static double squaredDistanceToBaseRoute(BlockPos pos, RoutePosition route) {
        double distance = Double.MAX_VALUE;
        for (int index = 1; index < route.nodes().size(); index++) {
            distance = Math.min(distance, squaredDistanceToSegment(
                    pos, route.nodes().get(index - 1), route.nodes().get(index)));
        }
        if (route.loop() && route.nodes().size() > 1) {
            distance = Math.min(distance, squaredDistanceToSegment(
                    pos, route.nodes().getLast(), route.nodes().getFirst()));
        }
        return distance;
    }

    private static double squaredDistanceToSegment(BlockPos pos, BlockPos first, BlockPos second) {
        double vx = second.getX() - first.getX();
        double vy = second.getY() - first.getY();
        double vz = second.getZ() - first.getZ();
        double lengthSqr = vx * vx + vy * vy + vz * vz;
        if (lengthSqr <= 0.0001D) {
            return pos.distSqr(first);
        }
        double progress = Math.max(0.0D, Math.min(1.0D,
                ((pos.getX() - first.getX()) * vx
                        + (pos.getY() - first.getY()) * vy
                        + (pos.getZ() - first.getZ()) * vz) / lengthSqr));
        double dx = pos.getX() - (first.getX() + vx * progress);
        double dy = pos.getY() - (first.getY() + vy * progress);
        double dz = pos.getZ() - (first.getZ() + vz * progress);
        return dx * dx + dy * dy + dz * dz;
    }

    private static int nearestConnectedRouteNode(RoutePosition route, int targetIndex, BlockPos villagerPos) {
        int size = route.nodes().size();
        if (size < 2) {
            return -1;
        }
        int previous = targetIndex - 1;
        int next = targetIndex + 1;
        if (route.loop()) {
            previous = Math.floorMod(previous, size);
            next = Math.floorMod(next, size);
        }
        if (previous < 0) {
            return next < size ? next : -1;
        }
        if (next >= size) {
            return previous;
        }
        return villagerPos.distSqr(route.nodes().get(previous))
                <= villagerPos.distSqr(route.nodes().get(next)) ? previous : next;
    }

    private static void renderWorkforceMarkers(
            RenderLevelStageEvent event,
            List<WorkforceMarker> markers,
            boolean problemsOnly) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || markers.isEmpty()) {
            return;
        }
        ResourceKey<Level> dimension = minecraft.level.dimension();
        List<WorkforceMarker> visible = markers.stream()
                .filter(marker -> marker.dimension().equals(dimension))
                .filter(marker -> !problemsOnly || marker.warning())
                .filter(marker -> !problemsOnly
                        || matchesClipboardScope(marker.ownerName(), marker.jobName(), true))
                .filter(marker -> minecraft.level.hasChunkAt(marker.pos()))
                .filter(marker -> isVisible(event, markerBox(marker.pos()).inflate(1.0D)))
                .toList();
        if (visible.isEmpty()) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Map<UUID, Entity> loadedEntities = new HashMap<>();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            loadedEntities.put(entity.getUUID(), entity);
        }
        List<ResolvedWorkforceMarker> resolved = visible.stream()
                .map(marker -> resolveWorkforceMarker(marker, loadedEntities.get(marker.villagerId()), partialTick))
                .toList();
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());
        for (ResolvedWorkforceMarker resolvedMarker : resolved) {
            WorkforceMarker marker = resolvedMarker.marker();
            int color = marker.warning() ? ROUTE_INVALID_COLOR : SELECTED_COLOR;
            renderColoredBox(poseStack, consumer, resolvedMarker.box(), color);
        }
        bufferSource.endBatch(RenderType.lines());
        poseStack.popPose();

        List<DebugLabelPosition> labels = resolved.stream()
                .map(resolvedMarker -> new DebugLabelPosition(
                        resolvedMarker.labelPos(),
                        resolvedMarker.marker().jobName(),
                        resolvedMarker.marker().status(),
                        resolvedMarker.marker().warning() ? ROUTE_INVALID_COLOR : SELECTED_COLOR))
                .toList();
        renderLabels(event, labels);
    }

    private static ResolvedWorkforceMarker resolveWorkforceMarker(
            WorkforceMarker marker,
            Entity entity,
            float partialTick) {
        if (entity != null && entity.isAlive()) {
            Vec3 renderPos = entity.getPosition(partialTick);
            Vec3 interpolationOffset = renderPos.subtract(entity.position());
            AABB box = entity.getBoundingBox().move(interpolationOffset).inflate(0.15D);
            return new ResolvedWorkforceMarker(
                    marker,
                    box,
                    renderPos.add(0.0D, entity.getBbHeight() + 0.4D, 0.0D));
        }
        AABB box = new AABB(marker.pos()).inflate(0.15D).expandTowards(0.0D, 0.9D, 0.0D);
        return new ResolvedWorkforceMarker(
                marker,
                box,
                new Vec3(marker.pos().getX() + 0.5D, marker.pos().getY() + 2.35D, marker.pos().getZ() + 0.5D));
    }

    private static void renderAssignmentTargets(
            RenderLevelStageEvent event,
            List<WorkforceMarker> markers,
            boolean problemsOnly) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        ResourceKey<Level> dimension = minecraft.level.dimension();
        List<WorkforceMarker> visible = markers.stream()
                .filter(marker -> marker.dimension().equals(dimension))
                .filter(marker -> marker.targetPos() != null)
                .filter(marker -> matchesClipboardScope(marker.ownerName(), marker.jobName(), problemsOnly))
                .filter(marker -> minecraft.level.hasChunkAt(marker.targetPos()))
                .filter(marker -> isVisible(event, markerBox(marker.targetPos())))
                .toList();
        if (visible.isEmpty()) {
            return;
        }
        int color = problemsOnly ? ROUTE_INVALID_COLOR : WORK_AREA_CENTER_COLOR;
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());
        for (WorkforceMarker marker : visible) {
            renderColoredBox(poseStack, consumer, markerBox(marker.targetPos()), color);
        }
        bufferSource.endBatch(RenderType.lines());
        poseStack.popPose();
        renderLabels(event, visible.stream()
                .map(marker -> new DebugLabelPosition(
                        Vec3.atCenterOf(marker.targetPos()).add(0.0D, 0.85D, 0.0D),
                        "",
                        Component.translatable(
                                "villagerretaliation.gui.clipboard_workforce.preview_tab.assignments.target").getString(),
                        color))
                .toList());
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
        Map<BlockPos, Integer> storageLabelLineCounts = new HashMap<>();
        double storageLabelLineStep = (minecraft.font.lineHeight + 1.0D) * DEBUG_LABEL_SCALE;
        BlockPos hoveredBlock = hoveredBlock(minecraft);
        for (OutlinedStoragePosition position : storagePositions) {
            if (!position.dimension().equals(currentDimension)
                    || position.payment() && !includePaymentStorage
                    || !position.payment() && !includeNormalStorage
                    || position.ownerName().isBlank()
                    || !minecraft.level.hasChunkAt(position.pos())) {
                continue;
            }
            int precedingLines = storageLabelLineCounts.getOrDefault(position.pos(), 0);
            int ownerLineCount = showVillagerNames()
                    ? ownerNameLines(position.ownerName(), position.pos().equals(hoveredBlock)).size()
                    : 0;
            int labelLineCount = ownerLineCount + (position.storageType().isBlank() ? 0 : 1);
            int expandedPushUpLines = Math.max(0, labelLineCount - 2);
            labels.add(new DebugLabelPosition(
                    new Vec3(
                            position.pos().getX() + 0.5D,
                            position.pos().getY() + 1.25D
                                    + (precedingLines + expandedPushUpLines) * storageLabelLineStep,
                            position.pos().getZ() + 0.5D),
                    position.ownerName(),
                    position.storageType(),
                    position.payment() ? PAYMENT_COLOR : ASSIGNED_COLOR,
                    position.pos()
            ));
            storageLabelLineCounts.put(position.pos(), precedingLines + labelLineCount);
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
                            playerRouteNodeColor(route, index, PLAYER_ROUTE_CREATION_COLOR)
                    ));
                }
            }
        }
        if (includeOwnerLabels) {
            addRouteOwnerLabels(minecraft, currentDimension, routes, labels);
        }
        renderLabels(event, labels);
    }

    private static void renderRouteContinuationLabel(RenderLevelStageEvent event, BlockPos lastNode) {
        renderLabels(event, List.of(new DebugLabelPosition(
                new Vec3(lastNode.getX() + 0.5D, lastNode.getY() + 1.8D, lastNode.getZ() + 0.5D),
                "",
                Component.translatable(
                        "villagerretaliation.clipboard.route_preview.continue",
                        HiredRoute.MAX_NODE_DISTANCE).getString(),
                PLAYER_ROUTE_CREATION_COLOR)));
    }

    private static void renderRoutePreviewLabel(RenderLevelStageEvent event, RoutePreview preview) {
        if (preview.hint().isBlank()) {
            return;
        }
        int color = preview.valid() ? PLAYER_ROUTE_CREATION_COLOR : ROUTE_INVALID_COLOR;
        double labelHeight = preview.valid() ? 1.8D : 2.2D;
        renderLabels(event, List.of(new DebugLabelPosition(
                new Vec3(
                        preview.target().getX() + 0.5D,
                        preview.target().getY() + labelHeight,
                        preview.target().getZ() + 0.5D),
                "",
                preview.hint(),
                color)));
    }

    private static void addRouteOwnerLabels(
            Minecraft minecraft,
            ResourceKey<Level> currentDimension,
            List<RoutePosition> routes,
            List<DebugLabelPosition> labels) {
        Map<BlockPos, Integer> routeLabelCounts = new HashMap<>();
        double routeLabelStackStep = (minecraft.font.lineHeight + 1.0D) * 2.0D * DEBUG_LABEL_SCALE;
        for (RoutePosition route : routes) {
            if (!route.dimension().equals(currentDimension)
                    || route.ownerName().isBlank()
                    || route.nodes().isEmpty()
                    || !minecraft.level.hasChunkAt(route.nodes().getFirst())) {
                continue;
            }
            BlockPos labelPos = route.nodes().getFirst();
            int stackIndex = routeLabelCounts.merge(labelPos, 1, Integer::sum) - 1;
            labels.add(new DebugLabelPosition(
                    new Vec3(
                            labelPos.getX() + 0.5D,
                            labelPos.getY() + 1.75D + stackIndex * routeLabelStackStep,
                            labelPos.getZ() + 0.5D),
                    route.ownerName(),
                    route.jobName().isBlank() ? routeDescription(route) : route.jobName() + " route",
                    ROUTE_COLOR
            ));
        }
    }

    private static void renderLabels(RenderLevelStageEvent event, List<DebugLabelPosition> labels) {
        Minecraft minecraft = Minecraft.getInstance();
        int maxVisibleLabels = Math.max(0, VillagerRetaliationConfig.DEBUG_PREVIEW_MAX_VISIBLE_LABELS.get());
        if (labels.isEmpty() || maxVisibleLabels == 0) {
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        List<DebugLabelPosition> visibleLabels = labels.stream()
                .filter(label -> label.pos().distanceToSqr(camera) <= MAX_LABEL_DISTANCE_SQR)
                .filter(label -> isVisible(event, AABB.ofSize(label.pos(), 1.0D, 1.0D, 1.0D)))
                .sorted((first, second) -> Double.compare(
                        first.pos().distanceToSqr(camera),
                        second.pos().distanceToSqr(camera)))
                .limit(maxVisibleLabels)
                .toList();
        if (visibleLabels.isEmpty()) {
            return;
        }
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        Font font = minecraft.font;
        int background = ((int) (minecraft.options.getBackgroundOpacity(0.25F) * 255.0F)) << 24;
        BlockPos hoveredBlock = hoveredBlock(minecraft);
        for (DebugLabelPosition label : visibleLabels) {
            String ownerName = showVillagerNames() ? label.ownerName() : "";
            boolean storageLabel = label.hoverTarget() != null;
            boolean expanded = storageLabel && label.hoverTarget().equals(hoveredBlock);
            List<String> lines = new ArrayList<>(storageLabel
                    ? ownerNameLines(ownerName, expanded)
                    : ownerName.isBlank() ? List.of() : List.of(ownerName));
            if (!label.jobName().isBlank()) {
                lines.add(label.jobName());
            }
            poseStack.pushPose();
            poseStack.translate(label.pos().x - camera.x, label.pos().y - camera.y, label.pos().z - camera.z);
            poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
            poseStack.scale(DEBUG_LABEL_SCALE, -DEBUG_LABEL_SCALE, DEBUG_LABEL_SCALE);
            Matrix4f pose = poseStack.last().pose();
            for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                renderLabelLine(
                        font,
                        bufferSource,
                        pose,
                        lines.get(lineIndex),
                        lineIndex * (font.lineHeight + 1.0F),
                        background,
                        label.color());
            }
            poseStack.popPose();
        }
        bufferSource.endBatch();
    }

    private static BlockPos hoveredBlock(Minecraft minecraft) {
        if (minecraft.hitResult instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK) {
            return hit.getBlockPos();
        }
        return null;
    }

    private static List<String> ownerNameLines(String ownerNames, boolean expanded) {
        if (ownerNames == null || ownerNames.isBlank()) {
            return List.of();
        }
        OwnerNameLayout cached = OWNER_NAME_LAYOUT_CACHE.get(ownerNames);
        if (cached == null) {
            if (OWNER_NAME_LAYOUT_CACHE.size() >= MAX_OWNER_NAME_LAYOUT_CACHE_ENTRIES) {
                OWNER_NAME_LAYOUT_CACHE.clear();
            }
            cached = createOwnerNameLayout(ownerNames);
            OWNER_NAME_LAYOUT_CACHE.put(ownerNames, cached);
        }
        return expanded ? cached.expandedLines() : cached.collapsedLines();
    }

    private static OwnerNameLayout createOwnerNameLayout(String ownerNames) {
        List<String> names = List.of(ownerNames.split(",\\s*"));
        if (names.size() <= OWNER_NAMES_PER_LINE) {
            List<String> singleLine = List.of(String.join(", ", names));
            return new OwnerNameLayout(singleLine, singleLine);
        }
        List<String> lines = new ArrayList<>();
        for (int start = 0; start < names.size(); start += OWNER_NAMES_PER_LINE) {
            lines.add(String.join(", ", names.subList(start, Math.min(start + OWNER_NAMES_PER_LINE, names.size()))));
        }
        List<String> collapsed = List.of(
                lines.getFirst() + ", ... +" + (names.size() - OWNER_NAMES_PER_LINE) + " More");
        return new OwnerNameLayout(collapsed, List.copyOf(lines));
    }

    private static boolean isVisible(RenderLevelStageEvent event, AABB bounds) {
        return event.getFrustum() == null || event.getFrustum().isVisible(bounds);
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
        renderAssignedPositions(
                event,
                positions,
                includeNormalStorage,
                includePaymentStorage,
                ASSIGNED_COLOR,
                PAYMENT_COLOR);
    }

    private static void renderAssignedPositions(
            RenderLevelStageEvent event,
            List<OutlinedStoragePosition> positions,
            boolean includeNormalStorage,
            boolean includePaymentStorage,
            int storageColor,
            int paymentColor) {
        renderOutlinedStoragePositions(event, positions, storageColor, includeNormalStorage, false);
        renderOutlinedStoragePositions(event, positions, paymentColor, false, includePaymentStorage);
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
                    || !minecraft.level.hasChunkAt(position.pos())
                    || !isVisible(event, markerBox(position.pos()))) {
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
            if (!position.dimension().equals(currentDimension)
                    || !minecraft.level.hasChunkAt(position.pos())
                    || !isVisible(event, markerBox(position.pos()))) {
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
            AABB areaBox = workAreaBox(area);
            if (!area.dimension().equals(currentDimension) || !isVisible(event, areaBox)) {
                continue;
            }
            LevelRenderer.renderLineBox(poseStack, consumer, areaBox, red, green, blue, alpha);
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

    private static void renderAssignedOrDebugRoutes(
            RenderLevelStageEvent event,
            Minecraft minecraft,
            List<RoutePosition> routes,
            int color) {
        if (minecraft.getEntityRenderDispatcher().shouldRenderHitBoxes()) {
            renderDebugRoutes(event, routes, color);
        } else {
            renderAssignedRoutes(event, routes, color);
        }
    }

    private static void renderAssignedRoutes(RenderLevelStageEvent event, List<RoutePosition> routes, int color) {
        renderPlayerRoutes(
                event,
                routes,
                color == ROUTE_COLOR ? PLAYER_ROUTE_COLOR : color,
                ASSIGNED_ROUTE_OUTLINE_HALF_WIDTH,
                ASSIGNED_ROUTE_CORE_HALF_WIDTH,
                true);
    }

    private static void renderCreationRoute(RenderLevelStageEvent event, RoutePosition route) {
        renderPlayerRoutes(
                event,
                List.of(route),
                PLAYER_ROUTE_CREATION_COLOR,
                CREATION_ROUTE_OUTLINE_HALF_WIDTH,
                CREATION_ROUTE_CORE_HALF_WIDTH,
                false);
    }

    private static void renderPlayerRoutes(
            RenderLevelStageEvent event,
            List<RoutePosition> routes,
            int color,
            double outlineHalfWidth,
            double coreHalfWidth,
            boolean highlightActiveTargets) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || routes.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer routeConsumer = bufferSource.getBuffer(PLAYER_ROUTE_QUADS_TYPE);
        ResourceKey<Level> currentDimension = minecraft.level.dimension();
        int maxVisibleNodes = Math.max(0, VillagerRetaliationConfig.DEBUG_PREVIEW_MAX_VISIBLE_NODES.get());
        int renderedNodes = 0;
        int remainingSegments = Math.max(0, VillagerRetaliationConfig.DEBUG_PREVIEW_MAX_VISIBLE_SEGMENTS.get());
        for (RoutePosition route : routes) {
            if (!route.dimension().equals(currentDimension) || route.nodes().isEmpty()) {
                continue;
            }
            ActiveRouteTargets activeTargets = highlightActiveTargets
                    ? activeRouteTargets(route, currentDimension)
                    : ActiveRouteTargets.EMPTY;
            if (remainingSegments > 0) {
                remainingSegments -= renderPlayerRouteGuide(
                        event,
                        minecraft,
                        poseStack,
                        routeConsumer,
                        route,
                        color,
                        remainingSegments,
                        outlineHalfWidth,
                        coreHalfWidth,
                        activeTargets.edges());
            }
            for (int branchIndex = 0; branchIndex < route.branches().size(); branchIndex++) {
                HiredRoute.Branch branch = route.branches().get(branchIndex);
                if (remainingSegments <= 0) {
                    break;
                }
                AABB branchBounds = new AABB(Vec3.atCenterOf(branch.anchor()), Vec3.atCenterOf(branch.end())).inflate(1.0D);
                if (minecraft.level.hasChunkAt(branch.anchor())
                        && minecraft.level.hasChunkAt(branch.end())
                        && isVisible(event, branchBounds)) {
                    renderPlayerRouteBeam(
                            poseStack,
                            routeConsumer,
                            minecraft.level,
                            branch.anchor(),
                            branch.end(),
                            activeTargets.branches().contains(branchIndex)
                                    ? PLAYER_ROUTE_ACTIVE_COLOR
                                    : PLAYER_BRANCH_COLOR,
                            outlineHalfWidth,
                            coreHalfWidth);
                    if (renderedNodes < maxVisibleNodes) {
                        renderPlayerBranchNode(
                                poseStack,
                                routeConsumer,
                                minecraft.level,
                                branch.end(),
                                activeTargets.branches().contains(branchIndex)
                                        ? PLAYER_ROUTE_ACTIVE_COLOR
                                        : PLAYER_BRANCH_NODE_COLOR);
                        renderedNodes++;
                    }
                    remainingSegments--;
                }
            }
            for (int index = 0; index < route.nodes().size() && renderedNodes < maxVisibleNodes; index++) {
                BlockPos node = route.nodes().get(index);
                if (minecraft.level.hasChunkAt(node) && isVisible(event, markerBox(node))) {
                    renderPlayerRouteNode(
                            poseStack,
                            routeConsumer,
                            minecraft.level,
                            route,
                            index,
                            activeTargets.nodes().contains(index)
                                    ? PLAYER_ROUTE_ACTIVE_COLOR
                                    : playerRouteNodeColor(route, index, color));
                    renderedNodes++;
                }
            }
            if (renderedNodes >= maxVisibleNodes && remainingSegments <= 0) {
                break;
            }
        }
        bufferSource.endBatch(PLAYER_ROUTE_QUADS_TYPE);
        poseStack.popPose();
    }

    private static void renderDebugRoutes(RenderLevelStageEvent event, List<RoutePosition> routes, int color) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || routes.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        ResourceKey<Level> currentDimension = minecraft.level.dimension();
        int maxVisibleNodes = Math.max(0, VillagerRetaliationConfig.DEBUG_PREVIEW_MAX_VISIBLE_NODES.get());
        int renderedNodes = 0;
        int remainingSegments = Math.max(0, VillagerRetaliationConfig.DEBUG_PREVIEW_MAX_VISIBLE_SEGMENTS.get());
        for (RoutePosition route : routes) {
            if (!route.dimension().equals(currentDimension) || route.nodes().isEmpty()) {
                continue;
            }
            for (BlockPos node : route.nodes()) {
                if (renderedNodes >= maxVisibleNodes) {
                    break;
                }
                if (minecraft.level.hasChunkAt(node) && isVisible(event, markerBox(node))) {
                    renderDebugRouteNode(poseStack, bufferSource, node);
                    renderedNodes++;
                }
            }
            for (HiredRoute.Branch branch : route.branches()) {
                if (renderedNodes >= maxVisibleNodes) {
                    break;
                }
                if (minecraft.level.hasChunkAt(branch.end()) && isVisible(event, markerBox(branch.end()))) {
                    renderDebugBranchNode(poseStack, bufferSource, branch.end());
                    renderedNodes++;
                }
            }
            if (remainingSegments > 0) {
                remainingSegments -= renderRouteGuideLayer(
                        event,
                        minecraft,
                        poseStack,
                        bufferSource,
                        route,
                        color,
                        ROUTE_LOOP_COLOR,
                        remainingSegments,
                        DEBUG_ROUTE_GUIDE_TYPE);
            }
            for (HiredRoute.Branch branch : route.branches()) {
                if (remainingSegments <= 0) {
                    break;
                }
                AABB branchBounds = new AABB(
                        Vec3.atCenterOf(branch.anchor()),
                        Vec3.atCenterOf(branch.end())).inflate(1.0D);
                if (minecraft.level.hasChunkAt(branch.anchor())
                        && minecraft.level.hasChunkAt(branch.end())
                        && isVisible(event, branchBounds)) {
                    VertexConsumer branchConsumer = bufferSource.getBuffer(DEBUG_ROUTE_GUIDE_TYPE);
                    renderRouteGuide(
                            poseStack,
                            branchConsumer,
                            minecraft.level,
                            branch.anchor(),
                            branch.end(),
                            PLAYER_BRANCH_COLOR,
                            true);
                    bufferSource.endBatch(DEBUG_ROUTE_GUIDE_TYPE);
                    remainingSegments--;
                }
            }
            if (renderedNodes >= maxVisibleNodes && remainingSegments <= 0) {
                break;
            }
        }
        poseStack.popPose();
        bufferSource.endBatch(RenderType.debugFilledBox());
        renderDebugRouteNodeLabels(event, routes);
    }

    private static int renderPlayerRouteGuide(
            RenderLevelStageEvent event,
            Minecraft minecraft,
            PoseStack poseStack,
            VertexConsumer consumer,
            RoutePosition route,
            int color,
            int segmentBudget,
            double outlineHalfWidth,
            double coreHalfWidth,
            Set<RouteEdge> activeEdges) {
        if (route.nodes().size() < 2 || segmentBudget <= 0) {
            return 0;
        }
        int renderedSegments = 0;
        for (int index = 1; index < route.nodes().size() && renderedSegments < segmentBudget; index++) {
            BlockPos previous = route.nodes().get(index - 1);
            BlockPos current = route.nodes().get(index);
            AABB segmentBounds = new AABB(Vec3.atCenterOf(previous), Vec3.atCenterOf(current)).inflate(1.0D);
            if (minecraft.level.hasChunkAt(previous)
                    && minecraft.level.hasChunkAt(current)
                    && isVisible(event, segmentBounds)) {
                renderPlayerRouteBeam(
                        poseStack,
                        consumer,
                        minecraft.level,
                        previous,
                        current,
                        activeEdges.contains(RouteEdge.of(index - 1, index)) ? PLAYER_ROUTE_ACTIVE_COLOR : color,
                        outlineHalfWidth,
                        coreHalfWidth);
                renderedSegments++;
            }
        }
        if (route.loop() && renderedSegments < segmentBudget) {
            BlockPos first = route.nodes().getFirst();
            BlockPos last = route.nodes().getLast();
            if (minecraft.level.hasChunkAt(last)
                    && minecraft.level.hasChunkAt(first)
                    && isVisible(event, new AABB(Vec3.atCenterOf(last), Vec3.atCenterOf(first)).inflate(1.0D))) {
                renderPlayerRouteBeam(
                        poseStack,
                        consumer,
                        minecraft.level,
                        last,
                        first,
                        activeEdges.contains(RouteEdge.of(route.nodes().size() - 1, 0))
                                ? PLAYER_ROUTE_ACTIVE_COLOR
                                : playerRouteLoopColor(color),
                        outlineHalfWidth,
                        coreHalfWidth);
                renderedSegments++;
            }
        }
        return renderedSegments;
    }

    private static int renderRouteGuideLayer(
            RenderLevelStageEvent event,
            Minecraft minecraft,
            PoseStack poseStack,
            MultiBufferSource.BufferSource bufferSource,
            RoutePosition route,
            int color,
            int loopColor,
            int segmentBudget,
            RenderType guideType) {
        if (route.nodes().size() < 2 || segmentBudget <= 0) {
            return 0;
        }
        VertexConsumer guideConsumer = null;
        boolean renderedGuide = false;
        int renderedSegments = 0;
        for (int index = 1; index < route.nodes().size() && renderedSegments < segmentBudget; index++) {
            BlockPos previous = route.nodes().get(index - 1);
            BlockPos current = route.nodes().get(index);
            AABB segmentBounds = new AABB(Vec3.atCenterOf(previous), Vec3.atCenterOf(current)).inflate(1.0D);
            if (minecraft.level.hasChunkAt(previous)
                    && minecraft.level.hasChunkAt(current)
                    && isVisible(event, segmentBounds)) {
                boolean includeStart = !renderedGuide;
                if (!renderedGuide) {
                    guideConsumer = bufferSource.getBuffer(guideType);
                    renderedGuide = true;
                }
                renderRouteGuide(poseStack, guideConsumer, minecraft.level, previous, current, color, includeStart);
                renderedSegments++;
            } else if (renderedGuide) {
                bufferSource.endBatch(guideType);
                renderedGuide = false;
            }
        }
        if (renderedGuide) {
            bufferSource.endBatch(guideType);
        }
        if (route.loop() && renderedSegments < segmentBudget) {
            BlockPos first = route.nodes().getFirst();
            BlockPos last = route.nodes().getLast();
            if (minecraft.level.hasChunkAt(last)
                    && minecraft.level.hasChunkAt(first)
                    && isVisible(event, new AABB(Vec3.atCenterOf(last), Vec3.atCenterOf(first)).inflate(1.0D))) {
                VertexConsumer loopConsumer = bufferSource.getBuffer(guideType);
                renderRouteGuide(poseStack, loopConsumer, minecraft.level, last, first, loopColor, true);
                bufferSource.endBatch(guideType);
                renderedSegments++;
            }
        }
        return renderedSegments;
    }

    private static void renderCreationRouteSegment(
            PoseStack poseStack,
            MultiBufferSource.BufferSource bufferSource,
            BlockPos first,
            BlockPos second,
            int color) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        VertexConsumer consumer = bufferSource.getBuffer(PLAYER_ROUTE_QUADS_TYPE);
        renderPlayerRouteBeam(
                poseStack,
                consumer,
                minecraft.level,
                first,
                second,
                color,
                CREATION_ROUTE_OUTLINE_HALF_WIDTH,
                CREATION_ROUTE_CORE_HALF_WIDTH);
        bufferSource.endBatch(PLAYER_ROUTE_QUADS_TYPE);
    }

    private static void renderPlayerRouteBeam(
            PoseStack poseStack,
            VertexConsumer consumer,
            Level level,
            BlockPos first,
            BlockPos second,
            int color,
            double outlineHalfWidth,
            double coreHalfWidth) {
        double heightAdjustment = PLAYER_ROUTE_HEIGHT_ABOVE_SURFACE - ROUTE_GUIDE_HEIGHT_ABOVE_SURFACE;
        List<Vec3> points = routeGuidePoints(level, first, second).stream()
                .map(point -> point.add(0.0D, heightAdjustment, 0.0D))
                .toList();
        renderRouteCuboidPath(poseStack, consumer, points, coreHalfWidth, color);
    }

    private static void renderRouteCuboidPath(
            PoseStack poseStack,
            VertexConsumer consumer,
            List<Vec3> points,
            double halfWidth,
            int color) {
        for (int index = 1; index < points.size(); index++) {
            Vec3 start = points.get(index - 1);
            Vec3 end = points.get(index);
            Vec3 difference = end.subtract(start);
            double length = difference.length();
            if (length < 1.0E-4D) {
                continue;
            }
            Vec3 direction = difference.scale(1.0D / length);
            double overlap = Math.min(PLAYER_ROUTE_SEGMENT_OVERLAP, length * 0.2D);
            renderRouteCuboid(
                    poseStack,
                    consumer,
                    start.subtract(direction.scale(overlap)),
                    end.add(direction.scale(overlap)),
                    halfWidth,
                    color);
        }
    }

    private static void renderRouteCuboid(
            PoseStack poseStack,
            VertexConsumer consumer,
            Vec3 start,
            Vec3 end,
            double halfWidth,
            int color) {
        Vec3 direction = end.subtract(start).normalize();
        Vec3 reference = Math.abs(direction.y) > 0.9D
                ? new Vec3(1.0D, 0.0D, 0.0D)
                : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 side = direction.cross(reference).normalize();
        Vec3 top = side.cross(direction).normalize();
        if (top.y < 0.0D) {
            side = side.scale(-1.0D);
            top = top.scale(-1.0D);
        }
        Vec3 sideOffset = side.scale(halfWidth);
        Vec3 topOffset = top.scale(halfWidth);

        Vec3 startSideTop = start.add(sideOffset).add(topOffset);
        Vec3 startSideBottom = start.add(sideOffset).subtract(topOffset);
        Vec3 startOppositeTop = start.subtract(sideOffset).add(topOffset);
        Vec3 startOppositeBottom = start.subtract(sideOffset).subtract(topOffset);
        Vec3 endSideTop = end.add(sideOffset).add(topOffset);
        Vec3 endSideBottom = end.add(sideOffset).subtract(topOffset);
        Vec3 endOppositeTop = end.subtract(sideOffset).add(topOffset);
        Vec3 endOppositeBottom = end.subtract(sideOffset).subtract(topOffset);

        renderRouteQuad(poseStack, consumer, startSideTop, endSideTop, endOppositeTop, startOppositeTop,
                shadeRouteColor(color, 1.0D));
        renderRouteQuad(poseStack, consumer, startSideBottom, endSideBottom, endSideTop, startSideTop,
                shadeRouteColor(color, 0.82D));
        renderRouteQuad(poseStack, consumer, startOppositeTop, endOppositeTop, endOppositeBottom, startOppositeBottom,
                shadeRouteColor(color, 0.68D));
        renderRouteQuad(poseStack, consumer, startOppositeBottom, endOppositeBottom, endSideBottom, startSideBottom,
                shadeRouteColor(color, 0.52D));
        renderRouteQuad(poseStack, consumer, startOppositeTop, startOppositeBottom, startSideBottom, startSideTop,
                shadeRouteColor(color, 0.74D));
        renderRouteQuad(poseStack, consumer, endSideTop, endSideBottom, endOppositeBottom, endOppositeTop,
                shadeRouteColor(color, 0.88D));
    }

    private static void renderPlayerBranchNode(
            PoseStack poseStack,
            VertexConsumer consumer,
            Level level,
            BlockPos node,
            int color) {
        double halfSize = 0.09D;
        Vec3 center = new Vec3(
                node.getX() + 0.5D,
                routeGuideSurfaceY(level, node.getX(), node.getY(), node.getZ())
                        + PLAYER_ROUTE_HEIGHT_ABOVE_SURFACE
                        - ROUTE_GUIDE_HEIGHT_ABOVE_SURFACE
                        + 0.02D,
                node.getZ() + 0.5D);
        renderAxisAlignedRouteCuboid(
                poseStack,
                consumer,
                center.x - halfSize,
                center.y - halfSize,
                center.z - halfSize,
                center.x + halfSize,
                center.y + halfSize,
                center.z + halfSize,
                color);
    }

    private static void renderPlayerRouteNode(
            PoseStack poseStack,
            VertexConsumer consumer,
            Level level,
            RoutePosition route,
            int nodeIndex,
            int color) {
        BlockPos node = route.nodes().get(nodeIndex);
        double halfSize = 0.09D;
        Vec3 center = new Vec3(
                node.getX() + 0.5D,
                routeGuideSurfaceY(level, node.getX(), node.getY(), node.getZ())
                        + PLAYER_ROUTE_HEIGHT_ABOVE_SURFACE
                        - ROUTE_GUIDE_HEIGHT_ABOVE_SURFACE
                        + 0.02D,
                node.getZ() + 0.5D);
        renderAxisAlignedRouteCuboid(
                poseStack,
                consumer,
                center.x - halfSize,
                center.y - halfSize,
                center.z - halfSize,
                center.x + halfSize,
                center.y + halfSize,
                center.z + halfSize,
                color);
    }

    private static void renderAxisAlignedRouteCuboid(
            PoseStack poseStack,
            VertexConsumer consumer,
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ,
            int color) {
        Vec3 bottomNorthWest = new Vec3(minX, minY, minZ);
        Vec3 bottomNorthEast = new Vec3(maxX, minY, minZ);
        Vec3 bottomSouthEast = new Vec3(maxX, minY, maxZ);
        Vec3 bottomSouthWest = new Vec3(minX, minY, maxZ);
        Vec3 topNorthWest = new Vec3(minX, maxY, minZ);
        Vec3 topNorthEast = new Vec3(maxX, maxY, minZ);
        Vec3 topSouthEast = new Vec3(maxX, maxY, maxZ);
        Vec3 topSouthWest = new Vec3(minX, maxY, maxZ);
        renderRouteQuad(poseStack, consumer, topNorthWest, topSouthWest, topSouthEast, topNorthEast,
                shadeRouteColor(color, 1.0D));
        renderRouteQuad(poseStack, consumer, bottomNorthEast, bottomSouthEast, bottomSouthWest, bottomNorthWest,
                shadeRouteColor(color, 0.5D));
        renderRouteQuad(poseStack, consumer, bottomNorthWest, bottomSouthWest, topSouthWest, topNorthWest,
                shadeRouteColor(color, 0.72D));
        renderRouteQuad(poseStack, consumer, bottomSouthEast, bottomNorthEast, topNorthEast, topSouthEast,
                shadeRouteColor(color, 0.82D));
        renderRouteQuad(poseStack, consumer, bottomNorthEast, bottomNorthWest, topNorthWest, topNorthEast,
                shadeRouteColor(color, 0.66D));
        renderRouteQuad(poseStack, consumer, bottomSouthWest, bottomSouthEast, topSouthEast, topSouthWest,
                shadeRouteColor(color, 0.88D));
    }

    private static int shadeRouteColor(int color, double brightness) {
        int alpha = color >> 24 & 0xFF;
        int red = (int) Math.round((color >> 16 & 0xFF) * brightness);
        int green = (int) Math.round((color >> 8 & 0xFF) * brightness);
        int blue = (int) Math.round((color & 0xFF) * brightness);
        return alpha << 24
                | Math.min(255, red) << 16
                | Math.min(255, green) << 8
                | Math.min(255, blue);
    }

    private static void renderRouteQuad(
            PoseStack poseStack,
            VertexConsumer consumer,
            Vec3 first,
            Vec3 second,
            Vec3 third,
            Vec3 fourth,
            int color) {
        renderRouteQuadVertex(poseStack, consumer, first, color);
        renderRouteQuadVertex(poseStack, consumer, second, color);
        renderRouteQuadVertex(poseStack, consumer, third, color);
        renderRouteQuadVertex(poseStack, consumer, fourth, color);
    }

    private static void renderRouteQuadVertex(
            PoseStack poseStack,
            VertexConsumer consumer,
            Vec3 point,
            int color) {
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        int alpha = color >> 24 & 0xFF;
        consumer.addVertex(poseStack.last(), (float) point.x, (float) point.y, (float) point.z)
                .setColor(red, green, blue, alpha);
    }

    private static void renderRouteReach(RenderLevelStageEvent event, BlockPos center) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !minecraft.level.hasChunkAt(center)) {
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(ROUTE_REACH_TYPE);
        for (Vec3 point : routeReachPoints(center, minecraft.player.getY() + 0.1D)) {
            renderRouteGuideVertex(poseStack, consumer, point, ROUTE_REACH_COLOR);
        }
        bufferSource.endBatch(ROUTE_REACH_TYPE);
        poseStack.popPose();
    }

    private static List<Vec3> routeReachPoints(BlockPos center, double height) {
        List<Vec3> points = new ArrayList<>(ROUTE_REACH_SEGMENTS + 1);
        for (int segment = 0; segment <= ROUTE_REACH_SEGMENTS; segment++) {
            double angle = Math.PI * 2.0D * segment / ROUTE_REACH_SEGMENTS;
            double x = center.getX() + 0.5D + Math.cos(angle) * HiredRoute.MAX_NODE_DISTANCE;
            double z = center.getZ() + 0.5D + Math.sin(angle) * HiredRoute.MAX_NODE_DISTANCE;
            points.add(new Vec3(x, height, z));
        }
        return points;
    }

    private static void renderRouteGuide(PoseStack poseStack, VertexConsumer consumer, BlockPos first, BlockPos second, int color) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        renderRouteGuide(poseStack, consumer, minecraft.level, first, second, color, true);
    }

    private static void renderRouteGuide(
            PoseStack poseStack,
            VertexConsumer consumer,
            Level level,
            BlockPos first,
            BlockPos second,
            int color,
            boolean includeStart) {
        List<Vec3> points = routeGuidePoints(level, first, second);
        int firstPoint = includeStart ? 0 : 1;
        for (int index = firstPoint; index < points.size(); index++) {
            renderRouteGuideVertex(poseStack, consumer, points.get(index), color);
        }
    }

    private static List<Vec3> routeGuidePoints(Level level, BlockPos first, BlockPos second) {
        RouteGuideCacheKey key = new RouteGuideCacheKey(level.dimension(), first, second);
        long gameTime = level.getGameTime();
        CachedRouteGuide cached = ROUTE_GUIDE_CACHE.get(key);
        if (cached != null && gameTime <= cached.validUntilGameTime()) {
            return cached.points();
        }
        int steps = routeGuideSteps(first, second);
        List<Vec3> points = new ArrayList<>(steps + 1);
        for (int step = 0; step <= steps; step++) {
            points.add(routeGuidePoint(level, first, second, step / (double) steps));
        }
        List<Vec3> immutablePoints = List.copyOf(points);
        if (ROUTE_GUIDE_CACHE.size() >= MAX_ROUTE_GUIDE_CACHE_ENTRIES) {
            ROUTE_GUIDE_CACHE.clear();
        }
        ROUTE_GUIDE_CACHE.put(key, new CachedRouteGuide(gameTime + ROUTE_GUIDE_CACHE_TICKS, immutablePoints));
        return immutablePoints;
    }

    private static int routeGuideSteps(BlockPos first, BlockPos second) {
        int xDistance = Math.abs(second.getX() - first.getX());
        int yDistance = Math.abs(second.getY() - first.getY());
        int zDistance = Math.abs(second.getZ() - first.getZ());
        return Math.max(1, Math.max(yDistance, Math.max(xDistance, zDistance)));
    }

    private static Vec3 routeGuidePoint(Level level, BlockPos first, BlockPos second, double progress) {
        int sampleX = routeGuideSampleCoordinate(first.getX(), second.getX(), progress);
        int sampleZ = routeGuideSampleCoordinate(first.getZ(), second.getZ(), progress);
        int expectedGroundY = (int) Math.round(first.getY() + (second.getY() - first.getY()) * progress);
        return new Vec3(
                sampleX + 0.5D,
                routeGuideSurfaceY(level, sampleX, expectedGroundY, sampleZ),
                sampleZ + 0.5D);
    }

    private static int routeGuideSampleCoordinate(int first, int second, double progress) {
        return (int) Math.round(first + (second - first) * progress);
    }

    private static double routeGuideSurfaceY(Level level, int x, int expectedGroundY, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int offset = 0; offset <= Math.max(ROUTE_GUIDE_SURFACE_SEARCH_UP, ROUTE_GUIDE_SURFACE_SEARCH_DOWN); offset++) {
            if (offset <= ROUTE_GUIDE_SURFACE_SEARCH_UP) {
                double surfaceY = routeGuideSurfaceYAt(level, pos, x, expectedGroundY + offset, z);
                if (!Double.isNaN(surfaceY)) {
                    return surfaceY;
                }
            }
            if (offset > 0 && offset <= ROUTE_GUIDE_SURFACE_SEARCH_DOWN) {
                double surfaceY = routeGuideSurfaceYAt(level, pos, x, expectedGroundY - offset, z);
                if (!Double.isNaN(surfaceY)) {
                    return surfaceY;
                }
            }
        }
        return expectedGroundY + 1.0D + ROUTE_GUIDE_HEIGHT_ABOVE_SURFACE;
    }

    private static double routeGuideSurfaceYAt(Level level, BlockPos.MutableBlockPos pos, int x, int y, int z) {
        pos.set(x, y, z);
        if (!level.hasChunkAt(pos)) {
            return Double.NaN;
        }
        BlockState state = level.getBlockState(pos);
        VoxelShape collisionShape = state.getCollisionShape(level, pos);
        if (collisionShape.isEmpty()) {
            return Double.NaN;
        }
        return y + collisionShape.bounds().maxY + ROUTE_GUIDE_HEIGHT_ABOVE_SURFACE;
    }

    private static void renderRouteGuideVertex(PoseStack poseStack, VertexConsumer consumer, Vec3 point, int color) {
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        int alpha = (color >> 24) & 0xFF;
        consumer.addVertex(poseStack.last(), (float) point.x, (float) point.y, (float) point.z)
                .setColor(red, green, blue, alpha);
    }

    private static int playerRouteNodeColor(RoutePosition route, int index, int routeColor) {
        if (routeColor == ROUTE_INVALID_COLOR) {
            return ROUTE_INVALID_COLOR;
        }
        if (routeColor == PLAYER_ROUTE_CREATION_COLOR) {
            return PLAYER_ROUTE_CREATION_COLOR;
        }
        if (route.loop() && index == 0) {
            return PLAYER_ROUTE_LOOP_COLOR;
        }
        if (index == 0) {
            return PLAYER_ROUTE_FIRST_COLOR;
        }
        if (!route.loop() && index == route.nodes().size() - 1) {
            return PLAYER_ROUTE_LAST_COLOR;
        }
        return PLAYER_ROUTE_NODE_COLOR;
    }

    private static int playerRouteLoopColor(int routeColor) {
        if (routeColor == ROUTE_INVALID_COLOR || routeColor == PLAYER_ROUTE_CREATION_COLOR) {
            return routeColor;
        }
        return PLAYER_ROUTE_LOOP_COLOR;
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

    private static void renderDebugRouteNode(PoseStack poseStack, MultiBufferSource bufferSource, BlockPos pos) {
        renderDebugRouteNode(poseStack, bufferSource, pos, 0xFF0000FF);
    }

    private static void renderDebugBranchNode(PoseStack poseStack, MultiBufferSource bufferSource, BlockPos pos) {
        renderDebugRouteNode(poseStack, bufferSource, pos, PLAYER_BRANCH_NODE_COLOR);
    }

    private static void renderDebugRouteNode(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            BlockPos pos,
            int color) {
        AABB box = debugRouteNodeBox(pos);
        LevelRenderer.addChainedFilledBoxVertices(
                poseStack,
                bufferSource.getBuffer(RenderType.debugFilledBox()),
                box.minX,
                box.minY,
                box.minZ,
                box.maxX,
                box.maxY,
                box.maxZ,
                ((color >> 16) & 0xFF) / 255.0F,
                ((color >> 8) & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F,
                DEBUG_ROUTE_NODE_ALPHA);
    }

    private static AABB debugRouteNodeBox(BlockPos pos) {
        return new AABB(
                pos.getX() + 0.5D - DEBUG_ROUTE_NODE_HALF_WIDTH,
                pos.getY() + 1.01D,
                pos.getZ() + 0.5D - DEBUG_ROUTE_NODE_HALF_WIDTH,
                pos.getX() + 0.5D + DEBUG_ROUTE_NODE_HALF_WIDTH,
                pos.getY() + 1.01D + DEBUG_ROUTE_NODE_HEIGHT,
                pos.getZ() + 0.5D + DEBUG_ROUTE_NODE_HALF_WIDTH);
    }

    private static void renderDebugRouteNodeLabels(RenderLevelStageEvent event, List<RoutePosition> routes) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || routes.isEmpty()) {
            return;
        }
        List<DebugLabelPosition> labels = new ArrayList<>();
        ResourceKey<Level> currentDimension = minecraft.level.dimension();
        Vec3 camera = event.getCamera().getPosition();
        for (RoutePosition route : routes) {
            if (!route.dimension().equals(currentDimension)) {
                continue;
            }
            for (int index = 0; index < route.nodes().size(); index++) {
                BlockPos node = route.nodes().get(index);
                Vec3 labelPos = new Vec3(
                        node.getX() + 0.5D,
                        node.getY() + DEBUG_ROUTE_LABEL_HEIGHT,
                        node.getZ() + 0.5D);
                if (minecraft.level.hasChunkAt(node)
                        && labelPos.distanceToSqr(camera) <= MAX_LABEL_DISTANCE_SQR
                        && isVisible(event, markerBox(node).inflate(1.0D))) {
                    addDebugRouteNodeLabel(labels, routeLabelName(route), node, index + 1);
                }
            }
            for (int index = 0; index < route.branches().size(); index++) {
                BlockPos branchEnd = route.branches().get(index).end();
                Vec3 labelPos = new Vec3(
                        branchEnd.getX() + 0.5D,
                        branchEnd.getY() + DEBUG_ROUTE_LABEL_HEIGHT,
                        branchEnd.getZ() + 0.5D);
                if (minecraft.level.hasChunkAt(branchEnd)
                        && labelPos.distanceToSqr(camera) <= MAX_LABEL_DISTANCE_SQR
                        && isVisible(event, markerBox(branchEnd).inflate(1.0D))) {
                    addDebugBranchNodeLabel(labels, routeLabelName(route), branchEnd, index + 1);
                }
            }
        }
        renderLabels(event, labels);
    }

    private static void renderDebugRouteNodeLabel(RenderLevelStageEvent event, BlockPos node, String labelName, int nodeIndex) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.level.hasChunkAt(node)) {
            List<DebugLabelPosition> labels = new ArrayList<>();
            addDebugRouteNodeLabel(labels, labelName, node, nodeIndex);
            renderLabels(event, labels);
        }
    }

    private static void addDebugRouteNodeLabel(List<DebugLabelPosition> labels, String labelName, BlockPos node, int nodeIndex) {
        labels.add(new DebugLabelPosition(
                new Vec3(node.getX() + 0.5D, node.getY() + DEBUG_ROUTE_LABEL_HEIGHT, node.getZ() + 0.5D),
                labelName,
                "Node #" + Math.max(1, nodeIndex),
                DEBUG_ROUTE_LABEL_COLOR
        ));
    }

    private static void addDebugBranchNodeLabel(
            List<DebugLabelPosition> labels,
            String labelName,
            BlockPos node,
            int branchIndex) {
        labels.add(new DebugLabelPosition(
                new Vec3(node.getX() + 0.5D, node.getY() + DEBUG_ROUTE_LABEL_HEIGHT, node.getZ() + 0.5D),
                labelName,
                "Branch #" + Math.max(1, branchIndex),
                PLAYER_BRANCH_NODE_COLOR
        ));
    }

    private static String routeLabelName(RoutePosition route) {
        return route.ownerName().isBlank() ? "Route" : route.ownerName();
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
            AABB bounds = blockOutlineBox(level, pos, state);
            if (state.getBlock() instanceof ChestBlock
                    && state.hasProperty(ChestBlock.TYPE)
                    && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                BlockPos connectedPos = pos.relative(ChestBlock.getConnectedDirection(state));
                if (level.hasChunkAt(connectedPos)) {
                    BlockState connectedState = level.getBlockState(connectedPos);
                    if (connectedState.getBlock() instanceof ChestBlock
                            && connectedState.hasProperty(ChestBlock.TYPE)
                            && connectedState.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                        bounds = bounds.minmax(blockOutlineBox(level, connectedPos, connectedState));
                    }
                }
            }
            if (bounds.getSize() > 0.0D) {
                return bounds.inflate(0.003D);
            }
        }
        return new AABB(pos).inflate(0.003D);
    }

    private static AABB blockOutlineBox(Level level, BlockPos pos, BlockState state) {
        VoxelShape shape = state.getShape(level, pos);
        return shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);
    }

    private static boolean isHoldingClipboard(Minecraft minecraft) {
        return VillagerRetaliationItems.isClipboard(minecraft.player.getMainHandItem())
                || VillagerRetaliationItems.isClipboard(minecraft.player.getOffhandItem());
    }

    private static ItemStack clipboardStack(Minecraft minecraft) {
        ItemStack mainHand = minecraft.player.getMainHandItem();
        return VillagerRetaliationItems.isClipboard(mainHand) ? mainHand : minecraft.player.getOffhandItem();
    }

    private static boolean showVillagerNames() {
        return VillagerRetaliationServerConfigClient.showVillagerNameTags()
                && VillagerRetaliationClientPreferences.showVillagerNameTags();
    }

    public enum ClipboardPreviewLens {
        NONE,
        WORKFORCE,
        ASSIGNMENTS,
        PROBLEMS
    }

    private record RouteEdge(int first, int second) {
        private static RouteEdge of(int first, int second) {
            return first <= second ? new RouteEdge(first, second) : new RouteEdge(second, first);
        }
    }

    private record ActiveRouteTargets(Set<Integer> nodes, Set<RouteEdge> edges, Set<Integer> branches) {
        private static final ActiveRouteTargets EMPTY = new ActiveRouteTargets(Set.of(), Set.of(), Set.of());
    }

    public record WorkforceMarker(
            UUID villagerId,
            ResourceKey<Level> dimension,
            BlockPos pos,
            String ownerName,
            String jobName,
            String status,
            BlockPos targetPos,
            boolean warning) {
        public WorkforceMarker {
            villagerId = villagerId == null ? new UUID(0L, 0L) : villagerId;
            pos = pos.immutable();
            ownerName = ownerName == null ? "" : ownerName;
            jobName = jobName == null ? "" : jobName;
            status = status == null ? "" : status;
            targetPos = targetPos == null ? null : targetPos.immutable();
        }
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

    private record RoutePosition(
            ResourceKey<Level> dimension,
            List<BlockPos> nodes,
            boolean loop,
            List<HiredRoute.Branch> branches,
            String ownerName,
            String jobName) {
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
            branches = branches == null ? List.of() : List.copyOf(branches);
            ownerName = ownerName == null ? "" : ownerName;
            jobName = jobName == null ? "" : jobName;
        }
    }

    private record RoutePreview(
            BlockPos target,
            BlockPos from,
            boolean valid,
            boolean showTargetMarker,
            int nodeIndex,
            String hint) {
    }

    private record RoutePreviewKey(ResourceKey<Level> dimension, List<BlockPos> nodes, boolean loop) {
        private static RoutePreviewKey of(RouteDraft draft) {
            return new RoutePreviewKey(draft.dimension(), List.copyOf(draft.route().nodes()), draft.route().loop());
        }
    }

    private record DebugLabelPosition(Vec3 pos, String ownerName, String jobName, int color, BlockPos hoverTarget) {
        private DebugLabelPosition(Vec3 pos, String ownerName, String jobName, int color) {
            this(pos, ownerName, jobName, color, null);
        }
    }

    private record RouteGuideCacheKey(ResourceKey<Level> dimension, BlockPos first, BlockPos second) {
        private RouteGuideCacheKey {
            first = first.immutable();
            second = second.immutable();
        }
    }

    private record CachedRouteGuide(long validUntilGameTime, List<Vec3> points) {
    }

    private record OwnerNameLayout(List<String> collapsedLines, List<String> expandedLines) {
    }

    private record ResolvedWorkforceMarker(WorkforceMarker marker, AABB box, Vec3 labelPos) {
    }
}
