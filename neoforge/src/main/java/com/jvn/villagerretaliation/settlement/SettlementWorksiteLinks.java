package com.jvn.villagerretaliation.settlement;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public record SettlementWorksiteLinks(Set<UUID> supplies, Set<UUID> outputs, Set<BlockPos> stations) {
    public static final int MAX_LINKS = 128;
    public static final String STATE_TAG = "WorksiteLinks";
    public static final SettlementWorksiteLinks AUTOMATIC = new SettlementWorksiteLinks(Set.of(), Set.of(), Set.of());

    public SettlementWorksiteLinks {
        supplies = Set.copyOf(supplies);
        outputs = Set.copyOf(outputs);
        stations = stations.stream().map(BlockPos::immutable).collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (supplies.size() > MAX_LINKS || outputs.size() > MAX_LINKS || stations.size() > MAX_LINKS) {
            throw new IllegalArgumentException("Too many worksite links");
        }
    }

    public boolean permitsSupply(UUID depot) {
        return this.supplies.isEmpty() || this.supplies.contains(depot);
    }

    public boolean permitsOutput(UUID depot) {
        return this.outputs.isEmpty() || this.outputs.contains(depot);
    }

    public boolean permitsStation(BlockPos station) {
        return this.stations.isEmpty() || this.stations.contains(station);
    }

    boolean apply(CompoundTag state) {
        CompoundTag saved = save();
        if (saved.equals(state.getCompound(STATE_TAG))) return false;
        state.put(STATE_TAG, saved);
        return true;
    }

    static boolean permitsSupply(CompoundTag state, UUID depot) {
        return permitsDepot(state.getCompound(STATE_TAG).getList("Supplies", Tag.TAG_COMPOUND), depot);
    }

    static boolean permitsOutput(CompoundTag state, UUID depot) {
        return permitsDepot(state.getCompound(STATE_TAG).getList("Outputs", Tag.TAG_COMPOUND), depot);
    }

    private static boolean permitsDepot(ListTag links, UUID depot) {
        if (links.isEmpty()) return true;
        for (var tag : links) {
            if (((CompoundTag) tag).hasUUID("Depot") && ((CompoundTag) tag).getUUID("Depot").equals(depot)) return true;
        }
        return false;
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.put("Supplies", depots(this.supplies));
        tag.put("Outputs", depots(this.outputs));
        tag.putLongArray("Stations", this.stations.stream().sorted().mapToLong(BlockPos::asLong).toArray());
        return tag;
    }

    static SettlementWorksiteLinks load(CompoundTag tag) {
        if (!tag.contains("Stations", Tag.TAG_LONG_ARRAY)) throw new IllegalArgumentException("Missing worksite stations");
        long[] positions = tag.getLongArray("Stations");
        if (positions.length > MAX_LINKS) throw new IllegalArgumentException("Too many worksite stations");
        var stations = new HashSet<BlockPos>();
        for (long position : positions) {
            if (!stations.add(BlockPos.of(position))) throw new IllegalArgumentException("Duplicate worksite station");
        }
        return new SettlementWorksiteLinks(depots(tag, "Supplies"), depots(tag, "Outputs"), stations);
    }

    private static ListTag depots(Set<UUID> ids) {
        var list = new ListTag();
        ids.stream().sorted().forEach(id -> {
            var tag = new CompoundTag();
            tag.putUUID("Depot", id);
            list.add(tag);
        });
        return list;
    }

    private static Set<UUID> depots(CompoundTag tag, String key) {
        if (!(tag.get(key) instanceof ListTag list) || list.size() > MAX_LINKS
                || !list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("Invalid worksite depot links");
        }
        var result = new HashSet<UUID>();
        for (var raw : list) {
            var entry = (CompoundTag) raw;
            if (!entry.hasUUID("Depot") || !result.add(entry.getUUID("Depot"))) {
                throw new IllegalArgumentException("Missing or duplicate worksite depot");
            }
        }
        return result;
    }
}
