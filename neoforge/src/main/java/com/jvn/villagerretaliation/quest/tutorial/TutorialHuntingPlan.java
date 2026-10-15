package com.jvn.villagerretaliation.quest.tutorial;

import com.jvn.villagerretaliation.interaction.*;
import com.jvn.villagerretaliation.interaction.work.*;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.tags.ItemTags;

public record TutorialHuntingPlan(TutorialRun.Scope scope,String blocker){
    public static boolean settings(net.minecraft.nbt.CompoundTag state){var targets=HiredHuntingTargets.fromState(state);return targets.animals()&&!targets.hostiles()&&!targets.players();}
    public static TutorialHuntingPlan inspect(ServerLevel level,Villager worker){
        var session=HiredWorkSession.create(level,worker,HiredVillagerRole.HUNTING);
        if(!session.area().explicitlyAssigned()||!settings(session.state()))return blocked("Apply the small hunting area. Set Hunt animals ON; Hunt hostiles and Hunt players OFF.");
        var queue=HiredTemporaryWorkService.queue(session.state());
        if(queue==null||queue.tasks().size()!=1||queue.current().kind()!=HiredTemporaryTask.Kind.HUNT
                ||!(level.getEntity(queue.current().entity()) instanceof Animal target)||target.hasCustomName()
                ||!session.area().contains(target.blockPosition())||!HuntingWorker.canOrderTarget(worker,target,HiredHuntingTargets.fromState(session.state())))
            return blocked("Select one eligible wild animal with Sneak + Use while holding the Clipboard, then Draft > Task > Pursue selected target > Apply. Owned, tamed, allied and named animals are excluded.");
        java.util.function.Predicate<net.minecraft.world.item.ItemStack> weapon=s->HuntingWorker.isHuntingWeapon(s)&&(s.is(ItemTags.SWORDS)||s.is(ItemTags.AXES))&&(!s.isDamageableItem()||s.getDamageValue()<s.getMaxDamage());
        if(session.inventory().findTool(weapon).isEmpty()&&com.jvn.villagerretaliation.inventory.AssignedStorageService.countItems(worker,weapon)==0)return blocked("Supply a usable supported melee sword or axe for this first hunt.");
        String output=TutorialPlanChecks.output(level,worker);if(output==null)return blocked(TutorialPlanChecks.STORAGE_HELP);
        if(!session.context().autoDepositOutputs()||!session.context().useAssignedStorageForSupplies())return blocked("Enable assigned supplies and automatic output delivery.");
        String quarry=target.getUUID().toString();
        return new TutorialHuntingPlan(new TutorialRun.Scope(Map.of("kill",Set.of(quarry),"collect",Set.of(quarry),"loot_reconciled",Set.of(quarry),"deposit",Set.of(output)),
                Map.of("kill",1,"collect",64,"loot_reconciled",1,"deposit",65536),Map.of("kill",1,"loot_reconciled",1),output,true),"");
    }
    public static boolean allowed(Villager worker,net.minecraft.world.entity.LivingEntity target){
        var run=TutorialWorkService.run(worker).orElse(null);
        return run!=null&&run.scope()!=null&&TutorialWorkService.canTick(worker,HiredVillagerRole.HUNTING)&&settings(HiredWorkStateStore.state(worker))
                &&target instanceof Animal&&!target.hasCustomName()&&run.scope().targets().getOrDefault("kill",Set.of()).contains(target.getUUID().toString())
                &&run.committedUnits("kill")==0&&HuntingWorker.canOrderTarget(worker,target,HiredHuntingTargets.fromState(HiredWorkStateStore.state(worker)));
    }
    private static TutorialHuntingPlan blocked(String reason){return new TutorialHuntingPlan(null,reason);}
}
