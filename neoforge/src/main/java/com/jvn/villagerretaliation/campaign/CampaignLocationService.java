package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignLocationDefinition;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.village.VillageRegistrySavedData;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;

public final class CampaignLocationService {
    private static final Map<MinecraftServer, Map<CampaignLocationBinding.Key, SearchMiss>> MISSES = new WeakHashMap<>();
    private static final long RETRY_TICKS = 100L;
    private static final int MAX_CACHED_MISSES = 1024;

    private CampaignLocationService() { }

    public static Result resolve(MinecraftServer server, ResourceLocation campaign, String alias) {
        if (server == null || !server.isSameThread()) return Result.unavailable("Campaign locations require the server thread.");
        if (campaign == null || alias == null || alias.isBlank()) return Result.unavailable("A campaign location reference is required.");
        if (CampaignWorldModeSavedData.get(server.overworld()).mode() != CampaignWorldMode.CAMPAIGN) return Result.unavailable("This location requires a Campaign Mode world.");
        var content = CampaignResources.current(server).campaigns().get(campaign);
        if (content == null) return Result.unavailable("Campaign location content is missing. Restore the campaign datapack.");
        var definition = content.definition();
        var location = definition.locations().get(alias);
        if (location == null) return Result.unavailable("Unknown campaign location " + alias + ". Restore its authored definition.");
        var campaigns = CampaignSavedData.get(server.overworld());
        String issue = campaigns.compatibility(definition);
        if (!issue.isBlank()) return Result.unavailable(issue);
        var data = CampaignLocationSavedData.get(server.overworld());
        if (!data.diagnostic().isBlank()) return Result.unavailable(data.diagnostic());
        var key = new CampaignLocationBinding.Key(campaign, alias);
        var binding = data.bindings().get(key);
        var instance = campaigns.instances().get(campaign);
        if (binding != null && (instance == null || !binding.instance().equals(instance.instanceId())
                || binding.definitionRevision() != definition.revision() || !binding.fingerprint().equals(definition.fingerprint())
                || binding.source() != location.source() || !binding.dimension().equals(location.dimension()))) {
            return Result.unavailable("Saved campaign location ownership is incompatible. Restore the matching campaign and location saves.");
        }
        var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, location.dimension()));
        if (level == null) return Result.unavailable("Campaign location dimension is unavailable: " + location.dimension());
        if (binding != null) {
            if (!validPosition(level, binding.position())) return Result.unavailable("The saved campaign destination is outside the current world bounds. Restore its dimension or world border.");
            return new Result(binding, "");
        }
        if (location.source() == CampaignLocationDefinition.Source.STRUCTURE
                && !level.registryAccess().registryOrThrow(Registries.STRUCTURE).containsKey(location.structure())) {
            return Result.unavailable("Registered campaign structure is unavailable: " + location.structure() + ". Restore its datapack definition.");
        }
        if (location.source() == CampaignLocationDefinition.Source.MARKER && !validPosition(level, location.origin())) {
            return Result.unavailable("The authored campaign marker is outside this dimension or world border. Correct its coordinates or restore the world bounds.");
        }
        long time = server.overworld().getGameTime();
        var misses = MISSES.computeIfAbsent(server, ignored -> new LinkedHashMap<>());
        var miss = misses.get(key);
        if (miss != null && miss.fingerprint().equals(definition.fingerprint()) && time >= miss.searchedAt() && time - miss.searchedAt() < RETRY_TICKS) {
            return Result.unavailable(miss.diagnostic());
        }
        var candidate = find(level, location);
        if (candidate == null) {
            String diagnostic = "Campaign location " + alias + " has no eligible known destination within its authored search area. Explore that area and retry.";
            if (location.source() == CampaignLocationDefinition.Source.STRUCTURE) {
                if (location.structureMarker() != null) diagnostic = "Campaign location " + alias + " needs a loaded marker tagged "
                        + location.structureMarker() + " inside " + location.structure() + ". Explore its authored search area and retry.";
                if (misses.size() >= MAX_CACHED_MISSES) misses.remove(misses.keySet().iterator().next());
                misses.put(key, new SearchMiss(definition.fingerprint(), time, diagnostic));
            }
            return Result.unavailable(diagnostic);
        }
        try {
            instance = campaigns.open(content, time);
            binding = data.bind(new CampaignLocationBinding(campaign, instance.instanceId(), definition.revision(), definition.fingerprint(),
                    alias, location.source(), candidate.identity(), location.dimension(), candidate.position(), time));
            misses.remove(key);
            return new Result(binding, "");
        } catch (IOException | IllegalArgumentException exception) {
            return Result.unavailable("Campaign location could not be saved: " + exception.getMessage());
        }
    }

    static Candidate find(ServerLevel level, CampaignLocationDefinition location) {
        var origin = location.origin() == null ? level.getLevelData().getSpawnPos() : location.origin();
        if (location.source() == CampaignLocationDefinition.Source.MARKER) {
            return validPosition(level, origin) ? new Candidate("marker:" + location.id(), origin) : null;
        }
        int radius = location.searchRadius();
        if (location.source() == CampaignLocationDefinition.Source.VILLAGE) {
            return VillageRegistrySavedData.get(level).entries().stream()
                    .filter(entry -> entry.dimension().equals(level.dimension().location()) && inRange(origin, entry.center(), radius)
                            && validPosition(level, entry.center()))
                    .map(entry -> new Candidate("village:" + entry.key(), entry.center()))
                    .min(order(origin)).orElse(null);
        }
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var structure = registry.get(location.structure());
        if (structure == null) return null;
        Candidate nearest = null;
        for (int x = (origin.getX() - radius) >> 4; x <= (origin.getX() + radius) >> 4; x++) {
            for (int z = (origin.getZ() - radius) >> 4; z <= (origin.getZ() + radius) >> 4; z++) {
                var chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) continue;
                var start = chunk.getStartForStructure(structure);
                if (start == null || !start.isValid()) continue;
                String identity = "structure:" + location.structure() + "@" + start.getChunkPos().toLong();
                Candidate candidate;
                if (location.structureMarker() == null) {
                    var position = start.getBoundingBox().getCenter();
                    if (!inRange(origin, position, radius) || !validPosition(level, position)) continue;
                    candidate = new Candidate(identity, position);
                } else candidate = marker(level, location, start, origin, identity);
                if (candidate != null && (nearest == null || order(origin).compare(candidate, nearest) < 0)) nearest = candidate;
            }
        }
        return nearest;
    }

    private static Candidate marker(ServerLevel level, CampaignLocationDefinition location, StructureStart start, BlockPos origin, String identity) {
        var bounds = start.getBoundingBox();
        int radius = location.searchRadius();
        int minX = Math.max(bounds.minX(), origin.getX() - radius);
        int maxX = Math.min(bounds.maxX(), origin.getX() + radius);
        int minY = Math.max(bounds.minY(), level.getMinBuildHeight());
        int maxY = Math.min(bounds.maxY(), level.getMaxBuildHeight() - 1);
        int minZ = Math.max(bounds.minZ(), origin.getZ() - radius);
        int maxZ = Math.min(bounds.maxZ(), origin.getZ() + radius);
        if (minX > maxX || minY > maxY || minZ > maxZ) return null;
        var area = new AABB(minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0);
        return level.getEntitiesOfClass(Marker.class, area, entity -> entity.getTags().contains(location.structureMarker().toString())
                        && !entity.isRemoved() && bounds.isInside(entity.blockPosition()) && inRange(origin, entity.blockPosition(), radius)
                        && validPosition(level, entity.blockPosition()))
                .stream().map(entity -> new Candidate(identity + "/marker:" + entity.getUUID(), entity.blockPosition()))
                .min(order(origin)).orElse(null);
    }

    private static Comparator<Candidate> order(BlockPos origin) {
        return Comparator.<Candidate>comparingDouble(value -> horizontalDistance(origin, value.position())).thenComparing(Candidate::identity);
    }

    private static boolean inRange(BlockPos origin, BlockPos position, int radius) {
        return horizontalDistance(origin, position) <= (double) radius * radius;
    }

    private static double horizontalDistance(BlockPos first, BlockPos second) {
        double x = (double) first.getX() - second.getX();
        double z = (double) first.getZ() - second.getZ();
        return x * x + z * z;
    }

    private static boolean validPosition(ServerLevel level, BlockPos position) {
        return !level.isOutsideBuildHeight(position) && level.getWorldBorder().isWithinBounds(position);
    }

    public record Result(CampaignLocationBinding binding, String diagnostic) {
        public boolean available() { return binding != null; }
        private static Result unavailable(String diagnostic) { return new Result(null, diagnostic); }
    }

    record Candidate(String identity, BlockPos position) { }
    private record SearchMiss(String fingerprint, long searchedAt, String diagnostic) { }
}
