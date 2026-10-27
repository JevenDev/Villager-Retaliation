package com.jvn.villagerretaliation.diplomacy;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;

public final class VillageDiplomacyMetrics {
    public enum Part { CONTACTS, AID, COURIERS }
    private static final boolean ENABLED = Integer.getInteger("villagerretaliation.diplomacyProfile", 0) > 0;
    private static final Map<MinecraftServer, long[]> TOTALS = new WeakHashMap<>();
    private VillageDiplomacyMetrics() { }
    public static long start() { return ENABLED ? System.nanoTime() : 0; }
    public static void finish(MinecraftServer server, Part part, long started) {
        if (started != 0) TOTALS.computeIfAbsent(server, ignored -> new long[Part.values().length])[part.ordinal()] += System.nanoTime() - started;
    }
    public static long total(MinecraftServer server, Part part) {
        var values = TOTALS.get(server); return values == null ? 0 : values[part.ordinal()];
    }
}
