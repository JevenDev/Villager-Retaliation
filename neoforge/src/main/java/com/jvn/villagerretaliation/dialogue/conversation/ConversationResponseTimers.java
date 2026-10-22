package com.jvn.villagerretaliation.dialogue.conversation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

final class ConversationResponseTimers {
    private ConversationResponseTimers() { }

    static void arm(ConversationSession session, ConversationDefinition definition, long time) {
        if (session.phase != ConversationSession.Phase.RESPONSES || session.responseTimerResolved || session.responseDeadline >= 0) return;
        var node = definition.nodes().get(session.node);
        if (node != null && node.responseTimer() != null)
            session.responseDeadline = Math.addExact(time, node.responseTimer().durationTicks());
    }

    static boolean expired(ConversationSession session, long time) {
        return session.phase == ConversationSession.Phase.RESPONSES && !session.responseTimerResolved
                && session.responseDeadline >= 0 && time >= session.responseDeadline;
    }

    static boolean expire(ServerPlayer player, ConversationSavedData data, ConversationSession session) throws IOException {
        if (!expired(session, player.serverLevel().getGameTime())) return false;
        var definition = ConversationService.definition(player.server, session);
        var timer = definition.nodes().get(session.node).responseTimer();
        ConversationService.require(timer != null, "The saved response timer is no longer available.");
        ConversationService.require(player.getUUID().equals(session.controller) && ConversationService.member(player, session)
                && ConversationService.canControl(player, session), "The conversation controller must return to resolve its deadline.");
        var character = ConversationService.character(player, session);
        ConversationService.require(!ConversationActors.interrupted(player, character),
                "Wait for the current interruption to finish.");
        ConversationService.require(ConversationService.near(player, character),
                "Return to the conversation to resolve its deadline.");
        var request = UUID.nameUUIDFromBytes((session.identity.id() + "/" + session.visit + "/timeout").getBytes(StandardCharsets.UTF_8));
        // an unavailable default must leave the original visit open for manual choices
        session = new ConversationSession(session);
        session.responseTimerResolved = true;
        if (!timer.response().isBlank()) {
            try { ConversationService.choose(player, session, definition, request, timer.response()); }
            catch (ConversationService.Unavailable unavailable) { return false; }
        } else {
            ConversationService.enter(session, timer.next());
        }
        ConversationService.save(player, data, session);
        ConversationRuntime.drive(player, session.identity.id());
        return true;
    }

    static long remaining(ServerPlayer player, UUID id) {
        var session = ConversationSavedData.get(player.serverLevel()).session(id);
        return session == null || session.phase != ConversationSession.Phase.RESPONSES || session.responseTimerResolved
                || session.responseDeadline < 0 ? -1 : Math.max(0, session.responseDeadline - player.serverLevel().getGameTime());
    }
}
