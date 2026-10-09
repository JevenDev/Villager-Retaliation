package com.jvn.villagerretaliation.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.dialogue.DialogueTreeResources;
import com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTreeDefinition;
import com.jvn.villagerretaliation.dialogue.resources.DialogueTreeValidation;
import com.jvn.villagerretaliation.dialogue.resources.QuestDialogueCompiler;
import com.jvn.villagerretaliation.quest.schema.QuestResourceEnvelope;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Parser;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_foundation")
@PrefixGameTestTemplate(false)
public final class QuestDialogueCompilationGameTests {
    private QuestDialogueCompilationGameTests() {
    }

    @GameTest(template = "empty", batch = "campaign_dialogue_compile")
    public static void embeddedQuestExchangesKeepSpeakerAndResponseText(GameTestHelper helper) {
        var id = VillagerRetaliation.id("speaker_quest");
        var raw = json("""
                {"schema":"villagerretaliation:quest/v2","id":"villagerretaliation:speaker_quest",
                 "metadata":{"title":"Speakers","description":"A conversation","questline":"fixtures"},
                 "provider":{"type":"villagerretaliation:villager"},"entry_stage":"offer",
                 "stages":[{"id":"offer","objectives":[],"dialogue":{"offer":{"exchanges":[
                   {"speaker":{"type":"role","role":"provider"},"text":"Can you help?"}],
                   "responses":[{"id":"accept","label":"Accept","end":true,"exchanges":[
                     {"speaker":{"type":"player"},"text":"I will help you."}]}]}}}]}
                """);
        var envelope = QuestResourceEnvelope.read(id, raw).orElseThrow();
        var resource = QuestV2Parser.parse(envelope).orElseThrow();
        var tree = QuestDialogueCompiler.compile(resource, envelope).tree(QuestDialogueCompiler.treeId(id)).orElseThrow();
        var node = tree.nodes().values().stream().filter(value -> !value.exchanges().isEmpty()).findFirst().orElseThrow();
        helper.assertValueEqual(node.exchanges().getFirst().text(), "Can you help?", "embedded scene preserves the NPC line");
        helper.assertValueEqual(node.responses().getFirst().exchanges().getFirst().speaker(),
                com.jvn.villagerretaliation.dialogue.conversation.ConversationDefinition.SpeakerKind.PLAYER, "response is player speech");
        helper.assertValueEqual(node.responses().getFirst().label(), "Accept", "button label is independent of spoken text");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_dialogue_compile")
    public static void localizedExchangeVariantsKeepTheirRuntimeKey(GameTestHelper helper) {
        var id = VillagerRetaliation.id("localized_exchange");
        var source = json("""
                {"exchanges":[{"speaker":{"type":"role","role":"provider"},"text":{"key":"#opening"}}]}
                """);
        var variants = JsonParser.parseString("[\"First version.\",\"Second version.\"]");
        var bundle = new com.jvn.villagerretaliation.quest.content.bundle.QuestBundleTransactions.EffectiveBundle(
                com.jvn.villagerretaliation.quest.content.bundle.QuestBundlePath.Owner.shared("fixture"), id, "fixture",
                Map.of(com.jvn.villagerretaliation.quest.content.bundle.QuestBundlePath.Kind.QUEST, Map.of(id, source)), Map.of(),
                new com.jvn.villagerretaliation.quest.content.bundle.QuestLocaleCatalog(Map.of("en_us", Map.of("fixture.opening", variants))), java.util.Set.of());
        var materialized = com.jvn.villagerretaliation.quest.content.bundle.QuestBundleRuntimeMaterializer.materialize(bundle);
        helper.assertTrue(materialized.errors().isEmpty(), "localized exchange materializes");
        var exchanges = com.jvn.villagerretaliation.dialogue.DialogueExchange.read(materialized.quest());
        helper.assertValueEqual(exchanges.getFirst().text(), "First version.", "fallback is a single valid string");
        helper.assertValueEqual(exchanges.getFirst().textKey(), "fixture.opening", "runtime selection retains all localized variants");
        helper.assertValueEqual(bundle.locales().payload("en_us", "fixture.opening").orElseThrow(), variants, "locale alternatives remain intact");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_dialogue_compile")
    public static void typedDialogueMatchesLegacyBoundary(GameTestHelper helper) {
        var location = VillagerRetaliation.id("quests/dialogue_parity.json");
        var raw = json("""
                {"schema":"villagerretaliation:quest/v2","id":"villagerretaliation:dialogue_parity",
                 "metadata":{"title":"Parity","description":"Dialogue fixture","tags":["test"],"questline":"fixtures"},
                 "provider":{"type":"villagerretaliation:villager"},"entry_stage":"offer",
                 "stages":[{"id":"offer","objectives":[{"id":"choose","type":"choice"}],
                   "ui":{"tracker_text":"Choose carefully."},"responses":[
                     {"id":"accept","label":"Accept","label_key":"test.accept","text":"Accepted.",
                      "text_key":"test.accepted","priority":7,"order":4,"stage":"work",
                      "metadata":{"topic":"authored","notes":"source note","tags":["response"],
                        "routing_tags":["quest"],"anti_repeat_groups":["greeting"],"quest":"ignored:quest","stage":"ignored"},
                      "conditions":[{"type":"quest","state":"active"}],
                      "actions":[{"type":"start_scene","scene_id":"villagerretaliation:test_scene",
                         "operation_id":"accepted","wait_for_result":true,"required":true,
                         "lines":{"started":["Watch."]},"line_keys":{"started":["test.watch"]}},
                        {"type":"experience","amount":3,"required":true}]},
                     {"id":"decline","label":"Decline","lines":["Later.","No."],"end":true}]},
                   {"id":"work","objectives":[{"id":"finish","type":"choice"}]}]}
                """);
        var envelope = QuestResourceEnvelope.read(location, raw).orElseThrow();
        var resource = QuestV2Parser.parse(envelope).orElseThrow();
        var treeId = QuestDialogueCompiler.treeId(resource.id());
        var tree = QuestDialogueCompiler.compile(resource, envelope).tree(treeId).orElseThrow();
        var expected = DialogueTreeResources.readGeneratedTree(location, json("""
                {"id":"villagerretaliation:quest_dialogue/dialogue_parity",
                 "display":{"title":"Parity","description":"Dialogue fixture"},
                 "metadata":{"topic":"quest_module_v2","notes":"","quest":"villagerretaliation:dialogue_parity",
                   "questline":"fixtures","tags":["generated","quest_v2","test"]},
                 "entries":[{"id":"stage.offer.responses","label":"Offer","start":"stage.offer.responses",
                   "metadata":{"topic":"quest_module_v2_entry","notes":"/stages/0/responses",
                     "quest":"villagerretaliation:dialogue_parity","questline":"fixtures","stage":"offer",
                     "tags":["generated","quest_v2","test"]}}],
                 "nodes":{"stage.offer.responses":{"lines":["Choose carefully."],"responses":[
                   {"id":"accept","label":"Accept","label_key":"test.accept","lines":["Accepted."],
                    "text_key":"test.accepted","priority":7,"order":4,
                    "metadata":{"topic":"authored","notes":"source note","tags":["generated","quest_v2","test","response"],
                      "routing_tags":["quest"],"anti_repeat_groups":["greeting"],"quest":"villagerretaliation:dialogue_parity",
                      "questline":"fixtures","stage":"offer"},
                    "conditions":[{"type":"quest","state":"active"}],
                    "actions":[{"type":"start_scene","scene_id":"villagerretaliation:test_scene",
                       "operation_id":"accepted","wait_for_result":true,"required":true,
                       "lines":{"started":["Watch."]},"line_keys":{"started":["test.watch"]}},
                      {"type":"quest_transition","quest":"villagerretaliation:dialogue_parity","from_stage":"offer",
                       "scene_path":"stage.offer.responses","response_id":"accept","source_pointer":"/stages/0/responses/0",
                       "target":"stage","target_stage":"work"},
                      {"type":"experience","amount":3,"required":true}]},
                   {"id":"decline","label":"Decline","lines":["Later.","No."],"end":true,
                    "metadata":{"topic":"quest_module_v2_response","notes":"/stages/0/responses/1",
                      "quest":"villagerretaliation:dialogue_parity","questline":"fixtures","stage":"offer",
                      "tags":["generated","quest_v2","test"]}}]}}}
                """), treeId);
        helper.assertValueEqual(tree, expected, "typed compilation retains the complete legacy dialogue contract");
        helper.assertValueEqual(QuestDialogueCompiler.compile(resource, envelope).tree(treeId).orElseThrow(), tree,
                "compilation is deterministic and does not mutate authored content");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_dialogue_compile")
    public static void typedAndJsonTreesShareOptionValidation(GameTestHelper helper) {
        var id = VillagerRetaliation.id("dialogue_validation");
        var valid = new DialogueTreeDefinition.Entry("valid", "Valid", null, "start", null,
                true, true, null, null, null, false, 1);
        var tooLong = new DialogueTreeDefinition.Entry("long", "x".repeat(DialogueOptionDefinition.MAX_NETWORK_LABEL_LENGTH + 1),
                null, "start", null, true, true, null, null, null, false, 0);
        var missing = new DialogueTreeDefinition.Entry("missing", "Missing", null, "absent", null,
                true, true, null, null, null, false, 2);
        var badResponse = new DialogueTreeDefinition.Response("long", tooLong.label(), null, "", null,
                List.of(), List.of(), List.of(), false, 0);
        var goodResponse = new DialogueTreeDefinition.Response("valid", "Valid", null, "", null,
                List.of(), List.of(), List.of(), true, 1);
        var node = new DialogueTreeDefinition.Node("start", List.of(), List.of(), List.of(), List.of(badResponse, goodResponse), false);
        var typed = DialogueTreeValidation.validate(id, new DialogueTreeDefinition(id, "", "", null, List.of(),
                List.of(tooLong, valid, missing), Map.of("start", node)));
        helper.assertValueEqual(typed.entries(), List.of(valid), "invalid entries are removed without renumbering authored order");
        helper.assertValueEqual(typed.nodes().get("start").responses(), List.of(goodResponse),
                "invalid responses are removed without changing valid responses");
        var raw = json("""
                {"entries":[{"id":"long","label":"replace","start":"start"},
                  {"id":"valid","label":"Valid","start":"start"},{"id":"missing","label":"Missing","start":"absent"}],
                 "nodes":{"start":{"responses":[{"id":"long","label":"replace"},
                   {"id":"valid","label":"Valid","end":true}]}}}
                """);
        raw.getAsJsonArray("entries").get(0).getAsJsonObject().addProperty("label", tooLong.label());
        raw.getAsJsonObject("nodes").getAsJsonObject("start").getAsJsonArray("responses").get(0)
                .getAsJsonObject().addProperty("label", tooLong.label());
        helper.assertValueEqual(DialogueTreeResources.readGeneratedTree(id, raw, id), typed,
                "JSON and typed producers enforce the same graph and option limits");
        helper.succeed();
    }

    private static JsonObject json(String value) {
        return JsonParser.parseString(value).getAsJsonObject();
    }
}
