package com.jvn.villagerretaliation.quest;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.party.PartySharedQuestRecord;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.quest.inventory.QuestInventoryService;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("quest_reliability")
@PrefixGameTestTemplate(false)
public final class QuestHandInGameTests {
    @GameTest(template = "empty")
    public static void sharedSubmissionIncludesOffhand(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(PartyService.createParty(player).success(), "fixture party created");
        var party = PartyService.getPartyForPlayer(helper.getLevel(), player.getUUID()).orElseThrow();
        var definition = definition(false);
        var shared = new PartySharedQuestRecord(definition.id(), java.util.UUID.randomUUID(), 0);
        shared.enroll(player.getUUID(), false);
        party.addSharedQuest(shared);
        PartyService.markChanged(helper.getLevel());
        try {
            player.getInventory().setItem(0, new ItemStack(Items.BONE));
            helper.assertTrue(PartyQuestService.planSharedItemSubmission(player, shared, definition.objectives(),
                    (objective, stack) -> QuestInventoryService.matchesObjectiveItemStack(objective, stack, objective.item())).isPresent(),
                    "main inventory control must succeed");
            player.getInventory().setItem(0, ItemStack.EMPTY);
            player.getInventory().offhand.set(0, new ItemStack(Items.BONE));
            helper.assertTrue(QuestInventoryService.previewObjectiveItemStacks(player, definition.objectives(),
                    QuestDefinition.Objective::item).isPresent(), "solo offhand control must succeed");
            helper.assertTrue(PartyQuestService.planSharedItemSubmission(player, shared, definition.objectives(),
                    (objective, stack) -> QuestInventoryService.matchesObjectiveItemStack(objective, stack, objective.item())).isPresent(),
                    "shared submission must accept the same offhand supply");
            var plan = PartyQuestService.planSharedItemSubmission(player, shared, definition.objectives(),
                    (objective, stack) -> QuestInventoryService.matchesObjectiveItemStack(objective, stack, objective.item())).orElseThrow();
            helper.assertTrue(plan.remove(), "offhand supply is consumed");
            helper.assertTrue(player.getInventory().offhand.getFirst().isEmpty(), "offhand removed exactly once");
            helper.assertFalse(plan.remove(), "used plan cannot remove again");
            player.getInventory().armor.set(0, new ItemStack(Items.BONE));
            helper.assertTrue(PartyQuestService.planSharedItemSubmission(player, shared, definition.objectives(),
                    (objective, stack) -> stack.is(Items.BONE)).isEmpty(), "armor remains protected");
        } finally {
            PartyService.leaveParty(player);
            player.getInventory().clearContent();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void soloOverlappingRequirementsAreIndependentOfSlotOrder(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var definition = definition(true);
        try {
            player.getInventory().setItem(0, new ItemStack(Items.BONE));
            player.getInventory().setItem(1, marked());
            helper.assertTrue(QuestInventoryService.previewObjectiveItemStacks(player, definition.objectives(),
                    QuestDefinition.Objective::item).isPresent(), "unrestricted item first control must succeed");
            player.getInventory().setItem(0, marked());
            player.getInventory().setItem(1, new ItemStack(Items.BONE));
            helper.assertTrue(QuestInventoryService.previewObjectiveItemStacks(player, definition.objectives(),
                    QuestDefinition.Objective::item).isPresent(), "swapping slots must not reject a valid inventory");
            helper.assertValueEqual(QuestInventoryService.handInToReceiver(player, null, definition.objectives(),
                    QuestDefinition.Objective::item, null), QuestInventoryService.ItemHandInResult.SUCCESS, "valid overlapping hand-in succeeds");
            helper.assertTrue(player.getInventory().getItem(0).isEmpty() && player.getInventory().getItem(1).isEmpty(),
                    "both assigned supplies consumed exactly once");
        } finally {
            player.getInventory().clearContent();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sharedOverlappingRequirementsAreIndependentOfSlotOrder(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(PartyService.createParty(player).success(), "fixture party created");
        var party = PartyService.getPartyForPlayer(helper.getLevel(), player.getUUID()).orElseThrow();
        var definition = definition(true);
        var shared = new PartySharedQuestRecord(definition.id(), java.util.UUID.randomUUID(), 0);
        shared.enroll(player.getUUID(), false);
        party.addSharedQuest(shared);
        PartyService.markChanged(helper.getLevel());
        try {
            player.getInventory().setItem(0, new ItemStack(Items.BONE));
            player.getInventory().setItem(1, marked());
            helper.assertTrue(PartyQuestService.planSharedItemSubmission(player, shared, definition.objectives(),
                    (objective, stack) -> QuestInventoryService.matchesObjectiveItemStack(objective, stack, objective.item())).isPresent(),
                    "unrestricted item first control must succeed");
            player.getInventory().setItem(0, marked());
            player.getInventory().setItem(1, new ItemStack(Items.BONE));
            helper.assertTrue(PartyQuestService.planSharedItemSubmission(player, shared, definition.objectives(),
                    (objective, stack) -> QuestInventoryService.matchesObjectiveItemStack(objective, stack, objective.item())).isPresent(),
                    "shared allocation must not depend on inventory slot order");
            var plan = PartyQuestService.planSharedItemSubmission(player, shared, definition.objectives(),
                    (objective, stack) -> QuestInventoryService.matchesObjectiveItemStack(objective, stack, objective.item())).orElseThrow();
            player.getInventory().setItem(1, ItemStack.EMPTY);
            helper.assertFalse(plan.remove(), "missing source invalidates the entire plan");
            helper.assertValueEqual(player.getInventory().getItem(0).getCount(), 1, "failed removal preserves the strict supply");
        } finally {
            PartyService.leaveParty(player);
            player.getInventory().clearContent();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allocationHandlesCrossingRequirementsAndLimitedSupplies(GameTestHelper helper) {
        var id = VillagerRetaliation.id("allocation_shapes");
        var raw = JsonParser.parseString("""
                {"objectives":[
                  {"id":"o0","type":"item_check","item":"minecraft:bone","count":1},
                  {"id":"o1","type":"item_check","item":"minecraft:bone","count":1},
                  {"id":"o2","type":"item_check","item":"minecraft:bone","count":2}]}
                """).getAsJsonObject();
        var objectives = QuestDefinitionFixtures.read(id, raw, id).objectives();
        var stacks = java.util.List.of(new ItemStack(Items.BONE, 3), new ItemStack(Items.BONE), new ItemStack(Items.BONE));
        var supplies = java.util.List.of(
                new com.jvn.villagerretaliation.quest.inventory.QuestItemAllocation.Supply(stacks.get(0), 2),
                new com.jvn.villagerretaliation.quest.inventory.QuestItemAllocation.Supply(stacks.get(1), 1),
                new com.jvn.villagerretaliation.quest.inventory.QuestItemAllocation.Supply(stacks.get(2), 1));
        for (int mask = 0; mask < 512; mask++) {
            int edges = mask;
            var plan = com.jvn.villagerretaliation.quest.inventory.QuestItemAllocation.plan(objectives, supplies,
                    (objective, stack) -> (edges & (1 << (objectives.indexOf(objective) * 3 + stacks.indexOf(stack)))) != 0);
            boolean possible = canAllocate(0, new int[] {1, 1, 2}, new int[] {2, 1, 1}, edges);
            helper.assertTrue(plan.isPresent() == possible, "allocation matches exhaustive search for graph " + edges);
            if (plan.isPresent()) {
                int[] used = new int[3];
                int[] filled = new int[3];
                for (var allocation : plan.get()) {
                    int objective = objectives.indexOf(allocation.objective());
                    helper.assertTrue((edges & (1 << (objective * 3 + allocation.source()))) != 0, "allocation respects requirements");
                    used[allocation.source()] += allocation.count();
                    filled[objective] += allocation.count();
                }
                helper.assertTrue(used[0] <= 2 && used[1] <= 1 && used[2] <= 1, "allocation respects consent limits");
                helper.assertTrue(filled[0] == 1 && filled[1] == 1 && filled[2] == 2, "every requirement is fulfilled");
            }
        }
        helper.assertValueEqual(stacks.getFirst().getCount(), 3, "planning never mutates supplies");
        var repeated = new com.jvn.villagerretaliation.quest.inventory.QuestItemAllocation.Supply(stacks.get(1), 1);
        helper.assertTrue(com.jvn.villagerretaliation.quest.inventory.QuestItemAllocation.plan(
                java.util.List.of(objectives.get(2)), java.util.List.of(repeated, repeated), (objective, stack) -> true).isEmpty(),
                "two references to the same stack cannot duplicate its supply");
        helper.succeed();
    }

    private static boolean canAllocate(int objective, int[] demand, int[] remaining, int edges) {
        if (objective == demand.length) return true;
        if (demand[objective] == 0) return canAllocate(objective + 1, demand, remaining, edges);
        for (int source = 0; source < remaining.length; source++) {
            if (remaining[source] == 0 || (edges & (1 << (objective * 3 + source))) == 0) continue;
            remaining[source]--;
            demand[objective]--;
            boolean possible = canAllocate(objective, demand, remaining, edges);
            demand[objective]++;
            remaining[source]++;
            if (possible) return true;
        }
        return false;
    }

    private static ItemStack marked() {
        var stack = new ItemStack(Items.BONE);
        var tag = new CompoundTag();
        tag.putBoolean("quest_mark", true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static QuestDefinition definition(boolean overlapping) {
        var id = VillagerRetaliation.id("quest_reliability");
        var raw = JsonParser.parseString("{\"objectives\":[{\"id\":\"plain\",\"type\":\"item_check\",\"item\":\"minecraft:bone\",\"count\":1,\"consume\":true}]}").getAsJsonObject();
        if (overlapping) raw.getAsJsonArray("objectives").add(JsonParser.parseString("{\"id\":\"marked\",\"type\":\"item_check\",\"item\":\"minecraft:bone\",\"count\":1,\"consume\":true,\"custom_data\":{\"quest_mark\":true}}"));
        return QuestDefinitionFixtures.read(id, raw, id);
    }
}
