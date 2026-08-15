package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import com.jvn.villagerretaliation.item.VillagerItemFilterData;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class ContainerFilterEvaluatorGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    private ContainerFilterEvaluatorGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void framedRuleSetsComposeDeterministically(GameTestHelper helper) {
        ItemStack dirtUnlimited = configuredRule(
                Items.DIRT,
                VillagerFilterPolicy.TransferDirection.RECEIVE,
                VillagerFilterPolicy.ListMode.ALLOW_MATCHING,
                0);
        ItemStack dirtTarget64 = configuredRule(
                Items.DIRT,
                VillagerFilterPolicy.TransferDirection.RECEIVE,
                VillagerFilterPolicy.ListMode.ALLOW_MATCHING,
                64);
        ItemStack dirtTarget32 = configuredRule(
                Items.DIRT,
                VillagerFilterPolicy.TransferDirection.RECEIVE,
                VillagerFilterPolicy.ListMode.ALLOW_MATCHING,
                32);
        ItemStack stoneAllow = configuredRule(
                Items.STONE,
                VillagerFilterPolicy.TransferDirection.RECEIVE,
                VillagerFilterPolicy.ListMode.ALLOW_MATCHING,
                0);
        ItemStack dirtDeny = configuredRule(
                Items.DIRT,
                VillagerFilterPolicy.TransferDirection.RECEIVE,
                VillagerFilterPolicy.ListMode.DENY_MATCHING,
                0);
        ItemStack stoneDeny = configuredRule(
                Items.STONE,
                VillagerFilterPolicy.TransferDirection.RECEIVE,
                VillagerFilterPolicy.ListMode.DENY_MATCHING,
                0);
        ItemStack provideOnlyDirt = configuredRule(
                Items.DIRT,
                VillagerFilterPolicy.TransferDirection.PROVIDE,
                VillagerFilterPolicy.ListMode.ALLOW_MATCHING,
                0);
        ItemStack emptyAllow = configuredRule(
                null,
                VillagerFilterPolicy.TransferDirection.RECEIVE,
                VillagerFilterPolicy.ListMode.ALLOW_MATCHING,
                0);
        ItemStack emptyDeny = configuredRule(
                null,
                VillagerFilterPolicy.TransferDirection.RECEIVE,
                VillagerFilterPolicy.ListMode.DENY_MATCHING,
                0);
        ItemStack malformed = dirtDeny.copy();
        CompoundTag malformedData = malformed.getOrDefault(
                DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        malformedData.putString("villagerretaliation:filter_policy", "invalid");
        malformed.set(DataComponents.CUSTOM_DATA, CustomData.of(malformedData));

        ItemStack dirt = new ItemStack(Items.DIRT);
        ItemStack stone = new ItemStack(Items.STONE);
        List<EvaluationCase> cases = List.of(
                new EvaluationCase("unfiltered", List.of(), dirt,
                        VillagerFilterPolicy.TransferOperation.RECEIVE, true,
                        VillagerFilterPolicy.UNLIMITED_ALLOWANCE, true),
                new EvaluationCase("matching unlimited allow", List.of(dirtUnlimited), dirt,
                        VillagerFilterPolicy.TransferOperation.RECEIVE, true,
                        VillagerFilterPolicy.UNLIMITED_ALLOWANCE, true),
                new EvaluationCase("allow miss", List.of(stoneAllow), dirt,
                        VillagerFilterPolicy.TransferOperation.RECEIVE, false, 0, true),
                new EvaluationCase("only non-matching deny", List.of(stoneDeny), dirt,
                        VillagerFilterPolicy.TransferOperation.RECEIVE, true,
                        VillagerFilterPolicy.UNLIMITED_ALLOWANCE, true),
                new EvaluationCase("deny veto", List.of(dirtUnlimited, dirtDeny), dirt,
                        VillagerFilterPolicy.TransferOperation.RECEIVE, false, 0, true),
                new EvaluationCase("non-matching deny does not veto", List.of(dirtUnlimited, stoneDeny), dirt,
                        VillagerFilterPolicy.TransferOperation.RECEIVE, true,
                        VillagerFilterPolicy.UNLIMITED_ALLOWANCE, true),
                new EvaluationCase("direction ignores inapplicable allow", List.of(provideOnlyDirt), stone,
                        VillagerFilterPolicy.TransferOperation.RECEIVE, true,
                        VillagerFilterPolicy.UNLIMITED_ALLOWANCE, true),
                new EvaluationCase("empty allow matches nothing", List.of(emptyAllow), dirt,
                        VillagerFilterPolicy.TransferOperation.RECEIVE, false, 0, true),
                new EvaluationCase("empty deny denies nothing", List.of(emptyDeny), dirt,
                        VillagerFilterPolicy.TransferOperation.RECEIVE, true,
                        VillagerFilterPolicy.UNLIMITED_ALLOWANCE, true),
                new EvaluationCase("finite beats unlimited", List.of(dirtUnlimited, dirtTarget64), dirt,
                        VillagerFilterPolicy.TransferOperation.RECEIVE, true, 50, true),
                new EvaluationCase("most restrictive finite", List.of(dirtTarget64, dirtTarget32), dirt,
                        VillagerFilterPolicy.TransferOperation.RECEIVE, true, 18, true),
                new EvaluationCase("legacy exact route matches", List.of(new ItemStack(Items.DIRT)), dirt,
                        VillagerFilterPolicy.TransferOperation.RECEIVE, true,
                        VillagerFilterPolicy.UNLIMITED_ALLOWANCE, true),
                new EvaluationCase("legacy exact route misses", List.of(new ItemStack(Items.DIAMOND)), dirt,
                        VillagerFilterPolicy.TransferOperation.RECEIVE, false, 0, true),
                new EvaluationCase("malformed configured rule", List.of(malformed), dirt,
                        VillagerFilterPolicy.TransferOperation.RECEIVE, false, 0, false));

        for (EvaluationCase testCase : cases) {
            ContainerFilterEvaluator.Evaluation evaluation = ContainerFilterEvaluator.evaluate(
                    helper.getLevel(),
                    testCase.rules(),
                    testCase.candidate(),
                    testCase.operation(),
                    (rule, policy, candidate, operation) ->
                            new ContainerFilterEvaluator.StockState(10, 4));
            helper.assertValueEqual(evaluation.permitted(), testCase.permitted(),
                    testCase.name() + " permission");
            helper.assertValueEqual(evaluation.allowance(), testCase.allowance(),
                    testCase.name() + " allowance");
            helper.assertValueEqual(evaluation.valid(), testCase.valid(),
                    testCase.name() + " validity");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void denyRulesAreBooleanAndNeverReadStock(GameTestHelper helper) {
        ItemStack denyWithTarget = configuredRule(
                Items.DIRT,
                VillagerFilterPolicy.TransferDirection.BOTH,
                VillagerFilterPolicy.ListMode.DENY_MATCHING,
                1);
        int[] stockReads = {0};
        ContainerFilterEvaluator.Evaluation evaluation = ContainerFilterEvaluator.evaluate(
                helper.getLevel(),
                List.of(denyWithTarget),
                new ItemStack(Items.DIRT),
                VillagerFilterPolicy.TransferOperation.PROVIDE,
                (rule, policy, candidate, operation) -> {
                    stockReads[0]++;
                    return new ContainerFilterEvaluator.StockState(1000, 1000);
                });
        helper.assertFalse(evaluation.permitted(), "matching deny should veto the transfer");
        helper.assertValueEqual(stockReads[0], 0, "deny rules must not evaluate stock targets");
        helper.succeed();
    }

    private static ItemStack configuredRule(
            Item item,
            VillagerFilterPolicy.TransferDirection direction,
            VillagerFilterPolicy.ListMode mode,
            int target) {
        ItemStack filter = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        if (item != null) {
            VillagerItemFilterData.setEntry(filter, 0, new ItemStack(item));
        }
        VillagerFilterPolicy.setPolicy(
                filter,
                direction,
                mode,
                VillagerFilterPolicy.CombinationMode.MATCH_ANY,
                target > 0 ? OptionalInt.of(target) : OptionalInt.empty());
        return filter;
    }

    private record EvaluationCase(
            String name,
            List<ItemStack> rules,
            ItemStack candidate,
            VillagerFilterPolicy.TransferOperation operation,
            boolean permitted,
            int allowance,
            boolean valid) {
    }
}
