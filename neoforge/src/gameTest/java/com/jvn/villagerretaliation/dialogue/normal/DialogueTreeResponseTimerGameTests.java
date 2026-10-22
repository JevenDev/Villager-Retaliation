package com.jvn.villagerretaliation.dialogue.normal;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueResponseTimer;
import com.jvn.villagerretaliation.dialogue.DialogueTreeResources;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("quest_mechanics")
@PrefixGameTestTemplate(false)
public final class DialogueTreeResponseTimerGameTests {
    @GameTest(template = "empty")
    public static void expiredDefaultsRespectConditionsWithoutBlockingLiveChoices(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        villager.setNoAi(true);
        var context = VillagerInteractionService.createDialogueContext(level, player, villager);
        var id = ResourceLocation.parse("test:conditional_timer");
        var root = JsonParser.parseString("""
                {"id":"test:conditional_timer","entries":[{"id":"start","label":"Ask","start":"start"}],
                 "nodes":{"start":{"lines":["Choose."],"response_timer":{"duration_ticks":40,"response":"healthy"},
                   "responses":[{"id":"healthy","label":"Healthy choice","next":"done",
                     "conditions":[{"type":"health","subject":"player","min":0.5}],
                     "actions":[{"type":"experience","amount":3}]},
                     {"id":"other","label":"Other choice","next":"done",
                      "actions":[{"type":"experience","amount":7}]}]},
                   "done":{"lines":["Done."],"end":true}}}
                """).getAsJsonObject();
        var tree = DialogueTreeResources.readGeneratedTree(id, root, id);
        helper.assertTrue(tree != null, "conditional timeout content loads");
        DialogueTreeResources.installTestTrees(player.server, context.locale(), List.of(tree), Set.of());
        try {
            for (boolean defaultAvailable : List.of(false, true)) {
                for (boolean tickFirst : List.of(false, true)) {
                    player.setHealth(player.getMaxHealth());
                    DialogueTreeService.startEntry(context, id, "start").orElseThrow();
                    if (!defaultAvailable) player.setHealth(player.getMaxHealth() / 4);
                    var option = DialogueTreeService.activeOptions(context).orElseThrow().stream()
                            .filter(value -> value.treeReference().responseId().equals("other")).findFirst().orElseThrow();
                    var data = DialogueTreeSessionSavedData.get(level);
                    var key = new DialogueTreeService.SessionKey(player.getUUID(), villager.getUUID());
                    var session = data.get(key);
                    data.put(key, new DialogueTreeService.Session(session.id(), id, session.nodeId(),
                            session.lastTouchedGameTime(), session.replacements(), level.getGameTime(), false));
                    int experience = player.totalExperience;
                    if (tickFirst) DialogueTreeService.tick(context);
                    if (!tickFirst || !defaultAvailable) {
                        helper.assertTrue(DialogueTreeService.handleDialogueOption(context, option).isPresent(),
                                "an unavailable default does not block a valid manual response");
                    }
                    int expectedExperience = experience + (defaultAvailable ? 3 : 7);
                    helper.assertValueEqual(player.totalExperience, expectedExperience,
                            "an available default takes precedence, otherwise only the selected response runs");
                    helper.assertTrue(data.get(key) == null, "the selected terminal releases its cursor");
                    DialogueTreeService.tick(context);
                    helper.assertTrue(DialogueTreeService.handleDialogueOption(context, option).isEmpty(),
                            "a completed selection cannot be replayed");
                    helper.assertValueEqual(player.totalExperience, expectedExperience, "response effects run once");
                }
            }
        } finally {
            DialogueTreeService.leaveActiveSession(context);
            DialogueTreeResources.clearCache();
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void savedTreeDeadlinesSelectDefaultsOrSilenceWithoutReplayingEffects(GameTestHelper helper) {
        var level = helper.getLevel();
        var storage = level.getServer().overworld().getDataStorage();
        var prior = DialogueTreeSessionSavedData.get(level);
        var player = helper.makeMockServerPlayerInLevel();
        var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        villager.setNoAi(true);
        player.setPos(villager.getX() + 1, villager.getY(), villager.getZ());
        var context = VillagerInteractionService.createDialogueContext(level, player, villager);
        try {
            for (boolean silence : List.of(false, true)) {
                var id = ResourceLocation.parse("test:timed_" + silence);
                var timer = new DialogueResponseTimer(40, silence ? "" : "accept", silence ? "done" : "");
                var response = new DialogueTreeDefinition.Response("accept", "Accept", DialogueEntryMetadata.EMPTY, "done",
                        DialogueRequestType.STORY, List.of(), List.of(), List.of(), false, 0);
                var opening = new DialogueTreeDefinition.Node("welcome", List.of("Choose."), List.of(), List.of(),
                        List.of(), List.of(response), false, List.of(), null, timer);
                var actions = VillagerActionDefinition.readListOrInline(id, "timer",
                        JsonParser.parseString("{\"type\":\"experience\",\"amount\":7}").getAsJsonObject());
                var done = new DialogueTreeDefinition.Node("done", List.of("Done."), actions, List.of(), List.of(), true);
                var tree = new DialogueTreeDefinition(id, "Timed tree", "", DialogueEntryMetadata.EMPTY,
                        List.of(), List.of(), Map.of("welcome", opening, "done", done));
                DialogueTreeResources.installTestTrees(level.getServer(), context.locale(), List.of(tree), Set.of());
                var data = new DialogueTreeSessionSavedData();
                var key = new DialogueTreeService.SessionKey(player.getUUID(), villager.getUUID());
                data.put(key, new DialogueTreeService.Session(UUID.randomUUID(), id, "welcome", level.getGameTime(),
                        Map.of(), level.getGameTime(), false));
                data = DialogueTreeSessionSavedData.load(data.save(new CompoundTag(), level.registryAccess()));
                storage.set("villagerretaliation_dialogue_trees", data);
                if (silence) helper.assertTrue(com.jvn.villagerretaliation.interaction.VillagerConversationService.startForced(player, villager),
                        "fixture opens a locked dialogue");
                int experience = player.totalExperience;
                DialogueTreeService.tick(context);
                helper.assertValueEqual(player.totalExperience, experience + 7, "deadline executes the destination once");
                helper.assertTrue(data.get(key) == null, "terminal destination retires the timed cursor");
                if (silence) helper.assertFalse(com.jvn.villagerretaliation.interaction.VillagerConversationService.isConversing(villager),
                        "a timed terminal releases its locked conversation");
                data = DialogueTreeSessionSavedData.load(data.save(new CompoundTag(), level.registryAccess()));
                storage.set("villagerretaliation_dialogue_trees", data);
                DialogueTreeService.tick(context);
                helper.assertValueEqual(player.totalExperience, experience + 7, "reloading cannot replay the timeout reward");
            }
        } finally {
            com.jvn.villagerretaliation.interaction.VillagerConversationService.endForPlayer(player, false);
            storage.set("villagerretaliation_dialogue_trees", prior);
            DialogueTreeResources.clearCache();
            villager.discard();
        }
        helper.succeed();
    }
}
