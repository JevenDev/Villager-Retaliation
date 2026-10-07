package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.campaign.content.CampaignDefinition;
import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.campaign.content.CampaignDefinitionParser;
import com.jvn.villagerretaliation.campaign.content.CampaignSchema;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalog;
import com.jvn.villagerretaliation.quest.content.bundle.QuestLocaleCatalog;
import com.google.gson.JsonPrimitive;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_content")
@PrefixGameTestTemplate(false)
public final class CampaignDefinitionGameTests {
    private static final ResourceLocation SOURCE = ResourceLocation.parse("test:campaigns/journey/campaign.json");

    private CampaignDefinitionGameTests() {
    }

    @GameTest(template = "empty", batch = "campaign_definitions")
    public static void typedDefinitionsPreserveOrderAndDetachFromJson(GameTestHelper helper) {
        var raw = fixture();
        var parsed = CampaignDefinitionParser.parse(SOURCE, raw);
        helper.assertTrue(parsed.valid(), "fixture is valid: " + parsed.diagnostics());
        var definition = parsed.definition();
        helper.assertValueEqual(new ArrayList<>(definition.chapters().keySet()), List.of("opening", "aftermath"),
                "chapter ordering remains authored");
        helper.assertValueEqual(new ArrayList<>(definition.milestones().get("dragon").outcomes().keySet()),
                List.of("defeated", "spared"), "outcome ordering remains authored");
        helper.assertValueEqual(definition.facts().get("discovered").scope(), CampaignDefinition.FactScope.PLAYER,
                "personal fact scope remains typed");
        helper.assertValueEqual(definition.facts().get("world_visits").kind(), CampaignDefinition.FactKind.COUNTER,
                "counter kind remains typed");
        helper.assertValueEqual(definition.milestones().get("dragon").catchUp().pointer(), "/milestones/0/catch_up",
                "catch-up retains its source pointer");
        raw.getAsJsonArray("chapters").get(0).getAsJsonObject().addProperty("id", "changed");
        helper.assertTrue(definition.chapters().containsKey("opening"), "compiled data does not retain mutable JSON");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_definitions")
    public static void fingerprintsIgnoreObjectOrderButKeepAuthoredArrayOrder(GameTestHelper helper) {
        var raw = fixture();
        var original = CampaignDefinitionParser.parse(SOURCE, raw).definition();
        var reordered = new JsonObject();
        var entries = new ArrayList<>(raw.entrySet());
        Collections.reverse(entries);
        entries.forEach(entry -> reordered.add(entry.getKey(), entry.getValue().deepCopy()));
        helper.assertValueEqual(CampaignDefinitionParser.parse(SOURCE, reordered).definition().fingerprint(), original.fingerprint(),
                "object formatting does not change content identity");
        var chapters = raw.getAsJsonArray("chapters");
        var first = chapters.remove(0);
        chapters.add(first);
        helper.assertFalse(CampaignDefinitionParser.parse(SOURCE, raw).definition().fingerprint().equals(original.fingerprint()),
                "authored presentation order changes content identity");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_definitions")
    public static void irreversibleMilestonesRequireAuthoredCatchUp(GameTestHelper helper) {
        var raw = fixture();
        milestone(raw).remove("catch_up");
        reject(helper, raw, "/milestones/0/catch_up", "catch-up");
        milestone(raw).addProperty("irreversible", false);
        milestone(raw).remove("observe");
        helper.assertTrue(CampaignDefinitionParser.parse(SOURCE, raw).valid(), "reversible milestones need no catch-up entry");
        milestone(raw).add("observe", JsonParser.parseString("{\"event\":\"minecraft:ender_dragon_defeated\",\"outcome\":\"defeated\"}"));
        reject(helper, raw, "/milestones/0/observe", "irreversible");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_definitions")
    public static void chapterAndMilestoneDependenciesRejectCycles(GameTestHelper helper) {
        var raw = fixture();
        raw.getAsJsonArray("chapters").get(0).getAsJsonObject().add("prerequisites",
                JsonParser.parseString("[{\"chapter\":\"aftermath\"}]"));
        reject(helper, raw, "/chapters/0/prerequisites", "cycle");
        raw.getAsJsonArray("chapters").get(0).getAsJsonObject().add("prerequisites",
                JsonParser.parseString("[{\"milestone\":\"dragon\"}]"));
        reject(helper, raw, "/chapters/0/prerequisites", "cycle");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_definitions")
    public static void prerequisiteTargetsAndOutcomesMustExist(GameTestHelper helper) {
        var raw = fixture();
        var requirement = raw.getAsJsonArray("chapters").get(1).getAsJsonObject()
                .getAsJsonArray("prerequisites").get(0).getAsJsonObject();
        requirement.addProperty("outcome", "missing");
        reject(helper, raw, "/chapters/1/prerequisites/0/outcome", "unknown");
        requirement.addProperty("outcome", "defeated");
        requirement.addProperty("milestone", "missing");
        reject(helper, raw, "/chapters/1/prerequisites/0/milestone", "unknown");
        requirement.addProperty("chapter", "opening");
        reject(helper, raw, "/chapters/1/prerequisites/0", "exactly one");
        requirement.remove("milestone");
        reject(helper, raw, "/chapters/1/prerequisites/0/outcome", "cannot name");
        requirement.remove("outcome");
        helper.assertTrue(CampaignDefinitionParser.parse(SOURCE, raw).valid(), "chapter prerequisites are supported");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_definitions")
    public static void questReferencesMustUseDeclaredBundles(GameTestHelper helper) {
        var raw = fixture();
        milestone(raw).getAsJsonObject("catch_up").addProperty("quest", "test:undeclared");
        reject(helper, raw, "/milestones/0/catch_up/quest", "quest_bundles");
        raw = fixture();
        milestone(raw).addProperty("quest", "test:catch_up");
        reject(helper, raw, "/milestones/0/quest", "belong to its chapter");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_definitions")
    public static void duplicateIdsAndReferencesDoNotOverwriteDefinitions(GameTestHelper helper) {
        var raw = fixture();
        raw.getAsJsonArray("chapters").add(raw.getAsJsonArray("chapters").get(0).deepCopy());
        reject(helper, raw, "/chapters/2/id", "duplicate");
        raw = fixture();
        var outcomes = milestone(raw).getAsJsonArray("outcomes");
        outcomes.add(outcomes.get(0).deepCopy());
        reject(helper, raw, "/milestones/0/outcomes/2/id", "duplicate");
        raw = fixture();
        raw.getAsJsonArray("quest_bundles").add("test:journey");
        reject(helper, raw, "/quest_bundles/2", "duplicate");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_definitions")
    public static void localizationKeysRemainCampaignOwned(GameTestHelper helper) {
        var raw = fixture();
        milestone(raw).getAsJsonArray("outcomes").get(0).getAsJsonObject().addProperty("title_key", "other_campaign.title");
        reject(helper, raw, "/milestones/0/outcomes/0/title_key", "prefix");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_definitions")
    public static void schemaAndRuntimeRejectUnsupportedShapesAtTheSamePointer(GameTestHelper helper) {
        var raw = fixture();
        raw.addProperty("unsupported", true);
        rejectShape(helper, raw, "/unsupported");
        raw = fixture();
        milestone(raw).getAsJsonObject("catch_up").addProperty("unsupported", true);
        rejectShape(helper, raw, "/milestones/0/catch_up/unsupported");
        raw = fixture();
        raw.addProperty("revision", 1.5);
        rejectShape(helper, raw, "/revision");
        raw.addProperty("revision", 2147483648L);
        rejectShape(helper, raw, "/revision");
        raw.addProperty("revision", "1");
        rejectShape(helper, raw, "/revision");
        raw = fixture();
        raw.addProperty("id", "test:journey\n");
        rejectShape(helper, raw, "/id");
        raw = fixture();
        raw.addProperty("title_key", "test.campaign.title\n");
        rejectShape(helper, raw, "/title_key");
        raw = fixture();
        raw.addProperty("schema", "villagerretaliation:campaign/v2");
        rejectShape(helper, raw, "/schema");
        raw = fixture();
        raw.getAsJsonArray("facts").get(0).getAsJsonObject().addProperty("scope", "party");
        rejectShape(helper, raw, "/facts/0/scope");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_definitions")
    public static void externalObservationsRequireSupportedEventsAndNamedOutcomes(GameTestHelper helper) {
        var raw = fixture();
        milestone(raw).getAsJsonObject("observe").addProperty("event", "minecraft:unknown");
        rejectShape(helper, raw, "/milestones/0/observe/event");
        raw = fixture();
        milestone(raw).getAsJsonObject("observe").addProperty("outcome", "missing");
        reject(helper, raw, "/milestones/0/observe/outcome", "does not exist");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_definitions")
    public static void catchUpStagesResolveAgainstCompiledQuestBundles(GameTestHelper helper) {
        var catalog = campaignCatalog(helper);
        var quest = catalog.compiledQuests().stream().filter(value -> !value.entryStage().isBlank() && value.stages().size() > 1)
                .findFirst().orElseThrow();
        var raw = fixture();
        var references = new JsonArray();
        references.add(quest.id().toString());
        raw.add("quest_bundles", references);
        for (var chapter : raw.getAsJsonArray("chapters")) chapter.getAsJsonObject().add("quests", references.deepCopy());
        milestone(raw).addProperty("quest", quest.id().toString());
        var catchUp = milestone(raw).getAsJsonObject("catch_up");
        catchUp.addProperty("quest", quest.id().toString());
        catchUp.addProperty("stage", quest.stages().stream().filter(stage -> !stage.id().equals(quest.entryStage())).findFirst().orElseThrow().id());
        var parsed = CampaignDefinitionParser.parse(SOURCE, raw);
        helper.assertTrue(parsed.valid(), "compiled-reference fixture parses");
        helper.assertTrue(CampaignDefinitionParser.validateQuestReferences(parsed.definition(), catalog).isEmpty(),
                "existing compiled quest and catch-up stage resolve");
        catchUp.addProperty("stage", "missing_campaign_catch_up");
        parsed = CampaignDefinitionParser.parse(SOURCE, raw);
        helper.assertTrue(CampaignDefinitionParser.validateQuestReferences(parsed.definition(), catalog).stream()
                        .anyMatch(diagnostic -> diagnostic.jsonPointer().equals("/milestones/0/catch_up/stage")),
                "missing catch-up stage reports its exact source location");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_definitions")
    public static void catalogRequiresOwnedEnglishTextAndKeepsLocaleFallback(GameTestHelper helper) {
        var quests = campaignCatalog(helper);
        var raw = resolvedFixture(quests);
        var empty = new CampaignContentCatalog.Source(SOURCE, "test_pack", raw, QuestLocaleCatalog.empty(), List.of());
        var rejected = CampaignContentCatalog.compile(quests, List.of(empty));
        helper.assertTrue(rejected.campaigns().isEmpty(), "missing English text blocks only the affected campaign");
        helper.assertTrue(rejected.diagnostics().stream().anyMatch(value -> value.sourcePack().equals("test_pack")
                        && value.jsonPointer().equals("/title_key")), "diagnostics retain source pack and pointer");
        var source = source(raw, SOURCE);
        raw.addProperty("id", "test:mutated_after_read");
        var valid = CampaignContentCatalog.compile(quests, List.of(source));
        helper.assertTrue(valid.diagnostics().isEmpty(), "complete campaign compiles: " + valid.diagnostics());
        var entry = valid.campaigns().get(ResourceLocation.parse("test:journey"));
        helper.assertTrue(entry != null, "source retains its immutable identity");
        helper.assertValueEqual(entry.localization().plainText("fr_fr", "campaign.journey.title").orElseThrow(),
                "Journey", "missing translations fall back to owned English text");
        helper.assertTrue(valid.quests() == quests, "campaign references retain their quest catalog snapshot");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_definitions")
    public static void duplicateCampaignIdsRejectBothSources(GameTestHelper helper) {
        var quests = campaignCatalog(helper);
        var raw = resolvedFixture(quests);
        var result = CampaignContentCatalog.compile(quests, List.of(source(raw, SOURCE),
                source(raw, ResourceLocation.parse("test:campaigns/duplicate/campaign.json"))));
        helper.assertTrue(result.campaigns().isEmpty(), "duplicate identities cannot pick a filesystem winner");
        helper.assertTrue(result.diagnostics().stream().anyMatch(value -> value.jsonPointer().equals("/id")
                && value.message().contains("already defined")), "duplicate identity has a repairable diagnostic");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_definitions")
    public static void overlappingLocalizationOwnershipRejectsBothCampaigns(GameTestHelper helper) {
        var quests = campaignCatalog(helper);
        var raw = resolvedFixture(quests);
        var other = raw.deepCopy();
        other.addProperty("id", "test:other");
        other.addProperty("localization_prefix", "campaign.");
        var result = CampaignContentCatalog.compile(quests, List.of(source(raw, SOURCE),
                source(other, ResourceLocation.parse("test:campaigns/other/campaign.json"))));
        helper.assertTrue(result.campaigns().isEmpty(), "overlapping prefixes cannot steal message ownership");
        helper.assertTrue(result.diagnostics().stream().anyMatch(value -> value.message().contains("locale message belongs")),
                "message ownership conflict is explained");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_definitions")
    public static void campaignCacheTracksQuestReloadsWithoutAddingProductionFixtures(GameTestHelper helper) {
        var first = CampaignResources.current(helper.getLevel().getServer());
        helper.assertTrue(first.campaigns().isEmpty(), "test campaign definitions stay out of production resources");
        helper.assertTrue(CampaignResources.current(helper.getLevel().getServer()) == first, "unchanged catalog is reused");
        QuestContentCatalogs.invalidate();
        var second = CampaignResources.current(helper.getLevel().getServer());
        helper.assertTrue(second != first && second.quests() != first.quests(), "quest reload rebuilds campaign references");
        CampaignResources.clearCache();
        helper.assertTrue(CampaignResources.current(helper.getLevel().getServer()) != second, "server cache can be cleared");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_definitions")
    public static void playableCampaignQuestsRequireExplicitOwnership(GameTestHelper helper) {
        var catalog = campaignCatalog(helper);
        var raw = resolvedFixture(catalog);
        var definition = CampaignDefinitionParser.parse(SOURCE, raw).definition();
        helper.assertTrue(CampaignDefinitionParser.validateQuestReferences(definition, catalog).isEmpty(), "explicit campaign ownership is valid");
        var ordinary = QuestContentCatalogs.current(helper.getLevel().getServer());
        helper.assertTrue(CampaignDefinitionParser.validateQuestReferences(definition, ordinary).stream()
                        .anyMatch(value -> value.message().contains("metadata.campaign")),
                "referencing an ordinary quest cannot silently convert it into campaign content");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_definitions")
    public static void milestoneAndCatchUpEntriesMustResolveToDistinctAuthoredStages(GameTestHelper helper) {
        var catalog = campaignCatalog(helper);
        var raw = resolvedFixture(catalog);
        milestone(raw).addProperty("stage", "missing_stage");
        var definition = CampaignDefinitionParser.parse(SOURCE, raw).definition();
        helper.assertTrue(CampaignDefinitionParser.validateQuestReferences(definition, catalog).stream()
                        .anyMatch(value -> value.jsonPointer().equals("/milestones/0/stage")), "unknown original stage is rejected");
        milestone(raw).addProperty("stage", milestone(raw).getAsJsonObject("catch_up").get("stage").getAsString());
        definition = CampaignDefinitionParser.parse(SOURCE, raw).definition();
        helper.assertTrue(CampaignDefinitionParser.validateQuestReferences(definition, catalog).stream()
                        .anyMatch(value -> value.message().contains("alternate entry stage")), "catch-up cannot replay its original entry stage");
        helper.succeed();
    }

    private static QuestContentCatalog campaignCatalog(GameTestHelper helper) {
        var original = QuestContentCatalogs.current(helper.getLevel().getServer());
        var compiled = new java.util.LinkedHashMap<ResourceLocation, com.jvn.villagerretaliation.quest.compiled.CompiledQuest>();
        var definitions = new java.util.LinkedHashMap<ResourceLocation, com.jvn.villagerretaliation.quest.QuestDefinition>();
        for (var quest : original.compiledQuests()) {
            var metadata = quest.metadata();
            var owned = new com.jvn.villagerretaliation.quest.compiled.CompiledQuest(quest.id(), quest.source(), null,
                    new com.jvn.villagerretaliation.quest.compiled.CompiledQuestMetadata(metadata.title(), metadata.description(),
                            metadata.titleKey(), metadata.descriptionKey(), metadata.questline(), metadata.tags(), metadata.parent(),
                            metadata.dialogue(), ResourceLocation.parse("test:journey")),
                    quest.provider(), quest.target(), quest.entryStage(), quest.prerequisites(), quest.rules(), quest.ui(),
                    quest.objectives(), quest.objectivesById(), quest.stages(), quest.stagesById(), quest.triggers(), quest.triggersByEvent(),
                    quest.triggerIndex(), quest.rewards(), quest.showLockedAdventureHint(), quest.revision());
            compiled.put(owned.id(), owned);
            definitions.put(owned.id(), owned.asQuestDefinition());
        }
        return new QuestContentCatalog(original.generation(), new com.jvn.villagerretaliation.quest.compiled.CompiledQuestCatalog(compiled),
                original.dialogueCatalog(), definitions, original.objectiveEventQuestIds(), original.factQuestIds(),
                original.memoryEventQuestIds(), original.exclusiveGroupQuestIds(), original.triggerEventQuestIds(), original.scenes(),
                original.encounters(), original.pools(), original.bundles(), original.localization(), original.rewards());
    }

    private static JsonObject resolvedFixture(QuestContentCatalog catalog) {
        var quest = catalog.compiledQuests().stream().filter(value -> !value.entryStage().isBlank() && value.stages().size() > 1).findFirst().orElseThrow();
        var raw = fixture();
        var references = new JsonArray();
        references.add(quest.id().toString());
        raw.add("quest_bundles", references);
        for (var chapter : raw.getAsJsonArray("chapters")) chapter.getAsJsonObject().add("quests", references.deepCopy());
        milestone(raw).addProperty("quest", quest.id().toString());
        milestone(raw).getAsJsonObject("catch_up").addProperty("quest", quest.id().toString());
        milestone(raw).getAsJsonObject("catch_up").addProperty("stage",
                quest.stages().stream().filter(stage -> !stage.id().equals(quest.entryStage())).findFirst().orElseThrow().id());
        return raw;
    }

    private static CampaignContentCatalog.Source source(JsonObject root, ResourceLocation location) {
        var locales = new QuestLocaleCatalog(Map.of("en_us", Map.of(
                "campaign.journey.title", new JsonPrimitive("Journey"),
                "campaign.journey.opening", new JsonPrimitive("Opening"),
                "campaign.journey.aftermath", new JsonPrimitive("Aftermath"),
                "campaign.journey.defeated", new JsonPrimitive("Defeated"),
                "campaign.journey.spared", new JsonPrimitive("Spared"))));
        return new CampaignContentCatalog.Source(location, "test_pack", root, locales, List.of());
    }

    private static void rejectShape(GameTestHelper helper, JsonObject raw, String pointer) {
        helper.assertTrue(CampaignSchema.validate(SOURCE, raw).stream().anyMatch(value -> value.jsonPointer().equals(pointer)),
                "schema rejects " + pointer);
        reject(helper, raw, pointer, "");
    }

    private static void reject(GameTestHelper helper, JsonObject raw, String pointer, String message) {
        var parsed = CampaignDefinitionParser.parse(SOURCE, raw);
        helper.assertFalse(parsed.valid(), "invalid campaign cannot compile");
        helper.assertTrue(parsed.diagnostics().stream().anyMatch(value -> value.resourceId().equals(SOURCE)
                        && value.jsonPointer().equals(pointer) && value.message().contains(message)),
                "diagnostic retains source and pointer " + pointer + ": " + parsed.diagnostics());
    }

    private static JsonObject milestone(JsonObject raw) {
        return raw.getAsJsonArray("milestones").get(0).getAsJsonObject();
    }

    private static JsonObject fixture() {
        return JsonParser.parseString("""
                {"schema":"villagerretaliation:campaign/v1","id":"test:journey","revision":1,
                 "title_key":"campaign.journey.title","localization_prefix":"campaign.journey.",
                 "quest_bundles":["test:journey","test:catch_up"],
                 "chapters":[
                  {"id":"opening","title_key":"campaign.journey.opening","quests":["test:journey"]},
                  {"id":"aftermath","title_key":"campaign.journey.aftermath","quests":["test:journey"],
                   "prerequisites":[{"milestone":"dragon","outcome":"defeated"}]}],
                 "milestones":[{"id":"dragon","chapter":"opening","quest":"test:journey",
                  "outcomes":[{"id":"defeated","title_key":"campaign.journey.defeated"},
                              {"id":"spared","title_key":"campaign.journey.spared"}],
                  "catch_up":{"quest":"test:catch_up","stage":"learn"},
                  "observe":{"event":"minecraft:ender_dragon_defeated","outcome":"defeated"}}],
                 "facts":[{"id":"discovered","scope":"player","kind":"tag"},
                          {"id":"world_visits","scope":"world","kind":"counter"}]}
                """).getAsJsonObject();
    }
}
