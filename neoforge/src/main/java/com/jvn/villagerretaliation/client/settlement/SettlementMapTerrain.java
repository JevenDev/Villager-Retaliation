package com.jvn.villagerretaliation.client.settlement;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.client.model.data.ModelData;

final class SettlementMapTerrain implements AutoCloseable {
    static final int PIXELS_PER_BLOCK = 4;
    static final int TILE_SIZE = 16 * PIXELS_PER_BLOCK;
    private final Map<ChunkPos, Tile> tiles = new HashMap<>();
    private final Map<TextureAtlasSprite, int[]> textures = new IdentityHashMap<>();
    private final RandomSource random = RandomSource.create();
    private ClientLevel level;
    private BakedModel models;

    void update(List<ChunkPos> allowed, double minX, double minZ, double maxX, double maxZ) {
        var minecraft = Minecraft.getInstance();
        var currentModels = minecraft.getModelManager().getMissingModel();
        if (this.level != minecraft.level || this.models != currentModels) {
            close();
            this.level = minecraft.level;
            this.models = currentModels;
        }
        if (this.level == null) return;
        var iterator = this.tiles.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var position = entry.getKey();
            if (!allowed.contains(position) || this.level.getChunkSource().getChunk(position.x, position.z, false) == null) {
                minecraft.getTextureManager().release(entry.getValue().location);
                iterator.remove();
            }
        }
        long now = Util.getMillis();
        var visible = allowed.stream().filter(position -> position.getMaxBlockX() + 1 > minX
                && position.getMinBlockX() < maxX && position.getMaxBlockZ() + 1 > minZ && position.getMinBlockZ() < maxZ)
                .sorted(Comparator.comparingLong(position -> this.tiles.containsKey(position) ? this.tiles.get(position).updated : 0L))
                .toList();
        int updated = 0;
        for (var position : visible) {
            var chunk = this.level.getChunkSource().getChunk(position.x, position.z, false);
            if (chunk == null) continue;
            var tile = this.tiles.get(position);
            if (tile != null && tile.chunk == chunk && now - tile.updated < 1_000L) continue;
            if (tile == null) {
                tile = new Tile();
                this.tiles.put(position, tile);
            }
            sample(chunk, tile);
            tile.chunk = chunk;
            tile.updated = now;
            if (++updated >= 2) break;
        }
    }

    Tile tile(ChunkPos position) { return this.tiles.get(position); }
    int size() { return this.tiles.size(); }

    private void sample(LevelChunk chunk, Tile tile) {
        var minecraft = Minecraft.getInstance();
        NativeImage image = tile.texture.getPixels();
        var position = new BlockPos.MutableBlockPos();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int worldX = chunk.getPos().getMinBlockX() + x;
                int worldZ = chunk.getPos().getMinBlockZ() + z;
                int height = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                position.set(worldX, height, worldZ);
                var state = chunk.getBlockState(position);
                while (height > chunk.getMinBuildHeight() && state.getMapColor(this.level, position).col == 0
                        && state.getFluidState().isEmpty()) {
                    position.setY(--height);
                    state = chunk.getBlockState(position);
                }
                if (state.isAir()) {
                    for (int pz = 0; pz < PIXELS_PER_BLOCK; pz++) for (int px = 0; px < PIXELS_PER_BLOCK; px++)
                        image.setPixelRGBA(x * PIXELS_PER_BLOCK + px, z * PIXELS_PER_BLOCK + pz, 0);
                    continue;
                }
                TextureAtlasSprite sprite;
                int tint;
                var fluid = state.getFluidState();
                boolean water = state.getRenderShape() == RenderShape.INVISIBLE && fluid.is(FluidTags.WATER);
                boolean lava = state.getRenderShape() == RenderShape.INVISIBLE && fluid.is(FluidTags.LAVA);
                if (water || lava) {
                    sprite = minecraft.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(ResourceLocation.withDefaultNamespace(
                            water ? "block/water_still" : "block/lava_still"));
                    tint = water ? BiomeColors.getAverageWaterColor(this.level, position) : 0xFFFFFF;
                } else {
                    var model = minecraft.getBlockRenderer().getBlockModel(state);
                    var data = chunk.getModelData(position);
                    if (data == null) data = ModelData.EMPTY;
                    this.random.setSeed(state.getSeed(position));
                    var faces = model.getQuads(state, Direction.UP, this.random, data, null);
                    if (faces.isEmpty()) {
                        this.random.setSeed(state.getSeed(position));
                        faces = model.getQuads(state, null, this.random, data, null).stream()
                                .filter(quad -> quad.getDirection() == Direction.UP).toList();
                    }
                    var face = faces.stream().max(Comparator.comparingDouble(SettlementMapTerrain::faceHeight)).orElse(null);
                    sprite = face == null ? model.getParticleIcon(data) : face.getSprite();
                    tint = face == null || face.isTinted() ? minecraft.getBlockColors().getColor(state, this.level, position,
                            face == null ? 0 : face.getTintIndex()) : 0xFFFFFF;
                    if (tint == -1) tint = 0xFFFFFF;
                }
                int north = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, Math.max(0, z - 1));
                int west = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, Math.max(0, x - 1), z);
                double shade = Math.clamp(0.90D + (height * 2 - north - west) * 0.035D, 0.65D, 1.08D);
                if (water) {
                    int depth = 0;
                    while (depth < 12 && position.getY() > chunk.getMinBuildHeight()
                            && chunk.getFluidState(position).is(FluidTags.WATER)) {
                        depth++;
                        position.move(Direction.DOWN);
                    }
                    shade = 0.95D - depth * 0.018D;
                }
                var pixels = this.textures.computeIfAbsent(sprite, SettlementMapTerrain::texturePixels);
                int fallback = state.getMapColor(this.level, new BlockPos(worldX, height, worldZ)).col;
                for (int pz = 0; pz < PIXELS_PER_BLOCK; pz++) {
                    for (int px = 0; px < PIXELS_PER_BLOCK; px++) {
                        int color = pixels[px + pz * PIXELS_PER_BLOCK];
                        if (color >>> 24 == 0) color = 0xFF000000 | (fallback & 255) << 16 | fallback & 0xFF00 | fallback >> 16 & 255;
                        image.setPixelRGBA(x * PIXELS_PER_BLOCK + px, z * PIXELS_PER_BLOCK + pz, tint(color, tint, shade));
                    }
                }
            }
        }
        tile.texture.upload();
    }

    private static double faceHeight(BakedQuad quad) {
        int[] vertices = quad.getVertices();
        int stride = vertices.length / 4;
        double height = 0;
        for (int i = 0; i < 4; i++) height += Float.intBitsToFloat(vertices[i * stride + 1]);
        return height;
    }

    private static int[] texturePixels(TextureAtlasSprite sprite) {
        int[] pixels = new int[PIXELS_PER_BLOCK * PIXELS_PER_BLOCK];
        int width = sprite.contents().width();
        int height = sprite.contents().height();
        for (int z = 0; z < PIXELS_PER_BLOCK; z++) {
            for (int x = 0; x < PIXELS_PER_BLOCK; x++) {
                long red = 0, green = 0, blue = 0, alpha = 0;
                for (int sz = z * height / PIXELS_PER_BLOCK; sz < Math.max(z * height / PIXELS_PER_BLOCK + 1, (z + 1) * height / PIXELS_PER_BLOCK); sz++) {
                    for (int sx = x * width / PIXELS_PER_BLOCK; sx < Math.max(x * width / PIXELS_PER_BLOCK + 1, (x + 1) * width / PIXELS_PER_BLOCK); sx++) {
                        int color = sprite.getPixelRGBA(0, sx, sz);
                        int opacity = color >>> 24;
                        red += (color & 255) * opacity;
                        green += (color >> 8 & 255) * opacity;
                        blue += (color >> 16 & 255) * opacity;
                        alpha += opacity;
                    }
                }
                if (alpha > 0) pixels[x + z * PIXELS_PER_BLOCK] = 0xFF000000 | (int) (blue / alpha) << 16
                        | (int) (green / alpha) << 8 | (int) (red / alpha);
            }
        }
        return pixels;
    }

    static int tint(int abgr, int rgb, double shade) {
        // native image pixels store red in the low byte
        int red = Math.clamp((int) Math.round((abgr & 255) * (rgb >> 16 & 255) / 255.0D * shade), 0, 255);
        int green = Math.clamp((int) Math.round((abgr >> 8 & 255) * (rgb >> 8 & 255) / 255.0D * shade), 0, 255);
        int blue = Math.clamp((int) Math.round((abgr >> 16 & 255) * (rgb & 255) / 255.0D * shade), 0, 255);
        return 0xFF000000 | blue << 16 | green << 8 | red;
    }

    @Override
    public void close() {
        for (var tile : this.tiles.values()) Minecraft.getInstance().getTextureManager().release(tile.location);
        this.tiles.clear();
        this.textures.clear();
        this.level = null;
        this.models = null;
    }

    static final class Tile {
        final DynamicTexture texture = new DynamicTexture(TILE_SIZE, TILE_SIZE, true);
        final ResourceLocation location = Minecraft.getInstance().getTextureManager().register("settlement_map", this.texture);
        LevelChunk chunk;
        long updated;
    }
}
