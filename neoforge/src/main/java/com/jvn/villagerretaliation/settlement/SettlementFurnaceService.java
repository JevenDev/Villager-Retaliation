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
        var construction = new SettlementConstructionMachines(level, town, chunk);
        for (SettlementFurnaceState state : List.copyOf(town.furnaces().values())) {
            if (!new ChunkPos(state.position()).equals(chunk.getPos())) {
                continue;
            }
            var source = construction.source(state.position(), state.id());
            if (source != null) {
                if (source.ready()) {
                    state.constructionReady();
                } else {
                    state.missing();
                    state.pause(level.getGameTime());
                }
                town.changed();
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
        var construction = new SettlementConstructionMachines(level, town, null);
        for (var entry : town.surveys().entrySet()) {
            var area = town.areas().get(entry.getKey());
            if (area == null || !area.enabled()) {
                continue;
            }
            for (BlockPos position : entry.getValue().fixtures().keySet()) {
                var existing = town.furnaces().get(position);
                var source = construction.source(position);
                if (source != null && source.ready()
                        && (existing == null || !existing.pending() && !existing.id().equals(source.id()))
                        && source.create(level) instanceof AbstractFurnaceBlockEntity furnace && accepts(area, furnace)) {
                    town.recordFurnace(SettlementFurnaceState.capture(level, area.id(), furnace, now));
                } else if (source == null && existing == null
                        && level.hasChunkAt(position) && level.getBlockEntity(position) instanceof AbstractFurnaceBlockEntity furnace
                        && accepts(area, furnace)) {
                    observe(level, town, area, furnace, now);
                    level.getChunkAt(position).setUnsaved(true);
                }
            }
        }
        for (SettlementFurnaceState state : List.copyOf(town.furnaces().values())) {
            boolean operating = town.areas().values().stream().anyMatch(area -> serves(area, state));
            var source = construction.source(state.position(), state.id());
            if (source != null) {
                if (source.ready()) {
                    state.constructionReady();
                    if (operating) {
                        state.advance(level, now);
                    } else {
                        state.pause(now);
                    }
                } else {
                    state.missing();
                    state.pause(now);
                }
                continue;
            }
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
                        || !operating) {
                    state.observe(level.registryAccess(), furnace, now);
                } else {
                    state.refresh(level.registryAccess(), furnace);
                    state.advance(level, now);
                    applyLoaded(level, state);
                }
            } else if (operating) {
                state.advance(level, now);
            } else {
                state.pause(now);
            }
        }
        if (!town.furnaces().isEmpty()) {
            town.changed();
        }
    }

    static boolean available(ServerLevel level, SettlementFurnaceState state, SettlementConstructionMachines construction) {
        var source = construction.source(state.position(), state.id());
        if (source != null) {
            if (source.ready()) state.constructionReady();
            return source.ready();
        }
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

    static boolean serves(SettlementArea area, SettlementFurnaceState state) {
        return area != null && area.enabled() && accepts(area, state.position(), state.block());
    }

    private static boolean accepts(SettlementArea area, AbstractFurnaceBlockEntity furnace) {
        return SettlementFurnaceState.supported(furnace)
                && accepts(area, furnace.getBlockPos(), net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(furnace.getBlockState().getBlock()));
    }

    private static boolean accepts(SettlementArea area, BlockPos position, net.minecraft.resources.ResourceLocation block) {
        return area.contains(position) && (area.type() == SettlementAreaType.KITCHEN
                && !block.equals(net.minecraft.resources.ResourceLocation.withDefaultNamespace("blast_furnace"))
                || area.type() == SettlementAreaType.SMELTERY
                && !block.equals(net.minecraft.resources.ResourceLocation.withDefaultNamespace("smoker")));
    }

    private static void observe(ServerLevel level, SettlementRecord town, SettlementArea area,
                                AbstractFurnaceBlockEntity furnace, long now) {
        var state = town.furnaces().get(furnace.getBlockPos());
        if (state == null || !state.pending() && !state.matches(furnace)) {
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
