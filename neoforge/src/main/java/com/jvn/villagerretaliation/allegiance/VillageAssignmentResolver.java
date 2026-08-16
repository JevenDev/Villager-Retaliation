package com.jvn.villagerretaliation.allegiance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;

/** Scores assignment evidence without changing village or entity state. */
public final class VillageAssignmentResolver {
    private static final int MINIMUM_SCORE = 50;
    private static final int CLEAR_WIN_MARGIN = 15;
    private static final int COMPLETE_CHUNK_RADIUS = 2;

    private VillageAssignmentResolver() {
    }

    public static VillageAssignmentResolution resolve(
            ServerLevel level,
            Entity entity,
            BlockPos evidencePosition,
            Optional<VillageAllegianceId> discoveredVillage,
            List<VillageAllegianceId> parentAllegiances) {
        if (level == null || evidencePosition == null) {
            return new VillageAssignmentResolution(
                    VillageAssignmentResolution.Status.INCOMPLETE, null, List.of(), false);
        }
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        Map<VillageAllegianceId, MutableCandidate> scored = new HashMap<>();
        addPositionEvidence(registry, level, evidencePosition,
                VillageAssignmentResolution.Evidence.CURRENT_FOOTPRINT, 60, scored);
        discoveredVillage.flatMap(registry::canonical).ifPresent(id -> add(
                registry, id, evidencePosition,
                VillageAssignmentResolution.Evidence.OCCUPIED_POI_CLUSTER, 120, scored));

        if (entity instanceof Villager villager) {
            poiMemory(villager, level, MemoryModuleType.HOME).ifPresent(position -> addPositionEvidence(
                    registry, level, position, VillageAssignmentResolution.Evidence.HOME_POI, 90, scored));
            poiMemory(villager, level, MemoryModuleType.JOB_SITE).ifPresent(position -> addPositionEvidence(
                    registry, level, position, VillageAssignmentResolution.Evidence.JOB_SITE_POI, 75, scored));
        }
        if (parentAllegiances != null) {
            for (VillageAllegianceId parent : parentAllegiances) {
                registry.canonical(parent).ifPresent(id -> add(
                        registry, id, evidencePosition,
                        VillageAssignmentResolution.Evidence.PARENT_ALLEGIANCE, 110, scored));
            }
        }

        List<VillageAssignmentResolution.Candidate> candidates = scored.values().stream()
                .map(MutableCandidate::freeze)
                .sorted(Comparator.comparingInt(VillageAssignmentResolution.Candidate::score).reversed()
                        .thenComparingDouble(VillageAssignmentResolution.Candidate::distanceSquared)
                        .thenComparing(VillageAssignmentResolution.Candidate::id))
                .toList();
        boolean complete = observationComplete(level, evidencePosition);
        if (candidates.isEmpty()) {
            return new VillageAssignmentResolution(
                    complete ? VillageAssignmentResolution.Status.NONE : VillageAssignmentResolution.Status.INCOMPLETE,
                    null, candidates, complete);
        }
        VillageAssignmentResolution.Candidate first = candidates.getFirst();
        if (first.score() < MINIMUM_SCORE) {
            return new VillageAssignmentResolution(
                    complete ? VillageAssignmentResolution.Status.NONE : VillageAssignmentResolution.Status.INCOMPLETE,
                    null, candidates, complete);
        }
        if (candidates.size() > 1 && first.score() - candidates.get(1).score() < CLEAR_WIN_MARGIN) {
            return new VillageAssignmentResolution(
                    VillageAssignmentResolution.Status.AMBIGUOUS, null, candidates, complete);
        }
        return new VillageAssignmentResolution(
                VillageAssignmentResolution.Status.RESOLVED, first.id(), candidates, complete);
    }

    public static boolean observationComplete(ServerLevel level, BlockPos position) {
        ChunkPos center = new ChunkPos(position);
        for (int x = center.x - COMPLETE_CHUNK_RADIUS; x <= center.x + COMPLETE_CHUNK_RADIUS; x++) {
            for (int z = center.z - COMPLETE_CHUNK_RADIUS; z <= center.z + COMPLETE_CHUNK_RADIUS; z++) {
                if (!level.hasChunk(x, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void addPositionEvidence(
            VillageAllegianceRegistrySavedData registry,
            ServerLevel level,
            BlockPos position,
            VillageAssignmentResolution.Evidence evidence,
            int score,
            Map<VillageAllegianceId, MutableCandidate> candidates) {
        for (VillageAllegianceRegistrySavedData.AllegianceRecord record : registry.recordsAt(level, position)) {
            add(registry, record.id(), position, evidence, score, candidates);
            if (record.sourceSections().contains(SectionPos.asLong(position))) {
                add(registry, record.id(), position, evidence, 20, candidates);
            }
        }
    }

    private static void add(
            VillageAllegianceRegistrySavedData registry,
            VillageAllegianceId rawId,
            BlockPos position,
            VillageAssignmentResolution.Evidence evidence,
            int score,
            Map<VillageAllegianceId, MutableCandidate> candidates) {
        registry.canonicalRecord(rawId).ifPresent(record -> candidates
                .computeIfAbsent(record.id(), ignored -> new MutableCandidate(
                        record.id(), record.center().distSqr(position)))
                .add(evidence, score));
    }

    private static Optional<BlockPos> poiMemory(
            Villager villager,
            ServerLevel level,
            MemoryModuleType<GlobalPos> memoryType) {
        return villager.getBrain().getMemory(memoryType)
                .filter(global -> global.dimension().equals(level.dimension()))
                .map(GlobalPos::pos);
    }

    private static final class MutableCandidate {
        private final VillageAllegianceId id;
        private final double distanceSquared;
        private final EnumSet<VillageAssignmentResolution.Evidence> evidence =
                EnumSet.noneOf(VillageAssignmentResolution.Evidence.class);
        private int score;

        private MutableCandidate(VillageAllegianceId id, double distanceSquared) {
            this.id = id;
            this.distanceSquared = distanceSquared;
        }

        private void add(VillageAssignmentResolution.Evidence evidence, int score) {
            if (this.evidence.add(evidence)) {
                this.score += score;
            } else if (evidence == VillageAssignmentResolution.Evidence.CURRENT_FOOTPRINT) {
                this.score += score;
            }
        }

        private VillageAssignmentResolution.Candidate freeze() {
            return new VillageAssignmentResolution.Candidate(this.id, this.score, this.distanceSquared, this.evidence);
        }
    }
}
