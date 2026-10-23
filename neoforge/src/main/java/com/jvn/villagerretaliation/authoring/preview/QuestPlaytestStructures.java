package com.jvn.villagerretaliation.authoring.preview;

import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.tracking.VillagerQuestTargets;
import com.jvn.villagerretaliation.util.VillagerEntityResolver;
import com.jvn.villagerretaliation.util.VillagerWorldTargetCache;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class QuestPlaytestStructures {
    private QuestPlaytestStructures() { }

    public static Optional<VillagerQuestTargets.LocatedTarget> target(DialogueContext context, ResourceLocation quest, String objective) {
        var controls = QuestPlaytestSession.controls(context.player(), quest);
        return controls == null ? Optional.empty() : Optional.ofNullable(controls.target(objective));
    }

    public static String spawn(ServerPlayer player, ResourceLocation quest, String objectiveId) {
        var controls = QuestPlaytestSession.require(player, quest);
        var definition = VillagerQuestResources.quest(player.server, quest).orElseThrow(() -> new IllegalArgumentException("Unknown quest " + quest));
        var selection = QuestPlaytestSession.selection(player);
        if (selection == null || !selection.quest().equals(quest)) throw new IllegalStateException("Prepare a quest offer first");
        if (objectiveId.isBlank() && definition.target().site() != null) {
            var provider = VillagerEntityResolver.loaded(player.server, selection.provider());
            if (provider == null || provider.level() != player.level()) throw new IllegalStateException("Return to the quest provider before preparing its site");
            var context = VillagerInteractionService.createDialogueContext(player.serverLevel(), player, provider);
            if (!com.jvn.villagerretaliation.quest.site.QuestSiteService.offerAvailable(context, definition))
                throw new IllegalStateException(com.jvn.villagerretaliation.quest.site.QuestSiteService.issue(context, definition));
            return "Quest site preparation requested. Accept the quest from its provider when the site is ready.";
        }
        var objective = objectiveId.isBlank() ? null : definition.objectives().stream().filter(value -> value.id().equals(objectiveId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown objective " + objectiveId));
        var structureId = objective == null ? definition.target().structure() : objective.structure();
        var dimension = objective == null ? definition.target().dimension() : objective.dimension();
        if (structureId == null) throw new IllegalStateException("This target has no structure to spawn");
        if (objective != null && !objective.campaignLocation().isBlank()) throw new IllegalStateException("This objective uses a campaign location. Prepare its campaign binding instead.");
        if (controls.target(objectiveId) != null) return "A test structure is already prepared for this target. Travel to it to test the objective.";
        var level = dimension == null ? player.serverLevel() : player.server.getLevel(dimension);
        if (level == null) throw new IllegalStateException("The structure dimension is unavailable");
        var holder = VillagerWorldTargetCache.structureHolder(level.registryAccess().registryOrThrow(Registries.STRUCTURE), structureId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown structure " + structureId));
        var generator = level.getChunkSource().getGenerator();
        var origin = new ChunkPos(player.blockPosition().offset(256, 0, 0));
        var start = holder.value().generate(level.registryAccess(), generator, generator.getBiomeSource(), level.getChunkSource().randomState(),
                level.getStructureManager(), level.getSeed(), origin, 0, level, biome -> true);
        if (!start.isValid()) throw new IllegalStateException("This structure cannot generate in the current terrain. Travel to suitable terrain and retry.");
        var requiredPieces = objective == null ? definition.target().pieces() : objective.pieces();
        var destination = start.getPieces().stream().filter(piece -> {
            String description = piece instanceof net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece pool ? pool.getElement().toString() : piece.toString();
            return requiredPieces.isEmpty() || requiredPieces.stream().anyMatch(description::contains);
        }).findFirst().orElseThrow(() -> new IllegalStateException("The generated structure has none of this target's required pieces. Try another area."));
        var bounds = start.getBoundingBox();
        var first = new ChunkPos(bounds.minX() >> 4, bounds.minZ() >> 4);
        var last = new ChunkPos(bounds.maxX() >> 4, bounds.maxZ() >> 4);
        long chunkCount = ((long) last.x - first.x + 1) * ((long) last.z - first.z + 1);
        if (chunkCount > 64) throw new IllegalStateException("This structure spans more than 64 chunks. Use an existing world structure for this test.");
        if ((!level.getWorldBorder().isWithinBounds(new net.minecraft.core.BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()))
                || !level.getWorldBorder().isWithinBounds(new net.minecraft.core.BlockPos(bounds.maxX(), bounds.maxY(), bounds.maxZ()))) || bounds.minY() < level.getMinBuildHeight() || bounds.maxY() >= level.getMaxBuildHeight())
            throw new IllegalStateException("The structure would extend outside the world bounds");
        var chunks = ChunkPos.rangeClosed(first, last).toList();
        for (var chunk : chunks) {
            var loaded = level.getChunk(chunk.x, chunk.z);
            if (!loaded.getBlockEntities().isEmpty() || loaded.getAllStarts().values().stream().anyMatch(existing -> existing.isValid() && existing.getBoundingBox().intersects(bounds)))
                throw new IllegalStateException("The structure area contains an existing structure or block entity. Move to a clear area and retry.");
        }
        for (var entity : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                net.minecraft.world.phys.AABB.of(bounds).inflate(16))) {
            if (entity instanceof net.minecraft.world.entity.player.Player || entity instanceof net.minecraft.world.entity.npc.Villager)
                throw new IllegalStateException("The structure area contains a player or villager. Move to a clear area and retry.");
        }
        for (var chunk : chunks) start.placeInChunk(level, level.structureManager(), generator, level.getRandom(),
                new BoundingBox(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(),
                        chunk.getMaxBlockX(), level.getMaxBuildHeight(), chunk.getMaxBlockZ()), chunk);
        // visit objectives query structure starts and references, not just the placed blocks
        level.getChunk(origin.x, origin.z).setStartForStructure(holder.value(), start);
        for (var chunk : chunks) level.getChunk(chunk.x, chunk.z).addReferenceForStructure(holder.value(), origin.toLong());
        var target = new VillagerQuestTargets.LocatedTarget(level.dimension(), destination.getBoundingBox().getCenter(), objectiveId);
        controls.target(target);
        var data = VillagerQuestSavedData.get(player.serverLevel());
        var progress = data.get(player.getUUID(), quest);
        if (progress != null && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE
                && (objectiveId.isBlank() || objectiveId.equals(progress.targetObjectiveId()))) {
            progress.setTarget(progress.startedVillagerId(), target.dimension(), target.pos(), objectiveId);
            data.setDirty();
            com.jvn.villagerretaliation.quest.VillagerQuestService.refreshTracker(player);
        }
        return "Spawned " + structureId + " at " + target.pos().toShortString() + ". Travel to the target to test it.";
    }
}
