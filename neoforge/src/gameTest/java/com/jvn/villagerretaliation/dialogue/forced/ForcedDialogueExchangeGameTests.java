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
                {"id":"speaker_fixture","trigger":"quest","camera":{"preset":"birds_eye"},"exchanges":[
                  {"speaker":{"type":"narrator"},"text":"A voice calls from the gate."},
                  {"speaker":{"type":"role","role":"provider"},"text":"Wait a moment."}],
                 "options":[{"id":"answer","label":"Ask why","end_conversation":false,"camera":{"preset":"over_shoulder_right"},"exchanges":[
                   {"speaker":{"type":"player"},"text":"What do you need?"}],
                   "follow_up":{"camera":{"preset":"speaker_facing"},"exchanges":[{"speaker":{"type":"role","role":"provider"},"text":"Your help."}],
                     "options":[{"id":"leave","label":"Later","end_conversation":true}]}}]}
                """).getAsJsonObject();
        var definition = ForcedDialogueResources.readEntry(ResourceLocation.parse("fixture:forced"), root, 0, null,
                DialogueEntryMetadata.EMPTY, List.of(), "").orElseThrow();
        helper.assertValueEqual(definition.output().exchanges().size(), 2, "opening keeps both speakers without legacy lines");
        var villager = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, 1, 1, 1);
        var context = new ForcedDialogueResources.ForcedDialogueContext("Guide", "", "", "", "", "", "", 0,
                "", "", "", "", 0, 0, 0, 0, 0, helper.getLevel().getServer(), helper.getLevel().getRandom(), "en_us");
        String wordLine = ForcedDialogueResources.resolveTemplate(
                "I'm <aqua>{word:villagerretaliation:mood/content}</aqua> this {time_of_day}.", context);
        helper.assertFalse(wordLine.contains("{word"), "forced dialogue expands pools");
        helper.assertFalse(wordLine.contains("{time_of_day}"), "forced dialogue retains time placeholders");
        helper.assertTrue(wordLine.contains("<aqua>"), "formatting remains for the final parser");
        var ambient = ForcedDialogueService.resolveForcedExchanges(null, villager, definition.output().exchanges(), context, java.util.Map.of());
        helper.assertTrue(ambient.getFirst().speaker() == null, "ambient narration needs no player identity");
        helper.assertValueEqual(ambient.getLast().speaker(), villager.getUUID(), "ambient NPC keeps its entity identity");
        var answer = definition.options().stream().filter(option -> option.id().equals("answer")).findFirst().orElseThrow();
        helper.assertValueEqual(answer.label().text(), "Ask why", "choice label is separate");
        helper.assertValueEqual(answer.exchanges().getFirst().speaker(), SpeakerKind.PLAYER, "response is player speech");
        helper.assertValueEqual(answer.followUp().exchanges().getFirst().text(), "Your help.", "follow-up retains its own sequence");
        helper.assertValueEqual(definition.output().exchanges().getFirst().camera(), definition.output().camera(), "forced opening inherits its camera");
        helper.assertValueEqual(answer.exchanges().getFirst().camera(), answer.camera(), "forced response inherits its camera");
        helper.assertValueEqual(answer.followUp().exchanges().getFirst().camera(), answer.followUp().camera(), "follow-up inherits its camera");
        root.getAsJsonArray("exchanges").get(0).getAsJsonObject().getAsJsonObject("speaker").addProperty("type", "unknown");
        helper.assertTrue(ForcedDialogueResources.readEntry(ResourceLocation.parse("fixture:forced"), root, 0, null,
                DialogueEntryMetadata.EMPTY, List.of(), "").isEmpty(), "malformed speech rejects its definition");
        helper.succeed();
    }
}
