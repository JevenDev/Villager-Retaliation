package com.jvn.villagerretaliation.settlement;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementMineFluidGameTests {
    private SettlementMineFluidGameTests() {
    }

    @GameTest(template = "empty", batch = "isolated_mine_water_delay")
    public static void waterFlowsOnlyAfterItsNativeDelayWithoutLoadingChunks(GameTestHelper helper) {
        var fixture = fixture(helper);
        set(fixture.chunk(), fixture.position(), Blocks.WATER.defaultBlockState());
        var mine = observe(fixture);
        open(helper, mine, fixture.position().east());
        int loaded = helper.getLevel().getChunkSource().getLoadedChunksCount();
        SettlementMineFluids.advance(helper.getLevel(), mine, 4L);
        helper.assertTrue(mine.getBlockState(fixture.position().east()).isAir(), "water waits for its fifth tick");
        SettlementMineFluids.advance(helper.getLevel(), mine, 5L);
        helper.assertTrue(mine.getFluidState(fixture.position().east()).is(Fluids.FLOWING_WATER), "native water flows through the opened face");
        helper.assertValueEqual(mine.getFluidState(fixture.position().east()).getAmount(), 7, "horizontal water keeps the native dropoff");
        helper.assertValueEqual(helper.getLevel().getChunkSource().getLoadedChunksCount(), loaded, "fluid calculations cannot load native chunks");
        helper.assertTrue(fixture.chunk().getBlockState(fixture.position().east()).is(Blocks.STONE), "physical blocks await the terrain handoff");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "isolated_mine_lava_delay")
    public static void overworldLavaKeepsItsSlowerDelayAndDropoff(GameTestHelper helper) {
        var fixture = fixture(helper);
        set(fixture.chunk(), fixture.position(), Blocks.LAVA.defaultBlockState());
        var mine = observe(fixture);
        open(helper, mine, fixture.position().east());
        SettlementMineFluids.advance(helper.getLevel(), mine, 29L);
        helper.assertTrue(mine.getBlockState(fixture.position().east()).isAir(), "overworld lava waits thirty ticks");
        SettlementMineFluids.advance(helper.getLevel(), mine, 30L);
        helper.assertTrue(mine.getFluidState(fixture.position().east()).is(Fluids.FLOWING_LAVA), "lava follows its native fluid implementation");
        helper.assertValueEqual(mine.getFluidState(fixture.position().east()).getAmount(), 6, "overworld lava loses two levels across a horizontal step");
        var delayed = observe(fixture);
        delayed.change(Map.of(fixture.position().east(), Blocks.AIR.defaultBlockState()));
        schedule(delayed, fixture.position(), Fluids.LAVA, 120);
        schedule(delayed, fixture.position(), Fluids.LAVA, 30);
        var nativeTicks = (LevelChunkTicks<Fluid>) fixture.chunk().getFluidTicks();
        long now = helper.getLevel().getGameTime();
        nativeTicks.schedule(new ScheduledTick<>(Fluids.LAVA, fixture.position(), now + 120L, TickPriority.NORMAL, 0L));
        nativeTicks.schedule(new ScheduledTick<>(Fluids.LAVA, fixture.position(), now + 30L, TickPriority.NORMAL, 1L));
        helper.assertValueEqual((long) delay(delayed, fixture.position()), nativeTicks.peek().triggerTick() - now,
                "repeat notifications retain the first timer just as native tick containers do");
        SettlementMineFluids.advance(helper.getLevel(), delayed, 119L);
        helper.assertTrue(delayed.getBlockState(fixture.position().east()).isAir(), "a neighbor notification cannot shorten an existing lava delay");
        SettlementMineFluids.advance(helper.getLevel(), delayed, 120L);
        helper.assertTrue(delayed.getFluidState(fixture.position().east()).is(Fluids.FLOWING_LAVA), "lava resumes when the retained delay expires");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "isolated_mine_water_fall")
    public static void waterFallsOneScheduledStepAtATime(GameTestHelper helper) {
        var fixture = fixture(helper);
        set(fixture.chunk(), fixture.position(), Blocks.WATER.defaultBlockState());
        var mine = observe(fixture);
        open(helper, mine, fixture.position().below(), fixture.position().below(2));
        SettlementMineFluids.advance(helper.getLevel(), mine, 5L);
        helper.assertTrue(mine.getFluidState(fixture.position().below()).getValue(FlowingFluid.FALLING), "the first drop carries the native falling flag");
        helper.assertTrue(mine.getBlockState(fixture.position().below(2)).isAir(), "a new fluid cell cannot spread again in the same instant");
        SettlementMineFluids.advance(helper.getLevel(), mine, 10L);
        helper.assertTrue(mine.getFluidState(fixture.position().below(2)).is(Fluids.FLOWING_WATER), "the next delay advances the falling column");
        helper.assertValueEqual(mine.getFluidState(fixture.position().below(2)).getAmount(), 8, "falling water retains full falling amount");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "isolated_mine_water_drops")
    public static void flowingWaterRetainsWashedAwayItemsExactlyOnce(GameTestHelper helper) {
        var fixture = fixture(helper);
        set(fixture.chunk(), fixture.position(), Blocks.WATER.defaultBlockState());
        set(fixture.chunk(), fixture.position().east(2), Blocks.TORCH.defaultBlockState());
        var mine = observe(fixture);
        open(helper, mine, fixture.position().east());
        SettlementMineFluids.advance(helper.getLevel(), mine, 20L);
        helper.assertTrue(mine.getFluidState(fixture.position().east(2)).is(Fluids.FLOWING_WATER), "flow replaces the torch");
        helper.assertValueEqual(drops(mine), 1, "water preserves the native torch loot as one loose item");
        var restored = SettlementMine.load(mine.save());
        SettlementMineFluids.advance(helper.getLevel(), restored, 100L);
        helper.assertValueEqual(drops(restored), 1, "resuming fluid flow cannot drop the already removed torch again");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "isolated_mine_waterlogging")
    public static void ordinaryFlowPreservesADryFence(GameTestHelper helper) {
        var fixture = fixture(helper);
        set(fixture.chunk(), fixture.position(), Blocks.WATER.defaultBlockState());
        set(fixture.chunk(), fixture.position().east(), Blocks.OAK_FENCE.defaultBlockState());
        var mine = observe(fixture);
        open(helper, mine, fixture.position().above());
        SettlementMineFluids.advance(helper.getLevel(), mine, 5L);
        var fence = mine.getBlockState(fixture.position().east());
        helper.assertTrue(fence.is(Blocks.OAK_FENCE) && !fence.getValue(BlockStateProperties.WATERLOGGED), "native waterlogging requires source water rather than ordinary flow");
        helper.assertValueEqual(drops(mine), 0, "waterlogging does not turn the retained block into loot");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "isolated_mine_source_waterlogging")
    public static void sourceWaterlogsTheFenceWithoutDroppingIt(GameTestHelper helper) {
        var fixture = fixture(helper);
        set(fixture.chunk(), fixture.position(), Blocks.WATER.defaultBlockState());
        set(fixture.chunk(), fixture.position().east(2), Blocks.WATER.defaultBlockState());
        set(fixture.chunk(), fixture.position().east(), Blocks.OAK_FENCE.defaultBlockState());
        var mine = observe(fixture);
        var rule = helper.getLevel().getGameRules().getRule(GameRules.RULE_WATER_SOURCE_CONVERSION);
        boolean previous = rule.get();
        try {
            rule.set(true, helper.getLevel().getServer());
            open(helper, mine, fixture.position().above());
            SettlementMineFluids.advance(helper.getLevel(), mine, 5L);
            var fence = mine.getBlockState(fixture.position().east());
            helper.assertTrue(fence.is(Blocks.OAK_FENCE) && fence.getValue(BlockStateProperties.WATERLOGGED),
                    "source conversion supplies the waterlogging state accepted by the native container");
            helper.assertValueEqual(drops(mine), 0, "a waterlogged fence remains a block without duplicate loot");
        } finally {
            rule.set(previous, helper.getLevel().getServer());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "isolated_mine_fluid_mixing")
    public static void backgroundWaterAndLavaUseNativeMixingRules(GameTestHelper helper) {
        var fixture = fixture(helper);
        set(fixture.chunk(), fixture.position(), Blocks.WATER.defaultBlockState());
        set(fixture.chunk(), fixture.position().east(2), Blocks.LAVA.defaultBlockState());
        var mine = observe(fixture);
        open(helper, mine, fixture.position().east());
        SettlementMineFluids.advance(helper.getLevel(), mine, 5L);
        helper.assertTrue(mine.getBlockState(fixture.position().east(2)).is(Blocks.OBSIDIAN), "water reaching a lava source produces native obsidian");
        helper.assertValueEqual(drops(mine), 0, "solidification creates a block rather than a duplicate item");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "isolated_mine_source_conversion")
    public static void nativeSourceConversionRespectsTheWorldRule(GameTestHelper helper) {
        var fixture = fixture(helper);
        set(fixture.chunk(), fixture.position().west(), Blocks.WATER.defaultBlockState());
        set(fixture.chunk(), fixture.position().east(), Blocks.WATER.defaultBlockState());
        var rule = helper.getLevel().getGameRules().getRule(GameRules.RULE_WATER_SOURCE_CONVERSION);
        boolean previous = rule.get();
        try {
            rule.set(true, helper.getLevel().getServer());
            var enabled = observe(fixture);
            open(helper, enabled, fixture.position());
            SettlementMineFluids.advance(helper.getLevel(), enabled, 5L);
            helper.assertTrue(enabled.getFluidState(fixture.position()).isSource(), "two sources create a new source when the native rule allows it");
            rule.set(false, helper.getLevel().getServer());
            var disabled = observe(fixture);
            open(helper, disabled, fixture.position());
            SettlementMineFluids.advance(helper.getLevel(), disabled, 5L);
            helper.assertFalse(disabled.getFluidState(fixture.position()).isSource(), "disabling source conversion leaves flowing water");
            helper.assertTrue(disabled.getFluidState(fixture.position()).is(Fluids.FLOWING_WATER), "disabled conversion still permits ordinary flow");
        } finally {
            rule.set(previous, helper.getLevel().getServer());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "isolated_mine_fluid_saved_delay")
    public static void savingKeepsTheRemainingFluidDelay(GameTestHelper helper) {
        var fixture = fixture(helper);
        set(fixture.chunk(), fixture.position(), Blocks.WATER.defaultBlockState());
        var mine = observe(fixture);
        open(helper, mine, fixture.position().east());
        SettlementMineFluids.advance(helper.getLevel(), mine, 3L);
        var restored = SettlementMine.load(mine.save());
        helper.assertValueEqual(delay(restored, fixture.position()), 2, "the saved source retains its two remaining ticks");
        SettlementMineFluids.advance(helper.getLevel(), restored, 4L);
        helper.assertTrue(restored.getBlockState(fixture.position().east()).isAir(), "saving cannot reset or bypass a fluid delay");
        SettlementMineFluids.advance(helper.getLevel(), restored, 5L);
        helper.assertTrue(restored.getFluidState(fixture.position().east()).is(Fluids.FLOWING_WATER), "flow resumes at the original deadline");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "isolated_mine_fluid_clock")
    public static void fluidClockCapsElapsedTimeAndDoesNotReplayTheSameInterval(GameTestHelper helper) {
        var fixture = fixture(helper);
        set(fixture.chunk(), fixture.position(), Blocks.WATER.defaultBlockState());
        var mine = observe(fixture);
        schedule(mine, fixture.position(), Fluids.WATER, 1_000);
        SettlementMineFluids.advance(helper.getLevel(), mine, 100_000L);
        helper.assertValueEqual(delay(mine, fixture.position()), 800, "a long gap advances at most one normal background interval");
        SettlementMineFluids.advance(helper.getLevel(), mine, 100_000L);
        helper.assertValueEqual(delay(mine, fixture.position()), 800, "the same server interval cannot run twice");
        var restored = SettlementMine.load(mine.save());
        SettlementMineFluids.advance(helper.getLevel(), restored, 100_200L);
        helper.assertValueEqual(delay(restored, fixture.position()), 600, "the saved clock continues the next interval exactly once");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "isolated_mine_fluid_replacement")
    public static void staleFluidTicksPreserveReplacementBlocks(GameTestHelper helper) {
        var fixture = fixture(helper);
        set(fixture.chunk(), fixture.position(), Blocks.WATER.defaultBlockState());
        var mine = observe(fixture);
        schedule(mine, fixture.position(), Fluids.WATER, 5);
        mine.change(Map.of(fixture.position(), Blocks.GOLD_BLOCK.defaultBlockState()));
        SettlementMineFluids.advance(helper.getLevel(), mine, 5L);
        helper.assertTrue(mine.getBlockState(fixture.position()).is(Blocks.GOLD_BLOCK), "a stale tick cannot overwrite a later replacement");
        helper.assertValueEqual(delay(mine, fixture.position()), -1, "a mismatched fluid tick is consumed without replay");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "isolated_mine_fluid_unknown")
    public static void unavailableFluidObservationsCannotPartiallyApplyATick(GameTestHelper helper) {
        var fixture = fixture(helper);
        var outside = fixture.position().west(5);
        set(fixture.chunk(), outside, Blocks.WATER.defaultBlockState());
        set(fixture.chunk(), outside.west(), Blocks.WATER.defaultBlockState());
        set(fixture.chunk(), outside.east(), Blocks.WATER.defaultBlockState());
        set(fixture.chunk(), outside.north(), Blocks.WATER.defaultBlockState());
        set(fixture.chunk(), outside.below(), Blocks.AIR.defaultBlockState());
        var ready = fixture.position().east(2);
        set(fixture.chunk(), ready, Blocks.WATER.defaultBlockState());
        set(fixture.chunk(), ready.east(), Blocks.AIR.defaultBlockState());
        var mine = observe(fixture);
        schedule(mine, outside, Fluids.WATER, 5);
        schedule(mine, ready, Fluids.WATER, 5);
        SettlementMineFluids.advance(helper.getLevel(), mine, 5L);
        helper.assertTrue(mine.getBlockState(outside.below()).isAir(), "a later unavailable side query rolls back the already staged downward flow");
        helper.assertTrue(mine.getFluidState(outside).isSource(), "the source survives an unavailable calculation");
        helper.assertValueEqual(delay(mine, outside), 0, "the pending tick remains available for later observation or native handoff");
        helper.assertTrue(mine.getFluidState(ready.east()).is(Fluids.FLOWING_WATER), "an independent ready tick proceeds after the aborted transaction");
        helper.assertValueEqual(drops(mine), 0, "an aborted fluid calculation cannot create loose items");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "isolated_mine_fluid_capture")
    public static void nativeFluidTimersTransferToBackgroundOwnership(GameTestHelper helper) {
        var fixture = fixture(helper);
        set(fixture.chunk(), fixture.position(), Blocks.WATER.defaultBlockState());
        var mine = observe(fixture);
        var ticks = (LevelChunkTicks<Fluid>) fixture.chunk().getFluidTicks();
        ticks.schedule(new ScheduledTick<>(Fluids.WATER, fixture.position(), helper.getLevel().getGameTime() + 3L, TickPriority.HIGH, 1L));
        var section = mine.sections().values().iterator().next();
        section.captureFluidTicks(fixture.chunk());
        helper.assertValueEqual(ticks.count(), 0, "captured native ticks relinquish ownership");
        helper.assertValueEqual(delay(mine, fixture.position()), 3, "capturing a native tick retains its actual remaining delay");
        var restored = SettlementMine.load(mine.save());
        helper.assertValueEqual(delay(restored, fixture.position()), 3, "native capture survives saving");
        var packed = new LevelChunkTicks<Fluid>(List.of(new net.minecraft.world.ticks.SavedTick<>(
                Fluids.WATER, fixture.position(), 3, TickPriority.HIGH)));
        var returning = new LevelChunk(helper.getLevel(), fixture.chunk().getPos(), net.minecraft.world.level.chunk.UpgradeData.EMPTY,
                new LevelChunkTicks<>(), packed, 0L, fixture.chunk().getSections(), null, null);
        var returningMine = observe(new Fixture(returning, fixture.position(), fixture.area()));
        helper.assertTrue(packed.peek() == null && packed.count() == 1, "freshly loaded native timers remain packed before block ticking starts");
        returningMine.sections().values().iterator().next().captureFluidTicks(returning);
        helper.assertValueEqual(packed.count(), 0, "a view-only chunk can transfer its packed timer without beginning native ticking");
        helper.assertValueEqual(delay(returningMine, fixture.position()), 3, "unpacking preserves the saved relative delay");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "isolated_mine_fluid_deferred_handoff")
    public static void aViewOnlyHandoffRetainsBackgroundFluidOwnership(GameTestHelper helper) {
        var fixture = fixture(helper);
        set(fixture.chunk(), fixture.position(), Blocks.WATER.defaultBlockState());
        var mine = observe(fixture);
        open(helper, mine, fixture.position().east());
        SettlementMineFluids.advance(helper.getLevel(), mine, 3L);
        var section = mine.sections().values().iterator().next();
        helper.assertTrue(section.reconcile(fixture.chunk(), (position, before) -> {}, true), "a loaded view receives the terrain revision");
        section.captureFluidTicks(fixture.chunk());
        helper.assertTrue(section.pending(), "view-only terrain keeps its pending background timers");
        helper.assertValueEqual(delay(mine, fixture.position()), 2, "returning terrain does not restart the remaining delay");
        SettlementMineFluids.advance(helper.getLevel(), mine, 5L);
        helper.assertTrue(mine.getFluidState(fixture.position().east()).is(Fluids.FLOWING_WATER), "background flow continues after a view-only handoff");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "isolated_mine_fluid_native_receipt")
    public static void nativeFluidHandoffRetiresTheSavedTimerRevision(GameTestHelper helper) {
        var fixture = fixture(helper);
        set(fixture.chunk(), fixture.position(), Blocks.WATER.defaultBlockState());
        var mine = observe(fixture);
        schedule(mine, fixture.position(), Fluids.WATER, 3);
        var saved = mine.save();
        var section = mine.sections().values().iterator().next();
        helper.assertTrue(section.reconcile(fixture.chunk()), "native ticking receives the saved timer");
        var ticks = (LevelChunkTicks<Fluid>) fixture.chunk().getFluidTicks();
        helper.assertTrue(ticks.hasScheduledTick(fixture.position(), Fluids.WATER), "handoff schedules the actual native tick");
        helper.assertFalse(section.pending(), "native ownership completes the pending revision");
        ticks.removeIf(tick -> tick.pos().equals(fixture.position()));
        var old = SettlementMine.load(saved).sections().values().iterator().next();
        helper.assertTrue(old.reconcile(fixture.chunk()), "an older timer save recognizes the completed receipt");
        helper.assertValueEqual(ticks.count(), 0, "replaying an older save cannot schedule the consumed native timer again");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "isolated_mine_fluid_service")
    public static void townUpdatesKeepFluidTimeAcrossLoadedViewReconciliations(GameTestHelper helper) {
        var fixture = fixture(helper);
        var level = helper.getLevel();
        var actual = level.getChunkAt(fixture.position());
        for (BlockPos cell : BlockPos.betweenClosed(fixture.area().min().offset(-2, -2, -2), fixture.area().max().offset(2, 2, 2))) {
            set(actual, cell, fixture.chunk().getBlockState(cell));
        }
        set(actual, fixture.position(), Blocks.WATER.defaultBlockState());
        var mine = observe(new Fixture(actual, fixture.position(), fixture.area()));
        var registry = com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), fixture.position(), "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), fixture.position(), 0L).orElseThrow();
        helper.assertTrue(town.putArea(town.leader(), fixture.area()), "the fluid mine is registered");
        town.recordMine(mine);
        helper.assertFalse(level.shouldTickBlocksAt(actual.getPos().toLong()), "the view-only chunk does not run native block ticks");
        open(helper, mine, fixture.position().east());
        SettlementMineService.update(level, town, 3L);
        helper.assertTrue(actual.getBlockState(fixture.position().east()).isAir(), "the first update publishes the cut");
        helper.assertValueEqual(delay(mine, fixture.position()), 2, "the source keeps two ticks after a real service handoff");
        SettlementMineService.update(level, town, 5L);
        helper.assertTrue(mine.getFluidState(fixture.position().east()).is(Fluids.FLOWING_WATER), "the town update advances the original fluid deadline");
        SettlementMineService.refresh(level, mine);
        helper.assertTrue(actual.getFluidState(fixture.position().east()).is(Fluids.FLOWING_WATER), "the reacted fluid reaches the view without waiting for native ticking");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "isolated_mine_fluid_legacy_clock")
    public static void olderMineSavesStartTheirFluidClockWithoutCatchup(GameTestHelper helper) {
        var fixture = fixture(helper);
        set(fixture.chunk(), fixture.position(), Blocks.WATER.defaultBlockState());
        set(fixture.chunk(), fixture.position().east(), Blocks.AIR.defaultBlockState());
        var mine = observe(fixture);
        schedule(mine, fixture.position(), Fluids.WATER, 5);
        var saved = mine.save();
        saved.remove("LastFluidTick");
        var legacy = SettlementMine.load(saved);
        SettlementMineFluids.advance(helper.getLevel(), legacy, 100_000L);
        helper.assertTrue(legacy.getBlockState(fixture.position().east()).isAir(), "an older save does not invent elapsed offline time");
        helper.assertValueEqual(delay(legacy, fixture.position()), 5, "the original deferred delay remains intact");
        SettlementMineFluids.advance(helper.getLevel(), legacy, 100_005L);
        helper.assertTrue(legacy.getFluidState(fixture.position().east()).is(Fluids.FLOWING_WATER), "subsequent server time resumes the older pending fluid tick");
        helper.succeed();
    }

    private static void open(GameTestHelper helper, SettlementMine mine, BlockPos... positions) {
        var changes = new java.util.LinkedHashMap<BlockPos, BlockState>();
        for (var position : positions) changes.put(position, Blocks.AIR.defaultBlockState());
        helper.assertTrue(mine.change(changes), "the observed excavation opens its fluid face");
        SettlementMinePhysics.advance(helper.getLevel(), mine);
    }

    private static void schedule(SettlementMine mine, BlockPos position, Fluid fluid, int delay) {
        if (!mine.react(Map.of(), List.of(new SettlementTerrainTick(position, BuiltInRegistries.FLUID.getKey(fluid), true, delay, TickPriority.NORMAL)), List.of())) {
            throw new IllegalStateException("Fluid fixture cannot schedule its observed source");
        }
    }

    private static int delay(SettlementMine mine, BlockPos position) {
        return mine.sections().values().stream().flatMap(section -> section.fluidTicks().stream())
                .filter(tick -> tick.position().equals(position)).mapToInt(SettlementTerrainTick::delay).findFirst().orElse(-1);
    }

    private static int drops(SettlementMine mine) {
        return mine.save().getList("Sections", Tag.TAG_COMPOUND).stream()
                .mapToInt(raw -> ((CompoundTag) raw).getList("Drops", Tag.TAG_COMPOUND).size()).sum();
    }

    private static Fixture fixture(GameTestHelper helper) {
        var origin = helper.absolutePos(BlockPos.ZERO);
        var chunk = new LevelChunk(helper.getLevel(), new ChunkPos(440_000 + origin.getX(), 440_000 + origin.getZ()));
        var position = new BlockPos(chunk.getPos().getMinBlockX() + 8, 70, chunk.getPos().getMinBlockZ() + 8);
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.MINE, "Mine", position.offset(-4, -2, -4), position.offset(4, 2, 4), 5, true);
        for (BlockPos cell : BlockPos.betweenClosed(area.min().offset(-2, -2, -2), area.max().offset(2, 2, 2))) {
            set(chunk, cell, Blocks.STONE.defaultBlockState());
        }
        helper.assertFalse(helper.getLevel().hasChunkAt(position), "the fluid fixture is detached from native chunks");
        return new Fixture(chunk, position, area);
    }

    private static SettlementMine observe(Fixture fixture) {
        var mine = new SettlementMine(fixture.area());
        mine.record(SettlementTerrainSection.capture("Mine", fixture.area().id(), mine.observedMin(), mine.observedMax(), fixture.chunk()));
        mine.observeTime(0L);
        return mine;
    }

    private static void set(LevelChunk chunk, BlockPos position, BlockState state) {
        chunk.getSection(chunk.getSectionIndex(position.getY())).setBlockState(position.getX() & 15,
                position.getY() & 15, position.getZ() & 15, state);
    }

    private record Fixture(LevelChunk chunk, BlockPos position, SettlementArea area) {
    }
}
