package com.jvn.villagerretaliation.settlement;

import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementMinePhysicsGameTests {
    private SettlementMinePhysicsGameTests() {
    }

    @GameTest(template = "empty", batch = "isolated_mine_background_torch")
    public static void unsupportedBackgroundTorchesBecomePersistentLooseItems(GameTestHelper helper) {
        var fixture = fixture(helper);
        set(fixture.chunk(), fixture.position(), Blocks.STONE.defaultBlockState());
        set(fixture.chunk(), fixture.position().above(), Blocks.TORCH.defaultBlockState());
        var mine = observe(fixture);
        int chunks = helper.getLevel().getChunkSource().getLoadedChunksCount();
        mine.change(Map.of(fixture.position(), Blocks.AIR.defaultBlockState()));
        SettlementMinePhysics.advance(helper.getLevel(), mine);
        helper.assertTrue(mine.getBlockState(fixture.position().above()).isAir(), "observed support loss removes the torch while unloaded");
        helper.assertValueEqual(drops(mine), 1, "the lost torch is retained as one finite item");
        helper.assertTrue(fixture.chunk().getBlockState(fixture.position().above()).is(Blocks.TORCH), "background physics does not mutate a detached native chunk");
        var restored = SettlementMine.load(mine.save());
        for (int index = 0; index < 5; index++) SettlementMinePhysics.advance(helper.getLevel(), restored);
        helper.assertValueEqual(drops(restored), 1, "saving and resuming cannot repeat an already applied neighbor drop");
        helper.assertValueEqual(helper.getLevel().getChunkSource().getLoadedChunksCount(), chunks, "reacting never loads the observed area");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "isolated_mine_background_gravel")
    public static void backgroundGravelFallsOntoItsObservedFloor(GameTestHelper helper) {
        var fixture = fixture(helper);
        set(fixture.chunk(), fixture.position(), Blocks.STONE.defaultBlockState());
        set(fixture.chunk(), fixture.position().above(), Blocks.GRAVEL.defaultBlockState());
        var mine = observe(fixture);
        mine.change(Map.of(fixture.position(), Blocks.AIR.defaultBlockState()));
        SettlementMinePhysics.advance(helper.getLevel(), mine);
        helper.assertTrue(mine.getBlockState(fixture.position()).is(Blocks.GRAVEL), "gravity fills the opened cell before another mining action");
        helper.assertTrue(mine.getBlockState(fixture.position().above()).isAir(), "gravity removes the original gravel exactly once");
        helper.assertValueEqual(drops(mine), 0, "a successful landing creates no loose duplicate");
        var restored = SettlementMine.load(mine.save());
        SettlementMinePhysics.advance(helper.getLevel(), restored);
        helper.assertTrue(restored.getBlockState(fixture.position()).is(Blocks.GRAVEL), "the settled block survives a restart");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "isolated_mine_background_stack")
    public static void stackedFallingBlocksSettleWithoutDestroyingOneAnother(GameTestHelper helper) {
        var fixture = fixture(helper);
        set(fixture.chunk(), fixture.position(), Blocks.STONE.defaultBlockState());
        set(fixture.chunk(), fixture.position().above(), Blocks.GRAVEL.defaultBlockState());
        set(fixture.chunk(), fixture.position().above(2), Blocks.RED_SAND.defaultBlockState());
        var mine = observe(fixture);
        mine.change(Map.of(fixture.position(), Blocks.AIR.defaultBlockState()));
        SettlementMinePhysics.advance(helper.getLevel(), mine);
        helper.assertTrue(mine.getBlockState(fixture.position()).is(Blocks.GRAVEL), "the lower gravel lands first");
        helper.assertTrue(mine.getBlockState(fixture.position().above()).is(Blocks.RED_SAND), "the upper sand settles on the lower gravel");
        helper.assertTrue(mine.getBlockState(fixture.position().above(2)).isAir(), "both source cells are updated");
        helper.assertValueEqual(drops(mine), 0, "settling a column conserves both blocks");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "isolated_mine_background_fence")
    public static void nativeFenceShapesUpdateInObservedBackgroundTerrain(GameTestHelper helper) {
        var fixture = fixture(helper);
        set(fixture.chunk(), fixture.position(), Blocks.STONE.defaultBlockState());
        set(fixture.chunk(), fixture.position().west(), Blocks.OAK_FENCE.defaultBlockState().setValue(FenceBlock.EAST, true));
        var mine = observe(fixture);
        mine.change(Map.of(fixture.position(), Blocks.AIR.defaultBlockState()));
        SettlementMinePhysics.advance(helper.getLevel(), mine);
        helper.assertFalse(mine.getBlockState(fixture.position().west()).getValue(FenceBlock.EAST), "the fence uses its native shape rule without a live chunk");
        helper.assertValueEqual(drops(mine), 0, "a connection change creates no item");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "isolated_mine_background_slab")
    public static void fallingOntoASlabProducesOneLooseBlock(GameTestHelper helper) {
        var fixture = fixture(helper);
        set(fixture.chunk(), fixture.position().below(), Blocks.STONE_SLAB.defaultBlockState());
        set(fixture.chunk(), fixture.position().above(), Blocks.STONE.defaultBlockState());
        set(fixture.chunk(), fixture.position().above(2), Blocks.SAND.defaultBlockState());
        var mine = observe(fixture);
        mine.change(Map.of(fixture.position().above(), Blocks.AIR.defaultBlockState()));
        SettlementMinePhysics.advance(helper.getLevel(), mine);
        helper.assertTrue(mine.getBlockState(fixture.position().below()).is(Blocks.STONE_SLAB), "a partial landing surface is preserved");
        helper.assertTrue(mine.getBlockState(fixture.position().above(2)).isAir(), "falling consumes the original sand");
        helper.assertValueEqual(drops(mine), 1, "failed placement leaves one sand item");
        helper.assertTrue(mine.getBlockState(fixture.position()).isAir(), "sand cannot float over the half-height collision");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "isolated_mine_background_torch_landing")
    public static void fallingPastATorchRetainsTheTorchAndDropsTheGravel(GameTestHelper helper) {
        var fixture = fixture(helper);
        set(fixture.chunk(), fixture.position(), Blocks.TORCH.defaultBlockState());
        set(fixture.chunk(), fixture.position().above(2), Blocks.STONE.defaultBlockState());
        set(fixture.chunk(), fixture.position().above(3), Blocks.GRAVEL.defaultBlockState());
        var mine = observe(fixture);
        mine.change(Map.of(fixture.position().above(2), Blocks.AIR.defaultBlockState()));
        SettlementMinePhysics.advance(helper.getLevel(), mine);
        helper.assertTrue(mine.getBlockState(fixture.position()).is(Blocks.TORCH), "the landing torch survives the falling block");
        helper.assertTrue(mine.getBlockState(fixture.position().above(3)).isAir(), "the gravel leaves its original cell");
        helper.assertValueEqual(drops(mine), 1, "the blocked landing produces one gravel item");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "isolated_mine_background_unknown_floor")
    public static void anUnobservedLandingCannotConsumeOrInventFallingTerrain(GameTestHelper helper) {
        var fixture = fixture(helper);
        for (int y = fixture.area().min().getY() - 2; y < fixture.position().getY(); y++) {
            set(fixture.chunk(), new BlockPos(fixture.position().getX(), y, fixture.position().getZ()), Blocks.AIR.defaultBlockState());
        }
        set(fixture.chunk(), fixture.position(), Blocks.STONE.defaultBlockState());
        set(fixture.chunk(), fixture.position().above(), Blocks.GRAVEL.defaultBlockState());
        var mine = observe(fixture);
        mine.change(Map.of(fixture.position(), Blocks.AIR.defaultBlockState()));
        SettlementMinePhysics.advance(helper.getLevel(), mine);
        helper.assertTrue(mine.getBlockState(fixture.position().above()).is(Blocks.GRAVEL), "unknown landing terrain leaves the whole fall unapplied");
        helper.assertValueEqual(drops(mine), 0, "an unknown fall cannot fabricate or discard an item");
        helper.assertTrue(mine.reactions().contains(fixture.position().above()), "the unresolved fall remains pending");
        helper.assertTrue(SettlementMine.load(mine.save()).reactions().contains(fixture.position().above()), "a saved fall resumes when observation becomes available");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "isolated_mine_background_rules")
    public static void backgroundNeighborDropsRespectTheWorldDropRule(GameTestHelper helper) {
        var fixture = fixture(helper);
        set(fixture.chunk(), fixture.position(), Blocks.STONE.defaultBlockState());
        set(fixture.chunk(), fixture.position().above(), Blocks.TORCH.defaultBlockState());
        var mine = observe(fixture);
        var rule = helper.getLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS);
        boolean previous = rule.get();
        try {
            rule.set(false, helper.getLevel().getServer());
            mine.change(Map.of(fixture.position(), Blocks.AIR.defaultBlockState()));
            SettlementMinePhysics.advance(helper.getLevel(), mine);
            helper.assertTrue(mine.getBlockState(fixture.position().above()).isAir(), "disabling drops still applies support loss");
            helper.assertValueEqual(drops(mine), 0, "disabled block drops do not create deferred stock");
        } finally {
            rule.set(previous, helper.getLevel().getServer());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "isolated_mine_background_conflict")
    public static void conflictedTerrainRetainsItsLooseItemsWithoutSpawningThem(GameTestHelper helper) {
        var fixture = fixture(helper);
        set(fixture.chunk(), fixture.position(), Blocks.STONE.defaultBlockState());
        set(fixture.chunk(), fixture.position().above(), Blocks.TORCH.defaultBlockState());
        var mine = observe(fixture);
        mine.change(Map.of(fixture.position(), Blocks.AIR.defaultBlockState()));
        SettlementMinePhysics.advance(helper.getLevel(), mine);
        set(fixture.chunk(), fixture.position(), Blocks.GOLD_BLOCK.defaultBlockState());
        for (var section : mine.sections().values()) section.reconcile(fixture.chunk());
        helper.assertTrue(mine.conflict(), "a later player replacement rejects the dependent terrain change");
        helper.assertValueEqual(drops(mine), 1, "the rejected revision retains its loose item for conflict recovery");
        helper.assertTrue(SettlementChunkReceipts.terrainDrops(fixture.chunk(), false).isEmpty(), "rejected terrain cannot enqueue an item in the native world");
        helper.assertValueEqual(drops(SettlementMine.load(mine.save())), 1, "pending drops remain readable in a conflicted save");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "isolated_mine_background_item_return")
    public static void returningChunksMaterializeSavedLooseItemsOnlyOnce(GameTestHelper helper) {
        var fixture = fixture(helper);
        set(fixture.chunk(), fixture.position(), Blocks.STONE.defaultBlockState());
        set(fixture.chunk(), fixture.position().above(), Blocks.TORCH.defaultBlockState());
        var mine = observe(fixture);
        mine.change(Map.of(fixture.position(), Blocks.AIR.defaultBlockState()));
        SettlementMinePhysics.advance(helper.getLevel(), mine);
        var saved = mine.save();
        var level = helper.getLevel();
        var chunkPosition = fixture.chunk().getPos();
        level.getChunkSource().addRegionTicket(TicketType.FORCED, chunkPosition, 2, chunkPosition);
        var actual = level.getChunkAt(fixture.position());
        for (BlockPos position : BlockPos.betweenClosed(mine.observedMin(), mine.observedMax())) {
            set(actual, position, fixture.chunk().getBlockState(position));
        }
        actual.readAttachmentsFromNBT(level.registryAccess(), fixture.chunk().writeAttachmentsToNBT(level.registryAccess()));
        for (var section : mine.sections().values()) helper.assertTrue(section.reconcile(actual), "the complete background revision returns to its chunk");
        helper.assertValueEqual(SettlementChunkReceipts.terrainDrops(actual, false).getAllKeys().size(), 1, "the chunk owns exactly one saved drop after handoff");
        var attachments = actual.writeAttachmentsToNBT(level.registryAccess()).copy();
        SettlementTerrainDrops.forget(actual);
        actual.readAttachmentsFromNBT(level.registryAccess(), attachments);
        SettlementTerrainDrops.track(actual);
        helper.succeedWhen(() -> {
            var items = level.getEntitiesOfClass(ItemEntity.class, new AABB(fixture.position()).inflate(4.0D));
            helper.assertValueEqual(items.stream().filter(entity -> entity.getItem().is(Items.TORCH))
                    .mapToInt(entity -> entity.getItem().getCount()).sum(), 1, "normal server ticking materializes the saved torch once");
            helper.assertTrue(SettlementChunkReceipts.terrainDrops(actual, false).isEmpty(), "successful materialization acknowledges the saved drop");
            var oldMine = SettlementMine.load(saved);
            for (var section : oldMine.sections().values()) helper.assertTrue(section.reconcile(actual), "the older journal recognizes the completed receipt");
            SettlementTerrainDrops.flush(actual);
            helper.assertTrue(SettlementChunkReceipts.terrainDrops(actual, false).isEmpty(), "replaying a completed revision cannot enqueue another item");
            helper.assertTrue(actual.getBlockState(fixture.position().above()).isAir(), "the returned torch stays removed from terrain");
            level.getChunkSource().removeRegionTicket(TicketType.FORCED, chunkPosition, 2, chunkPosition);
        });
    }

    @GameTest(template = "empty", batch = "isolated_mine_background_section_fall")
    public static void aFallAcrossTerrainSectionsWaitsForEveryBaselineToMatch(GameTestHelper helper) {
        var fixture = fixture(helper, 78);
        set(fixture.chunk(), fixture.position().above(), Blocks.STONE.defaultBlockState());
        set(fixture.chunk(), fixture.position().above(2), Blocks.GRAVEL.defaultBlockState());
        var mine = observe(fixture);
        mine.change(Map.of(fixture.position().above(), Blocks.AIR.defaultBlockState()));
        SettlementMinePhysics.advance(helper.getLevel(), mine);
        helper.assertTrue(mine.getBlockState(fixture.position()).is(Blocks.GRAVEL), "the observed fall crosses the section boundary");
        set(fixture.chunk(), fixture.position().above(2), Blocks.GOLD_BLOCK.defaultBlockState());
        var level = helper.getLevel();
        var registry = com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), fixture.position(), "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), fixture.position(), 0L).orElseThrow();
        helper.assertTrue(town.putArea(town.leader(), fixture.area()), "the returning mine is registered");
        town.recordMine(mine);
        SettlementMineService.onChunkLoad(new net.neoforged.neoforge.event.level.ChunkEvent.Load(fixture.chunk(), false));
        helper.assertTrue(mine.conflict(), "the source section detects a later player edit");
        helper.assertTrue(fixture.chunk().getBlockState(fixture.position()).isAir(), "the destination cannot receive half of a rejected fall");
        helper.assertTrue(fixture.chunk().getBlockState(fixture.position().above()).is(Blocks.STONE), "the dependent cut remains unapplied");
        helper.assertTrue(fixture.chunk().getBlockState(fixture.position().above(2)).is(Blocks.GOLD_BLOCK), "the player replacement is preserved");
        set(fixture.chunk(), fixture.position().above(2), Blocks.GRAVEL.defaultBlockState());
        SettlementMineService.onChunkLoad(new net.neoforged.neoforge.event.level.ChunkEvent.Load(fixture.chunk(), false));
        helper.assertFalse(mine.conflict(), "matching the source baseline permits recovery");
        helper.assertTrue(fixture.chunk().getBlockState(fixture.position()).is(Blocks.GRAVEL), "recovery applies the complete fall");
        helper.assertTrue(fixture.chunk().getBlockState(fixture.position().above(2)).isAir(), "recovery removes the physical source");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "isolated_mine_background_loaded_view")
    public static void loadedChunksWithoutNativeBlockTicksStillSimulateFalls(GameTestHelper helper) {
        var fixture = fixture(helper);
        var level = helper.getLevel();
        var actual = level.getChunkAt(fixture.position());
        for (BlockPos cell : BlockPos.betweenClosed(fixture.area().min().offset(-2, -2, -2), fixture.area().max().offset(2, 2, 2))) {
            set(actual, cell, fixture.chunk().getBlockState(cell));
        }
        set(actual, fixture.position(), Blocks.STONE.defaultBlockState());
        set(actual, fixture.position().above(), Blocks.GRAVEL.defaultBlockState());
        var mine = observe(new Fixture(actual, fixture.position(), fixture.area()));
        helper.assertFalse(level.shouldTickBlocksAt(ChunkPos.asLong(fixture.position())), "the distant loaded chunk has no native block ticking ticket");
        mine.change(Map.of(fixture.position(), Blocks.AIR.defaultBlockState()));
        SettlementMinePhysics.advance(level, mine);
        helper.assertTrue(mine.getBlockState(fixture.position()).is(Blocks.GRAVEL), "background gravity continues when only a view keeps terrain loaded");
        helper.assertTrue(actual.getBlockState(fixture.position()).is(Blocks.STONE), "the complete reacted revision still awaits handoff");
        for (var section : mine.sections().values()) helper.assertTrue(section.reconcile(actual), "the completed reaction returns to the loaded chunk");
        helper.assertTrue(actual.getBlockState(fixture.position()).is(Blocks.GRAVEL), "handoff places the final fallen block");
        helper.assertTrue(actual.getBlockState(fixture.position().above()).isAir(), "handoff removes the old source");
        helper.succeed();
    }

    private static int drops(SettlementMine mine) {
        return mine.save().getList("Sections", Tag.TAG_COMPOUND).stream()
                .mapToInt(raw -> ((CompoundTag) raw).getList("Drops", Tag.TAG_COMPOUND).size()).sum();
    }

    private static Fixture fixture(GameTestHelper helper) {
        return fixture(helper, 69);
    }

    private static Fixture fixture(GameTestHelper helper, int height) {
        var origin = helper.absolutePos(BlockPos.ZERO);
        var chunk = new LevelChunk(helper.getLevel(), new ChunkPos(320_000 + origin.getX(), 320_000 + origin.getZ()));
        var position = new BlockPos(chunk.getPos().getMinBlockX() + 8, height, chunk.getPos().getMinBlockZ() + 8);
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.MINE, "Mine", position.offset(-3, -2, -3), position.offset(3, 3, 3), 5, true);
        for (BlockPos cell : BlockPos.betweenClosed(position.offset(-5, -1, -5), position.offset(5, -1, 5))) {
            set(chunk, cell, Blocks.STONE.defaultBlockState());
        }
        helper.assertFalse(helper.getLevel().hasChunkAt(position), "the fixture starts outside the native loaded world");
        return new Fixture(chunk, position, area);
    }

    private static SettlementMine observe(Fixture fixture) {
        var mine = new SettlementMine(fixture.area());
        for (int y = mine.observedMin().getY() >> 4; y <= mine.observedMax().getY() >> 4; y++) {
            var min = new BlockPos(mine.observedMin().getX(), Math.max(y << 4, mine.observedMin().getY()), mine.observedMin().getZ());
            var max = new BlockPos(mine.observedMax().getX(), Math.min((y << 4) + 15, mine.observedMax().getY()), mine.observedMax().getZ());
            mine.record(SettlementTerrainSection.capture("Mine", fixture.area().id(), min, max, fixture.chunk()));
        }
        return mine;
    }

    private static void set(LevelChunk chunk, BlockPos position, BlockState state) {
        chunk.getSection(chunk.getSectionIndex(position.getY())).setBlockState(position.getX() & 15,
                position.getY() & 15, position.getZ() & 15, state);
    }

    private record Fixture(LevelChunk chunk, BlockPos position, SettlementArea area) {
    }
}
