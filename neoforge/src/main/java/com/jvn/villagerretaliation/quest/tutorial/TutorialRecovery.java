package com.jvn.villagerretaliation.quest.tutorial;
import com.jvn.villagerretaliation.interaction.*;
import com.jvn.villagerretaliation.interaction.work.*;
import java.util.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.resources.ResourceLocation;

public final class TutorialRecovery{
    private TutorialRecovery(){}
    public static String revalidate(ServerLevel level,Villager worker,TutorialRun run){
        if(run.scope()==null)return "";
        var role=HiredVillagerRole.bySerializedName(run.binding().role());
        if(!run.scope().output().isEmpty()&&!run.scope().output().equals(TutorialPlanChecks.output(level,worker)))
            return "Restore this worker's original Supplies and Output links; a different chest cannot satisfy the bound delivery.";
        var session=HiredWorkSession.create(level,worker,role);
        if(!TutorialSetupChecks.policy(worker,role,session.state()))return "Restore the lesson's exact role settings before resuming.";
        if(role==HiredVillagerRole.COOK||role==HiredVillagerRole.SMELTER){
            if(!TutorialSetupChecks.selection(level,worker,role))return "Restore the original output recipe filter before resuming.";
            TutorialFurnaceService.rebindStation(worker);
        }
        return "";
    }
    public static String recover(ServerPlayer player,Villager worker,TutorialRun run){
        if(run.scope()==null)return "Finish the initial setup first; no productive result has been reserved yet.";
        var role=HiredVillagerRole.bySerializedName(run.binding().role());var state=HiredWorkStateStore.state(worker);
        if(role==HiredVillagerRole.FISHING&&run.committedUnits("catch")==0){
            var min=TutorialFarmingPlan.target(run,"area_min");var max=TutorialFarmingPlan.target(run,"area_max");
            var area=HiredWorkArea.fromState(state,worker.blockPosition());
            if(min==null||max==null||!area.explicitlyAssigned()||!area.min().equals(min)||!area.max().equals(max))return "Apply the original shore box to the replacement Fisher first, then restore the remaining task.";
            var id=UUID.fromString(run.scope().targets().get("catch").iterator().next());
            String blocker=HiredTemporaryWorkService.submit(player,worker,new HiredTemporaryTask(id,HiredTemporaryTask.Kind.FISH,min,max,1),false);
            if(!blocker.isEmpty())return blocker;
        }
        if(role==HiredVillagerRole.BREWING&&run.committedUnits("brew")<3&&!com.jvn.villagerretaliation.interaction.work.brewing.BrewingWorker.hasOrder(state)){
            String[] route=run.scope().targets().get("route").iterator().next().split("\\|");
            com.jvn.villagerretaliation.interaction.work.brewing.BrewingWorker.setOrder(state,ResourceLocation.parse(route[0]),ResourceLocation.parse(route[1]),3-run.committedUnits("brew"),false,run.binding().assignment());
        }
        int recovered=TutorialOutputService.recoverCarried(worker);HiredWorkSession.invalidate(worker);TutorialSavedData.get(player.serverLevel()).setDirty();
        return "Restored the remaining bound task and moved "+recovered+" receipt-marked carried items to output cargo. For a replacement, move the old worker's marked goods into this worker's supply slots first. Unmarked matching items are untouched. Validate the remaining task before resuming.";
    }
    public static void reconcileLivingQuarry(ServerLevel level,TutorialRun run){
        for(var permit:List.copyOf(run.snapshot().pending().values()))if(permit.action().equals("kill")){
            try{if(level.getEntity(UUID.fromString(permit.target())) instanceof net.minecraft.world.entity.LivingEntity target&&target.isAlive())run.failed(permit);}
            catch(IllegalArgumentException invalid){ /* Uncertain work keeps its reservation. */ }
        }
    }
}
