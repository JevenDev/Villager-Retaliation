package com.jvn.villagerretaliation.quest;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.quest.inventory.QuestInventoryService;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("narrative_boundary")
@PrefixGameTestTemplate(false)
public final class QuestInventoryGameTests {
    private QuestInventoryGameTests() { }

    @GameTest(template = "empty", batch = "quest_inventory_boundaries", timeoutTicks = 40)
    public static void itemCountsRetainExactKeysChangeCountersAndRebuildCadence(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var definition = definition("""
                {"objectives":[
                  {"id":"plain","type":"item_check","item":"minecraft:bone","count":1},
                  {"id":"marked","type":"item_check","item":"minecraft:bone","count":1,"custom_data":{"quest_mark":true}}]}
                """);
        var objective = definition.objectives().get(1);
        var item = objective.item();
        var marked = new ItemStack(Items.BONE, 3);
        var data = new CompoundTag();
        data.putBoolean("quest_mark", true);
        marked.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        player.getInventory().setItem(0, new ItemStack(Items.BONE, 2));
        player.getInventory().offhand.set(0, marked);
        player.getInventory().armor.set(0, new ItemStack(Items.BONE, 20));
        player.getInventory().setChanged();
        QuestInventoryService.clearRuntimeState(player);
        helper.assertValueEqual(QuestInventoryService.itemCount(player, item), 5, "main inventory and offhand count without armor");
        helper.assertValueEqual(QuestInventoryService.itemCount(player, item, objective.itemRequirements()), 3,
                "exact requirements reject an unmarked stack of the same item");
        helper.assertValueEqual(QuestInventoryService.itemCount(player, item, objective.itemRequirements()), 3,
                "exact cache keys reuse the same resolved item and requirements");
        marked.grow(1);
        helper.assertValueEqual(QuestInventoryService.itemCount(player, item), 5, "in-place changes retain the hot simple cache");
        helper.assertValueEqual(QuestInventoryService.itemCount(player, item, objective.itemRequirements()), 3,
                "in-place changes retain the hot exact cache");
        var diagnostic = QuestInventoryService.debugInventoryCacheLine(player, definition);
        helper.assertTrue(diagnostic.contains("exact_lookups=3") && diagnostic.contains("exact_cache_misses=1"),
                "diagnostics retain exact hit and miss accounting");
        helper.runAfterDelay(6, () -> {
            try {
                helper.assertValueEqual(QuestInventoryService.itemCount(player, item), 6,
                        "cadenced rebuild observes an in-place stack change");
                helper.assertValueEqual(QuestInventoryService.itemCount(player, item, objective.itemRequirements()), 4,
                        "cadenced rebuild invalidates exact results with simple results");
                marked.shrink(1);
                player.getInventory().setChanged();
                helper.assertValueEqual(QuestInventoryService.itemCount(player, item), 5,
                        "inventory change counters invalidate a still-hot cache");
                helper.assertValueEqual(QuestInventoryService.itemCount(player, item, objective.itemRequirements()), 3,
                        "change counter invalidation covers exact requirements");
                VillagerQuestService.clearRuntimeState(player);
                helper.assertTrue(QuestInventoryService.debugInventoryCacheLine(player, definition).contains("state=cold"),
                        "the existing facade clears the collaborator cache for this player");
            } finally {
                QuestInventoryService.clearRuntimeState(player);
                player.getInventory().clearContent();
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "quest_inventory_boundaries")
    public static void allObjectivesAndIssuerRoomArePlannedBeforeAnyStackIsRemoved(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var issuer = helper.spawn(EntityType.VILLAGER, 2, 1, 2);
        issuer.setNoAi(true);
        var definition = definition("""
                {"objectives":[
                  {"id":"first","type":"item_check","item":"minecraft:bone","count":3,"consume":true},
                  {"id":"second","type":"item_check","item":"minecraft:bone","count":3,"consume":true}]}
                """);
        player.getInventory().setItem(0, new ItemStack(Items.BONE, 5));
        try {
            helper.assertTrue(QuestInventoryService.previewObjectiveItemStacks(
                    player, definition.objectives(), QuestDefinition.Objective::item).isEmpty(),
                    "overlapping objectives cannot count a source stack twice");
            helper.assertValueEqual(QuestInventoryService.handInRequiredObjectiveItems(
                    player, issuer, definition.objectives(), QuestDefinition.Objective::item, null),
                    QuestInventoryService.ItemHandInResult.MISSING_ITEMS, "every objective must have a complete removal plan");
            helper.assertValueEqual(player.getInventory().getItem(0).getCount(), 5, "missing later items preserve earlier sources");
            player.getInventory().offhand.set(0, new ItemStack(Items.BONE, 1));
            var full = IntStream.range(0, VillagerInventoryAccess.captureFullInventory(issuer).size())
                    .mapToObj(ignored -> new ItemStack(Items.STONE, 64)).toList();
            VillagerInventoryAccess.replaceFullInventory(issuer, full);
            helper.assertValueEqual(QuestInventoryService.handInRequiredObjectiveItems(
                    player, issuer, definition.objectives(), QuestDefinition.Objective::item, null),
                    QuestInventoryService.ItemHandInResult.NO_ROOM, "the full issuer rejects a complete submission plan");
            helper.assertValueEqual(player.getInventory().getItem(0).getCount(), 5, "issuer capacity failure preserves main inventory");
            helper.assertValueEqual(player.getInventory().offhand.getFirst().getCount(), 1, "issuer capacity failure preserves offhand");
            VillagerInventoryAccess.replaceFullInventory(issuer, List.of());
            helper.assertValueEqual(QuestInventoryService.handInRequiredObjectiveItems(
                    player, issuer, definition.objectives(), QuestDefinition.Objective::item, null),
                    QuestInventoryService.ItemHandInResult.SUCCESS, "a complete plan transfers all required items");
            helper.assertTrue(player.getInventory().getItem(0).isEmpty() && player.getInventory().offhand.getFirst().isEmpty(),
                    "successful submission removes each source exactly once");
            helper.assertValueEqual(VillagerInventoryAccess.captureFullInventory(issuer).stream()
                    .filter(stack -> stack.is(Items.BONE)).mapToInt(ItemStack::getCount).sum(), 6,
                    "the issuer receives the exact planned count");
        } finally {
            QuestInventoryService.clearRuntimeState(player);
            player.getInventory().clearContent();
            issuer.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "quest_inventory_boundaries")
    public static void itemHandInsUseEntityIndependentReceivers(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var definition = definition("""
                {"objectives":[{"id":"delivery","type":"item_check","item":"minecraft:bone","count":3,"consume":true}]}
                """);
        player.getInventory().setItem(0, new ItemStack(Items.BONE, 4));
        var received = new java.util.ArrayList<ItemStack>();
        boolean[] room = { false };
        var receiver = new com.jvn.villagerretaliation.quest.inventory.QuestItemReceiver() {
            @Override public boolean canReceive(List<ItemStack> stacks) { return room[0]; }
            @Override public ItemStack receive(ItemStack stack) { received.add(stack.copy()); return ItemStack.EMPTY; }
        };
        helper.assertValueEqual(QuestInventoryService.handInToReceiver(player, receiver, definition.objectives(),
                QuestDefinition.Objective::item, null), QuestInventoryService.ItemHandInResult.NO_ROOM, "generic receiver capacity is checked first");
        helper.assertValueEqual(player.getInventory().getItem(0).getCount(), 4, "failed preflight keeps every item");
        room[0] = true;
        helper.assertValueEqual(QuestInventoryService.handInToReceiver(player, receiver, definition.objectives(),
                QuestDefinition.Objective::item, null), QuestInventoryService.ItemHandInResult.SUCCESS, "generic receiver accepts the delivery");
        helper.assertValueEqual(received.getFirst().getCount(), 3, "generic receiver receives the requested count");
        helper.assertValueEqual(player.getInventory().getItem(0).getCount(), 1, "unsubmitted items stay with the player");
        helper.succeed();
    }

    private static QuestDefinition definition(String raw) {
        var id = VillagerRetaliation.id("quest_inventory_boundary");
        return QuestDefinitionFixtures.read(id, JsonParser.parseString(raw).getAsJsonObject(), id);
    }
}
