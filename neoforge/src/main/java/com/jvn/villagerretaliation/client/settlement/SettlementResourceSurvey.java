package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask;
import com.jvn.villagerretaliation.interaction.work.logging.LoggingTreeGeometry;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;

final class SettlementResourceSurvey {
    private final HiredTemporaryTask task;
    private final LoggingTreeGeometry.DiscoveryCache trees;
    private final List<BlockPos> highlights = new ArrayList<>();
    private final Set<BlockPos> roots = new java.util.HashSet<>();
    private int cursor;
    private int ores;
    private boolean waiting;

    SettlementResourceSurvey(ClientLevel level, HiredTemporaryTask task) {
        this.task = task;
        this.trees = LoggingTreeGeometry.discovery(level, level::hasChunkAt, Set.of());
    }

    void tick(ClientLevel level) {
        if (!this.task.kind().forest() && this.task.kind() != HiredTemporaryTask.Kind.ORE_AREA) return;
        if (this.cursor >= volume()) return;
        this.waiting = false;
        for (int x = this.task.min().getX() >> 4; x <= this.task.max().getX() >> 4; x++) {
            for (int z = this.task.min().getZ() >> 4; z <= this.task.max().getZ() >> 4; z++) {
                if (!level.hasChunk(x, z)) {
                    this.waiting = true;
                    return;
                }
            }
        }
        int xSize = this.task.max().getX() - this.task.min().getX() + 1;
        int zSize = this.task.max().getZ() - this.task.min().getZ() + 1;
        int end = Math.min(volume(), this.cursor + 768);
        var logs = new ArrayList<BlockPos>();
        for (; this.cursor < end; this.cursor++) {
            var position = this.task.min().offset(this.cursor % xSize, this.cursor / (xSize * zSize), this.cursor / xSize % zSize);
            if (!level.hasChunkAt(position)) continue;
            var state = level.getBlockState(position);
            if (this.task.kind().forest()) {
                if (state.is(BlockTags.LOGS) && this.trees.isNaturalTree(position)) {
                    logs.add(position);
                    if (this.highlights.size() < 1024) this.highlights.add(position);
                }
            } else if (OreNodeCatalogClient.isOre(state) && !state.hasBlockEntity() && state.getDestroySpeed(level, position) >= 0
                    && this.task.matches(state)
                    && com.jvn.villagerretaliation.interaction.work.mining.MiningBlockRules.isExposed(level, level::hasChunkAt, position)) {
                this.ores++;
                if (this.highlights.size() < 1024) this.highlights.add(position);
            }
        }
        this.roots.addAll(this.trees.distinctRoots(logs));
    }

    private int volume() {
        return (this.task.max().getX() - this.task.min().getX() + 1) * (this.task.max().getY() - this.task.min().getY() + 1)
                * (this.task.max().getZ() - this.task.min().getZ() + 1);
    }

    List<BlockPos> highlights() { return this.highlights; }

    String summary() {
        if (!this.task.kind().forest() && this.task.kind() != HiredTemporaryTask.Kind.ORE_AREA) return "Preview shown in the world";
        return (this.waiting ? "Waiting for terrain: " : this.cursor < volume() ? "Surveying: " : "Survey: ") + (this.task.kind().forest() ? this.roots.size() + " trees" : this.ores + " exposed ores");
    }
}
