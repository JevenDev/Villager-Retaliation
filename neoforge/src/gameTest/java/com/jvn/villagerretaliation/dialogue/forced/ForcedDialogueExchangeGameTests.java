package com.jvn.villagerretaliation.dialogue.forced;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationDefinition.SpeakerKind;
import com.jvn.villagerretaliation.dialogue.normal.DialogueEntryMetadata;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class ForcedDialogueExchangeGameTests {
    private ForcedDialogueExchangeGameTests() { }

    @GameTest(template = "empty", batch = "forced_dialogue_exchanges")
    public static void forcedOpeningsResponsesAndFollowUpsKeepOrderedSpeech(GameTestHelper helper) {
        var root = JsonParser.parseString("""
                {"id":"speaker_fixture","trigger":"quest","exchanges":[
                  {"speaker":{"type":"narrator"},"text":"A voice calls from the gate."},
                  {"speaker":{"type":"role","role":"provider"},"text":"Wait a moment."}],
                 "options":[{"id":"answer","label":"Ask why","end_conversation":false,"exchanges":[
                   {"speaker":{"type":"player"},"text":"What do you need?"}],
                   "follow_up":{"exchanges":[{"speaker":{"type":"role","role":"provider"},"text":"Your help."}],
                     "options":[{"id":"leave","label":"Later","end_conversation":true}]}}]}
                """).getAsJsonObject();
        var definition = ForcedDialogueResources.readEntry(ResourceLocation.parse("fixture:forced"), root, 0, null,
                DialogueEntryMetadata.EMPTY, List.of(), "").orElseThrow();
        helper.assertValueEqual(definition.output().exchanges().size(), 2, "opening keeps both speakers without legacy lines");
        var villager = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, 1, 1, 1);
        var context = new ForcedDialogueResources.ForcedDialogueContext("Guide", "", "", "", "", "", "", 0,
                "", "", "", "", 0, 0, 0, 0, 0, helper.getLevel().getServer(), helper.getLevel().getRandom(), "en_us");
        var ambient = ForcedDialogueService.resolveForcedExchanges(null, villager, definition.output().exchanges(), context, java.util.Map.of());
        helper.assertTrue(ambient.getFirst().speaker() == null, "ambient narration needs no player identity");
        helper.assertValueEqual(ambient.getLast().speaker(), villager.getUUID(), "ambient NPC keeps its entity identity");
        var answer = definition.options().stream().filter(option -> option.id().equals("answer")).findFirst().orElseThrow();
        helper.assertValueEqual(answer.label().text(), "Ask why", "choice label is separate");
        helper.assertValueEqual(answer.exchanges().getFirst().speaker(), SpeakerKind.PLAYER, "response is player speech");
        helper.assertValueEqual(answer.followUp().exchanges().getFirst().text(), "Your help.", "follow-up retains its own sequence");
        root.getAsJsonArray("exchanges").get(0).getAsJsonObject().getAsJsonObject("speaker").addProperty("type", "unknown");
        helper.assertTrue(ForcedDialogueResources.readEntry(ResourceLocation.parse("fixture:forced"), root, 0, null,
                DialogueEntryMetadata.EMPTY, List.of(), "").isEmpty(), "malformed speech rejects its definition");
        helper.succeed();
    }
}
