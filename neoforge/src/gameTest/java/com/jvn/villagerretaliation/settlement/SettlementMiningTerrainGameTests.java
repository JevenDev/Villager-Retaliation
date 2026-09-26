package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredMiningMode;
import com.jvn.villagerretaliation.interaction.work.mining.MiningBlockRules;
import com.jvn.villagerretaliation.interaction.work.mining.MiningSafety;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementMiningTerrainGameTests {
    private SettlementMiningTerrainGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_terrain")
    public static void mineSurveysRetainFiniteTerrainAndReadOnlySafetyMargins(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var mine = observe(helper, fixture, fixture.first());
            var margin = fixture.area().min().below();
            helper.assertTrue(mine.observed(fixture.ore()) && mine.getBlockState(fixture.ore()).is(Blocks.IRON_ORE),
                    "surveys retain the actual ore block");
            helper.assertTrue(mine.observed(fixture.ore().above()) && mine.getBlockState(fixture.ore().above()).isAir(),
                    "observed air remains distinct from missing terrain");
            helper.assertTrue(mine.observed(margin) && mine.observed(fixture.area().min().below(2)),
                    "the safety margin includes floor and landing observations below the work area");
            helper.assertFalse(mine.observed(fixture.area().min().below(3)), "the observation margin is bounded");
            helper.assertFalse(mine.change(Map.of(fixture.ore(), Blocks.AIR.defaultBlockState(), margin, Blocks.AIR.defaultBlockState())),
                    "a normal harvest cannot remove blocks in its read only safety margin");
            helper.assertTrue(mine.getBlockState(fixture.ore()).is(Blocks.IRON_ORE) && !mine.pending(),
                    "an invalid margin edit does not stage the valid part of a harvest");
            var missing = fixture.ore().east(16);
            helper.assertFalse(mine.observed(missing), "an absent neighboring chunk is unknown");
            helper.assertTrue(mine.getBlockState(missing).is(Blocks.BARRIER), "unknown cells cannot appear as an open route");
            observe(helper, fixture, fixture.second());
            helper.assertTrue(mine.observed(missing), "a later supplied chunk fills in its own observations");
            helper.assertFalse(helper.getLevel().hasChunkAt(fixture.ore()) || helper.getLevel().hasChunkAt(missing),
                    "observations never load detached chunks");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_terrain")
    public static void observedOresAndExcavationUseNativeExposureAndToolRules(GameTestHelper helper) {
        fixture(helper, fixture -> {
            for (var direction : Direction.values()) set(fixture.first(), fixture.ore().relative(direction), Blocks.STONE.defaultBlockState());
            var mine = observe(helper, fixture, fixture.first());
            helper.assertFalse(MiningBlockRules.isMineableOre(mine, mine::observed, fixture.ore()), "fully enclosed ore is not exposed");
            mine.change(Map.of(fixture.ore().east(), Blocks.AIR.defaultBlockState()));
            helper.assertTrue(MiningBlockRules.isMineableOre(mine, mine::observed, fixture.ore()), "observed air exposes the ore");
            helper.assertFalse(MiningBlockRules.isMineableOre(mine,
                    position -> mine.observed(position) && !position.equals(fixture.ore().east()), fixture.ore()),
                    "an unknown neighbor cannot expose otherwise enclosed ore");
            helper.assertTrue(MiningBlockRules.isMineableExcavationBlock(mine, mine::observed, fixture.ore()),
                    "a full exposed tool block is an excavation candidate");
            helper.assertFalse(MiningBlockRules.isUsableMiningTool(HiredMiningMode.EXPOSED_ORES, new ItemStack(Items.WOODEN_PICKAXE),
                    mine.getBlockState(fixture.ore())), "the ore still requires a tool with sufficient harvest tier");
            helper.assertTrue(MiningBlockRules.isUsableMiningTool(HiredMiningMode.EXPOSED_ORES, new ItemStack(Items.IRON_PICKAXE),
                    mine.getBlockState(fixture.ore())), "a suitable real pickaxe passes native tool rules");
            for (var block : new net.minecraft.world.level.block.Block[] {Blocks.LADDER, Blocks.TORCH, Blocks.STONE_SLAB, Blocks.BEDROCK}) {
                mine.change(Map.of(fixture.ore(), block.defaultBlockState()));
                helper.assertFalse(MiningBlockRules.isMineableExcavationBlock(mine, mine::observed, fixture.ore()),
                        "supports, partial blocks, and unbreakable terrain are not excavation targets");
            }
            mine.change(Map.of(fixture.ore(), Blocks.DIRT.defaultBlockState()));
            helper.assertTrue(MiningBlockRules.isMineableExcavationBlock(mine, mine::observed, fixture.ore()), "exposed dirt can be excavated");
            helper.assertTrue(MiningBlockRules.isUsableMiningTool(HiredMiningMode.HORIZONTAL_EXCAVATION, new ItemStack(Items.IRON_SHOVEL),
                    mine.getBlockState(fixture.ore())), "excavation uses the native shovel tag");
            helper.assertFalse(helper.getLevel().hasChunkAt(fixture.ore()), "candidate selection uses only observed terrain");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_terrain")
    public static void observedMiningRoutesRejectHeatFluidsFallsAndUnknownHeadroom(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var mine = observe(helper, fixture, fixture.first());
            var stance = fixture.ore().south(2);
            helper.assertTrue(MiningSafety.isSafePathPosition(mine, mine::observed, stance), "observed clear headroom over stone is safe");
            helper.assertFalse(MiningSafety.isSafePathPosition(mine,
                    position -> mine.observed(position) && !position.equals(stance.above()), stance), "missing headroom is unsafe");
            helper.assertFalse(MiningSafety.isSafePathPosition(mine,
                    position -> mine.observed(position) && !position.equals(stance.east()), stance), "unknown neighboring heat hazards block a route");
            for (var block : new net.minecraft.world.level.block.Block[] {Blocks.LAVA, Blocks.MAGMA_BLOCK, Blocks.CAMPFIRE, Blocks.CACTUS}) {
                mine.change(Map.of(stance.east(), block.defaultBlockState()));
                helper.assertFalse(MiningSafety.isSafePathPosition(mine, mine::observed, stance), "immediate heat and damaging neighbors block a route");
            }
            mine.change(Map.of(stance.east(), Blocks.AIR.defaultBlockState(), stance, Blocks.WATER.defaultBlockState()));
            helper.assertFalse(MiningSafety.isSafePathPosition(mine, mine::observed, stance), "a flooded body position is unsafe");
            mine.change(Map.of(stance, Blocks.AIR.defaultBlockState(), stance.below(), Blocks.AIR.defaultBlockState()));
            helper.assertFalse(MiningSafety.isSafePathPosition(mine, mine::observed, stance), "a missing floor cannot support a route");
            helper.assertTrue(MiningSafety.needsFallGuard(mine, mine::observed, stance.below()), "a missing landing requires protection");
            mine.change(Map.of(stance.below(), Blocks.LADDER.defaultBlockState(), stance, Blocks.LADDER.defaultBlockState()));
            helper.assertTrue(MiningSafety.isSafePathPosition(mine, mine::observed, stance), "an observed ladder route follows the native exception");
            helper.assertFalse(MiningSafety.isUnsafeUnderfootTarget(mine, mine::observed, stance.below(), stance),
                    "a ladder stance can safely remove its underfoot target");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_terrain")
    public static void observedMiningTargetsCheckActualAndPlannedUnderfootLandings(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var mine = observe(helper, fixture, fixture.first());
            helper.assertFalse(MiningSafety.isUnsafeMiningTarget(mine, mine::observed, fixture.ore(), fixture.ore().above(), null),
                    "a known solid landing below the target is safe");
            mine.change(Map.of(fixture.ore().below(), Blocks.AIR.defaultBlockState()));
            helper.assertTrue(MiningSafety.isUnsafeMiningTarget(mine, mine::observed, fixture.ore(), fixture.ore().above(), null),
                    "planned underfoot mining cannot create a drop");
            helper.assertTrue(MiningSafety.isUnsafeMiningTarget(mine, mine::observed, fixture.ore(), fixture.ore().east(), fixture.ore().above()),
                    "the worker's actual stance is checked even when its plan is elsewhere");
            mine.change(Map.of(fixture.ore().above(), Blocks.LADDER.defaultBlockState()));
            helper.assertFalse(MiningSafety.isUnsafeMiningTarget(mine, mine::observed, fixture.ore(), fixture.ore().above(), null),
                    "the native ladder exception applies to the shared target rule");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_terrain")
    public static void chunkBoundaryMiningWaitsForObservedFluidFaces(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var edge = new BlockPos(fixture.first().getPos().getMaxBlockX(), 65, fixture.ore().getZ());
            set(fixture.first(), edge, Blocks.DIAMOND_ORE.defaultBlockState());
            set(fixture.second(), edge.east(), Blocks.WATER.defaultBlockState());
            var mine = observe(helper, fixture, fixture.first());
            helper.assertTrue(MiningSafety.hasUncontainedFluidFace(mine, mine::observed, edge), "an unknown fluid face blocks mining");
            helper.assertTrue(MiningBlockRules.hasAdjacentExcavationFluid(mine, mine::observed, edge), "excavation also treats missing faces as unsafe");
            observe(helper, fixture, fixture.second());
            helper.assertTrue(MiningSafety.hasUncontainedFluidFace(mine, mine::observed, edge), "the observed water pocket still requires containment");
            set(fixture.second(), edge.east(), Blocks.STONE.defaultBlockState());
            observe(helper, fixture, fixture.second());
            helper.assertFalse(MiningSafety.hasUncontainedFluidFace(mine, mine::observed, edge), "a newly observed seal removes the fluid hazard");
            helper.assertFalse(MiningBlockRules.hasAdjacentExcavationFluid(mine, mine::observed, edge), "all six observed faces now permit excavation");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_terrain")
    public static void mineSnapshotsAndPendingCutsSurviveTownSaves(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var water = fixture.ore().east(3);
            set(fixture.first(), water, Blocks.WATER.defaultBlockState());
            var mine = observe(helper, fixture, fixture.first());
            mine.change(Map.of(fixture.ore(), Blocks.AIR.defaultBlockState()));
            var tag = fixture.town().save(helper.getLevel().registryAccess());
            var restored = SettlementRecord.load(tag, helper.getLevel().registryAccess(), () -> {}).mines().get(fixture.area().id());
            helper.assertTrue(restored.pending() && restored.getBlockState(fixture.ore()).isAir(), "deferred excavation survives a town save");
            helper.assertTrue(restored.observed(water) && !restored.getFluidState(water).isEmpty(), "observed fluids survive the state palette");
            helper.assertTrue(restored.observed(fixture.area().min().below()), "saved safety observations keep their bounds");
            tag.remove("Mines");
            helper.assertTrue(SettlementRecord.load(tag, helper.getLevel().registryAccess(), () -> {}).mines().isEmpty(),
                    "older settlement saves begin with no invented mine observations");
            tag = fixture.town().save(helper.getLevel().registryAccess());
            var mines = tag.getList("Mines", Tag.TAG_COMPOUND);
            mines.add(mines.getFirst().copy());
            var duplicate = tag;
            rejects(helper, () -> SettlementRecord.load(duplicate, helper.getLevel().registryAccess(), () -> {}), "duplicate mine identities are rejected");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_terrain")
    public static void deferredMiningReconcilesEachChunkOnceAndPreservesPlayerEdits(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var other = fixture.ore().east(16);
            set(fixture.second(), other, Blocks.GOLD_ORE.defaultBlockState());
            var mine = observe(helper, fixture, fixture.first());
            observe(helper, fixture, fixture.second());
            mine.change(Map.of(fixture.ore(), Blocks.AIR.defaultBlockState(), other, Blocks.AIR.defaultBlockState()));
            var saved = mine.save();
            var unrelated = fixture.ore().west(2);
            set(fixture.first(), unrelated, Blocks.GOLD_BLOCK.defaultBlockState());
            var first = mine.sections().get(SettlementTerrainSection.sectionOrigin(fixture.ore()));
            var second = mine.sections().get(SettlementTerrainSection.sectionOrigin(other));
            helper.assertTrue(second.reconcile(fixture.second()) && first.pending(), "the second chunk can return before the first");
            helper.assertTrue(first.reconcile(fixture.first()), "the original chunk applies its pending cut");
            helper.assertTrue(fixture.first().getBlockState(unrelated).is(Blocks.GOLD_BLOCK), "unrelated player changes are not overwritten");
            helper.assertTrue(mine.getBlockState(unrelated).is(Blocks.GOLD_BLOCK), "successful handoff refreshes unrelated observed cells");
            set(fixture.second(), other, Blocks.GOLD_ORE.defaultBlockState());
            var stale = SettlementMine.load(saved);
            helper.assertTrue(stale.sections().get(first.origin()).reconcile(fixture.first())
                    && stale.sections().get(second.origin()).reconcile(fixture.second()), "stale saved sections recognize completed chunk receipts");
            helper.assertTrue(fixture.first().getBlockState(fixture.ore()).isAir()
                    && fixture.second().getBlockState(other).is(Blocks.GOLD_ORE), "replayed saves cannot resurrect or remove replacement ore");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_terrain")
    public static void conflictingMineTerrainPausesWithoutPartiallyApplyingCuts(GameTestHelper helper) {
        fixture(helper, fixture -> {
            set(fixture.first(), fixture.ore().above(), Blocks.COAL_ORE.defaultBlockState());
            var mine = observe(helper, fixture, fixture.first());
            mine.change(Map.of(fixture.ore(), Blocks.AIR.defaultBlockState(), fixture.ore().above(), Blocks.AIR.defaultBlockState()));
            set(fixture.first(), fixture.ore().above(), Blocks.DIAMOND_BLOCK.defaultBlockState());
            var section = mine.sections().get(SettlementTerrainSection.sectionOrigin(fixture.ore()));
            helper.assertFalse(section.reconcile(fixture.first()), "an externally replaced target conflicts with its saved baseline");
            helper.assertTrue(fixture.first().getBlockState(fixture.ore()).is(Blocks.IRON_ORE), "no other cut in the section is partially applied");
            helper.assertTrue(fixture.first().getBlockState(fixture.ore().above()).is(Blocks.DIAMOND_BLOCK), "the changed player block remains intact");
            helper.assertTrue(mine.pending() && mine.conflict() && !mine.observed(fixture.ore()), "conflicting work is retained but unavailable to new mining");
            helper.assertFalse(mine.change(Map.of(fixture.ore().east(), Blocks.AIR.defaultBlockState())), "new writes to the conflicted section pause");
            var restored = SettlementMine.load(mine.save());
            helper.assertTrue(restored.pending() && restored.conflict(), "the pending conflict survives a save");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_terrain")
    public static void missingTerrainAndBlockEntitiesRejectWholeMineChanges(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var chest = fixture.ore().west(2);
            set(fixture.first(), chest, Blocks.CHEST.defaultBlockState());
            var mine = observe(helper, fixture, fixture.first());
            helper.assertFalse(mine.change(Map.of(fixture.ore(), Blocks.AIR.defaultBlockState(), fixture.ore().east(16), Blocks.AIR.defaultBlockState())),
                    "missing chunk observations reject the entire proposed edit");
            helper.assertFalse(mine.change(Map.of(chest, Blocks.AIR.defaultBlockState())), "saved terrain cannot destroy a container");
            helper.assertFalse(mine.change(Map.of(fixture.ore(), Blocks.CHEST.defaultBlockState())), "saved terrain cannot invent block entity contents");
            helper.assertFalse(MiningBlockRules.isMineableExcavationBlock(mine, mine::observed, chest), "native mining rules reject observed block entities");
            helper.assertTrue(!mine.pending() && mine.getBlockState(fixture.ore()).is(Blocks.IRON_ORE), "failed edits leave known ore and revisions unchanged");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_terrain")
    public static void mineReceiptsRejectReplacementChunksAndStaySeparateFromForests(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var mine = observe(helper, fixture, fixture.first());
            mine.change(Map.of(fixture.ore(), Blocks.AIR.defaultBlockState()));
            var section = mine.sections().get(SettlementTerrainSection.sectionOrigin(fixture.ore()));
            var replacement = new LevelChunk(helper.getLevel(), fixture.first().getPos());
            set(replacement, fixture.ore(), Blocks.IRON_ORE.defaultBlockState());
            helper.assertFalse(section.reconcile(replacement), "matching terrain in a different chunk identity cannot consume old work");
            helper.assertTrue(replacement.getBlockState(fixture.ore()).is(Blocks.IRON_ORE), "the replacement chunk remains untouched");
            String forestKey = "Forest:" + fixture.area().id() + ":" + section.min().asLong();
            UUID forestId = SettlementChunkReceipts.identity(fixture.first(), forestKey);
            helper.assertTrue(section.reconcile(fixture.first()), "the original receipt can still finish the cut");
            helper.assertValueEqual(SettlementChunkReceipts.version(fixture.first(), forestKey, forestId), 0L,
                    "mining does not advance a forest receipt at the same section corner");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_terrain")
    public static void mineRemovalAndResizeWaitForDeferredTerrainHandoff(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var original = observe(helper, fixture, fixture.first());
            original.change(Map.of(fixture.ore(), Blocks.AIR.defaultBlockState()));
            var resized = new SettlementArea(fixture.area().id(), SettlementAreaType.MINE, "Smaller mine",
                    fixture.area().min(), fixture.area().max().west(16), 5, true);
            helper.assertTrue(fixture.town().putArea(fixture.town().leader(), resized), "the work area can be resized");
            SettlementMineService.observeArea(helper.getLevel(), fixture.town(), resized, fixture.first());
            helper.assertTrue(fixture.town().mines().get(resized.id()) == original, "resizing preserves the old pending transaction");
            SettlementMineService.onChunkLoad(new ChunkEvent.Load(fixture.first(), false));
            SettlementMineService.observeArea(helper.getLevel(), fixture.town(), resized, fixture.first());
            var mine = fixture.town().mines().get(resized.id());
            helper.assertTrue(mine != original && mine.matches(resized) && mine.getBlockState(fixture.ore()).isAir(),
                    "completed handoff permits a fresh snapshot of the resized mine");
            mine.change(Map.of(fixture.ore().below(), Blocks.AIR.defaultBlockState()));
            helper.assertTrue(fixture.town().removeArea(fixture.town().leader(), resized.id()), "the area can be removed");
            SettlementMineService.update(helper.getLevel(), fixture.town());
            helper.assertTrue(fixture.town().mines().containsKey(resized.id()), "removal retains outstanding world changes");
            SettlementMineService.onChunkUnload(new ChunkEvent.Unload(fixture.first()));
            helper.assertTrue(fixture.first().getBlockState(fixture.ore().below()).isAir(), "the removed area's cut still reaches its returning chunk");
            SettlementMineService.update(helper.getLevel(), fixture.town());
            helper.assertFalse(fixture.town().mines().containsKey(resized.id()), "obsolete observations are removed after handoff");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_terrain")
    public static void unloadSurveysObserveFreshMiningAndNeighboringSafetyChunks(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var area = new SettlementArea(fixture.area().id(), SettlementAreaType.MINE, "Edge mine", fixture.area().min(),
                    new BlockPos(fixture.first().getPos().getMaxBlockX(), fixture.area().max().getY(), fixture.area().max().getZ()), 5, true);
            helper.assertTrue(fixture.town().putArea(fixture.town().leader(), area), "the mine ends at the first chunk border");
            SettlementMineService.observeArea(helper.getLevel(), fixture.town(), area, fixture.first());
            var mine = fixture.town().mines().get(area.id());
            set(fixture.first(), fixture.ore(), Blocks.AIR.defaultBlockState());
            SettlementMineService.onChunkUnload(new ChunkEvent.Unload(fixture.first()));
            helper.assertTrue(mine.getBlockState(fixture.ore()).isAir(), "unload refreshes cuts made by a native miner");
            var hazard = new BlockPos(fixture.second().getPos().getMinBlockX(), 65, fixture.ore().getZ());
            set(fixture.second(), hazard, Blocks.LAVA.defaultBlockState());
            SettlementBuildingService.onChunkUnload(new ChunkEvent.Unload(fixture.second()));
            helper.assertTrue(mine.observed(hazard) && mine.getBlockState(hazard).is(Blocks.LAVA),
                    "a chunk intersecting only the safety margin is observed before unload");
            helper.assertFalse(mine.observed(hazard.east(2)), "the neighboring chunk is not copied beyond the bounded margin");
            helper.assertFalse(helper.getLevel().hasChunkAt(hazard), "unload observation does not load its supplied chunk");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mine_survey_budget")
    public static void regularMineSurveysRotateThroughBoundedLoadedSections(GameTestHelper helper) {
        var level = helper.getLevel();
        var chunk = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
        var center = new BlockPos(chunk.getMinBlockX() + 8, 64, chunk.getMinBlockZ() + 8);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, 0L).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.MINE, "Deep mine",
                new BlockPos(chunk.getMinBlockX() + 2, 34, chunk.getMinBlockZ() + 2),
                new BlockPos(chunk.getMaxBlockX() - 2, 93, chunk.getMaxBlockZ() - 2), 5, true);
        helper.assertTrue(town.putArea(town.leader(), area), "the deep mine is registered");
        SettlementBuildingService.refresh(level, town, area);
        var mine = town.mines().get(area.id());
        helper.assertValueEqual(mine.sections().size(), 2, "an ordinary building survey copies at most two mine sections");
        helper.assertFalse(mine.observed(area.max()), "unvisited upper sections remain unknown");
        SettlementBuildingService.refresh(level, town, area);
        helper.assertValueEqual(mine.sections().size(), 4, "the next survey continues through the remaining sections");
        helper.assertTrue(mine.observed(area.max()) && mine.observed(area.min().below(2)), "rotation includes the safety margin");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_mining_terrain")
    public static void malformedMineSnapshotsCannotInventTerrainOrDuplicateSections(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var mine = observe(helper, fixture, fixture.first());
            var truncated = mine.save();
            truncated.getList("Sections", Tag.TAG_COMPOUND).getCompound(0).putIntArray("Baseline", new int[] {0});
            rejects(helper, () -> SettlementMine.load(truncated), "incomplete palettes are rejected");
            var duplicate = mine.save();
            var sections = duplicate.getList("Sections", Tag.TAG_COMPOUND);
            sections.add(sections.getFirst().copy());
            rejects(helper, () -> SettlementMine.load(duplicate), "duplicate sections are rejected");
            var outside = mine.save();
            outside.putLong("Min", fixture.area().min().above(10).asLong());
            rejects(helper, () -> SettlementMine.load(outside), "observed sections cannot exceed the area and its safety margin");
            mine.change(Map.of(fixture.ore(), Blocks.AIR.defaultBlockState()));
            var unstaged = mine.save();
            for (var raw : unstaged.getList("Sections", Tag.TAG_COMPOUND)) ((CompoundTag) raw).putBoolean("Pending", false);
            rejects(helper, () -> SettlementMine.load(unstaged), "changed terrain requires a pending revision");
        });
    }

    private static void rejects(GameTestHelper helper, Runnable action, String message) {
        boolean rejected = false;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, message);
    }

    private static SettlementMine observe(GameTestHelper helper, Fixture fixture, LevelChunk chunk) {
        SettlementMineService.observeArea(helper.getLevel(), fixture.town(), fixture.area(), chunk);
        return fixture.town().mines().get(fixture.area().id());
    }

    private static void set(LevelChunk chunk, BlockPos position, BlockState state) {
        chunk.getSection(chunk.getSectionIndex(position.getY())).setBlockState(position.getX() & 15,
                position.getY() & 15, position.getZ() & 15, state);
    }

    private static void fixture(GameTestHelper helper, Consumer<Fixture> check) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(BlockPos.ZERO);
        var first = new LevelChunk(level, new ChunkPos(750_000 + origin.getX() * 4, 750_000 + origin.getZ() * 4));
        var second = new LevelChunk(level, new ChunkPos(first.getPos().x + 1, first.getPos().z));
        var ore = new BlockPos(first.getPos().getMinBlockX() + 7, 65, first.getPos().getMinBlockZ() + 8);
        for (var chunk : java.util.List.of(first, second)) {
            for (BlockPos position : BlockPos.betweenClosed(new BlockPos(chunk.getPos().getMinBlockX(), 62, chunk.getPos().getMinBlockZ()),
                    new BlockPos(chunk.getPos().getMaxBlockX(), 64, chunk.getPos().getMaxBlockZ()))) {
                set(chunk, position, Blocks.STONE.defaultBlockState());
            }
        }
        set(first, ore, Blocks.IRON_ORE.defaultBlockState());
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), ore, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), ore, 0L).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.MINE, "Mine",
                new BlockPos(first.getPos().getMinBlockX() + 3, 64, first.getPos().getMinBlockZ() + 4),
                new BlockPos(second.getPos().getMaxBlockX() - 3, 79, second.getPos().getMaxBlockZ() - 4), 5, true);
        helper.assertTrue(town.putArea(town.leader(), area), "fixture mine is registered");
        helper.assertFalse(level.hasChunkAt(ore), "fixture chunks are detached from the live level");
        check.accept(new Fixture(town, area, first, second, ore));
        helper.succeed();
    }

    private record Fixture(SettlementRecord town, SettlementArea area, LevelChunk first, LevelChunk second, BlockPos ore) {
    }
}
