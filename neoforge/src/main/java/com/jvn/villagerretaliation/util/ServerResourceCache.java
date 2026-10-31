package com.jvn.villagerretaliation.util;

import com.jvn.toucanlib.util.ToucanResourceCache;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;

public final class ServerResourceCache<T> {
    private final ToucanResourceCache<MinecraftServer, T> cache;

    private ServerResourceCache(Supplier<T> emptyValue, Function<MinecraftServer, T> loader) {
        cache = new ToucanResourceCache<>(emptyValue, loader);
    }

    public static <T> ServerResourceCache<T> create(Supplier<T> emptyValue, Function<MinecraftServer, T> loader) {
        return new ServerResourceCache<>(emptyValue, loader);
    }

    public T get(MinecraftServer server) { return cache.get(server); }
    public void clear() { cache.clear(); }
}
