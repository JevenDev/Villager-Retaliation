package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.network.RecruitmentResultPayload.FailureReason;
import com.jvn.villagerretaliation.network.VillagerRecruitRequestPayload.Action;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("narrative_interaction")
@PrefixGameTestTemplate(false)
public final class InteractionReliabilityGameTests {
    private InteractionReliabilityGameTests() { }

    @GameTest(template = "empty", batch = "interaction_reliability")
    public static void staleCloseRequestsPreserveReplacementVillagerSessions(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var first = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        var second = helper.spawn(EntityType.VILLAGER, 2, 1, 1);
        first.setNoAi(true);
        second.setNoAi(true);
        player.teleportTo(first.getX(), first.getY(), first.getZ());
        try {
            helper.assertTrue(VillagerConversationService.start(player, first), "first conversation starts");
            UUID original = VillagerConversationService.sessionId(player, first);
            helper.assertTrue(VillagerConversationService.start(player, second), "second conversation starts");
            UUID replacement = VillagerConversationService.sessionId(player, second);
            VillagerInteractionService.handleConversationEndRequest(player, first.getId(), original);
            VillagerInteractionService.handleConversationEndRequest(player, first.getId());
            VillagerInteractionService.handleConversationEndRequest(player, Integer.MAX_VALUE, replacement);
            helper.assertValueEqual(VillagerConversationService.sessionId(player, second), replacement,
                    "old or missing actors cannot close the replacement conversation");
            helper.assertTrue(VillagerConversationService.start(player, second), "same villager can reopen");
            UUID reopened = VillagerConversationService.sessionId(player, second);
            VillagerInteractionService.handleConversationEndRequest(player, second.getId(), replacement);
            helper.assertValueEqual(VillagerConversationService.sessionId(player, second), reopened,
                    "old identity cannot close a reopened conversation with the same villager");
            VillagerInteractionService.handleConversationEndRequest(player, second.getId(), reopened);
            helper.assertFalse(VillagerConversationService.isConversing(player), "current close request ends its conversation");
        } finally {
            VillagerConversationService.endForPlayer(player, false);
            first.discard();
            second.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "interaction_reliability")
    public static void dialogueRequestHistoryIsBoundedAndOwnedByItsSession(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        villager.setNoAi(true);
        player.teleportTo(villager.getX(), villager.getY(), villager.getZ());
        try {
            helper.assertTrue(VillagerConversationService.start(player, villager), "conversation starts");
            var session = InteractionSessions.current(player).orElseThrow();
            UUID first = UUID.randomUUID();
            helper.assertTrue(session.acceptDialogueRequest(first), "new request is accepted");
            helper.assertFalse(session.acceptDialogueRequest(first), "repeated request is rejected");
            UUID recent = null;
            for (int index = 0; index < 512; index++) {
                recent = UUID.randomUUID();
                helper.assertTrue(session.acceptDialogueRequest(recent), "new requests remain available in a long conversation");
            }
            helper.assertFalse(session.acceptDialogueRequest(recent), "recent duplicates remain rejected");
            helper.assertTrue(session.acceptDialogueRequest(first), "old requests leave the bounded history");
            helper.assertTrue(VillagerConversationService.start(player, villager), "replacement conversation starts");
            helper.assertFalse(session.acceptDialogueRequest(UUID.randomUUID()), "retired sessions reject new activity");
            helper.assertTrue(InteractionSessions.current(player).orElseThrow().acceptDialogueRequest(first),
                    "a new session has its own request history");
        } finally {
            VillagerConversationService.endForPlayer(player, false);
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "interaction_reliability")
    public static void giftCapacityUsesTheSameComponentsAsStorage(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        villager.setNoAi(true);
        try {
            var full = new ArrayList<ItemStack>();
            for (int index = 0; index < VillagerInventoryAccess.captureFullInventory(villager).size(); index++) {
                full.add(new ItemStack(Items.COBBLESTONE, 64));
            }
            ItemStack offered = new ItemStack(Items.WHEAT, 4);
            ItemStack prepared = VillagerGiftKeepsakes.prepareGift(player, villager, offered);
            helper.assertFalse(ItemStack.isSameItemSameComponents(offered, prepared), "preparation only changes the copied stack");
            full.set(0, VillagerGiftKeepsakes.prepareGift(player, villager, new ItemStack(Items.WHEAT, 60)));
            VillagerInventoryAccess.replaceFullInventory(villager, full);
            helper.assertFalse(VillagerInventoryAccess.canAddItems(villager, List.of(offered)), "unmarked preview would incorrectly reject a matching gift");
            helper.assertTrue(VillagerInventoryAccess.canAddItems(villager, List.of(prepared)), "prepared gift fits the existing tracked stack");
            VillagerGiftKeepsakes.storeGift(helper.getLevel(), villager, player, offered, ResolvedGiftPreference.neutral(), 0);
            helper.assertValueEqual(VillagerInventoryAccess.captureFullInventory(villager).getFirst().getCount(), 64,
                    "the accepted gift merges into the existing stack");
            full.set(0, new ItemStack(Items.WHEAT, 60));
            VillagerInventoryAccess.replaceFullInventory(villager, full);
            helper.assertTrue(VillagerInventoryAccess.canAddItems(villager, List.of(offered)), "unmarked preview would incorrectly accept an untracked stack");
            helper.assertFalse(VillagerInventoryAccess.canAddItems(villager, List.of(prepared)),
                    "gift tracking prevents merging into ordinary items when storage is full");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "interaction_reliability")
    public static void recruitmentResultsReflectCommandFailures(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        villager.setNoAi(true);
        player.teleportTo(villager.getX(), villager.getY(), villager.getZ());
        try {
            helper.assertTrue(HiredVillagerContractService.startHireContract(helper.getLevel(), villager, player, 1, 1),
                    "test contract starts");
            helper.assertTrue(VillagerConversationService.start(player, villager), "conversation starts");
            long revision = HiredVillagerContractService.synchronizeAssignment(helper.getLevel(), villager).revision();
            var extension = VillagerInteractionService.recruitmentResult(player, villager.getId(), Action.EXTEND_ONE_DAY, null, revision);
            helper.assertFalse(extension.success(), "unpaid extension must not report success");
            helper.assertValueEqual(extension.failureReason(), FailureReason.INSUFFICIENT_PAYMENT, "extension rejection identifies payment");
            helper.assertValueEqual(HiredVillagerContractService.getRemainingHireDays(helper.getLevel(), villager), 1,
                    "failed extension leaves contract unchanged");
            var role = HiredVillagerContractService.activeRole(helper.getLevel(), villager);
            var wrongRoleAction = role == HiredVillagerRole.BREWING ? Action.CONFIGURE_MINING : Action.STOP_BREWING;
            var work = VillagerInteractionService.recruitmentResult(player, villager.getId(), wrongRoleAction, null, revision);
            helper.assertFalse(work.success(), "wrong-role work command must not report success");
            helper.assertValueEqual(work.failureReason(), FailureReason.INVALID_ROLE, "work rejection identifies role");
            var storage = VillagerInteractionService.recruitmentResult(player, villager.getId(), Action.TOGGLE_AUTO_PAYMENT, null, revision);
            helper.assertFalse(storage.success(), "auto payment needs assigned storage");
            helper.assertValueEqual(storage.failureReason(), FailureReason.INVALID_TRANSITION, "storage rejection is explicit");
            var state = HiredWorkStateStore.state(villager);
            HiredWorkStateStore.initializeDefaults(state, villager);
            boolean enabled = state.getBoolean("Enabled");
            var accepted = VillagerInteractionService.recruitmentResult(player, villager.getId(), Action.TOGGLE_WORK_ENABLED, null, revision);
            helper.assertTrue(accepted.success(), "authorized work toggle succeeds");
            helper.assertValueEqual(accepted.failureReason(), FailureReason.NONE, "success has no failure reason");
            helper.assertValueEqual(state.getBoolean("Enabled"), !enabled, "successful command changes work state");
            var stale = VillagerInteractionService.recruitmentResult(player, villager.getId(), Action.TOGGLE_WORK_ENABLED, null, -1L);
            helper.assertValueEqual(stale.failureReason(), FailureReason.STALE_STATE, "unversioned command is rejected");
            helper.assertValueEqual(state.getBoolean("Enabled"), !enabled, "stale command does not mutate state");
            VillagerConversationService.endForPlayer(player, false);
            var closed = VillagerInteractionService.recruitmentResult(player, villager.getId(), Action.TOGGLE_WORK_ENABLED, null,
                    accepted.assignment().revision());
            helper.assertValueEqual(closed.failureReason(), FailureReason.CONVERSATION_ENDED, "closed conversation rejects commands");
        } finally {
            VillagerConversationService.endForPlayer(player, false);
            HiredVillagerContractService.endHireContract(helper.getLevel(), villager, player);
            villager.discard();
        }
        helper.succeed();
    }
}
