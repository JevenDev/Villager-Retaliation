package com.jvn.villagerretaliation.client.allegiance;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.network.VillageBoundsSubscriptionPayload;
import com.jvn.villagerretaliation.network.VillageBoundsSyncPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

public final class VillageBoundsDebugRenderer {
    private static final int OUTLINE_COLOR = 0xFFFFD54A;
    private static final int CENTER_COLOR = 0xFFFFF176;
    private static final List<VillageGeometry> VILLAGES = new ArrayList<>();
    private static boolean subscribed;
    private static ResourceLocation subscribedDimension;
    private static ResourceLocation payloadDimension;
    private static long visibleUntilMillis;

    private VillageBoundsDebugRenderer() {
    }

    public static void accept(VillageBoundsSyncPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!payload.enabled()) {
            clearGeometry();
            subscribed = false;
            return;
        }
        if (!VillagerRetaliationConfig.SHOW_VILLAGE_BOUNDS.get()
                || minecraft.level == null
                || !minecraft.level.dimension().location().equals(payload.dimension())) {
            clearGeometry();
            return;
        }
        VILLAGES.clear();
        for (VillageBoundsSyncPayload.VillageEntry entry : payload.villages()) {
            VILLAGES.add(buildGeometry(entry));
        }
        payloadDimension = payload.dimension();
        visibleUntilMillis = Util.getMillis() + payload.visibleTicks() * 50L;
        subscribed = true;
        subscribedDimension = payload.dimension();
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            clearAll();
            return;
        }
        boolean enabled = VillagerRetaliationConfig.SHOW_VILLAGE_BOUNDS.get();
        ResourceLocation dimension = minecraft.level.dimension().location();
        if (!enabled) {
            if (subscribed) {
                PacketDistributor.sendToServer(new VillageBoundsSubscriptionPayload(false));
            }
            clearAll();
            return;
        }
        if (!subscribed || !dimension.equals(subscribedDimension)) {
            clearGeometry();
            subscribed = true;
            subscribedDimension = dimension;
            PacketDistributor.sendToServer(new VillageBoundsSubscriptionPayload(true));
        }
        expireIfStale();
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clearAll();
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || !VillagerRetaliationConfig.SHOW_VILLAGE_BOUNDS.get()) {
            return;
        }
        expireIfStale();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || VILLAGES.isEmpty()
                || !minecraft.level.dimension().location().equals(payloadDimension)) {
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.lines());
        for (VillageGeometry village : VILLAGES) {
            for (LineSegment segment : village.boundary()) {
                renderLine(poseStack, consumer, segment, OUTLINE_COLOR);
            }
            renderMarker(poseStack, consumer, village.center(), CENTER_COLOR);
        }
        poseStack.popPose();
        buffers.endBatch(RenderType.lines());
    }

    public static void onRenderGuiLayer(RenderGuiLayerEvent.Post event) {
        if (!VanillaGuiLayers.SAVING_INDICATOR.equals(event.getName())
                || !VillagerRetaliationConfig.SHOW_VILLAGE_BOUNDS.get()) {
            return;
        }
        expireIfStale();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.options.hideGui
                || !minecraft.level.dimension().location().equals(payloadDimension)) {
            return;
        }
        VillageGeometry containing = VILLAGES.stream()
                .filter(village -> village.sections().contains(SectionPos.asLong(minecraft.player.blockPosition())))
                .findFirst().orElse(null);
        if (containing == null || containing.name().isBlank()) {
            return;
        }
        Font font = minecraft.font;
        int centerX = minecraft.getWindow().getGuiScaledWidth() / 2;
        int x = centerX - font.width(containing.name()) / 2;
        event.getGuiGraphics().drawString(font, containing.name(), x, 8, OUTLINE_COLOR, true);
    }

    private static VillageGeometry buildGeometry(VillageBoundsSyncPayload.VillageEntry entry) {
        Set<Long> sections = new LinkedHashSet<>(entry.sections());
        Map<EdgeKey, EdgeInfo> edges = new HashMap<>();
        for (long packed : sections) {
            SectionPos section = SectionPos.of(packed);
            int sx = section.x();
            int sy = section.y();
            int sz = section.z();
            int x0 = SectionPos.sectionToBlockCoord(sx);
            int y0 = SectionPos.sectionToBlockCoord(sy);
            int z0 = SectionPos.sectionToBlockCoord(sz);
            int x1 = x0 + 16;
            int y1 = y0 + 16;
            int z1 = z0 + 16;
            if (!sections.contains(SectionPos.asLong(sx - 1, sy, sz))) {
                addFace(edges, 0, new IntPoint(x0, y0, z0), new IntPoint(x0, y1, z0),
                        new IntPoint(x0, y1, z1), new IntPoint(x0, y0, z1));
            }
            if (!sections.contains(SectionPos.asLong(sx + 1, sy, sz))) {
                addFace(edges, 1, new IntPoint(x1, y0, z0), new IntPoint(x1, y0, z1),
                        new IntPoint(x1, y1, z1), new IntPoint(x1, y1, z0));
            }
            if (!sections.contains(SectionPos.asLong(sx, sy - 1, sz))) {
                addFace(edges, 2, new IntPoint(x0, y0, z0), new IntPoint(x0, y0, z1),
                        new IntPoint(x1, y0, z1), new IntPoint(x1, y0, z0));
            }
            if (!sections.contains(SectionPos.asLong(sx, sy + 1, sz))) {
                addFace(edges, 3, new IntPoint(x0, y1, z0), new IntPoint(x1, y1, z0),
                        new IntPoint(x1, y1, z1), new IntPoint(x0, y1, z1));
            }
            if (!sections.contains(SectionPos.asLong(sx, sy, sz - 1))) {
                addFace(edges, 4, new IntPoint(x0, y0, z0), new IntPoint(x1, y0, z0),
                        new IntPoint(x1, y1, z0), new IntPoint(x0, y1, z0));
            }
            if (!sections.contains(SectionPos.asLong(sx, sy, sz + 1))) {
                addFace(edges, 5, new IntPoint(x0, y0, z1), new IntPoint(x0, y1, z1),
                        new IntPoint(x1, y1, z1), new IntPoint(x1, y0, z1));
            }
        }
        List<LineSegment> boundary = edges.entrySet().stream()
                .filter(edge -> edge.getValue().count == 1 || edge.getValue().faceNormals.size() > 1)
                .map(edge -> new LineSegment(edge.getKey().first, edge.getKey().second))
                .toList();
        return new VillageGeometry(entry.name(), entry.center(), Set.copyOf(sections), boundary);
    }

    private static void addFace(
            Map<EdgeKey, EdgeInfo> edges,
            int normal,
            IntPoint first,
            IntPoint second,
            IntPoint third,
            IntPoint fourth) {
        addEdge(edges, normal, first, second);
        addEdge(edges, normal, second, third);
        addEdge(edges, normal, third, fourth);
        addEdge(edges, normal, fourth, first);
    }

    private static void addEdge(Map<EdgeKey, EdgeInfo> edges, int normal, IntPoint first, IntPoint second) {
        EdgeKey key = EdgeKey.of(first, second);
        EdgeInfo info = edges.computeIfAbsent(key, ignored -> new EdgeInfo());
        info.count++;
        info.faceNormals.add(normal);
    }

    private static void renderLine(PoseStack poseStack, VertexConsumer consumer, LineSegment segment, int color) {
        float dx = segment.second().x() - segment.first().x();
        float dy = segment.second().y() - segment.first().y();
        float dz = segment.second().z() - segment.first().z();
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length <= 0.0F) {
            return;
        }
        dx /= length;
        dy /= length;
        dz /= length;
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        int alpha = color >>> 24;
        consumer.addVertex(poseStack.last(), segment.first().x(), segment.first().y(), segment.first().z())
                .setColor(red, green, blue, alpha).setNormal(poseStack.last(), dx, dy, dz);
        consumer.addVertex(poseStack.last(), segment.second().x(), segment.second().y(), segment.second().z())
                .setColor(red, green, blue, alpha).setNormal(poseStack.last(), dx, dy, dz);
    }

    private static void renderMarker(PoseStack poseStack, VertexConsumer consumer, BlockPos center, int color) {
        float red = (color >> 16 & 0xFF) / 255.0F;
        float green = (color >> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float alpha = (color >>> 24) / 255.0F;
        AABB marker = new AABB(center).inflate(0.01D);
        LevelRenderer.renderLineBox(poseStack, consumer, marker, red, green, blue, alpha);
    }

    private static void expireIfStale() {
        if (visibleUntilMillis > 0L && Util.getMillis() > visibleUntilMillis) {
            clearGeometry();
        }
    }

    private static void clearGeometry() {
        VILLAGES.clear();
        payloadDimension = null;
        visibleUntilMillis = 0L;
    }

    private static void clearAll() {
        clearGeometry();
        subscribed = false;
        subscribedDimension = null;
    }

    private record VillageGeometry(String name, BlockPos center, Set<Long> sections, List<LineSegment> boundary) {
    }

    private record LineSegment(IntPoint first, IntPoint second) {
    }

    private record IntPoint(int x, int y, int z) implements Comparable<IntPoint> {
        @Override
        public int compareTo(IntPoint other) {
            int xCompare = Integer.compare(this.x, other.x);
            if (xCompare != 0) return xCompare;
            int yCompare = Integer.compare(this.y, other.y);
            return yCompare != 0 ? yCompare : Integer.compare(this.z, other.z);
        }
    }

    private record EdgeKey(IntPoint first, IntPoint second) {
        private static EdgeKey of(IntPoint first, IntPoint second) {
            return first.compareTo(second) <= 0 ? new EdgeKey(first, second) : new EdgeKey(second, first);
        }
    }

    private static final class EdgeInfo {
        private int count;
        private final Set<Integer> faceNormals = new HashSet<>();
    }
}
