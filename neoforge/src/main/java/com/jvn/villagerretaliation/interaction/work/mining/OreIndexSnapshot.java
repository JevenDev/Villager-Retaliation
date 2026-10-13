package com.jvn.villagerretaliation.interaction.work.mining;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.PalettedContainer;

// copied palettes and immutable ore membership are the only inputs used by the worker thread
record OreIndexSnapshot(int x, int z, List<Section> sections, Set<BlockState> ores) {
    static OreIndexSnapshot capture(LevelChunk chunk, Set<BlockState> ores) {
        var sections = new ArrayList<Section>();
        for (int i = 0; i < chunk.getSectionsCount(); i++) {
            var states = chunk.getSection(i).getStates();
            if (states.maybeHas(ores::contains)) sections.add(new Section(chunk.getSectionYFromSectionIndex(i) << 4, states.copy()));
        }
        return new OreIndexSnapshot(chunk.getPos().getMinBlockX(), chunk.getPos().getMinBlockZ(), List.copyOf(sections), ores);
    }

    LongSet scan() {
        var positions = new LongOpenHashSet();
        for (var section : sections) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("Ore indexing interrupted");
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (ores.contains(section.states.get(x, y, z))) positions.add(BlockPos.asLong(this.x + x, section.y + y, this.z + z));
                    }
                }
            }
        }
        return positions;
    }

    record Section(int y, PalettedContainer<BlockState> states) { }
}
