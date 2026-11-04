package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.network.SettlementBuildingPreviewPayload;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;

public final class SettlementBuildingAlignment {
    public enum Mode { FREE, STREET, BUILDINGS, PLOTS }
    public record Placement(BlockPos anchor, int turns) {}
    private SettlementBuildingAlignment() {}

    public static Placement suggest(SettlementBuildingPreviewPayload preview, BlockPos anchor, Mode mode, int spacing,
                                    List<BlockPos> roads, List<AABB> neighbors) {
        if (spacing < 1 || spacing > 5) throw new IllegalArgumentException("Invalid building spacing");
        if (mode == Mode.FREE || preview == null || preview.blocks().isEmpty()) return new Placement(anchor, 0);
        var offset = anchor.subtract(preview.anchor());
        var min = preview.min().offset(offset); var max = preview.max().offset(offset);
        if (mode == Mode.STREET) {
            var door = preview.blocks().stream().filter(b -> b.state().getBlock() instanceof DoorBlock
                    && b.state().getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER).findFirst().orElse(null);
            var road = roads.stream().filter(pos -> Math.abs(pos.getY() + 1 - anchor.getY()) <= 4)
                    .min(Comparator.comparingDouble(pos -> horizontal(pos, anchor))).orElse(null);
            if (door == null || road == null || horizontal(road, anchor) > 32 * 32) return new Placement(anchor, 0);
            var doorWorld = preview.origin().offset(offset).offset(door.local());
            var entrance = preview.entrances().stream().min(Comparator.comparingDouble(pos -> horizontal(pos, doorWorld.subtract(offset)))).orElse(null);
            var outward = entrance != null ? horizontalDirection(entrance.getX() - preview.origin().getX() - door.local().getX(),
                    entrance.getZ() - preview.origin().getZ() - door.local().getZ()) : door.state().getValue(DoorBlock.FACING);
            var facing = horizontalDirection(road.getX() - anchor.getX(), road.getZ() - anchor.getZ());
            int turns = 0;
            while (turns < 4 && Rotation.values()[turns].rotate(outward) != facing) turns++;
            var rotation = Rotation.values()[turns % 4];
            var localMin = preview.min().subtract(preview.origin()); var localMax = preview.max().subtract(preview.origin());
            var corners = List.of(new BlockPos(localMin.getX(), 0, localMin.getZ()).rotate(rotation),
                    new BlockPos(localMin.getX(), 0, localMax.getZ()).rotate(rotation),
                    new BlockPos(localMax.getX(), 0, localMin.getZ()).rotate(rotation), new BlockPos(localMax.getX(), 0, localMax.getZ()).rotate(rotation));
            int centerX = Math.floorDiv(corners.stream().mapToInt(BlockPos::getX).min().orElseThrow() + corners.stream().mapToInt(BlockPos::getX).max().orElseThrow(), 2);
            int centerZ = Math.floorDiv(corners.stream().mapToInt(BlockPos::getZ).min().orElseThrow() + corners.stream().mapToInt(BlockPos::getZ).max().orElseThrow(), 2);
            var localDoor = door.local().rotate(rotation);
            var target = road.relative(facing.getOpposite(), spacing + 2);
            return new Placement(new BlockPos(target.getX() - localDoor.getX() + centerX,
                    road.getY() + 1 - (localDoor.getY() - localMin.getY()), target.getZ() - localDoor.getZ() + centerZ), turns % 4);
        }
        int dx = 0, dz = 0;
        if (mode == Mode.PLOTS) {
            dx = Math.round((float) min.getX() / 16) * 16 + spacing - min.getX();
            dz = Math.round((float) min.getZ() / 16) * 16 + spacing - min.getZ();
        } else {
            int bestX = 4, bestZ = 4;
            for (var neighbor : neighbors) {
                if (neighbor.maxZ + spacing >= min.getZ() && neighbor.minZ - spacing <= max.getZ() + 1)
                    for (double candidate : new double[]{neighbor.minX - spacing - 1 - max.getX(), neighbor.maxX + spacing - min.getX(), neighbor.minX - min.getX()})
                        if (Math.abs(candidate) < bestX) { dx = (int) Math.round(candidate); bestX = Math.abs(dx); }
                if (neighbor.maxX + spacing >= min.getX() && neighbor.minX - spacing <= max.getX() + 1)
                    for (double candidate : new double[]{neighbor.minZ - spacing - 1 - max.getZ(), neighbor.maxZ + spacing - min.getZ(), neighbor.minZ - min.getZ()})
                        if (Math.abs(candidate) < bestZ) { dz = (int) Math.round(candidate); bestZ = Math.abs(dz); }
            }
            var bounds = new AABB(net.minecraft.world.phys.Vec3.atLowerCornerOf(min), net.minecraft.world.phys.Vec3.atLowerCornerOf(max.offset(1, 1, 1))).move(dx, 0, dz);
            if (neighbors.stream().anyMatch(bounds::intersects)) return new Placement(anchor, 0);
        }
        return new Placement(anchor.offset(dx, 0, dz), 0);
    }

    public static double horizontal(BlockPos a, BlockPos b) { double dx = (double) a.getX() - b.getX(), dz = (double) a.getZ() - b.getZ(); return dx * dx + dz * dz; }
    private static Direction horizontalDirection(int dx, int dz) { return Math.abs(dx) >= Math.abs(dz) ? dx >= 0 ? Direction.EAST : Direction.WEST : dz >= 0 ? Direction.SOUTH : Direction.NORTH; }
}
