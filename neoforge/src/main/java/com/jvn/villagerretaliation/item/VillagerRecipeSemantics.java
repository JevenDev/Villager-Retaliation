package com.jvn.villagerretaliation.item;

import java.util.concurrent.atomic.AtomicLong;

/** Monotonic datapack recipe-registry revision used by production dependencies. */
public final class VillagerRecipeSemantics {
    private static final AtomicLong REVISION = new AtomicLong(1L);

    private VillagerRecipeSemantics() {
    }

    public static long revision() {
        return REVISION.get();
    }

    /** Safe to invoke from reload preparation because the revision contains no world state. */
    public static long markReloaded() {
        return REVISION.incrementAndGet();
    }
}
