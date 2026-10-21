package com.jvn.villagerretaliation.client.quest;

import java.util.Map;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

public record QuestPresentation(Map<Texture, ResourceLocation> textures, Map<Color, Integer> colors,
        String translationPrefix, int maxHudEntries) {
    public QuestPresentation {
        textures = Map.copyOf(textures);
        colors = Map.copyOf(colors);
        translationPrefix = Objects.requireNonNull(translationPrefix);
        if (maxHudEntries < 1) throw new IllegalArgumentException("HUD entry count must be positive");
    }

    public ResourceLocation texture(Texture texture) {
        return Objects.requireNonNull(this.textures.get(texture), "Missing quest texture: " + texture);
    }

    public int color(Color color) {
        return Objects.requireNonNull(this.colors.get(color), "Missing quest color: " + color);
    }

    public String key(String key) { return this.translationPrefix + key; }
    public MutableComponent text(String key, Object... arguments) { return Component.translatable(key(key), arguments); }

    public enum Texture {
        SCROLLBAR,
        SCROLLER,
        SCROLLER_HIGHLIGHT,
        QUEST_NUMBER,
        ENTRY_1,
        ENTRY_2,
        ENTRY_HIGHLIGHT,
        SELECTED_QUEST,
        CONTAINER,
        CONTAINER_OVERLAY,
        ICON_UPDATE,
        ICON_UPDATE_SELECTED_QUEST,
        DIVIDER,
        ICON_QUEST_STEP_COMPLETED,
        ICON_QUEST_STEP,
        BOOKMARK_RED,
        BOOKMARK_ICON_AVAILABLE,
        BOOKMARK_PURPLE,
        BOOKMARK_ICON_ACTIVE,
        BOOKMARK_TEAL,
        BOOKMARK_ICON_COMPLETED,
        ICON_AVAILABLE,
        ICON_ACTIVE,
        ICON_INACTIVE,
        ICON_COMPLETED,
        ICON_SELECTED_QUEST_COMPLETED,
        ICON_SELECTED_QUEST,
        HUD_PANEL
    }

    public enum Color {
        TEXT_COLOR,
        TITLE_COLOR,
        QUEST_TITLE_TEXT_COLOR,
        NO_QUEST_TITLE_OUTLINE,
        MUTED_TEXT_COLOR,
        SELECTED_TEXT_COLOR,
        HOVERED_TEXT_COLOR,
        LINK_TEXT_COLOR,
        QUESTLINE_LABEL_COLOR,
        QUESTLINE_PROGRESS_COLOR,
        QUESTLINE_TOOLTIP_LABEL_COLOR,
        QUESTLINE_TOOLTIP_NAME_COLOR,
        QUESTLINE_TOOLTIP_PROGRESS_COLOR,
        QUESTLINE_NODE_TOOLTIP_DESCRIPTION_COLOR,
        QUESTLINE_NODE_TOOLTIP_ACTION_COLOR,
        QUESTLINE_NODE_TOOLTIP_ACTIVE_COLOR,
        QUESTLINE_NODE_TOOLTIP_COMPLETED_COLOR,
        QUESTLINE_NODE_TOOLTIP_CLOSED_COLOR,
        QUESTLINE_NODE_TOOLTIP_LOCKED_COLOR,
        QUEST_COUNT_TEXT_COLOR,
        PAGE_TEXT_COLOR,
        LIGHTEST_JOURNAL_ENTRY_COLOR,
        QUEST_COUNT_ACTIVE,
        QUEST_COUNT_ACCEPTED,
        QUEST_COUNT_COMPLETED,
        QUEST_COUNT_NEARBY,
        HUD_ACCENT_COLOR,
        HUD_TITLE_COLOR,
        HUD_TEXT_COLOR,
        HUD_MUTED_TEXT_COLOR,
        HUD_BAR_BACKGROUND_COLOR,
        HUD_EDGE_HIGHLIGHT_COLOR
    }
}
