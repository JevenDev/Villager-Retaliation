package com.jvn.villagerretaliation.quest.tutorial;

import com.jvn.villagerretaliation.interaction.*;
import com.jvn.villagerretaliation.interaction.work.*;
import com.jvn.villagerretaliation.interaction.work.logging.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

public record TutorialLoggingPlan(TutorialRun.Scope scope, String blocker) {
    public static boolean settings(net.minecraft.nbt.CompoundTag state) {
        return HiredLoggingOptions.plantSaplings(state) && HiredLoggingOptions.pickUpDecayDrops(state)
                && !HiredLoggingOptions.stripLogs(state) && !HiredLoggingOptions.harvestLeaves(state) && !HiredLoggingOptions.bonemealSaplings(state);
    }
    public static TutorialLoggingPlan inspect(ServerLevel level, Villager worker) {
        var session = HiredWorkSession.create(level, worker, HiredVillagerRole.LOGGING);
        var area = session.area();
        if (!area.explicitlyAssigned() || (long)(area.max().getX()-area.min().getX()+1)*(area.max().getY()-area.min().getY()+1)*(area.max().getZ()-area.min().getZ()+1)>4096)
            return blocked("Apply a tight three-dimensional box around one small natural tree, its canopy, soil and safe approach.");
        if (!settings(session.state())) return blocked("Turn Plant saplings and Pick up decay drops ON; Strip logs, Harvest leaves and Bonemeal saplings OFF.");
        var queue = HiredTemporaryWorkService.queue(session.state());
        if (queue != null && (queue.tasks().size()!=1 || queue.current().kind()!=HiredTemporaryTask.Kind.TREE)) return blocked("Cancel unrelated forest tasks; this lesson covers one selected tree.");
        List<BlockPos> logs = List.of();
        if (queue != null) logs = LoggingTreeGeometry.selectedTreeLogs(level, queue.current().target(), HiredLoggingFilters.selectedFilterIds(session.state()));
        else {
            for (var raw : session.context().workAreaPositions()) if (level.hasChunkAt(raw) && level.getBlockState(raw).is(BlockTags.LOGS)) {
                var found = LoggingTreeGeometry.selectedTreeLogs(level, raw, HiredLoggingFilters.selectedFilterIds(session.state()));
                if (found.isEmpty()) continue;
                if (!logs.isEmpty() && !new HashSet<>(logs).equals(new HashSet<>(found))) return blocked("Keep only the chosen small tree inside this lesson's box.");
                logs = found;
            }
        }
        if (logs.isEmpty() || logs.size()>8 || !logs.stream().allMatch(area::contains)) return blocked("Choose a complete recognizable natural tree of one to eight logs, wholly inside the box.");
        var root = LoggingTreeGeometry.treeRoot(logs);
        final int minY = root.getY();
        if (logs.stream().filter(p -> p.getY()==minY && LoggingTreeGeometry.isNaturalTreeBase(level.getBlockState(p.below()))).count()!=1)
            return blocked("Choose a tree with exactly one replacement sapling location; large multi-stem trees are outside this lesson.");
        var sapling = LoggingTreeGeometry.saplingForTree(level, level::hasChunkAt, logs);
        if (sapling.isEmpty() || HiredSupplyCrafting.countAvailable(worker, session.context(), s->s.is(sapling.getItem()))<1)
            return blocked("Put one matching replacement sapling in accessible Supplies. It must survive on the chosen root soil.");
        int cuts=logs.size();
        if (session.inventory().findTool(s->s.is(ItemTags.AXES)&&(!s.isDamageableItem()||s.getMaxDamage()-s.getDamageValue()>=cuts)).isEmpty()
                && com.jvn.villagerretaliation.inventory.AssignedStorageService.countItems(worker,s->s.is(ItemTags.AXES)&&(!s.isDamageableItem()||s.getMaxDamage()-s.getDamageValue()>=cuts))==0)
            return blocked("Supply a usable axe with enough durability for the approved tree.");
        String output=TutorialPlanChecks.output(level,worker);
        if(output==null)return blocked(TutorialPlanChecks.STORAGE_HELP);
        if(!session.context().autoDepositOutputs()||!session.context().useAssignedStorageForSupplies())return blocked("Enable assigned supplies and automatic output delivery.");
        var logTargets=new LinkedHashSet<String>();logs.forEach(p->logTargets.add(TutorialWorkService.position(level,p)));
        var leafTargets=new LinkedHashSet<String>();LoggingTreeGeometry.naturalTreeLeaves(level,level::hasChunkAt,logs).stream().filter(area::contains).forEach(p->leafTargets.add(TutorialWorkService.position(level,p)));
        if(leafTargets.isEmpty()||leafTargets.size()>128)return blocked("Choose a small natural canopy of at most 128 leaves.");
        return new TutorialLoggingPlan(new TutorialRun.Scope(Map.of("log",logTargets,"leaf",leafTargets,"collect",leafTargets,
                "replant",Set.of(TutorialWorkService.position(level,root)),"deposit",Set.of(output)),
                Map.of("log",logs.size(),"leaf",leafTargets.size(),"collect",64,"replant",1,"deposit",65536),Map.of("log",logs.size(),"replant",1),output,true),"");
    }
    private static TutorialLoggingPlan blocked(String reason){return new TutorialLoggingPlan(null,reason);}
}
