package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueRequestType;
import com.jvn.villagerretaliation.network.VillagerRecruitRequestPayload;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
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
    public static void recruitmentActionsUseCanonicalMappings(GameTestHelper helper) {
        helper.assertValueEqual(
                RecruitmentActionMappings.hireDays(VillagerRecruitRequestPayload.Action.HIRE_FIFTEEN_DAYS),
                15,
                "hire duration mapping");
        helper.assertValueEqual(
                RecruitmentActionMappings.extensionDays(VillagerRecruitRequestPayload.Action.EXTEND_THIRTY_DAYS),
                30,
                "extension duration mapping");
        helper.assertValueEqual(
                RecruitmentActionMappings.role(VillagerRecruitRequestPayload.Action.SET_ROLE_COURIER),
                HiredVillagerRole.COURIER,
                "role mapping");
        helper.assertValueEqual(
                RecruitmentActionMappings.hireDays(VillagerRecruitRequestPayload.Action.SET_ROLE_COURIER),
                0,
                "non-hire action duration");
        helper.assertTrue(
                RecruitmentActionMappings.role(VillagerRecruitRequestPayload.Action.HIRE_ONE_DAY) == null,
                "non-role actions must not map to a role");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void hiredContractTransactionDebitsBeforeMutationAndRollsBackFailure(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().setItem(0, new ItemStack(Items.EMERALD, 5));

        HiredContractRequestHandler.TransactionResult success = HiredContractRequestHandler.transact(
                player,
                3,
                () -> VillagerCurrencyPayment.count(player) == 2);
        helper.assertValueEqual(
                success,
                HiredContractRequestHandler.TransactionResult.SUCCESS,
                "successful transaction result");
        helper.assertValueEqual(VillagerCurrencyPayment.count(player), 2, "successful transaction debit");

        HiredContractRequestHandler.TransactionResult rolledBack = HiredContractRequestHandler.transact(
                player,
                2,
                () -> false);
        helper.assertValueEqual(
                rolledBack,
                HiredContractRequestHandler.TransactionResult.MUTATION_FAILED,
                "failed mutation result");
        helper.assertValueEqual(VillagerCurrencyPayment.count(player), 2, "failed mutation payment rollback");

        boolean[] called = {false};
        HiredContractRequestHandler.TransactionResult insufficient = HiredContractRequestHandler.transact(
                player,
                3,
                () -> {
                    called[0] = true;
                    return true;
                });
        helper.assertValueEqual(
                insufficient,
                HiredContractRequestHandler.TransactionResult.PAYMENT_FAILED,
                "insufficient payment result");
        helper.assertFalse(called[0], "mutation must not run before payment validation succeeds");
        helper.assertValueEqual(VillagerCurrencyPayment.count(player), 2, "rejected transaction balance");
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
    public static void constructionBlueprintOpeningAlwaysProvidesDialogue(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Villager villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        DialogueContext context = VillagerInteractionService.createDialogueContext(
                helper.getLevel(), player, villager);

        String opening = VillagerInteractionService.constructionBlueprintOpening(context);

        helper.assertFalse(opening.isBlank(),
                "opening a Builder blueprint interaction must provide dialogue alongside its options");
        villager.discard();
        helper.succeed();
    }

}
