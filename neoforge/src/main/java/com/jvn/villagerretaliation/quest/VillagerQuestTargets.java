package com.jvn.villagerretaliation.quest;

import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.mojang.datafixers.util.Pair;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

final class VillagerQuestTargets {
    private static final int MIN_SPARSE_STRUCTURE_SEARCH_RADIUS = 1024;
    private static final int MIN_VERY_SPARSE_STRUCTURE_SEARCH_RADIUS = 2048;

    private VillagerQuestTargets() {
    }

    static boolean requiresLocatedTarget(QuestDefinition definition) {
        return definition.target().hasStructureTarget()
                || definition.objectives().stream()
                .anyMatch(objective -> !objective.optional()
                        && objective.type() == QuestDefinition.ObjectiveType.STRUCTURE_VISIT);
    }

    static Optional<LocatedTarget> locateInitialTarget(DialogueContext context, QuestDefinition definition) {
        if (definition.target().hasStructureTarget()) {
            return locateTarget(context.level(), context.villager().blockPosition(), definition);
        }
        return definition.objectives().stream()
                .filter(objective -> objective.type() == QuestDefinition.ObjectiveType.STRUCTURE_VISIT)
                .findFirst()
                .flatMap(objective -> locateTarget(context.level(), context.villager().blockPosition(), objective));
    }

    static Optional<LocatedTarget> locateTarget(ServerLevel level, BlockPos origin, QuestDefinition definition) {
        if (!definition.target().hasStructureTarget()) {
            return Optional.empty();
        }
        return locateStructure(
                level,
                origin,
                definition.target().structure(),
                definition.target().dimension(),
                definition.target().searchRadius(),
                "");
    }

    static Optional<LocatedTarget> locateTarget(
            ServerLevel level,
            BlockPos origin,
            QuestDefinition.Objective objective) {
        return locateStructure(
                level,
                origin,
                objective.structure(),
                objective.dimension(),
                objective.searchRadius(),
                objective.id());
    }

    static boolean isAtQuestTarget(
            ServerLevel level,
            BlockPos playerPos,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        if (!definition.target().hasStructureTarget()) {
            return false;
        }
        return isAtStructureTarget(
                level,
                playerPos,
                progress,
                definition.target().structure(),
                definition.target().discoveryRadius(),
                definition.target().pieces());
    }

    static boolean isAtObjectiveTarget(
            ServerLevel level,
            BlockPos playerPos,
            QuestDefinition.Objective objective,
            VillagerQuestSavedData.QuestProgress progress) {
        if (objective.structure() == null
                || progress == null
                || !objective.id().equals(progress.targetObjectiveId())) {
            return false;
        }
        return isAtStructureTarget(
                level,
                playerPos,
                progress,
                objective.structure(),
                objective.discoveryRadius(),
                objective.pieces());
    }

    private static Optional<LocatedTarget> locateStructure(
            ServerLevel level,
            BlockPos origin,
            ResourceLocation structureId,
            ResourceKey<Level> targetDimension,
            int searchRadius,
            String objectiveId) {
        if (structureId == null || !level.getServer().getWorldData().worldGenOptions().generateStructures()) {
            return Optional.empty();
        }
        if (targetDimension != null) {
            ServerLevel targetLevel = level.getServer().getLevel(targetDimension);
            return targetLevel == null
                    ? Optional.empty()
                    : locateStructureInLevel(targetLevel, projectedOrigin(origin, level.dimension(), targetLevel.dimension()), structureId, searchRadius)
                    .map(pos -> new LocatedTarget(targetLevel.dimension(), pos, objectiveId));
        }

        Optional<BlockPos> current = locateStructureInLevel(level, origin, structureId, searchRadius);
        if (current.isPresent()) {
            return current.map(pos -> new LocatedTarget(level.dimension(), pos, objectiveId));
        }
        for (ServerLevel candidate : level.getServer().getAllLevels()) {
            if (candidate == level) {
                continue;
            }
            Optional<BlockPos> found = locateStructureInLevel(
                    candidate,
                    projectedOrigin(origin, level.dimension(), candidate.dimension()),
                    structureId,
                    searchRadius);
            if (found.isPresent()) {
                return found.map(pos -> new LocatedTarget(candidate.dimension(), pos, objectiveId));
            }
        }
        return Optional.empty();
    }

    private static Optional<BlockPos> locateStructureInLevel(
            ServerLevel level,
            BlockPos origin,
            ResourceLocation structureId,
            int searchRadius) {
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        ResourceKey<Structure> structureKey = ResourceKey.create(Registries.STRUCTURE, structureId);
        Optional<Holder.Reference<Structure>> holder = registry.getHolder(structureKey);
        if (holder.isEmpty()) {
            return Optional.empty();
        }

        Pair<BlockPos, Holder<Structure>> nearest = level.getChunkSource().getGenerator().findNearestMapStructure(
                level,
                HolderSet.direct(holder.get()),
                origin,
                effectiveSearchRadius(structureId, searchRadius),
                false
        );
        return nearest == null ? Optional.empty() : Optional.of(nearest.getFirst());
    }

    private static int effectiveSearchRadius(ResourceLocation structureId, int searchRadius) {
        if (structureId == null) {
            return searchRadius;
        }
        return Math.max(searchRadius, switch (structureId.toString()) {
            case "minecraft:woodland_mansion" -> MIN_VERY_SPARSE_STRUCTURE_SEARCH_RADIUS;
            case "minecraft:ancient_city",
                    "minecraft:bastion_remnant",
                    "minecraft:end_city",
                    "minecraft:fortress",
                    "minecraft:trial_chambers" -> MIN_SPARSE_STRUCTURE_SEARCH_RADIUS;
            default -> searchRadius;
        });
    }

    private static BlockPos projectedOrigin(
            BlockPos origin,
            ResourceKey<Level> sourceDimension,
            ResourceKey<Level> targetDimension) {
        if (sourceDimension == Level.OVERWORLD && targetDimension == Level.NETHER) {
            return new BlockPos(origin.getX() >> 3, origin.getY(), origin.getZ() >> 3);
        }
        if (sourceDimension == Level.NETHER && targetDimension == Level.OVERWORLD) {
            return new BlockPos(origin.getX() << 3, origin.getY(), origin.getZ() << 3);
        }
        return origin;
    }

    private static boolean isAtStructureTarget(
            ServerLevel level,
            BlockPos playerPos,
            VillagerQuestSavedData.QuestProgress progress,
            ResourceLocation structureId,
            int discoveryRadius,
            List<String> expectedPieces) {
        if (progress == null
                || progress.targetDimension() == null
                || progress.targetPos() == null
                || level.dimension() != progress.targetDimension()
                || structureId == null) {
            return false;
        }
        double targetTolerance = Math.max(512.0D, (double) discoveryRadius * 4.0D);
        if (playerPos.distSqr(progress.targetPos()) > targetTolerance * targetTolerance) {
            return false;
        }

        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        ResourceKey<Structure> structureKey = ResourceKey.create(Registries.STRUCTURE, structureId);
        Optional<Holder.Reference<Structure>> holder = registry.getHolder(structureKey);
        if (holder.isEmpty()) {
            return false;
        }

        StructureStart start = level.structureManager()
                .getStructureWithPieceAt(playerPos, HolderSet.direct(holder.get()));
        if (!start.isValid()) {
            return false;
        }
        if (expectedPieces.isEmpty()) {
            return true;
        }
        for (StructurePiece piece : start.getPieces()) {
            if (!piece.getBoundingBox().isInside(playerPos)) {
                continue;
            }
            if (matchesStructurePiece(piece, expectedPieces)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesStructurePiece(StructurePiece piece, List<String> expectedPieces) {
        String pieceDescription = piece instanceof PoolElementStructurePiece poolPiece
                ? poolPiece.getElement().toString()
                : piece.toString();
        for (String expectedPiece : expectedPieces) {
            if (!expectedPiece.isBlank() && pieceDescription.contains(expectedPiece)) {
                return true;
            }
        }
        return false;
    }

    record LocatedTarget(ResourceKey<Level> dimension, BlockPos pos, String objectiveId) {
    }
}
