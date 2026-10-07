package com.jvn.villagerretaliation.villagelife;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public final class VillageLifeGameTests {
    private VillageLifeGameTests() {
    }

    @GameTest(template = "empty", batch = "village_occasions")
    public static void occasionsMergePersistDeduplicateAndExpire(GameTestHelper helper) {
        var level = helper.getLevel();
        boolean enabled = VillagerRetaliationConfig.ENABLE_VILLAGE_LIFE.get();
        long time = level.getDayTime();
        try {
            VillagerRetaliationConfig.ENABLE_VILLAGE_LIFE.set(true);
            level.setDayTime(7 * 24_000L + 10_000L);
            var id = com.jvn.villagerretaliation.allegiance.VillageAllegianceId.random();
            var data = new VillageOccasionSavedData();
            data.scheduleMarket(level, id);
            helper.assertTrue(data.schedule(level, id, VillageOccasionSavedData.Kind.BIRTH, "child"), "an accepted birth schedules an occasion");
            data.schedule(level, id, VillageOccasionSavedData.Kind.RAID_VICTORY, "raid");
            helper.assertValueEqual(data.due(level, id).size(), 3, "overlapping occasions merge");
            var saved = data.save(new CompoundTag(), level.registryAccess());
            data = VillageOccasionSavedData.load(saved, level.registryAccess());
            helper.assertFalse(data.schedule(level, id, VillageOccasionSavedData.Kind.BIRTH, "child"), "reload preserves birth deduplication");
            helper.assertValueEqual(data.due(level, id).size(), 3, "reload preserves pending occasions");
            data.consume(level, id);
            data.scheduleMarket(level, id);
            helper.assertTrue(data.due(level, id).isEmpty(), "a consumed market cannot restart in the same week");
            var entry = saved.getList("Villages", Tag.TAG_COMPOUND).getCompound(0);
            entry.putLong("ExpiresAt", level.getGameTime());
            helper.assertTrue(VillageOccasionSavedData.load(saved, level.registryAccess()).due(level, id).isEmpty(),
                    "occasions expire at their elapsed one-day boundary");
            level.setDayTime(8 * 24_000L + 1_000L);
            helper.assertTrue(VillageOccasionSavedData.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess())
                    .due(level, id).isEmpty(), "markets never occur during dawn leisure");
        } finally {
            VillagerRetaliationConfig.ENABLE_VILLAGE_LIFE.set(enabled);
            level.setDayTime(time);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 380, batch = "isolated_village_life_ownership")
    public static void activitiesRespectBudgetsYieldToControllersAndRestoreOwnedTargets(GameTestHelper helper) {
        var fixture = fixture(helper, 8);
        var level = helper.getLevel();
        long[] lastDecisions = {0};
        int[] lastPaths = {0};
        helper.onEachTick(() -> {
            var diagnostics = VillageLifeService.diagnostics(level.getServer());
            helper.assertTrue(diagnostics.decisions() - lastDecisions[0] <= 8, "at most eight decisions run per server tick");
            helper.assertTrue(diagnostics.paths() - lastPaths[0] <= 2, "at most two new ambient paths run per server tick");
            lastDecisions[0] = diagnostics.decisions();
            lastPaths[0] = diagnostics.paths();
        });
        helper.startSequence().thenWaitUntil(() ->
                helper.assertTrue(VillageLifeService.diagnostics(level.getServer()).active() >= 2,
                        "two nearby villagers start activities before controller ownership is tested")).thenExecute(() -> {
            var controlled = fixture.villagers.stream().filter(v -> v.getBrain().hasMemoryValue(MemoryModuleType.PATH)).findFirst().orElseThrow();
            var foreignWalk = new WalkTarget(controlled.blockPosition().east(3), 0.7F, 1);
            var foreignLook = new BlockPosTracker(controlled.blockPosition().west(3));
            var foreignPath = new Path(List.of(), controlled.blockPosition().east(3), true);
            controlled.getBrain().setMemory(MemoryModuleType.WALK_TARGET, foreignWalk);
            controlled.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, foreignLook);
            controlled.getBrain().setMemory(MemoryModuleType.PATH, foreignPath);
            controlled.setTradingPlayer(fixture.player);
            VillageLifeService.beforeTick(controlled);
            helper.assertTrue(controlled.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElseThrow() == foreignWalk,
                    "cancellation preserves another controller's walk target");
            helper.assertTrue(controlled.getBrain().getMemory(MemoryModuleType.LOOK_TARGET).orElseThrow() == foreignLook,
                    "cancellation preserves another controller's attention");
            helper.assertTrue(controlled.getBrain().getMemory(MemoryModuleType.PATH).orElseThrow() == foreignPath,
                    "cancellation preserves another controller's path");
            var threatened = fixture.villagers.stream().filter(v -> v != controlled && v.getBrain().hasMemoryValue(MemoryModuleType.PATH))
                    .findFirst().orElseThrow();
            int activeBeforeDanger = VillageLifeService.diagnostics(level.getServer()).active();
            threatened.setRemainingFireTicks(40);
            VillageLifeService.beforeTick(threatened);
            helper.assertValueEqual(VillageLifeService.diagnostics(level.getServer()).active(), activeBeforeDanger - 1,
                    "danger immediately releases the ambient reservation");
            helper.assertFalse(threatened.getBrain().hasMemoryValue(MemoryModuleType.PATH), "danger releases only the owned path");
            VillagerRetaliationConfig.ENABLE_VILLAGE_LIFE.set(false);
            VillageLifeService.tick(level.getServer());
            helper.assertValueEqual(VillageLifeService.diagnostics(level.getServer()).active(), 0, "disabling clears all reservations");
            helper.assertTrue(controlled.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElseThrow() == foreignWalk,
                    "disabling still preserves other controllers");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 380, batch = "isolated_village_life_failures")
    public static void unreachableDestinationsBackOffWithoutLoadingChunks(GameTestHelper helper) {
        var fixture = fixture(helper, 1);
        var level = helper.getLevel();
        var villager = fixture.villagers.getFirst();
        var target = villager.blockPosition().east(10);
        for (var pos : BlockPos.betweenClosed(target.offset(-2, 0, -2), target.offset(2, 4, 2))) {
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
        }
        villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), target));
        helper.runAtTickTime(180, () -> {
            var diagnostics = VillageLifeService.diagnostics(level.getServer());
            helper.assertTrue(diagnostics.failures() > 0, "the sealed destination produces a real failed path");
            helper.assertTrue(diagnostics.paths() <= 1, "a failed destination backs off for two hundred ticks");
            var remote = target.offset(1_000_000, 0, 1_000_000);
            villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), remote));
            helper.assertFalse(level.hasChunkAt(remote), "the remote destination starts unloaded");
            helper.runAtTickTime(320, () -> {
                helper.assertFalse(level.hasChunkAt(remote), "ambient location handling never loads the remote chunk");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 260, batch = "isolated_village_life_rain")
    public static void rainUsesShelterAndMissingLocationsRemainIdle(GameTestHelper helper) {
        var fixture = fixture(helper, 2);
        var level = helper.getLevel();
        var first = fixture.villagers.getFirst();
        var second = fixture.villagers.getLast();
        var home = first.blockPosition().east(8);
        for (var pos : BlockPos.betweenClosed(home.offset(-2, 3, -2), home.offset(2, 3, 2))) level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
        first.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), home));
        second.getBrain().eraseMemory(MemoryModuleType.MEETING_POINT);
        VillageAllegianceApi.assignUnaffiliated(level, second, AllegianceAssignmentSource.EXPLICIT_API);
        level.setWeatherParameters(0, 10_000, true, false);
        helper.runAtTickTime(180, () -> {
            helper.assertFalse(second.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET), "missing village locations leave the villager idle");
            var walk = first.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElseThrow();
            helper.assertFalse(level.canSeeSky(walk.getTarget().currentBlockPosition()), "rain chooses a reachable sheltered home");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 1_200, batch = "isolated_village_life_courtship")
    public static void completedVisitsFormACoupleAcrossThreeDistinctDays(GameTestHelper helper) {
        var fixture = fixture(helper, 2);
        var level = helper.getLevel();
        level.setDayTime(34_000L);
        var first = fixture.villagers.getFirst();
        var second = fixture.villagers.getLast();
        second.setPos(first.position().add(1.0D, 0.0D, 0.0D));
        for (var villager : fixture.villagers) {
            villager.getBrain().eraseMemory(MemoryModuleType.MEETING_POINT);
        }
        var graph = com.jvn.villagerretaliation.social.VillagerSocialGraphSavedData.get(level);
        for (int tick = 300; tick <= 900; tick += 300) helper.runAtTickTime(tick, () -> {
            level.setDayTime(level.getDayTime() + 24_000L);
            fixture.villagers.forEach(VillageLifeService::yield);
        });
        helper.succeedWhen(() -> helper.assertTrue(graph.relationshipStage(first.getUUID(), second.getUUID()).orElse(null)
                == com.jvn.villagerretaliation.social.VillagerRelationshipStage.DATING,
                "three completed close visits on different days establish an exclusive couple"));
    }

    private static Fixture fixture(GameTestHelper helper, int count) {
        var level = helper.getLevel();
        long time = level.getDayTime();
        boolean enabled = VillagerRetaliationConfig.ENABLE_VILLAGE_LIFE.get();
        int chance = VillagerRetaliationConfig.VILLAGE_LIFE_ACTIVITY_CHANCE.get();
        boolean raining = level.isRaining();
        boolean thunder = level.isThundering();
        VillageLifeService.clear(level.getServer());
        VillagerRetaliationConfig.ENABLE_VILLAGE_LIFE.set(true);
        VillagerRetaliationConfig.VILLAGE_LIFE_ACTIVITY_CHANCE.set(100);
        level.setDayTime(10_000L);
        level.setWeatherParameters(10_000, 0, false, false);
        var origin = helper.absolutePos(new BlockPos(30, 2, 30));
        for (int x = (origin.getX() - 48) >> 4; x <= (origin.getX() + 48) >> 4; x++) {
            for (int z = (origin.getZ() - 48) >> 4; z <= (origin.getZ() + 48) >> 4; z++) level.getChunk(x, z);
        }
        for (var pos : BlockPos.betweenClosed(origin.offset(-16, -1, -16), origin.offset(24, -1, 24))) level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
        var player = helper.makeMockServerPlayerInLevel();
        player.setPos(origin.above().getCenter());
        var id = VillageAllegianceRegistrySavedData.get(level).create(level.getGameTime(), level.dimension().location(), origin, "Village life test");
        var villagers = new ArrayList<Villager>();
        for (int index = 0; index < count; index++) {
            var villager = EntityType.VILLAGER.create(level);
            villager.setPos(origin.offset(index % 4 * 3, 0, index / 4 * 3).getBottomCenter());
            villager.setOnGround(true);
            VillageAllegianceApi.assignKnown(level, villager, id, AllegianceAssignmentSource.EXPLICIT_API);
            level.addFreshEntity(villager);
            villager.getBrain().removeAllBehaviors();
            villager.getBrain().setSchedule(new ScheduleBuilder(new Schedule()).changeActivityAt(0, Activity.IDLE).build());
            villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), origin));
            villagers.add(villager);
        }
        helper.testInfo.addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo test) {}
            @Override public void testPassed(GameTestInfo test, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
            @Override public void testFailed(GameTestInfo test, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
            @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, net.minecraft.gametest.framework.GameTestRunner runner) {}
            private void cleanup() {
                villagers.forEach(Villager::discard);
                player.discard();
                VillageLifeService.clear(level.getServer());
                VillagerRetaliationConfig.ENABLE_VILLAGE_LIFE.set(enabled);
                VillagerRetaliationConfig.VILLAGE_LIFE_ACTIVITY_CHANCE.set(chance);
                level.setDayTime(time);
                level.setWeatherParameters(10_000, 10_000, raining, thunder);
            }
        });
        return new Fixture(villagers, player);
    }

    private record Fixture(List<Villager> villagers, ServerPlayer player) {
    }
}
