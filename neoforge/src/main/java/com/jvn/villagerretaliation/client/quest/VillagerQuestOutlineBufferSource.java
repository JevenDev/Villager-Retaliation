package com.jvn.villagerretaliation.client.quest;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.FastColor;

public final class VillagerQuestOutlineBufferSource implements MultiBufferSource {
    private static final VertexConsumer DISCARDING_CONSUMER = new DiscardingVertexConsumer();

    private final MultiBufferSource.BufferSource outlineBufferSource =
            MultiBufferSource.immediate(new ByteBufferBuilder(1536));
    private int red = 255;
    private int green = 255;
    private int blue = 255;
    private int alpha = 255;

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        if (renderType.isOutline()) {
            return outlineConsumer(renderType);
        }
        return renderType.outline()
                .<VertexConsumer>map(this::outlineConsumer)
                .orElse(DISCARDING_CONSUMER);
    }

    public void setColor(int red, int green, int blue, int alpha) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
    }

    public void endOutlineBatch() {
        this.outlineBufferSource.endBatch();
    }

    private VertexConsumer outlineConsumer(RenderType renderType) {
        return new EntityOutlineGenerator(
                this.outlineBufferSource.getBuffer(renderType),
                this.red,
                this.green,
                this.blue,
                this.alpha);
    }

    private record EntityOutlineGenerator(VertexConsumer delegate, int color) implements VertexConsumer {
        EntityOutlineGenerator(VertexConsumer delegate, int red, int green, int blue, int alpha) {
            this(delegate, FastColor.ARGB32.color(alpha, red, green, blue));
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.delegate.addVertex(x, y, z).setColor(this.color);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            return this;
        }
    }

    private static final class DiscardingVertexConsumer implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            return this;
        }
    }
}
