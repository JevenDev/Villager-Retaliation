package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("settlement_housing_livability")
@PrefixGameTestTemplate(false)
public final class SettlementHousingLivabilityGameTests {
    private SettlementHousingLivabilityGameTests() {
    }

    @GameTest(template = "empty", batch = "settlement_housing_livability")
    public static void openAirLanternHomeCountsEachReachableBedOnce(GameTestHelper helper) {
        var home = home(helper);
        bed(helper.getLevel(), home.min().offset(1, 0, 3));
        var survey = SettlementBuildingSurvey.inspect(helper.getLevel(), home);
        helper.assertValueEqual(survey.beds().size(), 2, "two complete beds provide two slots without walls or a roof");
        helper.assertTrue(helper.getLevel().getBlockState(home.min().offset(4, 0, 0)).is(SettlementHousingSurvey.LIGHTS),
                "lanterns are accepted through the housing light tag");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_housing_livability")
    public static void tierOneTentCountsSixBedsWithoutDoorsOrEnclosure(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(1, 2, 1));
        var home = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HOME, "Tent", origin, origin.offset(4, 2, 5), 5, true);
        for (var pos : BlockPos.betweenClosed(origin.offset(-1, -1, -1), origin.offset(5, -1, 6))) level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        for (var pos : BlockPos.betweenClosed(origin.offset(-1, 0, -1), origin.offset(5, 3, 6))) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        for (int x : new int[]{0, 4}) for (int z : new int[]{0, 2, 4}) bed(level, origin.offset(x, 0, z));
        level.setBlock(origin.offset(2, 0, 5), Blocks.TORCH.defaultBlockState(), 2);
        helper.assertValueEqual(SettlementBuildingSurvey.inspect(level, home).beds().size(), 6, "one torch supports six accessible beds without a door");
        level.setBlock(origin.offset(2, 0, 5), Blocks.AIR.defaultBlockState(), 2);
        helper.assertTrue(SettlementBuildingSurvey.inspect(level, home).beds().isEmpty(), "daylight alone does not supply a housing fixture");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_housing_livability")
    public static void tierOneAllowsNarrowHousingWithoutAThreeByThreeFloor(GameTestHelper helper) {
        var home = home(helper);
        var level = helper.getLevel();
        var narrow = new SettlementArea(home.id(), home.type(), home.name(), home.min().east(3), home.max(), 5, true);
        helper.assertValueEqual(SettlementBuildingSurvey.inspect(level, narrow).beds().size(), 1, "a two-block-wide selection qualifies");
        for (int z = 0; z < 5; z++) level.setBlock(home.min().offset(1, -1, z), Blocks.AIR.defaultBlockState(), 2);
        helper.assertValueEqual(SettlementBuildingSurvey.inspect(level, narrow).beds().size(), 1, "a three by three floor is unnecessary");
        level.setBlock(home.min().offset(3, -1, 2), Blocks.AIR.defaultBlockState(), 2);
        helper.assertTrue(SettlementBuildingSurvey.inspect(level, narrow).beds().isEmpty(), "the bed itself still needs support");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_housing_livability")
    public static void bedNeedsAPathFromTheEntranceAndClearance(GameTestHelper helper) {
        var home = home(helper);
        var level = helper.getLevel();
        encloseApproaches(level, home);
        for (var pos : BlockPos.betweenClosed(home.min().offset(2, 0, 0), home.min().offset(2, 1, 4))) {
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
        }
        helper.assertTrue(SettlementBuildingSurvey.inspect(level, home).beds().isEmpty(), "a bed with a local exit but no entrance path is excluded");
        for (int y = 0; y < 2; y++) level.setBlock(home.min().offset(2, y, 2), Blocks.AIR.defaultBlockState(), 2);
        helper.assertValueEqual(SettlementBuildingSurvey.inspect(level, home).beds().size(), 1, "opening a two-block-high passage makes the bed reachable");
        level.setBlock(home.min().offset(3, 1, 2), Blocks.STONE.defaultBlockState(), 2);
        helper.assertTrue(SettlementBuildingSurvey.inspect(level, home).beds().isEmpty(), "blocked clearance above the bed invalidates it");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_housing_livability")
    public static void blockedEntranceAndUnlitTaggedFixtureDoNotQualify(GameTestHelper helper) {
        var home = home(helper);
        var level = helper.getLevel();
        encloseApproaches(level, home);
        var approach = home.min().offset(-1, 0, 2);
        level.setBlock(approach, Blocks.STONE.defaultBlockState(), 2);
        helper.assertTrue(SettlementBuildingSurvey.inspect(level, home).beds().isEmpty(), "a blocked outside approach is not a usable entrance");
        level.setBlock(approach, Blocks.AIR.defaultBlockState(), 2);
        var light = home.min().offset(4, 0, 0);
        level.setBlock(light, Blocks.REDSTONE_LAMP.defaultBlockState(), 2);
        helper.assertTrue(SettlementBuildingSurvey.inspect(level, home).beds().isEmpty(), "tagged fixtures must actually emit light");
        level.setBlock(light, Blocks.SOUL_LANTERN.defaultBlockState(), 2);
        helper.assertValueEqual(SettlementBuildingSurvey.inspect(level, home).beds().size(), 1, "soul lanterns also satisfy the data-driven tag");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_housing_livability")
    public static void supportedEntranceStepsAndPathsKeepBedsReachable(GameTestHelper helper) {
        var home = home(helper);
        var level = helper.getLevel();
        encloseApproaches(level, home);
        var approachFloor = home.min().offset(-1, -1, 2);
        for (var block : List.of(Blocks.OAK_STAIRS, Blocks.OAK_SLAB, Blocks.DIRT_PATH)) {
            level.setBlock(approachFloor, block.defaultBlockState(), 2);
            helper.assertValueEqual(SettlementBuildingSurvey.inspect(level, home).beds().size(), 1,
                    "supported partial-height entrance remains usable: " + block);
        }
        level.setBlock(approachFloor, Blocks.AIR.defaultBlockState(), 2);
        helper.assertTrue(SettlementBuildingSurvey.inspect(level, home).beds().isEmpty(), "an unsupported entrance still blocks housing");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_housing_livability")
    public static void observedLivabilityLossInvalidatesPreviouslyRecordedCapacity(GameTestHelper helper) {
        var home = home(helper);
        var level = helper.getLevel();
        var registry = new VillageAllegianceRegistrySavedData();
        var data = new SettlementSavedData();
        var id = registry.create(0L, level.dimension().location(), home.center(), "");
        var town = data.establish(registry, id, UUID.randomUUID(), home.center(), 0L).orElseThrow();
        town.putArea(town.leader(), home);
        SettlementBuildingService.refresh(level, town, home);
        helper.assertValueEqual(town.housingCapacity(), 1, "the live home provides one slot");
        level.setBlock(home.min().offset(4, 0, 0), Blocks.AIR.defaultBlockState(), 2);
        SettlementBuildingService.refresh(level, town, home);
        helper.assertValueEqual(town.housingCapacity(), 0, "an observed loss of required lighting removes the old capacity");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_housing_livability")
    public static void repeatedBedObservationsAcrossAreasCannotMultiplyCapacity(GameTestHelper helper) {
        var registry = new VillageAllegianceRegistrySavedData();
        var data = new SettlementSavedData();
        var center = helper.absolutePos(new BlockPos(1, 2, 1));
        var id = registry.create(0L, helper.getLevel().dimension().location(), center, "");
        var town = data.establish(registry, id, UUID.randomUUID(), center, 0L).orElseThrow();
        var bed = center.offset(1, 0, 1);
        for (int x : new int[]{0, 5}) {
            var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HOME, "Home", center.east(x), center.offset(x + 3, 2, 3), 5, true);
            town.putArea(town.leader(), area);
            town.recordSurvey(area.id(), new SettlementBuildingSurvey.Survey(0L, List.of(bed), List.of(), true));
        }
        helper.assertValueEqual(town.housingCapacity(), 1, "the same physical bed is counted once even in repeated saved observations");
        helper.succeed();
    }

    private static SettlementArea home(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(1, 2, 1));
        var home = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HOME, "Open home", origin, origin.offset(4, 2, 4), 5, true);
        for (var pos : BlockPos.betweenClosed(origin.offset(-1, -1, 0), origin.offset(4, -1, 4))) level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
        for (var pos : BlockPos.betweenClosed(origin.west(), home.max())) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        entrance(level, origin.south(2));
        level.setBlock(origin.offset(4, 0, 0), Blocks.LANTERN.defaultBlockState(), 2);
        bed(level, origin.offset(3, 0, 2));
        return home;
    }

    private static void encloseApproaches(ServerLevel level, SettlementArea home) {
        for (var pos : BlockPos.betweenClosed(home.min().offset(-1, 0, -1), home.max().offset(1, 0, 1).atY(home.min().getY()))) {
            if (home.contains(pos) || pos.equals(home.min().offset(-1, 0, 2))) continue;
            for (int y = 0; y <= 2; y++) level.setBlock(pos.above(y), Blocks.STONE.defaultBlockState(), 2);
        }
    }

    static void entrance(ServerLevel level, BlockPos position) {
        var door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
        level.setBlock(position, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 2);
        level.setBlock(position.above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
    }

    // supply lighting and an entrance without changing the bed layout
    static void furnish(ServerLevel level, SettlementArea home, int bedY) {
        var entrance = new BlockPos(home.min().getX(), bedY, home.min().getZ());
        for (var pos : new BlockPos[]{entrance.west(), entrance, entrance.east()}) {
            level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 2);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 2);
        }
        entrance(level, entrance);
        level.setBlock(new BlockPos(home.max().getX(), bedY + 2, home.max().getZ()), Blocks.SEA_LANTERN.defaultBlockState(), 2);
    }

    private static void bed(ServerLevel level, BlockPos head) {
        var state = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        level.setBlock(head, state.setValue(BedBlock.PART, BedPart.HEAD), 2);
        level.setBlock(head.south(), state.setValue(BedBlock.PART, BedPart.FOOT), 2);
    }
}
