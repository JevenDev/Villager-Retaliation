package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.client.interaction.DialogueCameraOcclusion;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;

public final class SettlementPeekRenderer {
    private static final float FADE_MILLIS = 450;
    private static ShaderInstance shader;
    private static int resources;
    private final List<Fade> fades = new ArrayList<>();
    private Set<BlockPos> target = Set.of();
    private long lastFrame;

    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    VillagerRetaliation.id("settlement_peek"), DefaultVertexFormat.BLOCK), loaded -> {
                shader = loaded;
                resources++;
            });
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to register settlement peek shader", exception);
        }
    }

    void target(Set<BlockPos> blocks) {
        if (this.target.equals(blocks)) return;
        this.target = Set.copyOf(blocks);
        var added = new HashSet<>(blocks);
        var split = new ArrayList<Fade>();
        for (var fade : this.fades) {
            added.removeAll(fade.blocks);
            var retained = new HashSet<>(fade.blocks);
            retained.retainAll(blocks);
            if (!retained.isEmpty() && retained.size() != fade.blocks.size()) {
                var removed = new HashSet<>(fade.blocks);
                removed.removeAll(retained);
                split.add(new Fade(Set.copyOf(retained), fade.progress, true));
                split.add(new Fade(Set.copyOf(removed), fade.progress, fade.masked));
                fade.close();
                fade.blocks = Set.of();
            } else if (!retained.isEmpty()) fade.masked = true;
        }
        this.fades.removeIf(fade -> fade.blocks.isEmpty());
        this.fades.addAll(split);
        var sections = new HashMap<SectionPos, Set<BlockPos>>();
        for (var position : added) sections.computeIfAbsent(SectionPos.of(position), section -> new HashSet<>()).add(position);
        for (var blocksInSection : sections.values()) this.fades.add(new Fade(Set.copyOf(blocksInSection), 1, true));
        publish();
    }

    void advance(long now) {
        float elapsed = this.lastFrame == 0 ? 0 : Math.clamp((now - this.lastFrame) / FADE_MILLIS, 0, 1);
        this.lastFrame = now;
        for (var fade : this.fades) {
            boolean revealing = this.target.contains(fade.blocks.iterator().next());
            // wait for the masked terrain upload before dissolving its replacement
            if (!revealing || DialogueCameraOcclusion.settlementMeshesReady(fade.blocks))
                fade.progress = Math.clamp(fade.progress + (revealing ? -elapsed : elapsed), 0, 1);
            if (!revealing && fade.progress == 1) {
                if (fade.masked) fade.masked = false;
                else if (DialogueCameraOcclusion.settlementMeshesReady(fade.blocks)) {
                    fade.close();
                    fade.blocks = Set.of();
                }
            }
        }
        this.fades.removeIf(fade -> fade.blocks.isEmpty());
        publish();
    }

    private void publish() {
        var watched = new HashSet<BlockPos>();
        var hidden = new HashSet<BlockPos>();
        for (var fade : this.fades) {
            watched.addAll(fade.blocks);
            if (fade.masked) hidden.addAll(fade.blocks);
        }
        DialogueCameraOcclusion.watchSettlementMeshes(watched);
        DialogueCameraOcclusion.settlementMask(hidden);
    }

    float opacity(BlockPos position) {
        for (var fade : this.fades) {
            if (fade.blocks.contains(position)) return smooth(fade.progress);
        }
        return 1;
    }

    private static float smooth(float progress) {
        return progress * progress * (3 - 2 * progress);
    }

    void render(RenderLevelStageEvent event) {
        if (shader == null || this.fades.isEmpty()) return;
        advance(Util.getMillis());
        var minecraft = Minecraft.getInstance();
        var camera = event.getCamera().getPosition();
        RenderType.solid().setupRenderState();
        minecraft.gameRenderer.lightTexture().turnOnLightLayer();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        try {
            for (var fade : this.fades) {
                if (fade.progress == 0) {
                    fade.close();
                    continue;
                }
                if (fade.meshResources != resources || fade.states.entrySet().stream().anyMatch(entry ->
                        minecraft.level.getBlockState(entry.getKey()) != entry.getValue())) fade.close();
                if (fade.mesh == null) fade.build();
                if (fade.mesh == null) continue;
                shader.safeGetUniform("Opacity").set(smooth(fade.progress));
                shader.safeGetUniform("CameraOffset").set((float) (fade.origin.getX() - camera.x),
                        (float) (fade.origin.getY() - camera.y), (float) (fade.origin.getZ() - camera.z));
                var matrix = new Matrix4f(event.getModelViewMatrix()).translate(
                        (float) (fade.origin.getX() - camera.x), (float) (fade.origin.getY() - camera.y),
                        (float) (fade.origin.getZ() - camera.z));
                fade.mesh.bind();
                fade.mesh.drawWithShader(matrix, event.getProjectionMatrix(), shader);
            }
        } finally {
            VertexBuffer.unbind();
            RenderSystem.disableBlend();
            RenderType.solid().clearRenderState();
        }
    }

    void clear() {
        this.fades.forEach(Fade::close);
        this.fades.clear();
        this.target = Set.of();
        this.lastFrame = 0;
        publish();
    }

    private static final class Fade {
        private Set<BlockPos> blocks;
        private final BlockPos origin;
        private float progress;
        private boolean masked;
        private VertexBuffer mesh;
        private final Map<BlockPos, BlockState> states = new HashMap<>();
        private int meshResources = -1;

        private Fade(Set<BlockPos> blocks, float progress, boolean masked) {
            this.blocks = blocks;
            this.origin = blocks.iterator().next();
            this.progress = progress;
            this.masked = masked;
        }

        private void build() {
            var minecraft = Minecraft.getInstance();
            var level = minecraft.level;
            var dispatcher = minecraft.getBlockRenderer();
            var vertices = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            var pose = new PoseStack();
            var random = RandomSource.create();
            for (var position : this.blocks) {
                var state = level.getBlockState(position);
                this.states.put(position, state);
                if (state.getRenderShape() != RenderShape.MODEL) continue;
                var data = level.getModelDataManager().getAt(position);
                if (data == null) data = ModelData.EMPTY;
                var model = dispatcher.getBlockModel(state);
                data = model.getModelData(level, position, state, data);
                random.setSeed(state.getSeed(position));
                for (var layer : model.getRenderTypes(state, random, data)) {
                    pose.pushPose();
                    pose.translate(position.getX() - this.origin.getX(), position.getY() - this.origin.getY(),
                            position.getZ() - this.origin.getZ());
                    dispatcher.renderBatched(state, position, level, pose, vertices, true, random, data, layer);
                    pose.popPose();
                }
            }
            var built = vertices.build();
            if (built != null) {
                this.mesh = new VertexBuffer(VertexBuffer.Usage.STATIC);
                this.mesh.bind();
                this.mesh.upload(built);
                VertexBuffer.unbind();
            }
            this.meshResources = resources;
        }

        private void close() {
            if (this.mesh != null) this.mesh.close();
            this.mesh = null;
            this.states.clear();
        }
    }
}
