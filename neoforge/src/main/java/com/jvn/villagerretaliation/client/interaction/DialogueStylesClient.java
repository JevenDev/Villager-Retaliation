package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.dialogue.resources.DialogueStyleResources;
import com.jvn.villagerretaliation.network.DialogueStylesPayload;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

public final class DialogueStylesClient {
    private DialogueStylesClient() { }

    public static void accept(DialogueStylesPayload payload) {
        DialogueStyleResources.replaceClient(payload.styles());
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) { DialogueStyleResources.replaceClient(java.util.Map.of()); }
}
