package com.jvn.villagerretaliation.item;

import com.jvn.villagerretaliation.interaction.work.HiredProcessingRecipeFilter;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.ContainerFilterEvaluator;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Boundary contracts shared by filter editors, nested predicates, and worker/container consumers. */
@GameTestHolder("villagerretaliation_filters")
@PrefixGameTestTemplate(false)
public final class FilterSemanticsGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    private FilterSemanticsGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void emptyPredicatesNeverMatchEvenWithAllCombination(GameTestHelper helper) {
        for (Item type : List.of(VillagerRetaliationItems.ITEM_FILTER.get(),
                VillagerRetaliationItems.ATTRIBUTE_FILTER.get(), VillagerRetaliationItems.RECIPE_FILTER.get())) {
            for (VillagerFilterPolicy.CombinationMode combination : List.of(
                    VillagerFilterPolicy.CombinationMode.MATCH_ANY, VillagerFilterPolicy.CombinationMode.MATCH_ALL)) {
                for (VillagerFilterPolicy.ListMode mode : VillagerFilterPolicy.ListMode.values()) {
                    ItemStack filter = new ItemStack(type);
                    policy(filter, VillagerFilterPolicy.TransferDirection.BOTH, mode, combination, 0);
                    VillagerFilterMatcher.RawMatchResult raw = VillagerFilterMatcher.rawMatchResult(
                            helper.getLevel(), filter, new ItemStack(Items.STICK));
                    helper.assertTrue(raw.valid() && !raw.matched(),
                            "empty predicates must be valid non-matches, including Match All");
                    helper.assertValueEqual(VillagerFilterMatcher.matches(
                                    helper.getLevel(), filter, new ItemStack(Items.STICK)),
                            mode == VillagerFilterPolicy.ListMode.DENY_MATCHING,
                            "only the outer deny mode inverts an empty predicate");
                    helper.assertFalse(VillagerFilterMatcher.matches(helper.getLevel(), filter, ItemStack.EMPTY),
                            "deny mode must not turn an empty candidate into an item");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void nestedPredicatesIgnoreChildModeDirectionAndStock(GameTestHelper helper) {
        ItemStack child = itemFilter(Items.STICK);
        policy(child, VillagerFilterPolicy.TransferDirection.PROVIDE,
                VillagerFilterPolicy.ListMode.DENY_MATCHING,
                VillagerFilterPolicy.CombinationMode.MATCH_ANY, 1);
        ItemStack outer = itemFilter(Items.DIRT);
        helper.assertTrue(VillagerItemFilterData.setEntry(outer, 1, child), "nested item filter setup");
        assertMatches(helper, child, Items.STICK, false, "child deny applies when used directly");
        assertMatches(helper, outer, Items.STICK, true, "Any includes the child's raw stick predicate");
        assertMatches(helper, outer, Items.DIRT, true, "Any includes the ordinary dirt entry");
        assertMatches(helper, outer, Items.STONE, false, "Any requires one raw entry match");

        VillagerFilterPolicy.setCombinationMode(outer, VillagerFilterPolicy.CombinationMode.MATCH_ALL);
        assertMatches(helper, outer, Items.STICK, false, "All also requires the dirt identity");
        assertMatches(helper, outer, Items.DIRT, false, "All also requires the child's stick identity");
        VillagerFilterPolicy.setListMode(outer, VillagerFilterPolicy.ListMode.DENY_MATCHING);
        assertMatches(helper, outer, Items.STICK, true, "outer deny inverts the entire All expression");

        ItemStack fuel = attributeFilter(VillagerAttributeFilterData.AttributeType.FURNACE_FUEL);
        policy(fuel, VillagerFilterPolicy.TransferDirection.PROVIDE,
                VillagerFilterPolicy.ListMode.DENY_MATCHING,
                VillagerFilterPolicy.CombinationMode.MATCH_ANY, 1);
        ItemStack intersection = itemFilter(Items.STICK);
        VillagerItemFilterData.setEntry(intersection, 1, fuel);
        VillagerFilterPolicy.setCombinationMode(intersection, VillagerFilterPolicy.CombinationMode.MATCH_ALL);
        assertMatches(helper, intersection, Items.STICK, true, "All can match overlapping identity and fuel predicates");
        assertMatches(helper, intersection, Items.COAL, false, "a fuel alone does not satisfy the identity");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void attributeAnyAllAndOuterDenyUseTheWholeExpression(GameTestHelper helper) {
        ItemStack filter = attributeFilter(VillagerAttributeFilterData.AttributeType.FURNACE_FUEL);
        VillagerAttributeFilterData.toggleSelected(filter, new VillagerAttributeFilterData.Attribute(
                VillagerAttributeFilterData.AttributeType.PLACEABLE, ""));
        assertMatches(helper, filter, Items.STICK, true, "Any accepts fuel without placeability");
        assertMatches(helper, filter, Items.STONE, true, "Any accepts placeability without fuel");
        VillagerFilterPolicy.setCombinationMode(filter, VillagerFilterPolicy.CombinationMode.MATCH_ALL);
        assertMatches(helper, filter, Items.STICK, false, "All rejects fuel without placeability");
        assertMatches(helper, filter, Items.STONE, false, "All rejects placeability without fuel");
        assertMatches(helper, filter, Items.OAK_PLANKS, true, "All accepts an item with both attributes");
        VillagerFilterPolicy.setListMode(filter, VillagerFilterPolicy.ListMode.DENY_MATCHING);
        assertMatches(helper, filter, Items.OAK_PLANKS, false, "Deny rejects the full intersection");
        assertMatches(helper, filter, Items.STICK, true, "Deny permits a partial intersection match");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void containerApplicabilityAndDenyPrecedenceAreOrderIndependent(GameTestHelper helper) {
        ItemStack allow = itemFilter(Items.DIRT);
        ItemStack irrelevant = itemFilter(Items.STONE);
        VillagerFilterPolicy.setDirection(irrelevant, VillagerFilterPolicy.TransferDirection.PROVIDE);
        ItemStack deny = itemFilter(Items.DIRT);
        VillagerFilterPolicy.setListMode(deny, VillagerFilterPolicy.ListMode.DENY_MATCHING);
        for (List<ItemStack> rules : List.of(List.of(allow, deny), List.of(deny, allow))) {
            var result = evaluate(helper, rules, VillagerFilterPolicy.TransferOperation.RECEIVE, 0, 0);
            helper.assertTrue(result.valid() && !result.permitted() && result.allowance() == 0,
                    "matching deny must veto an allow in either rule order");
        }
        var noRules = evaluate(helper, List.of(), VillagerFilterPolicy.TransferOperation.RECEIVE, 0, 0);
        helper.assertTrue(noRules.permitted() && noRules.allowance() == VillagerFilterPolicy.UNLIMITED_ALLOWANCE,
                "no attached rules leave transfer unrestricted");
        var inapplicable = evaluate(helper, List.of(irrelevant),
                VillagerFilterPolicy.TransferOperation.RECEIVE, 0, 0);
        helper.assertTrue(inapplicable.permitted() && inapplicable.applicableAllowRules() == 0,
                "provide-only rules do not restrict receiving");
        var applicableMiss = evaluate(helper, List.of(irrelevant),
                VillagerFilterPolicy.TransferOperation.PROVIDE, 0, 0);
        helper.assertTrue(!applicableMiss.permitted() && applicableMiss.applicableAllowRules() == 1,
                "an applicable allow requires at least one matching allow");
        var mixed = evaluate(helper, List.of(allow, irrelevant),
                VillagerFilterPolicy.TransferOperation.RECEIVE, 0, 0);
        helper.assertTrue(mixed.permitted() && mixed.applicableAllowRules() == 1 && mixed.matchingAllowRules() == 1,
                "only direction-applicable allow rules enter the container union");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void containerStockCapsUseTightestMatchingAllowanceAtBoundaries(GameTestHelper helper) {
        ItemStack low = itemFilter(Items.DIRT);
        ItemStack high = itemFilter(Items.DIRT);
        ItemStack unlimited = itemFilter(Items.DIRT);
        for (ItemStack filter : List.of(low, high, unlimited)) {
            VillagerFilterPolicy.setDirection(filter, VillagerFilterPolicy.TransferDirection.BOTH);
        }
        VillagerFilterPolicy.setStockTarget(low, 10);
        VillagerFilterPolicy.setStockTarget(high, 20);
        ItemStack nonmatching = itemFilter(Items.STONE);
        VillagerFilterPolicy.setStockTarget(nonmatching, 1);
        for (List<ItemStack> rules : List.of(List.of(low, high, unlimited, nonmatching),
                List.of(nonmatching, unlimited, high, low))) {
            var receive = evaluate(helper, rules, VillagerFilterPolicy.TransferOperation.RECEIVE, 7, 2);
            helper.assertValueEqual(receive.allowance(), 1, "lowest matching receive target wins after reservations");
            var full = evaluate(helper, rules, VillagerFilterPolicy.TransferOperation.RECEIVE, 8, 2);
            helper.assertTrue(full.permitted() && full.allowance() == 0,
                    "a matched rule at its target has zero transferable units");
            var provide = evaluate(helper, rules, VillagerFilterPolicy.TransferOperation.PROVIDE, 25, 2);
            helper.assertValueEqual(provide.allowance(), 3, "highest matching reserve target is tightest for providing");
            var reserved = evaluate(helper, rules, VillagerFilterPolicy.TransferOperation.PROVIDE, 22, 2);
            helper.assertValueEqual(reserved.allowance(), 0, "outbound claims consume all stock above the reserve");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void legacyCombinationAndQuantitiesSurvivePersistenceWithoutReinterpretation(GameTestHelper helper) {
        ItemStack legacy = itemFilter(Items.STICK);
        VillagerItemFilterData.setAmount(legacy, 0, 3);
        VillagerItemFilterData.setEntry(legacy, 1, new ItemStack(Items.STICK));
        VillagerItemFilterData.setAmount(legacy, 1, 4);
        VillagerItemFilterData.setEntry(legacy, 2, new ItemStack(Items.DIRT));
        VillagerItemFilterData.setEntry(legacy, 3,
                attributeFilter(VillagerAttributeFilterData.AttributeType.FURNACE_FUEL));
        CompoundTag data = legacy.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        data.getCompound("villagerretaliation:item_filter").remove("EntryCombination");
        legacy.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        helper.assertValueEqual(VillagerFilterPolicy.read(legacy).combinationMode(),
                VillagerFilterPolicy.CombinationMode.LEGACY, "missing saved combination retains legacy semantics");
        assertMatches(helper, legacy, Items.STICK, true, "legacy accepts one identity plus every nested predicate");
        assertMatches(helper, legacy, Items.DIRT, false, "legacy requires nested fuel even when an identity matches");
        assertMatches(helper, legacy, Items.COAL, false, "legacy requires an identity even when nested fuel matches");
        helper.assertValueEqual(VillagerItemFilterData.amountLimit(legacy, new ItemStack(Items.STICK)), 7,
                "duplicate positive quantities add within an identity group");
        helper.assertTrue(VillagerFilterMatcher.matches(helper.getLevel(), legacy, new ItemStack(Items.STICK, 64)),
                "entry amount is a stock limit rather than a candidate stack-size predicate");
        VillagerItemFilterData.setAmount(legacy, 1, 0);
        helper.assertValueEqual(VillagerItemFilterData.amount(legacy, 1), 1,
                "duplicate identity entries clamp to a positive minimum");
        VillagerItemFilterData.setAmount(legacy, 1, 4);
        VillagerFilterPolicy.setDirection(legacy, VillagerFilterPolicy.TransferDirection.BOTH);
        helper.assertTrue(VillagerFilterPolicy.read(legacy).valid(),
                "an unrelated direction edit must not write a policy that its own reader rejects");
        VillagerFilterPolicy.setStockTarget(legacy, 30);
        VillagerFilterPolicy.applyChange(legacy, VillagerFilterPolicy.PolicyField.LIST_MODE,
                VillagerFilterPolicy.ListMode.DENY_MATCHING.networkId());
        assertMatches(helper, legacy, Items.STICK, false, "mode edit negates the retained legacy expression");
        assertMatches(helper, legacy, Items.DIRT, true, "mode edit preserves nested requirements before negation");
        VillagerFilterPolicy.applyChange(legacy, VillagerFilterPolicy.PolicyField.LIST_MODE,
                VillagerFilterPolicy.ListMode.ALLOW_MATCHING.networkId());
        ItemStack restored = roundTrip(helper, legacy);
        ItemStack copied = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.copyConfiguration(restored, copied);
        for (ItemStack filter : List.of(restored, copied)) {
            helper.assertValueEqual(VillagerFilterPolicy.read(filter), VillagerFilterPolicy.read(legacy),
                    "policy edits, persistence, and copies retain the legacy combination");
            helper.assertValueEqual(VillagerItemFilterData.amountLimit(filter, new ItemStack(Items.STICK)), 7,
                    "entry quantities survive separately from the shared stock target");
            assertMatches(helper, filter, Items.STICK, true, "restored legacy positive boundary");
            assertMatches(helper, filter, Items.DIRT, false, "restored legacy negative boundary");
            helper.assertValueEqual(evaluate(helper, List.of(filter),
                    VillagerFilterPolicy.TransferOperation.RECEIVE, 0, 0).allowance(), 0,
                    "a matching identity alone cannot bypass a legacy nested predicate");
        }
        VillagerFilterPolicy.applyChange(copied, VillagerFilterPolicy.PolicyField.COMBINATION,
                VillagerFilterPolicy.CombinationMode.MATCH_ANY.networkId());
        helper.assertValueEqual(VillagerFilterPolicy.read(copied).stockTarget().orElse(-1), 30,
                "explicitly replacing legacy combination must retain the stock target");
        helper.assertValueEqual(VillagerFilterPolicy.read(copied).direction(),
                VillagerFilterPolicy.TransferDirection.BOTH,
                "explicitly replacing legacy combination must retain direction");
        assertMatches(helper, copied, Items.DIRT, true, "an explicit Any edit replaces the legacy intersection");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void recipeIdentityIsWorkerSpecificWhileContainerMatchingUsesOutput(GameTestHelper helper) {
        List<RecipeHolder<?>> recipes = VillagerRecipeFilterData.catalog(helper.getLevel());
        RecipeHolder<?> selected = recipes.stream().filter(holder -> holder.value().getType() == RecipeType.SMELTING)
                .filter(holder -> recipes.stream().anyMatch(other -> !other.id().equals(holder.id())
                        && ItemStack.isSameItemSameComponents(result(helper, holder), result(helper, other))))
                .findFirst().orElseThrow();
        RecipeHolder<?> alternate = recipes.stream().filter(holder -> !holder.id().equals(selected.id())
                && ItemStack.isSameItemSameComponents(result(helper, selected), result(helper, holder)))
                .findFirst().orElseThrow();
        ItemStack output = result(helper, selected);
        ItemStack filter = new ItemStack(VillagerRetaliationItems.RECIPE_FILTER.get());
        VillagerRecipeFilterData.setRecipe(filter, helper.getLevel(), selected.id());
        policy(filter, VillagerFilterPolicy.TransferDirection.PROVIDE,
                VillagerFilterPolicy.ListMode.ALLOW_MATCHING,
                VillagerFilterPolicy.CombinationMode.MATCH_ALL, 1);
        helper.assertTrue(HiredProcessingRecipeFilter.allows(helper.getLevel(), filter, selected,
                        ItemStack.EMPTY, output), "worker allows the selected exact recipe despite container direction/stock");
        helper.assertFalse(HiredProcessingRecipeFilter.allows(helper.getLevel(), filter, alternate,
                        ItemStack.EMPTY, output), "worker rejects another recipe with the identical output");
        helper.assertTrue(VillagerFilterMatcher.matches(helper.getLevel(), filter, output.copyWithCount(64)),
                "generic output matching ignores provenance and candidate count");
        var container = ContainerFilterEvaluator.evaluate(helper.getLevel(), List.of(filter), output,
                VillagerFilterPolicy.TransferOperation.PROVIDE,
                (rule, policy, candidate, operation) -> new ContainerFilterEvaluator.StockState(3, 1));
        helper.assertTrue(container.permitted() && container.allowance() == 1,
                "containers match the output and apply their own reserve and claims");
        VillagerFilterPolicy.setListMode(filter, VillagerFilterPolicy.ListMode.DENY_MATCHING);
        helper.assertFalse(HiredProcessingRecipeFilter.allowsCraftingRecipe(
                helper.getLevel(), filter, selected, output), "worker deny rejects the selected recipe identity");
        helper.assertTrue(HiredProcessingRecipeFilter.allowsCraftingRecipe(
                helper.getLevel(), filter, alternate, output), "worker deny allows another recipe with the same output");
        helper.assertFalse(VillagerFilterMatcher.matches(helper.getLevel(), filter, output),
                "output deny still rejects that same output regardless of provenance");
        ItemStack emptyRecipe = new ItemStack(VillagerRetaliationItems.RECIPE_FILTER.get());
        VillagerFilterPolicy.setListMode(emptyRecipe, VillagerFilterPolicy.ListMode.DENY_MATCHING);
        helper.assertFalse(HiredProcessingRecipeFilter.allows(
                helper.getLevel(), emptyRecipe, selected, ItemStack.EMPTY, output),
                "an unconfigured recipe cannot authorize worker processing, including in deny mode");
        helper.assertTrue(HiredProcessingRecipeFilter.allows(
                helper.getLevel(), ItemStack.EMPTY, selected, ItemStack.EMPTY, output),
                "no worker filter leaves recipe selection unrestricted");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void narrowedRecipeInputPersistsButDoesNotNarrowOutputMatching(GameTestHelper helper) {
        RecipeHolder<?> recipe = VillagerRecipeFilterData.catalog(helper.getLevel()).stream()
                .filter(holder -> holder.value().getType() == RecipeType.SMELTING)
                .filter(holder -> holder.value().getIngredients().size() == 1
                        && VillagerRecipeFilterData.ingredientChoices(holder.value().getIngredients().getFirst()).size() > 1)
                .findFirst().orElseThrow();
        List<ItemStack> choices = VillagerRecipeFilterData.ingredientChoices(recipe.value().getIngredients().getFirst());
        ItemStack filter = new ItemStack(VillagerRetaliationItems.RECIPE_FILTER.get());
        VillagerRecipeFilterData.setRecipe(filter, helper.getLevel(), recipe.id());
        helper.assertTrue(VillagerRecipeFilterData.setIngredient(filter, helper.getLevel(), 0,
                BuiltInRegistries.ITEM.getKey(choices.getFirst().getItem())), "narrow a processing ingredient");
        ItemStack restored = roundTrip(helper, filter);
        ItemStack output = result(helper, recipe);
        helper.assertTrue(HiredProcessingRecipeFilter.allows(helper.getLevel(), restored, recipe,
                choices.getFirst(), output), "saved chosen processing input is accepted");
        helper.assertFalse(HiredProcessingRecipeFilter.allows(helper.getLevel(), restored, recipe,
                choices.get(1), output), "another valid recipe ingredient is excluded by narrowing");
        helper.assertTrue(VillagerFilterMatcher.matches(helper.getLevel(), restored, output),
                "ingredient narrowing does not impose recipe provenance on an output stack");
        VillagerFilterPolicy.setListMode(restored, VillagerFilterPolicy.ListMode.DENY_MATCHING);
        helper.assertFalse(HiredProcessingRecipeFilter.allows(helper.getLevel(), restored, recipe,
                choices.getFirst(), output), "deny inverts exact recipe plus narrowed input");
        helper.assertTrue(HiredProcessingRecipeFilter.allows(helper.getLevel(), restored, recipe,
                choices.get(1), output), "deny allows a nonselected input for the same recipe");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void malformedNestedRuleCannotBeHiddenByAnyOrDeny(GameTestHelper helper) {
        ItemStack malformed = itemFilter(Items.STONE);
        CompoundTag data = malformed.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        data.putString("villagerretaliation:filter_policy", "not a policy");
        malformed.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        ItemStack outer = itemFilter(Items.DIRT);
        helper.assertTrue(VillagerItemFilterData.setEntry(outer, 1, malformed), "malformed nested fixture setup");
        for (VillagerFilterPolicy.ListMode mode : VillagerFilterPolicy.ListMode.values()) {
            VillagerFilterPolicy.setListMode(outer, mode);
            var raw = VillagerFilterMatcher.rawMatchResult(helper.getLevel(), outer, new ItemStack(Items.DIRT));
            helper.assertFalse(raw.valid(), "a matching Any branch must not hide an invalid nested branch");
            assertMatches(helper, outer, Items.DIRT, false, "invalid graphs fail closed even with outer deny");
            var evaluation = evaluate(helper, List.of(outer), VillagerFilterPolicy.TransferOperation.RECEIVE, 0, 0);
            helper.assertTrue(!evaluation.valid() && !evaluation.permitted(),
                    "container evaluation must retain malformed-graph failure");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void savedLegacyPolicyIsItemOnlyAndCannotBeNewlySelected(GameTestHelper helper) {
        for (Item type : List.of(VillagerRetaliationItems.ITEM_FILTER.get(),
                VillagerRetaliationItems.ATTRIBUTE_FILTER.get(), VillagerRetaliationItems.RECIPE_FILTER.get())) {
            ItemStack filter = new ItemStack(type);
            helper.assertFalse(VillagerFilterPolicy.setCombinationMode(filter,
                    VillagerFilterPolicy.CombinationMode.LEGACY), "legacy cannot be selected as a new editor mode");
            policy(filter, VillagerFilterPolicy.TransferDirection.BOTH,
                    VillagerFilterPolicy.ListMode.ALLOW_MATCHING,
                    VillagerFilterPolicy.CombinationMode.MATCH_ANY, 12);
            CompoundTag data = filter.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            data.getCompound("villagerretaliation:filter_policy").putString("Combination", "legacy");
            filter.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
            helper.assertValueEqual(VillagerFilterPolicy.read(filter).valid(),
                    type == VillagerRetaliationItems.ITEM_FILTER.get(),
                    "only item filters have a saved legacy predicate expression");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void matcherEditsPreserveLegacyContainerEntryLimits(GameTestHelper helper) {
        SimpleContainer container = new SimpleContainer(new ItemStack(Items.STICK, 2));
        for (VillagerItemFilterData.EntryCombination original : VillagerItemFilterData.EntryCombination.values()) {
            ItemStack filter = itemFilter(Items.STICK);
            VillagerItemFilterData.setAmount(filter, 0, 3);
            VillagerItemFilterData.setEntry(filter, 1, new ItemStack(Items.STICK));
            VillagerItemFilterData.setAmount(filter, 1, 4);
            VillagerItemFilterData.setEntry(filter, 2,
                    attributeFilter(VillagerAttributeFilterData.AttributeType.FURNACE_FUEL));
            if (original == VillagerItemFilterData.EntryCombination.LEGACY) {
                CompoundTag data = filter.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                data.getCompound("villagerretaliation:item_filter").remove("EntryCombination");
                filter.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
            } else {
                VillagerItemFilterData.setEntryCombination(filter, original);
            }
            helper.assertValueEqual(legacyStockAllowance(helper, filter, container), 4,
                    "entry total seven minus two stored and one reserved leaves four");
            helper.assertTrue(VillagerFilterPolicy.applyChange(filter, VillagerFilterPolicy.PolicyField.LIST_MODE,
                    VillagerFilterPolicy.ListMode.DENY_MATCHING.networkId()), "edit legacy mode to deny");
            helper.assertFalse(VillagerFilterPolicy.hasStoredPolicy(filter),
                    "mode editing must not implicitly disable legacy quantity handling");
            helper.assertValueEqual(legacyStockAllowance(helper, filter, container), 0,
                    "deny mode blocks the configured item");
            helper.assertTrue(VillagerFilterPolicy.applyChange(filter, VillagerFilterPolicy.PolicyField.LIST_MODE,
                    VillagerFilterPolicy.ListMode.ALLOW_MATCHING.networkId()), "restore legacy allow mode");
            helper.assertValueEqual(legacyStockAllowance(helper, filter, container), 4,
                    "restoring allow restores the original live stock limit");
            for (VillagerFilterPolicy.CombinationMode combination : List.of(
                    VillagerFilterPolicy.CombinationMode.MATCH_ALL, VillagerFilterPolicy.CombinationMode.MATCH_ANY)) {
                VillagerFilterPolicy.applyChange(filter, VillagerFilterPolicy.PolicyField.COMBINATION,
                        combination.networkId());
                helper.assertFalse(VillagerFilterPolicy.hasStoredPolicy(filter),
                        "combination editing must keep legacy entry amounts active");
                helper.assertValueEqual(legacyStockAllowance(helper, filter, container), 4,
                        "Any and All preserve quantity handling for overlapping predicates");
            }
            ItemStack restored = roundTrip(helper, filter);
            ItemStack copied = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
            VillagerItemFilterData.copyConfiguration(restored, copied);
            helper.assertValueEqual(legacyStockAllowance(helper, copied, container), 4,
                    "edited quantities remain enforced after persistence and copying");
            container.setItem(0, new ItemStack(Items.STICK, 6));
            helper.assertValueEqual(legacyStockAllowance(helper, copied, container), 0,
                    "six stored plus one reserved reaches the seven-item cap");
            container.setItem(0, new ItemStack(Items.STICK, 2));
            VillagerFilterPolicy.applyChange(copied, VillagerFilterPolicy.PolicyField.DIRECTION,
                    VillagerFilterPolicy.TransferDirection.BOTH.networkId());
            helper.assertTrue(VillagerFilterPolicy.hasStoredPolicy(copied),
                    "container-direction editing intentionally authors the shared policy");
            helper.assertValueEqual(legacyStockAllowance(helper, copied, container),
                    VillagerFilterPolicy.UNLIMITED_ALLOWANCE,
                    "explicit container policy uses its shared target instead of legacy entry amounts");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void configuredAttributeModeEditsRetainLegacyInversion(GameTestHelper helper) {
        ItemStack filter = attributeFilter(VillagerAttributeFilterData.AttributeType.FURNACE_FUEL);
        for (VillagerFilterPolicy.ListMode mode : List.of(
                VillagerFilterPolicy.ListMode.DENY_MATCHING, VillagerFilterPolicy.ListMode.ALLOW_MATCHING)) {
            helper.assertTrue(VillagerFilterPolicy.applyChange(filter, VillagerFilterPolicy.PolicyField.LIST_MODE,
                    mode.networkId()), "legacy attribute mode edit");
            helper.assertFalse(VillagerFilterPolicy.hasStoredPolicy(filter),
                    "configured attribute inversion needs no explicit transfer policy");
            helper.assertValueEqual(VillagerAttributeFilterData.read(filter).inverted(),
                    mode == VillagerFilterPolicy.ListMode.DENY_MATCHING, "attribute inversion follows the editor mode");
            assertMatches(helper, roundTrip(helper, filter), Items.STICK,
                    mode == VillagerFilterPolicy.ListMode.ALLOW_MATCHING, "saved inversion has the selected semantics");
        }
        ItemStack empty = new ItemStack(VillagerRetaliationItems.ATTRIBUTE_FILTER.get());
        helper.assertTrue(VillagerFilterPolicy.applyChange(empty, VillagerFilterPolicy.PolicyField.LIST_MODE,
                VillagerFilterPolicy.ListMode.DENY_MATCHING.networkId()), "empty attribute can still select deny");
        assertMatches(helper, empty, Items.STICK, true, "empty attribute deny still allows nonempty candidates");
        helper.succeed();
    }

    private static int legacyStockAllowance(GameTestHelper helper, ItemStack filter, SimpleContainer container) {
        return AssignedStorageService.observedTransferAllowance(helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 1, 1)), container, List.of(filter), new ItemStack(Items.STICK),
                VillagerFilterPolicy.TransferOperation.RECEIVE, List.of(new ItemStack(Items.STICK)));
    }

    private static ItemStack itemFilter(Item item) {
        ItemStack filter = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.setEntry(filter, 0, new ItemStack(item));
        return filter;
    }

    private static ItemStack attributeFilter(VillagerAttributeFilterData.AttributeType type) {
        ItemStack filter = new ItemStack(VillagerRetaliationItems.ATTRIBUTE_FILTER.get());
        VillagerAttributeFilterData.setSelected(filter, new VillagerAttributeFilterData.Attribute(type, ""), false);
        return filter;
    }

    private static void policy(ItemStack filter, VillagerFilterPolicy.TransferDirection direction,
            VillagerFilterPolicy.ListMode mode, VillagerFilterPolicy.CombinationMode combination, int target) {
        VillagerFilterPolicy.setPolicy(filter, direction, mode, combination,
                target > 0 ? OptionalInt.of(target) : OptionalInt.empty());
    }

    private static void assertMatches(GameTestHelper helper, ItemStack filter, Item item,
            boolean expected, String message) {
        helper.assertValueEqual(VillagerFilterMatcher.matches(helper.getLevel(), filter, new ItemStack(item)),
                expected, message);
    }

    private static ContainerFilterEvaluator.Evaluation evaluate(GameTestHelper helper, List<ItemStack> rules,
            VillagerFilterPolicy.TransferOperation operation, int stock, int claims) {
        return ContainerFilterEvaluator.evaluate(helper.getLevel(), rules, new ItemStack(Items.DIRT), operation,
                (rule, policy, candidate, transfer) -> new ContainerFilterEvaluator.StockState(stock, claims));
    }

    private static ItemStack roundTrip(GameTestHelper helper, ItemStack filter) {
        return ItemStack.parseOptional(helper.getLevel().registryAccess(),
                (CompoundTag) filter.saveOptional(helper.getLevel().registryAccess()));
    }

    private static ItemStack result(GameTestHelper helper, RecipeHolder<?> recipe) {
        return recipe.value().getResultItem(helper.getLevel().registryAccess());
    }
}
