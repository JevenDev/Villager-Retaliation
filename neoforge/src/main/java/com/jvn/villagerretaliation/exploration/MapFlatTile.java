package com.jvn.villagerretaliation.exploration;

import java.util.Arrays;
import java.util.function.ToIntFunction;

public final class MapFlatTile {
    private MapFlatTile() { }
    public static void prepare(MapSurfaceSnapshot source, MapSurfaceSnapshot.Reader reader,
            int[] colors, long[] coverage, int[] heights, MapSurfaceSnapshot north, ToIntFunction<MapSurfaceSnapshot.Reader> material) {
        Arrays.fill(colors, 0); Arrays.fill(coverage, 0);
        Arrays.fill(heights, Integer.MIN_VALUE);
        if (north != null) readHeights(north, reader, heights, -56);
        readHeights(source, reader, heights, 8);
        reader.reset(source.columns());
        for (int c = 0; c < source.columns().size(); c++) {
            reader.column(c);
            for (int l = 0; l < reader.layers(); l++) {
                reader.layer(l);
                if ((reader.faces() & 2) == 0) continue;
                int index = reader.index();
                int color = material.applyAsInt(reader);
                int northY = heights[index];
                if (northY == Integer.MIN_VALUE) northY = reader.y();
                int parity = (source.x() * 8 + index % 8 + source.z() * 8 + index / 8) & 1;
                int shade = brightness(reader.y(), northY, source.stride(), parity,
                        (color & 0x1000000) != 0 ? reader.y() - reader.bottom() + 1 : 0);
                colors[index] = shade(color, shade) | (reader.light() * 17) << 24;
                coverage[index] = reader.coverage();
                break;
            }
        }
        reader.reset(java.util.List.of());
    }
    private static void readHeights(MapSurfaceSnapshot source, MapSurfaceSnapshot.Reader reader, int[] heights, int offset) {
        reader.reset(source.columns());
        for (int c = 0; c < source.columns().size(); c++) {
            reader.column(c);
            int index = reader.index() + offset;
            if (index < 0 || index >= heights.length) continue;
            for (int l = 0; l < reader.layers(); l++) {
                reader.layer(l);
                if ((reader.faces() & 2) != 0) { heights[index] = reader.y(); break; }
            }
        }
    }
    public static int brightness(int y, int northY, int stride, int parity, int waterDepth) {
        if (waterDepth > 0) {
            double depth = waterDepth * .1 + parity * .2;
            return depth < .5 ? 255 : depth > .9 ? 180 : 220;
        }
        double slope = (y - northY) * 4.0 / (stride + 4) + (parity - .5) * .4;
        return slope > .6 ? 255 : slope < -.6 ? 180 : 220;
    }
    public static int shade(int color, int brightness) {
        return (color & 255) * brightness / 255 | ((color >> 8 & 255) * brightness / 255) << 8
                | ((color >> 16 & 255) * brightness / 255) << 16;
    }
    public static boolean covered(long mask, int stride, int x, int z) {
        return (mask & 1L << (Math.floorMod(x, stride) + Math.floorMod(z, stride) * stride)) != 0;
    }
}
