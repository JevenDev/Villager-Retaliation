package com.jvn.villagerretaliation.item;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class VillagerFilterMatcherGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    private VillagerFilterMatcherGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void sharedMatcherPreservesBuiltinDispatch(GameTestHelper helper) {
        ItemStack listFilter = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.setEntry(listFilter, 0, new ItemStack(Items.EMERALD));
        helper.assertTrue(
                VillagerFilterMatcher.matches(helper.getLevel(), listFilter, new ItemStack(Items.EMERALD)),
                "the shared matcher should preserve List Filter identity matching");
        helper.assertFalse(
                VillagerFilterMatcher.matches(helper.getLevel(), listFilter, new ItemStack(Items.DIRT)),
                "the shared matcher should preserve List Filter rejection");

        ItemStack attributeFilter = new ItemStack(VillagerRetaliationItems.ATTRIBUTE_FILTER.get());
        VillagerAttributeFilterData.setSelected(
                attributeFilter,
                new VillagerAttributeFilterData.Attribute(
                        VillagerAttributeFilterData.AttributeType.FURNACE_FUEL,
                        ""),
                false);
        helper.assertTrue(
                VillagerFilterMatcher.matches(helper.getLevel(), attributeFilter, new ItemStack(Items.STICK)),
                "the shared matcher should preserve Attribute Filter matching");
        helper.assertFalse(
                VillagerFilterMatcher.matches(helper.getLevel(), new ItemStack(Items.PAPER), new ItemStack(Items.PAPER)),
                "ordinary items must not become configured matchers");
        helper.succeed();
    }
}
