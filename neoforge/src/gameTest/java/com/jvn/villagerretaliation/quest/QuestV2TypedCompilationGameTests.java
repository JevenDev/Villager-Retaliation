package com.jvn.villagerretaliation.quest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuest;
import com.jvn.villagerretaliation.quest.schema.QuestResourceEnvelope;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Parser;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_foundation")
@PrefixGameTestTemplate(false)
public final class QuestV2TypedCompilationGameTests {
    private QuestV2TypedCompilationGameTests() {
    }

    @GameTest(template = "empty", batch = "campaign_typed_quest")
    public static void typedCompilationMatchesLegacyQuestContract(GameTestHelper helper) {
        var root = fixture();
        CompiledQuest compiled = compile(root);
        var legacy = VillagerQuestResources.readCanonicalQuest(VillagerRetaliation.id("quests/typed_parity.json"), json("""
                {"id":"villagerretaliation:typed_parity","display":{"title":"Typed parity","description":"Fixture"},
                 "questline":"fixtures","tags":["test"],"parent":"villagerretaliation:prior",
                 "prerequisites":["villagerretaliation:prior"],"revision":3,
                 "migration":{"active_policy":"reset_stage","stage_aliases":{"old":"offer"}},"show_locked_adventure_hint":false,
                 "offer":{"professions":["farmer"],"min_villager_level":2,"weight":5},
                 "rules":{"repeatable":true,"completion_cooldown_seconds":2},
                 "tracker":{"title":"Journal","steps":{"choice":{"text":"Choose."},"fact":{"text":"Choose."},
                   "inactive":{"text":"Choose."},"work":{"text":"Bring wheat."}}},
                 "entry_stage":"offer","objectives":[
                   {"id":"offer.choose","type":"choice","choice":"choose","quest":"villagerretaliation:typed_parity"},
                   {"id":"work.wheat","type":"item_check","item":"minecraft:wheat","count":2}],
                 "stages":{"offer":{"objectives":["offer.choose"],"complete_when":["offer.choose"],
                   "completion_mode":"any","next":"work","metadata":{"journal":"decision"},
                   "entry_actions":[{"type":"experience","amount":1},{"type":"experience","amount":2,"required":true},
                     {"type":"experience","amount":3},{"type":"experience","amount":4}],
                   "exit_actions":[{"type":"experience","amount":5},{"type":"experience","amount":6}],
                   "bonuses":[{"id":"extra","when":["offer.choose"],"actions":[{"type":"experience","amount":2}]}]},
                   "work":{"objectives":["work.wheat"],"complete_when":["work.wheat"],
                     "entry_actions":[{"type":"experience","amount":1}],"exit_actions":[{"type":"experience","amount":6}]}},
                 "triggers":[{"id":"work.progress","event":"progress","stages":["work"],
                     "cooldown_ticks":12,"priority":2,"weight":3,"chance":0.5,"exclusive":true,
                     "actions":[{"type":"experience","amount":1}]},
                   {"id":"lifecycle.on_start","event":"started","actions":[{"type":"experience","amount":7}]},
                   {"id":"root","event":"player_tick","stages":["work"],"actions":[{"type":"tracker"}]}],
                 "rewards":{"experience":8,"reputation":1,"gossip_reputation":3}}
                """), compiled.id());
        helper.assertValueEqual(compiled.asQuestDefinition(), legacy,
                "typed compilation preserves the full legacy quest boundary contract");
        helper.assertValueEqual(compiled.stages().stream().map(stage -> stage.id()).toList(), List.of("offer", "work"),
                "explicit entry stage retains canonical runtime ordering");
        helper.assertValueEqual(compiled.stagesById().get("offer").source().jsonPointer(), "/stages/1",
                "runtime ordering retains authored stage source pointers");
        helper.assertValueEqual(compiled.objectivesById().get("work.wheat").source().jsonPointer(), "/stages/0/objectives/0",
                "objective qualification retains its authored source pointer");
        helper.assertValueEqual(compiled.triggers().getFirst().source().jsonPointer(), "/stages/0/events/0",
                "event source pointers retain authored order");
        helper.assertValueEqual(compile(root).asQuestDefinition(), legacy, "repeated compilation does not mutate the resource");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_typed_quest")
    public static void typedLifecycleTransitionsPreserveActionDefaults(GameTestHelper helper) {
        var root = fixture();
        root.getAsJsonObject("lifecycle").add("on_start", json("{\"stage\":\"work\"}"));
        var compiled = compile(root);
        var trigger = compiled.triggers().stream().filter(value -> value.id().equals("lifecycle.on_start")).findFirst().orElseThrow();
        var expected = VillagerActionDefinition.readList(VillagerRetaliation.id("fixture"), "fixture", json("""
                {"actions":[{"type":"set_variable","quest":"villagerretaliation:typed_parity","scope":"quest","stage":"work","flash_tracker":true}]}
                """), compiled.id());
        helper.assertValueEqual(trigger.definition().actions(), expected, "generated lifecycle transitions retain every action default");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_typed_quest")
    public static void rewardActionsCanCancelScalarReputation(GameTestHelper helper) {
        var root = fixture();
        root.add("rewards", json("""
                {"reputation":4,"gossip_reputation":-3,"actions":[
                  {"type":"reputation","amount":-4},{"type":"gossip","amount":3}]}
                """));
        var rewards = compile(root).rewards().definition();
        helper.assertValueEqual(rewards.reputation(), 0, "zero totals replace the original scalar reward");
        helper.assertValueEqual(rewards.gossipReputation(), 0, "zero gossip totals are not dropped during compilation");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_typed_quest")
    public static void qualifiedObjectiveCollisionsFailWithDiagnostics(GameTestHelper helper) {
        var root = json("""
                {"schema":"villagerretaliation:quest/v2","id":"villagerretaliation:qualified_collision",
                 "provider":{"type":"villagerretaliation:villager"},"entry_stage":"a",
                 "stages":[{"id":"a","next":"a.b","objectives":[{"id":"b.c","type":"choice"}]},
                   {"id":"a.b","objectives":[{"id":"c","type":"choice"}]}]}
                """);
        var envelope = QuestResourceEnvelope.read(VillagerRetaliation.id("quests/qualified_collision.json"), root).orElseThrow();
        var resource = QuestV2Parser.parse(envelope).orElseThrow();
        helper.assertTrue(QuestV2Compiler.compile(resource, envelope).isEmpty(),
                "different authored objectives cannot silently share one qualified runtime identity");
        helper.assertTrue(DatapackDiagnostics.recent().stream().anyMatch(entry ->
                        entry.message().contains("duplicate compiled objective id a.b.c")),
                "a rejected collision identifies the conflicting runtime ID");
        helper.succeed();
    }

    private static CompiledQuest compile(JsonObject root) {
        var envelope = QuestResourceEnvelope.read(VillagerRetaliation.id("quests/typed_parity.json"), root).orElseThrow();
        return QuestV2Compiler.compile(QuestV2Parser.parse(envelope).orElseThrow(), envelope).orElseThrow();
    }

    private static JsonObject fixture() {
        return json("""
                {"schema":"villagerretaliation:quest/v2","id":"villagerretaliation:typed_parity",
                 "metadata":{"title":"Typed parity","description":"Fixture","questline":"fixtures","tags":["TEST"],
                   "revision":3,"migration":{"active_policy":"reset_stage","stage_aliases":{"old":"offer"}},
                   "show_locked_adventure_hint":false},
                 "provider":{"type":"villagerretaliation:villager","filters":{"professions":["farmer"],"min_villager_level":2}},
                 "availability":{"repeatable":true,"cooldown_seconds":2,"weight":5,"prerequisites":["villagerretaliation:prior"]},
                 "entry_stage":"offer","ui":{"title":"Journal","tracker_text":"Choose."},
                 "lifecycle":{"on_start":{"actions":[{"type":"experience","amount":7}]},
                   "on_stage_enter":{"actions":[{"type":"experience","amount":1}]},
                   "on_stage_exit":{"actions":[{"type":"experience","amount":6}]}},
                 "stages":[{"id":"work","objectives":[{"id":"wheat","type":"item_check","target":{"item":"minecraft:wheat"},"count":2}],
                   "ui":{"tracker_text":"Bring wheat."},"events":[{"id":"progress","event":"progress","cooldown_ticks":12,
                     "priority":2,"weight":3,"chance":0.5,"exclusive":true,"actions":[{"type":"experience","amount":1}]}]},
                   {"id":"offer","objectives":[{"id":"choose","type":"choice"}],"complete_when":{"objectives":["choose"]},
                    "completion":{"mode":"any"},"next":"work","metadata":{"journal":"decision"},
                    "entry_actions":[{"type":"experience","amount":2,"required":true}],"exit_actions":[{"type":"experience","amount":5}],
                    "rewards":{"experience":4,"actions":[{"type":"experience","amount":3}]},
                    "bonuses":[{"id":"extra","when":{"objective":"choose"},"actions":[{"type":"experience","amount":2}]}]}],
                 "events":[{"id":"root","event":"player_tick","stages":["work"],"actions":[{"type":"tracker"}]}],
                 "rewards":{"experience":5,"reputation":2,"gossip_reputation":3,
                   "actions":[{"type":"experience","amount":3},{"type":"reputation","amount":-1}]}}
                """);
    }

    private static JsonObject json(String raw) {
        return JsonParser.parseString(raw).getAsJsonObject();
    }
}
