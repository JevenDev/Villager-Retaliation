package com.jvn.villagerretaliation.quest.tutorial;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.inventory.VillagerItemFilterService;
import com.jvn.villagerretaliation.item.VillagerFilterMatcher;
import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import com.jvn.villagerretaliation.item.VillagerRecipeFilterData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** A setup-only exercise: committed assignment and evaluated matching never grant productive credit. */
public final class TutorialFilterExercise {
    public static final String EXCLUDED = "filter_exercise_assigned_excluded";
    public static final String OBSERVED = "filter_observe";
    public static final String CORRECTED = "filter_exercise_assigned_corrected";

    private TutorialFilterExercise() { }

    /** Called only after the real assignment service has installed and consumed the held filter. */
    public static void assigned(ServerPlayer player, Villager worker) {
        var run = TutorialWorkService.run(worker).orElse(null);
        if (run == null || !run.binding().player().equals(player.getUUID())
                || !run.binding().role().equals(HiredVillagerRole.CRAFTSMAN.serializedName())
                || run.completed() || !(worker.level() instanceof ServerLevel level)) return;
        String checkpoint = configured(level, worker, false) ? EXCLUDED
                : configured(level, worker, true) && run.snapshot().learned().contains(OBSERVED) ? CORRECTED : "";
        if (!checkpoint.isEmpty() && run.learn(checkpoint)) {
            var saved = TutorialSavedData.get(level);
            saved.setDirty();
            saved.saveOwnerCheckpoints();
            TutorialWorkService.setupCommitted(worker, player.getUUID(), "work_filter_assigned",
                    java.util.Map.of("source", "committed_assignment", "checkpoint", checkpoint));
        }
    }

    public static boolean check(ServerLevel level, Villager worker, TutorialRun run, String parameter) {
        return switch (parameter) {
            case "filter_excluded", "filter_observed" -> run.snapshot().learned().contains(EXCLUDED)
                    && configured(level, worker, false);
            case "filter_corrected" -> run.snapshot().learned().contains(OBSERVED)
                    && run.snapshot().learned().contains(CORRECTED) && configured(level, worker, true);
            default -> false;
        };
    }

    private static boolean configured(ServerLevel level, Villager worker, boolean allow) {
        var filter = VillagerItemFilterService.assignedFilter(worker);
        var recipe = TutorialCraftingPlan.recipe(level);
        var resolution = VillagerRecipeFilterData.resolve(level, filter);
        var policy = VillagerFilterPolicy.read(filter);
        return recipe != null && resolution.valid() && resolution.recipe().id().equals(recipe.id())
                && policy.valid() && policy.listMode() == (allow ? VillagerFilterPolicy.ListMode.ALLOW_MATCHING
                        : VillagerFilterPolicy.ListMode.DENY_MATCHING)
                && VillagerFilterMatcher.matches(level, filter, new ItemStack(Items.OAK_PLANKS)) == allow;
    }
}
