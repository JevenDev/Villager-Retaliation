package com.jvn.villagerretaliation.quest.tutorial;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.interaction.work.FarmerHoeRequirement;
import com.jvn.villagerretaliation.interaction.work.HiredFarmingOptions;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;

/** Captures the actual two-square teaching fixture, never a fallback farming radius. */
public record TutorialFarmingPlan(TutorialRun.Scope scope, String blocker) {
    public static TutorialFarmingPlan inspect(ServerLevel level, Villager worker) {
        var session = HiredWorkSession.create(level, worker, HiredVillagerRole.FARMING);
        var area = session.area();
        if (!area.explicitlyAssigned()) return blocked("Apply an Edit Job Site box to your Farmer first; the preview is not assignment.");
        long volume = (long) (area.max().getX() - area.min().getX() + 1) * (area.max().getY() - area.min().getY() + 1)
                * (area.max().getZ() - area.min().getZ() + 1);
        if (volume > 512) return blocked("Use a small box around one bare planting square and one ripe wheat crop, including safe standing room.");
        var soils = new ArrayList<BlockPos>(); var crops = new ArrayList<BlockPos>();
        for (var raw : BlockPos.betweenClosed(area.min(), area.max())) {
            BlockPos pos = raw.immutable();
            if (!level.hasChunkAt(pos) || !level.hasChunkAt(pos.above())) return blocked("Load the entire small field before starting.");
            var state = level.getBlockState(pos);
            if ((state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK)) && level.getBlockState(pos.above()).isAir()) soils.add(pos);
            if (state.is(Blocks.WHEAT) && ((CropBlock) state.getBlock()).isMaxAge(state)) crops.add(pos);
        }
        if (soils.size() != 1 || crops.size() != 1)
            return blocked("The lesson needs exactly one bare dirt/grass square with air above it and one separate ripe wheat crop in the box. Resize the box to identify them.");
        if (!AssignedStorageService.hasLiveAssignedStorageForPurpose(level, worker, "supply")
                || !AssignedStorageService.hasLiveAssignedStorageForPurpose(level, worker, "output"))
            return blocked("Assign usable Supplies and Output chests to this Farmer with Assign Clipboard Storage.");
        var outputs = AssignedStorageService.assignedStorage(level, worker).stream().filter(r -> r.purpose().equals("output")
                && r.dimension().equals(level.dimension())).toList();
        if (outputs.size() != 1) return blocked("Choose one Output chest for this small lesson so its delivery has an unambiguous destination.");
        var output = AssignedStorageService.canonicalContainerPosition(level, outputs.getFirst().pos());
        if (output == null) return blocked("The assigned Output container is unavailable.");
        int seeds = session.inventory().supplySlots().stream().map(session.inventory()::getItem)
                .filter(stack -> stack.is(Items.WHEAT_SEEDS)).mapToInt(net.minecraft.world.item.ItemStack::getCount).sum()
                + AssignedStorageService.countItems(worker, stack -> stack.is(Items.WHEAT_SEEDS));
        boolean hasHoe = !session.inventory().findTool(FarmerHoeRequirement::isHoe).isEmpty()
                || AssignedStorageService.countItems(worker, FarmerHoeRequirement::isHoe) > 0;
        if (seeds < 2 || !hasHoe)
            return blocked("Accessible wheat seeds: " + seeds + "/2. Usable hoe: " + (hasHoe ? "ready" : "missing")
                    + ". Load the assigned Supplies chest with an empty hand, or place these inputs in this Farmer's supply/tool slots.");
        if (HiredFarmingOptions.crop(session.state()) != HiredFarmingOptions.Crop.WHEAT
                || !HiredFarmingOptions.tillSoil(session.state()) || !HiredFarmingOptions.plantCrops(session.state())
                || !HiredFarmingOptions.harvestCrops(session.state()) || !session.context().useAssignedStorageForSupplies()
                || !session.context().autoDepositOutputs())
            return blocked("Inspect Job > Work Settings: choose Wheat; enable Till, Plant and Harvest, assigned supplies and automatic output delivery.");
        String soil = TutorialWorkService.position(level, soils.getFirst());
        String plant = TutorialWorkService.position(level, soils.getFirst().above());
        String crop = TutorialWorkService.position(level, crops.getFirst());
        String destination = TutorialWorkService.position(level, output);
        return new TutorialFarmingPlan(new TutorialRun.Scope(Map.of("till", Set.of(soil), "plant", Set.of(plant),
                "harvest", Set.of(crop), "replant", Set.of(crop), "deposit", Set.of(destination)),
                Map.of("till", 1, "plant", 1, "harvest", 1, "replant", 1, "deposit", 65536),
                Map.of("till", 1, "plant", 1, "harvest", 1, "replant", 1, "deposit", 1), destination), "");
    }
    public static BlockPos target(TutorialRun run, String action) {
        if (run == null || run.scope() == null) return null;
        var targets = run.scope().targets().getOrDefault(action, Set.of());
        if (targets.size() != 1) return null;
        String target = targets.iterator().next(); String[] xyz = target.substring(target.lastIndexOf('/') + 1).split(",");
        try { return new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2])); }
        catch (RuntimeException ex) { return null; }
    }
    public static TutorialFarmingPlan revalidate(ServerLevel level, Villager worker, TutorialRun run) {
        var session = HiredWorkSession.create(level, worker, HiredVillagerRole.FARMING);
        for (String action : java.util.List.of("till", "plant", "harvest", "replant")) {
            var target = target(run, action);
            if (target == null || !level.hasChunkAt(target) || !session.context().isInsideWorkArea(target)
                    && !session.context().isInsideWorkArea(target.below())) return blocked("Restore the original small field box and load its bound target squares.");
        }
        if (!AssignedStorageService.hasLiveAssignedStorageForPurpose(level, worker, "supply")
                || !AssignedStorageService.hasLiveAssignedStorageForPurpose(level, worker, "output"))
            return blocked("Restore the bound worker's Supplies and original Output links, then inspect its tools and settings.");
        return new TutorialFarmingPlan(run.scope(), "");
    }
    private static TutorialFarmingPlan blocked(String reason) { return new TutorialFarmingPlan(null, reason); }
}
