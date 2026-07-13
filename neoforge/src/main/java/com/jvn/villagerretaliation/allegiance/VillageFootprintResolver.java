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
    private VillageFootprintResolver() {
    }

    public static Set<Long> resolve(
            ServerLevel level,
            Collection<Long> baseSections,
            BlockPos origin,
            int radiusBlocks) {
        LinkedHashSet<Long> footprint = new LinkedHashSet<>(baseSections == null ? Set.of() : baseSections);
        if (level == null || origin == null || footprint.isEmpty()) {
            return Set.copyOf(footprint);
        }
        int chunkRadius = Math.floorDiv(Math.max(0, radiusBlocks), 16) + 1;
        ChunkPos originChunk = new ChunkPos(origin);
        for (Set<Long> structure : taggedStructureSections(level, originChunk, chunkRadius)) {
            if (touches(footprint, structure)) {
                footprint.addAll(structure);
            }
        }
        Set<Long> terrain = taggedTerrainSections(level, originChunk, chunkRadius);
        footprint.addAll(connectedTerrain(terrain, footprint));
        return Set.copyOf(footprint);
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

    private static Set<Long> taggedTerrainSections(ServerLevel level, ChunkPos origin, int chunkRadius) {
        Set<Long> sections = new LinkedHashSet<>();
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
                        sections.add(SectionPos.asLong(chunkX, chunk.getMinSection() + index, chunkZ));
                    }
                }
            }
        }
        return sections;
    }

    private static Set<Long> connectedTerrain(Collection<Long> candidates, Collection<Long> seeds) {
        Set<Long> remaining = new HashSet<>(candidates);
        Set<Long> connected = new LinkedHashSet<>();
        ArrayDeque<Long> pending = new ArrayDeque<>(seeds);
        while (!pending.isEmpty()) {
            SectionPos current = SectionPos.of(pending.removeFirst());
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1) {
                            continue;
                        }
                        long neighbor = SectionPos.asLong(current.x() + dx, current.y() + dy, current.z() + dz);
                        if (remaining.remove(neighbor) && connected.add(neighbor)) {
                            pending.addLast(neighbor);
                        }
                    }
                }
            }
        }
        return connected;
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
