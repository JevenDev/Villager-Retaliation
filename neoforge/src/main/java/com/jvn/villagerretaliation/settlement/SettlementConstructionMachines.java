package com.jvn.villagerretaliation.settlement;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

final class SettlementConstructionMachines {
    private final Map<BlockPos, Source> sources = new HashMap<>();

    SettlementConstructionMachines(ServerLevel level, SettlementRecord town, LevelChunk supplied) {
        if (!town.dimension().equals(level.dimension().location())) return;
        SettlementBuildingSurvey.ObservedBlocks blocks = null;
        for (var project : town.projects().values()) {
            var site = project.site();
            if (site == null || !town.owns(project)) continue;
            for (var placement : site.placements().values()) {
                for (var part : placement.parts()) {
                    if (!part.after().is(Blocks.FURNACE) && !part.after().is(Blocks.SMOKER)
                            && !part.after().is(Blocks.BLAST_FURNACE) && !part.after().is(Blocks.BREWING_STAND)) continue;
                    if (blocks == null) blocks = new SettlementBuildingSurvey.ObservedBlocks(level, supplied, town);
                    var pos = part.position();
                    var chunk = blocks.chunk(pos.getX() >> 4, pos.getZ() >> 4);
                    if (chunk != null && placement.applied(chunk, project.id(), pos)) continue;
                    this.sources.put(pos, new Source(placement.id(), part, blocks.pendingConstruction(pos, part.after())));
                }
            }
        }
    }

    Source source(BlockPos position) {
        return this.sources.get(position);
    }

    Source source(BlockPos position, UUID id) {
        var source = source(position);
        if (source == null || source.id().equals(id)) return source;
        return new Source(source.id(), source.part(), false);
    }

    static void restore(ServerLevel level, UUID id, SettlementConstructionPlacement.Part part, BlockEntity entity) {
        if (part.blockEntity() != null) {
            var tag = part.blockEntity().copy();
            tag.remove("LootTable");
            tag.remove("LootTableSeed");
            tag.putInt("x", part.position().getX());
            tag.putInt("y", part.position().getY());
            tag.putInt("z", part.position().getZ());
            entity.loadWithComponents(tag, level.registryAccess());
        }
        if (entity instanceof AbstractFurnaceBlockEntity furnace && SettlementFurnaceState.supported(furnace)) {
            SettlementFurnaceState.bindConstruction(furnace, id);
        } else if (entity instanceof BrewingStandBlockEntity stand && SettlementBrewingStandState.supported(stand)) {
            SettlementBrewingStandState.bindConstruction(stand, id);
        }
    }

    static void delivered(ServerLevel level, UUID project, BlockEntity entity) {
        if (!(entity instanceof AbstractFurnaceBlockEntity) && !(entity instanceof BrewingStandBlockEntity)) return;
        for (var town : SettlementSavedData.get(level).settlements()) {
            if (!town.dimension().equals(level.dimension().location()) || !town.projects().containsKey(project)) continue;
            if (entity instanceof AbstractFurnaceBlockEntity furnace) {
                var state = town.furnaces().get(entity.getBlockPos());
                if (state != null && state.matches(furnace)) {
                    SettlementFurnaceService.applyLoaded(level, state);
                    state.pause(level.getGameTime());
                }
            } else if (entity instanceof BrewingStandBlockEntity stand) {
                var state = town.brewingStands().get(entity.getBlockPos());
                if (state != null && state.matches(stand)) {
                    SettlementBrewingStandService.applyLoaded(level, state);
                    state.pause(level.getGameTime());
                }
            }
            town.changed();
            return;
        }
    }

    record Source(UUID id, SettlementConstructionPlacement.Part part, boolean ready) {
        BlockEntity create(ServerLevel level) {
            var entity = ((EntityBlock) this.part.after().getBlock()).newBlockEntity(this.part.position(), this.part.after());
            if (entity == null) throw new IllegalStateException("Construction machine has no block entity");
            restore(level, this.id, this.part, entity);
            return entity;
        }
    }
}
