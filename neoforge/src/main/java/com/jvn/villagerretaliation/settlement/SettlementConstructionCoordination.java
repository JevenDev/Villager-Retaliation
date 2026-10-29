package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.builder.BuilderSitePlanner;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderWorker;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;

final class SettlementConstructionCoordination {
    private SettlementConstructionCoordination() {}

    static void refresh(SettlementRecord town, SettlementProject project, BuilderStructureScanner.StructurePlan plan, LevelReader view) {
        if (project.refreshProgress(index -> {
            var block = plan.blocks().get(index);
            var position = plan.worldPos(project.origin(), block);
            return view.hasChunkAt(position) ? BuilderStructureScanner.sameSchematicState(view.getBlockState(position), block.state())
                    : project.crew().completed(index);
        })) town.changed();
    }

    static int select(SettlementRecord town, SettlementProject project, UUID worker, BuilderStructureScanner.StructurePlan plan,
                      LevelReader view, long now) {
        return select(town, project, worker, plan, view, now, null);
    }

    static int select(SettlementRecord town, SettlementProject project, UUID worker, BuilderStructureScanner.StructurePlan plan,
                      LevelReader view, long now, com.jvn.villagerretaliation.inventory.HiredJobInventory inventory) {
        var crew = project.crew();
        refresh(town, project, plan, view);
        if (project.placed() == project.total()) {
            crew.release(worker);
            return project.total();
        }
        var claim = crew.claim(worker, now);
        if (claim != null && claim.index() >= 0 && !crew.completed(claim.index())
                && acquire(project, worker, plan, view, claim.index(), now)) return claim.index();
        crew.release(worker);
        for (int pass = 0; pass < (inventory == null ? 1 : 2); pass++) {
            for (int index = 0; index < plan.blocks().size(); index++) {
                if (crew.completed(index)) continue;
                if (inventory != null && pass == 0) {
                    var group = BuilderWorker.placementGroup(plan, project.origin(), plan.blocks().get(index));
                    var material = group.materialBlock();
                    var position = group.materialPart().worldPos();
                    if (!view.hasChunkAt(position)) continue;
                    var current = view.getBlockState(position);
                    if (material.requiresMaterial() && !BuilderStructureScanner.sameSchematicState(current, material.state())
                            && !BuilderStructureScanner.canTransformExisting(current, material.state())
                            && inventory.findSupply(material::materialMatches).isEmpty() && !inventory.hasOutput(material::materialMatches)) continue;
                }
                if (acquire(project, worker, plan, view, index, now)) return index;
            }
        }
        return -1;
    }

    private static boolean acquire(SettlementProject project, UUID worker, BuilderStructureScanner.StructurePlan plan,
                                   LevelReader view, int index, long now) {
        if (project.wallConstruction() != null && !project.wallConstruction().available(index, project.crew())) return false;
        var group = BuilderWorker.placementGroup(plan, project.origin(), plan.blocks().get(index));
        for (var part : group.parts()) {
            if (!view.hasChunkAt(part.worldPos())) return false;
            for (var direction : Direction.values()) if (!view.hasChunkAt(part.worldPos().relative(direction))) return false;
            var current = view.getBlockState(part.worldPos());
            if (BuilderStructureScanner.sameSchematicState(current, part.block().state())
                    || BuilderStructureScanner.canTransformExisting(current, part.block().state())) continue;
            if (!BuilderSitePlanner.safeReplaceable(view, part.worldPos(), current)
                    || part == group.materialPart() && !part.block().state().canSurvive(view, part.worldPos())) return false;
        }
        return project.crew().acquire(worker, index, positions(group), now);
    }

    static List<BlockPos> positions(BuilderWorker.PlacementGroup group) {
        return group.parts().stream().map(BuilderWorker.PlacementPart::worldPos).toList();
    }
}
