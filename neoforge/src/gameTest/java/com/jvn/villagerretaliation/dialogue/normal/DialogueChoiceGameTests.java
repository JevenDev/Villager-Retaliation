package com.jvn.villagerretaliation.dialogue.normal;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.DialogueTreeResources;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import java.util.List;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class DialogueChoiceGameTests {
    private DialogueChoiceGameTests() { }

    @GameTest(template = "empty", batch = "dialogue_choices")
    public static void disabledChoicesRemainVisibleAndCannotAdvance(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), player, villager);
        var id = ResourceLocation.parse("fixture:locked_choice");
        var root = JsonParser.parseString("""
                {"id":"fixture:locked_choice","entries":[{"id":"start","label":"Ask","start":"start"}],
                 "nodes":{"start":{"lines":["Hello"],"responses":[
                   {"id":"hidden","label":"Hidden","conditions":[{"type":"not","condition":{"type":"all","conditions":[]}}],"end":true},
                   {"id":"locked","label":"Locked","unavailable_behavior":"disable","unavailable":"Earn my trust first.",
                    "conditions":[{"type":"not","condition":{"type":"all","conditions":[]}}],"next":"end"}]},
                  "end":{"lines":["Done"],"end":true}}}
                """).getAsJsonObject();
        var tree = DialogueTreeResources.readGeneratedTree(id, root, id);
        helper.assertTrue(tree != null, "tree loads");
        DialogueTreeResources.installTestTrees(player.server, context.locale(), List.of(tree), Set.of());
        try {
            DialogueTreeService.startEntry(context, id, "start").orElseThrow();
            var options = DialogueTreeService.activeOptions(context).orElseThrow();
            helper.assertValueEqual(options.size(), 1, "hidden responses stay hidden");
            helper.assertFalse(options.getFirst().available(), "disabled response is not selectable");
            helper.assertValueEqual(options.getFirst().unavailableReason(), "Earn my trust first.", "authored explanation");
            helper.assertTrue(DialogueTreeService.handleDialogueOption(context, options.getFirst()).isEmpty(),
                    "server rejects a disabled response");
            helper.assertValueEqual(DialogueTreeService.activeOptions(context).orElseThrow().getFirst().id(),
                    options.getFirst().id(), "rejected selection does not advance");
        } finally {
            DialogueTreeService.leaveActiveSession(context);
            DialogueTreeResources.clearCache();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_choices")
    public static void runtimeRejectsBrokenTargetsAndDuplicateResponses(GameTestHelper helper) {
        var id = ResourceLocation.parse("fixture:broken");
        var root = JsonParser.parseString("""
                {"id":"fixture:broken","entries":[{"id":"start","label":"Ask","start":"start"}],
                 "nodes":{"start":{"responses":[{"id":"reply","label":"Reply","next":"missing"}]}}}
                """).getAsJsonObject();
        helper.assertTrue(DialogueTreeResources.readGeneratedTree(id, root, id) == null, "missing target rejected");
        var responses = root.getAsJsonObject("nodes").getAsJsonObject("start").getAsJsonArray("responses");
        responses.get(0).getAsJsonObject().addProperty("next", "start");
        helper.assertTrue(DialogueTreeResources.readGeneratedTree(id, root, id) != null, "legitimate loop accepted");
        responses.add(responses.get(0).deepCopy());
        helper.assertTrue(DialogueTreeResources.readGeneratedTree(id, root, id) == null, "duplicate choice rejected");
        helper.succeed();
    }
}
