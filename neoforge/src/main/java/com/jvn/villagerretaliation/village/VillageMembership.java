package com.jvn.villagerretaliation.village;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class VillageMembership {
    private static final double DEFAULT_RADIUS = 64.0D;
    private static final double MAX_RADIUS = 96.0D;
    private static final double VILLAGER_DISCOVERY_RADIUS = 96.0D;
    private static final double EDGE_PADDING = 16.0D;

    private VillageMembership() {
    }

    public static boolean isVillagePosition(ServerLevel level, BlockPos pos) {
        return level.isVillage(pos) || resolve(level, pos).isPresent();
    }

    public static Optional<VillageArea> resolve(ServerLevel level, Villager anchor) {
        if (anchor == null || !anchor.isAlive()) {
            return Optional.empty();
        }
        return resolve(level, anchor.blockPosition())
                .filter(area -> area.contains(anchor));
    }

    public static Optional<BlockPos> meetingPoint(ServerLevel level, Villager villager) {
        return poiMemoryInLevel(level, villager, MemoryModuleType.MEETING_POINT);
    }

    public static Optional<VillageArea> resolve(ServerLevel level, BlockPos origin) {
        List<Villager> nearbyResidents = residentVillagersNear(level, origin, VILLAGER_DISCOVERY_RADIUS);
        boolean vanillaVillage = level.isVillage(origin);
        if (!vanillaVillage && nearbyResidents.isEmpty()) {
            return Optional.empty();
        }

        Vec3 center = centerOf(vanillaVillage ? origin : null, nearbyResidents);
        double radius = radiusFor(center, nearbyResidents);
        List<Villager> members = level.getEntitiesOfClass(
                Villager.class,
                AABB.ofSize(center, radius * 2.0D, radius * 2.0D, radius * 2.0D),
                villager -> isMember(level, villager, center, radius)
        );
        VillageArea area = new VillageArea(level, center, radius, List.copyOf(members), vanillaVillage);
        return area.contains(origin) ? Optional.of(area) : Optional.empty();
    }

    public static List<Villager> villagersForLocalVillage(ServerLevel level, BlockPos origin, double fallbackRadius) {
        List<Villager> nearbyVillagers = aliveVillagersNear(level, origin, fallbackRadius);
        Optional<VillageArea> area = resolve(level, origin);
        if (area.isEmpty()) {
            return nearbyVillagers;
        }
        Set<Villager> villagers = new LinkedHashSet<>(area.get().members());
        villagers.addAll(nearbyVillagers);
        return List.copyOf(villagers);
    }

    private static List<Villager> residentVillagersNear(ServerLevel level, BlockPos origin, double radius) {
        return level.getEntitiesOfClass(
                Villager.class,
                new AABB(origin).inflate(radius),
                villager -> villager.isAlive() && hasVillageSignal(level, villager)
        );
    }

    private static List<Villager> aliveVillagersNear(ServerLevel level, BlockPos origin, double radius) {
        return level.getEntitiesOfClass(
                Villager.class,
                new AABB(origin).inflate(radius),
                Villager::isAlive
        );
    }

    private static boolean isMember(ServerLevel level, Villager villager, Vec3 center, double radius) {
        if (!villager.isAlive() || villager.distanceToSqr(center) > radius * radius) {
            return false;
        }
        return hasVillageSignal(level, villager) || level.isVillage(villager.blockPosition());
    }

    private static boolean hasVillageSignal(ServerLevel level, Villager villager) {
        return level.isVillage(villager.blockPosition())
                || hasPoiMemoryInLevel(level, villager, MemoryModuleType.HOME)
                || hasPoiMemoryInLevel(level, villager, MemoryModuleType.JOB_SITE)
                || hasPoiMemoryInLevel(level, villager, MemoryModuleType.MEETING_POINT);
    }

    private static Optional<BlockPos> poiMemoryInLevel(ServerLevel level, Villager villager, MemoryModuleType<GlobalPos> memoryType) {
        return villager.getBrain()
                .getMemory(memoryType)
                .filter(pos -> pos.dimension().equals(level.dimension()))
                .map(GlobalPos::pos);
    }

    private static boolean hasPoiMemoryInLevel(ServerLevel level, Villager villager, MemoryModuleType<GlobalPos> memoryType) {
        return poiMemoryInLevel(level, villager, memoryType).isPresent();
    }

    private static Vec3 centerOf(BlockPos origin, List<Villager> villagers) {
        double x = 0.0D;
        double y = 0.0D;
        double z = 0.0D;
        int count = 0;
        if (origin != null) {
            x += origin.getX() + 0.5D;
            y += origin.getY() + 0.5D;
            z += origin.getZ() + 0.5D;
            count++;
        }
        for (Villager villager : villagers) {
            x += villager.getX();
            y += villager.getY();
            z += villager.getZ();
            count++;
        }
        if (count == 0) {
            return Vec3.atCenterOf(BlockPos.ZERO);
        }
        return new Vec3(x / count, y / count, z / count);
    }

    private static double radiusFor(Vec3 center, List<Villager> villagers) {
        double radius = DEFAULT_RADIUS;
        for (Villager villager : villagers) {
            radius = Math.max(radius, Math.sqrt(villager.distanceToSqr(center)) + EDGE_PADDING);
        }
        return Math.min(MAX_RADIUS, radius);
    }

    public record VillageArea(ServerLevel level, Vec3 center, double radius, List<Villager> members, boolean vanillaVillageAtOrigin) {
        public boolean contains(BlockPos pos) {
            return this.level.isVillage(pos) || Vec3.atCenterOf(pos).distanceToSqr(this.center) <= this.radius * this.radius;
        }

        public BlockPos centerBlock() {
            return BlockPos.containing(this.center);
        }

        public boolean contains(Villager villager) {
            return villager != null
                    && villager.isAlive()
                    && villager.level() == this.level
                    && contains(villager.blockPosition());
        }

        public List<Villager> membersMatching(Predicate<Villager> predicate) {
            if (predicate == null) {
                return this.members;
            }
            List<Villager> matches = new ArrayList<>();
            for (Villager villager : this.members) {
                if (predicate.test(villager)) {
                    matches.add(villager);
                }
            }
            return matches;
        }

        public int countMembers(Predicate<Villager> predicate, int stopAt) {
            int count = 0;
            for (Villager villager : this.members) {
                if (predicate.test(villager) && ++count >= stopAt) {
                    return count;
                }
            }
            return count;
        }
    }
}
