package com.jvn.villagerretaliation.dialogue.conversation;

import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import net.minecraft.server.level.ServerPlayer;

public final class ConversationText {
    private ConversationText() { }

    public static String text(ServerPlayer viewer, ConversationSession.Line line) {
        return resolve(viewer, line.textKey(), line.fallback(), line.replacements(), line.localized());
    }

    public static String resolve(ServerPlayer viewer, String key, String fallback, java.util.Map<String, String> replacements, boolean localized) {
        if (!localized) return fallback;
        return QuestContentCatalogs.current(viewer.server).localization()
                .plainText(viewer.clientInformation().language(), key)
                .map(template -> VillagerDialogueResources.resolveTemplate(template, replacements))
                .orElse(fallback);
    }
}
