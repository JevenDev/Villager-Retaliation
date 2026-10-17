package com.jvn.villagerretaliation.exploration;

import java.util.function.IntPredicate;

public final class MapCloudRegions {
    private MapCloudRegions() { }

    public static int[] anchors(int width, int height, IntPredicate opaque) {
        if (width < 1 || height < 1 || width > 4096 || height > 4096) throw new IllegalArgumentException("Cloud texture bounds");
        int count = width * height;
        int[] result = new int[count * 2], queue = new int[count], x = new int[count], y = new int[count];
        boolean[] visited = new boolean[count];
        for (int start = 0; start < count; start++) {
            if (visited[start] || !opaque.test(start)) continue;
            int head = 0, tail = 1;
            queue[0] = start; visited[start] = true;
            x[start] = start % width; y[start] = start / width;
            long sumX = 0, sumY = 0;
            while (head < tail) {
                int index = queue[head++];
                sumX += x[index]; sumY += y[index];
                for (int direction = 0; direction < 4; direction++) {
                    int nx = x[index] + (direction == 0 ? -1 : direction == 1 ? 1 : 0);
                    int ny = y[index] + (direction == 2 ? -1 : direction == 3 ? 1 : 0);
                    int neighbor = Math.floorMod(ny, height) * width + Math.floorMod(nx, width);
                    if (visited[neighbor] || !opaque.test(neighbor)) continue;
                    visited[neighbor] = true; x[neighbor] = nx; y[neighbor] = ny; queue[tail++] = neighbor;
                }
            }
            int centerX = (int)Math.round((double)sumX / tail), centerY = (int)Math.round((double)sumY / tail);
            int extentX = 0, extentY = 0;
            for (int i = 0; i < tail; i++) {
                int index = queue[i], dx = centerX - x[index], dy = centerY - y[index];
                extentX = Math.max(extentX, Math.abs(dx) + 1);
                extentY = Math.max(extentY, Math.abs(dy) + 1);
            }
            // offsets keep a cloud's shared anchor continuous across the repeating texture seam
            for (int i = 0; i < tail; i++) {
                int index = queue[i], dx = centerX - x[index] + 32768, dy = centerY - y[index] + 32768;
                result[count + index] = extentX | extentY << 16;
                result[index] = (dx & 255) | (dx >> 8 & 255) << 8 | (dy & 255) << 16 | (dy >> 8 & 255) << 24;
            }
        }
        return result;
    }
}
