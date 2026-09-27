package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.interaction.work.builder.BuilderSitePlanner;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.interaction.work.mining.MiningBlockRules;
import com.jvn.villagerretaliation.item.ConstructionBlueprintItem.PreviewBlock;
import com.jvn.villagerretaliation.item.ConstructionBlueprintItem.PreviewData;
import com.jvn.villagerretaliation.settlement.SettlementViewWindow;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

final class SettlementStructurePlacement {
    private final PreviewData source;
    private BlockPos anchor;
    private Rotation rotation = Rotation.NONE;
    private PreviewData preview;
    private List<BlockPos> conflicts = List.of();

    SettlementStructurePlacement(PreviewData source) {
        this.source = source;
        this.anchor = new BlockPos(source.worldCenter().getX(), source.worldMin().getY(), source.worldCenter().getZ());
        rebuild();
    }

    BlockPos anchor() {
        return this.anchor;
    }

    Rotation rotation() {
        return this.rotation;
    }

    PreviewData preview() {
        return this.preview;
    }

    List<BlockPos> conflicts() {
        return this.conflicts;
    }

    boolean move(BlockPos anchor) {
        if (this.anchor.equals(anchor)) return false;
        this.anchor = anchor.immutable();
        rebuild();
        return true;
    }

    void rotate(boolean reverse) {
        this.rotation = this.rotation.getRotated(reverse ? Rotation.COUNTERCLOCKWISE_90 : Rotation.CLOCKWISE_90);
        rebuild();
    }

    private void rebuild() {
        var blocks = this.source.blocks().stream().map(block -> new PreviewBlock(
                StructureTemplate.transform(block.localPos(), Mirror.NONE, this.rotation, BlockPos.ZERO),
                block.state().rotate(this.rotation))).toList();
        var min = new BlockPos(blocks.stream().mapToInt(block -> block.localPos().getX()).min().orElse(0),
                blocks.stream().mapToInt(block -> block.localPos().getY()).min().orElse(0),
                blocks.stream().mapToInt(block -> block.localPos().getZ()).min().orElse(0));
        var max = new BlockPos(blocks.stream().mapToInt(block -> block.localPos().getX()).max().orElse(0),
                blocks.stream().mapToInt(block -> block.localPos().getY()).max().orElse(0),
                blocks.stream().mapToInt(block -> block.localPos().getZ()).max().orElse(0));
        var origin = this.anchor.offset(-Math.floorDiv(min.getX() + max.getX(), 2), -min.getY(),
                -Math.floorDiv(min.getZ() + max.getZ(), 2));
        this.preview = new PreviewData(this.source.jobId(), null, this.source.dimension(), this.source.structureId(),
                this.source.structureLabel(), origin, this.rotation, min, max, blocks.size(), 0, 0, this.source.materialSummary(),
                "", "", false, false, false, false, blocks);
    }

    String validate(ClientLevel level, SettlementViewWindow window) {
        var min = this.preview.worldMin();
        var max = this.preview.worldMax();
        this.conflicts = List.of();
        if (min.getX() < window.center().getX() - window.radius() || max.getX() > window.center().getX() + window.radius()
                || min.getZ() < window.center().getZ() - window.radius() || max.getZ() > window.center().getZ() + window.radius())
            return "Move the entire footprint inside the village bounds.";
        if (level.isOutsideBuildHeight(min) || level.isOutsideBuildHeight(max)
                || !level.getWorldBorder().isWithinBounds(min) || !level.getWorldBorder().isWithinBounds(max))
            return "Move the building inside the world bounds.";
        var blocked = new ArrayList<BlockPos>();
        for (var block : this.preview.blocks()) {
            var pos = block.worldPos(this.preview.origin());
            if (level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, false) == null) return "Wait for the site chunks to load.";
            var current = level.getBlockState(pos);
            if (!BuilderStructureScanner.sameSchematicState(current, block.state())
                    && !BuilderStructureScanner.canTransformExisting(current, block.state())
                    && !BuilderSitePlanner.safeReplaceable(level, pos, current)
                    && !MiningBlockRules.isBuilderClearableObstruction(level, pos, current)) blocked.add(pos);
        }
        this.conflicts = List.copyOf(blocked);
        return blocked.isEmpty() ? "" : "Clear the highlighted blocks or choose another site.";
    }
}
