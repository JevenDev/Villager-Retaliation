package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.exploration.*;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.ToIntFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.EmptyBlockGetter;

final class MapTileCache implements AutoCloseable {
    static final int CAPACITY = 4096, ATLAS_SIDE = 1024;
    private final java.util.List<Long2ObjectLinkedOpenHashMap<Entry>> levels = java.util.List.of(
            new Long2ObjectLinkedOpenHashMap<>(), new Long2ObjectLinkedOpenHashMap<>(),
            new Long2ObjectLinkedOpenHashMap<>(), new Long2ObjectLinkedOpenHashMap<>());
    private record Material(int rgb, boolean water) { }
    static final class Entry {
        final int slot;
        MapSurfaceSnapshot source, north;
        boolean empty;
        Entry(int slot, MapSurfaceSnapshot source) { this.slot = slot; this.source = source; }
    }
    private final Map<String, Material> materials = new HashMap<>();
    private final MapSurfaceSnapshot.Reader reader = new MapSurfaceSnapshot.Reader();
    private final int[] colors = new int[64], heights = new int[72];
    private final long[] coverage = new long[64];
    private final ToIntFunction<MapSurfaceSnapshot.Reader> color = this::color;
    private DynamicTexture terrain, masks;
    private Object models;
    private long generation, uploads;
    long generation() { return generation; }
    long uploads() { return uploads; }
    int terrainId() { return terrain.getId(); }
    int maskId() { return masks.getId(); }
    boolean ready() {
        var current = Minecraft.getInstance().getModelManager().getMissingModel();
        if (models != current) { close(); models = current; return false; }
        if (terrain == null) {
            terrain = new DynamicTexture(ATLAS_SIDE, ATLAS_SIDE, true); return false;
        }
        if (masks == null) {
            masks = new DynamicTexture(ATLAS_SIDE * 2, ATLAS_SIDE, true); return false;
        }
        return true;
    }
    Entry get(int level, long key) { return levels.get(level).getAndMoveToLast(key); }
    Entry prepare(int level, MapSurfaceSnapshot source, MapSurfaceSnapshot north) {
        var entries = levels.get(level);
        var entry = entries.getAndMoveToLast(source.key());
        if (entry != null && entry.source.sameContent(source) && (entry.north == north || entry.north != null && entry.north.sameContent(north))) { entry.source = source; entry.north = north; return entry; }
        int slot = entry != null ? entry.slot : entries.size() < CAPACITY ? level * CAPACITY + entries.size() : entries.removeFirst().slot;
        MapFlatTile.prepare(source, reader, colors, coverage, heights, north, color);
        boolean empty = true;
        for (long mask : coverage) if (mask != 0) { empty = false; break; }
        entry = new Entry(slot, source); entry.north = north; entry.empty = empty;
        entries.putAndMoveToLast(source.key(), entry); generation++;
        if (empty) return entry;
        int ox = slot % 128 * 8, oy = slot / 128 * 8;
        var pixels = terrain.getPixels(); var mask = masks.getPixels();
        for (int i = 0; i < 64; i++) {
            int x = i % 8, y = i / 8;
            pixels.setPixelRGBA(ox + x, oy + y, colors[i]);
            mask.setPixelRGBA(ox * 2 + x * 2, oy + y, (int)coverage[i]);
            mask.setPixelRGBA(ox * 2 + x * 2 + 1, oy + y, (int)(coverage[i] >>> 32));
        }
        // upload changed tiles instead of rebuilding a viewport-sized image
        terrain.bind(); pixels.upload(0, ox, oy, ox, oy, 8, 8, false, false);
        masks.bind(); mask.upload(0, ox * 2, oy, ox * 2, oy, 16, 8, false, false);
        uploads++;
        return entry;
    }
    private int color(MapSurfaceSnapshot.Reader cell) {
        var material = materials.get(cell.state());
        if (material == null) {
            try {
                var state = BlockStateParser.parseForBlock(Minecraft.getInstance().level.registryAccess().lookupOrThrow(Registries.BLOCK), cell.state(), false).blockState();
                var mapColor = state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                material = new Material(mapColor.col, mapColor == MapColor.WATER);
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                System.getLogger("VR map").log(System.Logger.Level.DEBUG, "Unknown map material: " + cell.state(), e);
                material = new Material(0x888888, false);
            }
            if (materials.size() >= 4096) materials.clear();
            materials.put(cell.state(), material);
        }
        int rgb = material.rgb;
        return (rgb & 255) << 16 | rgb & 0xff00 | rgb >>> 16 & 255 | (material.water ? 0x1000000 : 0);
    }
    @Override public void close() {
        if (terrain != null) terrain.close();
        if (masks != null) masks.close();
        terrain = null; masks = null; materials.clear();
        for (var level : levels) level.clear();
        generation++;
    }
}
