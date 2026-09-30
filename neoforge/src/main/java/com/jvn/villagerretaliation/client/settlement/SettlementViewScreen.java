package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.client.item.ConstructionBlueprintPreviewRenderer;
import com.jvn.villagerretaliation.client.settlement.SettlementViewInteraction.Mode;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.network.SettlementManagementActionResultPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page;
import com.jvn.villagerretaliation.network.SettlementViewRequestPayload;
import com.jvn.villagerretaliation.network.SettlementViewStatePayload;
import com.jvn.villagerretaliation.settlement.SettlementAreaType;
import com.jvn.villagerretaliation.settlement.SettlementViewAction;
import com.jvn.villagerretaliation.network.SettlementViewBatchPayload;
import com.jvn.villagerretaliation.network.SettlementViewBatchResultPayload;
import com.jvn.villagerretaliation.settlement.SettlementGuardOrder;
import com.jvn.villagerretaliation.settlement.SettlementPermission;
import com.jvn.villagerretaliation.settlement.SettlementProject;
import com.jvn.villagerretaliation.settlement.SettlementViewWindow;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
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
    private static final double MOVE_RESPONSE = 12.0D;
    private static final double ZOOM_RESPONSE = 14.0D;
    private final SettlementManagementScreen parent;
    private final UUID session;
    private final SettlementViewSnapshots snapshots;
    private final SettlementViewSelection selection = new SettlementViewSelection();
    private final SettlementViewPreferences preferences;
    private final Map<UUID, SettlementManagementPayload.Resident> roster = new LinkedHashMap<>();
    private final SettlementViewBrowser browser = new SettlementViewBrowser();
    private final SettlementOverviewMap overviewMap = new SettlementOverviewMap();
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
    private boolean boxAdd;
    private boolean boxDragging;
    private List<UUID> beforeBox = List.of();
    private final Map<UUID, SettlementManagementPayload.Area> areas = new LinkedHashMap<>();
    private final Map<UUID, SettlementManagementPayload.Project> projects = new LinkedHashMap<>();
    private final Set<Integer> heldKeys = new HashSet<>();
    private final SettlementViewCamera camera = new SettlementViewCamera();
    private SettlementViewPopup popup;
    private boolean panning;
    private SettlementManagementPayload.Header header;
    private SettlementViewWindow window;
    private double focusX;
    private double focusZ;
    private Vec3 requestedFocus;
    private float viewHeight;
    private double cameraHeight;
    private double panVelocityX;
    private double panVelocityZ;
    private Pick hovered = new Pick(null, null);
    private int requestId;
    private int acceptedRequestId;
    private int queuedChunks;
    private long nextViewRequest;
    private long keepAliveAt;
    private long lastFrame;
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
    private Button newBuilding;
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
    private SettlementRouteDraft routeDraft;
    private UUID routeArea;
    private UUID routeResident;
    private SettlementGuardOrder routeExpected;
    private boolean routeInherited;
    private boolean routeSubmitted;
    private boolean routeUncertain;
    private boolean routeReloading;
    private boolean routeDragHit;
    private double routeDragX;
    private double routeDragY;
    private BlockPos routePlacementPreview;
    private final List<Button> routeButtons = new ArrayList<>();
    private final SettlementViewCommandTracker commands = new SettlementViewCommandTracker();
    private SettlementViewBatchPayload assignmentReview;
    private Button groupActions;
    private Button commandResults;
    private Component guardFeedback = Component.empty();
    private SettlementStorageScreen storagePicker;
    private UUID selectedResident;
    private SettlementManagementPayload.Resident resident;
    private boolean residentChecked;
    private UUID selectedArea;
    private BlockPos selectedBlock;
    private BlockPos dragStart;
    private UUID dragEntity;
    private BlockPos dragEnd;
    private final SettlementViewInteraction interaction = new SettlementViewInteraction();
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
        this.focusX = this.window.x();
        this.focusZ = this.window.z();
        this.viewHeight = this.window.height();
        this.preferences = SettlementViewPreferences.forSettlement(this.header.id());
        this.selection.restore(this.preferences.groups);
        this.viewHeight = Math.clamp(this.preferences.height, SettlementViewWindow.MIN_HEIGHT,
                Math.min(SettlementViewWindow.MAX_HEIGHT, Math.max(SettlementViewWindow.MIN_HEIGHT,
                        net.minecraft.client.Minecraft.getInstance().options.getEffectiveRenderDistance() * 12.0F)));
        this.camera.rotate((this.preferences.yaw - this.camera.yaw()) / 90.0D, 1);
        this.cameraHeight = this.viewHeight;
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
        return this.focusX;
    }

    double focusZ() {
        return this.focusZ;
    }

    float viewHeight() {
        return this.viewHeight;
    }

    void rotateCamera(double degrees) {
        this.camera.rotate(degrees / 90.0D, 1.0D);
    }

    float cameraYaw() {
        return this.camera.yaw();
    }

    void resumeManagement(SettlementManagementScreen management) {
        this.snapshots.refresh();
        var updated = management.snapshot().header();
        if (updated != null && updated.id().equals(this.header.id())) {
            this.header = updated;
        }
        clearControls();
        this.lastFrame = 0L;
        this.waitingRequestId = -1;
        this.detailsAt = Math.max(this.detailsAt, Util.getMillis() + 300L);
    }

    double cameraHeight() {
        return this.cameraHeight;
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
        this.plotSnapshot = payload.plots();
        this.queuedChunks = payload.queuedChunks();
        this.window = payload.window();
        layoutMap();
        updatePlotActions();
        SettlementViewChunks.update(this.window);
        if (payload.requestId() >= this.requestId && !this.moved) {
            this.focusX = this.window.x();
            this.focusZ = this.window.z();
            this.viewHeight = this.window.height();
            if (Math.abs(this.cameraHeight - this.viewHeight) < 0.01D) {
                this.cameraHeight = this.viewHeight;
            }
        }
        positionCamera();
    }

    void acceptOverview(com.jvn.villagerretaliation.network.SettlementViewOverviewPayload payload) {
        if (!this.snapshots.accept(payload, Util.getMillis())) return;
        this.header = this.snapshots.header();
        this.browser.updateAttention(this.snapshots.entries(Page.ISSUES));
        this.roster.clear();
        for (var entry : this.snapshots.entries(Page.RESIDENTS)) {
            var row = (SettlementManagementPayload.Resident) entry;
            this.roster.put(row.id(), row);
        }
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
        if (this.routeReloading && this.routeDraft != null) {
            this.routeReloading = false;
            beginPersistentRoute(this.routeArea, this.routeResident);
        }
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
        if (this.routeSubmitted) {
            this.routeSubmitted = false;
            if (payload.applied()) enterMode(Mode.SELECT);
        }
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
        this.lastFrame = 0L;
        this.sidebarWidgets.clear();
        this.browserRows.clear();
        this.browserWidgets.clear();
        this.layout = new SettlementViewLayout(this.width, this.height, this.preferences.sidebar, this.preferences.map);
        this.panelWidth = this.layout.panelWidth;
        this.panelLeft = this.layout.panelLeft;
        initBrowser();
        for (var child : children()) if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) this.browserWidgets.add(widget);
        this.inspector.init(this.panelLeft, this.panelWidth, SettlementViewLayout.HEADER_HEIGHT, this.layout.contentBottom, this::addRenderableWidget);
        this.manage = addRenderableWidget(Button.builder(text("manage_selection"), ignored -> manageSelection())
                .bounds(this.panelLeft + 6, this.layout.contentBottom - 22, this.panelWidth - 12, 20).build());
        for (var child : children()) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
                this.sidebarWidgets.add(widget);
                widget.visible = this.preferences.sidebar;
            }
        }
        int nameWidth = Math.clamp(this.width / 5, 82, 144);
        addRenderableWidget(new Button(2, 2, nameWidth - 4, 30, text("overview"),
                ignored -> openManagement(Page.SUMMARY, ""), narration -> narration.get()) {
            @Override
            protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                if (isHoveredOrFocused()) graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xFF81B6FF);
            }
        });
        addRenderableWidget(Button.builder(Component.literal(this.preferences.sidebar ? ">" : "<"), ignored -> {
            this.preferences.sidebar = !this.preferences.sidebar;
            savePreferences();
            rebuildWidgets();
        }).bounds(this.width - 50, 7, 20, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Toggle settlement browser"))).build());
        addRenderableWidget(new SettlementViewUi.IconButton(Component.translatable("gui.done"), this.width - 26, 7, 22, 20,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BARRIER), false, false, () -> false, this::onClose));
        int toolTop = SettlementViewLayout.HEADER_HEIGHT + 6;
        addRenderableWidget(tool(Component.literal("Select"), toolTop, net.minecraft.world.item.Items.ARROW, false,
                () -> this.interaction.is(Mode.SELECT) && this.gizmo == null && this.guardMode == null,
                () -> { enterMode(Mode.SELECT); updateActions(); updatePlotActions(); }));
        addRenderableWidget(tool(Component.literal("Residents"), toolTop + this.layout.railSize + 2,
                net.minecraft.world.item.Items.EMERALD, true, () -> this.preferences.sidebar && !this.inspector.visible() && this.browser.page == Page.RESIDENTS,
                this::browseResidents));
        this.drawArea = addRenderableWidget(tool(text("draw_area"), toolTop + (this.layout.railSize + 2) * 2,
                net.minecraft.world.item.Items.OAK_FENCE, false, () -> this.interaction.is(Mode.DRAW_AREA), () -> {
                    enterMode(this.interaction.is(Mode.DRAW_AREA) ? Mode.SELECT : Mode.DRAW_AREA);
                    updatePlotActions();
                    updateActions();
                }));
        this.newBuilding = addRenderableWidget(tool(SettlementManagementScreen.text("new_building"),
                toolTop + (this.layout.railSize + 2) * 3, net.minecraft.world.item.Items.BRICKS, false,
                () -> this.parent.buildingDraft() != null, () -> {
                    enterMode(Mode.PLACE_BUILDING);
                    updatePlotActions();
                    management().newBuilding();
                }));
        addRenderableWidget(tool(text("plots"), toolTop + (this.layout.railSize + 2) * 4,
                net.minecraft.world.item.Items.FILLED_MAP, false, () -> this.interaction.is(Mode.SELECT_PLOT), this::togglePlots));
        this.overlayButton = addRenderableWidget(tool(Component.literal("Overlays"),
                toolTop + (this.layout.railSize + 2) * 5, net.minecraft.world.item.Items.SPYGLASS, false,
                () -> this.preferences.workOverlay || this.preferences.issueOverlay, () -> openActionPopup(SettlementViewPopup.Kind.OVERLAYS)));
        this.claimFree = addRenderableWidget(Button.builder(text("plot_free"), ignored -> claimPlot(true))
                .bounds(this.layout.worldLeft, this.layout.commandBottom - 44, Math.max(40, (this.panelLeft - this.layout.worldLeft - 10) / 2), 20).build());
        this.buyPlot = addRenderableWidget(Button.builder(text("plot_buy", 0), ignored -> claimPlot(false))
                .bounds(this.claimFree.getRight() + 4, this.claimFree.getY(), this.claimFree.getWidth(), 20).build());
        for (var mode : SettlementGuardOrder.Mode.values()) {
            this.guardButtons[mode.ordinal()] = addRenderableWidget(Button.builder(text("guard_" + mode.name().toLowerCase(java.util.Locale.ROOT)),
                    ignored -> beginGuardOrder(mode, this.guardWorksite)).bounds(this.layout.worldLeft, this.layout.commandTop, 72, 20).build());
        }
        this.returnToPost = addRenderableWidget(Button.builder(text("guard_post"), ignored -> submitGuardOrder(null))
                .bounds(this.layout.worldLeft, this.layout.commandTop + 22, 148, 20).build());
        this.groupActions = addRenderableWidget(Button.builder(Component.literal("Actions"), ignored ->
                openActionPopup(SettlementViewPopup.Kind.ACTIONS)).bounds(this.layout.worldLeft, this.layout.commandBottom - 22, 88, 18).build());
        this.commandResults = addRenderableWidget(Button.builder(Component.literal("Results"), ignored ->
                openActionPopup(SettlementViewPopup.Kind.RESULTS)).bounds(this.layout.worldLeft + 92, this.layout.commandBottom - 22, 88, 18).build());
        initRouteButtons();
        this.transform = addRenderableWidget(Button.builder(text("gizmo_move"), ignored -> {
            if (this.gizmo != null) this.gizmo.mode(SettlementSelectionGizmo.Mode.TRANSFORM);
        }).bounds(this.layout.worldLeft, this.layout.commandTop, 92, 20).build());
        this.resize = addRenderableWidget(Button.builder(text("gizmo_resize"), ignored -> {
            if (this.gizmo != null) this.gizmo.mode(SettlementSelectionGizmo.Mode.RESIZE);
        }).bounds(this.layout.worldLeft + 96, this.layout.commandTop, 68, 20).build());
        this.applyGizmo = addRenderableWidget(Button.builder(text("gizmo_area_settings"), ignored -> {
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
        this.changePrefab = addRenderableWidget(Button.builder(SettlementManagementScreen.text("change_prefab"), ignored -> management().newBuilding())
                .bounds(this.layout.worldLeft, this.layout.commandTop + 22, 144, 20).build());
        this.cancelGizmo = addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), ignored -> cancelGizmo())
                .bounds(this.layout.worldLeft, this.layout.commandTop + 22, 76, 20).build());
        layoutMap();
        int mapButtonX = this.preferences.sidebar ? this.panelLeft + 6 : this.overviewMap.left;
        int mapButtonY = this.preferences.sidebar && this.layout.dockedMap && this.preferences.map
                ? this.overviewMap.top - 20 : this.height - 24;
        int mapButtonWidth = this.preferences.sidebar ? this.panelWidth - 12 : this.overviewMap.width;
        addRenderableWidget(Button.builder(Component.literal(this.preferences.map ? "Hide map" : "Map"), ignored -> {
            this.preferences.map = !this.preferences.map;
            savePreferences();
            rebuildWidgets();
        }).bounds(mapButtonX, mapButtonY, mapButtonWidth, 18).build());
        if (this.preferences.map) {
            int left = this.layout.dockedMap ? this.panelLeft + 6 : this.overviewMap.left - 24;
            addRenderableWidget(new SettlementViewUi.IconButton(text("center"), left, this.overviewMap.top, 20, 18,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COMPASS), false, false, () -> false,
                    () -> { this.overviewMap.center(); focus(this.window.center().getX() + 0.5D, this.window.center().getZ() + 0.5D); }));
            addRenderableWidget(new SettlementViewUi.IconButton(text("map_zoom_in"), left, this.overviewMap.top + 20, 20, 18,
                    net.minecraft.world.item.ItemStack.EMPTY, false, false, () -> false, () -> this.overviewMap.zoom(-1, this.focusX, this.focusZ)).symbol("+"));
            addRenderableWidget(new SettlementViewUi.IconButton(text("map_zoom_out"), left, this.overviewMap.top + 40, 20, 18,
                    net.minecraft.world.item.ItemStack.EMPTY, false, false, () -> false, () -> this.overviewMap.zoom(1, this.focusX, this.focusZ)).symbol("-"));
        }
        updateBrowserRows();
        updateActions();
        updatePlotActions();
        positionCamera();
    }

    private SettlementViewUi.IconButton tool(Component label, int top, net.minecraft.world.item.Item icon, boolean portrait,
                                             java.util.function.BooleanSupplier selected, Runnable action) {
        return new SettlementViewUi.IconButton(label, 6, top, this.layout.railSize, this.layout.railSize,
                new net.minecraft.world.item.ItemStack(icon), false, portrait, selected, action);
    }

    private void browseResidents() {
        if (!this.preferences.sidebar) {
            this.preferences.sidebar = true;
            savePreferences();
            rebuildWidgets();
        }
        this.inspector.browse();
        this.browser.page = Page.RESIDENTS;
        this.browser.filter = "All";
        this.browser.scroll = 0;
        updateInspector();
    }

    private void layoutMap() {
        if (this.layout != null) this.overviewMap.layout(this.layout.mapLeft, this.layout.mapTop, this.layout.mapWidth,
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
                    38, w, 18, showLabel ? net.minecraft.world.item.ItemStack.EMPTY : new net.minecraft.world.item.ItemStack(icon),
                    showLabel, !showLabel && tab == Page.RESIDENTS, () -> this.browser.page == tab, () -> {
                this.browser.page = tab;
                this.browser.filter = "All";
                this.browser.scroll = 0;
                updateBrowserRows();
            }));
            left += w;
        }
        this.search = addRenderableWidget(new net.minecraft.client.gui.components.EditBox(this.font,
                this.panelLeft + 7, 60, this.panelWidth - 14, 16, Component.literal("Search settlement")));
        this.search.setHint(Component.literal("Search residents..."));
        this.search.setValue(this.browser.query);
        this.search.setResponder(value -> {
            this.browser.query = value;
            this.browser.scroll = 0;
            updateBrowserRows();
        });
        this.filterButton = addRenderableWidget(Button.builder(Component.literal(this.browser.filter), ignored -> {
            var filters = this.browser.filters(this.snapshots.entries(this.browser.page));
            this.browser.filter = filters.get((filters.indexOf(this.browser.filter) + 1) % filters.size());
            this.browser.scroll = 0;
            updateBrowserRows();
        }).bounds(this.panelLeft + 6, 80, this.panelWidth - 12, 16).build());
        for (int i = 0; i < this.layout.browserRows(); i++) {
            final int row = i;
            this.browserRows.add(addRenderableWidget(new SettlementViewUi.EntryButton(this.panelLeft + 6, 100 + i * 28,
                    this.panelWidth - 12, 27, () -> {
                var rows = this.browser.rows(this.snapshots.entries(this.browser.page));
                if (row + this.browser.scroll < rows.size()) inspect(rows.get(row + this.browser.scroll));
            })));
        }
    }

    private void updateBrowserRows() {
        if (this.filterButton == null) return;
        var rows = this.browser.rows(this.snapshots.entries(this.browser.page));
        this.filterButton.setMessage(Component.literal(this.browser.filter + " (" + rows.size() + ")"));
        if (this.search != null) this.search.setHint(Component.literal("Search " + switch (this.browser.page) {
            case RESIDENTS -> "residents...";
            case AREAS -> "areas...";
            case PROJECTS -> "buildings...";
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
            if (position == null) openManagement(issue.target(), issue.targetId() == null ? "" : issue.targetId().toString());
        }
        updateActions();
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
        var markers = new ArrayList<SettlementOverviewMap.Marker>();
        for (var area : this.areas.values()) {
            if (area.kind() == SettlementAreaType.CONSTRUCTION
                    || area.kind() == SettlementAreaType.HOME && !area.id().equals(this.selectedArea)) continue;
            markers.add(new SettlementOverviewMap.Marker(area, area.min().offset((area.max().getX() - area.min().getX()) / 2,
                    0, (area.max().getZ() - area.min().getZ()) / 2), false,
                    area.id().equals(this.selectedArea) ? 0xFF70CBFF : 0xFF63879C));
        }
        for (var project : this.projects.values()) markers.add(new SettlementOverviewMap.Marker(project,
                project.min().offset((project.max().getX() - project.min().getX()) / 2, 0, (project.max().getZ() - project.min().getZ()) / 2),
                false, project.id().equals(this.selectedArea) ? 0xFF70CBFF : 0xFFE7B64E));
        Map<UUID, BlockPos> visible = new java.util.HashMap<>();
        for (var entity : this.minecraft.level.entitiesForRendering()) {
            if (entity instanceof Villager && contains(entity.blockPosition())) visible.put(entity.getUUID(), entity.blockPosition());
        }
        for (var r : this.roster.values()) {
            boolean live = visible.containsKey(r.id());
            markers.add(new SettlementOverviewMap.Marker(r, visible.getOrDefault(r.id(), r.position()), !live,
                    this.selection.contains(r.id()) ? 0xFF40CCFF : live ? 0xFF66E478 : 0xFFAAAAAA));
        }
        for (var entry : this.snapshots.entries(Page.ISSUES)) {
            var issue = (SettlementManagementPayload.Issue) entry;
            BlockPos position = issuePosition(issue);
            if (position != null) markers.add(new SettlementOverviewMap.Marker(issue, position, false, 0xFFFF8840));
        }
        var camera = this.minecraft.gameRenderer.getMainCamera();
        this.overviewMap.render(graphics, this.font, this.window.chunks(), this.plotSnapshot.plots(), List.copyOf(this.areas.values()),
                List.copyOf(this.projects.values()), markers, this.selectedArea, mapCameraFootprint(), this.focusX, this.focusZ,
                camera.getPosition().x, camera.getPosition().z, this.camera.yaw(), mouseX, mouseY);
    }

    private List<Vec3> mapCameraFootprint() {
        double ground = terrainHeight((int) Math.floor(this.focusX), (int) Math.floor(this.focusZ));
        if (!Double.isFinite(ground)) return List.of();
        var corners = new ArrayList<Vec3>();
        for (double[] point : new double[][]{{0, 0}, {this.panelLeft, 0}, {this.panelLeft, this.height}, {0, this.height}}) {
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
        if (this.routeSubmitted && !this.parent.submitting()) {
            this.routeSubmitted = false;
            this.routeUncertain = true;
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
        long now = Util.getMillis();
        if (now >= this.nextViewRequest && (this.moved || now >= this.keepAliveAt)) {
            PacketDistributor.sendToServer(new SettlementViewRequestPayload(this.session, nextRequestId(),
                    this.requestedFocus == null ? this.focusX : this.requestedFocus.x,
                    this.requestedFocus == null ? this.focusZ : this.requestedFocus.z, this.viewHeight, false));
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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        double frameTime = this.lastFrame == 0L ? 0.0D : Math.max(0.0D, (now - this.lastFrame) / 1_000.0D);
        double elapsed = Math.min(frameTime, 0.05D);
        this.lastFrame = now;
        if (!this.minecraft.isWindowActive() || this.search != null && this.search.isFocused()) clearControls();
        rotateCamera(((held(GLFW.GLFW_KEY_E) ? 1 : 0) - (held(GLFW.GLFW_KEY_Q) ? 1 : 0)) * 90.0D * frameTime);
        double forward = (held(GLFW.GLFW_KEY_W) || held(GLFW.GLFW_KEY_UP) ? 1 : 0)
                - (held(GLFW.GLFW_KEY_S) || held(GLFW.GLFW_KEY_DOWN) ? 1 : 0);
        double right = (held(GLFW.GLFW_KEY_D) || held(GLFW.GLFW_KEY_RIGHT) ? 1 : 0)
                - (held(GLFW.GLFW_KEY_A) || held(GLFW.GLFW_KEY_LEFT) ? 1 : 0);
        double speed = this.viewHeight * (shiftDown() ? 2.5D : 1.0D) / Math.max(1.0D, Math.hypot(forward, right));
        Vec3 movement = SettlementViewCamera.movement(this.camera.yaw(), right, forward);
        double response = 1.0D - Math.exp(-MOVE_RESPONSE * elapsed);
        this.panVelocityX += (movement.x * speed - this.panVelocityX) * response;
        this.panVelocityZ += (movement.z * speed - this.panVelocityZ) * response;
        pan(this.panVelocityX * elapsed, this.panVelocityZ * elapsed);
        this.cameraHeight += (this.viewHeight - this.cameraHeight) * (1.0D - Math.exp(-ZOOM_RESPONSE * elapsed));
        if (Math.abs(this.viewHeight - this.cameraHeight) < 0.01D) {
            this.cameraHeight = this.viewHeight;
        }
        positionCamera(frameTime);
        this.hovered = worldContains(mouseX, mouseY) ? pick(mouseX, mouseY) : new Pick(null, null);
        var currentDraft = this.parent.buildingDraft();
        if (currentDraft != null && !currentDraft.placed && this.hovered.position() != null && terrainLoaded()) {
            currentDraft.move(this.hovered.position().above());
        }
        synchronizeGizmo();
        if (this.gizmo != null && worldContains(mouseX, mouseY) && !SettlementViewClient.panelsOpen(this)) this.gizmo.hover(ray(mouseX, mouseY));
        this.routePlacementPreview = this.routeDraft != null && !this.routeDraft.dragging() && this.hovered.position() != null
                && routeHandle(mouseX, mouseY) < 0 ? this.routeDraft.placementPosition(this.hovered.position().above()) : null;
        if (this.routeDraft != null) {
            var handles = this.routeDraft.handles();
            for (int index = 0; index < handles.size(); index++) {
                var point = project(Vec3.atCenterOf(handles.get(index)));
                if (point != null && worldContains(point[0], point[1]))
                    graphics.drawString(this.font, Integer.toString(index + 1), (int) point[0] + 5, (int) point[1] - 5, index == this.routeDraft.selected() ? 0xFFDD55 : 0xFFFFFF);
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
                : Component.literal(this.panelLeft < 360 ? "Drag selects. Shift adds. F focus. Home center."
                        : "Drag selects. Shift adds. Right-click actions. F focus. Home center. Ctrl+1-9 save, 1-9 recall.");
        if (this.guardMode == SettlementGuardOrder.Mode.PATROL) hint = Component.literal(
                (this.guardWorksite == null ? "Patrol" : "Shared patrol") + ": " + this.guardPoints.size() + "/"
                        + SettlementGuardOrder.MAX_POINTS + " points. Enter finishes. Right-click cancels.");
        if (this.routeDraft != null) hint = routeHint();
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
        renderMap(graphics, mouseX, mouseY);
        if (this.boxDragging) {
            int left = (int) Math.min(this.boxX, this.boxEndX);
            int top = (int) Math.min(this.boxY, this.boxEndY);
            int rightEdge = (int) Math.max(this.boxX, this.boxEndX);
            int bottom = (int) Math.max(this.boxY, this.boxEndY);
            graphics.fill(left, top, rightEdge, bottom, 0x3040CCFF);
            graphics.renderOutline(left, top, rightEdge - left, bottom - top, 0xFF40CCFF);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        this.inspector.render(graphics, this.font, mouseX, mouseY);
        if (allLines.size() > lines.size() && mouseX < this.panelLeft && mouseY >= this.height - 42) {
            graphics.renderTooltip(this.font, hint, mouseX, mouseY);
        }
        if (this.popup != null) this.popup.render(graphics, this.font, mouseX, mouseY, partialTick);
    }

    private void renderChrome(GuiGraphics graphics, int mouseX, int mouseY) {
        SettlementViewUi.frame(graphics, 0, 0, this.width, SettlementViewLayout.HEADER_HEIGHT);
        int nameWidth = Math.clamp(this.width / 5, 82, 144);
        SettlementViewUi.item(graphics, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.RED_BANNER), 7, 7, 18);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(this.header.name(), nameWidth - 34), 29, 7, 0xFFFFFF);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(SettlementManagementScreen.enumText("stage", this.header.stage()).getString(),
                nameWidth - 34), 29, 20, 0xB9C1CB);
        var icons = List.of(net.minecraft.world.item.Items.EMERALD, net.minecraft.world.item.Items.VILLAGER_SPAWN_EGG,
                net.minecraft.world.item.Items.RED_BED, net.minecraft.world.item.Items.WHEAT, net.minecraft.world.item.Items.REDSTONE_TORCH);
        var values = List.of(this.header.balance().decimal(2), Integer.toString(this.header.population()), Integer.toString(this.header.housing()),
                String.format(java.util.Locale.ROOT, "%.1f", this.header.foodDays()), Integer.toString(this.header.issues()));
        var labels = List.of("Treasury", "Residents", "Beds", "Food days", "Issues");
        int available = this.width - nameWidth - 96;
        for (int index = 0; index < icons.size(); index++) {
            int left = nameWidth + available * index / icons.size();
            int width = available * (index + 1) / icons.size() - (left - nameWidth);
            graphics.fill(left, 4, left + 1, 30, 0xFF666C73);
            boolean detailed = width >= 58;
            int iconSize = width >= 40 ? 16 : 10;
            if (index == 1) SettlementViewUi.portrait(graphics, left + 5, detailed ? 9 : 5, iconSize);
            else SettlementViewUi.item(graphics, new net.minecraft.world.item.ItemStack(icons.get(index)), left + 5, detailed ? 9 : 5, iconSize);
            int valueLeft = detailed ? left + 25 : left + 5;
            graphics.drawString(this.font, this.font.plainSubstrByWidth(values.get(index), width - (valueLeft - left) - 3),
                    valueLeft, detailed ? 7 : 20, index == 4 && this.header.issues() > 0 ? 0xFF9C83 : 0xFFFFFF);
            if (detailed) graphics.drawString(this.font, labels.get(index), valueLeft, 20, 0xB9C1CB);
            if (mouseX >= left && mouseX < left + width && mouseY < SettlementViewLayout.HEADER_HEIGHT)
                graphics.renderTooltip(this.font, Component.literal(labels.get(index) + ": " + values.get(index)), mouseX, mouseY);
        }
        long time = this.minecraft.level.getDayTime();
        long clock = Math.floorMod(time + 6_000L, 24_000L);
        graphics.drawString(this.font, "Day " + (Math.floorDiv(time, 24_000L) + 1), this.width - 90, 7, 0xFFFFFF);
        graphics.drawString(this.font, String.format(java.util.Locale.ROOT, "%02d:%02d", clock / 1_000L, clock % 1_000L * 60L / 1_000L),
                this.width - 90, 20, 0xB9C1CB);
        if (this.preferences.sidebar) {
            SettlementViewUi.frame(graphics, this.panelLeft + 2, SettlementViewLayout.HEADER_HEIGHT + 2,
                    this.panelWidth - 4, this.layout.contentBottom - SettlementViewLayout.HEADER_HEIGHT);
            if (this.preferences.map && this.layout.dockedMap) {
                SettlementViewUi.frame(graphics, this.panelLeft + 2, this.overviewMap.top - 22,
                        this.panelWidth - 4, this.height - this.overviewMap.top + 20);
            }
        }
        SettlementViewUi.frame(graphics, 3, SettlementViewLayout.HEADER_HEIGHT + 3, this.layout.railSize + 6, (this.layout.railSize + 2) * 6 + 4);
        var commands = contextButtons().stream().filter(button -> button.visible).toList();
        if (!commands.isEmpty()) {
            int left = commands.stream().mapToInt(Button::getX).min().orElse(0) - 3;
            int top = commands.stream().mapToInt(Button::getY).min().orElse(0) - 3;
            int right = commands.stream().mapToInt(Button::getRight).max().orElse(0) + 3;
            int bottom = commands.stream().mapToInt(Button::getBottom).max().orElse(0) + 3;
            SettlementViewUi.frame(graphics, left, top, right - left, bottom - top);
        }
        if (this.interaction.is(Mode.SELECT_PLOT) && this.preferences.sidebar) {
            var totals = this.plotSnapshot.totals();
            var text = text("plot_totals", totals.owned(), totals.capacity(), totals.free(), totals.spendable().decimal(2))
                    .copy().append("\n\n").append(plotDescription());
            int y = 42;
            for (var line : this.font.split(text, this.panelWidth - 16)) {
                if (y + this.font.lineHeight >= this.manage.getY() - 4) break;
                graphics.drawString(this.font, line, this.panelLeft + 8, y, 0xFFFFFF);
                y += this.font.lineHeight + 2;
            }
        } else if (this.preferences.sidebar && !this.inspector.visible()
                && this.browser.rows(this.snapshots.entries(this.browser.page)).isEmpty()) {
            int y = 106;
            for (var line : this.font.split(Component.literal(this.snapshots.loading() ? "Loading settlement..." : "No matching entries"),
                    this.panelWidth - 16)) {
                graphics.drawString(this.font, line, this.panelLeft + 8, y, 0xB9C1CB);
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

    private boolean worldContains(double x, double y) {
        if (x < this.layout.worldLeft || x >= this.panelLeft || y < SettlementViewLayout.HEADER_HEIGHT + 4 || y >= this.height - 32)
            return false;
        if (this.preferences.map && this.overviewMap.coversChrome(x, y)) return false;
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
        if (this.inspected instanceof SettlementManagementPayload.Issue issue) return SettlementManagementScreen.name(issue).copy()
                .append("\n").append(SettlementManagementScreen.detail(issue));
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
        if (!permits(SettlementPermission.BUILD)) {
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
            this.groupActions.visible = this.selection.size() != 0 && !manipulating && !this.interaction.is(Mode.SELECT_PLOT);
            this.groupActions.active = !this.commands.busy();
            this.commandResults.visible = !this.commands.outcomes().isEmpty() && !manipulating && !this.interaction.is(Mode.SELECT_PLOT);
        }
        if (this.newBuilding != null) this.newBuilding.active = permits(SettlementPermission.BUILD) && this.header.projectLimit() > 0 && !this.commands.busy();
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
            this.manage.setTooltip(net.minecraft.client.gui.components.Tooltip.create(selectedName()));
            this.manage.active = !this.commands.busy() && (this.inspected instanceof SettlementManagementPayload.Issue || this.resident != null
                    || this.projects.containsKey(this.selectedArea)
                    || this.selectedArea != null && this.areas.containsKey(this.selectedArea) && permits(SettlementPermission.BUILD));
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
        if (this.routeDraft != null || this.storagePicker != null) {
            for (var button : this.guardButtons) if (button != null) button.visible = false;
            this.returnToPost.visible = false;
            this.groupActions.visible = false;
            this.commandResults.visible = false;
            this.manage.active = false;
            this.drawArea.active = false;
            this.newBuilding.active = false;
        }
        layoutContextActions();
    }

    private boolean selectedGuard() {
        return eligibleCount(SettlementViewAction.MOVE, null) > 0;
    }

    private long eligibleCount(SettlementViewAction action, UUID destination) {
        if (!permits(action.permission())) return 0;
        return this.selection.residents().stream().map(this.roster::get).filter(java.util.Objects::nonNull)
                .filter(r -> action.eligible(r, this.areas.get(action == SettlementViewAction.ASSIGN ? destination : r.area()))).count();
    }

    private void submitBatch(SettlementViewAction action, UUID area, SettlementGuardOrder order) {
        if (this.commands.busy() || this.selection.size() == 0) return;
        var targets = this.selection.residents().stream().map(id -> new SettlementViewBatchPayload.Target(id,
                this.roster.containsKey(id) ? this.roster.get(id).area() : null)).toList();
        var request = new SettlementViewBatchPayload(this.session, UUID.randomUUID(), action, targets, area, order);
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
        sendBatch(new SettlementViewBatchPayload(this.session, UUID.randomUUID(), SettlementViewAction.TEMPORARY_TASK,
                targets, null, null, task, append));
    }

    private void openTemporaryTaskPicker() {
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

    private void openActionPopup(SettlementViewPopup.Kind kind) {
        clearControls();
        this.popup = new SettlementViewPopup(SettlementViewPopup.Target.of(kind, null), 8, 122);
        refreshPopup();
    }

    private void executeAction(SettlementViewAction action) {
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
        for (var action : SettlementViewAction.values()) {
            long eligible = action == SettlementViewAction.ASSIGN
                    ? this.areas.keySet().stream().mapToLong(id -> eligibleCount(action, id)).max().orElse(0)
                    : eligibleCount(action, null);
            actions.add(new SettlementViewPopup.Action(Component.literal(action.label() + " (" + eligible + "/"
                    + this.selection.size() + ")"), !this.commands.busy() && eligible > 0,
                    () -> executeAction(action)));
        }
        return actions;
    }

    private void initRouteButtons() {
        this.routeButtons.clear();
        String[] labels = {"Save route", "Cancel", "Reload", "Inherit", "Insert", "Remove", "Earlier", "Later", "Loop", "Branch", "Clear", "Append"};
        for (int index = 0; index < labels.length; index++) {
            int action = index;
            this.routeButtons.add(addRenderableWidget(Button.builder(Component.literal(labels[index]), ignored -> routeAction(action))
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
            button.visible = this.routeDraft != null;
            button.setX(this.layout.worldLeft + index % columns * (width + 4));
            button.setY(top + index / columns * 22);
            button.setWidth(width);
            button.active = this.routeDraft != null && !this.parent.submitting() && !this.commands.busy();
            if (this.routeDraft == null) continue;
            button.active &= !this.routeReloading && (!this.routeDraft.dragging() || index == 1);
            if (index == 0 || index == 3) button.active &= !this.routeUncertain;
            if (index == 0) button.active &= this.routeDraft.validation().isEmpty();
            if (index == 3) button.setMessage(Component.literal(this.routeResident == null ? "Automatic" : "Inherit"));
            if (index == 4) button.setMessage(Component.literal(this.routeDraft.inserting() ? "Insert: on" : "Insert"));
            if (index >= 4 && index <= 7 || index == 9) button.active &= this.routeDraft.selected() >= 0;
            if (index == 8) button.setMessage(Component.literal(this.routeDraft.loop() ? "Loop: on" : "Loop: off"));
            if (index == 9) button.setMessage(Component.literal(this.routeDraft.branching() ? "Branch: on" : "Branch"));
            button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(switch (index) {
                case 0 -> "Save this route. Changes stay in the draft until saved.";
                case 2 -> "Discard this draft and reload the latest saved route.";
                case 3 -> this.routeResident == null ? "Remove the worksite route and restore automatic behavior." : "Remove only this worker's route override.";
                case 4 -> "Place the next node after the selected node.";
                case 5 -> "Remove the selected node or branch.";
                case 9 -> "Select a route node, then place an out-and-back branch.";
                case 10 -> "Clear the draft. Save to explicitly disable this route.";
                default -> button.getMessage().getString();
            })));
        }
    }

    private void routeAction(int action) {
        if (this.routeDraft == null || this.parent.submitting()) return;
        switch (action) {
            case 0 -> savePersistentRoute(false);
            case 1 -> enterMode(Mode.SELECT);
            case 2 -> {
                this.routeReloading = true;
                refreshManagement();
                this.guardFeedback = Component.literal("Reloading the saved route...");
            }
            case 3 -> savePersistentRoute(true);
            case 4 -> this.routeDraft.insertAfterSelection();
            case 5 -> this.routeDraft.remove();
            case 6 -> this.routeDraft.reorder(-1);
            case 7 -> this.routeDraft.reorder(1);
            case 8 -> this.routeDraft.toggleLoop();
            case 9 -> this.routeDraft.branchFromSelection();
            case 10 -> this.routeDraft.clear();
            case 11 -> { if (this.routeDraft.inserting()) this.routeDraft.insertAfterSelection(); if (this.routeDraft.branching()) this.routeDraft.branchFromSelection(); }
            default -> { }
        }
        updateActions();
    }

    private void beginPersistentRoute() {
        var area = inspectorWorksite();
        var selected = inspectorSelection();
        if (area != null) beginPersistentRoute(area.id(), selected instanceof SettlementManagementPayload.Resident resident ? resident.id() : null);
    }

    private void beginPersistentRoute(UUID areaId, UUID residentId) {
        var area = this.areas.get(areaId);
        if (area == null || !permits(SettlementPermission.STAFF) || !SettlementGuardOrder.supports(area.kind().role())) return;
        if (residentId == null && area.plan() == null) {
            this.minecraft.setScreen(new SettlementWorkerScreen(management(), area, this));
            return;
        }
        var resident = this.roster.get(residentId);
        if (residentId != null && (resident == null || !areaId.equals(resident.area()))) return;
        var expected = residentId == null ? area.plan().route() : resident.guardOrder();
        var initial = expected != null ? expected : area.plan() == null ? null : area.plan().route();
        enterMode(Mode.EDIT_ROUTE);
        this.routeArea = areaId;
        this.routeResident = residentId;
        this.routeExpected = residentId == null ? expected : initial;
        this.routeInherited = residentId != null && expected == null;
        this.routeDraft = new SettlementRouteDraft(area.kind().role(), initial);
        this.routeUncertain = false;
        this.routeReloading = false;
        this.guardFeedback = Component.empty();
        updateActions();
    }

    private void savePersistentRoute(boolean inherit) {
        if (this.routeDraft == null || this.routeDraft.dragging() || this.routeUncertain || this.routeReloading || this.parent.submitting() || this.commands.busy()) return;
        if (!inherit && !this.routeDraft.validation().isEmpty()) {
            this.guardFeedback = Component.literal(this.routeDraft.validation());
            return;
        }
        this.routeSubmitted = true;
        this.parent.submit(new SettlementManagementActionPayload.SaveRoute(this.routeArea, this.routeResident,
                this.routeExpected, inherit ? null : this.routeDraft.route()));
        this.guardFeedback = Component.literal("Saving route...");
        updateActions();
    }

    private Component routeHint() {
        String scope = this.routeResident == null ? "Worksite default" : this.routeInherited ? "Worker / inherited draft" : "Worker override";
        String validation = this.routeDraft.validation();
        String feedback = !validation.isEmpty() ? validation : this.guardFeedback.getString();
        return Component.literal(scope + " / " + this.routeDraft.nodes().size() + "/16 nodes"
                + (this.routeDraft.selected() < 0 ? "" : " / selected " + (this.routeDraft.selected() + 1))
                + "\n" + (feedback.isEmpty() ? "Click to place. Hold a node to drag. Middle drag pans. Esc cancels." : feedback));
    }

    private int routeHandle(double x, double y) {
        int selected = -1;
        double distance = 10;
        var handles = this.routeDraft.handles();
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
        clearControls();
        this.boxX = -1;
        this.boxDragging = false;
        this.dragStart = null;
        this.dragEnd = null;
        if (mode != Mode.EDIT_ROUTE) {
            this.routeDraft = null;
            this.routeSubmitted = false;
            this.routeReloading = false;
            this.routeUncertain = false;
            this.routeDragHit = false;
        }
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
        if (this.gizmo != null) this.gizmo.scale(this.cameraHeight * 0.075);
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

    boolean hasProject(UUID id) { return this.projects.containsKey(id); }

    int viewportWidth() { return this.panelLeft; }

    SettlementOverviewMap overviewMap() { return this.overviewMap; }

    private boolean shiftDown() {
        return hasShiftDown() || held(GLFW.GLFW_KEY_LEFT_SHIFT) || held(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    private boolean held(int key) {
        return this.heldKeys.contains(key);
    }

    void focus(double x, double z) {
        this.panVelocityX = 0.0D;
        this.panVelocityZ = 0.0D;
        pan(x - this.focusX, z - this.focusZ);
        this.requestedFocus = new Vec3(x, 0, z);
        this.moved = true;
    }

    private void pan(double x, double z) {
        var target = this.window.focus(this.focusX + x, this.focusZ + z);
        double nextX = target.x;
        double nextZ = target.z;
        if (nextX != this.focusX || nextZ != this.focusZ) {
            this.requestedFocus = null;
            this.moved = true;
        }
        this.focusX = nextX;
        this.focusZ = nextZ;
    }

    private void zoom(double direction) {
        float maximum = Math.min(SettlementViewWindow.MAX_HEIGHT,
                Math.max(SettlementViewWindow.MIN_HEIGHT, this.minecraft.options.getEffectiveRenderDistance() * 12.0F));
        float next = (float) Math.clamp(this.viewHeight * Math.pow(1.15D, direction), SettlementViewWindow.MIN_HEIGHT, maximum);
        this.moved |= next != this.viewHeight;
        this.viewHeight = next;
    }

    private void positionCamera() {
        positionCamera(0.0D);
    }

    private void positionCamera(double elapsed) {
        if (this.minecraft == null || this.minecraft.level == null) {
            return;
        }
        var pose = this.camera.position(this.window, this.focusX, this.focusZ, this.cameraHeight,
                this.minecraft.options.getEffectiveRenderDistance() * 16.0D - 8.0D, this::terrainHeight, elapsed);
        SettlementViewClient.position(pose.position().x, pose.position().y, pose.position().z, pose.yaw(), pose.pitch());
    }

    private double terrainHeight(int x, int z) {
        var chunk = this.minecraft.level.getChunkSource().getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16), false);
        return chunk == null ? Double.NaN : chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 1.0D;
    }

    void clearControls() {
        if (this.routeDraft != null) this.routeDraft.cancelDrag();
        this.panning = false;
        if (this.gizmo != null) this.gizmo.end();
        this.heldKeys.clear();
        this.panVelocityX = 0.0D;
        this.panVelocityZ = 0.0D;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.storagePicker != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) finishStoragePick(null);
            else if (navigationKey(keyCode)) this.heldKeys.add(keyCode);
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
        if (this.routeDraft != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (this.routeDraft.dragging()) this.routeDraft.cancelDrag();
                else if (!this.parent.submitting()) enterMode(Mode.SELECT);
                updateActions();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) { savePersistentRoute(false); return true; }
            if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!this.parent.submitting()) this.routeDraft.remove();
                updateRouteButtons();
                return true;
            }
            if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
                this.routeDraft.select(keyCode - GLFW.GLFW_KEY_1);
                updateRouteButtons();
                return true;
            }
            if (this.routeDraft.dragging()) return true;
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
        if (navigationKey(keyCode)) {
            if (this.gizmo == null || !this.gizmo.dragging()) this.heldKeys.add(keyCode);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (navigationKey(keyCode)) {
            this.heldKeys.remove(keyCode);
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    private static boolean navigationKey(int key) {
        return key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT || key == GLFW.GLFW_KEY_W || key == GLFW.GLFW_KEY_A || key == GLFW.GLFW_KEY_S || key == GLFW.GLFW_KEY_D
                || key == GLFW.GLFW_KEY_Q || key == GLFW.GLFW_KEY_E
                || key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_RIGHT;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (this.routeDraft != null && this.routeDraft.dragging()) return true;
        if (this.popup != null && this.popup.scroll(x, y, vertical) || this.gizmo != null && this.gizmo.dragging()) return true;
        if (this.preferences.map && this.overviewMap.contains(x, y)) {
            if (vertical != 0) this.overviewMap.zoom(-vertical, this.focusX, this.focusZ);
            return true;
        }
        if (this.preferences.map && this.overviewMap.coversChrome(x, y)) return true;
        if (this.preferences.sidebar && x >= this.panelLeft) {
            if (y < SettlementViewLayout.HEADER_HEIGHT || y >= this.layout.contentBottom) return true;
            if (this.inspector.visible()) { this.inspector.scroll(vertical); return true; }
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
        if (this.storagePicker != null) {
            if (super.mouseClicked(x, y, button)) return true;
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
        if (this.popup != null) {
            if (this.popup.click(x, y, button)) return true;
            this.popup = null;
        }
        if (this.routeDraft == null && this.inspector.click(x, y, button)) return true;
        if (super.mouseClicked(x, y, button)) {
            return true;
        }
        if (this.preferences.map && this.overviewMap.contains(x, y)) {
            if (this.routeDraft != null) {
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
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            if (this.routeDraft != null && this.routeDraft.dragging()) return true;
            this.panning = true;
            return true;
        }
        if (this.routeDraft != null) {
            if (this.parent.submitting()) return true;
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                if (this.routeDraft.dragging()) this.routeDraft.cancelDrag();
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
                this.routeDraft.beginDrag(handle);
                this.routeDragHit = true;
                this.routeDragX = x;
                this.routeDragY = y;
            } else {
                var position = pick(x, y).position();
                this.guardFeedback = Component.literal(position == null ? "No loaded map surface at this position"
                        : this.routeDraft.place(position.above()));
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
            this.boxAdd = shiftDown();
            this.beforeBox = this.selection.residents();
            this.boxDragging = false;
        }
        var matching = matchingAreas(hit, x, y);
        select(hit, matching.isEmpty() ? null : matching.getFirst());
        if (hit.resident() == null && matching.size() > 1 && !this.interaction.is(Mode.DRAW_AREA)) openChooser(matching, x, y);
        if (this.interaction.is(Mode.DRAW_AREA)) {
            this.dragStart = hit.position();
            this.dragEnd = hit.position();
        }
        updateActions();
        return true;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double deltaX, double deltaY) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.routeDraft != null && this.routeDraft.dragging()) {
            var position = worldContains(x, y) ? pick(x, y).position() : null;
            this.routeDragHit = position != null;
            this.routeDragX = x;
            this.routeDragY = y;
            if (position != null) this.routeDraft.dragTo(position.above());
            this.guardFeedback = Component.literal(position == null ? "No map surface. Release restores the original node." : this.routeDraft.validation());
            updateRouteButtons();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && this.panning) {
            double scale = 2.0D * this.viewHeight * Math.tan(Math.toRadians(SettlementViewClient.FOV / 2.0D)) / this.height;
            double yaw = Math.toRadians(this.camera.yaw());
            this.panVelocityX = 0.0D;
            this.panVelocityZ = 0.0D;
            pan((-Math.cos(yaw) * deltaX - Math.sin(yaw) * deltaY) * scale,
                    (-Math.sin(yaw) * deltaX + Math.cos(yaw) * deltaY) * scale);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.gizmo != null && this.gizmo.dragging()) {
            this.gizmo.drag(ray(Math.min(x, this.panelLeft - 1), y)).ifPresent(this::adjustGizmo);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && (this.interaction.is(Mode.DRAW_AREA)
                || this.interaction.is(Mode.TASK_AREA)) && this.dragStart != null) {
            BlockPos position = pick(Math.min(x, this.panelLeft - 1), y).position();
            if (position != null) {
                this.dragEnd = position;
            }
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.boxX >= 0) {
            this.boxEndX = Math.clamp(x, 0, this.panelLeft - 1);
            this.boxEndY = Math.clamp(y, 0, this.height - 1);
            this.boxDragging |= Math.hypot(x - this.boxX, y - this.boxY) >= 4;
            if (this.boxDragging) this.popup = null;
            return true;
        }
        return super.mouseDragged(x, y, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.routeDraft != null && this.routeDraft.dragging()) {
            if (x != this.routeDragX || y != this.routeDragY) mouseDragged(x, y, button, 0, 0);
            boolean valid = this.routeDraft.finishDrag(position -> this.routeDragHit && worldContains(x, y)
                    && !this.minecraft.level.isOutsideBuildHeight(position));
            this.guardFeedback = Component.literal(valid ? "Draft updated. Save applies the route." : "Invalid drop. Original node restored.");
            updateRouteButtons();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.boxX >= 0) {
            if (this.boxDragging) finishBox();
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
                .add(new Vec3(camera.getLeftVector()).scale(-(x * 2.0D - this.panelLeft) * tangent / this.height))
                .add(new Vec3(camera.getUpVector()).scale((1.0D - y / this.height * 2.0D) * tangent));
        return new SettlementSelectionGizmo.Ray(camera.getPosition(), direction);
    }

    private Pick pick(double x, double y) {
        var camera = this.minecraft.gameRenderer.getMainCamera();
        var ray = ray(x, y);
        Vec3 start = ray.origin();
        Vec3 end = start.add(ray.direction().scale(256.0D));
        var block = this.minecraft.level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, camera.getEntity()));
        double distance = block.getType() == HitResult.Type.MISS ? 256.0D * 256.0D : start.distanceToSqr(block.getLocation());
        UUID residentId = null;
        UUID targetId = null;
        BlockPos targetPosition = null;
        for (Entity entity : this.minecraft.level.entitiesForRendering()) {
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
            for (Entity entity : this.minecraft.level.entitiesForRendering()) {
                if (this.selection.contains(entity.getUUID())) {
                    LevelRenderer.renderLineBox(pose, lines, entity.getBoundingBox().inflate(0.08D),
                            0.25F, 0.9F, 1.0F, 1.0F);
                } else if (entity.getUUID().equals(this.hovered.resident()) && !SettlementViewClient.panelsOpen(this)) {
                    LevelRenderer.renderLineBox(pose, lines, entity.getBoundingBox().inflate(0.08D),
                            1.0F, 0.78F, 0.25F, 0.7F);
                }
            }
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
        if (this.routeDraft != null) {
            routes.clear();
            routes.add(this.routeDraft.nodes());
        }
        for (var points : routes) {
            for (int i = 0; i < points.size(); i++) {
                BlockPos point = points.get(i);
                LevelRenderer.renderLineBox(pose, lines, new AABB(point).deflate(0.15D), 1, 1, 1, 1);
                if (i > 0) SettlementSelectionGizmo.line(pose, lines, Vec3.atCenterOf(points.get(i - 1)),
                        Vec3.atCenterOf(point), 1, 1, 1, 1);
            }
        }
        if (this.routeDraft != null) {
            var nodes = this.routeDraft.nodes();
            boolean valid = (!this.routeDraft.dragging() || this.routeDragHit) && this.routeDraft.validation().isEmpty();
            float green = valid ? 0.85F : 0.2F;
            if (this.routeDraft.loop() && nodes.size() > 1) SettlementSelectionGizmo.line(pose, lines,
                    Vec3.atCenterOf(nodes.getLast()), Vec3.atCenterOf(nodes.getFirst()), 1, green, 0.2F, 1);
            for (var branch : this.routeDraft.branches()) SettlementSelectionGizmo.line(pose, lines,
                    Vec3.atCenterOf(branch.anchor()), Vec3.atCenterOf(branch.end()), 1, green, 0.2F, 1);
            if (this.routePlacementPreview != null && !this.routeDraft.dragging()) {
                LevelRenderer.renderLineBox(pose, lines, new AABB(this.routePlacementPreview).deflate(0.1), 1, 0.85F, 0.2F, 0.65F);
                for (var neighbor : this.routeDraft.placementNeighbors()) SettlementSelectionGizmo.line(pose, lines,
                        Vec3.atCenterOf(neighbor), Vec3.atCenterOf(this.routePlacementPreview), 1, 0.85F, 0.2F, 0.65F);
            }
            var handles = this.routeDraft.handles();
            for (int index = 0; index < handles.size(); index++) {
                boolean selected = index == this.routeDraft.selected();
                LevelRenderer.renderLineBox(pose, lines, new AABB(handles.get(index)).inflate(selected ? 0.1 : -0.1),
                        selected ? 1 : 0.4F, green, selected ? 0.2F : 1, 1);
            }
        }
        if (this.preferences.workOverlay || this.preferences.issueOverlay) {
            for (var entity : this.minecraft.level.entitiesForRendering()) {
                var r = this.roster.get(entity.getUUID());
                if (r == null || !contains(entity.blockPosition())) continue;
                boolean issue = this.browser.attention.contains(r.id());
                if (this.preferences.issueOverlay && issue || this.preferences.workOverlay) {
                    boolean paused = r.status().equals("paused") || r.status().equals("area_paused");
                    LevelRenderer.renderLineBox(pose, lines, entity.getBoundingBox().inflate(0.13),
                            issue ? 1 : paused ? 0.8F : 0.3F, issue ? 0.35F : 0.8F, 0.3F, 0.8F);
                }
            }
        }
        if (this.interaction.is(Mode.TASK_AREA) && this.dragStart != null && this.dragEnd != null) {
            var min = com.jvn.villagerretaliation.interaction.HiredWorkArea.minPos(this.dragStart, this.dragEnd);
            var max = com.jvn.villagerretaliation.interaction.HiredWorkArea.maxPos(this.dragStart, this.dragEnd).above(2);
            LevelRenderer.renderLineBox(pose, lines, new SettlementViewBounds(min, max).box(), 0.3F, 0.9F, 0.6F, 0.8F);
        }
        if (this.interaction.is(Mode.DRAW_AREA) && this.dragStart != null && this.dragEnd != null) {
            LevelRenderer.renderLineBox(pose, lines, draftBounds(), 0.62F, 0.72F, 0.78F, 0.65F);
        }
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
        if (this.interaction.is(Mode.SELECT_PLOT)) SettlementPlotRenderer.render(event, this.plotSnapshot, this.selectedPlot, hoveredPlot());
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
        if (hit.resident() != null && shiftDown()) {
            if (!this.selection.toggle(hit.resident())) this.guardFeedback = Component.literal("Selection limit: 128 residents");
        } else {
            this.selection.select(hit.resident() == null ? List.of() : List.of(hit.resident()), false);
        }
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
        savePreferences();
        this.commands.clear();
        this.snapshots.clear();
        this.overviewMap.close();
        this.assignmentReview = null;
        this.selection.select(List.of(), false);
        this.roster.clear();
        enterMode(Mode.SELECT);
    }

    void savePreferences() {
        this.preferences.groups = this.selection.groups();
        this.preferences.height = this.viewHeight;
        this.preferences.yaw = this.camera.yaw();
        this.preferences.save();
    }

    private void focusSelection() {
        var positions = this.selection.residents().stream().map(this.roster::get).filter(java.util.Objects::nonNull)
                .map(SettlementManagementPayload.Resident::position).toList();
        if (!positions.isEmpty()) {
            focus(positions.stream().mapToInt(BlockPos::getX).average().orElse(this.focusX),
                    positions.stream().mapToInt(BlockPos::getZ).average().orElse(this.focusZ));
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
        return new double[] {this.panelLeft / 2.0D - offset.dot(new Vec3(camera.getLeftVector())) * scale,
                this.height / 2.0D - offset.dot(new Vec3(camera.getUpVector())) * scale};
    }

    private void finishBox() {
        List<UUID> visible = new ArrayList<>();
        for (Entity entity : this.minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof Villager) || !this.roster.containsKey(entity.getUUID()) || !contains(entity.blockPosition())) continue;
            var point = project(entity.getBoundingBox().getCenter());
            if (point == null || point[0] < 0 || point[0] >= this.panelLeft || point[1] < 0 || point[1] >= this.height) continue;
            if (point[0] < Math.min(this.boxX, this.boxEndX) || point[0] > Math.max(this.boxX, this.boxEndX)
                    || point[1] < Math.min(this.boxY, this.boxEndY) || point[1] > Math.max(this.boxY, this.boxEndY)) continue;
            var camera = this.minecraft.gameRenderer.getMainCamera();
            var obstruction = this.minecraft.level.clip(new ClipContext(camera.getPosition(), entity.getBoundingBox().getCenter(),
                    ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, camera.getEntity()));
            if (obstruction.getType() == HitResult.Type.MISS) visible.add(entity.getUUID());
        }
        if (this.boxAdd) this.selection.select(this.beforeBox, false);
        if (!this.selection.select(visible, this.boxAdd)) this.guardFeedback = Component.literal("Selection limit: 128 residents");
        this.selectedArea = null;
        this.selectedBlock = null;
        syncSelection();
    }

    private List<UUID> matchingAreas(Pick hit, double x, double y) {
        if (hit.position() != null) {
            var touching = selectableAreas().stream()
                    .filter(area -> new SettlementViewBounds(area.min(), area.max()).box().contains(Vec3.atCenterOf(hit.position())))
                    .map(SettlementManagementPayload.Area::id).toList();
            if (!touching.isEmpty()) {
                return touching;
            }
        }
        var ray = ray(x, y);
        Vec3 end = hit.position() == null ? ray.end() : ray.origin().add(ray.direction()
                .scale(ray.origin().distanceTo(Vec3.atCenterOf(hit.position())) + 1.0D));
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
        boolean handle = this.editingArea != null && this.gizmo != null && this.gizmo.hover(ray);
        if (handle) {
            this.popup = new SettlementViewPopup(SettlementViewPopup.Target.of(SettlementViewPopup.Kind.DRAFT, this.editingArea.id()), x, y);
        } else if (hit.resident() != null) {
            if (!this.selection.contains(hit.resident())) select(hit, null);
            this.popup = new SettlementViewPopup(SettlementViewPopup.Target.of(SettlementViewPopup.Kind.RESIDENT, hit.resident()), x, y);
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
                    actions.add(new SettlementViewPopup.Action(Component.literal(area.name() + " (" + eligible + "/"
                            + this.selection.size() + ")"), !this.commands.busy(),
                            () -> submitBatch(SettlementViewAction.ASSIGN, area.id(), null)));
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
                    actions.add(contextAction("manage_selection", ready, () -> openManagement(Page.PROJECTS, project.id().toString())));
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
        this.popup.update(this.font, this.width, this.height, details, actions);
    }

    private SettlementViewPopup.Action contextAction(String key, boolean enabled, Runnable run) {
        return new SettlementViewPopup.Action(text(key), enabled, () -> {
            this.popup = null;
            run.run();
        });
    }

    SettlementSelectionInspector inspector() { return this.inspector; }
    SettlementRouteDraft routeDraft() { return this.routeDraft; }

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
                : selected instanceof SettlementManagementPayload.Project value ? this.areas.get(value.area()) : null;
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
                this.preferences.sidebar && !this.interaction.is(Mode.SELECT_PLOT), this.commands.busy() || this.parent.submitting() || this.routeDraft != null || this.storagePicker != null);
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
            case OVERVIEW -> focusSelection();
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
                if (selected instanceof SettlementManagementPayload.Resident resident && resident.settings() != null) {
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
        if (this.inspected instanceof SettlementManagementPayload.Issue issue) {
            openManagement(issue.target(), issue.targetId() == null ? "" : issue.targetId().toString());
            return;
        }
        if (this.resident != null) {
            openManagement(Page.RESIDENTS, this.resident.id().toString());
        } else if (this.projects.containsKey(this.selectedArea)) {
            openManagement(Page.PROJECTS, this.selectedArea.toString());
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
        this.lastFrame = 0L;
        if (!SettlementViewClient.panelsOpen(this)) {
            SettlementViewClient.end(this);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(null);
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
