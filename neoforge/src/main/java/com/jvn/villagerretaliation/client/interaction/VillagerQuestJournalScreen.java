package com.jvn.villagerretaliation.client.interaction;

import com.jvn.toucanlib.client.ToucanScrollState;
import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import com.jvn.villagerretaliation.client.quest.VillagerQuestKeyMappings;
import com.jvn.villagerretaliation.client.quest.VillagerQuestTrackerOverlay;
import com.jvn.villagerretaliation.client.ui.VillagerClientUiUtil;
import com.jvn.villagerretaliation.network.QuestTrackerSyncPayload;
import com.jvn.villagerretaliation.network.QuestTrackerRequestPayload;
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
import net.minecraft.network.chat.Style;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class VillagerQuestJournalScreen extends Screen {
    private static final float OPTION_SCROLL_LERP = 0.32F;
    private static final float OPTION_SCROLL_STEP = 12.0F;
    private static final float JOURNAL_ANIMATION_DURATION_MILLIS = 280.0F;

    private static final int JOURNAL_WIDTH = 351;
    private static final int JOURNAL_HEIGHT = 215;
    private static final int BOOKMARK_WIDTH = 25;
    private static final int BOOKMARK_HEIGHT = 30;
    private static final int BOOKMARK_LEFT_OFFSET = 13;
    private static final int BOOKMARK_TOP_OFFSET = 207;
    private static final int BOOKMARK_GAP = 1;
    private static final int INACTIVE_BOOKMARK_OFFSET_Y = -5;
    private static final int BOOKMARK_ICON_SIZE = 17;
    private static final int SELECTED_BOOKMARK_ICON_OFFSET_Y = -2;
    private static final int INACTIVE_BOOKMARK_ICON_OFFSET_Y = 1;
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

    private static final int DETAILS_LEFT_OFFSET = 192;
    private static final int DETAILS_TOP_OFFSET = 28;
    private static final int DETAILS_WIDTH = 137;
    private static final int DETAILS_HEIGHT = 163;
    private static final int DETAILS_LINE_STEP = 11;
    private static final int DETAILS_DIVIDER_WIDTH = 137;
    private static final int DETAILS_DIVIDER_HEIGHT = 5;
    private static final int DETAILS_TITLE_ICON_SIZE = 9;
    private static final int DETAILS_QUEST_STEP_ICON_SIZE = 7;
    private static final int DETAILS_REWARD_ITEM_SIZE = 12;
    private static final int DETAILS_QUEST_STEP_ICON_LEFT_PADDING = 0;
    private static final int DETAILS_QUEST_STEP_TEXT_GAP = 5;
    private static final int DETAILS_PAGE_LABEL_GAP = 2;
    private static final int DETAILS_ABANDON_HEIGHT = 12;

    private static final int TEXT_COLOR = 0xFF000000;
    private static final int TITLE_COLOR = 0xFF000000;
    private static final int MUTED_TEXT_COLOR = 0xFF000000;
    private static final int SELECTED_TEXT_COLOR = 0xFFFFFFFF;
    private static final int HOVERED_TEXT_COLOR = 0xFF000000;
    private static final int LINK_TEXT_COLOR = 0xFF315B8A;
    private static final int QUEST_COUNT_TEXT_COLOR = 0xFFFFFFFF;
    private static final int PAGE_TEXT_COLOR = 0xFFE1DAC7;
    private static final Style QUEST_COUNT_ACTIVE_STYLE = Style.EMPTY.withColor(0xF9CB5F);
    private static final Style QUEST_COUNT_ACCEPTED_STYLE = Style.EMPTY.withColor(0xE1DAC7);
    private static final Style QUEST_COUNT_COMPLETED_STYLE = Style.EMPTY.withColor(0xB5F45B);
    private static final Style QUEST_COUNT_NEARBY_STYLE = Style.EMPTY.withColor(0x5C96EF);
    private static final String GUI_KEY_PREFIX = "villagerretaliation.gui.quest_journal.";

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
    private int detailsPage;
    private String selectedQuestId = "";
    private String searchQuery = "";
    private boolean searchActive;
    private QuestJournalTab selectedTab = QuestJournalTab.AVAILABLE;
    private boolean draggingOptionScrollbar;
    private float optionScrollbarDragOffset;
    private boolean closingWithAnimation;
    private boolean openedSoundPlayed;
    private long animationStartMillis = -1L;
    private final float cinematicBarSlant = VillagerDialogueCinematicBars.sampleSlant();

    public VillagerQuestJournalScreen() {
        super(Component.translatable(GUI_KEY_PREFIX + "title"));
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
        if (ClientVillagerConversationState.active()) {
            VillagerDialogueCinematicBars.render(graphics, this.width, this.height, 1.0F, this.cinematicBarSlant);
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, slideOffset, 0.0F);
        renderJournalContainer(graphics);
        renderTabTitle(graphics);
        renderQuestCountBadge(graphics);
        renderQuestOptions(graphics, mouseX, journalMouseY, slideOffset);
        renderQuestDetails(graphics, slideOffset);
        graphics.pose().popPose();
        renderQuestlineTooltip(graphics, mouseX, mouseY, slideOffset);
        renderPreviousStepTooltip(graphics, mouseX, mouseY, slideOffset);
        renderQuestCountTooltip(graphics, mouseX, mouseY, slideOffset);
        renderBookmarkTooltip(graphics, mouseX, mouseY, slideOffset);
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
                if (this.searchActive || !this.searchQuery.isBlank()) {
                    this.searchActive = false;
                    this.searchQuery = "";
                    resetFilteredSelection();
                    yield true;
                }
                closeJournal();
                yield true;
            }
            case GLFW.GLFW_KEY_SLASH -> {
                this.searchActive = true;
                yield true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (this.searchActive && !this.searchQuery.isEmpty()) {
                    this.searchQuery = this.searchQuery.substring(0, this.searchQuery.length() - 1);
                    resetFilteredSelection();
                    yield true;
                }
                yield super.keyPressed(keyCode, scanCode, modifiers);
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
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.searchActive && !Character.isISOControl(codePoint) && this.searchQuery.length() < 48) {
            this.searchQuery += codePoint;
            resetFilteredSelection();
            return true;
        }
        return super.charTyped(codePoint, modifiers);
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

        QuestDetailLine previousStepLink = detailLinkAt(mouseX, journalMouseY);
        if (previousStepLink != null && openQuestPage(previousStepLink.targetQuestId())) {
            return true;
        }

        QuestTrackerSyncPayload.Entry selected = selectedEntry();
        if (canAbandon(selected) && isPointInside(
                mouseX,
                journalMouseY,
                detailsLeft(),
                detailsTop() + DETAILS_HEIGHT - DETAILS_ABANDON_HEIGHT,
                detailsLeft() + DETAILS_WIDTH,
                detailsTop() + DETAILS_HEIGHT)) {
            PacketDistributor.sendToServer(new QuestTrackerRequestPayload(
                    selected.questId(), QuestTrackerRequestPayload.Action.ABANDON));
            playBookSound(0.78F);
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
        if (scrollY != 0.0D && isPointInsideDetailsScrollArea(mouseX, journalMouseY) && turnDetailsPage(scrollY < 0.0D ? 1 : -1)) {
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

    public boolean isClosingWithAnimation() {
        return this.closingWithAnimation;
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
        Component title = this.searchActive || !this.searchQuery.isBlank()
                ? Component.translatable(GUI_KEY_PREFIX + "search", this.searchQuery.isBlank() ? "_" : this.searchQuery)
                : this.selectedTab.title();
        graphics.drawString(
                this.font,
                title,
                journalLeft() + TAB_TITLE_LEFT_OFFSET,
                journalTop() + TAB_TITLE_BOTTOM_OFFSET - this.font.lineHeight,
                TITLE_COLOR,
                false);
    }

    private void renderQuestCountBadge(GuiGraphics graphics) {
        String count = Integer.toString(visibleEntries().size());
        int textWidth = this.font.width(count);
        int badgeWidth = questCountBadgeWidth(textWidth);
        int badgeLeft = questCountBadgeLeft(badgeWidth);
        int badgeTop = questCountBadgeTop();
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

    private void renderQuestCountTooltip(GuiGraphics graphics, int mouseX, int mouseY, int slideOffset) {
        String count = Integer.toString(visibleEntries().size());
        int badgeWidth = questCountBadgeWidth(this.font.width(count));
        int badgeLeft = questCountBadgeLeft(badgeWidth);
        int badgeTop = questCountBadgeTop() + slideOffset;
        if (!isPointInside(mouseX, mouseY, badgeLeft, badgeTop, badgeLeft + badgeWidth, badgeTop + QUEST_COUNT_BADGE_HEIGHT)) {
            return;
        }
        graphics.renderComponentTooltip(this.font, questCountTooltip(), mouseX, mouseY);
    }

    private int questCountBadgeWidth(int textWidth) {
        return Math.max(
                QUEST_COUNT_BADGE_WIDTH,
                textWidth + QUEST_COUNT_BADGE_HORIZONTAL_PADDING * 2 + QUEST_COUNT_BADGE_INNER_INSET);
    }

    private int questCountBadgeLeft(int badgeWidth) {
        return optionsLeft() + QUEST_OPTION_WIDTH - badgeWidth;
    }

    private int questCountBadgeTop() {
        return optionsTop() - QUEST_COUNT_BADGE_BOTTOM_GAP - QUEST_COUNT_BADGE_HEIGHT;
    }

    private List<Component> questCountTooltip() {
        QuestCountSummary summary = questCountSummary();
        return switch (this.selectedTab) {
            case AVAILABLE -> List.of(
                    Component.translatable(GUI_KEY_PREFIX + "count.title").withStyle(Style.EMPTY.withColor(0xA0A0A0)),
                    Component.translatable(GUI_KEY_PREFIX + "count.active", summary.active()).withStyle(QUEST_COUNT_ACTIVE_STYLE),
                    Component.translatable(GUI_KEY_PREFIX + "count.accepted", summary.accepted()).withStyle(QUEST_COUNT_ACCEPTED_STYLE),
                    Component.translatable(GUI_KEY_PREFIX + "count.nearby", summary.nearby()).withStyle(QUEST_COUNT_NEARBY_STYLE));
            case ACTIVE -> List.of(
                    Component.translatable(GUI_KEY_PREFIX + "count.title").withStyle(Style.EMPTY.withColor(0xA0A0A0)),
                    Component.translatable(GUI_KEY_PREFIX + "count.active", summary.active()).withStyle(QUEST_COUNT_ACTIVE_STYLE),
                    Component.translatable(GUI_KEY_PREFIX + "count.accepted", summary.accepted()).withStyle(QUEST_COUNT_ACCEPTED_STYLE));
            case COMPLETED -> List.of(
                    Component.translatable(GUI_KEY_PREFIX + "count.title").withStyle(Style.EMPTY.withColor(0xA0A0A0)),
                    Component.translatable(GUI_KEY_PREFIX + "count.completed", summary.completed()).withStyle(QUEST_COUNT_COMPLETED_STYLE));
        };
    }

    private void renderBookmarkTooltip(GuiGraphics graphics, int mouseX, int mouseY, int slideOffset) {
        QuestJournalTab hoveredTab = bookmarkAt(mouseX, mouseY - slideOffset);
        if (hoveredTab == null) {
            return;
        }
        graphics.renderComponentTooltip(this.font, List.of(hoveredTab.tooltip()), mouseX, mouseY);
    }

    private static QuestCountSummary questCountSummary() {
        int active = 0;
        int accepted = 0;
        int nearby = 0;
        int completed = 0;
        for (QuestTrackerSyncPayload.Entry entry : entries()) {
            QuestJournalEntryState state = QuestJournalEntryState.from(entry);
            if (entry.questAvailable()) {
                nearby++;
            } else if (state == QuestJournalEntryState.ACTIVE && VillagerQuestTrackerOverlay.isTracked(entry)) {
                active++;
            } else if (state == QuestJournalEntryState.ACTIVE) {
                accepted++;
            } else if (state == QuestJournalEntryState.COMPLETED) {
                completed++;
            }
        }
        return new QuestCountSummary(active, accepted, nearby, completed);
    }

    private void renderBookmarks(GuiGraphics graphics, int journalLeft, int journalTop) {
        int left = journalLeft + BOOKMARK_LEFT_OFFSET;
        for (QuestJournalTab tab : QuestJournalTab.values()) {
            int bookmarkLeft = bookmarkLeft(left, tab);
            int bookmarkTop = bookmarkTop(journalTop, tab);
            renderBookmark(graphics, tab.texture(), bookmarkLeft, bookmarkTop);
            renderBookmarkIcon(graphics, tab.iconTexture(), bookmarkLeft, bookmarkTop, tab == this.selectedTab);
        }
    }

    private void renderBookmark(GuiGraphics graphics, ResourceLocation texture, int left, int top) {
        graphics.blit(texture, left, top, 0, 0, BOOKMARK_WIDTH, BOOKMARK_HEIGHT, BOOKMARK_WIDTH, BOOKMARK_HEIGHT);
    }

    private void renderBookmarkIcon(
            GuiGraphics graphics,
            ResourceLocation texture,
            int bookmarkLeft,
            int bookmarkTop,
            boolean selected) {
        int left = bookmarkLeft + (BOOKMARK_WIDTH - BOOKMARK_ICON_SIZE) / 2;
        int top = bookmarkTop + (BOOKMARK_HEIGHT - BOOKMARK_ICON_SIZE) / 2;
        top += selected ? SELECTED_BOOKMARK_ICON_OFFSET_Y : INACTIVE_BOOKMARK_ICON_OFFSET_Y;
        graphics.blit(
                texture,
                left,
                top,
                0,
                0,
                BOOKMARK_ICON_SIZE,
                BOOKMARK_ICON_SIZE,
                BOOKMARK_ICON_SIZE,
                BOOKMARK_ICON_SIZE);
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
        ItemStack authoredIcon = journalIcon(entry);
        if (!authoredIcon.isEmpty()) {
            float scale = QUEST_OPTION_STATE_ICON_SIZE / 16.0F;
            graphics.pose().pushPose();
            graphics.pose().translate(iconLeft, iconTop, 0.0F);
            graphics.pose().scale(scale, scale, 1.0F);
            graphics.renderItem(authoredIcon, 0, 0);
            graphics.pose().popPose();
        } else {
            renderQuestOptionIcon(graphics, state.texture(), iconLeft, iconTop);
            if (selected) {
                renderQuestOptionIcon(graphics, state.selectedTexture(), iconLeft, iconTop);
            }
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

    private static ItemStack journalIcon(QuestTrackerSyncPayload.Entry entry) {
        ResourceLocation icon = entry == null ? null : ResourceLocation.tryParse(entry.journal().icon());
        return icon == null
                ? ItemStack.EMPTY
                : BuiltInRegistries.ITEM.getOptional(icon).map(ItemStack::new).orElse(ItemStack.EMPTY);
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
        QuestTrackerSyncPayload.Entry entry = visibleEntries().get(index);
        int authoredColor = journalColor(entry, TEXT_COLOR);
        int textColor = selected ? SELECTED_TEXT_COLOR : index == hovered ? HOVERED_TEXT_COLOR : authoredColor;
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
        List<QuestDetailLine> detailLines = buildQuestDetailLines(selected, DETAILS_WIDTH, DETAILS_LINE_STEP);
        int contentHeight = detailContentHeight(detailLines);
        int viewportHeight = detailsPageViewportHeight(selected);
        int maskBottom = top + viewportHeight;
        boolean paged = contentHeight > viewportHeight;
        List<Integer> pageStarts = detailPageStarts(detailLines, viewportHeight);
        int pageCount = pageStarts.size();
        this.detailsPage = Mth.clamp(this.detailsPage, 0, pageCount - 1);
        int pageTop = pageStarts.get(this.detailsPage);

        graphics.enableScissor(left, top + slideOffset, right, maskBottom + slideOffset);
        for (QuestDetailLine line : detailLines) {
            if (line.top() < pageTop || line.top() + line.height() > pageTop + viewportHeight) {
                continue;
            }
            float lineTop = top + line.top() - pageTop;
            float lineBottom = lineTop + line.height();
            if (lineBottom < top || lineTop > maskBottom) {
                continue;
            }
            renderQuestDetailLine(graphics, line, left, Mth.floor(lineTop));
        }
        graphics.disableScissor();

        if (paged) {
            renderDetailsPageLabel(graphics, left, maskBottom + DETAILS_PAGE_LABEL_GAP, this.detailsPage + 1, pageCount);
        }
        if (canAbandon(selected)) {
            Component label = Component.translatable(GUI_KEY_PREFIX + "abandon");
            int actionTop = top + DETAILS_HEIGHT - DETAILS_ABANDON_HEIGHT + 2;
            graphics.drawString(
                    this.font,
                    label,
                    left + Math.round((DETAILS_WIDTH - this.font.width(label)) / 2.0F),
                    actionTop,
                    0xFF8A1F1F,
                    false);
        }
    }

    private static List<Integer> detailPageStarts(List<QuestDetailLine> lines, int viewportHeight) {
        if (lines.isEmpty()) {
            return List.of(0);
        }
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        int pageStart = 0;
        for (QuestDetailLine line : lines) {
            if (line.top() + line.height() <= pageStart + viewportHeight) {
                continue;
            }
            if (line.top() <= pageStart) {
                continue;
            }
            pageStart = line.top();
            starts.add(pageStart);
        }
        return starts;
    }

    private void renderDetailsPageLabel(GuiGraphics graphics, int left, int top, int page, int pageCount) {
        String text = "Page: " + page + "/" + pageCount;
        graphics.drawString(
                this.font,
                text,
                left + Math.round((DETAILS_WIDTH - this.font.width(text)) / 2.0F),
                top,
                PAGE_TEXT_COLOR,
                false);
    }

    private void renderQuestDetailLine(GuiGraphics graphics, QuestDetailLine line, int left, int top) {
        if (line.divider()) {
            int dividerLeft = left + Math.max(0, (DETAILS_WIDTH - DETAILS_DIVIDER_WIDTH) / 2);
            graphics.blit(
                    VillagerRetaliationClientAssets.QUEST_JOURNAL_DIVIDER_TEXTURE,
                    dividerLeft,
                    top,
                    0,
                    0,
                    DETAILS_DIVIDER_WIDTH,
                    DETAILS_DIVIDER_HEIGHT,
                    DETAILS_DIVIDER_WIDTH,
                    DETAILS_DIVIDER_HEIGHT);
            return;
        }
        int textLeft = left;
        ItemStack rewardItem = rewardItemStack(line.itemId());
        if (!rewardItem.isEmpty()) {
            float scale = DETAILS_REWARD_ITEM_SIZE / 16.0F;
            graphics.pose().pushPose();
            graphics.pose().translate(left + DETAILS_QUEST_STEP_ICON_LEFT_PADDING, top - 2, 0.0F);
            graphics.pose().scale(scale, scale, 1.0F);
            graphics.renderItem(rewardItem, 0, 0);
            graphics.pose().popPose();
            textLeft += rewardItemTextIndent();
        } else if (line.icon() != null) {
            int iconSize = line.titleIcon() ? DETAILS_TITLE_ICON_SIZE : DETAILS_QUEST_STEP_ICON_SIZE;
            int iconTop = top + (line.titleIcon() ? -1 : 0);
            graphics.blit(
                    line.icon(),
                    left + DETAILS_QUEST_STEP_ICON_LEFT_PADDING,
                    iconTop,
                    0,
                    0,
                    iconSize,
                    iconSize,
                    iconSize,
                    iconSize);
            textLeft += DETAILS_QUEST_STEP_ICON_LEFT_PADDING + iconSize + DETAILS_QUEST_STEP_TEXT_GAP;
        }
        if (line.text() == null) {
            return;
        }
        if (line.centered()) {
            int textWidth = this.font.width(line.text());
            textLeft = left + Math.round((DETAILS_WIDTH - textWidth) / 2.0F);
        }
        graphics.drawString(this.font, line.text(), textLeft, top, line.color(), false);
    }

    private List<QuestDetailLine> buildQuestDetailLines(QuestTrackerSyncPayload.Entry selected, int wrapWidth, int lineStep) {
        List<QuestDetailLine> lines = new ArrayList<>();
        int y = 1;
        y = addWrappedDetailLines(
                lines,
                selected.title(),
                wrapWidth - titleIconTextIndent(),
                journalColor(selected, TITLE_COLOR),
                y,
                lineStep,
                0,
                QuestJournalEntryState.from(selected).texture(),
                true);
        y = addDividerLine(lines, y + 3, 3);
        y = addWrappedDetailLines(lines, statusLine(selected), wrapWidth, MUTED_TEXT_COLOR, y, lineStep, 2);
        QuestTrackerSyncPayload.Journal journal = selected.journal();
        if (!journal.blocker().isBlank()) {
            y = addWrappedDetailLines(
                    lines,
                    Component.translatable(GUI_KEY_PREFIX + "waiting_for", journal.blocker()),
                    wrapWidth,
                    TEXT_COLOR,
                    y,
                    lineStep,
                    1);
        }
        if (!journal.questline().isBlank()) {
            y = addWrappedDetailLines(lines, questlineLine(journal), wrapWidth, MUTED_TEXT_COLOR, y, lineStep, 1);
        }
        if (!journal.tags().isEmpty()) {
            y = addWrappedDetailLines(lines, Component.translatable(GUI_KEY_PREFIX + "tags", String.join(", ", journal.tags())), wrapWidth, MUTED_TEXT_COLOR, y, lineStep, 1);
        }
        Component timing = journalTimingLine(selected);
        if (timing != null) {
            y = addWrappedDetailLines(lines, timing, wrapWidth, MUTED_TEXT_COLOR, y, lineStep, 1);
        }
        Component waypoint = journalWaypointLine(selected);
        if (waypoint != null) {
            y = addWrappedDetailLines(lines, waypoint, wrapWidth, MUTED_TEXT_COLOR, y, lineStep, 1);
        }
        if (!selected.issuer().isBlank()) {
            y = addWrappedDetailLines(
                    lines,
                    Component.translatable(GUI_KEY_PREFIX + "issuer", selected.issuer()),
                    wrapWidth,
                    MUTED_TEXT_COLOR,
                    y,
                    lineStep,
                    2);
        }
        if (!selected.issuerLocation().isBlank()) {
            y = addWrappedDetailLines(lines, selected.issuerLocation(), wrapWidth, MUTED_TEXT_COLOR, y, lineStep, 0);
        }
        y = addDividerLine(lines, y + 3, 3);
        y = addWrappedDetailLines(lines, descriptionLine(selected), wrapWidth, TEXT_COLOR, y, lineStep, 0);
        if (!selected.prerequisites().isEmpty()) {
            y = addDividerLine(lines, y + 3, 3);
            String sectionKey = selected.prerequisites().size() == 1
                    ? "section.previous_step"
                    : "section.previous_steps";
            y = addCenteredDetailLine(lines, Component.translatable(GUI_KEY_PREFIX + sectionKey), TITLE_COLOR, y, lineStep, 0);
            y = addDividerLine(lines, y + 3, 3);
            for (QuestTrackerSyncPayload.Prerequisite prerequisite : selected.prerequisites()) {
                QuestTrackerSyncPayload.Entry target = journalEntryForQuestId(prerequisite.questId());
                if (target == null) {
                    y = addQuestStepLines(lines, prerequisiteLine(prerequisite), prerequisite.met(), y, lineStep, 2);
                } else {
                    y = addQuestLinkLines(
                            lines,
                            prerequisiteLine(prerequisite),
                            prerequisite.met(),
                            prerequisite.questId(),
                            y,
                            lineStep,
                            2);
                }
            }
        }
        y = addDividerLine(lines, y + 3, 3);
        y = addCenteredDetailLine(lines, Component.translatable(GUI_KEY_PREFIX + "section.objectives"), TITLE_COLOR, y, lineStep, 0);
        y = addDividerLine(lines, y + 3, 3);
        boolean completed = selected.progress() >= 1.0F || QuestJournalEntryState.from(selected) == QuestJournalEntryState.COMPLETED;
        if (!selected.objectiveSteps().isEmpty()) {
            for (QuestTrackerSyncPayload.ObjectiveStep objectiveStep : selected.objectiveSteps()) {
                y = addQuestStepLines(lines, objectiveStep.label(), objectiveStep.completed(), true, y, lineStep, 2);
            }
        } else if (selected.questItems().isEmpty()) {
            y = addQuestStepLines(lines, selected.objective(), completed, y, lineStep, 2);
        } else {
            for (QuestTrackerSyncPayload.QuestItem item : selected.questItems()) {
                y = addQuestStepLines(lines, questItemLine(item), questItemComplete(item), y, lineStep, 2);
            }
        }
        if (!selected.rewardPreviews().isEmpty()) {
            y = addDividerLine(lines, y + 3, 3);
            y = addCenteredDetailLine(lines, Component.translatable(GUI_KEY_PREFIX + "section.rewards"), TITLE_COLOR, y, lineStep, 0);
            y = addDividerLine(lines, y + 3, 3);
            for (QuestTrackerSyncPayload.RewardPreview reward : selected.rewardPreviews()) {
                if (rewardItemStack(reward.itemId()).isEmpty()) {
                    y = addQuestStepLines(lines, rewardPreviewLine(reward), completed, y, lineStep, 2);
                } else {
                    y = addItemRewardLines(lines, reward, y, lineStep, 2);
                }
            }
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
        return addWrappedDetailLines(lines, text, wrapWidth, color, top, lineStep, gapAfter, null, false);
    }

    private int addWrappedDetailLines(
            List<QuestDetailLine> lines,
            Component text,
            int wrapWidth,
            int color,
            int top,
            int lineStep,
            int gapAfter) {
        return addWrappedDetailLines(lines, text, wrapWidth, color, top, lineStep, gapAfter, null, false, false);
    }

    private int addWrappedDetailLines(
            List<QuestDetailLine> lines,
            String text,
            int wrapWidth,
            int color,
            int top,
            int lineStep,
            int gapAfter,
            ResourceLocation firstLineIcon,
            boolean titleIcon) {
        return addWrappedDetailLines(lines, text, wrapWidth, color, top, lineStep, gapAfter, firstLineIcon, titleIcon, false);
    }

    private int addWrappedDetailLines(
            List<QuestDetailLine> lines,
            String text,
            int wrapWidth,
            int color,
            int top,
            int lineStep,
            int gapAfter,
            ResourceLocation firstLineIcon,
            boolean titleIcon,
            boolean strikethrough) {
        if (text == null || text.isBlank() || wrapWidth <= 0) {
            return top;
        }
        Component component = strikethrough
                ? Component.literal(text).withStyle(Style.EMPTY.withStrikethrough(true))
                : Component.literal(text);
        return addWrappedDetailLines(lines, component, wrapWidth, color, top, lineStep, gapAfter, firstLineIcon, titleIcon, false);
    }

    private int addWrappedDetailLines(
            List<QuestDetailLine> lines,
            Component component,
            int wrapWidth,
            int color,
            int top,
            int lineStep,
            int gapAfter,
            ResourceLocation firstLineIcon,
            boolean titleIcon,
            boolean strikethrough) {
        if (component == null || wrapWidth <= 0) {
            return top;
        }
        if (strikethrough) {
            component = component.copy().withStyle(Style.EMPTY.withStrikethrough(true));
        }
        int y = top;
        boolean first = true;
        for (FormattedCharSequence line : this.font.split(component, wrapWidth)) {
            lines.add(new QuestDetailLine(
                    line,
                    color,
                    y,
                    this.font.lineHeight,
                    first ? firstLineIcon : null,
                    false,
                    false,
                    first && titleIcon));
            y += lineStep;
            first = false;
        }
        return y - lineStep + this.font.lineHeight + gapAfter;
    }

    private int addCenteredDetailLine(
            List<QuestDetailLine> lines,
            Component text,
            int color,
            int top,
            int lineStep,
            int gapAfter) {
        if (text == null) {
            return top;
        }
        lines.add(new QuestDetailLine(
                text.getVisualOrderText(),
                color,
                top,
                this.font.lineHeight,
                null,
                true,
                false,
                false));
        return top + this.font.lineHeight + gapAfter;
    }

    private int addDividerLine(List<QuestDetailLine> lines, int top, int gapAfter) {
        lines.add(new QuestDetailLine(null, TEXT_COLOR, top, DETAILS_DIVIDER_HEIGHT, null, false, true, false));
        return top + DETAILS_DIVIDER_HEIGHT + gapAfter;
    }

    private int addQuestStepLines(
            List<QuestDetailLine> lines,
            String text,
            boolean completed,
            int top,
            int lineStep,
            int gapAfter) {
        return addQuestStepLines(lines, text, completed, false, top, lineStep, gapAfter);
    }

    private int addQuestStepLines(
            List<QuestDetailLine> lines,
            String text,
            boolean completed,
            boolean strikeCompleted,
            int top,
            int lineStep,
            int gapAfter) {
        ResourceLocation icon = completed
                ? VillagerRetaliationClientAssets.QUEST_JOURNAL_ICON_QUEST_STEP_COMPLETED_TEXTURE
                : VillagerRetaliationClientAssets.QUEST_JOURNAL_ICON_QUEST_STEP_TEXTURE;
        return addWrappedDetailLines(
                lines,
                text,
                Math.max(1, DETAILS_WIDTH - questStepTextIndent()),
                TEXT_COLOR,
                top,
                lineStep,
                gapAfter,
                icon,
                false,
                strikeCompleted && completed);
    }

    private int addQuestLinkLines(
            List<QuestDetailLine> lines,
            String text,
            boolean completed,
            String targetQuestId,
            int top,
            int lineStep,
            int gapAfter) {
        if (text == null || text.isBlank() || targetQuestId == null || targetQuestId.isBlank()) {
            return top;
        }
        ResourceLocation icon = completed
                ? VillagerRetaliationClientAssets.QUEST_JOURNAL_ICON_QUEST_STEP_COMPLETED_TEXTURE
                : VillagerRetaliationClientAssets.QUEST_JOURNAL_ICON_QUEST_STEP_TEXTURE;
        Component component = Component.literal(text).withStyle(Style.EMPTY.withUnderlined(true));
        int y = top;
        boolean first = true;
        for (FormattedCharSequence line : this.font.split(component, Math.max(1, DETAILS_WIDTH - questStepTextIndent()))) {
            lines.add(new QuestDetailLine(
                    line,
                    LINK_TEXT_COLOR,
                    y,
                    this.font.lineHeight,
                    first ? icon : null,
                    false,
                    false,
                    false,
                    targetQuestId));
            y += lineStep;
            first = false;
        }
        return y - lineStep + this.font.lineHeight + gapAfter;
    }

    private int addItemRewardLines(
            List<QuestDetailLine> lines,
            QuestTrackerSyncPayload.RewardPreview reward,
            int top,
            int lineStep,
            int gapAfter) {
        ItemStack stack = rewardItemStack(reward.itemId());
        if (stack.isEmpty()) {
            return addQuestStepLines(lines, rewardPreviewLine(reward), false, top, lineStep, gapAfter);
        }
        Component text = Component.literal("x" + reward.label() + " ").append(stack.getHoverName());
        int y = top;
        boolean first = true;
        int rowStep = Math.max(lineStep, DETAILS_REWARD_ITEM_SIZE);
        for (FormattedCharSequence line : this.font.split(text, Math.max(1, DETAILS_WIDTH - rewardItemTextIndent()))) {
            lines.add(new QuestDetailLine(
                    line,
                    TEXT_COLOR,
                    y,
                    Math.max(this.font.lineHeight, first ? DETAILS_REWARD_ITEM_SIZE : this.font.lineHeight),
                    null,
                    false,
                    false,
                    false,
                    "",
                    first ? reward.itemId() : ""));
            y += rowStep;
            first = false;
        }
        return y - rowStep + Math.max(this.font.lineHeight, DETAILS_REWARD_ITEM_SIZE) + gapAfter;
    }

    private static int detailContentHeight(List<QuestDetailLine> detailLines) {
        if (detailLines.isEmpty()) {
            return 0;
        }
        QuestDetailLine lastLine = detailLines.get(detailLines.size() - 1);
        return lastLine.top() + lastLine.height();
    }

    private static Component statusLine(QuestTrackerSyncPayload.Entry entry) {
        if (entry.status().isBlank()) {
            String key = entry.trackable() && VillagerQuestTrackerOverlay.isTracked(entry) ? "tracked" : "not_tracked";
            return Component.translatable(GUI_KEY_PREFIX + "status." + key);
        }
        if (!entry.trackable()) {
            return Component.translatable(GUI_KEY_PREFIX + "status.value", entry.status());
        }
        return VillagerQuestTrackerOverlay.isTracked(entry)
                ? Component.translatable(GUI_KEY_PREFIX + "status.value_tracked", entry.status())
                : Component.translatable(GUI_KEY_PREFIX + "status.value_not_tracked", entry.status());
    }

    private static String descriptionLine(QuestTrackerSyncPayload.Entry entry) {
        return entry.description().isBlank() ? entry.objective() : entry.description();
    }

    private static String questItemLine(QuestTrackerSyncPayload.QuestItem item) {
        int current = Math.max(0, Math.min(item.count(), item.currentCount()));
        return item.label() + " " + current + "/" + item.count();
    }

    private static boolean questItemComplete(QuestTrackerSyncPayload.QuestItem item) {
        return item.count() <= 0 || item.currentCount() >= item.count();
    }

    private static String rewardPreviewLine(QuestTrackerSyncPayload.RewardPreview reward) {
        return reward.label();
    }

    private static ItemStack rewardItemStack(String itemId) {
        ResourceLocation id = itemId == null ? null : ResourceLocation.tryParse(itemId);
        return id == null
                ? ItemStack.EMPTY
                : BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    private static String prerequisiteLine(QuestTrackerSyncPayload.Prerequisite prerequisite) {
        return prerequisite.label();
    }

    private static int questStepTextIndent() {
        return DETAILS_QUEST_STEP_ICON_LEFT_PADDING + DETAILS_QUEST_STEP_ICON_SIZE + DETAILS_QUEST_STEP_TEXT_GAP;
    }

    private static int rewardItemTextIndent() {
        return DETAILS_QUEST_STEP_ICON_LEFT_PADDING + DETAILS_REWARD_ITEM_SIZE + DETAILS_QUEST_STEP_TEXT_GAP;
    }

    private static int titleIconTextIndent() {
        return DETAILS_QUEST_STEP_ICON_LEFT_PADDING + DETAILS_TITLE_ICON_SIZE + DETAILS_QUEST_STEP_TEXT_GAP;
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
        this.detailsPage = 0;
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
                this.detailsPage = 0;
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
        this.detailsPage = 0;
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

    private boolean turnDetailsPage(int direction) {
        QuestTrackerSyncPayload.Entry selected = selectedEntry();
        if (selected == null || direction == 0) {
            return false;
        }
        int pageCount = detailsPageCount(selected);
        if (pageCount <= 1) {
            return false;
        }
        int nextPage = Mth.clamp(this.detailsPage + direction, 0, pageCount - 1);
        if (nextPage == this.detailsPage) {
            return false;
        }
        this.detailsPage = nextPage;
        playBookSound(direction > 0 ? 0.92F : 1.08F);
        return true;
    }

    private int detailsPageCount(QuestTrackerSyncPayload.Entry selected) {
        return detailPageStarts(
                        buildQuestDetailLines(selected, DETAILS_WIDTH, DETAILS_LINE_STEP),
                        detailsPageViewportHeight(selected))
                .size();
    }

    private static int detailsPageViewportHeight(QuestTrackerSyncPayload.Entry selected) {
        return Math.max(1, DETAILS_HEIGHT - (canAbandon(selected) ? DETAILS_ABANDON_HEIGHT : 0));
    }

    private static boolean canAbandon(QuestTrackerSyncPayload.Entry entry) {
        return entry != null && "active".equalsIgnoreCase(entry.state());
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

    private void renderQuestlineTooltip(GuiGraphics graphics, int mouseX, int mouseY, int slideOffset) {
        QuestTrackerSyncPayload.Entry entry = questTitleAt(mouseX, mouseY - slideOffset);
        if (entry == null || entry.journal().questline().isBlank()) {
            return;
        }
        graphics.renderComponentTooltip(
                this.font,
                List.of(questlineLine(entry.journal())),
                mouseX,
                mouseY);
    }

    private static Component questlineLine(QuestTrackerSyncPayload.Journal journal) {
        if (journal.questlineTotal() > 0) {
            return Component.translatable(
                    GUI_KEY_PREFIX + "questline_progress",
                    journal.questline(),
                    journal.questlineCompleted(),
                    journal.questlineTotal());
        }
        return Component.translatable(GUI_KEY_PREFIX + "questline", journal.questline());
    }

    private QuestTrackerSyncPayload.Entry questTitleAt(double mouseX, double journalMouseY) {
        int index = questOptionAt(mouseX, journalMouseY);
        if (index < 0) {
            return null;
        }
        int left = optionsLeft() + QUEST_OPTION_TEXT_LEFT_PADDING;
        int top = Mth.floor(optionsTop() + optionOffset(index) - optionRenderScroll()) + QUEST_OPTION_TEXT_TOP_PADDING;
        List<FormattedCharSequence> titleLines = questOptionTitleLines(index);
        int width = titleLines.stream().mapToInt(this.font::width).max().orElse(0);
        int height = titleLines.isEmpty()
                ? 0
                : this.font.lineHeight + (titleLines.size() - 1) * (this.font.lineHeight + QUEST_OPTION_TEXT_LINE_GAP);
        return isPointInside(mouseX, journalMouseY, left, top, left + width, top + height)
                ? visibleEntries().get(index)
                : null;
    }

    private void renderPreviousStepTooltip(GuiGraphics graphics, int mouseX, int mouseY, int slideOffset) {
        QuestDetailLine link = detailLinkAt(mouseX, mouseY - slideOffset);
        if (link == null) {
            return;
        }
        graphics.renderComponentTooltip(
                this.font,
                List.of(Component.translatable(GUI_KEY_PREFIX + "view_previous_step")),
                mouseX,
                mouseY);
    }

    private QuestDetailLine detailLinkAt(double mouseX, double journalMouseY) {
        QuestTrackerSyncPayload.Entry selected = selectedEntry();
        if (selected == null || !isPointInsideDetailsScrollArea(mouseX, journalMouseY)) {
            return null;
        }
        List<QuestDetailLine> lines = buildQuestDetailLines(selected, DETAILS_WIDTH, DETAILS_LINE_STEP);
        int viewportHeight = detailsPageViewportHeight(selected);
        List<Integer> pageStarts = detailPageStarts(lines, viewportHeight);
        int page = Mth.clamp(this.detailsPage, 0, pageStarts.size() - 1);
        int pageTop = pageStarts.get(page);
        for (QuestDetailLine line : lines) {
            if (!line.link() || line.top() < pageTop || line.top() + line.height() > pageTop + viewportHeight) {
                continue;
            }
            int top = detailsTop() + line.top() - pageTop;
            int left = detailsLeft() + (line.icon() == null ? 0 : questStepTextIndent());
            int width = line.text() == null ? 0 : this.font.width(line.text());
            if (isPointInside(mouseX, journalMouseY, left, top, left + width, top + line.height())) {
                return line;
            }
        }
        return null;
    }

    private boolean openQuestPage(String targetQuestId) {
        QuestTrackerSyncPayload.Entry target = journalEntryForQuestId(targetQuestId);
        if (target == null) {
            return false;
        }
        this.searchActive = false;
        this.searchQuery = "";
        QuestJournalTab targetTab = switch (QuestJournalEntryState.from(target)) {
            case COMPLETED -> QuestJournalTab.COMPLETED;
            case ACTIVE -> QuestJournalTab.ACTIVE;
            default -> QuestJournalTab.AVAILABLE;
        };
        selectTab(targetTab);
        int index = indexOfQuestId(visibleEntries(), target.questId());
        if (index < 0) {
            return false;
        }
        setSelectedOption(index);
        acknowledgeSelectedQuestUpdate();
        ensureSelectedVisible(true);
        return true;
    }

    private static QuestTrackerSyncPayload.Entry journalEntryForQuestId(String questId) {
        if (questId == null || questId.isBlank()) {
            return null;
        }
        for (QuestTrackerSyncPayload.Entry entry : entries()) {
            if (!entry.journal().hidden() && questId.equals(entry.questId())) {
                return entry;
            }
        }
        for (QuestTrackerSyncPayload.Entry entry : entries()) {
            if (!entry.journal().hidden() && questId.equals(baseQuestId(entry.questId()))) {
                return entry;
            }
        }
        return null;
    }

    private static String baseQuestId(String questId) {
        if (questId == null) {
            return "";
        }
        int historySuffix = questId.indexOf("#completed/");
        return historySuffix < 0 ? questId : questId.substring(0, historySuffix);
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

    private float maxOptionScroll() {
        return ToucanScrollState.maxScroll(optionContentHeight(), optionViewportHeight());
    }

    private float maxDetailsScroll() {
        return 0.0F;
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
        String query = normalized(this.searchQuery);
        return entries().stream()
                .filter(entry -> !entry.journal().hidden())
                .filter(entry -> this.selectedTab.includes(QuestJournalEntryState.from(entry)))
                .filter(entry -> query.isBlank() || journalSearchText(entry).contains(query))
                .sorted(Comparator.comparingInt(VillagerQuestJournalScreen::activeTrackedSortKey)
                        .thenComparing(Comparator.comparingInt((QuestTrackerSyncPayload.Entry entry) -> entry.journal().priority()).reversed())
                        .thenComparing(entry -> normalized(entry.journal().questline()))
                        .thenComparing(entry -> normalized(entry.title())))
                .toList();
    }

    private void resetFilteredSelection() {
        this.state.resetOptions(!visibleEntries().isEmpty());
        this.visualOptionScroll = 0.0F;
        this.visualDetailsScroll = 0.0F;
        this.selectedQuestId = "";
        this.detailsPage = 0;
    }

    private static String journalSearchText(QuestTrackerSyncPayload.Entry entry) {
        return normalized(String.join(" ",
                entry.title(), entry.description(), entry.objective(), entry.status(), entry.journal().questline(),
                String.join(" ", entry.journal().tags())));
    }

    private static int journalColor(QuestTrackerSyncPayload.Entry entry, int fallback) {
        String value = entry == null ? "" : entry.journal().color().trim();
        if (value.startsWith("#") && value.length() == 7) {
            try {
                return 0xFF000000 | Integer.parseInt(value.substring(1), 16);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        net.minecraft.ChatFormatting formatting = net.minecraft.ChatFormatting.getByName(value);
        return formatting != null && formatting.getColor() != null ? 0xFF000000 | formatting.getColor() : fallback;
    }

    private static Component journalTimingLine(QuestTrackerSyncPayload.Entry entry) {
        long now = currentGameTime();
        if (entry.journal().expiresAtGameTime() > 0L && "active".equals(normalized(entry.state()))) {
            long remaining = Math.max(0L, entry.journal().expiresAtGameTime() - now);
            return Component.translatable(GUI_KEY_PREFIX + "expires", formatDuration(remaining));
        }
        if (entry.journal().completedGameTime() >= 0L) {
            return Component.translatable(GUI_KEY_PREFIX + "completed_ago", formatDuration(Math.max(0L, now - entry.journal().completedGameTime())));
        }
        return null;
    }

    private static Component journalWaypointLine(QuestTrackerSyncPayload.Entry entry) {
        QuestTrackerSyncPayload.Waypoint waypoint = entry.journal().waypoint();
        if (!waypoint.present()) {
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        String distance = "";
        if (minecraft.player != null && minecraft.level != null
                && minecraft.level.dimension().location().toString().equals(waypoint.dimension())) {
            int dx = waypoint.x() - minecraft.player.blockPosition().getX();
            int dz = waypoint.z() - minecraft.player.blockPosition().getZ();
            distance = " • " + Math.round(Math.sqrt((double) dx * dx + (double) dz * dz)) + " blocks";
        }
        return Component.translatable(GUI_KEY_PREFIX + "waypoint", waypoint.x(), waypoint.y(), waypoint.z(), waypoint.dimension(), distance);
    }

    private static long currentGameTime() {
        return Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime();
    }

    private static String formatDuration(long ticks) {
        long seconds = Math.max(0L, ticks / 20L);
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remainder = seconds % 60L;
        if (days > 0L) return days + "d " + hours + "h";
        if (hours > 0L) return hours + "h " + minutes + "m";
        if (minutes > 0L) return minutes + "m " + remainder + "s";
        return remainder + "s";
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
        AVAILABLE(
                0,
                GUI_KEY_PREFIX + "tab.available",
                GUI_KEY_PREFIX + "tab.available.tooltip",
                GUI_KEY_PREFIX + "empty.available",
                VillagerRetaliationClientAssets.QUEST_JOURNAL_BOOKMARK_RED_TEXTURE,
                VillagerRetaliationClientAssets.QUEST_JOURNAL_BOOKMARK_ICON_AVAILABLE_TEXTURE),
        ACTIVE(
                1,
                GUI_KEY_PREFIX + "tab.active",
                GUI_KEY_PREFIX + "tab.active.tooltip",
                GUI_KEY_PREFIX + "empty.active",
                VillagerRetaliationClientAssets.QUEST_JOURNAL_BOOKMARK_PURPLE_TEXTURE,
                VillagerRetaliationClientAssets.QUEST_JOURNAL_BOOKMARK_ICON_ACTIVE_TEXTURE),
        COMPLETED(
                2,
                GUI_KEY_PREFIX + "tab.completed",
                GUI_KEY_PREFIX + "tab.completed.tooltip",
                GUI_KEY_PREFIX + "empty.completed",
                VillagerRetaliationClientAssets.QUEST_JOURNAL_BOOKMARK_TEAL_TEXTURE,
                VillagerRetaliationClientAssets.QUEST_JOURNAL_BOOKMARK_ICON_COMPLETED_TEXTURE);

        private final int index;
        private final String titleKey;
        private final String tooltipKey;
        private final String emptyMessageKey;
        private final ResourceLocation texture;
        private final ResourceLocation iconTexture;

        QuestJournalTab(
                int index,
                String titleKey,
                String tooltipKey,
                String emptyMessageKey,
                ResourceLocation texture,
                ResourceLocation iconTexture) {
            this.index = index;
            this.titleKey = titleKey;
            this.tooltipKey = tooltipKey;
            this.emptyMessageKey = emptyMessageKey;
            this.texture = texture;
            this.iconTexture = iconTexture;
        }

        int index() {
            return this.index;
        }

        Component title() {
            return Component.translatable(this.titleKey);
        }

        Component tooltip() {
            return Component.translatable(this.tooltipKey).withStyle(this.tooltipStyle());
        }

        private Style tooltipStyle() {
            return switch (this) {
                case AVAILABLE -> QUEST_COUNT_NEARBY_STYLE;
                case ACTIVE -> QUEST_COUNT_ACTIVE_STYLE;
                case COMPLETED -> QUEST_COUNT_COMPLETED_STYLE;
            };
        }

        Component emptyMessage() {
            return Component.translatable(this.emptyMessageKey);
        }

        ResourceLocation texture() {
            return this.texture;
        }

        ResourceLocation iconTexture() {
            return this.iconTexture;
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

    private record QuestDetailLine(
            FormattedCharSequence text,
            int color,
            int top,
            int height,
            ResourceLocation icon,
            boolean centered,
            boolean divider,
            boolean titleIcon,
            String targetQuestId,
            String itemId) {
        private QuestDetailLine(
                FormattedCharSequence text,
                int color,
                int top,
                int height,
                ResourceLocation icon,
                boolean centered,
                boolean divider,
                boolean titleIcon) {
            this(text, color, top, height, icon, centered, divider, titleIcon, "", "");
        }

        private QuestDetailLine(
                FormattedCharSequence text,
                int color,
                int top,
                int height,
                ResourceLocation icon,
                boolean centered,
                boolean divider,
                boolean titleIcon,
                String targetQuestId) {
            this(text, color, top, height, icon, centered, divider, titleIcon, targetQuestId, "");
        }

        private boolean link() {
            return this.targetQuestId != null && !this.targetQuestId.isBlank();
        }
    }

    private record QuestCountSummary(int active, int accepted, int nearby, int completed) {
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
