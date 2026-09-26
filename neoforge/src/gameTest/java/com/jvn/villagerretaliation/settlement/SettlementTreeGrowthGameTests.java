package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.VillagerRetaliation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.treedecorators.BeehiveDecorator;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockGrowFeatureEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(VillagerRetaliation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SettlementTreeGrowthGameTests {
    private SettlementTreeGrowthGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_tree_growth")
    public static void saplingsMatureBeforeGrowingActualTreesWithoutLoadingChunks(GameTestHelper helper) {
        var fixture = fixture(helper, Blocks.BIRCH_SAPLING, 0);
        int chunks = helper.getLevel().getChunkSource().getLoadedChunksCount();
        var stage = SettlementTreeGrowth.advance(helper.getLevel(), fixture.forest(), fixture.root(), RandomSource.create(0L));
        helper.assertTrue(stage.commit(), "the first growth step commits");
        helper.assertValueEqual(fixture.forest().getBlockState(fixture.root()).getValue(SaplingBlock.STAGE), 1, "the first growth step only matures the sapling");
        helper.assertTrue(count(fixture.forest(), BlockTags.LOGS) == 0L, "maturation does not produce timber");
        var tree = SettlementTreeGrowth.advance(helper.getLevel(), fixture.forest(), fixture.root(), RandomSource.create(0L));
        helper.assertTrue(tree.status().equals("tree_grown") && tree.commit(), "the native configured birch feature grows in the observed terrain: " + tree.status());
        helper.assertTrue(fixture.forest().getBlockState(fixture.root()).is(Blocks.BIRCH_LOG), "the sapling becomes a real birch trunk");
        helper.assertTrue(count(fixture.forest(), BlockTags.LOGS) >= 5 && count(fixture.forest(), BlockTags.LEAVES) > 10, "native generation places a full tree");
        helper.assertTrue(fixture.chunk(fixture.root()).getBlockState(fixture.root()).is(Blocks.BIRCH_SAPLING), "physical terrain waits for the deferred handoff");
        helper.assertValueEqual(helper.getLevel().getChunkSource().getLoadedChunksCount(), chunks, "growth never loads a chunk");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_tree_growth")
    public static void ordinarySaplingsUseTheirNativeTreeSpecies(GameTestHelper helper) {
        var saplings = List.of(Blocks.OAK_SAPLING, Blocks.BIRCH_SAPLING, Blocks.SPRUCE_SAPLING,
                Blocks.JUNGLE_SAPLING, Blocks.ACACIA_SAPLING, Blocks.CHERRY_SAPLING);
        var logs = List.of(Blocks.OAK_LOG, Blocks.BIRCH_LOG, Blocks.SPRUCE_LOG, Blocks.JUNGLE_LOG, Blocks.ACACIA_LOG, Blocks.CHERRY_LOG);
        for (int index = 0; index < saplings.size(); index++) {
            var fixture = fixture(helper, saplings.get(index), index);
            fixture.forest().change(Map.of(fixture.root(), saplings.get(index).defaultBlockState().setValue(SaplingBlock.STAGE, 1)));
            var plan = SettlementTreeGrowth.advance(helper.getLevel(), fixture.forest(), fixture.root(), RandomSource.create(12L));
            helper.assertTrue(plan.status().equals("tree_grown") && plan.commit(), "the native species grows: " + saplings.get(index) + " " + plan.status());
            helper.assertTrue(fixture.forest().getBlockState(fixture.root()).is(logs.get(index)), "the actual sapling determines the trunk species");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_tree_growth")
    public static void darkOakRequiresAndConsumesTheActualFourSaplings(GameTestHelper helper) {
        var fixture = fixture(helper, Blocks.DARK_OAK_SAPLING, 0);
        var mature = Blocks.DARK_OAK_SAPLING.defaultBlockState().setValue(SaplingBlock.STAGE, 1);
        fixture.forest().change(Map.of(fixture.root(), mature));
        var single = SettlementTreeGrowth.advance(helper.getLevel(), fixture.forest(), fixture.root(), RandomSource.create(2L));
        helper.assertTrue(single.commit() && single.status().equals("sapling_blocked"), "a lone dark oak cannot become a tree");
        helper.assertTrue(count(fixture.forest(), BlockTags.LOGS) == 0L, "a missing sapling square never grants logs");
        fixture.forest().change(Map.of(fixture.root().east(), mature, fixture.root().south(), mature, fixture.root().east().south(), mature));
        var square = SettlementTreeGrowth.advance(helper.getLevel(), fixture.forest(), fixture.root(), RandomSource.create(2L));
        helper.assertTrue(square.status().equals("tree_grown") && square.commit(), "the complete square grows its native dark oak: " + square.status());
        for (var position : List.of(fixture.root(), fixture.root().east(), fixture.root().south(), fixture.root().east().south())) {
            helper.assertTrue(fixture.forest().getBlockState(position).is(Blocks.DARK_OAK_LOG), "each supplied sapling becomes part of the trunk");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_tree_growth")
    public static void treeGrowthRejectsUnknownSpaceAndRollsBackEveryPlannedBlock(GameTestHelper helper) {
        var fixture = fixture(helper, Blocks.BIRCH_SAPLING, 0);
        fixture.forest().change(Map.of(fixture.root(), Blocks.BIRCH_SAPLING.defaultBlockState().setValue(SaplingBlock.STAGE, 1)));
        fixture.forest().remove(SettlementForestSection.sectionOrigin(fixture.root().east()));
        var before = fixture.forest().save();
        var plan = SettlementTreeGrowth.advance(helper.getLevel(), fixture.forest(), fixture.root(), RandomSource.create(0L));
        helper.assertFalse(plan.accepted() || plan.commit(), "unknown neighboring terrain prevents the whole tree transaction");
        helper.assertValueEqual(plan.status(), "tree_needs_observation", "the reason identifies missing terrain");
        helper.assertTrue(before.equals(fixture.forest().save()), "neither sapling removal nor partial branches leak out of a failed transaction");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_tree_growth")
    public static void solidObstructionsPreserveSaplingsAndPlayerBuilds(GameTestHelper helper) {
        var fixture = fixture(helper, Blocks.BIRCH_SAPLING, 0);
        fixture.forest().change(Map.of(fixture.root(), Blocks.BIRCH_SAPLING.defaultBlockState().setValue(SaplingBlock.STAGE, 1),
                fixture.root().above(), Blocks.DIAMOND_BLOCK.defaultBlockState()));
        var before = fixture.forest().save();
        var plan = SettlementTreeGrowth.advance(helper.getLevel(), fixture.forest(), fixture.root(), RandomSource.create(0L));
        helper.assertTrue(plan.commit() && plan.status().equals("sapling_blocked"), "an obstructed growth attempt follows the native placement failure");
        helper.assertTrue(before.equals(fixture.forest().save()), "the failed native feature changes no observed terrain");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_tree_growth")
    public static void growthEventsCanCancelOrReplaceTheConfiguredTree(GameTestHelper helper) {
        var fixture = fixture(helper, Blocks.BIRCH_SAPLING, 0);
        fixture.forest().change(Map.of(fixture.root(), Blocks.BIRCH_SAPLING.defaultBlockState().setValue(SaplingBlock.STAGE, 1)));
        Consumer<BlockGrowFeatureEvent> cancel = event -> {
            if (event.getPos().equals(fixture.root())) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(cancel);
        try {
            var blocked = SettlementTreeGrowth.advance(helper.getLevel(), fixture.forest(), fixture.root(), RandomSource.create(0L));
            helper.assertTrue(blocked.commit() && blocked.status().equals("sapling_blocked"), "growth cancellation is honored");
            helper.assertTrue(fixture.forest().getBlockState(fixture.root()).is(Blocks.BIRCH_SAPLING), "canceled growth leaves the sapling");
        } finally {
            NeoForge.EVENT_BUS.unregister(cancel);
        }
        Consumer<BlockGrowFeatureEvent> replace = event -> {
            if (event.getPos().equals(fixture.root())) event.setFeature(TreeFeatures.SPRUCE);
        };
        NeoForge.EVENT_BUS.addListener(replace);
        try {
            var changed = SettlementTreeGrowth.advance(helper.getLevel(), fixture.forest(), fixture.root(), RandomSource.create(0L));
            helper.assertTrue(changed.status().equals("tree_grown") && changed.commit(), "a replacement native feature is used");
            helper.assertTrue(fixture.forest().getBlockState(fixture.root()).is(Blocks.SPRUCE_LOG), "the event chooses the resulting tree species");
        } finally {
            NeoForge.EVENT_BUS.unregister(replace);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_tree_growth")
    public static void beeNestGenerationWaitsForNativeSimulationWithoutLosingItsTree(GameTestHelper helper) {
        var fixture = fixture(helper, Blocks.BIRCH_SAPLING, 0);
        fixture.forest().change(Map.of(fixture.root(), Blocks.BIRCH_SAPLING.defaultBlockState().setValue(SaplingBlock.STAGE, 1)));
        var original = (TreeConfiguration) helper.getLevel().registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE)
                .getHolderOrThrow(TreeFeatures.BIRCH).value().config();
        var config = new TreeConfiguration.TreeConfigurationBuilder(original.trunkProvider, original.trunkPlacer,
                original.foliageProvider, original.foliagePlacer, original.minimumSize)
                .decorators(List.of(new BeehiveDecorator(1.0F))).ignoreVines().build();
        Consumer<BlockGrowFeatureEvent> bees = event -> {
            if (event.getPos().equals(fixture.root())) event.setFeature(Holder.direct(new ConfiguredFeature<>(Feature.TREE, config)));
        };
        var before = fixture.forest().save();
        NeoForge.EVENT_BUS.addListener(bees);
        try {
            var plan = SettlementTreeGrowth.advance(helper.getLevel(), fixture.forest(), fixture.root(), RandomSource.create(0L));
            helper.assertFalse(plan.accepted() || plan.commit(), "unmodeled block entity contents are never discarded");
            helper.assertValueEqual(plan.status(), "tree_needs_native_growth", "a bee nest requires loaded simulation");
            helper.assertTrue(before.equals(fixture.forest().save()), "the entire tree stays uncommitted when its bee nest cannot be preserved");
        } finally {
            NeoForge.EVENT_BUS.unregister(bees);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_tree_growth")
    public static void grownTreesAndTheirScheduledTicksSurviveSaveAndHandoffOnce(GameTestHelper helper) {
        var fixture = fixture(helper, Blocks.BIRCH_SAPLING, 0);
        fixture.forest().change(Map.of(fixture.root(), Blocks.BIRCH_SAPLING.defaultBlockState().setValue(SaplingBlock.STAGE, 1)));
        var plan = SettlementTreeGrowth.advance(helper.getLevel(), fixture.forest(), fixture.root(), RandomSource.create(0L));
        helper.assertTrue(plan.commit(), "the tree transaction commits");
        var saved = fixture.forest().save();
        var restored = SettlementForest.load(saved);
        helper.assertTrue(saved.equals(restored.save()), "tree blocks and pending ticks round trip exactly");
        helper.assertTrue(restored.sections().values().stream().mapToInt(section -> section.save().getList("Ticks", 10).size()).sum() > 0,
                "native scheduled leaf updates are saved with the tree");
        for (var section : restored.sections().values()) helper.assertTrue(section.reconcile(fixture.chunk(section.min())), "the observed section hands off");
        helper.assertTrue(fixture.chunk(fixture.root()).getBlockState(fixture.root()).is(Blocks.BIRCH_LOG), "real terrain receives the tree");
        int ticks = fixture.chunks().values().stream().mapToInt(chunk -> chunk.getBlockTicks().count()).sum();
        helper.assertTrue(ticks > 0, "native scheduled updates are transferred into the physical chunks");
        var stale = SettlementForest.load(saved);
        for (var section : stale.sections().values()) helper.assertTrue(section.reconcile(fixture.chunk(section.min())), "stale growth state reconciles by receipt");
        helper.assertValueEqual(fixture.chunks().values().stream().mapToInt(chunk -> chunk.getBlockTicks().count()).sum(), ticks,
                "repeated handoff cannot duplicate the scheduled updates");
        helper.assertFalse(stale.pending(), "the stale plan adopts the already applied terrain");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_tree_growth")
    public static void failedGrowthHandoffPreservesLaterPlayerChanges(GameTestHelper helper) {
        var fixture = fixture(helper, Blocks.BIRCH_SAPLING, 0);
        fixture.forest().change(Map.of(fixture.root(), Blocks.BIRCH_SAPLING.defaultBlockState().setValue(SaplingBlock.STAGE, 1)));
        helper.assertTrue(SettlementTreeGrowth.advance(helper.getLevel(), fixture.forest(), fixture.root(), RandomSource.create(0L)).commit(), "the tree grows");
        set(fixture.chunk(fixture.root()), fixture.root(), Blocks.DIAMOND_BLOCK.defaultBlockState());
        var rootSection = fixture.forest().sections().get(SettlementForestSection.sectionOrigin(fixture.root()));
        helper.assertFalse(rootSection.reconcile(fixture.chunk(fixture.root())), "a changed sapling location conflicts with the pending trunk");
        helper.assertTrue(rootSection.conflict() && fixture.chunk(fixture.root()).getBlockState(fixture.root()).is(Blocks.DIAMOND_BLOCK),
                "a conflicting tree cannot overwrite the player block");
        helper.assertValueEqual(fixture.chunk(fixture.root()).getBlockTicks().count(), 0, "the conflicting section transfers no scheduled ticks");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_tree_growth")
    public static void hangingPropagulesMatureWithoutTurningIntoTrunks(GameTestHelper helper) {
        var fixture = fixture(helper, Blocks.MANGROVE_PROPAGULE, 0);
        fixture.forest().change(Map.of(fixture.root(), net.minecraft.world.level.block.MangrovePropaguleBlock.createNewHangingPropagule(),
                fixture.root().above(), Blocks.MANGROVE_LEAVES.defaultBlockState()));
        for (int age = 1; age <= 4; age++) {
            var plan = SettlementTreeGrowth.bonemeal(helper.getLevel(), fixture.forest(), fixture.root(), RandomSource.create(0L));
            helper.assertTrue(plan.commit(), "bone meal matures an attached propagule");
            helper.assertValueEqual(fixture.forest().getBlockState(fixture.root()).getValue(net.minecraft.world.level.block.MangrovePropaguleBlock.AGE),
                    age, "one application advances one native propagule age");
        }
        helper.assertTrue(count(fixture.forest(), BlockTags.LOGS) == 0L, "a hanging propagule cannot generate a trunk");
        helper.assertFalse(SettlementTreeGrowth.bonemeal(helper.getLevel(), fixture.forest(), fixture.root(), RandomSource.create(0L)).accepted(),
                "a fully mature hanging propagule no longer consumes bone meal");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_tree_growth")
    public static void forestTickSavesRejectConflictingSchedulesAndInvalidTypes(GameTestHelper helper) {
        var fixture = fixture(helper, Blocks.BIRCH_SAPLING, 0);
        fixture.forest().change(Map.of(fixture.root(), Blocks.BIRCH_SAPLING.defaultBlockState().setValue(SaplingBlock.STAGE, 1)));
        helper.assertTrue(SettlementTreeGrowth.advance(helper.getLevel(), fixture.forest(), fixture.root(), RandomSource.create(0L)).commit(), "the tree grows");
        var section = fixture.forest().sections().values().stream().filter(value -> !value.save().getList("Ticks", 10).isEmpty()).findFirst().orElseThrow();
        var saved = section.save();
        var duplicate = saved.copy();
        var ticks = duplicate.getList("Ticks", 10);
        var conflicting = ticks.getCompound(0).copy();
        conflicting.putInt("Delay", conflicting.getInt("Delay") + 1);
        ticks.add(conflicting);
        assertInvalidSection(helper, fixture.forest().area(), duplicate, "two schedules for the same block position are invalid");
        var unknown = saved.copy();
        unknown.getList("Ticks", 10).getCompound(0).putString("Type", "villagerretaliation:missing_tree_tick");
        assertInvalidSection(helper, fixture.forest().area(), unknown, "unknown tick types cannot be resolved as default blocks");
        var outside = saved.copy();
        outside.getList("Ticks", 10).getCompound(0).putLong("Position", section.max().above().asLong());
        assertInvalidSection(helper, fixture.forest().area(), outside, "ticks cannot escape their observed section");
        var negative = saved.copy();
        negative.getList("Ticks", 10).getCompound(0).putInt("Delay", -1);
        assertInvalidSection(helper, fixture.forest().area(), negative, "negative delays are invalid");
        var legacy = saved.copy();
        legacy.remove("Ticks");
        helper.assertTrue(SettlementForestSection.load(fixture.forest().area(), legacy).pending(), "older pending sections without tick data remain readable");
        helper.succeed();
    }

    private static void assertInvalidSection(GameTestHelper helper, UUID area, net.minecraft.nbt.CompoundTag tag, String message) {
        boolean rejected = false;
        try {
            SettlementForestSection.load(area, tag);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, message);
    }

    private static long count(SettlementForest forest, net.minecraft.tags.TagKey<Block> tag) {
        long count = 0;
        for (var section : forest.sections().values()) {
            for (BlockPos position : BlockPos.betweenClosed(section.min(), section.max())) {
                if (forest.getBlockState(position).is(tag)) count++;
            }
        }
        return count;
    }

    private static Fixture fixture(GameTestHelper helper, Block sapling, int variant) {
        var origin = helper.absolutePos(BlockPos.ZERO);
        int x = 710_000 + origin.getX() * 20 + variant * 3;
        int z = 710_000 + origin.getZ() * 20;
        var min = new BlockPos(x * 16, 64, z * 16);
        var max = min.offset(31, 47, 31);
        var root = min.offset(15, 1, 15);
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.FORESTRY, "Forest", min, max, 5, true);
        var forest = new SettlementForest(area);
        var chunks = new LinkedHashMap<ChunkPos, LevelChunk>();
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                var chunk = new LevelChunk(helper.getLevel(), new ChunkPos(x + dx, z + dz));
                chunks.put(chunk.getPos(), chunk);
                for (BlockPos ground : BlockPos.betweenClosed(new BlockPos(chunk.getPos().getMinBlockX(), 64, chunk.getPos().getMinBlockZ()),
                        new BlockPos(chunk.getPos().getMaxBlockX(), 64, chunk.getPos().getMaxBlockZ()))) {
                    set(chunk, ground, Blocks.DIRT.defaultBlockState());
                }
                if (chunk.getPos().equals(new ChunkPos(root))) set(chunk, root, sapling.defaultBlockState());
                for (int y = 64; y <= 96; y += 16) {
                    forest.record(SettlementForestSection.capture(area.id(), new BlockPos(chunk.getPos().getMinBlockX(), y, chunk.getPos().getMinBlockZ()),
                            new BlockPos(chunk.getPos().getMaxBlockX(), y + 15, chunk.getPos().getMaxBlockZ()), chunk));
                }
            }
        }
        helper.assertFalse(helper.getLevel().hasChunkAt(root), "growth uses detached terrain");
        return new Fixture(forest, root, chunks);
    }

    private static void set(LevelChunk chunk, BlockPos position, BlockState state) {
        chunk.getSection(chunk.getSectionIndex(position.getY())).setBlockState(position.getX() & 15, position.getY() & 15, position.getZ() & 15, state);
    }

    private record Fixture(SettlementForest forest, BlockPos root, Map<ChunkPos, LevelChunk> chunks) {
        LevelChunk chunk(BlockPos position) {
            return this.chunks.get(new ChunkPos(position));
        }
    }
}
