package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.sell.CurrencyAmount;
import java.util.*;
import net.minecraft.server.level.ServerLevel;

/** Repairs only the village's own completed authored structures, through normal full-cost escrow. */
final class AutonomousVillageMaintenance {
    private AutonomousVillageMaintenance() {}
    static boolean plan(ServerLevel level,SettlementRecord town,AutonomousVillageRules.Rules rules,long now) {
        var state=town.autonomous();if(state.structures.isEmpty()) return false;
        var structures=List.copyOf(state.structures.entrySet());
        var entry=structures.stream().filter(row->state.need.equals("repair:"+row.getKey())).findFirst()
                .orElseGet(()->structures.get(Math.floorMod(state.maintenanceCursor++,structures.size())));
        var original=town.project(entry.getKey());if(original==null || original.status()!=SettlementProject.Status.COMPLETED) return false;
        var plan=SettlementConstructionService.scan(level,original.structure(),original.rotation());
        if(plan==null || plan.blocks().size()>4096 || !original.matchesPlan(plan)) return false;
        int missing=0;
        var actor=SettlementStorageService.actor(level,town);
        for(var block:plan.blocks()) {
            var pos=plan.worldPos(original.origin(),block);
            if(!town.contains(pos) || !level.hasChunkAt(pos) || !level.mayInteract(actor,pos)) return false;
            var current=level.getBlockState(pos);
            if(BuilderStructureScanner.sameSchematicState(current,block.state())) continue;
            // Never remove a player's alteration or belongings to restore an old blueprint.
            if(!current.isAir() || level.getBlockEntity(pos)!=null) return false;
            missing++;
        }
        if(missing==0) return false;
        String need="repair:"+original.id();
        if(!state.need.equals(need)) {state.need=need;state.observations=0;}
        state.observations=Math.min(rules.sustained(),state.observations+1);
        state.status="Repair needed: "+original.label()+" ("+missing+" missing blocks)";
        state.nextAction="Reserve full authored repair quote; preserve existing matching blocks and contents";
        if(state.observations<rules.sustained() || town.economy().constructionFunds().compareTo(CurrencyAmount.of(plan.price(),1))<0) return true;
        var id=UUID.randomUUID();var area=SettlementConstructionService.workplace(level,town,plan,original.origin(),id);
        if(area==null || !town.owns(area)) return true;
        var repair=SettlementProject.planned(town,area,SettlementProject.AUTOMATIC_BUILDER,plan,original.origin(),id,now);
        repair.repairOf(original.id());repair.queue(town.leader());
        if(town.queueProject(town.leader(),repair)) {
            state.projects.put(id,entry.getValue());state.observations=0;
            AutonomousVillageService.assignBuilder(level,town,area);
            SettlementConstructionService.tickQueue(level,town);town.changed();
        }
        return true;
    }
    static boolean safe(ServerLevel level,SettlementRecord town,SettlementProject project) {
        var source=town.project(project.repairOf());
        if(source==null || source.status()!=SettlementProject.Status.COMPLETED || !source.min().equals(project.min())
                || !source.max().equals(project.max()) || !source.origin().equals(project.origin()) || !source.structure().equals(project.structure())
                || source.rotation()!=project.rotation()) return false;
        var plan=SettlementConstructionService.scan(level,project.structure(),project.rotation());
        if(plan==null || !source.matchesPlan(plan) || !project.matchesPlan(plan)) return false;
        var actor=SettlementStorageService.actor(level,town);
        for(var block:plan.blocks()) {
            var pos=plan.worldPos(project.origin(),block);
            if(!town.contains(pos) || !level.hasChunkAt(pos) || !level.mayInteract(actor,pos)) return false;
            var current=level.getBlockState(pos);
            if(!BuilderStructureScanner.sameSchematicState(current,block.state()) && (!current.isAir() || level.getBlockEntity(pos)!=null)) return false;
        }
        return true;
    }
}
