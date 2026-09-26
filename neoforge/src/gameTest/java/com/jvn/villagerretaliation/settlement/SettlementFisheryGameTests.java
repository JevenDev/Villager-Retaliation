package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementFisheryGameTests {
    private SettlementFisheryGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_fishery")
    public static void fisheryFindsObservedWaterAndDryCastingSpaceWithoutLoadingChunks(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            SettlementFisheryService.observeArea(level, fixture.town(), fixture.area(), fixture.chunk());
            var spots = fixture.town().fishingSpots().get(fixture.area().id());
            helper.assertTrue(spots != null && !spots.isEmpty(), "an observed shore provides fishing sites");
            helper.assertTrue(spots.stream().allMatch(spot -> spot.valid() && fixture.area().contains(spot.approach())),
                    "every site has valid water and a standing position inside the assigned area");
            helper.assertTrue(spots.stream().anyMatch(SettlementFishingSpot::openWater), "the deep open pool supports open-water catches");
            helper.assertTrue(spots.size() <= SettlementFisheryService.MAX_SPOTS, "observed sites remain bounded");
            helper.assertFalse(level.hasChunkAt(fixture.water()), "discovering a shore never loads its detached chunk");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_fishery")
    public static void fishingTerrainAndOpenWaterEligibilitySurviveSaving(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            var spot = capture(helper, fixture);
            helper.assertTrue(spot.openWater(), "the original pool is open water");
            fixture.town().recordFishingSpots(fixture.area().id(), List.of(spot));
            var restored = SettlementRecord.load(fixture.town().save(level.registryAccess()), level.registryAccess(), () -> {});
            var saved = restored.fishingSpots().get(fixture.area().id()).getFirst();
            helper.assertTrue(saved.valid() && saved.openWater(), "water, clearance and deep-water eligibility survive the town save");
            helper.assertValueEqual(saved.water(), spot.water(), "the exact water position survives saving");
            helper.assertValueEqual(saved.approach(), spot.approach(), "the exact casting position survives saving");
            SettlementFisheryService.update(level, restored);
            helper.assertTrue(saved.valid() && saved.openWater(), "an unloaded update retains the observed terrain");
            var older = fixture.town().save(level.registryAccess());
            older.remove("FishingSpots");
            helper.assertTrue(SettlementRecord.load(older, level.registryAccess(), () -> {}).fishingSpots().isEmpty(),
                    "older towns can load before their first fishery observation");
            helper.assertFalse(level.hasChunkAt(saved.water()), "restoring fishing observations does not load terrain");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_fishery")
    public static void changedWaterAndObstructedCastingSpaceInvalidateObservedSites(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var spot = capture(helper, fixture);
            setBlock(fixture.chunk(), fixture.approach(), Blocks.STONE.defaultBlockState());
            spot.observe(helper.getLevel(), fixture.chunk());
            helper.assertFalse(spot.valid(), "a blocked standing position cannot fish");
            setBlock(fixture.chunk(), fixture.approach(), Blocks.AIR.defaultBlockState());
            setBlock(fixture.chunk(), fixture.approach().east().above(), Blocks.STONE.defaultBlockState());
            spot.observe(helper.getLevel(), fixture.chunk());
            helper.assertFalse(spot.valid(), "a wall obstructing the casting line prevents fishing");
            setBlock(fixture.chunk(), fixture.approach().east().above(), Blocks.AIR.defaultBlockState());
            setBlock(fixture.chunk(), fixture.water(), Blocks.STONE.defaultBlockState());
            spot.observe(helper.getLevel(), fixture.chunk());
            helper.assertFalse(spot.valid(), "removed water cannot keep producing fish");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_fishery")
    public static void containersAndFireAreNotFishingStandingPositions(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var spot = capture(helper, fixture);
            setBlock(fixture.chunk(), fixture.approach(), Blocks.FIRE.defaultBlockState());
            spot.observe(helper.getLevel(), fixture.chunk());
            helper.assertFalse(spot.valid(), "fire is not a safe standing position");
            setBlock(fixture.chunk(), fixture.approach(), Blocks.AIR.defaultBlockState());
            var barrel = Blocks.BARREL.defaultBlockState();
            setBlock(fixture.chunk(), fixture.approach().below(), barrel);
            fixture.chunk().setBlockEntity(new BarrelBlockEntity(fixture.approach().below(), barrel));
            spot.observe(helper.getLevel(), fixture.chunk());
            helper.assertFalse(spot.valid(), "workers do not stand on storage containers");
            helper.assertFalse(SettlementFishingSpot.load(spot.save()).valid(), "the container restriction survives an unloaded save");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_fishery")
    public static void fishingUsesSmallPoolsButReservesTreasureEligibilityForOpenWater(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var spot = capture(helper, fixture);
            setBlock(fixture.chunk(), fixture.water().east(2).below(), Blocks.STONE.defaultBlockState());
            spot.observe(helper.getLevel(), fixture.chunk());
            helper.assertTrue(spot.valid(), "a small pool still supports ordinary fishing");
            helper.assertFalse(spot.openWater(), "a blocked lower water layer cannot qualify for open-water treasure");
            setBlock(fixture.chunk(), fixture.water().east(2).below(), Blocks.WATER.defaultBlockState());
            setBlock(fixture.chunk(), fixture.water().above(), Blocks.LILY_PAD.defaultBlockState());
            spot.observe(helper.getLevel(), fixture.chunk());
            helper.assertTrue(spot.openWater(), "lily pads follow the native open-water rule");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_fishery")
    public static void partialFishingObservationsPreserveUnknownTerrainAndRejectUnseenSites(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            var original = capture(helper, fixture);
            var tag = original.save();
            BlockPos water = original.water().east(6);
            BlockPos approach = original.approach().east(6);
            tag.putLong("Water", water.asLong());
            tag.putLong("Approach", approach.asLong());
            var saved = SettlementFishingSpot.load(tag);
            for (int x = 4; x <= 7; x++) {
                for (int z = -2; z <= 2; z++) {
                    setBlock(fixture.chunk(), fixture.water().offset(x, 0, z), Blocks.WATER.defaultBlockState());
                    setBlock(fixture.chunk(), fixture.water().offset(x, -1, z), Blocks.WATER.defaultBlockState());
                }
            }
            setBlock(fixture.chunk(), approach.below(), Blocks.STONE.defaultBlockState());
            var shiftedArea = new SettlementArea(fixture.area().id(), SettlementAreaType.FISHERY, "Shifted shore",
                    fixture.area().min().east(6), fixture.area().max().east(6), 5, true);
            helper.assertTrue(SettlementFishingSpot.capture(level, shiftedArea, water, approach, fixture.chunk()) == null,
                    "new sites require observed terrain on both sides of a chunk boundary");
            saved.observe(level, fixture.chunk());
            helper.assertTrue(saved.valid() && saved.openWater(), "known terrain can refresh without erasing the missing neighboring chunk");
            setBlock(fixture.chunk(), water, Blocks.STONE.defaultBlockState());
            saved.observe(level, fixture.chunk());
            helper.assertFalse(saved.valid(), "a known water change still invalidates a partially loaded site");
            helper.assertFalse(level.hasChunkAt(water.east(2)), "partial observation never requests the missing neighbor");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_fishery")
    public static void finalChunkHandoffRecordsWaterChangesAndRemovedAreasRetireSites(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var spot = capture(helper, fixture);
            fixture.town().recordFishingSpots(fixture.area().id(), List.of(spot));
            setBlock(fixture.chunk(), fixture.water().east(2).below(), Blocks.STONE.defaultBlockState());
            SettlementBuildingService.onChunkUnload(new ChunkEvent.Unload(fixture.chunk()));
            var saved = fixture.town().fishingSpots().get(fixture.area().id()).stream()
                    .filter(candidate -> candidate.water().equals(fixture.water())).findFirst().orElseThrow();
            helper.assertFalse(saved.openWater(), "the final supplied chunk captures the changed pool before unloading");
            helper.assertTrue(fixture.town().removeArea(fixture.town().leader(), fixture.area().id()), "the fishing assignment can be removed");
            SettlementFisheryService.update(helper.getLevel(), fixture.town());
            helper.assertTrue(fixture.town().fishingSpots().isEmpty(), "removed areas cannot retain active fishing sites");
            helper.assertFalse(helper.getLevel().hasChunkAt(fixture.water()), "handoff and retirement leave the chunk unloaded");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_fishery")
    public static void incompleteFishingSnapshotsAndDuplicateSitesAreRejected(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var spot = capture(helper, fixture);
            var incomplete = spot.save();
            incomplete.putIntArray("Blocks", new int[0]);
            boolean rejected = false;
            try {
                SettlementFishingSpot.load(incomplete);
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            helper.assertTrue(rejected, "incomplete terrain cannot become a usable fishing site");
            var duplicate = fixture.town().save(helper.getLevel().registryAccess());
            var entries = new ListTag();
            entries.add(spot.save());
            entries.add(spot.save());
            duplicate.put("FishingSpots", entries);
            rejected = false;
            try {
                SettlementRecord.load(duplicate, helper.getLevel().registryAccess(), () -> {});
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            helper.assertTrue(rejected, "duplicate site positions cannot enter a town ledger");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_fishery")
    public static void fisheryDiscoveryContinuesAcrossBoundedSurveyPasses(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            SettlementFisheryService.observeArea(level, fixture.town(), fixture.area(), fixture.chunk());
            int first = fixture.town().fishingSpots().get(fixture.area().id()).size();
            helper.assertTrue(first > 0 && first <= 32, "one survey considers a bounded number of new water candidates");
            SettlementFisheryService.observeArea(level, fixture.town(), fixture.area(), fixture.chunk());
            var spots = fixture.town().fishingSpots().get(fixture.area().id());
            helper.assertTrue(spots.size() > first, "the next pass continues its search instead of revisiting only the first sites");
            helper.assertValueEqual(spots.stream().map(SettlementFishingSpot::water).distinct().count(), (long) spots.size(),
                    "continued discovery cannot duplicate sites");
            helper.assertFalse(level.hasChunkAt(fixture.water()), "continued surveys retain the no-chunk-load guarantee");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_fishery")
    public static void movedFishingAreasRetireTheirOldStandingPositions(GameTestHelper helper) {
        fixture(helper, fixture -> {
            fixture.town().recordFishingSpots(fixture.area().id(), List.of(capture(helper, fixture)));
            var moved = new SettlementArea(fixture.area().id(), SettlementAreaType.FISHERY, "New shore",
                    fixture.area().min().east(15), fixture.area().max().east(15), 5, true);
            helper.assertTrue(fixture.town().putArea(fixture.town().leader(), moved), "the fishing assignment can move");
            SettlementFisheryService.update(helper.getLevel(), fixture.town());
            helper.assertTrue(fixture.town().fishingSpots().isEmpty(), "old standing positions outside the new area are retired");
            helper.assertTrue(fixture.chunk().getBlockState(fixture.water()).is(Blocks.WATER), "retiring the observation does not change the real pool");
        });
    }

    private static SettlementFishingSpot capture(GameTestHelper helper, Fixture fixture) {
        var spot = SettlementFishingSpot.capture(helper.getLevel(), fixture.area(), fixture.water(), fixture.approach(), fixture.chunk());
        helper.assertTrue(spot != null && spot.valid(), "the fixture supplies a complete usable fishing site");
        return spot;
    }

    private static void setBlock(LevelChunk chunk, BlockPos position, BlockState state) {
        chunk.getSection(chunk.getSectionIndex(position.getY())).setBlockState(position.getX() & 15,
                position.getY() & 15, position.getZ() & 15, state);
    }

    private static void fixture(GameTestHelper helper, Consumer<Fixture> check) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(BlockPos.ZERO);
        var chunk = new LevelChunk(level, new ChunkPos(650_000 + origin.getX() * 4, 650_000 + origin.getZ() * 4));
        var water = new BlockPos(chunk.getPos().getMinBlockX() + 8, 63, chunk.getPos().getMinBlockZ() + 8);
        var approach = water.west(4).above();
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                setBlock(chunk, water.offset(x, 0, z), Blocks.WATER.defaultBlockState());
                setBlock(chunk, water.offset(x, -1, z), Blocks.WATER.defaultBlockState());
            }
        }
        for (int z = -3; z <= 3; z++) {
            setBlock(chunk, approach.offset(0, -1, z), Blocks.STONE.defaultBlockState());
        }
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), approach, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), approach, 0L).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.FISHERY, "Shore",
                approach.north(3), approach.south(3), 5, true);
        helper.assertTrue(town.putArea(town.leader(), area), "the shore fits the settlement");
        check.accept(new Fixture(town, area, chunk, water, approach));
        helper.succeed();
    }

    private record Fixture(SettlementRecord town, SettlementArea area, LevelChunk chunk, BlockPos water, BlockPos approach) {
    }
}
