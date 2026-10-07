package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignDefinition;
import com.jvn.villagerretaliation.campaign.content.CampaignDefinitionParser;
import com.jvn.villagerretaliation.quest.content.bundle.QuestLocaleCatalog;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.neoforged.neoforge.common.IOUtilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignFactGameTests {
    private static final ResourceLocation CAMPAIGN = ResourceLocation.parse("test:facts");
    private static final ResourceLocation QUEST = ResourceLocation.parse("test:quest");

    private CampaignFactGameTests() {
    }

    @GameTest(template = "empty", batch = "campaign_facts")
    public static void typedWorldFactsAndPersonalDiscoveriesSurviveDiskReload(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk(helper)) {
            var data = disk.open();
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            helper.assertTrue(data.readFact(content().definition(), first, "clue").value() == null, "missing facts remain distinct from explicit values");
            write(data, first, "gate_open", "true", 0L);
            write(data, first, "ruler", "", 1L);
            write(data, first, "supplies", "42", 2L);
            write(data, first, "clue", "true", 0L);
            write(data, second, "clue", "false", 0L);
            write(data, first, "theory", "The gate is a decoy.", 1L);
            write(data, first, "visits", "2", 2L);
            var loaded = disk.load();
            var instance = loaded.instances().get(CAMPAIGN);
            helper.assertValueEqual(loaded.readFact(content().definition(), second, "gate_open").value().text(), "true", "world facts are shared");
            helper.assertValueEqual(loaded.readFact(content().definition(), second, "clue").value().text(), "false", "personal knowledge is isolated");
            helper.assertValueEqual(loaded.readFact(content().definition(), first, "theory").value().text(), "The gate is a decoy.", "personal variables retain their text");
            helper.assertValueEqual(loaded.readFact(content().definition(), first, "ruler").value().text(), "", "empty variables remain explicit values");
            helper.assertValueEqual(instance.facts().world().get("supplies").kind(), CampaignDefinition.FactKind.COUNTER, "counter type survives reload");
            helper.assertValueEqual(instance.worldRevision(), 3L, "personal discoveries do not advance world revision");
            helper.assertValueEqual(instance.players().get(first).revision(), 3L, "personal revision tracks only that player's changes");
            helper.assertTrue(instance.players().values().stream().allMatch(player -> player.participation().isEmpty()
                    && player.discoveredOutcomes().isEmpty() && player.milestones().isEmpty()), "facts do not invent participation or milestone progress");
            helper.assertTrue(loaded.diagnostic().isBlank(), "typed fact save is readable");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_facts")
    public static void repeatedCounterChangesReturnTheirReceiptWithoutApplyingAgain(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk(helper)) {
            var data = disk.open();
            UUID player = UUID.randomUUID();
            UUID operation = UUID.randomUUID();
            var first = change(data, player, "supplies", CampaignFacts.Mutation.ADD, "4", operation, 0L);
            helper.assertValueEqual(first.status(), CampaignSavedData.Status.APPLIED, "first counter operation commits");
            data = disk.load();
            change(data, player, "supplies", CampaignFacts.Mutation.ADD, "3", UUID.randomUUID(), 1L);
            var replay = change(data, player, "supplies", CampaignFacts.Mutation.ADD, "4", operation, 0L);
            helper.assertValueEqual(replay.status(), CampaignSavedData.Status.ALREADY_SATISFIED, "receipt precedes stale revision checks");
            helper.assertValueEqual(replay.receipt(), first.receipt(), "repeat receives its durable original result");
            helper.assertValueEqual(replay.value().text(), "7", "repeat also receives the current fact value");
            helper.assertValueEqual(disk.load().instances().get(CAMPAIGN).worldRevision(), 2L, "repeat does not add a revision");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_facts")
    public static void factOperationIdentityCannotBeReusedWithDifferentIntent(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk(helper)) {
            var data = disk.open();
            UUID player = UUID.randomUUID();
            UUID operation = UUID.randomUUID();
            change(data, player, "supplies", CampaignFacts.Mutation.ADD, "4", operation, 0L);
            var before = data.instances().get(CAMPAIGN);
            helper.assertValueEqual(change(data, player, "supplies", CampaignFacts.Mutation.ADD, "5", operation, 1L).status(), CampaignSavedData.Status.INVALID_REQUEST, "changed operand is rejected");
            helper.assertValueEqual(change(data, UUID.randomUUID(), "supplies", CampaignFacts.Mutation.ADD, "4", operation, 1L).status(), CampaignSavedData.Status.ALREADY_SATISFIED, "another actor receives the shared receipt without repeating its effect");
            helper.assertValueEqual(change(data, player, "visits", CampaignFacts.Mutation.ADD, "4", operation, 0L).status(), CampaignSavedData.Status.INVALID_REQUEST, "changed scope and fact are rejected");
            helper.assertValueEqual(change(data, player, "supplies", CampaignFacts.Mutation.SET, "4", operation, 1L).status(), CampaignSavedData.Status.INVALID_REQUEST, "changed mutation is rejected");
            helper.assertValueEqual(data.instances().get(CAMPAIGN), before, "invalid retries preserve state");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_facts")
    public static void competingFactWritesCommitOnceAndReturnTheCurrentValue(GameTestHelper helper) throws Exception {
        try (Disk disk = new Disk(helper); var executor = Executors.newFixedThreadPool(2)) {
            var data = disk.open();
            List<Callable<CampaignSavedData.FactResult>> changes = List.of(
                    () -> write(data, UUID.randomUUID(), "ruler", "one", 0L),
                    () -> write(data, UUID.randomUUID(), "ruler", "two", 0L));
            var results = executor.invokeAll(changes);
            var first = results.get(0).get();
            var second = results.get(1).get();
            helper.assertValueEqual(Set.of(first.status(), second.status()), Set.of(CampaignSavedData.Status.APPLIED, CampaignSavedData.Status.STALE), "one expected revision wins");
            helper.assertValueEqual(first.value(), second.value(), "losing intent receives the committed value");
            helper.assertValueEqual(disk.load().instances().get(CAMPAIGN).facts().receipts().size(), 1, "only the winning intent has a receipt");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_facts")
    public static void sameValueWritesAreReceiptedAndOldSetOperationsCannotOverwriteNewValues(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk(helper)) {
            var data = disk.open();
            UUID player = UUID.randomUUID();
            UUID operation = UUID.randomUUID();
            change(data, player, "gate_open", CampaignFacts.Mutation.SET, "false", operation, 0L);
            write(data, player, "gate_open", "false", 1L);
            write(data, player, "gate_open", "true", 2L);
            var result = change(disk.load(), player, "gate_open", CampaignFacts.Mutation.SET, "false", operation, 0L);
            helper.assertValueEqual(result.status(), CampaignSavedData.Status.ALREADY_SATISFIED, "old set operation is recognized");
            helper.assertValueEqual(result.value().text(), "true", "old set cannot undo a later change");
            helper.assertValueEqual(disk.load().instances().get(CAMPAIGN).facts().receipts().size(), 3, "same-value operation also has a durable receipt");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_facts")
    public static void outcomeAndFactWritesShareWorldRevisionsButNotPersonalRevisions(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk(helper)) {
            var data = disk.open();
            UUID player = UUID.randomUUID();
            UUID outcome = UUID.randomUUID();
            write(data, player, "clue", "true", 0L);
            helper.assertValueEqual(data.commitWorldOutcome(content(), "gate", "opened", outcome, 0L,
                    CampaignInstance.Origin.PLAYER_DECISION, Set.of(), 3L).status(), CampaignSavedData.Status.APPLIED, "personal changes do not stale world outcomes");
            helper.assertValueEqual(write(data, player, "gate_open", "true", 0L).status(), CampaignSavedData.Status.STALE, "world outcomes stale earlier shared writes");
            helper.assertValueEqual(write(data, player, "gate_open", "true", 1L).status(), CampaignSavedData.Status.APPLIED, "refreshed shared write commits");
            helper.assertValueEqual(write(data, player, "visits", "1", 1L).status(), CampaignSavedData.Status.APPLIED, "world changes do not stale personal writes");
            helper.assertValueEqual(change(data, player, "supplies", CampaignFacts.Mutation.SET, "1", outcome, 2L).status(), CampaignSavedData.Status.INVALID_REQUEST, "fact operation cannot reuse an outcome identity");
            helper.assertTrue(disk.load().diagnostic().isBlank(), "mixed world revisions reload consistently");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_facts")
    public static void invalidFactValuesAndCounterOverflowLeaveStateUntouched(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk(helper)) {
            var data = disk.open();
            UUID player = UUID.randomUUID();
            write(data, player, "supplies", Long.toString(Long.MAX_VALUE), 0L);
            var before = data.instances().get(CAMPAIGN);
            for (String value : List.of("1.5", "01", "1e3", " 1", "9223372036854775808", "")) {
                helper.assertValueEqual(write(data, player, "supplies", value, 1L).status(), CampaignSavedData.Status.INVALID_REQUEST, "malformed counter is rejected: " + value);
            }
            helper.assertValueEqual(change(data, player, "supplies", CampaignFacts.Mutation.ADD, "1", UUID.randomUUID(), 1L).status(), CampaignSavedData.Status.INVALID_REQUEST, "overflow is rejected");
            helper.assertValueEqual(change(data, player, "ruler", CampaignFacts.Mutation.ADD, "1", UUID.randomUUID(), 1L).status(), CampaignSavedData.Status.INVALID_REQUEST, "variables cannot be incremented");
            helper.assertValueEqual(write(data, player, "gate_open", "yes", 1L).status(), CampaignSavedData.Status.INVALID_REQUEST, "malformed tag is rejected");
            helper.assertValueEqual(write(data, player, "missing", "1", 1L).status(), CampaignSavedData.Status.INVALID_REQUEST, "unknown fact is rejected");
            helper.assertValueEqual(write(data, null, "ruler", "one", 1L).status(), CampaignSavedData.Status.INVALID_REQUEST, "missing actor is rejected");
            helper.assertValueEqual(write(data, player, "ruler", "x".repeat(4097), 1L).status(), CampaignSavedData.Status.INVALID_REQUEST, "oversized values are rejected");
            helper.assertValueEqual(data.instances().get(CAMPAIGN), before, "invalid values have no effects");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_facts")
    public static void campaignProgressUpdatesPreserveStoryFacts(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk(helper)) {
            var data = disk.open();
            UUID player = UUID.randomUUID();
            write(data, player, "clue", "true", 0L);
            write(data, player, "gate_open", "true", 0L);
            var facts = data.instances().get(CAMPAIGN).facts();
            data.enterMilestone(content().definition(), player, "gate", 2L);
            data.commitWorldOutcome(content(), "gate", "opened", UUID.randomUUID(), 1L, CampaignInstance.Origin.PLAYER_DECISION, Set.of(player), 3L);
            data.completeMilestone(content().definition(), player, "gate", CampaignInstance.Route.ORIGINAL, "opened", UUID.randomUUID(), 4L);
            data.completeQuest(content().definition(), player, QUEST);
            data.discover(content().definition(), UUID.randomUUID(), "gate");
            var restored = disk.load();
            helper.assertTrue(restored.diagnostic().isBlank(), "personal fact revisions coexist with later progression");
            helper.assertValueEqual(restored.instances().get(CAMPAIGN).facts(), facts, "entry, outcome, completion, and discovery all retain facts and receipts");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_facts")
    public static void versionTwoSavesGainEmptyFactsWithoutChangingProgress(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk(helper)) {
            var data = disk.open();
            UUID player = UUID.randomUUID();
            data.enterMilestone(content().definition(), player, "gate", 1L);
            data.commitWorldOutcome(content(), "gate", "opened", UUID.randomUUID(), 0L, CampaignInstance.Origin.PLAYER_DECISION, Set.of(player), 2L);
            var before = data.instances().get(CAMPAIGN);
            var old = data.save(new CompoundTag(), helper.getLevel().registryAccess());
            old.putInt("DataVersion", 2);
            old.getList("Campaigns", Tag.TAG_COMPOUND).getCompound(0).remove("Facts");
            var restored = CampaignSavedData.load(old, disk.directory);
            helper.assertTrue(restored.diagnostic().isBlank(), "version two migrates");
            helper.assertValueEqual(restored.instances().get(CAMPAIGN), before, "migration preserves identity, outcomes, and personal progress");
            helper.assertFalse(old.getList("Campaigns", Tag.TAG_COMPOUND).getCompound(0).contains("Facts"), "migration leaves the source untouched");
            helper.assertValueEqual(restored.save(new CompoundTag(), helper.getLevel().registryAccess()).getInt("DataVersion"), CampaignSavedData.CURRENT_DATA_VERSION, "migration advances save version");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_facts")
    public static void malformedFactValuesAndReceiptsArePreservedForRepair(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk(helper)) {
            var data = disk.open();
            UUID player = UUID.randomUUID();
            write(data, player, "supplies", "3", 0L);
            write(data, player, "clue", "true", 0L);
            var saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
            List<Consumer<CompoundTag>> corruptions = List.of(
                    tag -> tag.remove("Facts"),
                    tag -> tag.getCompound("Facts").getCompound("World").getCompound("supplies").putInt("Value", 3),
                    tag -> tag.getCompound("Facts").getCompound("World").getCompound("supplies").putString("Value", "4"),
                    tag -> tag.getCompound("Facts").getCompound("World").getCompound("supplies").putString("Kind", "UNKNOWN"),
                    tag -> tag.getCompound("Facts").getList("Receipts", Tag.TAG_COMPOUND).getCompound(0).putLong("Revision", 2L),
                    tag -> tag.getCompound("Facts").getList("Receipts", Tag.TAG_COMPOUND).add(tag.getCompound("Facts").getList("Receipts", Tag.TAG_COMPOUND).get(0).copy()),
                    tag -> tag.getCompound("Facts").getList("Players", Tag.TAG_COMPOUND).getCompound(0).putUUID("Player", UUID.randomUUID()),
                    tag -> tag.getCompound("Facts").getList("Receipts", Tag.TAG_COMPOUND).getCompound(1).putLong("Revision", 2L));
            for (var corrupt : corruptions) {
                var invalid = saved.copy();
                corrupt.accept(invalid.getList("Campaigns", Tag.TAG_COMPOUND).getCompound(0));
                var restored = CampaignSavedData.load(invalid, disk.directory);
                helper.assertFalse(restored.diagnostic().isBlank(), "malformed story state is unavailable");
                helper.assertValueEqual(restored.save(new CompoundTag(), helper.getLevel().registryAccess()), invalid, "malformed state remains intact for repair");
                helper.assertValueEqual(write(restored, player, "ruler", "one", 1L).status(), CampaignSavedData.Status.UNAVAILABLE, "unreadable state blocks mutation");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_facts")
    public static void incompatibleContentCannotReadOrReplaceSavedFacts(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk(helper)) {
            var data = disk.open();
            UUID player = UUID.randomUUID();
            write(data, player, "clue", "true", 0L);
            var definition = content().definition();
            var changed = new CampaignDefinition(definition.id(), definition.revision() + 1, "different", definition.titleKey(), definition.localizationPrefix(),
                    definition.questBundles(), definition.chapters(), definition.milestones(), definition.facts(), definition.source());
            helper.assertValueEqual(data.readFact(changed, player, "clue").status(), CampaignSavedData.Status.UNAVAILABLE, "incompatible content cannot expose facts as current");
            helper.assertValueEqual(data.commitFact(changed, player, "clue", CampaignFacts.Mutation.SET, "false", UUID.randomUUID(), 1L, 2L).status(), CampaignSavedData.Status.UNAVAILABLE, "incompatible content cannot mutate facts");
            helper.assertValueEqual(disk.load().readFact(definition, player, "clue").value().text(), "true", "restoring compatible content recovers saved knowledge");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_facts")
    public static void failedFactWritesPublishNeitherValuesNorReceipts(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk(helper)) {
            var data = disk.open();
            UUID player = UUID.randomUUID();
            write(data, player, "supplies", "3", 0L);
            var before = data.instances().get(CAMPAIGN);
            Path backup = disk.root.resolve("previous.dat");
            Files.move(disk.file(), backup);
            Files.createDirectory(disk.file());
            try {
                boolean failed = false;
                try {
                    write(data, player, "supplies", "4", 1L);
                } catch (IOException exception) {
                    failed = true;
                }
                helper.assertTrue(failed, "write failure reaches the caller");
                helper.assertValueEqual(data.instances().get(CAMPAIGN), before, "failed write leaves memory unchanged");
            } finally {
                Files.delete(disk.file());
                Files.move(backup, disk.file());
            }
            helper.assertValueEqual(disk.load().instances().get(CAMPAIGN), before, "previous durable values and receipts are preserved");
            helper.assertValueEqual(write(data, player, "supplies", "4", 1L).status(), CampaignSavedData.Status.APPLIED, "repairing storage permits retry");
        }
        helper.succeed();
    }

    private static CampaignSavedData.FactResult write(CampaignSavedData data, UUID player, String fact, String value, long revision) throws IOException {
        return change(data, player, fact, CampaignFacts.Mutation.SET, value, UUID.randomUUID(), revision);
    }

    private static CampaignSavedData.FactResult change(CampaignSavedData data, UUID player, String fact, CampaignFacts.Mutation mutation,
            String value, UUID operation, long revision) throws IOException {
        return data.commitFact(content().definition(), player, fact, mutation, value, operation, revision, 2L);
    }

    private static CampaignContentCatalog.Entry content() {
        var root = JsonParser.parseString("""
                {"schema":"villagerretaliation:campaign/v1","id":"test:facts","revision":1,
                 "title_key":"campaign.facts.title","localization_prefix":"campaign.facts.",
                 "quest_bundles":["test:quest","test:catch_up"],
                 "chapters":[{"id":"opening","title_key":"campaign.facts.title","quests":["test:quest"]}],
                 "milestones":[{"id":"gate","chapter":"opening","quest":"test:quest",
                    "outcomes":[{"id":"opened","title_key":"campaign.facts.title"}],
                    "catch_up":{"quest":"test:catch_up","stage":"learn"}}],
                 "facts":[{"id":"gate_open","scope":"world","kind":"tag"},
                          {"id":"ruler","scope":"world","kind":"variable"},
                          {"id":"supplies","scope":"world","kind":"counter"},
                          {"id":"clue","scope":"player","kind":"tag"},
                          {"id":"theory","scope":"player","kind":"variable"},
                          {"id":"visits","scope":"player","kind":"counter"}]}
                """).getAsJsonObject();
        var parsed = CampaignDefinitionParser.parse(CAMPAIGN, root);
        if (!parsed.valid()) throw new IllegalStateException(parsed.diagnostics().toString());
        return new CampaignContentCatalog.Entry(parsed.definition(), new QuestLocaleCatalog(Map.of("en_us", Map.of(
                "campaign.facts.title", new JsonPrimitive("Story fact fixture")))));
    }

    private static final class Disk implements AutoCloseable {
        private final GameTestHelper helper;
        private final Path root;
        private final Path directory;

        private Disk(GameTestHelper helper) throws IOException {
            this.helper = helper;
            this.root = Files.createTempDirectory("campaign-facts-");
            this.directory = root.resolve("data");
        }

        private Path file() { return directory.resolve(CampaignSavedData.DATA_NAME + ".dat"); }

        private CampaignSavedData open() throws IOException {
            var data = load();
            data.open(content(), 1L);
            return data;
        }

        private CampaignSavedData load() {
            IOUtilities.waitUntilIOWorkerComplete();
            return CampaignSavedData.get(new DimensionDataStorage(directory.toFile(), helper.getLevel().getServer().getFixerUpper(),
                    helper.getLevel().registryAccess()), directory);
        }

        @Override
        public void close() throws IOException {
            IOUtilities.waitUntilIOWorkerComplete();
            Files.deleteIfExists(file());
            Files.deleteIfExists(directory);
            Files.deleteIfExists(root);
        }
    }
}
