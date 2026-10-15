package com.jvn.villagerretaliation.quest.tutorial;

import com.jvn.villagerretaliation.interaction.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/** Natural canopy drops are eligible only when born from the approved tree after its worker cut it. */
@EventBusSubscriber(modid = "villagerretaliation")
public final class TutorialPickupService {
    private static final String TAG = "VillagerRetaliationTutorialDrop";
    private TutorialPickupService() { }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void naturalDecay(BlockDropsEvent event) {
        if (event.isCanceled() || event.getBreaker()!=null || !event.getTool().isEmpty()
                || !com.jvn.villagerretaliation.interaction.work.logging.LoggingTreeGeometry.isNaturalLeaf(event.getState())) return;
        var level=event.getLevel();
        String target=TutorialWorkService.position(level,event.getPos());
        for(var run:TutorialSavedData.get(level).runs()) {
            if(run.state()!=TutorialRun.State.ACTIVE || !run.binding().role().equals("logging") || run.scope()==null || run.committedUnits("log")==0
                    || !run.binding().dimension().equals(level.dimension().location().toString()) || !run.scope().targets().getOrDefault("collect",java.util.Set.of()).contains(target)) continue;
            for(var item:event.getDrops()) mark(item,run,target);
            break;
        }
    }
    public static void mark(ItemEntity item,TutorialRun run,String target) {
        var marker=new net.minecraft.nbt.CompoundTag();marker.putUUID("Trial",run.trial());marker.putString("Source",target);
        item.getPersistentData().put(TAG,marker);
    }
    public static boolean allowed(Villager worker,ItemEntity item) {
        if(!TutorialWorkService.isTrial(worker))return true;
        var run=TutorialWorkService.run(worker).orElse(null);
        var marker=item.getPersistentData().getCompound(TAG);
        return run!=null && run.state()==TutorialRun.State.ACTIVE && run.scope()!=null && marker.hasUUID("Trial") && marker.getUUID("Trial").equals(run.trial())
                && run.scope().targets().getOrDefault("collect",java.util.Set.of()).contains(marker.getString("Source"))
                && item.getItem().getCount()<=run.scope().limits().getOrDefault("collect",0)-run.committedUnits("collect");
    }
    public static TutorialRun.Permit reserve(Villager worker,ItemEntity item) {
        if(!allowed(worker,item))return null;
        return TutorialWorkService.reserve(worker,"collect",item.getPersistentData().getCompound(TAG).getString("Source"),item.getItem().getCount());
    }
}
