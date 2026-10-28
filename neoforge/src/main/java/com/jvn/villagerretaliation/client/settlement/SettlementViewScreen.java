package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.client.ui.VillagerUi;
import com.jvn.villagerretaliation.client.interaction.DialogueCameraOcclusion;
import com.jvn.villagerretaliation.client.item.ConstructionBlueprintPreviewRenderer;
import com.jvn.villagerretaliation.client.settlement.SettlementViewInteraction.Mode;
import com.jvn.villagerretaliation.client.settlement.SettlementSelectionSet.Operation;
import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.network.SettlementManagementActionResultPayload;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload;
import com.jvn.villagerretaliation.network.SettlementViewBatchPayload;
import com.jvn.villagerretaliation.network.SettlementViewBatchResultPayload;
import com.jvn.villagerretaliation.network.SettlementViewRequestPayload;
import com.jvn.villagerretaliation.network.SettlementViewStatePayload;
import com.jvn.villagerretaliation.settlement.SettlementAreaType;
import com.jvn.villagerretaliation.settlement.SettlementGuardOrder;
import com.jvn.villagerretaliation.settlement.SettlementPermission;
import com.jvn.villagerretaliation.settlement.SettlementProject;
import com.jvn.villagerretaliation.settlement.SettlementViewAction;
import com.jvn.villagerretaliation.settlement.SettlementViewWindow;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class SettlementViewScreen extends Screen implements com.jvn.villagerretaliation.client.inventory.ProductionClient.Receiver,
        com.jvn.villagerretaliation.client.inventory.TransferClient.Receiver {
    static final float PITCH = SettlementViewCamera.PITCH;
    private final SettlementManagementScreen parent;
    private final UUID session;
    private final SettlementViewSnapshots snapshots;
    private final SettlementViewSelection selection = new SettlementViewSelection();
    private final SettlementViewPreferences preferences;
    private final Map<UUID, SettlementManagementPayload.Resident> roster = new LinkedHashMap<>();
    private final SettlementViewBrowser browser = new SettlementViewBrowser();
    private final SettlementOverviewMap overviewMap = new SettlementOverviewMap();
    private final SettlementMapMarkers mapMarkers = new SettlementMapMarkers();
    private final SettlementViewEntities frameEntities = new SettlementViewEntities();
    private final SettlementUiWindow sidebarWindow = new SettlementUiWindow(140, 140);
    private final SettlementUiWindow mapWindow = new SettlementUiWindow(68, 64 + SettlementUiWindow.TITLE_HEIGHT + 4);
    private final List<SettlementUiWindow> windows = new ArrayList<>(List.of(this.sidebarWindow, this.mapWindow));
    private SettlementUiWindow draggingWindow;
    private SettlementUiWindow resizingWindow;
    private final List<Button> browserRows = new ArrayList<>();
    private final List<net.minecraft.client.gui.components.AbstractWidget> browserWidgets = new ArrayList<>();
    private final SettlementSelectionInspector inspector = new SettlementSelectionInspector(this::inspectorAction, this::updateInspector,
            id -> { var row = this.roster.get(id); if (row != null) inspect(row); }, this::beginPersistentRoute);
    private final List<net.minecraft.client.gui.components.AbstractWidget> sidebarWidgets = new ArrayList<>();
    private net.minecraft.client.gui.components.EditBox search;
    private Button filterButton;
    private SettlementManagementPayload.Entry inspected;
    private double boxX = -1;
    private double boxY;
    private double boxEndX;
    private double boxEndY;
    private Operation boxOperation = Operation.REPLACE;
    private boolean boxDragging;
    private List<UUID> beforeBox = List.of();
    private List<BlockPos> bedsBeforeBox = List.of();
    private final Map<BlockPos, SettlementManagementPayload.Bed> beds = new LinkedHashMap<>();
    private final Map<UUID, SettlementManagementPayload.Area> areas = new LinkedHashMap<>();
    private final Map<UUID, SettlementManagementPayload.Project> projects = new LinkedHashMap<>();
    private final SettlementViewNavigation navigation;
    private final SettlementViewPeek peek = new SettlementViewPeek();
    private InputConstants.Key peekKey;
    private Set<UUID> peekFocus = Set.of();
    private SettlementViewPopup popup;
    private boolean panning;
    private SettlementManagementPayload.Header header;
    private final SettlementViewUi.HeaderButton[] statusButtons = new SettlementViewUi.HeaderButton[5];
    private SettlementViewWindow window;
    private Vec3 requestedFocus;
    private Pick hovered = new Pick(null, null);
    private int requestId;
    private int acceptedRequestId;
    private int queuedChunks;
    private long nextViewRequest;
    private long keepAliveAt;
    private boolean moved;
    private int panelLeft;
    private int panelWidth;
    private boolean plotPending;
    private final SettlementManagementTracker plotSubmission = new SettlementManagementTracker();
    private net.minecraft.world.level.ChunkPos selectedPlot;
    private com.jvn.villagerretaliation.network.SettlementPlotSnapshot plotSnapshot;
    private Button claimFree;
    private Button buyPlot;
    private Component plotFeedback = Component.empty();
    private Button manage;
    private SettlementViewLayout layout;
    private Button overlayButton;
    private Component modeHint = Component.empty();
    private Button drawArea;
    private final Button[] guardButtons = new Button[3];
    private Button returnToPost;
    private Button buildMenu;
    private Button transform;
    private Button resize;
    private Button applyGizmo;
    private Button cancelGizmo;
    private Button changePrefab;
    private SettlementSelectionGizmo gizmo;
    private boolean buildingGizmo;
    private SettlementManagementPayload.Area editingArea;
    private AABB areaBounds;
    private SettlementGuardOrder.Mode guardMode;
    private UUID guardWorksite;
    private final List<BlockPos> guardPoints = new ArrayList<>();
    private final SettlementViewInteraction interaction = new SettlementViewInteraction();
    private final SettlementRouteEditor route = this.interaction.route();
    private final List<Button> routeButtons = new ArrayList<>();
    private final SettlementViewCommandTracker commands = new SettlementViewCommandTracker();
    private long commandSequence;
    private SettlementViewBatchPayload assignmentReview;
    private final SettlementPathEditor paths = new SettlementPathEditor();
    private final List<Button> pathButtons = new ArrayList<>();
    private Button groupActions;
    private Button commandResults;
    private Component guardFeedback = Component.empty();
    private SettlementStorageScreen storagePicker;
    private UUID selectedResident;
    private SettlementManagementPayload.Resident resident;
    private boolean residentChecked;
    private UUID selectedArea;
    private BlockPos selectedBlock;
    private com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask.Kind resourceKind;
    private com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask resourcePreview;
    private BlockPos resourceTarget;
    private SettlementResourceSurvey resourceSurvey;
    private BlockPos dragStart;
    private UUID dragEntity;
    private BlockPos dragEnd;
    private int managementRequestId;
    private int waitingRequestId = -1;
    private Page waitingPage;
    private long waitingSince;
    private long detailsAt;
    private long residentRefreshAt;

    SettlementViewScreen(SettlementManagementScreen parent, SettlementViewStatePayload payload) {
        super(text("title"));
        this.parent = parent;
        this.header = parent.snapshot().header();
        this.session = payload.session();
        this.snapshots = new SettlementViewSnapshots(this.session, this.header.id());
        this.plotSnapshot = payload.plots();
        this.queuedChunks = payload.queuedChunks();
        this.window = payload.window();
        this.preferences = SettlementViewPreferences.forSettlement(this.header.id());
        this.sidebarWindow.restore(this.preferences.sidebarWindow);
        this.mapWindow.restore(this.preferences.mapWindow);
        this.selection.restore(this.preferences.groups);
        this.navigation = new SettlementViewNavigation(this.window, this.preferences.height, this.preferences.yaw,
                net.minecraft.client.Minecraft.getInstance().options.getEffectiveRenderDistance());
        this.moved = true;
        this.requestId = payload.requestId();
        this.acceptedRequestId = payload.requestId();
        this.managementRequestId = parent.snapshot().requestId() + 1;
    }

    SettlementManagementScreen parent() {
        return this.parent;
    }

    SettlementSelectionGizmo selectionGizmo() {
        return this.gizmo;
    }

    UUID session() {
        return this.session;
    }

    int nextRequestId() {
        return ++this.requestId;
    }

    double focusX() {
        return this.navigation.x();
    }

    double focusZ() {
        return this.navigation.z();
    }

    float viewHeight() {
        return this.navigation.height();
    }

    void rotateCamera(double degrees) {
        this.navigation.rotate(degrees);
    }

    float cameraYaw() {
        return this.navigation.yaw();
    }

    void resumeManagement(SettlementManagementScreen management) {
        this.snapshots.refresh();
        var updated = management.snapshot().header();
        if (updated != null && updated.id().equals(this.header.id())) {
            this.header = updated;
        }
        clearControls();
        this.waitingRequestId = -1;
        this.detailsAt = Math.max(this.detailsAt, Util.getMillis() + 300L);
    }

    double cameraHeight() {
        return this.navigation.cameraHeight();
    }

    boolean terrainLoaded() {
        return this.minecraft != null && this.minecraft.level != null && this.window.chunks().stream().allMatch(position ->
                this.minecraft.level.getChunkSource().getChunk(position.x, position.z, false) != null);
    }

    boolean contains(BlockPos position) {
        return this.window.contains(position);
    }

    void acceptWindow(SettlementViewStatePayload payload) {
        if (payload.requestId() < this.acceptedRequestId) {
            return;
        }
        this.acceptedRequestId = payload.requestId();
        if (!this.window.equals(payload.window()) || !this.plotSnapshot.equals(payload.plots())) this.paths.refresh();
        this.plotSnapshot = payload.plots();
        this.queuedChunks = payload.queuedChunks();
        this.window = payload.window();
        layoutMap();
        updatePlotActions();
        SettlementViewChunks.update(this.window);
        if (payload.requestId() >= this.requestId && !this.moved) {
            this.navigation.accept(this.window);
        }
        positionCamera();
    }

    void acceptOverview(com.jvn.villagerretaliation.network.SettlementViewOverviewPayload payload) {
        if (!this.snapshots.accept(payload, Util.getMillis())) return;
        this.header = this.snapshots.header();
        if (this.inspected instanceof SettlementManagementPayload.Depot || this.inspected instanceof SettlementManagementPayload.Order) {
            var selected = this.inspected;
            var page = selected instanceof SettlementManagementPayload.Depot ? Page.DEPOTS : Page.TRADE;
            this.inspected = this.snapshots.entries(page).stream().filter(entry -> entry.id().equals(selected.id())).findFirst().orElse(null);
            if (this.inspected instanceof SettlementManagementPayload.Depot depot) {
                this.selectedBlock = depot.position();
                this.selectedArea = depot.area();
            }
        }
        this.browser.updateAttention(this.snapshots.entries(Page.ISSUES));
        this.roster.clear();
        for (var entry : this.snapshots.entries(Page.RESIDENTS)) {
            var row = (SettlementManagementPayload.Resident) entry;
            this.roster.put(row.id(), row);
        }
        this.beds.clear();
        for (var entry : this.snapshots.entries(Page.BEDS)) {
            var bed = (SettlementManagementPayload.Bed) entry;
            this.beds.put(bed.position(), bed);
        }
        if (!this.commands.busy()) this.selection.retainBeds(this.beds.keySet());
        var groups = this.selection.groups();
        if (this.commands.busy()) this.selection.pruneGroups(this.roster.keySet());
        else this.selection.prune(this.roster.keySet());
        syncSelection();
        if (!groups.equals(this.selection.groups())) savePreferences();
        this.areas.clear();
        this.projects.clear();
        for (var entry : this.snapshots.entries(Page.AREAS)) {
            var area = (SettlementManagementPayload.Area) entry;
            this.areas.put(area.id(), area);
        }
        for (var entry : this.snapshots.entries(Page.PROJECTS)) {
            var project = (SettlementManagementPayload.Project) entry;
            if (project.status() != SettlementProject.Status.CANCELLED || project.blocker().equals("pending_world")
                    || project.blocker().equals("world_conflict")) this.projects.put(project.id(), project);
        }
        this.mapMarkers.update(this.areas.values(), this.projects.values());
        if (this.route.reloading() && this.route.draft() != null) beginPersistentRoute(this.route.area(), this.route.resident());
        updateBrowserRows();
        updateActions();
    }

    void acceptManagement(SettlementManagementPayload payload) {
        if (payload.requestId() != this.waitingRequestId) {
            return;
        }
        this.waitingRequestId = -1;
        if (payload.header() == null || !payload.header().id().equals(this.header.id())) {
            onClose();
            return;
        }
        this.header = payload.header();
        if (payload.page() == Page.RESIDENTS && this.waitingPage == Page.RESIDENTS) {
            this.residentChecked = payload.query().equals(String.valueOf(this.selectedResident));
            this.resident = payload.entries().stream().filter(entry -> entry.id().equals(this.selectedResident))
                    .filter(SettlementManagementPayload.Resident.class::isInstance)
                    .map(SettlementManagementPayload.Resident.class::cast).findFirst().orElse(null);
        }
        updateActions();
    }

    void acceptManagementResult(SettlementManagementActionResultPayload payload) {
        if (this.route.accept(payload.applied())) enterMode(Mode.SELECT);
        this.guardFeedback = SettlementManagementScreen.resultMessage(payload);
        refreshManagement();
        updateActions();
    }

    void refreshManagement() {
        this.snapshots.refresh();
        this.residentRefreshAt = 0;
        this.moved = true;
    }

    void acceptResult(SettlementManagementActionResultPayload payload) {
        if (this.parent.validSession() && this.plotSubmission.accept(payload)) {
            this.plotPending = false;
            this.plotFeedback = payload.applied() ? text("plot_claimed") : Component.translatableWithFallback(
                    "villagerretaliation.gui.settlement_view.plot_" + payload.failure(),
                    SettlementManagementScreen.resultMessage(payload).getString());
            this.moved = true;
            updatePlotActions();
        }
    }

    @Override
    protected void init() {
        clearControls();
        this.sidebarWidgets.clear();
        this.browserRows.clear();
        this.browserWidgets.clear();
        this.layout = new SettlementViewLayout(this.width, this.height, this.preferences.sidebar, this.preferences.map,
                this.sidebarWindow.position(), this.mapWindow.position());
        this.panelWidth = this.layout.panelWidth;
        this.panelLeft = this.layout.panelLeft;
        this.sidebarWindow.layout(this.panelLeft + 2, SettlementViewLayout.HEADER_HEIGHT + 2, this.panelWidth - 4,
                this.layout.contentBottom - SettlementViewLayout.HEADER_HEIGHT, this.width, this.height);
        this.mapWindow.layout(this.layout.mapLeft - 2, this.layout.mapTop - SettlementUiWindow.TITLE_HEIGHT - 2,
                this.layout.mapWidth + 4, this.layout.mapHeight + SettlementUiWindow.TITLE_HEIGHT + 4, this.width, this.height);
        initBrowser();
        for (var child : children()) if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) this.browserWidgets.add(widget);
        this.inspector.init(this.panelLeft, this.panelWidth, SettlementViewLayout.HEADER_HEIGHT + SettlementUiWindow.TITLE_HEIGHT, this.layout.contentBottom, this::addRenderableWidget);
        this.manage = addRenderableWidget(VillagerUi.buttonBuilder(text("manage_selection"), ignored -> manageSelection())
                .bounds(this.panelLeft + 6, this.layout.contentBottom - 22, this.panelWidth - 12, 20).build());
        addRenderableWidget(new SettlementViewUi.IconButton(Component.literal("Hide settlement"),
                this.panelLeft + this.panelWidth - 16, SettlementViewLayout.HEADER_HEIGHT + 4, 12, 12,
                net.minecraft.world.item.ItemStack.EMPTY, false, false, () -> false, () -> {
                    this.preferences.sidebar = false;
                    savePreferences();
                    rebuildWidgets();
                }).symbol("X"));
        for (var child : children()) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
                this.sidebarWidgets.add(widget);
                widget.visible = this.preferences.sidebar;
            }
        }
        this.statusButtons[0] = addRenderableWidget(new SettlementViewUi.HeaderButton(text("overview"), 0, 1,
                () -> openHeaderPopup(SettlementViewPopup.Kind.OVERVIEW, 4)));
        var metrics = List.of("Residents", "Beds", "Food supply", "Treasury");
        for (int index = 0; index < metrics.size(); index++) {
            int metric = index;
            this.statusButtons[index + 1] = addRenderableWidget(new SettlementViewUi.HeaderButton(Component.literal(metrics.get(index)), 0, 1, () -> {
                int left = this.statusButtons[metric + 1].getX();
                switch (metric) {
                    case 0 -> browse(Page.RESIDENTS, "All");
                    case 1 -> browse(Page.AREAS, SettlementViewBrowser.kindFilter(SettlementAreaType.HOME));
                    case 2 -> openHeaderPopup(SettlementViewPopup.Kind.FOOD, left);
                    case 3 -> openHeaderPopup(SettlementViewPopup.Kind.TREASURY, left);
                }
            }));
        }
        updateStatusButtons(statusLayout(this.width));
        int toolTop = SettlementViewLayout.HEADER_HEIGHT + 6;
        addRenderableWidget(tool(SettlementViewKeyMappings.hint(Component.literal("Select"), SettlementViewKeyMappings.SELECT), toolTop, net.minecraft.world.item.Items.ARROW, false,
                () -> this.interaction.is(Mode.SELECT) && this.gizmo == null && this.guardMode == null,
                () -> { enterMode(Mode.SELECT); updateActions(); updatePlotActions(); }));
        addRenderableWidget(tool(Component.literal("Residents"), toolTop + this.layout.railSize + 2,
                net.minecraft.world.item.Items.EMERALD, true, () -> this.preferences.sidebar && !this.inspector.visible() && this.browser.page == Page.RESIDENTS,
                this::browseResidents));
        this.drawArea = addRenderableWidget(tool(SettlementViewKeyMappings.hint(text("draw_area"), SettlementViewKeyMappings.DRAW), toolTop + (this.layout.railSize + 2) * 2,
                net.minecraft.world.item.Items.OAK_FENCE, false, () -> this.interaction.is(Mode.DRAW_AREA), () -> {
                    enterMode(this.interaction.is(Mode.DRAW_AREA) ? Mode.SELECT : Mode.DRAW_AREA);
                    updatePlotActions();
                    updateActions();
                }));
        this.buildMenu = addRenderableWidget(tool(Component.literal("Build"),
                toolTop + (this.layout.railSize + 2) * 3, net.minecraft.world.item.Items.BRICKS, false,
                () -> this.parent.buildingDraft() != null || this.paths.active()
                        || this.popup != null && this.popup.target().kind() == SettlementViewPopup.Kind.BUILD,
                this::openBuildMenu));
        addRenderableWidget(tool(SettlementViewKeyMappings.hint(text("plots"), SettlementViewKeyMappings.PLOTS), toolTop + (this.layout.railSize + 2) * 4,
                net.minecraft.world.item.Items.FILLED_MAP, false, () -> this.interaction.is(Mode.SELECT_PLOT), this::togglePlots));
        this.overlayButton = addRenderableWidget(tool(Component.literal("Overlays"),
                toolTop + (this.layout.railSize + 2) * 5, net.minecraft.world.item.Items.SPYGLASS, false,
                () -> this.preferences.workOverlay || this.preferences.issueOverlay || SettlementBoundsClient.enabled(this.header.id()), () -> openActionPopup(SettlementViewPopup.Kind.OVERLAYS)));
        addRenderableWidget(tool(Component.literal("Settlement menu"), toolTop + (this.layout.railSize + 2) * 6,
                net.minecraft.world.item.Items.BOOK, false, () -> this.popup != null && this.popup.target().kind() == SettlementViewPopup.Kind.NAVIGATION,
                () -> openHeaderPopup(SettlementViewPopup.Kind.NAVIGATION, this.layout.worldLeft)));
        this.claimFree = addRenderableWidget(VillagerUi.buttonBuilder(text("plot_free"), ignored -> claimPlot(true))
                .bounds(this.layout.worldLeft, this.layout.commandBottom - 44, Math.max(40, (this.panelLeft - this.layout.worldLeft - 10) / 2), 20).build());
        this.buyPlot = addRenderableWidget(VillagerUi.buttonBuilder(text("plot_buy", 0), ignored -> claimPlot(false))
                .bounds(this.claimFree.getRight() + 4, this.claimFree.getY(), this.claimFree.getWidth(), 20).build());
        for (var mode : SettlementGuardOrder.Mode.values()) {
            this.guardButtons[mode.ordinal()] = addRenderableWidget(VillagerUi.buttonBuilder(text("guard_" + mode.name().toLowerCase(java.util.Locale.ROOT)),
                    ignored -> beginGuardOrder(mode, this.guardWorksite)).bounds(this.layout.worldLeft, this.layout.commandTop, 72, 20).build());
        }
        this.returnToPost = addRenderableWidget(VillagerUi.buttonBuilder(text("guard_post"), ignored -> submitGuardOrder(null))
                .bounds(this.layout.worldLeft, this.layout.commandTop + 22, 148, 20).build());
        this.groupActions = addRenderableWidget(VillagerUi.buttonBuilder(Component.literal("Actions"), ignored ->
                openActionPopup(SettlementViewPopup.Kind.ACTIONS)).bounds(this.layout.worldLeft, this.layout.commandBottom - 22, 88, 18).build());
        this.commandResults = addRenderableWidget(VillagerUi.buttonBuilder(Component.literal("Results"), ignored ->
                openActionPopup(SettlementViewPopup.Kind.RESULTS)).bounds(this.layout.worldLeft + 92, this.layout.commandBottom - 22, 88, 18).build());
        initRouteButtons();
        initPathButtons();
        this.transform = addRenderableWidget(VillagerUi.buttonBuilder(text("gizmo_move"), ignored -> {
            if (this.gizmo != null) this.gizmo.mode(SettlementSelectionGizmo.Mode.TRANSFORM);
        }).bounds(this.layout.worldLeft, this.layout.commandTop, 92, 20).build());
        this.resize = addRenderableWidget(VillagerUi.buttonBuilder(text("gizmo_resize"), ignored -> {
            if (this.gizmo != null) this.gizmo.mode(SettlementSelectionGizmo.Mode.RESIZE);
        }).bounds(this.layout.worldLeft + 96, this.layout.commandTop, 68, 20).build());
        this.transform.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                SettlementViewKeyMappings.hint(text("gizmo_move"), SettlementViewKeyMappings.MOVE)));
        this.resize.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                SettlementViewKeyMappings.hint(text("gizmo_resize"), SettlementViewKeyMappings.RESIZE)));
        this.applyGizmo = addRenderableWidget(VillagerUi.buttonBuilder(text("gizmo_area_settings"), ignored -> {
            var draft = this.parent.buildingDraft();
            if (draft != null && draft.placed) {
                this.minecraft.setScreen(new SettlementBuildingScreen(management()));
            } else if (this.editingArea != null) {
                var area = new SettlementManagementPayload.Area(this.editingArea.id(), this.editingArea.name(), this.editingArea.kind(),
                        BlockPos.containing(this.areaBounds.minX, this.areaBounds.minY, this.areaBounds.minZ),
                        BlockPos.containing(this.areaBounds.maxX - 1, this.areaBounds.maxY - 1, this.areaBounds.maxZ - 1),
                        this.editingArea.priority(), this.editingArea.enabled(), this.editingArea.beds(), this.editingArea.fixtures(), this.editingArea.workers());
                editArea(area);
            }
        }).bounds(this.layout.worldLeft, this.layout.commandTop, 100, 20).build());
        this.changePrefab = addRenderableWidget(VillagerUi.buttonBuilder(SettlementManagementScreen.text("change_prefab"), ignored -> management().newBuilding())
                .bounds(this.layout.worldLeft, this.layout.commandTop + 22, 144, 20).build());
        this.cancelGizmo = addRenderableWidget(VillagerUi.buttonBuilder(Component.translatable("gui.cancel"), ignored -> cancelGizmo())
                .bounds(this.layout.worldLeft, this.layout.commandTop + 22, 76, 20).build());
        layoutMap();
        var mapWidgets = new ArrayList<net.minecraft.client.gui.components.AbstractWidget>();
        if (this.preferences.map) {
            Component[] labels = {text("center"), text("map_zoom_in"), text("map_zoom_out"), Component.literal("Hide map")};
            String[] symbols = {"o", "+", "-", "X"};
            Runnable[] actions = {
                    () -> { this.overviewMap.center(); focus(this.window.center().getX() + 0.5D, this.window.center().getZ() + 0.5D); },
                    () -> this.overviewMap.zoom(-1, this.navigation.x(), this.navigation.z()),
                    () -> this.overviewMap.zoom(1, this.navigation.x(), this.navigation.z()),
                    () -> {
                        this.preferences.map = false;
                        savePreferences();
                        rebuildWidgets();
                    }};
            for (int index = 0; index < labels.length; index++) {
                int action = index;
                mapWidgets.add(addRenderableWidget(new SettlementViewUi.IconButton(labels[index],
                        this.layout.mapLeft + this.layout.mapWidth - 51 + index * 13,
                        this.layout.mapTop - SettlementUiWindow.TITLE_HEIGHT, 12, 12,
                        net.minecraft.world.item.ItemStack.EMPTY, false, false, () -> false, actions[action]).symbol(symbols[index])));
            }
        }
        this.sidebarWindow.bind(this.sidebarWidgets);
        this.inspector.move(this.sidebarWindow.offsetX(), this.sidebarWindow.offsetY());
        this.mapWindow.bind(mapWidgets);
        this.renderables.removeAll(this.sidebarWidgets);
        this.renderables.removeAll(mapWidgets);
        updateBrowserRows();
        updateActions();
        updatePlotActions();
        positionCamera();
    }

    private SettlementViewUi.IconButton tool(Component label, int top, net.minecraft.world.item.Item icon, boolean portrait,
                                             java.util.function.BooleanSupplier selected, Runnable action) {
        return new SettlementViewUi.IconButton(label, 6, top, this.layout.railSize, this.layout.railSize,
                new net.minecraft.world.item.ItemStack(icon), false, portrait, selected, action).borderless();
    }

    private void browseResidents() {
        browse(Page.RESIDENTS, "All");
    }

    private void browse(Page page, String filter) {
        this.popup = null;
        clearControls();
        if (!this.preferences.sidebar) {
            this.preferences.sidebar = true;
            savePreferences();
            rebuildWidgets();
        }
        this.inspector.browse();
        this.browser.page = page;
        this.browser.filter = filter;
        this.browser.query = "";
        this.search.setValue("");
        this.browser.scroll = 0;
        updateInspector();
    }

    private void layoutMap() {
        if (this.layout != null) this.overviewMap.layout(this.layout.mapLeft + this.mapWindow.offsetX(), this.layout.mapTop + this.mapWindow.offsetY(), this.layout.mapWidth,
                this.layout.mapHeight, this.window.center(), this.window.radius());
    }

    private void initBrowser() {
        var tabs = List.of(Page.RESIDENTS, Page.AREAS, Page.PROJECTS, Page.ISSUES);
        var labels = List.of("People", "Areas", "Builds", "Issues");
        int labelWidth = labels.stream().mapToInt(label -> this.font.width(label) + 6).sum();
        int left = this.panelLeft + 6;
        for (int i = 0; i < tabs.size(); i++) {
            Page tab = tabs.get(i);
            String label = labels.get(i);
            int w = i == tabs.size() - 1 ? this.panelLeft + this.panelWidth - left - 6 : (this.panelWidth - 12) * (this.font.width(label) + 6) / labelWidth;
            boolean showLabel = this.font.width(label) <= w - 6;
            var icon = switch (tab) {
                case RESIDENTS -> net.minecraft.world.item.Items.VILLAGER_SPAWN_EGG;
                case AREAS -> net.minecraft.world.item.Items.OAK_FENCE;
                case PROJECTS -> net.minecraft.world.item.Items.BRICKS;
                default -> net.minecraft.world.item.Items.REDSTONE_TORCH;
            };
            addRenderableWidget(new SettlementViewUi.IconButton(Component.literal(label), left,
                    38 + SettlementUiWindow.TITLE_HEIGHT, w, 18, showLabel ? net.minecraft.world.item.ItemStack.EMPTY : new net.minecraft.world.item.ItemStack(icon),
                    showLabel, !showLabel && tab == Page.RESIDENTS, () -> this.browser.page == tab, () -> {
                this.browser.page = tab;
                this.browser.filter = "All";
                this.browser.scroll = 0;
                updateBrowserRows();
            }).borderless());
            left += w;
        }
        this.search = addRenderableWidget(new net.minecraft.client.gui.components.EditBox(this.font,
                this.panelLeft + 7, 60 + SettlementUiWindow.TITLE_HEIGHT, this.panelWidth - 14, 16, Component.literal("Search settlement")));
        this.search.setHint(Component.literal("Search residents..."));
        this.search.setValue(this.browser.query);
        this.search.setResponder(value -> {
            this.browser.query = value;
            this.browser.scroll = 0;
            updateBrowserRows();
        });
        this.filterButton = addRenderableWidget(VillagerUi.buttonBuilder(Component.literal(this.browser.filter), ignored -> {
            this.popup = new SettlementViewPopup(SettlementViewPopup.Target.of(SettlementViewPopup.Kind.FILTERS, null),
                    this.filterButton.getX(), this.filterButton.getBottom() + 2);
            refreshPopup();
        }).bounds(this.panelLeft + 6, 80 + SettlementUiWindow.TITLE_HEIGHT, this.panelWidth - 12, 16).build());
        for (int i = 0; i < this.layout.browserRows(); i++) {
            final int row = i;
            this.browserRows.add(addRenderableWidget(new SettlementViewUi.EntryButton(this.panelLeft + 6, 100 + SettlementUiWindow.TITLE_HEIGHT + i * 28,
                    this.panelWidth - 12, 27, () -> {
                var rows = this.browser.rows(this.snapshots.entries(this.browser.page));
                if (row + this.browser.scroll < rows.size()) inspect(rows.get(row + this.browser.scroll));
            })));
        }
    }

    private void updateBrowserRows() {
        if (this.filterButton == null) return;
        var rows = this.browser.rows(this.snapshots.entries(this.browser.page));
        this.filterButton.setMessage(Component.literal((this.browser.page == Page.DEPOTS || this.browser.page == Page.TRADE
                ? SettlementManagementScreen.enumText("page", this.browser.page).getString() + " / " : "")
                + this.browser.filter + " (" + rows.size() + ")"));
        if (this.search != null) this.search.setHint(Component.literal("Search " + switch (this.browser.page) {
            case RESIDENTS -> "residents...";
            case AREAS -> "areas...";
            case PROJECTS -> "buildings...";
            case DEPOTS -> "depots...";
            case TRADE -> "trade orders...";
            default -> "issues...";
        }));
        this.browser.scroll = Math.clamp(this.browser.scroll, 0, Math.max(0, rows.size() - this.browserRows.size()));
        for (int i = 0; i < this.browserRows.size(); i++) {
            var button = this.browserRows.get(i);
            int index = i + this.browser.scroll;
            button.visible = this.preferences.sidebar && !this.inspector.visible() && !this.interaction.is(Mode.SELECT_PLOT) && index < rows.size();
            if (index < rows.size()) {
                var row = rows.get(index);
                boolean selected = this.selection.contains(row.id()) || row.id().equals(this.selectedArea)
                        || this.inspected != null && row.id().equals(this.inspected.id());
                ((SettlementViewUi.EntryButton) button).entry(row, selected);
            }
        }
    }

    void inspect(SettlementManagementPayload.Entry entry) {
        this.inspector.reveal();
        this.inspected = null;
        if (entry instanceof SettlementManagementPayload.Resident r) {
            select(new Pick(r.position(), r.id()), null);
        } else if (entry instanceof SettlementManagementPayload.Area a) {
            select(new Pick(a.min(), null), a.id());
        } else if (entry instanceof SettlementManagementPayload.Project p) {
            select(new Pick(p.origin(), null), p.id());
        } else if (entry instanceof SettlementManagementPayload.Issue issue) {
            this.inspected = entry;
            BlockPos position = issuePosition(issue);
            this.selectedBlock = position;
            this.selection.select(List.of(), false);
            syncSelection();
            this.selectedArea = null;
        } else if (entry instanceof SettlementManagementPayload.Depot depot) {
            select(new Pick(depot.position(), null), depot.area());
            this.inspected = depot;
        } else if (entry instanceof SettlementManagementPayload.Order) {
            select(new Pick(null, null), null);
            this.inspected = entry;
        }
        updateActions();
    }

    void focusSupply(UUID areaId, BlockPos position) {
        var area = this.areas.get(areaId);
        if (area != null) inspect(area);
        else select(new Pick(position, null), null);
        focus(area == null ? position.getX() + 0.5D : (area.min().getX() + area.max().getX() + 1) / 2.0D,
                area == null ? position.getZ() + 0.5D : (area.min().getZ() + area.max().getZ() + 1) / 2.0D);
    }

    private BlockPos issuePosition(SettlementManagementPayload.Issue issue) {
        var resident = this.roster.get(issue.resident());
        if (resident != null) return resident.position();
        var area = this.areas.get(issue.area());
        if (area != null) return area.min();
        var project = this.projects.get(issue.targetId());
        return project == null ? null : project.origin();
    }

    private void renderMap(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!this.preferences.map) return;
        var markers = new ArrayList<>(this.mapMarkers.markers(this.selectedArea));
        var entities = entities();
        for (var r : this.roster.values()) {
            var entity = entities.get(r.id());
            boolean live = entity instanceof Villager && contains(entity.blockPosition());
            markers.add(new SettlementOverviewMap.Marker(r, live ? entity.blockPosition() : r.position(), !live,
                    this.selection.contains(r.id()) ? 0xFF40CCFF : live ? 0xFF66E478 : 0xFFAAAAAA));
        }
        for (var entry : this.snapshots.entries(Page.ISSUES)) {
            var issue = (SettlementManagementPayload.Issue) entry;
            BlockPos position = issuePosition(issue);
            if (position != null) markers.add(new SettlementOverviewMap.Marker(issue, position, false, 0xFFFF8840));
        }
        var camera = this.minecraft.gameRenderer.getMainCamera();
        this.overviewMap.render(graphics, this.font, this.plotSnapshot.plots(), this.mapMarkers.areas(),
                this.mapMarkers.projects(), markers, this.selectedArea, mapCameraFootprint(), this.navigation.x(), this.navigation.z(),
                camera.getPosition().x, camera.getPosition().z, this.navigation.yaw(), mouseX, mouseY);
    }

    private List<Vec3> mapCameraFootprint() {
        double ground = terrainHeight((int) Math.floor(this.navigation.x()), (int) Math.floor(this.navigation.z()));
        if (!Double.isFinite(ground)) return List.of();
        var corners = new ArrayList<Vec3>();
        for (double[] point : new double[][]{{0, 0}, {this.width, 0}, {this.width, this.height}, {0, this.height}}) {
            var ray = ray(point[0], point[1]);
            if (ray.direction().y >= -1.0E-5D) return List.of();
            double distance = (ground - ray.origin().y) / ray.direction().y;
            if (distance <= 0) return List.of();
            corners.add(ray.origin().add(ray.direction().scale(distance)));
        }
        return corners;
    }

    @Override
    public void tick() {
        this.frameEntities.invalidate();
        var pathPlots = this.plotSnapshot.plots().stream().filter(com.jvn.villagerretaliation.network.SettlementPlotSnapshot.Plot::owned)
                .map(com.jvn.villagerretaliation.network.SettlementPlotSnapshot.Plot::chunk).collect(java.util.stream.Collectors.toSet());
        this.paths.tick(this.minecraft.level, this.session,
                pos -> this.window.contains(pos) && pathPlots.contains(new net.minecraft.world.level.ChunkPos(pos)), PacketDistributor::sendToServer);
        if (SettlementViewClient.closing()) {
            keepAlive();
            return;
        }
        if (this.route.timeout(this.parent.submitting())) {
            this.guardFeedback = Component.literal("Outcome unconfirmed. Reload the saved route before resubmitting.");
            updateRouteButtons();
        }
        if (this.plotSubmission.timeout(Util.getMillis())) {
            this.plotPending = false;
            this.plotFeedback = SettlementManagementScreen.text("acknowledgement_timeout");
            this.moved = true;
            updatePlotActions();
        }
        long now = Util.getMillis();
        var overviewRequest = this.snapshots.request(now);
        if (overviewRequest != null) PacketDistributor.sendToServer(overviewRequest);
        refreshPopup();
        this.inspector.tick();
        var draft = this.parent.buildingDraft();
        if (draft != null) draft.tick(this.header.id());
        synchronizeGizmo();
        updateActions();
        if (this.commands.timeout(now)) {
            this.snapshots.refresh();
            this.residentRefreshAt = 0;
            this.guardFeedback = Component.literal(this.commands.summary());
        }
        keepAlive();
        if (this.waitingRequestId >= 0 && now - this.waitingSince > 3_000L) {
            this.waitingRequestId = -1;
        }
        if (this.waitingRequestId < 0 && now >= this.detailsAt) {
            if (this.selectedResident != null && now >= this.residentRefreshAt) {
                requestDetails(Page.RESIDENTS, 0, this.selectedResident.toString(), now);
                this.residentRefreshAt = now + 2_000L;


            }
        }
    }

    void keepAlive() {
        if (this.resourceSurvey != null && this.minecraft.level != null) this.resourceSurvey.tick(this.minecraft.level);
        long now = Util.getMillis();
        if (now >= this.nextViewRequest && (this.moved || now >= this.keepAliveAt)) {
            PacketDistributor.sendToServer(new SettlementViewRequestPayload(this.session, nextRequestId(),
                    this.requestedFocus == null ? this.navigation.x() : this.requestedFocus.x,
                    this.requestedFocus == null ? this.navigation.z() : this.requestedFocus.z, this.navigation.height(), false));
            this.requestedFocus = null;
            this.moved = false;
            this.nextViewRequest = now + 150L;
            this.keepAliveAt = now + 1_000L;
        }
    }

    private void requestDetails(Page page, int index, String query, long now) {
        this.waitingRequestId = ++this.managementRequestId;
        this.waitingPage = page;
        this.waitingSince = now;
        this.detailsAt = now + 300L;
        PacketDistributor.sendToServer(new SettlementManagementRequestPayload(this.header.id(), page, index, query,
                this.waitingRequestId, false));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public Component getNarrationMessage() {
        return this.title.copy().append(". ").append(selectedName()).append(". ").append(text("controls"))
                .append(this.modeHint).append(this.popup == null ? Component.empty() : this.popup.narration());
    }

    void beginFrame() { this.frameEntities.invalidate(); }

    private SettlementViewEntities entities() {
        return this.frameEntities.prepare(this.minecraft.level.entitiesForRendering());
    }

    void updateCamera(double elapsed) {
        if (!this.minecraft.isWindowActive() || this.search != null && this.search.isFocused()) clearControls();
        Vec3 movement = this.navigation.movement(elapsed, shiftDown());
        pan(movement.x, movement.z);
        positionCamera(elapsed);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (SettlementViewClient.closing()) return;
        long now = Util.getMillis();
        updatePeek(mouseX, mouseY);
        this.hovered = !SettlementViewClient.transitioning() && worldContains(mouseX, mouseY) ? pick(mouseX, mouseY) : new Pick(null, null);
        var currentDraft = this.parent.buildingDraft();
        if (currentDraft != null && !currentDraft.placed && this.hovered.position() != null && terrainLoaded()) {
            currentDraft.move(this.hovered.position().above());
        }
        synchronizeGizmo();
        if (this.gizmo != null && worldContains(mouseX, mouseY) && !SettlementViewClient.panelsOpen(this)) this.gizmo.hover(ray(mouseX, mouseY));
        this.route.preview(this.route.draft() != null && !this.route.draft().dragging() && this.hovered.position() != null
                && routeHandle(mouseX, mouseY) < 0 ? this.route.draft().placementPosition(this.hovered.position().above()) : null);
        if (this.route.draft() != null) {
            var handles = this.route.draft().handles();
            for (int index = 0; index < handles.size(); index++) {
                var point = project(Vec3.atCenterOf(handles.get(index)));
                if (point != null && worldContains(point[0], point[1]))
                    graphics.drawString(this.font, Integer.toString(index + 1), (int) point[0] + 5, (int) point[1] - 5, index == this.route.draft().selected() ? 0xFFDD55 : 0xFFFFFF);
            }
        }
        renderChrome(graphics, mouseX, mouseY);
        var draft = this.parent.buildingDraft();
        Component hint = draft != null ? text(draft.placed ? "building_adjust_hint" : "building_place_hint")
                : this.editingArea != null ? text("gizmo_area_hint")
                : this.guardMode != null ? text(this.guardMode == SettlementGuardOrder.Mode.PATROL
                ? "guard_patrol_hint" : "guard_destination_hint", this.guardPoints.size(), SettlementGuardOrder.MAX_POINTS)
                : this.interaction.is(Mode.SELECT_PLOT) ? text("plot_hint")
                : !this.guardFeedback.getString().isEmpty() ? this.guardFeedback : this.interaction.is(Mode.DRAW_AREA) ? text("draw_hint")
                : Component.literal(this.panelLeft < 360 ? "Drag selects beds and residents. Shift adds. Ctrl toggles. Ctrl+Shift removes."
                        : "Drag selects beds and residents. Shift adds. Ctrl toggles. Ctrl+Shift removes. Right-click actions. F focus. Ctrl+1-9 save.");
        if (this.guardMode == SettlementGuardOrder.Mode.PATROL) hint = Component.literal(
                (this.guardWorksite == null ? "Patrol" : "Shared patrol") + ": " + this.guardPoints.size() + "/"
                        + SettlementGuardOrder.MAX_POINTS + " points. Enter finishes. Right-click cancels.");
        if (this.paths.active()) hint = Component.literal(this.paths.hint());
        else if (this.route.draft() != null) hint = routeHint();
        else if (this.interaction.is(Mode.SELECT) && draft == null && this.editingArea == null)
            hint = Component.translatable("villagerretaliation.gui.settlement.peek_hint", SettlementViewKeyMappings.PEEK.getTranslatedKeyMessage())
                    .append("\n").append(hint);
        if (this.snapshots.stale(now)) hint = Component.literal("Overview stale / loading").append("\n").append(hint);
        else if (this.snapshots.loading()) hint = Component.literal("Refreshing overview").append("\n").append(hint);
        if (this.queuedChunks > 0) hint = Component.translatable("villagerretaliation.gui.settlement.view_queue",
                this.queuedChunks, this.window.chunks().size());
        else if (!terrainLoaded()) hint = text("loading");
        this.modeHint = hint;
        var allLines = this.font.split(hint, Math.max(80, this.panelLeft - this.layout.worldLeft - 12));
        var lines = allLines.subList(0, Math.min(2, allLines.size()));
        int hintTop = this.height - 8 - lines.size() * (this.font.lineHeight + 1);
        graphics.fill(this.layout.worldLeft - 2, hintTop - 4, this.panelLeft - 4, this.height - 4, 0xD010151B);
        for (var line : lines) {
            graphics.drawString(this.font, line, this.layout.worldLeft + 2, hintTop, 0xFFFFFF);
            hintTop += this.font.lineHeight + 1;
        }
        if (this.boxDragging) {
            int left = (int) Math.min(this.boxX, this.boxEndX);
            int top = (int) Math.min(this.boxY, this.boxEndY);
            int rightEdge = (int) Math.max(this.boxX, this.boxEndX);
            int bottom = (int) Math.max(this.boxY, this.boxEndY);
            graphics.fill(left, top, rightEdge, bottom, 0x3040CCFF);
            graphics.renderOutline(left, top, rightEdge - left, bottom - top, 0xFF40CCFF);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        var hoveredWindow = windowAt(mouseX, mouseY);
        int layer = 200;
        for (var window : this.windows) {
            if (!windowVisible(window)) continue;
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, layer);
            layer += 400;
            window.render(graphics, this.font, Component.literal(window == this.sidebarWindow ? "Settlement" : "Map"));
            int pointerX = window == hoveredWindow ? mouseX : -1;
            int pointerY = window == hoveredWindow ? mouseY : -1;
            if (window == this.sidebarWindow) {
                renderSidebarContent(graphics);
                this.inspector.render(graphics, this.font, pointerX, pointerY);
            } else renderMap(graphics, pointerX, pointerY);
            for (var widget : window.widgets()) widget.render(graphics, pointerX, pointerY, partialTick);
            window.renderResizeHandles(graphics, pointerX, pointerY);
            graphics.pose().popPose();
        }
        if (allLines.size() > lines.size() && mouseX < this.panelLeft && mouseY >= this.height - 42) {
            graphics.renderTooltip(this.font, hint, mouseX, mouseY);
        }
        if (this.popup != null) this.popup.render(graphics, this.font, mouseX, mouseY, partialTick);
    }

    void renderPanelBackdrop(GuiGraphics graphics, int width) {
        renderHeader(graphics, -1, -1, width);
    }

    private void renderHeader(GuiGraphics graphics, int mouseX, int mouseY, int screenWidth) {
        var layout = statusLayout(screenWidth);
        if (screenWidth == this.width) updateStatusButtons(layout);
        int highlightedEntry = -1;
        if (mouseX >= 0 && mouseY >= 0) {
            for (int index = 0; index < this.statusButtons.length; index++) {
                var button = this.statusButtons[index];
                if (button != null && button.isMouseOver(mouseX, mouseY)) highlightedEntry = index;
            }
            if (highlightedEntry < 0) {
                for (int index = 0; index < this.statusButtons.length; index++) {
                    var button = this.statusButtons[index];
                    if (button != null && button.isFocused()) highlightedEntry = index;
                }
            }
        }
        SettlementStatusBar.render(graphics, this.font, layout, mouseX, mouseY, highlightedEntry);
    }

    private SettlementStatusBar.Layout statusLayout(int screenWidth) {
        return SettlementStatusBar.layout(this.font, this.header, this.minecraft.level.getDayTime(), screenWidth);
    }

    private void updateStatusButtons(SettlementStatusBar.Layout layout) {
        if (this.statusButtons[0] == null) return;
        this.statusButtons[0].setX(layout.left());
        this.statusButtons[0].setWidth(layout.divider());
        for (int index = 0; index < 4; index++) {
            int left = layout.boundary(layout.metricLeft(index));
            this.statusButtons[index + 1].setX(left);
            this.statusButtons[index + 1].setWidth(layout.boundary(layout.metricLeft(index + 1)) - left);
        }
    }

    private void renderChrome(GuiGraphics graphics, int mouseX, int mouseY) {
        renderHeader(graphics, mouseX, mouseY, this.width);
        TooltipRenderUtil.renderTooltipBackground(graphics, 6, SettlementViewLayout.HEADER_HEIGHT + 6,
                this.layout.railSize, (this.layout.railSize + 2) * 7 - 2, 0);
        var commands = contextButtons().stream().filter(button -> button.visible).toList();
        if (!commands.isEmpty()) {
            int left = commands.stream().mapToInt(Button::getX).min().orElse(0) - 3;
            int top = commands.stream().mapToInt(Button::getY).min().orElse(0) - 3;
            int right = commands.stream().mapToInt(Button::getRight).max().orElse(0) + 3;
            int bottom = commands.stream().mapToInt(Button::getBottom).max().orElse(0) + 3;
            SettlementViewUi.frame(graphics, left, top, right - left, bottom - top);
        }
    }

    private void renderSidebarContent(GuiGraphics graphics) {
        if (this.interaction.is(Mode.SELECT_PLOT) && this.preferences.sidebar) {
            var totals = this.plotSnapshot.totals();
            var text = text("plot_totals", totals.owned(), totals.capacity(), totals.free(), totals.spendable().decimal(2))
                    .copy().append("\n\n").append(plotDescription());
            int y = this.sidebarWindow.top() + 18;
            for (var line : this.font.split(text, this.panelWidth - 16)) {
                if (y + this.font.lineHeight >= this.manage.getY() - 4) break;
                graphics.drawString(this.font, line, this.sidebarWindow.left() + 6, y, 0xFFFFFF);
                y += this.font.lineHeight + 2;
            }
        } else if (this.preferences.sidebar && !this.inspector.visible()
                && this.browser.rows(this.snapshots.entries(this.browser.page)).isEmpty()) {
            int y = this.sidebarWindow.top() + 84;
            for (var line : this.font.split(Component.literal(this.snapshots.loading() ? "Loading settlement..." : "No matching entries"),
                    this.panelWidth - 16)) {
                graphics.drawString(this.font, line, this.sidebarWindow.left() + 6, y, 0xB9C1CB);
                y += this.font.lineHeight + 2;
            }
        }
    }

    private List<Button> contextButtons() {
        var buttons = new ArrayList<Button>();
        for (var button : this.guardButtons) if (button != null) buttons.add(button);
        for (var button : new Button[]{this.returnToPost, this.groupActions, this.commandResults, this.transform, this.resize,
                this.applyGizmo, this.cancelGizmo, this.changePrefab, this.claimFree, this.buyPlot}) if (button != null) buttons.add(button);
        buttons.addAll(this.routeButtons);
        return buttons;
    }

    private boolean windowVisible(SettlementUiWindow window) {
        return window == this.sidebarWindow ? this.preferences.sidebar : this.preferences.map;
    }

    private SettlementUiWindow windowAt(double x, double y) {
        for (int index = this.windows.size() - 1; index >= 0; index--) {
            var window = this.windows.get(index);
            if (windowVisible(window) && window.contains(x, y)) return window;
        }
        return null;
    }

    private boolean worldContains(double x, double y) {
        if (x < 0 || x >= this.width || y < SettlementViewLayout.HEADER_HEIGHT + 4 || y >= this.height - 32)
            return false;
        if (windowAt(x, y) != null) return false;
        for (var child : children()) if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget && widget.visible
                && x >= widget.getX() - 3 && x < widget.getRight() + 3 && y >= widget.getY() - 3 && y < widget.getBottom() + 3) return false;
        return true;
    }

    private void activateGroup(int group, boolean store) {
        if (store) {
            this.selection.store(group);
            savePreferences();
            this.guardFeedback = Component.literal("Group " + (group + 1) + " saved");
        } else {
            boolean focus = this.selection.recall(group, Util.getMillis());
            this.selectedArea = null;
            syncSelection();
            if (focus) focusSelection();
        }
        updateActions();
    }

    private void layoutContextActions() {
        int left = this.layout.worldLeft;
        int available = this.panelLeft - left - 6;
        int third = Math.min(96, (available - 8) / 3);
        for (int index = 0; index < this.guardButtons.length; index++) {
            var button = this.guardButtons[index];
            button.setX(left + index * (third + 4));
            button.setY(this.layout.commandTop);
            button.setWidth(third);
        }
        this.returnToPost.setX(left);
        this.returnToPost.setY(this.layout.commandTop + 22);
        this.returnToPost.setWidth(third);
        this.returnToPost.setTooltip(net.minecraft.client.gui.components.Tooltip.create(this.returnToPost.getMessage()));
        int half = Math.min(148, (available - 4) / 2);
        this.groupActions.setX(left);
        this.groupActions.setY(this.layout.commandBottom - 22);
        this.groupActions.setWidth(Math.min(88, half));
        this.commandResults.setX(this.groupActions.getRight() + 4);
        this.commandResults.setY(this.groupActions.getY());
        this.commandResults.setWidth(this.groupActions.getWidth());
        if (java.util.Arrays.stream(this.guardButtons).anyMatch(button -> button.visible)) {
            this.groupActions.setX(left + third + 4);
            this.groupActions.setY(this.layout.commandTop + 22);
            this.groupActions.setWidth(third);
            this.commandResults.setX(left + 2 * (third + 4));
            this.commandResults.setY(this.groupActions.getY());
            this.commandResults.setWidth(third);
        }
        boolean building = this.parent.buildingDraft() != null;
        this.transform.setX(left);
        this.transform.setY(this.layout.commandTop);
        this.transform.setWidth(building ? half : third);
        this.resize.setX(left + third + 4);
        this.resize.setY(this.layout.commandTop);
        this.resize.setWidth(third);
        this.applyGizmo.setX(building ? left + half + 4 : left + 2 * (third + 4));
        this.applyGizmo.setY(this.layout.commandTop);
        this.applyGizmo.setWidth(building ? half : third);
        this.changePrefab.setX(left);
        this.changePrefab.setY(this.layout.commandTop + 22);
        this.changePrefab.setWidth(half);
        this.cancelGizmo.setX(building ? left + half + 4 : left);
        this.cancelGizmo.setY(this.layout.commandTop + 22);
        this.cancelGizmo.setWidth(half);
    }

    private Component selectedName() {
        if (this.inspected != null) return SettlementManagementScreen.name(this.inspected).copy()
                .append("\n").append(SettlementManagementScreen.detail(this.inspected));
        if (!this.selection.beds().isEmpty()) return Component.literal(this.selection.size() + " residents / "
                + this.selection.beds().size() + " beds selected");
        if (this.selection.size() > 1) {
            var rows = this.selection.residents().stream().map(this.roster::get).filter(java.util.Objects::nonNull).toList();
            long paused = rows.stream().filter(row -> row.status().equals("paused")).count();
            long enabled = rows.stream().filter(row -> row.area() != null && !row.status().equals("paused")).count();
            long assignments = rows.stream().map(SettlementManagementPayload.Resident::area).distinct().count();
            var shared = rows.isEmpty() ? null : this.areas.get(rows.getFirst().area());
            long guards = eligibleCount(SettlementViewAction.MOVE, null);
            return Component.literal(rows.size() + " selected / " + guards + " guards\n" + paused + " paused / "
                    + enabled + " enabled\n"
                    + (assignments > 1 ? "Mixed assignments" : shared == null ? "Unassigned" : shared.name()));
        }
        if (this.resident != null) {
            Component name = text("resident", this.resident.name(), Math.round(this.resident.health()), this.resident.food());
            if (!this.resident.temporary().isEmpty()) name = name.copy().append("\n").append(this.resident.temporary());
            return this.resident.guardOrder() == null ? name : name.copy().append("\n")
                    .append(text("guard_" + this.resident.guardOrder().mode().name().toLowerCase(java.util.Locale.ROOT)));
        }
        var project = this.projects.get(this.selectedArea);
        if (project != null) return Component.literal(project.name()).append("\n").append(projectProgress(project));
        var area = this.areas.get(this.selectedArea);
        if (area != null) {
            return text("area", area.name(), SettlementManagementScreen.enumText("area", area.kind()), area.workers(), area.beds());
        }
        if (this.selectedResident != null) {
            return text(this.residentChecked ? "not_resident" : "checking_resident");
        }
        return this.selectedBlock == null ? text("select_hint")
                : text("position", this.selectedBlock.getX(), this.selectedBlock.getY(), this.selectedBlock.getZ());
    }

    private void updateActions() {
        updateInspector();
        updatePathButtons();
        if (!permits(SettlementPermission.BUILD)) {
            if (this.paths.active() && !this.paths.confirming()) enterMode(Mode.SELECT);
            cancelGizmo();
            if (this.interaction.is(Mode.DRAW_AREA)) this.interaction.enter(Mode.SELECT);
            this.dragStart = null;
            this.dragEnd = null;
        }
        if (!selectedGuard() && !sharedRouteAvailable(this.areas.get(this.guardWorksite))) {
            this.guardMode = null;
            this.guardWorksite = null;
            if (this.interaction.is(Mode.GUARD_TARGET)) this.interaction.enter(Mode.SELECT);
            this.guardPoints.clear();
        }
        var draft = this.parent.buildingDraft();
        boolean manipulating = this.gizmo != null;
        if (this.groupActions != null) {
            this.groupActions.visible = this.selection.totalSize() != 0 && !manipulating && !this.interaction.is(Mode.SELECT_PLOT);
            this.groupActions.active = !this.commands.busy();
            this.commandResults.visible = !this.commands.outcomes().isEmpty() && !manipulating && !this.interaction.is(Mode.SELECT_PLOT);
        }
        if (this.buildMenu != null) this.buildMenu.active = !this.commands.busy() && !this.parent.submitting() && !this.paths.confirming();
        if (this.transform != null) {
            this.transform.visible = manipulating;
            this.transform.setMessage(text(draft == null ? "gizmo_move" : "gizmo_transform"));
            this.transform.active = manipulating && this.gizmo.mode() != SettlementSelectionGizmo.Mode.TRANSFORM;
            this.resize.visible = manipulating && draft == null;
            this.resize.active = manipulating && this.gizmo.mode() != SettlementSelectionGizmo.Mode.RESIZE;
            this.applyGizmo.visible = manipulating;
            this.applyGizmo.setMessage(text(draft == null ? "gizmo_area_settings" : "building_choose_builder"));
            this.applyGizmo.active = draft == null || draft.placed && !draft.submitting;
            this.cancelGizmo.visible = manipulating;
            this.changePrefab.visible = manipulating && draft != null;
        }
        if (this.manage != null) {
            this.manage.setMessage(this.inspected instanceof SettlementManagementPayload.Depot ? Component.literal("Depot settings")
                    : this.inspected instanceof SettlementManagementPayload.Order ? Component.literal("Edit trade order") : text("manage_selection"));
            this.manage.setTooltip(net.minecraft.client.gui.components.Tooltip.create(selectedName()));
            this.manage.active = !this.commands.busy() && (!this.selection.beds().isEmpty() && permits(SettlementPermission.STAFF) || this.inspected != null || this.resident != null
                    || this.projects.containsKey(this.selectedArea)
                    || this.selectedArea != null && this.areas.containsKey(this.selectedArea) && permits(SettlementPermission.BUILD));
            if (this.inspected instanceof SettlementManagementPayload.Depot) this.manage.active &= permits(SettlementPermission.LOGISTICS) && !this.parent.submitting();
            if (this.inspected instanceof SettlementManagementPayload.Order) this.manage.active &= permits(SettlementPermission.TRADE) && !this.parent.submitting();
            this.drawArea.active = permits(SettlementPermission.BUILD) && !this.commands.busy();
            this.drawArea.setMessage(text(this.interaction.is(Mode.DRAW_AREA) ? "cancel_area" : "draw_area"));
        }
        for (var button : this.guardButtons) {
            if (button != null) {
                boolean shared = sharedRouteAvailable(this.areas.get(this.guardWorksite));
                button.visible = (shared || selectedGuard()) && !manipulating && !this.interaction.is(Mode.SELECT_PLOT);
                button.active = !this.commands.busy();
                int ordinal = java.util.Arrays.asList(this.guardButtons).indexOf(button);
                if (shared && this.areas.get(this.guardWorksite).kind() == SettlementAreaType.HUNTING_GROUNDS
                        && ordinal != SettlementGuardOrder.Mode.PATROL.ordinal()) button.visible = false;
                button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(shared ? "Shared worksite route"
                        : eligibleCount(SettlementViewAction.MOVE, null) + "/" + this.selection.size() + " eligible guards")));
            }
        }
        if (this.returnToPost != null) {
            this.returnToPost.visible = this.guardWorksite == null && selectedGuard() && !manipulating && !this.interaction.is(Mode.SELECT_PLOT);
            this.returnToPost.active = !this.commands.busy();
            this.guardButtons[SettlementGuardOrder.Mode.PATROL.ordinal()].setMessage(text(
                    this.guardMode == SettlementGuardOrder.Mode.PATROL ? "guard_finish_patrol" : "guard_patrol"));
        }
        updateRouteButtons();
        if (this.route.draft() != null || this.storagePicker != null || this.paths.active()) {
            for (var button : this.guardButtons) if (button != null) button.visible = false;
            this.returnToPost.visible = false;
            this.groupActions.visible = false;
            this.commandResults.visible = false;
            this.manage.active = false;
            this.drawArea.active = false;
        }
        layoutContextActions();
    }

    private boolean selectedGuard() {
        return eligibleCount(SettlementViewAction.MOVE, null) > 0;
    }

    private SettlementManagementPayload.Resident residentSnapshot(UUID id) {
        var overview = this.roster.get(id);
        return overview != null && this.resident != null && id.equals(this.resident.id())
                && java.util.Objects.equals(overview.area(), this.resident.area()) ? this.resident : overview;
    }

    private long eligibleCount(SettlementViewAction action, UUID destination) {
        if (!permits(action.permission())) return 0;
        return this.selection.residents().stream().map(this::residentSnapshot).filter(java.util.Objects::nonNull)
                .filter(r -> SettlementViewUi.actionEligible(action, r, this.areas.get(action == SettlementViewAction.ASSIGN ? destination : r.area()))).count();
    }

    private void submitBatch(SettlementViewAction action, UUID area, SettlementGuardOrder order) {
        if (this.commands.busy() || this.selection.size() == 0 || !permits(action.permission())) return;
        var targets = this.selection.residents().stream().map(this::residentSnapshot).filter(java.util.Objects::nonNull)
                .filter(resident -> SettlementViewUi.actionEligible(action, resident,
                        this.areas.get(action == SettlementViewAction.ASSIGN ? area : resident.area())))
                .map(resident -> new SettlementViewBatchPayload.Target(resident.id(), resident.area())).toList();
        if (targets.isEmpty()) return;
        var request = new SettlementViewBatchPayload(this.session, SettlementViewBatchPayload.operation(this.session, ++this.commandSequence),
                action, targets, area, order);
        if (action == SettlementViewAction.ASSIGN && targets.stream().anyMatch(target ->
                target.expectedArea() != null && !target.expectedArea().equals(area)
                        && action.eligible(this.roster.get(target.resident()), this.areas.get(area)))) {
            this.assignmentReview = request;
            openActionPopup(SettlementViewPopup.Kind.CONFIRM_ASSIGN);
        } else sendBatch(request);
    }

    void submitTemporaryTask(List<SettlementViewBatchPayload.Target> targets,
                             com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask task, boolean append) {
        if (this.commands.busy()) return;
        sendBatch(new SettlementViewBatchPayload(this.session, SettlementViewBatchPayload.operation(this.session, ++this.commandSequence),
                SettlementViewAction.TEMPORARY_TASK,
                targets, null, null, task, append));
    }

    private List<SettlementViewBatchPayload.Target> resourceWorkers(com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask.Kind kind) {
        return this.selection.residents().stream().map(this.roster::get).filter(java.util.Objects::nonNull)
                .filter(resident -> !resident.baby() && this.areas.containsKey(resident.area())
                        && this.areas.get(resident.area()).kind().role() == kind.role())
                .map(resident -> new SettlementViewBatchPayload.Target(resident.id(), resident.area())).toList();
    }

    boolean issueResourceOrder(com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask task, boolean append) {
        var workers = resourceWorkers(task.kind());
        if (workers.isEmpty() || this.commands.busy() || !permits(SettlementPermission.STAFF)) return false;
        submitTemporaryTask(workers, task, append);
        return true;
    }

    void previewResourceOrder(com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask task) {
        this.resourcePreview = task;
        this.resourceSurvey = task == null ? null : new SettlementResourceSurvey(this.minecraft.level, task);
    }

    String resourceSurveySummary() { return this.resourceSurvey == null ? "Preview shown in the world" : this.resourceSurvey.summary(); }

    private void resourceOrder(com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask.Kind kind) {
        var position = this.resourceTarget;
        if (position == null) return;
        if (kind == com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask.Kind.TREE
                || kind == com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask.Kind.ORE) {
            issueResourceOrder(new com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask(UUID.randomUUID(), kind, position, position, 1, position), shiftDown());
        } else if (kind == com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask.Kind.TUNNEL || kind.staircase()) {
            this.popup = null;
            SettlementViewClient.openPanels(this);
            this.minecraft.setScreen(new SettlementResourceOrderScreen(this, kind, position, position, shiftDown()));
        } else {
            enterMode(Mode.TASK_AREA);
            this.resourceKind = kind;
            this.guardFeedback = Component.literal("Drag the work area. Release to review. Right-click cancels");
        }
    }

    private void openTemporaryTaskPicker() {
        if (this.resourceKind != null) {
            var kind = this.resourceKind;
            var start = this.dragStart;
            var end = this.dragEnd;
            enterMode(Mode.SELECT);
            SettlementViewClient.openPanels(this);
            this.minecraft.setScreen(new SettlementResourceOrderScreen(this, kind, start, end, shiftDown()));
            return;
        }
        var min = com.jvn.villagerretaliation.interaction.HiredWorkArea.minPos(this.dragStart, this.dragEnd);
        var max = com.jvn.villagerretaliation.interaction.HiredWorkArea.maxPos(this.dragStart, this.dragEnd).above(2);
        var targets = this.selection.residents().stream().map(id -> new SettlementViewBatchPayload.Target(id,
                this.roster.containsKey(id) ? this.roster.get(id).area() : null)).toList();
        var kind = targets.stream().map(target -> this.areas.get(target.expectedArea())).filter(java.util.Objects::nonNull)
                .map(area -> com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask.Kind.forRole(area.kind().role()))
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
        boolean append = shiftDown();
        var selectedTarget = this.dragStart;
        var selectedEntity = this.dragEntity;
        if (selectedEntity != null && this.dragStart.equals(this.dragEnd)) {
            min = selectedTarget;
            max = selectedTarget;
        }
        enterMode(Mode.SELECT);
        if (kind == null || targets.isEmpty()) return;
        SettlementViewClient.openPanels(this);
        this.minecraft.setScreen(new SettlementTemporaryTaskScreen(this, targets, kind, min, max, append, selectedTarget, selectedEntity));
    }

    private void sendBatch(SettlementViewBatchPayload request) {
        this.commands.sent(request, Util.getMillis());
        this.assignmentReview = null;
        this.popup = null;
        this.guardFeedback = Component.literal(this.commands.summary());
        PacketDistributor.sendToServer(request);
        updateActions();
    }

    void acceptBatch(SettlementViewBatchResultPayload payload) {
        if (!this.commands.accept(payload)) return;
        this.guardFeedback = Component.literal(this.commands.summary());
        this.snapshots.refresh();
        this.residentRefreshAt = 0;
        updateActions();
    }

    private void openBuildMenu() {
        clearControls();
        if (this.popup != null && this.popup.target().kind() == SettlementViewPopup.Kind.BUILD) {
            this.popup = null;
            return;
        }
        this.popup = new SettlementViewPopup(SettlementViewPopup.Target.of(SettlementViewPopup.Kind.BUILD, null),
                this.layout.worldLeft, this.buildMenu.getY());
        refreshPopup();
    }

    private void openHeaderPopup(SettlementViewPopup.Kind kind, int left) {
        clearControls();
        this.popup = new SettlementViewPopup(SettlementViewPopup.Target.of(kind, null), left, SettlementViewLayout.HEADER_HEIGHT + 4);
        refreshPopup();
    }

    private SettlementViewPopup.Action menuAction(Component label, boolean enabled, Runnable action) {
        return new SettlementViewPopup.Action(label, enabled, () -> {
            this.popup = null;
            action.run();
        });
    }

    private void openActionPopup(SettlementViewPopup.Kind kind) {
        clearControls();
        this.popup = new SettlementViewPopup(SettlementViewPopup.Target.of(kind, null), 8, 122);
        refreshPopup();
    }

    private void executeAction(SettlementViewAction action) {
        if (action == SettlementViewAction.ASSIGN_HOME) { openHousingAssignments(); return; }
        if (action == SettlementViewAction.TEMPORARY_TASK) {
            enterMode(Mode.TASK_AREA);
            this.guardFeedback = Component.literal("Drag task bounds. Shift appends. Right-click cancels");
            updateActions();
        }
        else if (action == SettlementViewAction.ASSIGN) openActionPopup(SettlementViewPopup.Kind.ASSIGN);
        else if (action.targeting() != null) { this.popup = null; beginGuardOrder(action.targeting()); }
        else submitBatch(action, null, null);
    }

    private List<SettlementViewPopup.Action> selectionActions() {
        var actions = new ArrayList<SettlementViewPopup.Action>();
        if (this.selection.totalSize() > 0 && permits(SettlementPermission.STAFF)) {
            actions.add(new SettlementViewPopup.Action(Component.literal("Assign beds"), !this.commands.busy(), this::openHousingAssignments));
        }
        for (var action : SettlementViewAction.values()) {
            if (action == SettlementViewAction.ASSIGN_HOME) continue;
            long eligible = action == SettlementViewAction.ASSIGN
                    ? this.areas.keySet().stream().mapToLong(id -> eligibleCount(action, id)).max().orElse(0)
                    : eligibleCount(action, null);
            if (eligible == 0) continue;
            actions.add(new SettlementViewPopup.Action(SettlementViewUi.actionLabel(action.label(), eligible, this.selection.size()),
                    !this.commands.busy(), () -> executeAction(action), action));
        }
        return actions;
    }

    void beginPath(boolean branch) {
        if (!permits(SettlementPermission.BUILD) || this.paths.confirming() || this.parent.submitting() || this.commands.busy()) return;
        enterMode(branch ? Mode.BRANCH_PATH : Mode.CREATE_PATH);
        this.selection.select(List.of(), false);
        this.paths.begin(branch);
        updateActions();
        updatePlotActions();
    }

    SettlementPathEditor pathEditor() { return this.paths; }

    void acceptPath(com.jvn.villagerretaliation.network.SettlementPathResultPayload payload) {
        if (this.paths.accept(payload)) {
            this.guardFeedback = Component.literal(payload.message());
            enterMode(Mode.SELECT);
        }
        updateActions();
    }

    private Vec3 pathPoint(double x, double y) {
        if (!worldContains(x, y)) return null;
        var ray = ray(x, y);
        var hit = SettlementViewPeek.clip(this.minecraft.level, ray.origin(), ray.origin().add(ray.direction().normalize().scale(256)),
                ClipContext.Block.OUTLINE, this.minecraft.gameRenderer.getMainCamera().getEntity());
        if (hit.getType() == HitResult.Type.MISS || !this.minecraft.level.hasChunkAt(hit.getBlockPos())) return null;
        var pos = com.jvn.villagerretaliation.settlement.SettlementPathTerrain.surface(this.minecraft.level,
                hit.getBlockPos().getX(), hit.getBlockPos().getZ());
        return new Vec3(pos.getX() + 0.5, pos.getY() + 0.98, pos.getZ() + 0.5);
    }

    private void pathClick(double x, double y) {
        var draft = this.paths.draft();
        var handle = SettlementPathGizmo.hit(draft, this::project, x, y);
        if (this.search != null) this.search.setFocused(false);
        setFocused(null);
        clearControls();
        if (handle != null) {
            draft.begin(handle.node(), handle.part());
            this.paths.startDrag(x, y, handle.part());
            return;
        }
        if (!draft.extending() && !draft.inserting()) return;
        var point = pathPoint(x, y);
        if (point == null) { this.paths.feedback("No loaded ground at this position"); return; }
        if (draft.path().nodes().isEmpty()) {
            var state = this.minecraft.level.getBlockState(BlockPos.containing(point));
            boolean existing = state.is(net.minecraft.world.level.block.Blocks.DIRT_PATH);
            if (draft.path().branch() ? !existing : !existing && !com.jvn.villagerretaliation.settlement.SettlementPathTerrain.soil(state)) {
                this.paths.feedback(draft.path().branch() ? "Start the branch on an existing dirt path" : "Start on valid soil");
                return;
            }
        }
        if (draft.place(point)) this.paths.startDrag(x, y, 1);
        else this.paths.feedback("Path has 64 nodes. Confirm this section before adding more");
    }

    private void initPathButtons() {
        this.pathButtons.clear();
        String[] labels = {"Width -", "+", "Clean", "Undo", "Redo", "Lines", "Insert", "Remove", "Confirm", "Cancel", "Refresh", "Close loop", "Extend", "Edit"};
        for (int i = 0; i < labels.length; i++) {
            int action = i;
            this.pathButtons.add(addRenderableWidget(VillagerUi.buttonBuilder(Component.literal(labels[i]), ignored -> pathAction(action))
                    .bounds(8, 52, 64, 18).build()));
        }
    }

    private void pathAction(int action) {
        if (!this.paths.active() || this.paths.confirming()) return;
        var draft = this.paths.draft();
        var path = draft.path();
        switch (action) {
            case 0 -> draft.settings(path.width() - 1, path.style());
            case 1 -> draft.settings(path.width() + 1, path.style());
            case 2 -> draft.settings(path.width(), path.style() == com.jvn.villagerretaliation.settlement.SettlementPath.Style.CLEAN
                    ? com.jvn.villagerretaliation.settlement.SettlementPath.Style.NATURAL : com.jvn.villagerretaliation.settlement.SettlementPath.Style.CLEAN);
            case 3 -> draft.undo();
            case 4 -> draft.redo();
            case 5 -> draft.smooth();
            case 6 -> draft.inserting(!draft.inserting());
            case 7 -> draft.remove();
            case 8 -> this.paths.confirm(this.session, PacketDistributor::sendToServer);
            case 9 -> enterMode(Mode.SELECT);
            case 10 -> this.paths.refresh();
            case 11 -> draft.toggleLoop();
            case 12 -> { if (draft.extending()) this.paths.stopExtending(); else draft.extend(); }
            case 13 -> {
                clearControls();
                this.paths.hoveredHandle(null);
                var edit = this.pathButtons.get(13);
                this.popup = new SettlementViewPopup(SettlementViewPopup.Target.of(SettlementViewPopup.Kind.PATH_EDIT, null),
                        edit.getX(), edit.getY() - 96);
                refreshPopup();
            }
            default -> throw new IllegalArgumentException("Unknown path action");
        }
        this.paths.hover(null);
        updateActions();
    }

    private void updatePathButtons() {
        if (this.pathButtons.isEmpty()) return;
        for (int i = 0; i < this.pathButtons.size(); i++) {
            var button = this.pathButtons.get(i);
            button.visible = this.paths.active() && i != 6 && i != 7 && i != 10 && i != 11 && i != 12;
            button.active = this.paths.active() && !this.paths.confirming() && !this.paths.draft().dragging();
            if (!this.paths.active()) continue;
            var draft = this.paths.draft();
            var path = draft.path();
            if (i == 0) { button.setMessage(Component.literal("Width " + path.width() + " -")); button.active &= path.width() > 1; }
            if (i == 1) button.active &= path.width() < 7;
            if (i == 2) button.setMessage(Component.literal(path.style() == com.jvn.villagerretaliation.settlement.SettlementPath.Style.CLEAN ? "Clean" : "Natural"));
            if (i == 3) button.active &= draft.canUndo();
            if (i == 4) button.active &= draft.canRedo();
            if (i == 5) button.setMessage(Component.literal(draft.smoothMode() ? "Curves" : "Lines"));
            if (i >= 6 && i <= 7) button.active &= draft.selected() >= 0;
            if (i == 6) button.setMessage(Component.literal(draft.inserting() ? "Insert: on" : "Insert"));
            if (i == 7) button.active &= !path.branch() || draft.selected() != 0;
            if (i == 8) button.active &= this.paths.canConfirm();
            if (i == 10) button.active &= path.nodes().size() >= 2;
            if (i == 11) {
                button.setMessage(Component.literal(path.closed() ? "Open loop" : "Close loop"));
                button.active &= path.closed() || draft.canClose();
            }
            if (i == 12) {
                button.setMessage(Component.literal(draft.extending() ? "Stop extending" : "Extend"));
                button.active &= !path.closed();
            }
            button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(switch (i) {
                case 0 -> "Decrease path width";
                case 1 -> "Increase path width";
                case 5 -> "Choose lines or curves. In Curves mode, click any node to edit its handles. This choice is remembered.";
                case 6 -> "Place the next node after the selected node.";
                case 8 -> "Apply all previewed path blocks. Placement cannot be undone.";
                case 10 -> "Review the latest terrain after loading chunks or changing the view.";
                case 11 -> "Connect the last node back to the first, or reopen the loop.";
                case 12 -> "Stop or resume adding nodes while keeping the draft. Escape stops extending.";
                default -> button.getMessage().getString();
            })));
        }
        int available = Math.max(80, this.panelLeft - this.layout.worldLeft - 6);
        int x = 0, row = 0;
        for (int index : new int[]{0, 1, 2, 5, 3, 4, 13, 8, 9}) {
            var button = this.pathButtons.get(index);
            int width = Math.min(available, Math.max(20, this.font.width(button.getMessage()) + 12));
            if (x > 0 && x + width > available) { x = 0; row++; }
            button.setWidth(width);
            button.setX(this.layout.worldLeft + x);
            button.setY(row * 22);
            x += width + 4;
        }
        int top = this.height - 36 - (row + 1) * 22;
        for (var button : this.pathButtons) if (button.visible) button.setY(button.getY() + top);
    }

    private void initRouteButtons() {
        this.routeButtons.clear();
        String[] labels = {"Save route", "Cancel", "Reload", "Inherit", "Insert", "Remove", "Earlier", "Later", "Loop", "Branch", "Clear", "Append"};
        for (int index = 0; index < labels.length; index++) {
            int action = index;
            this.routeButtons.add(addRenderableWidget(VillagerUi.buttonBuilder(Component.literal(labels[index]), ignored -> routeAction(action))
                    .bounds(8, 52, 64, 18).build()));
        }
    }

    private void updateRouteButtons() {
        int available = this.panelLeft - this.layout.worldLeft - 6;
        int columns = available < 240 ? 3 : 4;
        int width = (available - (columns - 1) * 4) / columns;
        int rows = (this.routeButtons.size() + columns - 1) / columns;
        int top = this.height - 36 - rows * 22;
        for (int index = 0; index < this.routeButtons.size(); index++) {
            var button = this.routeButtons.get(index);
            button.visible = this.route.draft() != null;
            button.setX(this.layout.worldLeft + index % columns * (width + 4));
            button.setY(top + index / columns * 22);
            button.setWidth(width);
            button.active = this.route.draft() != null && !this.parent.submitting() && !this.commands.busy();
            if (this.route.draft() == null) continue;
            button.active &= !this.route.reloading() && (!this.route.draft().dragging() || index == 1);
            if (index == 0 || index == 3) button.active &= !this.route.uncertain();
            if (index == 0) button.active &= this.route.draft().validation().isEmpty();
            if (index == 3) button.setMessage(Component.literal(this.route.resident() == null ? "Automatic" : "Inherit"));
            if (index == 4) button.setMessage(Component.literal(this.route.draft().inserting() ? "Insert: on" : "Insert"));
            if (index >= 4 && index <= 7 || index == 9) button.active &= this.route.draft().selected() >= 0;
            if (index == 8) button.setMessage(Component.literal(this.route.draft().loop() ? "Loop: on" : "Loop: off"));
            if (index == 9) button.setMessage(Component.literal(this.route.draft().branching() ? "Branch: on" : "Branch"));
            button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(switch (index) {
                case 0 -> "Save this route. Changes stay in the draft until saved.";
                case 2 -> "Discard this draft and reload the latest saved route.";
                case 3 -> this.route.resident() == null ? "Remove the worksite route and restore automatic behavior." : "Remove only this worker's route override.";
                case 4 -> "Place the next node after the selected node.";
                case 5 -> "Remove the selected node or branch.";
                case 9 -> "Select a route node, then place an out-and-back branch.";
                case 10 -> "Clear the draft. Save to explicitly disable this route.";
                default -> button.getMessage().getString();
            })));
        }
    }

    private void routeAction(int action) {
        if (this.route.draft() == null || this.parent.submitting()) return;
        switch (action) {
            case 0 -> savePersistentRoute(false);
            case 1 -> enterMode(Mode.SELECT);
            case 2 -> {
                this.route.reload();
                refreshManagement();
                this.guardFeedback = Component.literal("Reloading the saved route...");
            }
            case 3 -> savePersistentRoute(true);
            default -> this.route.edit(action);
        }
        updateActions();
    }

    private void beginPersistentRoute() {
        var area = inspectorWorksite();
        var selected = inspectorSelection();
        if (area != null) beginPersistentRoute(area.id(), selected instanceof SettlementManagementPayload.Resident resident ? resident.id() : null);
    }

    private void beginPersistentRoute(UUID areaId, UUID residentId) {
        if (this.route.reloading()) enterMode(Mode.SELECT);
        var area = this.areas.get(areaId);
        if (area == null || !permits(SettlementPermission.STAFF) || !SettlementGuardOrder.supports(area.kind().role())) return;
        if (residentId == null && area.plan() == null) {
            this.minecraft.setScreen(new SettlementWorkerScreen(management(), area, this));
            return;
        }
        var resident = this.roster.get(residentId);
        if (residentId != null && (resident == null || !areaId.equals(resident.area()))) return;
        var expected = residentId == null ? area.plan().route() : resident.guardOrder();
        enterMode(Mode.EDIT_ROUTE);
        this.route.begin(areaId, residentId, area.kind().role(), expected, area.plan() == null ? null : area.plan().route());
        this.guardFeedback = Component.empty();
        updateActions();
    }

    private void savePersistentRoute(boolean inherit) {
        if (!this.route.canSubmit() || this.parent.submitting() || this.commands.busy()) return;
        if (!inherit && !this.route.draft().validation().isEmpty()) {
            this.guardFeedback = Component.literal(this.route.draft().validation());
            return;
        }
        var request = this.route.submit(inherit);
        if (request == null) return;
        this.parent.submit(request);
        this.guardFeedback = Component.literal("Saving route...");
        updateActions();
    }

    private Component routeHint() {
        String scope = this.route.resident() == null ? "Worksite default" : this.route.inherited() ? "Worker / inherited draft" : "Worker override";
        String validation = this.route.draft().validation();
        String feedback = !validation.isEmpty() ? validation : this.guardFeedback.getString();
        return Component.literal(scope + " / " + this.route.draft().nodes().size() + "/16 nodes"
                + (this.route.draft().selected() < 0 ? "" : " / selected " + (this.route.draft().selected() + 1))
                + "\n" + (feedback.isEmpty() ? "Click to place. Hold a node to drag. Middle drag pans. Esc cancels." : feedback));
    }

    private int routeHandle(double x, double y) {
        int selected = -1;
        double distance = 10;
        var handles = this.route.draft().handles();
        for (int index = 0; index < handles.size(); index++) {
            var point = project(Vec3.atCenterOf(handles.get(index)));
            if (point == null) continue;
            double candidate = Math.hypot(point[0] - x, point[1] - y);
            if (candidate < distance) { selected = index; distance = candidate; }
        }
        return selected;
    }

    private boolean sharedRouteAvailable(SettlementManagementPayload.Area area) {
        return area != null && area.plan() != null && permits(SettlementPermission.STAFF)
                && (area.kind() == SettlementAreaType.GUARD_POST || area.kind() == SettlementAreaType.HUNTING_GROUNDS);
    }

    private void beginGuardOrder(SettlementGuardOrder.Mode mode) {
        beginGuardOrder(mode, null);
    }

    private void beginGuardOrder(SettlementGuardOrder.Mode mode, UUID worksite) {
        if (worksite != null && mode == SettlementGuardOrder.Mode.PATROL) {
            beginPersistentRoute(worksite, null);
            return;
        }
        if (this.guardMode == SettlementGuardOrder.Mode.PATROL && mode == this.guardMode
                && java.util.Objects.equals(worksite, this.guardWorksite)) {
            if (this.guardPoints.size() >= 2) {
                submitGuardOrder(new SettlementGuardOrder(mode, this.guardPoints));
            }
            return;
        }
        cancelGizmo();
        enterMode(Mode.GUARD_TARGET);
        this.guardMode = mode;
        this.guardWorksite = worksite;
        this.guardPoints.clear();
        this.guardFeedback = Component.empty();
        if (this.interaction.is(Mode.DRAW_AREA)) this.interaction.enter(Mode.SELECT);
        this.dragStart = null;
        this.dragEnd = null;
        updateActions();
    }

    private void submitGuardOrder(SettlementGuardOrder order) {
        if (this.commands.busy() || this.parent.submitting()) return;
        if (this.guardWorksite != null) {
            var area = this.areas.get(this.guardWorksite);
            if (!sharedRouteAvailable(area)) return;
            this.parent.submit(new SettlementManagementActionPayload.SaveRoute(area.id(), null, area.plan().route(), order));
        } else {
            if (!selectedGuard()) return;
            submitBatch(order == null ? SettlementViewAction.RETURN : switch (order.mode()) {
                case MOVE -> SettlementViewAction.MOVE;
                case PATROL -> SettlementViewAction.PATROL;
                case DEFEND -> SettlementViewAction.DEFEND;
            }, null, order);
        }
        this.guardMode = null;
        this.guardWorksite = null;
        if (this.interaction.is(Mode.GUARD_TARGET)) this.interaction.enter(Mode.SELECT);
        this.guardPoints.clear();
        this.guardFeedback = text("guard_sending");
        updateActions();
    }

    private void enterMode(Mode mode) {
        if (this.paths.confirming()) return;
        this.paths.cancel(this.session, PacketDistributor::sendToServer);
        this.resourceKind = null;
        this.resourcePreview = null;
        this.resourceSurvey = null;
        clearControls();
        this.boxX = -1;
        this.boxDragging = false;
        this.dragStart = null;
        this.dragEnd = null;
        this.guardMode = null;
        this.guardWorksite = null;
        this.guardPoints.clear();
        if (mode != Mode.PLACE_BUILDING) this.parent.clearBuildingDraft();
        if (mode != Mode.EDIT_BOUNDS) {
            this.editingArea = null;
            this.areaBounds = null;
        }
        this.gizmo = null;
        this.popup = null;
        this.interaction.enter(mode);
    }

    private void cancelGizmo() {
        if (this.interaction.is(Mode.EDIT_BOUNDS) || this.interaction.is(Mode.PLACE_BUILDING)) this.interaction.enter(Mode.SELECT);
        this.parent.clearBuildingDraft();
        this.editingArea = null;
        this.areaBounds = null;
        this.gizmo = null;
        this.popup = null;
    }

    private void synchronizeGizmo() {
        var draft = this.parent.buildingDraft();
        if (draft != null) {
            this.interaction.enter(Mode.PLACE_BUILDING);
            if (this.gizmo == null || !this.buildingGizmo) {
                this.gizmo = new SettlementSelectionGizmo(draft.bounds(), true, false, true, this::allowedGizmoBounds);
                this.buildingGizmo = true;
                this.editingArea = null;
                if (this.interaction.is(Mode.DRAW_AREA)) this.interaction.enter(Mode.SELECT);
                this.guardMode = null;
                if (this.interaction.is(Mode.GUARD_TARGET)) this.interaction.enter(Mode.SELECT);
            }
            this.gizmo.bounds(draft.bounds());
        } else if (this.editingArea != null) {
            if (this.gizmo == null || this.buildingGizmo) {
                this.gizmo = new SettlementSelectionGizmo(this.areaBounds, true, true, false, this::allowedGizmoBounds);
                this.buildingGizmo = false;
            }
            this.gizmo.bounds(this.areaBounds);
        } else {
            this.gizmo = null;
        }
        if (this.gizmo != null) this.gizmo.scale(this.navigation.cameraHeight() * 0.075);
    }

    private boolean allowedGizmoBounds(AABB bounds) {
        var level = this.minecraft.level;
        var min = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ);
        var max = BlockPos.containing(bounds.maxX - 1, bounds.maxY - 1, bounds.maxZ - 1);
        if (level == null || level.isOutsideBuildHeight(min) || level.isOutsideBuildHeight(max)
                || !level.getWorldBorder().isWithinBounds(min) || !level.getWorldBorder().isWithinBounds(max)
                || min.getX() < this.header.center().getX() - this.window.radius() || max.getX() > this.header.center().getX() + this.window.radius()
                || min.getZ() < this.header.center().getZ() - this.window.radius() || max.getZ() > this.header.center().getZ() + this.window.radius()) return false;
        return this.parent.buildingDraft() != null || bounds.getXsize() <= 128 && bounds.getYsize() <= 64 && bounds.getZsize() <= 128
                && bounds.getXsize() * bounds.getYsize() * bounds.getZsize() <= com.jvn.villagerretaliation.settlement.SettlementArea.MAX_VOLUME;
    }

    private void adjustGizmo(SettlementSelectionGizmo.Change change) {
        var draft = this.parent.buildingDraft();
        if (draft != null) {
            draft.placed = true;
            draft.rotate(change.turns());
            var old = draft.bounds();
            draft.move(draft.anchor.offset((int) Math.round(change.bounds().minX - old.minX),
                    (int) Math.round(change.bounds().minY - old.minY), (int) Math.round(change.bounds().minZ - old.minZ)));
        } else {
            this.areaBounds = change.bounds();
        }
    }

    private boolean permits(SettlementPermission permission) {
        return this.header.permissions().contains(permission);
    }

    List<UUID> selectedResidents() { return this.selection.residents(); }

    List<BlockPos> selectedBeds() { return this.selection.beds(); }

    private void openHousingAssignments() {
        if (this.commands.busy() || !permits(SettlementPermission.STAFF)) return;
        this.popup = null;
        SettlementViewClient.openPanels(this);
        this.minecraft.setScreen(new SettlementHousingAssignmentScreen(this, List.copyOf(this.roster.values()),
                List.copyOf(this.beds.values()), this.selection.residents(), this.selection.beds()));
    }

    boolean assignHomes(List<SettlementViewBatchPayload.Target> targets) {
        if (targets.isEmpty() || this.commands.busy() || !permits(SettlementPermission.STAFF)) return false;
        sendBatch(new SettlementViewBatchPayload(this.session, SettlementViewBatchPayload.operation(this.session, ++this.commandSequence),
                SettlementViewAction.ASSIGN_HOME, targets, null, null));
        return true;
    }

    private Operation selectionOperation() {
        return Operation.modifiers(shiftDown(), hasControlDown() || held(GLFW.GLFW_KEY_LEFT_CONTROL) || held(GLFW.GLFW_KEY_RIGHT_CONTROL));
    }

    private BlockPos bedPosition(BlockPos position) {
        if (position == null || this.minecraft.level == null) return null;
        var state = this.minecraft.level.getBlockState(position);
        if (!(state.getBlock() instanceof net.minecraft.world.level.block.BedBlock)) return null;
        return state.getValue(net.minecraft.world.level.block.BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD
                ? position : position.relative(state.getValue(net.minecraft.world.level.block.BedBlock.FACING));
    }

    private AABB bedBounds(BlockPos head) {
        var state = this.minecraft.level.getBlockState(head);
        return state.getBlock() instanceof net.minecraft.world.level.block.BedBlock
                ? new AABB(head).minmax(new AABB(head.relative(state.getValue(net.minecraft.world.level.block.BedBlock.FACING).getOpposite())))
                : new AABB(head);
    }

    boolean hasProject(UUID id) { return this.projects.containsKey(id); }

    List<SettlementUiWindow> uiWindows() { return List.copyOf(this.windows); }

    int viewportWidth() { return this.width; }

    SettlementOverviewMap overviewMap() { return this.overviewMap; }

    private boolean shiftDown() {
        return hasShiftDown() || held(GLFW.GLFW_KEY_LEFT_SHIFT) || held(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    private boolean held(int key) {
        return this.navigation.held(key);
    }

    void focus(double x, double z) {
        this.peekFocus = Set.of();
        this.navigation.stopPan();
        pan(x - this.navigation.x(), z - this.navigation.z());
        this.requestedFocus = new Vec3(x, 0, z);
        this.moved = true;
    }

    private void pan(double x, double z) {
        if (this.navigation.pan(this.window, x, z)) {
            this.requestedFocus = null;
            this.peekFocus = Set.of();
            this.moved = true;
        }
    }

    private void zoom(double direction) {
        this.moved |= this.navigation.zoom(direction, this.minecraft.options.getEffectiveRenderDistance());
    }

    private void positionCamera() {
        positionCamera(0.0D);
    }

    private void positionCamera(double elapsed) {
        if (this.minecraft == null || this.minecraft.level == null) {
            return;
        }
        var pose = this.navigation.position(this.window,
                this.minecraft.options.getEffectiveRenderDistance() * 16.0D - 8.0D, this::terrainHeight, elapsed);
        SettlementViewClient.position(pose, elapsed);
    }

    private double terrainHeight(int x, int z) {
        var chunk = this.minecraft.level.getChunkSource().getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16), false);
        return chunk == null ? Double.NaN : chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 1.0D;
    }

    void clearControls() {
        this.route.cancelDrag();
        this.panning = false;
        this.draggingWindow = null;
        this.resizingWindow = null;
        if (this.gizmo != null) this.gizmo.end();
        this.navigation.clear();
        this.peekKey = null;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (SettlementViewClient.transitioning()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) onClose();
            return true;
        }
        if (this.storagePicker != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) finishStoragePick(null);
            else if (SettlementViewNavigation.handles(keyCode)) this.navigation.press(keyCode);
            return true;
        }
        if (this.search != null && this.search.isFocused()) {
            clearControls();
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { this.search.setFocused(false); setFocused(null); return true; }
            this.search.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (this.popup != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.popup = null;
                this.assignmentReview = null;
            } else this.popup.key(keyCode);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && this.boxX >= 0) {
            this.boxX = -1;
            this.boxDragging = false;
            return true;
        }
        if (SettlementViewKeyMappings.matches(SettlementViewKeyMappings.PEEK, keyCode, scanCode)) {
            this.peekKey = InputConstants.getKey(keyCode, scanCode);
            return true;
        }
        if (this.paths.active()) {
            if (this.paths.confirming()) return true;
            var path = this.paths.draft();
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.paths.stopExtending();
            } else if (hasControlDown() && keyCode == GLFW.GLFW_KEY_Z) {
                if (hasShiftDown()) path.redo(); else path.undo();
            } else if (hasControlDown() && keyCode == GLFW.GLFW_KEY_Y) path.redo();
            else if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) path.remove();
            else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                this.paths.confirm(this.session, PacketDistributor::sendToServer);
            else if (SettlementViewNavigation.handles(keyCode)) this.navigation.press(keyCode);
            updatePathButtons();
            return true;
        }
        if (this.route.draft() != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (this.route.draft().dragging()) this.route.cancelDrag();
                else if (!this.parent.submitting()) enterMode(Mode.SELECT);
                updateActions();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) { savePersistentRoute(false); return true; }
            if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!this.parent.submitting()) this.route.draft().remove();
                updateRouteButtons();
                return true;
            }
            if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
                this.route.draft().select(keyCode - GLFW.GLFW_KEY_1);
                updateRouteButtons();
                return true;
            }
            if (this.route.draft().dragging()) return true;
        }
        if (this.route.draft() == null && toolShortcut(keyCode, scanCode)) return true;
        if (keyCode == GLFW.GLFW_KEY_DELETE && this.route.draft() == null) {
            if (inspectorSelection() instanceof SettlementManagementPayload.Area area) confirmRemoveArea(area);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F) { focusSelection(); return true; }
        if (keyCode == GLFW.GLFW_KEY_HOME) { focus(this.window.center().getX(), this.window.center().getZ()); return true; }
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            int group = keyCode - GLFW.GLFW_KEY_1;
            activateGroup(group, (modifiers & GLFW.GLFW_MOD_CONTROL) != 0);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && !this.interaction.is(Mode.SELECT)) {
            if (this.gizmo != null && this.gizmo.dragging()) this.gizmo.end();
            else enterMode(Mode.SELECT);
            updateActions();
            updatePlotActions();
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && this.guardMode == SettlementGuardOrder.Mode.PATROL) {
            if (this.guardPoints.size() >= 2) submitGuardOrder(new SettlementGuardOrder(this.guardMode, this.guardPoints));
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && this.editingArea != null) {
            confirmDraft();
            return true;
        }
        if (SettlementViewNavigation.handles(keyCode)) {
            if (this.gizmo == null || !this.gizmo.dragging()) this.navigation.press(keyCode);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean toolShortcut(int key, int scan) {
        if (SettlementViewKeyMappings.matches(SettlementViewKeyMappings.BOUNDS, key, scan)) {
            SettlementBoundsClient.toggle(this.header.id());
        } else if (SettlementViewKeyMappings.matches(SettlementViewKeyMappings.ACTIONS, key, scan)) {
            openActionPopup(SettlementViewPopup.Kind.ACTIONS);
        } else if (this.commands.busy() || this.parent.submitting() || this.gizmo != null && this.gizmo.dragging()) {
            return false;
        } else if (SettlementViewKeyMappings.matches(SettlementViewKeyMappings.SELECT, key, scan)) {
            enterMode(Mode.SELECT);
        } else if (SettlementViewKeyMappings.matches(SettlementViewKeyMappings.PLOTS, key, scan)) {
            togglePlots();
        } else if (SettlementViewKeyMappings.matches(SettlementViewKeyMappings.DRAW, key, scan)) {
            if (!permits(SettlementPermission.BUILD)) return true;
            enterMode(this.interaction.is(Mode.DRAW_AREA) ? Mode.SELECT : Mode.DRAW_AREA);
        } else if (SettlementViewKeyMappings.matches(SettlementViewKeyMappings.MOVE, key, scan)
                || SettlementViewKeyMappings.matches(SettlementViewKeyMappings.RESIZE, key, scan)) {
            if (this.gizmo == null && inspectorSelection() instanceof SettlementManagementPayload.Area area) beginBoundsEdit(area);
            if (this.gizmo != null) this.gizmo.mode(SettlementViewKeyMappings.matches(SettlementViewKeyMappings.RESIZE, key, scan)
                    ? SettlementSelectionGizmo.Mode.RESIZE : SettlementSelectionGizmo.Mode.TRANSFORM);
        } else return false;
        clearControls();
        updateActions();
        updatePlotActions();
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (InputConstants.getKey(keyCode, scanCode).equals(this.peekKey)) {
            this.peekKey = null;
            return true;
        }
        if (SettlementViewNavigation.handles(keyCode)) {
            this.navigation.release(keyCode);
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void mouseMoved(double x, double y) {
        if (this.popup != null) this.popup.hover(x, y);
        if (this.paths.active()) {
            boolean overButton = this.pathButtons.stream().anyMatch(button -> button.visible && button.isMouseOver(x, y));
            var handle = this.popup == null && !overButton && worldContains(x, y) && windowAt(x, y) == null
                    ? SettlementPathGizmo.hit(this.paths.draft(), this::project, x, y) : null;
            this.paths.hoveredHandle(handle);
            this.paths.hover(this.popup == null && worldContains(x, y) && windowAt(x, y) == null && !overButton && handle == null ? pathPoint(x, y) : null);
        }
        super.mouseMoved(x, y);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (SettlementViewClient.transitioning()) return true;
        if (this.route.draft() != null && this.route.draft().dragging()) return true;
        if (this.popup != null && this.popup.scroll(x, y, vertical) || this.gizmo != null && this.gizmo.dragging()) return true;
        var hoveredWindow = windowAt(x, y);
        if (hoveredWindow == this.mapWindow && this.overviewMap.contains(x, y)) {
            if (vertical != 0) this.overviewMap.zoom(-vertical, this.navigation.x(), this.navigation.z());
            return true;
        }
        if (hoveredWindow == this.mapWindow) return true;
        if (hoveredWindow == this.sidebarWindow) {
            if (this.sidebarWindow.title(x, y)) return true;
            if (this.inspector.visible()) { this.inspector.scroll(vertical); return true; }
            if (vertical == 0) return true;
            this.browser.scroll += vertical > 0 ? -1 : 1;
            updateBrowserRows();
            return true;
        }
        if (worldContains(x, y)) {
            zoom(-vertical);
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (SettlementViewClient.transitioning()) return true;
        if (worldContains(x, y) && this.popup == null && (this.search == null || !this.search.isFocused())
                && SettlementViewKeyMappings.PEEK.matchesMouse(button)) {
            this.peekKey = InputConstants.Type.MOUSE.getOrCreate(button);
            return true;
        }
        if (this.popup != null) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.popup.target().kind() == SettlementViewPopup.Kind.BUILD
                    && this.buildMenu.isMouseOver(x, y)) {
                this.popup = null;
                return true;
            }
            if (this.popup.click(x, y, button)) return true;
            this.popup = null;
        }
        if (worldContains(x, y)) updatePeek(x, y);
        var clickedWindow = windowAt(x, y);
        if (clickedWindow != null) {
            this.windows.remove(clickedWindow);
            this.windows.add(clickedWindow);
            if (button == 0 && clickedWindow.resizeCorner(x, y) != 0) {
                clearControls();
                this.boxX = -1;
                this.boxDragging = false;
                setFocused(null);
                this.resizingWindow = clickedWindow;
                clickedWindow.grabResize(x, y);
                return true;
            }
            for (var widget : clickedWindow.widgets()) if (widget.visible && widget.mouseClicked(x, y, button)) {
                setFocused(widget);
                if (button == 0) setDragging(true);
                return true;
            }
            if (button == 0 && clickedWindow.title(x, y)) {
                clearControls();
                this.boxX = -1;
                this.boxDragging = false;
                setFocused(null);
                this.draggingWindow = clickedWindow;
                clickedWindow.grab(x, y);
                return true;
            }
            if (clickedWindow == this.sidebarWindow) {
                if (this.route.draft() == null && !this.paths.active()) this.inspector.click(x, y, button);
                return true;
            }
            if (!this.overviewMap.contains(x, y)) return true;
        }
        if (this.storagePicker != null) {
            if (clickedWindow == null && super.mouseClicked(x, y, button)) return true;
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) finishStoragePick(null);
            else if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && worldContains(x, y)) this.panning = true;
            else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.preferences.map && this.overviewMap.contains(x, y)) {
                focus(this.overviewMap.worldX(x), this.overviewMap.worldZ(y));
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && worldContains(x, y)) {
                var position = pick(x, y).position();
                if (position == null) this.guardFeedback = Component.literal("No loaded map surface at this position.");
                else finishStoragePick(position);
            }
            return true;
        }
        if (clickedWindow == null && super.mouseClicked(x, y, button)) {
            return true;
        }
        if (this.preferences.map && this.overviewMap.contains(x, y)) {
            if (this.route.draft() != null || this.paths.active()) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) focus(this.overviewMap.worldX(x), this.overviewMap.worldZ(y));
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                var marker = this.overviewMap.marker(x, y);
                if (marker != null) inspect(marker.entry());
                else {
                    BlockPos point = BlockPos.containing(this.overviewMap.worldX(x), this.header.center().getY(), this.overviewMap.worldZ(y));
                    var area = selectableAreas().stream().filter(a -> point.getX() >= a.min().getX() && point.getX() <= a.max().getX()
                            && point.getZ() >= a.min().getZ() && point.getZ() <= a.max().getZ()).findFirst().orElse(null);
                    if (area != null) inspect(this.projects.containsKey(area.id()) ? this.projects.get(area.id()) : area);
                    else focus(this.overviewMap.worldX(x), this.overviewMap.worldZ(y));
                }
            }
            return true;
        }
        if (!worldContains(x, y)) return true;
        if (this.paths.active() && button != GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            if (this.paths.confirming()) return true;
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                this.paths.stopExtending();
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) pathClick(x, y);
            updatePathButtons();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            if (this.route.draft() != null && this.route.draft().dragging() || this.paths.active() && this.paths.draft().dragging()) return true;
            this.panning = true;
            return true;
        }
        if (this.route.draft() != null) {
            if (this.parent.submitting()) return true;
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                if (this.route.draft().dragging()) this.route.cancelDrag();
                else enterMode(Mode.SELECT);
                updateActions();
                return true;
            }
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
            int handle = routeHandle(x, y);
            if (handle >= 0) {
                if (this.search != null) this.search.setFocused(false);
                setFocused(null);
                clearControls();
                this.route.beginDrag(handle, x, y);
            } else {
                var position = pick(x, y).position();
                this.guardFeedback = Component.literal(position == null ? "No loaded map surface at this position"
                        : this.route.draft().place(position.above()));
            }
            updateRouteButtons();
            return true;
        }
        if (this.interaction.is(Mode.SELECT_PLOT) && (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
            var hit = pick(x, y);
            if (hit.position() != null) selectPlot(new net.minecraft.world.level.ChunkPos(hit.position()));
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && this.parent.buildingDraft() != null) {
            cancelGizmo();
            updateActions();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && this.guardMode != null) {
            this.guardMode = null;
            this.guardWorksite = null;
            if (this.interaction.is(Mode.GUARD_TARGET)) this.interaction.enter(Mode.SELECT);
            this.guardPoints.clear();
            updateActions();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && this.interaction.is(Mode.TASK_AREA)) {
            enterMode(Mode.SELECT);
            this.guardFeedback = Component.empty();
            updateActions();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            openContext(pick(x, y), x, y);
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        var draft = this.parent.buildingDraft();
        if (this.gizmo != null && (draft == null || draft.placed) && this.gizmo.begin(ray(x, y))) return true;
        var hit = pick(x, y);
        if (this.interaction.is(Mode.TASK_AREA)) {
            this.dragEntity = hit.entity();
            this.dragStart = hit.position();
            this.dragEnd = hit.position();
            return true;
        }
        if (draft != null) {
            if (hit.position() != null) {
                draft.move(hit.position().above());
                draft.placed = true;
                synchronizeGizmo();
                updateActions();
            }
            return true;
        }
        if (this.editingArea != null) return true;
        if (this.guardMode != null) {
            if (hit.position() != null && this.guardPoints.size() < SettlementGuardOrder.MAX_POINTS) {
                BlockPos destination = hit.position().above();
                if (!this.guardPoints.contains(destination)) {
                    this.guardPoints.add(destination);
                }
                if (this.guardMode != SettlementGuardOrder.Mode.PATROL) {
                    submitGuardOrder(new SettlementGuardOrder(this.guardMode, this.guardPoints));
                }
            }
            return true;
        }
        this.guardFeedback = Component.empty();
        if (!this.interaction.is(Mode.DRAW_AREA)) {
            this.boxX = x;
            this.boxY = y;
            this.boxEndX = x;
            this.boxEndY = y;
            this.boxOperation = selectionOperation();
            this.beforeBox = this.selection.residents();
            this.bedsBeforeBox = this.selection.beds();
            this.boxDragging = false;
        }
        var matching = matchingAreas(hit, x, y);
        select(hit, matching.isEmpty() ? null : matching.getFirst());
        if (hit.resident() == null && bedPosition(hit.position()) == null && matching.size() > 1 && !this.interaction.is(Mode.DRAW_AREA)) openChooser(matching, x, y);
        if (this.interaction.is(Mode.DRAW_AREA)) {
            this.dragStart = hit.position();
            this.dragEnd = hit.position();
        }
        updateActions();
        return true;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double deltaX, double deltaY) {
        if (SettlementViewClient.transitioning()) return true;
        if (button == 0 && this.resizingWindow != null) {
            var window = this.resizingWindow;
            if (window.resize(x, y)) {
                rebuildWidgets();
                this.resizingWindow = window;
            }
            return true;
        }
        if (button == 0 && this.draggingWindow != null) {
            int[] delta = this.draggingWindow.drag(x, y);
            if (this.draggingWindow == this.sidebarWindow) this.inspector.move(delta[0], delta[1]);
            else layoutMap();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.paths.active() && this.paths.draft().dragging()) {
            if (this.paths.moved(x, y)) {
                var node = this.paths.draft().path().nodes().get(this.paths.draft().selected());
                var point = this.paths.dragPart() == 0 ? pathPoint(x, y) : SettlementPathGizmo.plane(ray(x, y), node.position().y);
                if (point != null) this.paths.draft().drag(point, hasAltDown());
            }
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.route.draft() != null && this.route.draft().dragging()) {
            var position = worldContains(x, y) ? pick(x, y).position() : null;
            this.route.drag(x, y, position);
            this.guardFeedback = Component.literal(position == null ? "No map surface. Release restores the original node." : this.route.draft().validation());
            updateRouteButtons();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && this.panning) {
            var movement = this.navigation.drag(deltaX, deltaY, this.height, SettlementViewClient.FOV);
            pan(movement.x, movement.z);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.gizmo != null && this.gizmo.dragging()) {
            this.gizmo.drag(ray(Math.clamp(x, 0, this.width - 1), y)).ifPresent(this::adjustGizmo);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && (this.interaction.is(Mode.DRAW_AREA)
                || this.interaction.is(Mode.TASK_AREA)) && this.dragStart != null) {
            BlockPos position = pick(Math.clamp(x, 0, this.width - 1), y).position();
            if (position != null) {
                this.dragEnd = position;
            }
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.boxX >= 0) {
            this.boxEndX = Math.clamp(x, 0, this.width - 1);
            this.boxEndY = Math.clamp(y, 0, this.height - 1);
            this.boxDragging |= Math.hypot(x - this.boxX, y - this.boxY) >= 4;
            if (this.boxDragging) this.popup = null;
            return true;
        }
        return super.mouseDragged(x, y, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (InputConstants.Type.MOUSE.getOrCreate(button).equals(this.peekKey)) {
            this.peekKey = null;
            return true;
        }
        if (button == 0 && (this.draggingWindow != null || this.resizingWindow != null)) {
            if (this.resizingWindow != null) mouseDragged(x, y, button, 0, 0);
            this.draggingWindow = null;
            this.resizingWindow = null;
            this.preferences.sidebarWindow = this.sidebarWindow.position();
            this.preferences.mapWindow = this.mapWindow.position();
            savePreferences();
            setDragging(false);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.paths.active() && this.paths.draft().dragging()) {
            if (worldContains(x, y)) {
                boolean closeLoop = !this.paths.moved(x, y) && this.paths.draft().closingClick();
                mouseDragged(x, y, button, 0, 0);
                this.paths.draft().finish();
                if (closeLoop) this.paths.draft().toggleLoop();
            } else this.paths.draft().cancelDrag();
            updatePathButtons();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.route.draft() != null && this.route.draft().dragging()) {
            if (this.route.movedSinceDrag(x, y)) mouseDragged(x, y, button, 0, 0);
            boolean valid = this.route.finishDrag(position -> worldContains(x, y)
                    && !this.minecraft.level.isOutsideBuildHeight(position));
            this.guardFeedback = Component.literal(valid ? "Draft updated. Save applies the route." : "Invalid drop. Original node restored.");
            updateRouteButtons();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.boxX >= 0) {
            if (this.boxDragging) finishBox();
            else if (this.boxOperation == Operation.REPLACE && !this.selection.beds().isEmpty()) {
                this.popup = new SettlementViewPopup(SettlementViewPopup.Target.of(SettlementViewPopup.Kind.BEDS, null), x, y);
                refreshPopup();
            }
            this.boxX = -1;
            this.boxDragging = false;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) this.panning = false;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.gizmo != null && this.gizmo.dragging()) {
            this.gizmo.end();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.interaction.is(Mode.TASK_AREA) && this.dragStart != null && this.dragEnd != null) {
            openTemporaryTaskPicker();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.interaction.is(Mode.DRAW_AREA) && this.dragStart != null && this.dragEnd != null) {
            AABB bounds = draftBounds();
            var area = new SettlementManagementPayload.Area(UUID.randomUUID(), "", SettlementAreaType.HOME,
                    BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                    BlockPos.containing(bounds.maxX - 1.0D, bounds.maxY - 1.0D, bounds.maxZ - 1.0D), 5, true, 0, 0, 0);
            this.interaction.enter(Mode.EDIT_BOUNDS);
            this.editingArea = area;
            this.areaBounds = bounds;
            if (this.interaction.is(Mode.DRAW_AREA)) this.interaction.enter(Mode.SELECT);
            this.dragStart = null;
            this.dragEnd = null;
            synchronizeGizmo();
            updateActions();
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    private SettlementSelectionGizmo.Ray ray(double x, double y) {
        var camera = this.minecraft.gameRenderer.getMainCamera();
        double tangent = Math.tan(Math.toRadians(SettlementViewClient.FOV / 2.0D));
        Vec3 direction = new Vec3(camera.getLookVector())
                .add(new Vec3(camera.getLeftVector()).scale(-(x * 2.0D - this.width) * tangent / this.height))
                .add(new Vec3(camera.getUpVector()).scale((1.0D - y / this.height * 2.0D) * tangent));
        return new SettlementSelectionGizmo.Ray(camera.getPosition(), direction);
    }

    void clearPeek() {
        this.peek.clear();
    }

    private void updatePeek(double x, double y) {
        if (!permits(SettlementPermission.BUILD) || this.popup != null
                || this.parent.buildingDraft() != null || this.gizmo != null || !this.minecraft.isWindowActive()
                || this.search != null && this.search.isFocused()) {
            clearPeek();
            return;
        }
        if (this.peekKey == null && this.peekFocus.isEmpty()) {
            this.peek.release();
            return;
        }
        var candidates = new ArrayList<SettlementViewBounds>();
        for (var area : this.areas.values()) {
            if (area.enabled() && switch (area.kind()) {
                case HOME, KITCHEN, WORKSHOP, SMELTERY, BREWERY, DEPOT, TRADING_POST, GUARD_POST -> true;
                default -> false;
            }) candidates.add(new SettlementViewBounds(area.min(), area.max()));
        }
        for (var project : this.projects.values()) {
            if (project.status() == SettlementProject.Status.COMPLETED)
                candidates.add(new SettlementViewBounds(project.min(), project.max()));
        }
        var ray = focusedPeekRay(candidates);
        if (ray == null && this.peekKey != null && worldContains(x, y)) ray = ray(x, y);
        if (ray == null) this.peek.release();
        else this.peek.update(this.minecraft.level, this.window, candidates, ray);
    }

    private SettlementSelectionGizmo.Ray focusedPeekRay(List<SettlementViewBounds> candidates) {
        if (this.peekFocus.isEmpty()) return null;
        var start = this.minecraft.gameRenderer.getMainCamera().getPosition();
        var entities = entities();
        for (var id : this.peekFocus) {
            var entity = entities.get(id);
            if (entity == null || !this.selection.contains(id) || !entity.isAlive() || !this.window.contains(entity.blockPosition())) continue;
            var end = entity.getEyePosition();
            var hit = this.minecraft.level.clip(new ClipContext(start, end, ClipContext.Block.VISUAL,
                    ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
            if (hit.getType() != HitResult.Type.MISS && candidates.stream().anyMatch(bounds ->
                    bounds.box().contains(end) && bounds.box().contains(hit.getBlockPos().getCenter())))
                return new SettlementSelectionGizmo.Ray(start, end.subtract(start));
        }
        return null;
    }

    private Pick pick(double x, double y) {
        var camera = this.minecraft.gameRenderer.getMainCamera();
        var ray = ray(x, y);
        Vec3 start = ray.origin();
        Vec3 end = start.add(ray.direction().scale(256.0D));
        var block = SettlementViewPeek.clip(this.minecraft.level, start, end, ClipContext.Block.OUTLINE, camera.getEntity());
        double distance = block.getType() == HitResult.Type.MISS ? 256.0D * 256.0D : start.distanceToSqr(block.getLocation());
        UUID residentId = null;
        UUID targetId = null;
        BlockPos targetPosition = null;
        for (Entity entity : entities().all()) {
            if (this.window.contains(entity.blockPosition()) && (entity instanceof Villager && this.roster.containsKey(entity.getUUID())
                    || this.interaction.is(Mode.TASK_AREA) && entity instanceof net.minecraft.world.entity.LivingEntity)) {
                var hit = entity.getBoundingBox().inflate(0.2D).clip(start, end);
                if (hit.isPresent() && hit.get().distanceToSqr(start) < distance) {
                    distance = hit.get().distanceToSqr(start);
                    residentId = this.roster.containsKey(entity.getUUID()) ? entity.getUUID() : null;
                    targetId = entity.getUUID();
                    targetPosition = entity.blockPosition();
                }
            }
        }
        BlockPos position = block.getType() != HitResult.Type.MISS && this.window.contains(block.getBlockPos()) ? block.getBlockPos() : null;
        return new Pick(this.interaction.is(Mode.TASK_AREA) && targetPosition != null ? targetPosition : position, residentId, targetId);
    }

    void renderWorldSelection(RenderLevelStageEvent event) {
        var draft = this.parent.buildingDraft();
        if (draft != null) ConstructionBlueprintPreviewRenderer.renderPreview(event, draft.ghost(), false);
        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        var buffers = this.minecraft.renderBuffers().bufferSource();
        var lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        if (this.paths.active()) SettlementPathGizmo.renderTerrain(this.paths, pose, lines);
        for (var area : this.areas.values()) {
            if ((area.id().equals(this.selectedArea) || this.preferences.workOverlay)
                    && this.window.chunks().stream().anyMatch(chunk -> chunk.getMaxBlockX() >= area.min().getX()
                    && chunk.getMinBlockX() <= area.max().getX() && chunk.getMaxBlockZ() >= area.min().getZ()
                    && chunk.getMinBlockZ() <= area.max().getZ())) {
                boolean selected = area.id().equals(this.selectedArea);
                LevelRenderer.renderLineBox(pose, lines, new AABB(Vec3.atLowerCornerOf(area.min()), Vec3.atLowerCornerOf(area.max().offset(1, 1, 1))),
                        selected ? 0.25F : 0.65F, selected ? 0.9F : 0.65F,
                        selected ? 1.0F : 0.65F, selected ? 1.0F : 0.45F);
            }
        }
        for (var bed : this.selection.beds()) {
            if (contains(bed)) LevelRenderer.renderLineBox(pose, lines, bedBounds(bed).inflate(0.015D), 0.25F, 0.8F, 1.0F, 1.0F);
        }
        if (this.gizmo == null && this.selectedBlock != null && this.selectedArea == null && this.selectedResident == null) {
            LevelRenderer.renderLineBox(pose, lines, new AABB(this.selectedBlock).inflate(0.015D),
                    0.25F, 0.9F, 1.0F, 1.0F);
        }
        if (this.gizmo == null && this.hovered.position() != null && !this.hovered.position().equals(this.selectedBlock)
                && this.hovered.resident() == null && !SettlementViewClient.panelsOpen(this)) {
            LevelRenderer.renderLineBox(pose, lines, new AABB(this.hovered.position()).inflate(0.015D),
                    1.0F, 0.78F, 0.25F, 0.7F);
        }
        if (this.selection.size() != 0 || this.hovered.resident() != null) {
            var entities = entities();
            for (var id : this.selection.residents()) {
                var entity = entities.get(id);
                if (entity != null) LevelRenderer.renderLineBox(pose, lines, entity.getBoundingBox().inflate(0.08D),
                        0.25F, 0.9F, 1.0F, 1.0F);
            }
            var hovered = entities.get(this.hovered.resident());
            if (hovered != null && !this.selection.contains(hovered.getUUID()) && !SettlementViewClient.panelsOpen(this))
                LevelRenderer.renderLineBox(pose, lines, hovered.getBoundingBox().inflate(0.08D), 1.0F, 0.78F, 0.25F, 0.7F);
        }
        Set<List<BlockPos>> routes = new java.util.LinkedHashSet<>();
        if (this.guardMode != null) routes.add(List.copyOf(this.guardPoints));
        for (var id : this.selection.residents()) {
            var r = this.roster.get(id);
            var order = this.resident != null && this.resident.id().equals(id) ? this.resident.guardOrder() : r == null ? null : r.guardOrder();
            var assigned = r == null ? null : this.areas.get(r.area());
            if (order == null && assigned != null && assigned.plan() != null) order = assigned.plan().route();
            if (order != null) routes.add(order.traversal());
        }
        var selectedWorksite = this.areas.get(this.selectedArea);
        if (selectedWorksite != null && selectedWorksite.plan() != null && selectedWorksite.plan().route() != null)
            routes.add(selectedWorksite.plan().route().traversal());
        if (this.route.draft() != null) {
            routes.clear();
            routes.add(this.route.draft().nodes());
        }
        for (var points : routes) {
            for (int i = 0; i < points.size(); i++) {
                BlockPos point = points.get(i);
                LevelRenderer.renderLineBox(pose, lines, new AABB(point).deflate(0.15D), 1, 1, 1, 1);
                if (i > 0) SettlementSelectionGizmo.line(pose, lines, Vec3.atCenterOf(points.get(i - 1)),
                        Vec3.atCenterOf(point), 1, 1, 1, 1);
            }
        }
        if (this.route.draft() != null) {
            var nodes = this.route.draft().nodes();
            boolean valid = (!this.route.draft().dragging() || this.route.dragHit()) && this.route.draft().validation().isEmpty();
            float green = valid ? 0.85F : 0.2F;
            if (this.route.draft().loop() && nodes.size() > 1) SettlementSelectionGizmo.line(pose, lines,
                    Vec3.atCenterOf(nodes.getLast()), Vec3.atCenterOf(nodes.getFirst()), 1, green, 0.2F, 1);
            for (var branch : this.route.draft().branches()) SettlementSelectionGizmo.line(pose, lines,
                    Vec3.atCenterOf(branch.anchor()), Vec3.atCenterOf(branch.end()), 1, green, 0.2F, 1);
            if (this.route.preview() != null && !this.route.draft().dragging()) {
                LevelRenderer.renderLineBox(pose, lines, new AABB(this.route.preview()).deflate(0.1), 1, 0.85F, 0.2F, 0.65F);
                for (var neighbor : this.route.draft().placementNeighbors()) SettlementSelectionGizmo.line(pose, lines,
                        Vec3.atCenterOf(neighbor), Vec3.atCenterOf(this.route.preview()), 1, 0.85F, 0.2F, 0.65F);
            }
            var handles = this.route.draft().handles();
            for (int index = 0; index < handles.size(); index++) {
                boolean selected = index == this.route.draft().selected();
                LevelRenderer.renderLineBox(pose, lines, new AABB(handles.get(index)).inflate(selected ? 0.1 : -0.1),
                        selected ? 1 : 0.4F, green, selected ? 0.2F : 1, 1);
            }
        }
        if (this.preferences.workOverlay || this.preferences.issueOverlay) {
            var entities = entities();
            for (var r : this.roster.values()) {
                var entity = entities.get(r.id());
                if (entity == null || !contains(entity.blockPosition())) continue;
                boolean issue = this.browser.attention.contains(r.id());
                if (this.preferences.issueOverlay && issue || this.preferences.workOverlay) {
                    boolean paused = r.status().equals("paused") || r.status().equals("area_paused");
                    LevelRenderer.renderLineBox(pose, lines, entity.getBoundingBox().inflate(0.13),
                            issue ? 1 : paused ? 0.8F : 0.3F, issue ? 0.35F : 0.8F, 0.3F, 0.8F);
                }
            }
        }
        if (this.resourceSurvey != null) {
            for (var position : this.resourceSurvey.highlights()) LevelRenderer.renderLineBox(pose, lines, new AABB(position).inflate(0.01D), 0.9F, 0.75F, 0.2F, 0.9F);
        }
        if (this.resourcePreview != null) {
            var task = this.resourcePreview;
            if (task.kind().staircase()) {
                boolean xAxis = task.max().getX() - task.min().getX() >= task.max().getZ() - task.min().getZ();
                int start = xAxis ? task.min().getX() : task.min().getZ();
                int end = xAxis ? task.max().getX() : task.max().getZ();
                for (int coordinate = start; coordinate <= end; coordinate++) {
                    int y = task.target().getY() - Math.abs(coordinate - (xAxis ? task.target().getX() : task.target().getZ()));
                    var a = new BlockPos(xAxis ? coordinate : task.min().getX(), y, xAxis ? task.min().getZ() : coordinate);
                    var b = new BlockPos(xAxis ? coordinate : task.max().getX(), y + 2, xAxis ? task.max().getZ() : coordinate);
                    LevelRenderer.renderLineBox(pose, lines, new SettlementViewBounds(a, b).box(), 0.3F, 0.9F, 0.6F, 0.8F);
                }
            } else LevelRenderer.renderLineBox(pose, lines, new SettlementViewBounds(task.min(), task.max()).box(), 0.3F, 0.9F, 0.6F, 0.8F);
        }
        if (this.interaction.is(Mode.TASK_AREA) && this.dragStart != null && this.dragEnd != null) {
            var min = com.jvn.villagerretaliation.interaction.HiredWorkArea.minPos(this.dragStart, this.dragEnd);
            var max = com.jvn.villagerretaliation.interaction.HiredWorkArea.maxPos(this.dragStart, this.dragEnd).above(2);
            LevelRenderer.renderLineBox(pose, lines, new SettlementViewBounds(min, max).box(), 0.3F, 0.9F, 0.6F, 0.8F);
        }
        if (this.interaction.is(Mode.DRAW_AREA) && this.dragStart != null && this.dragEnd != null) {
            LevelRenderer.renderLineBox(pose, lines, draftBounds(), 0.62F, 0.72F, 0.78F, 0.65F);
        }
        if (this.paths.active()) SettlementPathGizmo.renderHandles(this.paths, pose, buffers.getBuffer(SettlementSelectionGizmo.LINES));
        for (var project : this.projects.values()) {
            if ((project.id().equals(this.selectedArea) || this.preferences.issueOverlay)
                    && this.window.chunks().stream().anyMatch(chunk -> chunk.getMaxBlockX() >= project.min().getX()
                    && chunk.getMinBlockX() <= project.max().getX() && chunk.getMaxBlockZ() >= project.min().getZ()
                    && chunk.getMinBlockZ() <= project.max().getZ())) {
                boolean selected = project.id().equals(this.selectedArea);
                LevelRenderer.renderLineBox(pose, buffers.getBuffer(SettlementSelectionGizmo.LINES),
                        new SettlementViewBounds(project.min(), project.max()).box(),
                        selected ? 0.25F : 1.0F, selected ? 0.9F : 0.78F,
                        selected ? 1.0F : 0.25F, selected ? 1.0F : 0.8F);
            }
        }
        if (this.gizmo != null && !SettlementViewClient.panelsOpen(this)) {
            this.gizmo.render(pose, buffers.getBuffer(SettlementSelectionGizmo.LINES), draft == null || draft.preview == null || draft.preview.failure().isEmpty());
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
        buffers.endBatch(SettlementSelectionGizmo.LINES);
    }

    void togglePlots() {
        enterMode(this.interaction.is(Mode.SELECT_PLOT) ? Mode.SELECT : Mode.SELECT_PLOT);
        updateActions();
        updatePlotActions();
    }

    com.jvn.villagerretaliation.network.SettlementPlotSnapshot plots() { return this.plotSnapshot; }

    void selectPlot(net.minecraft.world.level.ChunkPos chunk) {
        this.selectedPlot = chunk;
        this.plotFeedback = Component.empty();
        updatePlotActions();
    }

    private com.jvn.villagerretaliation.network.SettlementPlotSnapshot.Plot plotOffer() {
        return this.plotSnapshot.plots().stream().filter(plot -> plot.chunk().equals(this.selectedPlot)).findFirst().orElse(null);
    }

    private void updatePlotActions() {
        if (this.claimFree == null) return;
        var offer = plotOffer();
        if (this.overlayButton != null) this.overlayButton.visible = !this.interaction.is(Mode.SELECT_PLOT);
        this.claimFree.visible = this.interaction.is(Mode.SELECT_PLOT);
        this.buyPlot.visible = this.interaction.is(Mode.SELECT_PLOT);
        this.claimFree.active = offer != null && offer.freeFailure().isEmpty() && !this.plotPending;
        this.buyPlot.active = offer != null && offer.paidFailure().isEmpty() && !this.plotPending;
        this.buyPlot.setMessage(text("plot_buy", offer == null ? 0 : offer.price()));
    }

    void claimPlot(boolean free) {
        var offer = plotOffer();
        if (offer == null || this.plotPending || !(free ? offer.freeFailure() : offer.paidFailure()).isEmpty()) return;
        this.plotPending = true;
        var payload = new SettlementManagementActionPayload(this.header.id(),
                new SettlementManagementActionPayload.ClaimPlot(offer.chunk().x, offer.chunk().z, free,
                        this.plotSnapshot.totals().revision(), offer.price()));
        this.plotSubmission.sent(payload, Util.getMillis());
        PacketDistributor.sendToServer(payload);
        updatePlotActions();
    }

    private Component plotDescription() {
        var description = Component.empty();
        if (!this.plotSnapshot.totals().conflict().isEmpty()) description.append(this.plotSnapshot.totals().conflict()).append("\n");
        var offer = plotOffer();
        if (offer == null) return description.append(text("plot_select"));
        description.append(text("plot_position", offer.chunk().x, offer.chunk().z)).append("\n");
        if (offer.owned()) description.append(text("plot_owned"));
        else {
            description.append(text("plot_buy", offer.price())).append("\n");
            if (!offer.freeFailure().isEmpty()) description.append(text("plot_" + offer.freeFailure())).append("\n");
            if (!offer.paidFailure().isEmpty() && !offer.paidFailure().equals(offer.freeFailure())) {
                description.append(text("plot_" + offer.paidFailure())).append("\n");
            }
            if (offer.freeFailure().equals("capacity") || offer.paidFailure().equals("capacity")) {
                this.header.stage().next().ifPresent(stage -> description.append(text("plot_next", stage.name())).append("\n"));
            }
        }
        return description.append("\n").append(this.plotFeedback);
    }

    net.minecraft.world.level.ChunkPos hoveredPlot() {
        return this.interaction.is(Mode.SELECT_PLOT) && this.hovered.position() != null
                ? new net.minecraft.world.level.ChunkPos(this.hovered.position()) : null;
    }

    void renderPlotTint(RenderLevelStageEvent event) {
        this.peek.render(event);
        if (this.interaction.is(Mode.SELECT_PLOT)) {
            SettlementPlotRenderer.render(event, this.plotSnapshot, this.selectedPlot, hoveredPlot());
        } else if (SettlementBoundsClient.enabled(this.header.id()) || this.interaction.is(Mode.DRAW_AREA)
                || this.editingArea != null || this.parent.buildingDraft() != null) {
            SettlementPlotRenderer.render(event, this.plotSnapshot, null, null, true);
        }
    }

    private AABB draftBounds() {
        BlockPos first = new BlockPos(this.dragStart.getX(),
                Math.min(this.header.center().getY(), Math.min(this.dragStart.getY(), this.dragEnd.getY())), this.dragStart.getZ());
        BlockPos second = new BlockPos(this.dragEnd.getX(), Math.max(this.dragStart.getY(), this.dragEnd.getY()) + 4, this.dragEnd.getZ());
        return SettlementViewBounds.drawing(first, second, boundsLimits()).box();
    }

    SettlementViewPopup contextPopup() {
        return this.popup;
    }

    private SettlementViewBounds editedBounds() {
        return this.areaBounds == null ? null : new SettlementViewBounds(
                BlockPos.containing(this.areaBounds.minX, this.areaBounds.minY, this.areaBounds.minZ),
                BlockPos.containing(this.areaBounds.maxX - 1, this.areaBounds.maxY - 1, this.areaBounds.maxZ - 1));
    }

    private SettlementViewBounds.Limits boundsLimits() {
        return new SettlementViewBounds.Limits(this.header.center(), this.header.radius(),
                this.minecraft.level.getMinBuildHeight(), this.minecraft.level.getMaxBuildHeight() - 1);
    }

    private void confirmRemoveArea(SettlementManagementPayload.Area area) {
        if (!this.areas.containsKey(area.id()) || !permits(SettlementPermission.BUILD)
                || this.commands.busy() || this.parent.submitting()) return;
        clearControls();
        this.popup = null;
        SettlementViewClient.openPanels(this);
        this.minecraft.setScreen(new SettlementConfirmScreen(this, SettlementManagementScreen.text("remove_area"),
                SettlementManagementScreen.text("remove_area_hint", area.name()), () -> {
                    if (this.editingArea != null && this.editingArea.id().equals(area.id())) cancelGizmo();
                    this.parent.submit(new SettlementManagementActionPayload.RemoveArea(area.id()));
                }));
    }

    private void beginBoundsEdit(SettlementManagementPayload.Area area) {
        if (area == null || !permits(SettlementPermission.BUILD) || this.commands.busy()) return;
        enterMode(Mode.EDIT_BOUNDS);
        this.editingArea = area;
        this.areaBounds = new SettlementViewBounds(area.min(), area.max()).box();
        synchronizeGizmo();
        updateActions();
    }

    private void confirmDraft() {
        if (this.editingArea != null && this.gizmo != null && !this.gizmo.dragging()
                && allowedGizmoBounds(this.areaBounds) && permits(SettlementPermission.BUILD) && !this.commands.busy()) {
            this.applyGizmo.onPress();
        }
    }

    void areaSaved(UUID id) {
        this.snapshots.refresh();
        cancelGizmo();
        this.selectedArea = id;
        this.selection.select(List.of(), false);
        this.selectedResident = null;
        this.resident = null;
        this.areas.remove(id);
        this.mapMarkers.update(this.areas.values(), this.projects.values());
        this.waitingRequestId = -1;
        this.detailsAt = Math.max(this.detailsAt, Util.getMillis() + 300L);
    }

    private static Component boundsDetails(SettlementViewBounds bounds) {
        return text("bounds", bounds.min().toShortString(), bounds.max().toShortString()).copy().append("\n")
                .append(text("dimensions", bounds.size(Direction.Axis.X), bounds.size(Direction.Axis.Y), bounds.size(Direction.Axis.Z)));
    }

    private void select(Pick hit, UUID area) {
        this.inspector.reveal();
        this.inspected = null;
        this.selectedArea = hit.resident() == null ? area : null;
        this.selectedBlock = hit.position();
        var bed = hit.resident() == null ? bedPosition(hit.position()) : null;
        if (bed != null) {
            this.selectedArea = null;
            if (!this.beds.containsKey(bed)) {
                this.guardFeedback = Component.literal("Bed not recorded as usable housing. Check the area and lighting, or wait for refresh.");
                bed = null;
            }
        }
        if (!this.selection.apply(hit.resident() == null ? List.of() : List.of(hit.resident()),
                bed == null ? List.of() : List.of(bed), selectionOperation())) this.guardFeedback = Component.literal("Selection limit: 128 residents and 128 beds");
        syncSelection();
        updateActions();
    }

    private void syncSelection() {
        updateBrowserRows();
        UUID single = this.selection.single();
        if (!java.util.Objects.equals(this.selectedResident, single)) {
            this.selectedResident = single;
            this.resident = null;
            this.residentChecked = false;
            this.residentRefreshAt = 0;
        }
    }

    void endSession() {
        clearPeek();
        savePreferences();
        this.commands.clear();
        this.snapshots.clear();
        this.overviewMap.close();
        this.frameEntities.clear();
        this.mapMarkers.update(List.of(), List.of());
        this.assignmentReview = null;
        this.selection.select(List.of(), false);
        this.roster.clear();
        enterMode(Mode.SELECT);
    }

    void savePreferences() {
        this.preferences.groups = this.selection.groups();
        this.preferences.height = this.navigation.height();
        this.preferences.yaw = this.navigation.yaw();
        this.preferences.save();
    }

    private void focusSelection() {
        var positions = new ArrayList<>(this.selection.residents().stream().map(this.roster::get).filter(java.util.Objects::nonNull)
                .map(SettlementManagementPayload.Resident::position).toList());
        positions.addAll(this.selection.beds());
        if (!positions.isEmpty()) {
            focus(positions.stream().mapToInt(BlockPos::getX).average().orElse(this.navigation.x()),
                    positions.stream().mapToInt(BlockPos::getZ).average().orElse(this.navigation.z()));
            this.peekFocus = Set.copyOf(this.selection.residents());
        } else if (this.selectedArea != null) {
            var area = selectableAreas().stream().filter(row -> row.id().equals(this.selectedArea)).findFirst().orElse(null);
            if (area != null) focus((area.min().getX() + area.max().getX()) / 2.0D, (area.min().getZ() + area.max().getZ()) / 2.0D);
        } else if (this.selectedBlock != null) focus(this.selectedBlock.getX(), this.selectedBlock.getZ());
    }

    private double[] project(Vec3 position) {
        var camera = this.minecraft.gameRenderer.getMainCamera();
        Vec3 offset = position.subtract(camera.getPosition());
        double depth = offset.dot(new Vec3(camera.getLookVector()));
        if (depth <= 0) return null;
        double scale = this.height / (2.0D * Math.tan(Math.toRadians(SettlementViewClient.FOV / 2.0D)) * depth);
        return new double[] {this.width / 2.0D - offset.dot(new Vec3(camera.getLeftVector())) * scale,
                this.height / 2.0D - offset.dot(new Vec3(camera.getUpVector())) * scale};
    }

    private void finishBox() {
        List<UUID> visible = new ArrayList<>();
        for (Entity entity : entities().all()) {
            if (!(entity instanceof Villager) || !this.roster.containsKey(entity.getUUID()) || !contains(entity.blockPosition())) continue;
            var point = project(entity.getBoundingBox().getCenter());
            if (point == null || point[0] < 0 || point[0] >= this.width || point[1] < 0 || point[1] >= this.height) continue;
            if (point[0] < Math.min(this.boxX, this.boxEndX) || point[0] > Math.max(this.boxX, this.boxEndX)
                    || point[1] < Math.min(this.boxY, this.boxEndY) || point[1] > Math.max(this.boxY, this.boxEndY)) continue;
            var camera = this.minecraft.gameRenderer.getMainCamera();
            var obstruction = SettlementViewPeek.clip(this.minecraft.level, camera.getPosition(), entity.getBoundingBox().getCenter(),
                    ClipContext.Block.VISUAL, camera.getEntity());
            if (obstruction.getType() == HitResult.Type.MISS) visible.add(entity.getUUID());
        }
        var visibleBeds = new ArrayList<BlockPos>();
        for (var bed : this.beds.values()) {
            if (!contains(bed.position())) continue;
            var center = bedBounds(bed.position()).getCenter().subtract(0, 0.2D, 0);
            var point = project(center);
            if (point == null || !worldContains(point[0], point[1])
                    || point[0] < Math.min(this.boxX, this.boxEndX) || point[0] > Math.max(this.boxX, this.boxEndX)
                    || point[1] < Math.min(this.boxY, this.boxEndY) || point[1] > Math.max(this.boxY, this.boxEndY)) continue;
            var camera = this.minecraft.gameRenderer.getMainCamera();
            var obstruction = SettlementViewPeek.clip(this.minecraft.level, camera.getPosition(), center,
                    ClipContext.Block.VISUAL, camera.getEntity());
            if (obstruction.getType() == HitResult.Type.MISS || bed.position().equals(bedPosition(obstruction.getBlockPos()))) visibleBeds.add(bed.position());
        }
        this.selection.apply(this.beforeBox, this.bedsBeforeBox, Operation.REPLACE);
        if (!this.selection.apply(visible, visibleBeds, this.boxOperation)) this.guardFeedback = Component.literal("Selection limit: 128 residents and 128 beds");
        this.selectedArea = null;
        this.selectedBlock = null;
        syncSelection();
    }

    private List<UUID> matchingAreas(Pick hit, double x, double y) {
        var ray = ray(x, y);
        Vec3 end = hit.position() == null ? ray.end() : ray.origin().add(ray.direction()
                .scale(ray.origin().distanceTo(Vec3.atCenterOf(hit.position())) + 1.0D));
        var exterior = this.minecraft.level.clip(new ClipContext(ray.origin(), end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, this.minecraft.gameRenderer.getMainCamera().getEntity()));
        if (exterior.getType() != HitResult.Type.MISS && DialogueCameraOcclusion.hides(exterior.getBlockPos())) return List.of();
        if (hit.position() != null) {
            var touching = selectableAreas().stream()
                    .filter(area -> new SettlementViewBounds(area.min(), area.max()).box().contains(Vec3.atCenterOf(hit.position())))
                    .map(SettlementManagementPayload.Area::id).toList();
            if (!touching.isEmpty()) {
                return touching;
            }
        }
        return selectableAreas().stream().filter(area -> new SettlementViewBounds(area.min(), area.max()).box()
                .clip(ray.origin(), end).isPresent()).map(SettlementManagementPayload.Area::id).toList();
    }

    private List<SettlementManagementPayload.Area> selectableAreas() {
        var result = new ArrayList<>(this.areas.values());
        this.projects.values().forEach(project -> result.add(projectArea(project)));
        return result;
    }

    private static SettlementManagementPayload.Area projectArea(SettlementManagementPayload.Project project) {
        return new SettlementManagementPayload.Area(project.id(), project.name(), SettlementAreaType.CONSTRUCTION,
                project.min(), project.max(), 5, true, 0, 0, project.status() == SettlementProject.Status.ACTIVE ? 1 : 0);
    }

    private static Component projectProgress(SettlementManagementPayload.Project project) {
        return SettlementManagementScreen.text("project_row",
                project.status() == SettlementProject.Status.ACTIVE ? SettlementManagementScreen.enumText("project_phase", project.phase())
                        : SettlementManagementScreen.enumText("project_status", project.status()), project.placed(), project.total());
    }

    private void openChooser(List<UUID> choices, double x, double y) {
        this.popup = new SettlementViewPopup(new SettlementViewPopup.Target(SettlementViewPopup.Kind.CHOOSER, null, choices), x, y);
        clearControls();
        refreshPopup();
    }

    private void openContext(Pick hit, double x, double y) {
        clearControls();
        var ray = ray(x, y);
        var depot = hit.resident() == null && this.editingArea == null
                ? this.snapshots.entries(Page.DEPOTS).stream().map(SettlementManagementPayload.Depot.class::cast)
                        .filter(entry -> entry.position().equals(hit.position())).findFirst().orElse(null)
                : null;
        boolean handle = this.editingArea != null && this.gizmo != null && this.gizmo.hover(ray);
        if (handle) {
            this.popup = new SettlementViewPopup(SettlementViewPopup.Target.of(SettlementViewPopup.Kind.DRAFT, this.editingArea.id()), x, y);
        } else if (hit.resident() == null && this.beds.containsKey(bedPosition(hit.position()))) {
            var bed = bedPosition(hit.position());
            if (!this.selection.containsBed(bed)) this.selection.apply(List.of(), List.of(bed), Operation.ADD);
            this.inspected = null;
            this.selectedArea = null;
            this.selectedBlock = bed;
            syncSelection();
            updateActions();
            this.popup = new SettlementViewPopup(SettlementViewPopup.Target.of(SettlementViewPopup.Kind.BEDS, null), x, y);
        } else if (hit.resident() != null) {
            if (!this.selection.contains(hit.resident())) select(hit, null);
            this.popup = new SettlementViewPopup(SettlementViewPopup.Target.of(SettlementViewPopup.Kind.RESIDENT, hit.resident()), x, y);
        } else if (depot != null) {
            inspect(depot);
            this.popup = new SettlementViewPopup(SettlementViewPopup.Target.of(SettlementViewPopup.Kind.DEPOT, depot.id()), x, y);
        } else if (hit.position() != null && this.editingArea == null && permits(SettlementPermission.STAFF)
                && (!resourceWorkers(com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask.Kind.ORE).isEmpty()
                || !resourceWorkers(com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask.Kind.FOREST).isEmpty())) {
            this.resourceTarget = hit.position().immutable();
            this.popup = new SettlementViewPopup(SettlementViewPopup.Target.of(SettlementViewPopup.Kind.RESOURCE, null), x, y);
        } else if (this.editingArea != null && editedBounds().box().clip(ray.origin(), ray.end()).isPresent()) {
            this.popup = new SettlementViewPopup(SettlementViewPopup.Target.of(SettlementViewPopup.Kind.DRAFT, this.editingArea.id()), x, y);
        } else {
            var matching = matchingAreas(hit, x, y);
            select(hit, matching.isEmpty() ? null : matching.getFirst());
            if (matching.size() > 1) {
                openChooser(matching, x, y);
                return;
            }
            if (!matching.isEmpty()) {
                this.popup = new SettlementViewPopup(SettlementViewPopup.Target.of(SettlementViewPopup.Kind.AREA, matching.getFirst()), x, y);
            }
        }
        refreshPopup();
    }

    private void refreshPopup() {
        if (this.popup == null || this.minecraft == null || this.minecraft.level == null) {
            return;
        }
        var target = this.popup.target();
        List<Component> details = new ArrayList<>();
        List<SettlementViewPopup.Action> actions = new ArrayList<>();
        boolean ready = !this.commands.busy();
        switch (target.kind()) {
            case BEDS -> {
                details.add(selectedName());
                for (var position : this.selection.beds()) {
                    var bed = this.beds.get(position);
                    if (bed != null) details.add(Component.literal(position.toShortString() + " / "
                            + (bed.resident() == null ? bed.available() ? "Available" : "Unavailable" : "Assigned to " + bed.residentName())));
                }
                actions.add(new SettlementViewPopup.Action(Component.literal("Assign beds"), ready && permits(SettlementPermission.STAFF), this::openHousingAssignments));
            }
            case DEPOT -> {
                var depot = this.snapshots.entries(Page.DEPOTS).stream().map(SettlementManagementPayload.Depot.class::cast)
                        .filter(entry -> entry.id().equals(target.id())).findFirst().orElse(null);
                if (depot == null) {
                    details.add(text("details_unavailable"));
                } else {
                    details.add(Component.literal(depot.name()));
                    details.add(SettlementManagementScreen.text("field.purpose").append(": ")
                            .append(SettlementManagementScreen.enumText("depot", depot.purpose())));
                    details.add(Component.literal(depot.position().toShortString()));
                    actions.add(menuAction(Component.literal("Edit depot"), ready && !this.parent.submitting()
                            && permits(SettlementPermission.LOGISTICS), () -> this.minecraft.setScreen(
                                    new SettlementEditScreen(management(), SettlementEditScreen.Mode.DEPOT, depot, this))));
                }
            }
            case RESOURCE -> {
                details.add(Component.literal("Resource work"));
                if (this.resourceTarget != null) {
                    var state = this.minecraft.level.getBlockState(this.resourceTarget);
                    details.add(state.getBlock().getName());
                    for (var kind : List.of(com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask.Kind.TREE,
                            com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask.Kind.FOREST,
                            com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask.Kind.ORE,
                            com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask.Kind.ORE_AREA,
                            com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask.Kind.QUARRY,
                            com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask.Kind.TUNNEL,
                            com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask.Kind.STAIRCASE)) {
                        if (kind == com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask.Kind.TREE
                                && !state.is(net.minecraft.tags.BlockTags.LOGS) && !state.is(net.minecraft.tags.BlockTags.LEAVES)) continue;
                        if (kind == com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask.Kind.ORE
                                && !OreNodeCatalogClient.isOre(state)) continue;
                        int workers = resourceWorkers(kind).size();
                        if (workers > 0) actions.add(menuAction(Component.literal(kind.label() + " (" + workers + ")"), ready, () -> resourceOrder(kind)));
                    }
                }
            }
            case FILTERS -> {
                details.add(Component.literal("Choose a filter"));
                for (var filter : this.browser.filters(this.snapshots.entries(this.browser.page))) {
                    actions.add(menuAction(Component.literal(filter), true, () -> {
                        this.browser.filter = filter;
                        this.browser.scroll = 0;
                        updateBrowserRows();
                    }));
                }
            }
            case OVERVIEW -> {
                details.add(Component.literal(this.header.name()));
                details.add(SettlementManagementScreen.text("stage", SettlementManagementScreen.enumText("stage", this.header.stage())));
                details.add(SettlementManagementScreen.text("population", this.header.population(), this.header.housing()));
                details.add(SettlementManagementScreen.text("food", String.format(java.util.Locale.ROOT, "%.1f", this.header.foodDays())));
                details.add(SettlementManagementScreen.text("support", String.format(java.util.Locale.ROOT, "%.1f", this.header.supportTicks() / 24_000.0D),
                        String.format(java.util.Locale.ROOT, "%.1f", this.header.requiredTicks() / 24_000.0D)));
                for (var missing : this.header.missing()) details.add(SettlementManagementScreen.enumText("requirement", missing));
                actions.add(menuAction(Component.literal("Residents"), true, this::browseResidents));
                actions.add(menuAction(Component.literal("Construction"), true, () -> browse(Page.PROJECTS, "All")));
                actions.add(menuAction(Component.literal("Issues (" + this.header.issues() + ")"), true, () -> browse(Page.ISSUES, "All")));
                actions.add(menuAction(Component.literal("Settlement administration"), ready, () -> openManagement(Page.SUMMARY, "")));
            }
            case TREASURY -> {
                details.add(SettlementManagementScreen.text("treasury_balance", this.header.balance().mixedFraction()));
                details.add(SettlementManagementScreen.text("treasury_limits", this.header.reserve().mixedFraction(),
                        this.header.spent().mixedFraction(), this.header.budget().mixedFraction()));
                actions.add(menuAction(SettlementManagementScreen.text("form.treasury"), ready && permits(SettlementPermission.TREASURY),
                        () -> this.minecraft.setScreen(new SettlementEditScreen(management(), SettlementEditScreen.Mode.TREASURY, null))));
                actions.add(menuAction(SettlementManagementScreen.text("form.budget"), ready && permits(SettlementPermission.TREASURY),
                        () -> this.minecraft.setScreen(new SettlementEditScreen(management(), SettlementEditScreen.Mode.BUDGET, null))));
                actions.add(menuAction(Component.literal("Trade orders"), true, () -> browse(Page.TRADE, "All")));
            }
            case FOOD -> {
                details.add(SettlementManagementScreen.text("food", String.format(java.util.Locale.ROOT, "%.1f", this.header.foodDays())));
                details.add(SettlementManagementScreen.text("food_hint"));
                if (this.header.shortageTicks() > 0) details.add(SettlementManagementScreen.text("shortage",
                        String.format(java.util.Locale.ROOT, "%.1f", this.header.shortageTicks() / 24_000.0D)));
                actions.add(menuAction(Component.literal("Farms"), true, () -> browse(Page.AREAS, SettlementViewBrowser.kindFilter(SettlementAreaType.FARM))));
                actions.add(menuAction(SettlementManagementScreen.enumText("page", Page.SUPPLIES), ready, () -> openManagement(Page.SUPPLIES, "")));
                actions.add(menuAction(Component.literal("Depots"), true, () -> browse(Page.DEPOTS, "All")));
                actions.add(menuAction(Component.literal("Settlement issues"), true, () -> browse(Page.ISSUES, "All")));
            }
            case PATH_EDIT -> {
                if (!this.paths.active()) { this.popup = null; return; }
                details.add(Component.literal("Edit path"));
                updatePathButtons();
                for (int index : new int[]{6, 7, 11, 12, 10}) {
                    var button = this.pathButtons.get(index);
                    actions.add(menuAction(button.getMessage(), button.active, () -> pathAction(index)));
                }
            }
            case BUILD -> {
                details.add(Component.literal("Build"));
                boolean available = ready && permits(SettlementPermission.BUILD) && !this.parent.submitting() && !this.paths.confirming();
                actions.add(menuAction(SettlementManagementScreen.text("new_building"),
                        available && this.header.projectLimit() > 0 && this.route.draft() == null && this.storagePicker == null && !this.paths.active(), () -> {
                            enterMode(Mode.PLACE_BUILDING);
                            updatePlotActions();
                            management().newBuilding();
                        }));
                actions.add(menuAction(Component.literal("Path"), available, () -> beginPath(false)));
                actions.add(menuAction(Component.literal("Branch"), available, () -> beginPath(true)));
            }
            case NAVIGATION -> {
                details.add(Component.literal(this.header.name()));
                actions.add(menuAction(SettlementManagementScreen.enumText("page", Page.SUPPLIES), ready, () -> openManagement(Page.SUPPLIES, "")));
                for (var page : List.of(Page.RESIDENTS, Page.AREAS, Page.PROJECTS, Page.DEPOTS, Page.TRADE, Page.ISSUES)) {
                    actions.add(menuAction(SettlementManagementScreen.enumText("page", page), true, () -> browse(page, "All")));
                }
                for (var page : List.of(Page.BEDS, Page.APPLICANTS, Page.DELEGATES, Page.HISTORY, Page.SUMMARY)) {
                    actions.add(menuAction(SettlementManagementScreen.enumText("page", page), ready, () -> openManagement(page, "")));
                }
            }
            case RESIDENT -> {
                var current = this.resident != null && this.resident.id().equals(target.id()) ? this.resident : null;
                if (current == null) {
                    details.add(selectedName());
                    actions.addAll(selectionActions());
                } else {
                    details.add(text("resident", current.name(), Math.round(current.health()), current.food()));
                    details.add(Component.translatableWithFallback(VillagerProfessionUtil.parse(current.profession().toString())
                            .map(profession -> VillagerProfessionUtil.translationKey(profession, "entity.minecraft.villager.none"))
                            .orElse("entity.minecraft.villager.none"), current.profession().getPath()));
                    details.add(SettlementManagementScreen.enumText("presence", current.presence()));
                    details.add(Component.translatableWithFallback(current.status().startsWith("workforce.")
                            ? "villagerretaliation.gui.clipboard_workforce.status." + current.status().substring("workforce.".length())
                            : "villagerretaliation.gui.settlement.work." + current.status(),
                            SettlementManagementScreen.text("work.idle").getString()));
                    var assigned = this.areas.get(current.area());
                    details.add(text("assigned_area", current.area() == null ? text("unassigned")
                            : assigned == null ? text("details_loading") : Component.literal(assigned.name())));
                    var route = current.guardOrder() != null ? current.guardOrder()
                            : assigned == null || assigned.plan() == null ? null : assigned.plan().route();
                    details.add(text("guard_order", text(route == null ? "guard_order_none"
                            : "guard_" + route.mode().name().toLowerCase(java.util.Locale.ROOT))));
                    if (route != null) details.add(Component.literal(current.guardOrder() == null ? "Inherited from worksite" : "Individual override"));
                    actions.add(contextAction("manage_selection", ready, () -> openManagement(Page.RESIDENTS, target.id().toString())));
                    actions.addAll(selectionActions());
                }
            }
            case OVERLAYS -> {
                details.add(Component.literal("World overlays"));
                if (!this.preferences.map) actions.add(menuAction(Component.literal("Show map"), true, () -> {
                    this.preferences.map = true;
                    savePreferences();
                    rebuildWidgets();
                }));
                actions.add(new SettlementViewPopup.Action(SettlementViewKeyMappings.hint(
                        SettlementManagementScreen.text(SettlementBoundsClient.enabled(this.header.id()) ? "hide_bounds" : "show_bounds"),
                        SettlementViewKeyMappings.BOUNDS), true, () -> SettlementBoundsClient.toggle(this.header.id())));
                actions.add(new SettlementViewPopup.Action(Component.literal("Work state: " + (this.preferences.workOverlay ? "On" : "Off")),
                        true, () -> { this.preferences.workOverlay = !this.preferences.workOverlay; savePreferences(); }));
                actions.add(new SettlementViewPopup.Action(Component.literal("Issues: " + (this.preferences.issueOverlay ? "On" : "Off")),
                        true, () -> { this.preferences.issueOverlay = !this.preferences.issueOverlay; savePreferences(); }));
            }
            case ACTIONS -> {
                details.add(selectedName());
                actions.addAll(selectionActions());
            }
            case ASSIGN -> {
                details.add(Component.literal("Choose workplace"));
                for (var area : this.areas.values()) {
                    long eligible = eligibleCount(SettlementViewAction.ASSIGN, area.id());
                    if (eligible == 0) continue;
                    actions.add(new SettlementViewPopup.Action(SettlementViewUi.actionLabel(area.name(), eligible, this.selection.size()), !this.commands.busy(),
                            () -> submitBatch(SettlementViewAction.ASSIGN, area.id(), null), area.id()));
                }
                if (actions.isEmpty()) details.add(Component.literal("No compatible workplaces"));
            }
            case CONFIRM_ASSIGN -> {
                details.add(Component.literal("Review changed assignments"));
                if (this.assignmentReview != null) {
                    var area = this.areas.get(this.assignmentReview.area());
                    for (var member : this.assignmentReview.targets()) {
                        if (member.expectedArea() == null || member.expectedArea().equals(this.assignmentReview.area())
                                || !SettlementViewAction.ASSIGN.eligible(this.roster.get(member.resident()), area)) continue;
                        var r = this.roster.get(member.resident());
                        var previous = this.areas.get(member.expectedArea());
                        details.add(Component.literal(r.name() + ": " + (previous == null ? "Previous workplace" : previous.name())
                                + " -> " + (area == null ? "Unavailable" : area.name())));
                    }
                    actions.add(new SettlementViewPopup.Action(Component.literal("Confirm reassignment"), area != null && !this.commands.busy(),
                            () -> sendBatch(this.assignmentReview)));
                }
                actions.add(new SettlementViewPopup.Action(Component.translatable("gui.cancel"), true, () -> this.popup = null));
            }
            case RESULTS -> {
                details.add(Component.literal(this.commands.summary()));
                for (var result : this.commands.outcomes()) {
                    var r = this.roster.get(result.resident());
                    details.add(Component.literal((r == null ? result.resident().toString() : r.name())
                            + ": " + result.status().name().toLowerCase(java.util.Locale.ROOT) + " / " + result.reason()));
                }
            }
            case AREA -> {
                var area = this.areas.get(target.id());
                var project = this.projects.get(target.id());
                if (project != null) {
                    details.add(Component.literal(project.name()));
                    details.add(projectProgress(project));
                    details.add(boundsDetails(new SettlementViewBounds(project.min(), project.max())));
                    if (!project.blocker().isBlank()) {
                        details.add(Component.translatableWithFallback("villagerretaliation.gui.settlement.project_blocker."
                                + project.blocker().replace("interaction.work.builder.", ""), SettlementManagementScreen.text("project_blocked").getString()));
                    }
                    if (!project.missingMaterials().isBlank()) details.add(SettlementManagementScreen.text("project_missing", project.missingMaterials()));
                    actions.add(contextAction("manage_selection", ready, () -> this.minecraft.setScreen(new SettlementProjectScreen(management(), project, this))));
                    actions.add(new SettlementViewPopup.Action(SettlementManagementScreen.text("project_builder"), ready,
                            () -> openManagement(Page.RESIDENTS, project.builder().toString())));
                    if (permits(SettlementPermission.BUILD)) {
                        actions.add(contextAction("draw_area", ready, () -> {
                            beginBoundsEdit(new SettlementManagementPayload.Area(UUID.randomUUID(), "", SettlementAreaType.HOME,
                                    project.min(), project.max(), 5, true, 0, 0, 0));
                            confirmDraft();
                        }));
                    }
                } else if (area == null) {
                    details.add(text("details_unavailable"));
                } else {
                    details.add(Component.literal(area.name()));
                    details.add(SettlementManagementScreen.enumText("area", area.kind()));
                    details.add(boundsDetails(new SettlementViewBounds(area.min(), area.max())));
                    details.add(SettlementManagementScreen.text(area.enabled() ? "enabled" : "disabled"));
                    details.add(text("area_counts", area.workers(), area.beds(), area.fixtures()));
                    if (area.plan() != null) details.add(SettlementManagementScreen.text("worksite_staff_count", area.workers(), area.plan().desiredStaff()));
                    if (area.kind().role() != null && permits(SettlementPermission.STAFF)) {
                        actions.add(new SettlementViewPopup.Action(SettlementManagementScreen.text(
                                area.plan() == null ? "worksite_adopt" : "worksite_settings"), ready, () -> {
                            var screen = management();
                            this.minecraft.setScreen(screen);
                            this.minecraft.setScreen(new SettlementWorkerScreen(screen, area, this));
                        }));
                        if (area.plan() != null) actions.add(new SettlementViewPopup.Action(SettlementManagementScreen.text("worksite_fill"),
                                ready && !this.parent.submitting(), () -> this.parent.submit(new SettlementManagementActionPayload.FillStaffing(area.id()))));
                        if (sharedRouteAvailable(area)) {
                            if (area.plan().route() != null) details.add(Component.literal("Shared " + area.plan().route().mode().name().toLowerCase(java.util.Locale.ROOT)
                                    + " / " + area.plan().route().points().size() + " points"));
                            actions.add(new SettlementViewPopup.Action(Component.literal("Set shared patrol"), ready && !this.parent.submitting(),
                                    () -> beginGuardOrder(SettlementGuardOrder.Mode.PATROL, area.id())));
                            if (area.kind() == SettlementAreaType.GUARD_POST)
                                actions.add(new SettlementViewPopup.Action(Component.literal("Set shared defend"), ready && !this.parent.submitting(),
                                        () -> beginGuardOrder(SettlementGuardOrder.Mode.DEFEND, area.id())));
                            if (area.plan().route() != null) actions.add(new SettlementViewPopup.Action(Component.literal("Clear shared route"),
                                    ready && !this.parent.submitting(), () -> this.parent.submit(
                                            new SettlementManagementActionPayload.SaveWorksite(area.id(), area.plan().withRoute(null), false))));
                        }
                    }
                    if (permits(SettlementPermission.BUILD)) {
                        actions.add(contextAction("configure", ready, () -> {
                            beginBoundsEdit(area);
                            confirmDraft();
                        }));
                        actions.add(contextAction("edit_bounds", ready, () -> beginBoundsEdit(area)));
                        actions.add(menuAction(SettlementManagementScreen.text("remove_area"), ready && !this.parent.submitting(),
                                () -> confirmRemoveArea(area)));
                    }
                }
            }
            case DRAFT -> {
                if (this.editingArea == null || !this.editingArea.id().equals(target.id())) {
                    details.add(text("details_unavailable"));
                } else {
                    details.add(text("draft"));
                    details.add(boundsDetails(editedBounds()));
                    boolean valid = editedBounds().valid(boundsLimits());
                    details.add(text(valid ? "bounds_valid" : "bounds_invalid"));
                    actions.add(contextAction("configure", ready && valid && permits(SettlementPermission.BUILD), this::confirmDraft));
                    actions.add(contextAction("cancel_area", ready, () -> {
                        cancelGizmo();
                        updateActions();
                    }));
                }
            }
            case CHOOSER -> {
                details.add(text("choose_area"));
                for (UUID id : target.choices()) {
                    var project = this.projects.get(id);
                    var area = project == null ? this.areas.get(id) : projectArea(project);
                    if (area != null) {
                        actions.add(new SettlementViewPopup.Action(text("area_choice", area.name(), SettlementManagementScreen.enumText("area", area.kind())),
                                true, () -> {
                            this.selectedArea = id;
                            this.selectedResident = null;
                            this.resident = null;
                            this.popup = new SettlementViewPopup(SettlementViewPopup.Target.of(SettlementViewPopup.Kind.AREA, id), this.popup.anchorX(), this.popup.anchorY());
                            updateActions();
                            refreshPopup();
                        }));
                    }
                }
                if (actions.isEmpty()) details.add(text("details_unavailable"));
            }
        }
        if (this.commands.busy() && (target.kind() == SettlementViewPopup.Kind.RESIDENT
                || target.kind() == SettlementViewPopup.Kind.ACTIONS || target.kind() == SettlementViewPopup.Kind.ASSIGN)) {
            details.add(Component.literal(this.commands.summary()));
        }
        this.popup.update(this.font, this.width, this.height, details, actions);
    }

    private SettlementViewPopup.Action contextAction(String key, boolean enabled, Runnable run) {
        return new SettlementViewPopup.Action(text(key), enabled, () -> {
            this.popup = null;
            run.run();
        });
    }

    SettlementSelectionInspector inspector() { return this.inspector; }
    SettlementRouteDraft routeDraft() { return this.route.draft(); }

    SettlementManagementPayload.Entry inspectorSelection() {
        if (this.inspected != null) return this.inspected;
        if (this.selectedResident != null) return this.resident == null ? this.roster.get(this.selectedResident) : this.resident;
        if (this.projects.containsKey(this.selectedArea)) return this.projects.get(this.selectedArea);
        return this.areas.get(this.selectedArea);
    }

    private SettlementManagementPayload.Area inspectorWorksite() {
        var selected = inspectorSelection();
        var area = selected instanceof SettlementManagementPayload.Area value ? value
                : selected instanceof SettlementManagementPayload.Resident value ? this.areas.get(value.area())
                : selected instanceof SettlementManagementPayload.Project value ? this.areas.get(value.area())
                : selected instanceof SettlementManagementPayload.Depot value ? this.areas.get(value.area()) : null;
        return area != null && area.kind().role() != null ? area : null;
    }

    private List<SettlementManagementPayload.Resident> inspectorStaff() {
        var selected = inspectorSelection();
        var worksite = inspectorWorksite();
        if (selected instanceof SettlementManagementPayload.Resident resident) return List.of(resident);
        return this.roster.values().stream().filter(row -> worksite != null ? worksite.id().equals(row.area())
                : this.selection.contains(row.id())).toList();
    }

    private void updateInspector() {
        this.inspector.update(this.header, inspectorSelection(), inspectorWorksite(), inspectorStaff(),
                this.snapshots.entries(Page.DEPOTS), this.snapshots.entries(Page.TRADE),
                this.preferences.sidebar && !this.interaction.is(Mode.SELECT_PLOT), this.commands.busy() || this.parent.submitting() || this.route.draft() != null || this.storagePicker != null);
        for (var widget : this.browserWidgets) widget.visible = this.preferences.sidebar && !this.inspector.visible() && !this.interaction.is(Mode.SELECT_PLOT);
        if (this.inspector.visible() && this.search != null) this.search.setFocused(false);
        updateBrowserRows();
    }

    @Override
    public void acceptProduction(com.jvn.villagerretaliation.network.ProductionSnapshotPayload payload) {
        this.inspector.accept(payload);
    }

    @Override
    public void acceptTransfer(com.jvn.villagerretaliation.network.TransferSnapshotPayload payload) {
        this.inspector.acceptTransfers(payload);
    }

    private void inspectorAction(SettlementSelectionInspector.Section section) {
        var selected = inspectorSelection();
        var area = inspectorWorksite();
        switch (section) {
            case OVERVIEW -> {
                if (selected instanceof SettlementManagementPayload.Order) manageSelection();
                else focusSelection();
            }
            case STAFF -> {
                if (selected instanceof SettlementManagementPayload.Project project) {
                    this.minecraft.setScreen(new SettlementProjectScreen(management(), project, this));
                } else if (selected instanceof SettlementManagementPayload.Area && area != null && area.plan() != null) {
                    this.parent.submit(new SettlementManagementActionPayload.FillStaffing(area.id()));
                } else {
                    this.selection.select(inspectorStaff().stream().map(SettlementManagementPayload.Resident::id).toList(), false);
                    this.selectedArea = null;
                    syncSelection();
                    openActionPopup(SettlementViewPopup.Kind.ACTIONS);
                }
            }
            case ORDERS -> {
                if (area != null && com.jvn.villagerretaliation.interaction.work.production.ProductionManagementService.supports(area.kind().role())) {
                    this.minecraft.setScreen(new com.jvn.villagerretaliation.client.inventory.ProductionOrdersScreen(management(),
                            new com.jvn.villagerretaliation.network.ProductionTarget(this.header.id(), area.id()),
                            () -> SettlementViewClient.resume(this.parent)));
                } else if (area != null && area.kind() == SettlementAreaType.DEPOT) {
                    this.minecraft.setScreen(new com.jvn.villagerretaliation.client.inventory.TransferOrdersScreen(management(),
                            new com.jvn.villagerretaliation.network.ProductionTarget(this.header.id(), area.id()),
                            () -> SettlementViewClient.resume(this.parent)));
                } else if (area != null && area.kind() == SettlementAreaType.TRADING_POST) {
                    openManagement(Page.TRADE, "");
                } else if (area != null && area.kind() == SettlementAreaType.FISHERY) {
                    if (selected instanceof SettlementManagementPayload.Resident resident && resident.settings() != null) {
                        this.minecraft.setScreen(new SettlementWorkerScreen(management(), resident, this));
                    } else this.minecraft.setScreen(new SettlementWorkerScreen(management(), area, this));
                } else if (selected instanceof SettlementManagementPayload.Resident) {
                    openActionPopup(SettlementViewPopup.Kind.ACTIONS);
                } else if (area != null && (area.kind() == SettlementAreaType.GUARD_POST || area.kind() == SettlementAreaType.HUNTING_GROUNDS)) {
                    if (sharedRouteAvailable(area)) beginGuardOrder(SettlementGuardOrder.Mode.PATROL, area.id());
                    else this.minecraft.setScreen(new SettlementWorkerScreen(management(), area, this));
                } else if (area != null && area.kind() == SettlementAreaType.CONSTRUCTION) {
                    openManagement(Page.PROJECTS, selected instanceof SettlementManagementPayload.Project project ? project.id().toString() : "");
                } else {
                    this.selection.select(inspectorStaff().stream().map(SettlementManagementPayload.Resident::id).toList(), false);
                    this.selectedArea = null;
                    syncSelection();
                    executeAction(SettlementViewAction.TEMPORARY_TASK);
                }
            }
            case SETTINGS -> {
                if (selected instanceof SettlementManagementPayload.Depot || selected instanceof SettlementManagementPayload.Order) {
                    manageSelection();
                } else if (selected instanceof SettlementManagementPayload.Resident resident && resident.settings() != null) {
                    this.minecraft.setScreen(new SettlementWorkerScreen(management(), resident, this));
                } else if (area != null) this.minecraft.setScreen(new SettlementWorkerScreen(management(), area, this));
            }
            case STORAGE -> {
                if (area != null && area.kind().role() != null) {
                    if (area.plan() == null && !(selected instanceof SettlementManagementPayload.Resident)) {
                        this.minecraft.setScreen(new SettlementWorkerScreen(management(), area, this));
                    } else this.minecraft.setScreen(new SettlementStorageScreen(management(), this, area,
                            selected instanceof SettlementManagementPayload.Resident resident ? resident : null));
                }
            }
        }
    }

    void pickStorage(SettlementStorageScreen form) {
        enterMode(Mode.SELECT_STORAGE);
        this.storagePicker = form;
        SettlementViewClient.resume(this.parent);
        this.guardFeedback = Component.literal("Select a loaded storage block. Middle-drag pans. Escape or right-click returns without adding.");
        updateActions();
        updateInspector();
    }

    private void finishStoragePick(BlockPos position) {
        var form = this.storagePicker;
        this.storagePicker = null;
        enterMode(Mode.SELECT);
        this.guardFeedback = Component.empty();
        if (position != null) form.picked(position);
        SettlementViewClient.openPanels(this);
        this.minecraft.setScreen(form);
        form.request();
    }

    private SettlementManagementScreen management() {
        SettlementViewClient.openPanels(this);
        return this.parent;
    }

    private void openManagement(Page page, String query) {
        var screen = management();
        this.minecraft.setScreen(screen);
        screen.navigateTo(page, query);
    }

    private void manageSelection() {
        if (!this.selection.beds().isEmpty()) { openHousingAssignments(); return; }
        if (this.inspected instanceof SettlementManagementPayload.Depot depot) {
            this.minecraft.setScreen(new SettlementEditScreen(management(), SettlementEditScreen.Mode.DEPOT, depot));
            return;
        }
        if (this.inspected instanceof SettlementManagementPayload.Order order) {
            this.minecraft.setScreen(new SettlementEditScreen(management(), SettlementEditScreen.Mode.ORDER, order));
            return;
        }
        if (this.inspected instanceof SettlementManagementPayload.Issue issue) {
            if (!SettlementIssueView.open(management(), issue))
                openManagement(issue.target(), issue.targetId() == null ? "" : issue.targetId().toString());
            return;
        }
        if (this.resident != null) {
            openManagement(Page.RESIDENTS, this.resident.id().toString());
        } else if (this.projects.containsKey(this.selectedArea)) {
            this.minecraft.setScreen(new SettlementProjectScreen(management(), this.projects.get(this.selectedArea), this));
        } else if (this.areas.containsKey(this.selectedArea)) {
            this.parent.clearBuildingDraft();
            this.editingArea = this.areas.get(this.selectedArea);
            this.areaBounds = new AABB(Vec3.atLowerCornerOf(this.editingArea.min()), Vec3.atLowerCornerOf(this.editingArea.max().offset(1, 1, 1)));
            synchronizeGizmo();
            updateActions();
        }
    }

    private void editArea(SettlementManagementPayload.Area area) {
        var screen = management();
        this.minecraft.setScreen(screen);
        this.minecraft.setScreen(new SettlementEditScreen(screen, SettlementEditScreen.Mode.AREA, area, this));
    }

    @Override
    public void removed() {
        clearControls();
        if (!SettlementViewClient.panelsOpen(this)) {
            SettlementViewClient.end(this);
        }
    }

    @Override
    public void onClose() {
        SettlementViewClient.close(this);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static Component text(String key, Object... arguments) {
        return Component.translatable("villagerretaliation.gui.settlement_view." + key, arguments);
    }

    private record Pick(BlockPos position, UUID resident, UUID entity) {
        Pick(BlockPos position, UUID resident) { this(position, resident, null); }
    }
}
