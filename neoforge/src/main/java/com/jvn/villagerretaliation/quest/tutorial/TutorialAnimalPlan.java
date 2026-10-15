package com.jvn.villagerretaliation.quest.tutorial;

import com.jvn.villagerretaliation.interaction.*;
import com.jvn.villagerretaliation.interaction.work.*;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceLocation;

public record TutorialAnimalPlan(TutorialRun.Scope scope,String blocker) {
    public static String pair(java.util.UUID a,java.util.UUID b){return a.compareTo(b)<0?a+"/"+b:b+"/"+a;}
    public static boolean settings(net.minecraft.nbt.CompoundTag state){return HiredAnimalHandlingOptions.shearSheep(state)&&HiredAnimalHandlingOptions.breed(state)
            &&!HiredAnimalHandlingOptions.milk(state)&&!HiredAnimalHandlingOptions.collect(state)&&!HiredAnimalHandlingOptions.cull(state)
            &&HiredAnimalBreedingTargets.selectedTargetIds(state).equals(Set.of(ResourceLocation.parse("minecraft:sheep")));}
    public static TutorialAnimalPlan inspect(ServerLevel level,Villager worker){
        var session=HiredWorkSession.create(level,worker,HiredVillagerRole.ANIMAL_HANDLING);
        if(!session.area().explicitlyAssigned()||!settings(session.state()))return blocked("Apply the sheep pen; select only Sheep. Turn Shearing and Breeding ON, Milking, Collection and Culling OFF.");
        var animals=level.getEntitiesOfClass(Sheep.class,new net.minecraft.world.phys.AABB(net.minecraft.world.phys.Vec3.atLowerCornerOf(session.area().min()),net.minecraft.world.phys.Vec3.atLowerCornerOf(session.area().max().offset(1,1,1))),s->s.isAlive()&&!s.isBaby()&&s.getAge()==0&&!s.isInLove()&&session.area().contains(s.blockPosition()));
        if(animals.size()!=2)return blocked("Use a safe accessible pen with exactly two eligible adult sheep, ready to breed.");
        var queue=HiredTemporaryWorkService.queue(session.state());
        if(queue==null||queue.tasks().size()!=1||queue.current().kind()!=HiredTemporaryTask.Kind.SHEAR
                ||animals.stream().noneMatch(s->s.getUUID().equals(queue.current().entity())&&s.readyForShearing()))return blocked("Hold the Clipboard, hold Sneak and Use the ready sheep, then Draft > Task > Shear selected sheep > Apply.");
        if(HiredSupplyCrafting.countAvailable(worker,session.context(),Items.WHEAT)<2||HiredSupplyCrafting.countAvailable(worker,session.context(),s->s.is(Items.SHEARS)&&(!s.isDamageableItem()||s.getDamageValue()<s.getMaxDamage()))<1
                &&session.inventory().findTool(s->s.is(Items.SHEARS)).isEmpty())return blocked("Stock usable shears and two wheat in accessible Supplies.");
        String output=TutorialPlanChecks.output(level,worker);if(output==null)return blocked(TutorialPlanChecks.STORAGE_HELP);
        if(!session.context().autoDepositOutputs()||!session.context().useAssignedStorageForSupplies())return blocked("Enable assigned supplies and automatic output delivery.");
        return new TutorialAnimalPlan(new TutorialRun.Scope(Map.of("shear",Set.of(queue.current().entity().toString()),"breed",Set.of(pair(animals.get(0).getUUID(),animals.get(1).getUUID())),
                "parents",Set.of(animals.get(0).getUUID().toString(),animals.get(1).getUUID().toString()),"deposit",Set.of(output)),
                Map.of("shear",1,"breed",1,"deposit",65536),Map.of("shear",1,"breed",1),output,true),"");
    }
    public static boolean allows(Villager worker,String action,String target){
        var run=TutorialWorkService.run(worker).orElse(null);
        return run!=null&&run.scope()!=null&&run.scope().targets().getOrDefault(action,Set.of()).contains(target)
                &&run.committedUnits(action)<run.scope().limits().getOrDefault(action,0)
                &&run.snapshot().pending().values().stream().noneMatch(p->p.action().equals(action));
    }
    private static TutorialAnimalPlan blocked(String reason){return new TutorialAnimalPlan(null,reason);}
}
