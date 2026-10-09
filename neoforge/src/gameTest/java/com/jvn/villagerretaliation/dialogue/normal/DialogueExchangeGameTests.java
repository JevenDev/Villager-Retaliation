package com.jvn.villagerretaliation.dialogue.normal;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.DialogueExchange;
import com.jvn.villagerretaliation.dialogue.DialogueSpeech;
import com.jvn.villagerretaliation.dialogue.DialogueTreeResources;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationDefinition.SpeakerKind;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.network.VillagerInteractionNoticePayload;
import com.jvn.villagerretaliation.scene.runtime.SceneContinuation;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class DialogueExchangeGameTests {
    private DialogueExchangeGameTests() { }

    @GameTest(template = "empty", batch = "dialogue_exchanges")
    public static void treesPreserveOrderedSpeakersAndSeparateChoiceLabels(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), player, villager);
        var id = ResourceLocation.parse("fixture:speaker_tree");
        var root = JsonParser.parseString("""
                {"id":"fixture:speaker_tree","entries":[{"id":"offer","label":"Ask about work","start":"start"}],
                 "nodes":{"start":{"exchanges":[
                   {"speaker":{"type":"role","role":"provider"},"text":"A road needs repairing."},
                   {"speaker":{"type":"player"},"text":"Where does it lead?"},
                   {"speaker":{"type":"narrator"},"text":"The mason unfolds a map."}],
                   "responses":[{"id":"accept","label":"Accept the job","next":"reply","exchanges":[
                     {"speaker":{"type":"player"},"text":"I will repair the road."}]}]},
                 "reply":{"lines":["Then take this map."],"end":true}}}
                """).getAsJsonObject();
        var tree = DialogueTreeResources.readGeneratedTree(id, root, id);
        helper.assertValueEqual(tree.nodes().get("start").exchanges().size(), 3, "validation retains the exchange sequence");
        DialogueTreeResources.installTestTrees(player.server, context.locale(), List.of(tree), Set.of());
        try {
            var result = DialogueTreeService.startEntry(context, id, "offer").orElseThrow();
            helper.assertValueEqual(result.exchanges().stream().map(DialogueSpeech::speaker).toList(),
                    java.util.Arrays.asList(villager.getUUID(), player.getUUID(), null), "each ordered line has its own speaker");
            var option = DialogueTreeService.activeOptions(context).orElseThrow().getFirst();
            helper.assertValueEqual(option.label(), "Accept the job", "choice label remains concise");
            result = DialogueTreeService.handleDialogueOption(context, option).orElseThrow();
            helper.assertValueEqual(result.exchanges().stream().map(DialogueSpeech::text).toList(),
                    List.of("I will repair the road.", "Then take this map."), "player speaks before the next NPC reply");
            helper.assertTrue(DialogueTreeService.activeOptions(context).isEmpty(), "terminal node releases the cursor");
        } finally {
            DialogueTreeService.leaveActiveSession(context);
            DialogueTreeResources.clearCache();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_exchanges")
    public static void forcedTreeChoicesReachTheirSpokenTerminalReply(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        villager.setNoAi(true);
        player.setPos(villager.getX() + 1, villager.getY(), villager.getZ());
        var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), player, villager);
        var id = ResourceLocation.parse("fixture:forced_speaker_tree");
        var root = JsonParser.parseString("""
                {"id":"fixture:forced_speaker_tree","entries":[{"id":"offer","label":"Work","start":"start"}],
                 "nodes":{"start":{"exchanges":[{"speaker":{"type":"role","role":"provider"},"text":"Will you help?"}],
                   "responses":[{"id":"accept","label":"Accept","next":"reply","actions":[{"type":"experience","amount":3}],
                     "exchanges":[{"speaker":{"type":"player"},"text":"I will help."}]}]},
                   "reply":{"exchanges":[{"speaker":{"type":"narrator"},"text":"The agreement is made."}],"end":true}}}
                """).getAsJsonObject();
        DialogueTreeResources.installTestTrees(player.server, context.locale(), List.of(DialogueTreeResources.readGeneratedTree(id, root, id)), Set.of());
        com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueService.endForPlayer(player);
        try {
            helper.assertTrue(com.jvn.villagerretaliation.interaction.VillagerConversationService.startForced(player, villager), "forced tree session starts");
            DialogueTreeService.startEntryResult(context, id, "offer", Map.of(), true);
            var option = DialogueTreeService.activeOptions(context).orElseThrow().getFirst();
            int experience = player.totalExperience;
            com.jvn.villagerretaliation.interaction.VillagerDialogueRequestHandler.handle(player, villager.getId(), option.id());
            helper.assertValueEqual(player.totalExperience, experience + 3, "forced tree executes the chosen action");
            helper.assertTrue(DialogueTreeService.activeOptions(context).isEmpty(), "forced tree reaches its terminal reply");
            helper.assertFalse(com.jvn.villagerretaliation.interaction.VillagerConversationService.validate(player, villager), "terminal reply releases server conversation");
        } finally {
            com.jvn.villagerretaliation.interaction.VillagerConversationService.endForPlayer(player, false);
            DialogueTreeService.leaveActiveSession(context);
            DialogueTreeResources.clearCache();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_exchanges")
    public static void legacyTextAndSuspendedSpeechRetainTheirContracts(GameTestHelper helper) {
        var line = new DialogueExchange(SpeakerKind.PLAYER, "I will return, {player}.", "fixture.spoken");
        var continuation = new SceneContinuation(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                ResourceLocation.parse("fixture:quest"), UUID.randomUUID(), "/response/accept", List.of(), 0, Map.of("player", "Alex"));
        var resume = new SceneContinuation.DialogueResume(ResourceLocation.parse("fixture:tree"), "reply", true, "Accepted.", true, List.of(line));
        continuation.dialogueResume(resume);
        var restored = SceneContinuation.load(continuation.save());
        helper.assertValueEqual(restored.dialogueResume(), resume, "suspended response retains authored speech and speaker");
        var old = continuation.save();
        old.getCompound("DialogueResume").remove("Exchanges");
        helper.assertTrue(SceneContinuation.load(old).dialogueResume().exchanges().isEmpty(), "old saves still load without exchanges");
        var legacy = new VillagerDialogueService.DialogueResult("old", "An old NPC line.");
        helper.assertValueEqual(legacy.text(), "An old NPC line.", "legacy result text is unchanged");
        helper.assertTrue(legacy.exchanges().isEmpty(), "legacy callers need no exchange migration");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_exchanges")
    public static void exchangePacketsPreserveSpeakersAndRejectUnboundedLists(GameTestHelper helper) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            var lines = List.of(new DialogueSpeech(UUID.randomUUID(), 1, "Alex", DialogueTextSegment.parse("I agree.", DialogueTextEffects.NONE)),
                    new DialogueSpeech(null, -1, "", DialogueTextSegment.parse("Silence.", DialogueTextEffects.NONE)));
            var payload = new VillagerInteractionNoticePayload(4, "I agree.", "Alex", lines.getFirst().segments(), lines);
            VillagerInteractionNoticePayload.STREAM_CODEC.encode(buffer, payload);
            helper.assertValueEqual(VillagerInteractionNoticePayload.STREAM_CODEC.decode(buffer), payload, "ordered exchanges survive the wire");
            buffer.clear();
            var combined = java.util.Collections.nCopies(128, lines.getFirst());
            DialogueSpeech.writeList(buffer, combined);
            helper.assertValueEqual(DialogueSpeech.readList(buffer).size(), 128, "response and node can each contain a full sequence");
            buffer.clear();
            var closing = new com.jvn.villagerretaliation.network.VillagerConversationEndedPayload(4, "", true);
            com.jvn.villagerretaliation.network.VillagerConversationEndedPayload.STREAM_CODEC.encode(buffer, closing);
            helper.assertValueEqual(com.jvn.villagerretaliation.network.VillagerConversationEndedPayload.STREAM_CODEC.decode(buffer), closing,
                    "terminal speech close policy survives the wire");
            var provider = new DialogueSpeech(UUID.randomUUID(), 8, "Mason", lines.getFirst().segments());
            var replacement = new DialogueSpeech(provider.speaker(), 8, "Mason", DialogueTextSegment.parse("Payment accepted.", DialogueTextEffects.NONE));
            helper.assertValueEqual(DialogueSpeech.replaceSpeakerReply(List.of(lines.getFirst(), provider, lines.getLast()), replacement),
                    List.of(lines.getFirst(), replacement, lines.getLast()), "response overrides retain player speech and narration");
            for (int count : new int[] {-1, DialogueSpeech.MAX_EXCHANGES + 1, Integer.MAX_VALUE}) {
                buffer.clear();
                buffer.writeVarInt(count);
                try {
                    DialogueSpeech.readList(buffer);
                    helper.fail("invalid exchange count must be rejected");
                } catch (IllegalArgumentException expected) { }
            }
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_exchanges")
    public static void invalidSpeakersAndEmptySequencesAreRejected(GameTestHelper helper) {
        for (String raw : List.of("{\"exchanges\":[]}",
                "{\"exchanges\":[{\"speaker\":{\"type\":\"role\",\"role\":\"stranger\"},\"text\":\"Hello\"}]}",
                "{\"exchanges\":[{\"speaker\":{\"type\":\"player\",\"role\":\"provider\"},\"text\":\"Hello\"}]}")) {
            try {
                DialogueExchange.read(JsonParser.parseString(raw).getAsJsonObject());
                helper.fail("invalid exchange content must be rejected");
            } catch (IllegalArgumentException expected) { }
        }
        helper.succeed();
    }
}
