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
            var demand = SettlementDemandService.inspect(helper.getLevel(), town);
            helper.assertTrue(demand.demands().stream().anyMatch(row -> row.kind()
                    == com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.Kind.FOOD && row.missing() < 0),
                    "the missing physical material depot leaves food accounting unknown");
            helper.assertValueEqual(demand.sellable(fixture.depot(), new ItemStack(Items.WHEAT)), 20,
                    "unknown meal stock cannot protect unrelated known wheat from sales");
            helper.assertValueEqual(demand.sellable(fixture.depot(), new ItemStack(Items.BREAD)), 0,
                    "unknown meal stock still prevents selling eligible food");
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
                3, 5, village.services(), village.plots(), village.freePlots()));
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
                        1, 2, village.services(), village.plots(), village.freePlots()));
                SettlementStageRules.apply(custom);
                helper.assertValueEqual(SettlementTradeService.process(helper.getLevel(), town, 1_000L).get(sell.id()).status(),
                        SettlementTradeService.Status.MARKET_LIMIT, "lower limits cannot reset already used daily demand");
                helper.assertValueEqual(fixture.depot().count(sell::matches), 11, "lower market limits preserve remaining stock");
            });
        } finally {
            SettlementStageRules.apply(previous);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_trade")
    public static void tradeProtectsConsumersAndSubtractsProductionCommitments(GameTestHelper helper) {
        withFixture(helper, fixture -> {
            var level = helper.getLevel();
            var town = fixture.town();
            var position = town.center().east(6);
            var bakery = new SettlementArea(UUID.randomUUID(), SettlementAreaType.KITCHEN, "Bakery", position, position.offset(2, 2, 2), 5, true);
            SettlementTestPlots.claim(level, town, position, bakery.max());
            helper.assertTrue(town.putArea(town.leader(), bakery), "the bakery is registered");
            town.setWorksitePlan(town.leader(), bakery.id(), SettlementWorksitePlan.defaults(bakery.type().role()));
            var production = com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.create(level,
                    com.jvn.villagerretaliation.interaction.work.production.HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace("bread"), java.util.Map.of()),
                    com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.Mode.BATCH, 4, fixture.depot().position());
            var queue = town.beginProduction(town.leader(), bakery.id());
            queue.append(production);
            fixture.depot().setItem(0, new ItemStack(Items.WHEAT, 20));
            var sale = order(Items.WHEAT, SettlementTradeOrder.Direction.SELL, 0, true);
            SettlementTradeService.putOrder(fixture.leader(), town.id(), sale);
            helper.assertValueEqual(SettlementTradeService.process(level, town, 1_000L).get(sale.id()).count(), 8, "only the eight surplus wheat are sold");
            helper.assertValueEqual(SettlementTradeService.process(level, town, 1_200L).get(sale.id()).count(), 0, "the remaining twelve wheat are protected");
            queue.pause(production.id(), true);
            var jobs = new java.util.ArrayList<Runnable>();
            SettlementTradeService.schedule(level, town, 1_400L, jobs);
            var scheduled = jobs.getLast();
            scheduled.run();
            queue.pause(production.id(), false);
            for (int step = 0; step < 10 && !SettlementWork.complete(scheduled); step++) scheduled.run();
            helper.assertValueEqual(fixture.depot().count(stack -> stack.is(Items.WHEAT)), 12, "scheduled sales recheck consumers changed between work steps");
            queue.pause(production.id(), true);
            helper.assertValueEqual(SettlementTradeService.process(level, town, 1_600L).get(sale.id()).count(), 8, "pausing releases future consumer protection");
            SettlementTradeService.removeOrder(fixture.leader(), town.id(), sale.id());
            queue.pause(production.id(), false);
            queue.reserve(UUID.randomUUID(), production.id(), 1, 4, 0, null);
            town.economy().credit(money(100));
            SettlementTradeService.configureBudget(fixture.leader(), town.id(), CurrencyAmount.ZERO, money(100));
            var purchase = order(Items.BREAD, SettlementTradeOrder.Direction.BUY, 6, true);
            SettlementTradeService.putOrder(fixture.leader(), town.id(), purchase);
            helper.assertValueEqual(SettlementTradeService.process(level, town, 1_800L).get(purchase.id()).count(), 2, "buying subtracts four committed loaves");
            helper.assertValueEqual(SettlementTradeService.process(level, town, 2_000L).get(purchase.id()).count(), 0, "the same incoming supply cannot be purchased twice");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_trade")
    public static void policyPurchasesWaitForShortagesAndKeepBuyingUntilTheDeficitIsFilled(GameTestHelper helper) {
        withFixture(helper, fixture -> {
            var level = helper.getLevel();
            var town = fixture.town();
            fund(town);
            var policy = stockPurchases(helper, fixture, Items.WHEAT, 12, 5, money(100), money(100));
            var approval = policy.configuration().purchases().approvals().getFirst();
            SettlementTradeService.process(level, town, 200);
            SettlementTradeService.process(level, town, 1_200);
            helper.assertTrue(town.economy().policySpending(policy.id()).isZero(), "fallback waits for 1,200 ticks of observed shortage");
            SettlementTradeService.process(level, town, 1_400);
            helper.assertValueEqual(fixture.depot().count(stack -> stack.is(Items.WHEAT)), 8, "fallback uses the native clerk capacity");
            helper.assertValueEqual(policy.purchaseShortages().get(approval.id()).since(), 200L, "its own purchase does not restart the local progress timer");
            SettlementTradeService.process(level, town, 1_600);
            helper.assertValueEqual(fixture.depot().count(stack -> stack.is(Items.WHEAT)), 12, "the next cycle buys only the remaining deficit");
            helper.assertTrue(policy.purchaseShortages().isEmpty(), "fulfilled demand clears its fallback timer");
            helper.assertTrue(town.economy().orders().isEmpty(), "policy execution never appends or adopts manual trade orders");
            helper.assertValueEqual(policy.history().stream().filter(event -> event.kind() == SettlementSupplyPolicy.EventKind.PURCHASED).count(),
                    2L, "successful purchases enter the bounded policy history");
            var spent = town.economy().spent();
            SettlementTradeService.process(level, town, 1_800);
            helper.assertValueEqual(town.economy().spent(), spent, "completed stock targets do not keep spending");
            fixture.depot().withdraw(stack -> stack.is(Items.WHEAT), 7);
            SettlementTradeService.process(level, town, 2_000);
            fixture.depot().deposit(new ItemStack(Items.WHEAT, 2));
            SettlementTradeService.process(level, town, 2_400);
            helper.assertValueEqual(policy.purchaseShortages().get(approval.id()).since(), 2_400L, "local supply progress restarts the next shortage");
            SettlementTradeService.process(level, town, 3_200);
            helper.assertValueEqual(town.economy().spent(), spent, "local progress postpones fallback for the full new delay");
            SettlementTradeService.process(level, town, 3_600);
            helper.assertValueEqual(fixture.depot().count(stack -> stack.is(Items.WHEAT)), 12, "a renewed persistent shortage buys exactly five remaining items");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_trade")
    public static void policyPurchasesShareProtectedNeedsAndRemainingClerkCapacity(GameTestHelper helper) {
        withFixture(helper, fixture -> {
            var level = helper.getLevel();
            var town = fixture.town();
            fund(town);
            var wheat = stockPurchases(helper, fixture, Items.WHEAT, 12, 9, money(100), money(100));
            var food = foodPurchases(helper, fixture, 0, money(100));
            var demand = SettlementDemandService.inspect(level, town).demands().stream()
                    .filter(row -> row.kind() == com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.Kind.FOOD).findFirst().orElseThrow();
            long bread = (demand.supply().protectedMinimum() - 1) / demand.requirement().value(new ItemStack(Items.BREAD)) + 1;
            helper.assertTrue(bread > 0 && bread < 8, "the fixture leaves some clerk capacity after its protected food minimum");
            SettlementTradeService.process(level, town, 200);
            SettlementTradeService.process(level, town, 1_400);
            helper.assertValueEqual(fixture.depot().count(stack -> stack.is(Items.BREAD)), (int) bread,
                    "protected food buys before a higher priority ordinary stock target");
            helper.assertValueEqual(fixture.depot().count(stack -> stack.is(Items.WHEAT)), 8 - (int) bread,
                    "ordinary purchases share the remaining capacity instead of opening another clerk allowance");
            helper.assertFalse(town.economy().policySpending(food.id()).isZero(), "food spending belongs to its authorizing policy");
            helper.assertFalse(town.economy().policySpending(wheat.id()).isZero(), "remaining capacity retains its independent policy ledger");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_trade")
    public static void sharedFoodPurchasesAggregateBeforeUsingAnotherPolicyBudget(GameTestHelper helper) {
        withFixture(helper, fixture -> {
            var level = helper.getLevel();
            var town = fixture.town();
            fund(town);
            var price = VillageSellMarket.quote(level, town.center(), new ItemStack(Items.BREAD)).orElseThrow().baseUnitPrice().multiply(2);
            var first = foodPurchases(helper, fixture, 9, price.multiply(2));
            var second = foodPurchases(helper, fixture, 1, money(100));
            var demand = SettlementDemandService.inspect(level, town).demands().stream()
                    .filter(row -> row.kind() == com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.Kind.FOOD).findFirst().orElseThrow();
            int needed = (int) ((demand.missing() - 1) / demand.requirement().value(new ItemStack(Items.BREAD)) + 1);
            SettlementTradeService.process(level, town, 200);
            SettlementTradeService.process(level, town, 1_400);
            helper.assertValueEqual(fixture.depot().count(stack -> stack.is(Items.BREAD)), needed,
                    "multiple authorizers buy the shared food deficit once");
            helper.assertValueEqual(town.economy().policySpending(first.id()), price.multiply(2), "higher policy priority receives scarce spending capacity first");
            helper.assertValueEqual(town.economy().policySpending(second.id()), price.multiply(needed - 2),
                    "another approved budget can finish the same persistent shortage without restarting its timer");
            helper.assertTrue(first.purchaseShortages().isEmpty() && second.purchaseShortages().isEmpty(), "both policies observe the completed shared requirement");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_trade")
    public static void policyPurchasesRespectPricesDailyCapsReloadAndFiniteMarkets(GameTestHelper helper) {
        withFixture(helper, fixture -> {
            var level = helper.getLevel();
            var town = fixture.town();
            fund(town);
            var quote = VillageSellMarket.quote(level, town.center(), new ItemStack(Items.WHEAT)).orElseThrow();
            var price = quote.baseUnitPrice().multiply(2);
            var policy = stockPurchases(helper, fixture, Items.WHEAT, 256, 5, price.multiply(3), quote.baseUnitPrice());
            SettlementTradeService.process(level, town, 200);
            SettlementTradeService.process(level, town, 1_400);
            helper.assertTrue(town.economy().policySpending(policy.id()).isZero(), "fallback cannot exceed its approved unit price");
            var original = policy.configuration();
            var old = original.purchases().approvals().getFirst();
            var approval = new SettlementPurchasePlan.Approval(old.id(), old.consumer(), old.requirement(), old.item(), old.destination(), price);
            policy.configure(new SettlementSupplyPolicy.Configuration(original.name(), original.target(), original.priority(), original.steps(), original.connections(),
                    new SettlementPurchasePlan(true, price.multiply(3), List.of(approval))), town.leader(), level.getGameTime());
            SettlementTradeService.process(level, town, 1_600);
            SettlementTradeService.process(level, town, 2_800);
            helper.assertValueEqual(fixture.depot().count(stack -> stack.is(Items.WHEAT)), 3, "the policy-wide cap limits exact fractional native purchases");
            var restored = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
            SettlementTradeService.process(level, restored, 3_000);
            helper.assertValueEqual(restored.economy().policySpending(policy.id()), price.multiply(3), "reload cannot renew the policy's spent daily allowance");
            helper.assertValueEqual(restored.depots().get(fixture.depot().id()).count(stack -> stack.is(Items.WHEAT)), 3, "reload does not buy beyond the saved cap");
            policy.configure(new SettlementSupplyPolicy.Configuration(original.name(), original.target(), original.priority(), original.steps(), original.connections(),
                    new SettlementPurchasePlan(true, money(100), List.of(approval))), town.leader(), level.getGameTime());
            SettlementTradeService.process(level, town, 3_200);
            for (long tick = 4_400; tick <= 6_400; tick += 200) SettlementTradeService.process(level, town, tick);
            helper.assertValueEqual(fixture.depot().count(stack -> stack.is(Items.WHEAT)), SettlementTradeService.dailySupply(town.stage()),
                    "policy budgets do not create a separate or infinite commodity market");
            helper.assertValueEqual(town.economy().market(quote.marketGroup()).bought(), SettlementTradeService.dailySupply(town.stage()),
                    "policy purchases consume the native finite market accounting");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_trade")
    public static void manualPurchasesRemainIndependentAndReducePolicyFallback(GameTestHelper helper) {
        withFixture(helper, fixture -> {
            var level = helper.getLevel();
            var town = fixture.town();
            fund(town);
            var policy = stockPurchases(helper, fixture, Items.WHEAT, 12, 5, money(100), money(100));
            SettlementTradeService.process(level, town, 200);
            var manual = order(Items.WHEAT, SettlementTradeOrder.Direction.BUY, 4, true);
            helper.assertTrue(SettlementTradeService.putOrder(fixture.leader(), town.id(), manual), "an independent manual order can share the commodity");
            SettlementTradeService.process(level, town, 1_400);
            helper.assertValueEqual(fixture.depot().count(stack -> stack.is(Items.WHEAT)), 4, "manual output is credited before fallback dispatch");
            helper.assertTrue(town.economy().policySpending(policy.id()).isZero(), "manual supply progress restarts policy fallback without adopting its spending");
            SettlementTradeService.process(level, town, 2_600);
            helper.assertValueEqual(fixture.depot().count(stack -> stack.is(Items.WHEAT)), 12, "the policy later purchases only the remaining eight items");
            helper.assertValueEqual(town.economy().orders().get(manual.id()), manual, "policy execution preserves the manual order and target");
            var price = VillageSellMarket.quote(level, town.center(), new ItemStack(Items.WHEAT)).orElseThrow().baseUnitPrice().multiply(2);
            helper.assertValueEqual(town.economy().orderSpending(manual.id()), price.multiply(4), "manual spending remains independently accounted");
            helper.assertValueEqual(town.economy().policySpending(policy.id()), price.multiply(8), "the policy is charged only for its own goods");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_trade")
    public static void policyPurchasesRespectWorkingHoursClerkPausesAndTreasuryReserves(GameTestHelper helper) {
        withFixture(helper, fixture -> {
            var level = helper.getLevel();
            var town = fixture.town();
            fund(town);
            var policy = stockPurchases(helper, fixture, Items.WHEAT, 12, 5, money(100), money(100));
            var approval = policy.configuration().purchases().approvals().getFirst();
            SettlementTradeService.process(level, town, 200);
            level.setDayTime(18_000);
            SettlementTradeService.process(level, town, 1_400);
            helper.assertValueEqual(SettlementTradeService.purchaseAvailability(level, town, policy.id(), policy.configuration().purchases(), approval),
                    SettlementTradeService.Status.CLOSED, "persistent shortages cannot authorize trade outside working hours");
            helper.assertTrue(fixture.depot().isEmpty() && town.economy().policySpending(policy.id()).isZero(), "closed markets preserve treasury and stock");
            level.setDayTime(6_000);
            var settings = SettlementWorkerConfigurationService.snapshot(level, town, fixture.clerk().getUUID());
            helper.assertTrue(SettlementWorkerConfigurationService.configure(fixture.leader(), town, fixture.clerk().getUUID(),
                    fixture.area().id(), settings.with(SettlementWorkerSetting.ENABLED, 0)), "the clerk is explicitly paused");
            SettlementTradeService.process(level, town, 1_600);
            helper.assertValueEqual(SettlementTradeService.purchaseAvailability(level, town, policy.id(), policy.configuration().purchases(), approval),
                    SettlementTradeService.Status.NO_STAFF, "paused clerks cannot execute policy purchases");
            helper.assertTrue(fixture.depot().isEmpty(), "the shortage timer cannot bypass staffing");
            SettlementWorkerConfigurationService.configure(fixture.leader(), town, fixture.clerk().getUUID(), fixture.area().id(), settings);
            var price = VillageSellMarket.quote(level, town.center(), new ItemStack(Items.WHEAT)).orElseThrow().baseUnitPrice().multiply(2);
            var reserve = town.economy().balance().subtract(price.multiply(2));
            town.economy().configureBudget(reserve, money(1_000));
            SettlementTradeService.process(level, town, 1_800);
            helper.assertValueEqual(fixture.depot().count(stack -> stack.is(Items.WHEAT)), 2, "restored staffing can spend only funds above the exact treasury reserve");
            helper.assertValueEqual(town.economy().balance(), reserve, "the native debit stops at the protected reserve");
            SettlementTradeService.process(level, town, 2_000);
            helper.assertValueEqual(town.economy().policySpending(policy.id()), price.multiply(2), "another eligible cycle cannot spend reserved treasury funds");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_trade")
    public static void scheduledPurchasesRecheckProgressAndAuthorityAtEveryWorkBoundary(GameTestHelper helper) {
        withFixture(helper, fixture -> {
            var level = helper.getLevel();
            var town = fixture.town();
            fund(town);
            var policy = stockPurchases(helper, fixture, Items.WHEAT, 12, 5, money(100), money(100));
            var delegate = UUID.randomUUID();
            boolean purchased = false;
            for (boolean revoke : List.of(false, true)) for (int boundary = 0; boundary < 64; boundary++) {
                long now = (1L + boundary + (revoke ? 64 : 0)) * 24_000;
                fixture.depot().clearContent();
                town.setPermissions(town.leader(), delegate, java.util.Set.of(SettlementPermission.STAFF, SettlementPermission.LOGISTICS, SettlementPermission.TRADE));
                policy.configure(policy.configuration(), delegate, now);
                SettlementTradeService.process(level, town, now);
                var work = SettlementPurchaseService.work(level, town, now + 1_200, new int[] {8});
                for (int step = 0; step < boundary && !SettlementWork.complete(work); step++) work.run();
                var spent = town.economy().policySpending(policy.id());
                int bought = fixture.depot().count(stack -> stack.is(Items.WHEAT));
                purchased |= bought > 0;
                if (revoke) town.setPermissions(town.leader(), delegate, java.util.Set.of(SettlementPermission.VIEW));
                else fixture.depot().setItem(0, new ItemStack(Items.WHEAT, bought + 8));
                int steps = 0;
                do { work.run(); } while (!SettlementWork.complete(work) && ++steps < 4_096);
                helper.assertTrue(SettlementWork.complete(work), "purchase planning remains bounded");
                helper.assertValueEqual(town.economy().policySpending(policy.id()), spent,
                        (revoke ? "revoked authority" : "real local progress") + " stops later spending at scheduler boundary " + boundary);
                helper.assertValueEqual(fixture.depot().count(stack -> stack.is(Items.WHEAT)), bought + (revoke ? 0 : 8),
                        "no cached work buys additional goods after invalidation at scheduler boundary " + boundary);
            }
            helper.assertTrue(purchased, "the tested boundaries include a real purchase before invalidation");
        });
    }

    private static void fund(SettlementRecord town) {
        town.economy().credit(money(1_000));
        town.economy().configureBudget(CurrencyAmount.ZERO, money(1_000));
    }

    private static SettlementSupplyPolicy stockPurchases(GameTestHelper helper, Fixture fixture, Item item, int amount, int priority,
                                                          CurrencyAmount budget, CurrencyAmount limit) {
        var id = UUID.randomUUID();
        var sample = new ItemStack(item);
        var requirement = com.jvn.villagerretaliation.interaction.work.production.WorkSupplyRequirement.exact("policy_stock", sample, amount);
        var requirementId = com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.identity(helper.getLevel().registryAccess(), id,
                com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.Kind.STOCK_RESERVE, requirement);
        var target = new SettlementSupplyPolicy.Target(SettlementSupplyPolicy.TargetKind.STOCK, null, null, fixture.depot().position(), sample, amount);
        return purchases(helper, fixture, id, target, requirementId, item, priority, budget, limit);
    }

    private static SettlementSupplyPolicy foodPurchases(GameTestHelper helper, Fixture fixture, int priority, CurrencyAmount budget) {
        var town = fixture.town();
        var demand = SettlementDemandService.inspect(helper.getLevel(), town).demands().stream()
                .filter(row -> row.kind() == com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.Kind.FOOD).findFirst().orElseThrow();
        var target = new SettlementSupplyPolicy.Target(SettlementSupplyPolicy.TargetKind.FOOD, town.id().value(), null, null, ItemStack.EMPTY, 0);
        return purchases(helper, fixture, UUID.randomUUID(), target, demand.supply().id(), Items.BREAD, priority, budget, money(100));
    }

    private static SettlementSupplyPolicy purchases(GameTestHelper helper, Fixture fixture, UUID id, SettlementSupplyPolicy.Target target,
            UUID requirement, Item item, int priority, CurrencyAmount budget, CurrencyAmount limit) {
        var town = fixture.town();
        var approval = new SettlementPurchasePlan.Approval(UUID.randomUUID(), null, requirement, BuiltInRegistries.ITEM.getKey(item), fixture.depot().position(), limit);
        var policy = new SettlementSupplyPolicy(id, new SettlementSupplyPolicy.Configuration("Approved imports", target, priority, List.of(), List.of(),
                new SettlementPurchasePlan(true, budget, List.of(approval))), town.leader(), town::supplyChanged, town::changed);
        town.addSupplyPolicy(policy);
        policy.state(SettlementSupplyPolicy.State.ACTIVE, town.leader(), "", helper.getLevel().getGameTime());
        return policy;
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

    static Fixture fixture(GameTestHelper helper) {
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
        level.setBlockAndUpdate(center.south(), com.jvn.villagerretaliation.block.VillagerRetaliationBlocks.SETTLEMENT_DEPOT.get().defaultBlockState());
        var depot = town.registerDepot(leader.getUUID(), area.id(), center.south(), SettlementDepotPurpose.GENERAL);
        ((com.jvn.villagerretaliation.block.SettlementDepotBlockEntity) level.getBlockEntity(center.south())).bind(town.id(), depot.id());
        depot.observeRules(List.of());
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

    record Fixture(SettlementRecord town, SettlementArea area, SettlementDepot depot,
                           ServerPlayer leader, Villager clerk, BlockPos station) {
    }
}
