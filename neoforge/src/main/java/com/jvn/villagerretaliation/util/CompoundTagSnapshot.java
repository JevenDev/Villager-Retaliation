package com.jvn.villagerretaliation.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class CompoundTagSnapshot {
    private final String[] keys;
    private final Tag[] values;
    private final boolean complete;

    public CompoundTagSnapshot(CompoundTag tag) {
        this(tag, true, tag.getAllKeys().toArray(String[]::new));
    }

    public CompoundTagSnapshot(CompoundTag tag, String... keys) {
        this(tag, false, keys.clone());
    }

    private CompoundTagSnapshot(CompoundTag tag, boolean complete, String[] keys) {
        this.keys = keys;
        this.complete = complete;
        this.values = new Tag[keys.length];
        for (int index = 0; index < keys.length; index++) {
            var value = tag.get(keys[index]);
            this.values[index] = value == null ? null : value.copy();
        }
    }

    public boolean matches(CompoundTag tag) {
        if (this.complete && tag.size() != this.keys.length) return false;
        for (int index = 0; index < this.keys.length; index++) {
            if (!java.util.Objects.equals(this.values[index], tag.get(this.keys[index]))) return false;
        }
        return true;
    }
}
