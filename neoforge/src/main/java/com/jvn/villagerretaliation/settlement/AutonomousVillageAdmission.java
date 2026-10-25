package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

public final class AutonomousVillageAdmission {
    private AutonomousVillageAdmission() {
    }

    public static Result inspect(ServerLevel level, VillageAllegianceId id) {
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var village = registry.canonicalRecord(id).orElse(null);
        if (village == null || village.archived() || !level.dimension().location().equals(village.originDimension())) {
            return Result.INVALID_VILLAGE;
        }
        if (SettlementSavedData.get(level).get(village.id()).isPresent()) {
            return Result.EXISTING_SETTLEMENT;
        }
        if (registry.isManaged(village.id())) {
            return Result.MANAGED_VILLAGE;
        }
        if (village.sourceSections().isEmpty() || village.footprintSections().isEmpty()
                || village.footprintSections().size() > 65536 || village.residents().size() > 128) {
            return Result.NO_SOURCE_FOOTPRINT;
        }
        if (level.getChunkSource().getChunkNow(village.center().getX() >> 4, village.center().getZ() >> 4) == null) {
            return Result.EVIDENCE_UNLOADED;
        }
        boolean resident = village.residents().keySet().stream().anyMatch(residentId ->
                level.getEntity(residentId) instanceof Villager villager && villager.isAlive()
                        && registry.contains(village, villager.blockPosition())
                        && VillageAllegianceApi.canonicalPrimary(level, villager).filter(village.id()::equals).isPresent());
        if (!resident) {
            return Result.NO_LOCAL_RESIDENT;
        }
        return generatedEvidence(level, village.sourceSections());
    }

    private static Result generatedEvidence(ServerLevel level, Set<Long> sources) {
        var structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Set<Long> chunks = new LinkedHashSet<>();
        for (long section : sources) {
            chunks.add(ChunkPos.asLong(SectionPos.x(section), SectionPos.z(section)));
        }
        Set<BlockPos> occupiedSources = new HashSet<>();
        Set<StructureStart> starts = new LinkedHashSet<>();
        Set<StartKey> seen = new HashSet<>();
        boolean incomplete = false;
        for (long packed : chunks) {
            var chunkPos = new ChunkPos(packed);
            var chunk = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
            if (chunk == null) {
                incomplete = true;
                continue;
            }
            level.getPoiManager().getInChunk(type -> type.is(PoiTypeTags.VILLAGE), chunkPos, PoiManager.Occupancy.IS_OCCUPIED)
                    .filter(poi -> sources.contains(SectionPos.asLong(poi.getPos())))
                    .forEach(poi -> occupiedSources.add(poi.getPos().immutable()));
            for (var entry : chunk.getAllStarts().entrySet()) {
                if (structures.wrapAsHolder(entry.getKey()).is(StructureTags.VILLAGE) && entry.getValue().isValid()) {
                    starts.add(entry.getValue());
                }
            }
            for (var entry : chunk.getAllReferences().entrySet()) {
                if (!structures.wrapAsHolder(entry.getKey()).is(StructureTags.VILLAGE)) {
                    continue;
                }
                for (long reference : entry.getValue()) {
                    if (!seen.add(new StartKey(entry.getKey(), reference))) {
                        continue;
                    }
                    var pos = new ChunkPos(reference);
                    var startChunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
                    if (startChunk == null) {
                        incomplete = true;
                        continue;
                    }
                    var start = startChunk.getStartForStructure(entry.getKey());
                    if (start != null && start.isValid()) {
                        starts.add(start);
                    }
                }
            }
        }
        boolean anchored = starts.stream().flatMap(start -> start.getPieces().stream())
                .anyMatch(piece -> occupiedSources.stream().anyMatch(piece.getBoundingBox()::isInside));
        return anchored ? Result.ELIGIBLE : incomplete ? Result.EVIDENCE_UNLOADED : Result.NO_GENERATED_VILLAGE;
    }

    private record StartKey(Structure structure, long chunk) {
    }

    public enum Result {
        ELIGIBLE("Eligible generated village"),
        EXISTING_SETTLEMENT("Already established, admission does not apply"),
        INVALID_VILLAGE("No active village in this dimension"),
        MANAGED_VILLAGE("Village is already managed"),
        NO_SOURCE_FOOTPRINT("No supported occupied village footprint"),
        EVIDENCE_UNLOADED("Generated village evidence is not fully loaded"),
        NO_LOCAL_RESIDENT("No loaded living resident at home"),
        NO_GENERATED_VILLAGE("No generated village anchored to occupied source POIs");

        private final String description;

        Result(String description) {
            this.description = description;
        }

        public String description() {
            return this.description;
        }
    }
}
