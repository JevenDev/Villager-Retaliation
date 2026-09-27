package com.jvn.villagerretaliation.settlement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

final class SettlementEntityChanges {
    private final Map<List<String>, Long> fields = new LinkedHashMap<>();

    boolean record(CompoundTag before, CompoundTag after, long version) {
        return record(before, after, List.of(), version);
    }

    private boolean record(CompoundTag before, CompoundTag after, List<String> path, long version) {
        var keys = new java.util.LinkedHashSet<>(before.getAllKeys());
        keys.addAll(after.getAllKeys());
        boolean changed = false;
        for (var key : keys) {
            var first = before.get(key);
            var second = after.get(key);
            if (Objects.equals(first, second)) continue;
            var nested = new ArrayList<>(path);
            nested.add(key);
            if (first instanceof CompoundTag oldCompound && second instanceof CompoundTag newCompound) {
                changed |= record(oldCompound, newCompound, nested, version);
            } else {
                this.fields.keySet().removeIf(existing -> existing.size() > nested.size()
                        && existing.subList(0, nested.size()).equals(nested));
                this.fields.put(List.copyOf(nested), version);
                changed = true;
            }
        }
        return changed;
    }

    boolean touches(String field, long appliedVersion) {
        return this.fields.entrySet().stream().anyMatch(entry -> entry.getKey().getFirst().equals(field) && entry.getValue() > appliedVersion);
    }

    void apply(CompoundTag current, CompoundTag latest, long appliedVersion) {
        this.fields.entrySet().stream().filter(entry -> entry.getValue() > appliedVersion)
                .sorted(java.util.Comparator.comparingInt(entry -> entry.getKey().size())).forEach(entry -> {
                    var path = entry.getKey();
                    var source = latest;
                    var destination = current;
                    for (int index = 0; index < path.size() - 1; index++) {
                        var key = path.get(index);
                        source = source.getCompound(key);
                        if (!destination.contains(key, Tag.TAG_COMPOUND)) destination.put(key, new CompoundTag());
                        destination = destination.getCompound(key);
                    }
                    var key = path.getLast();
                    var value = source.get(key);
                    if (value == null) destination.remove(key);
                    else destination.put(key, value.copy());
                });
    }

    ListTag save() {
        var result = new ListTag();
        this.fields.forEach((path, version) -> {
            var entry = new CompoundTag();
            var keys = new ListTag();
            path.forEach(key -> keys.add(StringTag.valueOf(key)));
            entry.put("Path", keys);
            entry.putLong("Version", version);
            result.add(entry);
        });
        return result;
    }

    void load(ListTag tags, long version) {
        for (var raw : tags) {
            var entry = (CompoundTag) raw;
            var keys = entry.getList("Path", Tag.TAG_STRING);
            var path = new ArrayList<String>();
            for (int index = 0; index < keys.size(); index++) path.add(keys.getString(index));
            long fieldVersion = entry.getLong("Version");
            if (path.isEmpty() || fieldVersion <= 0L || fieldVersion > version
                    || this.fields.putIfAbsent(List.copyOf(path), fieldVersion) != null) {
                throw new IllegalArgumentException("Entity changes require distinct paths and valid versions");
            }
        }
    }
}
