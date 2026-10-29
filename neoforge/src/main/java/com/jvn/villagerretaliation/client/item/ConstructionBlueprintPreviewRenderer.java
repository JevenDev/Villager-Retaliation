package com.jvn.villagerretaliation.client.item;

import com.jvn.villagerretaliation.client.PreviewMeshCache;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.item.ConstructionBlueprintItem;
import com.jvn.villagerretaliation.item.ConstructionBlueprintItem.PreviewBlock;
import com.jvn.villagerretaliation.item.ConstructionBlueprintItem.PreviewData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

public final class ConstructionBlueprintPreviewRenderer {
    private static final int MODEL_ALPHA = 94;
    private static final int FILL_ALPHA = 42;
    private static final int GHOST_RED = 93;
    private static final int GHOST_GREEN = 205;
    private static final int GHOST_BLUE = 255;
    private static final int OUTLINE_COLOR = 0xCC5DCDFF;
    private static final HeldPreview MAIN_HAND = new HeldPreview();
    private static final HeldPreview OFF_HAND = new HeldPreview();

    private ConstructionBlueprintPreviewRenderer() {
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        Optional<PreviewData> optionalPreview = heldBlueprintPreview(minecraft);
        if (optionalPreview.isEmpty()) {
            return;
        }
        PreviewData preview = optionalPreview.get();
        if (preview.expired()
                || preview.completed()
                || preview.blocks().isEmpty()
                || !preview.dimension().equals(minecraft.level.dimension())) {
            return;
        }

        renderPreview(event, preview, true);
    }

    public static void renderPreview(RenderLevelStageEvent event, PreviewData preview, boolean outline) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || preview == null || preview.blocks().isEmpty()) return;
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        PreviewMeshCache.render(preview.jobId(), preview, () -> buildGhosts(minecraft, preview), event);
        if (outline) renderBounds(minecraft.level, poseStack, bufferSource, preview);
        poseStack.popPose();

        bufferSource.endBatch(RenderType.translucent());
        bufferSource.endBatch(RenderType.lines());
    }

    private static PreviewMeshCache buildGhosts(Minecraft minecraft, PreviewData preview) {
        var mesh = new PreviewMeshCache();
        BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();
        PreviewBlockAndTintGetter previewLevel = new PreviewBlockAndTintGetter(minecraft.level);
        for (PreviewBlock block : preview.blocks()) {
            BlockPos worldPos = block.worldPos(preview.origin());
            previewLevel.watch(worldPos);
            if (minecraft.level.hasChunkAt(worldPos)) previewLevel.put(worldPos, block.state());
        }
        RandomSource random = RandomSource.create();
        ModelBlockRenderer.enableCaching();
        try {
            for (PreviewBlock block : preview.blocks()) {
                BlockPos worldPos = renderableWorldPos(minecraft.level, preview, block);
                BlockState state = block.state();
                if (worldPos == null || state.getRenderShape() != RenderShape.MODEL) continue;
                var section = mesh.at(worldPos);
                var poseStack = section.pose();
                if (shouldRenderSoftFill(previewLevel, worldPos, state))
                    renderSoftFill(poseStack, section.getBuffer(RenderType.debugFilledBox()), worldPos);
                var vertices = new GhostVertexConsumer(section.getBuffer(RenderType.translucent()));
                BakedModel model = dispatcher.getBlockModel(state);
                ModelData modelData = model.getModelData(previewLevel, worldPos, state, ModelData.EMPTY);
                long seed = state.getSeed(worldPos);
                random.setSeed(seed);
                for (RenderType renderType : model.getRenderTypes(state, random, modelData)) {
                    random.setSeed(seed);
                    poseStack.pushPose();
                    poseStack.translate(worldPos.getX(), worldPos.getY(), worldPos.getZ());
                    dispatcher.renderBatched(state, worldPos, previewLevel, poseStack, vertices, true, random, modelData, renderType);
                    poseStack.popPose();
                }
            }
        } finally {
            ModelBlockRenderer.clearCache();
        }
        mesh.validateWith(previewLevel::unchanged);
        return mesh;
    }

    private static BlockPos renderableWorldPos(Level level, PreviewData preview, PreviewBlock block) {
        BlockPos worldPos = block.worldPos(preview.origin());
        if (!level.hasChunkAt(worldPos)
                || BuilderStructureScanner.sameSchematicState(level.getBlockState(worldPos), block.state())) {
            return null;
        }
        return worldPos;
    }

    private static boolean shouldRenderSoftFill(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        if (!state.isSolidRender(level, pos)) return false;
        for (var direction : Direction.values()) {
            var neighbor = pos.relative(direction);
            if (!level.getBlockState(neighbor).isSolidRender(level, neighbor)) return true;
        }
        return false;
    }

    private static void renderSoftFill(PoseStack poseStack, VertexConsumer consumer, BlockPos pos) {
        LevelRenderer.addChainedFilledBoxVertices(
                poseStack,
                consumer,
                pos.getX() + 0.05D,
                pos.getY() + 0.05D,
                pos.getZ() + 0.05D,
                pos.getX() + 0.95D,
                pos.getY() + 0.95D,
                pos.getZ() + 0.95D,
                GHOST_RED / 255.0F,
                GHOST_GREEN / 255.0F,
                GHOST_BLUE / 255.0F,
                FILL_ALPHA / 255.0F);
    }

    private static void renderBounds(
            Level level,
            PoseStack poseStack,
            MultiBufferSource.BufferSource bufferSource,
            PreviewData preview) {
        if (!level.hasChunkAt(preview.worldMin()) || !level.hasChunkAt(preview.worldMax())) {
            return;
        }
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());
        float red = ((OUTLINE_COLOR >> 16) & 0xFF) / 255.0F;
        float green = ((OUTLINE_COLOR >> 8) & 0xFF) / 255.0F;
        float blue = (OUTLINE_COLOR & 0xFF) / 255.0F;
        float alpha = ((OUTLINE_COLOR >> 24) & 0xFF) / 255.0F;
        BlockPos min = preview.worldMin();
        BlockPos max = preview.worldMax();
        AABB box = new AABB(
                min.getX(),
                min.getY(),
                min.getZ(),
                max.getX() + 1.0D,
                max.getY() + 1.0D,
                max.getZ() + 1.0D).inflate(0.008D);
        LevelRenderer.renderLineBox(poseStack, consumer, box, red, green, blue, alpha);
    }

    private static Optional<PreviewData> heldBlueprintPreview(Minecraft minecraft) {
        ItemStack mainHand = minecraft.player.getMainHandItem();
        Optional<PreviewData> mainPreview = MAIN_HAND.read(mainHand);
        if (mainPreview.isPresent()) {
            return mainPreview;
        }
        return OFF_HAND.read(minecraft.player.getOffhandItem());
    }

    private static final class HeldPreview {
        private net.minecraft.world.item.component.CustomData data;
        private Optional<PreviewData> preview = Optional.empty();

        private Optional<PreviewData> read(ItemStack stack) {
            if (!(stack.getItem() instanceof ConstructionBlueprintItem)) {
                this.data = null;
                this.preview = Optional.empty();
            } else {
                var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                if (data != this.data) {
                    this.data = data;
                    this.preview = ConstructionBlueprintItem.previewData(stack);
                }
            }
            return this.preview;
        }
    }

    private record GhostVertexConsumer(VertexConsumer delegate) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            this.delegate.setColor(
                    tint(red, GHOST_RED),
                    tint(green, GHOST_GREEN),
                    tint(blue, GHOST_BLUE),
                    MODEL_ALPHA);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            this.delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            this.delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            this.delegate.setNormal(normalX, normalY, normalZ);
            return this;
        }

        private static int tint(int channel, int tintChannel) {
            return (channel + tintChannel + tintChannel) / 3;
        }
    }

    private static final class PreviewBlockAndTintGetter implements BlockAndTintGetter {
        private final Level delegate;
        private final Map<BlockPos, BlockState> previewStates = new LinkedHashMap<>();
        private final Map<BlockPos, BlockState> terrain = new LinkedHashMap<>();
        private final Map<BlockPos, Integer> skyLight = new LinkedHashMap<>();
        private final Map<BlockPos, Integer> blockLight = new LinkedHashMap<>();

        private void watch(BlockPos pos) {
            if (!this.terrain.containsKey(pos))
                this.terrain.put(pos.immutable(), this.delegate.hasChunkAt(pos) ? this.delegate.getBlockState(pos) : null);
        }

        private boolean unchanged() {
            for (var entry : this.terrain.entrySet()) {
                var pos = entry.getKey();
                if (entry.getValue() != (this.delegate.hasChunkAt(pos) ? this.delegate.getBlockState(pos) : null)) return false;
            }
            for (var entry : this.skyLight.entrySet())
                if (this.delegate.getBrightness(LightLayer.SKY, entry.getKey()) != entry.getValue()) return false;
            for (var entry : this.blockLight.entrySet())
                if (this.delegate.getBrightness(LightLayer.BLOCK, entry.getKey()) != entry.getValue()) return false;
            return true;
        }

        @Override
        public int getBrightness(LightLayer layer, BlockPos pos) {
            int brightness = this.delegate.getBrightness(layer, pos);
            (layer == LightLayer.SKY ? this.skyLight : this.blockLight).putIfAbsent(pos.immutable(), brightness);
            return brightness;
        }

        private PreviewBlockAndTintGetter(Level delegate) {
            this.delegate = delegate;
        }

        private void put(BlockPos pos, BlockState state) {
            this.previewStates.put(pos.immutable(), state);
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return this.previewStates.containsKey(pos) ? null : this.delegate.getBlockEntity(pos);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            BlockState previewState = this.previewStates.get(pos);
            if (previewState != null) {
                return previewState;
            }
            watch(pos);
            var state = this.terrain.get(pos);
            return state == null ? Blocks.AIR.defaultBlockState() : state;
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public int getHeight() {
            return this.delegate.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return this.delegate.getMinBuildHeight();
        }

        @Override
        public float getShade(Direction direction, boolean shade) {
            return this.delegate.getShade(direction, shade);
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return this.delegate.getLightEngine();
        }

        @Override
        public int getBlockTint(BlockPos blockPos, ColorResolver colorResolver) {
            return this.delegate.getBlockTint(blockPos, colorResolver);
        }

        @Override
        public ModelData getModelData(BlockPos pos) {
            return this.previewStates.containsKey(pos) ? ModelData.EMPTY : this.delegate.getModelData(pos);
        }
    }
}
