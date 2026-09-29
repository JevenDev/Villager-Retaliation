package com.jvn.villagerretaliation.settlement;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

final class SettlementReceiptLedger {
    private final Map<Key, TreeMap<Long, Long>> streams = new LinkedHashMap<>();
    private CompoundTag preserved;

    long version(SettlementReceipt receipt) {
        requireReadable();
        for (long version = 2; version >= 1; version--) {
            var ranges = this.streams.get(new Key(receipt.stream(), version));
            var range = ranges == null ? null : ranges.floorEntry(receipt.sequence());
            if (range != null && range.getValue() >= receipt.sequence()) return version;
        }
        return -1;
    }

    void applied(SettlementReceipt receipt, long version) {
        requireReadable();
        if (version < 1 || version > 2) throw new IllegalArgumentException("Unsupported settlement receipt version");
        var ranges = this.streams.computeIfAbsent(new Key(receipt.stream(), version), ignored -> new TreeMap<>());
        long first = receipt.sequence(), last = first;
        var before = ranges.floorEntry(first);
        if (before != null) {
            if (before.getValue() >= first) return;
            if (before.getValue() == first - 1) {
                first = before.getKey();
                ranges.remove(before.getKey());
            }
        }
        var after = ranges.ceilingEntry(last);
        if (after != null && last != Long.MAX_VALUE && after.getKey() == last + 1) {
            last = after.getValue();
            ranges.remove(after.getKey());
        }
        // only adjacent acknowledged operations merge, holes never imply completion
        ranges.put(first, last);
    }

    private void requireReadable() {
        if (this.preserved != null) throw new IllegalStateException("Settlement receipt ledger is unreadable. Its original data has been preserved and replay is suspended.");
    }

    int ranges() {
        return this.streams.values().stream().mapToInt(Map::size).sum();
    }

    CompoundTag save() {
        if (this.preserved != null) return this.preserved.copy();
        var tag = new CompoundTag();
        tag.putInt("Format", 1);
        var entries = new ListTag();
        this.streams.forEach((key, ranges) -> {
            var entry = new CompoundTag();
            entry.putUUID("Stream", key.stream());
            entry.putLong("Version", key.version());
            long[] bounds = new long[ranges.size() * 2];
            int index = 0;
            for (var range : ranges.entrySet()) {
                bounds[index++] = range.getKey();
                bounds[index++] = range.getValue();
            }
            entry.putLongArray("Ranges", bounds);
            entries.add(entry);
        });
        tag.put("Streams", entries);
        return tag;
    }

    static SettlementReceiptLedger load(CompoundTag tag) {
        var ledger = new SettlementReceiptLedger();
        try {
            if (!tag.contains("Format", Tag.TAG_INT) || tag.getInt("Format") != 1 || !tag.contains("Streams", Tag.TAG_LIST)
                    || !tag.getAllKeys().equals(java.util.Set.of("Format", "Streams"))) throw new IllegalArgumentException("Unknown receipt ledger format");
            var entries = (ListTag) tag.get("Streams");
            if (!entries.isEmpty() && entries.getElementType() != Tag.TAG_COMPOUND) throw new IllegalArgumentException("Invalid receipt stream list");
            for (Tag raw : entries) {
                var entry = (CompoundTag) raw;
                if (!entry.hasUUID("Stream") || !entry.contains("Version", Tag.TAG_LONG)
                        || !entry.contains("Ranges", Tag.TAG_LONG_ARRAY)
                        || !entry.getAllKeys().equals(java.util.Set.of("Stream", "Version", "Ranges"))) throw new IllegalArgumentException("Incomplete receipt ledger");
                var key = new Key(entry.getUUID("Stream"), entry.getLong("Version"));
                var bounds = entry.getLongArray("Ranges");
                if (key.version() < 1 || key.version() > 2 || bounds.length % 2 != 0 || ledger.streams.containsKey(key)) throw new IllegalArgumentException("Invalid receipt ledger ranges");
                var ranges = new TreeMap<Long, Long>();
                long previous = 0;
                for (int index = 0; index < bounds.length; index += 2) {
                    if (bounds[index] <= previous || bounds[index + 1] < bounds[index]) throw new IllegalArgumentException("Overlapping receipt ledger ranges");
                    ranges.put(bounds[index], bounds[index + 1]);
                    previous = bounds[index + 1];
                }
                ledger.streams.put(key, ranges);
            }
        } catch (IllegalArgumentException invalid) {
            ledger.streams.clear();
            ledger.preserved = tag.copy();
            com.mojang.logging.LogUtils.getLogger().error("Unreadable settlement receipt ledger was preserved. Construction replay is suspended until compatible receipt data is restored.", invalid);
        }
        return ledger;
    }

    private record Key(UUID stream, long version) { }
}
