package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;

public final class VillagerChatTextFormatter {
    private VillagerChatTextFormatter() {
    }

    public static void onClientChatReceived(ClientChatReceivedEvent event) {
        if (!VillagerChatEffectRenderer.animatedChatEffectsEnabled()) {
            Component stripped = stripEffectMarkup(event.getMessage());
            if (stripped != event.getMessage()) {
                event.setMessage(stripped);
            }
            return;
        }

        Component formatted = format(event.getMessage());
        if (formatted != event.getMessage()) {
            event.setMessage(formatted);
        }
    }

    static Component format(Component message) {
        return formatMarkup(message, false);
    }

    private static Component stripEffectMarkup(Component message) {
        return formatMarkup(message, true);
    }

    private static Component formatMarkup(Component message, boolean stripOnly) {
        var styles = com.jvn.villagerretaliation.dialogue.resources.DialogueStyleResources.current();
        int[] cues = {0};
        return com.jvn.toucanlib.client.ToucanComponentMarkup.format(message,
                VillagerChatEffectRenderer::animatedChatEffectsEnabled, stripOnly, tag -> {
                    var preset = styles.get(tag);
                    return preset == null ? null : base -> preset.apply(DialogueTextEffects.fromStyle(base)).toStyle();
                }, tag -> com.jvn.villagerretaliation.dialogue.DialogueCue.parse(tag) != null
                        && cues[0]++ < com.jvn.villagerretaliation.dialogue.DialogueCue.MAX_CUES);
    }
}
