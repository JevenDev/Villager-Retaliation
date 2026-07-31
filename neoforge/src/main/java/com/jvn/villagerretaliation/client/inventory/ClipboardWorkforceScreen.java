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
import com.jvn.villagerretaliation.network.ClipboardWorkforceSubscriptionPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class ClipboardWorkforceScreen extends Screen {
    private static final float CLIPBOARD_ANIMATION_DURATION_MILLIS = 280.0F;
    private static final float SCROLL_TAB_ANIMATION_DURATION_MILLIS = 180.0F;
    private static final float NUMBERED_TAB_ANIMATION_DURATION_MILLIS = 110.0F;
    private static final int SCROLL_TAB_START_INSET = 15;
    private static final int NAVIGATION_TAB_INSET = 15;
    private static final int HOME_NAVIGATION_TAB_INSET = NAVIGATION_TAB_INSET - 8;
    private static final int LEFT_NAVIGATION_TAB_INSET = NAVIGATION_TAB_INSET - 3;
    private static final float NAVIGATION_TAB_PEEK = 1.0F / 3.0F;
    private static final float HOME_NAVIGATION_TAB_PEEK = 5.0F / HOME_NAVIGATION_TAB_INSET;
    private static final float LEFT_NAVIGATION_TAB_PEEK = 5.0F / LEFT_NAVIGATION_TAB_INSET;
    private static final int NUMBERED_TAB_HOVER_EXTRUDE = 10;
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
    private static final int ROW_ARROW_WIDTH = 7;
    private static final int ROW_ARROW_HEIGHT = 7;
    private static final int ASSIGNMENT_CHECKMARK_WIDTH = 7;
    private static final int ASSIGNMENT_CHECKMARK_HEIGHT = 7;
    private static final int ROW_ARROW_TEXT_GAP = 2;
    private static final int ROW_ARROW_TEXT_OFFSET = ROW_ARROW_WIDTH + ROW_ARROW_TEXT_GAP;
    private static final int TEXT_CONTENT_TOP = 34;
    private static final int TEXT_CONTENT_BOTTOM = TEXTURE_HEIGHT - 20;
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
    private static final int JOB_DETAIL_WORKER_TOP = SUMMARY_TOP;
    private static final int JOB_DETAIL_INDENT = 8;
    private static final int JOB_DETAIL_BAND_LEFT = 10;
    private static final int JOB_DETAIL_BAND_RIGHT = TEXTURE_WIDTH - 9;
    private static final int JOB_DETAIL_BAND_COLOR = 0x18906F49;
    private static final int CONTAINER_TAB_RIGHT = 25;
    private static final int CONTAINER_TAB_1_WIDTH = 36;
    private static final int CONTAINER_TAB_1_HEIGHT = 46;
    private static final int CONTAINER_TAB_1_TOP = 31;
    private static final int CONTAINER_TAB_2_WIDTH = 33;
    private static final int CONTAINER_TAB_2_HEIGHT = 43;
    private static final int CONTAINER_TAB_2_TOP = 64;
    private static final int CONTAINER_TAB_3_WIDTH = 31;
    private static final int CONTAINER_TAB_3_HEIGHT = 32;
    private static final int CONTAINER_TAB_3_TOP = 96;
    private static final int CONTENT_RIGHT = 149;
    private static final int CONTENT_BOTTOM = 184;
    private static final int TEXT = 0xFF4B2B1D;
    private static final int MUTED = 0xFF8B6247;
    private static final int NOTICE = 0xFF707070;
    private static final int WARNING = 0xFF9A3B24;
    private static final int SUCCESS = 0xFF2E7135;
    private static final int IDLE = 0xFF936400;
    private static final int PATHING = 0xFFB44B0B;
    private static final int HOVER_FILL = 0x30A66A34;
    private static final int SELECTED_FILL = 0x3DA65C2B;
    private static final int JOB_SITE_ROW_STEP = 10;
    private static final int WRAPPED_LINE_STEP = 9;

    private ClipboardWorkforceSnapshot snapshot;
    private final List<RowAction> rowActions = new ArrayList<>();
    private Page page = Page.OVERVIEW;
    private HiredVillagerRole selectedRole = HiredVillagerRole.MINING;
    private WarningSummary selectedWarning;
    private WorkerRow selectedWorker;
    private int jobScroll;
    private int warningScroll;
    private int assignmentTrackingScroll;
    private int renderPanelLeft;
    private int renderPanelTop;
    private float renderPanelScale = 1.0F;
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
    private final TabSlideAnimation homeTabAnimation = new TabSlideAnimation();
    private final TabSlideAnimation leftPageTabAnimation = new TabSlideAnimation();
    private final TabSlideAnimation rightPageTabAnimation = new TabSlideAnimation();
    private final TabSlideAnimation numberedTab1Animation = new TabSlideAnimation(NUMBERED_TAB_ANIMATION_DURATION_MILLIS);
    private final TabSlideAnimation numberedTab2Animation = new TabSlideAnimation(NUMBERED_TAB_ANIMATION_DURATION_MILLIS);
    private final TabSlideAnimation numberedTab3Animation = new TabSlideAnimation(NUMBERED_TAB_ANIMATION_DURATION_MILLIS);
    private ClipboardPreviewTab activePreviewTab;
    private int suppressedTabHover;
    private final Set<HiredVillagerRole> trackedAssignmentRoles = new LinkedHashSet<>();
    private String lastPreviewStateKey = "";

    public ClipboardWorkforceScreen(ClipboardWorkforceSnapshot snapshot) {
        super(Component.translatable("villagerretaliation.gui.clipboard_workforce.title"));
        this.snapshot = snapshot == null ? ClipboardWorkforceSnapshot.empty() : snapshot;
    }

    /** Applies a live server snapshot without resetting the player's current page or dashboard selections. */
    public void updateSnapshot(ClipboardWorkforceSnapshot snapshot) {
        this.snapshot = snapshot == null ? ClipboardWorkforceSnapshot.empty() : snapshot;
        if (this.selectedWorker != null) {
            UUID selectedWorkerId = this.selectedWorker.villagerId();
            this.selectedWorker = this.snapshot.workers().stream()
                    .filter(worker -> worker.villagerId().equals(selectedWorkerId))
                    .findFirst()
                    .orElse(null);
        }
        if (this.selectedWarning != null) {
            WarningSummary previousWarning = this.selectedWarning;
            this.selectedWarning = this.snapshot.warnings().stream()
                    .filter(warning -> warning.type() == previousWarning.type() && warning.role() == previousWarning.role())
                    .findFirst()
                    .orElse(null);
        }
        this.jobScroll = Mth.clamp(this.jobScroll, 0, maxJobScroll());
        this.warningScroll = Mth.clamp(this.warningScroll, 0, maxWarningPageScroll());
        this.assignmentTrackingScroll = Mth.clamp(
                this.assignmentTrackingScroll, 0, maxAssignmentTrackingScroll());
        this.lastPreviewStateKey = "";
    }

    @Override
    protected void init() {
        this.closingWithAnimation = false;
        this.animationStartMillis = Util.getMillis();
        loadHoverMasks();
        this.scrollDownTabAnimation.reset(canScrollJobsDown());
        this.scrollUpTabAnimation.reset(canScrollJobsUp());
        this.homeTabAnimation.reset(false);
        this.leftPageTabAnimation.reset(false);
        this.rightPageTabAnimation.reset(false);
        this.activePreviewTab = ClipboardPreviewTab.fromLens(
                ClipboardStorageOutlineRenderer.clipboardPreviewLens());
        this.trackedAssignmentRoles.clear();
        Set<String> persistedTrackedJobs = ClipboardStorageOutlineRenderer.clipboardTrackedJobs();
        for (HiredVillagerRole role : HiredVillagerRole.values()) {
            if (persistedTrackedJobs.stream().anyMatch(job -> job.equalsIgnoreCase(role.label()))) {
                this.trackedAssignmentRoles.add(role);
            }
        }
        if (this.activePreviewTab == ClipboardPreviewTab.ASSIGNMENTS && this.trackedAssignmentRoles.isEmpty()) {
            this.activePreviewTab = null;
        }
        this.lastPreviewStateKey = "";
        this.suppressedTabHover = 0;
        this.numberedTab1Animation.reset(this.activePreviewTab == ClipboardPreviewTab.WORKFORCE);
        this.numberedTab2Animation.reset(this.activePreviewTab == ClipboardPreviewTab.ASSIGNMENTS);
        this.numberedTab3Animation.reset(this.activePreviewTab == ClipboardPreviewTab.PROBLEMS);
        syncActivePreview();
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
        if (!isJobSitePage()) {
            renderBackground(graphics, mouseX, mouseY, partialTick);
        }
        this.rowActions.clear();
        float scale = panelScale();
        int left = panelLeft(scale);
        int top = panelTop(scale) + slideOffsetY(scale);
        this.renderPanelLeft = left;
        this.renderPanelTop = top;
        this.renderPanelScale = scale;
        double panelMouseX = (mouseX - left) / scale;
        double panelMouseY = (mouseY - top) / scale;

        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        renderClipboard(graphics, panelMouseX, panelMouseY);
        graphics.pose().popPose();
        syncActivePreview();
        renderScrollTabTooltip(graphics, mouseX, mouseY, panelMouseX, panelMouseY);
        renderPreviewTabTooltip(graphics, mouseX, mouseY, panelMouseX, panelMouseY);
        renderJobDetailTooltip(graphics, mouseX, mouseY, panelMouseX, panelMouseY);
        renderJobSiteTooltip(graphics, mouseX, mouseY, panelMouseX, panelMouseY);
    }

    private void renderClipboard(GuiGraphics graphics, double mouseX, double mouseY) {
        updateSuppressedTabHover(mouseX, mouseY);
        syncNavigationTabAnimations(mouseX, mouseY);
        int hoveredTab = hoveredContainerTab(mouseX, mouseY);
        syncNumberedTabAnimations(hoveredTab);
        syncScrollTabAnimations();
        float scrollDownVisibility = this.scrollDownTabAnimation.visibility();
        float scrollUpVisibility = this.scrollUpTabAnimation.visibility();
        float homeVisibility = this.homeTabAnimation.visibility();
        float leftVisibility = this.leftPageTabAnimation.visibility();
        float rightVisibility = this.rightPageTabAnimation.visibility();
        boolean navigationHighlights = this.page != Page.OVERVIEW;
        boolean canTurnLeft = canTurnWorkerPage(-1);
        boolean canTurnRight = canTurnWorkerPage(1);
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
                navigationHighlights && hoveredTab == 4
                        ? VillagerRetaliationClientAssets.CLIPBOARD_CONTAINER_HOME_TAB_HIGHLIGHT_TEXTURE
                        : VillagerRetaliationClientAssets.CLIPBOARD_CONTAINER_HOME_TAB_TEXTURE,
                HOME_TAB_LEFT,
                homeTabTop(homeVisibility),
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
                navigationHighlights && canTurnLeft && hoveredTab == 5
                        ? VillagerRetaliationClientAssets.CLIPBOARD_CONTAINER_LEFT_TAB_HIGHLIGHT_TEXTURE
                        : VillagerRetaliationClientAssets.CLIPBOARD_CONTAINER_LEFT_TAB_TEXTURE,
                leftPageTabLeft(leftVisibility),
                PAGE_SIDE_TAB_TOP,
                0.0F,
                0.0F,
                PAGE_SIDE_TAB_WIDTH,
                PAGE_SIDE_TAB_HEIGHT,
                PAGE_SIDE_TAB_WIDTH,
                PAGE_SIDE_TAB_HEIGHT);
        graphics.blit(
                navigationHighlights && canTurnRight && hoveredTab == 6
                        ? VillagerRetaliationClientAssets.CLIPBOARD_CONTAINER_RIGHT_TAB_HIGHLIGHT_TEXTURE
                        : VillagerRetaliationClientAssets.CLIPBOARD_CONTAINER_RIGHT_TAB_TEXTURE,
                rightPageTabLeft(rightVisibility),
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
        switch (this.page) {
            case JOB -> renderJobDetailPage(graphics, mouseX, mouseY);
            case JOB_SITE -> renderJobSitePage(graphics, mouseX, mouseY);
            case ROUTE -> renderRoutePage(graphics);
            case WARNINGS -> renderWarningDetailPage(graphics, mouseX, mouseY);
            case WARNING_WORKERS -> renderWarningWorkersPage(graphics, mouseX, mouseY);
            case WORKER_ERRORS -> renderWorkerErrorsPage(graphics);
            case ASSIGNMENT_TRACKER -> renderAssignmentTrackerPage(graphics, mouseX, mouseY);
            default -> renderClipboardHeading(graphics, mouseX, mouseY);
        }
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

    private void renderPreviewTabTooltip(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            double panelMouseX,
            double panelMouseY) {
        if (this.closingWithAnimation) {
            return;
        }
        ClipboardPreviewTab tab = ClipboardPreviewTab.byTabNumber(hoveredContainerTab(panelMouseX, panelMouseY));
        if (tab == null) {
            return;
        }
        PreviewScope scope = previewScope(tab.lens());
        graphics.renderComponentTooltip(
                this.font,
                tab.tooltip(scope, this.activePreviewTab == tab),
                mouseX,
                mouseY);
    }

    private void renderJobDetailTooltip(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            double panelMouseX,
            double panelMouseY) {
        if (this.closingWithAnimation || this.page != Page.JOB) {
            return;
        }
        DetailTooltip tooltip = detailTooltipAt(panelMouseX, panelMouseY);
        if (tooltip == null) {
            return;
        }
        graphics.renderComponentTooltip(
                this.font,
                List.of(
                        Component.translatable(tooltip.titleKey()).withStyle(ChatFormatting.GREEN),
                        Component.translatable(tooltip.detailKey())
                                .withStyle(ChatFormatting.GRAY)),
                mouseX,
                mouseY);
    }

    private void renderJobSiteTooltip(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            double panelMouseX,
            double panelMouseY) {
        if (this.closingWithAnimation || this.page != Page.JOB_SITE) {
            return;
        }
        for (RowAction row : this.rowActions) {
            if (!row.tooltip().isEmpty() && row.contains(panelMouseX, panelMouseY)) {
                graphics.renderComponentTooltip(this.font, row.tooltip(), mouseX, mouseY);
                return;
            }
        }
    }

    private void renderClipboardHeading(GuiGraphics graphics, double mouseX, double mouseY) {
        Component title = Component.translatable("villagerretaliation.gui.clipboard_workforce.title");
        int titleLeft = (TEXTURE_WIDTH - this.font.width(title)) / 2;
        graphics.drawString(this.font, title, titleLeft, WORKFORCE_TITLE_TOP, TEXT, false);
        renderCenteredDivider(graphics, TITLE_DIVIDER_TOP);
        renderWorkforceSummary(graphics, SUMMARY_TOP, mouseX, mouseY);
    }

    private void renderWorkforceSummary(GuiGraphics graphics, int top, double mouseX, double mouseY) {
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

        if (mouseX >= JOB_DETAIL_BAND_LEFT
                && mouseX < JOB_DETAIL_BAND_RIGHT
                && mouseY >= WARNINGS_TOP - 2
                && mouseY < WARNINGS_TOP + TEXT_PIXEL_HEIGHT + 2) {
            graphics.fill(
                    JOB_DETAIL_BAND_LEFT,
                    WARNINGS_TOP - 2,
                    JOB_DETAIL_BAND_RIGHT,
                    WARNINGS_TOP + TEXT_PIXEL_HEIGHT + 2,
                    JOB_DETAIL_BAND_COLOR);
        }
        drawArrowLine(
                graphics,
                hasActionableWarnings(),
                workforceStatusSummary("villagerretaliation.gui.clipboard_workforce.summary"),
                SUMMARY_LEFT,
                WARNINGS_TOP,
                workforceStatusColor());
        renderCenteredDivider(graphics, JOBS_DIVIDER_TOP);
        renderJobsHeading(graphics, JOBS_TOP);
        renderCenteredDivider(graphics, JOB_LIST_DIVIDER_TOP);
        renderScrollableJobContent(graphics, mouseX, mouseY);
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

    private void renderScrollableJobContent(GuiGraphics graphics, double mouseX, double mouseY) {
        this.jobScroll = Mth.clamp(this.jobScroll, 0, maxJobScroll());
        int scrollOffset = jobScrollOffset();
        enableJobScrollScissor(graphics);
        int descriptionTop = JOB_DESCRIPTION_TOP - scrollOffset;
        int lineTop = descriptionTop;
        for (net.minecraft.util.FormattedCharSequence line : jobDescriptionLines()) {
            if (intersectsJobScrollViewport(lineTop)) {
                graphics.drawString(this.font, line, SUMMARY_LEFT, lineTop, TEXT, false);
            }
            lineTop += this.font.lineHeight;
        }

        List<JobListRow> rows = sortedJobRows();
        int rowTop = jobListTop() - scrollOffset;
        for (JobListRow row : rows) {
            if (!isFullyVisibleJobRow(rowTop)) {
                rowTop += SUMMARY_ROW_STEP;
                continue;
            }
            if (mouseX >= JOB_DETAIL_BAND_LEFT
                    && mouseX < JOB_DETAIL_BAND_RIGHT
                    && mouseY >= rowTop - 2
                    && mouseY < rowTop + TEXT_PIXEL_HEIGHT + 2) {
                graphics.fill(
                        JOB_DETAIL_BAND_LEFT,
                        rowTop - 2,
                        JOB_DETAIL_BAND_RIGHT,
                        rowTop + TEXT_PIXEL_HEIGHT + 2,
                        JOB_DETAIL_BAND_COLOR);
            }
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
        graphics.disableScissor();
    }

    private void enableJobScrollScissor(GuiGraphics graphics) {
        int left = Mth.floor(this.renderPanelLeft + JOB_DETAIL_BAND_LEFT * this.renderPanelScale);
        int top = Mth.floor(this.renderPanelTop + JOB_DESCRIPTION_TOP * this.renderPanelScale);
        int right = Mth.ceil(this.renderPanelLeft + JOB_DETAIL_BAND_RIGHT * this.renderPanelScale);
        int bottom = Mth.ceil(this.renderPanelTop + TEXT_CONTENT_BOTTOM * this.renderPanelScale);
        graphics.enableScissor(left, top, right, bottom);
    }

    private static boolean intersectsJobScrollViewport(int top) {
        return top + TEXT_PIXEL_HEIGHT > JOB_DESCRIPTION_TOP
                && top + TEXT_PIXEL_HEIGHT <= TEXT_CONTENT_BOTTOM;
    }

    private static boolean isFullyVisibleJobRow(int top) {
        return top >= JOB_DESCRIPTION_TOP
                && top + TEXT_PIXEL_HEIGHT <= TEXT_CONTENT_BOTTOM;
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

    private int jobScrollOffset() {
        int firstJobOffset = Math.max(0, jobListTop() - JOB_DESCRIPTION_TOP);
        int descriptionScrollStep = Math.max(1, this.font.lineHeight);
        int firstJobScroll = Math.max(1, firstJobOffset / descriptionScrollStep);
        if (this.jobScroll < firstJobScroll) {
            return this.jobScroll * descriptionScrollStep;
        }
        return firstJobOffset + (this.jobScroll - firstJobScroll) * SUMMARY_ROW_STEP;
    }

    private static int ceilDiv(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }

    private List<JobListRow> sortedJobRows() {
        List<JobListRow> rows = new ArrayList<>();
        for (HiredVillagerRole role : HiredVillagerRole.values()) {
            rows.add(new JobListRow(role, roleName(role), jobCount(role)));
        }
        rows.sort(Comparator.comparingInt(JobListRow::count)
                .reversed()
                .thenComparing(row -> row.label().getString(), String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private int maxJobScroll() {
        if (this.page != Page.OVERVIEW) {
            return 0;
        }
        int rowCount = sortedJobRows().size();
        if (rowCount == 0) {
            return 0;
        }
        int contentBottom = jobListTop() + (rowCount - 1) * SUMMARY_ROW_STEP + TEXT_PIXEL_HEIGHT;
        int overflow = Math.max(0, contentBottom - TEXT_CONTENT_BOTTOM);
        int firstJobOffset = Math.max(0, jobListTop() - JOB_DESCRIPTION_TOP);
        int descriptionScrollStep = Math.max(1, this.font.lineHeight);
        int firstJobScroll = Math.max(1, firstJobOffset / descriptionScrollStep);
        if (overflow < firstJobOffset) {
            return Math.min(firstJobScroll, ceilDiv(overflow, descriptionScrollStep));
        }
        return firstJobScroll
                + ceilDiv(overflow - firstJobOffset, SUMMARY_ROW_STEP);
    }

    private void renderJobDetailPage(GuiGraphics graphics, double mouseX, double mouseY) {
        Component title = Component.translatable(
                "villagerretaliation.gui.clipboard_workforce.job_detail.title",
                roleName(this.selectedRole));
        graphics.drawString(
                this.font,
                title,
                (TEXTURE_WIDTH - this.font.width(title)) / 2,
                WORKFORCE_TITLE_TOP,
                TEXT,
                false);
        renderCenteredDivider(graphics, TITLE_DIVIDER_TOP);

        WorkerRow worker = selectedRoleWorker();
        if (worker == null) {
            graphics.drawString(
                    this.font,
                    Component.translatable("villagerretaliation.gui.clipboard_workforce.no_workers"),
                    SUMMARY_LEFT,
                    JOB_DETAIL_WORKER_TOP,
                    MUTED,
                    false);
            return;
        }
        this.selectedWorker = worker;

        int y = JOB_DETAIL_WORKER_TOP;
        if (jobDetailWorkerRowHovered(mouseX, mouseY)) {
            graphics.fill(
                    JOB_DETAIL_BAND_LEFT,
                    y - 2,
                    JOB_DETAIL_BAND_RIGHT,
                    y + TEXT_PIXEL_HEIGHT + 2,
                    HOVER_FILL);
        }
        drawArrowLine(
                graphics,
                false,
                Component.translatable(
                        "villagerretaliation.gui.clipboard_workforce.job_detail.worker",
                        worker.displayName()),
                SUMMARY_LEFT,
                y,
                TEXT);
        y += SUMMARY_ROW_STEP;
        Component workerStatus = statusName(worker.status()).copy().withColor(statusColor(worker));
        y = drawDetailLine(graphics, Component.translatable(
                "villagerretaliation.gui.clipboard_workforce.job_detail.status",
                workerStatus), y, true, 0, TEXT);
        y = drawDetailLine(graphics, Component.translatable(
                "villagerretaliation.gui.clipboard_workforce.job_detail.location"), y, false, 0);

        var location = worker.location();
        y = drawDetailLine(graphics, Component.translatable(
                "villagerretaliation.gui.clipboard_workforce.job_detail.location_x",
                location.position().getX()), y, false, JOB_DETAIL_INDENT);
        y = drawDetailLine(graphics, Component.translatable(
                "villagerretaliation.gui.clipboard_workforce.job_detail.location_y",
                location.position().getY()), y, false, JOB_DETAIL_INDENT);
        y = drawDetailLine(graphics, Component.translatable(
                "villagerretaliation.gui.clipboard_workforce.job_detail.location_z",
                location.position().getZ()), y, false, JOB_DETAIL_INDENT);
        Component dimension = Component.translatable(
                "dimension." + location.dimension().getNamespace() + "." + location.dimension().getPath());
        y = drawDetailLine(graphics, Component.translatable(
                "villagerretaliation.gui.clipboard_workforce.job_detail.dimension",
                dimension), y, false, JOB_DETAIL_INDENT);

        Component contractDuration = Component.translatable(
                worker.contractDays() == 1
                        ? "villagerretaliation.gui.clipboard_workforce.job_detail.day"
                        : "villagerretaliation.gui.clipboard_workforce.job_detail.days",
                worker.contractDays()).withColor(contractDurationColor(worker.contractDays()));
        y = drawDetailLine(graphics, Component.translatable(
                "villagerretaliation.gui.clipboard_workforce.job_detail.contract",
                contractDuration), y, true, 0);
        y = drawDetailLine(graphics, Component.translatable(
                "villagerretaliation.gui.clipboard_workforce.job_detail.daily_pay",
                Component.literal(worker.dailyPayText()).withColor(worker.dailyPayColor())), y, false, 0);
        y = drawDetailLine(graphics, Component.translatable(
                "villagerretaliation.gui.clipboard_workforce.job_detail.recurring_payment",
                Component.translatable(worker.recurringPayment()
                        ? "villagerretaliation.gui.clipboard_workforce.job_detail.on"
                        : "villagerretaliation.gui.clipboard_workforce.job_detail.off")
                        .withColor(worker.recurringPayment() ? TEXT : WARNING)), y, true, 0);
        Component storageState = Component.translatable(worker.storageAssigned()
                ? "villagerretaliation.gui.clipboard_workforce.assigned"
                : "villagerretaliation.gui.clipboard_workforce.missing");
        drawDetailLine(graphics, Component.translatable(
                "villagerretaliation.gui.clipboard_workforce.job_detail.storage",
                storageState,
                worker.storageCount()), y, false, 0);
    }

    private void renderAssignmentTrackerPage(GuiGraphics graphics, double mouseX, double mouseY) {
        Component title = Component.translatable(
                "villagerretaliation.gui.clipboard_workforce.assignment_tracker.title");
        graphics.drawString(
                this.font,
                title,
                (TEXTURE_WIDTH - this.font.width(title)) / 2,
                WORKFORCE_TITLE_TOP,
                TEXT,
                false);
        renderCenteredDivider(graphics, TITLE_DIVIDER_TOP);

        int y = SUMMARY_TOP;
        List<net.minecraft.util.FormattedCharSequence> description = this.font.split(
                Component.translatable("villagerretaliation.gui.clipboard_workforce.assignment_tracker.description"),
                SUMMARY_RIGHT - SUMMARY_LEFT);
        for (net.minecraft.util.FormattedCharSequence line : description) {
            graphics.drawString(this.font, line, SUMMARY_LEFT, y, TEXT, false);
            y += this.font.lineHeight;
        }
        int rowsTop = y + GLOBAL_TEXT_GAP;
        List<JobListRow> jobs = assignmentTrackingRows();
        if (jobs.isEmpty()) {
            graphics.drawString(
                    this.font,
                    Component.translatable("villagerretaliation.gui.clipboard_workforce.no_workers"),
                    SUMMARY_LEFT,
                    rowsTop,
                    MUTED,
                    false);
            return;
        }
        this.assignmentTrackingScroll = Mth.clamp(
                this.assignmentTrackingScroll, 0, maxAssignmentTrackingScroll());
        int rowTop = rowsTop;
        int start = this.assignmentTrackingScroll;
        int visibleRows = visibleAssignmentTrackingRows(rowsTop);
        int end = Math.min(jobs.size(), start + visibleRows);
        for (int index = start; index < end; index++) {
            JobListRow row = jobs.get(index);
            boolean selected = this.trackedAssignmentRoles.contains(row.role());
            boolean hovered = mouseX >= JOB_DETAIL_BAND_LEFT
                    && mouseX < JOB_DETAIL_BAND_RIGHT
                    && mouseY >= rowTop - 2
                    && mouseY < rowTop + TEXT_PIXEL_HEIGHT + 2;
            if (selected || hovered) {
                graphics.fill(
                        JOB_DETAIL_BAND_LEFT,
                        rowTop - 2,
                        JOB_DETAIL_BAND_RIGHT,
                        rowTop + TEXT_PIXEL_HEIGHT + 2,
                        selected ? SELECTED_FILL : JOB_DETAIL_BAND_COLOR);
            }
            Component label = Component.translatable(
                    "villagerretaliation.gui.clipboard_workforce.assignment_tracker.job",
                    row.label(),
                    row.count());
            graphics.drawString(this.font, label, SUMMARY_LEFT, rowTop, selected ? TEXT : MUTED, false);
            if (selected) {
                graphics.blit(
                        VillagerRetaliationClientAssets.CLIPBOARD_ASSIGNMENT_CHECKMARK_TEXTURE,
                        SUMMARY_RIGHT - ASSIGNMENT_CHECKMARK_WIDTH,
                        rowTop,
                        0.0F,
                        0.0F,
                        ASSIGNMENT_CHECKMARK_WIDTH,
                        ASSIGNMENT_CHECKMARK_HEIGHT,
                        ASSIGNMENT_CHECKMARK_WIDTH,
                        ASSIGNMENT_CHECKMARK_HEIGHT);
            }
            rowTop += SUMMARY_ROW_STEP;
        }
    }

    private List<JobListRow> assignmentTrackingRows() {
        return sortedJobRows().stream()
                .filter(row -> row.count() > 0)
                .toList();
    }

    private int assignmentTrackingRowsTop() {
        int lines = Math.max(1, this.font.split(
                Component.translatable("villagerretaliation.gui.clipboard_workforce.assignment_tracker.description"),
                SUMMARY_RIGHT - SUMMARY_LEFT).size());
        return SUMMARY_TOP + lines * this.font.lineHeight + GLOBAL_TEXT_GAP;
    }

    private int visibleAssignmentTrackingRows(int rowsTop) {
        return Math.max(1, 1 + (TEXT_CONTENT_BOTTOM - rowsTop - TEXT_PIXEL_HEIGHT) / SUMMARY_ROW_STEP);
    }

    private int maxAssignmentTrackingScroll() {
        return Math.max(0, assignmentTrackingRows().size()
                - visibleAssignmentTrackingRows(assignmentTrackingRowsTop()));
    }

    private HiredVillagerRole assignmentTrackingRoleAt(double mouseX, double mouseY) {
        if (mouseX < JOB_DETAIL_BAND_LEFT || mouseX >= JOB_DETAIL_BAND_RIGHT) {
            return null;
        }
        int rowsTop = assignmentTrackingRowsTop();
        if (mouseY < rowsTop - 2 || mouseY >= TEXT_CONTENT_BOTTOM) {
            return null;
        }
        int visibleIndex = (int) ((mouseY - rowsTop + 2) / SUMMARY_ROW_STEP);
        List<JobListRow> rows = assignmentTrackingRows();
        int index = this.assignmentTrackingScroll + visibleIndex;
        if (index < 0 || index >= rows.size()) {
            return null;
        }
        int rowTop = rowsTop + visibleIndex * SUMMARY_ROW_STEP;
        return mouseY < rowTop + TEXT_PIXEL_HEIGHT + 2 ? rows.get(index).role() : null;
    }

    private void openAssignmentTracker() {
        this.page = Page.ASSIGNMENT_TRACKER;
        this.assignmentTrackingScroll = 0;
        this.selectedWorker = null;
        this.selectedWarning = null;
        this.activePreviewTab = this.trackedAssignmentRoles.isEmpty()
                ? null
                : ClipboardPreviewTab.ASSIGNMENTS;
        this.lastPreviewStateKey = "";
        syncActivePreview();
        sendPreviewConfiguration();
    }

    private void toggleTrackedAssignmentRole(HiredVillagerRole role) {
        if (!this.trackedAssignmentRoles.remove(role)) {
            this.trackedAssignmentRoles.add(role);
        }
        this.activePreviewTab = this.trackedAssignmentRoles.isEmpty()
                ? null
                : ClipboardPreviewTab.ASSIGNMENTS;
        this.lastPreviewStateKey = "";
        syncActivePreview();
        sendPreviewConfiguration();
    }

    private int drawDetailLine(GuiGraphics graphics, Component text, int top, boolean banded, int indent) {
        return drawDetailLine(graphics, text, top, banded, indent, TEXT);
    }

    private int drawDetailLine(
            GuiGraphics graphics,
            Component text,
            int top,
            boolean banded,
            int indent,
            int color) {
        int left = SUMMARY_LEFT + indent;
        List<net.minecraft.util.FormattedCharSequence> lines = this.font.split(text, SUMMARY_RIGHT - left);
        int blockHeight = Math.max(this.font.lineHeight, lines.size() * this.font.lineHeight);
        if (banded) {
            graphics.fill(
                    JOB_DETAIL_BAND_LEFT,
                    top - 2,
                    JOB_DETAIL_BAND_RIGHT,
                    top + blockHeight,
                    JOB_DETAIL_BAND_COLOR);
        }
        int lineTop = top;
        for (net.minecraft.util.FormattedCharSequence line : lines) {
            graphics.drawString(this.font, line, left, lineTop, color, false);
            lineTop += this.font.lineHeight;
        }
        return lineTop + Math.max(0, SUMMARY_ROW_STEP - this.font.lineHeight);
    }

    private void drawArrowLine(
            GuiGraphics graphics,
            boolean warning,
            Component text,
            int left,
            int top,
            int color) {
        graphics.blit(
                warning
                        ? VillagerRetaliationClientAssets.CLIPBOARD_WARNING_ARROW_TEXTURE
                        : VillagerRetaliationClientAssets.CLIPBOARD_ROW_ARROW_TEXTURE,
                left,
                top,
                0.0F,
                0.0F,
                ROW_ARROW_WIDTH,
                ROW_ARROW_HEIGHT,
                ROW_ARROW_WIDTH,
                ROW_ARROW_HEIGHT);
        graphics.drawString(this.font, text, left + ROW_ARROW_TEXT_OFFSET, top, color, false);
    }

    private WorkerRow selectedRoleWorker() {
        if (this.selectedWorker != null && this.selectedWorker.role() == this.selectedRole) {
            return this.selectedWorker;
        }
        return workersForSelectedRole().stream().findFirst().orElse(null);
    }

    private void renderWarningDetailPage(GuiGraphics graphics, double mouseX, double mouseY) {
        Component title = Component.translatable(
                "villagerretaliation.gui.clipboard_workforce.job_detail.title",
                Component.translatable("villagerretaliation.gui.clipboard_workforce.warning_header"));
        graphics.drawString(
                this.font,
                title,
                (TEXTURE_WIDTH - this.font.width(title)) / 2,
                WORKFORCE_TITLE_TOP,
                TEXT,
                false);
        renderCenteredDivider(graphics, TITLE_DIVIDER_TOP);

        List<WarningDisplayEntry> entries = warningDisplayEntries();
        if (entries.isEmpty()) {
            graphics.drawString(
                    this.font,
                    Component.translatable("villagerretaliation.gui.clipboard_workforce.no_warnings"),
                    SUMMARY_LEFT,
                    SUMMARY_TOP,
                    MUTED,
                    false);
            return;
        }
        this.warningScroll = Mth.clamp(this.warningScroll, 0, maxWarningPageScroll());
        int lineTop = SUMMARY_TOP - this.warningScroll * this.font.lineHeight;
        for (WarningDisplayEntry entry : entries) {
            int entryTop = lineTop;
            int entryBottom = entryTop
                    + (entry.lines().size() - 1) * this.font.lineHeight
                    + TEXT_PIXEL_HEIGHT;
            boolean hovered = mouseX >= JOB_DETAIL_BAND_LEFT
                    && mouseX < JOB_DETAIL_BAND_RIGHT
                    && mouseY >= Math.max(SUMMARY_TOP - 2, entryTop - 2)
                    && mouseY < Math.min(TEXT_CONTENT_BOTTOM, entryBottom + 2);
            if ((entry.banded() || hovered)
                    && entryBottom > SUMMARY_TOP
                    && entryTop < TEXT_CONTENT_BOTTOM) {
                graphics.fill(
                        JOB_DETAIL_BAND_LEFT,
                        Math.max(SUMMARY_TOP - 2, entryTop - 2),
                        JOB_DETAIL_BAND_RIGHT,
                        Math.min(TEXT_CONTENT_BOTTOM, entryBottom + 2),
                        JOB_DETAIL_BAND_COLOR);
            }
            for (net.minecraft.util.FormattedCharSequence line : entry.lines()) {
                if (lineTop >= SUMMARY_TOP && lineTop + TEXT_PIXEL_HEIGHT <= TEXT_CONTENT_BOTTOM) {
                    if (lineTop == entryTop) {
                        graphics.blit(
                                isWaitingNotice(entry.warning().type())
                                        ? VillagerRetaliationClientAssets.CLIPBOARD_ROW_ARROW_TEXTURE
                                        : VillagerRetaliationClientAssets.CLIPBOARD_WARNING_ARROW_TEXTURE,
                                SUMMARY_LEFT,
                                lineTop,
                                0.0F,
                                0.0F,
                                ROW_ARROW_WIDTH,
                                ROW_ARROW_HEIGHT,
                                ROW_ARROW_WIDTH,
                                ROW_ARROW_HEIGHT);
                    }
                    graphics.drawString(
                            this.font,
                            line,
                            lineTop == entryTop ? SUMMARY_LEFT + ROW_ARROW_TEXT_OFFSET : SUMMARY_LEFT,
                            lineTop,
                            warningColor(entry.warning().type()),
                            false);
                }
                lineTop += this.font.lineHeight;
            }
            lineTop += warningSummaryEntryGap();
        }
    }

    private List<WarningDisplayEntry> warningDisplayEntries() {
        List<WarningDisplayEntry> entries = new ArrayList<>();
        int warningIndex = 0;
        List<WarningSummary> warnings = this.snapshot.warnings().stream()
                .sorted(Comparator.comparingInt(WarningSummary::count)
                        .reversed()
                        .thenComparing(
                                warning -> roleName(warning.role()).getString(),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(warning -> warning.type().name()))
                .toList();
        for (WarningSummary warning : warnings) {
            Component text = warningText(warning);
            boolean banded = warningIndex % 2 != 0;
            entries.add(new WarningDisplayEntry(
                    warning,
                    splitArrowWrappedText(text),
                    banded));
            warningIndex++;
        }
        return entries;
    }

    private int maxWarningSummaryScroll() {
        List<WarningDisplayEntry> entries = warningDisplayEntries();
        int totalLines = entries.stream().mapToInt(entry -> entry.lines().size()).sum();
        int contentHeight = Math.max(0, totalLines - 1) * this.font.lineHeight
                + TEXT_PIXEL_HEIGHT
                + Math.max(0, entries.size() - 1) * warningSummaryEntryGap();
        int overflow = Math.max(0, contentHeight - (TEXT_CONTENT_BOTTOM - SUMMARY_TOP));
        return ceilDiv(overflow, this.font.lineHeight);
    }

    private int warningSummaryEntryGap() {
        return Math.max(0, SUMMARY_ROW_STEP - this.font.lineHeight);
    }

    private void renderWarningWorkersPage(GuiGraphics graphics, double mouseX, double mouseY) {
        Component title = this.selectedWarning == null
                ? Component.translatable("villagerretaliation.gui.clipboard_workforce.warning_header")
                : roleName(this.selectedWarning.role());
        renderDetailTitle(graphics, title);
        if (this.selectedWarning == null) {
            return;
        }
        List<net.minecraft.util.FormattedCharSequence> issueLines = warningIssueLines();
        int issueTop = SUMMARY_TOP;
        for (net.minecraft.util.FormattedCharSequence line : issueLines) {
            graphics.drawString(this.font, line, SUMMARY_LEFT, issueTop, warningColor(this.selectedWarning.type()), false);
            issueTop += this.font.lineHeight;
        }
        List<WorkerRow> workers = workersForSelectedWarning();
        int viewportTop = warningWorkersViewportTop();
        this.warningScroll = Mth.clamp(this.warningScroll, 0, maxWarningPageScroll());
        int rowTop = viewportTop - this.warningScroll * SUMMARY_ROW_STEP;
        for (int index = 0; index < workers.size(); index++) {
            WorkerRow worker = workers.get(index);
            if (rowTop >= viewportTop && rowTop + TEXT_PIXEL_HEIGHT <= TEXT_CONTENT_BOTTOM) {
                boolean hovered = mouseX >= JOB_DETAIL_BAND_LEFT
                        && mouseX < JOB_DETAIL_BAND_RIGHT
                        && mouseY >= rowTop - 2
                        && mouseY < rowTop + TEXT_PIXEL_HEIGHT + 2;
                if (index % 2 != 0 || hovered) {
                    graphics.fill(
                            JOB_DETAIL_BAND_LEFT,
                            rowTop - 2,
                            JOB_DETAIL_BAND_RIGHT,
                            rowTop + TEXT_PIXEL_HEIGHT + 2,
                            JOB_DETAIL_BAND_COLOR);
                }
                drawArrowLine(
                        graphics,
                        !isWaitingNotice(this.selectedWarning.type()),
                        Component.literal(worker.displayName()),
                        SUMMARY_LEFT,
                        rowTop,
                        warningColor(this.selectedWarning.type()));
            }
            rowTop += SUMMARY_ROW_STEP;
        }
    }

    private List<net.minecraft.util.FormattedCharSequence> warningIssueLines() {
        if (this.selectedWarning == null) {
            return List.of();
        }
        Component issue = Component.translatable(
                "villagerretaliation.gui.clipboard_workforce.warning_detail.issue",
                warningTypeName(this.selectedWarning.type()));
        return this.font.split(issue, SUMMARY_RIGHT - SUMMARY_LEFT);
    }

    private int warningWorkersViewportTop() {
        int issueLineCount = Math.max(1, warningIssueLines().size());
        return SUMMARY_TOP + issueLineCount * this.font.lineHeight + GLOBAL_TEXT_GAP;
    }

    private void renderWorkerErrorsPage(GuiGraphics graphics) {
        Component title = this.selectedWorker == null
                ? Component.translatable("villagerretaliation.gui.clipboard_workforce.warning_header")
                : Component.literal(this.selectedWorker.displayName());
        renderDetailTitle(graphics, title);
        if (this.selectedWorker == null) {
            return;
        }
        List<WorkerErrorDisplayEntry> entries = workerErrorDisplayEntries();
        this.warningScroll = Mth.clamp(this.warningScroll, 0, maxWarningPageScroll());
        int lineTop = SUMMARY_TOP - this.warningScroll * this.font.lineHeight;
        for (WorkerErrorDisplayEntry entry : entries) {
            int entryTop = lineTop;
            int entryBottom = entryTop
                    + (entry.lines().size() - 1) * this.font.lineHeight
                    + TEXT_PIXEL_HEIGHT;
            if (entry.banded() && entryBottom > SUMMARY_TOP && entryTop < TEXT_CONTENT_BOTTOM) {
                graphics.fill(
                        JOB_DETAIL_BAND_LEFT,
                        Math.max(SUMMARY_TOP - 2, entryTop - 2),
                        JOB_DETAIL_BAND_RIGHT,
                        Math.min(TEXT_CONTENT_BOTTOM, entryBottom + 2),
                        JOB_DETAIL_BAND_COLOR);
            }
            for (net.minecraft.util.FormattedCharSequence line : entry.lines()) {
                if (lineTop >= SUMMARY_TOP && lineTop + TEXT_PIXEL_HEIGHT <= TEXT_CONTENT_BOTTOM) {
                    if (lineTop == entryTop) {
                        graphics.blit(
                                entry.notice()
                                        ? VillagerRetaliationClientAssets.CLIPBOARD_ROW_ARROW_TEXTURE
                                        : VillagerRetaliationClientAssets.CLIPBOARD_WARNING_ARROW_TEXTURE,
                                SUMMARY_LEFT,
                                lineTop,
                                0.0F,
                                0.0F,
                                ROW_ARROW_WIDTH,
                                ROW_ARROW_HEIGHT,
                                ROW_ARROW_WIDTH,
                                ROW_ARROW_HEIGHT);
                    }
                    graphics.drawString(
                            this.font,
                            line,
                            lineTop == entryTop ? SUMMARY_LEFT + ROW_ARROW_TEXT_OFFSET : SUMMARY_LEFT,
                            lineTop,
                            entry.notice() ? NOTICE : WARNING,
                            false);
                }
                lineTop += this.font.lineHeight;
            }
        }
    }

    private List<WorkerErrorDisplayEntry> workerErrorDisplayEntries() {
        if (this.selectedWorker == null) {
            return List.of();
        }
        List<WorkerErrorDisplayEntry> entries = new ArrayList<>();
        List<ClipboardWorkforceSnapshot.WarningType> issues = warningTypes(this.selectedWorker);
        for (int index = 0; index < issues.size(); index++) {
            ClipboardWorkforceSnapshot.WarningType type = issues.get(index);
            Component diagnostic = Component.translatable(
                    "villagerretaliation.gui.clipboard_workforce.warning_diagnostic",
                    warningTypeName(type),
                    warningDiagnostic(type));
            entries.add(new WorkerErrorDisplayEntry(
                    splitArrowWrappedText(diagnostic),
                    index % 2 != 0,
                    isWaitingNotice(type)));
        }
        return entries;
    }

    private List<net.minecraft.util.FormattedCharSequence> splitArrowWrappedText(Component text) {
        List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
        String remaining = text.getString().strip();
        boolean firstLine = true;
        while (!remaining.isEmpty()) {
            int width = JOB_DETAIL_BAND_RIGHT
                    - SUMMARY_LEFT
                    - (firstLine ? ROW_ARROW_TEXT_OFFSET : 0);
            String fitted = this.font.plainSubstrByWidth(remaining, width);
            int consumed = fitted.length();
            if (consumed < remaining.length() && !Character.isWhitespace(remaining.charAt(consumed))) {
                int lastSpace = fitted.lastIndexOf(' ');
                if (lastSpace > 0) {
                    fitted = fitted.substring(0, lastSpace);
                    consumed = lastSpace;
                }
            }
            if (consumed <= 0) {
                fitted = remaining.substring(0, 1);
                consumed = 1;
            }
            lines.add(Component.literal(fitted).getVisualOrderText());
            remaining = remaining.substring(consumed).stripLeading();
            firstLine = false;
        }
        return lines;
    }

    private Component warningDiagnostic(ClipboardWorkforceSnapshot.WarningType type) {
        String key = "villagerretaliation.gui.clipboard_workforce.diagnostic."
                + type.name().toLowerCase(java.util.Locale.ROOT);
        if (type == ClipboardWorkforceSnapshot.WarningType.NO_STORAGE) {
            if (!this.selectedWorker.diagnostic().isBlank()) {
                return Component.literal(this.selectedWorker.diagnostic());
            }
            return Component.translatable(key + ".output");
        }
        if (type == ClipboardWorkforceSnapshot.WarningType.UNPAID) {
            return Component.translatable(key, this.selectedWorker.dailyPayText());
        }
        return Component.translatable(key);
    }

    private void renderDetailTitle(GuiGraphics graphics, Component value) {
        Component title = Component.translatable(
                "villagerretaliation.gui.clipboard_workforce.job_detail.title",
                value);
        String fitted = fit(title.getString(), SUMMARY_RIGHT - SUMMARY_LEFT);
        graphics.drawString(
                this.font,
                fitted,
                (TEXTURE_WIDTH - this.font.width(fitted)) / 2,
                WORKFORCE_TITLE_TOP,
                TEXT,
                false);
        renderCenteredDivider(graphics, TITLE_DIVIDER_TOP);
    }

    private boolean isWarningPage() {
        return this.page == Page.WARNINGS
                || this.page == Page.WARNING_WORKERS
                || this.page == Page.WORKER_ERRORS;
    }

    private int maxWarningPageScroll() {
        return switch (this.page) {
            case WARNINGS -> maxWarningSummaryScroll();
            case WARNING_WORKERS -> {
                int visibleRows = 1 + Math.max(
                        0,
                        TEXT_CONTENT_BOTTOM - warningWorkersViewportTop() - TEXT_PIXEL_HEIGHT)
                        / SUMMARY_ROW_STEP;
                yield Math.max(0, workersForSelectedWarning().size() - visibleRows);
            }
            case WORKER_ERRORS -> {
                int visibleRows = 1 + Math.max(0, TEXT_CONTENT_BOTTOM - SUMMARY_TOP - TEXT_PIXEL_HEIGHT)
                        / this.font.lineHeight;
                int totalLines = workerErrorDisplayEntries().stream()
                        .mapToInt(entry -> entry.lines().size())
                        .sum();
                yield Math.max(0, totalLines - visibleRows);
            }
            default -> 0;
        };
    }

    private List<WorkerRow> workersForSelectedWarning() {
        if (this.selectedWarning == null) {
            return List.of();
        }
        return this.snapshot.workers().stream()
                .filter(worker -> worker.role() == this.selectedWarning.role())
                .filter(worker -> workerHasWarning(worker, this.selectedWarning.type()))
                .sorted(Comparator.comparing(WorkerRow::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<ClipboardWorkforceSnapshot.WarningType> warningTypes(WorkerRow worker) {
        if (worker == null) {
            return List.of();
        }
        List<ClipboardWorkforceSnapshot.WarningType> types = new ArrayList<>();
        for (ClipboardWorkforceSnapshot.WarningType type : ClipboardWorkforceSnapshot.WarningType.values()) {
            if (workerHasWarning(worker, type)) {
                types.add(type);
            }
        }
        return types;
    }

    private boolean workerHasWarning(WorkerRow worker, ClipboardWorkforceSnapshot.WarningType type) {
        return switch (type) {
            case NO_WORK_AREA -> worker.noWorkArea();
            case NO_STORAGE -> worker.noStorage();
            case STORAGE_FULL -> worker.storageFull();
            case INVENTORY_FULL -> worker.inventoryFull()
                    && worker.status() != ClipboardWorkforceSnapshot.WorkerStatus.MATERIAL_INVENTORY_FULL;
            case MISSING_TOOLS -> worker.missingTools();
            case UNPAID -> worker.unpaid();
            case NO_TARGETS -> worker.noTargets();
            case TOO_FAR -> worker.tooFar();
            case MISSING_MATERIALS -> worker.missingMaterials();
            case MATERIAL_STORAGE_UNREACHABLE -> worker.materialStorageUnreachable();
            case MATERIAL_INVENTORY_FULL -> worker.materialInventoryFull();
            case BUILD_SITE_UNREACHABLE -> worker.buildSiteUnreachable();
        };
    }

    private Component warningTypeName(ClipboardWorkforceSnapshot.WarningType type) {
        ClipboardWorkforceSnapshot.WorkerStatus status = switch (type) {
            case NO_WORK_AREA -> ClipboardWorkforceSnapshot.WorkerStatus.NO_WORK_AREA;
            case NO_STORAGE -> ClipboardWorkforceSnapshot.WorkerStatus.NO_STORAGE;
            case STORAGE_FULL -> ClipboardWorkforceSnapshot.WorkerStatus.STORAGE_FULL;
            case INVENTORY_FULL -> ClipboardWorkforceSnapshot.WorkerStatus.INVENTORY_FULL;
            case MISSING_TOOLS -> ClipboardWorkforceSnapshot.WorkerStatus.MISSING_TOOLS;
            case UNPAID -> ClipboardWorkforceSnapshot.WorkerStatus.UNPAID;
            case NO_TARGETS -> ClipboardWorkforceSnapshot.WorkerStatus.NO_TARGETS;
            case TOO_FAR -> ClipboardWorkforceSnapshot.WorkerStatus.TOO_FAR;
            case MISSING_MATERIALS -> ClipboardWorkforceSnapshot.WorkerStatus.MISSING_MATERIALS;
            case MATERIAL_STORAGE_UNREACHABLE -> ClipboardWorkforceSnapshot.WorkerStatus.MATERIAL_STORAGE_UNREACHABLE;
            case MATERIAL_INVENTORY_FULL -> ClipboardWorkforceSnapshot.WorkerStatus.MATERIAL_INVENTORY_FULL;
            case BUILD_SITE_UNREACHABLE -> ClipboardWorkforceSnapshot.WorkerStatus.BUILD_SITE_UNREACHABLE;
        };
        return statusName(status);
    }

    private boolean canTurnWorkerPage(int direction) {
        List<WorkerRow> workers = workersForSelectedRole();
        if (this.page != Page.JOB || workers.size() < 2) {
            return false;
        }
        int index = this.selectedWorker == null ? 0 : workers.indexOf(this.selectedWorker);
        if (index < 0) {
            index = 0;
        }
        int nextIndex = index + Integer.signum(direction);
        return nextIndex >= 0 && nextIndex < workers.size();
    }

    private boolean turnWorkerPage(int direction) {
        if (!canTurnWorkerPage(direction)) {
            return false;
        }
        List<WorkerRow> workers = workersForSelectedRole();
        int index = workers.indexOf(this.selectedWorker);
        this.selectedWorker = workers.get(index + Integer.signum(direction));
        return true;
    }

    private boolean canScrollJobsDown() {
        if (this.page == Page.ASSIGNMENT_TRACKER) {
            return this.assignmentTrackingScroll < maxAssignmentTrackingScroll();
        }
        if (isWarningPage()) {
            return this.warningScroll < maxWarningPageScroll();
        }
        return this.jobScroll < maxJobScroll();
    }

    private boolean canScrollJobsUp() {
        if (this.page == Page.ASSIGNMENT_TRACKER) {
            return this.assignmentTrackingScroll > 0;
        }
        if (isWarningPage()) {
            return this.warningScroll > 0 && maxWarningPageScroll() > 0;
        }
        return this.jobScroll > 0 && maxJobScroll() > 0;
    }

    private void scrollJobs(int direction) {
        if (this.page == Page.ASSIGNMENT_TRACKER) {
            this.assignmentTrackingScroll = Mth.clamp(
                    this.assignmentTrackingScroll + direction, 0, maxAssignmentTrackingScroll());
            return;
        }
        if (isWarningPage()) {
            this.warningScroll = Mth.clamp(this.warningScroll + direction, 0, maxWarningPageScroll());
            return;
        }
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

    private static int homeTabTop(float visibility) {
        return HOME_TAB_TOP + Math.round((1.0F - visibility) * HOME_NAVIGATION_TAB_INSET);
    }

    private static int leftPageTabLeft(float visibility) {
        return LEFT_PAGE_TAB_LEFT + Math.round((1.0F - visibility) * LEFT_NAVIGATION_TAB_INSET);
    }

    private static int rightPageTabLeft(float visibility) {
        return RIGHT_PAGE_TAB_LEFT - Math.round((1.0F - visibility) * NAVIGATION_TAB_INSET);
    }

    private void syncNavigationTabAnimations(double mouseX, double mouseY) {
        float leftVisibility = this.leftPageTabAnimation.visibility();
        float rightVisibility = this.rightPageTabAnimation.visibility();
        boolean leftHovered = this.suppressedTabHover != 5 && (isExposedTabPixel(
                this.leftPageTabHoverMask,
                mouseX,
                mouseY,
                leftPageTabLeft(leftVisibility),
                PAGE_SIDE_TAB_TOP) || isExposedTabPixel(
                this.leftPageTabHoverMask,
                mouseX,
                mouseY,
                leftPageTabLeft(0.0F),
                PAGE_SIDE_TAB_TOP));
        boolean rightHovered = this.suppressedTabHover != 6 && (isExposedTabPixel(
                this.rightPageTabHoverMask,
                mouseX,
                mouseY,
                rightPageTabLeft(rightVisibility),
                PAGE_SIDE_TAB_TOP) || isExposedTabPixel(
                this.rightPageTabHoverMask,
                mouseX,
                mouseY,
                rightPageTabLeft(0.0F),
                PAGE_SIDE_TAB_TOP));

        if (this.page != Page.OVERVIEW) {
            this.homeTabAnimation.setVisibility(1.0F);
            this.leftPageTabAnimation.setVisibility(canTurnWorkerPage(-1)
                    ? 1.0F
                    : leftHovered ? LEFT_NAVIGATION_TAB_PEEK : 0.0F);
            this.rightPageTabAnimation.setVisibility(canTurnWorkerPage(1)
                    ? 1.0F
                    : rightHovered ? NAVIGATION_TAB_PEEK : 0.0F);
            return;
        }

        float homeVisibility = this.homeTabAnimation.visibility();
        boolean homeHovered = this.suppressedTabHover != 4 && (isExposedTabPixel(
                this.homeTabHoverMask,
                mouseX,
                mouseY,
                HOME_TAB_LEFT,
                homeTabTop(homeVisibility)) || isExposedTabPixel(
                this.homeTabHoverMask,
                mouseX,
                mouseY,
                HOME_TAB_LEFT,
                homeTabTop(0.0F)));
        this.homeTabAnimation.setVisibility(homeHovered ? HOME_NAVIGATION_TAB_PEEK : 0.0F);
        this.leftPageTabAnimation.setVisibility(leftHovered ? LEFT_NAVIGATION_TAB_PEEK : 0.0F);
        this.rightPageTabAnimation.setVisibility(rightHovered ? NAVIGATION_TAB_PEEK : 0.0F);
    }

    private void syncNumberedTabAnimations(int hoveredTab) {
        this.numberedTab1Animation.setVisible(hoveredTab == 1 || this.activePreviewTab == ClipboardPreviewTab.WORKFORCE);
        this.numberedTab2Animation.setVisible(hoveredTab == 2
                || this.activePreviewTab == ClipboardPreviewTab.ASSIGNMENTS
                || this.page == Page.ASSIGNMENT_TRACKER);
        this.numberedTab3Animation.setVisible(hoveredTab == 3 || this.activePreviewTab == ClipboardPreviewTab.PROBLEMS);
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

    private static int numberedTabLeft(int width, float visibility) {
        return CONTAINER_TAB_RIGHT - width + 1 - Math.round(visibility * NUMBERED_TAB_HOVER_EXTRUDE);
    }

    private static void renderContainerTab(
            GuiGraphics graphics,
            ResourceLocation texture,
            int width,
            int height,
            int top,
            float visibility) {
        int left = numberedTabLeft(width, visibility);
        graphics.blit(texture, left, top, 0.0F, 0.0F, width, height, width, height);
    }

    private void renderNumberedContainerTabs(GuiGraphics graphics, int hoveredTab) {
        int activeTab = this.page == Page.ASSIGNMENT_TRACKER
                ? ClipboardPreviewTab.ASSIGNMENTS.tabNumber
                : this.activePreviewTab == null ? 0 : this.activePreviewTab.tabNumber;
        for (int tabNumber = 3; tabNumber >= 1; tabNumber--) {
            if (tabNumber != activeTab && tabNumber != hoveredTab) {
                renderNumberedContainerTab(graphics, tabNumber, false);
            }
        }
        if (activeTab != 0 && activeTab != hoveredTab) {
            renderNumberedContainerTab(graphics, activeTab, false);
        }
        if (hoveredTab >= 1 && hoveredTab <= 3) {
            renderNumberedContainerTab(graphics, hoveredTab, true);
        }
    }

    private void renderNumberedContainerTab(GuiGraphics graphics, int tabNumber, boolean highlighted) {
        switch (tabNumber) {
            case 1 -> renderContainerTab(
                    graphics,
                    highlighted
                            ? VillagerRetaliationClientAssets.CLIPBOARD_CONTAINER_TAB_1_HIGHLIGHT_TEXTURE
                            : VillagerRetaliationClientAssets.CLIPBOARD_CONTAINER_TAB_1_TEXTURE,
                    CONTAINER_TAB_1_WIDTH,
                    CONTAINER_TAB_1_HEIGHT,
                    CONTAINER_TAB_1_TOP,
                    this.numberedTab1Animation.visibility());
            case 2 -> renderContainerTab(
                    graphics,
                    highlighted
                            ? VillagerRetaliationClientAssets.CLIPBOARD_CONTAINER_TAB_2_HIGHLIGHT_TEXTURE
                            : VillagerRetaliationClientAssets.CLIPBOARD_CONTAINER_TAB_2_TEXTURE,
                    CONTAINER_TAB_2_WIDTH,
                    CONTAINER_TAB_2_HEIGHT,
                    CONTAINER_TAB_2_TOP,
                    this.numberedTab2Animation.visibility());
            case 3 -> renderContainerTab(
                    graphics,
                    highlighted
                            ? VillagerRetaliationClientAssets.CLIPBOARD_CONTAINER_TAB_3_HIGHLIGHT_TEXTURE
                            : VillagerRetaliationClientAssets.CLIPBOARD_CONTAINER_TAB_3_TEXTURE,
                    CONTAINER_TAB_3_WIDTH,
                    CONTAINER_TAB_3_HEIGHT,
                    CONTAINER_TAB_3_TOP,
                    this.numberedTab3Animation.visibility());
            default -> {
            }
        }
    }

    private int hoveredContainerTab(double mouseX, double mouseY) {
        int hoveredTab = rawHoveredContainerTab(mouseX, mouseY);
        return hoveredTab == this.suppressedTabHover ? 0 : hoveredTab;
    }

    private int rawHoveredContainerTab(double mouseX, double mouseY) {
        int clipboardX = (int) Math.floor(mouseX);
        int clipboardY = (int) Math.floor(mouseY);
        if (this.mainPageHoverMask.hasAlpha(clipboardX, clipboardY)) {
            return 0;
        }
        if (isTabPixelHovered(
                this.leftPageTabHoverMask,
                mouseX,
                mouseY,
                leftPageTabLeft(this.leftPageTabAnimation.visibility()),
                PAGE_SIDE_TAB_TOP)) {
            return 5;
        }
        if (isTabPixelHovered(
                this.rightPageTabHoverMask,
                mouseX,
                mouseY,
                rightPageTabLeft(this.rightPageTabAnimation.visibility()),
                PAGE_SIDE_TAB_TOP)) {
            return 6;
        }
        if (isTabPixelHovered(
                this.containerTab1HoverMask,
                mouseX,
                mouseY,
                numberedTabLeft(CONTAINER_TAB_1_WIDTH, this.numberedTab1Animation.visibility()),
                CONTAINER_TAB_1_TOP) || isTabPixelHovered(
                this.containerTab1HoverMask,
                mouseX,
                mouseY,
                numberedTabLeft(CONTAINER_TAB_1_WIDTH, 0.0F),
                CONTAINER_TAB_1_TOP)) {
            return 1;
        }
        if (isTabPixelHovered(
                this.containerTab2HoverMask,
                mouseX,
                mouseY,
                numberedTabLeft(CONTAINER_TAB_2_WIDTH, this.numberedTab2Animation.visibility()),
                CONTAINER_TAB_2_TOP) || isTabPixelHovered(
                this.containerTab2HoverMask,
                mouseX,
                mouseY,
                numberedTabLeft(CONTAINER_TAB_2_WIDTH, 0.0F),
                CONTAINER_TAB_2_TOP)) {
            return 2;
        }
        if (isTabPixelHovered(
                this.containerTab3HoverMask,
                mouseX,
                mouseY,
                numberedTabLeft(CONTAINER_TAB_3_WIDTH, this.numberedTab3Animation.visibility()),
                CONTAINER_TAB_3_TOP) || isTabPixelHovered(
                this.containerTab3HoverMask,
                mouseX,
                mouseY,
                numberedTabLeft(CONTAINER_TAB_3_WIDTH, 0.0F),
                CONTAINER_TAB_3_TOP)) {
            return 3;
        }
        if (isTabPixelHovered(
                this.homeTabHoverMask,
                mouseX,
                mouseY,
                HOME_TAB_LEFT,
                homeTabTop(this.homeTabAnimation.visibility()))) {
            return 4;
        }
        return 0;
    }

    private void updateSuppressedTabHover(double mouseX, double mouseY) {
        if (this.suppressedTabHover != 0 && !isInsideTabTravelArea(this.suppressedTabHover, mouseX, mouseY)) {
            this.suppressedTabHover = 0;
        }
    }

    private boolean isInsideTabTravelArea(int tab, double mouseX, double mouseY) {
        return switch (tab) {
            case 4 -> contains(
                    mouseX,
                    mouseY,
                    HOME_TAB_LEFT,
                    homeTabTop(1.0F),
                    HOME_TAB_LEFT + HOME_TAB_WIDTH,
                    homeTabTop(0.0F) + HOME_TAB_HEIGHT);
            case 5 -> contains(
                    mouseX,
                    mouseY,
                    leftPageTabLeft(1.0F),
                    PAGE_SIDE_TAB_TOP,
                    leftPageTabLeft(0.0F) + PAGE_SIDE_TAB_WIDTH,
                    PAGE_SIDE_TAB_TOP + PAGE_SIDE_TAB_HEIGHT);
            case 6 -> contains(
                    mouseX,
                    mouseY,
                    rightPageTabLeft(0.0F),
                    PAGE_SIDE_TAB_TOP,
                    rightPageTabLeft(1.0F) + PAGE_SIDE_TAB_WIDTH,
                    PAGE_SIDE_TAB_TOP + PAGE_SIDE_TAB_HEIGHT);
            default -> false;
        };
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
            updateSuppressedTabHover(panelMouseX, panelMouseY);
            syncNavigationTabAnimations(panelMouseX, panelMouseY);
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
            int hoveredTab = hoveredContainerTab(panelMouseX, panelMouseY);
            ClipboardPreviewTab previewTab = ClipboardPreviewTab.byTabNumber(hoveredTab);
            if (previewTab != null) {
                if (previewTab == ClipboardPreviewTab.ASSIGNMENTS) {
                    openAssignmentTracker();
                } else if (this.page == Page.ASSIGNMENT_TRACKER) {
                    openOverview();
                    activatePreviewTab(previewTab);
                } else {
                    togglePreviewTab(previewTab);
                }
                playPageSound();
                return true;
            }
            if (hoveredTab == 4 && this.page != Page.OVERVIEW) {
                openOverview();
                this.suppressedTabHover = hoveredTab;
                playPageSound();
                return true;
            }
            if (this.page == Page.JOB && (hoveredTab == 5 || hoveredTab == 6)) {
                int direction = hoveredTab == 5 ? -1 : 1;
                if (turnWorkerPage(direction)) {
                    if (!canTurnWorkerPage(direction)) {
                        this.suppressedTabHover = hoveredTab;
                    }
                    playPageSound();
                    return true;
                }
            }
            if (this.page == Page.JOB && jobDetailWorkerRowHovered(panelMouseX, panelMouseY)) {
                openWorkerAssignment(selectedRoleWorker());
                playPageSound();
                return true;
            }
            if (this.page == Page.JOB_SITE) {
                for (RowAction row : this.rowActions) {
                    if (row.kind() == RowKind.JOB_SITE_ACTION && row.contains(panelMouseX, panelMouseY)) {
                        requestWorkAreaAction(row.worker(), row.workAreaAction());
                        playPageSound();
                        return true;
                    }
                }
            }
            if (this.page == Page.WARNINGS) {
                WarningSummary warning = warningAt(panelMouseX, panelMouseY);
                if (warning != null) {
                    this.selectedWarning = warning;
                    this.selectedWorker = null;
                    this.warningScroll = 0;
                    this.page = Page.WARNING_WORKERS;
                    playPageSound();
                    return true;
                }
            }
            if (this.page == Page.WARNING_WORKERS) {
                WorkerRow worker = warningWorkerAt(panelMouseX, panelMouseY);
                if (worker != null) {
                    this.selectedWorker = worker;
                    this.warningScroll = 0;
                    this.page = Page.WORKER_ERRORS;
                    playPageSound();
                    return true;
                }
            }
            if (this.page == Page.OVERVIEW) {
                if (panelMouseX >= JOB_DETAIL_BAND_LEFT
                        && panelMouseX < JOB_DETAIL_BAND_RIGHT
                        && panelMouseY >= WARNINGS_TOP - 2
                        && panelMouseY < WARNINGS_TOP + TEXT_PIXEL_HEIGHT + 2) {
                    this.page = Page.WARNINGS;
                    this.warningScroll = 0;
                    playPageSound();
                    return true;
                }
                HiredVillagerRole clickedRole = clickedJobRole(panelMouseX, panelMouseY);
                if (clickedRole != null) {
                    openJob(clickedRole);
                    playPageSound();
                    return true;
                }
            }
            if (this.page == Page.ASSIGNMENT_TRACKER) {
                HiredVillagerRole trackedRole = assignmentTrackingRoleAt(panelMouseX, panelMouseY);
                if (trackedRole != null) {
                    toggleTrackedAssignmentRole(trackedRole);
                    playPageSound();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private HiredVillagerRole clickedJobRole(double mouseX, double mouseY) {
        if (mouseX < SUMMARY_LEFT
                || mouseX >= SUMMARY_RIGHT
                || mouseY < JOB_DESCRIPTION_TOP
                || mouseY >= TEXT_CONTENT_BOTTOM) {
            return null;
        }
        List<JobListRow> rows = sortedJobRows();
        int rowTop = jobListTop() - jobScrollOffset();
        for (JobListRow row : rows) {
            if (isFullyVisibleJobRow(rowTop)
                    && mouseY >= rowTop
                    && mouseY < rowTop + SUMMARY_ROW_STEP) {
                return row.role();
            }
            rowTop += SUMMARY_ROW_STEP;
        }
        return null;
    }

    private DetailTooltip detailTooltipAt(double mouseX, double mouseY) {
        if (selectedRoleWorker() == null
                || mouseX < JOB_DETAIL_BAND_LEFT
                || mouseX >= JOB_DETAIL_BAND_RIGHT) {
            return null;
        }
        int row = Mth.floor((mouseY - JOB_DETAIL_WORKER_TOP + 2) / SUMMARY_ROW_STEP);
        String field = switch (row) {
            case 0 -> "worker";
            case 1 -> "status";
            case 2, 3, 4, 5, 6 -> "location";
            case 7 -> "contract";
            case 8 -> "daily_pay";
            case 9 -> "recurring_payment";
            case 10 -> "storage";
            default -> null;
        };
        if (field == null) {
            return null;
        }
        if ("worker".equals(field) && routeAssigned(selectedRoleWorker())) {
            field = "worker_route";
        }
        String prefix = "villagerretaliation.gui.clipboard_workforce.job_detail.tooltip." + field;
        return new DetailTooltip(prefix + ".title", prefix + ".detail");
    }

    private boolean jobDetailWorkerRowHovered(double mouseX, double mouseY) {
        return this.page == Page.JOB
                && selectedRoleWorker() != null
                && mouseX >= JOB_DETAIL_BAND_LEFT
                && mouseX < JOB_DETAIL_BAND_RIGHT
                && mouseY >= JOB_DETAIL_WORKER_TOP - 2
                && mouseY < JOB_DETAIL_WORKER_TOP + TEXT_PIXEL_HEIGHT + 2;
    }

    private WarningSummary warningAt(double mouseX, double mouseY) {
        if (mouseX < JOB_DETAIL_BAND_LEFT || mouseX >= JOB_DETAIL_BAND_RIGHT) {
            return null;
        }
        int lineTop = SUMMARY_TOP - this.warningScroll * this.font.lineHeight;
        for (WarningDisplayEntry entry : warningDisplayEntries()) {
            int entryTop = lineTop;
            int entryBottom = entryTop
                    + (entry.lines().size() - 1) * this.font.lineHeight
                    + TEXT_PIXEL_HEIGHT;
            if (entryBottom > SUMMARY_TOP
                    && entryTop < TEXT_CONTENT_BOTTOM
                    && mouseY >= Math.max(SUMMARY_TOP - 2, entryTop - 2)
                    && mouseY < Math.min(TEXT_CONTENT_BOTTOM, entryBottom + 2)) {
                return entry.warning();
            }
            lineTop += entry.lines().size() * this.font.lineHeight + warningSummaryEntryGap();
        }
        return null;
    }

    private WorkerRow warningWorkerAt(double mouseX, double mouseY) {
        if (mouseX < JOB_DETAIL_BAND_LEFT || mouseX >= JOB_DETAIL_BAND_RIGHT) {
            return null;
        }
        int viewportTop = warningWorkersViewportTop();
        int rowTop = viewportTop - this.warningScroll * SUMMARY_ROW_STEP;
        for (WorkerRow worker : workersForSelectedWarning()) {
            if (rowTop >= viewportTop
                    && rowTop + TEXT_PIXEL_HEIGHT <= TEXT_CONTENT_BOTTOM
                    && mouseY >= rowTop - 2
                    && mouseY < rowTop + TEXT_PIXEL_HEIGHT + 2) {
                return worker;
            }
            rowTop += SUMMARY_ROW_STEP;
        }
        return null;
    }

    private void togglePreviewTab(ClipboardPreviewTab tab) {
        this.activePreviewTab = this.activePreviewTab == tab ? null : tab;
        syncActivePreview();
        sendPreviewConfiguration();
    }

    private void activatePreviewTab(ClipboardPreviewTab tab) {
        this.activePreviewTab = tab;
        this.lastPreviewStateKey = "";
        syncActivePreview();
        sendPreviewConfiguration();
    }

    private void syncActivePreview() {
        ClipboardStorageOutlineRenderer.ClipboardPreviewLens lens = this.activePreviewTab == null
                ? ClipboardStorageOutlineRenderer.ClipboardPreviewLens.NONE
                : this.activePreviewTab.lens();
        PreviewScope scope = previewScope(lens);
        String previewStateKey = lens.name()
                + '|' + this.page.name()
                + '|' + scope.ownerName()
                + '|' + scope.jobName()
                + '|' + this.trackedAssignmentRoles;
        if (previewStateKey.equals(this.lastPreviewStateKey)) {
            return;
        }
        this.lastPreviewStateKey = previewStateKey;
        ClipboardStorageOutlineRenderer.setClipboardPreview(
                lens,
                scope.ownerName(),
                scope.jobName(),
                lens == ClipboardStorageOutlineRenderer.ClipboardPreviewLens.ASSIGNMENTS
                        ? trackedAssignmentOwnerNames()
                        : scopeOwnerNames(scope),
                trackedAssignmentJobNames(),
                workforceMarkers(),
                warningOwnerNames());
    }

    private PreviewScope previewScope(ClipboardStorageOutlineRenderer.ClipboardPreviewLens lens) {
        if (lens == ClipboardStorageOutlineRenderer.ClipboardPreviewLens.WORKFORCE) {
            return PreviewScope.NONE;
        }
        if (this.selectedWorker != null && this.page != Page.OVERVIEW) {
            return new PreviewScope(this.selectedWorker.displayName(), "");
        }
        HiredVillagerRole scopedRole = this.selectedWarning != null
                ? this.selectedWarning.role()
                : this.page == Page.JOB
                        ? this.selectedRole
                        : null;
        return scopedRole == null ? PreviewScope.NONE : new PreviewScope("", scopedRole.label());
    }

    private List<ClipboardStorageOutlineRenderer.WorkforceMarker> workforceMarkers() {
        List<ClipboardStorageOutlineRenderer.WorkforceMarker> markers = new ArrayList<>();
        for (WorkerRow worker : this.snapshot.workers()) {
            var location = worker.location();
            if (location == null) {
                continue;
            }
            markers.add(new ClipboardStorageOutlineRenderer.WorkforceMarker(
                    worker.villagerId(),
                    ResourceKey.create(Registries.DIMENSION, location.dimension()),
                    location.position(),
                    worker.displayName(),
                    roleName(worker.role()).getString(),
                    statusName(worker.status()).getString(),
                    previewTarget(worker.target()),
                    hasWarning(worker)));
        }
        return List.copyOf(markers);
    }

    private static BlockPos previewTarget(String target) {
        if (target == null || target.isBlank()) {
            return null;
        }
        String[] coordinates = target.trim().split("\\s+");
        if (coordinates.length != 3) {
            return null;
        }
        try {
            return new BlockPos(
                    Integer.parseInt(coordinates[0]),
                    Integer.parseInt(coordinates[1]),
                    Integer.parseInt(coordinates[2]));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Set<String> warningOwnerNames() {
        Set<String> owners = new LinkedHashSet<>();
        for (WorkerRow worker : this.snapshot.workers()) {
            if (hasWarning(worker)) {
                owners.add(worker.displayName());
            }
        }
        return Set.copyOf(owners);
    }

    private Set<String> trackedAssignmentJobNames() {
        Set<String> jobs = new LinkedHashSet<>();
        for (HiredVillagerRole role : this.trackedAssignmentRoles) {
            jobs.add(role.label());
        }
        return Set.copyOf(jobs);
    }

    private Set<String> trackedAssignmentOwnerNames() {
        Set<String> owners = new LinkedHashSet<>();
        for (WorkerRow worker : this.snapshot.workers()) {
            if (this.trackedAssignmentRoles.contains(worker.role())) {
                owners.add(worker.displayName());
            }
        }
        return Set.copyOf(owners);
    }

    private void sendPreviewConfiguration() {
        ClipboardStorageOutlineRenderer.ClipboardPreviewLens lens = this.activePreviewTab == null
                ? ClipboardStorageOutlineRenderer.ClipboardPreviewLens.NONE
                : this.activePreviewTab.lens();
        List<String> trackedJobs = this.trackedAssignmentRoles.stream()
                .map(HiredVillagerRole::serializedName)
                .toList();
        PacketDistributor.sendToServer(new ClipboardPreviewTogglePayload(
                this.activePreviewTab != null,
                lens.name().toLowerCase(java.util.Locale.ROOT),
                trackedJobs));
    }

    private Set<String> scopeOwnerNames(PreviewScope scope) {
        if (!scope.ownerName().isBlank()) {
            return Set.of(scope.ownerName());
        }
        if (scope.jobName().isBlank()) {
            return Set.of();
        }
        Set<String> owners = new LinkedHashSet<>();
        for (WorkerRow worker : this.snapshot.workers()) {
            if (worker.role().label().equalsIgnoreCase(scope.jobName())) {
                owners.add(worker.displayName());
            }
        }
        return Set.copyOf(owners);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.closingWithAnimation) {
            return true;
        }
        if (scrollY != 0.0D) {
            if (this.page == Page.JOB) {
                int direction = -(int) Math.signum(scrollY);
                if (turnWorkerPage(direction)) {
                    playPageSound();
                }
                return true;
            }
            float scale = panelScale();
            int left = panelLeft(scale);
            int top = panelTop(scale) + slideOffsetY(scale);
            double panelMouseX = (mouseX - left) / scale;
            double panelMouseY = (mouseY - top) / scale;
            if ((this.page == Page.OVERVIEW
                    && panelMouseX >= SUMMARY_LEFT
                    && panelMouseX < SUMMARY_RIGHT
                    && panelMouseY >= Math.max(TEXT_CONTENT_TOP, JOB_DESCRIPTION_TOP)
                    && panelMouseY < TEXT_CONTENT_BOTTOM)
                    || (this.page == Page.ASSIGNMENT_TRACKER
                    && panelMouseX >= JOB_DETAIL_BAND_LEFT
                    && panelMouseX < JOB_DETAIL_BAND_RIGHT
                    && panelMouseY >= assignmentTrackingRowsTop()
                    && panelMouseY < TEXT_CONTENT_BOTTOM)
                    || (isWarningPage()
                    && panelMouseX >= SUMMARY_LEFT
                    && panelMouseX < SUMMARY_RIGHT
                    && panelMouseY >= SUMMARY_TOP
                    && panelMouseY < TEXT_CONTENT_BOTTOM)) {
                int maxScroll = this.page == Page.ASSIGNMENT_TRACKER
                        ? maxAssignmentTrackingScroll()
                        : isWarningPage() ? maxWarningPageScroll() : maxJobScroll();
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
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && this.page != Page.OVERVIEW) {
            navigateBack();
            playPageSound();
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

    @Override
    public void removed() {
        PacketDistributor.sendToServer(new ClipboardWorkforceSubscriptionPayload(false));
        super.removed();
    }









    private boolean routeAssigned(WorkerRow worker) {
        return worker != null && "route".equals(worker.areaStatus());
    }



    private void renderJobSitePage(GuiGraphics graphics, double mouseX, double mouseY) {
        Component title = Component.translatable("villagerretaliation.gui.clipboard_workforce.job_site");
        graphics.drawString(
                this.font,
                title,
                (TEXTURE_WIDTH - this.font.width(title)) / 2,
                WORKFORCE_TITLE_TOP,
                TEXT,
                false);
        renderCenteredDivider(graphics, TITLE_DIVIDER_TOP);
        if (this.selectedWorker == null) {
            graphics.drawString(
                    this.font,
                    Component.translatable("villagerretaliation.gui.clipboard_workforce.job_site_unavailable"),
                    SUMMARY_LEFT,
                    SUMMARY_TOP,
                    MUTED,
                    false);
            return;
        }

        int y = SUMMARY_TOP;
        drawArrowLine(
                graphics,
                false,
                Component.literal(this.selectedWorker.displayName()),
                SUMMARY_LEFT,
                y,
                TEXT);
        y += JOB_SITE_ROW_STEP;
        Component centerLine = Component.translatable(
                "villagerretaliation.gui.clipboard_workforce.job_site_center",
                this.selectedWorker.workAreaCenter());
        y = drawJobSiteWrappedLine(
                graphics,
                centerLine,
                y,
                this.selectedWorker.noWorkArea() ? WARNING : MUTED);
        Component rangeLine = Component.translatable(
                "villagerretaliation.gui.clipboard_workforce.job_site_range",
                this.selectedWorker.horizontalRadius(),
                this.selectedWorker.verticalRadius());
        graphics.drawString(
                this.font,
                rangeLine,
                SUMMARY_LEFT,
                y,
                this.selectedWorker.noWorkArea() ? WARNING : MUTED,
                false);
        y += JOB_SITE_ROW_STEP;
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
                Component.translatable("villagerretaliation.gui.clipboard_workforce.job_site_expand_horizontal"),
                List.of(
                        new JobSiteButton(Component.translatable("villagerretaliation.gui.direction.north_short"), ClipboardWorkAreaActionPayload.Action.EXPAND_NORTH),
                        new JobSiteButton(Component.translatable("villagerretaliation.gui.direction.east_short"), ClipboardWorkAreaActionPayload.Action.EXPAND_EAST),
                        new JobSiteButton(Component.translatable("villagerretaliation.gui.direction.south_short"), ClipboardWorkAreaActionPayload.Action.EXPAND_SOUTH),
                        new JobSiteButton(Component.translatable("villagerretaliation.gui.direction.west_short"), ClipboardWorkAreaActionPayload.Action.EXPAND_WEST)));
        y = drawJobSiteDirectionRow(
                graphics,
                mouseX,
                mouseY,
                y,
                Component.translatable("villagerretaliation.gui.clipboard_workforce.job_site_contract_horizontal"),
                List.of(
                        new JobSiteButton(Component.translatable("villagerretaliation.gui.direction.north_short"), ClipboardWorkAreaActionPayload.Action.CONTRACT_NORTH),
                        new JobSiteButton(Component.translatable("villagerretaliation.gui.direction.east_short"), ClipboardWorkAreaActionPayload.Action.CONTRACT_EAST),
                        new JobSiteButton(Component.translatable("villagerretaliation.gui.direction.south_short"), ClipboardWorkAreaActionPayload.Action.CONTRACT_SOUTH),
                        new JobSiteButton(Component.translatable("villagerretaliation.gui.direction.west_short"), ClipboardWorkAreaActionPayload.Action.CONTRACT_WEST)));
        y = drawJobSiteDirectionRow(
                graphics,
                mouseX,
                mouseY,
                y,
                Component.translatable("villagerretaliation.gui.clipboard_workforce.job_site_expand_vertical"),
                List.of(
                        new JobSiteButton(Component.translatable("villagerretaliation.gui.direction.up"), ClipboardWorkAreaActionPayload.Action.EXPAND_UP),
                        new JobSiteButton(Component.translatable("villagerretaliation.gui.direction.down"), ClipboardWorkAreaActionPayload.Action.EXPAND_DOWN)));
        drawJobSiteDirectionRow(
                graphics,
                mouseX,
                mouseY,
                y,
                Component.translatable("villagerretaliation.gui.clipboard_workforce.job_site_contract_vertical"),
                List.of(
                        new JobSiteButton(Component.translatable("villagerretaliation.gui.direction.up"), ClipboardWorkAreaActionPayload.Action.CONTRACT_UP),
                        new JobSiteButton(Component.translatable("villagerretaliation.gui.direction.down"), ClipboardWorkAreaActionPayload.Action.CONTRACT_DOWN)));
    }

    private void renderRoutePage(GuiGraphics graphics) {
        Component title = Component.translatable("villagerretaliation.gui.clipboard_workforce.route.title");
        graphics.drawString(
                this.font,
                title,
                (TEXTURE_WIDTH - this.font.width(title)) / 2,
                WORKFORCE_TITLE_TOP,
                TEXT,
                false);
        renderCenteredDivider(graphics, TITLE_DIVIDER_TOP);
        if (this.selectedWorker == null || !routeAssigned(this.selectedWorker)) {
            graphics.drawString(
                    this.font,
                    Component.translatable("villagerretaliation.gui.clipboard_workforce.route.unavailable"),
                    SUMMARY_LEFT,
                    SUMMARY_TOP,
                    MUTED,
                    false);
            return;
        }

        int y = SUMMARY_TOP;
        drawArrowLine(
                graphics,
                false,
                Component.literal(this.selectedWorker.displayName()),
                SUMMARY_LEFT,
                y,
                TEXT);
        y += SUMMARY_ROW_STEP;
        y = drawWrappedDetailLine(graphics, Component.translatable(
                "villagerretaliation.gui.clipboard_workforce.route.assignment",
                this.selectedWorker.workAreaCenter()), y, true, TEXT);
        y = drawWrappedDetailLine(graphics, Component.translatable(
                "villagerretaliation.gui.clipboard_workforce.job_detail.status",
                statusName(this.selectedWorker.status()).copy().withColor(statusColor(this.selectedWorker))),
                y, false, TEXT);
        if (!this.selectedWorker.target().isBlank()) {
            y = drawWrappedDetailLine(graphics, Component.translatable(
                    "villagerretaliation.gui.clipboard_workforce.route.target",
                    this.selectedWorker.target()), y, true, TEXT);
        }
        var location = this.selectedWorker.location();
        y = drawWrappedDetailLine(graphics, Component.translatable(
                "villagerretaliation.gui.clipboard_workforce.route.location",
                location.position().getX(),
                location.position().getY(),
                location.position().getZ()), y, false, TEXT);
        y += GLOBAL_TEXT_GAP;
        for (net.minecraft.util.FormattedCharSequence line : this.font.split(
                Component.translatable("villagerretaliation.gui.clipboard_workforce.route.read_only"),
                SUMMARY_RIGHT - SUMMARY_LEFT)) {
            graphics.drawString(this.font, line, SUMMARY_LEFT, y, MUTED, false);
            y += this.font.lineHeight;
            if (y >= TEXT_CONTENT_BOTTOM) {
                break;
            }
        }
    }





    private int drawJobSiteActionRow(
            GuiGraphics graphics,
            double mouseX,
            double mouseY,
            int y,
            Component label,
            ClipboardWorkAreaActionPayload.Action action) {
        int rowBottom = y + this.font.lineHeight;
        boolean hovered = contains(mouseX, mouseY, JOB_DETAIL_BAND_LEFT, y - 2, JOB_DETAIL_BAND_RIGHT, rowBottom);
        if (hovered) {
            graphics.fill(JOB_DETAIL_BAND_LEFT, y - 2, JOB_DETAIL_BAND_RIGHT, rowBottom, HOVER_FILL);
        }
        graphics.drawString(this.font, fit(label.getString(), SUMMARY_RIGHT - SUMMARY_LEFT - ROW_ARROW_TEXT_OFFSET),
                SUMMARY_LEFT, y, TEXT, false);
        graphics.blit(
                VillagerRetaliationClientAssets.CLIPBOARD_ROW_ARROW_TEXTURE,
                SUMMARY_RIGHT - ROW_ARROW_WIDTH,
                y,
                0.0F,
                0.0F,
                ROW_ARROW_WIDTH,
                ROW_ARROW_HEIGHT,
                ROW_ARROW_WIDTH,
                ROW_ARROW_HEIGHT);
        this.rowActions.add(RowAction.jobSiteAction(
                this.selectedWorker,
                action,
                JOB_DETAIL_BAND_LEFT,
                y - 2,
                JOB_DETAIL_BAND_RIGHT,
                rowBottom));
        return y + JOB_SITE_ROW_STEP;
    }

    private int drawJobSiteDirectionRow(
            GuiGraphics graphics,
            double mouseX,
            double mouseY,
            int y,
            Component label,
            List<JobSiteButton> buttons) {
        graphics.drawString(this.font, label, SUMMARY_LEFT, y, MUTED, false);
        int rowBottom = y + this.font.lineHeight;
        int buttonCount = Math.max(1, buttons.size());
        int buttonLeft = SUMMARY_LEFT + 48;
        int buttonWidth = Math.max(18, (SUMMARY_RIGHT - buttonLeft) / buttonCount);
        for (int index = 0; index < buttons.size(); index++) {
            JobSiteButton button = buttons.get(index);
            int left = buttonLeft + index * buttonWidth;
            int right = index == buttons.size() - 1 ? SUMMARY_RIGHT : left + buttonWidth - 2;
            boolean hovered = contains(mouseX, mouseY, left, y - 2, right, rowBottom);
            if (hovered) {
                graphics.fill(left, y - 2, right, rowBottom, HOVER_FILL);
            }
            String buttonText = fit(button.label().getString(), right - left - 2);
            int labelLeft = left + Math.max(0, (right - left - this.font.width(buttonText)) / 2);
            int labelTop = y - 2 + Math.max(0, (rowBottom - (y - 2) - this.font.lineHeight) / 2) + 1;
            graphics.drawString(this.font, buttonText, labelLeft, labelTop, TEXT, false);
            this.rowActions.add(RowAction.jobSiteAction(
                    this.selectedWorker,
                    button.action(),
                    jobSiteButtonTooltip(label, button.label()),
                    left,
                    y - 2,
                    right,
                    rowBottom));
        }
        return y + JOB_SITE_ROW_STEP;
    }

    private int drawJobSiteWrappedLine(GuiGraphics graphics, Component text, int y, int color) {
        List<net.minecraft.util.FormattedCharSequence> lines = this.font.split(
                text,
                SUMMARY_RIGHT - SUMMARY_LEFT);
        int lineY = y;
        for (net.minecraft.util.FormattedCharSequence line : lines) {
            graphics.drawString(this.font, line, SUMMARY_LEFT, lineY, color, false);
            lineY += JOB_SITE_ROW_STEP;
        }
        return lineY;
    }

    private int drawWrappedDetailLine(
            GuiGraphics graphics,
            Component text,
            int y,
            boolean banded,
            int color) {
        List<net.minecraft.util.FormattedCharSequence> lines = this.font.split(
                text,
                SUMMARY_RIGHT - SUMMARY_LEFT);
        int blockHeight = Math.max(this.font.lineHeight, lines.size() * this.font.lineHeight);
        if (banded) {
            graphics.fill(
                    JOB_DETAIL_BAND_LEFT,
                    y - 2,
                    JOB_DETAIL_BAND_RIGHT,
                    y + blockHeight,
                    JOB_DETAIL_BAND_COLOR);
        }
        int lineY = y;
        for (net.minecraft.util.FormattedCharSequence line : lines) {
            graphics.drawString(this.font, line, SUMMARY_LEFT, lineY, color, false);
            lineY += this.font.lineHeight;
        }
        return lineY + 2;
    }

    private List<Component> jobSiteButtonTooltip(Component group, Component direction) {
        return List.of(
                Component.translatable(
                        "villagerretaliation.gui.clipboard_workforce.job_site_adjust",
                        group,
                        direction),
                Component.translatable("villagerretaliation.gui.clipboard_workforce.job_site_shift_hint")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
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



    private List<WorkerRow> workersForSelectedRole() {
        return this.snapshot.workers().stream()
                .filter(worker -> worker.role() == this.selectedRole)
                .sorted(Comparator.comparing(WorkerRow::displayName))
                .toList();
    }



    private void openOverview() {
        this.page = Page.OVERVIEW;
        this.selectedWorker = null;
        this.selectedWarning = null;
        this.warningScroll = 0;
    }

    private void openJob(HiredVillagerRole role) {
        if (role == null) {
            return;
        }
        this.selectedRole = role;
        this.page = Page.JOB;
        this.selectedWorker = this.snapshot.workers().stream()
                .filter(worker -> worker.role() == role)
                .sorted(Comparator.comparing(WorkerRow::displayName, String.CASE_INSENSITIVE_ORDER))
                .findFirst()
                .orElse(null);
    }

    private void openJobSite(WorkerRow worker) {
        if (worker == null) {
            return;
        }
        this.selectedWorker = worker;
        this.selectedRole = worker.role();
        this.page = Page.JOB_SITE;
    }

    private void openWorkerAssignment(WorkerRow worker) {
        if (worker == null) {
            return;
        }
        if (routeAssigned(worker)) {
            this.selectedWorker = worker;
            this.selectedRole = worker.role();
            this.page = Page.ROUTE;
            return;
        }
        openJobSite(worker);
    }

    private void navigateBack() {
        if (this.page == Page.JOB_SITE || this.page == Page.ROUTE) {
            this.page = Page.JOB;
            return;
        }
        if (this.page == Page.WORKER_ERRORS) {
            this.page = Page.WARNING_WORKERS;
            this.selectedWorker = null;
            this.warningScroll = 0;
            return;
        }
        if (this.page == Page.WARNING_WORKERS) {
            this.page = Page.WARNINGS;
            this.selectedWarning = null;
            this.warningScroll = 0;
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
        String key = warning.type() == ClipboardWorkforceSnapshot.WarningType.NO_TARGETS
                        && warning.role() == HiredVillagerRole.ANIMAL_HANDLING
                ? "villagerretaliation.gui.clipboard_workforce.notice.animal_handling_waiting"
                : "villagerretaliation.gui.clipboard_workforce.warning."
                        + warning.type().name().toLowerCase(java.util.Locale.ROOT);
        return Component.translatable(key, warning.count(), roleName(warning.role()));
    }

    private Component workforceStatusSummary(String prefix) {
        int warnings = actionableWarningCount();
        if (warnings > 0) {
            return Component.translatable(prefix + ".warnings", warnings);
        }
        int waiting = waitingNoticeCount();
        return Component.translatable(prefix + (waiting > 0 ? ".waiting" : ".warnings"), waiting);
    }

    private int workforceStatusColor() {
        return hasActionableWarnings() ? WARNING : waitingNoticeCount() > 0 ? NOTICE : TEXT;
    }

    private int actionableWarningCount() {
        return this.snapshot.warnings().stream().filter(w -> !isWaitingNotice(w.type())).mapToInt(WarningSummary::count).sum();
    }

    private int waitingNoticeCount() {
        return this.snapshot.warnings().stream().filter(w -> isWaitingNotice(w.type())).mapToInt(WarningSummary::count).sum();
    }

    private boolean hasActionableWarnings() {
        return actionableWarningCount() > 0;
    }

    private boolean isWaitingNotice(ClipboardWorkforceSnapshot.WarningType type) {
        return type == ClipboardWorkforceSnapshot.WarningType.MISSING_MATERIALS
                || type == ClipboardWorkforceSnapshot.WarningType.NO_TARGETS;
    }

    private int warningColor(ClipboardWorkforceSnapshot.WarningType type) {
        return isWaitingNotice(type) ? NOTICE : WARNING;
    }

    private int warningColor(int count) {
        return count > 0 ? WARNING : TEXT;
    }


    private boolean hasActionableWarning(WorkerRow worker) {
        return worker.inventoryFull() || worker.unpaid() || worker.noStorage() || worker.noWorkArea()
                || worker.tooFar() || worker.missingTools() || worker.storageFull()
                || worker.materialStorageUnreachable() || worker.materialInventoryFull()
                || worker.buildSiteUnreachable();
    }

    private boolean hasWaitingNotice(WorkerRow worker) {
        return worker.noTargets() || worker.missingMaterials();
    }

    private boolean hasWarning(WorkerRow worker) {
        return hasActionableWarning(worker) || hasWaitingNotice(worker);
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

    private int contractDurationColor(int days) {
        if (days <= 1) {
            return WARNING;
        }
        return days <= 3 ? IDLE : SUCCESS;
    }

    private int statusColor(WorkerRow worker) {
        if (hasActionableWarning(worker)) {
            return WARNING;
        }
        if (hasWaitingNotice(worker)) {
            return NOTICE;
        }
        return switch (worker.status()) {
            case PATHING -> PATHING;
            case OUTPUT_BACKPRESSURE -> NOTICE;
            case WAITING, WAITING_FOR_CROPS -> IDLE;
            case UNKNOWN -> MUTED;
            case WORKING,
                    MINING,
                    LOGGING,
                    FARMING,
                    BREWING,
                    COOKING,
                    SMELTING,
                    COURIERING,
                    BUILDING,
                    DEPOSITING -> SUCCESS;
            case NO_WORK_AREA,
                    NO_TARGETS,
                    NO_STORAGE,
                    STORAGE_FULL,
                    INVENTORY_FULL,
                    MISSING_TOOLS,
                    UNPAID,
                    TOO_FAR,
                    MISSING_MATERIALS,
                    MATERIAL_STORAGE_UNREACHABLE,
                    MATERIAL_INVENTORY_FULL,
                    BUILD_SITE_UNREACHABLE -> WARNING;
        };
    }

    private void playBookSound(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F, pitch));
    }

    private boolean contains(double x, double y, int left, int top, int right, int bottom) {
        return x >= left && x <= right && y >= top && y <= bottom;
    }

    boolean isJobSitePage() {
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
        private final float durationMillis;
        private float startVisibility;
        private float targetVisibility;
        private long startMillis;

        private TabSlideAnimation() {
            this(SCROLL_TAB_ANIMATION_DURATION_MILLIS);
        }

        private TabSlideAnimation(float durationMillis) {
            this.durationMillis = Math.max(1.0F, durationMillis);
        }

        private void reset(boolean visible) {
            this.startVisibility = 0.0F;
            this.targetVisibility = visible ? 1.0F : 0.0F;
            this.startMillis = Util.getMillis();
        }

        private void setVisible(boolean visible) {
            setVisibility(visible ? 1.0F : 0.0F);
        }

        private void setVisibility(float target) {
            target = Mth.clamp(target, 0.0F, 1.0F);
            if (this.targetVisibility == target) {
                return;
            }
            this.startVisibility = visibility();
            this.targetVisibility = target;
            this.startMillis = Util.getMillis();
        }

        private float visibility() {
            float progress = Mth.clamp(
                    (Util.getMillis() - this.startMillis) / this.durationMillis,
                    0.0F,
                    1.0F);
            float eased = progress * progress * (3.0F - 2.0F * progress);
            return Mth.lerp(eased, this.startVisibility, this.targetVisibility);
        }
    }

    private enum Page {
        OVERVIEW,
        ASSIGNMENT_TRACKER,
        JOB,
        JOB_SITE,
        ROUTE,
        WARNINGS,
        WARNING_WORKERS,
        WORKER_ERRORS,
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
        WORKFORCE(
                1,
                ClipboardStorageOutlineRenderer.ClipboardPreviewLens.WORKFORCE,
                "villagerretaliation.gui.clipboard_workforce.preview_tab.workforce",
                "villagerretaliation.gui.clipboard_workforce.preview_tab.workforce.detail",
                0xFF6E82FF),
        ASSIGNMENTS(
                2,
                ClipboardStorageOutlineRenderer.ClipboardPreviewLens.ASSIGNMENTS,
                "villagerretaliation.gui.clipboard_workforce.preview_tab.assignments",
                "villagerretaliation.gui.clipboard_workforce.preview_tab.assignments.detail",
                0xFF7654C8),
        PROBLEMS(
                3,
                ClipboardStorageOutlineRenderer.ClipboardPreviewLens.PROBLEMS,
                "villagerretaliation.gui.clipboard_workforce.preview_tab.problems",
                "villagerretaliation.gui.clipboard_workforce.preview_tab.problems.detail",
                0xFF2C9D68);

        private final int tabNumber;
        private final ClipboardStorageOutlineRenderer.ClipboardPreviewLens lens;
        private final String titleKey;
        private final String detailKey;
        private final int titleColor;

        ClipboardPreviewTab(
                int tabNumber,
                ClipboardStorageOutlineRenderer.ClipboardPreviewLens lens,
                String titleKey,
                String detailKey,
                int titleColor) {
            this.tabNumber = tabNumber;
            this.lens = lens;
            this.titleKey = titleKey;
            this.detailKey = detailKey;
            this.titleColor = titleColor;
        }

        ClipboardStorageOutlineRenderer.ClipboardPreviewLens lens() {
            return this.lens;
        }

        List<Component> tooltip(PreviewScope scope, boolean active) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(this.titleKey).withColor(this.titleColor));
            lines.add(Component.translatable(this.detailKey).withStyle(ChatFormatting.GRAY));
            if (this == ASSIGNMENTS) {
                lines.add(Component.translatable(
                                "villagerretaliation.gui.clipboard_workforce.preview_tab.click_configure")
                        .withStyle(ChatFormatting.DARK_GRAY));
            } else if (this == PROBLEMS && !scope.isEmpty()) {
                lines.add(Component.translatable(
                        "villagerretaliation.gui.clipboard_workforce.preview_tab.scope",
                        scope.label()).withStyle(ChatFormatting.DARK_GRAY));
            }
            if (this != ASSIGNMENTS) {
                lines.add(Component.translatable(active
                                ? "villagerretaliation.gui.clipboard_workforce.preview_tab.click_disable"
                                : "villagerretaliation.gui.clipboard_workforce.preview_tab.click_enable")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            return List.copyOf(lines);
        }

        static ClipboardPreviewTab byTabNumber(int tabNumber) {
            for (ClipboardPreviewTab tab : values()) {
                if (tab.tabNumber == tabNumber) {
                    return tab;
                }
            }
            return null;
        }

        static ClipboardPreviewTab fromLens(ClipboardStorageOutlineRenderer.ClipboardPreviewLens lens) {
            for (ClipboardPreviewTab tab : values()) {
                if (tab.lens == lens) {
                    return tab;
                }
            }
            return null;
        }
    }

    private record PreviewScope(String ownerName, String jobName) {
        private static final PreviewScope NONE = new PreviewScope("", "");

        private PreviewScope {
            ownerName = ownerName == null ? "" : ownerName;
            jobName = jobName == null ? "" : jobName;
        }

        private boolean isEmpty() {
            return this.ownerName.isBlank() && this.jobName.isBlank();
        }

        private Component label() {
            if (!this.ownerName.isBlank()) {
                return Component.literal(this.ownerName);
            }
            return Component.literal(this.jobName);
        }
    }


    private record JobListRow(HiredVillagerRole role, Component label, int count) {
    }

    private record WarningDisplayEntry(
            WarningSummary warning,
            List<net.minecraft.util.FormattedCharSequence> lines,
            boolean banded) {
    }

    private record WorkerErrorDisplayEntry(
            List<net.minecraft.util.FormattedCharSequence> lines,
            boolean banded,
            boolean notice) {
    }

    private record JobSiteButton(Component label, ClipboardWorkAreaActionPayload.Action action) {
    }

    private record DetailTooltip(String titleKey, String detailKey) {
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
