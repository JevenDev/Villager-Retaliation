package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.item.VillagerFilterMatcher;
import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import com.jvn.villagerretaliation.item.VillagerItemFilterData;
import com.jvn.villagerretaliation.item.VillagerAttributeFilterData;
import com.jvn.villagerretaliation.item.VillagerRecipeFilterData;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.VillagerWorkAuthority;
import com.jvn.villagerretaliation.interaction.work.CookingWorker;
import com.jvn.villagerretaliation.interaction.work.SmeltingWorker;
import java.util.stream.Stream;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;

public final class VillagerItemFilterService {
    private VillagerItemFilterService() {
    }

    public static ItemStack assignedFilter(Villager villager) {
        if (villager == null || villager.isBaby()) {
            return ItemStack.EMPTY;
        }
        ItemStack filter = HiredJobInventory.getJobInventory(villager).getItem(HiredJobInventory.FILTER_SLOT);
        return VillagerRetaliationItems.isFilter(filter) ? filter : ItemStack.EMPTY;
    }

    public static boolean mayWithdraw(Villager villager, ItemStack candidate) {
        ItemStack filter = assignedFilter(villager);
        return filter.isEmpty()
                || VillagerRetaliationItems.isRecipeFilter(filter)
                || VillagerFilterMatcher.matches(villager.level(), filter, candidate);
    }

    /** Replaces the assigned filter with a single configured copy and returns the prior filter. */
    public static ItemStack replaceFilter(Villager villager, ItemStack replacement) {
        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
        ItemStack oldFilter = inventory.getItem(HiredJobInventory.FILTER_SLOT).copy();
        ItemStack stored = VillagerRetaliationItems.isFilter(replacement)
                ? replacement.copyWithCount(1)
                : ItemStack.EMPTY;
        inventory.setItem(HiredJobInventory.FILTER_SLOT, stored);
        return oldFilter;
    }

    /**
     * Assigns one exact configured copy. Editing mode and policy is a separate editor action.
     * Conversation and distance validation remain the responsibility of the dialogue handler.
     */
    public static AssignmentResult assignHeldFilter(
            ServerPlayer player,
            Villager villager) {
        if (player == null
                || villager == null
                || villager.isBaby()
                || !(villager.level() instanceof ServerLevel level)
                || player.serverLevel() != level
                || !canAssign(level, villager, player)) {
            return AssignmentResult.REJECTED;
        }
        ItemStack heldFilter = player.getMainHandItem();
        if (!VillagerRetaliationItems.isFilter(heldFilter)) {
            return AssignmentResult.REJECTED;
        }

        ItemStack assignedFilter = heldFilter.copyWithCount(1);
        ItemStack oldFilter = replaceFilter(villager, assignedFilter);
        if (!player.getAbilities().instabuild) {
            heldFilter.shrink(1);
        }

        boolean hadOldFilter = !oldFilter.isEmpty();
        boolean droppedOldFilter = false;
        if (hadOldFilter && !player.getInventory().add(oldFilter)) {
            player.drop(oldFilter, false);
            droppedOldFilter = true;
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        com.jvn.villagerretaliation.quest.tutorial.TutorialFilterExercise.assigned(player, villager);
        return new AssignmentResult(true, hadOldFilter, droppedOldFilter);
    }

    /** Older callers may still supply a mode; assignment never edits the configured filter. */
    @Deprecated
    public static AssignmentResult assignHeldFilter(
            ServerPlayer player, Villager villager, VillagerItemFilterData.Mode mode) {
        return mode == null ? AssignmentResult.REJECTED : assignHeldFilter(player, villager);
    }

    /** Explains current-job behavior without changing, repairing, or migrating the saved filter. */
    public static Component assignmentSummary(ServerLevel level, Villager villager, ItemStack filter) {
        HiredVillagerRole role = VillagerWorkAuthority.resolve(level, villager).role();
        boolean production = role == HiredVillagerRole.CRAFTSMAN
                || role == HiredVillagerRole.COOK || role == HiredVillagerRole.SMELTER;
        String defaultKey = role == HiredVillagerRole.CRAFTSMAN ? "craftsman"
                : role == HiredVillagerRole.COOK ? "cook"
                : role == HiredVillagerRole.SMELTER ? "smelter" : "supplies";
        var summary = Component.empty();
        if (!VillagerRetaliationItems.isFilter(filter)) {
            return assignmentText("default." + defaultKey);
        }
        var policy = VillagerFilterPolicy.read(filter);
        String filterName = VillagerRetaliationItems.isRecipeFilter(filter) ? "Recipe Filter"
                : VillagerRetaliationItems.isAttributeFilter(filter) ? "Attribute Filter" : "Item Filter";
        summary.append(assignmentText("configured",
                Component.translatableWithFallback(filter.getDescriptionId(), filterName),
                Component.translatableWithFallback("item.villagerretaliation.filter_policy.mode." + policy.listMode().id(),
                        policy.listMode() == VillagerFilterPolicy.ListMode.ALLOW_MATCHING
                                ? "Allow Matching" : "Deny Matching")));
        String effect = VillagerRetaliationItems.isRecipeFilter(filter)
                ? production ? "recipe" : "recipe_unused"
                : production ? "output" : "supplies";
        summary.append(" ").append(assignmentText("effect." + effect, role == null ? "current" : role.label()));
        boolean empty = VillagerRetaliationItems.isItemFilter(filter)
                ? VillagerItemFilterData.entries(filter).stream().allMatch(ItemStack::isEmpty)
                : VillagerRetaliationItems.isAttributeFilter(filter)
                        ? VillagerAttributeFilterData.read(filter).attributes().isEmpty()
                        : VillagerRecipeFilterData.resolve(level, filter).state()
                                == VillagerRecipeFilterData.ResolutionState.EMPTY;
        if (!policy.valid()) {
            summary.append(" ").append(Component.translatableWithFallback("item.villagerretaliation.filter_policy.invalid",
                    "Invalid filter policy; matching is blocked. Edit the filter to repair it."));
        } else if (empty) {
            String emptyRule = VillagerRetaliationItems.isRecipeFilter(filter) ? "recipe"
                    : policy.listMode() == VillagerFilterPolicy.ListMode.ALLOW_MATCHING ? "allow" : "deny";
            summary.append(" ").append(assignmentText("empty." + emptyRule));
        }
        if (VillagerRetaliationItems.isRecipeFilter(filter) && !empty) {
            var resolution = VillagerRecipeFilterData.resolve(level, filter);
            if (!resolution.valid()) {
                summary.append(" ").append(assignmentText("recipe_invalid." + resolution.state().name().toLowerCase(java.util.Locale.ROOT)));
            } else if (production && !supportsRecipe(level, role, filter, resolution)) {
                summary.append(" ").append(assignmentText("recipe_incompatible", role.label()));
            }
        }
        summary.append(" ").append(assignmentText("default." + defaultKey));
        return summary.append(" ").append(assignmentText("retained"));
    }

    private static boolean supportsRecipe(ServerLevel level, HiredVillagerRole role, ItemStack filter,
            VillagerRecipeFilterData.Resolution resolution) {
        var recipe = resolution.recipe().value();
        var type = recipe.getType();
        ItemStack output = recipe.getResultItem(level.registryAccess());
        return switch (role) {
            case CRAFTSMAN -> type == RecipeType.CRAFTING;
            case COOK -> CookingWorker.isFood(output)
                    && (type == RecipeType.CRAFTING || type == RecipeType.SMELTING || type == RecipeType.SMOKING);
            case SMELTER -> SmeltingWorker.isSmeltedOre(output)
                    && (type == RecipeType.SMELTING || type == RecipeType.BLASTING)
                    && Stream.of(Items.RAW_IRON, Items.RAW_GOLD, Items.RAW_COPPER)
                            .anyMatch(item -> VillagerRecipeFilterData.matchesIngredient(level, filter, 0, new ItemStack(item)));
            default -> false;
        };
    }

    /** Notices are flattened on the server, so each mod translation also needs a readable fallback. */
    public static Component assignmentText(String key, Object... arguments) {
        String fallback = switch (key) {
            case "prompt" -> "Assign this filter with its saved settings? %s";
            case "confirmed" -> "Filter assigned without changing its settings. %s";
            case "configured" -> "%s: %s.";
            case "effect.supplies" -> "For %s, item and attribute rules restrict eligible storage withdrawals; they do not choose work targets.";
            case "effect.output" -> "For automatic %s work, rules select eligible outputs; item and attribute filters can also restrict storage withdrawals. Queued orders use their own recipe instructions.";
            case "effect.recipe" -> "For automatic %s work, this selects or excludes a recipe and its ingredient choices, within the job's supported recipes. It does not restrict supply withdrawals. Queued orders use their own recipe instructions.";
            case "effect.recipe_unused" -> "The %s job does not use recipe selection. This recipe filter is kept, but does not restrict its supply withdrawals.";
            case "empty.allow" -> "This empty Allow Matching filter admits no items.";
            case "empty.deny" -> "This empty Deny Matching filter excludes no items; normal job limits still apply.";
            case "empty.recipe" -> "An empty recipe filter selects no valid recipe and blocks automatic recipe production in either mode.";
            case "default.craftsman" -> "Without a filter, automatic crafting waits for output instructions; queued orders can still supply them.";
            case "default.cook" -> "Without a filter, automatic cooking uses eligible food recipes.";
            case "default.smelter" -> "Without a filter, automatic smelting processes raw iron, gold and copper.";
            case "default.supplies" -> "Without a filter, normal job supply rules apply.";
            case "recipe_invalid.missing_recipe" -> "The saved recipe is missing. Automatic recipe production is blocked until you select an available recipe.";
            case "recipe_invalid.unsupported_recipe" -> "The saved recipe type is unsupported. Automatic recipe production is blocked until you select a supported recipe.";
            case "recipe_invalid.invalid_alternative" -> "A saved ingredient choice is no longer valid. Automatic recipe production is blocked until you edit the recipe filter.";
            case "recipe_invalid.malformed" -> "The saved recipe configuration is invalid. Automatic recipe production is blocked until you edit the recipe filter.";
            case "recipe_incompatible" -> "The selected recipe cannot be produced by a %s. Review this recipe filter for the current job.";
            case "retained" -> "Changing jobs keeps this filter and its rules; review compatibility after a job change. Edit the filter to change its settings.";
            default -> throw new IllegalArgumentException("Unknown filter assignment text: " + key);
        };
        return Component.translatableWithFallback("villagerretaliation.gui.item_filter.assignment." + key,
                fallback, arguments);
    }

    public static boolean canAssign(ServerLevel level, Villager villager, ServerPlayer player) {
        return player != null && villager != null && !villager.isBaby() && player.isAlive() && !player.isSpectator()
                && villager.isAlive() && player.level() == level && villager.level() == level
                && (HiredVillagerContractService.isHiredBy(level, villager, player)
                    || com.jvn.villagerretaliation.settlement.SettlementWorkforceService.canManageEquipment(level, villager, player));
    }

    public record AssignmentResult(boolean assigned, boolean replaced, boolean droppedOldFilter) {
        private static final AssignmentResult REJECTED = new AssignmentResult(false, false, false);
    }
}
