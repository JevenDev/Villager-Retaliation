package com.jvn.villagerretaliation.quest.tutorial;

import com.jvn.villagerretaliation.interaction.*;
import com.jvn.villagerretaliation.interaction.work.*;
import com.jvn.villagerretaliation.inventory.VillagerItemFilterService;
import com.jvn.villagerretaliation.item.VillagerFilterMatcher;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.entity.*;

public record TutorialFurnacePlan(TutorialRun.Scope scope, String blocker) {
    public static Item input(HiredVillagerRole role) { return role == HiredVillagerRole.COOK ? Items.COD : Items.RAW_IRON; }
    public static Item output(HiredVillagerRole role) { return role == HiredVillagerRole.COOK ? Items.COOKED_COD : Items.IRON_INGOT; }
    @SuppressWarnings("unchecked")
    public static RecipeType<AbstractCookingRecipe> type(AbstractFurnaceBlockEntity station) {
        return (RecipeType<AbstractCookingRecipe>) (RecipeType<?>) (station instanceof SmokerBlockEntity ? RecipeType.SMOKING
                : station instanceof BlastFurnaceBlockEntity ? RecipeType.BLASTING : RecipeType.SMELTING);
    }
    public static TutorialFurnacePlan inspect(ServerLevel level, Villager worker, HiredVillagerRole role) {
        var session = HiredWorkSession.create(level, worker, role);
        if (!session.area().explicitlyAssigned()) return blocked("Apply the small workplace area around one empty station.");
        if (com.jvn.villagerretaliation.interaction.work.production.HiredProductionService.access(level, worker, role).managed())
            return blocked("Finish or cancel conflicting production orders. This lesson uses the ordinary output selection.");
        String output = TutorialPlanChecks.output(level, worker);
        if (output == null) return blocked(TutorialPlanChecks.STORAGE_HELP);
        if (!session.context().autoDepositOutputs() || !session.context().useAssignedStorageForSupplies()) return blocked("Enable assigned supplies and automatic output delivery.");
        ItemStack filter = VillagerItemFilterService.assignedFilter(worker);
        if (filter.isEmpty() || !VillagerFilterMatcher.matches(level, filter, new ItemStack(output(role))))
            return blocked("Set the job Filter to the selected finished result: " + new ItemStack(output(role)).getHoverName().getString() + ".");
        var stations = new ArrayList<AbstractFurnaceBlockEntity>();
        for (var raw : session.context().workAreaPositions()) if (level.hasChunkAt(raw) && level.getBlockEntity(raw) instanceof AbstractFurnaceBlockEntity station
                && (role == HiredVillagerRole.COOK ? station instanceof SmokerBlockEntity : station instanceof FurnaceBlockEntity || station instanceof BlastFurnaceBlockEntity)) stations.add(station);
        if (stations.size() != 1) return blocked("Include exactly one supported station: a smoker for the Cook, or furnace/blast furnace for the Smelter.");
        var station = stations.getFirst();
        if (!station.getItem(0).isEmpty() || !station.getItem(2).isEmpty()) return blocked("Clear the station's old input and output yourself. Existing goods cannot count as this assignment's batch.");
        var recipe = level.getRecipeManager().getRecipeFor(type(station), new SingleRecipeInput(new ItemStack(input(role))), level).orElse(null);
        if (recipe == null || !recipe.value().assemble(new SingleRecipeInput(new ItemStack(input(role))), level.registryAccess()).is(output(role))
                || recipe.value().getResultItem(level.registryAccess()).getCount() != 1) return blocked("This runtime pack does not provide the required one-input, one-result recipe.");
        if (HiredSupplyCrafting.countAvailable(worker, session.context(), input(role)) < 2
                || station.getItem(1).isEmpty() && HiredSupplyCrafting.countAvailable(worker, session.context(), Items.COAL) < 1)
            return blocked("Stock the two actual inputs and one coal in accessible Supplies. Existing station fuel may be reused.");
        String site = TutorialWorkService.position(level, station.getBlockPos());
        return new TutorialFurnacePlan(new TutorialRun.Scope(Map.of("load", Set.of(site), "process", Set.of(site),
                "fuel", Set.of(site), "station", Set.of(site), "recipe", Set.of(recipe.id().toString()), "deposit", Set.of(output)),
                Map.of("load", 2, "process", 2, "fuel", 1, "deposit", 65536), Map.of("process", 2), output, true), "");
    }
    public static BlockPos station(ServerLevel level, Villager worker) {
        var run = TutorialWorkService.run(worker).orElse(null);
        if (run == null || run.scope() == null) return null;
        String target = run.scope().targets().getOrDefault("station", Set.of()).stream().findFirst().orElse("");
        return TutorialFarmingPlan.target(run, "station");
    }
    private static TutorialFurnacePlan blocked(String reason) { return new TutorialFurnacePlan(null, reason); }
}
