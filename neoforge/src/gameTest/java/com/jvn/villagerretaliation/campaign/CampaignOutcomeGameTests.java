package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignDefinitionParser;
import com.jvn.villagerretaliation.quest.content.bundle.QuestLocaleCatalog;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.neoforged.neoforge.common.IOUtilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignOutcomeGameTests {
    private static final ResourceLocation CAMPAIGN = ResourceLocation.parse("test:outcomes");

    private CampaignOutcomeGameTests() {
    }

    @GameTest(template = "empty", batch = "campaign_outcomes")
    public static void campaignIdentityIsWrittenOnceAndSurvivesDiskReload(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk(helper)) {
            var content = content();
            var data = disk.load();
            var first = data.open(content, 42L);
            helper.assertValueEqual(data.open(content, 43L).instanceId(), first.instanceId(), "opening twice retains identity");
            var restored = disk.load().open(content, 90L);
            helper.assertValueEqual(restored.instanceId(), first.instanceId(), "identity is durable before open returns");
            helper.assertValueEqual(restored.createdAt(), 42L, "creation time remains stable");
            helper.assertValueEqual(restored.title(), "Outcome fixture", "saved title remains readable without its resource");
            helper.assertValueEqual(restored.fingerprint(), content.definition().fingerprint(), "instance binds its compiled definition");
            helper.assertFalse(data.isDirty(), "acknowledged state does not wait for autosave");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_outcomes")
    public static void outcomeAndParticipationAreDurableBeforeAcknowledgement(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk(helper)) {
            var content = content();
            var data = disk.load();
            data.open(content, 1L);
            UUID player = UUID.randomUUID();
            UUID operation = UUID.randomUUID();
            var result = decide(data, content, "dragon", "defeated", operation, 0L, Set.of(player));
            helper.assertValueEqual(result.status(), CampaignSavedData.Status.APPLIED, "first choice commits");
            var restored = disk.load().instances().get(CAMPAIGN);
            helper.assertValueEqual(restored.outcomes().get("dragon").operationId(), operation, "operation receipt is durable");
            helper.assertValueEqual(restored.outcomes().get("dragon").title(), "Defeated", "outcome remains readable without content");
            helper.assertValueEqual(restored.players().get(player).participation(), Set.of("dragon"), "actual participation is recorded separately");
            helper.assertValueEqual(restored.players().get(player).discoveredOutcomes(), Map.of("dragon", "defeated"), "participant knows the result");
            helper.assertValueEqual(restored.players().get(player).revision(), 1L, "personal revision advances independently");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_outcomes")
    public static void simultaneousChoicesCommitOneOutcomeAndReturnItToBothCallers(GameTestHelper helper) throws Exception {
        try (Disk disk = new Disk(helper); var executor = Executors.newFixedThreadPool(2)) {
            var content = content();
            var data = disk.load();
            data.open(content, 1L);
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            List<Callable<CampaignSavedData.CommitResult>> requests = List.of(
                    () -> decide(data, content, "dragon", "defeated", UUID.randomUUID(), 0L, Set.of(first)),
                    () -> decide(data, content, "dragon", "spared", UUID.randomUUID(), 0L, Set.of(second)));
            var results = executor.invokeAll(requests);
            var one = results.get(0).get();
            var two = results.get(1).get();
            helper.assertValueEqual(Set.of(one.status(), two.status()),
                    Set.of(CampaignSavedData.Status.APPLIED, CampaignSavedData.Status.ALREADY_COMMITTED), "exactly one choice wins");
            helper.assertValueEqual(one.committed(), two.committed(), "both callers receive the actual committed outcome");
            var restored = disk.load().instances().get(CAMPAIGN);
            UUID winner = one.status() == CampaignSavedData.Status.APPLIED ? first : second;
            helper.assertValueEqual(restored.players().keySet(), Set.of(winner), "losing request does not gain participation");
            helper.assertValueEqual(restored.worldRevision(), 1L, "world advances only once");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_outcomes")
    public static void duplicateRequestsAfterReloadCannotAddParticipants(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk(helper)) {
            var content = content();
            var data = disk.load();
            data.open(content, 1L);
            UUID operation = UUID.randomUUID();
            UUID participant = UUID.randomUUID();
            decide(data, content, "dragon", "spared", operation, 0L, Set.of(participant));
            var restored = disk.load();
            var before = restored.instances().get(CAMPAIGN);
            var result = decide(restored, content, "dragon", "defeated", operation, 0L, Set.of(UUID.randomUUID()));
            helper.assertValueEqual(result.status(), CampaignSavedData.Status.ALREADY_COMMITTED, "replayed choice observes the committed result");
            helper.assertValueEqual(result.committed().outcome(), "spared", "different requested outcome cannot replace the winner");
            helper.assertValueEqual(restored.instances().get(CAMPAIGN), before, "duplicate does not change player records or revision");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_outcomes")
    public static void staleAndInvalidRequestsCannotCommitAnotherMilestone(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk(helper)) {
            var content = content();
            var data = disk.load();
            data.open(content, 1L);
            UUID operation = UUID.randomUUID();
            decide(data, content, "dragon", "defeated", operation, 0L, Set.of());
            helper.assertValueEqual(decide(data, content, "gate", "spared", operation, 1L, Set.of()).status(),
                    CampaignSavedData.Status.INVALID_REQUEST, "operation identity cannot be reused for another milestone");
            helper.assertValueEqual(decide(data, content, "gate", "spared", UUID.randomUUID(), 0L, Set.of()).status(),
                    CampaignSavedData.Status.STALE, "stale world revision cannot commit another choice");
            helper.assertValueEqual(decide(data, content, "gate", "unknown", UUID.randomUUID(), 1L, Set.of()).status(),
                    CampaignSavedData.Status.INVALID_REQUEST, "unknown outcome cannot commit");
            helper.assertValueEqual(data.instances().get(CAMPAIGN).outcomes().keySet(), Set.of("dragon"), "rejections have no effects");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_outcomes")
    public static void lateDiscoveryDoesNotInventParticipationOrChangeWorldRevision(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk(helper)) {
            var content = content();
            var data = disk.load();
            data.open(content, 1L);
            UUID participant = UUID.randomUUID();
            UUID latePlayer = UUID.randomUUID();
            decide(data, content, "dragon", "defeated", UUID.randomUUID(), 0L, Set.of(participant));
            helper.assertFalse(data.instances().get(CAMPAIGN).players().containsKey(latePlayer), "absent players learn nothing automatically");
            helper.assertTrue(data.discover(content.definition(), latePlayer, "dragon"), "late player can discover the shared result");
            helper.assertFalse(data.discover(content.definition(), latePlayer, "dragon"), "duplicate discovery is idempotent");
            var restored = disk.load().instances().get(CAMPAIGN);
            helper.assertValueEqual(restored.worldRevision(), 1L, "discovery does not invalidate world choice revisions");
            helper.assertTrue(restored.players().get(latePlayer).participation().isEmpty(), "discovery never awards participation");
            helper.assertValueEqual(restored.players().get(latePlayer).discoveredOutcomes(), Map.of("dragon", "defeated"), "late discovery is durable");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_outcomes")
    public static void priorDragonDefeatCommitsOnceWithoutParticipantCredit(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk(helper); DragonState dragon = new DragonState(helper)) {
            var content = content();
            var data = disk.load();
            CampaignWorldEventService.reconcile(data, content, dragon.level, 1L);
            helper.assertTrue(data.instances().get(CAMPAIGN).outcomes().isEmpty(), "an undefeated world has no observed outcome");
            dragon.markPreviouslyDefeated();
            CampaignWorldEventService.reconcile(data, content, dragon.level, 2L);
            var before = data.instances().get(CAMPAIGN);
            helper.assertValueEqual(before.outcomes().get("dragon").origin(), CampaignInstance.Origin.WORLD_OBSERVATION,
                    "existing dragon state records its actual source");
            helper.assertTrue(before.players().isEmpty(), "world state does not invent participants or discovery");
            var restored = disk.load();
            CampaignWorldEventService.reconcile(restored, content, dragon.level, 100L);
            helper.assertValueEqual(restored.instances().get(CAMPAIGN), before, "repeat defeat and reload do not duplicate the outcome");
            helper.assertFalse(before.outcomes().containsKey("gate"), "only milestones authored for this event resolve");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_outcomes")
    public static void observationsCannotAwardParticipationOrOverwriteDecisions(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk(helper); DragonState dragon = new DragonState(helper)) {
            var content = content();
            var data = disk.load();
            data.open(content, 1L);
            var rejected = data.commitWorldOutcome(content, "dragon", "defeated", UUID.randomUUID(), 0L,
                    CampaignInstance.Origin.WORLD_OBSERVATION, Set.of(UUID.randomUUID()), 2L);
            helper.assertValueEqual(rejected.status(), CampaignSavedData.Status.INVALID_REQUEST, "observation cannot claim player participation");
            decide(data, content, "dragon", "spared", UUID.randomUUID(), 0L, Set.of());
            dragon.markPreviouslyDefeated();
            CampaignWorldEventService.reconcile(data, content, dragon.level, 3L);
            helper.assertValueEqual(data.instances().get(CAMPAIGN).outcomes().get("dragon").outcome(), "spared",
                    "a later world event does not replace an irreversible committed choice");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_outcomes")
    public static void failedOutcomeWritesLeaveMemoryAndPreviousSaveUnchanged(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk(helper)) {
            var content = content();
            var data = disk.load();
            var before = data.open(content, 1L);
            Path backup = disk.root.resolve("previous.dat");
            Files.move(disk.file(), backup);
            Files.createDirectory(disk.file());
            try {
                boolean failed = false;
                try {
                    decide(data, content, "dragon", "defeated", UUID.randomUUID(), 0L, Set.of(UUID.randomUUID()));
                } catch (IOException exception) {
                    failed = true;
                }
                helper.assertTrue(failed, "failed persistence reaches the caller");
                helper.assertValueEqual(data.instances().get(CAMPAIGN), before, "unwritten candidate cannot become authoritative in memory");
            } finally {
                Files.delete(disk.file());
                Files.move(backup, disk.file());
            }
            helper.assertValueEqual(disk.load().instances().get(CAMPAIGN), before, "previous durable state remains intact");
            helper.assertValueEqual(decide(data, content, "dragon", "defeated", UUID.randomUUID(), 0L, Set.of()).status(),
                    CampaignSavedData.Status.APPLIED, "repairing storage permits a fresh commit");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_outcomes")
    public static void incompatibleDefinitionsPreserveReadableStateUntilRestored(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk(helper)) {
            var original = content();
            var data = disk.load();
            var before = data.open(original, 1L);
            var root = definition();
            root.addProperty("revision", 2);
            var replacement = new CampaignContentCatalog.Entry(CampaignDefinitionParser.parse(CAMPAIGN, root).definition(), original.localization());
            helper.assertTrue(data.compatibility(replacement.definition()).contains(before.fingerprint()), "incompatible content explains how to restore the saved definition");
            helper.assertValueEqual(decide(data, replacement, "dragon", "defeated", UUID.randomUUID(), 0L, Set.of()).status(),
                    CampaignSavedData.Status.UNAVAILABLE, "incompatible definitions cannot mutate saved outcomes");
            helper.assertValueEqual(disk.load().instances().get(CAMPAIGN).title(), "Outcome fixture", "state remains readable without loading its definition");
            helper.assertValueEqual(data.open(original, 2L), before, "restoring matching content resumes the same instance");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_outcomes")
    public static void unsupportedAndMalformedSavesRemainPreserved(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk(helper)) {
            var data = disk.load();
            data.open(content(), 1L);
            var valid = data.save(new CompoundTag(), helper.getLevel().registryAccess());
            var future = valid.copy();
            future.putInt("DataVersion", CampaignSavedData.CURRENT_DATA_VERSION + 1);
            assertPreserved(helper, disk, future);
            var duplicate = valid.copy();
            var campaigns = duplicate.getList("Campaigns", Tag.TAG_COMPOUND);
            campaigns.add(campaigns.get(0).copy());
            assertPreserved(helper, disk, duplicate);
            var malformed = valid.copy();
            malformed.getList("Campaigns", Tag.TAG_COMPOUND).getCompound(0).putString("WorldRevision", "invalid");
            assertPreserved(helper, disk, malformed);
            var migrated = CampaignSavedData.load(new CompoundTag(), disk.directory);
            helper.assertValueEqual(migrated.save(new CompoundTag(), helper.getLevel().registryAccess()).getInt("DataVersion"),
                    CampaignSavedData.CURRENT_DATA_VERSION, "empty unversioned state initializes the current format");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_outcomes")
    public static void unreadableCampaignFilesCannotBeReplacedByNewInstances(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk(helper)) {
            Files.createDirectories(disk.directory);
            byte[] original = {3, 5, 7, 9};
            Files.write(disk.file(), original);
            var data = disk.load();
            helper.assertFalse(data.diagnostic().isBlank(), "unreadable state explains recovery");
            boolean failed = false;
            try {
                data.open(content(), 1L);
            } catch (IOException exception) {
                failed = true;
            }
            helper.assertTrue(failed, "unreadable state blocks new instances");
            data.setDirty();
            data.save(disk.file().toFile(), helper.getLevel().registryAccess());
            IOUtilities.waitUntilIOWorkerComplete();
            helper.assertTrue(Arrays.equals(Files.readAllBytes(disk.file()), original), "original unreadable bytes survive autosave");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_outcomes")
    public static void separateCampaignsKeepIndependentIdentitiesAndOutcomes(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk(helper)) {
            var first = content();
            var root = definition();
            root.addProperty("id", "test:second");
            var second = new CampaignContentCatalog.Entry(CampaignDefinitionParser.parse(CAMPAIGN, root).definition(), first.localization());
            var data = disk.load();
            var one = data.open(first, 1L);
            var two = data.open(second, 1L);
            helper.assertFalse(one.instanceId().equals(two.instanceId()), "each campaign receives its own world identity");
            decide(data, first, "dragon", "defeated", UUID.randomUUID(), 0L, Set.of());
            var restored = disk.load().instances();
            helper.assertValueEqual(restored.size(), 2, "a commit preserves other campaigns");
            helper.assertTrue(restored.get(second.definition().id()).outcomes().isEmpty(), "matching milestone names remain campaign-scoped");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_outcomes")
    public static void failedInstanceCreationRemainsRetryable(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk(helper)) {
            Files.writeString(disk.directory, "blocking file");
            var data = CampaignSavedData.load(new CompoundTag(), disk.directory);
            boolean failed = false;
            try {
                data.open(content(), 1L);
            } catch (IOException exception) {
                failed = true;
            }
            helper.assertTrue(failed, "failed instance persistence reaches its caller");
            helper.assertTrue(data.instances().isEmpty(), "failed creation publishes no campaign identity");
            helper.assertValueEqual(Files.readString(disk.directory), "blocking file", "failure preserves existing storage");
            Files.delete(disk.directory);
            var created = data.open(content(), 2L);
            helper.assertValueEqual(disk.load().instances().get(CAMPAIGN), created, "retry publishes only its durable instance");
        }
        helper.succeed();
    }

    private static void assertPreserved(GameTestHelper helper, Disk disk, CompoundTag input) {
        var expected = input.copy();
        var data = CampaignSavedData.load(input, disk.directory);
        helper.assertFalse(data.diagnostic().isBlank(), "unrecognized state is unavailable with a diagnostic");
        input.putString("Mutation", "ignored");
        helper.assertValueEqual(data.save(new CompoundTag(), helper.getLevel().registryAccess()), expected, "unrecognized data remains untouched");
    }

    private static CampaignSavedData.CommitResult decide(CampaignSavedData data, CampaignContentCatalog.Entry content,
            String milestone, String outcome, UUID operation, long revision, Set<UUID> participants) throws IOException {
        return data.commitWorldOutcome(content, milestone, outcome, operation, revision, CampaignInstance.Origin.PLAYER_DECISION, participants, 2L);
    }

    private static CampaignContentCatalog.Entry content() {
        var parsed = CampaignDefinitionParser.parse(CAMPAIGN, definition());
        if (!parsed.valid()) throw new IllegalStateException(parsed.diagnostics().toString());
        return new CampaignContentCatalog.Entry(parsed.definition(), new QuestLocaleCatalog(Map.of("en_us", Map.of(
                "campaign.fixture.title", new JsonPrimitive("Outcome fixture"),
                "campaign.fixture.chapter", new JsonPrimitive("Opening"),
                "campaign.fixture.defeated", new JsonPrimitive("Defeated"),
                "campaign.fixture.spared", new JsonPrimitive("Spared")))));
    }

    private static com.google.gson.JsonObject definition() {
        return JsonParser.parseString("""
                {"schema":"villagerretaliation:campaign/v1","id":"test:outcomes","revision":1,
                 "title_key":"campaign.fixture.title","localization_prefix":"campaign.fixture.",
                 "quest_bundles":["test:quest","test:catch_up"],
                 "chapters":[{"id":"opening","title_key":"campaign.fixture.chapter","quests":["test:quest"]}],
                 "milestones":[
                  {"id":"dragon","chapter":"opening","quest":"test:quest",
                   "outcomes":[{"id":"defeated","title_key":"campaign.fixture.defeated"},{"id":"spared","title_key":"campaign.fixture.spared"}],
                   "catch_up":{"quest":"test:catch_up","stage":"learn"},
                   "observe":{"event":"minecraft:ender_dragon_defeated","outcome":"defeated"}},
                  {"id":"gate","chapter":"opening","quest":"test:quest",
                   "outcomes":[{"id":"defeated","title_key":"campaign.fixture.defeated"},{"id":"spared","title_key":"campaign.fixture.spared"}],
                   "catch_up":{"quest":"test:catch_up","stage":"learn"}}]}
                """).getAsJsonObject();
    }

    private static final class DragonState implements AutoCloseable {
        private final ServerLevel level;
        private final EndDragonFight original;

        private DragonState(GameTestHelper helper) {
            this.level = helper.getLevel().getServer().getLevel(Level.END);
            if (this.level == null) throw new IllegalStateException("fixture requires the End");
            this.original = this.level.getDragonFight();
            this.level.setDragonFight(new EndDragonFight(this.level, 0L, EndDragonFight.Data.DEFAULT));
        }

        private void markPreviouslyDefeated() {
            this.level.setDragonFight(new EndDragonFight(this.level, 0L, new EndDragonFight.Data(false, false, true, false,
                    Optional.empty(), Optional.empty(), Optional.empty())));
        }

        @Override
        public void close() {
            this.level.setDragonFight(this.original);
        }
    }

    private static final class Disk implements AutoCloseable {
        private final GameTestHelper helper;
        private final Path root;
        private final Path directory;

        private Disk(GameTestHelper helper) throws IOException {
            this.helper = helper;
            this.root = Files.createTempDirectory("campaign-outcomes-");
            this.directory = this.root.resolve("data");
        }

        private Path file() {
            return this.directory.resolve(CampaignSavedData.DATA_NAME + ".dat");
        }

        private CampaignSavedData load() {
            IOUtilities.waitUntilIOWorkerComplete();
            var storage = new DimensionDataStorage(this.directory.toFile(), this.helper.getLevel().getServer().getFixerUpper(),
                    this.helper.getLevel().registryAccess());
            return CampaignSavedData.get(storage, this.directory);
        }

        @Override
        public void close() throws IOException {
            IOUtilities.waitUntilIOWorkerComplete();
            if (Files.isDirectory(this.directory)) Files.deleteIfExists(file());
            Files.deleteIfExists(this.directory);
            Files.deleteIfExists(this.root);
        }
    }
}
