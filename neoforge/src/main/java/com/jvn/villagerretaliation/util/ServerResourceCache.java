package com.jvn.villagerretaliation.util;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;

public final class ServerResourceCache<T> {
    private final Supplier<T> emptyValue;
    private final Function<MinecraftServer, T> loader;
    private volatile Entry<T> entry;

    private ServerResourceCache(Supplier<T> emptyValue, Function<MinecraftServer, T> loader) {
        this.emptyValue = Objects.requireNonNull(emptyValue);
        this.loader = Objects.requireNonNull(loader);
        this.entry = new Entry<>(null, emptyValue.get());
    }

    public static <T> ServerResourceCache<T> create(Supplier<T> emptyValue, Function<MinecraftServer, T> loader) {
        return new ServerResourceCache<>(emptyValue, loader);
    }

    public T get(MinecraftServer server) {
        Entry<T> current = this.entry;
        if (current.server() == server) {
            return current.value();
        }

        synchronized (this) {
            current = this.entry;
            if (current.server() == server) {
                return current.value();
            }
            T loaded = this.loader.apply(server);
            this.entry = new Entry<>(server, loaded);
            return loaded;
        }
    }

    public void clear() {
        this.entry = new Entry<>(null, this.emptyValue.get());
    }

    private record Entry<T>(MinecraftServer server, T value) {
    }
}
