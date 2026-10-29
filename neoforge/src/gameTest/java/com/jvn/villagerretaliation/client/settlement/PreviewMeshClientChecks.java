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
        for (int x = 0; x < 64; x++) {
            for (int z = 0; z < 64; z++) {
                cells.add(new SettlementPathResultPayload.Cell(center.offset(x - 32, 0, z - 32),
                        SettlementPathTerrain.Kind.values()[(x + z) % SettlementPathTerrain.Kind.values().length]));
            }
        }
        for (int x = 0; x < 32; x++) {
            for (int z = 0; z < 32; z++) {
                for (int y = 0; y < 16; y++) {
                    var state = (x + y + z) % 3 == 0 ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
                    blocks.add(new ConstructionBlueprintItem.PreviewBlock(new BlockPos(x, y, z), state));
                }
            }
        }
        set(paths, "cells", List.copyOf(cells));
        var wall = preview(center.offset(-16, 16, -16), blocks);
        var building = preview(center.offset(-8, 36, -8), blocks);
        long started = System.nanoTime();
        render(event, paths, wall, building);
        long buildNanos = System.nanoTime() - started;
        var pathMesh = mesh(paths);
        var wallMesh = mesh(wall.jobId());
        var buildingMesh = mesh(building.jobId());
        var wallBuffers = buffers(wallMesh);
        require(!wallBuffers.isEmpty() && wallBuffers.stream().noneMatch(VertexBuffer::isInvalid), "stress preview uploads native meshes");
        var wallLayers = layers(wallMesh);
        require(wallLayers.stream().anyMatch(layer -> get(layer, "sortedFrom") != null), "stress check submits visible ghost meshes");
        for (var layer : wallLayers) set(layer, "sortedFrom", null);
        render(visibility(event, false), paths, wall, building);
        require(wallLayers.stream().allMatch(layer -> get(layer, "sortedFrom") == null), "off-screen sections skip sorting and draw submission");
        render(event, paths, wall, building);
        started = System.nanoTime();
        for (int i = 0; i < 8; i++) render(event, paths, wall, building);
        long cachedNanos = (System.nanoTime() - started) / 8;
        require(mesh(paths) == pathMesh && mesh(wall.jobId()) == wallMesh && mesh(building.jobId()) == buildingMesh,
                "4,096 path columns and two 16,384-block ghosts reuse their uploaded meshes");
        LogUtils.getLogger().info("Preview stress checks: first build {} ms, cached submission {} ms, {} wall buffers",
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

    private static ConstructionBlueprintItem.PreviewData preview(BlockPos origin, List<ConstructionBlueprintItem.PreviewBlock> blocks) {
        return new ConstructionBlueprintItem.PreviewData(UUID.randomUUID(), UUID.randomUUID(), Minecraft.getInstance().level.dimension(),
                ResourceLocation.fromNamespaceAndPath("villagerretaliation", "preview_stress"), "Preview stress", origin, Rotation.NONE,
                BlockPos.ZERO, new BlockPos(31, 15, 31), blocks.size(), 0, 0, "", "", "", false, false, false, false, List.copyOf(blocks));
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

    private static Map<?, ?> cache() { return (Map<?, ?>) get(null, "CACHE"); }
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
