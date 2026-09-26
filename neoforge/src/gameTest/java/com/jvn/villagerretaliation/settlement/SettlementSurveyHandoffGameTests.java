package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementSurveyHandoffGameTests {
    private SettlementSurveyHandoffGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_survey_handoff")
    public static void unloadSurveyFindsNewBedsBeforePoiRegistration(GameTestHelper helper) {
        Fixture fixture = fixture(helper, SettlementAreaType.HOME);
        BlockPos head = fixture.town().center();
        bed(fixture.chunk(), head);
        helper.assertFalse(helper.getLevel().hasChunkAt(head), "the supplied chunk is not registered in the level");
        SettlementBuildingService.onChunkUnload(new ChunkEvent.Unload(fixture.chunk()));
        helper.assertValueEqual(fixture.town().housingCapacity(), 1, "the final chunk snapshot finds the usable bed directly");
        helper.assertFalse(helper.getLevel().hasChunkAt(head), "surveying the supplied chunk does not register or load it");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_survey_handoff")
    public static void unloadSurveyRemovesBedsDestroyedSinceTheLastObservation(GameTestHelper helper) {
        Fixture fixture = fixture(helper, SettlementAreaType.HOME);
        BlockPos head = fixture.town().center();
        bed(fixture.chunk(), head);
        SettlementBuildingService.onChunkLoad(new ChunkEvent.Load(fixture.chunk(), false));
        helper.assertValueEqual(fixture.town().housingCapacity(), 1, "the initial supplied chunk contains a home");
        set(fixture.chunk(), head.south(), Blocks.AIR.defaultBlockState());
        SettlementBuildingService.onChunkUnload(new ChunkEvent.Unload(fixture.chunk()));
        helper.assertValueEqual(fixture.town().housingCapacity(), 0, "a destroyed bed half cannot remain counted while unloaded");
        helper.assertFalse(helper.getLevel().hasChunkAt(head), "the final survey needs no live chunk lookup");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_survey_handoff")
    public static void suppliedChunkRefreshesFixturesAndPreservesUnobservedNeighbors(GameTestHelper helper) {
        Fixture fixture = fixture(helper, SettlementAreaType.WORKSHOP);
        var town = fixture.town();
        BlockPos center = town.center();
        var area = new SettlementArea(fixture.area().id(), SettlementAreaType.WORKSHOP, "Workshop",
                center, center.offset(26, 2, 2), 5, true);
        town.putArea(town.leader(), area);
        BlockPos removed = center.east();
        BlockPos added = center.east(2);
        BlockPos unobserved = center.east(20);
        ResourceLocation table = ResourceLocation.withDefaultNamespace("crafting_table");
        town.recordSurvey(area.id(), new SettlementBuildingSurvey.Survey(0L, List.of(), List.of(removed, unobserved),
                true, Map.of(removed, table, unobserved, table)));
        set(fixture.chunk(), added, Blocks.CRAFTING_TABLE.defaultBlockState());
        SettlementBuildingService.onChunkLoad(new ChunkEvent.Load(fixture.chunk(), false));
        var survey = town.surveys().get(area.id());
        helper.assertFalse(survey.fixtures().containsKey(removed), "a removed table is invalidated before background use");
        helper.assertValueEqual(survey.fixtures().get(added), table, "a newly built table is observed at chunk load");
        helper.assertValueEqual(survey.fixtures().get(unobserved), table, "the unobserved neighboring table keeps its verified state");
        helper.assertValueEqual(survey.workstations().size(), 2, "workstation and fixture lists stay consistent");
        helper.assertFalse(helper.getLevel().hasChunkAt(unobserved), "refreshing one chunk does not load its neighbors");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_survey_handoff")
    public static void suppliedNeighborUpdatesBedExitsOutsideTheHousingArea(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        ChunkPos position = new ChunkPos(650_000 + origin.getX() * 4, 650_000 + origin.getZ() * 4);
        LevelChunk housing = level.getChunk(position.x, position.z);
        BlockPos head = new BlockPos(position.getMaxBlockX(), 90, position.getMinBlockZ() + 6);
        bed(housing, head);
        for (BlockPos wall : List.of(head.north(), head.west(), head.south().west(), head.south(2))) {
            set(housing, wall, Blocks.STONE.defaultBlockState());
        }
        var neighbor = new LevelChunk(level, new ChunkPos(position.x + 1, position.z));
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), head, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), head, 0L).orElseThrow();
        var home = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HOME, "Boundary home", head, head.south().above(), 5, true);
        town.putArea(town.leader(), home);
        set(neighbor, head.east().below(), Blocks.STONE.defaultBlockState());
        SettlementBuildingService.onChunkLoad(new ChunkEvent.Load(neighbor, false));
        helper.assertValueEqual(town.housingCapacity(), 1, "a supplied neighboring exit supports the real bed");
        set(neighbor, head.east(), Blocks.STONE.defaultBlockState());
        set(neighbor, head.south().east(), Blocks.STONE.defaultBlockState());
        SettlementBuildingService.onChunkUnload(new ChunkEvent.Unload(neighbor));
        helper.assertValueEqual(town.housingCapacity(), 0, "changed exits invalidate housing even outside the registered area");
        helper.assertFalse(level.hasChunkAt(head.east()), "checking an exit does not force the supplied neighbor to load");
        helper.succeed();
    }

    private static Fixture fixture(GameTestHelper helper, SettlementAreaType type) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        ChunkPos position = new ChunkPos(600_000 + origin.getX() * 4, 600_000 + origin.getZ() * 4);
        var chunk = new LevelChunk(level, position);
        BlockPos center = new BlockPos(position.getMinBlockX() + 5, 90, position.getMinBlockZ() + 5);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, 0L).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), type, "Handoff area", center, center.offset(4, 3, 4), 5, true);
        town.putArea(town.leader(), area);
        return new Fixture(town, area, chunk);
    }

    private static void bed(LevelChunk chunk, BlockPos head) {
        var bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        set(chunk, head, bed.setValue(BedBlock.PART, BedPart.HEAD));
        set(chunk, head.south(), bed.setValue(BedBlock.PART, BedPart.FOOT));
        set(chunk, head.north().below(), Blocks.STONE.defaultBlockState());
        set(chunk, head.west().below(), Blocks.STONE.defaultBlockState());
    }

    private static void set(LevelChunk chunk, BlockPos position, BlockState state) {
        chunk.getSection(chunk.getSectionIndex(position.getY())).setBlockState(position.getX() & 15,
                position.getY() & 15, position.getZ() & 15, state);
    }

    private record Fixture(SettlementRecord town, SettlementArea area, LevelChunk chunk) {
    }
}
