package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.HiredWorkArea;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;

public final class BuilderSitePlanner {
    private static final int MAX_CANDIDATE_RING = 8;
    private static final int MAX_AREA_CANDIDATES = 512;

    private BuilderSitePlanner() {
    }

    public static SiteResult chooseSite(
            ServerLevel level,
            Player player,
            Villager villager,
            HiredWorkArea area,
            BuilderStructureScanner.StructurePlan plan) {
        if (level == null || player == null || villager == null || plan == null) {
            return SiteResult.failed("interaction.work.builder.site_missing");
        }
        for (BlockPos candidateCenter : candidateCenters(player, villager, area, plan)) {
            BlockPos origin = originFor(level, candidateCenter, plan);
            SiteResult validation = validateSite(level, player, villager, area, plan, origin);
            if (validation.valid()) {
                return validation;
            }
        }
        return SiteResult.failed("interaction.work.builder.no_safe_site");
    }

    public static SiteResult validateSite(
            ServerLevel level,
            Player player,
            Villager villager,
            HiredWorkArea area,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin) {
        if (plan.blocks().size() > Math.max(128, VillagerRetaliationConfig.HIRED_BUILDER_MAX_BLOCKS.get())) {
            return SiteResult.failed("interaction.work.builder.too_large");
        }
        if (!withinAllowedBounds(player, villager, area, plan, origin)) {
            return SiteResult.failed(area != null && area.usable()
                    ? "interaction.work.builder.site_outside_area"
                    : "interaction.work.builder.site_too_far");
        }
        for (BuilderStructureScanner.BuildBlock block : plan.blocks()) {
            BlockPos worldPos = plan.worldPos(origin, block);
            if (!level.hasChunkAt(worldPos)) {
                return SiteResult.failed("interaction.work.builder.site_unloaded", worldPos);
            }
            PlacementCheck check = canReserveAt(level, villager, worldPos, block.state());
            if (!check.valid()) {
                return SiteResult.failed(check.statusKey(), worldPos);
            }
        }
        return SiteResult.valid(origin);
    }

    public static PlacementCheck canReserveAt(ServerLevel level, Villager villager, BlockPos pos, BlockState targetState) {
        return placementCheck(level, villager, pos, targetState, false);
    }

    public static PlacementCheck canPlaceAt(ServerLevel level, Villager villager, BlockPos pos, BlockState targetState) {
        return placementCheck(level, villager, pos, targetState, true);
    }

    private static PlacementCheck placementCheck(
            ServerLevel level,
            Villager villager,
            BlockPos pos,
            BlockState targetState,
            boolean checkSupport) {
        if (!level.hasChunkAt(pos)) {
            return PlacementCheck.failed("interaction.work.builder.site_unloaded");
        }
        BlockState current = level.getBlockState(pos);
        if (current.equals(targetState)) {
            return PlacementCheck.success();
        }
        if (!safeReplaceable(level, pos, current)
                && !MiningBlockRules.isBuilderClearableObstruction(level, pos, current)) {
            return PlacementCheck.failed("interaction.work.builder.blocked_existing");
        }
        if (checkSupport && !targetState.canSurvive(level, pos)) {
            return PlacementCheck.failed("interaction.work.builder.blocked_support");
        }
        if (!targetState.getCollisionShape(level, pos, CollisionContext.empty()).isEmpty()
                && hasBlockingEntity(level, villager, pos)) {
            return PlacementCheck.failed("interaction.work.builder.blocked_entity");
        }
        return PlacementCheck.success();
    }

    public static boolean movementAllowed(HiredWorkArea area, BlockPos buildCenter, BlockPos pos) {
        if (pos == null) {
            return false;
        }
        if (area != null && area.usable()) {
            return areaContainsHorizontal(area, pos);
        }
        int maxDistance = Math.max(8, VillagerRetaliationConfig.HIRED_BUILDER_MAX_SITE_DISTANCE.get()) + 8;
        return buildCenter == null || horizontalDistanceSqr(pos, buildCenter) <= maxDistance * maxDistance;
    }

    private static boolean areaContainsHorizontal(HiredWorkArea area, BlockPos pos) {
        return pos.getX() >= area.min().getX()
                && pos.getX() <= area.max().getX()
                && pos.getZ() >= area.min().getZ()
                && pos.getZ() <= area.max().getZ();
    }

    private static List<BlockPos> candidateCenters(
            Player player,
            Villager villager,
            HiredWorkArea area,
            BuilderStructureScanner.StructurePlan plan) {
        List<BlockPos> candidates = new ArrayList<>();
        addCandidateCenters(candidates, player.blockPosition().relative(player.getDirection(), 5));
        addCandidateCenters(candidates, villager.blockPosition().relative(player.getDirection(), 4));
        if (area != null && area.usable()) {
            addCandidateCenters(candidates, area.center());
            addAreaCandidateCenters(candidates, player, villager, area, plan);
        }
        return candidates;
    }

    private static void addCandidateCenters(List<BlockPos> candidates, BlockPos anchor) {
        for (BlockPos candidate : candidateRing(anchor)) {
            addUnique(candidates, candidate);
        }
    }

    private static List<BlockPos> candidateRing(BlockPos anchor) {
        List<BlockPos> candidates = new ArrayList<>();
        candidates.add(anchor);
        for (int radius = 2; radius <= MAX_CANDIDATE_RING; radius += 2) {
            for (int x = -radius; x <= radius; x += radius) {
                for (int z = -radius; z <= radius; z += 2) {
                    candidates.add(anchor.offset(x, 0, z));
                }
            }
            for (int z = -radius; z <= radius; z += radius) {
                for (int x = -radius + 2; x <= radius - 2; x += 2) {
                    candidates.add(anchor.offset(x, 0, z));
                }
            }
        }
        return candidates;
    }

    private static void addAreaCandidateCenters(
            List<BlockPos> candidates,
            Player player,
            Villager villager,
            HiredWorkArea area,
            BuilderStructureScanner.StructurePlan plan) {
        List<BlockPos> areaCandidates = new ArrayList<>();
        int step = areaCandidateStep(plan);
        int minX = area.min().getX();
        int maxX = area.max().getX();
        int minZ = area.min().getZ();
        int maxZ = area.max().getZ();
        int y = area.center().getY();
        for (int x = minX; x <= maxX; x += step) {
            for (int z = minZ; z <= maxZ; z += step) {
                addUnique(areaCandidates, new BlockPos(x, y, z));
            }
            addUnique(areaCandidates, new BlockPos(x, y, maxZ));
        }
        for (int z = minZ; z <= maxZ; z += step) {
            addUnique(areaCandidates, new BlockPos(maxX, y, z));
        }
        addUnique(areaCandidates, new BlockPos(maxX, y, maxZ));
        addUnique(areaCandidates, area.center());
        areaCandidates.sort((left, right) -> Double.compare(
                candidateScore(left, player, villager),
                candidateScore(right, player, villager)));
        int added = 0;
        for (BlockPos candidate : areaCandidates) {
            if (added >= MAX_AREA_CANDIDATES) {
                break;
            }
            if (addUnique(candidates, candidate)) {
                added++;
            }
        }
    }

    private static int areaCandidateStep(BuilderStructureScanner.StructurePlan plan) {
        int width = plan.localMax().getX() - plan.localMin().getX() + 1;
        int depth = plan.localMax().getZ() - plan.localMin().getZ() + 1;
        int footprint = Math.max(width, depth);
        return Math.max(2, Math.min(6, Math.max(2, footprint / 2)));
    }

    private static double candidateScore(BlockPos candidate, Player player, Villager villager) {
        return candidate.distSqr(player.blockPosition()) + candidate.distSqr(villager.blockPosition()) * 0.5D;
    }

    private static boolean addUnique(List<BlockPos> candidates, BlockPos candidate) {
        BlockPos immutable = candidate.immutable();
        if (candidates.contains(immutable)) {
            return false;
        }
        candidates.add(immutable);
        return true;
    }

    private static BlockPos originFor(ServerLevel level, BlockPos center, BuilderStructureScanner.StructurePlan plan) {
        int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.getX(), center.getZ());
        BlockPos localCenter = plan.localCenter();
        return new BlockPos(
                center.getX() - localCenter.getX(),
                groundY - plan.localMin().getY(),
                center.getZ() - localCenter.getZ());
    }

    private static boolean withinAllowedBounds(
            Player player,
            Villager villager,
            HiredWorkArea area,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin) {
        BlockPos min = plan.worldMin(origin);
        BlockPos max = plan.worldMax(origin);
        if (area != null && area.usable()) {
            return areaContainsHorizontal(area, min) && areaContainsHorizontal(area, max);
        }
        int maxDistance = Math.max(8, VillagerRetaliationConfig.HIRED_BUILDER_MAX_SITE_DISTANCE.get());
        BlockPos center = new BlockPos(
                Math.floorDiv(min.getX() + max.getX(), 2),
                Math.floorDiv(min.getY() + max.getY(), 2),
                Math.floorDiv(min.getZ() + max.getZ(), 2));
        return horizontalDistanceSqr(center, player.blockPosition()) <= maxDistance * maxDistance
                || horizontalDistanceSqr(center, villager.blockPosition()) <= maxDistance * maxDistance;
    }

    private static double horizontalDistanceSqr(BlockPos left, BlockPos right) {
        double dx = left.getX() - right.getX();
        double dz = left.getZ() - right.getZ();
        return dx * dx + dz * dz;
    }

    private static boolean safeReplaceable(LevelReader level, BlockPos pos, BlockState current) {
        if (current.isAir()) {
            return true;
        }
        if (current.hasBlockEntity() || current.liquid() || current.is(Blocks.BEDROCK)) {
            return false;
        }
        if (isIncidentalSoftBlock(level, pos, current)) {
            return true;
        }
        return VillagerRetaliationConfig.HIRED_BUILDER_CAN_REPLACE_SOFT_BLOCKS.get()
                && current.is(BlockTags.REPLACEABLE);
    }

    public static boolean requiresClearingBeforePlacement(ServerLevel level, BlockPos pos, BlockState targetState) {
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        BlockState current = level.getBlockState(pos);
        return !current.equals(targetState)
                && !safeReplaceable(level, pos, current)
                && MiningBlockRules.isBuilderClearableObstruction(level, pos, current);
    }

    private static boolean isIncidentalSoftBlock(LevelReader level, BlockPos pos, BlockState current) {
        return current.is(BlockTags.REPLACEABLE)
                || current.is(BlockTags.SMALL_FLOWERS)
                || current.is(BlockTags.TALL_FLOWERS)
                || current.is(BlockTags.SNOW)
                || current.is(BlockTags.SAPLINGS)
                || current.getCollisionShape(level, pos, CollisionContext.empty()).isEmpty();
    }

    private static boolean hasBlockingEntity(ServerLevel level, Villager villager, BlockPos pos) {
        AABB bounds = new AABB(pos);
        List<Entity> entities = level.getEntities(
                villager,
                bounds,
                entity -> entity.isAlive() && !entity.isSpectator());
        return !entities.isEmpty();
    }

    public record SiteResult(boolean valid, BlockPos origin, String statusKey, Map<String, String> replacements) {
        private static SiteResult valid(BlockPos origin) {
            return new SiteResult(true, origin, "", Map.of());
        }

        private static SiteResult failed(String statusKey) {
            return new SiteResult(false, null, statusKey, Map.of());
        }

        private static SiteResult failed(String statusKey, BlockPos pos) {
            return new SiteResult(false, null, statusKey, Map.of(
                    "target", pos.getX() + " " + pos.getY() + " " + pos.getZ()));
        }
    }

    public record PlacementCheck(boolean valid, String statusKey) {
        private static PlacementCheck success() {
            return new PlacementCheck(true, "");
        }

        private static PlacementCheck failed(String statusKey) {
            return new PlacementCheck(false, statusKey);
        }
    }
}
