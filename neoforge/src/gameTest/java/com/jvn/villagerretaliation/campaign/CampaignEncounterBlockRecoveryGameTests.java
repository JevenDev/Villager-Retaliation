package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.scene.encounter.EncounterBlockOperatorService;
import com.jvn.villagerretaliation.scene.encounter.EncounterBlockOwnership;
import com.jvn.villagerretaliation.scene.encounter.EncounterInstance;
import com.jvn.villagerretaliation.scene.encounter.EncounterService;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignEncounterBlockRecoveryGameTests {
    private CampaignEncounterBlockRecoveryGameTests() { }

    @GameTest(template = "empty", batch = "campaign_encounter_environment_persistence")
    public static void nativeReservationWaitsForSceneAcknowledgementBeforePlacement(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPersistenceGameTests.Fixture(helper)) {
            var interrupted = new AtomicBoolean();
            Consumer<ChunkDataEvent.Save> listener = event -> {
                if (!event.getChunk().getPos().equals(new ChunkPos(fixture.barrier())) || !interrupted.compareAndSet(false, true)) return;
                var saved = fixture.disk().encounter(fixture.encounter().id()).orElseThrow().environmentBlocks().get("barrier");
                helper.assertValueEqual(saved.ownership().state(), EncounterBlockOwnership.State.PENDING, "scene intent is saved before native reservation");
                try { fixture.block(); }
                catch (IOException exception) { throw new AssertionError("cannot interrupt reservation acknowledgement", exception); }
            };
            NeoForge.EVENT_BUS.addListener(listener);
            try { fixture.run(); }
            finally { NeoForge.EVENT_BUS.unregister(listener); }
            helper.assertTrue(interrupted.get(), "native reservation was attempted");
            helper.assertTrue(helper.getLevel().getBlockState(fixture.barrier()).isAir(), "unacknowledged reservation cannot place blocks");
            helper.assertTrue(fixture.entities().isEmpty(), "unacknowledged reservation stops subsequent entities");
            var operation = fixture.encounter().environmentBlocks().get("barrier").ownership().operation();
            fixture.restore(); fixture.reload();
            helper.assertValueEqual(fixture.encounter().environmentBlocks().get("barrier").ownership().state(),
                    EncounterBlockOwnership.State.PENDING, "reload retains pending scene acknowledgement");
            fixture.run(); fixture.reload();
            var restored = fixture.encounter().environmentBlocks().get("barrier");
            helper.assertValueEqual(restored.ownership().operation(), operation, "recovery reuses the original native reservation");
            helper.assertValueEqual(restored.state(), EncounterInstance.EnvironmentBlockState.APPLIED, "repaired storage resumes placement");
            helper.assertValueEqual(fixture.entities().size(), 2, "subsequent actors spawn once");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_environment_persistence")
    public static void legacySceneMigrationPreservesTerrainWithoutInventingOwnership(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPersistenceGameTests.Fixture(helper)) {
            fixture.run();
            var root = fixture.data().save(new CompoundTag(), helper.getLevel().registryAccess());
            root.putInt("DataVersion", 17);
            block(root).remove("Ownership");
            install(fixture, root);
            var chunk = helper.getLevel().getChunkSource().getChunkNow(fixture.barrier().getX() >> 4, fixture.barrier().getZ() >> 4);
            chunk.removeData(CampaignBlockOwnership.OWNERSHIP);
            helper.assertValueEqual(fixture.encounter().environmentBlocks().get("barrier").ownership().state(),
                    EncounterBlockOwnership.State.LEGACY, "migration identifies unverifiable legacy ownership");
            helper.assertValueEqual(EncounterService.cleanup(fixture.base.base.server, fixture.data(), fixture.encounter(), true).status(),
                    EncounterService.Status.COMPLETED, "legacy cleanup can safely preserve terrain");
            helper.assertTrue(helper.getLevel().getBlockState(fixture.barrier()).is(Blocks.GLASS), "matching legacy terrain is preserved");
            helper.assertFalse(chunk.hasData(CampaignBlockOwnership.OWNERSHIP), "migration does not fabricate native ownership");
            fixture.reload();
            helper.assertValueEqual(fixture.encounter().environmentBlocks().get("barrier").state(),
                    EncounterInstance.EnvironmentBlockState.PRESERVED, "preserved legacy block remains settled after reload");
            helper.assertFalse(fixture.encounter().environmentBlocks().get("barrier").ownership().diagnostic().isBlank(),
                    "legacy preservation retains an inspectable diagnostic");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_environment_persistence")
    public static void unknownSceneOwnershipRemainsIntactAndBlocksCleanup(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPersistenceGameTests.Fixture(helper)) {
            fixture.run();
            var original = fixture.data().save(new CompoundTag(), helper.getLevel().registryAccess());
            var future = block(original).getCompound("Ownership").copy(); future.putInt("DataVersion", 99);
            var extra = block(original).getCompound("Ownership").copy(); extra.putString("FutureField", "retain");
            for (var raw : List.<Tag>of(future, extra, StringTag.valueOf("unreadable ownership"))) {
                var root = original.copy(); block(root).put("Ownership", raw.copy());
                install(fixture, root);
                helper.assertValueEqual(fixture.encounter().environmentBlocks().get("barrier").ownership().state(),
                        EncounterBlockOwnership.State.UNREADABLE, "unknown nested ownership does not drop its encounter");
                helper.assertValueEqual(EncounterService.cleanup(fixture.base.base.server, fixture.data(), fixture.encounter(), true).status(),
                        EncounterService.Status.WAITING, "unknown ownership stops cleanup");
                helper.assertValueEqual(block(fixture.data().save(new CompoundTag(), helper.getLevel().registryAccess())).get("Ownership"),
                        raw, "unknown ownership survives save exactly");
                helper.assertTrue(helper.getLevel().getBlockState(fixture.barrier()).is(Blocks.GLASS), "unreadable data never erases the block");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_environment_persistence")
    public static void operatorPreservationRetiresNativeOwnershipAndSavesOneAudit(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPersistenceGameTests.Fixture(helper)) {
            fixture.run();
            var id = fixture.encounter().id();
            var result = EncounterBlockOperatorService.preserve(helper.getLevel(), id, "barrier", "Keep the player barrier", "test-operator");
            helper.assertTrue(result.success(), "explicit preservation succeeds: " + result.message());
            fixture.reload();
            helper.assertValueEqual(fixture.encounter().environmentBlocks().get("barrier").state(),
                    EncounterInstance.EnvironmentBlockState.PRESERVED, "repair survives reload");
            var chunk = helper.getLevel().getChunkSource().getChunkNow(fixture.barrier().getX() >> 4, fixture.barrier().getZ() >> 4);
            helper.assertValueEqual(CampaignBlockOwnership.receipt(chunk, fixture.barrier()).state(),
                    CampaignBlockOwnershipLedger.State.CONFLICT, "native ownership is retired before scene acknowledgement");
            helper.assertTrue(EncounterBlockOperatorService.preserve(helper.getLevel(), id, "barrier", "Keep the player barrier", "test-operator").success(),
                    "duplicate repair reports the saved result");
            helper.assertValueEqual(fixture.disk().auditEntries().stream().filter(entry -> entry.actorAlias().equals(id + "/barrier")
                    && entry.reason().equals("Keep the player barrier") && entry.operatorIdentity().equals("test-operator")).count(), 1L,
                    "repair stores one durable audit with the reason and operator");
            helper.assertTrue(EncounterBlockOperatorService.inspect(helper.getLevel(), id, "barrier").stream()
                    .anyMatch(line -> line.contains("PRESERVED")), "operator inspection explains the settled state");
            EncounterService.cleanup(fixture.base.base.server, fixture.data(), fixture.encounter(), true);
            helper.assertTrue(helper.getLevel().getBlockState(fixture.barrier()).is(Blocks.GLASS), "later cleanup respects explicit preservation");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_environment_persistence")
    public static void operatorPreservationWaitsForStorageAndRetainsUnreadableEvidence(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPersistenceGameTests.Fixture(helper)) {
            fixture.run();
            var root = fixture.data().save(new CompoundTag(), helper.getLevel().registryAccess());
            var unreadable = StringTag.valueOf("retain the original proof");
            block(root).put("Ownership", unreadable);
            install(fixture, root);
            fixture.data().persistCampaignHandoff(helper.getLevel());
            fixture.block();
            helper.assertFalse(EncounterBlockOperatorService.preserve(helper.getLevel(), fixture.encounter().id(), "barrier", "Preserve unknown proof", "test-operator").success(),
                    "repair cannot claim success with unwritable scene storage");
            helper.assertValueEqual(fixture.encounter().environmentBlocks().get("barrier").state(),
                    EncounterInstance.EnvironmentBlockState.APPLIED, "failed initial checkpoint leaves the repair uncommitted");
            fixture.restore();
            helper.assertTrue(EncounterBlockOperatorService.preserve(helper.getLevel(), fixture.encounter().id(), "barrier", "Preserve unknown proof", "test-operator").success(),
                    "explicit preservation can settle unreadable ownership without replacing it");
            fixture.reload();
            helper.assertValueEqual(block(fixture.data().save(new CompoundTag(), helper.getLevel().registryAccess())).get("Ownership"),
                    unreadable, "operator repair retains unreadable proof for later recovery");
            helper.assertValueEqual(fixture.encounter().environmentBlocks().get("barrier").state(),
                    EncounterInstance.EnvironmentBlockState.PRESERVED, "explicit repair persists independently of opaque evidence");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_environment_persistence")
    public static void failedRepairAcknowledgementRecoversWithoutReplayingTheBlockChange(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPersistenceGameTests.Fixture(helper)) {
            fixture.run();
            var interrupted = new AtomicBoolean();
            Consumer<ChunkDataEvent.Save> listener = event -> {
                if (!event.getChunk().getPos().equals(new ChunkPos(fixture.barrier())) || !interrupted.compareAndSet(false, true)) return;
                try { fixture.block(); }
                catch (IOException exception) { throw new AssertionError("cannot interrupt repair acknowledgement", exception); }
            };
            NeoForge.EVENT_BUS.addListener(listener);
            try {
                helper.assertFalse(EncounterBlockOperatorService.preserve(helper.getLevel(), fixture.encounter().id(), "barrier",
                        "Preserve through storage failure", "test-operator").success(), "failed scene acknowledgement cannot report repair success");
            } finally { NeoForge.EVENT_BUS.unregister(listener); }
            fixture.restore(); fixture.reload();
            helper.assertValueEqual(fixture.encounter().environmentBlocks().get("barrier").state(),
                    EncounterInstance.EnvironmentBlockState.APPLIED, "disk retains the earlier scene state");
            var chunk = helper.getLevel().getChunkSource().getChunkNow(fixture.barrier().getX() >> 4, fixture.barrier().getZ() >> 4);
            helper.assertValueEqual(CampaignBlockOwnership.receipt(chunk, fixture.barrier()).state(),
                    CampaignBlockOwnershipLedger.State.CONFLICT, "native preservation survives the failed scene acknowledgement");
            helper.assertTrue(EncounterBlockOperatorService.preserve(helper.getLevel(), fixture.encounter().id(), "barrier",
                    "Preserve through storage failure", "test-operator").success(), "repair resumes from native preservation");
            fixture.reload();
            helper.assertValueEqual(fixture.data().auditEntries().stream().filter(entry -> entry.reason().equals("Preserve through storage failure")).count(),
                    1L, "recovery saves one repair audit");
            helper.assertTrue(helper.getLevel().getBlockState(fixture.barrier()).is(Blocks.GLASS), "repair recovery never changes terrain");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_environment_persistence")
    public static void repairCommandsRequireOperatorPermissionAndExecuteTheSavedRepair(GameTestHelper helper) throws Exception {
        try (var fixture = new CampaignEncounterPersistenceGameTests.Fixture(helper)) {
            fixture.run();
            var dispatcher = fixture.base.base.server.getCommands().getDispatcher();
            var source = fixture.base.player().createCommandSourceStack();
            var root = dispatcher.getRoot().getChild("vr").getChild("admin").getChild("scene").getChild("encounterBlock");
            helper.assertTrue(root != null && root.canUse(source.withPermission(2)), "operator repair commands are registered");
            helper.assertFalse(root.canUse(source.withPermission(0)), "ordinary players cannot invoke repair commands");
            var id = fixture.encounter().id();
            helper.assertValueEqual(dispatcher.execute("vr admin scene encounterBlock inspect " + id + " barrier", source.withPermission(2)),
                    1, "operator can inspect the existing receipt");
            helper.assertValueEqual(dispatcher.execute("vr admin scene encounterBlock preserve " + id + " barrier Keep this barrier", source.withPermission(2)),
                    1, "operator can explicitly preserve the block");
            fixture.reload();
            helper.assertValueEqual(fixture.encounter().environmentBlocks().get("barrier").state(),
                    EncounterInstance.EnvironmentBlockState.PRESERVED, "command acknowledges a durable repair");
        }
        helper.succeed();
    }

    private static CompoundTag block(CompoundTag root) {
        return root.getList("Encounters", Tag.TAG_COMPOUND).getCompound(0).getList("EnvironmentBlocks", Tag.TAG_COMPOUND).getCompound(0);
    }

    private static void install(CampaignEncounterPersistenceGameTests.Fixture fixture, CompoundTag root) {
        fixture.base.base.server.overworld().getDataStorage().set("villagerretaliation_scenes",
                SceneSavedData.load(root, fixture.helper.getLevel().registryAccess()));
    }
}
