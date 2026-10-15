package com.jvn.villagerretaliation.quest.tutorial;

import com.jvn.villagerretaliation.interaction.*;
import com.jvn.villagerretaliation.interaction.work.brewing.*;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

public final class TutorialBrewingService{
    private static final String TAG="VillagerRetaliationTutorialStand";
    private record Tick(Villager worker,TutorialRun.Permit permit,List<ItemStack> before,List<ItemStack> after,int ingredient){}
    private static final Map<BrewingStandBlockEntity,Tick>TICKS=new IdentityHashMap<>();
    private TutorialBrewingService(){}
    public static TutorialRun.Permit load(Villager worker,BrewingStandBlockEntity stand,String action,String target){
        var run=TutorialWorkService.run(worker).orElse(null);if(run==null||run.scope()==null)return null;
        String site=TutorialWorkService.position((ServerLevel)worker.level(),stand.getBlockPos());
        if(!run.scope().targets().get("station").contains(site))return null;
        var marker=stand.getPersistentData().getCompound(TAG);
        if(marker.hasUUID("Trial")&&!marker.getUUID("Trial").equals(run.trial()))return null;
        var permit=TutorialWorkService.reserve(worker,action,target,1);
        if(permit!=null){marker.putUUID("Trial",run.trial());stand.getPersistentData().put(TAG,marker);stand.setChanged();}
        return permit;
    }
    public static boolean beforeTick(Level world,BrewingStandBlockEntity stand){
        TICKS.remove(stand);var marker=stand.getPersistentData().getCompound(TAG);
        if(!(world instanceof ServerLevel level)||!marker.hasUUID("Trial"))return true;
        var run=TutorialSavedData.get(level).trial(marker.getUUID("Trial")).orElse(null);
        if(run!=null&&(run.state()==TutorialRun.State.STOPPED||run.state()==TutorialRun.State.CONVERTED||run.state()==TutorialRun.State.ABANDONED)){
            stand.getPersistentData().remove(TAG);stand.setChanged();return true;
        }
        if(run==null||!(level.getEntity(run.binding().worker()) instanceof Villager worker)||!TutorialWorkService.canTick(worker,HiredVillagerRole.BREWING))return false;
        var route=TutorialBrewingPlan.chosen(level,HiredWorkStateStore.state(worker));if(route==null)return false;
        int stage=run.committedUnits("brew_stage");
        if(stage>=route.ingredients().size()||stand.getItem(3).isEmpty())return true;
        if(run.committedUnits("load_bottle")!=3||!stand.getItem(3).is(route.ingredients().get(stage))||stand.getItem(3).getCount()!=1)return false;
        ItemStack expected=PotionContents.createItemStack(Items.POTION,Potions.WATER);
        for(int i=0;i<stage;i++)expected=level.potionBrewing().mix(new ItemStack(route.ingredients().get(i)),expected);
        var before=new ArrayList<ItemStack>();var after=new ArrayList<ItemStack>();
        for(int slot=0;slot<3;slot++){
            var bottle=stand.getItem(slot);if(bottle.getCount()!=1||!ItemStack.isSameItemSameComponents(bottle,expected))return false;
            before.add(bottle.copy());after.add(level.potionBrewing().mix(stand.getItem(3),bottle));
        }
        if(stand.saveWithoutMetadata(level.registryAccess()).getInt("BrewTime")!=1)return true;
        var permit=TutorialWorkService.reserve(worker,"brew_stage",TutorialWorkService.position(level,stand.getBlockPos())+"/stage/"+stage,1);
        if(permit==null)return false;TICKS.put(stand,new Tick(worker,permit,before,after,stand.getItem(3).getCount()));return true;
    }
    public static void afterTick(Level world,BrewingStandBlockEntity stand){
        var tick=TICKS.remove(stand);if(tick==null)return;
        boolean changed=true,unchanged=true;
        for(int slot=0;slot<3;slot++){
            changed&=ItemStack.isSameItemSameComponents(stand.getItem(slot),tick.after().get(slot));
            unchanged&=ItemStack.isSameItemSameComponents(stand.getItem(slot),tick.before().get(slot));
        }
        if(changed&&stand.getItem(3).getCount()==tick.ingredient()-1)TutorialWorkService.committed(tick.worker(),tick.permit(),"",0,"");
        else if(unchanged&&stand.getItem(3).getCount()==tick.ingredient())TutorialWorkService.failed(tick.worker(),tick.permit());
        else TutorialWorkService.interrupted(tick.worker());
    }
}
