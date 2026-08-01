package com.jvn.villagerretaliation.sell;

import com.google.gson.JsonParser;

import com.jvn.villagerretaliation.block.SellBoxBlockEntity;
import com.jvn.villagerretaliation.block.VillagerRetaliationBlocks;
import java.math.BigInteger;
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
    public static void pendingStackWaitsForConfirmation(GameTestHelper helper) {
        SellBoxBlockEntity sellBox = placeBox(helper);
        ItemStack coal = new ItemStack(Items.COAL, 7);
        ItemStack remainder = sellBox.insertForSale(coal, false);
        helper.assertTrue(remainder.isEmpty(), "valid input should be accepted");
        helper.assertValueEqual(sellBox.getItem(0).getCount(), 7, "the inserted stack should remain pending");
        helper.assertTrue(sellBox.balance().isZero(), "insertion into an empty box must not sell immediately");

        CurrencyAmount expected = DailySellMarket.value(helper.getLevel().getServer(), coal);
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
        CurrencyAmount expected = DailySellMarket.value(helper.getLevel().getServer(), oldStack);

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
        CurrencyAmount unitPrice = DailySellMarket.price(
                helper.getLevel().getServer(), new ItemStack(Items.COAL)).orElseThrow();
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
        helper.setBlock(BOX_POS, VillagerRetaliationBlocks.SELL_BOX.get());
        if (helper.getBlockEntity(BOX_POS) instanceof SellBoxBlockEntity sellBox) {
            return sellBox;
        }
        throw new IllegalStateException("Sell box block entity was not created");
    }
}
