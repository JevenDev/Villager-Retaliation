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

    @GameTest(template = "empty", batch = "campaign_typed_quest")
    public static void availabilityKeepsBranchOverridesAndLifecycleRules(GameTestHelper helper) {
        var root = fixture();
        root.add("availability", json("""
                {"repeatable":true,"locked_to_villager":false,"cross_villager_compatible":true,
                 "max_starts":5,"max_completions":3,"completion_scope":"world",
                 "cooldown_ticks":7,"completion_cooldown_days":2,"prerequisite_cooldown_seconds":3,
                 "abandonment":"cooldown","abandonment_cooldown_seconds":4,
                 "consume_on_completion":true,"consume_on_abandonment":true,
                 "max_active_quests":2,"max_active_by_tag":{"story":1},
                 "active":{"conditions":[{"type":"dimension","dimension":"minecraft:overworld"}],
                   "hide_when_unmet":true,"pause_progress_when_unmet":false},
                 "expiration":{"after_seconds":6,"consume":true,"allow_repickup":false,"notify":false},
                 "exclusive_group":"villagerretaliation:outer","exclusive_on":"completed",
                 "blocks_on_start":["villagerretaliation:outer_start"],
                 "branch":{"exclusive_group":"villagerretaliation:inner","exclusive_on":"started",
                   "blocks_on_start":["villagerretaliation:inner_start"],
                   "blocks_on_completion":["villagerretaliation:inner_finish"],
                   "blocks":["villagerretaliation:always_blocked"]}}
                """));
        var expected = VillagerQuestResources.readRules(VillagerRetaliation.id("quests/typed_parity.json"), json("""
                {"rules":{"repeatable":true,"locked_to_villager":false,"cross_villager_compatible":true,
                 "max_starts":5,"max_completions":3,"completion_scope":"world",
                 "completion_cooldown_ticks":7,"prerequisite_cooldown_ticks":60,
                 "abandonment":"cooldown","abandonment_cooldown_ticks":80,
                 "consume_on_completion":true,"consume_on_abandonment":true,
                 "max_active_quests":2,"max_active_by_tag":{"story":1},
                 "active":{"conditions":[{"type":"dimension","dimension":"minecraft:overworld"}],
                   "hide_when_unmet":true,"pause_progress_when_unmet":false},
                 "expiration":{"after_ticks":120,"consume":true,"allow_repickup":false,"notify":false},
                 "branch":{"exclusive_group":"villagerretaliation:outer","exclusive_on":"completed",
                   "blocks_on_start":["villagerretaliation:outer_start"],
                   "blocks_on_completion":["villagerretaliation:inner_finish","villagerretaliation:always_blocked"]}}}
                """), VillagerRetaliation.id("typed_parity"));
        helper.assertValueEqual(compile(root).rules(), expected, "v2 availability keeps its complete legacy execution contract");
        var availability = root.getAsJsonObject("availability");
        availability.addProperty("completion_cooldown_ticks", 9);
        helper.assertValueEqual(compile(root).rules().completionCooldownTicks(), 9L, "explicit ticks override the tick alias");
        availability.remove("completion_cooldown_ticks");
        availability.remove("cooldown_ticks");
        availability.addProperty("cooldown_days", 3);
        helper.assertValueEqual(compile(root).rules().completionCooldownTicks(), 48000L, "explicit days override the day alias");
        availability.remove("completion_cooldown_days");
        availability.remove("cooldown_days");
        availability.addProperty("cooldown_seconds", 4);
        availability.addProperty("completion_cooldown_seconds", 6);
        helper.assertValueEqual(compile(root).rules().completionCooldownTicks(), 120L, "explicit seconds override the second alias");
        availability.addProperty("cooldown_ticks", 0);
        helper.assertValueEqual(compile(root).rules().completionCooldownTicks(), 0L, "zero ticks still override longer-unit values");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_typed_quest")
    public static void providerFiltersAndTargetsKeepTheirBoundaryContract(GameTestHelper helper) {
        var root = fixture();
        root.getAsJsonObject("provider").getAsJsonObject("filters").add("skills", json("{\"farming\":{\"min\":3}}"));
        root.getAsJsonObject("availability").addProperty("selection_weight", 11);
        root.getAsJsonObject("availability").add("conditions", JsonParser.parseString("""
                [{"type":"dimension","dimension":"minecraft:overworld"}]
                """));
        root.add("target", json("""
                {"structure":"minecraft:village_plains","dimension":"minecraft:overworld",
                 "pieces":["minecraft:plains/houses/plains_small_house_1"],
                 "search_radius":64,"discovery_radius":12,"proof_item":"minecraft:wheat"}
                """));
        var compiled = compile(root);
        var expected = VillagerQuestResources.readOffer(VillagerRetaliation.id("quests/typed_parity.json"), json("""
                {"offer":{"professions":["farmer"],"min_villager_level":2,"skills":{"farming":{"min":3}},
                 "weight":5,"conditions":[{"type":"dimension","dimension":"minecraft:overworld"}]}}
                """), compiled.id());
        helper.assertValueEqual(compiled.provider().offer(), expected, "provider filters, authored conditions, and primary weight survive compilation");
        helper.assertValueEqual(compiled.target(), VillagerQuestResources.readTarget(root,
                com.jvn.villagerretaliation.util.item.ItemStackPredicateParser.DEFAULT_REGISTRIES),
                "target selectors and proof predicates keep the legacy boundary contract");
        root.getAsJsonObject("provider").remove("filters");
        helper.assertValueEqual(compile(root).provider().offer().conditions(), expected.conditions(), "missing provider filters do not drop availability conditions");
        helper.assertValueEqual(compile(root).provider().offer().weight(), 5, "missing provider filters do not drop selection weight");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_typed_quest")
    public static void nestedObjectiveSelectorsAndTrackerDefaultsMatchLegacyFields(GameTestHelper helper) {
        var root = fixture();
        root.getAsJsonArray("stages").get(0).getAsJsonObject().getAsJsonArray("objectives").set(0, json("""
                {"id":"wheat","type":"item_check","item":"minecraft:stone_sword","count":1,
                 "dimension":"minecraft:overworld","custom_data":{"origin":"root"},
                 "conditions":[{"type":"dimension","dimension":"minecraft:overworld"}],
                 "location":{"dimension":"minecraft:the_nether","x":1,"y":70,"z":3},
                 "target":{"item":"minecraft:iron_sword","count":2,"optional":true,"min_durability":12,
                   "custom_data":{"origin":"quest"},"components":{"minecraft:custom_name":"\\\"Quest sword\\\""},
                   "conditions":[{"type":"dimension","dimension":"minecraft:the_end"}],
                   "location":{"dimension":"minecraft:the_end","x":4,"y":80,"z":8,"radius":9},
                   "tracker":{"text":"","show_progress":false,"metadata":{"source":"tracker"}}},
                 "ui":{"tracker_text":"Find {item}","tracker_complete_text":"Found it",
                   "show_progress":true,"progress":0.3,"metadata":{"source":"ui"}}}
                """));
        var before = root.deepCopy();
        var compiled = compile(root);
        var expected = VillagerQuestResources.readObjective(VillagerRetaliation.id("quests/typed_parity.json"), json("""
                {"id":"work.wheat","type":"item_check","item":"minecraft:iron_sword","count":2,"optional":true,
                 "dimension":"minecraft:the_end","x":4,"y":80,"z":8,"radius":9,"min_durability":12,
                 "custom_data":{"origin":"quest"},"components":{"minecraft:custom_name":"\\\"Quest sword\\\""},
                 "conditions":[{"type":"dimension","dimension":"minecraft:the_end"}],
                 "tracker":{"text":"Find {item}","complete_text":"Found it","show_progress":false,
                   "progress":0.3,"metadata":{"source":"tracker"}}}
                """), 1, compiled.id(), com.jvn.villagerretaliation.util.item.ItemStackPredicateParser.DEFAULT_REGISTRIES).orElseThrow();
        helper.assertValueEqual(compiled.objectivesById().get("work.wheat").definition(), expected,
                "nested selectors, item predicates, optional flags, and tracker defaults retain their authored precedence");
        helper.assertValueEqual(root, before, "compilation does not mutate any authored objective or nested predicate");
        helper.assertValueEqual(compile(root).objectivesById().get("work.wheat").definition(), expected, "repeated objective compilation is stable");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_typed_quest")
    public static void choiceDefaultsAndNestedCampaignValidationKeepTheirContracts(GameTestHelper helper) {
        var root = fixture();
        var choice = root.getAsJsonArray("stages").get(1).getAsJsonObject().getAsJsonArray("objectives").get(0).getAsJsonObject();
        choice.addProperty("quest_id", "other:story");
        var initial = compile(root);
        var objective = initial.objectivesById().get("offer.choose").definition();
        helper.assertValueEqual(objective.factQuestId(), initial.id(), "v2 fact defaults retain the owning quest before legacy aliases");
        helper.assertValueEqual(objective.factKey(), "choice", "a bare choice receives its runtime key");
        helper.assertValueEqual(objective.factValues(), java.util.Set.of("choose"), "a bare choice retains its local authored value");
        choice.add("target", json("""
                {"scope":"player","quest":"other:story","key":"verdict","value":"mercy"}
                """));
        objective = compile(root).objectivesById().get("offer.choose").definition();
        helper.assertValueEqual(objective.factQuestId(), net.minecraft.resources.ResourceLocation.parse("other:story"), "explicit nested fact ownership is retained");
        helper.assertValueEqual(objective.factScope(), QuestFactScope.PLAYER, "explicit personal scope survives compilation");
        helper.assertValueEqual(objective.factKey(), "verdict", "explicit fact keys suppress the choice default");
        helper.assertValueEqual(objective.factValues(), java.util.Set.of("mercy"), "explicit values suppress the choice default");
        choice.add("target", json("""
                {"story":{"evidence":"ledger"}}
                """));
        var envelope = QuestResourceEnvelope.read(VillagerRetaliation.id("quests/typed_parity.json"), root).orElseThrow();
        var resource = QuestV2Parser.parse(envelope).orElseThrow();
        helper.assertTrue(QuestV2Compiler.compile(resource, envelope).isEmpty(),
                "a nested campaign story cannot bypass descriptor validation by using an ordinary objective type");
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
