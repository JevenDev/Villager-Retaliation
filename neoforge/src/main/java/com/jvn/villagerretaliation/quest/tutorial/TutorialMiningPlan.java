package com.jvn.villagerretaliation.quest.tutorial;

import com.jvn.villagerretaliation.interaction.HiredMiningMode;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.Tags;

public record TutorialMiningPlan(TutorialRun.Scope scope, String blocker) {
    public static TutorialMiningPlan inspect(ServerLevel level, Villager worker) {
        var session = HiredWorkSession.create(level, worker, HiredVillagerRole.MINING);
        if (HiredMiningMode.fromState(session.state()) != HiredMiningMode.EXPOSED_ORES)
            return blocked("Set Mining Mode to Exposed Ores. A temporary tunnel or quarry command cannot run under this free lesson.");
        var area = session.area();
        if (!area.explicitlyAssigned()) return blocked("Apply the small Edit Job Site box to this Miner first.");
        long volume = (long) (area.max().getX() - area.min().getX() + 1) * (area.max().getY() - area.min().getY() + 1)
                * (area.max().getZ() - area.min().getZ() + 1);
        if (volume > 512) return blocked("Resize the lesson box to a small exposed deposit of at most three ore blocks and safe standing room.");
        var targets = new ArrayList<BlockPos>();
        for (var raw : BlockPos.betweenClosed(area.min(), area.max())) {
            var pos = raw.immutable();
            if (!level.hasChunkAt(pos)) return blocked("Load the whole marked deposit first.");
            if (!level.getBlockState(pos).is(Tags.Blocks.ORES)) continue;
            boolean exposed = false;
            for (Direction side : Direction.values()) if (level.hasChunkAt(pos.relative(side)) && level.getBlockState(pos.relative(side)).isAir()) exposed = true;
            if (exposed) targets.add(pos);
        }
        if (targets.isEmpty() || targets.size() > 3) return blocked("The box must identify one to three exposed ore blocks. Protect unrelated structures and leave a safe approach.");
        java.util.function.Predicate<ItemStack> pick = stack -> !stack.isEmpty() && stack.is(ItemTags.PICKAXES)
                && (!stack.isDamageableItem() || stack.getMaxDamage() - stack.getDamageValue() >= targets.size())
                && targets.stream().allMatch(pos -> stack.isCorrectToolForDrops(level.getBlockState(pos)));
        if (session.inventory().findTool(pick).isEmpty() && AssignedStorageService.countItems(worker, pick) == 0)
            return blocked("Put a usable pickaxe of the correct tier for every approved ore in accessible Supplies or the Miner's tool slot.");
        if (!session.context().useAssignedStorageForSupplies() || !session.context().autoDepositOutputs())
            return blocked("Inspect Work Settings and enable assigned supplies and automatic output delivery.");
        var outputs = AssignedStorageService.assignedStorage(level, worker).stream().filter(r -> r.purpose().equals("output")
                && r.dimension().equals(level.dimension())).toList();
        if (outputs.size() != 1 || !AssignedStorageService.hasLiveAssignedStorageForPurpose(level, worker, "supply")
                || !AssignedStorageService.hasLiveAssignedStorageForPurpose(level, worker, "output"))
            return blocked("Commit one accessible Supplies link and one Output link through Assign Clipboard Storage.");
        var output = AssignedStorageService.canonicalContainerPosition(level, outputs.getFirst().pos());
        if (output == null) return blocked("The Output container is unavailable.");
        var allowed = new LinkedHashSet<String>(); targets.forEach(pos -> allowed.add(TutorialWorkService.position(level, pos)));
        String destination = TutorialWorkService.position(level, output);
        return new TutorialMiningPlan(new TutorialRun.Scope(Map.of("mine", allowed, "deposit", Set.of(destination)),
                Map.of("mine", targets.size(), "deposit", 65536), Map.of("mine", targets.size()), destination, true), "");
    }
    private static TutorialMiningPlan blocked(String reason) { return new TutorialMiningPlan(null, reason); }
}
