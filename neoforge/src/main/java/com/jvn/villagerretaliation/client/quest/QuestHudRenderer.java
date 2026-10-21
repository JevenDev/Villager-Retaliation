package com.jvn.villagerretaliation.client.quest;

import com.jvn.villagerretaliation.network.QuestTrackerSyncPayload;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class QuestHudRenderer {
    private QuestHudRenderer() { }

    public static int trackerWidth(int screenWidth) { return VrQuestPresentation.HUD.trackerWidth(screenWidth); }
    public static int visibleTrackerEntryCount(boolean showRecentQuests, int entryCount) {
        return VrQuestPresentation.HUD.visibleTrackerEntryCount(showRecentQuests, entryCount);
    }
    public static int trackerHeight(Font font, List<QuestTrackerSyncPayload.Entry> entries, int entryCount, int width) {
        return VrQuestPresentation.HUD.trackerHeight(font, entries.stream().map(VrQuestJournalData::shared).toList(), entryCount, width);
    }
    public static int notificationWidth(Font font, QuestTrackerSyncPayload.Entry entry, int screenWidth) {
        return VrQuestPresentation.HUD.notificationWidth(font, VrQuestJournalData.shared(entry), screenWidth);
    }
    public static int panelGap() { return VrQuestPresentation.HUD.panelGap(); }
    public static int notificationHeight(Font font, QuestTrackerSyncPayload.Entry entry, int width, int screenHeight) {
        return VrQuestPresentation.HUD.notificationHeight(font, VrQuestJournalData.shared(entry), width, screenHeight);
    }
    public static int primaryHeight() { return VrQuestPresentation.HUD.primaryHeight(); }
    public static int secondaryHeight(Font font, QuestTrackerSyncPayload.Entry entry, int width) {
        return VrQuestPresentation.HUD.secondaryHeight(font, VrQuestJournalData.shared(entry), width);
    }
    public static int slideDistance() { return VrQuestPresentation.HUD.slideDistance(); }
    public static void renderNotification(GuiGraphics graphics, Font font, QuestTrackerSyncPayload.Entry entry,
            int x, int y, int width, int height, float alpha, int age) {
        VrQuestPresentation.HUD.renderNotification(graphics, font, VrQuestJournalData.shared(entry), x, y, width, height, alpha, age);
    }
    public static void renderEntry(GuiGraphics graphics, Font font, QuestTrackerSyncPayload.Entry entry,
            int x, int y, int width, int height, float alpha, boolean primary, int age) {
        VrQuestPresentation.HUD.renderEntry(graphics, font, VrQuestJournalData.shared(entry), x, y, width, height, alpha, primary, age);
    }
}
