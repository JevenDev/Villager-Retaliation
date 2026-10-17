package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.exploration.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.client.renderer.texture.DynamicTexture;

final class MapTileView implements AutoCloseable {
    private DynamicTexture pages;
    private final int[] originsX = new int[4], originsZ = new int[4];
    private final ArrayList<MapSnapshot.Marker> markers = new ArrayList<>();
    private final HashSet<String> markerIds = new HashSet<>();
    private long nextUpdate, observed = -1, cached = -1;
    private MapView previous;
    private boolean missing;
    private List<MapSnapshot.Marker> resolved = List.of();
    int pageId() { return pages.getId(); }
    int originX(int level) { return originsX[level] * (8 << level); }
    int originZ(int level) { return originsZ[level] * (8 << level); }
    List<MapSnapshot.Marker> markers() { return resolved; }
    boolean update(MapRepository repository, MapTileCache cache, MapView view) {
        if (!cache.ready()) return false;
        if (pages == null) pages = new DynamicTexture(64, 256, true);
        long now = System.nanoTime();
        boolean moved = previous == null || previous.level() != view.level()
                || Math.abs(previous.width() / previous.scale() - view.width() / view.scale()) > (8 << view.level())
                || Math.abs(previous.height() / previous.scale() - view.height() / view.scale()) > (8 << view.level());
        for (int level = view.level(); level <= Math.min(3, view.level() + 1); level++) {
            int x = (int)Math.floor(view.x() / (8 << level)) - 32;
            int z = (int)Math.floor(view.z() / (8 << level)) - 32;
            moved |= originsX[level] != x || originsZ[level] != z;
        }
        if (!moved && observed == repository.generation() && cached == cache.generation() && !missing) return true;
        if (!moved && now < nextUpdate) return true;
        nextUpdate = now + 25_000_000;
        previous = view; observed = repository.generation(); missing = false;
        markers.clear(); markerIds.clear();
        var pixels = pages.getPixels();
        for (int y = 0; y < 256; y++) for (int x = 0; x < 64; x++) pixels.setPixelRGBA(x, y, 0);
        long deadline = System.nanoTime() + 1_000_000;
        int prepared = 0;
        // coarse pages remain available while finer tiles arrive, without revealing unknown mask bits
        for (int level = Math.min(3, view.level() + 1); level >= view.level(); level--) {
            int side = 8 << level;
            int cx = (int)Math.floor(view.x() / side), cz = (int)Math.floor(view.z() / side);
            originsX[level] = cx - 32; originsZ[level] = cz - 32;
            int rx = Math.min(30, (int)Math.ceil(view.width() / view.scale() / side / 2) + 2);
            int rz = Math.min(30, (int)Math.ceil(view.height() / view.scale() / side / 2) + 2);
            for (int radius = 0; radius <= Math.max(rx, rz); radius++) {
                for (int dz = -Math.min(radius, rz); dz <= Math.min(radius, rz); dz++)
                    for (int dx = -Math.min(radius, rx); dx <= Math.min(radius, rx); dx++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                        int tx = cx + dx, tz = cz + dz;
                        var source = level == 0 ? repository.getSurface(tx, tz) : repository.getOverview(tx, tz, 1 << level);
                        var entry = cache.get(level, MapSnapshot.key(tx, tz));
                        var north = level == 0 ? repository.getSurface(tx, tz - 1) : repository.getOverview(tx, tz - 1, 1 << level);
                        if (source != null && (entry == null || entry.source != source || entry.north != north)) {
                            if (prepared < 32 && System.nanoTime() < deadline) { entry = cache.prepare(level, source, north); prepared++; }
                            else missing = true;
                        }
                        if (source == null) missing = true;
                        if (entry == null) continue;
                        pixels.setPixelRGBA(tx - originsX[level], tz - originsZ[level] + level * 64, entry.empty ? 65535 : entry.slot + 1);
                        for (var marker : entry.source.markers()) if (entry.source.permits(marker) && markerIds.add(marker.id())) markers.add(marker);
                    }
            }
        }
        resolved = repository.resolveMarkers(markers);
        cached = cache.generation(); pages.upload();
        return true;
    }
    @Override public void close() {
        if (pages != null) pages.close();
        pages = null; previous = null; markers.clear(); markerIds.clear(); resolved = List.of();
        nextUpdate = 0; observed = -1; cached = -1;
    }
}
