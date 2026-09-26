package com.jvn.villagerretaliation.settlement;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent;

public final class SettlementFurnaceService {
    private SettlementFurnaceService() {
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            for (SettlementRecord town : SettlementSavedData.get(level).settlements()) {
                if (town.dimension().equals(level.dimension().location())) {
                    reconcileChunk(level, town, chunk);
                }
            }
        }
    }

    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            for (SettlementRecord town : SettlementSavedData.get(level).settlements()) {
                if (!town.dimension().equals(level.dimension().location())) {
                    continue;
                }
                reconcileChunk(level, town, chunk);
                for (var blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity instanceof AbstractFurnaceBlockEntity furnace && SettlementFurnaceState.supported(furnace)) {
                        var area = town.areas().values().stream().filter(candidate -> accepts(candidate, furnace)).findFirst().orElse(null);
                        if (area != null) {
                            observe(level, town, area, furnace, level.getGameTime());
                            chunk.setUnsaved(true);
                        }
                    }
                }
            }
        }
    }

    static void reconcileChunk(ServerLevel level, SettlementRecord town, LevelChunk chunk) {
        for (SettlementFurnaceState state : List.copyOf(town.furnaces().values())) {
            if (!new ChunkPos(state.position()).equals(chunk.getPos())) {
                continue;
            }
            boolean pending = state.pending();
            if (chunk.getBlockEntity(state.position()) instanceof AbstractFurnaceBlockEntity furnace
                    && state.reconcile(level.registryAccess(), furnace)) {
                state.observe(level.registryAccess(), furnace, level.getGameTime());
                updateLit(chunk, state);
                if (pending) {
                    chunk.setUnsaved(true);
                    level.getServer().tell(new net.minecraft.server.TickTask(level.getServer().getTickCount(),
                            () -> applyLoaded(level, state)));
                }
            } else if (state.pending()) {
                state.missing();
                state.pause(level.getGameTime());
            } else {
                town.removeFurnace(state.position());
            }
            town.changed();
        }
    }

    static void update(ServerLevel level, SettlementRecord town, long now) {
        for (var entry : town.surveys().entrySet()) {
            var area = town.areas().get(entry.getKey());
            if (area == null || !area.enabled()) {
                continue;
            }
            for (BlockPos position : entry.getValue().fixtures().keySet()) {
                var existing = town.furnaces().get(position);
                if ((existing == null || !existing.pending() && !existing.area().equals(area.id()))
                        && level.hasChunkAt(position)
                        && level.getBlockEntity(position) instanceof AbstractFurnaceBlockEntity furnace && accepts(area, furnace)) {
                    observe(level, town, area, furnace, now);
                    level.getChunkAt(position).setUnsaved(true);
                }
            }
        }
        for (SettlementFurnaceState state : List.copyOf(town.furnaces().values())) {
            var area = town.areas().get(state.area());
            boolean pending = state.pending();
            if (level.hasChunkAt(state.position())) {
                if (!(level.getBlockEntity(state.position()) instanceof AbstractFurnaceBlockEntity furnace)
                        || !state.reconcile(level.registryAccess(), furnace)) {
                    if (state.pending()) {
                        state.missing();
                        state.pause(now);
                    } else {
                        town.removeFurnace(state.position());
                    }
                    continue;
                }
                if (pending) {
                    applyLoaded(level, state);
                }
                if (level.getChunkSource().isPositionTicking(ChunkPos.asLong(state.position()))
                        || area == null || !area.enabled() || !accepts(area, furnace)) {
                    state.observe(level.registryAccess(), furnace, now);
                } else {
                    state.refresh(level.registryAccess(), furnace);
                    state.advance(level, now);
                    applyLoaded(level, state);
                }
            } else if (area != null && area.enabled() && area.contains(state.position())) {
                state.advance(level, now);
            } else {
                state.pause(now);
            }
        }
        if (!town.furnaces().isEmpty()) {
            town.changed();
        }
    }

    static boolean available(ServerLevel level, SettlementFurnaceState state) {
        if (state.conflict()) {
            return false;
        }
        if (!level.hasChunkAt(state.position())) {
            return true;
        }
        if (level.getBlockEntity(state.position()) instanceof AbstractFurnaceBlockEntity furnace
                && state.reconcile(level.registryAccess(), furnace)) {
            state.refresh(level.registryAccess(), furnace);
            return true;
        }
        return false;
    }

    static void applyLoaded(ServerLevel level, SettlementFurnaceState state) {
        if (!level.hasChunkAt(state.position())) {
            return;
        }
        if (level.getBlockEntity(state.position()) instanceof AbstractFurnaceBlockEntity furnace
                && state.reconcile(level.registryAccess(), furnace)) {
            state.refresh(level.registryAccess(), furnace);
            var previous = level.getBlockState(state.position());
            var updated = previous.setValue(AbstractFurnaceBlock.LIT, state.burnTime() > 0);
            if (previous != updated) {
                level.setBlock(state.position(), updated, 3);
            }
            furnace.setChanged();
            level.sendBlockUpdated(state.position(), previous, updated, 3);
        }
    }

    private static boolean accepts(SettlementArea area, AbstractFurnaceBlockEntity furnace) {
        return area.contains(furnace.getBlockPos()) && SettlementFurnaceState.supported(furnace)
                && (area.type() == SettlementAreaType.KITCHEN
                        && !(furnace instanceof net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity)
                    || area.type() == SettlementAreaType.SMELTERY
                        && !(furnace instanceof net.minecraft.world.level.block.entity.SmokerBlockEntity));
    }

    private static void observe(ServerLevel level, SettlementRecord town, SettlementArea area,
                                AbstractFurnaceBlockEntity furnace, long now) {
        var state = town.furnaces().get(furnace.getBlockPos());
        if (state == null || !state.pending() && (!state.matches(furnace) || !state.area().equals(area.id()))) {
            town.recordFurnace(SettlementFurnaceState.capture(level, area.id(), furnace, now));
        } else if (state.reconcile(level.registryAccess(), furnace)) {
            state.observe(level.registryAccess(), furnace, now);
            town.changed();
        }
    }

    private static void updateLit(LevelChunk chunk, SettlementFurnaceState state) {
        var block = chunk.getBlockState(state.position());
        boolean lit = state.burnTime() > 0;
        if (block.hasProperty(AbstractFurnaceBlock.LIT) && block.getValue(AbstractFurnaceBlock.LIT) != lit) {
            chunk.setBlockState(state.position(), block.setValue(AbstractFurnaceBlock.LIT, lit), false);
        }
    }
}
