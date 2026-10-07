package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignDefinitionParser;
import com.jvn.villagerretaliation.quest.content.bundle.QuestLocaleCatalog;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
public final class CampaignProgressionGameTests {
    private static final ResourceLocation CAMPAIGN = ResourceLocation.parse("test:progression");
    private static final ResourceLocation QUEST = ResourceLocation.parse("test:quest");
    private static final ResourceLocation CATCH_UP = ResourceLocation.parse("test:catch_up");

    private CampaignProgressionGameTests() {
    }

    @GameTest(template = "empty", batch = "campaign_progression")
    public static void enteringAMilestonePersistsProgressWithoutParticipation(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk(helper)) {
            var content = content(definition());
            var data = disk.load();
            data.open(content, 1L);
            UUID player = UUID.randomUUID();
            var entry = data.enterMilestone(content.definition(), player, "dragon", 2L);
            helper.assertValueEqual(entry.status(), CampaignSavedData.Status.APPLIED, "first entry persists");
            helper.assertValueEqual(entry.entry().route(), CampaignInstance.Route.ORIGINAL, "unresolved world uses the original path");
            helper.assertValueEqual(entry.entry().quest(), QUEST, "entry references the authored quest");
            var restored = disk.load().instances().get(CAMPAIGN).players().get(player);
            helper.assertTrue(restored.participation().isEmpty(), "acceptance is not participation");
            helper.assertTrue(restored.discoveredOutcomes().isEmpty(), "unresolved outcomes are not invented");
            helper.assertFalse(restored.milestones().get("dragon").completed(), "entry does not complete the milestone");
            helper.assertValueEqual(data.enterMilestone(content.definition(), player, "dragon", 3L).status(),
                    CampaignSavedData.Status.ALREADY_SATISFIED, "duplicate entry is idempotent");
            helper.assertValueEqual(data.instances().get(CAMPAIGN).players().get(player).revision(), 1L, "duplicate does not advance personal revision");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_progression")
    public static void sharedOutcomesReconcileActiveAbsentAndLatePlayers(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk(helper)) {
            var content = content(definition());
            var data = disk.load();
            data.open(content, 1L);
            UUID participant = UUID.randomUUID();
            UUID absent = UUID.randomUUID();
            UUID late = UUID.randomUUID();
            data.enterMilestone(content.definition(), participant, "dragon", 2L);
            data.enterMilestone(content.definition(), absent, "dragon", 3L);
            commit(data, content, "defeated", Set.of(participant));
            var restored = disk.load();
            var instance = restored.instances().get(CAMPAIGN);
            helper.assertValueEqual(CampaignProgression.entry(content.definition(), instance, participant, "dragon").route(),
                    CampaignInstance.Route.ORIGINAL, "participant continues their original quest");
            var alternate = CampaignProgression.entry(content.definition(), instance, absent, "dragon");
            helper.assertValueEqual(alternate.route(), CampaignInstance.Route.CATCH_UP, "offline active player receives catch-up durably");
            helper.assertValueEqual(alternate.quest(), CATCH_UP, "catch-up uses the authored alternate quest");
            helper.assertValueEqual(alternate.stage(), "learn", "catch-up uses the authored alternate stage");
            helper.assertFalse(instance.players().containsKey(late), "an unreached player is not silently advanced");
            var lateEntry = restored.enterMilestone(content.definition(), late, "dragon", 9L).entry();
            helper.assertValueEqual(lateEntry.worldOperation(), alternate.worldOperation(), "late player reconciles the same committed event");
            helper.assertValueEqual(lateEntry.route(), CampaignInstance.Route.CATCH_UP, "late player receives the alternate path");
            helper.assertTrue(restored.instances().get(CAMPAIGN).players().get(late).participation().isEmpty(), "late entry grants no participation");
            helper.assertTrue(restored.instances().get(CAMPAIGN).players().get(absent).participation().isEmpty(), "active absence grants no participation");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_progression")
    public static void catchUpCompletionUnlocksPersonalChaptersWithoutWorldEffects(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk(helper)) {
            var content = content(definition());
            var data = disk.load();
            data.open(content, 1L);
            commit(data, content, "defeated", Set.of());
            UUID late = UUID.randomUUID();
            var before = data.instances().get(CAMPAIGN);
            helper.assertFalse(CampaignProgression.chapter(content.definition(), before, late, "aftermath").available(),
                    "world outcome alone does not unlock personal progression");
            data.enterMilestone(content.definition(), late, "dragon", 3L);
            var done = data.completeMilestone(content.definition(), late, "dragon", CampaignInstance.Route.CATCH_UP,
                    "defeated", UUID.randomUUID(), 4L);
            helper.assertValueEqual(done.status(), CampaignSavedData.Status.APPLIED, "authored catch-up completion advances personal progress");
            var after = disk.load().instances().get(CAMPAIGN);
            helper.assertTrue(CampaignProgression.chapter(content.definition(), after, late, "aftermath").available(),
                    "completed catch-up unlocks the matching outcome chapter");
            helper.assertFalse(CampaignProgression.chapter(content.definition(), after, late, "mercy").available(),
                    "a different outcome branch stays unavailable");
            helper.assertValueEqual(after.outcomes(), before.outcomes(), "catch-up preserves all shared outcome receipts");
            helper.assertValueEqual(after.worldRevision(), before.worldRevision(), "catch-up never advances the shared revision");
            helper.assertTrue(after.players().get(late).participation().isEmpty(), "catch-up completion does not award participant credit");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_progression")
    public static void chapterCompletionRequiresItsQuestsAndMilestones(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk(helper)) {
            var content = content(definition());
            var data = disk.load();
            data.open(content, 1L);
            UUID player = UUID.randomUUID();
            data.enterMilestone(content.definition(), player, "dragon", 2L);
            data.completeQuest(content.definition(), player, QUEST);
            helper.assertFalse(CampaignProgression.chapter(content.definition(), data.instances().get(CAMPAIGN), player, "legacy").available(),
                    "quest completion alone cannot skip a required milestone");
            commit(data, content, "defeated", Set.of(player));
            data.completeMilestone(content.definition(), player, "dragon", CampaignInstance.Route.ORIGINAL, "defeated", UUID.randomUUID(), 4L);
            var access = CampaignProgression.chapter(content.definition(), disk.load().instances().get(CAMPAIGN), player, "opening");
            helper.assertTrue(access.completed(), "chapter completion combines personal quest and milestone progress");
            helper.assertTrue(CampaignProgression.chapter(content.definition(), data.instances().get(CAMPAIGN), player, "legacy").available(),
                    "completed chapter unlocks its dependent chapter");
            helper.assertFalse(data.completeQuest(content.definition(), player, QUEST), "repeated quest completion is idempotent");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_progression")
    public static void staleOriginalCompletionCannotCompleteAssignedCatchUp(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk(helper)) {
            var content = content(definition());
            var data = disk.load();
            data.open(content, 1L);
            UUID player = UUID.randomUUID();
            data.enterMilestone(content.definition(), player, "dragon", 2L);
            helper.assertValueEqual(data.completeMilestone(content.definition(), player, "dragon", CampaignInstance.Route.ORIGINAL,
                    "defeated", UUID.randomUUID(), 3L).status(), CampaignSavedData.Status.UNAVAILABLE, "irreversible personal progress requires a world outcome");
            commit(data, content, "defeated", Set.of());
            helper.assertValueEqual(data.completeMilestone(content.definition(), player, "dragon", CampaignInstance.Route.ORIGINAL,
                    "defeated", UUID.randomUUID(), 4L).status(), CampaignSavedData.Status.STALE, "old path completion cannot skip catch-up");
            helper.assertValueEqual(data.completeMilestone(content.definition(), player, "dragon", CampaignInstance.Route.CATCH_UP,
                    "spared", UUID.randomUUID(), 4L).status(), CampaignSavedData.Status.UNAVAILABLE, "catch-up cannot select a conflicting world outcome");
            helper.assertFalse(data.instances().get(CAMPAIGN).players().get(player).milestones().get("dragon").completed(),
                    "rejected completions have no personal effects");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_progression")
    public static void duplicateCompletionAfterReloadPreservesItsReceipt(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk(helper)) {
            var content = content(definition());
            var data = disk.load();
            data.open(content, 1L);
            commit(data, content, "defeated", Set.of());
            UUID player = UUID.randomUUID();
            UUID operation = UUID.randomUUID();
            data.enterMilestone(content.definition(), player, "dragon", 2L);
            data.completeMilestone(content.definition(), player, "dragon", CampaignInstance.Route.CATCH_UP, "defeated", operation, 3L);
            var restored = disk.load();
            var before = restored.instances().get(CAMPAIGN);
            var result = restored.completeMilestone(content.definition(), player, "dragon", CampaignInstance.Route.CATCH_UP,
                    "spared", UUID.randomUUID(), 4L);
            helper.assertValueEqual(result.status(), CampaignSavedData.Status.ALREADY_SATISFIED, "duplicate returns saved progress");
            helper.assertValueEqual(restored.instances().get(CAMPAIGN), before, "duplicate cannot rewrite a completion");
            helper.assertValueEqual(before.players().get(player).milestones().get("dragon").completionOperation(), operation, "first completion receipt survives reload");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_progression")
    public static void reversibleOutcomesRemainPersonal(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk(helper)) {
            var raw = definition();
            var milestone = raw.getAsJsonArray("milestones").get(0).getAsJsonObject();
            milestone.addProperty("irreversible", false);
            milestone.remove("observe");
            var content = content(raw);
            var data = disk.load();
            data.open(content, 1L);
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            for (UUID player : Set.of(first, second)) data.enterMilestone(content.definition(), player, "dragon", 2L);
            data.completeMilestone(content.definition(), first, "dragon", CampaignInstance.Route.ORIGINAL, "defeated", UUID.randomUUID(), 3L);
            data.completeMilestone(content.definition(), second, "dragon", CampaignInstance.Route.ORIGINAL, "spared", UUID.randomUUID(), 3L);
            var restored = disk.load().instances().get(CAMPAIGN);
            helper.assertTrue(restored.outcomes().isEmpty(), "personal outcomes do not create shared commits");
            helper.assertTrue(CampaignProgression.chapter(content.definition(), restored, first, "aftermath").available(), "first player follows their outcome");
            helper.assertTrue(CampaignProgression.chapter(content.definition(), restored, second, "mercy").available(), "second player follows a different outcome");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_progression")
    public static void oldPlayerRecordsMigrateWithoutInventingMilestoneCompletion(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk(helper)) {
            var content = content(definition());
            var data = disk.load();
            data.open(content, 1L);
            UUID player = UUID.randomUUID();
            commit(data, content, "defeated", Set.of(player));
            var old = data.save(new CompoundTag(), helper.getLevel().registryAccess());
            old.putInt("DataVersion", 1);
            var priorPlayer = old.getList("Campaigns", Tag.TAG_COMPOUND).getCompound(0).getList("Players", Tag.TAG_COMPOUND).getCompound(0);
            priorPlayer.remove("Milestones");
            priorPlayer.remove("CompletedQuests");
            var restored = CampaignSavedData.load(old, disk.directory);
            helper.assertTrue(restored.diagnostic().isBlank(), "version one migrates successfully");
            var progress = restored.instances().get(CAMPAIGN).players().get(player);
            helper.assertValueEqual(progress.participation(), Set.of("dragon"), "migration retains actual participation");
            helper.assertValueEqual(progress.discoveredOutcomes(), Map.of("dragon", "defeated"), "migration retains discoveries");
            helper.assertTrue(progress.milestones().isEmpty() && progress.completedQuests().isEmpty(), "participation is not personal quest completion");
            helper.assertFalse(priorPlayer.contains("Milestones"), "migration does not mutate the input save");
            helper.assertValueEqual(restored.save(new CompoundTag(), helper.getLevel().registryAccess()).getInt("DataVersion"), 2, "migrated state uses version two");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_progression")
    public static void blockedAndUnknownEntriesLeaveStateUntouched(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk(helper)) {
            var raw = definition();
            var copy = raw.getAsJsonArray("milestones").get(0).deepCopy().getAsJsonObject();
            copy.addProperty("id", "later");
            copy.addProperty("chapter", "aftermath");
            copy.addProperty("quest", "test:epilogue");
            raw.getAsJsonArray("milestones").add(copy);
            var content = content(raw);
            var data = disk.load();
            var before = data.open(content, 1L);
            UUID player = UUID.randomUUID();
            var blocked = data.enterMilestone(content.definition(), player, "later", 2L);
            helper.assertValueEqual(blocked.status(), CampaignSavedData.Status.UNAVAILABLE, "locked chapter prevents milestone entry");
            helper.assertTrue(blocked.blockers().stream().anyMatch(value -> value.contains("dragon")), "blocked path identifies its dependency");
            helper.assertValueEqual(data.enterMilestone(content.definition(), player, "missing", 2L).status(), CampaignSavedData.Status.INVALID_REQUEST,
                    "unknown milestone is rejected");
            helper.assertValueEqual(data.instances().get(CAMPAIGN), before, "unavailable paths create no player state");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_progression")
    public static void completingTheAlternateQuestSatisfiesPersonalChapterPrerequisites(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk(helper)) {
            var content = content(definition());
            var data = disk.load();
            data.open(content, 1L);
            commit(data, content, "defeated", Set.of());
            UUID player = UUID.randomUUID();
            data.enterMilestone(content.definition(), player, "dragon", 2L);
            data.completeMilestone(content.definition(), player, "dragon", CampaignInstance.Route.CATCH_UP, "defeated", UUID.randomUUID(), 4L);
            helper.assertFalse(CampaignProgression.chapter(content.definition(), data.instances().get(CAMPAIGN), player, "legacy").available(),
                    "milestone completion alone does not skip the rest of the alternate quest");
            var before = data.instances().get(CAMPAIGN);
            data.completeQuest(content.definition(), player, CATCH_UP);
            var restored = disk.load().instances().get(CAMPAIGN);
            helper.assertTrue(CampaignProgression.chapter(content.definition(), restored, player, "legacy").available(),
                    "completed alternate quest satisfies the original personal chapter requirement");
            helper.assertValueEqual(restored.players().get(player).completedQuests(), Set.of(QUEST, CATCH_UP),
                    "personal progress records the alternate quest and the original requirement it fulfills");
            helper.assertTrue(restored.players().get(player).participation().isEmpty(), "personal equivalence never grants participation");
            helper.assertValueEqual(restored.outcomes(), before.outcomes(), "personal equivalence preserves world receipts");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_progression")
    public static void questCompletionBeforeTheCatchUpMarkerReconcilesInEitherOrder(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk(helper)) {
            var content = content(definition());
            var data = disk.load();
            data.open(content, 1L);
            commit(data, content, "defeated", Set.of());
            UUID player = UUID.randomUUID();
            data.enterMilestone(content.definition(), player, "dragon", 2L);
            data.completeQuest(content.definition(), player, CATCH_UP);
            helper.assertFalse(data.instances().get(CAMPAIGN).players().get(player).completedQuests().contains(QUEST),
                    "alternate quest completion does not bypass its milestone marker");
            data.completeMilestone(content.definition(), player, "dragon", CampaignInstance.Route.CATCH_UP, "defeated", UUID.randomUUID(), 4L);
            helper.assertTrue(disk.load().instances().get(CAMPAIGN).players().get(player).completedQuests().contains(QUEST),
                    "the later milestone marker reconciles an already completed alternate quest");
        }
        helper.succeed();
    }

    private static void commit(CampaignSavedData data, CampaignContentCatalog.Entry content, String outcome, Set<UUID> participants) throws IOException {
        var result = data.commitWorldOutcome(content, "dragon", outcome, UUID.randomUUID(), 0L,
                CampaignInstance.Origin.PLAYER_DECISION, participants, 3L);
        if (result.status() != CampaignSavedData.Status.APPLIED) throw new IllegalStateException(result.toString());
    }

    private static CampaignContentCatalog.Entry content(JsonObject raw) {
        var result = CampaignDefinitionParser.parse(CAMPAIGN, raw);
        if (!result.valid()) throw new IllegalStateException(result.diagnostics().toString());
        return new CampaignContentCatalog.Entry(result.definition(), new QuestLocaleCatalog(Map.of("en_us", Map.of(
                "campaign.progress.title", new JsonPrimitive("Progression fixture"),
                "campaign.progress.chapter", new JsonPrimitive("Chapter"),
                "campaign.progress.defeated", new JsonPrimitive("Defeated"),
                "campaign.progress.spared", new JsonPrimitive("Spared")))));
    }

    private static JsonObject definition() {
        return JsonParser.parseString("""
                {"schema":"villagerretaliation:campaign/v1","id":"test:progression","revision":1,
                 "title_key":"campaign.progress.title","localization_prefix":"campaign.progress.",
                 "quest_bundles":["test:quest","test:catch_up","test:epilogue"],
                 "chapters":[
                  {"id":"opening","title_key":"campaign.progress.chapter","quests":["test:quest"]},
                  {"id":"aftermath","title_key":"campaign.progress.chapter","quests":["test:epilogue"],"prerequisites":[{"milestone":"dragon","outcome":"defeated"}]},
                  {"id":"mercy","title_key":"campaign.progress.chapter","quests":["test:epilogue"],"prerequisites":[{"milestone":"dragon","outcome":"spared"}]},
                  {"id":"legacy","title_key":"campaign.progress.chapter","quests":["test:epilogue"],"prerequisites":[{"chapter":"opening"}]}],
                 "milestones":[{"id":"dragon","chapter":"opening","quest":"test:quest",
                  "outcomes":[{"id":"defeated","title_key":"campaign.progress.defeated"},{"id":"spared","title_key":"campaign.progress.spared"}],
                  "catch_up":{"quest":"test:catch_up","stage":"learn"},
                  "observe":{"event":"minecraft:ender_dragon_defeated","outcome":"defeated"}}]}
                """).getAsJsonObject();
    }

    private static final class Disk implements AutoCloseable {
        private final GameTestHelper helper;
        private final Path root;
        private final Path directory;

        private Disk(GameTestHelper helper) throws IOException {
            this.helper = helper;
            this.root = Files.createTempDirectory("campaign-progression-");
            this.directory = this.root.resolve("data");
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
            Files.deleteIfExists(this.directory.resolve(CampaignSavedData.DATA_NAME + ".dat"));
            Files.deleteIfExists(this.directory);
            Files.deleteIfExists(this.root);
        }
    }
}
