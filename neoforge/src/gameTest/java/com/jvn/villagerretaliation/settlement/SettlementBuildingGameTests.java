package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.block.VillagerRetaliationBlocks;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementBuildingGameTests {
    private SettlementBuildingGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void managementTableFoundsAnEmptyTownAndRejectsTakeover(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.getLevel().setBlockAndUpdate(center, VillagerRetaliationBlocks.SETTLEMENT_MANAGEMENT.get().defaultBlockState());
        ServerPlayer leader = player(helper, center);
        var result = SettlementFoundingService.found(leader, center);
        helper.assertValueEqual(result.status(), SettlementFoundingService.Status.FOUNDED, "an empty town can be founded");
        var town = result.town().orElseThrow();
        helper.assertValueEqual(town.stage(), SettlementStage.CAMP, "new towns start as camps");
        helper.assertTrue(VillageAllegianceRegistrySavedData.get(helper.getLevel())
                .residentRoster(town.id(), helper.getLevel().getGameTime()).isEmpty(), "founding requires no residents");
        helper.assertValueEqual(SettlementFoundingService.found(leader, center).town().orElseThrow().id(), town.id(),
                "reopening the table preserves the town identity");
        ServerPlayer stranger = player(helper, center);
        helper.assertTrue(SettlementFoundingService.found(stranger, center).town().isEmpty(),
                "another player cannot take leadership through the table");
        leader.setPos(center.getX() + 30.0D, center.getY(), center.getZ());
        helper.assertValueEqual(SettlementFoundingService.found(leader, center).status(),
                SettlementFoundingService.Status.UNAVAILABLE, "founding requests require proximity to the real table");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void inhabitedVillageRequiresExplicitLeadershipAcceptance(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(center, VillagerRetaliationBlocks.SETTLEMENT_MANAGEMENT.get().defaultBlockState());
        BlockPos poi = center.above(2);
        var manager = level.getPoiManager();
        manager.add(poi, level.registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE)
                .getHolderOrThrow(PoiTypes.HOME));
        manager.take(type -> type.is(PoiTypes.HOME), (type, pos) -> pos.equals(poi), poi, 1).orElseThrow();
        helper.runAfterDelay(5, () -> {
            var registry = VillageAllegianceRegistrySavedData.get(level);
            var village = registry.discoverAt(level, center).orElseThrow();
            UUID resident = UUID.randomUUID();
            registry.addOrUpdateResident(village, resident, true, level.getGameTime());
            String name = registry.record(village).orElseThrow().displayName();
            try {
                var result = SettlementFoundingService.found(player(helper, center), center);
                helper.assertValueEqual(result.status(), SettlementFoundingService.Status.INDEPENDENT_VILLAGE,
                        "placing a table does not automatically enroll an inhabited village");
                helper.assertFalse(registry.isManaged(village), "the independent village keeps its leadership");
                helper.assertValueEqual(registry.record(village).orElseThrow().displayName(), name, "the name is preserved");
                helper.assertTrue(registry.record(village).orElseThrow().residents().containsKey(resident),
                        "the existing resident identity is preserved");
            } finally {
                manager.release(poi);
                manager.remove(poi);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void housingRequiresACompleteAccessibleBedInsideTheHome(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos head = helper.absolutePos(new BlockPos(3, 2, 3));
        BlockPos foot = head.south();
        for (BlockPos pos : BlockPos.betweenClosed(head.offset(-1, -1, -1), head.offset(1, -1, 2))) {
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        }
        var bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        level.setBlock(head, bed.setValue(BedBlock.PART, BedPart.HEAD), 2);
        level.setBlock(foot, bed.setValue(BedBlock.PART, BedPart.FOOT), 2);
        helper.assertTrue(SettlementBuildingSurvey.usableBed(level, head), "a complete bed with an exit is usable");
        helper.assertFalse(SettlementBuildingSurvey.usableBed(level, foot), "a bed is counted only once");
        level.setBlockAndUpdate(head.above(), Blocks.STONE.defaultBlockState());
        helper.assertFalse(SettlementBuildingSurvey.usableBed(level, head), "a blocked bed is not usable");
        level.removeBlock(head.above(), false);
        level.getPoiManager().add(head, level.registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE)
                .getHolderOrThrow(PoiTypes.HOME));
        var home = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HOME, "Home",
                head.offset(-1, -1, -1), head.offset(1, 2, 2), 5, true);
        SettlementHousingLivabilityGameTests.furnish(level, home, head.getY());
        helper.assertValueEqual(SettlementBuildingSurvey.inspect(level, home).beds().size(), 1,
                "the survey counts the real usable bed");
        var cut = new SettlementArea(home.id(), SettlementAreaType.HOME, "Home",
                head.offset(-1, -1, -1), head.offset(1, 2, 0), 5, true);
        helper.assertTrue(SettlementBuildingSurvey.inspect(level, cut).beds().isEmpty(),
                "both halves of a bed must belong to the home");
        level.removeBlock(foot, false);
        helper.assertFalse(SettlementBuildingSurvey.usableBed(level, head), "half a bed cannot provide housing");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void housingSurveysPersistAndUnloadedChunksKeepTheirLastObservation(GameTestHelper helper) {
        var registry = new VillageAllegianceRegistrySavedData();
        var data = new SettlementSavedData();
        BlockPos center = new BlockPos(4_000_000, 64, 4_000_000);
        var id = registry.create(0L, helper.getLevel().dimension().location(), center, "");
        UUID leader = UUID.randomUUID();
        var town = data.establish(registry, id, leader, center, 0L).orElseThrow();
        var home = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HOME, "Home",
                center, center.offset(5, 4, 5), 5, true);
        data.putArea(registry, id, leader, home);
        BlockPos bed = center.offset(2, 1, 2);
        town.recordSurvey(home.id(), new SettlementBuildingSurvey.Survey(10L, List.of(bed), List.of(), true));
        helper.assertFalse(helper.getLevel().hasChunkAt(center), "the distant home is not loaded");
        SettlementBuildingService.refresh(helper.getLevel(), town, home);
        helper.assertValueEqual(town.housingCapacity(), 1, "unloading does not erase verified housing");
        helper.assertFalse(helper.getLevel().hasChunkAt(center), "surveying does not force chunks to load");
        var restored = SettlementSavedData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()),
                helper.getLevel().registryAccess()).get(id).orElseThrow();
        helper.assertValueEqual(restored.housingCapacity(), 1, "verified housing survives a world reload");
        var resized = new SettlementArea(home.id(), home.type(), home.name(), center, center.offset(1, 4, 1), 5, true);
        helper.assertTrue(data.putArea(registry, id, leader, resized), "the home can be resized");
        helper.assertValueEqual(town.housingCapacity(), 0, "resizing invalidates the old bed count");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void buildingRegistrationChecksAuthorityAndWorldHeight(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(2, 2, 2));
        ServerPlayer leader = player(helper, center);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "");
        SettlementRecord town = SettlementSavedData.get(level)
                .establish(registry, id, leader.getUUID(), center, level.getGameTime()).orElseThrow();
        ServerPlayer delegate = player(helper, center);
        town.setPermissions(leader.getUUID(), delegate.getUUID(), Set.of(SettlementPermission.STAFF));
        var home = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HOME, "Home",
                center, center.offset(3, 3, 3), 5, true);
        helper.assertFalse(SettlementBuildingService.register(delegate, id, home), "staffing does not grant construction");
        town.setPermissions(leader.getUUID(), delegate.getUUID(), Set.of(SettlementPermission.BUILD));
        helper.assertTrue(SettlementBuildingService.register(delegate, id, home), "construction delegates can register homes");
        var invalid = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HOME, "Invalid",
                center.atY(level.getMaxBuildHeight()), center.offset(3, 0, 3).atY(level.getMaxBuildHeight()), 5, true);
        helper.assertFalse(SettlementBuildingService.register(delegate, id, invalid), "areas cannot extend beyond world height");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_housing_boundary")
    public static void unobservedBedExitsPreserveHousingUntilTheBoundaryLoads(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos head = new BlockPos(6_000_015, 64, 6_000_008);
        BlockPos foot = head.south();
        var chunk = level.getChunk(Math.floorDiv(head.getX(), 16), Math.floorDiv(head.getZ(), 16));
        var bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        chunk.setBlockState(head, bed.setValue(BedBlock.PART, BedPart.HEAD), false);
        chunk.setBlockState(foot, bed.setValue(BedBlock.PART, BedPart.FOOT), false);
        chunk.setBlockState(head.below(), Blocks.STONE.defaultBlockState(), false);
        chunk.setBlockState(foot.below(), Blocks.STONE.defaultBlockState(), false);
        for (BlockPos wall : List.of(head.north(), head.west(), foot.west(), foot.south())) {
            chunk.setBlockState(wall, Blocks.STONE.defaultBlockState(), false);
        }
        level.getPoiManager().add(head, level.registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE)
                .getHolderOrThrow(PoiTypes.HOME));
        helper.assertFalse(level.hasChunkAt(head.east()), "the possible bed exits lie in an unloaded neighboring chunk");
        var registry = new VillageAllegianceRegistrySavedData();
        var data = new SettlementSavedData();
        var id = registry.create(0L, level.dimension().location(), head, "");
        var town = data.establish(registry, id, UUID.randomUUID(), head, 0L).orElseThrow();
        var home = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HOME, "Boundary home", head.offset(-1, 0, -1), foot.offset(3, 2, 1), 5, true);
        data.putArea(registry, id, town.leader(), home);
        town.recordSurvey(home.id(), new SettlementBuildingSurvey.Survey(0L, List.of(head), List.of(), true));
        SettlementBuildingService.refresh(level, town, home);
        helper.assertValueEqual(town.housingCapacity(), 1, "an unobserved exit does not erase previously verified housing");
        helper.assertFalse(level.hasChunkAt(head.east()), "the survey leaves the neighboring chunk unloaded");
        var neighbor = level.getChunk(Math.floorDiv(head.east().getX(), 16), Math.floorDiv(head.getZ(), 16));
        for (var pos : BlockPos.betweenClosed(head.offset(1, -1, -1), head.offset(3, -1, 2))) {
            neighbor.setBlockState(pos, Blocks.STONE.defaultBlockState(), false);
        }
        SettlementHousingLivabilityGameTests.entrance(level, head.offset(2, 0, 0));
        neighbor.setBlockState(head.offset(3, 2, 2), Blocks.SEA_LANTERN.defaultBlockState(), false);
        SettlementBuildingService.refresh(level, town, home);
        helper.assertValueEqual(town.housingCapacity(), 1, "a newly observed safe exit confirms the existing bed");
        neighbor.setBlockState(head.east(), Blocks.STONE.defaultBlockState(), false);
        neighbor.setBlockState(foot.east(), Blocks.STONE.defaultBlockState(), false);
        SettlementBuildingService.refresh(level, town, home);
        helper.assertValueEqual(town.housingCapacity(), 0, "fully observed blocked exits remove unusable housing");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_building")
    public static void incrementalSurveysPublishAtomicallyAndCancelReplacedAreas(GameTestHelper helper) {
        var level = helper.getLevel();
        var center = helper.absolutePos(new BlockPos(2, 2, 2));
        var leader = player(helper, center);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "Survey queue");
        var town = SettlementSavedData.get(level).establish(registry, id, leader.getUUID(), center, level.getGameTime()).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.WORKSHOP, "Workshop",
                center, center.offset(1, 31, 1), 5, true);
        town.putArea(leader.getUUID(), area);
        level.setBlockAndUpdate(center, Blocks.CRAFTING_TABLE.defaultBlockState());
        var work = SettlementBuildingService.work(level, town, area);
        work.run();
        helper.assertFalse(town.surveys().containsKey(area.id()), "the first work unit cannot publish a partial survey");
        int steps = 1;
        while (!SettlementWork.complete(work) && steps++ < 30) work.run();
        helper.assertTrue(SettlementWork.complete(work), "bounded sections complete without draining the queue in one step");
        helper.assertTrue(town.surveys().get(area.id()).workstations().contains(center), "the complete survey contains the observed fixture");
        var prior = town.surveys().get(area.id());
        var cancelled = SettlementBuildingService.work(level, town, area);
        cancelled.run();
        var replacement = new SettlementArea(area.id(), area.type(), area.name(), area.min(), area.max(), 6, true);
        town.putArea(leader.getUUID(), replacement);
        cancelled.run();
        helper.assertTrue(SettlementWork.complete(cancelled), "editing an area cancels its pending scan");
        helper.assertValueEqual(town.surveys().get(area.id()), prior, "a cancelled scan cannot replace the last complete survey");
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper, BlockPos center) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "TownBuilder"));
        player.setPos(center.getX() + 0.5D, center.getY(), center.getZ() + 0.5D);
        return player;
    }
}
