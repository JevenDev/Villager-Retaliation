package com.jvn.villagerretaliation.util;

import com.google.gson.JsonParser;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class ItemPredicateIntegrationGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    private ItemPredicateIntegrationGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void dialoguePaymentsRemoveOnlyMatchingCustomStacks(GameTestHelper helper) {
        var root = JsonParser.parseString("""
                {"take_items":{"item":"minecraft:stone","count":2,
                "custom_data":{"quality":3}}}
                """).getAsJsonObject();
        VillagerInventoryItemRemoval removal =
                VillagerInventoryItemRemoval.read(root, "take_items").orElseThrow();
        var player = helper.makeMockServerPlayerInLevel();
        ItemStack plain = new ItemStack(Items.STONE, 4);
        ItemStack matching = stackWithQuality(Items.STONE.getDefaultInstance(), 3);
        matching.setCount(2);
        player.getInventory().setItem(0, plain);
        player.getInventory().setItem(1, matching);

        helper.assertTrue(removal.canRemove(player), "matching custom_data should satisfy the payment");
        helper.assertTrue(removal.remove(player), "matching custom_data should be removed");
        helper.assertValueEqual(plain.getCount(), 4, "plain stacks must not be consumed");
        helper.assertTrue(matching.isEmpty(), "the matching custom stack should be consumed");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void playerItemConditionsMatchCustomComponents(GameTestHelper helper) {
        var conditionJson = JsonParser.parseString("""
                {"player_item":"minecraft:stone","player_item_custom_data":{"quality":3}}
                """).getAsJsonObject();
        VillagerPlayerItemCondition condition = VillagerPlayerItemCondition.read(conditionJson);
        var player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, stackWithQuality(Items.STONE.getDefaultInstance(), 3));
        helper.assertTrue(condition.matches(player), "matching custom_data should satisfy a player item condition");

        player.setItemInHand(InteractionHand.MAIN_HAND, stackWithQuality(Items.STONE.getDefaultInstance(), 2));
        helper.assertFalse(condition.matches(player), "different custom_data must not satisfy the condition");
        helper.succeed();
    }

    private static ItemStack stackWithQuality(ItemStack stack, int quality) {
        CompoundTag data = new CompoundTag();
        data.putInt("quality", quality);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return stack;
    }
}
