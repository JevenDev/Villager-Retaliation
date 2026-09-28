package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementWorkerHandoffGameTests {
    private SettlementWorkerHandoffGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_handoff")
    public static void farmReturnsWithoutReplayingInventory(GameTestHelper helper) {
        repeatedReturn(helper, SettlementAreaType.FARM);
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_handoff")
    public static void fisheryReturnsWithoutReplayingInventory(GameTestHelper helper) {
        repeatedReturn(helper, SettlementAreaType.FISHERY);
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_handoff")
    public static void huntingGroundsReturnsWithoutReplayingInventory(GameTestHelper helper) {
        repeatedReturn(helper, SettlementAreaType.HUNTING_GROUNDS);
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_handoff")
    public static void forestryReturnsWithoutReplayingInventory(GameTestHelper helper) {
        repeatedReturn(helper, SettlementAreaType.FORESTRY);
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_handoff")
    public static void mineReturnsWithoutReplayingInventory(GameTestHelper helper) {
        repeatedReturn(helper, SettlementAreaType.MINE);
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_handoff")
    public static void animalPenReturnsWithoutReplayingInventory(GameTestHelper helper) {
        repeatedReturn(helper, SettlementAreaType.ANIMAL_PEN);
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_handoff")
    public static void kitchenReturnsWithoutReplayingInventory(GameTestHelper helper) {
        repeatedReturn(helper, SettlementAreaType.KITCHEN);
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_handoff")
    public static void workshopReturnsWithoutReplayingInventory(GameTestHelper helper) {
        repeatedReturn(helper, SettlementAreaType.WORKSHOP);
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_handoff")
    public static void smelteryReturnsWithoutReplayingInventory(GameTestHelper helper) {
        repeatedReturn(helper, SettlementAreaType.SMELTERY);
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_handoff")
    public static void breweryReturnsWithoutReplayingInventory(GameTestHelper helper) {
        repeatedReturn(helper, SettlementAreaType.BREWERY);
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_handoff")
    public static void depotReturnsWithoutReplayingInventory(GameTestHelper helper) {
        repeatedReturn(helper, SettlementAreaType.DEPOT);
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_handoff")
    public static void tradingPostReturnsWithoutReplayingInventory(GameTestHelper helper) {
        repeatedReturn(helper, SettlementAreaType.TRADING_POST);
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_handoff")
    public static void guardPostReturnsWithoutReplayingInventory(GameTestHelper helper) {
        repeatedReturn(helper, SettlementAreaType.GUARD_POST);
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_handoff")
    public static void constructionReturnsWithoutReplayingInventory(GameTestHelper helper) {
        repeatedReturn(helper, SettlementAreaType.CONSTRUCTION);
    }

    private static void repeatedReturn(GameTestHelper helper, SettlementAreaType type) {
        var level = helper.getLevel();
        var center = helper.absolutePos(new BlockPos(1, 2, 1));
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, 0L).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), type, "Work", center, center.offset(3, 2, 3), 5, true);
        town.putArea(town.leader(), area);
        var villager = EntityType.VILLAGER.create(level);
        villager.setPos(center.getCenter());
        VillageAllegianceApi.assignKnown(level, villager, id, AllegianceAssignmentSource.EXPLICIT_API);
        helper.assertTrue(town.assignDuty(villager.getUUID(), area.id()), "the resident has a civic workplace");
        SettlementWorkforceService.reconcile(level, villager);
        var inventory = HiredJobInventory.getJobInventory(villager);
        inventory.setItem(HiredJobInventory.HOTBAR_START, new ItemStack(Items.OAK_LOG, 8));
        HiredWorkStateStore.state(villager).putBoolean("AutoDepositOutputs", false);
        SettlementSimulationHandoff.stopTicking(villager);
        var worker = town.workers().get(villager.getUUID());
        helper.assertValueEqual(worker.role(), type.role(), "the captured role matches the workplace");
        for (int cycle = 0; cycle < 3; cycle++) {
            worker.work(35, 1.0D);
            var items = worker.items(level.registryAccess());
            items.get(HiredJobInventory.HOTBAR_START).shrink(1);
            items.set(HiredJobInventory.MAIN_GRID_START, new ItemStack(Items.OAK_PLANKS, 4));
            worker.updateInventory(level.registryAccess(), items);
            helper.assertTrue(worker.spend(20.0D), "completed work consumes its budget");
            worker = SettlementWorkerState.load(worker.save());
            town.recordWorker(worker);
            SettlementBackgroundService.onActive(villager);
            helper.assertValueEqual(inventory.countItem(Items.OAK_LOG), 7 - cycle, "spent supplies return exactly once");
            helper.assertValueEqual(inventory.countItem(Items.OAK_PLANKS), 4, "background output returns exactly once");
            helper.assertValueEqual(worker.progress(), 15.0D * (cycle + 1), "unfinished credit survives persistence and return");
            inventory.setItem(HiredJobInventory.MAIN_GRID_START, ItemStack.EMPTY);
            SettlementBackgroundService.onActive(villager);
            helper.assertValueEqual(inventory.countItem(Items.OAK_PLANKS), 0, "repeated activation cannot replay collected output");
            SettlementSimulationHandoff.stopTicking(villager);
            helper.assertValueEqual(worker.progress(), 15.0D * (cycle + 1), "observing the same duty retains unfinished credit");
            helper.assertFalse(worker.settings().getBoolean("AutoDepositOutputs"), "worker configuration survives the handoff");
            helper.assertValueEqual(worker.items(level.registryAccess()).get(HiredJobInventory.MAIN_GRID_START).getCount(),
                    0, "the next background snapshot captures native inventory changes");
        }
        var replacement = new SettlementArea(UUID.randomUUID(), type, "New work", center, center.offset(3, 2, 3), 5, true);
        town.putArea(town.leader(), replacement);
        worker.observe(level, villager, new SettlementWorkforceService.Duty(town, replacement), level.getGameTime());
        helper.assertValueEqual(worker.progress(), 0.0D, "credit cannot carry into a different assignment");
        helper.succeed();
    }
}
