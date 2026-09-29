package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredRoute;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public record SettlementGuardOrder(Mode mode, List<BlockPos> points, boolean loop, List<HiredRoute.Branch> branches) {
    public static final int MAX_POINTS = 16;

    public SettlementGuardOrder(Mode mode, List<BlockPos> points) {
        this(mode, points, false, List.of());
    }

    public SettlementGuardOrder {
        Objects.requireNonNull(mode);
        points = points.stream().map(BlockPos::immutable).toList();
        branches = List.copyOf(branches);
        var shared = new HiredRoute(points, loop, branches);
        if (points.size() > MAX_POINTS || new HashSet<>(points).size() != points.size()
                || mode == Mode.PATROL && points.size() == 1 || mode != Mode.PATROL && points.size() != 1
                || mode != Mode.PATROL && (loop || !branches.isEmpty())
                || loop != shared.loop() || !branches.equals(shared.branches())) {
            throw new IllegalArgumentException("Invalid settlement guard route");
        }
    }

    public static boolean supports(HiredVillagerRole role) {
        return role == HiredVillagerRole.COMBAT || role == HiredVillagerRole.HUNTING || role == HiredVillagerRole.COURIER;
    }

    public boolean validFor(HiredVillagerRole role) {
        return supports(role) && (role == HiredVillagerRole.COMBAT || mode == Mode.PATROL)
                && (role != HiredVillagerRole.COURIER || sharedRoute().equals(sharedRoute().validatedChain()));
    }

    public HiredRoute sharedRoute() { return new HiredRoute(points, loop, branches); }

    public List<BlockPos> traversal() { return sharedRoute().traversalNodes(); }

    public boolean within(SettlementRecord town) {
        return positions().allMatch(point -> within(town, point));
    }

    public java.util.stream.Stream<BlockPos> positions() {
        return java.util.stream.Stream.concat(points.stream(), branches.stream().flatMap(branch -> java.util.stream.Stream.of(branch.anchor(), branch.end())));
    }

    static boolean within(SettlementRecord town, BlockPos point) {
        return town.contains(point);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Mode", mode.name());
        tag.putLongArray("Points", points.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putBoolean("Loop", loop);
        var branchTags = new net.minecraft.nbt.ListTag();
        for (var branch : branches) {
            var entry = new CompoundTag();
            entry.putLong("Anchor", branch.anchor().asLong());
            entry.putLong("End", branch.end().asLong());
            branchTags.add(entry);
        }
        tag.put("Branches", branchTags);
        return tag;
    }

    static SettlementGuardOrder load(CompoundTag tag) {
        if (!tag.contains("Mode", net.minecraft.nbt.Tag.TAG_STRING) || !tag.contains("Points", net.minecraft.nbt.Tag.TAG_LONG_ARRAY)
                || tag.contains("Loop") && !tag.contains("Loop", net.minecraft.nbt.Tag.TAG_BYTE)
                || tag.contains("Branches") && (!(tag.get("Branches") instanceof net.minecraft.nbt.ListTag list)
                        || !list.isEmpty() && list.getElementType() != net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Invalid saved route");
        }
        var branches = new java.util.ArrayList<HiredRoute.Branch>();
        for (var raw : tag.getList("Branches", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            var branch = (CompoundTag) raw;
            if (!branch.contains("Anchor", net.minecraft.nbt.Tag.TAG_LONG) || !branch.contains("End", net.minecraft.nbt.Tag.TAG_LONG)) {
                throw new IllegalArgumentException("Invalid saved branch");
            }
            branches.add(new HiredRoute.Branch(BlockPos.of(branch.getLong("Anchor")), BlockPos.of(branch.getLong("End"))));
        }
        return new SettlementGuardOrder(Mode.valueOf(tag.getString("Mode")),
                java.util.Arrays.stream(tag.getLongArray("Points")).mapToObj(BlockPos::of).toList(), tag.getBoolean("Loop"), branches);
    }

    public enum Mode {
        MOVE, PATROL, DEFEND
    }
}
