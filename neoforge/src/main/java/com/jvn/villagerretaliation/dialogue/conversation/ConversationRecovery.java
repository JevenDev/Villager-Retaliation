package com.jvn.villagerretaliation.dialogue.conversation;

import net.minecraft.server.level.ServerPlayer;

public final class ConversationRecovery {
    private ConversationRecovery() { }

    public static boolean safe(ConversationSession session) {
        return session.active() && session.phase != ConversationSession.Phase.NODE_EFFECTS
                && session.phase != ConversationSession.Phase.RESPONSE_EFFECTS && session.nestedDecision == null
                && session.receipts.values().stream().noneMatch(receipt ->
                        receipt.state() == ConversationSession.ReceiptState.PREPARED || receipt.result().suspended());
    }

    public static boolean canControl(ServerPlayer player, ConversationSession session) {
        return safe(session) && authorizedController(player, session);
    }

    public static boolean canCancel(ServerPlayer player, ConversationSession session) {
        return session.active() && session.nestedDecision == null
                && session.receipts.values().stream().noneMatch(receipt ->
                        receipt.state() == ConversationSession.ReceiptState.PREPARED || receipt.result().suspended())
                && authorizedController(player, session);
    }

    private static boolean authorizedController(ServerPlayer player, ConversationSession session) {
        if (!ConversationService.member(player, session) || !ConversationService.canControl(player, session)) return false;
        if (session.controller == null || session.controller.equals(player.getUUID())) return true;
        var controller = player.server.getPlayerList().getPlayer(session.controller);
        var primary = ConversationCharacters.primary(player, session);
        return controller == null || !ConversationService.member(controller, session) || !ConversationService.canControl(controller, session)
                || primary == null || !ConversationService.near(controller, primary);
    }

    public static boolean canMigrate(ServerPlayer player, ConversationSession session) {
        var definition = ConversationResources.conversation(player.server, session.identity.conversation()).orElse(null);
        return definition != null && canControl(player, session) && !session.compatibilityFingerprint.isEmpty()
                && definition.compatibilityFingerprint().equals(session.compatibilityFingerprint)
                && (definition.revision() != session.contentRevision || !definition.fingerprint().equals(session.contentFingerprint));
    }

    static void apply(ServerPlayer player, ConversationSession session, ConversationSession.Intent intent) {
        ConversationService.require(intent == ConversationSession.Intent.CANCEL ? canCancel(player, session) : canControl(player, session),
                "An effect still has an uncertain result or a running dependency. Finish it and obtain conversation control before recovery.");
        if (intent == ConversationSession.Intent.MIGRATE) {
            ConversationService.require(canMigrate(player, session), "Conversation gameplay changed. Restore the saved content before resuming.");
            var definition = ConversationResources.conversation(player.server, session.identity.conversation()).orElseThrow();
            session.recoveryHistory.add("migrate " + session.contentRevision + "/" + session.contentFingerprint + " to "
                    + definition.revision() + "/" + definition.fingerprint() + " by " + player.getUUID()
                    + " at " + player.serverLevel().getGameTime());
            session.contentRevision = definition.revision();
            session.contentFingerprint = definition.fingerprint();
        } else {
            session.recoveryHistory.add("cancel by " + player.getUUID() + " at " + player.serverLevel().getGameTime());
            session.confirmation = null;
            session.pendingChoice = null;
            session.consent = new net.minecraft.nbt.CompoundTag();
            session.controller = null;
            session.phase = ConversationSession.Phase.CANCELLED;
        }
        session.diagnostic = "";
    }
}
