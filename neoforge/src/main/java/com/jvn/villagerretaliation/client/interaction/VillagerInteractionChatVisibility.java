package com.jvn.villagerretaliation.client.interaction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.resources.language.I18n;

final class VillagerInteractionChatVisibility {
    private static final String CHAT_TOOLTIP_KEY = "villagerretaliation.gui.chat.tooltip";
    private static final String CHAT_TAG_KEY = "villagerretaliation.gui.chat.tag";
    private static final List<HiddenMessage> hiddenMessages = new ArrayList<>();
    private static int visibleBaselineSize;
    private static boolean hiding;

    private VillagerInteractionChatVisibility() {
    }

    static void hidePreviousVillagerMessages(Minecraft minecraft) {
        restoreHiddenVillagerMessages(minecraft);
        ChatComponent chat = chat(minecraft);
        if (chat == null) {
            return;
        }

        List<GuiMessage> messages = chat.allMessages;
        if (messages.isEmpty()) {
            return;
        }

        for (int index = messages.size() - 1; index >= 0; index--) {
            GuiMessage message = messages.get(index);
            if (!isVillagerChatTag(message.tag())) {
                continue;
            }

            hiddenMessages.add(new HiddenMessage(index, message));
            messages.remove(index);
        }

        if (hiddenMessages.isEmpty()) {
            return;
        }
        hiddenMessages.sort(Comparator.comparingInt(HiddenMessage::index));
        visibleBaselineSize = messages.size();
        hiding = true;
        chat.refreshTrimmedMessages();
    }

    static void restoreHiddenVillagerMessages(Minecraft minecraft) {
        if (!hiding || hiddenMessages.isEmpty()) {
            return;
        }

        ChatComponent chat = chat(minecraft);
        if (chat == null) {
            hiddenMessages.clear();
            hiding = false;
            visibleBaselineSize = 0;
            return;
        }

        List<GuiMessage> messages = chat.allMessages;
        if (messages.size() >= visibleBaselineSize) {
            int addedWhileHidden = messages.size() - visibleBaselineSize;
            for (HiddenMessage hidden : hiddenMessages) {
                int insertionIndex = Math.min(messages.size(), hidden.index() + addedWhileHidden);
                messages.add(insertionIndex, hidden.message());
            }
            chat.refreshTrimmedMessages();
        }

        hiddenMessages.clear();
        hiding = false;
        visibleBaselineSize = 0;
    }

    private static ChatComponent chat(Minecraft minecraft) {
        return minecraft == null || minecraft.gui == null ? null : minecraft.gui.getChat();
    }

    private static boolean isVillagerChatTag(GuiMessageTag tag) {
        if (tag == null || tag.icon() != null) {
            return false;
        }
        String expectedLogTag = I18n.get(CHAT_TAG_KEY);
        String expectedTooltip = I18n.get(CHAT_TOOLTIP_KEY);
        return expectedLogTag.equals(tag.logTag()) && expectedTooltip.equals(tag.text().getString());
    }

    private record HiddenMessage(int index, GuiMessage message) {
    }
}
