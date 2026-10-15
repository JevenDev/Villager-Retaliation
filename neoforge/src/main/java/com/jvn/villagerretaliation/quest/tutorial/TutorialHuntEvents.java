package com.jvn.villagerretaliation.quest.tutorial;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.work.HuntingWorker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.*;

@EventBusSubscriber(modid="villagerretaliation")
public final class TutorialHuntEvents{
    private TutorialHuntEvents(){}
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void authorizeDamage(LivingIncomingDamageEvent event){
        if(!(event.getSource().getEntity() instanceof Villager worker)||!TutorialWorkService.isTrial(worker))return;
        if(!TutorialHuntingPlan.allowed(worker,event.getEntity())){event.setCanceled(true);return;}
        var run=TutorialWorkService.run(worker).orElseThrow();
        var pending=run.snapshot().pending().values().stream().filter(p->p.action().equals("kill")&&p.target().equals(event.getEntity().getUUID().toString())).findFirst().orElse(null);
        if(pending==null&&TutorialWorkService.reserve(worker,"kill",event.getEntity().getUUID().toString(),1)==null)event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void actualLoot(LivingDropsEvent event){
        if(!(event.getSource().getEntity() instanceof Villager worker)||!TutorialWorkService.isTrial(worker))return;
        var run=TutorialWorkService.run(worker).orElse(null);if(run==null)return;
        String target=event.getEntity().getUUID().toString();
        var permit=run.snapshot().pending().values().stream().filter(p->p.action().equals("kill")&&p.target().equals(target)).findFirst().orElse(null);
        if(permit==null)return;
        int remaining=64;
        if(!event.isCanceled())for(var drop:event.getDrops())if(HuntingWorker.isHuntingLoot(drop.getItem())&&drop.getItem().getCount()<=remaining){
            TutorialPickupService.mark(drop,run,target);run.learn("hunt_drop:"+drop.getUUID());remaining-=drop.getItem().getCount();
        }
        TutorialWorkService.committed(worker,permit,"",0,"");
        TutorialSavedData.get((ServerLevel)worker.level()).setDirty();
    }
    public static void finishCollection(Villager worker){
        var run=TutorialWorkService.run(worker).orElse(null);if(run==null||run.committedUnits("kill")==0||run.committedUnits("loot_reconciled")>0)return;
        var learned=run.snapshot().learned();
        if(learned.stream().filter(s->s.startsWith("hunt_drop:")).anyMatch(s->!learned.contains("collected_entity:"+s.substring("hunt_drop:".length()))))return;
        var permit=TutorialWorkService.reserve(worker,"loot_reconciled",run.scope().targets().get("kill").iterator().next(),1);
        if(permit!=null)TutorialWorkService.committed(worker,permit,"",0,"");
    }
}
