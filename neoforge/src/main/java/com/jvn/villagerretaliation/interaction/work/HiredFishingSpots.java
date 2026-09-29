package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredWorkArea;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public record HiredFishingSpots(List<BlockPos> positions) {
    public static final String STATE_TAG = "FishingSpots";
    public static final int MAX_SPOTS = 16;
    public static final int WATER_MARGIN = 8;
    public static final HiredFishingSpots AUTOMATIC = new HiredFishingSpots(List.of());

    public HiredFishingSpots {
        positions = positions.stream().map(BlockPos::immutable).toList();
        if (positions.size() > MAX_SPOTS || new HashSet<>(positions).size() != positions.size()) {
            throw new IllegalArgumentException("Invalid fishing spot priorities");
        }
    }

    public boolean selects(BlockPos water) {
        return this.positions.isEmpty() || this.positions.contains(water);
    }

    public int priority(BlockPos water) {
        if (this.positions.isEmpty()) return 0;
        int index = this.positions.indexOf(water);
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    public boolean within(HiredWorkArea area) {
        return this.positions.stream().allMatch(position ->
                position.getX() >= (long) area.min().getX() - WATER_MARGIN && position.getX() <= (long) area.max().getX() + WATER_MARGIN
                && position.getY() >= (long) area.min().getY() - WATER_MARGIN && position.getY() <= (long) area.max().getY() + WATER_MARGIN
                && position.getZ() >= (long) area.min().getZ() - WATER_MARGIN && position.getZ() <= (long) area.max().getZ() + WATER_MARGIN);
    }

    public boolean apply(CompoundTag state) {
        if (this.positions.isEmpty()) {
            boolean changed = state.contains(STATE_TAG);
            state.remove(STATE_TAG);
            if (changed) state.remove("FishingPreferredScanCursor");
            return changed;
        }
        var saved = save();
        if (saved.equals(state.get(STATE_TAG))) return false;
        state.put(STATE_TAG, saved);
        state.remove("FishingPreferredScanCursor");
        return true;
    }

    public CompoundTag save() {
        var saved = new CompoundTag();
        saved.putInt("Version", 1);
        saved.putLongArray("Positions", this.positions.stream().mapToLong(BlockPos::asLong).toArray());
        return saved;
    }

    public static HiredFishingSpots read(CompoundTag state) {
        if (!state.contains(STATE_TAG)) return AUTOMATIC;
        if (!state.contains(STATE_TAG, Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Invalid saved fishing preferences");
        return load(state.getCompound(STATE_TAG));
    }

    public static HiredFishingSpots load(CompoundTag saved) {
        if (!saved.contains("Version", Tag.TAG_INT) || saved.getInt("Version") != 1 || !saved.contains("Positions", Tag.TAG_LONG_ARRAY)) {
            throw new IllegalArgumentException("Invalid saved fishing spot priorities");
        }
        long[] positions = saved.getLongArray("Positions");
        if (positions.length > MAX_SPOTS) throw new IllegalArgumentException("Too many fishing spots");
        return new HiredFishingSpots(Arrays.stream(positions).mapToObj(BlockPos::of).toList());
    }
}
