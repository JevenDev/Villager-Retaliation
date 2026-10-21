package com.jvn.villagerretaliation.client.quest;

import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import com.jvn.villagerretaliation.quest.tracking.QuestTrackerLimits;
import java.util.Map;

public final class VrQuestPresentation {
    public static final QuestPresentation DEFAULT = new QuestPresentation(
            Map.ofEntries(
                    Map.entry(QuestPresentation.Texture.SCROLLBAR, VillagerRetaliationClientAssets.QUEST_JOURNAL_SCROLLBAR_TEXTURE),
                    Map.entry(QuestPresentation.Texture.SCROLLER, VillagerRetaliationClientAssets.QUEST_JOURNAL_SCROLLER_TEXTURE),
                    Map.entry(QuestPresentation.Texture.SCROLLER_HIGHLIGHT, VillagerRetaliationClientAssets.QUEST_JOURNAL_SCROLLER_HIGHLIGHT_TEXTURE),
                    Map.entry(QuestPresentation.Texture.QUEST_NUMBER, VillagerRetaliationClientAssets.QUEST_JOURNAL_QUEST_NUMBER_TEXTURE),
                    Map.entry(QuestPresentation.Texture.ENTRY_1, VillagerRetaliationClientAssets.QUEST_JOURNAL_ENTRY_1_TEXTURE),
                    Map.entry(QuestPresentation.Texture.ENTRY_2, VillagerRetaliationClientAssets.QUEST_JOURNAL_ENTRY_2_TEXTURE),
                    Map.entry(QuestPresentation.Texture.ENTRY_HIGHLIGHT, VillagerRetaliationClientAssets.QUEST_JOURNAL_ENTRY_HIGHLIGHT_TEXTURE),
                    Map.entry(QuestPresentation.Texture.SELECTED_QUEST, VillagerRetaliationClientAssets.QUEST_JOURNAL_SELECTED_QUEST_TEXTURE),
                    Map.entry(QuestPresentation.Texture.CONTAINER, VillagerRetaliationClientAssets.QUEST_JOURNAL_CONTAINER_TEXTURE),
                    Map.entry(QuestPresentation.Texture.CONTAINER_OVERLAY, VillagerRetaliationClientAssets.QUEST_JOURNAL_CONTAINER_OVERLAY_TEXTURE),
                    Map.entry(QuestPresentation.Texture.ICON_UPDATE, VillagerRetaliationClientAssets.QUEST_JOURNAL_ICON_UPDATE_TEXTURE),
                    Map.entry(QuestPresentation.Texture.ICON_UPDATE_SELECTED_QUEST, VillagerRetaliationClientAssets.QUEST_JOURNAL_ICON_UPDATE_SELECTED_QUEST_TEXTURE),
                    Map.entry(QuestPresentation.Texture.DIVIDER, VillagerRetaliationClientAssets.QUEST_JOURNAL_DIVIDER_TEXTURE),
                    Map.entry(QuestPresentation.Texture.ICON_QUEST_STEP_COMPLETED, VillagerRetaliationClientAssets.QUEST_JOURNAL_ICON_QUEST_STEP_COMPLETED_TEXTURE),
                    Map.entry(QuestPresentation.Texture.ICON_QUEST_STEP, VillagerRetaliationClientAssets.QUEST_JOURNAL_ICON_QUEST_STEP_TEXTURE),
                    Map.entry(QuestPresentation.Texture.BOOKMARK_RED, VillagerRetaliationClientAssets.QUEST_JOURNAL_BOOKMARK_RED_TEXTURE),
                    Map.entry(QuestPresentation.Texture.BOOKMARK_ICON_AVAILABLE, VillagerRetaliationClientAssets.QUEST_JOURNAL_BOOKMARK_ICON_AVAILABLE_TEXTURE),
                    Map.entry(QuestPresentation.Texture.BOOKMARK_PURPLE, VillagerRetaliationClientAssets.QUEST_JOURNAL_BOOKMARK_PURPLE_TEXTURE),
                    Map.entry(QuestPresentation.Texture.BOOKMARK_ICON_ACTIVE, VillagerRetaliationClientAssets.QUEST_JOURNAL_BOOKMARK_ICON_ACTIVE_TEXTURE),
                    Map.entry(QuestPresentation.Texture.BOOKMARK_TEAL, VillagerRetaliationClientAssets.QUEST_JOURNAL_BOOKMARK_TEAL_TEXTURE),
                    Map.entry(QuestPresentation.Texture.BOOKMARK_ICON_COMPLETED, VillagerRetaliationClientAssets.QUEST_JOURNAL_BOOKMARK_ICON_COMPLETED_TEXTURE),
                    Map.entry(QuestPresentation.Texture.ICON_AVAILABLE, VillagerRetaliationClientAssets.QUEST_JOURNAL_ICON_AVAILABLE_TEXTURE),
                    Map.entry(QuestPresentation.Texture.ICON_ACTIVE, VillagerRetaliationClientAssets.QUEST_JOURNAL_ICON_ACTIVE_TEXTURE),
                    Map.entry(QuestPresentation.Texture.ICON_INACTIVE, VillagerRetaliationClientAssets.QUEST_JOURNAL_ICON_INACTIVE_TEXTURE),
                    Map.entry(QuestPresentation.Texture.ICON_COMPLETED, VillagerRetaliationClientAssets.QUEST_JOURNAL_ICON_COMPLETED_TEXTURE),
                    Map.entry(QuestPresentation.Texture.ICON_SELECTED_QUEST_COMPLETED, VillagerRetaliationClientAssets.QUEST_JOURNAL_ICON_SELECTED_QUEST_COMPLETED_TEXTURE),
                    Map.entry(QuestPresentation.Texture.ICON_SELECTED_QUEST, VillagerRetaliationClientAssets.QUEST_JOURNAL_ICON_SELECTED_QUEST_TEXTURE),
                    Map.entry(QuestPresentation.Texture.HUD_PANEL, VillagerRetaliationClientAssets.INTERACTION_CONTAINER_OPTION_TEXTURE)),
            Map.ofEntries(
                    Map.entry(QuestPresentation.Color.TEXT_COLOR, 0xFF000000),
                    Map.entry(QuestPresentation.Color.TITLE_COLOR, 0xFF000000),
                    Map.entry(QuestPresentation.Color.QUEST_TITLE_TEXT_COLOR, 0xFF000000),
                    Map.entry(QuestPresentation.Color.NO_QUEST_TITLE_OUTLINE, 0x00000000),
                    Map.entry(QuestPresentation.Color.MUTED_TEXT_COLOR, 0xFF000000),
                    Map.entry(QuestPresentation.Color.SELECTED_TEXT_COLOR, 0xFFFFFFFF),
                    Map.entry(QuestPresentation.Color.HOVERED_TEXT_COLOR, 0xFF000000),
                    Map.entry(QuestPresentation.Color.LINK_TEXT_COLOR, 0xFF315B8A),
                    Map.entry(QuestPresentation.Color.QUESTLINE_LABEL_COLOR, 0xFF4A3828),
                    Map.entry(QuestPresentation.Color.QUESTLINE_PROGRESS_COLOR, 0xFF4F6F36),
                    Map.entry(QuestPresentation.Color.QUESTLINE_TOOLTIP_LABEL_COLOR, 0xFFF9CB5F),
                    Map.entry(QuestPresentation.Color.QUESTLINE_TOOLTIP_NAME_COLOR, 0xFF5C96EF),
                    Map.entry(QuestPresentation.Color.QUESTLINE_TOOLTIP_PROGRESS_COLOR, 0xFFB5F45B),
                    Map.entry(QuestPresentation.Color.QUESTLINE_NODE_TOOLTIP_DESCRIPTION_COLOR, 0xFFA0A0A0),
                    Map.entry(QuestPresentation.Color.QUESTLINE_NODE_TOOLTIP_ACTION_COLOR, 0xFF5C96EF),
                    Map.entry(QuestPresentation.Color.QUESTLINE_NODE_TOOLTIP_ACTIVE_COLOR, 0xFFF9CB5F),
                    Map.entry(QuestPresentation.Color.QUESTLINE_NODE_TOOLTIP_COMPLETED_COLOR, 0xFFB5F45B),
                    Map.entry(QuestPresentation.Color.QUESTLINE_NODE_TOOLTIP_CLOSED_COLOR, 0xFFF06A6A),
                    Map.entry(QuestPresentation.Color.QUESTLINE_NODE_TOOLTIP_LOCKED_COLOR, 0xFFA0A0A0),
                    Map.entry(QuestPresentation.Color.QUEST_COUNT_TEXT_COLOR, 0xFFFFFFFF),
                    Map.entry(QuestPresentation.Color.PAGE_TEXT_COLOR, 0xFFE1DAC7),
                    Map.entry(QuestPresentation.Color.LIGHTEST_JOURNAL_ENTRY_COLOR, 0xFFF6F0DF),
                    Map.entry(QuestPresentation.Color.QUEST_COUNT_ACTIVE, 0xF9CB5F),
                    Map.entry(QuestPresentation.Color.QUEST_COUNT_ACCEPTED, 0xE1DAC7),
                    Map.entry(QuestPresentation.Color.QUEST_COUNT_COMPLETED, 0xB5F45B),
                    Map.entry(QuestPresentation.Color.QUEST_COUNT_NEARBY, 0x5C96EF),
                    Map.entry(QuestPresentation.Color.HUD_ACCENT_COLOR, 0xFFFFD166),
                    Map.entry(QuestPresentation.Color.HUD_TITLE_COLOR, 0xFFFFF0C8),
                    Map.entry(QuestPresentation.Color.HUD_TEXT_COLOR, 0xFFE9EEF5),
                    Map.entry(QuestPresentation.Color.HUD_MUTED_TEXT_COLOR, 0xFFB8C3D0),
                    Map.entry(QuestPresentation.Color.HUD_BAR_BACKGROUND_COLOR, 0x80373A42),
                    Map.entry(QuestPresentation.Color.HUD_EDGE_HIGHLIGHT_COLOR, 0x2CFFFFFF)),
            "villagerretaliation.gui.", QuestTrackerLimits.MAX_TRACKED_QUESTS);
    public static final QuestHud HUD = new QuestHud(DEFAULT);

    private VrQuestPresentation() { }
}
