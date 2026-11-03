package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.diplomacy.VillageDiplomacyService;
import com.jvn.villagerretaliation.diplomacy.route.AidRouteFixture;
import com.jvn.villagerretaliation.diplomacy.route.DiplomaticRoute;
import com.jvn.villagerretaliation.sell.CurrencyAmount;
import com.jvn.villagerretaliation.sell.SellPriceResources;
import com.jvn.villagerretaliation.settlement.SettlementExchangeService.Action;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("settlement_exchange")
@PrefixGameTestTemplate(false)
public final class SettlementExchangeGameTests {
    @net.minecraft.gametest.framework.GameTestGenerator
    public static List<net.minecraft.gametest.framework.TestFunction> existingEconomyRegressions() {
        return List.of(
                regression("exact_currency", com.jvn.villagerretaliation.sell.SellBoxGameTests::exactCurrencyArithmeticNeverRoundsBulkPrices),
                regression("catalog_schema", com.jvn.villagerretaliation.sell.SellBoxGameTests::pricingSchemaDefaultsAndValidatesMarketGroups),
                regression("component_matching", com.jvn.villagerretaliation.sell.SellBoxGameTests::stackPredicatesUseComponentCodecsCustomDataAndRemainingDurability),
                regression("base_values", com.jvn.villagerretaliation.sell.SellBoxGameTests::villageBaseCandidatesAreDeterministicAndBounded),
                regression("market_pressure", com.jvn.villagerretaliation.sell.SellBoxGameTests::villageMarketPressureRecoversPersistsAndStaysIsolated),
                regression("catalog_materials", com.jvn.villagerretaliation.sell.SellBoxGameTests::wholesaleCatalogueAnchorsGroupsAndUnsafeGoods),
                regression("simulation", com.jvn.villagerretaliation.sell.SellBoxGameTests::handlerSimulationHasNoSideEffects),
                regression("storage_components", SettlementEconomyGameTests::stockTransfersPreserveComponentsAndDoNotOverfill));
    }
    private static net.minecraft.gametest.framework.TestFunction regression(String name, java.util.function.Consumer<GameTestHelper> test) {
        return new net.minecraft.gametest.framework.TestFunction("exchange_economy", "exchange_regression_" + name,
                "settlement_exchange:empty", 1200, 0L, true, test);
    }
    private record Pair(SettlementAidGameTests.Fixture first, SettlementAidGameTests.Fixture second, ServerPlayer a, ServerPlayer b) { }
    private static Pair pair(GameTestHelper helper) { return pair(helper, 0); }
    private static Pair pair(GameTestHelper helper, int offset) {
        var first = SettlementAidGameTests.fixture(helper, offset); var second = SettlementAidGameTests.fixture(helper, offset + 128);
        first.depot().setItem(0, new ItemStack(Items.OAK_LOG, 64)); second.depot().setItem(0, new ItemStack(Items.STONE, 64));
        first.town().economy().credit(CurrencyAmount.of(100, 1)); second.town().economy().credit(CurrencyAmount.of(100, 1));
        var a = player(helper, first.town(), "ExchangeA"); var b = player(helper, second.town(), "ExchangeB");
        VillageDiplomacyService.receiveCorrespondence(helper.getLevel(), first.town().id(), second.town().id());
        VillageDiplomacyService.receiveCorrespondence(helper.getLevel(), second.town().id(), first.town().id());
        return new Pair(first, second, a, b);
    }
    private static ServerPlayer player(GameTestHelper helper, SettlementRecord town, String name) {
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), name));
        town.setPermissions(town.leader(), player.getUUID(), Set.of(SettlementPermission.VIEW, SettlementPermission.DIPLOMACY,
                SettlementPermission.TRADE, SettlementPermission.TREASURY, SettlementPermission.STAFF));
        return player;
    }
    private static SettlementExchangeTerms terms(boolean firstSends) {
        return new SettlementExchangeTerms(new SettlementExchangeTerms.Bundle(List.of(new ItemStack(Items.OAK_LOG, 10)), CurrencyAmount.of(1, 4)),
                new SettlementExchangeTerms.Bundle(List.of(new ItemStack(Items.STONE, 20)), CurrencyAmount.of(3, 2)), firstSends);
    }
    private static SettlementFoodRequest propose(GameTestHelper helper, Pair pair, boolean firstSends) {
        var result = SettlementExchangeService.act(pair.a, pair.first.town().id(), pair.second.town().id(), UUID.randomUUID(),
                SettlementSavedData.get(helper.getLevel()).aid().revision(), Action.PROPOSE, terms(firstSends));
        helper.assertValueEqual(result.message(), "applied", "proposal accepted");
        return result.request();
    }
    private static void accept(GameTestHelper helper, Pair pair, SettlementFoodRequest request) {
        helper.assertValueEqual(SettlementExchangeService.act(pair.b, pair.second.town().id(), null, request.id(), request.revision(),
                Action.ACCEPT, null).message(), "applied", "recipient accepts actual resources");
    }
    @GameTest(template = "empty")
    public static void exactlyThreeCountersAcrossBothPartiesSurviveReloadAndFinalAcceptance(GameTestHelper helper) {
        var pair = pair(helper); var request = propose(helper, pair, true);
        for (int i = 0; i < 3; i++) {
            var player = i % 2 == 0 ? pair.b : pair.a;
            var town = i % 2 == 0 ? pair.second.town() : pair.first.town();
            helper.assertValueEqual(SettlementExchangeService.act(player, town.id(), null, request.id(), request.revision(), Action.COUNTER, terms(i % 2 == 0)).message(),
                    "applied", "counter allowed");
            var restored = SettlementFoodRequest.load(request.save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
            helper.assertValueEqual(restored.trade.countersRemaining(), 2 - i, "shared counter budget survives reload");
        }
        helper.assertValueEqual(SettlementExchangeService.act(pair.a, pair.first.town().id(), null, request.id(), request.revision(), Action.COUNTER, terms(true)).message(),
                "counter_limit", "fourth counter rejected");
        helper.assertValueEqual(SettlementExchangeService.act(pair.a, pair.first.town().id(), null, request.id(), request.revision(), Action.ACCEPT, null).message(),
                "applied", "third counter can still be accepted");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void staleAndUnauthorizedActionsCannotReserveOrDispatchTwice(GameTestHelper helper) {
        var pair = pair(helper); var data = SettlementSavedData.get(helper.getLevel()); long initial = data.aid().revision();
        var request = propose(helper, pair, true); long offered = request.revision();
        helper.assertValueEqual(SettlementExchangeService.act(pair.a, pair.first.town().id(), pair.second.town().id(), request.id(), initial, Action.PROPOSE, terms(true)).message(),
                "stale", "replayed proposal is rejected");
        helper.assertValueEqual(SettlementExchangeService.act(pair.a, pair.second.town().id(), null, request.id(), offered, Action.ACCEPT, null).message(),
                "forbidden", "cannot accept for another settlement");
        accept(helper, pair, request);
        helper.assertValueEqual(SettlementExchangeService.act(pair.b, pair.second.town().id(), null, request.id(), offered, Action.ACCEPT, null).message(),
                "stale", "second player click uses stale revision");
        helper.assertValueEqual(pair.first.depot().getItem(0).getCount(), 54, "first goods withdrawn exactly once");
        helper.assertValueEqual(pair.second.depot().getItem(0).getCount(), 44, "second goods withdrawn exactly once");
        helper.assertValueEqual(pair.first.town().economy().balance(), CurrencyAmount.of(399, 4), "fractional treasury reserve exact");
        helper.assertValueEqual(pair.second.town().economy().balance(), CurrencyAmount.of(197, 2), "counterparty treasury reserve exact");
        helper.assertTrue(request.journey == null, "acceptance prepares one journey without inventing a courier");
        helper.assertFalse(SettlementSavedData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess()).readOnly(),
                "accepted two-sided custody reloads");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void unavailableResourcesAndProtectedTreasuryFailWithoutPartialDebit(GameTestHelper helper) {
        var pair = pair(helper); var request = propose(helper, pair, true);
        pair.second.depot().setItem(0, ItemStack.EMPTY);
        helper.assertValueEqual(SettlementExchangeService.act(pair.b, pair.second.town().id(), null, request.id(), request.revision(), Action.ACCEPT, null).message(),
                "goods_reserved_or_unavailable", "missing second contribution blocks both debits");
        helper.assertValueEqual(pair.first.depot().getItem(0).getCount(), 64, "first contribution remains untouched");
        pair.second.depot().setItem(0, new ItemStack(Items.STONE, 64));
        pair.second.town().economy().configureBudget(CurrencyAmount.of(100, 1), CurrencyAmount.ZERO);
        helper.assertValueEqual(SettlementExchangeService.act(pair.b, pair.second.town().id(), null, request.id(), request.revision(), Action.ACCEPT, null).message(),
                "funds_unavailable", "protected treasury cannot be spent");
        helper.assertTrue(request.trade.first == null, "no partial reservation");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void quotesUseSharedCatalogAndTransportWithoutChangingInventoryOrTreasury(GameTestHelper helper) {
        var pair = pair(helper); var level = helper.getLevel();
        var before = SettlementSavedData.get(level).save(new CompoundTag(), level.registryAccess());
        var a = SettlementTradeEvaluation.quote(level, pair.first.town(), pair.second.town(), terms(true), false);
        var b = SettlementTradeEvaluation.quote(level, pair.first.town(), pair.second.town(), terms(false), false);
        helper.assertTrue(a != null && b != null, "both delivery arrangements can be quoted");
        helper.assertTrue(SettlementTradeEvaluation.evaluate(level, pair.first.town(), pair.second.town(), a, false).acceptable(), "quote satisfies evaluator exactly");
        helper.assertTrue(SettlementTradeEvaluation.evaluate(level, pair.first.town(), pair.second.town(), b, false).acceptable(), "sender transport is fully included");
        helper.assertTrue(b.first().funds().compareTo(a.first().funds()) > 0, "NPC asks more when it supplies transport");
        helper.assertValueEqual(SettlementSavedData.get(level).save(new CompoundTag(), level.registryAccess()), before, "quote leaves saved gameplay state unchanged");
        VillageDiplomacyService.standingEvent(level, pair.first.town().id(), pair.second.town().id(), UUID.randomUUID(), 1, 50, "trade_test");
        var cash = new SettlementExchangeTerms.Bundle(List.of(), CurrencyAmount.of(10, 1));
        var equalCash = new SettlementExchangeTerms(cash, cash, true);
        var cashQuote = SettlementTradeEvaluation.quote(level, pair.first.town(), pair.second.town(), equalCash, false);
        helper.assertValueEqual(cashQuote.first().funds(), cashQuote.second().funds(), "reputation and transport cannot generate profit by exchanging identical currency");
        var sameGoods = new SettlementExchangeTerms.Bundle(List.of(new ItemStack(Items.OAK_LOG, 10)), CurrencyAmount.ZERO);
        var roundTrip = new SettlementExchangeTerms(sameGoods, sameGoods, true);
        helper.assertTrue(SettlementTradeEvaluation.evaluate(level, pair.first.town(), pair.second.town(), roundTrip, false).transport().isZero(),
                "swapping identical goods cannot manufacture a transport credit");
        for (var item : List.of(Items.OAK_LOG, Items.OAK_PLANKS, Items.STONE, Items.COBBLESTONE))
            helper.assertTrue(SellPriceResources.definition(level.getServer(), new ItemStack(item)).isPresent(), "building material uses data-driven catalog");
        var unknown = new SettlementExchangeTerms(new SettlementExchangeTerms.Bundle(List.of(new ItemStack(Items.BARRIER)), CurrencyAmount.ZERO),
                terms(true).second(), true);
        helper.assertValueEqual(SettlementTradeEvaluation.evaluate(level, pair.first.town(), pair.second.town(), unknown, false).problem(), "unpriced_item",
                "unknown value is explicit");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void partialDeliveryAndRefundConserveComponentsAndExactFunds(GameTestHelper helper) {
        var pair = pair(helper); var level = helper.getLevel();
        var stack = new ItemStack(Items.OAK_LOG, 10); stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Timber"));
        var cargo = new SettlementNegotiatedTrade.Cargo(new SettlementExchangeTerms.Bundle(List.of(stack), CurrencyAmount.of(1, 3)));
        for (int i = 0; i < pair.second.depot().getContainerSize(); i++) pair.second.depot().setItem(i, new ItemStack(Items.STONE, 64));
        pair.second.depot().setItem(0, stack.copyWithCount(63));
        SettlementExchangeStock.unload(level, pair.second.town(), cargo, pair.second.depot().id(), false);
        helper.assertValueEqual(cargo.remaining.getFirst().getCount(), 9, "partial capacity retains nine exact stacks");
        helper.assertValueEqual(cargo.deliveredFunds, CurrencyAmount.of(1, 3), "funds paid once independently of item capacity");
        var restored = SettlementNegotiatedTrade.Cargo.load(cargo.save(level.registryAccess()), level.registryAccess());
        SettlementExchangeStock.unload(level, pair.second.town(), restored, pair.second.depot().id(), false);
        helper.assertValueEqual(restored.deliveredFunds, CurrencyAmount.of(1, 3), "reload cannot repeat payment");
        pair.second.depot().setItem(1, ItemStack.EMPTY);
        SettlementExchangeStock.unload(level, pair.second.town(), restored, pair.second.depot().id(), false);
        helper.assertTrue(restored.empty(), "remaining goods fit after storage opens");
        helper.assertTrue(ItemStack.isSameItemSameComponents(pair.second.depot().getItem(1), stack), "custom components survive delivery");
        helper.assertValueEqual(restored.delivered.stream().mapToInt(ItemStack::getCount).sum(), 10, "audited quantity matches reservation");
        var corrupt = restored.save(level.registryAccess());
        corrupt.getCompound("Remaining").put("Funds", CurrencyAmount.of(1, 1).save());
        boolean rejected = false;
        try { SettlementNegotiatedTrade.Cargo.load(corrupt, level.registryAccess()); } catch (IllegalArgumentException expected) { rejected = true; }
        helper.assertTrue(rejected, "duplicated saved currency is rejected");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void cancellationRetainsBlockedRefundsAndDeclinesRemainFinal(GameTestHelper helper) {
        var pair = pair(helper); var request = propose(helper, pair, true); accept(helper, pair, request);
        for (int i = 0; i < pair.first.depot().getContainerSize(); i++) pair.first.depot().setItem(i, new ItemStack(Items.STONE, 64));
        helper.assertValueEqual(SettlementExchangeService.act(pair.a, pair.first.town().id(), null, request.id(), request.revision(), Action.CANCEL, null).message(),
                "applied", "accepted trade can cancel before departure");
        SettlementExchangeService.step(helper.getLevel(), request, 0);
        helper.assertValueEqual(request.trade.state, SettlementNegotiatedTrade.State.CANCELLING, "full storage retains auditable reservation");
        pair.first.depot().setItem(0, ItemStack.EMPTY); SettlementExchangeService.step(helper.getLevel(), request, 0);
        helper.assertValueEqual(request.trade.state, SettlementNegotiatedTrade.State.CANCELLED, "refund completes when storage opens");
        helper.assertValueEqual(pair.first.town().economy().balance(), CurrencyAmount.of(100, 1), "funds refunded exactly once");
        SettlementExchangeService.step(helper.getLevel(), request, 0);
        helper.assertValueEqual(pair.first.depot().getItem(0).getCount(), 10, "repeated processing cannot duplicate refund");
        var next = propose(helper, pair, true);
        for (int i = 0; i < 3; i++) {
            var player = i % 2 == 0 ? pair.b : pair.a; var town = i % 2 == 0 ? pair.second.town() : pair.first.town();
            SettlementExchangeService.act(player, town.id(), null, next.id(), next.revision(), Action.COUNTER, terms(true));
        }
        helper.assertValueEqual(SettlementExchangeService.act(pair.a, pair.first.town().id(), null, next.id(), next.revision(), Action.DECLINE, null).message(),
                "applied", "third counter may still be declined");
        helper.assertValueEqual(next.trade.state, SettlementNegotiatedTrade.State.DECLINED, "final decline");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void realStockNeedsInfluenceValueAndNpcInitiatesWithoutSpamming(GameTestHelper helper) {
        var pair = pair(helper); var level = helper.getLevel(); var town = pair.first.town();
        var bundle = new SettlementExchangeTerms.Bundle(List.of(new ItemStack(Items.STONE, 20)), CurrencyAmount.ZERO);
        var before = SettlementTradeEvaluation.value(level, bundle, SettlementExchangeStock.demands(level, town));
        pair.first.depot().setStockTarget(new ItemStack(Items.STONE), 20);
        var after = SettlementTradeEvaluation.value(level, bundle, SettlementExchangeStock.demands(level, town));
        helper.assertTrue(after.compareTo(before) > 0, "real replenishment need increases marginal value");
        var whole = new SettlementExchangeTerms.Bundle(List.of(new ItemStack(Items.STONE, 40)), CurrencyAmount.ZERO);
        var split = new SettlementExchangeTerms.Bundle(List.of(new ItemStack(Items.STONE, 10), new ItemStack(Items.STONE, 10),
                new ItemStack(Items.STONE, 10), new ItemStack(Items.STONE, 10)), CurrencyAmount.ZERO);
        helper.assertValueEqual(SettlementTradeEvaluation.value(level, whole, SettlementExchangeStock.demands(level, town)),
                SettlementTradeEvaluation.value(level, split, SettlementExchangeStock.demands(level, town)), "splitting stacks cannot multiply the need bonus");
        var autonomous = new AutonomousVillageState(); autonomous.sections.add(net.minecraft.core.SectionPos.asLong(pair.first.depot().position())); town.autonomous(autonomous);
        SettlementExchangeService.observe(level, town, level.getGameTime());
        var offers = SettlementSavedData.get(level).aid().requests.values().stream().filter(value -> value.trade != null && value.donor().equals(town.id())).toList();
        helper.assertValueEqual(offers.size(), 1, "NPC initiates one funded offer for its actual shortage");
        var offer = offers.getFirst();
        helper.assertTrue(offer.trade.terms.second().items().getFirst().is(Items.STONE), "NPC requests the needed resource");
        helper.assertFalse(offer.trade.terms.first().funds().isZero(), "NPC offers its own funds");
        SettlementExchangeService.observe(level, town, level.getGameTime() + 200);
        helper.assertValueEqual(SettlementSavedData.get(level).aid().requests.values().stream().filter(value -> value.trade != null && value.donor().equals(town.id())).count(),
                1L, "persistent cooldown prevents repeated offers");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void npcQuotesBuyAndSellRealGoodsAndRespondsWithACounter(GameTestHelper helper) {
        var pair = pair(helper); var level = helper.getLevel(); var npc = pair.second.town();
        var autonomous = new AutonomousVillageState(); autonomous.sections.add(net.minecraft.core.SectionPos.asLong(pair.second.depot().position())); npc.autonomous(autonomous);
        var empty = new SettlementExchangeTerms.Bundle(List.of(), CurrencyAmount.ZERO);
        var sale = new SettlementExchangeTerms(new SettlementExchangeTerms.Bundle(List.of(new ItemStack(Items.OAK_LOG, 10)), CurrencyAmount.ZERO), empty, true);
        var quote = SettlementTradeEvaluation.quote(level, pair.first.town(), npc, sale, false);
        helper.assertFalse(quote.second().funds().isZero(), "sale quote pays for goods instead of proposing a gift");
        helper.assertTrue(SettlementTradeEvaluation.evaluate(level, pair.first.town(), npc, quote, false).acceptable(), "sale quote matches exact evaluation");
        var purchase = new SettlementExchangeTerms(empty, new SettlementExchangeTerms.Bundle(List.of(new ItemStack(Items.STONE, 20)), CurrencyAmount.ZERO), true);
        var response = SettlementExchangeService.act(pair.a, pair.first.town().id(), npc.id(), UUID.randomUUID(),
                SettlementSavedData.get(level).aid().revision(), Action.QUOTE, purchase);
        helper.assertValueEqual(response.message(), "applied", "player can request an NPC quote");
        var offer = response.request();
        helper.assertTrue(offer.trade.proposer.equals(npc.id()), "quote comes from the NPC");
        helper.assertTrue(offer.trade.first == null, "quote does not reserve inventory");
        helper.assertValueEqual(SettlementExchangeService.act(pair.a, pair.first.town().id(), null, offer.id(), offer.revision(), Action.DECLINE, null).message(),
                "applied", "quote can be declined without charge");
        var proposed = SettlementExchangeService.act(pair.a, pair.first.town().id(), npc.id(), UUID.randomUUID(),
                SettlementSavedData.get(level).aid().revision(), Action.PROPOSE, purchase).request();
        helper.assertTrue(proposed != null, "unbalanced proposal is negotiable");
        SettlementExchangeService.step(level, proposed, level.getGameTime());
        helper.assertValueEqual(proposed.trade.countersRemaining(), 2, "NPC counter consumes the shared budget");
        helper.assertTrue(proposed.trade.proposer.equals(npc.id()), "NPC does not accept everything");
        helper.succeed();
    }


    @GameTest(template = "empty", batch = "exchange_travel")
    public static void completeRoundTripUsesSharedTransfersAndReturnsTheSameResident(GameTestHelper helper) {
        for (boolean firstSends : new boolean[]{true, false}) {
            var pair = pair(helper, firstSends ? 0 : 512); var level = helper.getLevel(); var data = SettlementSavedData.get(level);
            var request = propose(helper, pair, firstSends); accept(helper, pair, request); attachJourney(helper, pair, request);
            var home = firstSends ? pair.first : pair.second; var journey = request.journey;
            var resident = EntityType.VILLAGER.create(level); resident.load(journey.residentContext.copy());
            home.town().recordResident(SettlementResidentState.capture(resident, home.town(), level.getGameTime()));
            AidRouteFixture.flat(level, pair.first.depot().position(), pair.second.depot().position());
            journey.encounterCooldown = Long.MAX_VALUE - 1;
            var runtime = new SettlementCourierService.Runtime();
            for (long time = 8; time < 8000 && !request.terminal(); time += 8) {
                data.aid().durableRevision = data.aid().revision();
                SettlementCourierService.step(level, request, time, runtime);
            }
            helper.assertValueEqual(request.state(), SettlementFoodRequest.State.COMPLETED, "shared courier completes the entire round trip: " + request.blocker());
            helper.assertValueEqual(request.trade.state, SettlementNegotiatedTrade.State.COMPLETED, "both contributions fulfill the agreement");
            helper.assertFalse(request.trade.unresolved(), "completed trade has no unresolved reservations");
            helper.assertFalse(home.town().residents().get(journey.resident).courierAway(), "the same resident returns home");
            helper.assertTrue(SettlementFoodRequest.load(request.save(level.registryAccess()), level.registryAccess()).terminal(), "completed round trip reloads");
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "exchange_travel")
    public static void routeAndGuardTermsLockAtAcceptance(GameTestHelper helper) {
        var pair = pair(helper); var level = helper.getLevel(); var data = SettlementSavedData.get(level);
        var request = propose(helper, pair, true); var pos = pair.first.depot().position();
        var guard = EntityType.VILLAGER.create(level); guard.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        guard.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        com.jvn.villagerretaliation.allegiance.VillageAllegianceApi.assignKnown(level, guard, pair.first.town().id(),
                com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource.EXPLICIT_API);
        pair.first.town().recordResident(SettlementResidentState.capture(guard, pair.first.town(), level.getGameTime()));
        var steps = new java.util.ArrayList<DiplomaticRoute.Step>();
        for (int i = 0; i <= 128; i++) steps.add(new DiplomaticRoute.Step(pos.east(i), DiplomaticRoute.Mode.WALK));
        var route = new SettlementRoute(UUID.randomUUID(), pair.first.town().id(), pair.second.town().id(),
                level.dimension().location(), "Trade route", new DiplomaticRoute(steps), data.aid().revision());
        data.aid().routes.put(route.id, route);
        helper.assertTrue(SettlementCaravanService.configure(pair.a, request.id(), request.revision(), route.id, java.util.Map.of(guard.getUUID(), true))
                .startsWith("Transport terms updated"), "sender may select existing route and equipped guard");
        accept(helper, pair, request);
        helper.assertTrue(SettlementCaravanService.configure(pair.a, request.id(), request.revision(), null, java.util.Map.of())
                .contains("locked"), "accepted route and guards cannot be rewritten");
        data.aid().routes.remove(route.id);
        SettlementExchangeTravel.prepare(level, request, level.getGameTime());
        helper.assertValueEqual(request.blocker(), "custom_route_unavailable", "removed route blocks dispatch without losing escrow");
        helper.assertTrue(request.journey == null && request.trade.unresolved(), "blocked route preserves actual reservations");
        helper.succeed();
    }

    private static void attachJourney(GameTestHelper helper, Pair pair, SettlementFoodRequest request) {
        var firstSends = request.trade.terms.firstSends(); var home = firstSends ? pair.first : pair.second;
        var away = firstSends ? pair.second : pair.first;
        var villager = EntityType.VILLAGER.create(helper.getLevel()); var from = home.depot().position(); var to = away.depot().position();
        var steps = new java.util.ArrayList<DiplomaticRoute.Step>();
        for (int i = 0; i <= 128; i++) steps.add(new DiplomaticRoute.Step(from.offset(firstSends ? i : -i, 0, 0), DiplomaticRoute.Mode.WALK));
        var approach = new DiplomaticRoute(List.of(new DiplomaticRoute.Step(from, DiplomaticRoute.Mode.WALK)));
        request.trade.courier = villager.getUUID();
        request.preparation = new SettlementAidPreparation(pair.first.depot().id(), pair.second.depot().id(), pair.first.depot().position(), pair.second.depot().position(),
                false, new SettlementAidPreparation.Candidate(villager.getUUID(), from), null);
        request.journey = new SettlementCourierJourney(request.id(), villager.getUUID(), home.town().id(), helper.getLevel().dimension().location(),
                approach, new DiplomaticRoute(steps), villager.saveWithoutId(new CompoundTag()), 1, ItemStack.EMPTY, 0);
    }
    @GameTest(template = "empty")
    public static void oneCaravanMovesBothContributionsInEitherDirection(GameTestHelper helper) {
        var pair = pair(helper); var request = propose(helper, pair, false); accept(helper, pair, request); attachJourney(helper, pair, request);
        var level = helper.getLevel(); var journey = request.journey;
        SettlementExchangeTravel.arrived(level, request);
        helper.assertValueEqual(request.trade.second.custody, SettlementNegotiatedTrade.Custody.ONBOARD, "designated collector takes its own contribution");
        journey.leg = SettlementCourierJourney.Leg.OUTBOUND; journey.position = pair.first.depot().position();
        SettlementExchangeTravel.arrived(level, request); SettlementExchangeTravel.arrived(level, request);
        helper.assertTrue(request.trade.second.empty(), "outgoing contribution delivered at far endpoint");
        helper.assertValueEqual(request.trade.first.custody, SettlementNegotiatedTrade.Custody.ONBOARD, "same caravan collects return contribution");
        var restored = SettlementFoodRequest.load(request.save(level.registryAccess()), level.registryAccess());
        restored.journey.leg = SettlementCourierJourney.Leg.HOMEBOUND; restored.journey.position = pair.second.depot().position();
        SettlementExchangeTravel.arrived(level, restored);
        helper.assertFalse(restored.trade.unresolved(), "both bundles accounted for after return");
        helper.assertValueEqual(pair.first.town().economy().balance(), CurrencyAmount.of(405, 4), "first receives only agreed funds");
        helper.assertValueEqual(pair.second.town().economy().balance(), CurrencyAmount.of(395, 4), "collector receives agreed funds on return");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void courierDeathRecordsLossAndReleasesOnlyUndispatchedCounterCargo(GameTestHelper helper) {
        var pair = pair(helper); var request = propose(helper, pair, true); accept(helper, pair, request); attachJourney(helper, pair, request);
        SettlementExchangeTravel.arrived(helper.getLevel(), request);
        request.journey.presence = SettlementCourierJourney.Presence.DEAD;
        SettlementExchangeTravel.lose(helper.getLevel(), request, null, true);
        SettlementExchangeTravel.lose(helper.getLevel(), request, null, true);
        SettlementExchangeTravel.refundReserved(helper.getLevel(), request);
        helper.assertValueEqual(request.trade.first.lost.stream().mapToInt(ItemStack::getCount).sum(), 10, "carried losses recorded once");
        helper.assertValueEqual(request.journey.recovery.size(), 1, "one recovery drop per lost stack");
        helper.assertValueEqual(pair.second.depot().getItem(0).getCount(), 64, "goods waiting at other settlement return to stock");
        helper.assertValueEqual(pair.second.town().economy().balance(), CurrencyAmount.of(100, 1), "undispatched funds refunded");
        helper.assertValueEqual(request.trade.first.lostFunds, CurrencyAmount.of(1, 4), "lost funds are not recreated");
        helper.assertValueEqual(SettlementFoodRequest.load(request.save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess()).trade.state,
                SettlementNegotiatedTrade.State.FAILED, "failure audit reloads");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 1600, batch = "exchange_dispatch")
    public static void acceptedTradeBorrowsOneRealResidentAndUsesExistingRoutePreparation(GameTestHelper helper) {
        var pair = pair(helper); var level = helper.getLevel(); var request = propose(helper, pair, true);
        var villager = EntityType.VILLAGER.create(level); var pos = pair.first.depot().position();
        villager.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        com.jvn.villagerretaliation.allegiance.VillageAllegianceApi.assignKnown(level, villager, pair.first.town().id(),
                com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource.EXPLICIT_API);
        pair.first.town().recordResident(SettlementResidentState.capture(villager, pair.first.town(), level.getGameTime()));
        AidRouteFixture.flat(level, pair.first.depot().position(), pair.second.depot().position());
        accept(helper, pair, request);
        helper.startSequence().thenWaitUntil(() -> {
            SettlementExchangeService.step(level, request, level.getGameTime());
            helper.assertTrue(request.journey != null, "existing route planner dispatches the agreed caravan: " + request.blocker());
        }).thenExecute(() -> {
            helper.assertValueEqual(request.journey.resident, villager.getUUID(), "caravan uses existing resident");
            helper.assertValueEqual(SettlementSavedData.get(level).aid().couriers.get(villager.getUUID()), request.id(), "one journey owns the carrier");
            SettlementExchangeService.step(level, request, level.getGameTime());
            helper.assertValueEqual(SettlementSavedData.get(level).aid().requests.values().stream().filter(value -> value.journey != null && value.journey.resident.equals(villager.getUUID())).count(),
                    1L, "retry cannot dispatch a second caravan");
        }).thenSucceed();
    }
}
