package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueRequestType;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class VillagerInteractionRoutingGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    private VillagerInteractionRoutingGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void itemFilterHandlerOwnsStableOptionContract(GameTestHelper helper) {
        List<DialogueOptionDefinition> options = VillagerItemFilterInteractionHandler.options();
        helper.assertValueEqual(options.size(), 3, "item-filter option count");
        helper.assertValueEqual(
                options.stream().map(DialogueOptionDefinition::id).toList(),
                List.of(
                        VillagerItemFilterInteractionHandler.ALLOWLIST_OPTION_ID,
                        VillagerItemFilterInteractionHandler.DENYLIST_OPTION_ID,
                        VillagerItemFilterInteractionHandler.NEVERMIND_OPTION_ID),
                "item-filter option ids");
        helper.assertValueEqual(
                options.stream().map(DialogueOptionDefinition::order).toList(),
                List.of(0, 1, 2),
                "item-filter option order");
        helper.assertTrue(
                options.stream().allMatch(option -> option.requestType() == DialogueRequestType.QUESTION),
                "item-filter options must remain question requests");
        helper.assertTrue(
                options.stream().allMatch(option -> VillagerItemFilterInteractionHandler.handlesOption(option.id())),
                "item-filter handler must recognize every option it presents");
        helper.assertFalse(
                VillagerItemFilterInteractionHandler.handlesOption("construction_blueprint_start"),
                "item-filter handler claimed another interaction namespace");
        helper.assertFalse(
                VillagerItemFilterInteractionHandler.handlesOption(null),
                "item-filter handler claimed a missing option id");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void negativeGiftOffersPreserveThePlayersStack(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().setItem(0, new ItemStack(Items.TNT, 16));

        ItemStack rejectedOffer = VillagerGiftRequestHandler.takeOfferedStack(
                player.getInventory(),
                0,
                VillagerGiftPreferences.GiftReaction.HATED);

        helper.assertTrue(rejectedOffer.is(Items.TNT) && rejectedOffer.getCount() == 16,
                "the rejected offer should retain the stack details for reaction processing");
        helper.assertTrue(player.getInventory().getItem(0).is(Items.TNT)
                        && player.getInventory().getItem(0).getCount() == 16,
                "a hated gift must remain in the player's inventory");

        ItemStack dislikedOffer = VillagerGiftRequestHandler.takeOfferedStack(
                player.getInventory(),
                0,
                VillagerGiftPreferences.GiftReaction.DISLIKED);

        helper.assertTrue(dislikedOffer.is(Items.TNT) && dislikedOffer.getCount() == 16,
                "the disliked offer should retain the stack details for reaction processing");
        helper.assertTrue(player.getInventory().getItem(0).is(Items.TNT)
                        && player.getInventory().getItem(0).getCount() == 16,
                "a disliked gift must remain in the player's inventory");

        ItemStack acceptedOffer = VillagerGiftRequestHandler.takeOfferedStack(
                player.getInventory(),
                0,
                VillagerGiftPreferences.GiftReaction.NEUTRAL);

        helper.assertTrue(acceptedOffer.is(Items.TNT) && acceptedOffer.getCount() == 16,
                "an accepted offer should transfer the selected stack");
        helper.assertTrue(player.getInventory().getItem(0).isEmpty(),
                "neutral and positive gifts should retain the existing transfer behavior");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void partialGiftOffersTransferOnlyTheChosenAmount(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().setItem(0, new ItemStack(Items.APPLE, 16));

        ItemStack acceptedOffer = VillagerGiftRequestHandler.takeOfferedStack(
                player.getInventory(),
                0,
                5,
                VillagerGiftPreferences.GiftReaction.LIKED);

        helper.assertTrue(acceptedOffer.is(Items.APPLE) && acceptedOffer.getCount() == 5,
                "the accepted gift should contain exactly the selected amount");
        helper.assertTrue(player.getInventory().getItem(0).is(Items.APPLE)
                        && player.getInventory().getItem(0).getCount() == 11,
                "the unselected remainder should stay in the player's inventory");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void giftKnowledgeTooltipUsesItemDisplayCapitalization(GameTestHelper helper) {
        helper.assertValueEqual(
                VillagerGiftKnowledgeService.displayItemName(Items.EMERALD),
                "Emerald",
                "gift knowledge should use the item display name instead of lowercase currency dialogue text");
        helper.succeed();
    }
}
