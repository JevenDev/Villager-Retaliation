package com.jvn.villagerretaliation.client.interaction;

import com.jvn.toucanlib.client.ToucanScrollState;
import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import com.jvn.villagerretaliation.client.quest.VillagerQuestKeyMappings;
import com.jvn.villagerretaliation.client.quest.VillagerQuestTrackerOverlay;
import com.jvn.villagerretaliation.client.ui.VillagerClientUiUtil;
import com.jvn.villagerretaliation.network.QuestTrackerSyncPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

public final class VillagerQuestJournalScreen extends Screen {
    private static final float OPTION_SCROLL_LERP = 0.32F;
    private static final float OPTION_SCROLL_STEP = 12.0F;
    private static final float DETAIL_SCROLL_STEP = 16.0F;
    private static final float JOURNAL_ANIMATION_DURATION_MILLIS = 280.0F;

    private static final int JOURNAL_WIDTH = 351;
    private static final int JOURNAL_HEIGHT = 215;
    private static final int BOOKMARK_WIDTH = 25;
    private static final int BOOKMARK_HEIGHT = 30;
    private static final int BOOKMARK_LEFT_OFFSET = 13;
    private static final int BOOKMARK_TOP_OFFSET = 206;
    private static final int BOOKMARK_GAP = 1;
    private static final int INACTIVE_BOOKMARK_OFFSET_Y = -5;
    private static final int TAB_TITLE_LEFT_OFFSET = 28;
    private static final int TAB_TITLE_BOTTOM_OFFSET = 37;

    private static final int QUEST_OPTION_LEFT_OFFSET = 23;
    private static final int QUEST_OPTION_TOP_OFFSET = 41;
    private static final int QUEST_OPTION_RIGHT_OFFSET = 150;
    private static final int QUEST_OPTION_BOTTOM_OFFSET = 191;
    private static final int QUEST_OPTION_WIDTH = QUEST_OPTION_RIGHT_OFFSET - QUEST_OPTION_LEFT_OFFSET;
    private static final int QUEST_OPTION_HEIGHT = 19;
    private static final int QUEST_OPTION_GAP = 0;
    private static final int QUEST_OPTION_VIEWPORT_HEIGHT = QUEST_OPTION_BOTTOM_OFFSET - QUEST_OPTION_TOP_OFFSET;
    private static final int QUEST_OPTION_TEXT_LEFT_PADDING = 15;
    private static final int QUEST_OPTION_TEXT_RIGHT_PADDING = 18;
    private static final int QUEST_OPTION_TEXT_TOP_PADDING = 6;
    private static final int QUEST_OPTION_TEXT_LINE_GAP = 2;
    private static final int QUEST_OPTION_EMPTY_TEXT_TOP_PADDING = 8;
    private static final int QUEST_OPTION_SELECTED_OVERHANG_X = 3;
    private static final int QUEST_OPTION_SELECTED_OVERHANG_Y = 2;
    private static final int QUEST_OPTION_STATE_ICON_LEFT_PADDING = 3;
    private static final int QUEST_OPTION_STATE_ICON_TOP_PADDING = 5;
    private static final int QUEST_OPTION_STATE_ICON_SIZE = 9;
    private static final int QUEST_OPTION_UPDATE_ICON_RIGHT_PADDING = 6;
    private static final int QUEST_OPTION_UPDATE_ICON_TOP_PADDING = 3;
    private static final int QUEST_OPTION_UPDATE_ICON_WIDTH = 6;
    private static final int QUEST_OPTION_UPDATE_ICON_HEIGHT = 13;
    private static final int QUEST_OPTION_SCROLLBAR_GAP = 5;
    private static final int QUEST_OPTION_SCROLLBAR_WIDTH = 4;
    private static final int QUEST_OPTION_SCROLLER_MIN_HEIGHT = 6;
    private static final int QUEST_COUNT_BADGE_WIDTH = 11;
    private static final int QUEST_COUNT_BADGE_HEIGHT = 11;
    private static final int QUEST_COUNT_BADGE_HORIZONTAL_PADDING = 2;
    private static final int QUEST_COUNT_BADGE_INNER_INSET = 1;
    private static final int QUEST_COUNT_BADGE_BOTTOM_GAP = 4;

    private static final int DETAILS_LEFT_OFFSET = 194;
    private static final int DETAILS_TOP_OFFSET = 40;
    private static final int DETAILS_WIDTH = 132;
    private static final int DETAILS_HEIGHT = 146;
    private static final int DETAILS_LINE_STEP = 11;
    private static final int DETAILS_PROGRESS_RESERVED_HEIGHT = 15;
    private static final int DETAILS_PROGRESS_HEIGHT = 3;

    private static final int TEXT_COLOR = 0xFF000000;
    private static final int TITLE_COLOR = 0xFF000000;
    private static final int MUTED_TEXT_COLOR = 0xFF000000;
    private static final int SELECTED_TEXT_COLOR = 0xFFFFFFFF;
    private static final int HOVERED_TEXT_COLOR = 0xFF000000;
    private static final int QUEST_COUNT_TEXT_COLOR = 0xFFFFFFFF;
    private static final int PROGRESS_BACKGROUND_COLOR = 0x553A2A1B;
    private static final int PROGRESS_FILL_COLOR = 0xFF9C3B22;

    private static final JournalNineSlice QUEST_JOURNAL_SCROLLBAR_NINE_SLICE =
            new JournalNineSlice(VillagerRetaliationClientAssets.QUEST_JOURNAL_SCROLLBAR_TEXTURE, 4, 6, 1, 1, 2, 2);
    private static final JournalNineSlice QUEST_JOURNAL_SCROLLER_NINE_SLICE =
            new JournalNineSlice(VillagerRetaliationClientAssets.QUEST_JOURNAL_SCROLLER_TEXTURE, 4, 6, 1, 1, 2, 2);
    private static final JournalNineSlice QUEST_JOURNAL_SCROLLER_HIGHLIGHT_NINE_SLICE =
            new JournalNineSlice(VillagerRetaliationClientAssets.QUEST_JOURNAL_SCROLLER_HIGHLIGHT_TEXTURE, 4, 6, 1, 1, 2, 2);
    private static final JournalNineSlice QUEST_JOURNAL_QUEST_NUMBER_NINE_SLICE =
            new JournalNineSlice(VillagerRetaliationClientAssets.QUEST_JOURNAL_QUEST_NUMBER_TEXTURE, 11, 11, 3, 3, 3, 3);
    private static final JournalNineSlice QUEST_JOURNAL_ENTRY_1_NINE_SLICE =
            new JournalNineSlice(VillagerRetaliationClientAssets.QUEST_JOURNAL_ENTRY_1_TEXTURE, 3, 3, 1, 1, 1, 1);
    private static final JournalNineSlice QUEST_JOURNAL_ENTRY_2_NINE_SLICE =
            new JournalNineSlice(VillagerRetaliationClientAssets.QUEST_JOURNAL_ENTRY_2_TEXTURE, 3, 3, 1, 1, 1, 1);
    private static final JournalNineSlice QUEST_JOURNAL_ENTRY_HIGHLIGHT_NINE_SLICE =
            new JournalNineSlice(VillagerRetaliationClientAssets.QUEST_JOURNAL_ENTRY_HIGHLIGHT_TEXTURE, 3, 3, 1, 1, 1, 1);
    private static final JournalNineSlice QUEST_JOURNAL_SELECTED_QUEST_NINE_SLICE =
            new JournalNineSlice(VillagerRetaliationClientAssets.QUEST_JOURNAL_SELECTED_QUEST_TEXTURE, 134, 23, 3, 3, 2, 2);

    private final VillagerInteractionScreenState state = new VillagerInteractionScreenState();
    private final EnumMap<QuestJournalTab, VillagerInteractionScreenState.OptionListPosition> tabPositions =
            new EnumMap<>(QuestJournalTab.class);
    private float visualOptionScroll;
    private float visualDetailsScroll;
    private long lastScrollRenderMillis;
    private int detailsSelectedOption = Integer.MIN_VALUE;
    private String selectedQuestId = "";
    private QuestJournalTab selectedTab = QuestJournalTab.AVAILABLE;
    private boolean draggingOptionScrollbar;
    private float optionScrollbarDragOffset;
    private boolean closingWithAnimation;
    private boolean openedSoundPlayed;
    private long animationStartMillis = -1L;

    public VillagerQuestJournalScreen() {
        super(Component.literal("Active Quests"));
    }

    @Override
    protected void init() {
        this.state.resetOptions(!visibleEntries().isEmpty());
        this.visualOptionScroll = this.state.optionScroll();
        this.visualDetailsScroll = this.state.detailsScroll();
        this.lastScrollRenderMillis = Util.getMillis();
        this.detailsSelectedOption = this.state.selectedOption();
        rememberSelectedQuestId();
        this.closingWithAnimation = false;
        this.animationStartMillis = Util.getMillis();
        if (!this.openedSoundPlayed) {
            this.openedSoundPlayed = true;
            playBookSound(0.9F);
        }
    }

    @Override
    public void tick() {
        if (this.closingWithAnimation) {
            if (animationElapsedMillis() >= JOURNAL_ANIMATION_DURATION_MILLIS) {
                Minecraft.getInstance().setScreen(null);
            }
            return;
        }
        clampSelectedOption();
        resetDetailsScrollAfterSelectionChange();
        this.state.tickOptionScroll(OPTION_SCROLL_LERP);
        this.state.tickDetailsScroll(OPTION_SCROLL_LERP);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (entries().isEmpty()) {
            Minecraft.getInstance().setScreen(null);
            return;
        }
        clampSelectedOption();
        updateVisualScrolls();

        int slideOffset = slideOffsetY();
        int journalMouseY = mouseY - slideOffset;

        VillagerClientUiUtil.pushGuiLayer(graphics, VillagerClientUiUtil.screenLayerZ());
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, slideOffset, 0.0F);
        renderJournalContainer(graphics);
        renderTabTitle(graphics);
        renderQuestCountBadge(graphics);
        renderQuestOptions(graphics, mouseX, journalMouseY, slideOffset);
        renderQuestDetails(graphics, slideOffset);
        graphics.pose().popPose();
        VillagerClientUiUtil.popGuiLayer(graphics);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.closingWithAnimation) {
            return true;
        }
        if (VillagerQuestKeyMappings.OPEN_JOURNAL.matches(keyCode, scanCode)) {
            VillagerQuestTrackerOverlay.ignorePendingJournalToggle();
            closeJournal();
            return true;
        }
        return switch (keyCode) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                closeJournal();
                yield true;
            }
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> {
                moveSelection(-1);
                yield true;
            }
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> {
                moveSelection(1);
                yield true;
            }
            default -> super.keyPressed(keyCode, scanCode, modifiers);
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.closingWithAnimation || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return this.closingWithAnimation || super.mouseClicked(mouseX, mouseY, button);
        }

        double journalMouseY = mouseY - slideOffsetY();
        QuestJournalTab clickedTab = bookmarkAt(mouseX, journalMouseY);
        if (clickedTab != null) {
            selectTab(clickedTab);
            return true;
        }

        if (tryBeginOptionScrollbarDrag(mouseX, journalMouseY)) {
            return true;
        }

        int hovered = questOptionAt(mouseX, journalMouseY);
        if (hovered >= 0) {
            if (hovered != this.state.selectedOption()) {
                setSelectedOption(hovered);
                acknowledgeSelectedQuestUpdate();
                ensureSelectedVisible(false);
                return true;
            }
            acknowledgeSelectedQuestUpdate();
            VillagerQuestTrackerOverlay.toggleTracking(visibleEntries().get(hovered));
            ensureSelectedVisible(false);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.closingWithAnimation) {
            return true;
        }

        double journalMouseY = mouseY - slideOffsetY();
        if (maxDetailsScroll() > 0.0F && isPointInsideDetailsScrollArea(mouseX, journalMouseY)) {
            setTargetDetailsScroll(this.state.targetDetailsScroll() - (float) scrollY * DETAIL_SCROLL_STEP);
            return true;
        }
        if (maxOptionScroll() <= 0.0F
                || (!isPointInsideOptionScrollArea(mouseX, journalMouseY)
                        && !isPointInsideOptionScrollbarArea(mouseX, journalMouseY))) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        setTargetOptionScroll(this.state.targetOptionScroll() - (float) scrollY * OPTION_SCROLL_STEP);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.draggingOptionScrollbar) {
            return dragOptionScrollbar(mouseY - slideOffsetY());
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.draggingOptionScrollbar) {
            this.draggingOptionScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        closeJournal();
    }

    private void renderJournalContainer(GuiGraphics graphics) {
        int left = journalLeft();
        int top = journalTop();
        graphics.blit(
                VillagerRetaliationClientAssets.QUEST_JOURNAL_CONTAINER_TEXTURE,
                left,
                top,
                0,
                0,
                JOURNAL_WIDTH,
                JOURNAL_HEIGHT,
                JOURNAL_WIDTH,
                JOURNAL_HEIGHT);
        renderBookmarks(graphics, left, top);
        graphics.blit(
                VillagerRetaliationClientAssets.QUEST_JOURNAL_CONTAINER_OVERLAY_TEXTURE,
                left,
                top,
                0,
                0,
                JOURNAL_WIDTH,
                JOURNAL_HEIGHT,
                JOURNAL_WIDTH,
                JOURNAL_HEIGHT);
    }

    private void renderTabTitle(GuiGraphics graphics) {
        graphics.drawString(
                this.font,
                this.selectedTab.title(),
                journalLeft() + TAB_TITLE_LEFT_OFFSET,
                journalTop() + TAB_TITLE_BOTTOM_OFFSET - this.font.lineHeight,
                TITLE_COLOR,
                false);
    }

    private void renderQuestCountBadge(GuiGraphics graphics) {
        String count = Integer.toString(visibleEntries().size());
        int textWidth = this.font.width(count);
        int badgeWidth = Math.max(
                QUEST_COUNT_BADGE_WIDTH,
                textWidth + QUEST_COUNT_BADGE_HORIZONTAL_PADDING * 2 + QUEST_COUNT_BADGE_INNER_INSET);
        int badgeLeft = optionsLeft() + QUEST_OPTION_WIDTH - badgeWidth;
        int badgeTop = optionsTop() - QUEST_COUNT_BADGE_BOTTOM_GAP - QUEST_COUNT_BADGE_HEIGHT;
        int innerLeft = badgeLeft + QUEST_COUNT_BADGE_INNER_INSET;
        int innerWidth = badgeWidth - QUEST_COUNT_BADGE_INNER_INSET * 2;
        QUEST_JOURNAL_QUEST_NUMBER_NINE_SLICE.render(
                graphics,
                badgeLeft,
                badgeTop,
                badgeWidth,
                QUEST_COUNT_BADGE_HEIGHT);
        graphics.drawString(
                this.font,
                count,
                innerLeft + Math.round((innerWidth - textWidth) / 2.0F),
                badgeTop + 2,
                QUEST_COUNT_TEXT_COLOR,
                false);
    }

    private void renderBookmarks(GuiGraphics graphics, int journalLeft, int journalTop) {
        int left = journalLeft + BOOKMARK_LEFT_OFFSET;
        for (QuestJournalTab tab : QuestJournalTab.values()) {
            renderBookmark(graphics, tab.texture(), bookmarkLeft(left, tab), bookmarkTop(journalTop, tab));
        }
    }

    private void renderBookmark(GuiGraphics graphics, ResourceLocation texture, int left, int top) {
        graphics.blit(texture, left, top, 0, 0, BOOKMARK_WIDTH, BOOKMARK_HEIGHT, BOOKMARK_WIDTH, BOOKMARK_HEIGHT);
    }

    private int bookmarkLeft(int firstBookmarkLeft, QuestJournalTab tab) {
        return firstBookmarkLeft + tab.index() * (BOOKMARK_WIDTH + BOOKMARK_GAP);
    }

    private int bookmarkTop(int journalTop, QuestJournalTab tab) {
        int selectedOffset = tab == this.selectedTab ? 0 : INACTIVE_BOOKMARK_OFFSET_Y;
        return journalTop + BOOKMARK_TOP_OFFSET + selectedOffset;
    }

    private void renderQuestOptions(GuiGraphics graphics, int mouseX, int journalMouseY, int slideOffset) {
        List<QuestTrackerSyncPayload.Entry> visibleEntries = visibleEntries();
        int left = optionsLeft();
        int top = optionsTop();
        int viewportBottom = top + optionViewportHeight();
        int hovered = questOptionAt(mouseX, journalMouseY);
        if (visibleEntries.isEmpty()) {
            graphics.drawString(
                    this.font,
                    this.selectedTab.emptyMessage(),
                    left,
                    top + QUEST_OPTION_EMPTY_TEXT_TOP_PADDING,
                    TEXT_COLOR,
                    false);
            return;
        }
        graphics.enableScissor(
                left - QUEST_OPTION_SELECTED_OVERHANG_X,
                top - QUEST_OPTION_SELECTED_OVERHANG_Y + slideOffset,
                left + QUEST_OPTION_WIDTH + QUEST_OPTION_SELECTED_OVERHANG_X,
                viewportBottom + QUEST_OPTION_SELECTED_OVERHANG_Y + slideOffset);
        for (int index = 0; index < visibleEntries.size(); index++) {
            int optionTop = Mth.floor(top + optionOffset(index) - optionRenderScroll());
            int optionHeight = questOptionHeight(index);
            int optionBottom = optionTop + optionHeight;
            if (optionBottom < top || optionTop > viewportBottom) {
                continue;
            }
            renderQuestOptionBackground(graphics, index, hovered, left, optionTop, optionHeight);
        }
        int selectedIndex = this.state.selectedOption();
        if (selectedIndex >= 0 && selectedIndex < visibleEntries.size()) {
            int selectedTop = Mth.floor(top + optionOffset(selectedIndex) - optionRenderScroll());
            int selectedHeight = questOptionHeight(selectedIndex);
            int selectedBottom = selectedTop + selectedHeight;
            if (selectedBottom >= top && selectedTop <= viewportBottom) {
                renderSelectedQuestOption(graphics, left, selectedTop, selectedHeight);
            }
        }
        for (int index = 0; index < visibleEntries.size(); index++) {
            int optionTop = Mth.floor(top + optionOffset(index) - optionRenderScroll());
            int optionHeight = questOptionHeight(index);
            int optionBottom = optionTop + optionHeight;
            if (optionBottom < top || optionTop > viewportBottom) {
                continue;
            }
            renderQuestOptionIcon(graphics, visibleEntries.get(index), index == selectedIndex, left, optionTop);
        }
        for (int index = 0; index < visibleEntries.size(); index++) {
            int optionTop = Mth.floor(top + optionOffset(index) - optionRenderScroll());
            int optionHeight = questOptionHeight(index);
            int optionBottom = optionTop + optionHeight;
            if (optionBottom < top || optionTop > viewportBottom) {
                continue;
            }
            renderQuestOptionTitle(graphics, index, hovered, left, optionTop);
        }
        graphics.disableScissor();
        renderQuestOptionScrollbar(graphics, mouseX, journalMouseY);
    }

    private void renderQuestOptionScrollbar(GuiGraphics graphics, int mouseX, int journalMouseY) {
        float maxScroll = maxOptionScroll();
        if (maxScroll <= 0.0F) {
            return;
        }

        int left = optionScrollbarLeft();
        int top = optionsTop();
        int height = optionViewportHeight();
        int scrollerHeight = optionScrollerHeight(height);
        int scrollerTop = optionScrollerTop(top, height, scrollerHeight, maxScroll);
        JournalNineSlice scroller =
                isPointInside(
                                mouseX,
                                journalMouseY,
                                left,
                                scrollerTop,
                                left + QUEST_OPTION_SCROLLBAR_WIDTH,
                                scrollerTop + scrollerHeight)
                        ? QUEST_JOURNAL_SCROLLER_HIGHLIGHT_NINE_SLICE
                        : QUEST_JOURNAL_SCROLLER_NINE_SLICE;

        QUEST_JOURNAL_SCROLLBAR_NINE_SLICE.render(graphics, left, top, QUEST_OPTION_SCROLLBAR_WIDTH, height);
        scroller.render(graphics, left, scrollerTop, QUEST_OPTION_SCROLLBAR_WIDTH, scrollerHeight);
    }

    private int optionScrollerHeight(int trackHeight) {
        float contentHeight = optionContentHeight();
        if (contentHeight <= 0.0F) {
            return trackHeight;
        }
        int scrollerHeight = Mth.floor(trackHeight * (trackHeight / contentHeight));
        return Mth.clamp(scrollerHeight, QUEST_OPTION_SCROLLER_MIN_HEIGHT, trackHeight);
    }

    private int optionScrollerTop(int trackTop, int trackHeight, int scrollerHeight, float maxScroll) {
        int travel = Math.max(0, trackHeight - scrollerHeight);
        if (travel <= 0 || maxScroll <= 0.0F) {
            return trackTop;
        }
        float scrollProgress = Mth.clamp(optionRenderScroll() / maxScroll, 0.0F, 1.0F);
        return trackTop + Math.round(travel * scrollProgress);
    }

    private boolean tryBeginOptionScrollbarDrag(double mouseX, double journalMouseY) {
        float maxScroll = maxOptionScroll();
        if (maxScroll <= 0.0F || !isPointInsideOptionScrollbarArea(mouseX, journalMouseY)) {
            return false;
        }

        int trackTop = optionsTop();
        int trackHeight = optionViewportHeight();
        int scrollerHeight = optionScrollerHeight(trackHeight);
        int scrollerTop = optionScrollerTop(trackTop, trackHeight, scrollerHeight, maxScroll);
        if (journalMouseY >= scrollerTop && journalMouseY <= scrollerTop + scrollerHeight) {
            this.optionScrollbarDragOffset = (float) journalMouseY - scrollerTop;
        } else {
            this.optionScrollbarDragOffset = scrollerHeight / 2.0F;
            setOptionScrollFromScrollbarDrag(journalMouseY);
        }
        this.draggingOptionScrollbar = true;
        return true;
    }

    private boolean dragOptionScrollbar(double journalMouseY) {
        if (maxOptionScroll() <= 0.0F) {
            this.draggingOptionScrollbar = false;
            return true;
        }
        setOptionScrollFromScrollbarDrag(journalMouseY);
        return true;
    }

    private void setOptionScrollFromScrollbarDrag(double journalMouseY) {
        int trackTop = optionsTop();
        int trackHeight = optionViewportHeight();
        int scrollerHeight = optionScrollerHeight(trackHeight);
        int travel = Math.max(0, trackHeight - scrollerHeight);
        if (travel <= 0) {
            setTargetOptionScroll(0.0F);
            this.visualOptionScroll = 0.0F;
            return;
        }

        float scrollerTop = Mth.clamp((float) journalMouseY - this.optionScrollbarDragOffset, trackTop, trackTop + travel);
        float scrollProgress = (scrollerTop - trackTop) / travel;
        float scroll = Mth.clamp(scrollProgress * maxOptionScroll(), 0.0F, maxOptionScroll());
        setTargetOptionScroll(scroll);
        this.visualOptionScroll = scroll;
    }

    private void renderQuestOptionBackground(GuiGraphics graphics, int index, int hovered, int left, int top, int height) {
        entryNineSlice(index).render(graphics, left, top, QUEST_OPTION_WIDTH, height);
        if (index == hovered && index != this.state.selectedOption()) {
            QUEST_JOURNAL_ENTRY_HIGHLIGHT_NINE_SLICE.render(graphics, left, top, QUEST_OPTION_WIDTH, height);
        }
    }

    private void renderSelectedQuestOption(GuiGraphics graphics, int left, int top, int height) {
        QUEST_JOURNAL_SELECTED_QUEST_NINE_SLICE.render(
                graphics,
                left - QUEST_OPTION_SELECTED_OVERHANG_X,
                top - QUEST_OPTION_SELECTED_OVERHANG_Y,
                QUEST_OPTION_WIDTH + QUEST_OPTION_SELECTED_OVERHANG_X * 2,
                height + QUEST_OPTION_SELECTED_OVERHANG_Y * 2);
    }

    private void renderQuestOptionIcon(GuiGraphics graphics, QuestTrackerSyncPayload.Entry entry, boolean selected, int left, int top) {
        QuestJournalEntryState state = QuestJournalEntryState.iconFor(entry);
        int iconLeft = left + QUEST_OPTION_STATE_ICON_LEFT_PADDING;
        int iconTop = top + QUEST_OPTION_STATE_ICON_TOP_PADDING;
        renderQuestOptionIcon(graphics, state.texture(), iconLeft, iconTop);
        if (selected) {
            renderQuestOptionIcon(graphics, state.selectedTexture(), iconLeft, iconTop);
        }
        if (hasQuestUpdate(entry)) {
            int updateIconLeft = left + QUEST_OPTION_WIDTH - QUEST_OPTION_UPDATE_ICON_RIGHT_PADDING - QUEST_OPTION_UPDATE_ICON_WIDTH;
            int updateIconTop = top + QUEST_OPTION_UPDATE_ICON_TOP_PADDING;
            renderQuestOptionIcon(
                    graphics,
                    VillagerRetaliationClientAssets.QUEST_JOURNAL_ICON_UPDATE_TEXTURE,
                    updateIconLeft,
                    updateIconTop,
                    QUEST_OPTION_UPDATE_ICON_WIDTH,
                    QUEST_OPTION_UPDATE_ICON_HEIGHT);
            if (selected) {
                renderQuestOptionIcon(
                        graphics,
                        VillagerRetaliationClientAssets.QUEST_JOURNAL_ICON_UPDATE_SELECTED_QUEST_TEXTURE,
                        updateIconLeft,
                        updateIconTop,
                        QUEST_OPTION_UPDATE_ICON_WIDTH,
                        QUEST_OPTION_UPDATE_ICON_HEIGHT);
            }
        }
    }

    private void renderQuestOptionIcon(GuiGraphics graphics, ResourceLocation texture, int left, int top) {
        renderQuestOptionIcon(graphics, texture, left, top, QUEST_OPTION_STATE_ICON_SIZE, QUEST_OPTION_STATE_ICON_SIZE);
    }

    private void renderQuestOptionIcon(GuiGraphics graphics, ResourceLocation texture, int left, int top, int width, int height) {
        graphics.blit(
                texture,
                left,
                top,
                0,
                0,
                width,
                height,
                width,
                height);
    }

    private void renderQuestOptionTitle(GuiGraphics graphics, int index, int hovered, int left, int top) {
        boolean selected = index == this.state.selectedOption();
        int textColor = selected ? SELECTED_TEXT_COLOR : index == hovered ? HOVERED_TEXT_COLOR : TEXT_COLOR;
        int textLeft = left + QUEST_OPTION_TEXT_LEFT_PADDING;
        int textTop = top + QUEST_OPTION_TEXT_TOP_PADDING;
        int lineStep = this.font.lineHeight + QUEST_OPTION_TEXT_LINE_GAP;
        for (FormattedCharSequence line : questOptionTitleLines(index)) {
            graphics.drawString(this.font, line, textLeft, textTop, textColor, false);
            textTop += lineStep;
        }
    }

    private JournalNineSlice entryNineSlice(int index) {
        return index % 2 == 0 ? QUEST_JOURNAL_ENTRY_1_NINE_SLICE : QUEST_JOURNAL_ENTRY_2_NINE_SLICE;
    }

    private void renderQuestDetails(GuiGraphics graphics, int slideOffset) {
        QuestTrackerSyncPayload.Entry selected = selectedEntry();
        if (selected == null) {
            return;
        }

        int left = detailsLeft();
        int top = detailsTop();
        int right = left + DETAILS_WIDTH;
        int bottom = top + DETAILS_HEIGHT;
        int textBottom = selected.showProgress() ? bottom - DETAILS_PROGRESS_RESERVED_HEIGHT : bottom;
        int viewportHeight = Math.max(1, textBottom - top);
        List<QuestDetailLine> detailLines = buildQuestDetailLines(selected, DETAILS_WIDTH, DETAILS_LINE_STEP);
        int contentHeight = detailContentHeight(detailLines, DETAILS_LINE_STEP);
        float maxScroll = ToucanScrollState.maxScroll(contentHeight, viewportHeight);
        float scroll = Mth.clamp(detailsRenderScroll(), 0.0F, maxScroll);

        graphics.enableScissor(left, top + slideOffset, right, textBottom + slideOffset);
        for (QuestDetailLine line : detailLines) {
            float lineTop = top + line.top() - scroll;
            float lineBottom = lineTop + DETAILS_LINE_STEP;
            if (lineBottom < top || lineTop > textBottom) {
                continue;
            }
            graphics.drawString(this.font, line.text(), left, Mth.floor(lineTop), line.color(), false);
        }
        graphics.disableScissor();

        if (selected.showProgress()) {
            int progressTop = bottom - DETAILS_PROGRESS_HEIGHT - 2;
            renderProgressBar(graphics, left, progressTop, right, DETAILS_PROGRESS_HEIGHT, selected.progress());
        }
    }

    private void renderProgressBar(GuiGraphics graphics, int left, int top, int right, int height, float progress) {
        graphics.fill(left, top, right, top + height, PROGRESS_BACKGROUND_COLOR);
        int fillRight = left + Math.round((right - left) * Mth.clamp(progress, 0.0F, 1.0F));
        if (fillRight > left) {
            graphics.fill(left, top, fillRight, top + height, PROGRESS_FILL_COLOR);
        }
    }

    private List<QuestDetailLine> buildQuestDetailLines(QuestTrackerSyncPayload.Entry selected, int wrapWidth, int lineStep) {
        List<QuestDetailLine> lines = new ArrayList<>();
        int y = 0;
        y = addWrappedDetailLines(lines, selected.title(), wrapWidth, TITLE_COLOR, y, lineStep, 7);
        y = addWrappedDetailLines(lines, selected.objective(), wrapWidth, TEXT_COLOR, y, lineStep, 5);
        y = addWrappedDetailLines(lines, statusLine(selected), wrapWidth, MUTED_TEXT_COLOR, y, lineStep, 2);
        if (!selected.issuer().isBlank()) {
            y = addWrappedDetailLines(lines, "Issued by: " + selected.issuer(), wrapWidth, MUTED_TEXT_COLOR, y, lineStep, 2);
        }
        if (!selected.issuerLocation().isBlank()) {
            y = addWrappedDetailLines(lines, selected.issuerLocation(), wrapWidth, MUTED_TEXT_COLOR, y, lineStep, 2);
        }
        if (!selected.questItems().isEmpty()) {
            y = addWrappedDetailLines(lines, questItemsLine(selected), wrapWidth, MUTED_TEXT_COLOR, y, lineStep, 2);
        }
        if (!selected.metadata().isBlank()) {
            y += 4;
            addWrappedDetailLines(lines, selected.metadata(), wrapWidth, MUTED_TEXT_COLOR, y, lineStep, 0);
        }
        return lines;
    }

    private int addWrappedDetailLines(
            List<QuestDetailLine> lines,
            String text,
            int wrapWidth,
            int color,
            int top,
            int lineStep,
            int gapAfter) {
        if (text == null || text.isBlank() || wrapWidth <= 0) {
            return top;
        }
        int y = top;
        for (FormattedCharSequence line : this.font.split(Component.literal(text), wrapWidth)) {
            lines.add(new QuestDetailLine(line, color, y));
            y += lineStep;
        }
        return y + gapAfter;
    }

    private static int detailContentHeight(List<QuestDetailLine> detailLines, int lineStep) {
        if (detailLines.isEmpty()) {
            return 0;
        }
        QuestDetailLine lastLine = detailLines.get(detailLines.size() - 1);
        return lastLine.top() + lineStep;
    }

    private static String statusLine(QuestTrackerSyncPayload.Entry entry) {
        if (entry.status().isBlank()) {
            return entry.trackable() && VillagerQuestTrackerOverlay.isTracked(entry) ? "Tracked" : "Not tracked";
        }
        if (!entry.trackable()) {
            return "Status: " + entry.status();
        }
        return VillagerQuestTrackerOverlay.isTracked(entry)
                ? "Status: " + entry.status() + " | Tracked"
                : "Status: " + entry.status() + " | Not tracked";
    }

    private static String questItemsLine(QuestTrackerSyncPayload.Entry entry) {
        List<String> names = new ArrayList<>();
        for (QuestTrackerSyncPayload.QuestItem item : entry.questItems()) {
            names.add(item.count() > 1 ? item.label() + " x" + item.count() : item.label());
        }
        return "Quest item: " + String.join(", ", names);
    }

    private void closeJournal() {
        if (this.closingWithAnimation) {
            return;
        }
        this.closingWithAnimation = true;
        this.draggingOptionScrollbar = false;
        this.animationStartMillis = Util.getMillis();
        VillagerQuestTrackerOverlay.dismissJournalFlash();
        playBookSound(0.72F);
    }

    private void ensureSelectedVisible(boolean jumpToTarget) {
        if (this.state.selectedOption() < 0 || this.state.selectedOption() >= visibleEntries().size()) {
            return;
        }

        float optionTop = optionOffset(this.state.selectedOption());
        float optionBottom = optionTop + questOptionHeight(this.state.selectedOption());
        float viewportTop = this.state.targetOptionScroll();
        float viewportBottom = viewportTop + optionViewportHeight();
        int padding = 5;
        boolean scrollChanged = false;
        if (optionTop < viewportTop + padding) {
            setTargetOptionScroll(optionTop - padding);
            scrollChanged = true;
        } else if (optionBottom > viewportBottom - padding) {
            setTargetOptionScroll(optionBottom - optionViewportHeight() + padding);
            scrollChanged = true;
        }
        if (jumpToTarget && scrollChanged) {
            this.state.jumpOptionScrollToTarget();
            this.visualOptionScroll = this.state.optionScroll();
        }
    }

    private void moveSelection(int direction) {
        if (visibleEntries().isEmpty()) {
            return;
        }
        setSelectedOption(Mth.positiveModulo(this.state.selectedOption() + direction, visibleEntries().size()));
        acknowledgeSelectedQuestUpdate();
        ensureSelectedVisible(false);
    }

    private void selectTab(QuestJournalTab tab) {
        playBookSound(1.0F);
        if (this.selectedTab == tab) {
            return;
        }

        rememberSelectedTabPosition();
        this.selectedTab = tab;
        restoreSelectedTabPosition();
    }

    private void rememberSelectedTabPosition() {
        this.tabPositions.put(this.selectedTab, this.state.captureOptionListPosition());
    }

    private void restoreSelectedTabPosition() {
        List<QuestTrackerSyncPayload.Entry> visibleEntries = visibleEntries();
        VillagerInteractionScreenState.OptionListPosition position = this.tabPositions.get(this.selectedTab);
        if (position == null) {
            this.state.resetOptions(!visibleEntries.isEmpty());
        } else {
            this.state.restoreOptionListPosition(position, visibleEntries.size(), maxOptionScroll());
            if (visibleEntries.isEmpty()) {
                this.state.resetOptions(false);
            }
        }
        this.state.resetDetailsScroll();
        this.visualOptionScroll = this.state.optionScroll();
        this.visualDetailsScroll = this.state.detailsScroll();
        this.detailsSelectedOption = this.state.selectedOption();
        rememberSelectedQuestId();
    }

    private boolean isPointInsideOptionScrollArea(double mouseX, double mouseY) {
        int left = optionsLeft();
        int top = optionsTop();
        int bottom = top + optionViewportHeight();
        return mouseX >= left
                && mouseX <= left + QUEST_OPTION_WIDTH
                && mouseY >= top
                && mouseY <= bottom;
    }

    private static boolean isPointInside(double mouseX, double mouseY, int left, int top, int right, int bottom) {
        return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
    }

    private boolean isPointInsideOptionScrollbarArea(double mouseX, double mouseY) {
        int left = optionsLeft() + QUEST_OPTION_WIDTH;
        int top = optionsTop();
        return isPointInside(
                mouseX,
                mouseY,
                left,
                top,
                optionScrollbarLeft() + QUEST_OPTION_SCROLLBAR_WIDTH,
                top + optionViewportHeight());
    }

    private boolean isPointInsideDetailsScrollArea(double mouseX, double mouseY) {
        int left = detailsLeft();
        int top = detailsTop();
        return mouseX >= left
                && mouseX <= left + DETAILS_WIDTH
                && mouseY >= top
                && mouseY <= top + DETAILS_HEIGHT;
    }

    private QuestJournalTab bookmarkAt(double mouseX, double mouseY) {
        int journalTop = journalTop();
        int firstBookmarkLeft = journalLeft() + BOOKMARK_LEFT_OFFSET;
        for (QuestJournalTab tab : QuestJournalTab.values()) {
            int left = bookmarkLeft(firstBookmarkLeft, tab);
            int top = bookmarkTop(journalTop, tab);
            if (mouseX >= left
                    && mouseX <= left + BOOKMARK_WIDTH
                    && mouseY >= top
                    && mouseY <= top + BOOKMARK_HEIGHT) {
                return tab;
            }
        }
        return null;
    }

    private int questOptionAt(double mouseX, double mouseY) {
        if (!isPointInsideOptionScrollArea(mouseX, mouseY)) {
            return -1;
        }
        int top = optionsTop();
        for (int index = 0; index < visibleEntries().size(); index++) {
            float optionTop = top + optionOffset(index) - optionRenderScroll();
            if (mouseY >= optionTop && mouseY <= optionTop + questOptionHeight(index)) {
                return index;
            }
        }
        return -1;
    }

    private void clampSelectedOption() {
        List<QuestTrackerSyncPayload.Entry> visibleEntries = visibleEntries();
        if (visibleEntries.isEmpty()) {
            this.state.resetOptions(false);
            this.selectedQuestId = "";
            return;
        }
        if (!this.selectedQuestId.isBlank()) {
            int selectedIndex = indexOfQuestId(visibleEntries, this.selectedQuestId);
            if (selectedIndex >= 0) {
                if (selectedIndex != this.state.selectedOption()) {
                    setSelectedOption(selectedIndex, true);
                    ensureSelectedVisible(true);
                }
                clampTargetOptionScroll();
                clampTargetDetailsScroll();
                return;
            }
        }
        setSelectedOption(Mth.clamp(this.state.selectedOption(), 0, visibleEntries.size() - 1));
        clampTargetOptionScroll();
        clampTargetDetailsScroll();
    }

    private void clampTargetOptionScroll() {
        float clamped = Mth.clamp(this.state.targetOptionScroll(), 0.0F, maxOptionScroll());
        if (Math.abs(clamped - this.state.targetOptionScroll()) > 0.01F) {
            setTargetOptionScroll(clamped);
        }
    }

    private void clampTargetDetailsScroll() {
        float clamped = Mth.clamp(this.state.targetDetailsScroll(), 0.0F, maxDetailsScroll());
        if (Math.abs(clamped - this.state.targetDetailsScroll()) > 0.01F) {
            setTargetDetailsScroll(clamped);
        }
    }

    private void setSelectedOption(int selectedOption) {
        setSelectedOption(selectedOption, false);
    }

    private void setSelectedOption(int selectedOption, boolean preserveDetailsScroll) {
        if (this.state.selectedOption() != selectedOption) {
            this.detailsSelectedOption = selectedOption;
            if (!preserveDetailsScroll) {
                this.state.resetDetailsScroll();
                this.visualDetailsScroll = 0.0F;
            }
        }
        this.state.setSelectedOption(selectedOption);
        rememberSelectedQuestId();
    }

    private void acknowledgeSelectedQuestUpdate() {
        QuestTrackerSyncPayload.Entry selected = selectedEntry();
        if (selected != null) {
            VillagerQuestTrackerOverlay.acknowledgeQuestUpdate(selected);
        }
    }

    private void resetDetailsScrollAfterSelectionChange() {
        if (this.detailsSelectedOption == this.state.selectedOption()) {
            return;
        }
        this.detailsSelectedOption = this.state.selectedOption();
        this.state.resetDetailsScroll();
        this.visualDetailsScroll = 0.0F;
    }

    private QuestTrackerSyncPayload.Entry selectedEntry() {
        List<QuestTrackerSyncPayload.Entry> visibleEntries = visibleEntries();
        if (this.state.selectedOption() < 0 || this.state.selectedOption() >= visibleEntries.size()) {
            return null;
        }
        return visibleEntries.get(this.state.selectedOption());
    }

    private void rememberSelectedQuestId() {
        QuestTrackerSyncPayload.Entry selected = selectedEntry();
        this.selectedQuestId = selected == null ? "" : selected.questId();
    }

    private static int indexOfQuestId(List<QuestTrackerSyncPayload.Entry> visibleEntries, String questId) {
        if (questId == null || questId.isBlank()) {
            return -1;
        }
        for (int index = 0; index < visibleEntries.size(); index++) {
            if (questId.equals(visibleEntries.get(index).questId())) {
                return index;
            }
        }
        return -1;
    }

    private void setTargetOptionScroll(float scroll) {
        this.state.setTargetOptionScroll(scroll, maxOptionScroll());
    }

    private void setTargetDetailsScroll(float scroll) {
        this.state.setTargetDetailsScroll(scroll, maxDetailsScroll());
    }

    private void updateVisualScrolls() {
        long now = Util.getMillis();
        float frames = Mth.clamp((now - this.lastScrollRenderMillis) / 16.6667F, 0.0F, 4.0F);
        this.lastScrollRenderMillis = now;
        this.visualOptionScroll = smoothScroll(this.visualOptionScroll, this.state.targetOptionScroll(), frames, maxOptionScroll());
        this.visualDetailsScroll = smoothScroll(this.visualDetailsScroll, this.state.targetDetailsScroll(), frames, maxDetailsScroll());
    }

    private static float smoothScroll(float current, float target, float frames, float maxScroll) {
        current = Mth.clamp(current, 0.0F, maxScroll);
        target = Mth.clamp(target, 0.0F, maxScroll);
        if (Math.abs(target - current) < 0.08F || frames <= 0.0F) {
            return target;
        }
        float retain = (float) Math.pow(0.68D, frames);
        return Mth.lerp(1.0F - retain, current, target);
    }

    private float optionRenderScroll() {
        return Mth.clamp(this.visualOptionScroll, 0.0F, maxOptionScroll());
    }

    private float detailsRenderScroll() {
        return Mth.clamp(this.visualDetailsScroll, 0.0F, maxDetailsScroll());
    }

    private float maxOptionScroll() {
        return ToucanScrollState.maxScroll(optionContentHeight(), optionViewportHeight());
    }

    private float maxDetailsScroll() {
        QuestTrackerSyncPayload.Entry selected = selectedEntry();
        if (selected == null) {
            return 0.0F;
        }
        return ToucanScrollState.maxScroll(detailsContentHeight(selected), detailsViewportHeight(selected));
    }

    private int detailsContentHeight(QuestTrackerSyncPayload.Entry selected) {
        return detailContentHeight(buildQuestDetailLines(selected, DETAILS_WIDTH, DETAILS_LINE_STEP), DETAILS_LINE_STEP);
    }

    private int detailsViewportHeight(QuestTrackerSyncPayload.Entry selected) {
        return selected.showProgress() ? DETAILS_HEIGHT - DETAILS_PROGRESS_RESERVED_HEIGHT : DETAILS_HEIGHT;
    }

    private float optionContentHeight() {
        List<QuestTrackerSyncPayload.Entry> visibleEntries = visibleEntries();
        if (visibleEntries.isEmpty()) {
            return 0.0F;
        }
        float contentHeight = 0.0F;
        for (int index = 0; index < visibleEntries.size(); index++) {
            contentHeight += questOptionHeight(index);
            if (index < visibleEntries.size() - 1) {
                contentHeight += QUEST_OPTION_GAP;
            }
        }
        return contentHeight;
    }

    private float optionOffset(int optionIndex) {
        float offset = 0.0F;
        for (int index = 0; index < optionIndex; index++) {
            offset += questOptionHeight(index) + QUEST_OPTION_GAP;
        }
        return offset;
    }

    private int questOptionHeight(int index) {
        int lineCount = Math.max(1, questOptionTitleLines(index).size());
        if (lineCount <= 1) {
            return QUEST_OPTION_HEIGHT;
        }
        return QUEST_OPTION_HEIGHT + (lineCount - 1) * (this.font.lineHeight + QUEST_OPTION_TEXT_LINE_GAP);
    }

    private List<FormattedCharSequence> questOptionTitleLines(int index) {
        return this.font.split(Component.literal(visibleEntries().get(index).title()), questOptionTextWidth());
    }

    private int questOptionTextWidth() {
        return Math.max(1, QUEST_OPTION_WIDTH - QUEST_OPTION_TEXT_LEFT_PADDING - QUEST_OPTION_TEXT_RIGHT_PADDING);
    }

    private int optionViewportHeight() {
        return QUEST_OPTION_VIEWPORT_HEIGHT;
    }

    private int journalLeft() {
        return (this.width - JOURNAL_WIDTH) / 2;
    }

    private int journalTop() {
        return Math.max(4, (this.height - JOURNAL_HEIGHT) / 2);
    }

    private int optionsLeft() {
        return journalLeft() + QUEST_OPTION_LEFT_OFFSET;
    }

    private int optionsTop() {
        return journalTop() + QUEST_OPTION_TOP_OFFSET;
    }

    private int optionScrollbarLeft() {
        return optionsLeft() + QUEST_OPTION_WIDTH + QUEST_OPTION_SCROLLBAR_GAP;
    }

    private int detailsLeft() {
        return journalLeft() + DETAILS_LEFT_OFFSET;
    }

    private int detailsTop() {
        return journalTop() + DETAILS_TOP_OFFSET;
    }

    private int slideOffsetY() {
        float visibility = journalVisibility();
        int offscreenDistance = this.height - journalTop() + 12;
        return Math.round((1.0F - visibility) * offscreenDistance);
    }

    private float journalVisibility() {
        float progress = Mth.clamp(animationElapsedMillis() / JOURNAL_ANIMATION_DURATION_MILLIS, 0.0F, 1.0F);
        return this.closingWithAnimation ? 1.0F - easeInCubic(progress) : easeOutCubic(progress);
    }

    private float animationElapsedMillis() {
        if (this.animationStartMillis < 0L) {
            return JOURNAL_ANIMATION_DURATION_MILLIS;
        }
        return Util.getMillis() - this.animationStartMillis;
    }

    private static float easeOutCubic(float value) {
        float inverse = 1.0F - value;
        return 1.0F - inverse * inverse * inverse;
    }

    private static float easeInCubic(float value) {
        return value * value * value;
    }

    private void playBookSound(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F, pitch));
    }

    private static List<QuestTrackerSyncPayload.Entry> entries() {
        return VillagerQuestTrackerOverlay.entries();
    }

    private List<QuestTrackerSyncPayload.Entry> visibleEntries() {
        return entries().stream()
                .filter(entry -> this.selectedTab.includes(QuestJournalEntryState.from(entry)))
                .sorted(Comparator.comparingInt(VillagerQuestJournalScreen::activeTrackedSortKey))
                .toList();
    }

    private static int activeTrackedSortKey(QuestTrackerSyncPayload.Entry entry) {
        if (QuestJournalEntryState.from(entry) != QuestJournalEntryState.ACTIVE) {
            return Integer.MAX_VALUE;
        }
        int trackedIndex = VillagerQuestTrackerOverlay.trackedIndex(entry);
        return trackedIndex < 0 ? Integer.MAX_VALUE : trackedIndex;
    }

    private static boolean hasQuestUpdate(QuestTrackerSyncPayload.Entry entry) {
        return entry.questUpdate() && !entry.questAvailable();
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private enum QuestJournalTab {
        AVAILABLE(0, "Available", VillagerRetaliationClientAssets.QUEST_JOURNAL_BOOKMARK_RED_TEXTURE),
        ACTIVE(1, "Active", VillagerRetaliationClientAssets.QUEST_JOURNAL_BOOKMARK_PURPLE_TEXTURE),
        COMPLETED(2, "Completed", VillagerRetaliationClientAssets.QUEST_JOURNAL_BOOKMARK_TEAL_TEXTURE);

        private final int index;
        private final String title;
        private final ResourceLocation texture;

        QuestJournalTab(int index, String title, ResourceLocation texture) {
            this.index = index;
            this.title = title;
            this.texture = texture;
        }

        int index() {
            return this.index;
        }

        String title() {
            return this.title;
        }

        String emptyMessage() {
            return switch (this) {
                case AVAILABLE -> "No available quests";
                case ACTIVE -> "No active quests";
                case COMPLETED -> "No completed quests";
            };
        }

        ResourceLocation texture() {
            return this.texture;
        }

        boolean includes(QuestJournalEntryState state) {
            return switch (this) {
                case AVAILABLE -> state == QuestJournalEntryState.AVAILABLE || state == QuestJournalEntryState.ACTIVE;
                case ACTIVE -> state == QuestJournalEntryState.ACTIVE;
                case COMPLETED -> state == QuestJournalEntryState.COMPLETED;
            };
        }
    }

    private enum QuestJournalEntryState {
        AVAILABLE(VillagerRetaliationClientAssets.QUEST_JOURNAL_ICON_AVAILABLE_TEXTURE),
        ACTIVE(VillagerRetaliationClientAssets.QUEST_JOURNAL_ICON_ACTIVE_TEXTURE),
        INACTIVE(VillagerRetaliationClientAssets.QUEST_JOURNAL_ICON_INACTIVE_TEXTURE),
        COMPLETED(VillagerRetaliationClientAssets.QUEST_JOURNAL_ICON_COMPLETED_TEXTURE);

        private final ResourceLocation texture;

        QuestJournalEntryState(ResourceLocation texture) {
            this.texture = texture;
        }

        static QuestJournalEntryState from(QuestTrackerSyncPayload.Entry entry) {
            if (entry.questAvailable()) {
                return AVAILABLE;
            }
            String state = normalized(entry.state());
            return switch (state) {
                case "active", "accepted", "ready", "ready_to_turn_in" -> ACTIVE;
                case "completed", "complete", "done" -> COMPLETED;
                case "inactive", "locked", "unavailable", "abandoned", "expired" -> INACTIVE;
                default -> AVAILABLE;
            };
        }

        static QuestJournalEntryState iconFor(QuestTrackerSyncPayload.Entry entry) {
            QuestJournalEntryState state = from(entry);
            if (state == ACTIVE && !VillagerQuestTrackerOverlay.isTracked(entry)) {
                return INACTIVE;
            }
            return state;
        }

        ResourceLocation texture() {
            return this.texture;
        }

        ResourceLocation selectedTexture() {
            return this == COMPLETED
                    ? VillagerRetaliationClientAssets.QUEST_JOURNAL_ICON_SELECTED_QUEST_COMPLETED_TEXTURE
                    : VillagerRetaliationClientAssets.QUEST_JOURNAL_ICON_SELECTED_QUEST_TEXTURE;
        }
    }

    private record QuestDetailLine(FormattedCharSequence text, int color, int top) {
    }

    private record JournalNineSlice(
            ResourceLocation texture,
            int textureWidth,
            int textureHeight,
            int sliceLeft,
            int sliceRight,
            int sliceTop,
            int sliceBottom) {
        private void render(GuiGraphics graphics, int left, int top, int width, int height) {
            int centerSourceWidth = this.textureWidth - this.sliceLeft - this.sliceRight;
            int centerSourceHeight = this.textureHeight - this.sliceTop - this.sliceBottom;
            int centerWidth = Math.max(0, width - this.sliceLeft - this.sliceRight);
            int centerHeight = Math.max(0, height - this.sliceTop - this.sliceBottom);

            blit(graphics, left, top, this.sliceLeft, this.sliceTop, 0, 0, this.sliceLeft, this.sliceTop);
            blit(graphics, left + this.sliceLeft, top, centerWidth, this.sliceTop, this.sliceLeft, 0, centerSourceWidth, this.sliceTop);
            blit(graphics, left + width - this.sliceRight, top, this.sliceRight, this.sliceTop, this.textureWidth - this.sliceRight, 0, this.sliceRight, this.sliceTop);

            blit(graphics, left, top + this.sliceTop, this.sliceLeft, centerHeight, 0, this.sliceTop, this.sliceLeft, centerSourceHeight);
            blit(graphics, left + this.sliceLeft, top + this.sliceTop, centerWidth, centerHeight, this.sliceLeft, this.sliceTop, centerSourceWidth, centerSourceHeight);
            blit(graphics, left + width - this.sliceRight, top + this.sliceTop, this.sliceRight, centerHeight, this.textureWidth - this.sliceRight, this.sliceTop, this.sliceRight, centerSourceHeight);

            blit(graphics, left, top + height - this.sliceBottom, this.sliceLeft, this.sliceBottom, 0, this.textureHeight - this.sliceBottom, this.sliceLeft, this.sliceBottom);
            blit(graphics, left + this.sliceLeft, top + height - this.sliceBottom, centerWidth, this.sliceBottom, this.sliceLeft, this.textureHeight - this.sliceBottom, centerSourceWidth, this.sliceBottom);
            blit(graphics, left + width - this.sliceRight, top + height - this.sliceBottom, this.sliceRight, this.sliceBottom, this.textureWidth - this.sliceRight, this.textureHeight - this.sliceBottom, this.sliceRight, this.sliceBottom);
        }

        private void blit(
                GuiGraphics graphics,
                int destLeft,
                int destTop,
                int destWidth,
                int destHeight,
                int sourceLeft,
                int sourceTop,
                int sourceWidth,
                int sourceHeight) {
            if (destWidth <= 0 || destHeight <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
                return;
            }
            graphics.blit(
                    this.texture,
                    destLeft,
                    destTop,
                    destWidth,
                    destHeight,
                    (float) sourceLeft,
                    (float) sourceTop,
                    sourceWidth,
                    sourceHeight,
                    this.textureWidth,
                    this.textureHeight);
        }
    }
}
