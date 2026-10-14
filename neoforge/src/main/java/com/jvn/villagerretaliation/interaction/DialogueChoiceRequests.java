package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;

public final class DialogueChoiceRequests {
    private record View(int entityId, Map<String, UUID> tokens) { }
    private static final Map<ServerPlayer, View> VIEWS = new WeakHashMap<>();
    private DialogueChoiceRequests() { }

    public static List<DialogueOptionDefinition> publish(ServerPlayer player, int entityId, List<DialogueOptionDefinition> options) {
        options = options.stream().map(option -> option.withSelectionToken(UUID.randomUUID())).toList();
        var tokens = new java.util.LinkedHashMap<String, UUID>();
        for (var option : options) if (option.available()) tokens.put(option.id(), option.selectionToken());
        VIEWS.put(player, new View(entityId, Map.copyOf(tokens)));
        return options;
    }

    public static boolean consume(ServerPlayer player, int entityId, String optionId, UUID token) {
        var view = VIEWS.get(player);
        if (token == null || view == null || view.entityId() != entityId || !token.equals(view.tokens().get(optionId))) return false;
        VIEWS.remove(player);
        return true;
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
