package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.network.SettlementManagementActionResultPayload;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.network.SettlementManagementPayload.Entry;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page;
import com.jvn.villagerretaliation.settlement.SettlementPermission;
import com.jvn.villagerretaliation.settlement.SettlementProject;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class SettlementManagementScreen extends Screen {
    private SettlementManagementPayload snapshot;
    private Page desiredPage;
    private int desiredIndex;
    private String query = "";
    private int requestId;
    private long refreshAt;
    private long nextRequestAt;
    private boolean submitting;
    private final SettlementManagementTracker submission = new SettlementManagementTracker();
    private Screen submittedFrom;
    private boolean resultLate;
    private final net.minecraft.client.multiplayer.ClientPacketListener connection = net.minecraft.client.Minecraft.getInstance().getConnection();
    private final net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
    private boolean viewRequested;
    private Button worldView;
    private UUID selected;
    private Component feedback = Component.empty();
    private long feedbackUntil;
    private Button previous;
    private Button next;
    private Rows rows;
    private EditBox search;
    private final Button[] actions = new Button[7];
    private UUID pendingResident;
    private int pendingResidentAction;
    private final SettlementManagementScreen parent;
    private final SettlementManagementPayload.Resident assigning;
    private SettlementBuildingDraft buildingDraft;
    private final SettlementManagementPayload.Bed choosingBed;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int contentLeft;
    private int contentWidth;

    public SettlementManagementScreen(SettlementManagementPayload snapshot) {
        this(snapshot, null, null, null);
    }

    private SettlementManagementScreen(SettlementManagementPayload snapshot, SettlementManagementScreen parent,
                                       SettlementManagementPayload.Resident assigning, SettlementManagementPayload.Bed choosingBed) {
        super(text("title"));
        this.snapshot = snapshot;
        this.parent = parent;
        this.assigning = assigning;
        this.choosingBed = choosingBed;
        this.desiredPage = choosingBed != null ? Page.RESIDENTS : assigning == null ? snapshot.page() : Page.AREAS;
        this.desiredIndex = assigning == null && choosingBed == null ? snapshot.index() : 0;
        this.query = assigning == null && choosingBed == null ? snapshot.query() : "";
        this.requestId = snapshot.requestId();
        this.refreshAt = Util.getMillis() + (assigning == null && choosingBed == null ? 2_000L : 0L);
    }

    SettlementManagementScreen root() {
        return this.parent == null ? this : this.parent.root();
    }

    public SettlementManagementPayload snapshot() {
        return this.snapshot;
    }

    public void accept(SettlementManagementPayload payload) {
        if (!this.snapshot.dimension().equals(payload.dimension()) || payload.requestId() < this.requestId
                || payload.header() != null && this.snapshot.header() != null && !payload.header().id().equals(this.snapshot.header().id())) {
            return;
        }
        if (payload.page() == Page.TOWNS && payload.header() == null) {
            this.desiredPage = Page.TOWNS;
            this.desiredIndex = payload.index();
            this.query = payload.query();
        } else if (payload.page() != this.desiredPage || !payload.query().equals(this.query)) {
            return;
        }
        boolean changedAccess = (this.snapshot.header() == null) != (payload.header() == null);
        this.snapshot = payload;
        this.desiredIndex = payload.index();
        if (this.selected == null && payload.entries().size() == 1 && payload.entries().getFirst().id().toString().equals(this.query)) {
            this.selected = payload.entries().getFirst().id();
        }
        if (changedAccess) {
            rebuildWidgets();
        } else {
            updateSearch();
            populateRows();
            updateActions();
        }
        if (this.pendingResident != null && this.minecraft.screen == this && currentPage()) {
            UUID resident = this.pendingResident;
            this.pendingResident = null;
            if (selectedEntry() instanceof SettlementManagementPayload.Resident selected && selected.id().equals(resident)
                    && permits(SettlementPermission.STAFF) && this.actions[this.pendingResidentAction].active) {
                perform(this.pendingResidentAction);
            }
        }
    }

    public boolean result(SettlementManagementActionResultPayload payload) {
        if (!validSession()) return false;
        boolean timely = this.submission.busy();
        if (!this.submission.accept(payload)) return false;
        this.submitting = false;
        this.viewRequested = false;
        this.resultLate = !timely;
        this.feedback = resultMessage(payload);
        this.feedbackUntil = Util.getMillis() + 8_000L;
        this.refreshAt = Util.getMillis();
        if (this.buildingDraft != null) this.buildingDraft.submitting = false;
        updateActions();
        return this.minecraft.screen == this.submittedFrom;
    }

    static Component resultMessage(SettlementManagementActionResultPayload payload) {
        return payload.applied() ? text("applied") : Component.translatableWithFallback(
                "villagerretaliation.gui.settlement.failure." + payload.failure(),
                text("failure.state_changed").getString());
    }

    boolean validSession() {
        var client = net.minecraft.client.Minecraft.getInstance();
        return client.getConnection() == this.connection && client.level != null && client.level == this.level
                && client.player != null && client.player.isAlive() && !client.player.isSpectator()
                && client.level.dimension().location().equals(this.snapshot.dimension());
    }

    void updateSubmission() {
        if (!validSession()) {
            this.submission.clear();
            this.submitting = false;
            return;
        }
        if (this.submission.timeout(Util.getMillis())) {
            this.submitting = false;
            this.viewRequested = false;
            this.feedback = text("acknowledgement_timeout");
            this.feedbackUntil = Util.getMillis() + 15_000L;
            this.refreshAt = Util.getMillis();
            if (this.minecraft.screen instanceof SettlementViewScreen view) view.refreshManagement();
            if (this.buildingDraft != null) {
                this.buildingDraft.submitting = false;
                this.buildingDraft.failure = "timeout";
            }
            updateActions();
        }
    }

    boolean resultWasLate() { return this.resultLate; }

    boolean unconfirmed() { return this.submission.unconfirmed(); }

    public void submit(SettlementManagementActionPayload.Command command) {
        if (this.submitting || this.snapshot.header() == null || !validSession()) return;
        var payload = new SettlementManagementActionPayload(this.snapshot.header().id(), command);
        this.submission.sent(payload, Util.getMillis());
        this.submittedFrom = this.minecraft.screen;
        this.submitting = true;
        this.feedback = text("saving");
        PacketDistributor.sendToServer(payload);
        updateActions();
    }

    public boolean permits(SettlementPermission permission) {
        return this.snapshot.header() != null && this.snapshot.header().permissions().contains(permission);
    }

    public boolean leader() {
        return this.snapshot.header() != null && this.minecraft != null && this.minecraft.player != null
                && this.snapshot.header().leader().equals(this.minecraft.player.getUUID());
    }

    boolean expectingView() {
        return this.viewRequested && this.submitting;
    }

    void navigateTo(Page page, String query) {
        navigate(page, 0, query);
    }

    public boolean submitting() {
        return this.submitting;
    }

    @Override
    protected void init() {
        var layout = SettlementViewClient.panelLayout(this.width, this.height, Math.min(700, this.width - 24),
                Math.min(420, this.height - 24), false);
        this.panelWidth = layout.width();
        this.panelHeight = layout.height();
        this.left = layout.left();
        this.top = layout.top();
        this.contentLeft = this.left + 102;
        this.contentWidth = this.panelWidth - 102;
        this.worldView = addRenderableWidget(Button.builder(text("world_view"), ignored -> {
            this.pendingResident = null;
            if (!SettlementViewClient.resume(this)) {
                this.viewRequested = true;
                submit(new SettlementManagementActionPayload.View());
            }
        }).bounds(this.left + this.panelWidth - 84, this.top - 4, 84, 20).build());
        this.worldView.visible = this.snapshot.header() != null && this.assigning == null && this.choosingBed == null;
        this.worldView.setTooltip(Tooltip.create(text("world_view_hint")));
        int y = this.top + 28;
        int navigationHeight = Math.min(20, (this.panelHeight - 56) / (Page.values().length - 2) - 2);
        for (Page page : Page.values()) {
            if (page == Page.STATIONS || page == Page.STORAGE) continue;
            if (page != Page.TOWNS && this.snapshot.header() == null || this.assigning != null && page != Page.AREAS
                    || this.choosingBed != null && page != Page.RESIDENTS) {
                continue;
            }
            Button button = addRenderableWidget(Button.builder(enumText("page", page), ignored -> navigate(page, 0, ""))
                    .bounds(this.left, y, 94, navigationHeight).build());
            button.active = page != this.desiredPage;
            y += navigationHeight + 2;
        }
        addRenderableWidget(Button.builder(Component.translatable(this.parent == null ? "gui.done" : "gui.back"), ignored -> onClose())
                .bounds(this.left, this.top + this.panelHeight - 20, 94, 20).build());
        this.search = new EditBox(this.font, this.contentLeft, this.top + 30, this.contentWidth, 20, text("search"));
        this.search.setMaxLength(64);
        this.search.setHint(text("search"));
        updateSearch();
        this.search.visible = this.desiredPage != Page.SUMMARY;
        this.search.active = this.search.visible;
        this.search.setResponder(this::searchChanged);
        addRenderableWidget(this.search);
        int actionCount = actionColumns();
        int extraActions = extraActionHeight();
        this.rows = addRenderableWidget(new Rows(this.contentLeft, this.top + 56, this.contentWidth,
                Math.max(36, this.panelHeight - 112 - extraActions)));
        int bottom = this.top + this.panelHeight;
        this.previous = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> navigate(this.desiredPage, this.desiredIndex - 1, this.query))
                .bounds(this.contentLeft, bottom - 46 - extraActions, 24, 20).build());
        this.next = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> navigate(this.desiredPage, this.desiredIndex + 1, this.query))
                .bounds(this.contentLeft + 28, bottom - 46 - extraActions, 24, 20).build());
        addRenderableWidget(Button.builder(text("refresh"), ignored -> {
            this.refreshAt = Util.getMillis();
        }).bounds(this.contentLeft + this.contentWidth - 64, bottom - 46 - extraActions, 64, 20).build());
        int actionWidth = (this.contentWidth - (actionCount - 1) * 4) / actionCount;
        for (int index = 0; index < this.actions.length; index++) {
            int action = index;
            this.actions[index] = addRenderableWidget(Button.builder(Component.empty(), ignored -> perform(action))
                    .bounds(this.contentLeft + (actionWidth + 4) * (index % actionCount),
                            bottom - 20 - extraActions + (index / actionCount) * 24, actionWidth, 20).build());
        }
        populateRows();
        updateActions();
    }

    private int actionColumns() {
        int columns = this.desiredPage == Page.PROJECTS || this.desiredPage == Page.ISSUES ? 4 : 3;
        return this.contentWidth < 360 ? 2 : columns;
    }

    private int extraActionHeight() {
        int count = switch (this.desiredPage) {
            case RESIDENTS -> 7;
            case SUMMARY, AREAS -> 5;
            case PROJECTS, ISSUES -> 4;
            default -> 3;
        };
        return ((count - 1) / actionColumns()) * 24;
    }

    private boolean currentPage() {
        return this.snapshot.page() == this.desiredPage && this.snapshot.index() == this.desiredIndex
                && this.snapshot.query().equals(this.query);
    }

    private void populateRows() {
        double scroll = this.rows.getScrollAmount();
        this.rows.reset();
        if (!currentPage()) {
            this.rows.add(null, text("loading"), Component.empty());
        } else if (this.desiredPage == Page.SUMMARY) {
            addSummary();
        } else if (this.snapshot.entries().isEmpty()) {
            this.rows.add(null, text(this.desiredPage == Page.TOWNS ? "no_towns"
                    : this.desiredPage == Page.ISSUES && this.query.isBlank() ? "no_issues" : "empty"), Component.empty());
        } else {
            for (Entry entry : this.snapshot.entries()) {
                this.rows.add(entry, name(entry), detail(entry));
            }
        }
        this.rows.setClampedScrollAmount(scroll);
        this.previous.active = currentPage() && this.desiredIndex > 0;
        this.next.active = currentPage() && (long) (this.desiredIndex + 1)
                * SettlementManagementRequestPayload.PAGE_SIZE < this.snapshot.total();
    }

    private void addSummary() {
        var header = this.snapshot.header();
        if (header == null) {
            return;
        }
        this.rows.add(null, text("issues_count", header.issues()), text("issues_hint"));
        this.rows.add(null, text(header.manualManagement() ? "manual_management" : "autonomous_management"), text("management_hint"));
        this.rows.add(null, text("population", header.population(), header.housing()), text("housing_hint"));
        this.rows.add(null, text("growth_target", header.growthTarget()), text("growth_target_hint"));
        for (var stage : com.jvn.villagerretaliation.settlement.SettlementStage.values()) {
            if (stage.ordinal() == 0) continue;
            this.rows.add(null, text("effective_population", enumText("stage", stage), header.populations().get(stage.ordinal())),
                    text(header.populationAdjusted() ? "population_adjusted" : "population_requirement_hint"));
        }
        header.stage().next().filter(next -> header.population() < header.populations().get(next.ordinal())
                && header.growthTarget() < header.populations().get(next.ordinal())).ifPresent(next ->
                this.rows.add(null, text("requirement.population_cap"), text("population_cap_detail", header.growthTarget(),
                        enumText("stage", next), header.populations().get(next.ordinal()))));
        if (!header.populationProblem().isBlank()) {
            this.rows.add(null, text("requirement.population_configuration"), Component.literal(header.populationProblem()));
        }
        this.rows.add(null, text("food", decimal(header.foodDays())), text("food_hint"));
        this.rows.add(null, text("stage", enumText("stage", header.stage())),
                Component.translatable("villagerretaliation.gui.settlement_view.plot_totals",
                        header.plots().owned(), header.plots().capacity(), header.plots().free(), header.plots().spendable().decimal(2)));
        if (!header.plots().conflict().isEmpty()) this.rows.add(null, Component.literal(header.plots().conflict()), Component.empty());
        this.rows.add(null, text("support", decimal(header.supportTicks() / 24_000.0D), decimal(header.requiredTicks() / 24_000.0D)),
                text(header.ready() ? "ready" : "support_hint"));
        if (header.shortageTicks() > 0L) {
            this.rows.add(null, text("shortage", decimal(header.shortageTicks() / 24_000.0D)), text("shortage_hint"));
        }
        this.rows.add(null, text("capacity", header.projectLimit(), header.depotLimit()),
                text("market_capacity", header.marketSupply(), header.marketDemand()));
        for (var missing : header.missing()) {
            this.rows.add(null, enumText("requirement", missing), text("needed"));
        }
        this.rows.add(null, text("treasury_balance", header.balance().mixedFraction()), text("treasury_limits",
                header.reserve().mixedFraction(), header.spent().mixedFraction(), header.budget().mixedFraction()));
    }

    private void updateSearch() {
        String value = this.snapshot.entries().stream().filter(entry -> entry.id().toString().equals(this.query))
                .map(entry -> name(entry).getString()).findFirst().orElse(this.query);
        if (!this.search.getValue().equals(value)) {
            this.search.setResponder(null);
            this.search.setValue(value);
            this.search.setResponder(this::searchChanged);
        }
    }

    private void searchChanged(String value) {
        this.pendingResident = null;
        this.query = value;
        this.desiredIndex = 0;
        this.refreshAt = Util.getMillis() + 350L;
        updateActions();
    }

    void navigate(Page page, int index, String query) {
        this.pendingResident = null;
        this.desiredPage = page;
        this.desiredIndex = Math.max(0, index);
        this.query = query;
        this.selected = null;
        this.feedback = Component.empty();
        this.refreshAt = Util.getMillis();
        rebuildWidgets();
    }

    void restoreSelection(UUID selection) {
        this.selected = selection;
        populateRows();
        updateActions();
    }

    private Entry selectedEntry() {
        return this.snapshot.entries().stream().filter(entry -> entry.id().equals(this.selected)).findFirst().orElse(null);
    }

    private void updateActions() {
        if (this.actions[0] == null) {
            return;
        }
        for (Button action : this.actions) {
            action.visible = false;
            action.active = false;
        }
        this.worldView.active = permits(SettlementPermission.VIEW) && !this.submitting;
        Entry selected = selectedEntry();
        if (this.choosingBed != null) {
            action(0, "assign_home", selected instanceof SettlementManagementPayload.Resident && permits(SettlementPermission.STAFF));
            return;
        }
        if (this.assigning != null) {
            boolean suitable = selected instanceof SettlementManagementPayload.Area area
                    && area.kind().role() != null && this.assigning.roles().contains(area.kind().role());
            action(0, "assign", suitable && permits(SettlementPermission.STAFF));
            return;
        }
        switch (this.desiredPage) {
            case ISSUES -> {
                action(0, "issue_open", selected instanceof SettlementManagementPayload.Issue);
                if (selected instanceof SettlementManagementPayload.Issue issue) {
                    if (issue.kind() == SettlementManagementPayload.IssueKind.WORKER) {
                        action(1, issue.enabled() ? "pause_worker" : "resume_worker", permits(SettlementPermission.STAFF));
                        action(2, "assign", permits(SettlementPermission.STAFF));
                        action(3, "worker_settings", permits(SettlementPermission.STAFF));
                    } else if (issue.kind() == SettlementManagementPayload.IssueKind.PROJECT) {
                        action(1, "project_builder", true);
                        action(2, "cancel_project", permits(SettlementPermission.BUILD));
                        action(3, "issue_area", true);
                    }
                }
            }
            case BEDS -> {
                action(0, "assign_home", permits(SettlementPermission.STAFF) && selected instanceof SettlementManagementPayload.Bed bed && bed.available());
                action(1, "release_home", permits(SettlementPermission.STAFF) && selected instanceof SettlementManagementPayload.Bed bed && bed.resident() != null);
                action(2, "bed_resident", selected instanceof SettlementManagementPayload.Bed bed && bed.resident() != null);
            }
            case PROJECTS -> {
                action(0, "new_building", permits(SettlementPermission.BUILD)
                        && this.snapshot.header() != null && this.snapshot.header().projectLimit() > 0);
                action(1, "cancel_project", permits(SettlementPermission.BUILD)
                        && selected instanceof SettlementManagementPayload.Project project && project.status() == SettlementProject.Status.ACTIVE);
                action(2, "project_builder", selected != null);
                action(3, "project_crew", selected instanceof SettlementManagementPayload.Project);
                this.actions[3].setMessage(Component.literal("Crew and priority"));

            }
            case TOWNS -> action(0, "open", selected != null);
            case SUMMARY -> {
                action(0, "advance", leader() && this.snapshot.header() != null && this.snapshot.header().ready());
                action(1, "treasury", permits(SettlementPermission.TREASURY));
                action(2, "budget", permits(SettlementPermission.TREASURY));
                action(3, "page.issues", permits(SettlementPermission.VIEW));
                action(4, this.snapshot.header() != null && this.snapshot.header().manualManagement() ? "enable_autonomy" : "manage_manually", leader());
            }
            case RESIDENTS -> {
                action(0, "assign", permits(SettlementPermission.STAFF) && selected instanceof SettlementManagementPayload.Resident resident
                        && !resident.baby() && !resident.roles().isEmpty());
                action(5, "automatic_staffing", permits(SettlementPermission.STAFF)
                        && selected instanceof SettlementManagementPayload.Resident resident && resident.manualStaffing());
                action(6, "automatic_staffing_all", permits(SettlementPermission.STAFF));
                action(1, "release", leader() && selected instanceof SettlementManagementPayload.Resident resident && resident.area() != null);
                action(3, "worker_settings", permits(SettlementPermission.STAFF) && selected instanceof SettlementManagementPayload.Resident resident
                        && resident.area() != null && resident.settings() != null);
                action(2, "inventory", permits(SettlementPermission.LOGISTICS) && selected instanceof SettlementManagementPayload.Resident resident
                        && resident.area() != null && resident.presence() != com.jvn.villagerretaliation.network.VillageOverviewPayload.Presence.UNLOADED);
                action(4, selected instanceof SettlementManagementPayload.Resident resident && resident.settings() != null
                                && !resident.settings().enabled() ? "resume_worker" : "pause_worker",
                        permits(SettlementPermission.STAFF) && selected instanceof SettlementManagementPayload.Resident resident && resident.settings() != null);
            }
            case AREAS -> {
                action(0, "add", permits(SettlementPermission.BUILD));
                action(1, "edit", selected != null && permits(SettlementPermission.BUILD));
                action(2, "remove", selected != null && permits(SettlementPermission.BUILD));
                action(3, "worksite_settings", permits(SettlementPermission.STAFF)
                        && selected instanceof SettlementManagementPayload.Area area && area.kind().role() != null);
                action(4, "worksite_fill", permits(SettlementPermission.STAFF)
                        && selected instanceof SettlementManagementPayload.Area area && area.plan() != null);
            }
            case DEPOTS -> {
                action(0, "edit", selected != null && permits(SettlementPermission.LOGISTICS));
                action(1, "stock_target", selected != null && permits(SettlementPermission.LOGISTICS));
            }
            case APPLICANTS -> {
                action(0, "approve", permits(SettlementPermission.STAFF) && selected instanceof SettlementManagementPayload.Applicant applicant && !applicant.approved());
                action(1, "decline", selected != null && permits(SettlementPermission.STAFF));
            }
            case TRADE -> {
                action(0, "add", permits(SettlementPermission.TRADE));
                action(1, "edit", selected != null && permits(SettlementPermission.TRADE));
                action(2, "remove", selected != null && permits(SettlementPermission.TRADE));
            }
            case DELEGATES -> {
                action(0, "add", leader());
                action(1, "edit", selected != null && leader());
                action(2, "transfer", selected != null && leader());
            }
        }
    }

    private void action(int index, String key, boolean active) {
        Button action = this.actions[index];
        action.visible = true;
        action.active = active && !this.submitting && currentPage();
        action.setMessage(text(key));
        action.setTooltip(Tooltip.create(text(key + "_hint")));
    }

    private void perform(int action) {
        if (!this.actions[action].active) return;
        Entry selected = selectedEntry();
        if (this.choosingBed != null && selected instanceof SettlementManagementPayload.Resident resident) {
            this.minecraft.setScreen(new SettlementConfirmScreen(this, text("assign_home"),
                    text("assign_home_review", name(resident), this.choosingBed.position().toShortString()), () -> {
                        this.minecraft.setScreen(this.parent);
                        this.parent.submit(new SettlementManagementActionPayload.Home(resident.id(), this.choosingBed.position(), true));
                    }));
            return;
        }
        if (this.assigning != null && selected instanceof SettlementManagementPayload.Area area) {
            Runnable assign = () -> {
                this.minecraft.setScreen(this.parent);
                this.parent.submit(new SettlementManagementActionPayload.Assign(this.assigning.id(), area.id()));
            };
            if (this.assigning.area() != null && !this.assigning.area().equals(area.id())) {
                confirm("reassign_resident", area, assign);
            } else {
                assign.run();
            }
            return;
        }
        switch (this.desiredPage) {
            case ISSUES -> {
                if (selected instanceof SettlementManagementPayload.Issue issue) {
                    if (action == 0) {
                        navigate(issue.target(), 0, issue.targetId() == null ? "" : issue.targetId().toString());
                    } else if (issue.kind() == SettlementManagementPayload.IssueKind.WORKER) {
                        if (action == 1) {
                            submit(new SettlementManagementActionPayload.WorkerEnabled(issue.resident(), issue.area(), !issue.enabled()));
                            navigate(Page.RESIDENTS, 0, issue.resident().toString());
                        } else {
                            navigate(Page.RESIDENTS, 0, issue.resident().toString());
                            this.pendingResident = issue.resident();
                            this.pendingResidentAction = action == 2 ? 0 : 3;
                        }
                    } else if (issue.kind() == SettlementManagementPayload.IssueKind.PROJECT) {
                        if (action == 1) {
                            navigate(Page.RESIDENTS, 0, issue.resident().toString());
                        } else if (action == 2) {
                            confirm("cancel_project", issue, () -> submit(new SettlementManagementActionPayload.CancelProject(issue.targetId())));
                        } else {
                            navigate(Page.AREAS, 0, issue.area().toString());
                        }
                    }
                }
            }
            case BEDS -> {
                if (selected instanceof SettlementManagementPayload.Bed bed) {
                    if (action == 0) {
                        this.minecraft.setScreen(new SettlementManagementScreen(this.snapshot, this, null, bed));
                    } else if (action == 1) {
                        this.minecraft.setScreen(new SettlementConfirmScreen(this, text("release_home"),
                                text("release_home_review", bed.position().toShortString()), () ->
                                    submit(new SettlementManagementActionPayload.Home(bed.resident(), bed.position(), false))));
                    } else if (bed.resident() != null) {
                        navigate(Page.RESIDENTS, 0, bed.resident().toString());
                    }
                }
            }
            case PROJECTS -> {
                if (action == 0) {
                    newBuilding();
                } else if (action == 1 && selected != null) {
                    confirm("cancel_project", selected, () -> submit(new SettlementManagementActionPayload.CancelProject(selected.id())));
                } else if (selected instanceof SettlementManagementPayload.Project project) {
                    if (action == 3) this.minecraft.setScreen(new SettlementProjectScreen(this, project, null));
                    else navigate(Page.RESIDENTS, 0, project.builder().toString());
                }
            }
            case TOWNS -> {
                if (selected != null) {
                    PacketDistributor.sendToServer(new SettlementManagementRequestPayload(selected.id(), Page.SUMMARY, 0, "", 0, true));
                }
            }
            case SUMMARY -> {
                if (action == 0) {
                    submit(new SettlementManagementActionPayload.Advance());
                } else if (action == 3) {
                    navigate(Page.ISSUES, 0, "");
                } else if (action == 4) {
                    submit(new SettlementManagementActionPayload.Management(!this.snapshot.header().manualManagement()));
                } else {
                    edit(action == 1 ? SettlementEditScreen.Mode.TREASURY : SettlementEditScreen.Mode.BUDGET, null);
                }
            }
            case RESIDENTS -> {
                if (action == 6) {
                    this.minecraft.setScreen(new SettlementConfirmScreen(this, text("automatic_staffing_all"),
                            text("automatic_staffing_all_hint"), () -> submit(new SettlementManagementActionPayload.AutomaticStaffing(null))));
                    return;
                }
                if (selected instanceof SettlementManagementPayload.Resident resident) {
                    if (action == 0) {
                        this.minecraft.setScreen(new SettlementManagementScreen(this.snapshot, this, resident, null));
                    } else if (action == 1) {
                        confirm("release_resident", resident, () -> submit(new SettlementManagementActionPayload.Release(resident.id())));
                    } else if (action == 2) {
                        submit(new SettlementManagementActionPayload.Inventory(resident.id()));
                    } else if (action == 5) {
                        submit(new SettlementManagementActionPayload.AutomaticStaffing(resident.id()));
                    } else if (action == 4) {
                        submit(new SettlementManagementActionPayload.WorkerEnabled(resident.id(), resident.area(), !resident.settings().enabled()));
                    } else {
                        this.minecraft.setScreen(new SettlementWorkerScreen(this, resident));
                    }
                }
            }
            case AREAS -> {
                if (action == 3 && selected instanceof SettlementManagementPayload.Area area) {
                    this.minecraft.setScreen(new SettlementWorkerScreen(this, area, null));
                } else if (action == 4 && selected instanceof SettlementManagementPayload.Area area) {
                    submit(new SettlementManagementActionPayload.FillStaffing(area.id()));
                } else if (action == 2 && selected != null) {
                    confirm("remove_area", selected, () -> submit(new SettlementManagementActionPayload.RemoveArea(selected.id())));
                } else {
                    edit(SettlementEditScreen.Mode.AREA, action == 0 ? null : selected);
                }
            }
            case DEPOTS -> edit(action == 0 ? SettlementEditScreen.Mode.DEPOT : SettlementEditScreen.Mode.STOCK, selected);
            case APPLICANTS -> {
                if (selected != null) {
                    submit(new SettlementManagementActionPayload.Applicant(selected.id(), action == 0));
                }
            }
            case TRADE -> {
                if (action == 2 && selected != null) {
                    submit(new SettlementManagementActionPayload.RemoveOrder(selected.id()));
                } else {
                    edit(SettlementEditScreen.Mode.ORDER, action == 0 ? null : selected);
                }
            }
            case DELEGATES -> {
                if (action == 2 && selected != null) {
                    confirm("transfer_leadership", selected, () -> submit(new SettlementManagementActionPayload.Transfer(selected.id())));
                } else {
                    edit(SettlementEditScreen.Mode.DELEGATE, action == 0 ? null : selected);
                }
            }
        }
    }

    SettlementBuildingDraft buildingDraft() {
        return root().buildingDraft;
    }

    void clearBuildingDraft() {
        root().buildingDraft = null;
    }

    void newBuilding() {
        this.minecraft.setScreen(new SettlementBlueprintScreen(this));
    }

    void placeBuilding(com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureCatalog.Entry prefab) {
        var draft = buildingDraft();
        if (draft == null || !draft.structure.equals(prefab.id())) {
            var replacement = new SettlementBuildingDraft(prefab.id(), prefab.menuLabel(),
                    draft == null ? this.snapshot.header().center().above() : draft.anchor);
            if (draft != null) {
                replacement.rotation = draft.rotation;
                replacement.builder = draft.builder;
                replacement.placed = draft.placed;
            }
            root().buildingDraft = replacement;
        }
        this.minecraft.setScreen(this);
        if (!SettlementViewClient.resume(this)) {
            this.viewRequested = true;
            submit(new SettlementManagementActionPayload.View());
        }
    }

    private void edit(SettlementEditScreen.Mode mode, Entry selected) {
        this.minecraft.setScreen(new SettlementEditScreen(this, mode, selected));
    }

    private void confirm(String message, Entry entry, Runnable accepted) {
        this.minecraft.setScreen(new SettlementConfirmScreen(this, text(message),
                text(message + "_hint", name(entry)), accepted));
    }

    @Override
    public void tick() {
        updateSubmission();
        long now = Util.getMillis();
        if (!this.submitting && now >= this.feedbackUntil) {
            this.feedback = Component.empty();
        }
        if (now >= this.refreshAt && now >= this.nextRequestAt && this.minecraft != null && this.minecraft.player != null) {
            this.nextRequestAt = now + 300L;
            this.refreshAt = now + 2_000L;
            UUID town = this.desiredPage == Page.TOWNS || this.snapshot.header() == null ? null : this.snapshot.header().id();
            PacketDistributor.sendToServer(new SettlementManagementRequestPayload(town, this.desiredPage,
                    this.desiredIndex, this.query, ++this.requestId, false));
        }
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ENTER && this.rows.isFocused() && this.actions[0].active) {
            perform(0);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        this.pendingResident = null;
        if (this.parent == null && SettlementViewClient.resume(this)) return;
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!SettlementViewClient.renderPanelBackground(graphics, this.left, this.top, this.panelWidth, this.panelHeight)) {
            super.renderBackground(graphics, mouseX, mouseY, partialTick);
            graphics.fill(this.left - 8, this.top - 8, this.left + this.panelWidth + 8, this.top + this.panelHeight + 8, 0xD0101010);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        Component heading = this.choosingBed != null ? text("choose_home_resident", this.choosingBed.position().toShortString())
                : this.assigning != null ? text("assign_resident", name(this.assigning))
                : this.snapshot.header() == null ? this.title : this.snapshot.header().name().isBlank()
                    ? text("unnamed") : Component.literal(this.snapshot.header().name());
        fitted(graphics, heading, this.left, this.top, this.panelWidth - (this.worldView.visible ? 92 : 0), 0xFFFFFF);
        if (this.desiredPage == Page.SUMMARY && this.snapshot.header() != null) {
            fitted(graphics, text("summary_hint"), this.contentLeft, this.top + 34, this.contentWidth, 0xD0D0D0);
        }
        int pages = Math.max(1, (this.snapshot.total() + SettlementManagementRequestPayload.PAGE_SIZE - 1) / SettlementManagementRequestPayload.PAGE_SIZE);
        renderFeedback(graphics, this.font, this.feedback.getString().isEmpty() ? text("page_count", this.desiredIndex + 1, pages) : this.feedback,
                this.contentLeft + 60, this.top + this.panelHeight - 40 - extraActionHeight(),
                this.contentWidth - 132, mouseX, mouseY);
    }

    static void renderFeedback(GuiGraphics graphics, net.minecraft.client.gui.Font font, Component message,
                               int x, int y, int width, int mouseX, int mouseY) {
        String value = message.getString();
        if (font.width(value) > width) value = font.plainSubstrByWidth(value, Math.max(0, width - font.width("..."))) + "...";
        graphics.drawString(font, value, x, y, 0xD0D0D0);
        if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 12 && !message.getString().isEmpty()) {
            graphics.renderTooltip(font, font.split(message, Math.max(100, Math.min(320, width))), mouseX, mouseY);
        }
    }

    private void fitted(GuiGraphics graphics, Component value, int x, int y, int width, int color) {
        String text = value.getString();
        if (this.font.width(text) > width) {
            text = this.font.plainSubstrByWidth(text, Math.max(0, width - this.font.width("..."))) + "...";
        }
        graphics.drawString(this.font, text, x, y, color);
    }

    public static MutableComponent text(String key, Object... arguments) {
        return Component.translatable("villagerretaliation.gui.settlement." + key, arguments);
    }

    public static MutableComponent enumText(String category, Enum<?> value) {
        return text(category + "." + value.name().toLowerCase(Locale.ROOT));
    }

    static Component name(Entry entry) {
        if (entry instanceof SettlementManagementPayload.Issue issue) {
            return switch (issue.kind()) {
                case PROGRESSION -> text("issue_progression");
                case SIMULATION -> text("issue_simulation");
                case WORKER, PROJECT, TRADE, RESIDENT -> text("issue_" + issue.kind().name().toLowerCase(Locale.ROOT),
                        issue.name().isBlank() ? text("unnamed_resident") : Component.literal(issue.name()));
            };
        }
        if (entry instanceof SettlementManagementPayload.History history) {
            Component subject = history.kind() == com.jvn.villagerretaliation.settlement.SettlementEvent.Type.STAGE_ADVANCED
                    ? text("stage." + history.name().toLowerCase(Locale.ROOT)) : Component.literal(history.name());
            return text("history." + history.kind().name().toLowerCase(Locale.ROOT), subject);
        }
        return entry.name().isBlank() ? text(entry instanceof SettlementManagementPayload.Town ? "unnamed" : "unnamed_resident")
                : Component.literal(entry.name());
    }

    static Component profession(net.minecraft.resources.ResourceLocation id) {
        return VillagerProfessionUtil.parse(id.toString()).<Component>map(profession -> Component.translatableWithFallback(
                VillagerProfessionUtil.translationKey(profession, "entity.minecraft.villager.none"),
                VillagerProfessionUtil.displayName(profession, text("unemployed").getString()))).orElse(text("unemployed"));
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    static Component detail(Entry entry) {
        return switch (entry) {
            case SettlementManagementPayload.Issue issue -> {
                if (issue.kind() == SettlementManagementPayload.IssueKind.WORKER && !issue.detail().isBlank()) {
                    yield Component.literal(issue.detail());
                }
                String key = switch (issue.kind()) {
                    case PROGRESSION -> "villagerretaliation.gui.settlement.requirement." + issue.cause();
                    case PROJECT -> "villagerretaliation.gui.settlement.project_blocker." + issue.cause();
                    case TRADE, RESIDENT, SIMULATION -> "villagerretaliation.gui.settlement.issue." + issue.cause();
                    case WORKER -> issue.cause().startsWith("workforce.")
                            ? "villagerretaliation.gui.clipboard_workforce.status." + issue.cause().substring("workforce.".length())
                            : "villagerretaliation.gui.settlement.work." + issue.cause();
                };
                yield Component.translatableWithFallback(key, text("work.needs_attention").getString());
            }
            case SettlementManagementPayload.Town town -> text("town_row", enumText("stage", town.stage()), town.population());
            case SettlementManagementPayload.Resident resident -> text("resident_row",
                    resident.baby() ? text("child") : profession(resident.profession()),
                    enumText("presence", resident.presence()), Component.translatableWithFallback(resident.status().startsWith("workforce.")
                            ? "villagerretaliation.gui.clipboard_workforce.status." + resident.status().substring("workforce.".length())
                            : "villagerretaliation.gui.settlement.work." + resident.status(), text("work.idle").getString()), text(resident.manualStaffing() ? "staffing_manual" : "staffing_automatic"));
            case SettlementManagementPayload.Bed bed -> text("bed_row", bed.position().toShortString(),
                    bed.resident() != null ? text("bed_owner", bed.residentName().isBlank() ? text("unnamed_resident") : Component.literal(bed.residentName()))
                            : text(bed.available() ? "bed_available" : "bed_unavailable"));
            case SettlementManagementPayload.Area area -> text("area_row", enumText("area", area.kind()), area.workers(),
                    area.kind() == com.jvn.villagerretaliation.settlement.SettlementAreaType.HOME ? text("beds", area.beds()) : text("fixtures", area.fixtures()));
            case SettlementManagementPayload.Station station -> Component.literal(station.position().toShortString());
            case SettlementManagementPayload.Storage storage -> Component.literal(storage.links().automatic() ? "Automatic depots" : storage.containers().size() + " storage links");
            case SettlementManagementPayload.Depot depot -> text("depot_row", enumText("depot", depot.purpose()), depot.usedSlots(), depot.items());
            case SettlementManagementPayload.Applicant applicant -> text("applicant_row",
                    profession(applicant.profession()),
                    text(applicant.approved() ? "approved" : "awaiting_approval"));
            case SettlementManagementPayload.Order order -> text("order_row", enumText("direction", order.direction()), order.target(),
                    text(order.enabled() ? "enabled" : "disabled"));
            case SettlementManagementPayload.Project project -> text("project_row",
                    project.status() == SettlementProject.Status.ACTIVE ? enumText("project_phase", project.phase())
                            : enumText("project_status", project.status()), project.placed(), project.total());
            case SettlementManagementPayload.History history -> text("history_time", history.gameTime() / 24_000L + 1L);
            case SettlementManagementPayload.Delegate delegate -> Component.literal(String.join(", ",
                    delegate.permissions().stream().map(permission -> enumText("permission", permission).getString()).toList()));
        };
    }

    private final class Rows extends ObjectSelectionList<Row> {
        private Rows(int x, int y, int width, int height) {
            super(SettlementManagementScreen.this.minecraft, width, height, y, 32);
            setX(x);
            this.centerListVertically = false;
        }

        private void reset() {
            clearEntries();
        }

        private void add(SettlementManagementPayload.Entry entry, Component primary, Component secondary) {
            Row row = new Row(entry, primary, secondary);
            addEntry(row);
            if (entry != null && entry.id().equals(SettlementManagementScreen.this.selected)) {
                setSelected(row);
            }
        }

        @Override
        public void setSelected(Row row) {
            super.setSelected(row);
            if (row != null && row.entry != null) {
                SettlementManagementScreen.this.selected = row.entry.id();
                updateActions();
            }
        }

        @Override
        public int getRowWidth() {
            return getWidth() - 12;
        }

        @Override
        protected int getScrollbarPosition() {
            return getRight() - 6;
        }

        @Override
        protected void renderListBackground(GuiGraphics graphics) {
        }

        @Override
        protected void renderListSeparators(GuiGraphics graphics) {
        }
    }

    private final class Row extends ObjectSelectionList.Entry<Row> {
        private final Entry entry;
        private final Component primary;
        private final Component secondary;

        private Row(Entry entry, Component primary, Component secondary) {
            this.entry = entry;
            this.primary = primary;
            this.secondary = secondary;
        }

        @Override
        public Component getNarration() {
            return this.primary.copy().append(". ").append(this.secondary);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0 && this.entry != null) {
                rows.setSelected(this);
                return true;
            }
            return false;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            fitted(graphics, this.primary, x + 3, y + 3, width - 8, 0xFFFFFF);
            fitted(graphics, this.secondary, x + 3, y + 15, width - 8, 0xD0D0D0);
            if (hovered) {
                List<Component> lines = new ArrayList<>(List.of(this.primary, this.secondary));
                if (this.entry instanceof SettlementManagementPayload.Issue issue) {
                    lines.add(enumText("severity", issue.diagnostic().severity()));
                    if (!issue.diagnostic().recovery().isBlank()) lines.add(Component.literal(issue.diagnostic().recovery()));
                    if (!issue.detail().isBlank() && issue.kind() != SettlementManagementPayload.IssueKind.WORKER) lines.add(issue.kind() == SettlementManagementPayload.IssueKind.PROJECT
                            ? text("project_missing", issue.detail()) : Component.literal(issue.detail()));
                    lines.add(text("issue_destination", enumText("page", issue.target())));
                } else if (this.entry instanceof SettlementManagementPayload.Resident resident) {
                    lines.add(text("automatic_staffing_hint"));
                    if (!resident.diagnostic().isBlank()) lines.add(Component.literal(resident.diagnostic()));
                    var diagnosis = com.jvn.villagerretaliation.settlement.SettlementWorkerDiagnostics.diagnostic(resident.status());
                    if (!diagnosis.recovery().isBlank() && !diagnosis.recovery().equals(resident.diagnostic())) lines.add(Component.literal(diagnosis.recovery()));
                    lines.add(text("resident_care", resident.food() < 0 ? "?" : resident.food(),
                            resident.health() < 0 ? "?" : decimal(resident.health())));
                } else if (this.entry instanceof SettlementManagementPayload.Depot depot) {
                    depot.targets().forEach(target -> lines.add(text("target_row", target.name(), target.count())));
                } else if (this.entry instanceof SettlementManagementPayload.Project project) {
                    lines.add(text("project_site", project.origin().toShortString()));
                    lines.add(Component.literal("Priority " + project.priority() + " / crew " + (project.assistants().size() + 1)));
                    lines.add(Component.literal("Lead: " + project.builderName()));
                    lines.add(text("project_materials", project.materials()));
                    if (project.status() == SettlementProject.Status.ACTIVE || !project.blocker().isBlank()) {
                        if (!project.missingMaterials().isBlank()) {
                            lines.add(text("project_missing", project.missingMaterials()));
                        }
                        if (!project.blocker().isBlank()) {
                            String reason = project.blocker().replace("interaction.work.builder.", "");
                            lines.add(Component.literal(com.jvn.villagerretaliation.settlement.SettlementWorkerDiagnostics.describe("project." + reason)));
                            lines.add(Component.translatableWithFallback("villagerretaliation.gui.settlement.project_blocker." + reason,
                                    text("project_blocked").getString()));
                        }
                    }
                } else if (this.entry instanceof SettlementManagementPayload.Order order) {
                    lines.add(text("order_limits", order.price().mixedFraction(), order.budget().mixedFraction()));
                    lines.add(text("trade_status." + order.result().toLowerCase(Locale.ROOT)));
                }
                setTooltipForNextRenderPass(lines.stream().flatMap(line -> font.split(line, Math.min(300, SettlementManagementScreen.this.width - 24)).stream()).toList());
            }
        }
    }
}
