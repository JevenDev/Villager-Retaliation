package com.jvn.villagerretaliation.quest.tutorial;

import com.jvn.villagerretaliation.interaction.*;
import com.jvn.villagerretaliation.interaction.work.*;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

public record TutorialCourierPlan(TutorialRun.Scope scope,String blocker){
    public static TutorialCourierPlan inspect(ServerLevel level,Villager worker){
        var session=HiredWorkSession.create(level,worker,HiredVillagerRole.COURIER);
        if(com.jvn.villagerretaliation.interaction.work.courier.HiredTransferService.access(level,worker).configured())return blocked("Finish conflicting transfer or civic hauling assignments first.");
        String output=TutorialPlanChecks.output(level,worker);if(output==null)return blocked(TutorialPlanChecks.STORAGE_HELP);
        var sources=AssignedStorageService.assignedStorage(level,worker).stream().filter(s->s.dimension().equals(level.dimension())&&s.purpose().equals("supply")).toList();
        if(sources.size()!=1)return blocked("Assign exactly one pickup chest A in Supplies mode.");
        var source=AssignedStorageService.canonicalContainerPosition(level,sources.getFirst().pos());
        if(source==null||TutorialWorkService.position(level,source).equals(output))return blocked("Pickup A and Output B must be different container identities, including both halves of a double chest.");
        var route=session.context().route();
        if(route==null||route.loop()||route.nodes().size()<2||!route.branches().isEmpty()||route.first().distSqr(source)>256
                ||route.last().distSqr(TutorialFarmingPlanPosition.parse(output))>256)return blocked("Apply a simple non-looping route from near A to near B, within sixteen blocks of each container.");
        for(var node:route.nodes()){
            var path=worker.getNavigation().createPath(node,0);
            if(!level.hasChunkAt(node)||path==null||!path.canReach())return blocked("Clear a walkable route to each node before dispatch.");
        }
        if(!session.context().autoDepositOutputs()||!session.context().useAssignedStorageForSupplies())return blocked("Enable assigned supplies and automatic output delivery.");
        return new TutorialCourierPlan(new TutorialRun.Scope(Map.of("pickup",Set.of(TutorialWorkService.position(level,source)),"route",Set.of(route.toString()),"deposit",Set.of(output)),
                Map.of("pickup",4,"deposit",4),Map.of("pickup",4,"deposit",4),output,true),"");
    }
    public static boolean matches(Villager worker,HiredWorkContext context){
        var run=TutorialWorkService.run(worker).orElse(null);
        return run!=null&&run.scope()!=null&&context.route()!=null&&run.scope().targets().getOrDefault("route",Set.of()).contains(context.route().toString());
    }
    private static TutorialCourierPlan blocked(String reason){return new TutorialCourierPlan(null,reason);}
}
