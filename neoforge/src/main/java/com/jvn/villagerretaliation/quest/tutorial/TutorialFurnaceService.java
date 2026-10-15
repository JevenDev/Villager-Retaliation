package com.jvn.villagerretaliation.quest.tutorial;

import com.jvn.villagerretaliation.interaction.*;
import com.jvn.villagerretaliation.interaction.work.*;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/** A loaded finite batch retains station identity; only an actual vanilla input/output transition produces evidence. */
public final class TutorialFurnaceService {
    private static final String TAG = "VillagerRetaliationTutorialFurnace";
    private record Tick(Villager worker, TutorialRun.Permit permit, int input, int output, ItemStack expected) { }
    private static final Map<AbstractFurnaceBlockEntity, Tick> TICKS = new IdentityHashMap<>();
    private TutorialFurnaceService() { }
    public static boolean bound(Villager worker, AbstractFurnaceBlockEntity furnace) {
        var run = TutorialWorkService.run(worker).orElse(null);
        var marker = furnace.getPersistentData().getCompound(TAG);
        return run != null && marker.hasUUID("Trial") && marker.getUUID("Trial").equals(run.trial())
                && marker.hasUUID("Worker") && marker.getUUID("Worker").equals(worker.getUUID());
    }
    public static TutorialRun.Permit reserveLoad(Villager worker, AbstractFurnaceBlockEntity furnace, int count, boolean fuel) {
        var run = TutorialWorkService.run(worker).orElse(null);
        if (run == null || !java.util.Objects.equals(TutorialFurnacePlan.station((ServerLevel)worker.level(), worker),furnace.getBlockPos())) return null;
        if (!fuel && !furnace.getItem(0).isEmpty()) return null;
        var marker = furnace.getPersistentData().getCompound(TAG);
        if (!marker.isEmpty() && !bound(worker, furnace)) return null;
        var permit = TutorialWorkService.reserve(worker, fuel ? "fuel" : "load", TutorialWorkService.position((ServerLevel)worker.level(), furnace.getBlockPos()), count);
        if (permit != null) {
            marker.putUUID("Trial", run.trial()); marker.putUUID("Worker", worker.getUUID());
            furnace.getPersistentData().put(TAG, marker); furnace.setChanged();
        }
        return permit;
    }
    public static void rebindStation(Villager worker){
        var run=TutorialWorkService.run(worker).orElse(null);if(run==null||run.scope()==null)return;
        var level=(ServerLevel)worker.level();var pos=TutorialFurnacePlan.station(level,worker);
        if(pos!=null&&level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace){
            var marker=furnace.getPersistentData().getCompound(TAG);
            if(marker.hasUUID("Trial")&&marker.getUUID("Trial").equals(run.trial())){
                marker.putUUID("Worker",worker.getUUID());furnace.getPersistentData().put(TAG,marker);furnace.setChanged();
            }
        }
    }
    public static boolean beforeTick(Level world, AbstractFurnaceBlockEntity furnace) {
        TICKS.remove(furnace);
        var marker = furnace.getPersistentData().getCompound(TAG);
        if (!(world instanceof ServerLevel level) || !marker.hasUUID("Trial")) return true;
        var run = TutorialSavedData.get(level).trial(marker.getUUID("Trial")).orElse(null);
        if (run != null && (run.state() == TutorialRun.State.STOPPED || run.state() == TutorialRun.State.CONVERTED || run.state() == TutorialRun.State.ABANDONED)) {
            furnace.getPersistentData().remove(TAG); furnace.setChanged(); return true;
        }
        if (run == null || !(level.getEntity(run.binding().worker()) instanceof Villager worker)
                || !bound(worker, furnace) || !TutorialWorkService.canTick(worker, HiredVillagerRole.bySerializedName(run.binding().role()))) return false;
        ItemStack input = furnace.getItem(0), output = furnace.getItem(2);
        if (input.isEmpty()) return true;
        var role = HiredVillagerRole.bySerializedName(run.binding().role());
        if (!input.is(TutorialFurnacePlan.input(role)) || input.getCount() > run.committedUnits("load") - run.committedUnits("process")
                || !output.isEmpty() && !TutorialOutputService.attributable(worker, output)) return false;
        var recipe = level.getRecipeManager().getRecipeFor(TutorialFurnacePlan.type(furnace), new SingleRecipeInput(input), level).orElse(null);
        if (recipe == null || !run.scope().targets().get("recipe").contains(recipe.id().toString())) return false;
        var expected = recipe.value().assemble(new SingleRecipeInput(input), level.registryAccess());
        var timing=furnace.saveWithoutMetadata(level.registryAccess());
        if(timing.getInt("CookTime")+1<Math.max(1,timing.getInt("CookTimeTotal")))return true;
        var permit = TutorialWorkService.reserve(worker, "process", TutorialWorkService.position(level, furnace.getBlockPos()), 1);
        if (permit == null) return false;
        TICKS.put(furnace, new Tick(worker, permit, input.getCount(), output.getCount(), expected));
        return true;
    }
    public static void afterTick(Level world, AbstractFurnaceBlockEntity furnace) {
        var tick = TICKS.remove(furnace);
        if (tick == null || !(world instanceof ServerLevel level)) return;
        var output = furnace.getItem(2);
        if (furnace.getItem(0).getCount() == tick.input() - 1 && output.getCount() == tick.output() + tick.expected().getCount()
                && ItemStack.isSameItemSameComponents(TutorialOutputService.clean(output), tick.expected())) {
            TutorialOutputService.stamp(tick.worker(), tick.permit(), List.of(output));
            TutorialWorkService.committedProducts(tick.worker(), tick.permit(), List.of(tick.expected()));
            furnace.setChanged();
        } else if (furnace.getItem(0).getCount() == tick.input() && output.getCount() == tick.output()) TutorialWorkService.failed(tick.worker(), tick.permit());
        else { TutorialWorkService.interrupted(tick.worker()); }
    }
}
