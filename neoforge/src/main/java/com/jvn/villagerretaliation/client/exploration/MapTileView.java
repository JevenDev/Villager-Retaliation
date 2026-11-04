package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.exploration.*;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

final class MapTileView implements AutoCloseable {
    static final int PAGE_SIDE = 96;
    private DynamicTexture pages;
    private TextureTarget fogMask, fogBlur;
    record FogRegion(int x, int z, int width, int height, float level, Object shader) { }
    private FogRegion fogRegion;
    private long fogBuilds, pageUploads;
    private int[] pageEntries = new int[PAGE_SIDE * PAGE_SIDE * 4], nextEntries = new int[PAGE_SIDE * PAGE_SIDE * 4];
    private final int[] originsX = new int[4], originsZ = new int[4];
    private final ArrayList<MapSnapshot.Marker> markers = new ArrayList<>();
    private final HashSet<String> markerIds = new HashSet<>();
    private long nextUpdate, observed = -1, cached = -1;
    private MapView previous;
    private boolean missing;
    private List<MapSnapshot.Marker> resolved = List.of();
    int pageId() { return pages.getId(); }
    long fogBuilds() { return fogBuilds; }
    long pageUploads() { return pageUploads; }
    boolean fogReady(FogRegion region) { return region.equals(fogRegion); }
    void fogRendered(FogRegion region) { fogRegion = region; fogBuilds++; }
    TextureTarget fogMask(int width, int height) {
        fogMask = resizeFogTarget(fogMask, width, height);
        return fogMask;
    }
    TextureTarget fogBlur(int width, int height) {
        fogBlur = resizeFogTarget(fogBlur, width, height);
        return fogBlur;
    }
    private static TextureTarget resizeFogTarget(TextureTarget target, int width, int height) {
        if (target != null && target.width == width && target.height == height) return target;
        if (target == null) target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        else target.resize(width, height, Minecraft.ON_OSX);
        // half floats retain fine coverage in the wide cloud fade and narrow terrain join
        RenderSystem.bindTexture(target.getColorTextureId());
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RG16F, width, height, 0, GL30.GL_RG, GL11.GL_FLOAT, 0L);
        target.setFilterMode(GL11.GL_LINEAR);
        return target;
    }

    int originX(int level) { return originsX[level] * (8 << level); }
    int originZ(int level) { return originsZ[level] * (8 << level); }
    List<MapSnapshot.Marker> markers() { return resolved; }
    boolean update(MapRepository repository, MapTileCache cache, MapView view) {
        if (!cache.ready()) return false;
        if (pages == null) pages = new DynamicTexture(PAGE_SIDE, PAGE_SIDE * 4, true);
        long now = System.nanoTime();
        boolean moved = previous == null || previous.level() != view.level()
                || Math.abs(previous.width() / previous.scale() - view.width() / view.scale()) > (8 << view.level())
                || Math.abs(previous.height() / previous.scale() - view.height() / view.scale()) > (8 << view.level());
        for (int level = view.level(); level < 4; level++) {
            int x = (int)Math.floor(view.x() / (8 << level)) - PAGE_SIDE / 2;
            int z = (int)Math.floor(view.z() / (8 << level)) - PAGE_SIDE / 2;
            moved |= originsX[level] != x || originsZ[level] != z;
        }
        if (!moved && observed == repository.generation() && cached == cache.generation() && !missing) return true;
        if (!moved && now < nextUpdate) return true;
        nextUpdate = now + 25_000_000;
        previous = view; observed = repository.generation(); missing = false;
        markers.clear(); markerIds.clear();
        Arrays.fill(nextEntries, 0);
        long preparationBudget = 1_000_000;
        int prepared = 0;
        // retain coarser LODs and load distant fog coverage without extra detail reads
        for (int level = 3; level >= view.level(); level--) {
            boolean load = level <= view.level() + 1 || level == 3;
            int side = 8 << level;
            int cx = (int)Math.floor(view.x() / side), cz = (int)Math.floor(view.z() / side);
            originsX[level] = cx - PAGE_SIDE / 2; originsZ[level] = cz - PAGE_SIDE / 2;
            // coarse pages supply the wider fog margin without expanding detailed terrain loads
            int margin = level == 3 ? MapFogRenderer.FOG_MARGIN : 40;
            int rx = Math.min(PAGE_SIDE / 2 - 2, (int)Math.ceil(view.width() / view.scale() / side / 2) + (int)Math.ceil((double) margin / side) + 1);
            int rz = Math.min(PAGE_SIDE / 2 - 2, (int)Math.ceil(view.height() / view.scale() / side / 2) + (int)Math.ceil((double) margin / side) + 1);
            for (int radius = 0; radius <= Math.max(rx, rz); radius++) {
                for (int dz = -Math.min(radius, rz); dz <= Math.min(radius, rz); dz++) {
                    if (Math.abs(dz) < radius && radius > rx) continue;
                    int step = Math.abs(dz) == radius ? 1 : Math.max(1, radius * 2);
                    for (int dx = -Math.min(radius, rx); dx <= Math.min(radius, rx); dx += step) {
                        int tx = cx + dx, tz = cz + dz;
                        var entry = cache.get(level, MapSnapshot.key(tx, tz));
                        if (load) {
                            var source = level == 0 ? repository.getSurface(tx, tz) : repository.getOverview(tx, tz, 1 << level);
                            var north = level == 0 ? repository.getSurface(tx, tz - 1) : repository.getOverview(tx, tz - 1, 1 << level);
                            if (source != null && (entry == null || entry.source != source || entry.north != north)) {
                                if (prepared < 32 && preparationBudget > 0) {
                                    long started = System.nanoTime();
                                    entry = cache.prepare(level, source, north); prepared++;
                                    preparationBudget -= System.nanoTime() - started;
                                }
                                else missing = true;
                            }
                            if (source == null) missing = true;
                        }
                        if (entry == null) continue;
                        nextEntries[tx - originsX[level] + (tz - originsZ[level] + level * PAGE_SIDE) * PAGE_SIDE] = entry.empty ? 65535 : entry.slot + 1;
                        for (var marker : entry.source.markers()) if (entry.source.permits(marker) && markerIds.add(marker.id())) markers.add(marker);
                    }
                }
            }
        }
        boolean changed = !Arrays.equals(pageEntries, nextEntries);
        if (changed) {
            var pixels = pages.getPixels();
            for (int i = 0; i < nextEntries.length; i++)
                if (pageEntries[i] != nextEntries[i]) pixels.setPixelRGBA(i % PAGE_SIDE, i / PAGE_SIDE, nextEntries[i]);
            var oldEntries = pageEntries; pageEntries = nextEntries; nextEntries = oldEntries;
            pages.upload(); pageUploads++;
        }
        if (changed || moved || cached != cache.generation()) fogRegion = null;
        resolved = repository.resolveMarkers(markers);
        cached = cache.generation();
        return true;
    }
    @Override public void close() {
        if (pages != null) pages.close();
        if (fogMask != null) fogMask.destroyBuffers();
        if (fogBlur != null) fogBlur.destroyBuffers();
        fogMask = null; fogBlur = null; fogRegion = null;
        Arrays.fill(pageEntries, 0); Arrays.fill(nextEntries, 0);
        pages = null; previous = null; markers.clear(); markerIds.clear(); resolved = List.of();
        nextUpdate = 0; observed = -1; cached = -1;
    }
}
