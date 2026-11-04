package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.client.PreviewMeshCache;
import com.jvn.villagerretaliation.client.item.ConstructionBlueprintPreviewRenderer;
import com.jvn.villagerretaliation.item.ConstructionBlueprintItem;
import com.jvn.villagerretaliation.network.SettlementPathResultPayload;
import com.jvn.villagerretaliation.settlement.SettlementPathTerrain;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

final class PreviewMeshClientChecks {
    private PreviewMeshClientChecks() {}

    static void verify(RenderLevelStageEvent event, BlockPos center) {
        PreviewMeshCache.clear();
        event = visibility(event, true);
        var paths = new SettlementPathEditor();
        var cells = new ArrayList<SettlementPathResultPayload.Cell>();
        var blocks = new ArrayList<ConstructionBlueprintItem.PreviewBlock>();
        boolean stress = Boolean.getBoolean("villagerretaliation.settlementPreviewStress");
        int pathSide = stress ? 64 : 18;
        int ghostSide = stress ? 32 : 18;
        int ghostHeight = stress ? 16 : 2;
        for (int x = 0; x < pathSide; x++) {
            for (int z = 0; z < pathSide; z++) {
                cells.add(new SettlementPathResultPayload.Cell(center.offset(x - pathSide / 2, 0, z - pathSide / 2),
                        SettlementPathTerrain.Kind.values()[(x + z) % SettlementPathTerrain.Kind.values().length]));
            }
        }
        for (int x = 0; x < ghostSide; x++) {
            for (int z = 0; z < ghostSide; z++) {
                for (int y = 0; y < ghostHeight; y++) {
                    var state = (x + y + z) % 3 == 0 ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
                    blocks.add(new ConstructionBlueprintItem.PreviewBlock(new BlockPos(x, y, z), state));
                }
            }
        }
        set(paths, "cells", List.copyOf(cells));
        var maximum = new BlockPos(ghostSide - 1, ghostHeight - 1, ghostSide - 1);
        var wall = preview(center.offset(-16, 16, -16), maximum, blocks);
        var building = preview(center.offset(-8, 36, -8), maximum, blocks);
        long started = stress ? System.nanoTime() : 0;
        render(event, paths, wall, building);
        long buildNanos = stress ? System.nanoTime() - started : 0;
        var pathMesh = mesh(paths);
        var wallMesh = mesh(wall.jobId());
        var buildingMesh = mesh(building.jobId());
        var wallBuffers = buffers(wallMesh);
        require(!wallBuffers.isEmpty() && wallBuffers.stream().noneMatch(VertexBuffer::isInvalid), "preview uploads native meshes");
        require(((Map<?, ?>) get(wallMesh, "sections")).size() > 1, "the ghost fixture exercises multiple sections");
        var wallLayers = layers(wallMesh);
        require(wallLayers.stream().anyMatch(layer -> get(layer, "sortedFrom") != null), "visible ghost meshes are submitted");
        for (var layer : wallLayers) set(layer, "sortedFrom", null);
        render(visibility(event, false), paths, wall, building);
        require(wallLayers.stream().allMatch(layer -> get(layer, "sortedFrom") == null), "off-screen sections skip sorting and draw submission");
        render(event, paths, wall, building);
        started = stress ? System.nanoTime() : 0;
        int submissions = stress ? 8 : 1;
        for (int i = 0; i < submissions; i++) render(event, paths, wall, building);
        long cachedNanos = stress ? (System.nanoTime() - started) / submissions : 0;
        require(mesh(paths) == pathMesh && mesh(wall.jobId()) == wallMesh && mesh(building.jobId()) == buildingMesh,
                "unchanged paths and ghosts reuse their uploaded meshes");
        if (stress) LogUtils.getLogger().info("Preview stress checks: first build {} ms, cached submission {} ms, {} wall buffers",
                buildNanos / 1_000_000.0, cachedNanos / 1_000_000.0, wallBuffers.size());

        cells.set(0, new SettlementPathResultPayload.Cell(center.above(2), SettlementPathTerrain.Kind.OUTSIDE));
        set(paths, "cells", List.copyOf(cells));
        render(event, paths, wall, building);
        require(mesh(paths) != pathMesh && mesh(wall.jobId()) == wallMesh, "editing paths only rebuilds their own geometry");

        var level = Minecraft.getInstance().level;
        var position = wall.origin();
        var before = level.getBlockState(position);
        try {
            level.setBlock(position, wall.blocks().getFirst().state(), 2);
            set(entry(wall.jobId()), "checkedAt", 0L);
            render(event, paths, wall, building);
            require(mesh(wall.jobId()) != wallMesh, "changed terrain invalidates cached ghost geometry");
            require(wallBuffers.stream().allMatch(VertexBuffer::isInvalid), "replaced meshes release their GPU buffers");
        } finally {
            level.setBlock(position, before, 2);
        }

        var retained = buffers(mesh(building.jobId()));
        PreviewMeshCache.clear();
        require(retained.stream().allMatch(VertexBuffer::isInvalid), "resource reload cleanup closes uploaded meshes");
        render(event, paths, wall, building);
        require(mesh(building.jobId()) != buildingMesh, "previews rebuild after resource reload cleanup");
        var rebuilt = buffers(mesh(building.jobId()));
        for (var entry : cache().values()) set(entry, "usedAt", 0L);
        PreviewMeshCache.tick(null);
        require(cache().isEmpty() && rebuilt.stream().allMatch(VertexBuffer::isInvalid), "inactive previews release cached GPU memory");
        LogUtils.getLogger().info("Preview mesh cache client checks passed");
    }

    private static ConstructionBlueprintItem.PreviewData preview(BlockPos origin, BlockPos maximum, List<ConstructionBlueprintItem.PreviewBlock> blocks) {
        return new ConstructionBlueprintItem.PreviewData(UUID.randomUUID(), UUID.randomUUID(), Minecraft.getInstance().level.dimension(),
                ResourceLocation.fromNamespaceAndPath("villagerretaliation", "preview_stress"), "Preview stress", origin, Rotation.NONE,
                BlockPos.ZERO, maximum, blocks.size(), 0, 0, "", "", "", false, false, false, false, List.copyOf(blocks));
    }

    private static void render(RenderLevelStageEvent event, SettlementPathEditor paths,
                               ConstructionBlueprintItem.PreviewData wall, ConstructionBlueprintItem.PreviewData building) {
        SettlementPathGizmo.renderTerrain(paths, event);
        ConstructionBlueprintPreviewRenderer.renderPreview(event, wall, false);
        ConstructionBlueprintPreviewRenderer.renderPreview(event, building, false);
    }

    private static RenderLevelStageEvent visibility(RenderLevelStageEvent event, boolean visible) {
        var frustum = new net.minecraft.client.renderer.culling.Frustum(event.getFrustum()) {
            @Override public boolean isVisible(net.minecraft.world.phys.AABB bounds) { return visible; }
        };
        return new RenderLevelStageEvent(event.getStage(), event.getLevelRenderer(), event.getPoseStack(),
                event.getModelViewMatrix(), event.getProjectionMatrix(), event.getRenderTick(), event.getPartialTick(), event.getCamera(), frustum);
    }

    private static List<Object> layers(Object mesh) {
        var layers = new ArrayList<Object>();
        for (var section : ((Map<?, ?>) get(mesh, "sections")).values())
            layers.addAll(((Map<?, ?>) get(section, "layers")).values());
        return layers;
    }

    private static List<VertexBuffer> buffers(Object mesh) {
        return layers(mesh).stream().map(layer -> (VertexBuffer) get(layer, "buffer")).filter(java.util.Objects::nonNull).toList();
    }

    private static Map<?, ?> cache() { return (Map<?, ?>) get(get(get(null, "CACHE"), "entries"), "entries"); }
    private static Object entry(Object owner) { return cache().get(owner); }
    private static Object mesh(Object owner) { return get(entry(owner), "mesh"); }

    private static Object get(Object target, String name) {
        try {
            var field = (target == null ? PreviewMeshCache.class : target.getClass()).getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    private static void set(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
