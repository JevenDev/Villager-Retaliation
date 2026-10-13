package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredWorkArea;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public final class ResourceWorkOrders {
    private ResourceWorkOrders() { }

    public static HiredTemporaryTask create(HiredTemporaryTask.Kind kind, BlockPos start, BlockPos end, Direction direction,
                                            int length, int width, int height, String material, int minY, int maxY) {
        if (direction.getAxis().isVertical() || length < 1 || length > 48 || width < 1 || width > 8 || height < 2 || height > 16)
            throw new IllegalArgumentException("Invalid resource order dimensions");
        if (kind.staircase() && length <= width) throw new IllegalArgumentException("Staircase length must exceed its width");
        BlockPos min = HiredWorkArea.minPos(start, end);
        BlockPos max = HiredWorkArea.maxPos(start, end);
        BlockPos target = null;
        if (kind == HiredTemporaryTask.Kind.TREE || kind == HiredTemporaryTask.Kind.ORE) {
            min = start;
            max = start;
            target = start;
        } else if (kind == HiredTemporaryTask.Kind.TUNNEL || kind.staircase()) {
            var finish = start.relative(direction, length - 1);
            if (kind.staircase()) finish = finish.below(length - 1);
            var side = direction.getClockWise();
            var a = start.relative(side, -(width / 2));
            var b = finish.relative(side, width - 1 - width / 2);
            min = HiredWorkArea.minPos(a, b);
            max = HiredWorkArea.maxPos(a, b).above(kind.staircase() ? 2 : height - 1);
            if (kind.staircase()) target = start;
        } else if (kind.forest()) {
            int floor = Math.max(minY, min.getY() - 24);
            min = new BlockPos(min.getX(), floor, min.getZ());
            max = new BlockPos(max.getX(), Math.min(maxY - 1, floor + 63), max.getZ());
        } else if (kind == HiredTemporaryTask.Kind.QUARRY) {
            min = new BlockPos(min.getX(), min.getY() - length + 1, min.getZ());
        } else if (kind == HiredTemporaryTask.Kind.ORE_AREA) {
            min = min.below(Math.min(length, 24));
            max = max.above(8);
        }
        if (min.getY() < minY || max.getY() >= maxY) throw new IllegalArgumentException("Order exceeds world height");
        return new HiredTemporaryTask(UUID.randomUUID(), kind, min, max, 1, target, null, material);
    }
}
