package com.jvn.villagerretaliation.settlement;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent;

public final class SettlementBrewingStandService {
    private SettlementBrewingStandService() {
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            for (var town : SettlementSavedData.get(level).settlements()) {
                if (town.dimension().equals(level.dimension().location())) {
                    reconcileChunk(level, town, chunk);
                }
            }
        }
    }

    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            for (var town : SettlementSavedData.get(level).settlements()) {
                if (!town.dimension().equals(level.dimension().location())) {
                    continue;
                }
                reconcileChunk(level, town, chunk);
                for (var block : chunk.getBlockEntities().values()) {
                    if (block instanceof BrewingStandBlockEntity stand && SettlementBrewingStandState.supported(stand)) {
                        var area = town.areas().values().stream().filter(candidate -> town.owns(candidate) && accepts(candidate, stand)).findFirst().orElse(null);
                        if (area != null) {
                            observe(level, town, area, stand, level.getGameTime());
                            chunk.setUnsaved(true);
                        }
                    }
                }
            }
        }
    }

    static void reconcileChunk(ServerLevel level, SettlementRecord town, LevelChunk chunk) {
        var construction = new SettlementConstructionMachines(level, town, chunk);
        for (var state : List.copyOf(town.brewingStands().values())) {
            if (!town.contains(state.position()) || town.areas().containsKey(state.area()) && !town.owns(town.areas().get(state.area()))) continue;
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
            if (chunk.getBlockEntity(state.position()) instanceof BrewingStandBlockEntity stand
                    && state.reconcile(level.registryAccess(), stand)) {
                state.observe(level.registryAccess(), stand, level.getGameTime());
                BlockState previous = chunk.getBlockState(state.position());
                BlockState updated = bottles(level, state, previous);
                if (updated != previous) {
                    chunk.setBlockState(state.position(), updated, false);
                }
                if (pending) {
                    chunk.setUnsaved(true);
                    level.getServer().tell(new net.minecraft.server.TickTask(level.getServer().getTickCount(), () -> applyLoaded(level, state)));
                }
            } else if (state.pending()) {
                state.missing();
                state.pause(level.getGameTime());
            } else {
                town.removeBrewingStand(state.position());
            }
            town.changed();
        }
    }

    static void update(ServerLevel level, SettlementRecord town, long now) {
        var jobs = new java.util.ArrayList<Runnable>();
        schedule(level, town, now, jobs);
        var work = new SettlementWork(jobs);
        while (!SettlementWork.complete(work)) work.run();
    }

    static void schedule(ServerLevel level, SettlementRecord town, long now, List<Runnable> jobs) {
        for (var entry : town.surveys().entrySet()) {
            if (entry.getValue().fixtures().isEmpty()) continue;
            var areaId = entry.getKey();
            jobs.add(SettlementWork.each(List.copyOf(entry.getValue().fixtures().keySet()), position -> {
                var area = town.areas().get(areaId);
                if (area == null || !area.enabled() || area.type() != SettlementAreaType.BREWERY) return;
                observeFixture(level, town, area, position, now);
            }));
        }
        jobs.add(SettlementWork.lazy(() -> SettlementWork.each(List.copyOf(town.brewingStands().keySet()), position -> {
            var state = town.brewingStands().get(position);
            if (state != null) {
                advance(level, town, state, now);
                town.changed();
            }
        })));
    }

    private static void observeFixture(ServerLevel level, SettlementRecord town, SettlementArea area, BlockPos position, long now) {
        var construction = new SettlementConstructionMachines(level, town, null);
        var existing = town.brewingStands().get(position);
        var source = construction.source(position);
        if (source != null && source.ready()
                && (existing == null || !existing.pending() && (!existing.area().equals(area.id()) || !existing.id().equals(source.id())))
                && source.create(level) instanceof BrewingStandBlockEntity stand && accepts(area, stand)) {
            town.recordBrewingStand(SettlementBrewingStandState.capture(level, area.id(), stand, now));
        } else if (source == null && (existing == null || !existing.pending() && !existing.area().equals(area.id()))
                && level.hasChunkAt(position) && level.getBlockEntity(position) instanceof BrewingStandBlockEntity stand
                && accepts(area, stand)) {
            observe(level, town, area, stand, now);
            level.getChunkAt(position).setUnsaved(true);
        }
    }

    private static void advance(ServerLevel level, SettlementRecord town, SettlementBrewingStandState state, long now) {
        var construction = new SettlementConstructionMachines(level, town, null);
        if (!town.contains(state.position()) || town.areas().containsKey(state.area()) && !town.owns(town.areas().get(state.area()))) return;
        var area = town.areas().get(state.area());
        var source = construction.source(state.position(), state.id());
        if (source != null) {
            if (source.ready()) {
                state.constructionReady();
                if (area != null && area.enabled() && area.type() == SettlementAreaType.BREWERY && area.contains(state.position())) {
                    state.advance(level, now);
                } else {
                    state.pause(now);
                }
            } else {
                state.missing();
                state.pause(now);
            }
            return;
        }
        boolean pending = state.pending();
        if (level.hasChunkAt(state.position())) {
            if (!(level.getBlockEntity(state.position()) instanceof BrewingStandBlockEntity stand)
                    || !state.reconcile(level.registryAccess(), stand)) {
                if (state.pending()) {
                    state.missing();
                    state.pause(now);
                } else {
                    town.removeBrewingStand(state.position());
                }
                return;
            }
            if (pending) {
                applyLoaded(level, state);
            }
            if (level.getChunkSource().isPositionTicking(ChunkPos.asLong(state.position()))
                    || area == null || !area.enabled() || !accepts(area, stand)) {
                state.observe(level.registryAccess(), stand, now);
            } else {
                state.refresh(level.registryAccess(), stand);
                state.advance(level, now);
                applyLoaded(level, state);
            }
        } else if (area != null && area.enabled() && area.type() == SettlementAreaType.BREWERY && area.contains(state.position())) {
            state.advance(level, now);
        } else {
            state.pause(now);
        }
    }

    static boolean available(ServerLevel level, SettlementBrewingStandState state, SettlementConstructionMachines construction) {
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
        if (level.getBlockEntity(state.position()) instanceof BrewingStandBlockEntity stand && state.reconcile(level.registryAccess(), stand)) {
            state.refresh(level.registryAccess(), stand);
            return true;
        }
        return false;
    }

    static void applyLoaded(ServerLevel level, SettlementBrewingStandState state) {
        if (!level.hasChunkAt(state.position())) {
            return;
        }
        if (level.getBlockEntity(state.position()) instanceof BrewingStandBlockEntity stand && state.reconcile(level.registryAccess(), stand)) {
            state.refresh(level.registryAccess(), stand);
            var previous = level.getBlockState(state.position());
            var updated = bottles(level, state, previous);
            if (previous != updated) {
                level.setBlock(state.position(), updated, 3);
            }
            stand.setChanged();
            level.sendBlockUpdated(state.position(), previous, updated, 3);
        }
    }

    private static boolean accepts(SettlementArea area, BrewingStandBlockEntity stand) {
        return area.type() == SettlementAreaType.BREWERY && area.contains(stand.getBlockPos()) && SettlementBrewingStandState.supported(stand);
    }

    private static void observe(ServerLevel level, SettlementRecord town, SettlementArea area, BrewingStandBlockEntity stand, long now) {
        var state = town.brewingStands().get(stand.getBlockPos());
        if (state == null || !state.pending() && (!state.matches(stand) || !state.area().equals(area.id()))) {
            town.recordBrewingStand(SettlementBrewingStandState.capture(level, area.id(), stand, now));
        } else if (state.reconcile(level.registryAccess(), stand)) {
            state.observe(level.registryAccess(), stand, now);
            town.changed();
        }
    }

    private static BlockState bottles(ServerLevel level, SettlementBrewingStandState state, BlockState block) {
        if (!(block.getBlock() instanceof BrewingStandBlock)) {
            return block;
        }
        var items = state.items(level.registryAccess());
        for (int slot = 0; slot < 3; slot++) {
            block = block.setValue(BrewingStandBlock.HAS_BOTTLE[slot], !items.get(slot).isEmpty());
        }
        return block;
    }
}
