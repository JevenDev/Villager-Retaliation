package com.jvn.villagerretaliation.quest.tutorial;

import com.google.gson.Gson;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid="villagerretaliation")
public final class TutorialAnimalBirthService {
    private static final String TAG="VillagerRetaliationTutorialBreeding";
    private static final Gson JSON=new Gson();
    private TutorialAnimalBirthService(){}
    public static void bind(Animal first,Animal second,TutorialRun.Permit permit){
        var tag=new net.minecraft.nbt.CompoundTag();tag.putString("Permit",JSON.toJson(permit));
        tag.putUUID("First",first.getUUID());tag.putUUID("Second",second.getUUID());
        first.getPersistentData().put(TAG,tag.copy());second.getPersistentData().put(TAG,tag.copy());
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void proposedChild(BabyEntitySpawnEvent event){
        if(event.isCanceled()||event.getChild()==null)return;
        var first=event.getParentA();var second=event.getParentB();var tag=first.getPersistentData().getCompound(TAG);
        if(tag.isEmpty()||!tag.equals(second.getPersistentData().getCompound(TAG))||!tag.hasUUID("First")||!tag.hasUUID("Second"))return;
        var pair=TutorialAnimalPlan.pair(first.getUUID(),second.getUUID());
        if(!pair.equals(TutorialAnimalPlan.pair(tag.getUUID("First"),tag.getUUID("Second"))))return;
        event.getChild().getPersistentData().put(TAG,tag.copy());
    }
    @SubscribeEvent
    public static void born(EntityTickEvent.Post event){
        var child=event.getEntity();var tag=child.getPersistentData().getCompound(TAG);
        if(!(child.level() instanceof ServerLevel level)||!(child instanceof Animal)||!child.isAlive()||!child.isAddedToLevel()||!tag.contains("Permit"))return;
        // Only the child has a third UUID; feeding/love mode and a proposed/cancelled birth are insufficient.
        if(child.getUUID().equals(tag.getUUID("First"))||child.getUUID().equals(tag.getUUID("Second")))return;
        try{
            var permit=JSON.fromJson(tag.getString("Permit"),TutorialRun.Permit.class);
            var run=TutorialSavedData.get(level).find(permit.binding().player(),TutorialService.questId(com.jvn.villagerretaliation.interaction.HiredVillagerRole.ANIMAL_HANDLING).toString()).orElse(null);
            if(run!=null&&permit.action().equals("breed")&&permit.target().equals(TutorialAnimalPlan.pair(tag.getUUID("First"),tag.getUUID("Second")))) {
                if(run.commit(new TutorialRun.Receipt(permit,"",0,child.getUUID().toString(),level.getGameTime())))TutorialSavedData.get(level).setDirty();
                if(run.snapshot().receipts().containsKey(permit.operation()))child.getPersistentData().remove(TAG);
            }
        }catch(RuntimeException invalid){ /* Preserve unknown evidence for explicit recovery. */ }
    }
}
