package com.jvn.villagerretaliation.quest.site;

import com.jvn.villagerretaliation.quest.site.QuestSiteSavedData.Site;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

final class QuestSitePlanner {
    private static final net.minecraft.server.level.TicketType<ChunkPos> LOAD = net.minecraft.server.level.TicketType.create(
            "villagerretaliation_quest_site", java.util.Comparator.comparingLong(ChunkPos::toLong), 40);
    private QuestSitePlanner() { }

    static boolean next(ServerLevel level, Site site, QuestSiteDefinition config) {
        if (site.attempt >= config.attempts()) return false;
        int attempt = site.attempt++;
        boolean existing = config.selection() != QuestSiteDefinition.Selection.NEW_SITE
                && (config.selection() == QuestSiteDefinition.Selection.EXISTING_ONLY || attempt < Math.min(8, config.attempts() / 2));
        double fraction = (attempt + 0.5) / config.attempts();
        double angle = attempt * 2.399963229728653 + (site.id.hashCode() & 255);
        double distance = config.minDistance() + (config.maxDistance() - config.minDistance()) * Math.sqrt(fraction);
        BlockPos sample = site.home.offset((int) (Math.cos(angle) * distance), 0, (int) (Math.sin(angle) * distance));
        site.template = null;
        if (existing) {
            var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
            var holder = registry.getHolder(config.structures().get(attempt % config.structures().size()));
            if (holder.isEmpty()) return true;
            var found = level.getChunkSource().getGenerator().findNearestMapStructure(level,
                    HolderSet.direct(holder.get()), sample, 1, false);
            if (found == null || !inRange(site.home, found.getFirst(), config)) return true;
            sample = found.getFirst();
        } else {
            site.template = config.templates().get(attempt % config.templates().size());
            if (template(level, site).isEmpty()) return true;
        }
        if (!level.getWorldBorder().isWithinBounds(sample) || conflicts(level, site, sample, config.buffer() + 24)) return true;
        site.position = sample.immutable();
        site.phase = QuestSiteSavedData.Phase.CANDIDATE;
        site.pending = null;
        return true;
    }

    static Optional<StructureTemplate> template(ServerLevel level, Site site) {
        if (site.template == null) return Optional.empty();
        return level.getStructureManager().get(site.template).filter(value -> {
            var size = value.getSize();
            return size.getX() >= 7 && size.getZ() >= 7 && size.getY() >= 2
                    && size.getX() <= 16 && size.getZ() <= 16 && size.getY() <= 16;
        });
    }

    static boolean inRange(BlockPos home, BlockPos target, QuestSiteDefinition config) {
        double distance = Math.hypot((double) home.getX() - target.getX(), (double) home.getZ() - target.getZ());
        return distance >= config.minDistance() && distance <= config.maxDistance();
    }

    static List<ChunkPos> chunks(Site site, QuestSiteDefinition config) {
        int radius = config.buffer() + 16;
        var chunks = new ArrayList<ChunkPos>();
        for (int x = (site.position.getX() - radius) >> 4; x <= (site.position.getX() + radius) >> 4; x++)
            for (int z = (site.position.getZ() - radius) >> 4; z <= (site.position.getZ() + radius) >> 4; z++)
                chunks.add(new ChunkPos(x, z));
        return chunks;
    }

    static boolean loadForPreparation(ServerLevel level, Site site, QuestSiteDefinition config) {
        if (site.pending == null) {
            var futures = chunks(site, config).stream().map(pos -> {
                level.getChunkSource().addRegionTicket(LOAD, pos, 0, pos);
                return level.getChunkSource().getChunkFuture(pos.x, pos.z, ChunkStatus.FULL, true);
            }).toList();
            site.pending = new Loads(futures);
        }
        for (var chunk : chunks(site, config)) level.getChunkSource().addRegionTicket(LOAD, chunk, 0, chunk);
        var loads = (Loads) site.pending;
        if (loads.values.stream().anyMatch(value -> !value.isDone())) return false;
        if (loads.values.stream().anyMatch(value -> !value.join().isSuccess())) {
            site.phase = QuestSiteSavedData.Phase.UNAVAILABLE;
            site.diagnostic = "Quest site terrain could not be loaded.";
            return false;
        }
        site.pending = null;
        return true;
    }

    static boolean candidate(ServerLevel level, Site site, QuestSiteDefinition config) {
        if (site.pending instanceof Loads) for (var chunk : chunks(site, config))
            level.getChunkSource().addRegionTicket(LOAD, chunk, 0, chunk);
        if (site.pending == null) {
            if (site.template != null && config.terrain() == QuestSiteDefinition.Terrain.NEW_TERRAIN_ONLY) {
                var chunks = chunks(site, config);
                if (chunks.stream().anyMatch(pos -> level.getChunkSource().hasChunk(pos.x, pos.z))) return reject(site);
                var reads = chunks.stream().map(level.getChunkSource().chunkMap::read).toList();
                site.pending = new Reads(reads);
            } else site.pending = new Reads(List.of());
            return false;
        }
        if (site.pending instanceof Reads reads) {
            if (reads.values.stream().anyMatch(value -> !value.isDone())) return false;
            if (reads.values.stream().anyMatch(value -> value.join().isPresent())) return reject(site);
            if (!site.accepted && site.template != null) {
                site.phase = QuestSiteSavedData.Phase.PREPARING;
                site.pending = null;
                site.validated = false;
                return false;
            }
            var futures = chunks(site, config).stream().map(pos -> {
                level.getChunkSource().addRegionTicket(LOAD, pos, 0, pos);
                return level.getChunkSource().getChunkFuture(pos.x, pos.z, ChunkStatus.FULL, true);
            }).toList();
            site.pending = new Loads(futures);
            return false;
        }
        var loads = (Loads) site.pending;
        if (loads.values.stream().anyMatch(value -> !value.isDone())) return false;
        if (loads.values.stream().anyMatch(value -> value.join().isSuccess() == false)) return reject(site);
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, site.position);
        if (site.template == null) {
            if (!intact(level, surface, config)) return reject(site);
            BlockPos found = null;
            for (int x = -16; x <= 16 && found == null; x += 4)
                for (int z = -16; z <= 16 && found == null; z += 4) {
                    var candidate = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, surface.offset(x, 0, z));
                    if (clear(level, candidate, 4, 3, false)) found = candidate;
                }
            if (found == null) return reject(site);
            surface = found;
        } else if (!clear(level, surface, 8, 16, true)) return reject(site);
        if (conflicts(level, site, surface, config.buffer() + 16)) return reject(site);
        site.position = surface.immutable();
        site.validated = true;
        site.pending = null;
        return true;
    }

    private static boolean reject(Site site) {
        site.phase = QuestSiteSavedData.Phase.SEARCHING;
        site.position = null;
        site.template = null;
        site.validated = false;
        site.pending = null;
        return false;
    }

    static boolean clear(ServerLevel level, BlockPos center, int radius, int height, boolean placement) {
        if (center.getY() < level.getMinBuildHeight() + 1 || center.getY() + height >= level.getMaxBuildHeight()) return false;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                var ground = center.offset(x, -1, z);
                if (!level.getWorldBorder().isWithinBounds(ground)) return false;
                var state = level.getBlockState(ground);
                if (!state.is(BlockTags.DIRT) && !state.is(BlockTags.SAND) && !state.is(BlockTags.BASE_STONE_OVERWORLD)) return false;
                for (int y = 0; y < height; y++) {
                    var pos = center.offset(x, y, z);
                    state = level.getBlockState(pos);
                    if (state.hasBlockEntity() || !state.getFluidState().isEmpty()) return false;
                    if (!state.isAir() && !state.canBeReplaced() && !(placement && state.is(BlockTags.LEAVES))) return false;
                }
            }
        }
        return true;
    }

    private static boolean intact(ServerLevel level, BlockPos center, QuestSiteDefinition config) {
        int remaining = config.minimumSupportBlocks();
        var support = net.minecraft.tags.TagKey.create(Registries.BLOCK, config.supportTag());
        for (int x = -16; x <= 16; x++)
            for (int z = -16; z <= 16; z++)
                for (int y = Math.max(level.getMinBuildHeight(), center.getY() - 24); y <= Math.min(level.getMaxBuildHeight() - 1, center.getY() + 16); y++) {
                    var state = level.getBlockState(new BlockPos(center.getX() + x, y, center.getZ() + z));
                    if (state.is(support) && --remaining <= 0) return true;
                }
        return false;
    }

    static boolean usable(ServerLevel level, Site site, QuestSiteDefinition config) {
        if (site.template == null) return intact(level, site.position, config);
        var template = template(level, site).orElse(null);
        if (template == null) return false;
        var origin = site.position.offset(-template.getSize().getX() / 2, -1, -template.getSize().getZ() / 2);
        var saved = template.save(new CompoundTag());
        var palette = saved.getList("palette", net.minecraft.nbt.Tag.TAG_COMPOUND);
        var blocks = saved.getList("blocks", net.minecraft.nbt.Tag.TAG_COMPOUND);
        int total = 0, retained = 0;
        for (int i = 0; i < blocks.size(); i++) {
            var block = blocks.getCompound(i);
            var pos = block.getList("pos", net.minecraft.nbt.Tag.TAG_INT);
            if (pos.size() != 3 || pos.getInt(1) == 0) continue;
            var id = net.minecraft.resources.ResourceLocation.tryParse(palette.getCompound(block.getInt("state")).getString("Name"));
            if (id == null || id.equals(net.minecraft.resources.ResourceLocation.withDefaultNamespace("air"))
                    || id.equals(net.minecraft.resources.ResourceLocation.withDefaultNamespace("structure_block"))
                    || id.equals(net.minecraft.resources.ResourceLocation.withDefaultNamespace("structure_void"))) continue;
            total++;
            var actual = level.getBlockState(origin.offset(pos.getInt(0), pos.getInt(1), pos.getInt(2)));
            if (net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(actual.getBlock()).equals(id)) retained++;
        }
        return total == 0 || retained >= Math.max(1, (total + 1) / 2);
    }

    private static boolean wilderness(ServerLevel level, BlockPos center, int radius) {
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            var top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.offset(x, 0, z));
            for (int y = -2; y <= 4; y++) {
                var state = level.getBlockState(top.offset(0, y, 0));
                if (!state.isAir() && !state.canBeReplaced() && !state.is(BlockTags.DIRT)
                        && !state.is(BlockTags.SAND) && !state.is(BlockTags.BASE_STONE_OVERWORLD)
                        && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES)
                        && !state.is(Blocks.WATER) && !state.is(Blocks.SNOW_BLOCK) && !state.is(Blocks.ICE)) return false;
            }
        }
        return true;
    }

    static boolean conflicts(ServerLevel level, Site site, BlockPos center, int radius) {
        var area = new AABB(center).inflate(radius, 32, radius);
        if (level.players().stream().anyMatch(player -> player.distanceToSqr(center.getCenter()) < 128 * 128)) return true;
        if (com.jvn.villagerretaliation.village.VillageRegistrySavedData.get(level).entries().stream()
                .anyMatch(village -> village.dimension().equals(level.dimension().location())
                        && village.center().distSqr(center) < (radius + 128.0) * (radius + 128.0))) return true;
        if (QuestSiteSavedData.get(level).sites.values().stream().anyMatch(other -> other != site && other.position != null
                && other.dimension.equals(site.dimension) && other.position.distSqr(center) < (radius + 48.0) * (radius + 48.0))) return true;
        for (var entity : level.getEntities((net.minecraft.world.entity.Entity) null, area)) {
            if (entity instanceof net.minecraft.world.entity.decoration.HangingEntity
                    || entity instanceof net.minecraft.world.entity.decoration.ArmorStand
                    || entity instanceof net.minecraft.world.entity.npc.AbstractVillager
                    || entity instanceof net.minecraft.world.entity.TamableAnimal tame && tame.isTame()) return true;
        }
        for (int x = (center.getX() - radius) >> 4; x <= (center.getX() + radius) >> 4; x++) {
            for (int z = (center.getZ() - radius) >> 4; z <= (center.getZ() + radius) >> 4; z++) {
                var chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) continue;
                if (site.template != null && !chunk.getBlockEntities().isEmpty()) return true;
                if (site.template != null && chunk.getAllStarts().values().stream().anyMatch(start -> start.isValid())) return true;
            }
        }
        boolean loaded = true;
        for (int x = (center.getX() - radius) >> 4; x <= (center.getX() + radius) >> 4; x++)
            for (int z = (center.getZ() - radius) >> 4; z <= (center.getZ() + radius) >> 4; z++)
                loaded &= level.getChunkSource().getChunkNow(x, z) != null;
        return site.template != null && loaded && !wilderness(level, center, radius);
    }

    private record Reads(List<CompletableFuture<Optional<CompoundTag>>> values) { }
    private record Loads(List<? extends CompletableFuture<? extends net.minecraft.server.level.ChunkResult<?>>> values) { }
}
