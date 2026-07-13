package com.jvn.villagerretaliation.allegiance;

import com.jvn.villagerretaliation.util.VillagerRetaliationTags;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/** Builds a loaded-only village footprint from POIs, tagged structures, and connected tagged terrain. */
public final class VillageFootprintResolver {
    private static final int[][] HORIZONTAL_NEIGHBORS = {
            {-1, 0}, {1, 0}, {0, -1}, {0, 1}
    };

    private VillageFootprintResolver() {
    }

    public static Set<Long> resolve(
            ServerLevel level,
            Collection<Long> baseSections,
            BlockPos origin,
            int radiusBlocks) {
        LinkedHashSet<Long> anchors = new LinkedHashSet<>(baseSections == null ? Set.of() : baseSections);
        if (level == null || origin == null || anchors.isEmpty()) {
            return Set.copyOf(anchors);
        }
        int chunkRadius = Math.floorDiv(Math.max(0, radiusBlocks), 16) + 1;
        ChunkPos originChunk = new ChunkPos(origin);
        for (Set<Long> structure : taggedStructureSections(level, originChunk, chunkRadius)) {
            if (touches(anchors, structure)) {
                anchors.addAll(structure);
            }
        }
        Set<Long> terrain = taggedTerrainBlocks(level, originChunk, chunkRadius);
        Set<Long> connectedTerrain = connectedTerrainSections(terrain, anchors);
        LinkedHashSet<Long> footprint = horizontalPadding(baseSections);
        footprint.addAll(anchors);
        footprint.addAll(connectedTerrain);
        return Set.copyOf(footprint);
    }

    private static LinkedHashSet<Long> horizontalPadding(Collection<Long> sections) {
        LinkedHashSet<Long> footprint = new LinkedHashSet<>();
        for (long packed : sections) {
            SectionPos section = SectionPos.of(packed);
            footprint.add(packed);
            footprint.add(SectionPos.asLong(section.x() - 1, section.y(), section.z()));
            footprint.add(SectionPos.asLong(section.x() + 1, section.y(), section.z()));
            footprint.add(SectionPos.asLong(section.x(), section.y(), section.z() - 1));
            footprint.add(SectionPos.asLong(section.x(), section.y(), section.z() + 1));
        }
        return footprint;
    }

    private static Collection<Set<Long>> taggedStructureSections(ServerLevel level, ChunkPos origin, int chunkRadius) {
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Set<StructureKey> seen = new HashSet<>();
        Collection<Set<Long>> structures = new java.util.ArrayList<>();
        for (int chunkX = origin.x - chunkRadius; chunkX <= origin.x + chunkRadius; chunkX++) {
            for (int chunkZ = origin.z - chunkRadius; chunkZ <= origin.z + chunkRadius; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                LevelChunk referenceChunk = level.getChunk(chunkX, chunkZ);
                for (Map.Entry<Structure, LongSet> entry : referenceChunk.getAllReferences().entrySet()) {
                    Structure structure = entry.getKey();
                    if (!registry.getHolder(registry.getId(structure))
                            .map(holder -> holder.is(VillagerRetaliationTags.Structures.VILLAGE_FOOTPRINT))
                            .orElse(false)) {
                        continue;
                    }
                    ResourceLocation structureId = registry.getKey(structure);
                    for (long packedStart : entry.getValue()) {
                        ChunkPos startChunk = new ChunkPos(packedStart);
                        StructureKey key = new StructureKey(structureId, startChunk.toLong());
                        if (!seen.add(key) || !level.hasChunk(startChunk.x, startChunk.z)) {
                            continue;
                        }
                        StructureStart start = level.getChunk(startChunk.x, startChunk.z).getStartForStructure(structure);
                        if (start == null || !start.isValid()) {
                            continue;
                        }
                        Set<Long> sections = new LinkedHashSet<>();
                        for (StructurePiece piece : start.getPieces()) {
                            addSections(sections, piece.getBoundingBox());
                        }
                        if (!sections.isEmpty()) {
                            structures.add(sections);
                        }
                    }
                }
            }
        }
        return structures;
    }

    private static Set<Long> taggedTerrainBlocks(ServerLevel level, ChunkPos origin, int chunkRadius) {
        Set<Long> blocks = new HashSet<>();
        for (int chunkX = origin.x - chunkRadius; chunkX <= origin.x + chunkRadius; chunkX++) {
            for (int chunkZ = origin.z - chunkRadius; chunkZ <= origin.z + chunkRadius; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                LevelChunkSection[] chunkSections = chunk.getSections();
                for (int index = 0; index < chunkSections.length; index++) {
                    LevelChunkSection section = chunkSections[index];
                    if (!section.hasOnlyAir()
                            && section.maybeHas(state -> state.is(VillagerRetaliationTags.Blocks.VILLAGE_TERRAIN))) {
                        int sectionY = chunk.getMinSection() + index;
                        int minX = SectionPos.sectionToBlockCoord(chunkX);
                        int minY = SectionPos.sectionToBlockCoord(sectionY);
                        int minZ = SectionPos.sectionToBlockCoord(chunkZ);
                        for (int localY = 0; localY < 16; localY++) {
                            for (int localZ = 0; localZ < 16; localZ++) {
                                for (int localX = 0; localX < 16; localX++) {
                                    if (section.getBlockState(localX, localY, localZ)
                                            .is(VillagerRetaliationTags.Blocks.VILLAGE_TERRAIN)) {
                                        blocks.add(BlockPos.asLong(
                                                minX + localX, minY + localY, minZ + localZ));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return blocks;
    }

    private static Set<Long> connectedTerrainSections(Collection<Long> candidates, Collection<Long> footprint) {
        Set<Long> remaining = new HashSet<>(candidates);
        Set<Long> connectedSections = new LinkedHashSet<>();
        ArrayDeque<Long> pending = new ArrayDeque<>();
        remaining.removeIf(packed -> {
            BlockPos pos = BlockPos.of(packed);
            if (!touchesFootprint(pos, footprint)) {
                return false;
            }
            pending.addLast(packed);
            connectedSections.add(SectionPos.asLong(pos));
            return true;
        });
        while (!pending.isEmpty()) {
            BlockPos current = BlockPos.of(pending.removeFirst());
            for (int dy = -1; dy <= 1; dy++) {
                for (int[] horizontal : HORIZONTAL_NEIGHBORS) {
                    long neighbor = BlockPos.asLong(
                            current.getX() + horizontal[0],
                            current.getY() + dy,
                            current.getZ() + horizontal[1]);
                    if (remaining.remove(neighbor)) {
                        pending.addLast(neighbor);
                        connectedSections.add(SectionPos.asLong(BlockPos.of(neighbor)));
                    }
                }
            }
        }
        return connectedSections;
    }

    private static boolean touchesFootprint(BlockPos pos, Collection<Long> footprint) {
        int sectionY = SectionPos.blockToSectionCoord(pos.getY());
        for (long packed : footprint) {
            SectionPos section = SectionPos.of(packed);
            if (section.y() != sectionY) {
                continue;
            }
            int minX = SectionPos.sectionToBlockCoord(section.x());
            int minZ = SectionPos.sectionToBlockCoord(section.z());
            int distanceX = distanceToRange(pos.getX(), minX, minX + 15);
            int distanceZ = distanceToRange(pos.getZ(), minZ, minZ + 15);
            if (distanceX + distanceZ <= 1) {
                return true;
            }
        }
        return false;
    }

    private static int distanceToRange(int value, int min, int max) {
        if (value < min) {
            return min - value;
        }
        if (value > max) {
            return value - max;
        }
        return 0;
    }

    private static boolean touches(Collection<Long> first, Collection<Long> second) {
        Set<Long> secondSet = second instanceof Set<Long> set ? set : Set.copyOf(second);
        for (long packed : first) {
            SectionPos section = SectionPos.of(packed);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > 1) {
                        continue;
                    }
                    if (secondSet.contains(SectionPos.asLong(
                                section.x() + dx, section.y() + dy, section.z() + dz))) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static void addSections(Set<Long> sections, BoundingBox box) {
        int minX = SectionPos.blockToSectionCoord(box.minX());
        int minY = SectionPos.blockToSectionCoord(box.minY());
        int minZ = SectionPos.blockToSectionCoord(box.minZ());
        int maxX = SectionPos.blockToSectionCoord(box.maxX());
        int maxY = SectionPos.blockToSectionCoord(box.maxY());
        int maxZ = SectionPos.blockToSectionCoord(box.maxZ());
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    sections.add(SectionPos.asLong(x, y, z));
                }
            }
        }
    }

    private record StructureKey(ResourceLocation structureId, long startChunk) {
    }
}
