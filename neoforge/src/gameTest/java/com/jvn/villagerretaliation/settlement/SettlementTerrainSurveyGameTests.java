package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementTerrainSurveyGameTests {
    private SettlementTerrainSurveyGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_terrain_surveys")
    public static void unloadedMiningChangesRemoveAWorkshopsRecordedFixture(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.MINE, false);
        var workshop = area(fixture, SettlementAreaType.WORKSHOP);
        fixture.town().putArea(fixture.town().leader(), workshop);
        var mine = new SettlementMine(fixture.terrain());
        mine.record(SettlementTerrainSection.load("Mine", fixture.terrain().id(), snapshot(fixture, Map.of())));
        fixture.town().recordMine(mine);
        SettlementBuildingService.refresh(helper.getLevel(), fixture.town(), workshop);
        helper.assertTrue(fixture.town().surveys().get(workshop.id()).fixtures().containsKey(fixture.table()),
                "the previously observed table supplies its shared workshop");
        helper.assertTrue(mine.change(Map.of(fixture.table(), Blocks.AIR.defaultBlockState())), "the recorded mining work removes the actual table");
        SettlementBuildingService.refresh(helper.getLevel(), fixture.town(), workshop);
        helper.assertTrue(fixture.town().surveys().get(workshop.id()).fixtures().isEmpty(),
                "an unloaded survey cannot preserve a workstation that recorded work removed");
        var restored = restore(helper, fixture.town());
        SettlementBuildingService.refresh(helper.getLevel(), restored, workshop);
        helper.assertTrue(restored.surveys().get(workshop.id()).fixtures().isEmpty(), "saving cannot restore the removed workstation");
        helper.assertFalse(helper.getLevel().hasChunkAt(fixture.origin()), "surveying the recorded mine never loads its chunk");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_terrain_surveys")
    public static void recordedTreeGrowthAndLoggingUpdateSharedHousing(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.FORESTRY, false);
        var home = area(fixture, SettlementAreaType.HOME);
        fixture.town().putArea(fixture.town().leader(), home);
        var forest = new SettlementForest(fixture.terrain());
        forest.record(SettlementForestSection.load(fixture.terrain().id(), snapshot(fixture, Map.of())));
        fixture.town().recordForest(forest);
        SettlementBuildingService.refresh(helper.getLevel(), fixture.town(), home);
        helper.assertValueEqual(fixture.town().housingCapacity(), 1, "the recorded bed has complete parts, headroom and an exit");
        helper.assertTrue(forest.change(Map.of(fixture.head().above(), Blocks.OAK_LOG.defaultBlockState())),
                "tree growth records an obstruction over the shared bed");
        SettlementBuildingService.refresh(helper.getLevel(), fixture.town(), home);
        helper.assertValueEqual(fixture.town().housingCapacity(), 0, "the new headroom obstruction invalidates housing while unloaded");
        var restored = restore(helper, fixture.town());
        SettlementBuildingService.refresh(helper.getLevel(), restored, home);
        helper.assertValueEqual(restored.housingCapacity(), 0, "the saved obstruction still blocks housing");
        helper.assertTrue(restored.forests().get(fixture.terrain().id()).change(Map.of(fixture.head().above(), Blocks.AIR.defaultBlockState())),
                "logging can remove the same recorded obstruction");
        SettlementBuildingService.refresh(helper.getLevel(), restored, home);
        helper.assertValueEqual(restored.housingCapacity(), 1, "clearing headroom restores the existing bed without another bed item");
        helper.assertFalse(helper.getLevel().hasChunkAt(fixture.origin()), "housing checks leave the forest unloaded");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_terrain_surveys")
    public static void recordedFieldsExposeRealCropHeightWithoutInventingLegacyHeadroom(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.FARM, false);
        var position = fixture.origin();
        var tag = field(fixture, position, true);
        var lower = Blocks.PITCHER_CROP.defaultBlockState().setValue(PitcherCropBlock.AGE, 4)
                .setValue(PitcherCropBlock.HALF, DoubleBlockHalf.LOWER);
        tag.put("Crop", NbtUtils.writeBlockState(lower));
        tag.put("Top", NbtUtils.writeBlockState(lower.setValue(PitcherCropBlock.HALF, DoubleBlockHalf.UPPER)));
        tag.putBoolean("Pending", true);
        tag.putLong("Version", 1L);
        fixture.town().recordField(SettlementFieldPlot.load(tag));
        var blocks = blocks(helper, fixture.town());
        helper.assertTrue(blocks.getBlockState(position.above()).equals(lower), "the recorded crop has its actual grown lower half");
        helper.assertTrue(blocks.getBlockState(position.above(2)).getValue(PitcherCropBlock.HALF) == DoubleBlockHalf.UPPER,
                "the upper half is observed rather than assumed to be air");
        helper.assertFalse(blocks.observed(position.above(3)), "space beyond the captured column stays unknown");
        var restored = restore(helper, fixture.town());
        helper.assertTrue(blocks(helper, restored).getBlockState(position.above(2)).is(Blocks.PITCHER_CROP), "crop height survives saving");
        var legacy = field(fixture, position, false);
        fixture.town().recordField(SettlementFieldPlot.load(legacy));
        helper.assertFalse(blocks(helper, fixture.town()).observed(position.above(2)), "older field saves cannot invent uncaptured headroom");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_terrain_surveys")
    public static void sharedObservationsRequireCompatibleStartingBlocks(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.MINE, false);
        var mine = new SettlementMine(fixture.terrain());
        var first = SettlementTerrainSection.load("Mine", fixture.terrain().id(), snapshot(fixture, Map.of()));
        mine.record(first);
        fixture.town().recordMine(mine);
        var neighboring = new SettlementArea(UUID.randomUUID(), SettlementAreaType.MINE, "Neighboring mine",
                fixture.terrain().max().east().atY(fixture.terrain().min().getY()), fixture.terrain().max().east(2), 5, true);
        helper.assertTrue(fixture.town().putArea(fixture.town().leader(), neighboring), "the neighboring mine has separate work bounds");
        var other = new SettlementMine(neighboring);
        BlockPos shared = fixture.table();
        var next = snapshot(shared, shared, shared.getY(), Map.of(shared, Blocks.CRAFTING_TABLE.defaultBlockState()));
        var second = SettlementTerrainSection.load("Mine", neighboring.id(), next);
        other.record(second);
        fixture.town().recordMine(other);
        first.change(Map.of(shared, Blocks.AIR.defaultBlockState()));
        var blocks = blocks(helper, fixture.town());
        helper.assertTrue(blocks.observed(shared) && blocks.getBlockState(shared).isAir(),
                "a recorded removal supersedes another snapshot of its unchanged starting block");
        second.change(Map.of(shared, Blocks.STONE.defaultBlockState()));
        helper.assertTrue(blocks(helper, fixture.town()).blocked(shared), "contradictory pending writes cannot establish a shared block state");
        next = snapshot(shared, shared, shared.getY(), Map.of(shared, Blocks.GOLD_BLOCK.defaultBlockState()));
        other.record(SettlementTerrainSection.load("Mine", neighboring.id(), next));
        blocks = blocks(helper, fixture.town());
        helper.assertTrue(blocks.blocked(shared) && !blocks.observed(shared),
                "a contradictory starting block prevents the changed record from claiming authority");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_terrain_surveys")
    public static void conflictingTerrainRecordsCannotPreserveAnOldUsableBuilding(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.MINE, false);
        var workshop = area(fixture, SettlementAreaType.WORKSHOP);
        fixture.town().putArea(fixture.town().leader(), workshop);
        var mine = new SettlementMine(fixture.terrain());
        var tag = snapshot(fixture, Map.of());
        var section = SettlementTerrainSection.load("Mine", fixture.terrain().id(), tag);
        section.change(Map.of(fixture.table(), Blocks.AIR.defaultBlockState()));
        tag = section.save();
        tag.putBoolean("Conflict", true);
        mine.record(SettlementTerrainSection.load("Mine", fixture.terrain().id(), tag));
        fixture.town().recordMine(mine);
        fixture.town().recordSurvey(workshop.id(), new SettlementBuildingSurvey.Survey(0L, List.of(), List.of(fixture.table()), true,
                Map.of(fixture.table(), net.minecraft.resources.ResourceLocation.withDefaultNamespace("crafting_table"))));
        SettlementBuildingService.refresh(helper.getLevel(), fixture.town(), workshop);
        helper.assertTrue(fixture.town().surveys().get(workshop.id()).fixtures().isEmpty(), "conflicted terrain cannot retain a previously verified workstation");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_terrain_surveys")
    public static void loadedWorldBlocksTakePrecedenceOverConflictingRecordedTerrain(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.MINE, true);
        var mine = new SettlementMine(fixture.terrain());
        var state = SettlementTerrainSection.load("Mine", fixture.terrain().id(),
                snapshot(fixture.table(), fixture.table(), fixture.origin().getY(), Map.of(fixture.table(), Blocks.CRAFTING_TABLE.defaultBlockState())));
        state.change(Map.of(fixture.table(), Blocks.AIR.defaultBlockState()));
        var tag = state.save();
        tag.putBoolean("Conflict", true);
        mine.record(SettlementTerrainSection.load("Mine", fixture.terrain().id(), tag));
        fixture.town().recordMine(mine);
        helper.getLevel().setBlockAndUpdate(fixture.table(), Blocks.GOLD_BLOCK.defaultBlockState());
        var before = fixture.town().save(helper.getLevel().registryAccess());
        var blocks = blocks(helper, fixture.town());
        helper.assertTrue(blocks.observed(fixture.table()) && !blocks.blocked(fixture.table()), "the loaded block supplies direct evidence");
        helper.assertTrue(blocks.getBlockState(fixture.table()).is(Blocks.GOLD_BLOCK), "stale background work does not replace the player's actual edit");
        helper.assertValueEqual(fixture.town().save(helper.getLevel().registryAccess()), before, "reading the combined view does not reconcile or mutate work");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_terrain_surveys")
    public static void removedTerrainAreasRetainOnlyTheirUndeliveredChanges(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.FORESTRY, false);
        var forest = new SettlementForest(fixture.terrain());
        forest.record(SettlementForestSection.load(fixture.terrain().id(), snapshot(fixture, Map.of())));
        fixture.town().recordForest(forest);
        helper.assertTrue(fixture.town().removeArea(fixture.town().leader(), fixture.terrain().id()), "the old work area can be removed");
        helper.assertFalse(blocks(helper, fixture.town()).observed(fixture.table()), "retired idle observations no longer establish building conditions");
        forest.change(Map.of(fixture.table(), Blocks.AIR.defaultBlockState()));
        helper.assertTrue(blocks(helper, fixture.town()).observed(fixture.table()) && blocks(helper, fixture.town()).getBlockState(fixture.table()).isAir(),
                "undelivered recorded work stays authoritative even after its designation is removed");
        var restored = restore(helper, fixture.town());
        helper.assertTrue(blocks(helper, restored).getBlockState(fixture.table()).isAir(), "pending work survives a save without its old designation");
        helper.succeed();
    }

    private static Fixture fixture(GameTestHelper helper, SettlementAreaType type, boolean loaded) {
        BlockPos nativeOrigin = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos origin = loaded ? nativeOrigin : new BlockPos(
                ((18_000_000 + nativeOrigin.getX() * 16) & ~15) + 4, 82, ((18_000_000 + nativeOrigin.getZ() * 16) & ~15) + 4);
        var town = new SettlementRecord(new VillageAllegianceId(UUID.randomUUID()), helper.getLevel().dimension().location(),
                origin, UUID.randomUUID(), 0L, () -> {});
        var area = new SettlementArea(UUID.randomUUID(), type, "Terrain", origin.below(), origin.offset(4, 3, 4), 5, true);
        town.putArea(town.leader(), area);
        return new Fixture(town, area, origin);
    }

    private static SettlementArea area(Fixture fixture, SettlementAreaType type) {
        return new SettlementArea(UUID.randomUUID(), type, type.name(), fixture.origin(), fixture.origin().offset(4, 2, 4), 5, true);
    }

    private static SettlementBuildingSurvey.ObservedBlocks blocks(GameTestHelper helper, SettlementRecord town) {
        return new SettlementBuildingSurvey.ObservedBlocks(helper.getLevel(), null, town);
    }

    private static SettlementRecord restore(GameTestHelper helper, SettlementRecord town) {
        return SettlementRecord.load(town.save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
    }

    private static CompoundTag field(Fixture fixture, BlockPos position, boolean topKnown) {
        var tag = new CompoundTag();
        tag.putUUID("Id", UUID.randomUUID());
        tag.putUUID("Area", fixture.terrain().id());
        tag.putLong("Position", position.asLong());
        for (var key : List.of("Soil", "BaselineSoil")) tag.put(key, NbtUtils.writeBlockState(Blocks.FARMLAND.defaultBlockState()));
        for (var key : List.of("Crop", "BaselineCrop")) tag.put(key, NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));
        if (topKnown) {
            for (var key : List.of("Top", "BaselineTop")) tag.put(key, NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));
        }
        return tag;
    }

    private static CompoundTag snapshot(Fixture fixture, Map<BlockPos, BlockState> overrides) {
        var states = new java.util.HashMap<>(overrides);
        var foot = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST).setValue(BedBlock.PART, BedPart.FOOT);
        states.putIfAbsent(fixture.head().west(), foot);
        states.putIfAbsent(fixture.head(), foot.setValue(BedBlock.PART, BedPart.HEAD));
        states.putIfAbsent(fixture.table(), Blocks.CRAFTING_TABLE.defaultBlockState());
        return snapshot(fixture.terrain().min(), fixture.terrain().max(), fixture.origin().getY(), states);
    }

    private static CompoundTag snapshot(BlockPos min, BlockPos max, int floor, Map<BlockPos, BlockState> overrides) {
        var palette = new ArrayList<BlockState>();
        int[] cells = new int[(max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1)];
        int index = 0;
        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int x = min.getX(); x <= max.getX(); x++) {
                    var position = new BlockPos(x, y, z);
                    var state = overrides.getOrDefault(position, y < floor ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState());
                    if (!palette.contains(state)) palette.add(state);
                    cells[index++] = palette.indexOf(state);
                }
            }
        }
        var tag = new CompoundTag();
        tag.putUUID("Id", UUID.randomUUID());
        tag.putLong("Min", min.asLong());
        tag.putLong("Max", max.asLong());
        var states = new ListTag();
        palette.forEach(state -> states.add(NbtUtils.writeBlockState(state)));
        tag.put("Palette", states);
        tag.putIntArray("Baseline", cells);
        return tag;
    }

    private record Fixture(SettlementRecord town, SettlementArea terrain, BlockPos origin) {
        BlockPos head() {
            return this.origin.offset(2, 0, 2);
        }

        BlockPos table() {
            return this.origin.offset(4, 0, 2);
        }
    }
}
