package com.jvn.villagerretaliation.quest.tutorial;

import com.jvn.villagerretaliation.interaction.*;
import com.jvn.villagerretaliation.interaction.work.*;
import com.jvn.villagerretaliation.inventory.VillagerItemFilterService;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;

public record TutorialCraftingPlan(TutorialRun.Scope scope, String blocker) {
    public static RecipeHolder<CraftingRecipe> recipe(ServerLevel level) {
        return level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream()
                .filter(h -> valid(level, h.value())).findFirst().orElse(null);
    }
    private static boolean valid(ServerLevel level, CraftingRecipe recipe) {
        var plan = WorkCraftingPlan.prepare(level, recipe, List.of(new ItemStack(Items.OAK_LOG)), Map.of(), true);
        return plan != null && plan.crafts() == 1 && plan.consumedStacks().size() == 1
                && plan.consumedStacks().getFirst().is(Items.OAK_LOG) && plan.consumedStacks().getFirst().getCount() == 1
                && plan.result().is(Items.OAK_PLANKS) && plan.result().getCount() == 4;
    }
    public static TutorialCraftingPlan inspect(ServerLevel level, Villager worker) {
        var session = HiredWorkSession.create(level, worker, HiredVillagerRole.CRAFTSMAN);
        var targets = CraftsmanWorker.targets(level, VillagerItemFilterService.assignedFilter(worker));
        var recipe = recipe(level);
        if (recipe == null) return blocked("The runtime pack does not provide the one-log, four-plank recipe; this fixture is unavailable.");
        if (targets.size() != 1 || !targets.getFirst().stack().is(Items.OAK_PLANKS)
                || CraftsmanWorker.mode(session.state()) != CraftsmanWorker.Mode.PREFER_FIRST)
            return blocked("Set one oak-planks target in the job Filter slot and choose Prefer First. Leave other targets out of this lesson.");
        if (!session.area().explicitlyAssigned()) return blocked("Apply a small workshop area containing a crafting table.");
        if (com.jvn.villagerretaliation.interaction.work.production.HiredProductionService.access(level, worker, HiredVillagerRole.CRAFTSMAN).managed())
            return blocked("Finish or cancel conflicting production orders first. This lesson uses your ordinary single output filter.");
        String output = TutorialPlanChecks.output(level, worker);
        if (output == null) return blocked(TutorialPlanChecks.STORAGE_HELP);
        if (!session.context().autoDepositOutputs() || !session.context().useAssignedStorageForSupplies()) return blocked("Enable assigned supplies and automatic output delivery.");
        BlockPos table = null;
        for (var raw : session.context().workAreaPositions()) if (level.hasChunkAt(raw) && level.getBlockState(raw).is(Blocks.CRAFTING_TABLE)) { table = raw.immutable(); break; }
        if (table == null) return blocked("No loaded crafting table is inside the saved workshop area.");
        if (HiredSupplyCrafting.countAvailable(worker, session.context(), Items.OAK_LOG) < 1) return blocked("Put one oak log in accessible Supplies or this worker's supply slots.");
        return new TutorialCraftingPlan(new TutorialRun.Scope(Map.of("craft", Set.of(recipe.id().toString()), "station", Set.of(TutorialWorkService.position(level, table)),
                "deposit", Set.of(output)), Map.of("craft", 1, "deposit", 65536), Map.of("craft", 1), output, true), "");
    }
    public static boolean allows(ServerLevel level, Villager worker, BlockPos table, CraftingRecipe recipe, Map<Item, Integer> materials) {
        var run = TutorialWorkService.run(worker).orElse(null);
        if (run == null || run.scope() == null || !run.scope().targets().getOrDefault("station", Set.of()).contains(TutorialWorkService.position(level, table))
                || materials.size() != 1 || materials.getOrDefault(Items.OAK_LOG, 0) != 1 || !valid(level, recipe)) return false;
        return level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream().anyMatch(h -> h.value() == recipe && run.scope().targets().get("craft").contains(h.id().toString()));
    }
    private static TutorialCraftingPlan blocked(String reason) { return new TutorialCraftingPlan(null, reason); }
}
