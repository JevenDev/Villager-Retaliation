package com.jvn.villagerretaliation.quest.tutorial;
import com.jvn.villagerretaliation.interaction.*;
import com.jvn.villagerretaliation.interaction.work.*;
import com.jvn.villagerretaliation.inventory.VillagerItemFilterService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.*;
public final class TutorialSetupChecks{
    private TutorialSetupChecks(){}
    public static boolean policy(Villager worker,HiredVillagerRole role,net.minecraft.nbt.CompoundTag state){
        return switch(role){case FARMING->HiredFarmingOptions.crop(state)==HiredFarmingOptions.Crop.WHEAT&&HiredFarmingOptions.tillSoil(state)&&HiredFarmingOptions.plantCrops(state)&&HiredFarmingOptions.harvestCrops(state);
            case LOGGING->TutorialLoggingPlan.settings(state);case HUNTING->TutorialHuntingPlan.settings(state);case ANIMAL_HANDLING->TutorialAnimalPlan.settings(state);
            case COMBAT->HiredCombatMode.fromState(state)==HiredCombatMode.GUARD;case MINING->HiredMiningMode.fromState(state)==HiredMiningMode.EXPOSED_ORES;
            case CRAFTSMAN->CraftsmanWorker.mode(state)==CraftsmanWorker.Mode.PREFER_FIRST;default->true;};
    }
    public static boolean selection(ServerLevel level,Villager worker,HiredVillagerRole role){
        var filter=VillagerItemFilterService.assignedFilter(worker);var state=HiredWorkStateStore.state(worker);
        return switch(role){case CRAFTSMAN->{var targets=CraftsmanWorker.targets(level,filter);yield targets.size()==1&&targets.getFirst().stack().is(Items.OAK_PLANKS)&&TutorialCraftingPlan.recipe(level)!=null;}
            case COOK,SMELTER->!filter.isEmpty()&&com.jvn.villagerretaliation.item.VillagerFilterMatcher.matches(level,filter,new ItemStack(TutorialFurnacePlan.output(role)));
            case BREWING->TutorialBrewingPlan.chosen(level,state)!=null&&!state.getBoolean("BrewingContinuous")&&state.getInt("BrewingRemaining")==3;
            case BUILDER->com.jvn.villagerretaliation.interaction.work.builder.BuilderTaskState.structureId(state).filter(TutorialBuilderPlan.TEMPLATE::equals).isPresent();
            default->true;};
    }
    public static boolean task(ServerLevel level,Villager worker,HiredVillagerRole role){
        var queue=HiredTemporaryWorkService.queue(HiredWorkStateStore.state(worker));if(queue==null||queue.tasks().size()!=1)return false;
        return switch(role){case FISHING->TutorialFishingPlan.inspect(level,worker).scope()!=null;case HUNTING->TutorialHuntingPlan.inspect(level,worker).scope()!=null;
            case ANIMAL_HANDLING->{var run=TutorialWorkService.run(worker).orElse(null);yield run!=null&&run.scope()!=null&&queue.current().kind()==HiredTemporaryTask.Kind.BREED
                    &&run.scope().targets().getOrDefault("parents",java.util.Set.of()).contains(queue.current().entity().toString())||TutorialAnimalPlan.inspect(level,worker).scope()!=null;}
            default->false;};
    }
}
