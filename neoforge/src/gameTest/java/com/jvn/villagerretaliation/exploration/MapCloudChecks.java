package com.jvn.villagerretaliation.exploration;

final class MapCloudChecks {
    private MapCloudChecks() { }
    static void run() {
        int width = 8, height = 6, count = width * height;
        var pixels = new boolean[count];
        for (int x : new int[]{7, 0, 1}) for (int y : new int[]{5, 0}) pixels[y * width + x] = true;
        pixels[3 * width + 4] = true;
        var regions = MapCloudRegions.anchors(width, height, index -> pixels[index]);
        int anchorX = -1, anchorY = -1, checks = 0;
        for (int i = 0; i < count; i++) {
            if (!pixels[i]) {
                require(regions[i] == 0 && regions[count + i] == 0, "transparent gaps stay empty"); checks++; continue;
            }
            int dx = (regions[i] & 65535) - 32768, dy = (regions[i] >>> 16) - 32768;
            int cx = Math.floorMod(i % width + dx, width), cy = Math.floorMod(i / width + dy, height);
            if (i == 3 * width + 4) {
                require(dx == 0 && dy == 0, "a separate cloud retains its own anchor"); checks++;
            } else {
                if (anchorX < 0) { anchorX = cx; anchorY = cy; }
                require(cx == anchorX && cy == anchorY, "one cloud shares its anchor across both repeating seams"); checks++;
                int extent = regions[count + i];
                require((extent & 65535) > Math.abs(dx) && (extent >>> 16) > Math.abs(dy), "flat cloud bounds contain every connected texel"); checks++;
            }
        }
        var empty = MapCloudRegions.anchors(1, 1, index -> false);
        require(empty[0] == 0 && empty[1] == 0, "empty cloud textures retain a black background"); checks++;
        System.out.println("Passed " + checks + " connected-cloud checks");
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
