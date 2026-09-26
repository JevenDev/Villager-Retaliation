package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MangrovePropaguleBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(VillagerRetaliation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SettlementNaturalForestGameTests {
    private SettlementNaturalForestGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_natural_forest")
    public static void sunnySaplingsGrowThroughTheBackgroundSettlementService(GameTestHelper helper) {
        fixture(helper, fixture -> {
            speed(helper, 144);
            int chunks = helper.getLevel().getChunkSource().getLoadedChunksCount();
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), fixture.now() + 200L);
            helper.assertValueEqual(fixture.forest().getBlockState(fixture.root()).getValue(SaplingBlock.STAGE), 1, "natural growth first matures the actual sapling");
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), fixture.now() + 400L);
            helper.assertTrue(fixture.forest().getBlockState(fixture.root()).is(Blocks.BIRCH_LOG), "the next natural attempt generates the real tree");
            helper.assertTrue(fixture.chunk(fixture.root()).getBlockState(fixture.root()).is(Blocks.BIRCH_SAPLING), "the unloaded terrain waits for its receipt handoff");
            helper.assertValueEqual(helper.getLevel().getChunkSource().getLoadedChunksCount(), chunks, "natural growth never loads a chunk");
            var loaded = SettlementForest.load(fixture.forest().save());
            for (var section : loaded.sections().values()) helper.assertTrue(section.reconcile(fixture.chunk(section.min())), "grown terrain hands off");
            helper.assertTrue(fixture.chunk(fixture.root()).getBlockState(fixture.root()).is(Blocks.BIRCH_LOG), "the native chunk receives the naturally grown trunk");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_natural_forest")
    public static void darknessAndNightPauseGrowthUntilRealLightReturns(GameTestHelper helper) {
        fixture(helper, fixture -> {
            speed(helper, 144);
            helper.getLevel().setDayTime(18_000L);
            helper.getLevel().updateSkyBrightness();
            SettlementForestGrowth.advance(helper.getLevel(), fixture.forest(), true, fixture.now() + 200L);
            helper.assertValueEqual(stage(fixture), 0, "nighttime sky light alone is too dim for an ordinary sapling");
            helper.getLevel().setDayTime(6_000L);
            helper.getLevel().updateSkyBrightness();
            roof(fixture, Blocks.STONE.defaultBlockState(), fixture.root().getY() + 2);
            SettlementForestGrowth.advance(helper.getLevel(), fixture.forest(), true, fixture.now() + 400L);
            helper.assertValueEqual(stage(fixture), 0, "an opaque roof prevents daytime growth too");
            fixture.forest().change(Map.of(fixture.root().above(2), Blocks.AIR.defaultBlockState()));
            SettlementForestGrowth.advance(helper.getLevel(), fixture.forest(), true, fixture.now() + 600L);
            helper.assertValueEqual(stage(fixture), 1, "opening an observed roof exposes the sapling to real sunlight");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_natural_forest")
    public static void artificialLightIsRecomputedWhenItsSourceChanges(GameTestHelper helper) {
        fixture(helper, fixture -> {
            speed(helper, 144);
            roof(fixture, Blocks.STONE.defaultBlockState(), fixture.root().getY() + 3);
            var lamp = fixture.root().above().east(2);
            fixture.forest().change(Map.of(lamp, Blocks.GLOWSTONE.defaultBlockState()));
            helper.assertTrue(fixture.forest().brightness(fixture.root().above(), 0) >= 9, "an actual observed lamp lights the sapling");
            SettlementForestGrowth.advance(helper.getLevel(), fixture.forest(), true, fixture.now() + 200L);
            helper.assertValueEqual(stage(fixture), 1, "block light permits natural maturation under cover");
            fixture.forest().change(Map.of(lamp, Blocks.AIR.defaultBlockState()));
            helper.assertValueEqual(fixture.forest().brightness(fixture.root().above(), 0), 0, "removing the lamp removes its cached light");
            SettlementForestGrowth.advance(helper.getLevel(), fixture.forest(), true, fixture.now() + 400L);
            helper.assertTrue(fixture.forest().getBlockState(fixture.root()).is(Blocks.BIRCH_SAPLING), "a removed lamp cannot keep growing trees");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_natural_forest")
    public static void leafCanopiesAndPartialBlockShapesControlForestLight(GameTestHelper helper) {
        fixture(helper, fixture -> {
            for (int y = 2; y <= 7; y++) roof(fixture, Blocks.OAK_LEAVES.defaultBlockState(), fixture.root().getY() + y);
            helper.assertValueEqual(fixture.forest().brightness(fixture.root().above(), 0), 8, "six layers of native leaf opacity shade the sapling");
            for (int y = 2; y <= 7; y++) roof(fixture, Blocks.AIR.defaultBlockState(), fixture.root().getY() + y);
            helper.assertValueEqual(fixture.forest().brightness(fixture.root().above(), 0), 15, "removing the canopy restores direct sky light");
            roof(fixture, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), fixture.root().getY() + 2);
            helper.assertValueEqual(fixture.forest().brightness(fixture.root().above(), 0), 0, "native face occlusion blocks light even when the slab's scalar opacity is zero");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_natural_forest")
    public static void lightCanTravelSidewaysAcrossObservedChunkSections(GameTestHelper helper) {
        fixture(helper, fixture -> {
            roof(fixture, Blocks.STONE.defaultBlockState(), fixture.root().getY() + 2);
            var opening = fixture.root().above(2).east(3);
            fixture.forest().change(Map.of(opening, Blocks.AIR.defaultBlockState()));
            helper.assertValueEqual(fixture.forest().brightness(fixture.root().above(), 0), 12, "sky light spreads sideways from a real opening across the section boundary");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_natural_forest")
    public static void growthProgressSurvivesSaveWithoutCountingTheSameTimeTwice(GameTestHelper helper) {
        fixture(helper, fixture -> {
            speed(helper, 3);
            SettlementForestGrowth.advance(helper.getLevel(), fixture.forest(), true, fixture.now() + 200L);
            double progress = fixture.section().growth(fixture.root());
            helper.assertTrue(progress > 0.0D && progress < 1.0D, "ordinary server time accumulates fractional natural growth");
            var saved = fixture.forest().save();
            var restored = SettlementForest.load(saved);
            helper.assertTrue(restored.save().equals(saved), "the growth clock and observed light round trip exactly");
            SettlementForestGrowth.advance(helper.getLevel(), restored, true, fixture.now() + 200L);
            helper.assertValueEqual(restored.sections().get(fixture.section().origin()).growth(fixture.root()), progress, "the same interval cannot be counted again after reload");
            for (int interval = 2; interval <= 96; interval++) {
                SettlementForestGrowth.advance(helper.getLevel(), restored, true, fixture.now() + interval * 200L);
            }
            helper.assertTrue(restored.getBlockState(fixture.root()).is(Blocks.BIRCH_LOG), "continued server time grows the saved sapling at the normal random tick rate");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_natural_forest")
    public static void disabledAreasAndStoppedRandomTicksCannotBankOfflineGrowth(GameTestHelper helper) {
        fixture(helper, fixture -> {
            speed(helper, 144);
            var disabled = new SettlementArea(fixture.area().id(), SettlementAreaType.FORESTRY, "Forest",
                    fixture.area().min(), fixture.area().max(), 5, false);
            fixture.town().putArea(fixture.town().leader(), disabled);
            SettlementForestService.update(helper.getLevel(), fixture.town(), fixture.now() + 24_000_000L);
            helper.assertValueEqual(stage(fixture), 0, "disabled areas accumulate no growth during a large time gap");
            fixture.town().putArea(fixture.town().leader(), fixture.area());
            speed(helper, 0);
            SettlementForestService.update(helper.getLevel(), fixture.town(), fixture.now() + 24_000_200L);
            helper.assertValueEqual(stage(fixture), 0, "the random tick game rule can stop natural growth");
            speed(helper, 3);
            SettlementForestService.update(helper.getLevel(), fixture.town(), fixture.now() + 48_000_000L);
            helper.assertValueEqual(stage(fixture), 0, "a large clock jump contributes at most one bounded server interval");
            helper.assertTrue(fixture.section().growth(fixture.root()) < 0.03D, "missed time is not replayed as offline growth");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_natural_forest")
    public static void nativeTerrainTickReceiptsPreventDoubleGrowth(GameTestHelper helper) {
        fixture(helper, fixture -> {
            speed(helper, 3);
            SettlementChunkReceipts.onVanillaTick(fixture.chunk(fixture.root()), fixture.now() + 190L);
            helper.assertTrue(fixture.section().reconcile(fixture.chunk(fixture.root())), "native observations reconcile before background work");
            SettlementForestGrowth.advance(helper.getLevel(), fixture.forest(), true, fixture.now() + 200L);
            double expected = 10.0D * 3.0D / 4096.0D / 7.0D;
            helper.assertTrue(Math.abs(fixture.section().growth(fixture.root()) - expected) < 1.0E-9D, "only time after the latest native terrain tick contributes growth");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_natural_forest")
    public static void legacyForestsWaitForAnEnvironmentObservation(GameTestHelper helper) {
        fixture(helper, fixture -> {
            speed(helper, 144);
            var saved = fixture.forest().save();
            for (var raw : saved.getList("Sections", 10)) ((net.minecraft.nbt.CompoundTag) raw).remove("LightBoundary");
            var legacy = SettlementForest.load(saved);
            SettlementForestGrowth.advance(helper.getLevel(), legacy, true, fixture.now() + 200L);
            helper.assertValueEqual(legacy.getBlockState(fixture.root()).getValue(SaplingBlock.STAGE), 0, "old saves cannot invent sunlight from an air snapshot");
            helper.assertFalse(legacy.sections().get(fixture.section().origin()).environmentKnown(), "legacy observations remain explicitly incomplete");
            var section = legacy.sections().get(fixture.section().origin());
            helper.assertTrue(section.reconcile(fixture.chunk(fixture.root())) && section.environmentKnown(), "a new native observation supplies the missing environment");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_natural_forest")
    public static void hangingMangrovePropagulesNaturallyMatureInDarkness(GameTestHelper helper) {
        fixture(helper, fixture -> {
            speed(helper, 21);
            roof(fixture, Blocks.STONE.defaultBlockState(), fixture.root().getY() + 3);
            fixture.forest().change(Map.of(fixture.root(), MangrovePropaguleBlock.createNewHangingPropagule(),
                    fixture.root().above(), Blocks.MANGROVE_LEAVES.defaultBlockState()));
            for (int age = 1; age <= 4; age++) {
                SettlementForestGrowth.advance(helper.getLevel(), fixture.forest(), true, fixture.now() + age * 200L);
                helper.assertValueEqual(fixture.forest().getBlockState(fixture.root()).getValue(MangrovePropaguleBlock.AGE), age,
                        "hanging propagules follow their native age progression without a light requirement");
            }
            helper.assertFalse(fixture.section().saplings().contains(fixture.root()), "a mature hanging propagule no longer needs random growth work");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_natural_forest")
    public static void boundedGrowthAttemptsRotateAcrossDensePlantingsAfterReload(GameTestHelper helper) {
        fixture(helper, fixture -> {
            speed(helper, 144);
            var planted = new LinkedHashMap<BlockPos, BlockState>();
            planted.put(fixture.root(), Blocks.AIR.defaultBlockState());
            for (int x = 3; x < 11; x++) {
                for (int z = 3; z < 11; z++) planted.put(fixture.area().min().offset(x, 1, z), Blocks.BIRCH_SAPLING.defaultBlockState());
            }
            fixture.forest().change(planted);
            SettlementForestGrowth.advance(helper.getLevel(), fixture.forest(), true, fixture.now() + 200L);
            helper.assertValueEqual(mature(fixture.forest(), planted), 32L, "one interval caps expensive growth attempts");
            var restored = SettlementForest.load(fixture.forest().save());
            SettlementForestGrowth.advance(helper.getLevel(), restored, true, fixture.now() + 400L);
            helper.assertValueEqual(mature(restored, planted), 64L, "the persisted cursor gives the rest of the planting its next turn");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_natural_forest")
    public static void nativeLightObservationsUseExistingChunksOnly(GameTestHelper helper) {
        var level = helper.getLevel();
        var position = helper.absolutePos(new BlockPos(3, 2, 3));
        var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        var min = SettlementForestSection.sectionOrigin(position);
        int chunks = level.getChunkSource().getLoadedChunksCount();
        var observed = SettlementForestSection.capture(UUID.randomUUID(), min, min.offset(15, 15, 15), chunk);
        helper.assertTrue(observed.environmentKnown() && !observed.lightSources().isEmpty(), "loaded terrain supplies real boundary light observations");
        observed.lightSources().forEach((sample, source) -> {
            helper.assertValueEqual(source.sky(), level.getBrightness(LightLayer.SKY, sample), "recorded sky light matches the native light engine");
            helper.assertValueEqual(source.block(), level.getBrightness(LightLayer.BLOCK, sample), "recorded block light matches the native light engine");
        });
        helper.assertValueEqual(level.getChunkSource().getLoadedChunksCount(), chunks, "observation cannot request missing neighboring chunks");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_natural_forest")
    public static void newerTerrainReceiptsDiscardStaleSaplingGrowthCredit(GameTestHelper helper) {
        fixture(helper, fixture -> {
            speed(helper, 3);
            SettlementForestGrowth.advance(helper.getLevel(), fixture.forest(), true, fixture.now() + 200L);
            helper.assertTrue(fixture.section().growth(fixture.root()) > 0.0D, "the old sapling has some accumulated growth");
            var old = fixture.forest().save();
            fixture.forest().change(Map.of(fixture.root(), Blocks.AIR.defaultBlockState()));
            fixture.forest().change(Map.of(fixture.root(), Blocks.BIRCH_SAPLING.defaultBlockState()));
            helper.assertTrue(fixture.section().reconcile(fixture.chunk(fixture.root())), "a replacement sapling advances the real terrain receipt");
            var stale = SettlementForest.load(old);
            var section = stale.sections().get(fixture.section().origin());
            helper.assertTrue(section.reconcile(fixture.chunk(fixture.root())), "the older forest adopts the newer receipt");
            helper.assertValueEqual(section.growth(fixture.root()), 0.0D, "an identically shaped replacement does not inherit stale growth credit");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_natural_forest")
    public static void malformedGrowthAndLightBoundariesCannotCreateUnobservedProgress(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var invalidGrowth = fixture.section().save();
            var entries = new net.minecraft.nbt.ListTag();
            var entry = new net.minecraft.nbt.CompoundTag();
            entry.putLong("Position", fixture.root().asLong());
            entry.putDouble("Progress", Double.NaN);
            entries.add(entry);
            invalidGrowth.put("Growth", entries);
            invalidSection(helper, fixture.area().id(), invalidGrowth, "non-finite growth is rejected");
            entry.putDouble("Progress", 0.5D);
            entry.putLong("Position", fixture.root().above().asLong());
            invalidSection(helper, fixture.area().id(), invalidGrowth, "growth cannot be assigned to observed air");
            var lit = fixture.forest().sections().values().stream().filter(section -> !section.lightSources().isEmpty()).findFirst().orElseThrow();
            var duplicate = lit.save();
            var sources = duplicate.getCompound("LightBoundary");
            var positions = sources.getLongArray("Positions");
            positions[1] = positions[0];
            sources.putLongArray("Positions", positions);
            invalidSection(helper, fixture.area().id(), duplicate, "duplicated boundary observations are rejected");
            var distant = lit.save();
            var outside = distant.getCompound("LightBoundary").getLongArray("Positions");
            outside[0] = lit.max().above(3).asLong();
            distant.getCompound("LightBoundary").putLongArray("Positions", outside);
            invalidSection(helper, fixture.area().id(), distant, "light observations cannot reference distant unobserved terrain");
            var mismatched = lit.save();
            mismatched.getCompound("LightBoundary").putByteArray("Light", new byte[0]);
            invalidSection(helper, fixture.area().id(), mismatched, "boundary coordinates and light values must have matching lengths");
        });
    }

    private static void invalidSection(GameTestHelper helper, UUID area, net.minecraft.nbt.CompoundTag tag, String message) {
        boolean rejected = false;
        try {
            SettlementForestSection.load(area, tag);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, message);
    }

    private static long mature(SettlementForest forest, Map<BlockPos, BlockState> planting) {
        return planting.keySet().stream().map(forest::getBlockState).filter(state -> state.is(Blocks.BIRCH_SAPLING)
                && state.getValue(SaplingBlock.STAGE) == 1).count();
    }

    private static int stage(Fixture fixture) {
        return fixture.forest().getBlockState(fixture.root()).getValue(SaplingBlock.STAGE);
    }

    private static void roof(Fixture fixture, BlockState state, int y) {
        var changes = new LinkedHashMap<BlockPos, BlockState>();
        for (BlockPos position : BlockPos.betweenClosed(new BlockPos(fixture.area().min().getX(), y, fixture.area().min().getZ()),
                new BlockPos(fixture.area().max().getX(), y, fixture.area().max().getZ()))) changes.put(position.immutable(), state);
        fixture.forest().change(changes);
    }

    private static void speed(GameTestHelper helper, int value) {
        helper.getLevel().getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(value, helper.getLevel().getServer());
    }

    private static void fixture(GameTestHelper helper, Consumer<Fixture> test) {
        var level = helper.getLevel();
        long day = level.getDayTime();
        int speed = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
        long now = level.getGameTime();
        var origin = helper.absolutePos(BlockPos.ZERO);
        int x = 770_000 + origin.getX() * 4;
        int z = 770_000 + origin.getZ() * 4;
        var min = new BlockPos(x * 16, 64, z * 16);
        var root = min.offset(15, 1, 15);
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.FORESTRY, "Forest", min, min.offset(31, 31, 31), 5, true);
        var chunks = new LinkedHashMap<ChunkPos, LevelChunk>();
        var forest = new SettlementForest(area);
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                var chunk = new LevelChunk(level, new ChunkPos(x + dx, z + dz));
                chunks.put(chunk.getPos(), chunk);
                for (BlockPos ground : BlockPos.betweenClosed(new BlockPos(chunk.getPos().getMinBlockX(), 64, chunk.getPos().getMinBlockZ()),
                        new BlockPos(chunk.getPos().getMaxBlockX(), 64, chunk.getPos().getMaxBlockZ()))) set(chunk, ground, Blocks.DIRT.defaultBlockState());
                if (chunk.getPos().equals(new ChunkPos(root))) set(chunk, root, Blocks.BIRCH_SAPLING.defaultBlockState());
                for (int y = 64; y <= 80; y += 16) {
                    var sectionMin = new BlockPos(chunk.getPos().getMinBlockX(), y, chunk.getPos().getMinBlockZ());
                    var section = SettlementForestSection.capture(area.id(), sectionMin, sectionMin.offset(15, 15, 15), chunk);
                    var saved = section.save();
                    var sources = new LinkedHashMap<BlockPos, SettlementForestLight.Source>();
                    if (y == 80) {
                        for (BlockPos position : BlockPos.betweenClosed(sectionMin.above(16), sectionMin.offset(15, 16, 15))) {
                            sources.put(position.immutable(), new SettlementForestLight.Source(Blocks.AIR.defaultBlockState(), 15, 0));
                        }
                    }
                    saved.put("LightBoundary", SettlementForestLight.save(sources));
                    forest.record(SettlementForestSection.load(area.id(), saved));
                }
            }
        }
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(now, level.dimension().location(), root, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), root, now).orElseThrow();
        helper.assertTrue(town.putArea(town.leader(), area), "the natural forest is registered");
        town.recordForest(forest);
        helper.assertFalse(level.hasChunkAt(root), "the observed forest remains unloaded");
        try {
            level.setDayTime(6_000L);
            level.updateSkyBrightness();
            test.accept(new Fixture(town, area, forest, root, chunks, now));
        } finally {
            speed(helper, speed);
            level.setDayTime(day);
            level.updateSkyBrightness();
        }
        helper.succeed();
    }

    private static void set(LevelChunk chunk, BlockPos position, BlockState state) {
        chunk.getSection(chunk.getSectionIndex(position.getY())).setBlockState(position.getX() & 15, position.getY() & 15, position.getZ() & 15, state);
    }

    private record Fixture(SettlementRecord town, SettlementArea area, SettlementForest forest, BlockPos root,
                           Map<ChunkPos, LevelChunk> chunks, long now) {
        LevelChunk chunk(BlockPos position) {
            return this.chunks.get(new ChunkPos(position));
        }

        SettlementForestSection section() {
            return this.forest.sections().get(SettlementForestSection.sectionOrigin(this.root));
        }
    }
}
