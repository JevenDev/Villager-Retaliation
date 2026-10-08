package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.scene.encounter.EncounterInstance;
import com.jvn.villagerretaliation.scene.encounter.EncounterPersistence;
import com.jvn.villagerretaliation.scene.encounter.EncounterService;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignEncounterEnvironmentPersistenceGameTests {
    private CampaignEncounterEnvironmentPersistenceGameTests() { }

    @GameTest(template = "empty", batch = "campaign_encounter_environment_persistence")
    public static void originalBlockOwnershipIsSavedBeforePlacement(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPersistenceGameTests.Fixture(helper)) {
            var encounter = fixture.data().startEncounter(fixture.template, fixture.scene(), fixture.sceneId + "/start/encounter",
                    helper.getLevel().dimension().location(), fixture.anchor, "normal").encounter();
            helper.assertTrue(EncounterPersistence.checkpoint(fixture.base.base.server, fixture.data(), encounter).isBlank(),
                    "encounter identity is already durable");
            fixture.block();
            var result = EncounterService.reconcileSpawn(fixture.base.base.server, fixture.data(), encounter, fixture.template);
            helper.assertTrue(helper.getLevel().getBlockState(fixture.barrier()).isAir(), "unsaved block ownership cannot change the world");
            helper.assertValueEqual(result.status(), EncounterService.Status.WAITING, "placement waits for its own receipt save");
            helper.assertTrue(fixture.entities().isEmpty(), "blocked environment stops subsequent entity effects");
            fixture.restore(); fixture.run(); fixture.reload();
            helper.assertValueEqual(fixture.encounter().environmentBlocks().get("barrier").original(), Blocks.AIR.defaultBlockState(),
                    "disk reload retains the original block");
            helper.assertTrue(helper.getLevel().getBlockState(fixture.barrier()).is(Blocks.GLASS), "repair resumes placement");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_environment_persistence")
    public static void placementAndRestorationReachNativeChunkStorageBeforeCompletion(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPersistenceGameTests.Fixture(helper)) {
            fixture.run();
            helper.assertTrue(diskBlock(helper, fixture.barrier()).is(Blocks.GLASS), "completed placement is present in the actual chunk save");
            helper.assertValueEqual(fixture.disk().encounter(fixture.encounter().id()).orElseThrow().environmentBlocks().get("barrier").state(),
                    EncounterInstance.EnvironmentBlockState.APPLIED, "applied receipt is durable");
            var result = EncounterService.cleanup(fixture.base.base.server, fixture.data(), fixture.encounter(), true);
            helper.assertValueEqual(result.status(), EncounterService.Status.COMPLETED, "loaded cleanup completes");
            helper.assertTrue(diskBlock(helper, fixture.barrier()).isAir(), "completed restoration is present in the actual chunk save");
            helper.assertValueEqual(fixture.disk().encounter(fixture.encounter().id()).orElseThrow().environmentBlocks().get("barrier").state(),
                    EncounterInstance.EnvironmentBlockState.RESTORED, "restored receipt is durable");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_environment_persistence")
    public static void failedCleanupSaveKeepsOwnedBlocksUntilStorageRecovers(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPersistenceGameTests.Fixture(helper)) {
            fixture.run(); fixture.block();
            var result = EncounterService.cleanup(fixture.base.base.server, fixture.data(), fixture.encounter(), true);
            helper.assertTrue(helper.getLevel().getBlockState(fixture.barrier()).is(Blocks.GLASS), "unsaved cleanup intent must not restore blocks");
            helper.assertValueEqual(result.status(), EncounterService.Status.WAITING, "cleanup waits for writable storage");
            helper.assertValueEqual(fixture.entities().size(), 2, "failed cleanup save prevents subsequent entity removal");
            fixture.restore();
            result = EncounterService.cleanup(fixture.base.base.server, fixture.data(), fixture.encounter(), true);
            helper.assertValueEqual(result.status(), EncounterService.Status.COMPLETED, "cleanup resumes after repair");
            helper.assertTrue(diskBlock(helper, fixture.barrier()).isAir(), "repaired restoration is durable");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_environment_persistence")
    public static void completionSaveFailureRetainsPreparedOwnershipAcrossReload(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPersistenceGameTests.Fixture(helper)) {
            var interrupted = new AtomicBoolean();
            Consumer<ChunkDataEvent.Save> listener = event -> {
                if (!event.getChunk().getPos().equals(new ChunkPos(fixture.barrier())) || !interrupted.compareAndSet(false, true)) return;
                var prepared = fixture.disk().encounter(fixture.encounter().id()).orElseThrow().environmentBlocks().get("barrier");
                helper.assertTrue(prepared != null && prepared.state() == EncounterInstance.EnvironmentBlockState.PREPARED,
                        "ownership is on disk before the native chunk write");
                try { fixture.block(); }
                catch (IOException exception) { throw new AssertionError("cannot interrupt environment completion storage", exception); }
            };
            NeoForge.EVENT_BUS.addListener(listener);
            try { fixture.run(); }
            finally { NeoForge.EVENT_BUS.unregister(listener); }
            helper.assertTrue(interrupted.get(), "placement performs a native chunk checkpoint");
            helper.assertTrue(fixture.data().campaignPersistencePending(), "completion save failure remains pending");
            helper.assertTrue(fixture.entities().isEmpty(), "unsaved environment completion stops entity creation");
            helper.assertTrue(diskBlock(helper, fixture.barrier()).is(Blocks.GLASS), "native placement survives the failed receipt completion");
            fixture.restore(); fixture.reload();
            helper.assertValueEqual(fixture.encounter().environmentBlocks().get("barrier").state(),
                    EncounterInstance.EnvironmentBlockState.PREPARED, "reload uses the original prepared ownership");
            fixture.run(); fixture.reload();
            helper.assertValueEqual(fixture.encounter().environmentBlocks().get("barrier").state(),
                    EncounterInstance.EnvironmentBlockState.APPLIED, "recovered placement completes its receipt");
            helper.assertValueEqual(fixture.entities().size(), 2, "recovery creates the subsequent mob and ally once");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_environment_persistence")
    public static void cleanupPreservesPlayerChangesInTheSavedChunk(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPersistenceGameTests.Fixture(helper)) {
            fixture.run();
            helper.getLevel().setBlock(fixture.barrier(), Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
            fixture.reload();
            var result = EncounterService.cleanup(fixture.base.base.server, fixture.data(), fixture.encounter(), true);
            helper.assertValueEqual(result.status(), EncounterService.Status.COMPLETED, "conflicting player block can be preserved");
            helper.assertTrue(helper.getLevel().getBlockState(fixture.barrier()).is(Blocks.DIAMOND_BLOCK), "cleanup preserves the live player block");
            helper.assertTrue(diskBlock(helper, fixture.barrier()).is(Blocks.DIAMOND_BLOCK), "cleanup also preserves the saved player block");
            fixture.reload();
            helper.assertValueEqual(fixture.encounter().environmentBlocks().get("barrier").state(),
                    EncounterInstance.EnvironmentBlockState.PRESERVED, "preservation is recorded across reload");
        }
        helper.succeed();
    }

    private static BlockState diskBlock(GameTestHelper helper, BlockPos position) {
        var saved = helper.getLevel().getChunkSource().chunkMap.read(new ChunkPos(position)).join();
        helper.assertTrue(saved.isPresent(), "native chunk checkpoint must exist");
        for (var raw : saved.orElseThrow().getList("sections", Tag.TAG_COMPOUND)) {
            var section = (net.minecraft.nbt.CompoundTag) raw;
            if (section.getByte("Y") != position.getY() >> 4 || !section.contains("block_states", Tag.TAG_COMPOUND)) continue;
            var states = PalettedContainer.codecRW(Block.BLOCK_STATE_REGISTRY, BlockState.CODEC,
                    PalettedContainer.Strategy.SECTION_STATES, Blocks.AIR.defaultBlockState())
                    .parse(NbtOps.INSTANCE, section.getCompound("block_states")).getOrThrow();
            return states.get(position.getX() & 15, position.getY() & 15, position.getZ() & 15);
        }
        return Blocks.AIR.defaultBlockState();
    }
}
