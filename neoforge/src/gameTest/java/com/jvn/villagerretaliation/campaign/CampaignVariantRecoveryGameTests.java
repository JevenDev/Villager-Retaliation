package com.jvn.villagerretaliation.campaign;

import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignVariantRecoveryGameTests {
    private CampaignVariantRecoveryGameTests() { }

    @GameTest(template = "empty", batch = "campaign_location_variant_recovery")
    public static void failedIntentStorageCannotChangeAnyBlock(GameTestHelper helper) throws Exception {
        try (var fixture = new CampaignVariantTestFixture(helper)) {
            fixture.commit("gate", "sealed", UUID.randomUUID());
            var file = fixture.base.disk.root.resolve(CampaignVariantSavedData.DATA_NAME + ".dat");
            Files.createDirectory(file);
            try {
                helper.assertFalse(fixture.reconcile().isBlank(), "failed intent save pauses the effect");
                helper.assertTrue(fixture.data().receipts().isEmpty(), "failed intent never publishes an unsaved receipt");
                helper.assertTrue(helper.getLevel().getBlockState(fixture.floor).is(Blocks.STONE_BRICKS), "no cell changes without a durable plan");
            } finally { Files.delete(file); }
            fixture.reload();
            helper.assertValueEqual(fixture.reconcile(), "", "saved world outcome reconstructs its missing intent after storage recovery");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_location_variant_recovery", timeoutTicks = 200)
    public static void nativeApplicationBeforeCursorSaveResumesFromTheSameReceipt(GameTestHelper helper) throws Exception {
        try (var fixture = new CampaignVariantTestFixture(helper)) {
            fixture.commit("gate", "sealed", UUID.randomUUID());
            var chunk = helper.getLevel().getChunkAt(fixture.floor);
            var file = fixture.base.disk.root.resolve(CampaignVariantSavedData.DATA_NAME + ".dat");
            var backup = fixture.base.disk.root.resolve("variant-plan.dat");
            var blocked = new AtomicBoolean();
            Consumer<ChunkDataEvent.Save> listener = event -> {
                if (event.getChunk() != chunk || !chunk.getBlockState(fixture.floor).is(Blocks.CRACKED_STONE_BRICKS)
                        || !blocked.compareAndSet(false, true)) return;
                try { Files.move(file, backup); Files.createDirectory(file); }
                catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
            };
            NeoForge.EVENT_BUS.addListener(listener);
            String issue;
            try { issue = fixture.reconcile(); }
            finally {
                NeoForge.EVENT_BUS.unregister(listener);
                if (blocked.get()) { Files.delete(file); Files.move(backup, file); }
            }
            helper.assertTrue(blocked.get() && !issue.isBlank(), "failure occurs after native application and before effect acknowledgement");
            helper.assertValueEqual(fixture.receipt("sealed").cursor(), 0, "unsaved cursor is not published");
            var applied = CampaignBlockOwnership.receipt(chunk, fixture.floor);
            var nativeSave = helper.getLevel().getChunkSource().chunkMap.read(chunk.getPos()).join().orElseThrow();
            var restoredOwnership = CampaignBlockOwnershipLedger.load(nativeSave.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).getCompound("villagerretaliation:campaign_blocks"));
            helper.assertValueEqual(restoredOwnership.get(fixture.floor), applied, "native save retains the exact applied operation");
            helper.assertTrue(applied.placed().includedIn(nativeSave, fixture.floor), "native block and receipt reached storage together");
            chunk.setData(CampaignBlockOwnership.OWNERSHIP, restoredOwnership);
            fixture.reload();
            helper.assertValueEqual(fixture.reconcile(), "", "reloaded plan acknowledges the old cell and continues");
            helper.assertValueEqual(CampaignBlockOwnership.receipt(chunk, fixture.floor).operation(), applied.operation(), "recovery never substitutes a new cell operation");
            helper.assertValueEqual(fixture.receipt("sealed").cursor(), 2, "both authored cells complete exactly once");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_location_variant_recovery")
    public static void unreadableAndFutureReceiptsRetainTheirEvidence(GameTestHelper helper) throws Exception {
        try (var fixture = new CampaignVariantTestFixture(helper)) {
            fixture.commit("gate", "sealed", UUID.randomUUID());
            helper.assertValueEqual(fixture.reconcile(), "", "fixture saves a completed effect");
            var original = fixture.data().save(new CompoundTag(), helper.getLevel().registryAccess());
            var future = original.copy(); future.putInt("DataVersion", 99);
            var unknown = original.copy(); unknown.putString("FutureField", "retain");
            var cursor = original.copy(); cursor.getList("Receipts", Tag.TAG_COMPOUND).getCompound(0).putInt("Cursor", 500);
            var changedOwner = original.copy();
            changedOwner.getList("Receipts", Tag.TAG_COMPOUND).getCompound(0).getList("Cells", Tag.TAG_COMPOUND)
                    .getCompound(0).getCompound("Requested").putUUID("Owner", UUID.randomUUID());
            for (var invalid : List.of(future, unknown, cursor, changedOwner)) {
                var data = CampaignVariantSavedData.load(invalid, fixture.base.disk.root);
                helper.assertFalse(data.diagnostic().isBlank(), "invalid receipt is dormant");
                helper.assertValueEqual(data.save(new CompoundTag(), helper.getLevel().registryAccess()), invalid, "unreadable state is preserved exactly");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_location_variant_recovery")
    public static void unloadedLocationDoesNotForceChunkLoading(GameTestHelper helper) throws Exception {
        try (var fixture = new CampaignVariantTestFixture(helper)) {
            fixture.commit("gate", "sealed", UUID.randomUUID());
            var instance = fixture.instance();
            var at = new net.minecraft.core.BlockPos(12_000_000, 70, 12_000_000);
            helper.assertTrue(helper.getLevel().getChunkSource().getChunkNow(at.getX() >> 4, at.getZ() >> 4) == null, "destination starts unloaded");
            var binding = new CampaignLocationBinding(fixture.base.campaignId, instance.instanceId(), instance.definitionRevision(), instance.fingerprint(),
                    "home", com.jvn.villagerretaliation.campaign.content.CampaignLocationDefinition.Source.MARKER,
                    "test:unloaded", helper.getLevel().dimension().location(), at, helper.getLevel().getGameTime());
            var saved = new CompoundTag(); saved.putInt("DataVersion", 1);
            var bindings = new net.minecraft.nbt.ListTag(); bindings.add(binding.save()); saved.put("Bindings", bindings);
            fixture.base.server.overworld().getDataStorage().set(CampaignLocationSavedData.DATA_NAME, CampaignLocationSavedData.load(saved, fixture.base.disk.root));
            helper.assertFalse(fixture.reconcile().isBlank(), "missing loaded provenance is recoverable");
            helper.assertTrue(helper.getLevel().getChunkSource().getChunkNow(at.getX() >> 4, at.getZ() >> 4) == null, "variant search cannot force its destination to load");
            helper.assertTrue(helper.getLevel().getBlockState(fixture.floor).is(Blocks.STONE_BRICKS), "unresolved binding cannot substitute a nearby location");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_location_variant_recovery")
    public static void outcomeContinuationAndLegacyRewardRecoveryKeepSeparateIdentities(GameTestHelper helper) {
        var quest = net.minecraft.resources.ResourceLocation.parse("campaign_fixture:variants");
        var action = com.jvn.villagerretaliation.action.VillagerActionDefinition.readList(quest, "test",
                com.google.gson.JsonParser.parseString("{\"actions\":[{\"type\":\"campaign_outcome\",\"milestone\":\"gate\",\"outcome\":\"sealed\",\"operation_id\":\"gate_choice\"}]}").getAsJsonObject());
        var continuation = new com.jvn.villagerretaliation.scene.runtime.SceneContinuation(UUID.randomUUID(), null, UUID.randomUUID(), UUID.randomUUID(),
                quest, UUID.randomUUID(), "test", action, 0, java.util.Map.of());
        var operation = UUID.randomUUID();
        continuation.waitForOutcome(operation, "fingerprint");
        var saved = continuation.save();
        var restored = com.jvn.villagerretaliation.scene.runtime.SceneContinuation.load(saved);
        helper.assertValueEqual(restored.outcomeOperation(), operation, "new recovery kind survives reload");
        helper.assertTrue(restored.rewardOperation() == null && restored.recoveryIssue().isBlank(), "outcome wait never becomes a reward wait");
        continuation.waitForReward(operation, "fingerprint");
        restored = com.jvn.villagerretaliation.scene.runtime.SceneContinuation.load(continuation.save());
        helper.assertValueEqual(restored.rewardOperation(), operation, "version one recovery remains compatible");
        helper.assertTrue(restored.outcomeOperation() == null, "legacy recovery gains no outcome dependency");
        saved.getCompound("CampaignRecovery").putUUID("RewardOperation", UUID.randomUUID());
        restored = com.jvn.villagerretaliation.scene.runtime.SceneContinuation.load(saved);
        helper.assertFalse(restored.recoveryIssue().isBlank(), "conflicting recovery identities remain unreadable");
        helper.assertValueEqual(restored.save().get("CampaignRecovery"), saved.get("CampaignRecovery"), "ambiguous recovery preserves its raw evidence");
        helper.succeed();
    }
}
