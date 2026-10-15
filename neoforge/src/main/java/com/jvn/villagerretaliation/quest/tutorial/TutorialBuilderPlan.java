package com.jvn.villagerretaliation.quest.tutorial;

import com.jvn.villagerretaliation.interaction.*;
import com.jvn.villagerretaliation.interaction.work.builder.*;
import com.jvn.villagerretaliation.item.ConstructionBlueprintItem;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Rotation;

public final class TutorialBuilderPlan{
    public static final ResourceLocation TEMPLATE=ResourceLocation.parse("minecraft:village/plains/houses/plains_accessory_1");
    private TutorialBuilderPlan(){}
    public static Optional<BuilderStructureScanner.StructurePlan> plan(ServerLevel level,Rotation rotation){
        return BuilderStructureCatalog.byId(level.getServer(),TEMPLATE).flatMap(entry->BuilderStructureScanner.scan(level,entry,rotation));
    }
    public static String fingerprint(BuilderStructureScanner.StructurePlan plan){
        String raw=plan.entry().id()+"/"+plan.rotation()+"/"+plan.blocks().toString()+"/"+plan.clearingBlocks().toString();
        try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    public static TutorialProvisionService.Delivery blueprint(ServerPlayer player,TutorialRun run){
        var plan=plan(player.serverLevel(),Rotation.NONE).orElse(null);
        if(plan==null)return TutorialProvisionService.Delivery.CONFLICT;
        var item=ConstructionBlueprintItem.create(player.serverLevel(),null,plan,BlockPos.ZERO,run.binding().task(),0,0,"",0L);
        var data=item.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        var marker=new net.minecraft.nbt.CompoundTag();marker.putUUID("Trial",run.trial());marker.putBoolean("PreviewApplied",false);
        data.put("VillagerRetaliationTutorialBlueprint",marker);item.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(data));
        return TutorialProvisionService.deliver(player,run,"plains_accessory_1_blueprint",item);
    }
    public static boolean approve(ServerLevel level,Villager worker,ConstructionBlueprintItem.PreviewData preview,BuilderStructureScanner.StructurePlan plan){
        var run=TutorialWorkService.run(worker).orElse(null);
        if(run==null||!TEMPLATE.equals(preview.structureId())||!run.binding().task().equals(preview.jobId())||run.completed())return false;
        if(run.scope()!=null)return matches(level,worker,plan,preview.origin(),preview.jobId());
        var positions=new LinkedHashSet<String>();plan.blocks().forEach(b->positions.add(TutorialWorkService.position(level,plan.worldPos(preview.origin(),b))));
        boolean needsWork=plan.blocks().stream().anyMatch(b->!BuilderStructureScanner.sameSchematicState(level.getBlockState(plan.worldPos(preview.origin(),b)),b.state()));
        if(!needsWork||positions.isEmpty()||positions.size()>65536)return false;
        var scope=new TutorialRun.Scope(Map.of("place",positions,"build_complete",Set.of(preview.jobId().toString()),
                "plan",Set.of(fingerprint(plan)),"origin",Set.of(TutorialWorkService.position(level,preview.origin()))),
                Map.of("place",positions.size(),"build_complete",1),Map.of("place",1,"build_complete",1),"");
        boolean result=run.activate(run.binding(),scope);run.pause();TutorialSavedData.get(level).setDirty();return result;
    }
    public static boolean matches(ServerLevel level,Villager worker,BuilderStructureScanner.StructurePlan plan,BlockPos origin,UUID job){
        var run=TutorialWorkService.run(worker).orElse(null);
        return run!=null&&run.scope()!=null&&TEMPLATE.equals(plan.entry().id())&&run.binding().task().equals(job)
                &&run.scope().targets().get("origin").contains(TutorialWorkService.position(level,origin))
                &&run.scope().targets().get("plan").contains(fingerprint(plan));
    }
    public static String readiness(ServerLevel level,Villager worker,TutorialRun run){
        var state=HiredWorkStateStore.state(worker);
        if(!BuilderTaskState.hasTask(state)||BuilderTaskState.structureId(state).filter(TEMPLATE::equals).isEmpty()
                ||BuilderTaskState.jobId(state).filter(run.binding().task()::equals).isEmpty())return "Hold this lesson's Plains Accessory 1 blueprint, place its preview, then use it on this Builder and confirm the zero-fee construction.";
        var plan=plan(level,BuilderTaskState.rotation(state)).orElse(null);
        if(plan==null||!matches(level,worker,plan,BuilderTaskState.origin(state),BuilderTaskState.jobId(state).orElseThrow()))return "Restore the original template and site; this lesson cannot sponsor a different project.";
        if(!com.jvn.villagerretaliation.inventory.AssignedStorageService.hasLiveAssignedStorageForPurpose(level,worker,"supply"))return TutorialPlanChecks.STORAGE_HELP;
        return "";
    }
}
