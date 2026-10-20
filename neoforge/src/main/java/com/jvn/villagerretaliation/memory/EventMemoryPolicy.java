package com.jvn.villagerretaliation.memory;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.ToLongFunction;

public final class EventMemoryPolicy<E> {
    private final int capacity;
    private final long lifetime;
    private final ToLongFunction<E> time;

    public EventMemoryPolicy(int capacity, long lifetime, ToLongFunction<E> time) {
        if (capacity < 1 || lifetime < 0) throw new IllegalArgumentException("Event memory requires positive capacity and nonnegative lifetime");
        this.capacity = capacity;
        this.lifetime = lifetime;
        this.time = Objects.requireNonNull(time);
    }

    public boolean append(ArrayDeque<E> events, E event, BiPredicate<ArrayDeque<E>, E> duplicate) {
        Objects.requireNonNull(event);
        if (duplicate != null && duplicate.test(events, event)) return false;
        events.addLast(event);
        trim(events);
        return true;
    }

    public boolean prune(ArrayDeque<E> events, long gameTime) {
        boolean changed = events.removeIf(event -> this.time.applyAsLong(event) < gameTime - this.lifetime);
        return trim(events) || changed;
    }

    public boolean trim(ArrayDeque<E> events) {
        boolean changed = false;
        while (events.size() > this.capacity) {
            events.removeFirst();
            changed = true;
        }
        return changed;
    }

    public void normalize(ArrayDeque<E> events) {
        var ordered = events.stream().sorted(Comparator.comparingLong(this.time)).toList();
        events.clear();
        ordered.stream().skip(Math.max(0, ordered.size() - this.capacity)).forEach(events::addLast);
    }
}
