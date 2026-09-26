package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementMineReactionGameTests {
    private SettlementMineReactionGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "isolated_mine_torch_reaction")
    public static void returningMineCutsReleaseUnsupportedTorchesExactlyOnce(GameTestHelper helper) {
        var fixture = fixture(helper);
        quiet(fixture, fixture.position(), Blocks.STONE.defaultBlockState());
        quiet(fixture, fixture.position().above(), Blocks.TORCH.defaultBlockState());
        var mine = observe(fixture);
        mine.change(Map.of(fixture.position(), Blocks.AIR.defaultBlockState()));
        var saved = mine.save();
        apply(fixture);
        helper.assertTrue(fixture.level().getBlockState(fixture.position()).isAir(), "the returning chunk receives the actual cut");
        helper.assertTrue(fixture.level().getBlockState(fixture.position().above()).is(Blocks.TORCH), "neighbor reactions wait until the terrain handoff finishes");
        helper.assertValueEqual(pending(fixture.chunk()), 1, "the completed cut retains one notification");
        tick(fixture);
        helper.assertTrue(fixture.level().getBlockState(fixture.position().above()).isAir(), "native neighbor rules remove the unsupported torch");
        helper.assertValueEqual(drops(fixture, Items.TORCH), 1, "the native reaction produces exactly one real torch");
        helper.assertValueEqual(pending(fixture.chunk()), 0, "completed reactions are acknowledged");
        fixture.town().recordMine(SettlementMine.load(saved));
        apply(fixture);
        tick(fixture);
        helper.assertValueEqual(drops(fixture, Items.TORCH), 1, "replaying an older mine save cannot repeat the drop");
        helper.assertValueEqual(pending(fixture.chunk()), 0, "a completed terrain receipt cannot recreate its reaction");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "isolated_mine_gravel_reaction")
    public static void returningMineCutsStartNativeGravelFalls(GameTestHelper helper) {
        var fixture = fixture(helper);
        quiet(fixture, fixture.position().below(), Blocks.STONE.defaultBlockState());
        quiet(fixture, fixture.position(), Blocks.STONE.defaultBlockState());
        raw(fixture.chunk(), fixture.position().above(), Blocks.GRAVEL.defaultBlockState());
        var mine = observe(fixture);
        mine.change(Map.of(fixture.position(), Blocks.AIR.defaultBlockState()));
        apply(fixture);
        tick(fixture);
        helper.assertTrue(fixture.chunk().getBlockTicks().hasScheduledTick(fixture.position().above(), Blocks.GRAVEL),
                "the cut schedules native gravity for its formerly supported gravel");
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.level().getBlockState(fixture.position()).is(Blocks.GRAVEL), "the actual gravel lands on the observed floor");
            helper.assertTrue(fixture.level().getBlockState(fixture.position().above()).isAir(), "falling removes the original gravel block");
            helper.assertValueEqual(drops(fixture, Items.GRAVEL), 0, "successful landing does not duplicate gravel as an item");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "isolated_mine_water_reaction")
    public static void returningMineCutsResumeNativeFluidFlow(GameTestHelper helper) {
        var fixture = fixture(helper);
        var water = fixture.position().east();
        for (var direction : net.minecraft.core.Direction.values()) {
            if (direction != net.minecraft.core.Direction.WEST) quiet(fixture, water.relative(direction), Blocks.STONE.defaultBlockState());
        }
        quiet(fixture, fixture.position(), Blocks.STONE.defaultBlockState());
        quiet(fixture, fixture.position().below(), Blocks.STONE.defaultBlockState());
        raw(fixture.chunk(), water, Blocks.WATER.defaultBlockState());
        var mine = observe(fixture);
        mine.change(Map.of(fixture.position(), Blocks.AIR.defaultBlockState()));
        apply(fixture);
        tick(fixture);
        helper.assertTrue(fixture.chunk().getFluidTicks().hasScheduledTick(water, Fluids.WATER), "the opened face schedules native fluid updates");
        helper.succeedWhen(() -> helper.assertTrue(fixture.level().getFluidState(fixture.position()).is(Fluids.FLOWING_WATER),
                "the actual source flows into the opened mine cell"));
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "isolated_mine_fence_reaction")
    public static void returningMineCutsUpdateNeighborConnectionShapes(GameTestHelper helper) {
        var fixture = fixture(helper);
        quiet(fixture, fixture.position(), Blocks.STONE.defaultBlockState());
        quiet(fixture, fixture.position().west(), Blocks.OAK_FENCE.defaultBlockState().setValue(FenceBlock.EAST, true));
        var mine = observe(fixture);
        mine.change(Map.of(fixture.position(), Blocks.AIR.defaultBlockState()));
        apply(fixture);
        tick(fixture);
        helper.assertFalse(fixture.level().getBlockState(fixture.position().west()).getValue(FenceBlock.EAST),
                "the native fence shape disconnects from the removed wall");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "isolated_mine_reaction_save")
    public static void pendingMineReactionsSurviveChunkAttachmentSaving(GameTestHelper helper) {
        var fixture = fixture(helper);
        quiet(fixture, fixture.position(), Blocks.STONE.defaultBlockState());
        quiet(fixture, fixture.position().above(), Blocks.TORCH.defaultBlockState());
        observe(fixture).change(Map.of(fixture.position(), Blocks.AIR.defaultBlockState()));
        apply(fixture);
        var saved = fixture.chunk().writeAttachmentsToNBT(fixture.level().registryAccess()).copy();
        fixture.chunk().readAttachmentsFromNBT(fixture.level().registryAccess(), saved);
        helper.assertValueEqual(pending(fixture.chunk()), 1, "a chunk save retains unapplied neighbor reactions");
        tick(fixture);
        helper.assertValueEqual(drops(fixture, Items.TORCH), 1, "the restored notification applies the native drop once");
        var finished = fixture.chunk().writeAttachmentsToNBT(fixture.level().registryAccess()).copy();
        fixture.chunk().readAttachmentsFromNBT(fixture.level().registryAccess(), finished);
        tick(fixture);
        helper.assertValueEqual(pending(fixture.chunk()), 0, "completed notifications remain acknowledged after saving");
        helper.assertValueEqual(drops(fixture, Items.TORCH), 1, "loading the completed attachment cannot duplicate its reaction");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "isolated_mine_reaction_coalescing")
    public static void mineReactionsCoalesceBeforeTheChunkTicks(GameTestHelper helper) {
        var fixture = fixture(helper);
        quiet(fixture, fixture.position(), Blocks.STONE.defaultBlockState());
        quiet(fixture, fixture.position().above(), Blocks.TORCH.defaultBlockState());
        var mine = observe(fixture);
        mine.change(Map.of(fixture.position(), Blocks.AIR.defaultBlockState()));
        apply(fixture);
        helper.assertValueEqual(pending(fixture.chunk()), 1, "the first physical change awaits notification");
        mine.change(Map.of(fixture.position(), Blocks.STONE.defaultBlockState()));
        apply(fixture);
        helper.assertValueEqual(pending(fixture.chunk()), 0, "returning to the original block cancels a stale intermediate notification");
        tick(fixture);
        helper.assertTrue(fixture.level().getBlockState(fixture.position().above()).is(Blocks.TORCH), "the final restored support retains its torch");
        helper.assertValueEqual(drops(fixture, Items.TORCH), 0, "an intermediate air state cannot create a ghost drop");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "isolated_mine_reaction_player_edit")
    public static void laterPlayerBlocksArePreservedWhenMineReactionsResume(GameTestHelper helper) {
        var fixture = fixture(helper);
        quiet(fixture, fixture.position(), Blocks.STONE.defaultBlockState());
        quiet(fixture, fixture.position().above(), Blocks.TORCH.defaultBlockState());
        observe(fixture).change(Map.of(fixture.position(), Blocks.AIR.defaultBlockState()));
        apply(fixture);
        quiet(fixture, fixture.position(), Blocks.GOLD_BLOCK.defaultBlockState());
        tick(fixture);
        helper.assertTrue(fixture.level().getBlockState(fixture.position()).is(Blocks.GOLD_BLOCK), "pending notifications never replace a later player block");
        helper.assertTrue(fixture.level().getBlockState(fixture.position().above()).is(Blocks.TORCH), "the newly supported torch is retained");
        helper.assertValueEqual(pending(fixture.chunk()), 0, "the stale notification is acknowledged without replaying old terrain");
        helper.assertValueEqual(drops(fixture, Items.TORCH), 0, "a stale cut cannot drop the newly supported torch");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "isolated_mine_reaction_budget")
    public static void largeMineReturnsBoundNeighborWorkPerChunkTick(GameTestHelper helper) {
        var fixture = fixture(helper);
        int count = 0;
        for (int x = 2; x <= 13 && count < 130; x++) {
            for (int z = 2; z <= 13 && count < 130; z++) {
                var position = new BlockPos(fixture.chunk().getPos().getMinBlockX() + x, fixture.position().getY(),
                        fixture.chunk().getPos().getMinBlockZ() + z);
                raw(fixture.chunk(), position, Blocks.AIR.defaultBlockState());
                SettlementMineReactions.queue(fixture.chunk(), position, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
                count++;
            }
        }
        helper.assertValueEqual(pending(fixture.chunk()), 130, "every changed cell retains a pending update");
        tick(fixture);
        helper.assertValueEqual(pending(fixture.chunk()), 66, "one chunk tick applies at most sixty four notifications");
        tick(fixture);
        helper.assertValueEqual(pending(fixture.chunk()), 2, "the next tick continues the remaining work");
        tick(fixture);
        helper.assertValueEqual(pending(fixture.chunk()), 0, "later ticks finish the finite queue");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "isolated_mine_reaction_detached")
    public static void detachedMineChunksKeepReactionsWithoutLoadingTheirWorld(GameTestHelper helper) {
        var chunk = new LevelChunk(helper.getLevel(), new ChunkPos(1_200_000 + helper.absolutePos(BlockPos.ZERO).getX(), 1_200_000));
        var position = new BlockPos(chunk.getPos().getMinBlockX() + 8, 65, chunk.getPos().getMinBlockZ() + 8);
        SettlementMineReactions.queue(chunk, position, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
        int chunks = helper.getLevel().getChunkSource().getLoadedChunksCount();
        SettlementMineReactions.flush(chunk);
        helper.assertValueEqual(pending(chunk), 1, "an unattached chunk retains its pending world reactions");
        helper.assertValueEqual(helper.getLevel().getChunkSource().getLoadedChunksCount(), chunks, "notification processing cannot load a detached mine");
        helper.assertFalse(helper.getLevel().hasChunkAt(position), "the detached mine stays outside the loaded world");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "isolated_mine_reaction_neighbors")
    public static void missingNeighborChunksDoNotBlockOtherQueuedMineUpdates(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(BlockPos.ZERO);
        var chunkPosition = new ChunkPos(220_000 + origin.getX(), 220_000 + origin.getZ());
        var edge = new BlockPos(chunkPosition.getMaxBlockX(), 65, chunkPosition.getMinBlockZ() + 8);
        var chunk = level.getChunkAt(edge);
        var center = edge.west(7);
        helper.assertTrue(level.getChunkSource().getChunkNow(chunkPosition.x + 1, chunkPosition.z) == null, "the fixture's adjacent chunk starts unloaded");
        raw(chunk, edge, Blocks.AIR.defaultBlockState());
        raw(chunk, center, Blocks.AIR.defaultBlockState());
        SettlementMineReactions.queue(chunk, edge, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
        SettlementMineReactions.queue(chunk, center, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
        int loaded = level.getChunkSource().getLoadedChunksCount();
        SettlementMineReactions.flush(chunk);
        helper.assertValueEqual(pending(chunk), 1, "the ready interior update proceeds while the edge waits");
        helper.assertValueEqual(level.getChunkSource().getLoadedChunksCount(), loaded, "waiting for a neighbor cannot force it to load");
        helper.assertTrue(SettlementChunkReceipts.mineUpdates(chunk, false).contains(Long.toString(edge.asLong())), "the edge notification stays pending");
        level.getChunkAt(edge.east());
        SettlementMineReactions.flush(chunk);
        helper.assertValueEqual(pending(chunk), 0, "the pending edge update resumes when the real neighbor becomes available");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "isolated_mine_reaction_sections")
    public static void mineSectionsFinishApplyingBeforeNeighborDropsRun(GameTestHelper helper) {
        var fixture = fixture(helper);
        var support = new BlockPos(fixture.position().getX(), (fixture.position().getY() & ~15) + 15, fixture.position().getZ());
        var torch = support.above();
        quiet(fixture, support, Blocks.STONE.defaultBlockState());
        quiet(fixture, torch, Blocks.TORCH.defaultBlockState());
        var lower = SettlementTerrainSection.capture("Mine", UUID.randomUUID(), support, support, fixture.chunk());
        var upper = SettlementTerrainSection.capture("Mine", UUID.randomUUID(), torch, torch, fixture.chunk());
        lower.change(Map.of(support, Blocks.AIR.defaultBlockState()));
        upper.change(Map.of(torch, Blocks.AIR.defaultBlockState()));
        helper.assertTrue(lower.reconcile(fixture.chunk(), (position, before) ->
                SettlementMineReactions.queue(fixture.chunk(), position, before, fixture.chunk().getBlockState(position))), "the lower section applies");
        helper.assertTrue(fixture.level().getBlockState(torch).is(Blocks.TORCH), "the lower section cannot prematurely drop an upper section's block");
        helper.assertTrue(upper.reconcile(fixture.chunk(), (position, before) ->
                SettlementMineReactions.queue(fixture.chunk(), position, before, fixture.chunk().getBlockState(position))), "the upper section applies before reactions");
        tick(fixture);
        helper.assertTrue(fixture.level().getBlockState(support).isAir() && fixture.level().getBlockState(torch).isAir(), "both intended terrain changes remain applied");
        helper.assertTrue(fixture.level().getEntitiesOfClass(ItemEntity.class, new AABB(torch).inflate(2.0D)).isEmpty(),
                "a block already removed by the terrain journal cannot also drop from an intermediate support loss");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "isolated_mine_reaction_bounds")
    public static void queuedMineReactionsRejectPositionsOutsideTheirChunk(GameTestHelper helper) {
        var fixture = fixture(helper);
        boolean rejected = false;
        try {
            SettlementMineReactions.queue(fixture.chunk(), fixture.position().east(16), Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "a chunk cannot retain another chunk's notification");
        helper.assertValueEqual(pending(fixture.chunk()), 0, "rejected notifications leave no queued work");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "isolated_mine_reaction_native_tick")
    public static void serverTicksResumeSavedMineReactionsWithoutAnotherSurvey(GameTestHelper helper) {
        var fixture = fixture(helper);
        quiet(fixture, fixture.position(), Blocks.STONE.defaultBlockState());
        quiet(fixture, fixture.position().above(), Blocks.TORCH.defaultBlockState());
        observe(fixture).change(Map.of(fixture.position(), Blocks.AIR.defaultBlockState()));
        apply(fixture);
        helper.assertValueEqual(pending(fixture.chunk()), 1, "the terrain handoff leaves one reaction for the server tick");
        var saved = fixture.chunk().writeAttachmentsToNBT(fixture.level().registryAccess()).copy();
        SettlementMineReactions.forget(fixture.chunk());
        fixture.chunk().readAttachmentsFromNBT(fixture.level().registryAccess(), saved);
        SettlementMineService.onChunkLoad(new ChunkEvent.Load(fixture.chunk(), false));
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.level().getBlockState(fixture.position().above()).isAir(), "normal world ticking releases the unsupported torch");
            helper.assertValueEqual(pending(fixture.chunk()), 0, "normal world ticking acknowledges the reaction");
            helper.assertValueEqual(drops(fixture, Items.TORCH), 1, "the automatic server tick produces one real drop");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "isolated_mine_reaction_conflict")
    public static void conflictedMineCutsCannotEnqueueWorldReactions(GameTestHelper helper) {
        var fixture = fixture(helper);
        quiet(fixture, fixture.position(), Blocks.STONE.defaultBlockState());
        quiet(fixture, fixture.position().above(), Blocks.TORCH.defaultBlockState());
        var mine = observe(fixture);
        mine.change(Map.of(fixture.position(), Blocks.AIR.defaultBlockState()));
        quiet(fixture, fixture.position(), Blocks.GOLD_BLOCK.defaultBlockState());
        apply(fixture);
        helper.assertTrue(mine.conflict() && mine.pending(), "a conflicting replacement keeps the unresolved cut pending");
        helper.assertValueEqual(pending(fixture.chunk()), 0, "a rejected terrain revision cannot enqueue its intended reaction");
        tick(fixture);
        helper.assertTrue(fixture.level().getBlockState(fixture.position()).is(Blocks.GOLD_BLOCK), "the conflicting player block remains intact");
        helper.assertTrue(fixture.level().getBlockState(fixture.position().above()).is(Blocks.TORCH), "rejected work cannot remove its supported neighbor");
        helper.assertValueEqual(drops(fixture, Items.TORCH), 0, "rejected work produces no neighbor drops");
        helper.succeed();
    }

    private static Fixture fixture(GameTestHelper helper) {
        var level = helper.getLevel();
        var position = helper.absolutePos(new BlockPos(2, 3, 2));
        for (int x = -4; x <= 4; x += 4) {
            for (int z = -4; z <= 4; z += 4) level.getChunkAt(position.offset(x, 0, z));
        }
        for (BlockPos cell : BlockPos.betweenClosed(position.offset(-2, -2, -2), position.offset(2, 3, 2))) {
            level.setBlock(cell, cell.getY() == position.getY() - 2 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        level.getEntitiesOfClass(ItemEntity.class, new AABB(position).inflate(5.0D)).forEach(ItemEntity::discard);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), position, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), position, 0L).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.MINE, "Mine", position.offset(-2, -2, -2), position.offset(2, 3, 2), 5, true);
        helper.assertTrue(town.putArea(town.leader(), area), "the physical mine area is registered");
        return new Fixture(level, town, area, level.getChunkAt(position), position);
    }

    private static SettlementMine observe(Fixture fixture) {
        SettlementMineService.observeArea(fixture.level(), fixture.town(), fixture.area(), fixture.chunk());
        return fixture.town().mines().get(fixture.area().id());
    }

    private static void apply(Fixture fixture) {
        SettlementMineService.onChunkLoad(new ChunkEvent.Load(fixture.chunk(), false));
    }

    private static void tick(Fixture fixture) {
        SettlementMineReactions.flush(fixture.chunk());
    }

    private static int pending(LevelChunk chunk) {
        return SettlementChunkReceipts.mineUpdates(chunk, false).getAllKeys().size();
    }

    private static int drops(Fixture fixture, Item item) {
        return fixture.level().getEntitiesOfClass(ItemEntity.class, new AABB(fixture.position()).inflate(5.0D)).stream()
                .map(ItemEntity::getItem).filter(stack -> stack.is(item)).mapToInt(net.minecraft.world.item.ItemStack::getCount).sum();
    }

    private static void quiet(Fixture fixture, BlockPos position, BlockState state) {
        fixture.level().setBlock(position, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    private static void raw(LevelChunk chunk, BlockPos position, BlockState state) {
        chunk.getSection(chunk.getSectionIndex(position.getY())).setBlockState(position.getX() & 15,
                position.getY() & 15, position.getZ() & 15, state);
    }

    private record Fixture(ServerLevel level, SettlementRecord town, SettlementArea area, LevelChunk chunk, BlockPos position) {
    }
}
