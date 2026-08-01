package com.jvn.villagerretaliation.sell;

import com.google.gson.JsonParser;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.block.SellBoxBlockEntity;
import com.jvn.villagerretaliation.block.VillagerRetaliationBlocks;
import java.math.BigInteger;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SellBoxGameTests {
    private static final String EMPTY_TEMPLATE = "empty";
    private static final BlockPos BOX_POS = new BlockPos(1, 1, 1);

    private SellBoxGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void exactCurrencyArithmeticNeverRoundsBulkPrices(GameTestHelper helper) {
        CurrencyAmount balance = CurrencyAmount.of(1, 15)
                .multiply(14)
                .add(CurrencyAmount.of(1, 15));
        helper.assertValueEqual(balance, CurrencyAmount.of(1, 1), "fifteen fifteenths must be exact");
        helper.assertValueEqual(
                CurrencyAmount.of(31, 15).withoutWholeUnits(BigInteger.TWO),
                CurrencyAmount.of(1, 15),
                "collecting whole units must retain the exact fraction");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void pricingSchemaDefaultsAndValidatesMarketGroups(GameTestHelper helper) {
        ResourceLocation location =
                ResourceLocation.fromNamespaceAndPath("test", "sell_prices/legacy_coal.json");
        SellPriceDefinition legacy = SellPriceResources.definitionFromJson(
                        location,
                        JsonParser.parseString(
                                        "{\"item\":\"minecraft:coal\",\"item_count\":12,\"currency_count\":1}")
                                .getAsJsonObject())
                .orElseThrow();
        helper.assertValueEqual(
                legacy.marketGroup(),
                ResourceLocation.fromNamespaceAndPath("minecraft", "coal"),
                "legacy definitions must default their market group to the item id");

        SellPriceDefinition grouped = SellPriceResources.definitionFromJson(
                        location,
                        JsonParser.parseString(
                                        "{\"item\":\"minecraft:coal\",\"item_count\":{\"min\":12,\"max\":14},"
                                                + "\"currency_count\":1,\"market_group\":\"villagerretaliation:fuel\"}")
                                .getAsJsonObject())
                .orElseThrow();
        helper.assertValueEqual(
                grouped.marketGroup(),
                ResourceLocation.fromNamespaceAndPath("villagerretaliation", "fuel"),
                "explicit market groups must parse");

        helper.assertTrue(
                SellPriceResources.definitionFromJson(
                                location,
                                JsonParser.parseString(
                                                "{\"item\":\"minecraft:coal\",\"item_count\":12,\"currency_count\":1,"
                                                        + "\"market_group\":\"Bad Group\"}")
                                        .getAsJsonObject())
                        .isEmpty(),
                "invalid market groups must reject the definition");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void exactMarketArithmeticSupportsMarginalTiers(GameTestHelper helper) {
        CurrencyAmount amount = CurrencyAmount.of(31, 5);
        helper.assertValueEqual(
                amount.multiplyRatio(125, 100),
                CurrencyAmount.of(31, 4),
                "exact rational multipliers must not round");
        helper.assertValueEqual(
                amount.subtract(CurrencyAmount.of(6, 1)),
                CurrencyAmount.of(1, 5),
                "exact subtraction must retain fractions");
        helper.assertValueEqual(
                CurrencyAmount.of(2, 1).subtractClamped(CurrencyAmount.of(3, 1)),
                CurrencyAmount.ZERO,
                "clamped subtraction must never become negative");
        helper.assertValueEqual(
                CurrencyAmount.of(3, 1).min(CurrencyAmount.of(4, 1)),
                CurrencyAmount.of(3, 1),
                "minimum selection must be exact");
        helper.assertValueEqual(
                VillageMarketPolicy.effectiveMultiplier(DailyDemandBand.VERY_LOW, SupplyBand.GLUTTED),
                CurrencyAmount.of(25, 100),
                "the effective multiplier must honor the 25 percent floor");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void dailyPricesAreDeterministicBoundedAndChangeConsecutively(GameTestHelper helper) {
        SellPriceDefinition definition = new SellPriceDefinition(
                ResourceLocation.fromNamespaceAndPath("test", "coal"),
                Items.COAL,
                new SellPriceDefinition.IntRange(15, 24),
                SellPriceDefinition.IntRange.fixed(1));
        CurrencyAmount previous = null;
        for (long day = -5; day < 25; day++) {
            CurrencyAmount selected = DailySellMarket.selectPrice(3733L, day, definition);
            helper.assertTrue(definition.candidatePrices().contains(selected), "daily price must be a configured candidate");
            helper.assertValueEqual(
                    DailySellMarket.selectPrice(3733L, day, definition),
                    selected,
                    "world seed, definition id, and day must select deterministically");
            if (previous != null) {
                helper.assertTrue(!previous.equals(selected), "multi-value ranges must change on consecutive days");
            }
            previous = selected;
        }

        boolean rejected = false;
        try {
            new SellPriceDefinition.IntRange(0, 1);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "non-positive datapack ranges must be rejected");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void villageDemandIsDeterministicLocalAndDaySensitive(GameTestHelper helper) {
        VillageAllegianceId first = new VillageAllegianceId(new UUID(11L, 12L));
        VillageAllegianceId second = new VillageAllegianceId(new UUID(13L, 14L));
        Set<ResourceLocation> groups = new LinkedHashSet<>();
        for (String path : new String[] {"logs", "wool", "fish", "grain", "iron", "flowers", "fuel", "stone", "paper", "gems"}) {
            groups.add(ResourceLocation.fromNamespaceAndPath("villagerretaliation", path));
        }
        Map<ResourceLocation, DailyDemandBand> firstDay =
                VillageSellMarket.demandBands(3733L, first, 5L, 2L, groups);
        helper.assertValueEqual(
                VillageSellMarket.demandBands(3733L, first, 5L, 2L, groups),
                firstDay,
                "demand must be restart-stable for the same inputs");
        helper.assertTrue(
                !firstDay.equals(VillageSellMarket.demandBands(3733L, second, 5L, 2L, groups)),
                "different villages must rank commodity groups independently");
        helper.assertTrue(
                !firstDay.equals(VillageSellMarket.demandBands(3733L, first, 6L, 2L, groups)),
                "demand must shift on a new overworld day");
        helper.assertValueEqual(
                VillageSellMarket.demandBands(3733L, first, 5L, 3L, groups),
                firstDay,
                "resource generations must invalidate caches without changing deterministic demand");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void marginalSaleCrossesSupplyTiersAndAddsFullPressure(GameTestHelper helper) {
        VillageAllegianceId village = new VillageAllegianceId(new UUID(15L, 16L));
        MarketQuote quote = VillageSellMarket.calculateQuote(
                village,
                "Oakvale",
                ResourceLocation.fromNamespaceAndPath("villagerretaliation", "logs"),
                CurrencyAmount.of(1, 1),
                DailyDemandBand.NORMAL,
                CurrencyAmount.of(12, 1),
                60);
        helper.assertValueEqual(quote.supplySegments().size(), 4, "the sale must cross all four supply tiers");
        helper.assertValueEqual(
                quote.stackPayout(),
                CurrencyAmount.of(34, 1),
                "marginal tiers must price 4 fresh, 16 active, 32 saturated, and 8 glutted base value");
        helper.assertValueEqual(
                quote.pressureAdded(),
                CurrencyAmount.of(60, 1),
                "discounted payout must still add the full base value as pressure");
        helper.assertValueEqual(
                quote.resultingPressure(),
                CurrencyAmount.of(72, 1),
                "resulting pressure must include the complete sale exactly once");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void villageMarketPressureRecoversPersistsAndStaysIsolated(GameTestHelper helper) {
        VillageAllegianceRegistrySavedData registry = new VillageAllegianceRegistrySavedData();
        VillageAllegianceId first = new VillageAllegianceId(new UUID(1L, 2L));
        VillageAllegianceId second = new VillageAllegianceId(new UUID(3L, 4L));
        registry.ensureRecord(first, 0L, helper.getLevel().dimension().location(), BlockPos.ZERO);
        registry.ensureRecord(second, 0L, helper.getLevel().dimension().location(), new BlockPos(64, 0, 0));

        ResourceLocation logs = ResourceLocation.fromNamespaceAndPath("villagerretaliation", "logs");
        VillageMarketSavedData data = new VillageMarketSavedData();
        data.recordPressure(registry, first, logs, CurrencyAmount.of(40, 1), 10L);
        helper.assertValueEqual(
                data.pressure(registry, first, logs, 11L),
                CurrencyAmount.of(24, 1),
                "one elapsed day must recover sixteen base emeralds");
        helper.assertValueEqual(
                data.pressure(registry, first, logs, 9L),
                CurrencyAmount.of(40, 1),
                "moving time backward must not reverse recovery");
        helper.assertValueEqual(
                data.pressure(registry, second, logs, 11L),
                CurrencyAmount.ZERO,
                "villages must keep independent pressure");

        CompoundTag saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        VillageMarketSavedData loaded = VillageMarketSavedData.load(saved, helper.getLevel().registryAccess());
        helper.assertValueEqual(
                loaded.pressure(registry, first, logs, 11L),
                CurrencyAmount.of(24, 1),
                "exact pressure must survive save and load");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void villageMarketAliasesMergeRecoveredPressure(GameTestHelper helper) {
        VillageAllegianceRegistrySavedData registry = new VillageAllegianceRegistrySavedData();
        VillageAllegianceId source = new VillageAllegianceId(new UUID(5L, 6L));
        VillageAllegianceId target = new VillageAllegianceId(new UUID(7L, 8L));
        registry.ensureRecord(source, 0L, helper.getLevel().dimension().location(), BlockPos.ZERO);
        registry.ensureRecord(target, 0L, helper.getLevel().dimension().location(), new BlockPos(64, 0, 0));

        ResourceLocation wool = ResourceLocation.fromNamespaceAndPath("villagerretaliation", "wool");
        VillageMarketSavedData data = new VillageMarketSavedData();
        data.recordPressure(registry, source, wool, CurrencyAmount.of(40, 1), 2L);
        data.recordPressure(registry, target, wool, CurrencyAmount.of(20, 1), 3L);
        helper.assertTrue(registry.merge(source, target), "the test villages must merge");
        data.canonicalize(registry, 4L);

        helper.assertValueEqual(data.marketCount(), 1, "alias market state must be retired");
        helper.assertValueEqual(
                data.pressure(registry, target, wool, 4L),
                CurrencyAmount.of(12, 1),
                "both states must recover to the merge day before pressure is combined");
        helper.assertValueEqual(
                data.pressure(registry, source, wool, 4L),
                CurrencyAmount.of(12, 1),
                "alias reads must resolve to the canonical market");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void sellBoxesInOneVillageSharePressure(GameTestHelper helper) {
        SellBoxBlockEntity first = placeBox(helper);
        BlockPos secondPos = new BlockPos(3, 1, 1);
        helper.setBlock(secondPos, VillagerRetaliationBlocks.SELL_BOX.get());
        SellBoxBlockEntity second = (SellBoxBlockEntity) helper.getBlockEntity(secondPos);

        MarketQuote before = VillageSellMarket.quote(
                        helper.getLevel(), second.getBlockPos(), new ItemStack(Items.DIAMOND, 64))
                .orElseThrow();
        first.insertForSale(new ItemStack(Items.DIAMOND, 64), false);
        helper.assertTrue(first.sellPending(), "the first box sale must complete");
        MarketQuote after = VillageSellMarket.quote(
                        helper.getLevel(), second.getBlockPos(), new ItemStack(Items.DIAMOND, 64))
                .orElseThrow();

        helper.assertTrue(
                after.stackPayout().compareTo(before.stackPayout()) < 0,
                "a second box in the village must see the first box's supply pressure");
        helper.assertValueEqual(
                after.recoveredPressure(),
                CurrencyAmount.of(64, 1),
                "one completed sale must add pressure exactly once");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void noVillageRejectsSalesButKeepsCurrencyCollectable(GameTestHelper helper) {
        helper.setBlock(BOX_POS, VillagerRetaliationBlocks.SELL_BOX.get());
        SellBoxBlockEntity sellBox = (SellBoxBlockEntity) helper.getBlockEntity(BOX_POS);
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(helper.getLevel());
        int recordsBefore = registry.records().size();
        ItemStack diamonds = new ItemStack(Items.DIAMOND, 4);

        helper.assertValueEqual(
                sellBox.inputHandler().insertItem(0, diamonds, true),
                diamonds,
                "simulation outside a village must reject the item");
        helper.assertValueEqual(
                registry.records().size(),
                recordsBefore,
                "simulation must not discover or create a village");
        helper.assertValueEqual(
                sellBox.insertForSale(diamonds, false),
                diamonds,
                "actual insertion outside a valid market must reject the item");
        helper.assertTrue(sellBox.getItem(0).isEmpty(), "rejected goods must not enter the pending slot");

        sellBox.restoreCurrency(new ItemStack(Items.EMERALD, 2));
        helper.assertValueEqual(
                sellBox.outputHandler().extractItem(0, 2, false).getCount(),
                2,
                "existing whole currency must remain extractable outside a village");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void pendingStackWaitsForConfirmation(GameTestHelper helper) {
        SellBoxBlockEntity sellBox = placeBox(helper);
        ItemStack coal = new ItemStack(Items.COAL, 7);
        ItemStack remainder = sellBox.insertForSale(coal, false);
        helper.assertTrue(remainder.isEmpty(), "valid input should be accepted");
        helper.assertValueEqual(sellBox.getItem(0).getCount(), 7, "the inserted stack should remain pending");
        helper.assertTrue(sellBox.balance().isZero(), "insertion into an empty box must not sell immediately");

        CurrencyAmount expected = sellBox.pendingValue();
        helper.assertTrue(sellBox.sellPending(), "confirmation should sell the pending stack");
        helper.assertValueEqual(sellBox.balance(), expected, "confirmed sale should add its exact current value");
        helper.assertTrue(sellBox.getItem(0).isEmpty(), "confirmed sale should clear the pending slot");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void occupiedInsertionSellsOldStackAtomically(GameTestHelper helper) {
        SellBoxBlockEntity sellBox = placeBox(helper);
        ItemStack oldStack = new ItemStack(Items.COAL, 3);
        ItemStack newStack = new ItemStack(Items.COAL, 5);
        sellBox.insertForSale(oldStack, false);
        CurrencyAmount expected = sellBox.pendingValue();

        helper.assertTrue(sellBox.insertForSale(newStack, false).isEmpty(), "replacement should be accepted");
        helper.assertValueEqual(sellBox.balance(), expected, "replacement must sell exactly the old stack");
        helper.assertValueEqual(sellBox.getItem(0).getCount(), 5, "the replacement stack should remain pending");

        CurrencyAmount beforeInvalid = sellBox.balance();
        ItemStack pendingBeforeInvalid = sellBox.getItem(0).copy();
        ItemStack invalid = new ItemStack(Items.EMERALD, 1);
        helper.assertValueEqual(sellBox.insertForSale(invalid, false), invalid, "currency must be rejected");
        helper.assertValueEqual(sellBox.balance(), beforeInvalid, "invalid insertion must not sell the pending stack");
        helper.assertTrue(
                sameStack(sellBox.getItem(0), pendingBeforeInvalid),
                "invalid insertion must not replace the pending stack");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void handlerSimulationHasNoSideEffects(GameTestHelper helper) {
        SellBoxBlockEntity sellBox = placeBox(helper);
        ItemStack first = new ItemStack(Items.COAL, 4);
        ItemStack second = new ItemStack(Items.STRING, 6);
        sellBox.insertForSale(first, false);

        helper.assertTrue(sellBox.inputHandler().insertItem(0, second, true).isEmpty(), "simulated valid insert should report acceptance");
        helper.assertTrue(
                sameStack(sellBox.getItem(0), first),
                "simulated insert must not replace pending input");
        helper.assertTrue(sellBox.balance().isZero(), "simulated insert must not trigger a sale");
        sellBox.restoreCurrency(new ItemStack(Items.EMERALD, 2));
        CurrencyAmount balanceBeforeExtraction = sellBox.balance();
        helper.assertValueEqual(
                sellBox.outputHandler().extractItem(0, 1, true).getCount(),
                1,
                "simulated payout should report one available whole currency item");
        helper.assertValueEqual(
                sellBox.balance(),
                balanceBeforeExtraction,
                "simulated extraction must not alter the balance");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void bottomExtractionPaysOnlyWholeCurrency(GameTestHelper helper) {
        SellBoxBlockEntity sellBox = placeBox(helper);
        CurrencyAmount unitPrice = VillageSellMarket.quote(
                        helper.getLevel(), sellBox.getBlockPos(), new ItemStack(Items.COAL))
                .orElseThrow()
                .effectiveUnitPrice();
        sellBox.insertForSale(new ItemStack(Items.COAL, 64), false);
        sellBox.sellPending();
        CurrencyAmount original = sellBox.balance();

        ItemStack simulated = sellBox.outputHandler().extractItem(0, 1, true);
        helper.assertValueEqual(simulated.getCount(), 1, "a whole currency item should be exposed at the bottom");
        helper.assertValueEqual(sellBox.balance(), original, "simulated extraction must preserve balance");

        ItemStack extracted = sellBox.outputHandler().extractItem(0, 1, false);
        helper.assertValueEqual(extracted.getCount(), 1, "actual extraction should mint one primary currency item");
        helper.assertValueEqual(
                sellBox.balance(),
                original.withoutWholeUnits(BigInteger.ONE),
                "actual extraction should remove exactly one whole unit");
        helper.assertTrue(unitPrice.compareTo(CurrencyAmount.ZERO) > 0, "the built-in coal definition should be active");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void loadedBlockItemPreservesPendingStackAndExactBalance(GameTestHelper helper) {
        SellBoxBlockEntity original = placeBox(helper);
        ItemStack sold = new ItemStack(Items.COAL, 3);
        ItemStack pending = new ItemStack(Items.STRING, 5);
        original.insertForSale(sold, false);
        original.insertForSale(pending, false);

        ItemStack drop = new ItemStack(VillagerRetaliationBlocks.SELL_BOX.get());
        original.saveToItem(drop, helper.getLevel().registryAccess());
        helper.assertValueEqual(drop.getMaxStackSize(), 1, "loaded sell-box items must remain unstackable");

        SellBoxBlockEntity restored = new SellBoxBlockEntity(
                new BlockPos(2, 1, 1), VillagerRetaliationBlocks.SELL_BOX.get().defaultBlockState());
        restored.setLevel(helper.getLevel());
        CustomData data = drop.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY);
        helper.assertTrue(
                data.loadInto(restored, helper.getLevel().registryAccess()),
                "the loaded block entity data should be applied");
        restored.applyComponentsFromItemStack(drop);
        helper.assertTrue(sameStack(restored.getItem(0), pending), "pending stack should survive the item round trip");
        helper.assertValueEqual(restored.balance(), original.balance(), "exact balance should survive the item round trip");
        helper.succeed();
    }

    private static boolean sameStack(ItemStack first, ItemStack second) {
        return first.getCount() == second.getCount()
                && ItemStack.isSameItemSameComponents(first, second);
    }

    private static SellBoxBlockEntity placeBox(GameTestHelper helper) {
        BlockPos absolute = helper.absolutePos(BOX_POS);
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(helper.getLevel());
        VillageAllegianceId village = new VillageAllegianceId(new UUID(
                absolute.asLong(),
                absolute.asLong() ^ 0x5DEECE66DL));
        registry.ensureRecord(
                village,
                helper.getLevel().getGameTime(),
                helper.getLevel().dimension().location(),
                absolute);
        helper.setBlock(BOX_POS, VillagerRetaliationBlocks.SELL_BOX.get());
        if (helper.getBlockEntity(BOX_POS) instanceof SellBoxBlockEntity sellBox) {
            return sellBox;
        }
        throw new IllegalStateException("Sell box block entity was not created");
    }
}
