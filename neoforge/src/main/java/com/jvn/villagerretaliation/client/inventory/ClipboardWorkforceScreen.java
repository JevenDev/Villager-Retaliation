package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceSnapshot;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceSnapshot.WarningSummary;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceSnapshot.WarningType;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceSnapshot.WorkerRow;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

public final class ClipboardWorkforceScreen extends Screen {
    private static final int TEXTURE_WIDTH = 145;
    private static final int TEXTURE_HEIGHT = 194;
    private static final int CONTENT_LEFT = 13;
    private static final int CONTENT_RIGHT = 133;
    private static final int CONTENT_TOP = 42;
    private static final int CONTENT_BOTTOM = 181;
    private static final int TITLE_Y = 34;
    private static final int PAGE_BUTTON_WIDTH = 23;
    private static final int PAGE_BUTTON_HEIGHT = 13;
    private static final int PAGE_BUTTON_LEFT = CONTENT_RIGHT - PAGE_BUTTON_WIDTH;
    private static final int PAGE_BUTTON_TOP = CONTENT_BOTTOM - PAGE_BUTTON_HEIGHT;
    private static final int FIRST_OVERVIEW_PAGE_LAST_ROLE = HiredVillagerRole.NAVIGATION.ordinal();
    private static final ResourceLocation PAGE_FORWARD = ResourceLocation.withDefaultNamespace("widget/page_forward");
    private static final ResourceLocation PAGE_FORWARD_HIGHLIGHTED = ResourceLocation.withDefaultNamespace("widget/page_forward_highlighted");
    private static final ResourceLocation PAGE_BACKWARD = ResourceLocation.withDefaultNamespace("widget/page_backward");
    private static final ResourceLocation PAGE_BACKWARD_HIGHLIGHTED = ResourceLocation.withDefaultNamespace("widget/page_backward_highlighted");
    private static final int TEXT = 0xFF4B2B1D;
    private static final int MUTED = 0xFF8B6247;
    private static final int WARNING = 0xFF9A3B24;
    private static final int HOVER_FILL = 0x30A66A34;
    private static final int SELECTED_FILL = 0x3DA65C2B;
    private static final int ROW_HEIGHT = 12;

    private final ClipboardWorkforceSnapshot snapshot;
    private final List<RowAction> rowActions = new ArrayList<>();
    private Page page = Page.OVERVIEW;
    private HiredVillagerRole selectedRole = HiredVillagerRole.MINING;
    private int selectedOverviewRow;
    private int overviewPage;
    private int workerScroll;

    public ClipboardWorkforceScreen(ClipboardWorkforceSnapshot snapshot) {
        super(Component.translatable("villagerretaliation.gui.clipboard_workforce.title"));
        this.snapshot = snapshot == null ? ClipboardWorkforceSnapshot.empty() : snapshot;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        this.rowActions.clear();
        float scale = panelScale();
        int left = panelLeft(scale);
        int top = panelTop(scale);
        double panelMouseX = (mouseX - left) / scale;
        double panelMouseY = (mouseY - top) / scale;

        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.blit(
                VillagerRetaliationClientAssets.CLIPBOARD_WORKFORCE_TEXTURE,
                0,
                0,
                0.0F,
                0.0F,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT);
        switch (this.page) {
            case OVERVIEW -> renderOverview(graphics, panelMouseX, panelMouseY);
            case JOB -> renderJobPage(graphics, panelMouseX, panelMouseY);
            case STORAGE -> renderStoragePage(graphics, panelMouseX, panelMouseY);
            case PAYMENT -> renderPaymentPage(graphics, panelMouseX, panelMouseY);
        }
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        float scale = panelScale();
        double panelMouseX = (mouseX - panelLeft(scale)) / scale;
        double panelMouseY = (mouseY - panelTop(scale)) / scale;
        for (RowAction row : this.rowActions) {
            if (!row.contains(panelMouseX, panelMouseY)) {
                continue;
            }
            switch (row.kind()) {
                case BACK -> openOverview();
                case PAGE_TURN -> turnOverviewPage();
                case JOB -> openJob(row.role());
                case STORAGE -> {
                    this.page = Page.STORAGE;
                    this.workerScroll = 0;
                }
                case PAYMENT -> {
                    this.page = Page.PAYMENT;
                    this.workerScroll = 0;
                }
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.page != Page.JOB) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int maxScroll = Math.max(0, workersForSelectedRole().size() - visibleWorkerRows());
        this.workerScroll = Mth.clamp(this.workerScroll - (int) Math.signum(scrollY), 0, maxScroll);
        return maxScroll > 0;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return switch (keyCode) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                if (this.page == Page.OVERVIEW) {
                    this.minecraft.setScreen(null);
                } else {
                    openOverview();
                }
                yield true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                openOverview();
                yield true;
            }
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> {
                moveOverviewSelection(-1);
                yield true;
            }
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> {
                moveOverviewSelection(1);
                yield true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                activateOverviewSelection();
                yield true;
            }
            default -> super.keyPressed(keyCode, scanCode, modifiers);
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
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
        drawLine(graphics, Component.translatable("villagerretaliation.gui.clipboard_workforce.warnings", this.snapshot.warningCount()), CONTENT_LEFT, y, warningColor(this.snapshot.warningCount()));
        y += 14;
        drawSmallHeader(graphics, Component.translatable("villagerretaliation.gui.clipboard_workforce.jobs"), y);
        y += 10;
        int rowIndex = 0;
        for (OverviewRow row : overviewRows()) {
            boolean selected = this.selectedOverviewRow == rowIndex;
            y = drawNavigationRow(graphics, mouseX, mouseY, y, selected, row.label(), row.value(), row.kind(), row.role(), row.muted());
            rowIndex++;
        }
        y += 2;
        renderWarnings(graphics, mouseX, mouseY, y);
        renderOverviewPageButton(graphics, mouseX, mouseY);
    }

    private void renderWarnings(GuiGraphics graphics, double mouseX, double mouseY, int startY) {
        if (this.snapshot.warnings().isEmpty() || startY > CONTENT_BOTTOM - 10) {
            return;
        }
        drawSmallHeader(graphics, Component.translatable("villagerretaliation.gui.clipboard_workforce.warning_header"), startY);
        int y = startY + 10;
        int shown = Math.min(3, this.snapshot.warnings().size());
        for (int index = 0; index < shown && y <= CONTENT_BOTTOM - 9; index++) {
            WarningSummary warning = this.snapshot.warnings().get(index);
            boolean hovered = contains(mouseX, mouseY, CONTENT_LEFT, y - 1, CONTENT_RIGHT, y + 9);
            if (hovered) {
                graphics.fill(CONTENT_LEFT - 2, y - 2, CONTENT_RIGHT + 1, y + 9, HOVER_FILL);
            }
            drawLine(graphics, warningText(warning), CONTENT_LEFT, y, WARNING);
            this.rowActions.add(new RowAction(RowKind.JOB, warning.role(), CONTENT_LEFT - 2, y - 2, CONTENT_RIGHT + 1, y + 9));
            y += 10;
        }
        int more = this.snapshot.warnings().size() - shown;
        if (more > 0 && y <= CONTENT_BOTTOM - 9) {
            drawLine(graphics, Component.translatable("villagerretaliation.gui.clipboard_workforce.more_warnings", more), CONTENT_LEFT, y, MUTED);
        }
    }

    private void renderJobPage(GuiGraphics graphics, double mouseX, double mouseY) {
        renderBackRow(graphics, mouseX, mouseY);
        drawCentered(graphics, roleName(this.selectedRole), TITLE_Y, TEXT);
        List<WorkerRow> workers = workersForSelectedRole();
        if (workers.isEmpty()) {
            int y = CONTENT_TOP + 22;
            drawLine(graphics, Component.translatable("villagerretaliation.gui.clipboard_workforce.no_workers"), CONTENT_LEFT, y, MUTED);
            drawLine(graphics, Component.translatable("villagerretaliation.gui.clipboard_workforce.controls_coming"), CONTENT_LEFT, y + 12, MUTED);
            return;
        }
        int maxScroll = Math.max(0, workers.size() - visibleWorkerRows());
        this.workerScroll = Mth.clamp(this.workerScroll, 0, maxScroll);
        int y = CONTENT_TOP + 12;
        int end = Math.min(workers.size(), this.workerScroll + visibleWorkerRows());
        for (int index = this.workerScroll; index < end; index++) {
            WorkerRow worker = workers.get(index);
            renderWorkerRow(graphics, mouseX, mouseY, worker, y);
            y += 35;
        }
        if (maxScroll > 0) {
            drawCentered(graphics, Component.translatable("villagerretaliation.gui.clipboard_workforce.page_count", this.workerScroll + 1, maxScroll + 1), CONTENT_BOTTOM - 4, MUTED);
        }
    }

    private void renderWorkerRow(GuiGraphics graphics, double mouseX, double mouseY, WorkerRow worker, int y) {
        boolean hovered = contains(mouseX, mouseY, CONTENT_LEFT - 2, y - 2, CONTENT_RIGHT + 1, y + 31);
        if (hovered) {
            graphics.fill(CONTENT_LEFT - 2, y - 2, CONTENT_RIGHT + 1, y + 31, HOVER_FILL);
        }
        drawLine(graphics, Component.literal(fit(worker.displayName(), CONTENT_RIGHT - CONTENT_LEFT - 12)), CONTENT_LEFT, y, TEXT);
        if (hasWarning(worker)) {
            drawLine(graphics, Component.literal("!"), CONTENT_RIGHT - 6, y, WARNING);
        }
        drawLine(graphics, Component.translatable("villagerretaliation.gui.clipboard_workforce.worker_status", statusName(worker.status())), CONTENT_LEFT, y + 10, mutedForWarning(worker));
        Component target = worker.target().isBlank()
                ? Component.translatable("villagerretaliation.gui.clipboard_workforce.not_assigned")
                : Component.literal(worker.target());
        drawLine(graphics, Component.translatable("villagerretaliation.gui.clipboard_workforce.worker_target", target), CONTENT_LEFT, y + 19, MUTED);
        drawLine(graphics, Component.translatable("villagerretaliation.gui.clipboard_workforce.worker_storage",
                worker.storageAssigned()
                        ? Component.translatable("villagerretaliation.gui.clipboard_workforce.assigned")
                        : Component.translatable("villagerretaliation.gui.clipboard_workforce.missing"),
                worker.workRadius(),
                worker.dailyWage()), CONTENT_LEFT, y + 28, worker.noStorage() ? WARNING : MUTED);
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
        int unpaid = (int) this.snapshot.workers().stream().filter(WorkerRow::unpaid).count();
        int y = CONTENT_TOP + 18;
        drawLine(graphics, Component.translatable("villagerretaliation.gui.clipboard_workforce.emerald_balance", this.snapshot.emeraldBalance()), CONTENT_LEFT, y, TEXT);
        drawLine(graphics, Component.translatable("villagerretaliation.gui.clipboard_workforce.daily_wages", this.snapshot.dailyWages()), CONTENT_LEFT, y + 14, TEXT);
        drawLine(graphics, Component.translatable("villagerretaliation.gui.clipboard_workforce.unpaid_workers", unpaid), CONTENT_LEFT, y + 28, warningColor(unpaid));
        drawWrapped(graphics, Component.translatable("villagerretaliation.gui.clipboard_workforce.payment_placeholder"), y + 48);
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
        boolean hovered = contains(mouseX, mouseY, CONTENT_LEFT - 2, y - 1, CONTENT_RIGHT + 1, y + ROW_HEIGHT - 1);
        if (selected || hovered) {
            graphics.fill(CONTENT_LEFT - 2, y - 2, CONTENT_RIGHT + 1, y + ROW_HEIGHT - 1, selected ? SELECTED_FILL : HOVER_FILL);
        }
        drawLine(graphics, Component.literal(selected ? ">" : ""), CONTENT_LEFT - 8, y, TEXT);
        drawLine(graphics, label, CONTENT_LEFT, y, muted ? MUTED : TEXT);
        drawRight(graphics, Component.literal(value), CONTENT_RIGHT, y, muted ? MUTED : TEXT);
        this.rowActions.add(new RowAction(kind, role, CONTENT_LEFT - 2, y - 2, CONTENT_RIGHT + 1, y + ROW_HEIGHT - 1));
        return y + ROW_HEIGHT;
    }

    private void renderBackRow(GuiGraphics graphics, double mouseX, double mouseY) {
        boolean hovered = contains(mouseX, mouseY, CONTENT_LEFT - 2, CONTENT_TOP - 2, CONTENT_LEFT + 42, CONTENT_TOP + 9);
        if (hovered) {
            graphics.fill(CONTENT_LEFT - 2, CONTENT_TOP - 2, CONTENT_LEFT + 42, CONTENT_TOP + 9, HOVER_FILL);
        }
        drawLine(graphics, Component.translatable("villagerretaliation.gui.back"), CONTENT_LEFT, CONTENT_TOP, TEXT);
        this.rowActions.add(new RowAction(RowKind.BACK, null, CONTENT_LEFT - 2, CONTENT_TOP - 2, CONTENT_LEFT + 42, CONTENT_TOP + 9));
    }

    private void renderOverviewPageButton(GuiGraphics graphics, double mouseX, double mouseY) {
        boolean hovered = contains(mouseX, mouseY, PAGE_BUTTON_LEFT, PAGE_BUTTON_TOP, PAGE_BUTTON_LEFT + PAGE_BUTTON_WIDTH, PAGE_BUTTON_TOP + PAGE_BUTTON_HEIGHT);
        ResourceLocation sprite = this.overviewPage == 0
                ? hovered ? PAGE_FORWARD_HIGHLIGHTED : PAGE_FORWARD
                : hovered ? PAGE_BACKWARD_HIGHLIGHTED : PAGE_BACKWARD;
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
        drawLine(graphics, left, CONTENT_LEFT, y, TEXT);
        drawRight(graphics, right, CONTENT_RIGHT, y, TEXT);
    }

    private void drawSmallHeader(GuiGraphics graphics, Component text, int y) {
        drawLine(graphics, text, CONTENT_LEFT, y, MUTED);
        graphics.fill(CONTENT_LEFT, y + 8, CONTENT_RIGHT, y + 9, 0x5A7A442F);
    }

    private void drawWrapped(GuiGraphics graphics, Component text, int y) {
        int lineY = y;
        for (net.minecraft.util.FormattedCharSequence line : this.font.split(text, CONTENT_RIGHT - CONTENT_LEFT)) {
            graphics.drawString(this.font, line, CONTENT_LEFT, lineY, MUTED, false);
            lineY += 10;
            if (lineY > CONTENT_BOTTOM - 8) {
                return;
            }
        }
    }

    private void drawLine(GuiGraphics graphics, Component text, int x, int y, int color) {
        graphics.drawString(this.font, text, x, y, color, false);
    }

    private void drawRight(GuiGraphics graphics, Component text, int right, int y, int color) {
        graphics.drawString(this.font, text, right - this.font.width(text), y, color, false);
    }

    private void drawCentered(GuiGraphics graphics, Component text, int y, int color) {
        graphics.drawString(this.font, text, (TEXTURE_WIDTH - this.font.width(text)) / 2, y, color, false);
    }

    private List<WorkerRow> workersForSelectedRole() {
        return this.snapshot.workers().stream()
                .filter(worker -> worker.role() == this.selectedRole)
                .sorted(Comparator.comparing(WorkerRow::displayName))
                .toList();
    }

    private int visibleWorkerRows() {
        return Math.max(1, (CONTENT_BOTTOM - (CONTENT_TOP + 12)) / 35);
    }

    private void openOverview() {
        this.page = Page.OVERVIEW;
        this.workerScroll = 0;
    }

    private void openJob(HiredVillagerRole role) {
        if (role == null) {
            return;
        }
        this.selectedRole = role;
        this.page = Page.JOB;
        this.workerScroll = 0;
    }

    private void moveOverviewSelection(int direction) {
        if (this.page != Page.OVERVIEW) {
            return;
        }
        int rowCount = overviewRows().size();
        this.selectedOverviewRow = Mth.clamp(this.selectedOverviewRow + direction, 0, Math.max(0, rowCount - 1));
    }

    private void activateOverviewSelection() {
        if (this.page != Page.OVERVIEW) {
            return;
        }
        List<OverviewRow> rows = overviewRows();
        if (this.selectedOverviewRow < 0 || this.selectedOverviewRow >= rows.size()) {
            return;
        }
        OverviewRow row = rows.get(this.selectedOverviewRow);
        switch (row.kind()) {
            case JOB -> openJob(row.role());
            case STORAGE -> this.page = Page.STORAGE;
            case PAYMENT -> this.page = Page.PAYMENT;
            default -> {
            }
        }
    }

    private void turnOverviewPage() {
        this.overviewPage = this.overviewPage == 0 ? 1 : 0;
        this.selectedOverviewRow = 0;
    }

    private List<OverviewRow> overviewRows() {
        List<OverviewRow> rows = new ArrayList<>();
        for (ClipboardWorkforceSnapshot.JobSummary job : this.snapshot.jobs()) {
            boolean firstPageRole = job.role().ordinal() <= FIRST_OVERVIEW_PAGE_LAST_ROLE;
            if ((this.overviewPage == 0) != firstPageRole) {
                continue;
            }
            rows.add(new OverviewRow(
                    RowKind.JOB,
                    job.role(),
                    roleName(job.role()),
                    Integer.toString(job.count()),
                    job.count() == 0));
        }
        if (this.overviewPage == 1) {
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
                    Integer.toString(this.snapshot.emeraldBalance()),
                    false));
        }
        return rows;
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
        return worker.inventoryFull() || worker.unpaid() || worker.noStorage() || worker.noTargets() || worker.tooFar();
    }

    private String fit(String text, int width) {
        if (this.font.width(text) <= width) {
            return text;
        }
        return this.font.plainSubstrByWidth(text, Math.max(0, width - this.font.width("..."))) + "...";
    }

    private boolean contains(double x, double y, int left, int top, int right, int bottom) {
        return x >= left && x <= right && y >= top && y <= bottom;
    }

    private float panelScale() {
        float fitWidth = (this.width - 12.0F) / TEXTURE_WIDTH;
        float fitHeight = (this.height - 12.0F) / TEXTURE_HEIGHT;
        return Mth.clamp(Math.min(fitWidth, fitHeight), 0.72F, 1.35F);
    }

    private int panelLeft(float scale) {
        return Math.round((this.width - TEXTURE_WIDTH * scale) / 2.0F);
    }

    private int panelTop(float scale) {
        return Math.round((this.height - TEXTURE_HEIGHT * scale) / 2.0F);
    }

    private enum Page {
        OVERVIEW,
        JOB,
        STORAGE,
        PAYMENT
    }

    private enum RowKind {
        BACK,
        PAGE_TURN,
        JOB,
        STORAGE,
        PAYMENT
    }

    private record OverviewRow(RowKind kind, HiredVillagerRole role, Component label, String value, boolean muted) {
    }

    private record RowAction(RowKind kind, HiredVillagerRole role, int left, int top, int right, int bottom) {
        private boolean contains(double x, double y) {
            return x >= this.left && x <= this.right && y >= this.top && y <= this.bottom;
        }
    }
}
