package com.jvn.villagerretaliation.repair;

import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class SmithRepairWorkstation {
    public static final int WORK_TICKS = 80;
    public static final int RETURN_TIMEOUT_TICKS = 200;
    public static final int APPROACH_TIMEOUT_TICKS = 200;
    private static final int SEARCH_RADIUS = 6;

    private SmithRepairWorkstation() {
    }

    public static BlockPos find(Villager villager) {
        GlobalPos job = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
        if (job != null && job.dimension().equals(villager.level().dimension())
                && job.pos().distSqr(villager.blockPosition()) <= SEARCH_RADIUS * SEARCH_RADIUS
                && available(villager, job.pos()) && standingPosition(villager, job.pos()) != null) return job.pos();
        return BlockPos.betweenClosedStream(villager.blockPosition().offset(-SEARCH_RADIUS, -2, -SEARCH_RADIUS),
                        villager.blockPosition().offset(SEARCH_RADIUS, 2, SEARCH_RADIUS))
                .map(BlockPos::immutable)
                .filter(pos -> available(villager, pos))
                .filter(pos -> standingPosition(villager, pos) != null)
                .min(Comparator.comparingDouble(pos -> pos.distToCenterSqr(villager.position())))
                .orElse(null);
    }

    public static boolean available(Villager villager, BlockPos pos) {
        Level level = villager.level();
        if (!level.hasChunkAt(pos) || !matches(villager, pos)
                || !level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) return false;
        return level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(pos).inflate(8),
                other -> other != villager && SmithRepairAnimationState.isActive(other)
                        && pos.equals(((SmithRepairAnimationState) other).villagerretaliation$repairStation())).isEmpty();
    }

    public static boolean matches(Villager villager, BlockPos pos) {
        var state = villager.level().getBlockState(pos);
        var profession = villager.getVillagerData().getProfession();
        return profession == VillagerProfession.TOOLSMITH && state.is(Blocks.SMITHING_TABLE)
                || profession == VillagerProfession.WEAPONSMITH && state.is(Blocks.GRINDSTONE)
                || profession == VillagerProfession.ARMORER && state.is(Blocks.BLAST_FURNACE);
    }

    public static Vec3 standingPosition(Villager villager, BlockPos station) {
        return Stream.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)
                .map(direction -> standingSurface(villager, station.relative(direction)))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(pos -> pos.distanceToSqr(villager.position())))
                .filter(pos -> pos.distanceToSqr(villager.position()) < 0.04 || reachable(villager, pos))
                .findFirst().orElse(null);
    }

    private static Vec3 standingSurface(Villager villager, BlockPos beside) {
        Level level = villager.level();
        for (int offset = 0; offset >= -2; offset--) {
            BlockPos floor = beside.offset(0, offset, 0);
            double top = level.getBlockState(floor).getCollisionShape(level, floor).toAabbs().stream()
                    .filter(box -> box.minX <= 0.5 && box.maxX >= 0.5 && box.minZ <= 0.5 && box.maxZ >= 0.5)
                    .mapToDouble(box -> box.maxY).max().orElse(0);
            if (top <= 0) continue;
            Vec3 pos = Vec3.atBottomCenterOf(floor).add(0, top, 0);
            if (level.noCollision(villager, villager.getBoundingBox().move(pos.subtract(villager.position())))) return pos;
        }
        return null;
    }

    private static boolean reachable(Villager villager, Vec3 pos) {
        var path = villager.getNavigation().createPath(BlockPos.containing(pos), 0);
        return path != null && path.canReach();
    }

    public static Vec3 itemPosition(Villager villager, BlockPos station) {
        Vec3 towardWorker = villager.position().subtract(Vec3.atBottomCenterOf(station)).multiply(1, 0, 1).normalize();
        double inset = villager.level().getBlockState(station).is(Blocks.GRINDSTONE) ? 0.18 : 0.28;
        return surfacePosition(villager, station, Vec3.atBottomCenterOf(station).add(towardWorker.scale(inset)));
    }

    public static Vec3 surfacePosition(Villager villager, BlockPos station, Vec3 point) {
        double localX = point.x - station.getX();
        double localZ = point.z - station.getZ();
        double top = villager.level().getBlockState(station).getShape(villager.level(), station).toAabbs().stream()
                .filter(box -> localX >= box.minX && localX <= box.maxX && localZ >= box.minZ && localZ <= box.maxZ)
                .mapToDouble(box -> box.maxY).max().orElse(1);
        return new Vec3(point.x, station.getY() + top + 0.035, point.z);
    }
}
