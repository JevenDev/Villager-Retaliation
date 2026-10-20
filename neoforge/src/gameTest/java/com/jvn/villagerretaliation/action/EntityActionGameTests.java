package com.jvn.villagerretaliation.action;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.EntityDialogueContext;
import com.jvn.villagerretaliation.quest.QuestScopeKey;
import com.jvn.villagerretaliation.quest.VillagerQuestFacts;
import com.jvn.villagerretaliation.reputation.runtime.EntityReputationSavedData;
import com.jvn.villagerretaliation.reputation.runtime.ReputationProfile;
import com.jvn.villagerretaliation.reputation.runtime.ReputationService;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("narrative_actions")
@PrefixGameTestTemplate(false)
public final class EntityActionGameTests {
    private EntityActionGameTests() { }

    @GameTest(template = "empty")
    public static void commonActionsAndConditionsUseConfiguredMobServices(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var wolf = helper.spawn(EntityType.WOLF, 1, 1, 1);
        wolf.setNoAi(true);
        var facts = new VillagerQuestFacts();
        var reputation = new ReputationService<>(new EntityReputationSavedData(),
                new ReputationProfile<>("neutral", List.of(new ReputationProfile.Tier<>("friend", 10, true))));
        var runtime = NarrativeActions.common().withReputation(reputation).withFacts(facts, null);
        var context = new EntityDialogueContext(helper.getLevel(), player, wolf, "en_us", "test:pack", runtime);
        var actions = actions("""
                {"actions":[{"type":"experience","amount":5},{"type":"reputation","amount":12},
                  {"type":"set_tag","scope":"villager","tag":"test:known"},
                  {"type":"set_variable","scope":"village","key":"greeting","value":"hello {player}"},
                  {"type":"counter","scope":"player","key":"visits","amount":2}]}
                """);
        int experience = player.totalExperience;
        var result = ActionSequenceRunner.run(context, actions, 0, Map.of(), () -> { }, null, null);
        helper.assertTrue(result.outcome().success(), "common actions execute for a non-villager speaker");
        helper.assertValueEqual(player.totalExperience, experience + 5, "experience reaches the player");
        helper.assertValueEqual(reputation.score(wolf.getUUID(), player.getUUID()), 12, "reputation reaches the configured entity service");
        helper.assertTrue(facts.hasTag(QuestScopeKey.entity(wolf.getUUID()).asString(), ResourceLocation.parse("test:known")),
                "entity-scoped facts use the speaker identity");
        helper.assertValueEqual(facts.variable(QuestScopeKey.group("test:pack").asString(), "greeting").orElseThrow(),
                "hello " + player.getName().getString(), "group facts use shared placeholders");
        helper.assertTrue(DialogueCondition.matchesAll(context, List.of(new DialogueCondition.Reputation(java.util.Set.of(), 10, 20))),
                "numeric reputation conditions read the same configured service");
        helper.assertTrue(DialogueCondition.matchesAll(context, List.of(new DialogueCondition.QuestFact(
                com.jvn.villagerretaliation.quest.QuestFactScope.VILLAGER, null, java.util.Set.of(ResourceLocation.parse("test:known")),
                "", java.util.Set.of(), null, null))), "fact conditions read the same configured store");
        var loaded = VillagerQuestFacts.load(facts.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertValueEqual(loaded.counter(QuestScopeKey.player(player.getUUID()).asString(), "visits"), 2, "generic facts survive saving");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void missingEntityServicesFailClosedAndStopRequiredSequences(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var cow = helper.spawn(EntityType.COW, 1, 1, 1);
        var context = new EntityDialogueContext(helper.getLevel(), player, cow, "en_us", "");
        int experience = player.totalExperience;
        var result = ActionSequenceRunner.run(context, actions("""
                {"actions":[{"type":"reputation","amount":4,"required":true},{"type":"experience","amount":7}]}
                """), 0, Map.of(), () -> { }, null, null);
        helper.assertValueEqual(result.outcome().status(), ActionStatus.UNAVAILABLE, "unregistered services remain unavailable");
        helper.assertValueEqual(player.totalExperience, experience, "a required unavailable action prevents later effects");
        helper.assertFalse(DialogueCondition.matchesAll(context, List.of(new DialogueCondition.Not(
                new DialogueCondition.Reputation(java.util.Set.of(), 1, null)))), "missing reputation cannot satisfy a negated condition");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void questStageFactsRequireTheQuestRuntimeAndGenericRegistrationsAreImmutable(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var cow = helper.spawn(EntityType.COW, 1, 1, 1);
        var facts = new VillagerQuestFacts();
        var configured = NarrativeActions.common().withFacts(facts, null);
        var context = new EntityDialogueContext(helper.getLevel(), player, cow, "en_us", "", configured);
        var action = actions("""
                {"type":"set_variable","scope":"quest","quest":"test:quest","key":"stage","value":"finished"}
                """).getFirst();
        helper.assertValueEqual(configured.execute(context, action, Map.of()).status(), ActionStatus.UNAVAILABLE,
                "generic fact writes cannot bypass authored quest transitions");
        helper.assertTrue(facts.variable(QuestScopeKey.quest(player.getUUID(), ResourceLocation.parse("test:quest")).asString(), "stage").isEmpty(),
                "blocked stage writes do not change facts");
        var custom = configured.with(VillagerActionDefinition.Kind.REPUTATION, (runtime, effect, replacements) ->
                ActionResult.success(VillagerActionResult.success(), java.util.Set.of()));
        var reputation = actions("{\"type\":\"reputation\",\"amount\":1}").getFirst();
        helper.assertTrue(custom.execute(context, reputation, Map.of()).success(), "consumers can provide entity-specific handlers");
        helper.assertValueEqual(configured.execute(context, reputation, Map.of()).status(), ActionStatus.UNAVAILABLE,
                "registration does not mutate a different runtime");
        helper.succeed();
    }

    private static List<VillagerActionDefinition> actions(String json) {
        return VillagerActionDefinition.readListOrInline(ResourceLocation.parse("test:entity_actions"), "actions",
                JsonParser.parseString(json).getAsJsonObject());
    }
}
