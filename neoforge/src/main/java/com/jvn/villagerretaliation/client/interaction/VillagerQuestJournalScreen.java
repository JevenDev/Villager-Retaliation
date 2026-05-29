package com.jvn.villagerretaliation.client.interaction;

import com.jvn.toucanlib.client.ToucanScrollbarThumb;
import com.jvn.toucanlib.client.ToucanScrollState;
import com.jvn.toucanlib.client.ToucanScrollbars;
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
    private static final float OPTION_HOVER_SCALE = 0.055F;
    private static final float OPTION_SELECTED_SCALE = 0.02F;
    private static final int QUEST_ACCENT_COLOR = 0xFFFFD166;
    private static final int TITLE_COLOR = 0xFFFFF0C8;
    private static final int TEXT_COLOR = 0xFFE9EEF5;
    private static final int MUTED_TEXT_COLOR = 0xFFB8C3D0;
    private static final int PANEL_FALLBACK_COLOR = 0xC0000000;
    private static final int SKILLS_CONTAINER_PADDING_X = 8;
    private static final int SKILLS_CONTAINER_PADDING_Y = 6;

    private final VillagerInteractionScreenState state = new VillagerInteractionScreenState();
    private final OptionListContext optionListContext = new OptionListContext();
    private boolean draggingScrollbar;
    private float scrollbarDragOffset;
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
        this.state.tickOptionScroll(OPTION_SCROLL_LERP);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (entries().isEmpty()) {
            Minecraft.getInstance().setScreen(null);
            return;
        }
        updateMouseSelection(mouseX, mouseY);
        clampSelectedOption();

        renderExperimentalBackdrop(graphics, mouseX, mouseY);
        VillagerInteractionOptionList.render(this.optionListContext, graphics, mouseX, mouseY);
        renderSelectedUnderline(graphics, mouseX, mouseY);
        renderQuestDetails(graphics);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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
            this.state.setSelectedOption(hovered);
            ensureSelectedVisible();
            return true;
        }

        ToucanScrollbarThumb scrollbarThumb = scrollbarThumb();
        if (scrollbarThumb != null && scrollbarThumb.contains(mouseX, mouseY)) {
            this.draggingScrollbar = true;
            this.scrollbarDragOffset = ToucanScrollbars.dragOffset(mouseY, scrollbarThumb);
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
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.draggingScrollbar) {
            this.draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
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
        if (!VillagerInteractionScreenShaderRenderer.renderExperimentalSkillsPanel(
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
                false)) {
            graphics.fill(skillsBackdropLeft(), skillsBackdropTop(), skillsBackdropRight(), skillsBackdropBottom(), PANEL_FALLBACK_COLOR);
        }
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
        int top = Mth.floor(rowTop + rowHeight - experimentalUnitAtLeast(1, 1) + shiftY) + 2;
        int right = Mth.floor(Math.min(optionsScrollbarLeft() - experimentalUnitAtLeast(8, 5), optionsLeft() + optionWidth() - experimentalUnitAtLeast(8, 5)) + shiftX);
        int bottom = top + experimentalUnitAtLeast(2, 1);
        if (right <= left) {
            return;
        }
        if (!VillagerInteractionScreenShaderRenderer.renderExperimentalSkillBar(
                graphics,
                left,
                top,
                right,
                bottom,
                QUEST_ACCENT_COLOR,
                1.0F,
                alpha,
                experimentalTicks(),
                false)) {
            graphics.fill(left, top, right, bottom, VillagerInteractionUiUtil.withAlpha(QUEST_ACCENT_COLOR, alpha));
        }
    }

    private void renderQuestDetails(GuiGraphics graphics) {
        QuestTrackerSyncPayload.Entry selected = selectedEntry();
        if (selected == null) {
            return;
        }

        int left = skillsPanelLeft();
        int top = skillsPanelTop() + SKILLS_CONTAINER_PADDING_Y;
        int right = Math.min(this.width - experimentalUnitAtLeast(10, 6), left + skillsPanelWidth());
        int bottom = Math.min(this.height - experimentalUnitAtLeast(10, 6), skillsPanelTop() + skillsContainerHeight() - SKILLS_CONTAINER_PADDING_Y);
        if (right <= left || bottom <= top) {
            return;
        }

        float alpha = VillagerInteractionExperimentalChrome.textFadeInAlpha();
        if (!VillagerInteractionExperimentalChrome.shouldDrawText(alpha)) {
            return;
        }
        float scale = 1.0F;
        int wrapWidth = VillagerInteractionUiUtil.scaledWrapWidth(right - left, scale);
        int progressReservedHeight = selected.showProgress() ? experimentalUnitAtLeast(16, 8) : 0;
        int textBottom = bottom - progressReservedHeight;
        graphics.enableScissor(left - SKILLS_CONTAINER_PADDING_X, top - SKILLS_CONTAINER_PADDING_Y, right + 4, bottom);
        VillagerInteractionUiUtil.drawScaledString(graphics, this.font, selected.title(), left, top, VillagerInteractionUiUtil.withAlpha(TITLE_COLOR, alpha), scale);
        int y = top + VillagerInteractionUiUtil.scaledLineStep(this.font, scale) + experimentalUnitAtLeast(8, 4);
        y = renderWrappedLine(graphics, selected.objective(), left, y, wrapWidth, VillagerInteractionUiUtil.withAlpha(TEXT_COLOR, alpha), scale, textBottom);
        if (!selected.metadata().isBlank()) {
            y += experimentalUnitAtLeast(8, 4);
            renderWrappedLine(graphics, selected.metadata(), left, y, wrapWidth, VillagerInteractionUiUtil.withAlpha(MUTED_TEXT_COLOR, alpha), scale, textBottom);
        }
        graphics.disableScissor();

        if (selected.showProgress()) {
            int barHeight = experimentalUnitAtLeast(2, 1);
            int barTop = Math.min(bottom - barHeight, y + experimentalUnitAtLeast(8, 4) + 2);
            int barRight = right;
            graphics.fill(left, barTop, barRight, barTop + barHeight, VillagerInteractionUiUtil.withAlpha(0x80373A42, alpha));
            VillagerInteractionScreenShaderRenderer.renderExperimentalSkillBar(
                    graphics,
                    left,
                    barTop,
                    left + Math.round((barRight - left) * selected.progress()),
                    barTop + barHeight,
                    QUEST_ACCENT_COLOR,
                    1.0F,
                    alpha,
                    experimentalTicks(),
                    false);
        }
    }

    private int renderWrappedLine(
            GuiGraphics graphics,
            String text,
            int left,
            int top,
            int wrapWidth,
            int color,
            float scale,
            int bottom) {
        int y = top;
        for (FormattedCharSequence line : this.font.split(Component.literal(text), wrapWidth)) {
            if (y + VillagerInteractionUiUtil.scaledLineStep(this.font, scale) > bottom) {
                break;
            }
            VillagerInteractionUiUtil.drawScaledString(graphics, this.font, line, left, y, color, scale);
            y += VillagerInteractionUiUtil.scaledLineStep(this.font, scale);
        }
        return y;
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
                textElements.add(new VillagerInteractionExperimentalChrome.ExitTextElement(
                        ">", textLeft - optionTextInset() - 7, textY, 0xFFFFFFFF, scale, delay, 0.0F, this.height - textY + 72.0F, false));
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
                        0.0F,
                        this.height - lineY + 88.0F,
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

    private void updateMouseSelection(int mouseX, int mouseY) {
        int hovered = VillagerInteractionOptionList.optionAt(this.optionListContext, mouseX, mouseY);
        if (hovered >= 0) {
            this.state.setSelectedOption(hovered);
        }
    }

    private boolean isPointInsideOptionScrollArea(double mouseX, double mouseY) {
        int left = optionsLeft() - experimentalUnitAtLeast(18, 10);
        int right = optionsLeft() + optionWidth() + experimentalUnitAtLeast(4, 2);
        int top = optionsTop();
        int bottom = top + optionViewportHeight();
        return mouseX >= left && mouseX <= right && mouseY >= top - 4 && mouseY <= bottom + 4;
    }

    private void clampSelectedOption() {
        if (entries().isEmpty()) {
            this.state.resetOptions(false);
            return;
        }
        this.state.setSelectedOption(Mth.clamp(this.state.selectedOption(), 0, entries().size() - 1));
        setTargetOptionScroll(this.state.targetOptionScroll());
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

    private float maxOptionScroll() {
        return ToucanScrollState.maxScroll(optionContentHeight(), optionViewportHeight());
    }

    private float optionContentHeight() {
        return VillagerInteractionOptionList.optionContentHeight(this.optionListContext);
    }

    private int optionWidth() {
        return VillagerInteractionLayoutMetrics.optionWidth(true);
    }

    private int optionHeight() {
        return VillagerInteractionLayoutMetrics.optionHeight(true);
    }

    private int optionTextInset() {
        return VillagerInteractionLayoutMetrics.optionTextInset(true);
    }

    private int optionViewportHeight() {
        return VillagerInteractionLayoutMetrics.fullOptionViewportHeight(true);
    }

    private int optionStride() {
        return VillagerInteractionLayoutMetrics.optionStride(true);
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

    private int skillsBackdropLeft() {
        return Math.max(0, skillsPanelLeft() - SKILLS_CONTAINER_PADDING_X - experimentalUnit(118));
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
        return VillagerInteractionLayoutMetrics.optionScrollbarOffset(true);
    }

    private int optionScrollbarWidth() {
        return VillagerInteractionLayoutMetrics.optionScrollbarWidth(true);
    }

    private int optionScrollbarHitWidth() {
        return VillagerInteractionLayoutMetrics.optionScrollbarHitWidth(true);
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
            return entries().get(index).title();
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
        public boolean experimentalStyle() {
            return true;
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
