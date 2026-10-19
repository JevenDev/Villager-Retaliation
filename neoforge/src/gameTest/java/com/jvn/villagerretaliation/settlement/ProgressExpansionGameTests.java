package com.jvn.villagerretaliation.settlement;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.conditions.ConditionEvaluator;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.progress.EventHistorySavedData;
import com.jvn.villagerretaliation.progress.ProgressHistoryService;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.conditions.VillagerConditionAccess;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("progress_expansion")
@PrefixGameTestTemplate(false)
public final class ProgressExpansionGameTests {
    private ProgressExpansionGameTests() { }

    @GameTest(template = "empty", batch = "progress_expansion")
    public static void everyStatisticCategoryUsesInclusiveThresholdsAndMissingTargetsStayUnknown(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        player.awardStat(Stats.BLOCK_MINED.get(net.minecraft.world.level.block.Blocks.STONE), 7);
        player.awardStat(Stats.ITEM_CRAFTED.get(Items.BREAD), 7);
        player.awardStat(Stats.ITEM_USED.get(Items.BREAD), 7);
        player.awardStat(Stats.ITEM_BROKEN.get(Items.BREAD), 7);
        player.awardStat(Stats.ITEM_PICKED_UP.get(Items.BREAD), 7);
        player.awardStat(Stats.ITEM_DROPPED.get(Items.BREAD), 7);
        player.awardStat(Stats.ENTITY_KILLED.get(EntityType.ZOMBIE), 7);
        player.awardStat(Stats.ENTITY_KILLED_BY.get(EntityType.ZOMBIE), 7);
        player.awardStat(Stats.MOB_KILLS, 7);
        for (String category : List.of("mined", "crafted", "used", "broken", "picked_up", "dropped", "killed", "killed_by", "custom")) {
            String target = category.equals("mined") ? "minecraft:stone" : category.startsWith("killed") ? "minecraft:zombie"
                    : category.equals("custom") ? "minecraft:mob_kills" : "minecraft:bread";
            var condition = parse("""
                    {"type":"player_stat","category":"%s","target":"%s","min":7,"max":7}
                    """.formatted(category, target));
            helper.assertValueEqual(ConditionEvaluator.evaluate(VillagerConditionAccess.forPlayer(player), null, condition),
                    ConditionOutcome.MATCHED, "exact inclusive statistic threshold: " + category);
            var missing = parse("""
                    {"type":"player_stat","category":"%s","target":"absent:target","min":0,"max":0}
                    """.formatted(category));
            helper.assertValueEqual(ConditionEvaluator.evaluate(VillagerConditionAccess.forPlayer(player), null, new DialogueCondition.Not(missing)),
                    ConditionOutcome.UNKNOWN, "missing registry entries cannot succeed through negation");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "progress_expansion")
    public static void persistentEventTotalsRespectActorsTargetsAndFirstVersusLastTime(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var other = helper.makeMockServerPlayerInLevel();
        var data = EventHistorySavedData.get(helper.getLevel());
        var event = id("test:encounter");
        long now = helper.getLevel().getServer().overworld().getGameTime();
        data.add("world", null, player.getUUID(), event, "minecraft:zombie", 2, 9, 0);
        data.add("world", null, player.getUUID(), event, "minecraft:zombie", 1, 4, now);
        data.add("world", null, other.getUUID(), event, "minecraft:zombie", 5, 50, now);
        var conditions = List.of(parse("""
                {"type":"event_history","event":"test:encounter","target":"minecraft:zombie","min":3,"max":3,"since":"last","max_age_ticks":0}
                """), parse("""
                {"type":"event_history","event":"test:encounter","metric":"amount","min":13,"max":13}
                """), parse("""
                {"type":"event_history","event":"test:encounter","scope":"world","min":8,"max":8}
                """), parse("""
                {"type":"event_history","event":"test:encounter","since":"first","min_age_ticks":%d,"max_age_ticks":%d}
                """.formatted(now, now)));
        helper.assertTrue(DialogueCondition.matchesAll(DialogueContext.forPlayer(player), conditions), "count, amount, actor and timestamp filters agree");
        var restored = EventHistorySavedData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertValueEqual(restored.query(helper.getLevel(), "player", null, player.getUUID(), event, null).count(), 3L,
                "lifetime totals survive serialization without relying on recent memories");
        helper.assertFalse(DialogueCondition.matchesAll(DialogueContext.forPlayer(player), List.of(parse("""
                {"type":"event_history","event":"test:never","min":0,"max_age_ticks":20}
                """))), "never-occurring events have no age");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "progress_expansion")
    public static void completedTradesRemainPersonalAndAvailableWithoutTheLiveMerchant(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var other = helper.makeMockServerPlayerInLevel();
        var merchant = helper.spawn(EntityType.VILLAGER, 1, 2, 1);
        merchant.setNoAi(true);
        var offer = new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.BREAD, 2), 20, 1, 0);
        merchant.getOffers().add(offer);
        offer.increaseUses();
        com.jvn.villagerretaliation.reputation.VillagerReputationEvents.onTradeWithVillager(
                new net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent(player, offer, merchant));
        ProgressHistoryService.trade(helper.getLevel(), player, merchant, offer, 2);
        ProgressHistoryService.trade(helper.getLevel(), other, merchant, offer, 4);
        var progress = new VillagerQuestSavedData.QuestProgress();
        progress.setIssuer(merchant.getUUID(), "Merchant", "minecraft:farmer", 1, helper.getLevel().dimension(), merchant.blockPosition(), "");
        var saved = VillagerConditionAccess.forSaved(helper.getLevel(), player, null, progress);
        var checks = List.of(parse("""
                {"type":"personal_trade","metric":"trades","min":3,"max":3}
                """), parse("""
                {"type":"personal_trade","metric":"currency_paid","min":9,"max":9}
                """), parse("""
                {"type":"personal_trade","metric":"items_received","item":"minecraft:bread","min":6,"max":6}
                """));
        var live = VillagerInteractionService.createDialogueContext(helper.getLevel(), player, merchant);
        helper.assertTrue(DialogueCondition.matchesAll(live, checks), "live dialogue uses this player's completed trades");
        merchant.discard();
        for (var check : checks) helper.assertValueEqual(ConditionEvaluator.evaluate(saved, null, check), ConditionOutcome.MATCHED,
                "saved issuer identity can query personal trade history after unloading");
        helper.assertValueEqual(ConditionEvaluator.evaluate(VillagerConditionAccess.forPlayer(player), null, new DialogueCondition.Not(checks.getFirst())),
                ConditionOutcome.UNKNOWN, "missing merchant identity is not an empty personal history");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "progress_expansion")
    public static void mergedGiftMemoriesKeepLifetimeCountsAndDuplicateIncidentsDoNotDoubleTotals(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var villager = helper.spawn(EntityType.VILLAGER, 2, 2, 2);
        villager.setNoAi(true);
        var data = EventHistorySavedData.get(helper.getLevel());
        var event = VillageEventMemory.EventTag.PLAYER_ATTACKED_VILLAGER;
        VillageEventMemory.remember(helper.getLevel(), event, villager.blockPosition(), villager, player, VillageEventMemory.MemoryScope.VILLAGER);
        VillageEventMemory.remember(helper.getLevel(), event, villager.blockPosition(), villager, player, VillageEventMemory.MemoryScope.VILLAGER);
        helper.assertValueEqual(data.query(helper.getLevel(), "player", null, player.getUUID(), VillageEventMemory.idFor(event), null).count(), 1L,
                "duplicate incident notifications do not inflate lifetime totals");
        for (int quantity : List.of(2, 3)) VillageEventMemory.rememberGift(helper.getLevel(), villager.blockPosition(), villager, player,
                "Friend", "Bread", "minecraft:bread", quantity,
                com.jvn.villagerretaliation.interaction.VillagerGiftPreferences.GiftReaction.LIKED, 1);
        var gifts = data.query(helper.getLevel(), "villager", villager.getUUID(), player.getUUID(),
                VillageEventMemory.idFor(VillageEventMemory.EventTag.PLAYER_GAVE_LIKED_GIFT), "minecraft:bread");
        helper.assertValueEqual(gifts.count(), 2L, "merging recent gift memories does not erase the number of gifts");
        helper.assertValueEqual(gifts.amount(), 5L, "lifetime gift quantities include every committed gift");
        var restored = EventHistorySavedData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        VillageEventMemory.clear();
        helper.assertValueEqual(restored.query(helper.getLevel(), "villager", villager.getUUID(), player.getUUID(), VillageEventMemory.idFor(event), null).count(), 1L,
                "durable personal history survives recent-memory cache eviction and reload");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "progress_expansion")
    public static void settlementNeedsAndCommittedContributionsUseTheBoundTown(GameTestHelper helper) {
        var level = helper.getLevel();
        var leader = helper.makeMockServerPlayerInLevel();
        var center = helper.absolutePos(new BlockPos(1, 2, 1));
        leader.setPos(center.getCenter());
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var data = SettlementSavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "");
        var town = data.establish(registry, id, leader.getUUID(), center, level.getGameTime()).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Supplies", center, center.offset(4, 2, 4), 5, true);
        helper.assertTrue(data.putArea(registry, id, leader.getUUID(), area), "fixture depot site is accepted");
        var depot = town.registerDepot(leader.getUUID(), area.id(), center.offset(1, 0, 1), SettlementDepotPurpose.GENERAL);
        SettlementTestPlots.activateDepot(level, town, depot);
        var resident = helper.spawn(EntityType.VILLAGER, 2, 2, 2);
        resident.setNoAi(true);
        VillageAllegianceApi.assignKnown(level, resident, town.id(), AllegianceAssignmentSource.EXPLICIT_API);
        town.recordResident(SettlementResidentState.capture(resident, town, level.getGameTime()));
        var progress = new VillagerQuestSavedData.QuestProgress();
        progress.setIssuer(resident.getUUID(), "Resident", "minecraft:none", 1, level.dimension(), center, "");
        var saved = VillagerConditionAccess.forSaved(level, leader, null, progress);
        var housing = parse("""
                {"type":"settlement_need","need":"housing","min":1,"max":1}
                """);
        helper.assertValueEqual(ConditionEvaluator.evaluate(saved, null, housing), ConditionOutcome.MATCHED, "a resident without an assigned bed needs housing");
        Long shortage = SettlementNeeds.value(level, town, "food");
        helper.assertTrue(shortage != null && shortage > 0, "an empty known depot has a real food reserve shortage");
        leader.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD, 10));
        helper.assertValueEqual(SettlementContributionService.contributeHeld(leader, town.id(), 6), 6, "only committed depot delivery earns contribution credit");
        helper.assertValueEqual(leader.getMainHandItem().getCount(), 4, "delivered items leave the player's inventory");
        helper.assertValueEqual(depot.count(stack -> stack.is(Items.BREAD)), 6, "delivered items reach real settlement stock");
        var foodContribution = parse("""
                {"type":"settlement_contribution","metric":"food","item":"minecraft:bread","min":6,"max":6}
                """);
        helper.assertValueEqual(ConditionEvaluator.evaluate(saved, null, foodContribution), ConditionOutcome.MATCHED, "personal contribution quantities are scoped to the saved settlement");
        helper.assertTrue(SettlementNeeds.value(level, town, "food") < shortage, "delivered food reduces the authoritative shortage");
        for (int slot = 0; slot < depot.getContainerSize(); slot++) depot.setItem(slot, new ItemStack(Items.STONE, 64));
        helper.assertValueEqual(SettlementContributionService.contributeHeld(leader, town.id(), 4), 0, "full storage cannot earn contribution credit");
        helper.assertValueEqual(ConditionEvaluator.evaluate(saved, null, foodContribution), ConditionOutcome.MATCHED, "failed delivery leaves history unchanged");
        var stranger = helper.makeMockServerPlayerInLevel();
        stranger.setPos(leader.position());
        stranger.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD, 4));
        helper.assertValueEqual(SettlementContributionService.contributeHeld(stranger, town.id(), 4), 0, "logistics permissions still apply");
        var home = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HOME, "Unobserved home", center.above(3), center.offset(3, 5, 3), 5, true);
        helper.assertTrue(data.putArea(registry, id, leader.getUUID(), home), "fixture home is accepted");
        helper.assertValueEqual(ConditionEvaluator.evaluate(saved, null, new DialogueCondition.Not(housing)), ConditionOutcome.UNKNOWN,
                "unobserved housing cannot pass negated needs checks");
        resident.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "progress_expansion")
    public static void canonicalVillageAliasesRetainBothHistoriesAfterAMerge(GameTestHelper helper) {
        var level = helper.getLevel();
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var player = helper.makeMockServerPlayerInLevel();
        var first = registry.create(0, level.dimension().location(), helper.absolutePos(BlockPos.ZERO), "");
        var second = registry.create(0, level.dimension().location(), helper.absolutePos(new BlockPos(20, 0, 20)), "");
        var data = EventHistorySavedData.get(level);
        data.record(level, player.getUUID(), null, first, id("test:helped"), "", 2, 4);
        data.record(level, player.getUUID(), null, second, id("test:helped"), "", 3, 6);
        helper.assertTrue(registry.merge(first, second), "fixture village aliases merge");
        helper.assertValueEqual(data.query(level, "settlement", second.value(), player.getUUID(), id("test:helped"), null).count(), 5L,
                "both settlements' totals remain visible through their canonical identity");
        helper.succeed();
    }

    private static DialogueCondition parse(String json) {
        var condition = DialogueCondition.readObjects(id("test:expanded_progress"), "test", List.of(JsonParser.parseString(json)), null).getFirst();
        if (condition instanceof DialogueCondition.Invalid invalid) throw new AssertionError(invalid.reason());
        return condition;
    }
    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
}
