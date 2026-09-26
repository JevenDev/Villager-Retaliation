package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.work.logging.LoggingTreeGeometry;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementForestryGameTests {
    private SettlementForestryGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_forestry")
    public static void forestSurveysDistinguishObservedAirFromMissingChunks(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var forest = observe(helper, fixture, fixture.first());
            BlockPos air = fixture.root().west(3);
            BlockPos missing = fixture.root().east(16);
            helper.assertTrue(forest.observed(air) && forest.getBlockState(air).isAir(), "survey retains known empty growth space");
            helper.assertFalse(forest.observed(missing), "the adjacent absent chunk is unknown");
            helper.assertTrue(forest.getBlockState(missing).is(Blocks.BARRIER), "unknown terrain cannot appear as free growth space");
            helper.assertFalse(forest.observed(fixture.area().min().west()), "terrain outside the designated forest stays unknown");
            observe(helper, fixture, fixture.second());
            helper.assertTrue(forest.observed(missing) && forest.getBlockState(missing).isAir(), "a later supplied chunk fills in the missing section");
            helper.assertTrue(forest.getBlockState(fixture.root()).is(Blocks.OAK_LOG), "partial surveys retain observations from other absent chunks");
            helper.assertFalse(helper.getLevel().hasChunkAt(fixture.root()) || helper.getLevel().hasChunkAt(missing),
                    "surveying supplied chunks does not force either chunk into the level");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_forestry")
    public static void observedForestsReuseNativeNaturalTreeAndFilterRules(GameTestHelper helper) {
        fixture(helper, fixture -> {
            BlockPos build = fixture.root().east(6);
            tree(fixture.first(), build, true);
            var forest = observe(helper, fixture, fixture.first());
            var logs = LoggingTreeGeometry.connectedTreeLogs(forest, forest::observed, fixture.root(), Set.of());
            helper.assertValueEqual(logs.size(), 4, "the observed natural oak uses the native connected log selection");
            helper.assertValueEqual(LoggingTreeGeometry.naturalTreeLeaves(forest, forest::observed, logs).size(), 4,
                    "the observed natural crown is selected without player placed leaves");
            helper.assertTrue(LoggingTreeGeometry.saplingForTree(forest, forest::observed, logs).is(Items.OAK_SAPLING),
                    "native log family rules select the planting item");
            helper.assertTrue(LoggingTreeGeometry.connectedTreeLogs(forest, forest::observed, build, Set.of()).isEmpty(),
                    "logs surrounded only by persistent player leaves are not a natural tree");
            helper.assertTrue(LoggingTreeGeometry.connectedTreeLogs(forest, forest::observed, fixture.root(),
                    Set.of(BuiltInRegistries.BLOCK.getKey(Blocks.BIRCH_LOG))).isEmpty(), "the selected log filter still applies");
            helper.assertFalse(helper.getLevel().hasChunkAt(fixture.root()), "tree classification only reads the saved forest");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_forestry")
    public static void forestPaletteAndPendingChangesSurviveTownSaves(GameTestHelper helper) {
        fixture(helper, fixture -> {
            BlockPos water = fixture.root().west(3);
            set(fixture.first(), water, Blocks.WATER.defaultBlockState());
            var forest = observe(helper, fixture, fixture.first());
            helper.assertTrue(forest.change(Map.of(fixture.root(), Blocks.AIR.defaultBlockState())), "an observed tree block can be staged");
            var saved = fixture.town().save(helper.getLevel().registryAccess());
            var restored = SettlementRecord.load(saved, helper.getLevel().registryAccess(), () -> {}).forests().get(fixture.area().id());
            helper.assertTrue(restored.pending() && restored.getBlockState(fixture.root()).isAir(), "the town save retains deferred tree cuts");
            helper.assertTrue(restored.observed(water) && !restored.getFluidState(water).isEmpty(), "palette serialization retains observed fluids");
            helper.assertTrue(restored.observed(fixture.root().west(2)) && restored.getBlockState(fixture.root().west(2)).isAir(),
                    "observed empty space survives saving");
            saved.remove("Forests");
            helper.assertTrue(SettlementRecord.load(saved, helper.getLevel().registryAccess(), () -> {}).forests().isEmpty(),
                    "older settlement saves start without invented forest observations");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_forestry")
    public static void staleForestCutsCannotRemoveNewTreesWithTheSameAppearance(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var forest = observe(helper, fixture, fixture.first());
            forest.change(Map.of(fixture.root(), Blocks.AIR.defaultBlockState()));
            var saved = forest.save();
            var restored = SettlementForest.load(saved);
            var returned = copy(helper, fixture.first());
            var section = restored.sections().get(SettlementForestSection.sectionOrigin(fixture.root()));
            helper.assertTrue(section.reconcile(returned), "saved terrain applies to its matching chunk identity");
            helper.assertTrue(returned.getBlockState(fixture.root()).isAir(), "the real block receives the deferred cut");
            set(returned, fixture.root(), Blocks.OAK_LOG.defaultBlockState());
            var old = SettlementForest.load(saved);
            helper.assertTrue(old.sections().get(section.origin()).reconcile(returned), "a previously applied cut is recognized by its receipt");
            helper.assertTrue(returned.getBlockState(fixture.root()).is(Blocks.OAK_LOG),
                    "an old ledger cannot cut a later tree merely because its block state matches");
            helper.assertFalse(old.pending(), "the stale ledger refreshes to the newer native state");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_forestry")
    public static void netUnchangedForestWorkStillAdvancesItsReceipt(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var forest = observe(helper, fixture, fixture.first());
            forest.change(Map.of(fixture.root(), Blocks.AIR.defaultBlockState()));
            var cut = forest.save();
            forest.change(Map.of(fixture.root(), Blocks.OAK_LOG.defaultBlockState()));
            var section = forest.sections().get(SettlementForestSection.sectionOrigin(fixture.root()));
            helper.assertTrue(section.reconcile(fixture.first()), "a completed cut and regrowth records a revision even when the state is unchanged");
            var old = SettlementForest.load(cut);
            helper.assertTrue(old.sections().get(section.origin()).reconcile(fixture.first()), "the older cut observes the newer revision");
            helper.assertTrue(fixture.first().getBlockState(fixture.root()).is(Blocks.OAK_LOG), "the net unchanged revision cannot be replayed as a cut");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_forestry")
    public static void forestConflictsPreserveEveryBlockInTheAffectedSection(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var forest = observe(helper, fixture, fixture.first());
            forest.change(Map.of(fixture.root(), Blocks.AIR.defaultBlockState(), fixture.root().above(), Blocks.AIR.defaultBlockState()));
            set(fixture.first(), fixture.root().above(), Blocks.DIAMOND_BLOCK.defaultBlockState());
            var section = forest.sections().get(SettlementForestSection.sectionOrigin(fixture.root()));
            helper.assertFalse(section.reconcile(fixture.first()), "changed baseline terrain rejects the section transaction");
            helper.assertTrue(fixture.first().getBlockState(fixture.root()).is(Blocks.OAK_LOG), "a conflict applies none of the other cuts in its section");
            helper.assertTrue(fixture.first().getBlockState(fixture.root().above()).is(Blocks.DIAMOND_BLOCK), "the player's changed block is preserved");
            helper.assertTrue(section.pending() && forest.conflict(), "the pending work remains available for recovery");
            helper.assertFalse(forest.observed(fixture.root()), "conflicted terrain is unavailable to new work");
            helper.assertFalse(forest.change(Map.of(fixture.root().above(2), Blocks.AIR.defaultBlockState())), "a conflict blocks further changes");
            var restored = SettlementForest.load(forest.save());
            helper.assertTrue(restored.conflict() && restored.pending(), "conflict state survives a save");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_forestry")
    public static void forestReplayPreservesUnrelatedPlayerEdits(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var forest = observe(helper, fixture, fixture.first());
            forest.change(Map.of(fixture.root(), Blocks.AIR.defaultBlockState()));
            BlockPos build = fixture.root().west(3);
            set(fixture.first(), build, Blocks.GOLD_BLOCK.defaultBlockState());
            helper.assertTrue(forest.sections().get(SettlementForestSection.sectionOrigin(fixture.root())).reconcile(fixture.first()),
                    "unmodified cells do not cause a false conflict");
            helper.assertTrue(fixture.first().getBlockState(build).is(Blocks.GOLD_BLOCK), "unrelated native edits are not overwritten by the saved palette");
            helper.assertTrue(forest.getBlockState(build).is(Blocks.GOLD_BLOCK), "successful replay refreshes other observed native states");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_forestry")
    public static void forestSectionsCanReturnInEitherOrderWithoutReplayingCuts(GameTestHelper helper) {
        fixture(helper, fixture -> {
            BlockPos other = fixture.root().east(16);
            set(fixture.second(), other, Blocks.OAK_LOG.defaultBlockState());
            var forest = observe(helper, fixture, fixture.first());
            observe(helper, fixture, fixture.second());
            forest.change(Map.of(fixture.root(), Blocks.AIR.defaultBlockState(), other, Blocks.AIR.defaultBlockState()));
            var saved = forest.save();
            var first = forest.sections().get(SettlementForestSection.sectionOrigin(fixture.root()));
            var second = forest.sections().get(SettlementForestSection.sectionOrigin(other));
            helper.assertTrue(second.reconcile(fixture.second()), "the neighboring chunk can return first");
            helper.assertTrue(first.pending() && !second.pending(), "only the supplied chunk completes its handoff");
            helper.assertTrue(first.reconcile(fixture.first()), "the original chunk completes later");
            set(fixture.second(), other, Blocks.OAK_LOG.defaultBlockState());
            var old = SettlementForest.load(saved);
            helper.assertTrue(old.sections().get(first.origin()).reconcile(fixture.first()), "stale first section has a completed receipt");
            helper.assertTrue(old.sections().get(second.origin()).reconcile(fixture.second()), "stale neighboring section has its own completed receipt");
            helper.assertTrue(fixture.first().getBlockState(fixture.root()).isAir()
                    && fixture.second().getBlockState(other).is(Blocks.OAK_LOG), "different return orders do not resurrect or recut native blocks");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_forestry")
    public static void removingAnAreaRetainsItsDeferredWorldChangesUntilHandoff(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var forest = observe(helper, fixture, fixture.first());
            forest.change(Map.of(fixture.root(), Blocks.AIR.defaultBlockState()));
            helper.assertTrue(fixture.town().removeArea(fixture.town().leader(), fixture.area().id()), "the forestry area can be removed");
            SettlementForestService.update(helper.getLevel(), fixture.town());
            helper.assertTrue(fixture.town().forests().containsKey(fixture.area().id()), "removing an area cannot discard already completed background work");
            SettlementForestService.onChunkLoad(new ChunkEvent.Load(fixture.first(), false));
            helper.assertTrue(fixture.first().getBlockState(fixture.root()).isAir(), "chunk handoff still applies the removed area's pending cut");
            SettlementForestService.update(helper.getLevel(), fixture.town());
            helper.assertFalse(fixture.town().forests().containsKey(fixture.area().id()), "an obsolete forest is removed after its changes reach the world");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_forestry")
    public static void unknownTerrainAndBlockEntitiesRejectWholeForestEdits(GameTestHelper helper) {
        fixture(helper, fixture -> {
            BlockPos chest = fixture.root().west(3);
            set(fixture.first(), chest, Blocks.CHEST.defaultBlockState());
            var forest = observe(helper, fixture, fixture.first());
            helper.assertFalse(forest.change(Map.of(fixture.root(), Blocks.AIR.defaultBlockState(), fixture.root().east(16), Blocks.OAK_LOG.defaultBlockState())),
                    "an edit cannot invent observations in an absent chunk");
            helper.assertTrue(forest.getBlockState(fixture.root()).is(Blocks.OAK_LOG) && !forest.pending(), "a rejected edit does not partially stage known positions");
            helper.assertFalse(forest.change(Map.of(chest, Blocks.AIR.defaultBlockState())), "forest changes cannot erase a container");
            helper.assertFalse(forest.change(Map.of(fixture.root(), Blocks.CHEST.defaultBlockState())), "forest changes cannot invent a block entity without its contents");
            helper.assertFalse(forest.pending(), "rejected block entity changes do not create a revision");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_forestry")
    public static void replacedChunkIdentityCannotConsumeOldForestWork(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var forest = observe(helper, fixture, fixture.first());
            forest.change(Map.of(fixture.root(), Blocks.AIR.defaultBlockState()));
            var replaced = new LevelChunk(helper.getLevel(), fixture.first().getPos());
            tree(replaced, fixture.root(), false);
            var section = forest.sections().get(SettlementForestSection.sectionOrigin(fixture.root()));
            helper.assertFalse(section.reconcile(replaced), "matching blocks in a different chunk identity cannot accept an old transaction");
            helper.assertTrue(replaced.getBlockState(fixture.root()).is(Blocks.OAK_LOG) && section.conflict(),
                    "the replacement tree remains intact and the pending work pauses");
            UUID fieldId = SettlementChunkReceipts.identity(fixture.first(), section.min());
            helper.assertValueEqual(SettlementChunkReceipts.version(fixture.first(), section.min(), fieldId), 0L,
                    "forest receipts do not share a field receipt at the section corner");
            helper.assertTrue(section.reconcile(fixture.first()), "the original matching receipt can still complete the work");
            helper.assertValueEqual(SettlementChunkReceipts.version(fixture.first(), section.min(), fieldId), 0L,
                    "applying a forest section leaves the field receipt untouched");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_forestry")
    public static void malformedForestPalettesCannotCreateUnobservedBlocks(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var forest = observe(helper, fixture, fixture.first());
            var malformed = forest.save();
            ((CompoundTag) malformed.getList("Sections", Tag.TAG_COMPOUND).getFirst()).putIntArray("Baseline", new int[] {0});
            rejects(helper, malformed, "a truncated palette cannot fabricate the missing section cells");
            malformed = forest.save();
            var sections = malformed.getList("Sections", Tag.TAG_COMPOUND);
            sections.add(sections.getFirst().copy());
            rejects(helper, malformed, "duplicate chunk sections are rejected");
            malformed = forest.save();
            var section = (CompoundTag) malformed.getList("Sections", Tag.TAG_COMPOUND).getFirst();
            int[] entries = section.getIntArray("Baseline");
            entries[0] = 50_000;
            section.putIntArray("Baseline", entries);
            rejects(helper, malformed, "an out of range state index is rejected");
            malformed = forest.save();
            section = (CompoundTag) malformed.getList("Sections", Tag.TAG_COMPOUND).getFirst();
            section.putLong("Max", BlockPos.of(section.getLong("Max")).east(16).asLong());
            rejects(helper, malformed, "a section cannot extend across chunk boundaries");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_forestry")
    public static void unloadSurveysCaptureFreshNativeTreeChanges(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var forest = observe(helper, fixture, fixture.first());
            set(fixture.first(), fixture.root(), Blocks.AIR.defaultBlockState());
            SettlementForestService.onChunkUnload(new ChunkEvent.Unload(fixture.first()));
            helper.assertTrue(forest.getBlockState(fixture.root()).isAir(), "unloading refreshes cuts made by the native worker");
            BlockPos newTree = fixture.root().east(16);
            tree(fixture.second(), newTree, false);
            SettlementBuildingService.onChunkUnload(new ChunkEvent.Unload(fixture.second()));
            helper.assertTrue(forest.observed(newTree) && forest.getBlockState(newTree).is(Blocks.OAK_LOG),
                    "the supplied unloading chunk contributes newly observed trees before it leaves the cache");
            helper.assertFalse(helper.getLevel().hasChunkAt(newTree), "unload observation never adds the supplied chunk to the live cache");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_forest_survey_budget")
    public static void regularForestSurveysRotateThroughABoundedSectionBudget(GameTestHelper helper) {
        var level = helper.getLevel();
        var chunkPos = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
        var center = new BlockPos(chunkPos.getMinBlockX() + 8, 64, chunkPos.getMinBlockZ() + 8);
        helper.assertTrue(level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z) != null, "the game test chunk is already present");
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, 0L).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.FORESTRY, "Tall forest",
                new BlockPos(chunkPos.getMinBlockX(), 32, chunkPos.getMinBlockZ()),
                new BlockPos(chunkPos.getMaxBlockX(), 95, chunkPos.getMaxBlockZ()), 5, true);
        helper.assertTrue(town.putArea(town.leader(), area), "the tall forest is registered");
        SettlementForestService.observeArea(level, town, area, null);
        var forest = town.forests().get(area.id());
        helper.assertValueEqual(forest.sections().size(), 2, "one ordinary survey only copies two sections");
        helper.assertFalse(forest.observed(area.max()), "the unvisited upper sections remain unknown");
        SettlementForestService.observeArea(level, town, area, null);
        helper.assertValueEqual(forest.sections().size(), 4, "the next survey continues from the earlier cursor");
        helper.assertTrue(forest.observed(area.max()), "rotation eventually observes the entire registered height");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_forestry")
    public static void resizingForestryWaitsForOldPendingTerrainBeforeReplacingObservations(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var original = observe(helper, fixture, fixture.first());
            original.change(Map.of(fixture.root(), Blocks.AIR.defaultBlockState()));
            var resized = new SettlementArea(fixture.area().id(), SettlementAreaType.FORESTRY, "Smaller forest",
                    fixture.area().min(), fixture.area().max().west(16), 5, true);
            helper.assertTrue(fixture.town().putArea(fixture.town().leader(), resized), "the area can be resized");
            SettlementForestService.observeArea(helper.getLevel(), fixture.town(), resized, fixture.first());
            helper.assertTrue(fixture.town().forests().get(resized.id()) == original, "resizing cannot replace a pending original snapshot");
            SettlementForestService.onChunkLoad(new ChunkEvent.Load(fixture.first(), false));
            SettlementForestService.observeArea(helper.getLevel(), fixture.town(), resized, fixture.first());
            var replacement = fixture.town().forests().get(resized.id());
            helper.assertTrue(replacement != original && replacement.matches(resized), "completed work permits a fresh snapshot of the new bounds");
            helper.assertTrue(replacement.getBlockState(fixture.root()).isAir(), "the replacement observes the already applied cut");
            helper.assertFalse(replacement.observed(fixture.root().east(16)), "old terrain outside the resized area is not retained as usable space");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_forestry")
    public static void nativeTreeGeometryConnectsObservedBranchesAcrossChunkBorders(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var root = new BlockPos(fixture.first().getPos().getMaxBlockX(), 65, fixture.root().getZ());
            set(fixture.first(), root.below(), Blocks.DIRT.defaultBlockState());
            for (int y = 0; y < 4; y++) set(fixture.first(), root.above(y), Blocks.OAK_LOG.defaultBlockState());
            var branch = root.above(3).east();
            set(fixture.second(), branch, Blocks.OAK_LOG.defaultBlockState());
            for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                var leaf = branch.above().relative(direction);
                set(new ChunkPos(leaf).equals(fixture.first().getPos()) ? fixture.first() : fixture.second(),
                        leaf, Blocks.OAK_LEAVES.defaultBlockState());
            }
            var forest = observe(helper, fixture, fixture.first());
            helper.assertTrue(LoggingTreeGeometry.connectedTreeLogs(forest, forest::observed, root, Set.of()).isEmpty(),
                    "an unseen crown is not invented when only the trunk chunk is known");
            observe(helper, fixture, fixture.second());
            var logs = LoggingTreeGeometry.connectedTreeLogs(forest, forest::observed, root, Set.of());
            helper.assertValueEqual(logs.size(), 5, "the native geometry includes the observed branch across the chunk border");
            helper.assertTrue(logs.contains(branch), "the neighboring branch is part of the selected tree");
            helper.assertFalse(helper.getLevel().hasChunkAt(root) || helper.getLevel().hasChunkAt(branch),
                    "cross border tree analysis does not load either chunk");
        });
    }

    private static void rejects(GameTestHelper helper, CompoundTag tag, String message) {
        boolean rejected = false;
        try {
            SettlementForest.load(tag);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, message);
    }

    private static SettlementForest observe(GameTestHelper helper, Fixture fixture, LevelChunk chunk) {
        SettlementForestService.observeArea(helper.getLevel(), fixture.town(), fixture.area(), chunk);
        return fixture.town().forests().get(fixture.area().id());
    }

    private static LevelChunk copy(GameTestHelper helper, LevelChunk original) {
        var result = new LevelChunk(helper.getLevel(), original.getPos());
        for (int y = 64; y < 80; y++) {
            for (int x = original.getPos().getMinBlockX(); x <= original.getPos().getMaxBlockX(); x++) {
                for (int z = original.getPos().getMinBlockZ(); z <= original.getPos().getMaxBlockZ(); z++) {
                    var position = new BlockPos(x, y, z);
                    set(result, position, original.getBlockState(position));
                }
            }
        }
        result.readAttachmentsFromNBT(helper.getLevel().registryAccess(), original.writeAttachmentsToNBT(helper.getLevel().registryAccess()));
        return result;
    }

    private static void tree(LevelChunk chunk, BlockPos root, boolean persistent) {
        set(chunk, root.below(), Blocks.DIRT.defaultBlockState());
        for (int y = 0; y < 4; y++) set(chunk, root.above(y), Blocks.OAK_LOG.defaultBlockState());
        var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, persistent);
        for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) set(chunk, root.above(3).relative(direction), leaves);
    }

    private static void set(LevelChunk chunk, BlockPos position, BlockState state) {
        chunk.getSection(chunk.getSectionIndex(position.getY())).setBlockState(position.getX() & 15,
                position.getY() & 15, position.getZ() & 15, state);
    }

    private static void fixture(GameTestHelper helper, Consumer<Fixture> check) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(BlockPos.ZERO);
        var first = new LevelChunk(level, new ChunkPos(650_000 + origin.getX() * 4, 650_000 + origin.getZ() * 4));
        var second = new LevelChunk(level, new ChunkPos(first.getPos().x + 1, first.getPos().z));
        var root = new BlockPos(first.getPos().getMinBlockX() + 7, 65, first.getPos().getMinBlockZ() + 8);
        tree(first, root, false);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), root, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), root, 0L).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.FORESTRY, "Forest",
                new BlockPos(first.getPos().getMinBlockX(), 64, first.getPos().getMinBlockZ()),
                new BlockPos(second.getPos().getMaxBlockX(), 79, second.getPos().getMaxBlockZ()), 5, true);
        helper.assertTrue(town.putArea(town.leader(), area), "fixture forestry area is registered");
        helper.assertFalse(level.hasChunkAt(root), "fixture chunks are detached from the live level");
        check.accept(new Fixture(town, area, first, second, root));
        helper.succeed();
    }

    private record Fixture(SettlementRecord town, SettlementArea area, LevelChunk first, LevelChunk second, BlockPos root) {
    }
}
