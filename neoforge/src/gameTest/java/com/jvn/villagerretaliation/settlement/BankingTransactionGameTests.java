package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyPayment;
import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.sell.CurrencyAmount;
import com.mojang.authlib.GameProfile;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("bank_trade")
@PrefixGameTestTemplate(false)
public final class BankingTransactionGameTests {
    @GameTest(template = "empty")
    public static void fullInventoryAndComponentMismatchesCannotDebitTreasury(GameTestHelper h) {
        ServerPlayer leader = player(h);
        SettlementRecord town = town(h, leader);
        CurrencyAmount opening = CurrencyAmount.of(301, 3);
        town.economy().credit(opening);
        for (int slot = 0; slot < leader.getInventory().items.size(); slot++) leader.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        ItemStack named = new ItemStack(Items.EMERALD, 63);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Keepsake"));
        leader.getInventory().setItem(0, named);
        h.assertFalse(SettlementTradeService.withdrawCurrency(leader, town.id(), 1), "unmergeable named currency cannot provide capacity");
        h.assertValueEqual(town.economy().balance(), opening, "full inventory keeps every treasury fraction");
        h.assertValueEqual(SettlementManagementValidation.selection(leader, town, new SettlementManagementActionPayload.Treasury(false, 1)), "withdrawal_space", "failure explains making room");
        leader.getInventory().setItem(1, new ItemStack(Items.EMERALD, 63));
        h.assertFalse(SettlementTradeService.withdrawCurrency(leader, town.id(), 2), "partial room cannot debit full withdrawal");
        h.assertValueEqual(leader.getInventory().getItem(1).getCount(), 63, "failed capacity check transfers nothing");
        h.assertTrue(SettlementTradeService.withdrawCurrency(leader, town.id(), 1), "exact merge capacity receives withdrawal");
        h.assertValueEqual(leader.getInventory().getItem(1).getCount(), 64, "only matching currency grows");
        h.assertValueEqual(leader.getInventory().getItem(0).getCount(), 63, "named stack is unchanged");
        leader.getInventory().offhand.set(0, new ItemStack(Items.EMERALD, 62));
        h.assertTrue(SettlementTradeService.withdrawCurrency(leader, town.id(), 2), "vanilla occupied offhand merge is honored");
        h.assertValueEqual(leader.getOffhandItem().getCount(), 64, "offhand receives exact amount");
        leader.getInventory().offhand.set(0, ItemStack.EMPTY);
        h.assertFalse(SettlementTradeService.withdrawCurrency(leader, town.id(), 1), "empty offhand is not a vanilla insertion slot");
        var restored = SettlementRecord.load(town.save(h.getLevel().registryAccess()), h.getLevel().registryAccess(), () -> {});
        h.assertValueEqual(restored.economy().balance(), opening.subtract(CurrencyAmount.of(3, 1)), "only completed transfers persist as debits");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void repeatedDepositsWithdrawalsPermissionsAndAmountBoundsConserveFunds(GameTestHelper h) {
        ServerPlayer leader = player(h);
        SettlementRecord town = town(h, leader);
        leader.getInventory().setItem(0, new ItemStack(Items.EMERALD, 10));
        h.assertFalse(SettlementTradeService.depositCurrency(leader, town.id(), 0), "zero deposit fails");
        h.assertFalse(SettlementTradeService.depositCurrency(leader, town.id(), 4097), "configured transfer bound remains enforced");
        h.assertTrue(SettlementTradeService.depositCurrency(leader, town.id(), 10), "deposit consumes real currency");
        h.assertFalse(SettlementTradeService.depositCurrency(leader, town.id(), 10), "replayed deposit cannot reuse consumed currency");
        h.assertValueEqual(town.economy().balance(), CurrencyAmount.of(10, 1), "only funded deposit is credited");
        ServerPlayer delegate = player(h);
        town.setPermissions(leader.getUUID(), delegate.getUUID(), Set.of(SettlementPermission.TREASURY));
        h.assertFalse(SettlementTradeService.withdrawCurrency(delegate, town.id(), 1), "treasury delegation does not grant leader withdrawals");
        h.assertValueEqual(SettlementManagementValidation.selection(delegate, town, new SettlementManagementActionPayload.Treasury(false, 1)), "leader_required", "authority failure explains leader requirement");
        h.assertFalse(SettlementTradeService.withdrawCurrency(leader, town.id(), 0), "zero withdrawal fails");
        h.assertFalse(SettlementTradeService.withdrawCurrency(leader, town.id(), 4097), "oversized withdrawal fails");
        h.assertTrue(SettlementTradeService.withdrawCurrency(leader, town.id(), 5), "first withdrawal completes");
        h.assertTrue(SettlementTradeService.withdrawCurrency(leader, town.id(), 5), "second funded withdrawal completes");
        h.assertFalse(SettlementTradeService.withdrawCurrency(leader, town.id(), 5), "replay cannot overdraw balance");
        h.assertValueEqual(VillagerCurrencyPayment.count(leader), 10, "all original funds return exactly once");
        h.assertTrue(town.economy().balance().isZero(), "no virtual funds are duplicated");
        h.succeed();
    }

    // Focused entry points keep the existing real trading fixtures selectable without the entire default namespace.
    @GameTest(template = "empty", batch = "settlement_trade")
    public static void existingPurchasePriceAndBudgetLimits(GameTestHelper h) {
        SettlementTradeGameTests.purchasesRespectPriceOrderBudgetAndFiniteSharedCommoditySupply(h);
    }

    @GameTest(template = "empty", batch = "settlement_trade")
    public static void existingSaleComponentAndReserveProtection(GameTestHelper h) {
        SettlementTradeGameTests.standingSalesKeepReservesComponentsAndProtectedInputs(h);
    }

    @GameTest(template = "empty", batch = "settlement_trade")
    public static void existingCapacityAndAvailabilityFailures(GameTestHelper h) {
        SettlementTradeGameTests.fullDepotsAndUnstaffedOrBrokenTradingPostsCannotTrade(h);
    }

    @GameTest(template = "empty", batch = "settlement_trade")
    public static void existingMarketLimitsAndPersistence(GameTestHelper h) {
        SettlementTradeGameTests.tradeStopsAtFiniteDemandAndPersistsTownWideSpending(h);
    }

    @GameTest(template = "empty")
    public static void existingLegacyRefundMigration(GameTestHelper h) {
        com.jvn.villagerretaliation.trade.VillagerTradeRequestGameTests.canceledPrepaidOrderRefundPersistsAndDeliversOnceAtOwningVillager(h);
    }

    @GameTest(template = "empty")
    public static void existingRefundOverflow(GameTestHelper h) {
        com.jvn.villagerretaliation.trade.VillagerTradeRequestGameTests.refundInventoryOverflowDropsSafelyAtPlayer(h);
    }

    @GameTest(template = "empty")
    public static void existingStaleQuoteRevalidation(GameTestHelper h) {
        com.jvn.villagerretaliation.sell.SellBoxGameTests.groupedItemsSharePressureAndServerRejectsStaleQuotes(h);
    }

    private static ServerPlayer player(GameTestHelper h) {
        return new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "BankOwner"));
    }

    private static SettlementRecord town(GameTestHelper h, ServerPlayer leader) {
        BlockPos center = h.absolutePos(new BlockPos(1, 2, 1));
        var registry = VillageAllegianceRegistrySavedData.get(h.getLevel());
        var id = registry.create(h.getLevel().getGameTime(), h.getLevel().dimension().location(), center, "");
        return SettlementSavedData.get(h.getLevel()).establish(registry, id, leader.getUUID(), center, h.getLevel().getGameTime()).orElseThrow();
    }
}
