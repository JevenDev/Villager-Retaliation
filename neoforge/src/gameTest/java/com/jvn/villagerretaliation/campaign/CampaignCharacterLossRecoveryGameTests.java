package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignCharacterLossRecoveryGameTests {
    private CampaignCharacterLossRecoveryGameTests() { }

    @GameTest(template = "empty", batch = "campaign_loss_recovery")
    public static void failedLossWritesResumeTheSameReplacementAfterStorageRecovers(GameTestHelper helper) throws IOException {
        for (var reason : List.of(Entity.RemovalReason.KILLED, Entity.RemovalReason.DISCARDED)) {
            try (var fixture = new Fixture(helper, false)) {
                var mentor = fixture.mentor(); var binding = fixture.data().forEntity(mentor.getUUID());
                var successor = fixture.villager();
                fixture.blockWrites(); mentor.remove(reason);
                helper.assertFalse(fixture.data().forEntity(mentor.getUUID()).lost(), "failed write does not publish loss");
                helper.assertFalse(fixture.base.resolve().available(), "unsaved loss blocks resolution");
                helper.assertFalse(CampaignCharacterLossService.diagnostic(fixture.base.server, binding).isBlank(), "pending save explains the blocked role");
                helper.assertTrue(fixture.data().replacements().isEmpty(), "replacement waits for durable loss");
                var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), helper.makeMockServerPlayerInLevel(), successor);
                for (var state : CampaignCharacterPredicate.State.values()) {
                    var condition = new DialogueCondition.CampaignCharacter(new CampaignCharacterPredicate(fixture.base.campaignId, "mentor", state));
                    helper.assertFalse(condition.matches(context), "pending loss cannot satisfy " + state);
                    helper.assertFalse(new DialogueCondition.Not(condition).matches(context), "pending loss cannot unlock negation of " + state);
                    helper.assertFalse(condition.unavailableReason(context).isBlank(), "condition explains the pending save");
                }
                fixture.restoreWrites(); fixture.retry();
                helper.assertTrue(fixture.data().forEntity(mentor.getUUID()).lost(), "confirmed loss retries without another death event");
                helper.assertValueEqual(fixture.base.resolve().entity(), successor, "saved loss permits the authored successor");
                var receipt = fixture.data().replacements().get(binding.key());
                fixture.base.reload(); fixture.retry(); CampaignCharacterService.onRemoved(mentor); fixture.retry();
                helper.assertValueEqual(fixture.base.resolve().entity(), successor, "reload and duplicate events retain the same successor");
                helper.assertValueEqual(fixture.data().replacements().get(binding.key()), receipt, "replacement receipt commits once");
                helper.assertTrue(CampaignSavedData.get(helper.getLevel()).instances().get(fixture.base.campaignId).players().isEmpty(), "loss recovery never creates participant credit");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_loss_recovery")
    public static void alternateStoryObservationWaitsForTheRetriedLoss(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper, true)) {
            var mentor = fixture.mentor(); fixture.blockWrites(); mentor.discard();
            CampaignWorldEventService.reconcile(fixture.base.server);
            helper.assertTrue(fixture.instance().outcomes().isEmpty(), "world outcome waits for durable evidence");
            fixture.restoreWrites(); fixture.retry(); CampaignWorldEventService.reconcile(fixture.base.server);
            var outcome = fixture.instance().outcomes().get("mentor_loss");
            helper.assertTrue(outcome != null && outcome.origin() == CampaignInstance.Origin.WORLD_OBSERVATION, "retried evidence resolves the alternate path");
            fixture.base.reload(); fixture.retry(); CampaignWorldEventService.reconcile(fixture.base.server);
            helper.assertValueEqual(fixture.instance().outcomes().get("mentor_loss"), outcome, "observed outcome remains exactly once");
            helper.assertValueEqual(fixture.instance().worldRevision(), 1L, "loss resolves one world revision");
            helper.assertTrue(fixture.instance().players().isEmpty(), "external loss invents neither participation nor progression");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_loss_recovery")
    public static void unloadingAndUnrelatedDeathsNeverEnterRecovery(GameTestHelper helper) throws IOException {
        for (var reason : List.of(Entity.RemovalReason.UNLOADED_TO_CHUNK, Entity.RemovalReason.CHANGED_DIMENSION)) {
            try (var fixture = new Fixture(helper, false)) {
                var mentor = fixture.mentor(); var binding = fixture.data().forEntity(mentor.getUUID());
                fixture.blockWrites(); mentor.remove(reason); fixture.villager().discard();
                helper.assertTrue(CampaignCharacterLossService.diagnostic(fixture.base.server, binding).isBlank(), "unloading does not create a pending death");
                fixture.restoreWrites(); fixture.retry(); fixture.base.reload();
                helper.assertFalse(fixture.data().forEntity(mentor.getUUID()).lost(), "unloaded role retains its original identity");
                helper.assertTrue(fixture.data().replacements().isEmpty(), "unloading cannot prepare a replacement");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_loss_recovery")
    public static void restoredOriginalCancelsAStalePendingLoss(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper, false)) {
            var mentor = fixture.mentor(); var binding = fixture.data().forEntity(mentor.getUUID());
            var snapshot = mentor.saveWithoutId(new CompoundTag());
            fixture.blockWrites(); mentor.discard(); var restored = fixture.restore(snapshot);
            fixture.retry();
            helper.assertTrue(CampaignCharacterLossService.diagnostic(fixture.base.server, binding).isBlank(), "original recovery clears the pending event without a write");
            helper.assertValueEqual(fixture.base.resolve().entity(), restored, "original can resume while storage is still unavailable");
            restored.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            fixture.restoreWrites(); fixture.retry(); fixture.base.reload();
            helper.assertFalse(fixture.data().forEntity(mentor.getUUID()).lost(), "later unloading cannot revive a cancelled loss");
            helper.assertTrue(fixture.data().replacements().isEmpty(), "recovery does not create a replacement decision");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_loss_recovery")
    public static void changedBindingsCannotInheritOldPendingLosses(GameTestHelper helper) throws IOException {
        for (String changed : List.of("Entity", "Instance", "Fingerprint", "DefinitionRevision", "BoundAt")) {
            try (var fixture = new Fixture(helper, false)) {
                var mentor = fixture.mentor(); var original = fixture.snapshot();
                fixture.blockWrites(); mentor.discard(); fixture.restoreWrites();
                var altered = original.copy(); var binding = altered.getList("Bindings", Tag.TAG_COMPOUND).getCompound(0);
                if (changed.equals("Entity") || changed.equals("Instance")) binding.putUUID(changed, UUID.randomUUID());
                if (changed.equals("Fingerprint")) binding.putString(changed, "restored-content");
                if (changed.equals("DefinitionRevision")) binding.putInt(changed, binding.getInt(changed) + 1);
                if (changed.equals("BoundAt")) binding.putLong(changed, binding.getLong(changed) + 1L);
                fixture.characters(CampaignCharacterSavedData.load(altered, fixture.base.disk.root));
                helper.assertTrue(fixture.data().diagnostic().isBlank(), "alternate save has a readable binding");
                fixture.retry();
                helper.assertValueEqual(fixture.snapshot(), altered, "old event cannot alter the changed binding: " + changed);
                fixture.characters(CampaignCharacterSavedData.load(original, fixture.base.disk.root)); fixture.retry();
                helper.assertValueEqual(fixture.snapshot(), original, "discarded event cannot revive when an older backup is restored");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_loss_recovery")
    public static void unreadableSavesKeepPendingEvidenceUntilOwnershipCanBeChecked(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper, true)) {
            var mentor = fixture.mentor(); fixture.blockWrites(); mentor.discard(); fixture.restoreWrites();
            var valid = fixture.data(); var future = new CompoundTag(); future.putInt("DataVersion", 99);
            fixture.characters(CampaignCharacterSavedData.load(future, fixture.base.disk.root)); fixture.retry();
            helper.assertValueEqual(fixture.snapshot(), future, "retry preserves unreadable saves");
            fixture.characters(valid);
            helper.assertFalse(CampaignCharacterLossService.diagnostic(fixture.base.server, valid.forEntity(mentor.getUUID())).isBlank(), "unreadable ownership does not discard evidence");
            var content = CampaignResources.current(fixture.base.server);
            CampaignResources.installForTests(fixture.base.server, new CampaignContentCatalog(fixture.base.catalog, Map.of(), List.of()));
            fixture.base.mode("sandbox"); fixture.retry();
            helper.assertTrue(fixture.data().forEntity(mentor.getUUID()).lost(), "confirmed physical loss saves without loaded story content or online players");
            CampaignWorldEventService.reconcile(fixture.base.server);
            helper.assertTrue(fixture.instance().outcomes().isEmpty(), "sandbox keeps story effects dormant");
            fixture.base.reload(); fixture.base.mode("campaign");
            CampaignWorldEventService.reconcile(fixture.base.server);
            helper.assertTrue(fixture.instance().outcomes().isEmpty(), "missing content keeps story effects dormant");
            CampaignResources.installForTests(fixture.base.server, content); CampaignWorldEventService.reconcile(fixture.base.server);
            helper.assertTrue(fixture.instance().outcomes().containsKey("mentor_loss"), "restored content consumes the saved loss");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_loss_recovery")
    public static void retryWorkIsBoundedAndRotatesPastFailedWrites(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper, false)) {
            var mentor = fixture.mentor(); var original = fixture.data().forEntity(mentor.getUUID());
            for (int index = 1; index < 17; index++) {
                var villager = fixture.villager();
                fixture.data().bind(new CampaignCharacterBinding(original.campaign(), original.instance(), original.definitionRevision(), original.fingerprint(),
                        "retry_" + index, original.source(), original.lossPolicy(), villager.getUUID(), original.dimension(), original.position(), original.boundAt(), false));
            }
            fixture.blockWrites(); fixture.villagers.forEach(Entity::discard); fixture.retry(); fixture.restoreWrites();
            int chunks = helper.getLevel().getChunkSource().getLoadedChunksCount();
            fixture.retry();
            helper.assertValueEqual(fixture.data().bindings().values().stream().filter(CampaignCharacterBinding::lost).count(), 16L, "one recovery pass writes at most sixteen losses");
            helper.assertTrue(fixture.data().forEntity(fixture.villagers.getLast().getUUID()).lost(), "failed first batch rotates behind the waiting event");
            helper.assertFalse(fixture.data().forEntity(fixture.villagers.get(15).getUUID()).lost(), "remaining work waits for the next pass");
            fixture.retry(); fixture.base.reload();
            helper.assertTrue(fixture.data().bindings().values().stream().allMatch(CampaignCharacterBinding::lost), "next pass durably completes the remaining loss");
            helper.assertValueEqual(helper.getLevel().getChunkSource().getLoadedChunksCount(), chunks, "retry never loads chunks");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_loss_recovery")
    public static void ownedRecoveryRequiresTheOriginalSpawnOwnership(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper, false)) {
            helper.setBlock(1, 0, 1, Blocks.STONE); helper.setBlock(1, 1, 1, Blocks.AIR); helper.setBlock(1, 2, 1, Blocks.AIR);
            var raw = fixture.base.raw(); raw.getAsJsonArray("characters").get(0).getAsJsonObject().addProperty("source", "owned_spawn"); fixture.base.install(raw);
            var resolved = fixture.base.resolve(); helper.assertTrue(resolved.available(), "owned fixture spawns: " + resolved.diagnostic());
            var mentor = resolved.entity(); fixture.villagers.add(mentor);
            var snapshot = mentor.saveWithoutId(new CompoundTag()); var binding = resolved.binding();
            fixture.blockWrites(); mentor.discard();
            var impostor = fixture.restore(snapshot); impostor.getPersistentData().remove("VillagerRetaliationCampaignOwner");
            fixture.retry();
            helper.assertFalse(CampaignCharacterLossService.diagnostic(fixture.base.server, binding).isBlank(), "same UUID without spawn ownership cannot cancel loss");
            helper.assertFalse(fixture.base.resolve().available(), "invalid recovery cannot restart the role or spawn another entity");
            impostor.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            var restored = fixture.restore(snapshot); fixture.retry(); fixture.restoreWrites();
            var recovered = fixture.base.resolve();
            helper.assertTrue(recovered.available(), "original recovery is available: " + recovered.diagnostic());
            helper.assertValueEqual(recovered.entity(), restored, "correct original ownership resumes the character");
            helper.assertFalse(fixture.data().forEntity(mentor.getUUID()).lost(), "verified recovery leaves the original binding alive");
            helper.assertTrue(fixture.data().replacements().isEmpty(), "no successor receipt is created");
            helper.assertValueEqual(CampaignSpawnSavedData.get(helper.getLevel()).receipts().size(), 1, "recovery never creates another spawn receipt");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_loss_recovery")
    public static void serverShutdownClearsOnlyRuntimeRecoveryState(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper, false)) {
            var mentor = fixture.mentor(); fixture.blockWrites(); mentor.discard(); fixture.restoreWrites();
            var before = fixture.snapshot();
            CampaignCharacterLossService.onServerStopped(new ServerStoppedEvent(fixture.base.server)); fixture.retry();
            helper.assertValueEqual(fixture.snapshot(), before, "shutdown cannot claim an unsaved loss was durable");
            helper.assertTrue(CampaignCharacterLossService.diagnostic(fixture.base.server, fixture.data().forEntity(mentor.getUUID())).isBlank(), "server recovery queue is cleared");
        }
        helper.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final CampaignCharacterTestFixture base;
        final List<Villager> villagers = new ArrayList<>();
        private CampaignCharacterSavedData healthy;
        private Path blocked;

        Fixture(GameTestHelper helper, boolean alternatePath) throws IOException {
            this.helper = helper;
            base = alternatePath ? new CampaignCharacterTestFixture(helper, "losses", "loss_greeting", List.of("losses_catch_up"))
                    : new CampaignCharacterTestFixture(helper, "replacements", "replacement_greeting");
        }

        CampaignCharacterSavedData data() { return CampaignCharacterSavedData.get(helper.getLevel()); }
        CampaignInstance instance() { return CampaignSavedData.get(helper.getLevel()).instances().get(base.campaignId); }
        CompoundTag snapshot() { return data().save(new CompoundTag(), helper.getLevel().registryAccess()); }
        void characters(CampaignCharacterSavedData data) { base.server.overworld().getDataStorage().set(CampaignCharacterSavedData.DATA_NAME, data); }
        void retry() { CampaignCharacterLossService.retry(base.server); }
        Villager villager() { var entity = helper.spawn(EntityType.VILLAGER, 1, 1, 1); entity.setNoAi(true); villagers.add(entity); return entity; }
        Villager mentor() { var entity = villager(); helper.assertValueEqual(base.resolve().entity(), entity, "fixture binds its mentor"); return entity; }
        Villager restore(CompoundTag snapshot) {
            var entity = EntityType.VILLAGER.create(helper.getLevel()); entity.load(snapshot.copy());
            helper.assertTrue(helper.getLevel().addFreshEntity(entity), "original character reloads"); villagers.add(entity); return entity;
        }

        void blockWrites() throws IOException {
            healthy = data(); blocked = Files.createTempFile(base.disk.root, "blocked-", ".tmp");
            characters(CampaignCharacterSavedData.load(snapshot(), blocked));
        }

        void restoreWrites() throws IOException {
            if (blocked == null) return;
            characters(healthy); Files.delete(blocked); blocked = null; healthy = null;
        }

        @Override public void close() throws IOException {
            restoreWrites();
            villagers.forEach(Entity::discard);
            CampaignCharacterLossService.onServerStopped(new ServerStoppedEvent(base.server));
            base.close();
        }
    }
}
