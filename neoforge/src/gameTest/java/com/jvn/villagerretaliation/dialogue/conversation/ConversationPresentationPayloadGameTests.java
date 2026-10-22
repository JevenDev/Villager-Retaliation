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
    public static void countdownRefreshesDoNotReplaceDialogueText(GameTestHelper helper) {
        var host = new Host();
        var controller = new CampaignConversationScreenController(host);
        controller.tick();
        var request = host.sent.getFirst();
        var id = UUID.randomUUID();
        var line = new Line("1/mentor", host.character, 4, "Mira", "A long prompt. ".repeat(60), 0, 1);
        for (int seconds = 0; seconds < 3; seconds++) {
            host.now = seconds * 1000;
            var session = new Session(id, 1, "Guidance", ConversationSession.Phase.RESPONSES, line, true, false, "Player",
                    List.of(), List.of(), null, "", null, List.of(), false, false, null, 100 - seconds * 20);
            controller.accept(new CampaignConversationViewPayload(request.viewId(), 4, host.character, 0, 1, List.of(), session, ""));
            helper.assertValueEqual(controller.responseRemainingTicks(), (long) (100 - seconds * 20), "snapshot updates the separate countdown");
            helper.assertValueEqual(host.texts.size(), 1, "countdown polling cannot restart playback or scrolling");
            helper.assertValueEqual(host.texts.getFirst(), line.text(), "the countdown is not part of animated dialogue");
        }
        host.now += 500;
        helper.assertValueEqual(controller.responseRemainingTicks(), 50L, "countdown advances between server polls");
        host.now += 5000;
        helper.assertValueEqual(controller.responseRemainingTicks(), 0L, "countdown stops at zero");
        var history = new Session(id, 1, "Guidance", ConversationSession.Phase.RESPONSES, line, false, false, "Player",
                List.of(), List.of(), null, "", null, List.of(), false, false, new History(0, 1, List.of("Earlier dialogue.")), 60);
        controller.accept(new CampaignConversationViewPayload(request.viewId(), 4, host.character, 0, 1, List.of(), history, ""));
        helper.assertValueEqual(controller.responseRemainingTicks(), -1L, "history does not display a live countdown");
        controller.accept(new CampaignConversationViewPayload(request.viewId(), 4, host.character, 0, 1, List.of(), null, ""));
        helper.assertValueEqual(controller.responseRemainingTicks(), -1L, "leaving clears the countdown");
        helper.succeed();
    }

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
                    List.of(new Topic("campaign_fixture:mentor_conversation", "guidance", "Ask for guidance", true),
                            new Topic("campaign_fixture:mentor_conversation", session.toString(), "Earlier guidance", true, TopicGroup.HISTORY)), null, "");
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

    @GameTest(template = "empty", batch = "conversation_payloads")
    public static void socialResultsPreserveTheSavedTotalAndAwardStatusDuringAnimation(GameTestHelper helper) {
        var host = new Host();
        var controller = new CampaignConversationScreenController(host);
        controller.tick();
        var request = host.sent.getFirst();
        UUID id = UUID.randomUUID();
        var skill = com.jvn.villagerretaliation.skill.player.PlayerSocialSkill.PERSUASION;
        var check = new Check("convince", 10, true, 8, true, 4, 12, skill, 10, false, "Alex");
        var session = new Session(id, 2, "Guidance", ConversationSession.Phase.ROLLED, null, true, false, "Player", List.of(), List.of(), check, "");
        var payload = new CampaignConversationViewPayload(request.viewId(), request.entityId(), request.character(), 0, 1, List.of(), session, "");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            CampaignConversationViewPayload.STREAM_CODEC.encode(buffer, payload);
            helper.assertValueEqual(CampaignConversationViewPayload.STREAM_CODEC.decode(buffer), payload, "wire retains the skill, bonus, total and pending award");
        } finally { buffer.release(); }
        var unresolved = new Session(id, 1, "Guidance", ConversationSession.Phase.CHECK, null, true, false, "Player", List.of(), List.of(),
                new Check("convince", 10, false, 0, false, 0, 0, skill, 10, false), "");
        controller.accept(new CampaignConversationViewPayload(request.viewId(), request.entityId(), request.character(), 0, 1, List.of(), unresolved, ""));
        controller.accept(payload);
        helper.assertTrue(controller.dieAnimating(), "saved social result starts animation");
        helper.assertTrue(host.texts.getLast().contains("conversation.social_check"), "skill remains readable during animation");
        int sent = host.sent.size();
        controller.skipDieAnimation();
        helper.assertValueEqual(host.sent.size(), sent, "skip sends no roll or reward request");
        helper.assertTrue(host.texts.getLast().contains("conversation.social_result") && host.texts.getLast().contains("12")
                && host.texts.getLast().contains("conversation.social_experience_pending"), "fallback shows saved total and pending award");
        var awarded = new Check("convince", 10, true, 8, true, 4, 12, skill, 10, true, "Alex");
        var updated = new Session(id, 3, "Guidance", ConversationSession.Phase.ROLLED, null, true, false, "Player", List.of(), List.of(), awarded, "");
        controller.accept(new CampaignConversationViewPayload(request.viewId(), request.entityId(), request.character(), 0, 1, List.of(), updated, ""));
        helper.assertFalse(controller.dieAnimating(), "award acknowledgment does not replay the animation");
        helper.assertTrue(host.texts.getLast().contains("conversation.social_experience[Alex, 10,")
                && !host.texts.getLast().contains("social_experience_pending"), "awarded experience replaces the pending notice");
        try { new Check("convince", 10, true, 8, true, 4, 13, skill, 10, true); helper.fail("invalid total must be rejected"); }
        catch (IllegalArgumentException expected) { }
        try { new Check("convince", 10, true, 1, true, 20, 21, skill, 10, true); helper.fail("natural one still fails with a bonus"); }
        catch (IllegalArgumentException expected) { }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_payloads")
    public static void situationalResultsShowSignedAndInactiveContributionsAfterSkipping(GameTestHelper helper) {
        var host = new Host();
        var controller = new CampaignConversationScreenController(host);
        controller.tick();
        var request = host.sent.getFirst();
        UUID id = UUID.randomUUID();
        var modifiers = List.of(new Modifier("trust", "Trust", 3, true), new Modifier("risk", "Risk", -8, true),
                new Modifier("support", "Support", 4, false));
        var check = new Check("convince", 10, true, 12, false, -5, 7, null, 0, false, "", modifiers);
        var session = new Session(id, 2, "Guidance", ConversationSession.Phase.ROLLED, null, true, false, "Player",
                List.of(), List.of(), check, "");
        var payload = new CampaignConversationViewPayload(request.viewId(), request.entityId(), request.character(), 0, 1, List.of(), session, "");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            CampaignConversationViewPayload.STREAM_CODEC.encode(buffer, payload);
            helper.assertValueEqual(CampaignConversationViewPayload.STREAM_CODEC.decode(buffer), payload, "wire preserves the signed breakdown and inactive rule");
        } finally { buffer.release(); }
        var waiting = new Session(id, 1, "Guidance", ConversationSession.Phase.CHECK, null, true, false, "Player",
                List.of(), List.of(), new Check("convince", 10, false, 0, false), "");
        controller.accept(new CampaignConversationViewPayload(request.viewId(), request.entityId(), request.character(), 0, 1, List.of(), waiting, ""));
        controller.accept(payload);
        helper.assertTrue(controller.dieAnimating(), "acknowledged situational result animates");
        helper.assertFalse(host.texts.getLast().contains("Risk"), "animation does not reveal the outcome breakdown early");
        int requests = host.sent.size();
        controller.skipDieAnimation();
        helper.assertValueEqual(host.sent.size(), requests, "skipping only affects presentation");
        String text = host.texts.getLast();
        helper.assertTrue(text.contains("conversation.modified_result[12, -5, 7, 10"), "fallback shows the signed saved total");
        helper.assertTrue(text.contains("conversation.modifier_applied[Trust, +3]")
                && text.contains("conversation.modifier_applied[Risk, -8]")
                && text.contains("conversation.modifier_inactive[Support]"), "each authored contribution is readable");
        controller.accept(payload);
        helper.assertFalse(controller.dieAnimating(), "refreshing the saved result does not replay animation");
        try {
            new Check("convince", 10, true, 12, false, -4, 8, null, 0, false, "", modifiers);
            helper.fail("display total must agree with the saved breakdown");
        } catch (IllegalArgumentException expected) { }
        try {
            new Check("convince", 10, true, 12, false, -5, 7, null, 0, false, "",
                    List.of(modifiers.get(0), modifiers.get(0)));
            helper.fail("duplicate contributions must be rejected");
        } catch (IllegalArgumentException expected) { }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_payloads")
    public static void transactionTermsRoundTripAndRejectInvalidPrices(GameTestHelper helper) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            var price = new Transaction("campaign_fixture:characters", "supplies", "pay", "", "Travel supplies", UUID.randomUUID(), "Alex",
                    "minecraft:emerald", 8, 5, 5, TransactionState.NEGOTIATED, false, "");
            var session = new Session(UUID.randomUUID(), 4, "Supplies", ConversationSession.Phase.RESPONSES, null,
                    true, false, "Alex", List.of(), List.of(new Response("pay", "Buy supplies", true)), null, "", null, List.of(price));
            var payload = new CampaignConversationViewPayload(UUID.randomUUID(), 4, UUID.randomUUID(), 0, 1, List.of(), session, "");
            CampaignConversationViewPayload.STREAM_CODEC.encode(buffer, payload);
            helper.assertValueEqual(CampaignConversationViewPayload.STREAM_CODEC.decode(buffer), payload, "prices and buyers survive the wire");
            for (int invalid : new int[] {-1, 0, 7, 9}) {
                try {
                    new Transaction(price.quest(), price.id(), "", "", price.title(), price.buyer(), price.buyerName(),
                            price.item(), 8, 5, invalid, TransactionState.NEGOTIATED, false, "");
                    helper.fail("price must agree with the quoted or bargained terms");
                } catch (IllegalArgumentException expected) { }
            }
            try {
                new Session(session.id(), 4, "Supplies", session.phase(), null, true, false, "Alex", List.of(), session.responses(),
                        null, "", null, java.util.Collections.nCopies(CampaignConversationViewPayload.MAX_TRANSACTIONS + 1, price));
                helper.fail("oversized transaction lists must be rejected");
            } catch (IllegalArgumentException expected) { }
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_payloads")
    public static void bargainingPricesRevealAfterTheSavedAnimationAndSurviveUnavailableViews(GameTestHelper helper) {
        var host = new Host();
        var controller = new CampaignConversationScreenController(host);
        controller.tick();
        var request = host.sent.getFirst();
        var price = new Transaction("campaign_fixture:characters", "supplies", "", "convince", "Travel supplies", UUID.randomUUID(), "Alex",
                "minecraft:emerald", 8, 5, 5, TransactionState.NEGOTIATED, false, "");
        var session = new Session(UUID.randomUUID(), 4, "Supplies", ConversationSession.Phase.ROLLED, null, true, false, "Alex",
                List.of(), List.of(), new Check("convince", 10, true, 15, true), "", null, List.of(price));
        var payload = new CampaignConversationViewPayload(request.viewId(), request.entityId(), request.character(), 0, 1, List.of(), session, "");
        var quote = new Transaction(price.quest(), price.id(), "", price.check(), price.title(), price.buyer(), price.buyerName(),
                price.item(), price.basePrice(), price.bargainPrice(), price.basePrice(), TransactionState.QUOTED, false, "");
        var awaiting = new Session(session.id(), 3, session.title(), ConversationSession.Phase.CHECK, null, true, false, "Alex",
                List.of(), List.of(), new Check("convince", 10, false, 0, false), "", null, List.of(quote));
        controller.accept(new CampaignConversationViewPayload(request.viewId(), request.entityId(), request.character(), 0, 1, List.of(), awaiting, ""));
        controller.accept(payload);
        helper.assertTrue(controller.dieAnimating(), "saved bargaining result uses the existing die animation");
        helper.assertTrue(host.texts.getLast().contains("conversation.transaction_quote[8, minecraft:emerald]")
                && host.texts.getLast().contains("conversation.transaction_bargain[5, minecraft:emerald]"), "animation preserves the quoted terms");
        helper.assertFalse(host.texts.getLast().contains("transaction_agreed"), "animation does not reveal its result through the price");
        controller.skipDieAnimation();
        helper.assertTrue(host.texts.getLast().contains("conversation.transaction_agreed[5, minecraft:emerald]")
                && host.texts.getLast().contains("conversation.transaction_buyer[Travel supplies, Alex]"), "skip reveals the agreed price and original buyer");
        controller.accept(payload);
        helper.assertFalse(controller.dieAnimating(), "refresh cannot replay the bargaining animation");
        controller.accept(new CampaignConversationViewPayload(request.viewId(), request.entityId(), request.character(), 0, 1,
                List.of(), null, "Return to the seller."));
        helper.assertValueEqual(controller.session().transactions(), List.of(price), "unavailable view keeps readable transaction history");
        helper.assertFalse(controller.session().canControl(), "retained prices cannot enable stale controls");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_payloads")
    public static void optionalPresentationSurvivesTheWireAndPausesWithoutLosingText(GameTestHelper helper) {
        var host = new Host();
        var controller = new CampaignConversationScreenController(host);
        controller.tick();
        var request = host.sent.getFirst();
        var line = new Line("1/mentor", host.character, 4, "Mira", "Welcome.", 0, 1,
                new ConversationLinePresentation.Camera(ConversationLinePresentation.Target.SPEAKER, 0.25D),
                net.minecraft.resources.ResourceLocation.parse("campaign_fixture:mentor/welcome"));
        var session = new Session(UUID.randomUUID(), 1, "Guidance", ConversationSession.Phase.EXCHANGES, line,
                true, false, "Player", List.of(), List.of(), null, "");
        var payload = new CampaignConversationViewPayload(request.viewId(), 4, host.character, 0, 1, List.of(), session, "");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            CampaignConversationViewPayload.STREAM_CODEC.encode(buffer, payload);
            helper.assertValueEqual(CampaignConversationViewPayload.STREAM_CODEC.decode(buffer), payload, "optional camera and voice use a bounded payload");
        } finally { buffer.release(); }
        controller.accept(payload);
        helper.assertValueEqual(controller.presentationLine(), line, "acknowledged speech is available to the renderer");
        controller.accept(payload);
        helper.assertValueEqual(host.texts.size(), 1, "polling does not restart the line");
        controller.accept(new CampaignConversationViewPayload(request.viewId(), 4, host.character, 0, 1, List.of(), null, "Character unavailable."));
        helper.assertTrue(controller.presentationLine() == null, "unavailable view pauses optional media");
        helper.assertValueEqual(controller.session().line().text(), "Welcome.", "text remains readable during the pause");
        controller.accept(payload);
        helper.assertValueEqual(controller.presentationLine(), line, "restored view retains the same presentation identity");
        helper.succeed();
    }

    private static CampaignConversationViewPayload message(CampaignConversationRequestPayload request, UUID id, long revision, String key, String text, boolean control) {
        var line = new Line(key, request.character(), request.entityId(), "Mira", text, 0, 1);
        var session = new Session(id, revision, "Guidance", ConversationSession.Phase.EXCHANGES, line, control, false, "Player", List.of(), List.of(), null, "");
        return new CampaignConversationViewPayload(request.viewId(), request.entityId(), request.character(), 0, 1, List.of(), session, "");
    }

    @GameTest(template = "empty", batch = "conversation_payloads")
    public static void historyAndRecoveryControlsPreserveWireBoundsAndStopAdvancement(GameTestHelper helper) {
        var host = new Host();
        var controller = new CampaignConversationScreenController(host);
        controller.tick();
        var request = host.sent.getFirst();
        var base = message(request, UUID.randomUUID(), 1, "1/mentor", "Welcome.", true);
        var prior = base.session();
        var state = new Session(prior.id(), prior.revision(), prior.title(), prior.phase(), prior.line(), true, false, "Player",
                List.of(), List.of(new Response("wait", "Ask again", false, "Discover the ledger first.")), null, "", null, List.of(),
                true, true, new History(0, 2, List.of("Mira: Welcome.", "Player: I will help.")));
        var payload = new CampaignConversationViewPayload(request.viewId(), 4, request.character(), 0, 1, List.of(), state, "");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            CampaignConversationViewPayload.STREAM_CODEC.encode(buffer, payload);
            helper.assertValueEqual(CampaignConversationViewPayload.STREAM_CODEC.decode(buffer), payload, "history, reasons and recovery permissions survive the wire");
        } finally { buffer.release(); }
        controller.accept(payload);
        controller.addSessionOptions();
        helper.assertTrue(controller.presentationLine() == null, "history cannot move the camera or play a live voice");
        helper.assertFalse(host.options.stream().anyMatch(option -> option.label().equals("conversation.continue")), "history cannot advance the conversation");
        helper.assertTrue(host.texts.getLast().contains("I will help"), "history includes player choices");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_payloads")
    public static void automaticAdvancementWaitsForReadingAndVoiceCompletion(GameTestHelper helper) {
        var host = new Host();
        var controller = new CampaignConversationScreenController(host);
        controller.tick();
        var request = host.sent.getFirst();
        controller.accept(message(request, UUID.randomUUID(), 1, "1/mentor", "Welcome.", true));
        controller.addSessionOptions();
        host.options.stream().filter(option -> option.label().equals("conversation.auto_off")).findFirst().orElseThrow().action().run();
        host.read = false;
        host.now = 2000;
        controller.tick();
        helper.assertValueEqual(host.sent.getLast().action(), Action.VIEW, "unread text pauses automatic advancement");
        host.read = true;
        host.playing = true;
        host.now = 4000;
        controller.tick();
        helper.assertValueEqual(host.sent.getLast().action(), Action.VIEW, "a playing voice pauses automatic advancement");
        host.playing = false;
        host.now = 6000;
        controller.tick();
        helper.assertValueEqual(host.sent.getLast().action(), Action.ADVANCE, "read text and finished voice advance once");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_payloads")
    public static void completedHistoryTopicsOpenReadOnlyPagesAndReturnToTopics(GameTestHelper helper) {
        var host = new Host();
        var controller = new CampaignConversationScreenController(host);
        controller.tick();
        var request = host.sent.getFirst();
        UUID archived = UUID.randomUUID();
        controller.accept(new CampaignConversationViewPayload(request.viewId(), 4, request.character(), 0, 1,
                List.of(new Topic("fixture:lore", archived.toString(), "Earlier guidance", true, TopicGroup.HISTORY)), null, ""));
        controller.addTopics();
        host.options.stream().filter(option -> option.label().equals("Earlier guidance")).findFirst().orElseThrow().action().run();
        helper.assertValueEqual(host.sent.getLast().action(), Action.HISTORY, "archived topic requests read-only history");
        helper.assertValueEqual(host.sent.getLast().session(), archived, "archived topic selects the saved session identity");
        var saved = new Session(archived, 7, "Guidance", ConversationSession.Phase.COMPLETE, null, false, false, "",
                List.of(), List.of(), null, "farewell", null, List.of(), false, false, new History(0, 1, List.of("Mira: Farewell.")));
        controller.accept(new CampaignConversationViewPayload(request.viewId(), 4, request.character(), 0, 1, List.of(), saved, ""));
        host.options.clear();
        controller.addSessionOptions();
        host.options.stream().filter(option -> option.label().equals("conversation.back")).findFirst().orElseThrow().action().run();
        helper.assertValueEqual(host.sent.getLast().action(), Action.TOPICS, "finished history returns to the provider's topics");
        helper.succeed();
    }

    private static final class Host implements CampaignConversationScreenController.Host {
        final UUID character = UUID.randomUUID();
        final List<CampaignConversationRequestPayload> sent = new ArrayList<>();
        final List<Option> options = new ArrayList<>();
        final List<String> texts = new ArrayList<>();
        long now;
        boolean read = true;
        boolean playing;
        @Override public boolean textRead() { return read; }
        @Override public boolean voicePlaying() { return playing; }
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
