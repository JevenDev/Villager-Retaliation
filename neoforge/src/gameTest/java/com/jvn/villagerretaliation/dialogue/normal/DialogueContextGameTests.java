package com.jvn.villagerretaliation.dialogue.normal;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome;
import com.jvn.villagerretaliation.dialogue.conditions.ConditionEvaluator;
import com.jvn.villagerretaliation.dialogue.resources.DialogueContextResources;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.quest.conditions.VillagerConditionAccess;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("dialogue_authoring")
@PrefixGameTestTemplate(false)
public final class DialogueContextGameTests {
    private DialogueContextGameTests() { }

    @GameTest(template = "empty", batch = "dialogue_contexts")
    public static void healthAndNamedContextsUseLiveStateAndInclusiveBounds(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), player, villager);
        var condition = new DialogueCondition.ContextRule(ResourceLocation.parse("villagerretaliation:injured_speaker"));
        villager.setHealth(villager.getMaxHealth() / 2);
        helper.assertTrue(condition.matches(context), "half health is included");
        villager.setHealth(villager.getMaxHealth() / 2 + 1);
        helper.assertFalse(condition.matches(context), "health is evaluated again, not cached");
        player.setHealth(player.getMaxHealth() / 4);
        var playerOnly = VillagerConditionAccess.forPlayer(player);
        helper.assertValueEqual(ConditionEvaluator.evaluate(playerOnly, null, new DialogueCondition.Health("player", 0.25, 0.25)),
                ConditionOutcome.MATCHED, "inclusive player bounds without a provider");
        helper.assertValueEqual(ConditionEvaluator.evaluate(playerOnly, null, new DialogueCondition.Not(condition)),
                ConditionOutcome.UNKNOWN, "negated speaker context stays unknown without a speaker");
        helper.assertValueEqual(ConditionEvaluator.evaluate(context.conditions(), null,
                new DialogueCondition.Not(new DialogueCondition.ContextRule(ResourceLocation.parse("fixture:absent")))),
                ConditionOutcome.UNKNOWN, "missing rule cannot match through negation");
        villager.setRemainingFireTicks(40);
        helper.assertTrue(new DialogueCondition.PhysicalState("speaker", "burning").matches(context), "burning state");
        var horse = helper.spawn(EntityType.HORSE, 2, 1, 1);
        villager.startRiding(horse, true);
        helper.assertTrue(new DialogueCondition.PhysicalState("speaker", "riding").matches(context), "riding state");
        villager.stopRiding();
        helper.assertFalse(new DialogueCondition.PhysicalState("speaker", "riding").matches(context), "dismount is observed");
        helper.assertTrue(DialogueCondition.trace(context, condition).message().contains("injured_speaker"), "trace names the context");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_contexts")
    public static void contextGraphsRejectCyclesAndMissingReferences(GameTestHelper helper) {
        var source = ResourceLocation.parse("fixture:dialogue_contexts/check.json");
        for (String json : new String[] {
                """
                {"id":"fixture:check","conditions":[{"type":"context","id":"fixture:check"}]}
                """,
                """
                {"id":"fixture:check","conditions":[{"type":"context","id":"fixture:absent"}]}
                """,
                """
                {"id":"fixture:check","conditions":[{"type":"health","subject":"player","min":0.8,"max":0.2}]}
                """}) {
            boolean rejected = false;
            try {
                DialogueContextResources.compile(Map.of(source, JsonParser.parseString(json).getAsJsonObject()));
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            helper.assertTrue(rejected, "invalid graph is rejected");
        }
        var valid = JsonParser.parseString("""
                {"id":"fixture:check","conditions":[{"type":"any","conditions":[
                  {"type":"weather","state":"rain"},{"type":"health","subject":"player","max":0.25}]}]}
                """).getAsJsonObject();
        helper.assertValueEqual(DialogueContextResources.compile(Map.of(source, valid)).size(), 1, "existing predicates compose");
        helper.succeed();
    }

}
