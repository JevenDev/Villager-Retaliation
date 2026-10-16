package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.work.CraftsmanWorker;
import com.jvn.villagerretaliation.interaction.work.HiredProcessingRecipeFilter;
import com.jvn.villagerretaliation.interaction.work.SmeltingWorker;
import com.jvn.villagerretaliation.item.VillagerAttributeFilterData;
import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import com.jvn.villagerretaliation.item.VillagerItemFilterData;
import com.jvn.villagerretaliation.item.VillagerRecipeFilterData;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villagerretaliation_filters")
@PrefixGameTestTemplate(false)
public final class VillagerFilterAssignmentGameTests {
    private VillagerFilterAssignmentGameTests() {
    }

    @GameTest(template = "empty")
    public static void assignmentDialogOffersAssignAndCancel(GameTestHelper helper) {
        com.jvn.villagerretaliation.interaction.VillagerInteractionRoutingGameTests
                .itemFilterHandlerOwnsStableOptionContract(helper);
    }

    @GameTest(template = "empty")
    public static void replacementConsumesOneAndReturnsPreviousFilter(GameTestHelper helper) {
        VillagerInventoryGameTests.villagerItemFilterAssignmentConsumesAndReturnsExactlyOne(helper);
    }

    @GameTest(template = "empty")
    public static void noFilterAndEmptyItemModesHaveDifferentWorkEffects(GameTestHelper helper) {
        Villager villager = villager(helper);
        var level = helper.getLevel();
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        helper.assertTrue(VillagerItemFilterService.mayWithdraw(villager, iron),
                "no filter must leave ordinary withdrawal eligibility unchanged");
        helper.assertTrue(CraftsmanWorker.targets(level, ItemStack.EMPTY).isEmpty(),
                "automatic crafting requires output instructions when no filter is assigned");
        helper.assertTrue(SmeltingWorker.isDesiredSmeltedOutput(level, ItemStack.EMPTY, iron),
                "no filter must preserve default smelted ore outputs");

        ItemStack empty = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterService.replaceFilter(villager, empty);
        helper.assertFalse(VillagerItemFilterService.mayWithdraw(villager, iron),
                "empty allow filter must block withdrawals");
        helper.assertTrue(CraftsmanWorker.targets(level, empty).isEmpty(),
                "empty allow filter must select no crafting output");
        helper.assertFalse(SmeltingWorker.isDesiredSmeltedOutput(level, empty, iron),
                "empty allow filter must block smelted output selection");

        VillagerFilterPolicy.applyChange(empty, VillagerFilterPolicy.PolicyField.LIST_MODE,
                VillagerFilterPolicy.ListMode.DENY_MATCHING.networkId());
        VillagerItemFilterService.replaceFilter(villager, empty);
        helper.assertTrue(VillagerItemFilterService.mayWithdraw(villager, iron),
                "empty deny filter must exclude no supplies");
        helper.assertFalse(CraftsmanWorker.targets(level, empty).isEmpty(),
                "empty deny filter must admit available crafting outputs");
        helper.assertTrue(SmeltingWorker.isDesiredSmeltedOutput(level, empty, iron),
                "empty deny filter must admit normal smelted outputs");
        helper.assertFalse(SmeltingWorker.isDesiredSmeltedOutput(level, empty, new ItemStack(Items.GLASS)),
                "empty deny filter must not expand the smelter's supported output types");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void assignmentPreservesDenyPolicyAndPersistsAcrossJobChange(GameTestHelper helper) {
        Villager villager = villager(helper);
        var level = helper.getLevel();
        ServerPlayer player = hire(helper, villager);
        ItemStack filter = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get(), 2);
        VillagerItemFilterData.setEntry(filter, 0, new ItemStack(Items.IRON_INGOT));
        VillagerItemFilterData.setMode(filter, VillagerItemFilterData.Mode.DENYLIST);
        VillagerFilterPolicy.setPolicy(filter, VillagerFilterPolicy.TransferDirection.PROVIDE,
                VillagerFilterPolicy.ListMode.DENY_MATCHING,
                VillagerFilterPolicy.CombinationMode.MATCH_ALL, OptionalInt.of(32));
        ItemStack expected = filter.copyWithCount(1);
        player.getInventory().setItem(player.getInventory().selected, filter);
        // A stale caller's old mode argument must never rewrite an explicitly configured filter.
        helper.assertTrue(VillagerItemFilterService.assignHeldFilter(
                player, villager, VillagerItemFilterData.Mode.ALLOWLIST).assigned(), "assignment failed");
        helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "one filter consumed");
        assertConfiguredCopy(helper, expected, VillagerItemFilterService.assignedFilter(villager));
        assertConfiguredCopy(helper, expected, player.getMainHandItem());
        helper.assertFalse(VillagerItemFilterService.mayWithdraw(villager, new ItemStack(Items.IRON_INGOT)),
                "assigned deny filter must still reject its listed item");
        helper.assertTrue(VillagerItemFilterService.mayWithdraw(villager, new ItemStack(Items.COBBLESTONE)),
                "assigned deny filter must still admit an unlisted item");

        helper.assertTrue(HiredVillagerContractService.setActiveRole(level, villager, HiredVillagerRole.COURIER),
                "courier must be available for the role-change regression");
        HiredJobInventory.clearRuntimeState(villager);
        assertConfiguredCopy(helper, expected, VillagerItemFilterService.assignedFilter(villager));
        helper.assertFalse(VillagerItemFilterService.mayWithdraw(villager, new ItemStack(Items.IRON_INGOT)),
                "saved deny rule must survive inventory reload and role change");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void assignmentPreservesLegacyAttributeInversionWithoutMigration(GameTestHelper helper) {
        Villager villager = villager(helper);
        ServerPlayer player = hire(helper, villager);
        ItemStack filter = new ItemStack(VillagerRetaliationItems.ATTRIBUTE_FILTER.get());
        VillagerAttributeFilterData.setSelected(filter,
                new VillagerAttributeFilterData.Attribute(VillagerAttributeFilterData.AttributeType.IN_TAG,
                        "minecraft:logs"), true);
        ItemStack expected = filter.copy();
        player.getInventory().setItem(player.getInventory().selected, filter);
        helper.assertTrue(VillagerItemFilterService.assignHeldFilter(player, villager).assigned(),
                "attribute assignment failed");
        ItemStack assigned = VillagerItemFilterService.assignedFilter(villager);
        assertConfiguredCopy(helper, expected, assigned);
        helper.assertFalse(VillagerFilterPolicy.hasStoredPolicy(assigned),
                "assignment must not silently migrate legacy filter policy");
        helper.assertFalse(VillagerItemFilterService.mayWithdraw(villager, new ItemStack(Items.OAK_LOG)),
                "legacy inverted attribute must still exclude matching logs");
        helper.assertTrue(VillagerItemFilterService.mayWithdraw(villager, new ItemStack(Items.DIRT)),
                "legacy inverted attribute must still admit dirt");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void recipeAssignmentKeepsRecipeDecisionSeparateFromSupplyWithdrawal(GameTestHelper helper) {
        Villager villager = villager(helper);
        ServerPlayer player = hire(helper, villager);
        var level = helper.getLevel();
        ItemStack filter = new ItemStack(VillagerRetaliationItems.RECIPE_FILTER.get());
        var recipeId = ResourceLocation.withDefaultNamespace("oak_planks");
        helper.assertTrue(VillagerRecipeFilterData.setRecipe(filter, level, recipeId), "recipe fixture missing");
        var recipe = level.getRecipeManager().byKey(recipeId).orElseThrow();
        ItemStack planks = new ItemStack(Items.OAK_PLANKS);
        VillagerFilterPolicy.setListMode(filter, VillagerFilterPolicy.ListMode.DENY_MATCHING);
        ItemStack expected = filter.copy();
        player.getInventory().setItem(player.getInventory().selected, filter);
        helper.assertTrue(VillagerItemFilterService.assignHeldFilter(player, villager).assigned(),
                "recipe assignment failed");
        ItemStack assigned = VillagerItemFilterService.assignedFilter(villager);
        assertConfiguredCopy(helper, expected, assigned);
        helper.assertFalse(HiredProcessingRecipeFilter.allowsCraftingRecipe(level, assigned, recipe, planks),
                "assigned deny recipe must reject that exact recipe");
        helper.assertTrue(VillagerItemFilterService.mayWithdraw(villager, new ItemStack(Items.OAK_LOG)),
                "recipe selection must not prohibit its input withdrawal");
        helper.assertTrue(VillagerItemFilterService.mayWithdraw(villager, planks),
                "recipe selection must not become an item withdrawal blacklist");

        filter = expected.copy();
        VillagerFilterPolicy.setListMode(filter, VillagerFilterPolicy.ListMode.ALLOW_MATCHING);
        VillagerItemFilterService.replaceFilter(villager, filter);
        helper.assertTrue(HiredProcessingRecipeFilter.allowsCraftingRecipe(level,
                        VillagerItemFilterService.assignedFilter(villager), recipe, planks),
                "intentional editor change to allow must admit the recipe");
        for (var mode : VillagerFilterPolicy.ListMode.values()) {
            ItemStack empty = new ItemStack(VillagerRetaliationItems.RECIPE_FILTER.get());
            VillagerFilterPolicy.setListMode(empty, mode);
            VillagerItemFilterService.replaceFilter(villager, empty);
            helper.assertFalse(HiredProcessingRecipeFilter.allowsCraftingRecipe(level,
                            VillagerItemFilterService.assignedFilter(villager), recipe, planks),
                    "empty recipe filters must not select production in either mode");
            helper.assertTrue(VillagerItemFilterService.mayWithdraw(villager, planks),
                    "empty recipe filters must still leave withdrawals unchanged");
        }
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void assignmentWarnsAboutRecipesUnsupportedByCurrentProductionJob(GameTestHelper helper) {
        for (HiredVillagerRole role : new HiredVillagerRole[] {
                HiredVillagerRole.CRAFTSMAN, HiredVillagerRole.COOK, HiredVillagerRole.SMELTER}) {
            Villager villager = villager(helper);
            villager.setVillagerData(villager.getVillagerData().setProfession(switch (role) {
                case COOK -> VillagerProfession.BUTCHER;
                case SMELTER -> VillagerProfession.ARMORER;
                default -> VillagerProfession.TOOLSMITH;
            }));
            hire(helper, villager, role);
            ItemStack filter = new ItemStack(VillagerRetaliationItems.RECIPE_FILTER.get());
            String recipe = role == HiredVillagerRole.CRAFTSMAN
                    ? "iron_ingot_from_smelting_raw_iron" : "oak_planks";
            helper.assertTrue(VillagerRecipeFilterData.setRecipe(filter, helper.getLevel(),
                    ResourceLocation.withDefaultNamespace(recipe)), "recipe fixture missing");
            ItemStack unchanged = filter.copy();
            String summary = VillagerItemFilterService.assignmentSummary(helper.getLevel(), villager, filter).getString();
            helper.assertTrue(summary.contains("cannot be produced"),
                    "assignment must explain recipe incompatibility for " + role);
            helper.assertFalse(summary.contains("villagerretaliation."),
                    "server-flattened notice must not display untranslated mod keys");
            assertConfiguredCopy(helper, unchanged, filter);
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void missingSavedRecipeIsPreservedAndExplainedInsteadOfRepaired(GameTestHelper helper) {
        Villager villager = villager(helper);
        ServerPlayer player = hire(helper, villager);
        ItemStack filter = new ItemStack(VillagerRetaliationItems.RECIPE_FILTER.get());
        helper.assertTrue(VillagerRecipeFilterData.setRecipe(filter, helper.getLevel(),
                ResourceLocation.withDefaultNamespace("oak_planks")), "recipe fixture missing");
        CustomData.update(DataComponents.CUSTOM_DATA, filter, tag ->
                tag.getCompound("villagerretaliation:recipe_filter").putString("Recipe", "villagerretaliation:removed_recipe"));
        ItemStack expected = filter.copy();
        player.getInventory().setItem(player.getInventory().selected, filter);
        helper.assertTrue(VillagerItemFilterService.assignHeldFilter(player, villager).assigned(),
                "assignment must retain saved configurations even when a recipe is unavailable");
        ItemStack assigned = VillagerItemFilterService.assignedFilter(villager);
        assertConfiguredCopy(helper, expected, assigned);
        helper.assertTrue(VillagerItemFilterService.assignmentSummary(helper.getLevel(), villager, assigned)
                        .getString().contains("saved recipe is missing"),
                "assignment must explain a missing recipe instead of claiming it is ready");
        var recipe = helper.getLevel().getRecipeManager().byKey(ResourceLocation.withDefaultNamespace("oak_planks"))
                .orElseThrow();
        helper.assertFalse(HiredProcessingRecipeFilter.allowsCraftingRecipe(helper.getLevel(), assigned,
                        recipe, new ItemStack(Items.OAK_PLANKS)), "missing recipe must fail closed for production");
        villager.discard();
        helper.succeed();
    }

    private static Villager villager(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 1));
        villager.setNoAi(true);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.TOOLSMITH));
        return villager;
    }

    private static ServerPlayer hire(GameTestHelper helper, Villager villager) {
        return hire(helper, villager, HiredVillagerRole.CRAFTSMAN);
    }

    private static ServerPlayer hire(GameTestHelper helper, Villager villager, HiredVillagerRole role) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getAbilities().instabuild = false;
        HiredVillagerContractService.startHireContract(helper.getLevel(), villager, player, 1, 0,
                role);
        helper.assertTrue(HiredVillagerContractService.isHiredBy(helper.getLevel(), villager, player),
                "fixture must have an active hire owned by the player");
        return player;
    }

    private static void assertConfiguredCopy(GameTestHelper helper, ItemStack expected, ItemStack actual) {
        helper.assertTrue(ItemStack.isSameItemSameComponents(expected, actual),
                "assignment must preserve every saved component");
        helper.assertValueEqual(actual.getCount(), 1, "assigned filter count");
    }
}
