package com.jvn.villagerretaliation.util;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;

public final class VillagerWorldTargetCache {
    private static final int CACHE_REGION_SHIFT = 8;
    private static final int MAX_STRUCTURE_MAP_SEARCH_RADIUS = 100;
    private static final int MAX_CACHE_ENTRIES = 512;
    private static final long POSITIVE_CACHE_TICKS = 20L * 60L * 10L;
    private static final long NEGATIVE_CACHE_TICKS = 20L * 30L;
    private static final double MIN_TARGET_SEPARATION_SQR = 96.0D * 96.0D;

    private static final ResourceLocation LEGACY_WOODLAND_MANSION_ID =
            ResourceLocation.withDefaultNamespace("woodland_mansion");
    private static final ResourceLocation WOODLAND_MANSION_ID =
            ResourceLocation.withDefaultNamespace("mansion");

    private static final Map<StructureSearchKey, CachedStructureLookup> STRUCTURE_CACHE = new HashMap<>();
    private static final Map<BiomeSearchKey, CachedBiomeLookup> BIOME_CACHE = new HashMap<>();
    private static MinecraftServer cacheServer;

    private VillagerWorldTargetCache() {
    }

    public static void clearCache() {
        STRUCTURE_CACHE.clear();
        BIOME_CACHE.clear();
        cacheServer = null;
    }

    public static ResourceLocation canonicalStructureId(ResourceLocation structureId) {
        if (LEGACY_WOODLAND_MANSION_ID.equals(structureId)) {
            return WOODLAND_MANSION_ID;
        }
        return structureId;
    }

    public static boolean sameStructureId(ResourceLocation first, ResourceLocation second) {
        if (first == null || second == null) {
            return false;
        }
        return canonicalStructureId(first).equals(canonicalStructureId(second));
    }

    public static Optional<Holder.Reference<Structure>> structureHolder(
            Registry<Structure> registry,
            ResourceLocation structureId) {
        if (registry == null || structureId == null) {
            return Optional.empty();
        }
        ResourceLocation canonicalId = canonicalStructureId(structureId);
        Optional<Holder.Reference<Structure>> holder = registry.getHolder(ResourceKey.create(Registries.STRUCTURE, canonicalId));
        if (holder.isPresent() || canonicalId.equals(structureId)) {
            return holder;
        }
        return registry.getHolder(ResourceKey.create(Registries.STRUCTURE, structureId));
    }

    public static Optional<LocatedStructure> findNearestStructure(
            ServerLevel level,
            BlockPos origin,
            ResourceLocation structureId,
            int requestedSearchRadius) {
        if (structureId == null) {
            return Optional.empty();
        }
        return findNearestStructure(level, origin, List.of(structureId), requestedSearchRadius);
    }

    public static Optional<LocatedStructure> findNearestStructure(
            ServerLevel level,
            BlockPos origin,
            Collection<ResourceLocation> structureIds,
            int requestedSearchRadius) {
        if (level == null
                || origin == null
                || structureIds == null
                || structureIds.isEmpty()
                || !level.getServer().getWorldData().worldGenOptions().generateStructures()) {
            return Optional.empty();
        }

        ensureCacheServer(level.getServer());
        List<ResourceLocation> canonicalIds = sortedCanonicalStructureIds(structureIds);
        if (canonicalIds.isEmpty()) {
            return Optional.empty();
        }

        int searchRadius = mapSearchRadius(requestedSearchRadius);
        StructureSearchKey cacheKey = StructureSearchKey.create(level, origin, canonicalIds, searchRadius);
        CachedStructureLookup cached = STRUCTURE_CACHE.get(cacheKey);
        long gameTime = level.getGameTime();
        if (cached != null) {
            if (cached.expiresAt() > gameTime) {
                return Optional.ofNullable(cached.target());
            }
            STRUCTURE_CACHE.remove(cacheKey);
        }

        Optional<LocatedStructure> located = locateStructure(level, origin, canonicalIds, searchRadius);
        STRUCTURE_CACHE.put(cacheKey, new CachedStructureLookup(
                located.orElse(null),
                gameTime + (located.isPresent() ? POSITIVE_CACHE_TICKS : NEGATIVE_CACHE_TICKS)
        ));
        pruneStructureCache(gameTime);
        return located;
    }

    public static List<LocatedBiome> findBiomeSamples(
            ServerLevel level,
            BlockPos origin,
            Collection<ResourceLocation> biomeIds,
            ResourceLocation excludedBiomeId,
            int minRadius,
            int maxRadius,
            int poolSize,
            int attempts,
            RandomSource random) {
        if (level == null
                || origin == null
                || biomeIds == null
                || biomeIds.isEmpty()
                || poolSize <= 0
                || attempts <= 0
                || random == null) {
            return List.of();
        }

        ensureCacheServer(level.getServer());
        List<ResourceLocation> sortedBiomeIds = sortedResourceIds(biomeIds);
        if (sortedBiomeIds.isEmpty()) {
            return List.of();
        }

        int safeMinRadius = Math.max(1, minRadius);
        int safeMaxRadius = Math.max(safeMinRadius, maxRadius);
        int safePoolSize = Math.max(1, poolSize);
        int safeAttempts = Math.max(safePoolSize, attempts);
        ResourceLocation excluded = excludedBiomeId == null ? null : excludedBiomeId;
        BiomeSearchKey cacheKey = BiomeSearchKey.create(
                level,
                origin,
                sortedBiomeIds,
                excluded,
                safeMinRadius,
                safeMaxRadius,
                safePoolSize);

        CachedBiomeLookup cached = BIOME_CACHE.get(cacheKey);
        long gameTime = level.getGameTime();
        if (cached != null) {
            if (cached.expiresAt() > gameTime) {
                return cached.targets();
            }
            BIOME_CACHE.remove(cacheKey);
        }

        List<LocatedBiome> targets = locateBiomeSamples(
                level,
                origin,
                sortedBiomeIds,
                excluded,
                safeMinRadius,
                safeMaxRadius,
                safePoolSize,
                safeAttempts,
                random);
        BIOME_CACHE.put(cacheKey, new CachedBiomeLookup(
                List.copyOf(targets),
                gameTime + (targets.isEmpty() ? NEGATIVE_CACHE_TICKS : POSITIVE_CACHE_TICKS)
        ));
        pruneBiomeCache(gameTime);
        return targets;
    }

    private static Optional<LocatedStructure> locateStructure(
            ServerLevel level,
            BlockPos origin,
            List<ResourceLocation> canonicalIds,
            int searchRadius) {
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        List<Holder.Reference<Structure>> holders = new ArrayList<>();
        for (ResourceLocation structureId : canonicalIds) {
            structureHolder(registry, structureId).ifPresent(holders::add);
        }
        if (holders.isEmpty()) {
            return Optional.empty();
        }

        Pair<BlockPos, Holder<Structure>> nearest = level.getChunkSource().getGenerator().findNearestMapStructure(
                level,
                HolderSet.direct(holders),
                origin,
                searchRadius,
                true
        );
        if (nearest == null) {
            return Optional.empty();
        }
        ResourceLocation structureId = keyLocation(nearest.getSecond()).orElse(null);
        if (structureId == null) {
            return Optional.empty();
        }
        return Optional.of(new LocatedStructure(canonicalStructureId(structureId), nearest.getFirst()));
    }

    private static List<LocatedBiome> locateBiomeSamples(
            ServerLevel level,
            BlockPos origin,
            List<ResourceLocation> biomeIds,
            ResourceLocation excludedBiomeId,
            int minRadius,
            int maxRadius,
            int poolSize,
            int attempts,
            RandomSource random) {
        Set<ResourceLocation> allowedBiomeIds = new HashSet<>(biomeIds);
        List<LocatedBiome> targets = new ArrayList<>();
        for (int attempt = 0; attempt < attempts && targets.size() < poolSize; attempt++) {
            int radius = randomRadius(random, minRadius, maxRadius);
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int x = origin.getX() + (int) Math.round(Math.cos(angle) * radius);
            int z = origin.getZ() + (int) Math.round(Math.sin(angle) * radius);
            Holder<Biome> biome = level.getUncachedNoiseBiome(
                    QuartPos.fromBlock(x),
                    QuartPos.fromBlock(origin.getY()),
                    QuartPos.fromBlock(z)
            );
            ResourceLocation biomeId = keyLocation(biome).orElse(null);
            if (biomeId != null
                    && !biomeId.equals(excludedBiomeId)
                    && allowedBiomeIds.contains(biomeId)
                    && isNewTarget(targets, biomeId, x, z)) {
                targets.add(new LocatedBiome(biomeId, new BlockPos(x, origin.getY(), z)));
            }
        }
        return targets;
    }

    private static int mapSearchRadius(int requestedSearchRadius) {
        return Math.max(1, Math.min(requestedSearchRadius, MAX_STRUCTURE_MAP_SEARCH_RADIUS));
    }

    private static int randomRadius(RandomSource random, int minRadius, int maxRadius) {
        if (maxRadius <= minRadius) {
            return minRadius;
        }
        double minSqr = (double) minRadius * minRadius;
        double maxSqr = (double) maxRadius * maxRadius;
        return (int) Math.round(Math.sqrt(minSqr + random.nextDouble() * (maxSqr - minSqr)));
    }

    private static List<ResourceLocation> sortedCanonicalStructureIds(Collection<ResourceLocation> ids) {
        return ids.stream()
                .filter(java.util.Objects::nonNull)
                .map(VillagerWorldTargetCache::canonicalStructureId)
                .distinct()
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
    }

    private static List<ResourceLocation> sortedResourceIds(Collection<ResourceLocation> ids) {
        return ids.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
    }

    private static Optional<ResourceLocation> keyLocation(Holder<?> holder) {
        return holder.unwrapKey().map(ResourceKey::location);
    }

    private static boolean isNewTarget(List<LocatedBiome> targets, ResourceLocation id, int x, int z) {
        for (LocatedBiome target : targets) {
            if (target.id().equals(id) || horizontalDistanceSqr(target.pos(), x, z) < MIN_TARGET_SEPARATION_SQR) {
                return false;
            }
        }
        return true;
    }

    private static double horizontalDistanceSqr(BlockPos pos, int x, int z) {
        double dx = pos.getX() - x;
        double dz = pos.getZ() - z;
        return dx * dx + dz * dz;
    }

    private static void ensureCacheServer(MinecraftServer server) {
        if (cacheServer == server) {
            return;
        }
        STRUCTURE_CACHE.clear();
        BIOME_CACHE.clear();
        cacheServer = server;
    }

    private static void pruneStructureCache(long gameTime) {
        if (STRUCTURE_CACHE.size() <= MAX_CACHE_ENTRIES) {
            return;
        }
        STRUCTURE_CACHE.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= gameTime);
        if (STRUCTURE_CACHE.size() > MAX_CACHE_ENTRIES) {
            STRUCTURE_CACHE.clear();
        }
    }

    private static void pruneBiomeCache(long gameTime) {
        if (BIOME_CACHE.size() <= MAX_CACHE_ENTRIES) {
            return;
        }
        BIOME_CACHE.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= gameTime);
        if (BIOME_CACHE.size() > MAX_CACHE_ENTRIES) {
            BIOME_CACHE.clear();
        }
    }

    public record LocatedStructure(ResourceLocation structureId, BlockPos pos) {
    }

    public record LocatedBiome(ResourceLocation id, BlockPos pos) {
    }

    private record StructureSearchKey(
            ResourceLocation dimension,
            int regionX,
            int regionZ,
            List<ResourceLocation> structureIds,
            int searchRadius
    ) {
        private static StructureSearchKey create(
                ServerLevel level,
                BlockPos origin,
                List<ResourceLocation> structureIds,
                int searchRadius) {
            return new StructureSearchKey(
                    level.dimension().location(),
                    origin.getX() >> CACHE_REGION_SHIFT,
                    origin.getZ() >> CACHE_REGION_SHIFT,
                    structureIds,
                    searchRadius
            );
        }
    }

    private record BiomeSearchKey(
            ResourceLocation dimension,
            int regionX,
            int regionZ,
            List<ResourceLocation> biomeIds,
            ResourceLocation excludedBiomeId,
            int minRadius,
            int maxRadius,
            int poolSize
    ) {
        private static BiomeSearchKey create(
                ServerLevel level,
                BlockPos origin,
                List<ResourceLocation> biomeIds,
                ResourceLocation excludedBiomeId,
                int minRadius,
                int maxRadius,
                int poolSize) {
            return new BiomeSearchKey(
                    level.dimension().location(),
                    origin.getX() >> CACHE_REGION_SHIFT,
                    origin.getZ() >> CACHE_REGION_SHIFT,
                    biomeIds,
                    excludedBiomeId,
                    minRadius,
                    maxRadius,
                    poolSize
            );
        }
    }

    private record CachedStructureLookup(LocatedStructure target, long expiresAt) {
    }

    private record CachedBiomeLookup(List<LocatedBiome> targets, long expiresAt) {
    }
}
