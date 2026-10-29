package com.jvn.villagerretaliation.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

public final class PreviewMeshCache {
    private static final Map<Object, Entry> CACHE = new LinkedHashMap<>();
    private final Map<SectionPos, Section> sections = new LinkedHashMap<>();
    private BooleanSupplier valid = () -> true;

    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> clear());
    }

    public static void tick(ClientTickEvent.Post event) {
        long now = Util.getMillis();
        var level = Minecraft.getInstance().level;
        CACHE.values().removeIf(entry -> {
            if (entry.level == level && now - entry.usedAt < 2_000) return false;
            entry.mesh.close();
            return true;
        });
    }

    public static void clear() {
        CACHE.values().forEach(entry -> entry.mesh.close());
        CACHE.clear();
    }

    public static void render(Object owner, Object revision, Supplier<PreviewMeshCache> build, RenderLevelStageEvent event) {
        long now = Util.getMillis();
        var level = Minecraft.getInstance().level;
        var entry = CACHE.get(owner);
        boolean changed = entry == null || entry.level != level || !entry.revision.equals(revision);
        if (!changed && now - entry.checkedAt >= 250) {
            entry.checkedAt = now;
            changed = !entry.mesh.valid.getAsBoolean();
        }
        if (changed) {
            if (entry != null) entry.mesh.close();
            else if (CACHE.size() >= 8) {
                var oldest = CACHE.entrySet().stream().min(Comparator.comparingLong(value -> value.getValue().usedAt)).orElseThrow();
                oldest.getValue().mesh.close();
                CACHE.remove(oldest.getKey());
            }
            var mesh = build.get();
            try {
                mesh.finish();
            } catch (RuntimeException | Error error) {
                mesh.close();
                CACHE.remove(owner);
                throw error;
            }
            entry = new Entry(level, revision, mesh, now);
            CACHE.put(owner, entry);
        }
        entry.usedAt = now;
        entry.mesh.draw(event);
    }

    public void validateWith(BooleanSupplier valid) {
        this.valid = valid;
    }

    public Section at(BlockPos position) {
        return this.sections.computeIfAbsent(SectionPos.of(position), Section::new);
    }

    private void finish() {
        this.sections.values().forEach(Section::finish);
    }

    private void draw(RenderLevelStageEvent event) {
        var camera = event.getCamera().getPosition();
        var visible = new ArrayList<Section>();
        for (var section : this.sections.values()) {
            if (!section.layers.isEmpty() && event.getFrustum().isVisible(section.bounds)) visible.add(section);
        }
        visible.sort(Comparator.comparingDouble((Section section) -> section.bounds.getCenter().distanceToSqr(camera)).reversed());
        for (var section : visible) section.draw(event, camera);
    }

    private void close() {
        this.sections.values().forEach(Section::close);
        this.sections.clear();
    }

    private static final class Entry {
        private final Level level;
        private final Object revision;
        private final PreviewMeshCache mesh;
        private long usedAt, checkedAt;

        private Entry(Level level, Object revision, PreviewMeshCache mesh, long now) {
            this.level = level;
            this.revision = revision;
            this.mesh = mesh;
            this.usedAt = now;
            this.checkedAt = now;
        }
    }

    public static final class Section implements MultiBufferSource {
        private final BlockPos origin;
        private AABB bounds;
        private final PoseStack pose = new PoseStack();
        private final Map<RenderType, Layer> layers = new LinkedHashMap<>();

        private Section(SectionPos section) {
            this.origin = section.origin();
            this.bounds = new AABB(Vec3.atLowerCornerOf(this.origin), Vec3.atLowerCornerOf(this.origin.offset(16, 16, 16))).inflate(1);
            this.pose.translate(-this.origin.getX(), -this.origin.getY(), -this.origin.getZ());
        }

        public PoseStack pose() { return this.pose; }

        public void include(Vec3 point) {
            if (!this.bounds.contains(point)) this.bounds = this.bounds.minmax(new AABB(point, point).inflate(1));
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            return this.layers.computeIfAbsent(type, Layer::new).builder;
        }

        private void finish() {
            this.layers.values().forEach(Layer::finish);
        }

        private void draw(RenderLevelStageEvent event, Vec3 camera) {
            var offset = camera.subtract(this.origin.getX(), this.origin.getY(), this.origin.getZ());
            var matrix = new Matrix4f(event.getModelViewMatrix()).translate((float) -offset.x, (float) -offset.y, (float) -offset.z);
            for (var layer : this.layers.values()) {
                if (layer.buffer == null) continue;
                layer.type.setupRenderState();
                try {
                    layer.buffer.bind();
                    if (layer.sort != null && (layer.sortedFrom == null || layer.sortedFrom.distanceToSqr(offset) >= 1)) {
                        try (var indices = new ByteBufferBuilder(4096)) {
                            layer.buffer.uploadIndexBuffer(layer.sort.buildSortedIndexBuffer(indices,
                                    VertexSorting.byDistance((float) offset.x, (float) offset.y, (float) offset.z)));
                        }
                        layer.sortedFrom = offset;
                    }
                    layer.buffer.drawWithShader(matrix, event.getProjectionMatrix(), RenderSystem.getShader());
                } finally {
                    VertexBuffer.unbind();
                    layer.type.clearRenderState();
                }
            }
        }

        private void close() { this.layers.values().forEach(Layer::close); }
    }

    private static final class Layer {
        private final RenderType type;
        private final ByteBufferBuilder storage = new ByteBufferBuilder(4096);
        private final BufferBuilder builder;
        private VertexBuffer buffer;
        private MeshData.SortState sort;
        private Vec3 sortedFrom;

        private Layer(RenderType type) {
            this.type = type;
            this.builder = new BufferBuilder(this.storage, type.mode(), type.format());
        }

        private void finish() {
            var data = this.builder.build();
            try {
                if (data == null) return;
                if (this.type == RenderType.translucent()) this.sort = data.sortQuads(this.storage, VertexSorting.DISTANCE_TO_ORIGIN);
                this.buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                this.buffer.bind();
                this.buffer.upload(data);
            } finally {
                VertexBuffer.unbind();
                this.storage.close();
            }
        }

        private void close() {
            if (this.buffer != null) this.buffer.close();
            this.storage.close();
        }
    }
}
