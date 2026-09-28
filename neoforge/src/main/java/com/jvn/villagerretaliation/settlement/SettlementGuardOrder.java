package com.jvn.villagerretaliation.settlement;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public record SettlementGuardOrder(Mode mode, List<BlockPos> points) {
    public static final int MAX_POINTS = 16;

    public SettlementGuardOrder {
        Objects.requireNonNull(mode);
        points = points.stream().map(BlockPos::immutable).toList();
        if (points.isEmpty() || points.size() > MAX_POINTS || new HashSet<>(points).size() != points.size()
                || mode == Mode.PATROL && points.size() < 2 || mode != Mode.PATROL && points.size() != 1) {
            throw new IllegalArgumentException("Invalid settlement guard route");
        }
    }

    public boolean within(SettlementRecord town) {
        return points.stream().allMatch(point -> within(town, point));
    }

    static boolean within(SettlementRecord town, BlockPos point) {
        return town.contains(point);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Mode", mode.name());
        tag.putLongArray("Points", points.stream().mapToLong(BlockPos::asLong).toArray());
        return tag;
    }

    static SettlementGuardOrder load(CompoundTag tag) {
        return new SettlementGuardOrder(Mode.valueOf(tag.getString("Mode")),
                java.util.Arrays.stream(tag.getLongArray("Points")).mapToObj(BlockPos::of).toList());
    }

    public enum Mode {
        MOVE, PATROL, DEFEND
    }
}
