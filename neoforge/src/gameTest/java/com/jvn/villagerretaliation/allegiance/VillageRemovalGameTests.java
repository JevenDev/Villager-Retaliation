package com.jvn.villagerretaliation.allegiance;

import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class VillageRemovalGameTests {
    private VillageRemovalGameTests() {
    }

    @GameTest(template = "empty", batch = "village_removal")
    public static void commandRemovesIndependentVillageInManagedOverlap(GameTestHelper helper) throws Exception {
        withOccupiedBed(helper, bed -> {
            var level = helper.getLevel();
            var registry = VillageAllegianceRegistrySavedData.get(level);
            var independent = registry.discoverAt(level, bed).orElseThrow();
            var managed = registry.create(level.getGameTime(), level.dimension().location(), bed, "");
            helper.assertTrue(registry.manage(managed, bed, Set.of(SectionPos.asLong(bed))),
                    "a managed settlement overlaps the independent village");
            var player = helper.makeMockServerPlayerInLevel();
            player.setPos(Vec3.atCenterOf(bed));
            var source = player.createCommandSourceStack().withPermission(2)
                    .withPosition(Vec3.atCenterOf(bed.offset(512, 0, 0)));
            var dispatcher = level.getServer().getCommands().getDispatcher();

            helper.assertValueEqual(dispatcher.execute("vr admin village removeHere", source), 1,
                    "the command uses the player's actual position even when the source is positioned elsewhere");
            helper.assertTrue(registry.record(independent).orElseThrow().archived(),
                    "the overlapping independent village is removed");
            helper.assertTrue(registry.recordsAt(level, bed).stream().allMatch(record -> record.id().equals(managed)),
                    "only the managed settlement retains bounds here");
            helper.assertTrue(registry.isManaged(managed) && !registry.record(managed).orElseThrow().archived(),
                    "the managed settlement remains active");
            helper.assertFalse(registry.removeVillage(managed), "managed villages reject direct removal too");
            helper.assertValueEqual(dispatcher.execute("vr admin village removeHere", source), 0,
                    "repeating the command cannot remove the managed settlement");
            player.setPos(Vec3.atCenterOf(bed.offset(512, 0, 0)));
            helper.assertValueEqual(dispatcher.execute("vr admin village removeHere", source), 0,
                    "the command fails outside any tracked village");
            helper.assertTrue(level.getBlockState(bed).is(Blocks.RED_BED), "removal leaves the physical village intact");
        });
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "village_removal")
    public static void removalSurvivesReloadAndCannotBeUndoneByMerging(GameTestHelper helper) throws Exception {
        withOccupiedBed(helper, bed -> {
            var level = helper.getLevel();
            var registry = new VillageAllegianceRegistrySavedData();
            var village = registry.discoverAt(level, bed).orElseThrow();
            var alias = registry.create(1L, level.dimension().location(), bed, "");
            UUID resident = UUID.randomUUID();
            registry.addOrUpdateResident(alias, resident, true, 2L);
            helper.assertTrue(registry.merge(alias, village), "the village includes an older identity");
            helper.assertTrue(registry.removeVillage(alias), "removing an alias removes its canonical village");
            helper.assertTrue(registry.resolveAt(level, bed).isEmpty(), "removal invalidates the bounds index");
            helper.assertTrue(registry.discoverAt(level, bed).isEmpty(),
                    "occupied beds cannot immediately recreate a removed village");

            var loaded = VillageAllegianceRegistrySavedData.load(
                    registry.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
            loaded.clearRuntimeCache();
            loaded.ensureRecord(village, 3L, level.dimension().location(), bed);
            loaded.addOrUpdateResident(alias, resident, true, 4L);
            loaded.refreshLoadedLifecycles(level, 100L);
            helper.assertTrue(loaded.discoverAt(level, bed).isEmpty(),
                    "removal survives reload, resident registration, and lifecycle refresh");
            helper.assertTrue(loaded.canonicalRecord(alias).orElseThrow().residents().containsKey(resident),
                    "historical resident references remain valid");
            helper.assertFalse(loaded.undoMerge(alias), "undoing an old merge cannot restore removed bounds");
            var neighbor = loaded.create(5L, level.dimension().location(), bed.offset(256, 0, 0), "");
            helper.assertFalse(loaded.merge(village, neighbor), "removed sources cannot be merged into active villages");
            helper.assertFalse(loaded.merge(neighbor, village), "removed targets cannot be revived by merging");
            helper.assertFalse(loaded.removeVillage(village), "repeated removal is rejected");
        });
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "village_removal")
    public static void suppressionIsLimitedToRemovedVillageAndDimension(GameTestHelper helper) throws Exception {
        withOccupiedBed(helper, bed -> {
            var level = helper.getLevel();
            var registry = new VillageAllegianceRegistrySavedData();
            var village = registry.discoverAt(level, bed).orElseThrow();
            helper.assertTrue(registry.archive(village), "ordinary lifecycle archival succeeds");
            var replacement = registry.discoverAt(level, bed).orElseThrow();
            helper.assertFalse(replacement.equals(village), "ordinary archival still allows discovery");
            helper.assertTrue(registry.removeVillage(replacement), "explicit removal suppresses discovery");
            CompoundTag saved = registry.save(new CompoundTag(), level.registryAccess());
            for (var raw : saved.getList("Records", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                var record = (CompoundTag) raw;
                if (record.getUUID("Id").equals(replacement.value())) {
                    record.putString("OriginDimension", "minecraft:the_nether");
                }
            }
            var otherDimension = VillageAllegianceRegistrySavedData.load(saved, level.registryAccess());
            helper.assertTrue(otherDimension.discoverAt(level, bed).isPresent(),
                    "removal in another dimension does not suppress discovery at the same coordinates");

            withOccupiedBed(helper, bed.offset(256, 0, 0), otherBed ->
                    helper.assertTrue(registry.discoverAt(level, otherBed).isPresent(),
                            "occupied beds outside the removed bounds can still create a village"));
        });
        helper.succeed();
    }

    private static void withOccupiedBed(GameTestHelper helper, BedTest test) throws Exception {
        withOccupiedBed(helper, helper.absolutePos(new BlockPos(2, 2, 4098)), test);
    }

    private static void withOccupiedBed(GameTestHelper helper, BlockPos bed, BedTest test) throws Exception {
        var level = helper.getLevel();
        level.getChunkAt(bed);
        level.getChunkAt(bed.south());
        var head = level.getBlockState(bed);
        var foot = level.getBlockState(bed.south());
        var headSupport = level.getBlockState(bed.below());
        var footSupport = level.getBlockState(bed.south().below());
        var manager = level.getPoiManager();
        try {
            level.setBlockAndUpdate(bed.below(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(bed.south().below(), Blocks.STONE.defaultBlockState());
            var state = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
            level.setBlock(bed, state.setValue(BedBlock.PART, BedPart.HEAD), 2);
            level.setBlock(bed.south(), state.setValue(BedBlock.PART, BedPart.FOOT), 2);
            manager.add(bed, level.registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE)
                    .getHolderOrThrow(PoiTypes.HOME));
            helper.assertTrue(manager.take(type -> type.is(PoiTypes.HOME),
                    (type, pos) -> pos.equals(bed), bed, 1).isPresent(), "the village bed is occupied");
            test.run(bed);
        } finally {
            manager.remove(bed);
            level.setBlock(bed, head, 2);
            level.setBlock(bed.south(), foot, 2);
            level.setBlock(bed.below(), headSupport, 2);
            level.setBlock(bed.south().below(), footSupport, 2);
        }
    }

    @FunctionalInterface
    private interface BedTest {
        void run(BlockPos bed) throws Exception;
    }
}
