package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

public final class DialogueChoiceRequests {
    private static final InteractionChoiceLedger CHOICES = new InteractionChoiceLedger();
    private DialogueChoiceRequests() { }

    public static List<DialogueOptionDefinition> publish(ServerPlayer player, int entityId, List<DialogueOptionDefinition> options) {
        var published = CHOICES.publish(player, target(player, entityId), options.stream()
                .map(option -> new InteractionMenu.Choice(option.id(), option.label(), option.available(), option.unavailableReason())).toList());
        var result = new java.util.ArrayList<DialogueOptionDefinition>();
        for (int index = 0; index < options.size(); index++) result.add(options.get(index).withSelectionToken(published.get(index).token()));
        return List.copyOf(result);
    }

    public static boolean consume(ServerPlayer player, int entityId, String optionId, UUID token) {
        return CHOICES.consume(player, target(player, entityId), optionId, token);
    }

    private static InteractionChoiceLedger.Target target(ServerPlayer player, int entityId) {
        var actor = player.serverLevel().getEntity(entityId);
        var session = InteractionSessions.current(player).orElse(null);
        var actorId = actor == null ? null : actor.getUUID();
        var sessionId = session != null && session.actorId().equals(actorId) ? session.id() : null;
        return new InteractionChoiceLedger.Target(entityId, actorId, sessionId, null);
    }

    public static void handle(ServerPlayer player, com.jvn.villagerretaliation.network.VillagerDialogueRequestPayload request) {
        boolean navigation = request.selectionToken() == null && (request.optionId().equals("leave")
                || request.optionId().equals(com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService.LEAVE_OPTION_ID)
                || request.optionId().equals(com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueService.SPECIAL_ORDER_STATUS_ROOT_OPTION_ID));
        if (navigation || consume(player, request.entityId(), request.optionId(), request.selectionToken())) {
            VillagerInteractionService.handleDialogueRequest(player, request.entityId(), request.optionId(), request.greetingRequestId());
        }
    }
}
