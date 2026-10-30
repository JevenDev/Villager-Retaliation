package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("settlement_simulation")
@PrefixGameTestTemplate(false)
public final class SettlementSimulationGameTests {
    private SettlementSimulationGameTests() {
    }

    @GameTest(template = "empty", batch = "settlement_simulation")
    public static void auxiliaryWorkYieldsFairlyAndDeduplicatesRequests(GameTestHelper helper) {
        var simulation = new SettlementSimulationService();
        var seen = new ArrayList<Integer>();
        simulation.enqueue("large", SettlementWork.each(java.util.stream.IntStream.range(0, 100).boxed().toList(), seen::add));
        simulation.enqueue("small", () -> seen.add(-1));
        simulation.enqueue("small", () -> seen.add(-2));
        helper.assertTrue(simulation.runPending(), "one work unit is available");
        helper.assertValueEqual(seen, java.util.List.of(0), "a large job advances only one element");
        simulation.runPending();
        helper.assertValueEqual(seen, java.util.List.of(0, -1), "short work receives a turn before the large job finishes");
        for (int step = 0; step < 99; step++) simulation.runPending();
        helper.assertValueEqual(seen.size(), 101, "each element runs exactly once and duplicate requests do not enter the queue");
        helper.assertFalse(simulation.runPending(), "completed tasks leave no historical queue entries");
        simulation.enqueue("cancelled", () -> seen.add(-3));
        simulation.invalidate();
        helper.assertFalse(simulation.runPending(), "lifecycle invalidation discards stale work");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_simulation")
    public static void nestedWorkPreservesOrderAndCancelsOnlyAtSafePoints(GameTestHelper helper) {
        var seen = new ArrayList<Integer>();
        boolean[] valid = {true};
        var work = SettlementWork.guard(new SettlementWork(java.util.List.of(
                SettlementWork.each(java.util.List.of(1, 2), seen::add),
                SettlementWork.lazy(() -> () -> seen.add(3)))), () -> valid[0]);
        work.run();
        helper.assertValueEqual(seen, java.util.List.of(1), "one call does not drain a nested sequence");
        work.run();
        work.run();
        helper.assertValueEqual(seen, java.util.List.of(1, 2), "lazy planning is a separate work unit");
        valid[0] = false;
        work.run();
        helper.assertTrue(SettlementWork.complete(work), "invalidated plans retire without applying later mutations");
        helper.assertValueEqual(seen, java.util.List.of(1, 2), "completed atomic mutations are preserved");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_simulation")
    public static void capRotatesEveryManagedSettlementFairly(GameTestHelper helper) {
        var towns = new ArrayList<SettlementRecord>();
        for (int index = 0; index < 203; index++) {
            towns.add(new SettlementRecord(new VillageAllegianceId(new UUID(0L, index)),
                    helper.getLevel().dimension().location(), BlockPos.ZERO, UUID.randomUUID(), 0L, () -> {}));
        }
        helper.assertTrue(SettlementSimulationService.select(towns, 0L, 0).isEmpty(), "zero disables background work");
        var counts = new HashMap<VillageAllegianceId, Integer>();
        for (long day = 0; day < 203; day++) {
            var selected = SettlementSimulationService.select(towns, day, 100);
            helper.assertValueEqual(selected.size(), 100, "the daily cap is exact");
            selected.forEach(town -> counts.merge(town.id(), 1, Integer::sum));
            java.util.Collections.reverse(towns);
            helper.assertValueEqual(SettlementSimulationService.select(towns, day, 100), selected,
                    "selection is stable across save and map order");
        }
        helper.assertValueEqual(counts.size(), 203, "every managed settlement receives turns");
        helper.assertTrue(counts.values().stream().allMatch(count -> count == 100), "rotation gives equal service over a full period");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_simulation")
    public static void delayedQueueConsumesEveryScheduledInterval(GameTestHelper helper) {
        var level = helper.getLevel();
        var center = helper.absolutePos(new BlockPos(2, 2, 2));
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), center, "");
        var town = new SettlementRecord(id, level.dimension().location(), center, UUID.randomUUID(), 0L, () -> {});
        var resident = EntityType.VILLAGER.create(level);
        resident.setPos(center.getCenter());
        resident.setAge(-1000);
        VillageAllegianceApi.assignKnown(level, resident, id, AllegianceAssignmentSource.EXPLICIT_API);
        var state = SettlementResidentState.capture(resident, town, 0L);
        town.recordResident(state);
        var entry = new SettlementSimulationService.Entry(level, town, 200L);
        int cycles = 0;
        for (int steps = 0; steps < SettlementSimulationService.MAX_STEPS_PER_TICK && cycles < 3; steps++) {
            if (entry.step()) cycles++;
        }
        helper.assertValueEqual(cycles, 3, "three queued cycles finish with bounded steps");
        helper.assertValueEqual(state.checkedAt(), 600L, "queued work consumes its scheduled time rather than the later wall clock");
        helper.assertValueEqual(state.age(), -400, "a delayed queue does not discard elapsed resident time");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_simulation")
    public static void pausedSettlementResumesWithoutCatchupAndPersistsItsClock(GameTestHelper helper) {
        var level = helper.getLevel();
        var town = new SettlementRecord(VillageAllegianceId.random(), level.dimension().location(), BlockPos.ZERO,
                UUID.randomUUID(), 0L, () -> {});
        town.resumeSimulation(24000L);
        var restored = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
        helper.assertValueEqual(restored.simulationResumedAt(), 24000L, "the resume boundary survives a save");
        SettlementSimulationClock.run(restored, () -> {
            helper.assertValueEqual(SettlementSimulationClock.elapsed(200L, 24000L), 0, "paused days award no work");
            helper.assertValueEqual(SettlementSimulationClock.elapsed(200L, 24040L), 40, "only running ticks count after resuming");
            helper.assertValueEqual(SettlementSimulationClock.elapsed(24050L, 24040L), 0, "newer native observations remain authoritative");
        });
        helper.assertValueEqual(SettlementSimulationClock.elapsed(0L, 100L), 100, "one settlement cannot alter another clock");
        helper.succeed();
    }
}
