package com.jvn.villagerretaliation.dialogue.conversation;

import com.jvn.villagerretaliation.client.interaction.CampaignConversationScreenController;
import com.jvn.villagerretaliation.network.CampaignConversationRequestPayload;
import com.jvn.villagerretaliation.network.CampaignConversationRequestPayload.Action;
import com.jvn.villagerretaliation.network.CampaignConversationViewPayload;
import com.jvn.villagerretaliation.network.CampaignConversationViewPayload.*;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class ConversationPresentationPayloadGameTests {
    private ConversationPresentationPayloadGameTests() { }

    @GameTest(template = "empty", batch = "conversation_payloads")
    public static void conversationMessagesPreserveIdentitySpeakersAndSavedChecks(GameTestHelper helper) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            UUID view = UUID.randomUUID(); UUID character = UUID.randomUUID(); UUID player = UUID.randomUUID(); UUID session = UUID.randomUUID();
            var line = new Line("1/mentor", character, 4, "Mira", "Welcome.", 0, 1);
            var state = new Session(session, 14, "Guidance", ConversationSession.Phase.ROLLED, line, true, false, "Player",
                    List.of(new Member(player, "Listener")), List.of(), new Check("convince", 10, true, 15, true), "");
            var message = new CampaignConversationViewPayload(view, 4, character, 0, 1, List.of(), state, "");
            CampaignConversationViewPayload.STREAM_CODEC.encode(buffer, message);
            helper.assertValueEqual(CampaignConversationViewPayload.STREAM_CODEC.decode(buffer), message, "all presentation fields survive the wire");
            buffer.clear();
            var request = new CampaignConversationRequestPayload(view, UUID.randomUUID(), 4, character, Action.HAND_OVER, session, 14, "", player.toString(), 0, 0);
            CampaignConversationRequestPayload.STREAM_CODEC.encode(buffer, request);
            helper.assertValueEqual(CampaignConversationRequestPayload.STREAM_CODEC.decode(buffer), request, "intent identifies its view, character, session, and revision");
            buffer.clear();
            var topics = new CampaignConversationViewPayload(view, 4, character, 1, 3,
                    List.of(new Topic("campaign_fixture:mentor_conversation", "guidance", "Ask for guidance", true)), null, "");
            CampaignConversationViewPayload.STREAM_CODEC.encode(buffer, topics);
            helper.assertValueEqual(CampaignConversationViewPayload.STREAM_CODEC.decode(buffer), topics, "topic page survives the wire");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_payloads")
    public static void oversizedMessagesAndInvalidCheckResultsAreRejected(GameTestHelper helper) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            for (int count : new int[] {-1, CampaignConversationViewPayload.PAGE_SIZE + 1, Integer.MAX_VALUE}) {
                buffer.clear();
                buffer.writeUUID(UUID.randomUUID()); buffer.writeVarInt(1); buffer.writeUUID(UUID.randomUUID());
                buffer.writeVarInt(0); buffer.writeVarInt(1); buffer.writeVarInt(count);
                try { CampaignConversationViewPayload.STREAM_CODEC.decode(buffer); helper.fail("invalid topic count must be rejected before allocation"); }
                catch (IllegalArgumentException expected) { }
            }
            try { new Check("test", 0, true, 1, true); helper.fail("natural one cannot be shown as success"); }
            catch (IllegalArgumentException expected) { }
            try { new Line("test", null, -1, "", "x".repeat(CampaignConversationViewPayload.MAX_TEXT_LENGTH + 1), 0, 1); helper.fail("oversized text must be paged"); }
            catch (IllegalArgumentException expected) { }
            try { new CampaignConversationRequestPayload(UUID.randomUUID(), UUID.randomUUID(), 1, UUID.randomUUID(), Action.ROLL, null, 1, "", "", 0, 0); helper.fail("roll requires a session"); }
            catch (IllegalArgumentException expected) { }
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_payloads")
    public static void screenStateRejectsOldViewsAndDoesNotReplayAnAcknowledgedLine(GameTestHelper helper) {
        var host = new Host();
        var controller = new CampaignConversationScreenController(host);
        controller.tick();
        var request = host.sent.getFirst();
        UUID session = UUID.randomUUID();
        var first = message(request, session, 3, "1/mentor", "Welcome.", false);
        helper.assertTrue(controller.accept(first), "active screen accepts its own snapshot");
        helper.assertValueEqual(host.texts.size(), 1, "first exchange is presented once");
        helper.assertTrue(controller.accept(first), "duplicate acknowledgement is harmless");
        helper.assertValueEqual(host.texts.size(), 1, "duplicate acknowledgement does not restart text animation");
        controller.accept(message(request, session, 4, "1/player", "Welcome.", true));
        helper.assertValueEqual(host.texts.size(), 2, "same words in a new exchange still change speaker and presentation");
        helper.assertFalse(controller.accept(first), "old revision cannot replace the newer exchange");
        var otherView = new CampaignConversationViewPayload(UUID.randomUUID(), request.entityId(), request.character(), 0, 1, List.of(), first.session(), "");
        helper.assertFalse(controller.accept(otherView), "earlier screen cannot update this view");
        helper.assertValueEqual(controller.session().revision(), 4L, "newer revision retained");
        var unavailable = new CampaignConversationViewPayload(request.viewId(), request.entityId(), request.character(), 0, 1, List.of(), null, "Return to the character.");
        helper.assertTrue(controller.accept(unavailable), "unavailable screen retains readable history");
        helper.assertFalse(controller.session().canControl() || controller.session().canTakeControl() || !controller.session().handover().isEmpty(), "unavailable view disables stale controls");
        helper.assertValueEqual(controller.session().line().text(), "Welcome.", "last acknowledged exchange stays readable");
        controller.accept(message(request, session, 4, "1/player", "Welcome.", true));
        helper.assertTrue(controller.session().canControl(), "fresh server snapshot restores valid controls");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_payloads")
    public static void screenSubmitsIntentAndRefreshesAfterAnUnacknowledgedRequest(GameTestHelper helper) {
        var host = new Host();
        var controller = new CampaignConversationScreenController(host);
        controller.tick();
        var request = host.sent.getFirst();
        controller.accept(message(request, UUID.randomUUID(), 5, "1/mentor", "Welcome.", true));
        controller.addSessionOptions();
        var advance = host.options.stream().filter(option -> option.label.equals("conversation.continue")).findFirst().orElseThrow();
        helper.assertFalse(advance.locked, "controller can advance");
        advance.action.run();
        var intent = host.sent.getLast();
        helper.assertValueEqual(intent.action(), Action.ADVANCE, "screen submits an intent");
        helper.assertValueEqual(intent.revision(), 5L, "intent uses acknowledged revision");
        helper.assertValueEqual(controller.session().revision(), 5L, "screen does not predict the next node");
        host.now = 2000;
        controller.tick();
        helper.assertValueEqual(host.sent.getLast().action(), Action.VIEW, "timeout refreshes state without repeating an effect");
        controller.close();
        helper.assertValueEqual(host.sent.getLast().action(), Action.CLOSE, "closing sends the screen identity");
        helper.assertValueEqual(host.sent.getLast().viewId(), request.viewId(), "close targets only this view");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_payloads")
    public static void screenAnimatesOnlyTheSavedRollAndSkipsWithoutAnotherRequest(GameTestHelper helper) {
        var host = new Host();
        var controller = new CampaignConversationScreenController(host);
        controller.tick();
        var request = host.sent.getFirst();
        UUID id = UUID.randomUUID();
        var unresolved = new Session(id, 1, "Guidance", ConversationSession.Phase.CHECK, null, true, false, "Player", List.of(), List.of(), new Check("convince", 10, false, 0, false), "");
        controller.accept(new CampaignConversationViewPayload(request.viewId(), request.entityId(), request.character(), 0, 1, List.of(), unresolved, ""));
        controller.addSessionOptions();
        host.options.stream().filter(option -> option.label.startsWith("conversation.roll")).findFirst().orElseThrow().action.run();
        helper.assertValueEqual(host.sent.getLast().action(), Action.ROLL, "roll submits server intent");
        helper.assertFalse(controller.dieAnimating() || controller.session().check().resolved(), "client cannot predict the roll");
        var resolved = new Session(id, 2, "Guidance", ConversationSession.Phase.ROLLED, null, true, false, "Player", List.of(), List.of(), new Check("convince", 10, true, 17, true), "");
        var acknowledged = new CampaignConversationViewPayload(request.viewId(), request.entityId(), request.character(), 0, 1, List.of(), resolved, "");
        controller.accept(acknowledged);
        helper.assertTrue(controller.dieAnimating(), "saved server result begins animation");
        helper.assertTrue(host.texts.getLast().startsWith("conversation.rolling"), "difficulty remains readable during the animation");
        int sent = host.sent.size();
        host.options.clear(); controller.addSessionOptions();
        helper.assertTrue(host.options.stream().noneMatch(option -> option.label.equals("conversation.continue")), "advance waits for presentation or skip");
        host.options.stream().filter(option -> option.label.equals("conversation.skip_roll")).findFirst().orElseThrow().action.run();
        helper.assertValueEqual(host.sent.size(), sent, "skipping changes no server state");
        helper.assertTrue(host.texts.getLast().contains("17") && host.texts.getLast().contains("10"), "fallback text contains the saved roll and difficulty");
        helper.assertFalse(controller.dieAnimating(), "skip ends animation");
        controller.accept(acknowledged);
        helper.assertFalse(controller.dieAnimating(), "duplicate saved result never restarts the animation");
        helper.assertValueEqual(controller.session().check().die(), 17, "animation preserves the acknowledged result");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_payloads")
    public static void confirmationPresentationSubmitsOnlyTheAcknowledgedIdentity(GameTestHelper helper) {
        var host = new Host();
        var controller = new CampaignConversationScreenController(host);
        controller.tick();
        var request = host.sent.getFirst();
        UUID session = UUID.randomUUID(); UUID confirmation = UUID.randomUUID();
        var line = new Line("confirmation/" + confirmation, null, -1, "", "Shared consequences", 0, 2);
        var first = new Session(session, 9, "Guidance", ConversationSession.Phase.CONFIRMATION, line,
                true, false, "Player", List.of(), List.of(), null, "", confirmation);
        var message = new CampaignConversationViewPayload(request.viewId(), request.entityId(), request.character(), 0, 1, List.of(), first, "");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            CampaignConversationViewPayload.STREAM_CODEC.encode(buffer, message);
            helper.assertValueEqual(CampaignConversationViewPayload.STREAM_CODEC.decode(buffer), message, "confirmation identity survives the wire");
            controller.accept(message);
            controller.addSessionOptions();
            helper.assertTrue(host.options.stream().noneMatch(option -> option.label.equals("conversation.confirm")), "all consequence pages precede approval");
            host.options.clear();
            var last = new Session(session, 9, "Guidance", ConversationSession.Phase.CONFIRMATION,
                    new Line(line.key(), null, -1, "", "Final consequences", 1, 2), true, false, "Player", List.of(), List.of(), null, "", confirmation);
            controller.accept(new CampaignConversationViewPayload(request.viewId(), request.entityId(), request.character(), 0, 1, List.of(), last, ""));
            host.read = false;
            controller.addSessionOptions();
            int sent = host.sent.size();
            helper.assertTrue(host.options.stream().noneMatch(option -> option.label.equals("conversation.confirm")), "confirmation waits for visible text to be reviewed");
            host.options.stream().filter(option -> option.label.equals("conversation.more_consequences")).findFirst().orElseThrow().action.run();
            helper.assertValueEqual(host.sent.size(), sent, "reading consequences sends no approval");
            host.options.clear(); controller.addSessionOptions();
            host.options.stream().filter(option -> option.label.equals("conversation.confirm")).findFirst().orElseThrow().action.run();
            var intent = host.sent.getLast();
            helper.assertValueEqual(intent.action(), Action.CONFIRM, "approval submits server intent");
            helper.assertValueEqual(intent.argument(), confirmation.toString(), "approval names only the displayed confirmation");
            helper.assertValueEqual(intent.revision(), 9L, "approval uses the acknowledged session revision");
            buffer.clear();
            CampaignConversationRequestPayload.STREAM_CODEC.encode(buffer, intent);
            helper.assertValueEqual(CampaignConversationRequestPayload.STREAM_CODEC.decode(buffer), intent, "confirmation request survives the wire");
            helper.assertValueEqual(controller.session().phase(), ConversationSession.Phase.CONFIRMATION, "client waits for durable server approval");
        } finally { buffer.release(); }
        helper.succeed();
    }

    private static CampaignConversationViewPayload message(CampaignConversationRequestPayload request, UUID id, long revision, String key, String text, boolean control) {
        var line = new Line(key, request.character(), request.entityId(), "Mira", text, 0, 1);
        var session = new Session(id, revision, "Guidance", ConversationSession.Phase.EXCHANGES, line, control, false, "Player", List.of(), List.of(), null, "");
        return new CampaignConversationViewPayload(request.viewId(), request.entityId(), request.character(), 0, 1, List.of(), session, "");
    }

    private static final class Host implements CampaignConversationScreenController.Host {
        final UUID character = UUID.randomUUID();
        final List<CampaignConversationRequestPayload> sent = new ArrayList<>();
        final List<Option> options = new ArrayList<>();
        final List<String> texts = new ArrayList<>();
        long now;
        boolean read = true;
        @Override public boolean confirmationTextRead() { return read; }
        @Override public void readConfirmationText() { read = true; }
        @Override public int entityId() { return 4; }
        @Override public UUID character() { return character; }
        @Override public long now() { return now; }
        @Override public String translate(String key, Object... arguments) { return arguments.length == 0 ? key : key + java.util.Arrays.toString(arguments); }
        @Override public void send(CampaignConversationRequestPayload payload) { sent.add(payload); }
        @Override public void option(String label, Runnable action, boolean locked) { options.add(new Option(label, action, locked)); }
        @Override public void updated(boolean session, String text) { if (text != null) texts.add(text); }
        @Override public void close() { }
    }
    private record Option(String label, Runnable action, boolean locked) { }
}
