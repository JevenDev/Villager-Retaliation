package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.sell.CurrencyAmount;
import com.jvn.villagerretaliation.storage.*;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("bank_treasury")
@PrefixGameTestTemplate(false)
public final class BankTreasuryGameTests {
    @GameTest(template="empty") public static void physicalFundsFallbackAndExactSpending(GameTestHelper h) {
        var economy=new SettlementEconomy(()->{}); economy.bank.bind(h.getLevel().getServer(),h.getLevel().registryAccess());
        economy.credit(CurrencyAmount.of(19,2));
        h.assertValueEqual(economy.balance(),CurrencyAmount.of(19,2),"absent storage retains exact virtual balance");
        var stock=economy.bank.newStock(BlockPos.ZERO,27); economy.bank.stocks.put(UUID.randomUUID(),stock);
        stock.inventory().setItem(0,new ItemStack(Items.DIAMOND,7)); economy.depositOverflow();
        h.assertValueEqual(stock.inventory().countItem(Items.EMERALD),9,"whole units migrate once to physical currency");
        h.assertValueEqual(economy.balance(),CurrencyAmount.of(19,2),"physical plus virtual is never double counted");
        h.assertTrue(economy.withdraw(CurrencyAmount.of(5,4)),"fractional debit can use physical funds");
        h.assertValueEqual(economy.balance(),CurrencyAmount.of(33,4),"fractional change conserved exactly");
        h.assertValueEqual(stock.inventory().countItem(Items.DIAMOND),7,"noncurrency never consumed");
        var saved=economy.save(); var restored=SettlementEconomy.load(saved,()->{});
        restored.bank.load(saved.getCompound("BankStorage"),h.getLevel().registryAccess()); restored.bank.bind(h.getLevel().getServer(),h.getLevel().registryAccess());
        h.assertValueEqual(restored.balance(),economy.balance(),"stock and virtual remainder reload together");
        for(int i=0;i<27;i++) stock.inventory().setItem(i,new ItemStack(Items.STONE,64));
        var before=economy.balance(); economy.credit(CurrencyAmount.of(100,1));
        h.assertValueEqual(economy.balance(),before.add(CurrencyAmount.of(100,1)),"full storage retains funds virtually");
        stock.inventory().setItem(26,ItemStack.EMPTY); economy.depositOverflow();
        h.assertValueEqual(stock.inventory().getItem(26).getCount(),64,"available space receives pending funds");
        h.assertFalse(economy.withdraw(CurrencyAmount.of(1000,1)),"insufficient debit changes nothing"); h.succeed();
    }
    @GameTest(template="empty") public static void registeredTreasuryLeaderBreachAndMultipleVaults(GameTestHelper h) {
        var bank=BankStorageGameTests.place(h,new BlockPos(1,2,1),StorageRegistry.SAFE.get());
        var leader=BankStorageGameTests.player(h,bank); bank.issue(leader);
        var registry=VillageAllegianceRegistrySavedData.get(h.getLevel());
        // Place the town in a dedicated remote chunk, away from other settlement test fixtures.
        var id=registry.create(h.getLevel().getGameTime(),h.getLevel().dimension().location(),bank.getBlockPos(),"");
        var town=SettlementSavedData.get(h.getLevel()).establish(registry,id,leader.getUUID(),bank.getBlockPos(),h.getLevel().getGameTime()).orElseThrow();
        bank.inventory().setItem(53,new ItemStack(Items.DIAMOND,2)); town.economy().credit(CurrencyAmount.of(15,1));
        h.assertTrue(BankTreasury.register(leader,bank),"leader with key registers treasury");
        h.assertValueEqual(bank.inventory().countItem(Items.EMERALD),15,"existing funds migrate non-destructively");
        h.assertValueEqual(bank.inventory().getItem(53).getCount(),2,"noncurrency and exact slot remain");
        var stranger=BankStorageGameTests.player(h,bank); stranger.getInventory().add(StorageKeys.create(StorageRegistry.KEY.get(),bank.identity));
        h.assertFalse(bank.allowed(stranger),"even matching key does not grant village withdrawal authority");
        h.assertTrue(bank.allowed(leader),"leader key opens");
        var second=BankStorageGameTests.place(h,new BlockPos(3,2,1),StorageRegistry.CONTROLLER.get()); second.issue(leader);
        h.assertTrue(BankTreasury.register(leader,second),"multiple registered vaults supported");
        h.assertValueEqual(town.economy().balance(),CurrencyAmount.of(15,1),"registration never credits twice");
        bank.hit(); h.assertTrue(bank.allowed(stranger),"breach explicitly overrides leader permission");
        var stolen=bank.automation().extractItem(0,4,false);
        h.assertValueEqual(stolen.getCount(),4,"breach exposes physical funds to theft");
        h.assertValueEqual(town.economy().balance(),CurrencyAmount.of(11,1),"theft immediately reduces spendable funds");
        var reloadWrites=new java.util.concurrent.atomic.AtomicInteger();
        var reload=SettlementRecord.load(town.save(h.getLevel().registryAccess()),h.getLevel().registryAccess(),reloadWrites::incrementAndGet);
        h.assertValueEqual(reloadWrites.get(),0,"loading bank stock does not dirty settlement history");
        h.assertValueEqual(reload.revision(),town.revision(),"loading stock preserves the saved settlement revision");
        reload.economy().bank.bind(h.getLevel().getServer(),h.getLevel().registryAccess());
        h.assertValueEqual(reload.economy().balance(),CurrencyAmount.of(11,1),"stolen funds are not recreated on restart");
        h.succeed();
    }
    @GameTest(template="empty") public static void vaultReassemblyRetainsTreasuryRegistration(GameTestHelper h) {
        var bank=BankStorageGameTests.vault(h,new BlockPos(2,3,1),net.minecraft.core.Direction.NORTH);
        var leader=BankStorageGameTests.player(h,bank); bank.issue(leader);
        var registry=VillageAllegianceRegistrySavedData.get(h.getLevel());
        var id=registry.create(h.getLevel().getGameTime(),h.getLevel().dimension().location(),bank.getBlockPos(),"");
        var town=SettlementSavedData.get(h.getLevel()).establish(registry,id,leader.getUUID(),bank.getBlockPos(),h.getLevel().getGameTime()).orElseThrow();
        bank.inventory().setItem(0,new ItemStack(Items.EMERALD,13));
        h.assertTrue(BankTreasury.register(leader,bank),"complete vault registers all member stocks");
        h.assertTrue(bank.localInventory().isEmpty(),"registered stock lives outside the block inventory");
        h.assertValueEqual(BankStorageGameTests.display(bank).countItem(Items.EMERALD),13,"vault displays authoritative treasury stock");
        bank.inventory().removeItem(0,3);
        h.assertValueEqual(BankStorageGameTests.display(bank).countItem(Items.EMERALD),10,"treasury withdrawals update displayed contents");
        bank.inventory().setItem(0,new ItemStack(Items.EMERALD,13));
        var removed=bank.group().getFirst().getBlockPos(); h.getLevel().destroyBlock(removed,true);
        h.assertFalse(bank.vaultFormed(),"missing member disables treasury access");
        h.assertFalse(bank.allowed(leader),"incomplete treasury cannot open");
        h.assertFalse(BankTreasury.register(leader,bank),"incomplete treasury cannot register");
        h.getLevel().setBlockAndUpdate(removed,StorageRegistry.VAULT.get().defaultBlockState());
        VaultConnections.merge(bank);
        h.assertTrue(bank.vaultFormed() && bank.allowed(leader),"restored cube retains its leader and key");
        h.assertValueEqual(bank.treasury,town.id().value(),"registration survives reassembly");
        h.assertValueEqual(town.economy().balance(),CurrencyAmount.of(13,1),"reassembly never creates or discards funds"); h.succeed();
    }
}
