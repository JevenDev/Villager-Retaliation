package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyPayment;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyResources;
import com.jvn.villagerretaliation.sell.CurrencyAmount;
import com.jvn.villagerretaliation.sell.VillageSellMarket;
import com.jvn.villagerretaliation.villager.VillagerRecoveryService;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementTradeGameTests {
    private SettlementTradeGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_trade")
    public static void treasuryTransfersRealCurrencyAndKeepsTradePermissionSeparate(GameTestHelper helper) {
        withFixture(helper, fixture -> {
            var town = fixture.town();
            var leader = fixture.leader();
            leader.getInventory().add(VillagerCurrencyResources.createStack(helper.getLevel().getServer(), 10));
            helper.assertTrue(SettlementTradeService.depositCurrency(leader, town.id(), 7), "funding removes real currency");
            helper.assertValueEqual(VillagerCurrencyPayment.count(leader), 3, "only the deposited currency leaves the player");
            helper.assertFalse(SettlementTradeService.depositCurrency(leader, town.id(), 4), "funding cannot overdraft inventory");
            town.economy().credit(CurrencyAmount.of(1L, 3L));
            helper.assertTrue(SettlementTradeService.withdrawCurrency(leader, town.id(), 7), "whole currency can be withdrawn");
            helper.assertValueEqual(town.economy().balance(), CurrencyAmount.of(1L, 3L), "fractional revenue remains in the treasury");
            helper.assertValueEqual(VillagerCurrencyPayment.count(leader), 10, "withdrawal returns physical currency");
            ServerPlayer delegate = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "TownTrader"));
            town.setPermissions(leader.getUUID(), delegate.getUUID(), Set.of(SettlementPermission.TRADE));
            var order = order(Items.WHEAT, SettlementTradeOrder.Direction.BUY, 64, true);
            helper.assertTrue(SettlementTradeService.putOrder(delegate, town.id(), order), "trade permission allows standing orders");
            helper.assertFalse(SettlementTradeService.configureBudget(delegate, town.id(), CurrencyAmount.ZERO, money(100)),
                    "trade permission cannot raise the treasury allowance");
            helper.assertFalse(SettlementTradeService.withdrawCurrency(delegate, town.id(), 1),
                    "trade permission does not allow withdrawals");
            helper.assertFalse(SettlementTradeService.depositCurrency(delegate, town.id(), 1),
                    "treasury transfers require their separate permission");
            var saved = town.save(helper.getLevel().registryAccess());
            var restored = SettlementRecord.load(saved, helper.getLevel().registryAccess(), () -> {});
            helper.assertValueEqual(restored.economy().balance(), CurrencyAmount.of(1L, 3L), "exact currency survives saving");
            saved.remove("Economy");
            helper.assertTrue(SettlementRecord.load(saved, helper.getLevel().registryAccess(), () -> {}).economy().balance().isZero(),
                    "older saves start with an empty treasury");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_trade")
    public static void ordersStartDisabledAndBuyingRequiresAnExplicitTownAllowance(GameTestHelper helper) {
        withFixture(helper, fixture -> {
            var town = fixture.town();
            var disabled = SettlementTradeOrder.disabled(BuiltInRegistries.ITEM.getKey(Items.BREAD), SettlementTradeOrder.Direction.BUY);
            SettlementTradeService.putOrder(fixture.leader(), town.id(), disabled);
            town.economy().credit(money(100));
            helper.assertValueEqual(SettlementTradeService.process(helper.getLevel(), town, 1_000L).get(disabled.id()).status(),
                    SettlementTradeService.Status.DISABLED, "new standing orders do not run");
            var enabled = new SettlementTradeOrder(disabled.id(), disabled.direction(), disabled.item(), 64,
                    money(100), money(100), true);
            SettlementTradeService.putOrder(fixture.leader(), town.id(), enabled);
            helper.assertValueEqual(SettlementTradeService.process(helper.getLevel(), town, 1_200L).get(enabled.id()).status(),
                    SettlementTradeService.Status.BUDGET_LIMIT, "an enabled order cannot spend without treasury authorization");
            SettlementTradeService.configureBudget(fixture.leader(), town.id(), money(99), money(100));
            var result = SettlementTradeService.process(helper.getLevel(), town, 1_400L).get(enabled.id());
            helper.assertValueEqual(result.count(), 3, "the currency reserve limits a purchase exactly");
            helper.assertValueEqual(town.economy().balance(), money(99), "the reserve is not spent");
            helper.assertValueEqual(fixture.depot().count(stack -> stack.is(Items.BREAD)), 3, "purchases arrive as real items");
            helper.assertTrue(SettlementTradeService.process(helper.getLevel(), town, 1_400L).isEmpty(),
                    "replaying the same cycle cannot buy twice");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_trade")
    public static void purchasesRespectPriceOrderBudgetAndFiniteSharedCommoditySupply(GameTestHelper helper) {
        withFixture(helper, fixture -> {
            var town = fixture.town();
            town.economy().credit(money(1_000));
            SettlementTradeService.configureBudget(fixture.leader(), town.id(), CurrencyAmount.ZERO, money(1_000));
            var rule = order(Items.BREAD, SettlementTradeOrder.Direction.BUY, 512, true);
            var cheap = new SettlementTradeOrder(rule.id(), rule.direction(), rule.item(), 512,
                    CurrencyAmount.of(1, 4), money(100), true);
            SettlementTradeService.putOrder(fixture.leader(), town.id(), cheap);
            helper.assertValueEqual(SettlementTradeService.process(helper.getLevel(), town, 200L).get(rule.id()).status(),
                    SettlementTradeService.Status.PRICE_LIMIT, "a purchase cannot exceed its unit price cap");
            var limited = new SettlementTradeOrder(rule.id(), rule.direction(), rule.item(), 512, money(1), money(1), true);
            SettlementTradeService.putOrder(fixture.leader(), town.id(), limited);
            helper.assertValueEqual(SettlementTradeService.process(helper.getLevel(), town, 400L).get(rule.id()).count(), 3,
                    "the per-order daily budget limits exact fractional prices");
            helper.assertValueEqual(SettlementTradeService.process(helper.getLevel(), town, 600L).get(rule.id()).status(),
                    SettlementTradeService.Status.BUDGET_LIMIT, "the order allowance is not reused next cycle");
            SettlementTradeService.putOrder(fixture.leader(), town.id(), rule);
            for (long tick = 800L; tick <= 2_600L; tick += 200L) {
                SettlementTradeService.process(helper.getLevel(), town, tick);
            }
            helper.assertValueEqual(fixture.depot().count(stack -> stack.is(Items.BREAD)), 64, "outside supply is finite");
            var wheat = new SettlementTradeOrder(rule.id(), rule.direction(), BuiltInRegistries.ITEM.getKey(Items.WHEAT),
                    512, money(100), money(100), true);
            SettlementTradeService.putOrder(fixture.leader(), town.id(), wheat);
            helper.assertValueEqual(SettlementTradeService.process(helper.getLevel(), town, 2_800L).get(rule.id()).status(),
                    SettlementTradeService.Status.MARKET_LIMIT, "related goods share the finite commodity supply");
            var restored = SettlementRecord.load(town.save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
            helper.assertValueEqual(SettlementTradeService.process(helper.getLevel(), restored, 3_000L).get(rule.id()).status(),
                    SettlementTradeService.Status.MARKET_LIMIT, "reloading cannot replenish exhausted supply");
            helper.assertValueEqual(SettlementTradeService.process(helper.getLevel(), restored, 240_000L).get(rule.id()).count(), 8,
                    "a later game day replenishes one finite supply without catching up every skipped day");
            helper.assertValueEqual(restored.economy().market(ResourceLocation.parse("villagerretaliation:grain")).bought(), 8,
                    "replenishment resets the current period instead of accumulating unlimited stock");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_trade")
    public static void standingSalesKeepReservesComponentsAndProtectedInputs(GameTestHelper helper) {
        withFixture(helper, fixture -> {
            var town = fixture.town();
            fixture.depot().setItem(0, new ItemStack(Items.WHEAT, 7));
            fixture.depot().setItem(1, new ItemStack(Items.WHEAT, 13));
            var named = new ItemStack(Items.WHEAT, 30);
            named.set(DataComponents.CUSTOM_NAME, Component.literal("Seed fair prize"));
            fixture.depot().setItem(2, named);
            var materials = town.registerDepot(fixture.leader().getUUID(), fixture.area().id(),
                    fixture.area().min().offset(2, 0, 1), SettlementDepotPurpose.MATERIALS);
            materials.setItem(0, new ItemStack(Items.WHEAT, 64));
            var rule = order(Items.WHEAT, SettlementTradeOrder.Direction.SELL, 12, true);
            SettlementTradeService.putOrder(fixture.leader(), town.id(), rule);
            CurrencyAmount expected = VillageSellMarket.quote(helper.getLevel(), town.center(), new ItemStack(Items.WHEAT, 8))
                    .orElseThrow().stackPayout();
            var result = SettlementTradeService.process(helper.getLevel(), town, 1_000L).get(rule.id());
            helper.assertValueEqual(result.count(), 8, "sales may span slots but retain the stock reserve");
            helper.assertValueEqual(town.economy().balance(), expected, "the treasury receives the existing market's exact payout");
            helper.assertValueEqual(fixture.depot().count(rule::matches), 12, "the sale consumes exactly its physical stock");
            helper.assertValueEqual(fixture.depot().getItem(2).getCount(), 30, "named goods are not matched by plain standing orders");
            helper.assertValueEqual(materials.getItem(0).getCount(), 64, "material depots are not sold out from under workers");
            helper.assertValueEqual(SettlementTradeService.process(helper.getLevel(), town, 1_200L).get(rule.id()).status(),
                    SettlementTradeService.Status.STOCK_TARGET, "the reserve prevents another sale");
            var expensive = new SettlementTradeOrder(rule.id(), rule.direction(), rule.item(), 0, money(100), CurrencyAmount.ZERO, true);
            SettlementTradeService.putOrder(fixture.leader(), town.id(), expensive);
            helper.assertValueEqual(SettlementTradeService.process(helper.getLevel(), town, 1_400L).get(rule.id()).status(),
                    SettlementTradeService.Status.PRICE_LIMIT, "sales respect their minimum unit price");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_trade")
    public static void tradeStopsAtFiniteDemandAndPersistsTownWideSpending(GameTestHelper helper) {
        withFixture(helper, fixture -> {
            var town = fixture.town();
            for (int slot = 0; slot < 4; slot++) {
                fixture.depot().setItem(slot, new ItemStack(Items.WHEAT, 64));
            }
            var rule = order(Items.WHEAT, SettlementTradeOrder.Direction.SELL, 0, true);
            SettlementTradeService.putOrder(fixture.leader(), town.id(), rule);
            for (long tick = 200L; tick <= 3_200L; tick += 200L) {
                SettlementTradeService.process(helper.getLevel(), town, tick);
            }
            helper.assertValueEqual(fixture.depot().count(rule::matches), 128, "a village can sell only its finite daily demand");
            helper.assertValueEqual(SettlementTradeService.process(helper.getLevel(), town, 3_400L).get(rule.id()).status(),
                    SettlementTradeService.Status.MARKET_LIMIT, "sales pause when the outside market is saturated");
            SettlementTradeService.removeOrder(fixture.leader(), town.id(), rule.id());
            var buy = order(Items.BREAD, SettlementTradeOrder.Direction.BUY, 64, true);
            SettlementTradeService.putOrder(fixture.leader(), town.id(), buy);
            SettlementTradeService.configureBudget(fixture.leader(), town.id(), CurrencyAmount.ZERO, money(1));
            helper.assertValueEqual(SettlementTradeService.process(helper.getLevel(), town, 3_600L).get(buy.id()).count(), 3,
                    "sale income can fund a purchase within the town allowance");
            SettlementTradeService.removeOrder(fixture.leader(), town.id(), buy.id());
            var replacement = order(Items.BREAD, SettlementTradeOrder.Direction.BUY, 64, true);
            SettlementTradeService.putOrder(fixture.leader(), town.id(), replacement);
            helper.assertValueEqual(SettlementTradeService.process(helper.getLevel(), town, 3_800L).get(replacement.id()).status(),
                    SettlementTradeService.Status.BUDGET_LIMIT, "recreating an order cannot reset the town spending cap");
            var restored = SettlementRecord.load(town.save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
            helper.assertValueEqual(restored.economy().spent(), money(1), "daily spending survives reload");
            helper.assertValueEqual(restored.economy().market(ResourceLocation.parse("villagerretaliation:grain")).sold(), 128,
                    "finite demand usage survives reload");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_trade")
    public static void fullDepotsAndUnstaffedOrBrokenTradingPostsCannotTrade(GameTestHelper helper) {
        withFixture(helper, fixture -> {
            var town = fixture.town();
            var rule = order(Items.BREAD, SettlementTradeOrder.Direction.BUY, 64, true);
            SettlementTradeService.putOrder(fixture.leader(), town.id(), rule);
            town.economy().credit(money(100));
            SettlementTradeService.configureBudget(fixture.leader(), town.id(), CurrencyAmount.ZERO, money(100));
            for (int slot = 0; slot < fixture.depot().getContainerSize(); slot++) {
                fixture.depot().setItem(slot, new ItemStack(Items.STONE, 64));
            }
            helper.assertValueEqual(SettlementTradeService.process(helper.getLevel(), town, 1_000L).get(rule.id()).status(),
                    SettlementTradeService.Status.NO_SPACE, "purchases need real depot capacity");
            helper.assertValueEqual(town.economy().balance(), money(100), "a failed purchase spends nothing");
            fixture.depot().clearContent();
            fixture.clerk().setPos(fixture.area().min().getX() + 20, fixture.area().min().getY(), fixture.area().min().getZ());
            helper.assertValueEqual(SettlementTradeService.process(helper.getLevel(), town, 1_200L).get(rule.id()).status(),
                    SettlementTradeService.Status.NO_STAFF, "a loaded clerk must actually reach the post");
            fixture.clerk().setPos(fixture.leader().position());
            helper.getLevel().removeBlock(fixture.station(), false);
            helper.assertValueEqual(SettlementTradeService.process(helper.getLevel(), town, 1_400L).get(rule.id()).status(),
                    SettlementTradeService.Status.NO_STAFF, "breaking the real lectern closes the post immediately");
            helper.getLevel().setBlockAndUpdate(fixture.station(), Blocks.LECTERN.defaultBlockState());
            helper.getLevel().setDayTime(13_000L);
            helper.assertValueEqual(SettlementTradeService.process(helper.getLevel(), town, 1_600L).get(rule.id()).status(),
                    SettlementTradeService.Status.CLOSED, "clerks rest outside their working hours");
            helper.getLevel().setDayTime(6_000L);
            var threat = EntityType.ZOMBIE.create(helper.getLevel());
            fixture.clerk().setTarget(threat);
            helper.assertValueEqual(SettlementTradeService.process(helper.getLevel(), town, 1_800L).get(rule.id()).status(),
                    SettlementTradeService.Status.NO_STAFF, "a clerk dealing with combat cannot run standing orders");
            fixture.clerk().setTarget(null);
            threat.discard();
            SettlementWorkforceService.release(helper.getLevel(), fixture.leader(), town.id(), fixture.clerk().getUUID());
            helper.assertValueEqual(SettlementTradeService.process(helper.getLevel(), town, 2_000L).get(rule.id()).status(),
                    SettlementTradeService.Status.NO_STAFF, "releasing the clerk stops automatic trade");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_trade")
    public static void civicClerkUsesTheRealTradingPostWorkerWithoutPersonalHire(GameTestHelper helper) {
        withFixture(helper, fixture -> {
            var session = HiredWorkSession.active(helper.getLevel(), fixture.clerk());
            var result = session.worker().tick(helper.getLevel(), fixture.clerk(), null, session.context());
            helper.assertValueEqual(result.status(), "interaction.work.trading.ready", "the civic role tends its trading post");
            helper.assertFalse(session.state().contains("NitwitNoticeTick"), "civic clerks do not run the personal nitwit reporting routine");
            helper.getLevel().removeBlock(fixture.station(), false);
            result = session.worker().tick(helper.getLevel(), fixture.clerk(), null, session.context());
            helper.assertValueEqual(result.status(), "interaction.work.trading.missing_station", "the worker notices destroyed equipment");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_trade")
    public static void unloadedTradingUsesTheSameLedgerWithoutLoadingTownChunks(GameTestHelper helper) {
        var level = helper.getLevel();
        long previousTime = level.getDayTime();
        try {
            level.setDayTime(6_000L);
            BlockPos center = new BlockPos(20_000_000 + helper.absolutePos(BlockPos.ZERO).getX(), 64,
                    20_000_000 + helper.absolutePos(BlockPos.ZERO).getZ());
            helper.assertFalse(level.hasChunkAt(center), "the background town starts unloaded");
            var registry = VillageAllegianceRegistrySavedData.get(level);
            var id = registry.create(level.getGameTime(), level.dimension().location(), center, "");
            var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, level.getGameTime()).orElseThrow();
            town.advance();
            town.advance();
            var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.TRADING_POST, "Distant post",
                    center, center.offset(3, 2, 3), 5, true);
            SettlementSavedData.get(level).putArea(registry, id, town.leader(), area);
            town.recordSurvey(area.id(), new SettlementBuildingSurvey.Survey(level.getGameTime(), List.of(), List.of(center), true));
            var depot = town.registerDepot(town.leader(), area.id(), center.east(), SettlementDepotPurpose.GENERAL);
            UUID clerk = UUID.randomUUID();
            registry.addOrUpdateResident(id, clerk, true, level.getGameTime());
            town.assignDuty(clerk, area.id());
            addHousing(town, helper);
            depot.setItem(0, new ItemStack(Items.COOKED_BEEF, 8));
            town.economy().credit(money(100));
            town.economy().configureBudget(CurrencyAmount.ZERO, money(100));
            var order = order(Items.BREAD, SettlementTradeOrder.Direction.BUY, 64, true);
            town.economy().putOrder(order);
            helper.assertValueEqual(SettlementTradeService.process(level, town, 1_000L).get(order.id()).count(), 8,
                    "unloaded civic clerks process real standing orders");
            helper.assertValueEqual(depot.count(order::matches), 8, "background purchases use the authoritative depot inventory");
            helper.assertFalse(level.hasChunkAt(center), "background trading does not load the town");
            helper.assertTrue(SettlementTradeService.process(level, town, 1_000L).isEmpty(), "a second path cannot repeat the background cycle");
            UUID clerkId = town.duties().keySet().iterator().next();
            var settings = SettlementWorkerConfigurationService.snapshot(level, town, clerkId).with(SettlementWorkerSetting.ENABLED, 0);
            town.setWorkerSettings(town.leader(), clerkId, settings);
            helper.assertValueEqual(SettlementTradeService.process(level, town, 1_200L).get(order.id()).status(),
                    SettlementTradeService.Status.NO_STAFF, "an unloaded paused clerk cannot keep trading");
        } finally {
            level.setDayTime(previousTime);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_stage_rules")
    public static void configuredMarketsLimitRealPurchasesAndSales(GameTestHelper helper) {
        var previous = new java.util.EnumMap<SettlementStage, SettlementStageRules.Limits>(SettlementStage.class);
        for (var stage : SettlementStage.values()) previous.put(stage, SettlementStageRules.forStage(stage));
        var custom = new java.util.EnumMap<>(previous);
        var village = previous.get(SettlementStage.VILLAGE);
        custom.put(SettlementStage.VILLAGE, new SettlementStageRules.Limits(village.radius(), village.projects(), village.depots(),
                3, 5, village.services()));
        try {
            SettlementStageRules.apply(custom);
            withFixture(helper, fixture -> {
                var town = fixture.town();
                town.economy().credit(money(100));
                SettlementTradeService.configureBudget(fixture.leader(), town.id(), CurrencyAmount.ZERO, money(100));
                var buy = order(Items.BREAD, SettlementTradeOrder.Direction.BUY, 64, true);
                SettlementTradeService.putOrder(fixture.leader(), town.id(), buy);
                helper.assertValueEqual(SettlementTradeService.process(helper.getLevel(), town, 200L).get(buy.id()).count(),
                        3, "purchases stop at configured outside supply");
                helper.assertValueEqual(fixture.depot().count(buy::matches), 3, "the limited purchase delivers three actual items");
                helper.assertValueEqual(SettlementTradeService.process(helper.getLevel(), town, 400L).get(buy.id()).status(),
                        SettlementTradeService.Status.MARKET_LIMIT, "the next cycle cannot repeat exhausted supply");
                SettlementTradeService.removeOrder(fixture.leader(), town.id(), buy.id());
                fixture.depot().setItem(1, new ItemStack(Items.WHEAT, 16));
                var sell = order(Items.WHEAT, SettlementTradeOrder.Direction.SELL, 0, true);
                SettlementTradeService.putOrder(fixture.leader(), town.id(), sell);
                helper.assertValueEqual(SettlementTradeService.process(helper.getLevel(), town, 600L).get(sell.id()).count(),
                        5, "sales stop at configured outside demand");
                helper.assertValueEqual(fixture.depot().count(sell::matches), 11, "unsold stock remains in the depot");
                helper.assertValueEqual(SettlementTradeService.process(helper.getLevel(), town, 800L).get(sell.id()).status(),
                        SettlementTradeService.Status.MARKET_LIMIT, "exhausted demand stops further sales");
                custom.put(SettlementStage.VILLAGE, new SettlementStageRules.Limits(village.radius(), village.projects(), village.depots(),
                        1, 2, village.services()));
                SettlementStageRules.apply(custom);
                helper.assertValueEqual(SettlementTradeService.process(helper.getLevel(), town, 1_000L).get(sell.id()).status(),
                        SettlementTradeService.Status.MARKET_LIMIT, "lower limits cannot reset already used daily demand");
                helper.assertValueEqual(fixture.depot().count(sell::matches), 11, "lower market limits preserve remaining stock");
            });
        } finally {
            SettlementStageRules.apply(previous);
        }
    }

    private static SettlementTradeOrder order(Item item, SettlementTradeOrder.Direction direction, int target, boolean enabled) {
        return new SettlementTradeOrder(UUID.randomUUID(), direction, BuiltInRegistries.ITEM.getKey(item), target,
                direction == SettlementTradeOrder.Direction.BUY ? money(100) : CurrencyAmount.ZERO, money(100), enabled);
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_trade")
    public static void hungryClerksTradeLessAndRecoverWhenSupported(GameTestHelper helper) {
        withFixture(helper, fixture -> {
            var town = fixture.town();
            var rule = order(Items.BREAD, SettlementTradeOrder.Direction.BUY, 64, true);
            SettlementTradeService.putOrder(fixture.leader(), town.id(), rule);
            town.economy().credit(money(100));
            SettlementTradeService.configureBudget(fixture.leader(), town.id(), CurrencyAmount.ZERO, money(100));
            VillagerRecoveryService.restoreRecoveryState(fixture.clerk(), new VillagerRecoveryService.RecoverySnapshot(0, 0, 0, 0));
            helper.assertValueEqual(SettlementTradeService.process(helper.getLevel(), town, 1_000L).get(rule.id()).count(), 3,
                    "severe hunger reduces the clerk's processing capacity");
            VillagerRecoveryService.restoreRecoveryState(fixture.clerk(), new VillagerRecoveryService.RecoverySnapshot(20, 5, 0, 0));
            helper.assertValueEqual(SettlementTradeService.process(helper.getLevel(), town, 1_200L).get(rule.id()).count(), 8,
                    "restored needs return the clerk to normal capacity");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_trade")
    public static void pausedClerksCannotSpendSettlementMoney(GameTestHelper helper) {
        withFixture(helper, fixture -> {
            var town = fixture.town();
            var rule = order(Items.BREAD, SettlementTradeOrder.Direction.BUY, 64, true);
            SettlementTradeService.putOrder(fixture.leader(), town.id(), rule);
            town.economy().credit(money(100));
            SettlementTradeService.configureBudget(fixture.leader(), town.id(), CurrencyAmount.ZERO, money(100));
            var settings = SettlementWorkerConfigurationService.snapshot(helper.getLevel(), town, fixture.clerk().getUUID());
            helper.assertTrue(SettlementWorkerConfigurationService.configure(fixture.leader(), town, fixture.clerk().getUUID(),
                    fixture.area().id(), settings.with(SettlementWorkerSetting.ENABLED, 0)), "the clerk can be paused");
            helper.assertValueEqual(SettlementTradeService.process(helper.getLevel(), town, 1_000L).get(rule.id()).status(),
                    SettlementTradeService.Status.NO_STAFF, "paused clerks do not process standing orders");
            helper.assertValueEqual(town.economy().balance(), money(100), "pausing the clerk preserves treasury funds");
            SettlementWorkerConfigurationService.configure(fixture.leader(), town, fixture.clerk().getUUID(), fixture.area().id(), settings);
            helper.assertTrue(SettlementTradeService.process(helper.getLevel(), town, 1_200L).get(rule.id()).count() > 0,
                    "resuming the clerk allows real trade");
        });
    }

    private static void addHousing(SettlementRecord town, GameTestHelper helper) {
        BlockPos position = town.center().above(4);
        var home = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HOME, "Home", position, position.offset(2, 2, 2), 5, true);
        town.putArea(town.leader(), home);
        town.recordSurvey(home.id(), new SettlementBuildingSurvey.Survey(helper.getLevel().getGameTime(), List.of(position), List.of(), true));
    }

    private static CurrencyAmount money(long count) {
        return CurrencyAmount.of(count, 1L);
    }

    private static void withFixture(GameTestHelper helper, Consumer<Fixture> check) {
        long dayTime = helper.getLevel().getDayTime();
        Fixture fixture = fixture(helper);
        try {
            helper.getLevel().setDayTime(6_000L);
            check.accept(fixture);
        } finally {
            fixture.clerk().discard();
            helper.getLevel().setDayTime(dayTime);
        }
        helper.succeed();
    }

    private static Fixture fixture(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(1, 2, 1));
        ServerPlayer leader = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "TradeSteward"));
        leader.setPos(center.getX() + 0.5D, center.getY(), center.getZ() + 0.5D);
        leader.getInventory().clearContent();
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, leader.getUUID(), center, level.getGameTime()).orElseThrow();
        town.advance();
        town.advance();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.TRADING_POST, "Market",
                center, center.offset(4, 2, 4), 5, true);
        SettlementSavedData.get(level).putArea(registry, id, leader.getUUID(), area);
        BlockPos station = center.east();
        level.setBlockAndUpdate(station, Blocks.LECTERN.defaultBlockState());
        SettlementBuildingService.refresh(level, town, area);
        var depot = town.registerDepot(leader.getUUID(), area.id(), center.south(), SettlementDepotPurpose.GENERAL);
        addHousing(town, helper);
        Villager clerk = EntityType.VILLAGER.create(level);
        clerk.setPos(leader.position());
        clerk.setNoAi(true);
        clerk.setVillagerData(clerk.getVillagerData().setProfession(VillagerProfession.NITWIT));
        level.addFreshEntity(clerk);
        VillageAllegianceApi.assignKnown(level, clerk, id, AllegianceAssignmentSource.EXPLICIT_API);
        helper.assertTrue(SettlementWorkforceService.assign(level, leader, id, clerk, area.id()), "the resident can staff the trading post");
        return new Fixture(town, area, depot, leader, clerk, station);
    }

    private record Fixture(SettlementRecord town, SettlementArea area, SettlementDepot depot,
                           ServerPlayer leader, Villager clerk, BlockPos station) {
    }
}
