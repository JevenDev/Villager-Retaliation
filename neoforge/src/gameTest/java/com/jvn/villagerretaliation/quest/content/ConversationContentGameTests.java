package com.jvn.villagerretaliation.quest.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.campaign.content.CampaignDefinitionParser;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationCompiler;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationDefinition;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationResources;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSchema;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleDiscovery;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundlePath;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleTransactions;
import com.jvn.villagerretaliation.quest.content.bundle.QuestLocaleCatalog;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Schema;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_content")
@PrefixGameTestTemplate(false)
public final class ConversationContentGameTests {
    private static final String BUNDLE = "quests/campaign_fixture/characters/";
    private static final String PREFIX = "campaign_fixture.quest.characters";
    private static final ResourceLocation ID = id("mentor_conversation");
    private static final QuestBundlePath.Owner OWNER = QuestBundlePath.Owner.quest("campaign_fixture", "campaign_fixture", "characters");

    private ConversationContentGameTests() {
    }

    @GameTest(template = "empty", batch = "conversation_content")
    public static void sharedAuthoringCasesMatchTheRuntimeContract(GameTestHelper helper) throws IOException {
        var campaign = CampaignDefinitionParser.parse(id("campaigns/characters/campaign.json"), resource("campaigns/characters/campaign.json")).definition();
        for (var entry : read("conversation_cases.json").getAsJsonArray()) {
            var test = entry.getAsJsonObject();
            var graph = graph();
            for (var patch : test.getAsJsonArray("patches")) patch(graph, patch.getAsJsonObject());
            var result = ConversationCompiler.compile(bundle(graph), ID);
            var issues = new ArrayList<>(result.diagnostics());
            if (result.valid()) issues.addAll(ConversationCompiler.validateCampaign(result.definition(), campaign));
            boolean valid = test.has("valid") && test.get("valid").getAsBoolean();
            helper.assertValueEqual(issues.isEmpty(), valid, test.get("name").getAsString() + ": " + issues);
            if (!valid) helper.assertTrue(issues.stream().anyMatch(issue -> issue.jsonPointer().equals(test.get("pointer").getAsString())),
                    test.get("name") + " preserves its source pointer: " + issues);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_content")
    public static void typedDefinitionsPreserveSpeakersOrderingAndEffectIdentity(GameTestHelper helper) throws IOException {
        var raw = graph();
        var parsed = ConversationCompiler.compile(bundle(raw), ID);
        helper.assertTrue(parsed.valid(), "authored fixture compiles: " + parsed.diagnostics());
        var definition = parsed.definition();
        helper.assertValueEqual(new ArrayList<>(definition.nodes().keySet()), List.of("welcome", "agreed", "declined"), "node order remains authored");
        var welcome = definition.nodes().get("welcome");
        helper.assertValueEqual(welcome.exchanges().stream().map(exchange -> exchange.speaker().kind()).toList(),
                List.of(ConversationDefinition.SpeakerKind.ROLE, ConversationDefinition.SpeakerKind.PLAYER, ConversationDefinition.SpeakerKind.NARRATOR),
                "ordered speaker changes are explicit");
        helper.assertValueEqual(new ArrayList<>(welcome.responses().keySet()), List.of("appeal", "leave"), "choice order remains authored");
        helper.assertValueEqual(definition.checks().get("convince").difficulty(), 10, "default difficulty is ten");
        var effect = definition.nodes().get("agreed").effects().getFirst();
        helper.assertValueEqual(effect.id(), "record_answer", "effect has an authored identity");
        helper.assertTrue(effect.action().required(), "required failure remains authoritative");
        helper.assertValueEqual(effect.action().kind(), VillagerActionDefinition.Kind.NOTIFICATION, "effect compiles into the shared action type");
        helper.assertValueEqual(effect.action().text(), "The mentor agreed to help.", "legacy action adapter resolves English once");
        helper.assertValueEqual(welcome.exchanges().getFirst().textKey(), PREFIX + ".conversation.welcome", "graph text remains a localized reference");
        raw.getAsJsonArray("nodes").remove(0);
        helper.assertTrue(definition.nodes().containsKey("welcome"), "compiled graph is detached from JSON");
        boolean immutable = false;
        try { definition.nodes().clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        helper.assertTrue(immutable, "published graph maps are immutable");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_content")
    public static void fingerprintsSeparateStructureFromTranslationAndSourceLocation(GameTestHelper helper) throws IOException {
        var raw = graph();
        var original = ConversationCompiler.compile(bundle(raw), ID).definition();
        var reversed = new JsonObject();
        var fields = new ArrayList<>(raw.entrySet());
        Collections.reverse(fields);
        fields.forEach(entry -> reversed.add(entry.getKey(), entry.getValue()));
        helper.assertValueEqual(ConversationCompiler.compile(bundle(reversed), ID).definition().fingerprint(), original.fingerprint(), "object order is cosmetic");
        var localized = bundle(raw);
        localized = localized.withLocales(localized.locales().overlay("en_us", Map.of(PREFIX + ".conversation.title", JsonParser.parseString("\"Translated title\""))));
        helper.assertValueEqual(ConversationCompiler.compile(localized, ID).definition().fingerprint(), original.fingerprint(), "translations do not change structural identity");
        var exchanges = raw.getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonArray("exchanges");
        exchanges.add(exchanges.remove(0));
        helper.assertFalse(ConversationCompiler.compile(bundle(raw), ID).definition().fingerprint().equals(original.fingerprint()), "changing exchange order changes structure");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_content")
    public static void sourcesAndLocaleOverlaysStayWithTheirDefinition(GameTestHelper helper) throws IOException {
        var resources = resources(graph());
        var override = graph();
        override.addProperty("revision", 2);
        resources.add(raw(1, "override_pack", BUNDLE + "conversations/renamed_file.json", override));
        var locale = new JsonObject();
        locale.addProperty(PREFIX + ".conversation.title", "Le conseil du mentor");
        resources.add(raw(2, "translation_pack", BUNDLE + "locales/fr_fr.json", locale));
        var compiled = QuestBundleTransactions.compile(resources, QuestBundleTransactions.CompatibilityRules.empty());
        helper.assertTrue(compiled.diagnostics().isEmpty(), "bundle override compiles: " + compiled.diagnostics());
        var definition = ConversationCompiler.compile(compiled.bundles().get(OWNER), ID).definition();
        helper.assertValueEqual(definition.revision(), 2, "latest accepted definition wins");
        helper.assertValueEqual(definition.source().resource(), id(BUNDLE + "conversations/renamed_file.json"), "source is its actual file, not an inferred stable-id filename");
        helper.assertValueEqual(definition.source().pack(), "override_pack", "translation overlays do not replace structural ownership");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_content")
    public static void invalidConversationOverrideRollsBackWithItsOwningBundle(GameTestHelper helper) throws IOException {
        var server = helper.getLevel().getServer();
        var held = QuestContentCatalogs.current(server);
        var resources = new ArrayList<>(QuestBundleDiscovery.discover(server));
        resources.addAll(resources(graph()));
        var invalid = graph();
        invalid.getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonArray("responses").get(1).getAsJsonObject().addProperty("next", "missing");
        resources.add(raw(1000, "bad_conversation_override", BUNDLE + "conversations/mentor.json", invalid));
        var result = QuestContentCatalogs.build(server, resources);
        helper.assertTrue(result.publishable(), "bad optional override retains a valid catalog: " + result.report().entries());
        helper.assertTrue(result.catalog().conversation(ID).isPresent(), "lower conversation is retained in the same catalog as its quest");
        helper.assertValueEqual(result.catalog().conversation(ID).orElseThrow().source().pack(), "conversation_fixture", "rollback restores lower structural source");
        helper.assertTrue(result.report().entries().stream().anyMatch(issue -> issue.message().contains("/nodes/0/responses/1/next") && issue.message().contains("missing")),
                "rollback report names the failed graph edge");
        helper.assertTrue(QuestContentCatalogs.current(server) == held, "candidate compilation does not mutate the live generation");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_content")
    public static void sharedTextIsAvailableWithoutChangingPrivateMessageOwnership(GameTestHelper helper) throws IOException {
        var graph = graph();
        graph.add("title", JsonParser.parseString("{\"key\":\"campaign_fixture.shared.conversation_title\"}"));
        var resources = resources(graph);
        var locale = new JsonObject();
        locale.addProperty("campaign_fixture.shared.conversation_title", "Shared title");
        resources.add(raw(0, "conversation_fixture", "quests/_shared/locales/en_us.json", locale));
        var bundles = QuestBundleTransactions.compile(resources, QuestBundleTransactions.CompatibilityRules.empty());
        helper.assertTrue(bundles.diagnostics().isEmpty(), "shared text passes bundle ownership: " + bundles.diagnostics());
        var compiled = ConversationResources.compile(bundles, Set.of(), Set.of(id("characters")));
        helper.assertTrue(compiled.diagnostics().isEmpty(), "shared text reaches compilation: " + compiled.diagnostics());
        helper.assertValueEqual(compiled.conversations().get(ID).titleKey(), "campaign_fixture.shared.conversation_title", "shared key remains stable");
        helper.assertFalse(bundles.bundles().get(OWNER).locales().messages("en_us").containsKey("campaign_fixture.shared.conversation_title"),
                "compiler lookup does not transfer ownership to the private bundle");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_content")
    public static void sharedConversationsCannotReachPrivateScenes(GameTestHelper helper) throws IOException {
        var resources = resources(graph());
        resources.add(raw(0, "conversation_fixture", BUNDLE + "scenes/character_greeting.json", resource(BUNDLE + "scenes/character_greeting.json")));
        var shared = graph();
        shared.addProperty("id", "campaign_fixture:shared_conversation");
        shared.addProperty("quest", "campaign_fixture:characters");
        expandSharedText(shared);
        shared.getAsJsonArray("nodes").get(1).getAsJsonObject().getAsJsonArray("effects").get(0).getAsJsonObject().add("action", JsonParser.parseString("""
                {"type":"start_scene","scene_id":"campaign_fixture:character_greeting","operation_id":"greet"}
                """));
        var english = new JsonObject();
        locale().getAsJsonObject("messages").entrySet().forEach(entry -> english.add(entry.getKey().replace(PREFIX, "campaign_fixture.shared"), entry.getValue()));
        resources.add(raw(0, "conversation_fixture", "quests/_shared/locales/en_us.json", english));
        resources.add(raw(0, "conversation_fixture", "quests/_shared/conversations/mentor.json", shared));
        var result = QuestBundleTransactions.compile(resources, QuestBundleTransactions.CompatibilityRules.empty());
        helper.assertTrue(result.diagnostics().stream().anyMatch(issue -> issue.code().equals("ownership") && issue.message().contains("private SCENE")),
                "shared graphs cannot borrow a private scene: " + result.diagnostics());
        helper.assertTrue(result.bundles().containsKey(OWNER), "private owning bundle is preserved");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_content")
    public static void typedSceneReferencesRespectOwnershipForEverySpelling(GameTestHelper helper) throws IOException {
        var owner = QuestBundlePath.Owner.shared("campaign_fixture");
        var english = new LinkedHashMap<String, JsonElement>();
        locale().getAsJsonObject("messages").entrySet().forEach(entry -> english.put(entry.getKey().replace(PREFIX, "campaign_fixture.shared"), entry.getValue()));
        var sceneId = id("character_greeting");
        var privateScene = new QuestBundleTransactions.EffectiveBundle(OWNER, id("characters"), PREFIX,
                Map.of(QuestBundlePath.Kind.SCENE, Map.of(sceneId, resource(BUNDLE + "scenes/character_greeting.json"))),
                Map.of(), QuestLocaleCatalog.empty(), Set.of());
        for (String type : List.of("start_scene", "scene", "scene_start")) {
            for (String field : List.of("scene", "scene_id", "start_scene")) {
                for (String reference : List.of(sceneId.toString(), sceneId.getPath())) {
                    var graph = graph();
                    graph.addProperty("quest", "campaign_fixture:characters");
                    expandSharedText(graph);
                    var action = new JsonObject();
                    action.addProperty("type", type);
                    action.addProperty(field, reference);
                    action.addProperty("scene_operation_id", "greeting");
                    graph.getAsJsonArray("nodes").get(1).getAsJsonObject().getAsJsonArray("effects").get(0).getAsJsonObject().add("action", action);
                    var shared = new QuestBundleTransactions.EffectiveBundle(owner, null, "",
                            Map.of(QuestBundlePath.Kind.CONVERSATION, Map.of(ID, graph)), Map.of(),
                            new QuestLocaleCatalog(Map.of("en_us", english)), Set.of());
                    var bundles = new QuestBundleTransactions.Result(Map.of(owner, shared, OWNER, privateScene), List.of());
                    var result = ConversationResources.compile(bundles, Set.of(sceneId), Set.of(id("characters")));
                    helper.assertTrue(result.conversations().isEmpty(), "typed ownership rejects " + type + "/" + field + "/" + reference);
                    helper.assertTrue(result.diagnostics().get(owner).stream().anyMatch(issue -> issue.message().contains("private scene")),
                            "resolved identity enforces ownership for " + type + "/" + field + "/" + reference);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_content")
    public static void sceneAndQuestReferencesMustResolveBeforePublication(GameTestHelper helper) throws IOException {
        var graph = graph();
        graph.getAsJsonArray("nodes").get(1).getAsJsonObject().getAsJsonArray("effects").get(0).getAsJsonObject().add("action", JsonParser.parseString("""
                {"type":"start_scene","scene_id":"campaign_fixture:missing_scene","operation_id":"greet","wait_for_result":true,"required":true}
                """));
        var bundles = new QuestBundleTransactions.Result(Map.of(OWNER, bundle(graph)), List.of());
        var result = ConversationResources.compile(bundles, Set.of(), Set.of(id("characters")));
        helper.assertTrue(result.conversations().isEmpty(), "unresolved scene rejects the conversation");
        helper.assertTrue(result.diagnostics().get(OWNER).stream().anyMatch(issue -> issue.jsonPointer().equals("/nodes/1/effects/0/action/scene_id")), "missing scene has an actionable pointer");
        result = ConversationResources.compile(bundles, Set.of(id("missing_scene")), Set.of(id("characters")));
        helper.assertTrue(result.conversations().containsKey(ID), "available scene compiles");
        var action = result.conversations().get(ID).nodes().get("agreed").effects().getFirst().action();
        helper.assertTrue(action.required() && action.waitForScene(), "scene suspension and required failure survive compilation");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_content")
    public static void schemasShareRegisteredConditionsAndActions(GameTestHelper helper) {
        var conversation = ConversationSchema.export().getAsJsonObject("$defs");
        var quests = QuestV2Schema.export().getAsJsonObject("$defs");
        for (String name : List.of("condition", "action", "localized_reference")) helper.assertValueEqual(conversation.get(name), quests.get(name), "shared schema for " + name);
        helper.succeed();
    }

    private static void patch(JsonObject root, JsonObject patch) {
        String[] tokens = patch.get("path").getAsString().substring(1).split("/");
        JsonElement target = root;
        for (int index = 0; index < tokens.length - 1; index++) target = target.isJsonArray() ? target.getAsJsonArray().get(Integer.parseInt(tokens[index])) : target.getAsJsonObject().get(tokens[index]);
        String key = tokens[tokens.length - 1];
        if (patch.has("remove")) target.getAsJsonObject().remove(key);
        else target.getAsJsonObject().add(key, patch.get("value").deepCopy());
    }

    private static void expandSharedText(JsonElement value) {
        if (value.isJsonArray()) value.getAsJsonArray().forEach(ConversationContentGameTests::expandSharedText);
        else if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            if (object.has("key")) object.addProperty("key", "campaign_fixture.shared." + object.get("key").getAsString().substring(1));
            else object.entrySet().forEach(entry -> expandSharedText(entry.getValue()));
        }
    }

    private static QuestBundleTransactions.EffectiveBundle bundle(JsonObject graph) throws IOException {
        var locale = QuestLocaleCatalog.read(locale());
        return new QuestBundleTransactions.EffectiveBundle(OWNER, id("characters"), PREFIX,
                Map.of(QuestBundlePath.Kind.CONVERSATION, Map.of(ID, graph)), Map.of(),
                new QuestLocaleCatalog(Map.of("en_us", locale.messages())), Set.of(),
                Map.of(new QuestBundleTransactions.DefinitionKey(QuestBundlePath.Kind.CONVERSATION, ID),
                        new QuestBundleTransactions.DefinitionSource(id(BUNDLE + "conversations/mentor.json"), "conversation_fixture")));
    }

    private static ArrayList<QuestBundleTransactions.RawResource> resources(JsonObject graph) throws IOException {
        return new ArrayList<>(List.of(raw(0, "conversation_fixture", BUNDLE + "quest.json", resource(BUNDLE + "quest.json")),
                raw(0, "conversation_fixture", BUNDLE + "locales/en_us.json", locale()),
                raw(0, "conversation_fixture", BUNDLE + "conversations/mentor.json", graph)));
    }

    private static QuestBundleTransactions.RawResource raw(int layer, String pack, String path, JsonObject root) {
        return QuestBundleTransactions.RawResource.valid(layer, pack, id(path), root);
    }

    private static JsonObject graph() throws IOException { return resource(BUNDLE + "conversations/mentor.json"); }
    private static JsonObject locale() throws IOException { return resource(BUNDLE + "locales/en_us.json"); }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("campaign_fixture", path); }
    private static JsonObject resource(String path) throws IOException { return read("data/campaign_fixture/" + path).getAsJsonObject(); }

    private static JsonElement read(String path) throws IOException {
        try (var stream = ConversationContentGameTests.class.getResourceAsStream("/campaign_fixture/" + path)) {
            if (stream == null) throw new IOException("Missing conversation fixture " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }
}
