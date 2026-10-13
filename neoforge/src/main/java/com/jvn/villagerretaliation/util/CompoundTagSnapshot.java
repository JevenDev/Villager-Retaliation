package com.jvn.villagerretaliation.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class CompoundTagSnapshot {
    private final String[] keys;
    private final Tag[] values;

    public CompoundTagSnapshot(CompoundTag tag) {
        this.keys = tag.getAllKeys().toArray(String[]::new);
        this.values = new Tag[this.keys.length];
        for (int index = 0; index < this.keys.length; index++) this.values[index] = tag.get(this.keys[index]).copy();
    }

    public boolean matches(CompoundTag tag) {
        if (tag.size() != this.keys.length) return false;
        for (int index = 0; index < this.keys.length; index++) {
            if (!this.values[index].equals(tag.get(this.keys[index]))) return false;
        }
        return true;
    }
}
