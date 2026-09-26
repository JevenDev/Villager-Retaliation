package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementMiningRouteGameTests {
    private SettlementMiningRouteGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_routes")
    public static void distantOreUsesARealRouteBeyondTheOldSearchNeighborhood(GameTestHelper helper) {
        fixture(helper, fixture -> {
            int chunks = helper.getLevel().getChunkSource().getLoadedChunksCount();
            var routes = routes(fixture);
            var result = routes.target(fixture.villager(), fixture.ore(), new SettlementMiningRoutes.Budget());
            helper.assertTrue(result != null && result.steps() > 200, "directed search reaches ore across the full observed mine");
            helper.assertValueEqual(result.steps(), 234, "the open floor route charges every horizontal step to the nearest stance");
            for (int now = 200; now <= 10_000; now += 200) {
                SettlementBackgroundService.update(helper.getLevel(), fixture.town(), now);
                if (now <= 2_000) helper.assertValueEqual(outputs(helper, fixture), 0, "search and long travel cannot harvest instantly");
            }
            helper.assertValueEqual(outputs(helper, fixture), 1, "the distant finite ore eventually reaches the worker inventory");
            helper.assertTrue(fixture.mine().getBlockState(fixture.ore()).isAir(), "the distant cut is recorded in the mine ledger");
            helper.assertValueEqual(helper.getLevel().getChunkSource().getLoadedChunksCount(), chunks, "long routing never forces any chunk to load");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_routes")
    public static void miningRouteSearchContinuesAcrossBoundedUpdates(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var routes = routes(fixture);
            var result = routes.target(fixture.villager(), fixture.ore(), new SettlementMiningRoutes.Budget(8));
            helper.assertTrue(result == null && routes.pending(fixture.ore()), "a small search budget leaves a pending frontier");
            helper.assertValueEqual(outputs(helper, fixture), 0, "a partial route produces no loot");
            for (int update = 0; update < 40 && result == null; update++) {
                var continued = routes(fixture);
                helper.assertTrue(continued == routes, "unchanged observed terrain preserves the search frontier");
                result = continued.target(fixture.villager(), fixture.ore(), new SettlementMiningRoutes.Budget(8));
            }
            helper.assertTrue(result != null && result.steps() == 234, "successive bounded updates finish the same exact route");
            helper.assertFalse(routes.pending(fixture.ore()), "a completed route releases its pending frontier");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_routes")
    public static void longMiningRoutesPayForRealWallDetours(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var nearby = fixture.origin().offset(16, 1, 6);
            fixture.mine().change(Map.of(fixture.ore(), Blocks.AIR.defaultBlockState(), nearby, Blocks.IRON_ORE.defaultBlockState()));
            for (BlockPos position : BlockPos.betweenClosed(fixture.origin().offset(8, 1, -2), fixture.origin().offset(8, 2, 100))) {
                set(fixture.chunks().get(new ChunkPos(position)), position, Blocks.STONE.defaultBlockState());
            }
            for (var chunk : fixture.chunks().values()) {
                if (chunk.getPos().x == fixture.origin().getX() >> 4) {
                    SettlementMineService.onChunkUnload(new ChunkEvent.Unload(chunk));
                }
            }
            var routes = routes(fixture);
            SettlementMiningRoutes.Target result = null;
            for (int update = 0; update < 40 && result == null; update++) {
                result = routes.target(fixture.villager(), nearby, new SettlementMiningRoutes.Budget());
            }
            helper.assertTrue(result != null && result.steps() > 190, "the route walks around the far end instead of crossing the wall");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_routes")
    public static void changedTerrainInvalidatesPendingAndCompletedMiningRoutes(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var pending = routes(fixture);
            pending.target(fixture.villager(), fixture.ore(), new SettlementMiningRoutes.Budget(8));
            helper.assertTrue(pending.pending(fixture.ore()), "the initial long route is still searching");
            var barrier = new LinkedHashMap<BlockPos, BlockState>();
            for (BlockPos position : BlockPos.betweenClosed(fixture.worker().position().offset(-1, 0, -1),
                    fixture.worker().position().offset(1, 1, 1))) {
                if (position.getX() != fixture.worker().position().getX() || position.getZ() != fixture.worker().position().getZ()) {
                    barrier.put(position.immutable(), Blocks.STONE.defaultBlockState());
                }
            }
            fixture.mine().change(barrier);
            var blocked = routes(fixture);
            helper.assertTrue(blocked != pending, "terrain edits discard a frontier built before the wall");
            helper.assertTrue(blocked.target(fixture.villager(), fixture.ore(), new SettlementMiningRoutes.Budget()) == null
                    && !blocked.pending(fixture.ore()), "a sealed starting chamber has no route");
            barrier.replaceAll((position, block) -> Blocks.AIR.defaultBlockState());
            fixture.mine().change(barrier);
            var reopened = routes(fixture);
            helper.assertTrue(reopened.target(fixture.villager(), fixture.ore(), new SettlementMiningRoutes.Budget()) != null,
                    "opening the chamber invalidates its old unreachable result");
            fixture.mine().change(Map.of(fixture.worker().position().above(), Blocks.STONE.defaultBlockState()));
            var unsafe = routes(fixture);
            helper.assertTrue(unsafe != reopened && unsafe.target(fixture.villager(), fixture.ore(), new SettlementMiningRoutes.Budget()) == null,
                    "a completed route cannot survive a newly blocked starting body");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_routes")
    public static void nativeTerrainRefreshInvalidatesCachedMiningRoutes(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var first = routes(fixture);
            helper.assertTrue(first.target(fixture.villager(), fixture.ore(), new SettlementMiningRoutes.Budget()) != null, "the initial route is complete");
            var chunk = fixture.chunks().get(new ChunkPos(fixture.worker().position()));
            set(chunk, fixture.worker().position().above(), Blocks.STONE.defaultBlockState());
            SettlementMineService.onChunkUnload(new ChunkEvent.Unload(chunk));
            var updated = routes(fixture);
            helper.assertTrue(updated != first, "a native block edit changes the observation even without a background journal revision");
            helper.assertTrue(updated.target(fixture.villager(), fixture.ore(), new SettlementMiningRoutes.Budget()) == null,
                    "a cached path cannot bypass the refreshed physical obstruction");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_routes")
    public static void unobservedMineSectionsCannotConnectDistantOre(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var missing = fixture.mine().sections().values().stream()
                    .filter(section -> section.origin().getX() == (fixture.origin().getX() + 16 & ~15)).toList();
            missing.forEach(section -> fixture.mine().remove(section.origin()));
            var routes = routes(fixture);
            SettlementMiningRoutes.Target result = null;
            for (int update = 0; update < 20; update++) {
                result = routes.target(fixture.villager(), fixture.ore(), new SettlementMiningRoutes.Budget());
                if (!routes.pending(fixture.ore())) break;
            }
            helper.assertTrue(result == null && !routes.pending(fixture.ore()), "a missing observed strip breaks every route across the mine");
            for (var section : missing) fixture.mine().record(section);
            var restored = routes(fixture);
            helper.assertTrue(restored != routes && restored.target(fixture.villager(), fixture.ore(), new SettlementMiningRoutes.Budget()) != null,
                    "observing the missing strip enables a fresh safe route");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_routes")
    public static void partiallyTravelledMiningRoutesSurviveWorkerReloads(GameTestHelper helper) {
        fixture(helper, fixture -> {
            plan(fixture);
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 200L);
            helper.assertValueEqual(outputs(helper, fixture), 0, "a paid partial journey cannot harvest distant ore");
            helper.assertTrue(fixture.worker().settings().contains("SettlementMiningPosition"), "partial travel records an observed route position");
            var position = BlockPos.of(fixture.worker().settings().getLong("SettlementMiningPosition"));
            helper.assertTrue(!position.equals(fixture.worker().position()) && position.distManhattan(fixture.worker().position()) == 12,
                    "two hundred ticks pay for twelve real route steps");
            var restored = SettlementWorkerState.load(fixture.worker().save());
            fixture.town().recordWorker(restored);
            for (int now = 400; now <= 6_000; now += 200) SettlementBackgroundService.update(helper.getLevel(), fixture.town(), now);
            int ore = restored.items(helper.getLevel().registryAccess()).stream()
                    .filter(stack -> stack.is(Items.RAW_IRON)).mapToInt(ItemStack::getCount).sum();
            helper.assertValueEqual(ore, 1, "a saved partial journey resumes from its paid position and completes once");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_routes")
    public static void miningJourneysCanExceedTheWorkCreditStorageLimit(GameTestHelper helper) {
        fixture(helper, fixture -> {
            for (int wall = 1; wall <= 12; wall++) {
                int start = wall % 2 == 1 ? -2 : 4;
                int end = wall % 2 == 1 ? 108 : 129;
                for (BlockPos position : BlockPos.betweenClosed(fixture.origin().offset(wall * 8, 1, start),
                        fixture.origin().offset(wall * 8, 2, end))) {
                    set(fixture.chunks().get(new ChunkPos(position)), position, Blocks.STONE.defaultBlockState());
                }
            }
            for (var chunk : fixture.chunks().values()) SettlementMineService.onChunkUnload(new ChunkEvent.Unload(chunk));
            var routes = routes(fixture);
            SettlementMiningRoutes.Target target = null;
            for (int update = 0; update < 100 && target == null; update++) {
                target = routes.target(fixture.villager(), fixture.ore(), new SettlementMiningRoutes.Budget());
            }
            helper.assertTrue(target != null && target.steps() > 1_500 && target.steps() <= 1_510,
                    "the observed maze requires slightly more than the maximum stored travel credit");
            plan(fixture);
            fixture.worker().work(24_000, 1.0D);
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 200L);
            helper.assertValueEqual(outputs(helper, fixture), 0, "a long journey first spends its credit on travelling");
            helper.assertValueEqual(BlockPos.of(fixture.worker().settings().getLong("SettlementMiningPosition")), target.path().get(1_500),
                    "the worker advances along exactly the affordable part of the verified path");
            helper.assertValueEqual(fixture.worker().progress(), 0.0D, "paid travel is deducted instead of waiting forever at the credit ceiling");
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 400L);
            helper.assertValueEqual(outputs(helper, fixture), 1, "the remaining paid steps and breaking finish on a later update");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_routes")
    public static void unreachablePlannedOreDoesNotHideAnotherPendingRoute(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var unsafe = fixture.origin().offset(10, 1, 10);
            fixture.mine().change(Map.of(unsafe, Blocks.IRON_ORE.defaultBlockState(), unsafe.above(), Blocks.WATER.defaultBlockState()));
            var routes = routes(fixture);
            helper.assertTrue(routes.target(fixture.villager(), unsafe, new SettlementMiningRoutes.Budget()) == null,
                    "the uncontained ore cannot be harvested");
            routes.target(fixture.villager(), fixture.ore(), new SettlementMiningRoutes.Budget(8));
            helper.assertTrue(routes.pending(fixture.ore()), "the different distant target has a pending route");
            plan(fixture, List.of(unsafe, fixture.ore()));
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), 200L);
            helper.assertTrue(fixture.worker().settings().contains("SettlementMiningPosition"),
                    "skipping an unreachable planned target still lets the next route resume and begin paid travel");
            helper.assertValueEqual(outputs(helper, fixture), 0, "resuming a route does not skip its remaining travel time");
        });
    }

    private static void plan(Fixture fixture) {
        plan(fixture, List.of(fixture.ore()));
    }

    private static void plan(Fixture fixture, List<BlockPos> targets) {
        var area = fixture.town().areas().get(fixture.worker().area());
        var context = new com.jvn.villagerretaliation.interaction.work.HiredWorkContext(
                HiredJobInventory.getJobInventory(fixture.villager()), fixture.worker().settings(), area.center(),
                area.min(), area.max(), 64, 32, true, 1, false, true);
        com.jvn.villagerretaliation.interaction.work.HiredWorkPlan.replaceWithObjective(context, "ore", targets.getFirst(), targets, 20);
    }

    private static SettlementMiningRoutes routes(Fixture fixture) {
        return SettlementMiningRoutes.forWorker(fixture.worker(), fixture.mine(), fixture.worker().position());
    }

    private static int outputs(GameTestHelper helper, Fixture fixture) {
        return fixture.worker().items(helper.getLevel().registryAccess()).stream()
                .filter(stack -> stack.is(Items.RAW_IRON)).mapToInt(ItemStack::getCount).sum();
    }

    private static void set(LevelChunk chunk, BlockPos position, BlockState state) {
        chunk.getSection(chunk.getSectionIndex(position.getY())).setBlockState(position.getX() & 15,
                position.getY() & 15, position.getZ() & 15, state);
    }

    private static void fixture(GameTestHelper helper, Consumer<Fixture> check) {
        var level = helper.getLevel();
        var test = helper.absolutePos(BlockPos.ZERO);
        var first = new ChunkPos(800_000 + test.getX() * 16, 800_000 + test.getZ() * 16);
        var origin = new BlockPos(first.getMinBlockX(), 64, first.getMinBlockZ());
        var ore = origin.offset(120, 1, 120);
        var chunks = new LinkedHashMap<ChunkPos, LevelChunk>();
        for (int x = -1; x <= 8; x++) {
            for (int z = -1; z <= 8; z++) {
                var chunk = new LevelChunk(level, new ChunkPos(first.x + x, first.z + z));
                for (BlockPos position : BlockPos.betweenClosed(new BlockPos(chunk.getPos().getMinBlockX(), 63, chunk.getPos().getMinBlockZ()),
                        new BlockPos(chunk.getPos().getMaxBlockX(), 64, chunk.getPos().getMaxBlockZ()))) set(chunk, position, Blocks.STONE.defaultBlockState());
                chunks.put(chunk.getPos(), chunk);
            }
        }
        set(chunks.get(new ChunkPos(ore)), ore, Blocks.IRON_ORE.defaultBlockState());
        var center = origin.offset(64, 0, 64);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, 0L).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.MINE, "Wide mine", origin, origin.offset(127, 2, 127), 5, true);
        helper.assertTrue(town.putArea(town.leader(), area), "the wide mine fits its legal area and settlement bounds");
        var home = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HOME, "Home", center.above(8), center.above(8), 5, true);
        town.putArea(town.leader(), home);
        town.recordSurvey(home.id(), new SettlementBuildingSurvey.Survey(0L, List.of(home.min()), List.of(), true));
        for (var chunk : chunks.values()) SettlementMineService.observeArea(level, town, area, chunk);
        var mine = town.mines().get(area.id());
        var villager = EntityType.VILLAGER.create(level);
        villager.setPos(origin.offset(2, 1, 2).getBottomCenter());
        villager.setNoAi(true);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.TOOLSMITH));
        VillageAllegianceApi.assignKnown(level, villager, town.id(), AllegianceAssignmentSource.EXPLICIT_API);
        town.assignDuty(villager.getUUID(), area.id());
        SettlementWorkforceService.reconcile(level, villager);
        HiredWorkStateStore.state(villager).putBoolean("AutoDepositOutputs", false);
        HiredJobInventory.getJobInventory(villager).setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.DIAMOND_PICKAXE));
        var worker = SettlementWorkerState.capture(level, villager, SettlementWorkforceService.duty(villager).orElseThrow(), 0L);
        town.recordWorker(worker);
        town.recordResident(SettlementResidentState.capture(villager, town, 0L));
        long dayTime = level.getDayTime();
        try {
            level.setDayTime(6_000L);
            check.accept(new Fixture(town, mine, origin, ore, chunks, worker, villager));
        } finally {
            level.setDayTime(dayTime);
        }
        helper.succeed();
    }

    private record Fixture(SettlementRecord town, SettlementMine mine, BlockPos origin, BlockPos ore,
                           Map<ChunkPos, LevelChunk> chunks, SettlementWorkerState worker, Villager villager) {
    }
}
