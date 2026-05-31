package com.jvn.villagerretaliation.client.interaction;

import com.jvn.toucanlib.client.ToucanScrollbarThumb;
import com.jvn.toucanlib.client.ToucanScrollState;
import com.jvn.toucanlib.client.ToucanScrollbars;
import com.jvn.villagerretaliation.client.quest.VillagerQuestKeyMappings;
import com.jvn.villagerretaliation.client.quest.VillagerQuestUi;
import com.jvn.villagerretaliation.client.quest.VillagerQuestTrackerOverlay;
import com.jvn.villagerretaliation.network.QuestTrackerSyncPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

public final class VillagerQuestJournalScreen extends Screen {
    private static final float OPTION_SCROLL_LERP = 0.32F;
    private static final float OPTION_SCROLL_STEP = 12.0F;
    private static final float DETAIL_SCROLL_STEP = 16.0F;
    private static final float OPTION_HOVER_SCALE = 0.055F;
    private static final float OPTION_SELECTED_SCALE = 0.02F;

    private final VillagerInteractionScreenState state = new VillagerInteractionScreenState();
    private final OptionListContext optionListContext = new OptionListContext();
    private boolean draggingScrollbar;
    private boolean draggingDetailsScrollbar;
    private float scrollbarDragOffset;
    private float detailsScrollbarDragOffset;
    private int detailsSelectedOption = Integer.MIN_VALUE;
    private long detailsAnimationStartMillis = -1L;
    private boolean closingWithAnimation;

    public VillagerQuestJournalScreen() {
        super(Component.literal("Active Quests"));
    }

    @Override
    protected void init() {
        this.state.resetOptions(!entries().isEmpty());
        this.detailsAnimationStartMillis = Util.getMillis();
        VillagerInteractionExperimentalChrome.resetAnimation();
    }

    @Override
    public void tick() {
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

        renderExperimentalBackdrop(graphics, mouseX, mouseY);
        VillagerInteractionOptionList.render(this.optionListContext, graphics, mouseX, mouseY);
        renderSelectedUnderline(graphics, mouseX, mouseY);
        renderQuestDetails(graphics);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        int hovered = VillagerInteractionOptionList.optionAt(this.optionListContext, mouseX, mouseY);
        if (hovered >= 0) {
            if (hovered != this.state.selectedOption()) {
                setSelectedOption(hovered);
                ensureSelectedVisible();
                return true;
            }
            VillagerQuestTrackerOverlay.toggleTracking(entries().get(hovered));
            ensureSelectedVisible();
            return true;
        }

        ToucanScrollbarThumb scrollbarThumb = scrollbarThumb();
        if (scrollbarThumb != null && scrollbarThumb.contains(mouseX, mouseY)) {
            this.draggingScrollbar = true;
            this.scrollbarDragOffset = ToucanScrollbars.dragOffset(mouseY, scrollbarThumb);
            return true;
        }
        ToucanScrollbarThumb detailsScrollbarThumb = detailsScrollbarThumb();
        if (detailsScrollbarThumb != null && detailsScrollbarThumb.contains(mouseX, mouseY)) {
            this.draggingDetailsScrollbar = true;
            this.detailsScrollbarDragOffset = ToucanScrollbars.dragOffset(mouseY, detailsScrollbarThumb);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.draggingScrollbar) {
            ToucanScrollbarThumb scrollbarThumb = scrollbarThumb();
            if (scrollbarThumb == null) {
                this.draggingScrollbar = false;
                return false;
            }
            setTargetOptionScroll(ToucanScrollbars.scrollFromThumbDrag(mouseY, this.scrollbarDragOffset, scrollbarThumb, maxOptionScroll()));
            this.state.jumpOptionScrollToTarget();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.draggingDetailsScrollbar) {
            ToucanScrollbarThumb detailsScrollbarThumb = detailsScrollbarThumb();
            if (detailsScrollbarThumb == null) {
                this.draggingDetailsScrollbar = false;
                return false;
            }
            setTargetDetailsScroll(ToucanScrollbars.scrollFromThumbDrag(
                    mouseY,
                    this.detailsScrollbarDragOffset,
                    detailsScrollbarThumb,
                    maxDetailsScroll()));
            this.state.jumpDetailsScrollToTarget();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.draggingScrollbar) {
            this.draggingScrollbar = false;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.draggingDetailsScrollbar) {
            this.draggingDetailsScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxDetailsScroll() > 0.0F && isPointInsideDetailsScrollArea(mouseX, mouseY)) {
            setTargetDetailsScroll(this.state.targetDetailsScroll() - (float) scrollY * DETAIL_SCROLL_STEP);
            return true;
        }
        if (maxOptionScroll() <= 0.0F || !isPointInsideOptionScrollArea(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        setTargetOptionScroll(this.state.targetOptionScroll() - (float) scrollY * OPTION_SCROLL_STEP);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        closeJournal();
    }

    private void renderExperimentalBackdrop(GuiGraphics graphics, int mouseX, int mouseY) {
        renderExperimentalSkillsBackdrop(graphics, mouseX, mouseY);
    }

    private void renderExperimentalSkillsBackdrop(GuiGraphics graphics, int mouseX, int mouseY) {
        VillagerInteractionScreenShaderRenderer.renderExperimentalSkillsPanel(
                graphics,
                skillsBackdropLeft(),
                skillsBackdropTop(),
                skillsBackdropRight(),
                skillsBackdropBottom(),
                VillagerInteractionExperimentalChrome.chromeAlpha(),
                experimentalTicks(),
                detailsElapsedMillis(),
                -1.0F,
                VillagerInteractionExperimentalChrome.backdropElapsedMillis(),
                VillagerInteractionExperimentalChrome.backdropExitElapsedMillis(),
                this.width,
                this.height,
                mouseX,
                mouseY,
                false);
    }

    private void renderSelectedUnderline(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.state.selectedOption() < 0 || this.state.selectedOption() >= entries().size()) {
            return;
        }

        int rowHeight = VillagerInteractionOptionList.optionHeight(this.optionListContext, this.state.selectedOption());
        float rowTop = optionsTop() + VillagerInteractionOptionList.optionOffset(this.optionListContext, this.state.selectedOption()) - this.state.optionScroll();
        int viewportTop = optionsTop();
        int viewportBottom = viewportTop + optionViewportHeight();
        if (rowTop + rowHeight < viewportTop || rowTop > viewportBottom) {
            return;
        }

        float alpha = edgeFadeAlpha(rowTop, viewportTop, viewportBottom);
        int hovered = VillagerInteractionOptionList.optionAt(this.optionListContext, mouseX, mouseY);
        float hoverMix = hovered == this.state.selectedOption() ? this.optionListContext.hoverIntensity(mouseX, mouseY, optionsLeft(), rowTop) : 0.0F;
        float textScale = VillagerInteractionExperimentalLayout.scaleFactor();
        float shiftX = hoverMix > 0.0F ? this.optionListContext.hoverShift(mouseX, optionsLeft(), optionWidth(), 3.2F * textScale) * hoverMix : 0.0F;
        float shiftY = hoverMix > 0.0F ? this.optionListContext.hoverShift(mouseY, rowTop, rowHeight, 1.6F * textScale) * hoverMix : 0.0F;
        int left = Mth.floor(optionsLeft() - experimentalUnitAtLeast(12, 8) + shiftX);
        int top = Mth.floor(rowTop + rowHeight - experimentalUnitAtLeast(1, 1) + shiftY) + experimentalUnitAtLeast(2, 1);
        int right = Mth.floor(Math.min(optionsScrollbarLeft() - experimentalUnitAtLeast(8, 5), optionsLeft() + optionWidth() - experimentalUnitAtLeast(8, 5)) + shiftX);
        int bottom = top + experimentalUnitAtLeast(2, 1);
        if (right <= left) {
            return;
        }
        VillagerQuestUi.renderAccentBar(graphics, left, top, right, bottom, alpha, experimentalTicks(), false);
    }

    private void renderQuestDetails(GuiGraphics graphics) {
        QuestTrackerSyncPayload.Entry selected = selectedEntry();
        if (selected == null) {
            return;
        }

        int left = skillsPanelLeft();
        int top = skillsPanelTop() + skillsContainerPaddingY();
        int right = Math.min(this.width - experimentalUnitAtLeast(10, 6), left + skillsPanelWidth());
        int bottom = Math.min(this.height - experimentalUnitAtLeast(10, 6), skillsPanelTop() + skillsContainerHeight() - skillsContainerPaddingY());
        if (right <= left || bottom <= top) {
            return;
        }

        float alpha = VillagerInteractionExperimentalChrome.textFadeInAlpha();
        if (!VillagerInteractionExperimentalChrome.shouldDrawText(alpha)) {
            return;
        }
        float scale = VillagerInteractionExperimentalLayout.scaleFactor();
        int contentRight = detailsContentRight(right);
        int wrapWidth = VillagerInteractionUiUtil.scaledWrapWidth(contentRight - left, scale);
        int lineStep = VillagerInteractionUiUtil.scaledLineStep(this.font, scale);
        int progressReservedHeight = selected.showProgress() ? experimentalUnitAtLeast(16, 8) : 0;
        int textBottom = Math.max(top + lineStep, bottom - progressReservedHeight);
        int viewportHeight = Math.max(1, textBottom - top);
        List<QuestDetailLine> detailLines = buildQuestDetailLines(selected, wrapWidth, lineStep);
        int contentHeight = detailContentHeight(detailLines, lineStep);
        float maxScroll = ToucanScrollState.maxScroll(contentHeight, viewportHeight);
        setTargetDetailsScroll(this.state.targetDetailsScroll());
        float scroll = Mth.clamp(this.state.detailsScroll(), 0.0F, maxScroll);

        graphics.enableScissor(left - skillsContainerPaddingX(), top, right + experimentalUnitAtLeast(4, 2), textBottom);
        for (QuestDetailLine line : detailLines) {
            float lineTop = top + line.top() - scroll;
            float lineBottom = lineTop + lineStep;
            if (lineBottom < top || lineTop > textBottom) {
                continue;
            }
            float edgeAlpha = VillagerInteractionUiUtil.edgeFadeAlpha(
                    scroll,
                    maxScroll,
                    lineTop,
                    lineBottom,
                    top,
                    textBottom,
                    experimentalUnitAtLeast(18, 10));
            if (!VillagerInteractionExperimentalChrome.shouldDrawText(edgeAlpha * alpha)) {
                continue;
            }
            VillagerInteractionUiUtil.drawScaledString(
                    graphics,
                    this.font,
                    line.text(),
                    left,
                    Mth.floor(lineTop),
                    VillagerInteractionUiUtil.withAlpha(line.color(), edgeAlpha * alpha),
                    scale);
        }
        graphics.disableScissor();

        renderDetailsScrollbar(graphics, top, viewportHeight, contentHeight, maxScroll, alpha);
        if (selected.showProgress()) {
            int barHeight = experimentalUnitAtLeast(2, 1);
            int barTop = bottom - barHeight - experimentalUnitAtLeast(2, 1);
            VillagerQuestUi.renderProgressBar(graphics, left, barTop, contentRight, barHeight, selected.progress(), alpha, experimentalTicks(), true, false);
        }
    }

    private List<QuestDetailLine> buildQuestDetailLines(QuestTrackerSyncPayload.Entry selected, int wrapWidth, int lineStep) {
        List<QuestDetailLine> lines = new ArrayList<>();
        int y = 0;
        y = addWrappedDetailLines(lines, selected.title(), wrapWidth, VillagerQuestUi.TITLE_COLOR, y, lineStep, experimentalUnitAtLeast(8, 4));
        y = addWrappedDetailLines(lines, selected.objective(), wrapWidth, VillagerQuestUi.TEXT_COLOR, y, lineStep, experimentalUnitAtLeast(6, 3));
        y = addWrappedDetailLines(lines, statusLine(selected), wrapWidth, VillagerQuestUi.MUTED_TEXT_COLOR, y, lineStep, experimentalUnitAtLeast(2, 1));
        if (!selected.issuer().isBlank()) {
            y = addWrappedDetailLines(lines, "Issued by: " + selected.issuer(), wrapWidth, VillagerQuestUi.MUTED_TEXT_COLOR, y, lineStep, experimentalUnitAtLeast(2, 1));
        }
        if (!selected.issuerLocation().isBlank()) {
            y = addWrappedDetailLines(lines, selected.issuerLocation(), wrapWidth, VillagerQuestUi.MUTED_TEXT_COLOR, y, lineStep, experimentalUnitAtLeast(2, 1));
        }
        if (!selected.questItems().isEmpty()) {
            y = addWrappedDetailLines(lines, questItemsLine(selected), wrapWidth, VillagerQuestUi.MUTED_TEXT_COLOR, y, lineStep, experimentalUnitAtLeast(2, 1));
        }
        if (!selected.metadata().isBlank()) {
            y += experimentalUnitAtLeast(6, 3);
            addWrappedDetailLines(lines, selected.metadata(), wrapWidth, VillagerQuestUi.MUTED_TEXT_COLOR, y, lineStep, 0);
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
        VillagerQuestTrackerOverlay.dismissJournalFlash();
        VillagerInteractionExperimentalChrome.startExitAnimation(
                buildExitTextElements(),
                buildExitFadeTextElements(),
                buildExitFadeRectElements(),
                new VillagerInteractionExperimentalChrome.ExitSkillsPanel(
                        skillsBackdropLeft(),
                        skillsBackdropTop(),
                        skillsBackdropRight(),
                        skillsBackdropBottom(),
                        detailsElapsedMillis()));
        Minecraft.getInstance().setScreen(null);
    }

    private List<VillagerInteractionExperimentalChrome.ExitTextElement> buildExitTextElements() {
        List<VillagerInteractionExperimentalChrome.ExitTextElement> textElements = new ArrayList<>();
        float textScale = VillagerInteractionExperimentalLayout.scaleFactor();
        int listLeft = optionsLeft();
        int textLeft = listLeft + optionTextInset();
        int top = optionsTop();
        int viewportTop = top;
        int viewportBottom = top + optionViewportHeight();
        for (int index = 0; index < entries().size(); index++) {
            int rowHeight = VillagerInteractionOptionList.optionHeight(this.optionListContext, index);
            float y = top + VillagerInteractionOptionList.optionOffset(this.optionListContext, index) - this.state.optionScroll();
            if (y + rowHeight < viewportTop || y > viewportBottom) {
                continue;
            }

            boolean selected = index == this.state.selectedOption();
            int color = selected ? 0xFFF8F8F4 : 0xCFC7C8C5;
            float scale = (1.48F + (selected ? OPTION_SELECTED_SCALE : 0.0F)) * textScale;
            float delay = 120.0F + index * 28.0F;
            int textY = Mth.floor(y + optionHeight() * (5.0F / 18.0F));
            if (selected) {
                int arrowX = textLeft - optionTextInset() - 7;
                textElements.add(new VillagerInteractionExperimentalChrome.ExitTextElement(
                        ">",
                        arrowX,
                        textY,
                        0xFFFFFFFF,
                        scale,
                        delay,
                        this.width - arrowX + 72.0F,
                        0.0F,
                        false));
            }
            List<String> labelLines = VillagerInteractionOptionList.wrappedOptionLabelLines(this.optionListContext, entries().get(index).title(), scale);
            for (int lineIndex = 0; lineIndex < labelLines.size(); lineIndex++) {
                int lineY = textY + lineIndex * optionHeight();
                textElements.add(new VillagerInteractionExperimentalChrome.ExitTextElement(
                        labelLines.get(lineIndex),
                        textLeft,
                        lineY,
                        color,
                        scale,
                        delay + 24.0F,
                        this.width - textLeft + 88.0F,
                        0.0F,
                        false));
            }
        }

        return textElements;
    }

    private List<VillagerInteractionExperimentalChrome.ExitFadeTextElement> buildExitFadeTextElements() {
        return List.of();
    }

    private List<VillagerInteractionExperimentalChrome.ExitFadeRectElement> buildExitFadeRectElements() {
        List<VillagerInteractionExperimentalChrome.ExitFadeRectElement> rectElements = new ArrayList<>();
        addScrollbarExitFadeRects(rectElements, scrollbarThumb(), this.state.optionScroll(), maxOptionScroll());
        addScrollbarExitFadeRects(rectElements, detailsScrollbarThumb(), this.state.detailsScroll(), maxDetailsScroll());
        return rectElements;
    }

    private void addScrollbarExitFadeRects(
            List<VillagerInteractionExperimentalChrome.ExitFadeRectElement> rectElements,
            ToucanScrollbarThumb scrollbarThumb,
            float currentScroll,
            float maxScroll) {
        if (scrollbarThumb == null) {
            return;
        }

        boolean canScrollUp = currentScroll > 0.75F;
        boolean canScrollDown = currentScroll < maxScroll - 0.75F;
        int fadeLength = Math.min(8, Math.max(3, scrollbarThumb.height() / 3));
        for (int y = scrollbarThumb.top(); y < scrollbarThumb.bottom(); y++) {
            float alphaFactor = 1.0F;
            if (canScrollUp && y < scrollbarThumb.top() + fadeLength) {
                alphaFactor = Math.min(alphaFactor, (y - scrollbarThumb.top() + 1.0F) / fadeLength);
            }
            if (canScrollDown && y >= scrollbarThumb.bottom() - fadeLength) {
                alphaFactor = Math.min(alphaFactor, (scrollbarThumb.bottom() - y) / (float) fadeLength);
            }
            rectElements.add(new VillagerInteractionExperimentalChrome.ExitFadeRectElement(
                    scrollbarThumb.left(),
                    y,
                    scrollbarThumb.right(),
                    y + 1,
                    0xBFFFFFFF,
                    alphaFactor));
        }
    }

    private void ensureSelectedVisible() {
        if (this.state.selectedOption() < 0 || this.state.selectedOption() >= entries().size()) {
            return;
        }

        float optionTop = VillagerInteractionOptionList.optionOffset(this.optionListContext, this.state.selectedOption());
        float optionBottom = optionTop + VillagerInteractionOptionList.optionHeight(this.optionListContext, this.state.selectedOption());
        float viewportTop = this.state.targetOptionScroll();
        float viewportBottom = viewportTop + optionViewportHeight();
        int padding = experimentalUnitAtLeast(6, 3);
        if (optionTop < viewportTop + padding) {
            setTargetOptionScroll(optionTop - padding);
        } else if (optionBottom > viewportBottom - padding) {
            setTargetOptionScroll(optionBottom - optionViewportHeight() + padding);
        }
    }

    private void moveSelection(int direction) {
        if (entries().isEmpty()) {
            return;
        }
        this.state.moveSelectedOption(direction, entries().size());
        ensureSelectedVisible();
    }

    private boolean isPointInsideOptionScrollArea(double mouseX, double mouseY) {
        int left = optionsLeft() - experimentalUnitAtLeast(18, 10);
        int right = optionsLeft() + optionWidth() + experimentalUnitAtLeast(4, 2);
        int top = optionsTop();
        int bottom = top + optionViewportHeight();
        int verticalPadding = experimentalUnitAtLeast(4, 2);
        return mouseX >= left && mouseX <= right && mouseY >= top - verticalPadding && mouseY <= bottom + verticalPadding;
    }

    private boolean isPointInsideDetailsScrollArea(double mouseX, double mouseY) {
        int left = skillsPanelLeft() - experimentalUnitAtLeast(6, 3);
        int right = Math.min(this.width - experimentalUnitAtLeast(10, 6), skillsPanelLeft() + skillsPanelWidth()) + experimentalUnitAtLeast(6, 3);
        int top = skillsPanelTop() + skillsContainerPaddingY();
        int bottom = Math.min(this.height - experimentalUnitAtLeast(10, 6), skillsPanelTop() + skillsContainerHeight() - skillsContainerPaddingY());
        return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
    }

    private void clampSelectedOption() {
        if (entries().isEmpty()) {
            this.state.resetOptions(false);
            return;
        }
        setSelectedOption(Mth.clamp(this.state.selectedOption(), 0, entries().size() - 1));
        setTargetOptionScroll(this.state.targetOptionScroll());
        setTargetDetailsScroll(this.state.targetDetailsScroll());
    }

    private void setSelectedOption(int selectedOption) {
        if (this.state.selectedOption() != selectedOption) {
            this.detailsSelectedOption = selectedOption;
            this.state.resetDetailsScroll();
        }
        this.state.setSelectedOption(selectedOption);
    }

    private void resetDetailsScrollAfterSelectionChange() {
        if (this.detailsSelectedOption == this.state.selectedOption()) {
            return;
        }
        this.detailsSelectedOption = this.state.selectedOption();
        this.state.resetDetailsScroll();
    }

    private QuestTrackerSyncPayload.Entry selectedEntry() {
        if (this.state.selectedOption() < 0 || this.state.selectedOption() >= entries().size()) {
            return null;
        }
        return entries().get(this.state.selectedOption());
    }

    private void setTargetOptionScroll(float scroll) {
        this.state.setTargetOptionScroll(scroll, maxOptionScroll());
    }

    private void setTargetDetailsScroll(float scroll) {
        this.state.setTargetDetailsScroll(scroll, maxDetailsScroll());
    }

    private float edgeFadeAlpha(float optionY, int viewportTop, int viewportBottom) {
        return VillagerInteractionUiUtil.edgeFadeAlpha(
                this.state.optionScroll(),
                maxOptionScroll(),
                optionY,
                optionY + optionHeight(),
                viewportTop,
                viewportBottom,
                26.0F
        );
    }

    private void renderScrollbar(GuiGraphics graphics) {
        ToucanScrollbars.renderFadedThumb(
                graphics,
                scrollbarThumb(),
                this.state.optionScroll(),
                maxOptionScroll(),
                0xBFFFFFFF,
                VillagerInteractionExperimentalChrome.chromeAlpha());
    }

    private void renderDetailsScrollbar(
            GuiGraphics graphics,
            int viewportTop,
            int viewportHeight,
            int contentHeight,
            float maxScroll,
            float alpha) {
        ToucanScrollbars.renderFadedThumb(
                graphics,
                detailsScrollbarThumb(viewportTop, viewportHeight, contentHeight, maxScroll),
                this.state.detailsScroll(),
                maxScroll,
                0xAFFFFFFF,
                alpha);
    }

    private ToucanScrollbarThumb scrollbarThumb() {
        return VillagerInteractionUiUtil.buildScrollbarThumb(
                optionsTop(),
                optionViewportHeight(),
                optionsScrollbarLeft(),
                optionScrollbarWidth(),
                optionScrollbarHitWidth(),
                optionHeight(),
                this.state.optionScroll(),
                maxOptionScroll(),
                optionContentHeight()
        );
    }

    private ToucanScrollbarThumb detailsScrollbarThumb() {
        QuestTrackerSyncPayload.Entry selected = selectedEntry();
        if (selected == null) {
            return null;
        }
        int top = skillsPanelTop() + skillsContainerPaddingY();
        int bottom = Math.min(this.height - experimentalUnitAtLeast(10, 6), skillsPanelTop() + skillsContainerHeight() - skillsContainerPaddingY());
        if (bottom <= top) {
            return null;
        }
        int viewportHeight = Math.max(1, bottom - top - (selected.showProgress() ? experimentalUnitAtLeast(16, 8) : 0));
        float maxScroll = maxDetailsScroll(selected, viewportHeight);
        int contentHeight = detailsContentHeight(selected);
        return detailsScrollbarThumb(top, viewportHeight, contentHeight, maxScroll);
    }

    private ToucanScrollbarThumb detailsScrollbarThumb(int viewportTop, int viewportHeight, int contentHeight, float maxScroll) {
        if (maxScroll <= 0.0F) {
            return null;
        }
        return VillagerInteractionUiUtil.buildScrollbarThumb(
                viewportTop,
                viewportHeight,
                detailsScrollbarLeft(),
                optionScrollbarWidth(),
                optionScrollbarHitWidth(),
                experimentalUnitAtLeast(18, 10),
                this.state.detailsScroll(),
                maxScroll,
                contentHeight);
    }

    private float maxOptionScroll() {
        return ToucanScrollState.maxScroll(optionContentHeight(), optionViewportHeight());
    }

    private float maxDetailsScroll() {
        QuestTrackerSyncPayload.Entry selected = selectedEntry();
        if (selected == null) {
            return 0.0F;
        }
        int top = skillsPanelTop() + skillsContainerPaddingY();
        int bottom = Math.min(this.height - experimentalUnitAtLeast(10, 6), skillsPanelTop() + skillsContainerHeight() - skillsContainerPaddingY());
        int viewportHeight = Math.max(1, bottom - top - (selected.showProgress() ? experimentalUnitAtLeast(16, 8) : 0));
        return maxDetailsScroll(selected, viewportHeight);
    }

    private float maxDetailsScroll(QuestTrackerSyncPayload.Entry selected, int viewportHeight) {
        return ToucanScrollState.maxScroll(detailsContentHeight(selected), viewportHeight);
    }

    private float optionContentHeight() {
        return VillagerInteractionOptionList.optionContentHeight(this.optionListContext);
    }

    private int detailsContentHeight(QuestTrackerSyncPayload.Entry selected) {
        float scale = VillagerInteractionExperimentalLayout.scaleFactor();
        int left = skillsPanelLeft();
        int right = Math.min(this.width - experimentalUnitAtLeast(10, 6), left + skillsPanelWidth());
        int wrapWidth = VillagerInteractionUiUtil.scaledWrapWidth(detailsContentRight(right) - left, scale);
        int lineStep = VillagerInteractionUiUtil.scaledLineStep(this.font, scale);
        return detailContentHeight(buildQuestDetailLines(selected, wrapWidth, lineStep), lineStep);
    }

    private int optionWidth() {
        return VillagerInteractionLayoutMetrics.optionWidth();
    }

    private int optionHeight() {
        return VillagerInteractionLayoutMetrics.optionHeight();
    }

    private int optionTextInset() {
        return VillagerInteractionLayoutMetrics.optionTextInset();
    }

    private int optionViewportHeight() {
        return VillagerInteractionLayoutMetrics.fullOptionViewportHeight();
    }

    private int optionStride() {
        return VillagerInteractionLayoutMetrics.optionStride();
    }

    private int optionsLeft() {
        return VillagerInteractionExperimentalLayout.optionsLeft(this.width, optionWidth());
    }

    private int optionsTop() {
        return VillagerInteractionExperimentalLayout.optionsTop(this.height, optionViewportHeight());
    }

    private int optionsScrollbarLeft() {
        return VillagerInteractionExperimentalLayout.scrollbarLeft(
                this.width,
                optionsLeft(),
                optionWidth(),
                optionScrollbarOffset(),
                optionScrollbarWidth());
    }

    private int scrollbarRight() {
        return optionsScrollbarLeft() + optionScrollbarWidth();
    }

    private int detailsScrollbarLeft() {
        int left = skillsPanelLeft();
        int right = Math.min(this.width - experimentalUnitAtLeast(10, 6), left + skillsPanelWidth());
        return Math.max(left, right - optionScrollbarWidth());
    }

    private int detailsContentRight(int panelRight) {
        return Math.max(skillsPanelLeft() + experimentalUnitAtLeast(48, 32), panelRight - experimentalUnitAtLeast(12, 7));
    }

    private int skillsPanelTop() {
        return VillagerInteractionLayoutMetrics.skillsPanelTop(this.height, skillsContainerHeight());
    }

    private int skillsPanelLeft() {
        int panelWidth = skillsPanelWidth();
        return VillagerInteractionLayoutMetrics.skillsPanelLeft(this.width, panelWidth, scrollbarRight() - panelWidth);
    }

    private int skillsPanelWidth() {
        return optionWidth();
    }

    private int skillsContainerHeight() {
        return VillagerInteractionLayoutMetrics.skillsContainerHeight(skillsPanelHeight());
    }

    private int skillsPanelHeight() {
        return VillagerInteractionLayoutMetrics.skillsPanelHeight(this.font);
    }

    private int skillsContainerPaddingX() {
        return VillagerInteractionLayoutMetrics.skillsContainerPaddingX();
    }

    private int skillsContainerPaddingY() {
        return VillagerInteractionLayoutMetrics.skillsContainerPaddingY();
    }

    private int skillsBackdropLeft() {
        return Math.max(0, skillsPanelLeft() - skillsContainerPaddingX() - experimentalUnit(118));
    }

    private int skillsBackdropTop() {
        return Math.max(0, skillsPanelTop() - experimentalUnit(26));
    }

    private int skillsBackdropRight() {
        return this.width;
    }

    private int skillsBackdropBottom() {
        return this.height;
    }

    private int optionScrollbarOffset() {
        return VillagerInteractionLayoutMetrics.optionScrollbarOffset();
    }

    private int optionScrollbarWidth() {
        return VillagerInteractionLayoutMetrics.optionScrollbarWidth();
    }

    private int optionScrollbarHitWidth() {
        return VillagerInteractionLayoutMetrics.optionScrollbarHitWidth();
    }

    private int experimentalUnit(int value) {
        return VillagerInteractionExperimentalLayout.unit(value);
    }

    private int experimentalUnitAtLeast(int value, int minimum) {
        return VillagerInteractionExperimentalLayout.unitAtLeast(value, minimum);
    }

    private int experimentalTicks() {
        return (int) ((Util.getMillis() % 1_000_000L) / 50L);
    }

    private float detailsElapsedMillis() {
        long now = Util.getMillis();
        if (this.detailsAnimationStartMillis < 0L) {
            this.detailsAnimationStartMillis = now;
        }
        return now - this.detailsAnimationStartMillis;
    }

    private static List<QuestTrackerSyncPayload.Entry> entries() {
        return VillagerQuestTrackerOverlay.entries();
    }

    private record QuestDetailLine(FormattedCharSequence text, int color, int top) {
    }

    private final class OptionListContext implements VillagerInteractionOptionList.Context {
        @Override
        public Font font() {
            return VillagerQuestJournalScreen.this.font;
        }

        @Override
        public int optionsLeft() {
            return VillagerQuestJournalScreen.this.optionsLeft();
        }

        @Override
        public int optionsTop() {
            return VillagerQuestJournalScreen.this.optionsTop();
        }

        @Override
        public int optionWidth() {
            return VillagerQuestJournalScreen.this.optionWidth();
        }

        @Override
        public int optionHeight() {
            return VillagerQuestJournalScreen.this.optionHeight();
        }

        @Override
        public int optionTextInset() {
            return VillagerQuestJournalScreen.this.optionTextInset();
        }

        @Override
        public int optionCount() {
            return entries().size();
        }

        @Override
        public String optionLabel(int index) {
            QuestTrackerSyncPayload.Entry entry = entries().get(index);
            if (!entry.trackable() && !entry.status().isBlank()) {
                return entry.title() + " [" + entry.status() + "]";
            }
            return entry.title();
        }

        @Override
        public int selectedOption() {
            return VillagerQuestJournalScreen.this.state.selectedOption();
        }

        @Override
        public float optionScroll() {
            return VillagerQuestJournalScreen.this.state.optionScroll();
        }

        @Override
        public int optionViewportHeight() {
            return VillagerQuestJournalScreen.this.optionViewportHeight();
        }

        @Override
        public int optionStride() {
            return VillagerQuestJournalScreen.this.optionStride();
        }

        @Override
        public float edgeFadeAlpha(float optionY, int viewportTop, int viewportBottom) {
            return VillagerQuestJournalScreen.this.edgeFadeAlpha(optionY, viewportTop, viewportBottom);
        }

        @Override
        public float hoverIntensity(double mouseX, double mouseY, int left, float top) {
            double normalizedX = Math.abs(((mouseX - left) / optionWidth()) * 2.0D - 1.0D);
            double normalizedY = Math.abs(((mouseY - top) / optionHeight()) * 2.0D - 1.0D);
            double distance = Math.sqrt(normalizedX * normalizedX + normalizedY * normalizedY);
            return (float) Mth.clamp(1.0D - distance / Math.sqrt(2.0D), 0.0D, 1.0D);
        }

        @Override
        public float hoverShift(double mouse, float start, float size, float strength) {
            return (float) ((((mouse - start) / size) * 2.0D) - 1.0D) * strength;
        }

        @Override
        public float optionHoverScale() {
            return OPTION_HOVER_SCALE;
        }

        @Override
        public float optionSelectedScale() {
            return OPTION_SELECTED_SCALE;
        }

        @Override
        public float experimentalTextScale() {
            return VillagerInteractionExperimentalLayout.scaleFactor();
        }

        @Override
        public int optionsScrollbarLeft() {
            return VillagerQuestJournalScreen.this.optionsScrollbarLeft();
        }

        @Override
        public void renderScrollbar(GuiGraphics graphics) {
            VillagerQuestJournalScreen.this.renderScrollbar(graphics);
        }
    }
}
