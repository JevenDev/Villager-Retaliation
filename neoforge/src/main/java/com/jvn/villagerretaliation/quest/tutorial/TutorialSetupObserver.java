package com.jvn.villagerretaliation.quest.tutorial;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/** Notifications follow saved inventory/settings mutations. They are setup observations, never productive outcomes. */
public final class TutorialSetupObserver{
    private static final ThreadLocal<Boolean> PUBLISHING=ThreadLocal.withInitial(()->false);
    private TutorialSetupObserver(){}
    public static void inventory(Villager worker,java.util.List<net.minecraft.world.item.ItemStack> items){
        if(!(worker.level() instanceof ServerLevel)||!TutorialWorkService.isTrial(worker))return;
        String snapshot=items.stream().map(stack->stack.isEmpty()?"empty":stack.save(worker.registryAccess()).toString()).collect(java.util.stream.Collectors.joining("|"));
        changed(worker,"Inventory",snapshot,"work_inventory_committed");
    }
    public static void settings(Villager worker,UUID player,CompoundTag state){
        var run=TutorialWorkService.run(worker).orElse(null);if(run==null||!run.binding().player().equals(player))return;
        var settings=new CompoundTag();
        for(String key:state.getAllKeys())if(!key.startsWith("Tutorial")&&!key.contains("Progress")&&!key.contains("Status")
                &&(key.equals("Enabled")||key.equals("UseAssignedStorageForSupplies")||key.equals("AutoDepositOutputs")
                ||key.startsWith("Farming")||key.startsWith("Logging")||key.startsWith("Hunt")||key.startsWith("AnimalHandling")
                ||key.equals("MiningMode")||key.equals("CombatMode")||key.equals("CraftsmanMode")))settings.put(key,state.get(key).copy());
        changed(worker,"Settings",settings.toString(),"work_settings_committed");
    }
    private static void changed(Villager worker,String kind,String snapshot,String event){
        if(PUBLISHING.get())return;
        var run=TutorialWorkService.run(worker).orElse(null);if(run==null)return;
        String key="TutorialObserved"+kind;var data=worker.getPersistentData();
        if(snapshot.equals(data.getString(key)))return;data.putString(key,snapshot);
        try{PUBLISHING.set(true);TutorialWorkService.setupCommitted(worker,run.binding().player(),event,Map.of("source","committed_state"));}
        finally{PUBLISHING.set(false);}
    }
}
