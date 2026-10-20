package com.jvn.villagerretaliation.dialogue.conversation;

import com.jvn.villagerretaliation.dialogue.DialogueResponseTimer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("quest_mechanics")
@PrefixGameTestTemplate(false)
public final class ConversationResponseTimerGameTests {
    @GameTest(template = "empty")
    public static void countdownsSurviveBothDialogueWireFormats(GameTestHelper helper) {
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            var state = new com.jvn.villagerretaliation.network.CampaignConversationViewPayload.Session(
                    UUID.randomUUID(), 1, "Timed", ConversationSession.Phase.RESPONSES, null, true, false, "Player",
                    List.of(), List.of(), null, "", null, List.of(), false, false, null, 40);
            var message = new com.jvn.villagerretaliation.network.CampaignConversationViewPayload(
                    UUID.randomUUID(), 1, UUID.randomUUID(), 0, 1, List.of(), state, "");
            var codec = com.jvn.villagerretaliation.network.CampaignConversationViewPayload.STREAM_CODEC;
            codec.encode(buffer, message);
            helper.assertValueEqual(codec.decode(buffer), message, "persistent conversation countdown survives the wire");
            buffer.clear();
            var normal = new com.jvn.villagerretaliation.network.VillagerDialogueResponsePayload(
                    1, 0, com.jvn.villagerretaliation.reputation.VillagerReputationLevel.values()[0],
                    com.jvn.villagerretaliation.dialogue.normal.DialogueDisposition.values()[0],
                    com.jvn.villagerretaliation.mood.VillagerMood.values()[0], false, List.of(), List.of(), 20);
            var normalCodec = com.jvn.villagerretaliation.network.VillagerDialogueResponsePayload.STREAM_CODEC;
            normalCodec.encode(buffer, normal);
            helper.assertValueEqual(normalCodec.decode(buffer), normal, "tree countdown survives the wire");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void deadlineBoundariesAndResolvedStateSurviveTheSessionCodec(GameTestHelper helper) {
        UUID player = UUID.randomUUID(), character = UUID.randomUUID();
        var id = ResourceLocation.parse("test:timed");
        var identity = new ConversationSession.Identity(UUID.randomUUID(), id, 1, "graph",
                null, null, "", id, "provider", character, ResourceLocation.parse("minecraft:overworld"), player,
                null, "Timed conversation", "talk", 0);
        var session = new ConversationSession(identity, player, null, "welcome",
                new ConversationSession.Speaker("provider", character, identity.dimension(), "Mira"));
        var timer = new DialogueResponseTimer(40, "", "silence");
        var node = new ConversationDefinition.Node("welcome", List.of(), List.of(), Map.of(), "", "", "", timer);
        var definition = new ConversationDefinition(id, 1, "graph", null, "provider", id, "",
                Map.of(), Map.of("welcome", node), Map.of(), Map.of(), null, null);
        ConversationResponseTimers.arm(session, definition, 100);
        helper.assertValueEqual(session.responseDeadline, -1L, "node effects cannot consume response time");
        session.phase = ConversationSession.Phase.RESPONSES;
        ConversationResponseTimers.arm(session, definition, 100);
        ConversationResponseTimers.arm(session, definition, 130);
        helper.assertValueEqual(session.responseDeadline, 140L, "refreshing cannot extend a deadline");
        session = ConversationSessionCodec.load(ConversationSessionCodec.save(session));
        helper.assertFalse(ConversationResponseTimers.expired(session, 139), "choice remains open before its boundary");
        helper.assertTrue(ConversationResponseTimers.expired(session, 140), "deadline is inclusive");
        session.responseTimerResolved = true;
        session = ConversationSessionCodec.load(ConversationSessionCodec.save(session));
        helper.assertFalse(ConversationResponseTimers.expired(session, 200), "a saved timeout cannot run twice");
        ConversationService.enter(session, "welcome");
        session.phase = ConversationSession.Phase.RESPONSES;
        ConversationResponseTimers.arm(session, definition, 300);
        helper.assertValueEqual(session.responseDeadline, 340L, "a new visit has its own deadline");
        helper.succeed();
    }
}
