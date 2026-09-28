package com.jvn.villagerretaliation.settlement;

import java.util.LinkedHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementTerritoryGameTests {
    private SettlementTerritoryGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void startingPlotsAndCompleteFootprintsUseChunkColumns(GameTestHelper helper) {
        var territory = new SettlementTerritory(new BlockPos(-1, 80, -1));
        territory.start();
        helper.assertValueEqual(territory.plots().size(), 9, "Camp owns nine chunks");
        helper.assertValueEqual(territory.foundingChunk(), new ChunkPos(-1, -1), "negative coordinates floor into chunks");
        helper.assertTrue(territory.contains(new BlockPos(-32, -64, -32), new BlockPos(15, 319, 15)),
                "owned columns span build height");
        helper.assertFalse(territory.contains(new BlockPos(-33, 80, 0)), "neighboring columns are unowned");
        helper.assertFalse(territory.adjacent(1, 1), "diagonal contact cannot be claimed");
        territory.advance(SettlementStage.HAMLET);
        territory.claim(1, 0, true);
        helper.assertTrue(territory.contains(1, 0), "edge neighbors can be claimed");
        helper.assertFalse(territory.contains(new BlockPos(-16, 80, -16), new BlockPos(31, 81, 15)),
                "owned corners do not authorize missing interior chunks");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void rewardsRemainBankedAndCannotRepeatAfterSaving(GameTestHelper helper) {
        var center = new BlockPos(8, 80, 8);
        var territory = new SettlementTerritory(center);
        territory.start();
        int credits = 0;
        for (var stage : SettlementStage.values()) {
            territory.advance(stage);
            credits += SettlementStageRules.defaultFreePlots(stage);
            helper.assertValueEqual(territory.capacity(), SettlementStageRules.defaultPlots(stage), "stage capacity");
            helper.assertValueEqual(territory.freeClaims(), credits, "stage rewards accumulate without claiming land");
            helper.assertValueEqual(territory.plots().size(), 9, "advancement never claims land automatically");
            territory = SettlementTerritory.load(center, territory.save());
            territory.advance(stage);
            territory.refresh(stage);
            helper.assertValueEqual(territory.freeClaims(), credits, "load and refresh cannot repeat rewards");
        }
        helper.assertValueEqual(territory.freeClaims(), 28, "all four rewards remain banked");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void pricesCountOnlyPaidClaimsAndCapacityRejectsExtensions(GameTestHelper helper) {
        var territory = new SettlementTerritory(new BlockPos(0, 80, 0));
        territory.start();
        helper.assertValueEqual(SettlementPlotPricing.price(territory, 2, 0), 20L, "first adjacent purchase price");
        expectInvalid(helper, () -> territory.claim(2, 0, false));
        territory.advance(SettlementStage.HAMLET);
        territory.claim(2, 0, true);
        helper.assertValueEqual(SettlementPlotPricing.price(territory, 3, 0), 24L, "free claims do not inflate paid count");
        territory.claim(3, 0, false);
        helper.assertValueEqual(territory.paidPurchases(), 1, "successful paid claim counted once");
        helper.assertValueEqual(SettlementPlotPricing.price(territory, 4, 0), 30L, "distance remains anchored to founding chunk");
        for (int x = 4; territory.plots().size() < territory.capacity(); x++) territory.claim(x, 0, false);
        int count = territory.plots().size();
        expectInvalid(helper, () -> territory.claim(18, 0, false));
        helper.assertValueEqual(territory.plots().size(), count, "rejected claims preserve ownership");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void migrationGrandfathersExcessAndKeepsLongLayouts(GameTestHelper helper) {
        var center = new BlockPos(0, 80, 0);
        var territory = new SettlementTerritory(center);
        var owned = new LinkedHashSet<Long>();
        for (int x = 0; x < 80; x++) owned.add(ChunkPos.asLong(x, 0));
        territory.migrate(owned, SettlementStage.HAMLET, "");
        helper.assertValueEqual(territory.capacity(), 80, "excess claims receive a fixed allowance");
        helper.assertValueEqual(territory.freeClaims(), 0, "preserved land consumes earned entitlement");
        helper.assertValueEqual(territory.paidPurchases(), 0, "preserved land never counts as paid purchases");
        territory.advance(SettlementStage.VILLAGE);
        helper.assertValueEqual(territory.capacity(), 104, "later stages add their normal capacity increase");
        helper.assertTrue(territory.envelopeRadius(center) > 512, "long territories extend beyond the old radius ceiling");
        var restored = SettlementTerritory.load(center, territory.save());
        helper.assertValueEqual(restored.plots(), owned, "all preserved claims survive saving");
        expectInvalid(helper, () -> restored.migrate(owned, SettlementStage.VILLAGE, ""));
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void migrationRoutesAroundOwnersAndRetainsSurvivingComponents(GameTestHelper helper) {
        long origin = ChunkPos.asLong(0, 0), distant = ChunkPos.asLong(2, 0);
        var desired = java.util.Set.of(origin, distant);
        var blocked = java.util.Set.of(ChunkPos.asLong(1, 0));
        var connected = SettlementTerritoryMigration.connect(desired, blocked, origin);
        helper.assertValueEqual(connected.size(), 5, "shortest route detours around occupied land");
        helper.assertFalse(connected.contains(ChunkPos.asLong(1, 0)), "migration never steals an occupied chunk");
        helper.assertValueEqual(SettlementTerritoryMigration.connect(desired, blocked, origin), connected,
                "migration tie breaking is deterministic");
        var enclosed = java.util.Set.of(ChunkPos.asLong(-1, 0), ChunkPos.asLong(1, 0),
                ChunkPos.asLong(0, -1), ChunkPos.asLong(0, 1));
        helper.assertValueEqual(SettlementTerritoryMigration.connect(desired, enclosed, origin), java.util.Set.of(origin),
                "an enclosed founding component survives while unreachable regions are excluded");
        helper.assertValueEqual(SettlementTerritoryMigration.connect(desired, java.util.Set.of(origin), origin), java.util.Set.of(distant),
                "the largest surviving component is used when founding land is occupied");
        helper.assertTrue(SettlementTerritoryMigration.connect(desired, desired, origin).isEmpty(),
                "complete ownership conflicts leave no active plots");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void ownedCornersCannotHideAnInteriorHole(GameTestHelper helper) {
        var owned = new LinkedHashSet<Long>();
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                if (x != 2 || z != 2) owned.add(ChunkPos.asLong(x, z));
            }
        }
        var territory = new SettlementTerritory(BlockPos.ZERO);
        territory.migrate(owned, SettlementStage.HAMLET, "");
        helper.assertTrue(territory.contains(new BlockPos(0, 0, 0)), "minimum corner is owned");
        helper.assertTrue(territory.contains(new BlockPos(63, 10, 63)), "maximum corner is owned");
        helper.assertFalse(territory.contains(BlockPos.ZERO, new BlockPos(63, 10, 63)), "an interior missing chunk rejects the whole footprint");
        helper.succeed();
    }

    private static void expectInvalid(GameTestHelper helper, Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        helper.fail("Invalid territory mutation succeeded");
    }
}
