package com.jvn.villagerretaliation.exploration;

import java.util.*;

public final class MapCaptureQueue {
    public static final int MAX_TILES = 128;
    private final LinkedHashMap<Long, Long> pending = new LinkedHashMap<>(), recent = new LinkedHashMap<>();
    public void offer(int x, int z) {
        long key = MapSnapshot.key(MapSnapshot.tile(x), MapSnapshot.tile(z));
        long bit = 1L << (Math.floorMod(z, 8) * 8 + Math.floorMod(x, 8));
        if ((recent.getOrDefault(key, 0L) & bit) != 0) return;
        if (pending.containsKey(key) || pending.size() < MAX_TILES) pending.merge(key, bit, (a, b) -> a | b);
    }
    public void offerTile(int x, int z) {
        long key = MapSnapshot.key(x, z), mask = ~recent.getOrDefault(key, 0L);
        if (mask != 0 && (pending.containsKey(key) || pending.size() < MAX_TILES)) pending.merge(key, mask, (a, b) -> a | b);
    }
    public int size() { return pending.size(); }
    public static List<int[]> chunkOffsets(int renderDistance) {
        int radius = Math.clamp(renderDistance, 2, 32);
        var result = new ArrayList<int[]>();
        for (int z = -radius; z <= radius; z++) for (int x = -radius; x <= radius; x++) result.add(new int[]{x, z});
        result.sort(Comparator.comparingInt(p -> p[0] * p[0] + p[1] * p[1]));
        return List.copyOf(result);
    }
    public List<Long> tiles() { return List.copyOf(pending.keySet()); }
    public long mask(long key) { return pending.getOrDefault(key, 0L); }
    public void complete(long key, long mask) {
        long remaining = mask(key) & ~mask;
        if (remaining == 0) pending.remove(key); else pending.put(key, remaining);
        recent.merge(key, mask, (a, b) -> a | b);
        while (recent.size() > MapRepository.MAX_SURFACES) recent.remove(recent.keySet().iterator().next());
    }
    public void discard(long key) { pending.remove(key); }
    public void refresh() { recent.clear(); }
    public void clear() { pending.clear(); recent.clear(); }
}
