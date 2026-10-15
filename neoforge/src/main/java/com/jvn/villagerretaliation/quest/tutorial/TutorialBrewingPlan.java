package com.jvn.villagerretaliation.quest.tutorial;

import com.jvn.villagerretaliation.interaction.*;
import com.jvn.villagerretaliation.interaction.work.*;
import com.jvn.villagerretaliation.interaction.work.brewing.*;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.resources.ResourceLocation;

public record TutorialBrewingPlan(TutorialRun.Scope scope,String blocker){
    public static HiredBrewingRecipeCatalog.BrewingRoute chosen(ServerLevel level,net.minecraft.nbt.CompoundTag state){
        return HiredBrewingRecipeCatalog.find(level,ResourceLocation.tryParse(state.getString("BrewingTargetItem")),ResourceLocation.tryParse(state.getString("BrewingTargetPotion"))).orElse(null);
    }
    public static TutorialBrewingPlan inspect(ServerLevel level,Villager worker){
        var session=HiredWorkSession.create(level,worker,HiredVillagerRole.BREWING);var state=session.state();var route=chosen(level,state);
        if(route==null||!route.output().is(Items.POTION)||state.getBoolean("BrewingContinuous")||state.getInt("BrewingRemaining")!=3)
            return blocked("Open Configure Brewing, choose a runtime-supported regular potion route and Brew 3. Do not choose continuous brewing.");
        if(com.jvn.villagerretaliation.interaction.work.production.HiredProductionService.access(level,worker,HiredVillagerRole.BREWING).managed())return blocked("Finish conflicting managed production orders first.");
        if(!session.area().explicitlyAssigned())return blocked("Apply the small area around the brewing stand.");
        var stands=new ArrayList<BrewingStandBlockEntity>();
        for(var pos:session.context().workAreaPositions())if(level.hasChunkAt(pos)&&level.getBlockEntity(pos) instanceof BrewingStandBlockEntity stand)stands.add(stand);
        if(stands.size()!=1)return blocked("Include exactly one loaded brewing stand in this lesson's area.");
        var stand=stands.getFirst();for(int slot=0;slot<4;slot++)if(!stand.getItem(slot).isEmpty())return blocked("Clear old bottles and ingredients from the stand yourself. Its existing output cannot count.");
        String output=TutorialPlanChecks.output(level,worker);if(output==null)return blocked(TutorialPlanChecks.STORAGE_HELP);
        if(!session.context().autoDepositOutputs()||!session.context().useAssignedStorageForSupplies())return blocked("Enable assigned supplies and automatic output delivery.");
        if(HiredSupplyCrafting.countAvailable(worker,session.context(),HiredBrewingRecipeCatalog::isWaterPotion)<3)return blocked("Stock three filled water bottles in accessible Supplies; this lesson does not require water filling.");
        var bill=new LinkedHashMap<Item,Integer>();route.ingredients().forEach(item->bill.merge(item,1,Integer::sum));
        for(var need:bill.entrySet())if(HiredSupplyCrafting.countAvailable(worker,session.context(),need.getKey())<need.getValue())return blocked("Stock the runtime route ingredient: "+need.getValue()+" "+new ItemStack(need.getKey()).getHoverName().getString()+".");
        if(HiredSupplyCrafting.countAvailable(worker,session.context(),Items.BLAZE_POWDER)<1)return blocked("Stock one blaze powder for this route's normal fuel use.");
        String site=TutorialWorkService.position(level,stand.getBlockPos());var slots=Set.of(site+"/0",site+"/1",site+"/2");
        var stages=new LinkedHashSet<String>();for(int i=0;i<route.ingredients().size();i++)stages.add(site+"/stage/"+i);
        return new TutorialBrewingPlan(new TutorialRun.Scope(Map.of("station",Set.of(site),"route",Set.of(route.itemId()+"|"+route.potionId()),
                "ingredients",Set.of(route.ingredients().stream().map(i->net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(i).toString()).collect(java.util.stream.Collectors.joining("|"))),
                "load_bottle",slots,"ingredient",stages,"brew_stage",stages,"brew",slots,"fuel",Set.of(site),"deposit",Set.of(output)),
                Map.of("load_bottle",3,"ingredient",stages.size(),"brew_stage",stages.size(),"brew",3,"fuel",1,"deposit",65536),Map.of("load_bottle",3,"brew_stage",stages.size(),"brew",3),output,true),"");
    }
    public static boolean matches(ServerLevel level,Villager worker,HiredWorkContext context,HiredBrewingRecipeCatalog.BrewingRoute route){
        var run=TutorialWorkService.run(worker).orElse(null);
        return run!=null&&run.scope()!=null&&route!=null&&!context.state().getBoolean("BrewingContinuous")
                &&context.state().getInt("BrewingRemaining")==3-run.committedUnits("brew")
                &&run.scope().targets().get("route").contains(route.itemId()+"|"+route.potionId())
                &&run.scope().targets().get("ingredients").contains(route.ingredients().stream().map(i->net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(i).toString()).collect(java.util.stream.Collectors.joining("|")));
    }
    private static TutorialBrewingPlan blocked(String reason){return new TutorialBrewingPlan(null,reason);}
}
