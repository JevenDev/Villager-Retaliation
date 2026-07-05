package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;

public final class VillagerChatTextFormatter {
    private VillagerChatTextFormatter() {
    }

    public static void onClientChatReceived(ClientChatReceivedEvent event) {
        if (VillagerRetaliationConfig.DISABLE_DIALOGUE_TEXT_EFFECTS.get()) {
            return;
        }

        Component formatted = format(event.getMessage());
        if (formatted != event.getMessage()) {
            event.setMessage(formatted);
        }
    }

    static Component format(Component message) {
        String text = message.getString();
        if (text.isBlank() || text.indexOf('<') < 0 || text.indexOf('>') < 0) {
            return message;
        }

        List<DialogueTextSegment> segments = DialogueTextSegment.parse(text, DialogueTextEffects.NONE);
        if (segments.stream().noneMatch(segment -> segment.effects().active())) {
            return message;
        }

        Component formatted = VillagerStyledTextRenderer.component(segments, message.getStyle(), null);
        VillagerAnimatedChatText.remember(segments);
        return formatted;
    }
}
