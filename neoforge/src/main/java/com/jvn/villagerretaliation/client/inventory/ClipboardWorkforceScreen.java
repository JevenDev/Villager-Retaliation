package com.jvn.villagerretaliation.client.inventory;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceSnapshot;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceSnapshot.WarningSummary;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceSnapshot.WorkerRow;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.network.ClipboardPreviewTogglePayload;
import com.jvn.villagerretaliation.network.ClipboardWorkAreaActionPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class ClipboardWorkforceScreen extends Screen {
    private static final float CLIPBOARD_ANIMATION_DURATION_MILLIS = 280.0F;
    private static final float SCROLL_TAB_ANIMATION_DURATION_MILLIS = 180.0F;
    private static final int SCROLL_TAB_START_INSET = 15;
    private static final int TEXTURE_WIDTH = 146;
    private static final int TEXTURE_HEIGHT = 196;
    private static final int HOME_TAB_LEFT = 3;
    private static final int HOME_TAB_TOP = 2;
    private static final int HOME_TAB_WIDTH = 42;
    private static final int HOME_TAB_HEIGHT = 45;
    private static final int SCROLL_TAB_WIDTH = 37;
    private static final int SCROLL_TAB_HEIGHT = 30;
    private static final int SCROLL_DOWN_TAB_LEFT = (TEXTURE_WIDTH - SCROLL_TAB_WIDTH) / 2;
    private static final int SCROLL_DOWN_TAB_TOP = TEXTURE_HEIGHT - 22;
    private static final int SCROLL_UP_TAB_LEFT = TEXTURE_WIDTH - 30 - SCROLL_TAB_WIDTH;
    private static final int SCROLL_UP_TAB_TOP = 29 - SCROLL_TAB_HEIGHT;
    private static final int PAGE_SIDE_TAB_WIDTH = 42;
    private static final int PAGE_SIDE_TAB_HEIGHT = 34;
    private static final int PAGE_SIDE_TAB_TOP = TEXTURE_HEIGHT - 53;
    private static final int LEFT_PAGE_TAB_LEFT = 22 - PAGE_SIDE_TAB_WIDTH;
    private static final int RIGHT_PAGE_TAB_LEFT = TEXTURE_WIDTH - 22;
    private static final int WORKFORCE_TITLE_TOP = 34;
    private static final int DIVIDER_WIDTH = 119;
    private static final int DIVIDER_HEIGHT = 4;
    private static final int DIVIDER_LINE_OFFSET = 1;
    private static final int TEXT_PIXEL_HEIGHT = 7;
    private static final int GLOBAL_TEXT_GAP = 4;
    private static final int SUMMARY_LEFT = (TEXTURE_WIDTH - DIVIDER_WIDTH) / 2 + 2;
    private static final int SUMMARY_RIGHT = (TEXTURE_WIDTH - DIVIDER_WIDTH) / 2 + DIVIDER_WIDTH;
    private static final int SUMMARY_ROW_STEP = TEXT_PIXEL_HEIGHT + GLOBAL_TEXT_GAP;
    private static final int JOBS_ICON_WIDTH = 24;
    private static final int JOBS_ICON_HEIGHT = 12;
    private static final int JOBS_ICON_BASELINE_ROW = 9;
    private static final int JOBS_LEFT_ICON_RIGHTMOST_PIXEL = 22;
    private static final int JOBS_RIGHT_ICON_LEFTMOST_PIXEL = 2;
    private static final int JOBS_ICON_TEXT_GAP = 3;
    private static final int TEXT_CONTENT_TOP = 34;
    private static final int TEXT_CONTENT_BOTTOM = TEXTURE_HEIGHT - 15;
    private static final int TITLE_DIVIDER_TOP = WORKFORCE_TITLE_TOP
            + TEXT_PIXEL_HEIGHT
            + GLOBAL_TEXT_GAP
            - DIVIDER_LINE_OFFSET;
    private static final int SUMMARY_TOP = TITLE_DIVIDER_TOP
            + DIVIDER_LINE_OFFSET
            + 1
            + GLOBAL_TEXT_GAP;
    private static final int WARNINGS_TOP = SUMMARY_TOP + SUMMARY_ROW_STEP * 2;
    private static final int JOBS_DIVIDER_TOP = WARNINGS_TOP
            + TEXT_PIXEL_HEIGHT
            + GLOBAL_TEXT_GAP
            - DIVIDER_LINE_OFFSET;
    private static final int JOBS_TOP = JOBS_DIVIDER_TOP
            + DIVIDER_LINE_OFFSET
            + 1
            + GLOBAL_TEXT_GAP;
    private static final int JOB_LIST_DIVIDER_TOP = JOBS_TOP
            + TEXT_PIXEL_HEIGHT
            + GLOBAL_TEXT_GAP
            - DIVIDER_LINE_OFFSET;
    private static final int JOB_DESCRIPTION_TOP = JOB_LIST_DIVIDER_TOP
            + DIVIDER_LINE_OFFSET
            + 1
            + GLOBAL_TEXT_GAP;
    private static final int CONTAINER_TAB_RIGHT = 25;
    private static final int CONTAINER_TAB_1_WIDTH = 36;
    private static final int CONTAINER_TAB_1_HEIGHT = 46;
    private static final int CONTAINER_TAB_1_TOP = 31;
    private static final int CONTAINER_TAB_2_WIDTH = 33;
    private static final int CONTAINER_TAB_2_HEIGHT = 43;
    private static final int CONTAINER_TAB_2_TOP = 54;
    private static final int CONTAINER_TAB_3_WIDTH = 31;
    private static final int CONTAINER_TAB_3_HEIGHT = 32;
    private static final int CONTAINER_TAB_3_TOP = 89;
    private static final int TAB_WIDTH = 27;
    private static final int TAB_HEIGHT = 24;
    private static final int TAB_RIGHT = 33;
    private static final int TAB_LEFT = TAB_RIGHT - TAB_WIDTH;
    private static final int TAB_HOVER_OFFSET = 8;
    private static final int TAB_HOVER_LEFT = 3;
    private static final int TAB_HOVER_RIGHT = 23;
    private static final int TAB_HOVER_HEIGHT = 25;
    private static final int TAB_1_TOP = 52;
    private static final int TAB_2_TOP = 76;
    private static final int TAB_3_TOP = 104;
    private static final int CONTENT_LEFT = 30;
    private static final int CONTENT_RIGHT = 149;
    private static final int CONTENT_TOP = 54;
    private static final int CONTENT_BOTTOM = 184;
    private static final int TITLE_Y = 44;
    private static final int PAGE_BUTTON_WIDTH = 23;
    private static final int PAGE_BUTTON_HEIGHT = 13;
    private static final int PAGE_BUTTON_LEFT = CONTENT_RIGHT - PAGE_BUTTON_WIDTH + 1;
    private static final int PAGE_BUTTON_TOP = CONTENT_BOTTOM - PAGE_BUTTON_HEIGHT;
    private static final List<HiredVillagerRole> FIRST_OVERVIEW_PAGE_ROLES = List.of(
            HiredVillagerRole.COMBAT,
            HiredVillagerRole.HUNTING,
            HiredVillagerRole.MINING,
            HiredVillagerRole.LOGGING,
            HiredVillagerRole.FARMING,
            HiredVillagerRole.FISHING,
            HiredVillagerRole.BREWING,
            HiredVillagerRole.COOK);
    private static final List<HiredVillagerRole> SECOND_OVERVIEW_PAGE_ROLES = List.of(
            HiredVillagerRole.BUILDER,
            HiredVillagerRole.SMELTER,
            HiredVillagerRole.COURIER,
            HiredVillagerRole.ANIMAL_HANDLING,
            HiredVillagerRole.NITWIT);
    private static final ResourceLocation PAGE_FORWARD = ResourceLocation.withDefaultNamespace("widget/page_forward");
    private static final ResourceLocation PAGE_FORWARD_HIGHLIGHTED = ResourceLocation.withDefaultNamespace("widget/page_forward_highlighted");
    private static final ResourceLocation PAGE_BACKWARD = ResourceLocation.withDefaultNamespace("widget/page_backward");
    private static final ResourceLocation PAGE_BACKWARD_HIGHLIGHTED = ResourceLocation.withDefaultNamespace("widget/page_backward_highlighted");
    private static final int TEXT = 0xFF4B2B1D;
    private static final int MUTED = 0xFF8B6247;
    private static final int WARNING = 0xFF9A3B24;
    private static final int HOVER_FILL = 0x30A66A34;
    private static final int SELECTED_FILL = 0x3DA65C2B;
    private static final int ROW_HEIGHT = 11;
    private static final int ROW_OPTION_HEIGHT = 12;
    private static final int WORKER_ROW_HEIGHT = 66;
    private static final int WRAPPED_LINE_STEP = 9;
    private static final int HEADER_DIVIDER_Y = 10;
    private static final int HEADER_ROW_START_OFFSET = 14;
    private static final int WARNING_ARROW_GAP = 10;
    private static final int JOB_PAGE_ROW_START_OFFSET = 15;
    private static final int WORKER_ROW_BOTTOM_INSET = 7;

    private final ClipboardWorkforceSnapshot snapshot;
    private final List<RowAction> rowActions = new ArrayList<>();
    private Page page = Page.OVERVIEW;
    private HiredVillagerRole selectedRole = HiredVillagerRole.MINING;
    private WorkerRow selectedWorker;
    private int selectedOverviewRow;
    private boolean showOverviewSelection;
    private int overviewPage;
    private int workerScroll;
    private int jobScroll;
    private boolean closingWithAnimation;
    private boolean openedSoundPlayed;
    private long animationStartMillis = -1L;
    private AlphaMask mainPageHoverMask = AlphaMask.empty(TEXTURE_WIDTH, TEXTURE_HEIGHT);
    private AlphaMask homeTabHoverMask = AlphaMask.full(HOME_TAB_WIDTH, HOME_TAB_HEIGHT);
    private AlphaMask containerTab1HoverMask = AlphaMask.full(CONTAINER_TAB_1_WIDTH, CONTAINER_TAB_1_HEIGHT);
    private AlphaMask containerTab2HoverMask = AlphaMask.full(CONTAINER_TAB_2_WIDTH, CONTAINER_TAB_2_HEIGHT);
    private AlphaMask containerTab3HoverMask = AlphaMask.full(CONTAINER_TAB_3_WIDTH, CONTAINER_TAB_3_HEIGHT);
    private AlphaMask leftPageTabHoverMask = AlphaMask.full(PAGE_SIDE_TAB_WIDTH, PAGE_SIDE_TAB_HEIGHT);
    private AlphaMask rightPageTabHoverMask = AlphaMask.full(PAGE_SIDE_TAB_WIDTH, PAGE_SIDE_TAB_HEIGHT);
    private AlphaMask scrollDownTabMask = AlphaMask.full(SCROLL_TAB_WIDTH, SCROLL_TAB_HEIGHT);
    private AlphaMask scrollUpTabMask = AlphaMask.full(SCROLL_TAB_WIDTH, SCROLL_TAB_HEIGHT);
    private final TabSlideAnimation scrollDownTabAnimation = new TabSlideAnimation();
    private final TabSlideAnimation scrollUpTabAnimation = new TabSlideAnimation();

    public ClipboardWorkforceScreen(ClipboardWorkforceSnapshot snapshot) {
        super(Component.translatable("villagerretaliation.gui.clipboard_workforce.title"));
        this.snapshot = snapshot == null ? ClipboardWorkforceSnapshot.empty() : snapshot;
    }

    @Override
    protected void init() {
        this.closingWithAnimation = false;
        this.animationStartMillis = Util.getMillis();
        loadHoverMasks();
        this.scrollDownTabAnimation.reset(canScrollJobsDown());
        this.scrollUpTabAnimation.reset(canScrollJobsUp());
        if (!this.openedSoundPlayed) {
            this.openedSoundPlayed = true;
            playBookSound(0.9F);
        }
    }

    @Override
    public void tick() {
        if (this.closingWithAnimation && animationElapsedMillis() >= CLIPBOARD_ANIMATION_DURATION_MILLIS) {
            Minecraft.getInstance().setScreen(null);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        this.rowActions.clear();
        float scale = panelScale();
        int left = panelLeft(scale);
        int top = panelTop(scale) + slideOffsetY(scale);
        double panelMouseX = (mouseX - left) / scale;
        double panelMouseY = (mouseY - top) / scale;

        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        renderClipboard(graphics, panelMouseX, panelMouseY);
        graphics.pose().popPose();
        renderScrollTabTooltip(graphics, mouseX, mouseY, panelMouseX, panelMouseY);
    }

    private void renderClipboard(GuiGraphics graphics, double mouseX, double mouseY) {
        int hoveredTab = hoveredContainerTab(mouseX, mouseY);
        syncScrollTabAnimations();
        float scrollDownVisibility = this.scrollDownTabAnimation.visibility();
        float scrollUpVisibility = this.scrollUpTabAnimation.visibility();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(
                VillagerRetaliationClientAssets.CLIPBOARD_CONTAINER_TEXTURE,
                0,
                0,
                0.0F,
                0.0F,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT);
        graphics.blit(
                VillagerRetaliationClientAssets.CLIPBOARD_CONTAINER_SECOND_PAGE_TEXTURE,
                0,
                0,
                0.0F,
                0.0F,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT);
        graphics.blit(
                hoveredTab == 4
                        ? VillagerRetaliationClientAssets.CLIPBOARD_CONTAINER_HOME_TAB_HIGHLIGHT_TEXTURE
                        : VillagerRetaliationClientAssets.CLIPBOARD_CONTAINER_HOME_TAB_TEXTURE,
                HOME_TAB_LEFT,
                HOME_TAB_TOP,
                0.0F,
                0.0F,
                HOME_TAB_WIDTH,
                HOME_TAB_HEIGHT,
                HOME_TAB_WIDTH,
                HOME_TAB_HEIGHT);
        renderNumberedContainerTabs(graphics, hoveredTab);
        if (scrollDownVisibility > 0.0F) {
            graphics.blit(
                    VillagerRetaliationClientAssets.CLIPBOARD_CONTAINER_SCROLL_DOWN_TAB_TEXTURE,
                    SCROLL_DOWN_TAB_LEFT,
                    scrollDownTabTop(scrollDownVisibility),
                    0.0F,
                    0.0F,
                    SCROLL_TAB_WIDTH,
                    SCROLL_TAB_HEIGHT,
                    SCROLL_TAB_WIDTH,
                    SCROLL_TAB_HEIGHT);
        }
        if (scrollUpVisibility > 0.0F) {
            graphics.blit(
                    VillagerRetaliationClientAssets.CLIPBOARD_CONTAINER_SCROLL_UP_TAB_TEXTURE,
                    SCROLL_UP_TAB_LEFT,
                    scrollUpTabTop(scrollUpVisibility),
                    0.0F,
                    0.0F,
                    SCROLL_TAB_WIDTH,
                    SCROLL_TAB_HEIGHT,
                    SCROLL_TAB_WIDTH,
                    SCROLL_TAB_HEIGHT);
        }
        graphics.blit(
                hoveredTab == 5
                        ? VillagerRetaliationClientAssets.CLIPBOARD_CONTAINER_LEFT_TAB_HIGHLIGHT_TEXTURE
                        : VillagerRetaliationClientAssets.CLIPBOARD_CONTAINER_LEFT_TAB_TEXTURE,
                LEFT_PAGE_TAB_LEFT,
                PAGE_SIDE_TAB_TOP,
                0.0F,
                0.0F,
                PAGE_SIDE_TAB_WIDTH,
                PAGE_SIDE_TAB_HEIGHT,
                PAGE_SIDE_TAB_WIDTH,
                PAGE_SIDE_TAB_HEIGHT);
        graphics.blit(
                hoveredTab == 6
                        ? VillagerRetaliationClientAssets.CLIPBOARD_CONTAINER_RIGHT_TAB_HIGHLIGHT_TEXTURE
                        : VillagerRetaliationClientAssets.CLIPBOARD_CONTAINER_RIGHT_TAB_TEXTURE,
                RIGHT_PAGE_TAB_LEFT,
                PAGE_SIDE_TAB_TOP,
                0.0F,
                0.0F,
                PAGE_SIDE_TAB_WIDTH,
                PAGE_SIDE_TAB_HEIGHT,
                PAGE_SIDE_TAB_WIDTH,
                PAGE_SIDE_TAB_HEIGHT);
        graphics.blit(
                VillagerRetaliationClientAssets.CLIPBOARD_CONTAINER_MAIN_PAGE_TEXTURE,
                0,
                0,
                0.0F,
                0.0F,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT);
        renderClipboardHeading(graphics);
        RenderSystem.disableBlend();
    }

    private void renderScrollTabTooltip(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            double panelMouseX,
            double panelMouseY) {
        if (this.closingWithAnimation) {
            return;
        }
        float scrollDownVisibility = this.scrollDownTabAnimation.visibility();
        if (canScrollJobsDown()
                && scrollDownVisibility > 0.0F
                && isExposedTabPixel(
                        this.scrollDownTabMask,
                        panelMouseX,
                        panelMouseY,
                        SCROLL_DOWN_TAB_LEFT,
                        scrollDownTabTop(scrollDownVisibility))) {
            graphics.renderComponentTooltip(
                    this.font,
                    List.of(Component.translatable(
                            "villagerretaliation.gui.clipboard_workforce.scroll_down.tooltip")),
                    mouseX,
                    mouseY);
            return;
        }

        float scrollUpVisibility = this.scrollUpTabAnimation.visibility();
        if (canScrollJobsUp()
                && scrollUpVisibility > 0.0F
                && isExposedTabPixel(
                        this.scrollUpTabMask,
                        panelMouseX,
                        panelMouseY,
                        SCROLL_UP_TAB_LEFT,
                        scrollUpTabTop(scrollUpVisibility))) {
            graphics.renderComponentTooltip(
                    this.font,
                    List.of(Component.translatable(
                            "villagerretaliation.gui.clipboard_workforce.scroll_up.tooltip")),
                    mouseX,
                    mouseY);
        }
    }

    private void renderClipboardHeading(GuiGraphics graphics) {
        Component title = Component.translatable("villagerretaliation.gui.clipboard_workforce.title");
        int titleLeft = (TEXTURE_WIDTH - this.font.width(title)) / 2;
        graphics.drawString(this.font, title, titleLeft, WORKFORCE_TITLE_TOP, TEXT, false);
        renderCenteredDivider(graphics, TITLE_DIVIDER_TOP);
        renderWorkforceSummary(graphics, SUMMARY_TOP);
    }

    private void renderWorkforceSummary(GuiGraphics graphics, int top) {
        graphics.drawString(
                this.font,
                Component.translatable("villagerretaliation.gui.clipboard_workforce.summary.hired", this.snapshot.totalHired()),
                SUMMARY_LEFT,
                top,
                TEXT,
                false);

        int secondRowTop = top + SUMMARY_ROW_STEP;
        graphics.drawString(
                this.font,
                Component.translatable("villagerretaliation.gui.clipboard_workforce.working", this.snapshot.workingCount()),
                SUMMARY_LEFT,
                secondRowTop,
                TEXT,
                false);
        Component idle = Component.translatable(
                "villagerretaliation.gui.clipboard_workforce.idle", this.snapshot.idleCount());
        graphics.drawString(
                this.font,
                idle,
                SUMMARY_RIGHT - this.font.width(idle),
                secondRowTop,
                TEXT,
                false);

        graphics.drawString(
                this.font,
                Component.translatable(
                        "villagerretaliation.gui.clipboard_workforce.summary.warnings",
                        this.snapshot.warningCount()),
                SUMMARY_LEFT,
                WARNINGS_TOP,
                WARNING,
                false);
        renderCenteredDivider(graphics, JOBS_DIVIDER_TOP);
        renderJobsHeading(graphics, JOBS_TOP);
        renderCenteredDivider(graphics, JOB_LIST_DIVIDER_TOP);
        renderJobDescription(graphics);
        renderJobList(graphics, jobListTop());
    }

    private void renderJobsHeading(GuiGraphics graphics, int top) {
        Component jobs = Component.translatable("villagerretaliation.gui.clipboard_workforce.jobs");
        int textWidth = this.font.width(jobs);
        int textLeft = (TEXTURE_WIDTH - textWidth) / 2;
        int textRight = textLeft + textWidth;
        int iconTop = top + TEXT_PIXEL_HEIGHT - 1 - JOBS_ICON_BASELINE_ROW;
        int leftIconLeft = textLeft
                - JOBS_ICON_TEXT_GAP
                - JOBS_LEFT_ICON_RIGHTMOST_PIXEL
                - 1;
        int rightIconLeft = textRight
                + JOBS_ICON_TEXT_GAP
                - JOBS_RIGHT_ICON_LEFTMOST_PIXEL;

        graphics.blit(
                VillagerRetaliationClientAssets.CLIPBOARD_JOBS_ICONS_LEFT_TEXTURE,
                leftIconLeft,
                iconTop,
                0.0F,
                0.0F,
                JOBS_ICON_WIDTH,
                JOBS_ICON_HEIGHT,
                JOBS_ICON_WIDTH,
                JOBS_ICON_HEIGHT);
        graphics.drawString(this.font, jobs, textLeft, top, TEXT, false);
        graphics.blit(
                VillagerRetaliationClientAssets.CLIPBOARD_JOBS_ICONS_RIGHT_TEXTURE,
                rightIconLeft,
                iconTop,
                0.0F,
                0.0F,
                JOBS_ICON_WIDTH,
                JOBS_ICON_HEIGHT,
                JOBS_ICON_WIDTH,
                JOBS_ICON_HEIGHT);
    }

    private void renderJobList(GuiGraphics graphics, int top) {
        List<JobListRow> rows = sortedJobRows();
        int listTop = Math.max(TEXT_CONTENT_TOP, top);
        int visibleRows = visibleJobRows(listTop);
        int maxScroll = Math.max(0, rows.size() - visibleRows);
        this.jobScroll = Mth.clamp(this.jobScroll, 0, maxScroll);
        int end = Math.min(rows.size(), this.jobScroll + visibleRows);
        int rowTop = listTop;
        for (int index = this.jobScroll; index < end; index++) {
            JobListRow row = rows.get(index);
            int color = row.count() > 0 ? TEXT : MUTED;
            graphics.drawString(this.font, row.label(), SUMMARY_LEFT, rowTop, color, false);
            String count = Integer.toString(row.count());
            graphics.drawString(
                    this.font,
                    count,
                    SUMMARY_RIGHT - this.font.width(count),
                    rowTop,
                    color,
                    false);
            rowTop += SUMMARY_ROW_STEP;
        }
    }

    private void renderJobDescription(GuiGraphics graphics) {
        int lineTop = JOB_DESCRIPTION_TOP;
        for (net.minecraft.util.FormattedCharSequence line : jobDescriptionLines()) {
            graphics.drawString(this.font, line, SUMMARY_LEFT, lineTop, TEXT, false);
            lineTop += this.font.lineHeight;
        }
    }

    private List<net.minecraft.util.FormattedCharSequence> jobDescriptionLines() {
        return this.font.split(
                Component.translatable("villagerretaliation.gui.clipboard_workforce.jobs.description"),
                SUMMARY_RIGHT - SUMMARY_LEFT);
    }

    private int jobListTop() {
        int lineCount = Math.max(1, jobDescriptionLines().size());
        int lastLineTop = JOB_DESCRIPTION_TOP + (lineCount - 1) * this.font.lineHeight;
        return lastLineTop + TEXT_PIXEL_HEIGHT + GLOBAL_TEXT_GAP;
    }

    private List<JobListRow> sortedJobRows() {
        List<JobListRow> rows = new ArrayList<>();
        for (HiredVillagerRole role : HiredVillagerRole.values()) {
            rows.add(new JobListRow(roleName(role), jobCount(role)));
        }
        rows.sort(Comparator.comparingInt(JobListRow::count)
                .reversed()
                .thenComparing(row -> row.label().getString(), String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private static int visibleJobRows(int top) {
        int availableHeight = TEXT_CONTENT_BOTTOM - top;
        if (availableHeight < TEXT_PIXEL_HEIGHT) {
            return 0;
        }
        return 1 + (availableHeight - TEXT_PIXEL_HEIGHT) / SUMMARY_ROW_STEP;
    }

    private int maxJobScroll() {
        return Math.max(0, sortedJobRows().size() - visibleJobRows(jobListTop()));
    }

    private boolean canScrollJobsDown() {
        return this.jobScroll < maxJobScroll();
    }

    private boolean canScrollJobsUp() {
        return this.jobScroll > 0 && maxJobScroll() > 0;
    }

    private void scrollJobs(int direction) {
        this.jobScroll = Mth.clamp(this.jobScroll + direction, 0, maxJobScroll());
    }

    private void syncScrollTabAnimations() {
        this.scrollDownTabAnimation.setVisible(canScrollJobsDown());
        this.scrollUpTabAnimation.setVisible(canScrollJobsUp());
    }

    private static int scrollDownTabTop(float visibility) {
        return Math.round(Mth.lerp(
                visibility,
                SCROLL_DOWN_TAB_TOP - SCROLL_TAB_START_INSET,
                SCROLL_DOWN_TAB_TOP));
    }

    private static int scrollUpTabTop(float visibility) {
        return Math.round(Mth.lerp(
                visibility,
                SCROLL_UP_TAB_TOP + SCROLL_TAB_START_INSET,
                SCROLL_UP_TAB_TOP));
    }

    private static void renderCenteredDivider(GuiGraphics graphics, int top) {
        int left = (TEXTURE_WIDTH - DIVIDER_WIDTH) / 2;
        graphics.blit(
                VillagerRetaliationClientAssets.CLIPBOARD_DIVIDER_TEXTURE,
                left,
                top,
                0.0F,
                0.0F,
                DIVIDER_WIDTH,
                DIVIDER_HEIGHT,
                DIVIDER_WIDTH,
                DIVIDER_HEIGHT);
    }

    private static void renderContainerTab(
            GuiGraphics graphics,
            ResourceLocation texture,
            int width,
            int height,
            int top) {
        int left = CONTAINER_TAB_RIGHT - width + 1;
        graphics.blit(texture, left, top, 0.0F, 0.0F, width, height, width, height);
    }

    private static void renderNumberedContainerTabs(GuiGraphics graphics, int hoveredTab) {
        if (hoveredTab != 3) {
            renderContainerTab(
                    graphics,
                    VillagerRetaliationClientAssets.CLIPBOARD_CONTAINER_TAB_3_TEXTURE,
                    CONTAINER_TAB_3_WIDTH,
                    CONTAINER_TAB_3_HEIGHT,
                    CONTAINER_TAB_3_TOP);
        }
        if (hoveredTab != 2) {
            renderContainerTab(
                    graphics,
                    VillagerRetaliationClientAssets.CLIPBOARD_CONTAINER_TAB_2_TEXTURE,
                    CONTAINER_TAB_2_WIDTH,
                    CONTAINER_TAB_2_HEIGHT,
                    CONTAINER_TAB_2_TOP);
        }
        if (hoveredTab != 1) {
            renderContainerTab(
                    graphics,
                    VillagerRetaliationClientAssets.CLIPBOARD_CONTAINER_TAB_1_TEXTURE,
                    CONTAINER_TAB_1_WIDTH,
                    CONTAINER_TAB_1_HEIGHT,
                    CONTAINER_TAB_1_TOP);
        }

        switch (hoveredTab) {
            case 1 -> renderContainerTab(
                    graphics,
                    VillagerRetaliationClientAssets.CLIPBOARD_CONTAINER_TAB_1_HIGHLIGHT_TEXTURE,
                    CONTAINER_TAB_1_WIDTH,
                    CONTAINER_TAB_1_HEIGHT,
                    CONTAINER_TAB_1_TOP);
            case 2 -> renderContainerTab(
                    graphics,
                    VillagerRetaliationClientAssets.CLIPBOARD_CONTAINER_TAB_2_HIGHLIGHT_TEXTURE,
                    CONTAINER_TAB_2_WIDTH,
                    CONTAINER_TAB_2_HEIGHT,
                    CONTAINER_TAB_2_TOP);
            case 3 -> renderContainerTab(
                    graphics,
                    VillagerRetaliationClientAssets.CLIPBOARD_CONTAINER_TAB_3_HIGHLIGHT_TEXTURE,
                    CONTAINER_TAB_3_WIDTH,
                    CONTAINER_TAB_3_HEIGHT,
                    CONTAINER_TAB_3_TOP);
            default -> {
            }
        }
    }

    private int hoveredContainerTab(double mouseX, double mouseY) {
        int clipboardX = (int) Math.floor(mouseX);
        int clipboardY = (int) Math.floor(mouseY);
        if (this.mainPageHoverMask.hasAlpha(clipboardX, clipboardY)) {
            return 0;
        }
        if (isTabPixelHovered(
                this.leftPageTabHoverMask,
                mouseX,
                mouseY,
                LEFT_PAGE_TAB_LEFT,
                PAGE_SIDE_TAB_TOP)) {
            return 5;
        }
        if (isTabPixelHovered(
                this.rightPageTabHoverMask,
                mouseX,
                mouseY,
                RIGHT_PAGE_TAB_LEFT,
                PAGE_SIDE_TAB_TOP)) {
            return 6;
        }
        if (isTabPixelHovered(
                this.containerTab1HoverMask,
                mouseX,
                mouseY,
                CONTAINER_TAB_RIGHT - CONTAINER_TAB_1_WIDTH + 1,
                CONTAINER_TAB_1_TOP)) {
            return 1;
        }
        if (isTabPixelHovered(
                this.containerTab2HoverMask,
                mouseX,
                mouseY,
                CONTAINER_TAB_RIGHT - CONTAINER_TAB_2_WIDTH + 1,
                CONTAINER_TAB_2_TOP)) {
            return 2;
        }
        if (isTabPixelHovered(
                this.containerTab3HoverMask,
                mouseX,
                mouseY,
                CONTAINER_TAB_RIGHT - CONTAINER_TAB_3_WIDTH + 1,
                CONTAINER_TAB_3_TOP)) {
            return 3;
        }
        if (isTabPixelHovered(this.homeTabHoverMask, mouseX, mouseY, HOME_TAB_LEFT, HOME_TAB_TOP)) {
            return 4;
        }
        return 0;
    }

    private static boolean isTabPixelHovered(AlphaMask mask, double mouseX, double mouseY, int left, int top) {
        int textureX = (int) Math.floor(mouseX - left);
        int textureY = (int) Math.floor(mouseY - top);
        return mask.hasAlpha(textureX, textureY);
    }

    private boolean isExposedTabPixel(AlphaMask mask, double mouseX, double mouseY, int left, int top) {
        int clipboardX = (int) Math.floor(mouseX);
        int clipboardY = (int) Math.floor(mouseY);
        return !this.mainPageHoverMask.hasAlpha(clipboardX, clipboardY)
                && isTabPixelHovered(mask, mouseX, mouseY, left, top);
    }

    private static void renderClipboardTab(
            GuiGraphics graphics,
            double mouseX,
            double mouseY,
            ResourceLocation texture,
            int top,
            boolean active) {
        int left = active || isClipboardTabHovered(mouseX, mouseY, top) ? TAB_LEFT - TAB_HOVER_OFFSET : TAB_LEFT;
        graphics.blit(
                texture,
                left,
                top,
                0.0F,
                0.0F,
                TAB_WIDTH,
                TAB_HEIGHT,
                TAB_WIDTH,
                TAB_HEIGHT);
    }

    private static boolean isClipboardTabHovered(double mouseX, double mouseY, int top) {
        return mouseX >= TAB_HOVER_LEFT && mouseX <= TAB_HOVER_RIGHT && mouseY >= top && mouseY <= top + TAB_HOVER_HEIGHT;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.closingWithAnimation) {
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            float scale = panelScale();
            int left = panelLeft(scale);
            int top = panelTop(scale) + slideOffsetY(scale);
            double panelMouseX = (mouseX - left) / scale;
            double panelMouseY = (mouseY - top) / scale;
            syncScrollTabAnimations();
            float scrollDownVisibility = this.scrollDownTabAnimation.visibility();
            float scrollUpVisibility = this.scrollUpTabAnimation.visibility();
            if (canScrollJobsDown() && isExposedTabPixel(
                    this.scrollDownTabMask,
                    panelMouseX,
                    panelMouseY,
                    SCROLL_DOWN_TAB_LEFT,
                    scrollDownTabTop(scrollDownVisibility))) {
                scrollJobs(1);
                playPageSound();
                return true;
            }
            if (canScrollJobsUp() && isExposedTabPixel(
                    this.scrollUpTabMask,
                    panelMouseX,
                    panelMouseY,
                    SCROLL_UP_TAB_LEFT,
                    scrollUpTabTop(scrollUpVisibility))) {
                scrollJobs(-1);
                playPageSound();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleClipboardTabClick(double panelMouseX, double panelMouseY) {
        ClipboardPreviewTab tab = hoveredPreviewTab(panelMouseX, panelMouseY);
        if (tab == null) {
            return false;
        }
        playPageSound();
        tab.toggle();
        PacketDistributor.sendToServer(new ClipboardPreviewTogglePayload(ClipboardStorageOutlineRenderer.anyNearbyPreviewEnabled()));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.closingWithAnimation) {
            return true;
        }
        if (scrollY != 0.0D) {
            float scale = panelScale();
            int left = panelLeft(scale);
            int top = panelTop(scale) + slideOffsetY(scale);
            double panelMouseX = (mouseX - left) / scale;
            double panelMouseY = (mouseY - top) / scale;
            if (panelMouseX >= SUMMARY_LEFT
                    && panelMouseX < SUMMARY_RIGHT
                    && panelMouseY >= Math.max(TEXT_CONTENT_TOP, JOB_DESCRIPTION_TOP)
                    && panelMouseY < TEXT_CONTENT_BOTTOM) {
                int maxScroll = maxJobScroll();
                scrollJobs(-(int) Math.signum(scrollY));
                return maxScroll > 0;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.closingWithAnimation) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        closeClipboard();
    }

    private void renderOverview(GuiGraphics graphics, double mouseX, double mouseY) {
        drawCentered(graphics, Component.translatable("villagerretaliation.gui.clipboard_workforce.title"), TITLE_Y, TEXT);
        int y = CONTENT_TOP + 2;
        drawLine(graphics, Component.translatable("villagerretaliation.gui.clipboard_workforce.hired",
                this.snapshot.totalHired(),
                this.snapshot.maxHired() < 0 ? Component.translatable("villagerretaliation.gui.clipboard_workforce.unknown").getString() : this.snapshot.maxHired()), CONTENT_LEFT, y, TEXT);
        y += 12;
        drawMetricPair(graphics, y,
                Component.translatable("villagerretaliation.gui.clipboard_workforce.working", this.snapshot.workingCount()),
                Component.translatable("villagerretaliation.gui.clipboard_workforce.idle", this.snapshot.idleCount()));
        y += 12;
        if (contains(mouseX, mouseY, CONTENT_LEFT - 2, y - 2, CONTENT_RIGHT + 1, y + 10)) {
            graphics.fill(CONTENT_LEFT - 2, y - 2, CONTENT_RIGHT + 1, y + 10, HOVER_FILL);
        }
        drawLine(graphics, Component.translatable("villagerretaliation.gui.clipboard_workforce.warnings", this.snapshot.warningCount()), CONTENT_LEFT, y, warningColor(this.snapshot.warningCount()));
        this.rowActions.add(new RowAction(RowKind.WARNINGS, null, CONTENT_LEFT - 2, y - 2, CONTENT_RIGHT + 1, y + 10));
        y += 14;
        drawSmallHeader(graphics, Component.translatable("villagerretaliation.gui.clipboard_workforce.jobs"), y);
        y += HEADER_ROW_START_OFFSET;
        int rowIndex = 0;
        for (OverviewRow row : overviewRows()) {
            boolean selected = this.showOverviewSelection && this.selectedOverviewRow == rowIndex;
            y = drawNavigationRow(graphics, mouseX, mouseY, y, selected, row.label(), row.value(), row.kind(), row.role(), row.muted());
            rowIndex++;
        }
        renderOverviewPageButton(graphics, mouseX, mouseY);
    }

    private void renderJobPage(GuiGraphics graphics, double mouseX, double mouseY) {
        renderBackRow(graphics, mouseX, mouseY);
        drawCentered(graphics, roleName(this.selectedRole), TITLE_Y, TEXT);
        List<WorkerRow> workers = workersForSelectedRole();
        if (workers.isEmpty()) {
            int y = CONTENT_TOP + 22;
            drawLine(graphics, Component.translatable("villagerretaliation.gui.clipboard_workforce.no_workers"), CONTENT_LEFT, y, MUTED);
            drawWrapped(graphics, Component.translatable("villagerretaliation.gui.clipboard_workforce.controls_coming"), y + 12);
            return;
        }
        int maxScroll = Math.max(0, workers.size() - visibleWorkerRows());
        this.workerScroll = Mth.clamp(this.workerScroll, 0, maxScroll);
        int y = CONTENT_TOP + JOB_PAGE_ROW_START_OFFSET;
        int end = Math.min(workers.size(), this.workerScroll + visibleWorkerRows());
        for (int index = this.workerScroll; index < end; index++) {
            WorkerRow worker = workers.get(index);
            renderWorkerRow(graphics, mouseX, mouseY, worker, y);
            y += WORKER_ROW_HEIGHT;
        }
        if (maxScroll > 0) {
            drawCentered(graphics, Component.translatable("villagerretaliation.gui.clipboard_workforce.page_count", this.workerScroll + 1, maxScroll + 1), CONTENT_BOTTOM - 4, MUTED);
        }
    }

    private void renderWarningsPage(GuiGraphics graphics, double mouseX, double mouseY) {
        renderBackRow(graphics, mouseX, mouseY);
        drawCentered(graphics, Component.translatable("villagerretaliation.gui.clipboard_workforce.warning_header"), TITLE_Y, TEXT);
        if (this.snapshot.warnings().isEmpty()) {
            drawLine(graphics, Component.translatable("villagerretaliation.gui.clipboard_workforce.no_warnings"), CONTENT_LEFT, CONTENT_TOP + 22, MUTED);
            return;
        }

        int maxScroll = Math.max(0, this.snapshot.warnings().size() - visibleWarningRows());
        this.workerScroll = Mth.clamp(this.workerScroll, 0, maxScroll);
        int y = CONTENT_TOP + 16;
        int end = Math.min(this.snapshot.warnings().size(), this.workerScroll + visibleWarningRows());
        for (int index = this.workerScroll; index < end; index++) {
            WarningSummary warning = this.snapshot.warnings().get(index);
            Component text = warningText(warning);
            int rowHeight = warningRowHeight(text);
            int rowBottom = y + rowHeight - 2;
            if (rowBottom > CONTENT_BOTTOM) {
                return;
            }
            boolean hovered = contains(mouseX, mouseY, CONTENT_LEFT - 2, y - 2, CONTENT_RIGHT + 1, rowBottom);
            if (hovered) {
                graphics.fill(CONTENT_LEFT - 2, y - 2, CONTENT_RIGHT + 1, rowBottom, HOVER_FILL);
            }
            drawWrappedLines(graphics, text, CONTENT_LEFT, y, warningTextRight(), WARNING);
            drawRight(graphics, Component.literal(">"), CONTENT_RIGHT, y, WARNING);
            this.rowActions.add(new RowAction(RowKind.JOB, warning.role(), CONTENT_LEFT - 2, y - 2, CONTENT_RIGHT + 1, rowBottom));
            y += rowHeight;
        }
        if (maxScroll > 0) {
            drawCentered(graphics, Component.translatable("villagerretaliation.gui.clipboard_workforce.page_count", this.workerScroll + 1, maxScroll + 1), CONTENT_BOTTOM - 4, MUTED);
        }
    }

    private void renderWorkerRow(GuiGraphics graphics, double mouseX, double mouseY, WorkerRow worker, int y) {
        int rowBottom = Math.max(y + WORKER_ROW_HEIGHT - WORKER_ROW_BOTTOM_INSET, workerSummaryBottom(worker, y));
        boolean hovered = contains(mouseX, mouseY, CONTENT_LEFT - 2, y - 2, CONTENT_RIGHT + 1, rowBottom);
        if (hovered) {
            graphics.fill(CONTENT_LEFT - 2, y - 2, CONTENT_RIGHT + 1, rowBottom, HOVER_FILL);
        }
        renderWorkerSummary(graphics, worker, y);
        drawRight(graphics, Component.literal(">"), CONTENT_RIGHT, y, mutedForWarning(worker));
        this.rowActions.add(RowAction.worker(worker, CONTENT_LEFT - 2, y - 2, CONTENT_RIGHT + 1, rowBottom));
    }

    private int renderWorkerSummary(GuiGraphics graphics, WorkerRow worker, int y) {
        drawLine(graphics, Component.literal(worker.displayName()), CONTENT_LEFT, y, CONTENT_RIGHT - 10, TEXT);
        if (hasWarning(worker)) {
            drawLine(graphics, Component.literal("!"), CONTENT_RIGHT - 6, y, WARNING);
        }
        int lineY = y + 10;
        drawLine(graphics, Component.translatable("villagerretaliation.gui.clipboard_workforce.worker_status", statusName(worker.status())), CONTENT_LEFT, lineY, mutedForWarning(worker));
        lineY += 10;
        if (!worker.diagnostic().isBlank()) {
            lineY = drawWrappedLines(
                    graphics,
                    Component.translatable(
                            "villagerretaliation.gui.clipboard_workforce.worker_issue",
                            Component.literal(worker.diagnostic())),
                    CONTENT_LEFT,
                    lineY,
                    WARNING);
        }
        Component area = workerAreaText(worker);
        lineY = drawWrappedLines(graphics, Component.translatable("villagerretaliation.gui.clipboard_workforce.worker_area", area), CONTENT_LEFT, lineY, worker.noWorkArea() ? WARNING : MUTED);
        if (!worker.target().isBlank()) {
            lineY = drawWrappedLines(graphics, Component.translatable("villagerretaliation.gui.clipboard_workforce.worker_target", Component.literal(worker.target())), CONTENT_LEFT, lineY, MUTED);
        }
        if (!worker.workMode().isBlank()) {
            lineY = drawWrappedLines(graphics, Component.translatable("villagerretaliation.gui.clipboard_workforce.worker_mode", Component.literal(worker.workMode())), CONTENT_LEFT, lineY, MUTED);
        }
        return drawWrappedLines(graphics, workerStorageText(worker), CONTENT_LEFT, lineY + 1, worker.noStorage() ? WARNING : MUTED);
    }

    private int workerSummaryBottom(WorkerRow worker, int y) {
        int lineY = y + 20;
        if (!worker.diagnostic().isBlank()) {
            lineY += wrappedLineCount(Component.translatable(
                    "villagerretaliation.gui.clipboard_workforce.worker_issue",
                    Component.literal(worker.diagnostic()))) * WRAPPED_LINE_STEP;
        }
        lineY += wrappedLineCount(Component.translatable("villagerretaliation.gui.clipboard_workforce.worker_area", workerAreaText(worker))) * WRAPPED_LINE_STEP;
        if (!worker.target().isBlank()) {
            lineY += wrappedLineCount(Component.translatable("villagerretaliation.gui.clipboard_workforce.worker_target", Component.literal(worker.target()))) * WRAPPED_LINE_STEP;
        }
        if (!worker.workMode().isBlank()) {
            lineY += wrappedLineCount(Component.translatable("villagerretaliation.gui.clipboard_workforce.worker_mode", Component.literal(worker.workMode()))) * WRAPPED_LINE_STEP;
        }
        return lineY + 1 + wrappedLineCount(workerStorageText(worker)) * WRAPPED_LINE_STEP;
    }

    private int wrappedLineCount(Component text) {
        return Math.max(1, this.font.split(text, CONTENT_RIGHT - CONTENT_LEFT).size());
    }

    private Component workerAreaText(WorkerRow worker) {
        if (routeAssigned(worker)) {
            return Component.translatable("villagerretaliation.gui.clipboard_workforce.worker_route_assigned",
                    worker.workAreaCenter());
        }
        return worker.hasWorkArea()
                ? Component.translatable("villagerretaliation.gui.clipboard_workforce.worker_area_assigned",
                        workerAreaSourceText(worker),
                        worker.workAreaCenter(),
                        worker.horizontalRadius(),
                        worker.verticalRadius())
                : Component.translatable("villagerretaliation.gui.clipboard_workforce.worker_area_missing");
    }

    private boolean routeAssigned(WorkerRow worker) {
        return worker != null && "route".equals(worker.areaStatus());
    }

    private Component workerAreaSourceText(WorkerRow worker) {
        String source = worker.areaStatus() == null || worker.areaStatus().isBlank()
                ? "assigned_area"
                : worker.areaStatus();
        return Component.translatable("villagerretaliation.gui.clipboard_workforce.worker_area_source." + source);
    }

    private Component workerStorageText(WorkerRow worker) {
        return Component.translatable("villagerretaliation.gui.clipboard_workforce.worker_storage",
                worker.storageAssigned()
                        ? Component.translatable("villagerretaliation.gui.clipboard_workforce.assigned")
                        : Component.translatable("villagerretaliation.gui.clipboard_workforce.missing"),
                worker.storageCount(),
                worker.dailyWage());
    }

    private void renderJobSitePage(GuiGraphics graphics, double mouseX, double mouseY) {
        renderBackRow(graphics, mouseX, mouseY);
        drawCentered(graphics, Component.translatable("villagerretaliation.gui.clipboard_workforce.job_site"), TITLE_Y, TEXT);
        if (this.selectedWorker == null) {
            drawLine(graphics, Component.translatable("villagerretaliation.gui.clipboard_workforce.job_site_unavailable"), CONTENT_LEFT, CONTENT_TOP + JOB_PAGE_ROW_START_OFFSET, MUTED);
            return;
        }

        int y = CONTENT_TOP + JOB_PAGE_ROW_START_OFFSET;
        drawLine(graphics, Component.literal(this.selectedWorker.displayName()), CONTENT_LEFT, y, CONTENT_RIGHT - 10, TEXT);
        Component area = workerAreaText(this.selectedWorker);
        drawWrappedLines(graphics, Component.translatable("villagerretaliation.gui.clipboard_workforce.worker_area", area), CONTENT_LEFT, y + 10, this.selectedWorker.noWorkArea() ? WARNING : MUTED);
        y = CONTENT_TOP + 47;
        y = drawJobSiteActionRow(
                graphics,
                mouseX,
                mouseY,
                y,
                Component.translatable("villagerretaliation.gui.clipboard_workforce.job_site_preview"),
                ClipboardWorkAreaActionPayload.Action.PREVIEW);
        y = drawJobSiteActionRow(
                graphics,
                mouseX,
                mouseY,
                y,
                Component.translatable("villagerretaliation.gui.clipboard_workforce.job_site_apply_draft"),
                ClipboardWorkAreaActionPayload.Action.APPLY_HELD_DRAFT);
        y = drawJobSiteActionRow(
                graphics,
                mouseX,
                mouseY,
                y,
                Component.translatable("villagerretaliation.gui.clipboard_workforce.job_site_center_here"),
                ClipboardWorkAreaActionPayload.Action.SET_CENTER_HERE);
        y = drawJobSiteActionRow(
                graphics,
                mouseX,
                mouseY,
                y,
                Component.translatable("villagerretaliation.gui.clipboard_workforce.job_site_center_villager"),
                ClipboardWorkAreaActionPayload.Action.RESET_CENTER_TO_VILLAGER);
        if (this.selectedWorker.role() == HiredVillagerRole.MINING || this.selectedWorker.role() == HiredVillagerRole.HUNTING) {
            String configureKey = this.selectedWorker.role() == HiredVillagerRole.HUNTING
                    ? "villagerretaliation.gui.clipboard_workforce.job_site_configure_hunting"
                    : "villagerretaliation.gui.clipboard_workforce.job_site_configure_mining";
            y = drawJobSiteActionRow(
                    graphics,
                    mouseX,
                    mouseY,
                    y,
                    Component.translatable(
                            configureKey,
                            Component.literal(this.selectedWorker.workMode())),
                    ClipboardWorkAreaActionPayload.Action.CONFIGURE_ROLE);
        }
        y = drawJobSiteDirectionRow(
                graphics,
                mouseX,
                mouseY,
                y,
                "+",
                List.of(
                        new JobSiteButton("N", ClipboardWorkAreaActionPayload.Action.EXPAND_NORTH),
                        new JobSiteButton("E", ClipboardWorkAreaActionPayload.Action.EXPAND_EAST),
                        new JobSiteButton("S", ClipboardWorkAreaActionPayload.Action.EXPAND_SOUTH),
                        new JobSiteButton("W", ClipboardWorkAreaActionPayload.Action.EXPAND_WEST)));
        y = drawJobSiteDirectionRow(
                graphics,
                mouseX,
                mouseY,
                y,
                "-",
                List.of(
                        new JobSiteButton("N", ClipboardWorkAreaActionPayload.Action.CONTRACT_NORTH),
                        new JobSiteButton("E", ClipboardWorkAreaActionPayload.Action.CONTRACT_EAST),
                        new JobSiteButton("S", ClipboardWorkAreaActionPayload.Action.CONTRACT_SOUTH),
                        new JobSiteButton("W", ClipboardWorkAreaActionPayload.Action.CONTRACT_WEST)));
        y = drawJobSiteDirectionRow(
                graphics,
                mouseX,
                mouseY,
                y,
                "+",
                List.of(
                        new JobSiteButton("Up", ClipboardWorkAreaActionPayload.Action.EXPAND_UP),
                        new JobSiteButton("Down", ClipboardWorkAreaActionPayload.Action.EXPAND_DOWN)));
        drawJobSiteDirectionRow(
                graphics,
                mouseX,
                mouseY,
                y,
                "-",
                List.of(
                        new JobSiteButton("Up", ClipboardWorkAreaActionPayload.Action.CONTRACT_UP),
                        new JobSiteButton("Down", ClipboardWorkAreaActionPayload.Action.CONTRACT_DOWN)));
    }

    private void renderStoragePage(GuiGraphics graphics, double mouseX, double mouseY) {
        renderBackRow(graphics, mouseX, mouseY);
        drawCentered(graphics, Component.translatable("villagerretaliation.gui.clipboard_workforce.storage"), TITLE_Y, TEXT);
        int missing = (int) this.snapshot.workers().stream().filter(WorkerRow::noStorage).count();
        int y = CONTENT_TOP + 18;
        drawLine(graphics, Component.translatable("villagerretaliation.gui.clipboard_workforce.assigned_containers", this.snapshot.assignedStorageCount()), CONTENT_LEFT, y, TEXT);
        drawLine(graphics, Component.translatable("villagerretaliation.gui.clipboard_workforce.missing_storage_workers", missing), CONTENT_LEFT, y + 14, warningColor(missing));
        drawWrapped(graphics, Component.translatable("villagerretaliation.gui.clipboard_workforce.storage_placeholder"), y + 34);
    }

    private void renderPaymentPage(GuiGraphics graphics, double mouseX, double mouseY) {
        renderBackRow(graphics, mouseX, mouseY);
        drawCentered(graphics, Component.translatable("villagerretaliation.gui.clipboard_workforce.payment"), TITLE_Y, TEXT);
        int y = CONTENT_TOP + 18;
        drawLine(graphics, Component.translatable("villagerretaliation.gui.clipboard_workforce.daily_wages", this.snapshot.dailyWages()), CONTENT_LEFT, y, TEXT);
        drawLine(graphics, Component.translatable("villagerretaliation.gui.clipboard_workforce.payment_containers", this.snapshot.paymentContainerCount()), CONTENT_LEFT, y + 14, TEXT);
        drawWrapped(graphics, Component.translatable("villagerretaliation.gui.clipboard_workforce.payment_placeholder"), y + 36);
    }

    private int drawNavigationRow(
            GuiGraphics graphics,
            double mouseX,
            double mouseY,
            int y,
            boolean selected,
            Component label,
            String value,
            RowKind kind,
            HiredVillagerRole role,
            boolean muted) {
        boolean hovered = contains(mouseX, mouseY, CONTENT_LEFT - 2, y - 1, CONTENT_RIGHT + 1, y + ROW_OPTION_HEIGHT - 1);
        int rowBottom = y + ROW_OPTION_HEIGHT - 3;
        if (selected || hovered) {
            graphics.fill(CONTENT_LEFT - 2, y - 2, CONTENT_RIGHT + 1, rowBottom, selected ? SELECTED_FILL : HOVER_FILL);
        }
        drawLine(graphics, Component.literal(selected ? ">" : ""), CONTENT_LEFT - 8, y, TEXT);
        drawRight(graphics, Component.literal(value), CONTENT_RIGHT, y, muted ? MUTED : TEXT);
        drawLine(graphics, label, CONTENT_LEFT, y, CONTENT_RIGHT - this.font.width(value) - 8, muted ? MUTED : TEXT);
        this.rowActions.add(new RowAction(kind, role, CONTENT_LEFT - 2, y - 2, CONTENT_RIGHT + 1, rowBottom));
        return y + ROW_HEIGHT;
    }

    private void renderBackRow(GuiGraphics graphics, double mouseX, double mouseY) {
        Component back = Component.translatable("villagerretaliation.gui.clipboard_workforce.back");
        int y = CONTENT_TOP + 2;
        int right = CONTENT_LEFT + Math.min(46, this.font.width(back) + 4);
        boolean hovered = contains(mouseX, mouseY, CONTENT_LEFT - 2, y - 2, right, y + 9);
        if (hovered) {
            graphics.fill(CONTENT_LEFT - 2, y - 2, right, y + 9, HOVER_FILL);
        }
        drawLine(graphics, back, CONTENT_LEFT, y, TEXT);
        this.rowActions.add(new RowAction(RowKind.BACK, null, CONTENT_LEFT - 2, y - 2, right, y + 9));
    }

    private int drawJobSiteActionRow(
            GuiGraphics graphics,
            double mouseX,
            double mouseY,
            int y,
            Component label,
            ClipboardWorkAreaActionPayload.Action action) {
        int rowBottom = y + ROW_OPTION_HEIGHT - 3;
        boolean hovered = contains(mouseX, mouseY, CONTENT_LEFT - 2, y - 2, CONTENT_RIGHT + 1, rowBottom);
        if (hovered) {
            graphics.fill(CONTENT_LEFT - 2, y - 2, CONTENT_RIGHT + 1, rowBottom, HOVER_FILL);
        }
        drawLine(graphics, label, CONTENT_LEFT, y, CONTENT_RIGHT - 10, TEXT);
        drawRight(graphics, Component.literal(">"), CONTENT_RIGHT, y, MUTED);
        this.rowActions.add(RowAction.jobSiteAction(this.selectedWorker, action, CONTENT_LEFT - 2, y - 2, CONTENT_RIGHT + 1, rowBottom));
        return y + ROW_HEIGHT;
    }

    private int drawJobSiteDirectionRow(
            GuiGraphics graphics,
            double mouseX,
            double mouseY,
            int y,
            String prefix,
            List<JobSiteButton> buttons) {
        drawLine(graphics, Component.literal(prefix), CONTENT_LEFT, y, TEXT);
        int rowBottom = y + ROW_OPTION_HEIGHT - 3;
        int buttonCount = Math.max(1, buttons.size());
        int buttonLeft = CONTENT_LEFT + 20;
        int buttonWidth = Math.max(18, (CONTENT_RIGHT - buttonLeft + 1) / buttonCount);
        for (int index = 0; index < buttons.size(); index++) {
            JobSiteButton button = buttons.get(index);
            int left = buttonLeft + index * buttonWidth;
            int right = index == buttons.size() - 1 ? CONTENT_RIGHT + 1 : left + buttonWidth - 2;
            boolean hovered = contains(mouseX, mouseY, left, y - 2, right, rowBottom);
            if (hovered) {
                graphics.fill(left, y - 2, right, rowBottom, HOVER_FILL);
            }
            String label = fit(button.label(), right - left - 2);
            int labelLeft = left + Math.max(0, (right - left - this.font.width(label)) / 2);
            int labelTop = y - 2 + Math.max(0, (rowBottom - (y - 2) - this.font.lineHeight) / 2) + 1;
            graphics.drawString(this.font, label, labelLeft, labelTop, TEXT, false);
            this.rowActions.add(RowAction.jobSiteAction(
                    this.selectedWorker,
                    button.action(),
                    jobSiteButtonTooltip(prefix, button.label()),
                    left,
                    y - 2,
                    right,
                    rowBottom));
        }
        return y + ROW_HEIGHT;
    }

    private List<Component> jobSiteButtonTooltip(String prefix, String label) {
        return List.of(
                Component.literal(prefix + " " + label),
                Component.literal("shift to increment by 5").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    private void renderOverviewPageButton(GuiGraphics graphics, double mouseX, double mouseY) {
        boolean hovered = contains(mouseX, mouseY, PAGE_BUTTON_LEFT, PAGE_BUTTON_TOP, PAGE_BUTTON_LEFT + PAGE_BUTTON_WIDTH, PAGE_BUTTON_TOP + PAGE_BUTTON_HEIGHT);
        ResourceLocation sprite = this.overviewPage == 0
                ? (hovered ? PAGE_FORWARD_HIGHLIGHTED : PAGE_FORWARD)
                : (hovered ? PAGE_BACKWARD_HIGHLIGHTED : PAGE_BACKWARD);
        graphics.blitSprite(sprite, PAGE_BUTTON_LEFT, PAGE_BUTTON_TOP, PAGE_BUTTON_WIDTH, PAGE_BUTTON_HEIGHT);
        this.rowActions.add(new RowAction(
                RowKind.PAGE_TURN,
                null,
                PAGE_BUTTON_LEFT,
                PAGE_BUTTON_TOP,
                PAGE_BUTTON_LEFT + PAGE_BUTTON_WIDTH,
                PAGE_BUTTON_TOP + PAGE_BUTTON_HEIGHT));
    }

    private void drawMetricPair(GuiGraphics graphics, int y, Component left, Component right) {
        int rightWidth = this.font.width(right);
        drawLine(graphics, left, CONTENT_LEFT, y, CONTENT_RIGHT - rightWidth - 4, TEXT);
        drawRight(graphics, right, CONTENT_RIGHT, y, TEXT);
    }

    private void drawSmallHeader(GuiGraphics graphics, Component text, int y) {
        drawLine(graphics, text, CONTENT_LEFT, y, MUTED);
        graphics.fill(CONTENT_LEFT, y + HEADER_DIVIDER_Y, CONTENT_RIGHT, y + HEADER_DIVIDER_Y + 1, 0x5A7A442F);
    }

    private void drawWrapped(GuiGraphics graphics, Component text, int y) {
        int lineY = y;
        for (net.minecraft.util.FormattedCharSequence line : this.font.split(text, CONTENT_RIGHT - CONTENT_LEFT)) {
            graphics.drawString(this.font, line, CONTENT_LEFT, lineY, MUTED, false);
            lineY += WRAPPED_LINE_STEP;
            if (lineY > CONTENT_BOTTOM - 8) {
                return;
            }
        }
    }

    private int drawWrappedLines(GuiGraphics graphics, Component text, int x, int y, int color) {
        return drawWrappedLines(graphics, text, x, y, CONTENT_RIGHT, color);
    }

    private int drawWrappedLines(GuiGraphics graphics, Component text, int x, int y, int right, int color) {
        int lineY = y;
        for (net.minecraft.util.FormattedCharSequence line : this.font.split(text, right - x)) {
            graphics.drawString(this.font, line, x, lineY, color, false);
            lineY += WRAPPED_LINE_STEP;
            if (lineY > CONTENT_BOTTOM - 0) {
                break;
            }
        }
        return lineY;
    }

    private void drawLine(GuiGraphics graphics, Component text, int x, int y, int color) {
        drawLine(graphics, text, x, y, CONTENT_RIGHT, color);
    }

    private void drawLine(GuiGraphics graphics, Component text, int x, int y, int right, int color) {
        int width = right - x;
        if (width <= 0) {
            return;
        }
        graphics.drawString(this.font, fit(text.getString(), width), x, y, color, false);
    }

    private void drawRight(GuiGraphics graphics, Component text, int right, int y, int color) {
        String line = fit(text.getString(), right - CONTENT_LEFT);
        graphics.drawString(this.font, line, right - this.font.width(line), y, color, false);
    }

    private void drawCentered(GuiGraphics graphics, Component text, int y, int color) {
        int width = CONTENT_RIGHT - CONTENT_LEFT;
        String line = fit(text.getString(), width);
        graphics.drawString(this.font, line, CONTENT_LEFT + (width - this.font.width(line)) / 2, y, color, false);
    }

    private List<WorkerRow> workersForSelectedRole() {
        return this.snapshot.workers().stream()
                .filter(worker -> worker.role() == this.selectedRole)
                .sorted(Comparator.comparing(WorkerRow::displayName))
                .toList();
    }

    private int visibleWorkerRows() {
        return Math.max(1, (CONTENT_BOTTOM - (CONTENT_TOP + 12)) / WORKER_ROW_HEIGHT);
    }

    private int visibleWarningRows() {
        return Math.max(1, (CONTENT_BOTTOM - (CONTENT_TOP + 16)) / ROW_HEIGHT);
    }

    private void openOverview() {
        this.page = Page.OVERVIEW;
        this.workerScroll = 0;
        this.showOverviewSelection = false;
        this.selectedWorker = null;
    }

    private void openJob(HiredVillagerRole role) {
        if (role == null) {
            return;
        }
        this.selectedRole = role;
        this.page = Page.JOB;
        this.workerScroll = 0;
        this.showOverviewSelection = false;
        this.selectedWorker = null;
    }

    private void openJobSite(WorkerRow worker) {
        if (worker == null) {
            return;
        }
        this.selectedWorker = worker;
        this.selectedRole = worker.role();
        this.page = Page.JOB_SITE;
        this.showOverviewSelection = false;
    }

    private void navigateBack() {
        if (this.page == Page.JOB_SITE) {
            this.page = Page.JOB;
            this.selectedWorker = null;
            this.showOverviewSelection = false;
            return;
        }
        openOverview();
    }

    private void requestWorkAreaAction(WorkerRow worker, ClipboardWorkAreaActionPayload.Action action) {
        if (worker == null || action == null) {
            return;
        }
        PacketDistributor.sendToServer(new ClipboardWorkAreaActionPayload(worker.villagerId(), action, hasShiftDown() ? 5 : 1));
    }

    private void moveOverviewSelection(int direction) {
        if (this.page != Page.OVERVIEW) {
            return;
        }
        int rowCount = overviewRows().size();
        this.selectedOverviewRow = Mth.clamp(this.selectedOverviewRow + direction, 0, Math.max(0, rowCount - 1));
        this.showOverviewSelection = true;
    }

    private void activateOverviewSelection() {
        if (this.page != Page.OVERVIEW) {
            return;
        }
        List<OverviewRow> rows = overviewRows();
        if (this.selectedOverviewRow < 0 || this.selectedOverviewRow >= rows.size()) {
            return;
        }
        if (!this.showOverviewSelection) {
            this.showOverviewSelection = true;
            return;
        }
        OverviewRow row = rows.get(this.selectedOverviewRow);
        this.showOverviewSelection = false;
        switch (row.kind()) {
            case JOB -> openJob(row.role());
            case WARNINGS -> this.page = Page.WARNINGS;
            case STORAGE -> this.page = Page.STORAGE;
            case PAYMENT -> this.page = Page.PAYMENT;
            default -> {
            }
        }
    }

    private void turnOverviewPage() {
        this.overviewPage = this.overviewPage == 0 ? 1 : 0;
        this.selectedOverviewRow = 0;
        this.showOverviewSelection = false;
    }

    private List<OverviewRow> overviewRows() {
        List<OverviewRow> rows = new ArrayList<>();
        for (HiredVillagerRole role : overviewPageRoles()) {
            int count = jobCount(role);
            rows.add(new OverviewRow(
                    RowKind.JOB,
                    role,
                    roleName(role),
                    Integer.toString(count),
                    count == 0));
        }
        if (this.overviewPage == 1) {
            rows.add(new OverviewRow(
                    RowKind.WARNINGS,
                    null,
                    Component.translatable("villagerretaliation.gui.clipboard_workforce.warning_header"),
                    Integer.toString(this.snapshot.warningCount()),
                    this.snapshot.warningCount() == 0));
            rows.add(new OverviewRow(
                    RowKind.STORAGE,
                    null,
                    Component.translatable("villagerretaliation.gui.clipboard_workforce.storage"),
                    Integer.toString(this.snapshot.assignedStorageCount()),
                    false));
            rows.add(new OverviewRow(
                    RowKind.PAYMENT,
                    null,
                    Component.translatable("villagerretaliation.gui.clipboard_workforce.payment"),
                    Integer.toString(this.snapshot.paymentContainerCount()),
                    false));
        }
        return rows;
    }

    private List<HiredVillagerRole> overviewPageRoles() {
        return this.overviewPage == 0 ? FIRST_OVERVIEW_PAGE_ROLES : SECOND_OVERVIEW_PAGE_ROLES;
    }

    private int jobCount(HiredVillagerRole role) {
        for (ClipboardWorkforceSnapshot.JobSummary job : this.snapshot.jobs()) {
            if (job.role() == role) {
                return job.count();
            }
        }
        return 0;
    }

    private Component roleName(HiredVillagerRole role) {
        return Component.translatable("villagerretaliation.gui.clipboard_workforce.role." + role.serializedName());
    }

    private Component statusName(ClipboardWorkforceSnapshot.WorkerStatus status) {
        return Component.translatable("villagerretaliation.gui.clipboard_workforce.status." + status.name().toLowerCase(java.util.Locale.ROOT));
    }

    private Component warningText(WarningSummary warning) {
        String key = "villagerretaliation.gui.clipboard_workforce.warning." + warning.type().name().toLowerCase(java.util.Locale.ROOT);
        return Component.translatable(key, warning.count(), roleName(warning.role()));
    }

    private int warningColor(int count) {
        return count > 0 ? WARNING : TEXT;
    }

    private int mutedForWarning(WorkerRow worker) {
        return hasWarning(worker) ? WARNING : MUTED;
    }

    private boolean hasWarning(WorkerRow worker) {
        return worker.inventoryFull()
                || worker.unpaid()
                || worker.noStorage()
                || worker.noWorkArea()
                || worker.noTargets()
                || worker.tooFar()
                || worker.missingTools()
                || worker.status() == ClipboardWorkforceSnapshot.WorkerStatus.STORAGE_FULL
                || worker.status() == ClipboardWorkforceSnapshot.WorkerStatus.MISSING_MATERIALS
                || worker.status() == ClipboardWorkforceSnapshot.WorkerStatus.MATERIAL_STORAGE_UNREACHABLE
                || worker.status() == ClipboardWorkforceSnapshot.WorkerStatus.MATERIAL_INVENTORY_FULL
                || worker.status() == ClipboardWorkforceSnapshot.WorkerStatus.BUILD_SITE_UNREACHABLE;
    }

    private int warningTextRight() {
        return CONTENT_RIGHT - WARNING_ARROW_GAP;
    }

    private int warningRowHeight(Component text) {
        int width = warningTextRight() - CONTENT_LEFT;
        int lines = Math.max(1, this.font.split(text, width).size());
        return Math.max(ROW_HEIGHT, lines * WRAPPED_LINE_STEP + 1);
    }

    private String fit(String text, int width) {
        if (this.font.width(text) <= width) {
            return text;
        }
        return this.font.plainSubstrByWidth(text, Math.max(0, width - this.font.width("..."))) + "...";
    }

    private void playPageSound() {
        playBookSound(0.65F);
    }

    private void playBookSound(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F, pitch));
    }

    private boolean contains(double x, double y, int left, int top, int right, int bottom) {
        return x >= left && x <= right && y >= top && y <= bottom;
    }

    private void renderHoveredTooltip(GuiGraphics graphics, int mouseX, int mouseY, double panelMouseX, double panelMouseY) {
        ClipboardPreviewTab tab = hoveredPreviewTab(panelMouseX, panelMouseY);
        if (tab != null) {
            graphics.renderComponentTooltip(this.font, tab.tooltip(), mouseX, mouseY);
            return;
        }
        for (RowAction row : this.rowActions) {
            if (row.tooltip().isEmpty() || !row.contains(panelMouseX, panelMouseY)) {
                continue;
            }
            graphics.renderComponentTooltip(this.font, row.tooltip(), mouseX, mouseY);
            return;
        }
    }

    private static ClipboardPreviewTab hoveredPreviewTab(double mouseX, double mouseY) {
        for (ClipboardPreviewTab tab : ClipboardPreviewTab.values()) {
            if (isClipboardTabHovered(mouseX, mouseY, tab.top())) {
                return tab;
            }
        }
        return null;
    }

    private boolean isJobSitePage() {
        return this.page == Page.JOB_SITE;
    }

    private float panelScale() {
        float fitWidth = (this.width - 12.0F) / TEXTURE_WIDTH;
        float fitHeight = (this.height - 12.0F) / TEXTURE_HEIGHT;
        return Mth.clamp(Math.min(fitWidth, fitHeight), 0.72F, 1.35F);
    }

    private int panelLeft(float scale) {
        float panelWidth = TEXTURE_WIDTH * scale;
        if (isJobSitePage()) {
            float toolCenter = this.width * 0.25F;
            return Math.round(Mth.clamp(toolCenter - panelWidth / 2.0F, 6.0F, Math.max(6.0F, this.width - panelWidth - 6.0F)));
        }
        return Math.round((this.width - panelWidth) / 2.0F);
    }

    private int panelTop(float scale) {
        return Math.round((this.height - TEXTURE_HEIGHT * scale) / 2.0F);
    }

    private int slideOffsetY(float scale) {
        float visibility = clipboardVisibility();
        int offscreenDistance = this.height - panelTop(scale) + 12;
        return Math.round((1.0F - visibility) * offscreenDistance);
    }

    private float clipboardVisibility() {
        float progress = Mth.clamp(animationElapsedMillis() / CLIPBOARD_ANIMATION_DURATION_MILLIS, 0.0F, 1.0F);
        return this.closingWithAnimation ? 1.0F - easeInCubic(progress) : easeOutCubic(progress);
    }

    private float animationElapsedMillis() {
        if (this.animationStartMillis < 0L) {
            return CLIPBOARD_ANIMATION_DURATION_MILLIS;
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

    private void closeClipboard() {
        if (this.closingWithAnimation) {
            return;
        }
        this.closingWithAnimation = true;
        this.animationStartMillis = Util.getMillis();
        playBookSound(0.72F);
    }

    private void loadHoverMasks() {
        ResourceManager resources = Minecraft.getInstance().getResourceManager();
        this.mainPageHoverMask = AlphaMask.load(
                resources,
                VillagerRetaliationClientAssets.CLIPBOARD_CONTAINER_MAIN_PAGE_TEXTURE,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT,
                false);
        this.homeTabHoverMask = AlphaMask.load(
                resources,
                VillagerRetaliationClientAssets.CLIPBOARD_CONTAINER_HOME_TAB_TEXTURE,
                HOME_TAB_WIDTH,
                HOME_TAB_HEIGHT,
                true);
        this.containerTab1HoverMask = AlphaMask.load(
                resources,
                VillagerRetaliationClientAssets.CLIPBOARD_CONTAINER_TAB_1_TEXTURE,
                CONTAINER_TAB_1_WIDTH,
                CONTAINER_TAB_1_HEIGHT,
                true);
        this.containerTab2HoverMask = AlphaMask.load(
                resources,
                VillagerRetaliationClientAssets.CLIPBOARD_CONTAINER_TAB_2_TEXTURE,
                CONTAINER_TAB_2_WIDTH,
                CONTAINER_TAB_2_HEIGHT,
                true);
        this.containerTab3HoverMask = AlphaMask.load(
                resources,
                VillagerRetaliationClientAssets.CLIPBOARD_CONTAINER_TAB_3_TEXTURE,
                CONTAINER_TAB_3_WIDTH,
                CONTAINER_TAB_3_HEIGHT,
                true);
        this.leftPageTabHoverMask = AlphaMask.load(
                resources,
                VillagerRetaliationClientAssets.CLIPBOARD_CONTAINER_LEFT_TAB_TEXTURE,
                PAGE_SIDE_TAB_WIDTH,
                PAGE_SIDE_TAB_HEIGHT,
                true);
        this.rightPageTabHoverMask = AlphaMask.load(
                resources,
                VillagerRetaliationClientAssets.CLIPBOARD_CONTAINER_RIGHT_TAB_TEXTURE,
                PAGE_SIDE_TAB_WIDTH,
                PAGE_SIDE_TAB_HEIGHT,
                true);
        this.scrollDownTabMask = AlphaMask.load(
                resources,
                VillagerRetaliationClientAssets.CLIPBOARD_CONTAINER_SCROLL_DOWN_TAB_TEXTURE,
                SCROLL_TAB_WIDTH,
                SCROLL_TAB_HEIGHT,
                true);
        this.scrollUpTabMask = AlphaMask.load(
                resources,
                VillagerRetaliationClientAssets.CLIPBOARD_CONTAINER_SCROLL_UP_TAB_TEXTURE,
                SCROLL_TAB_WIDTH,
                SCROLL_TAB_HEIGHT,
                true);
    }

    private record AlphaMask(int width, int height, boolean[] pixels) {
        private boolean hasAlpha(int x, int y) {
            return x >= 0 && x < this.width && y >= 0 && y < this.height && this.pixels[x + y * this.width];
        }

        private static AlphaMask load(
                ResourceManager resources,
                ResourceLocation texture,
                int expectedWidth,
                int expectedHeight,
                boolean opaqueFallback) {
            var resource = resources.getResource(texture);
            if (resource.isEmpty()) {
                return opaqueFallback ? full(expectedWidth, expectedHeight) : empty(expectedWidth, expectedHeight);
            }
            try (var input = resource.get().open(); NativeImage image = NativeImage.read(input)) {
                boolean[] pixels = new boolean[expectedWidth * expectedHeight];
                int width = Math.min(expectedWidth, image.getWidth());
                int height = Math.min(expectedHeight, image.getHeight());
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        pixels[x + y * expectedWidth] = (image.getPixelRGBA(x, y) >>> 24) != 0;
                    }
                }
                return new AlphaMask(expectedWidth, expectedHeight, pixels);
            } catch (java.io.IOException | RuntimeException exception) {
                return opaqueFallback ? full(expectedWidth, expectedHeight) : empty(expectedWidth, expectedHeight);
            }
        }

        private static AlphaMask full(int width, int height) {
            boolean[] pixels = new boolean[width * height];
            java.util.Arrays.fill(pixels, true);
            return new AlphaMask(width, height, pixels);
        }

        private static AlphaMask empty(int width, int height) {
            return new AlphaMask(width, height, new boolean[width * height]);
        }
    }

    private static final class TabSlideAnimation {
        private float startVisibility;
        private float targetVisibility;
        private long startMillis;

        private void reset(boolean visible) {
            this.startVisibility = 0.0F;
            this.targetVisibility = visible ? 1.0F : 0.0F;
            this.startMillis = Util.getMillis();
        }

        private void setVisible(boolean visible) {
            float target = visible ? 1.0F : 0.0F;
            if (this.targetVisibility == target) {
                return;
            }
            this.startVisibility = visibility();
            this.targetVisibility = target;
            this.startMillis = Util.getMillis();
        }

        private float visibility() {
            float progress = Mth.clamp(
                    (Util.getMillis() - this.startMillis) / SCROLL_TAB_ANIMATION_DURATION_MILLIS,
                    0.0F,
                    1.0F);
            float eased = progress * progress * (3.0F - 2.0F * progress);
            return Mth.lerp(eased, this.startVisibility, this.targetVisibility);
        }
    }

    private enum Page {
        OVERVIEW,
        JOB,
        JOB_SITE,
        WARNINGS,
        STORAGE,
        PAYMENT
    }

    private enum RowKind {
        BACK,
        PAGE_TURN,
        JOB,
        WARNINGS,
        STORAGE,
        PAYMENT,
        WORKER,
        JOB_SITE_ACTION
    }

    private enum ClipboardPreviewTab {
        WORK_AREAS(
                TAB_1_TOP,
                "villagerretaliation.gui.clipboard_workforce.preview_tab.work_areas",
                "villagerretaliation.gui.clipboard_workforce.preview_tab.work_areas.detail",
                0xFFFF4A3F) {
            @Override
            void toggle() {
                ClipboardStorageOutlineRenderer.toggleNearbyWorkAreaPreviews();
            }
        },
        STORAGE(
                TAB_2_TOP,
                "villagerretaliation.gui.clipboard_workforce.preview_tab.storage",
                "villagerretaliation.gui.clipboard_workforce.preview_tab.storage.detail",
                0xFFFFD54A) {
            @Override
            void toggle() {
                ClipboardStorageOutlineRenderer.toggleNearbyStoragePreviews();
            }
        },
        PAYMENT(
                TAB_3_TOP,
                "villagerretaliation.gui.clipboard_workforce.preview_tab.payment",
                "villagerretaliation.gui.clipboard_workforce.preview_tab.payment.detail",
                0xFF3FA7FF) {
            @Override
            void toggle() {
                ClipboardStorageOutlineRenderer.toggleNearbyPaymentPreviews();
            }
        };

        private final int top;
        private final String titleKey;
        private final String detailKey;
        private final int titleColor;

        ClipboardPreviewTab(int top, String titleKey, String detailKey, int titleColor) {
            this.top = top;
            this.titleKey = titleKey;
            this.detailKey = detailKey;
            this.titleColor = titleColor;
        }

        int top() {
            return this.top;
        }

        List<Component> tooltip() {
            return List.of(
                    Component.translatable(this.titleKey).withColor(this.titleColor),
                    Component.translatable(this.detailKey).withStyle(ChatFormatting.GRAY));
        }

        abstract void toggle();
    }

    private record OverviewRow(RowKind kind, HiredVillagerRole role, Component label, String value, boolean muted) {
    }

    private record JobListRow(Component label, int count) {
    }

    private record JobSiteButton(String label, ClipboardWorkAreaActionPayload.Action action) {
    }

    private record RowAction(
            RowKind kind,
            HiredVillagerRole role,
            WorkerRow worker,
            ClipboardWorkAreaActionPayload.Action workAreaAction,
            List<Component> tooltip,
            int left,
            int top,
            int right,
            int bottom) {
        private RowAction(RowKind kind, HiredVillagerRole role, int left, int top, int right, int bottom) {
            this(kind, role, null, null, List.of(), left, top, right, bottom);
        }

        private static RowAction worker(WorkerRow worker, int left, int top, int right, int bottom) {
            return new RowAction(RowKind.WORKER, null, worker, null, List.of(), left, top, right, bottom);
        }

        private static RowAction jobSiteAction(
                WorkerRow worker,
                ClipboardWorkAreaActionPayload.Action action,
                int left,
                int top,
                int right,
                int bottom) {
            return jobSiteAction(worker, action, List.of(), left, top, right, bottom);
        }

        private static RowAction jobSiteAction(
                WorkerRow worker,
                ClipboardWorkAreaActionPayload.Action action,
                List<Component> tooltip,
                int left,
                int top,
                int right,
                int bottom) {
            return new RowAction(RowKind.JOB_SITE_ACTION, null, worker, action, tooltip, left, top, right, bottom);
        }

        private boolean contains(double x, double y) {
            return x >= this.left && x <= this.right && y >= this.top && y <= this.bottom;
        }
    }
}
