package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.client.item.ConstructionBlueprintPreviewRenderer;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.network.SettlementManagementActionResultPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page;
import com.jvn.villagerretaliation.network.SettlementViewRequestPayload;
import com.jvn.villagerretaliation.network.SettlementViewStatePayload;
import com.jvn.villagerretaliation.settlement.SettlementAreaType;
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

public final class SettlementViewScreen extends Screen {
    static final float PITCH = SettlementViewCamera.PITCH;
    private static final double MOVE_RESPONSE = 12.0D;
    private static final double ZOOM_RESPONSE = 14.0D;
    private final SettlementManagementScreen parent;
    private final UUID session;
    private final Map<UUID, SettlementManagementPayload.Area> areas = new LinkedHashMap<>();
    private final Map<UUID, SettlementManagementPayload.Area> incomingAreas = new LinkedHashMap<>();
    private final Map<UUID, SettlementManagementPayload.Project> projects = new LinkedHashMap<>();
    private final Map<UUID, SettlementManagementPayload.Project> incomingProjects = new LinkedHashMap<>();
    private final Set<Integer> heldKeys = new HashSet<>();
    private final SettlementViewCamera camera = new SettlementViewCamera();
    private SettlementViewPopup popup;
    private boolean panning;
    private SettlementManagementPayload.Header header;
    private SettlementViewWindow window;
    private double focusX;
    private double focusZ;
    private float viewHeight;
    private double cameraHeight;
    private double panVelocityX;
    private double panVelocityZ;
    private Pick hovered = new Pick(null, null);
    private int requestId;
    private int acceptedRequestId;
    private long nextViewRequest;
    private long keepAliveAt;
    private long lastFrame;
    private boolean moved;
    private int panelLeft;
    private int panelWidth;
    private boolean plotMode;
    private boolean plotPending;
    private net.minecraft.world.level.ChunkPos selectedPlot;
    private com.jvn.villagerretaliation.network.SettlementPlotSnapshot plotSnapshot;
    private Button claimFree;
    private Button buyPlot;
    private Component plotFeedback = Component.empty();
    private Button manage;
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
    private final List<BlockPos> guardPoints = new ArrayList<>();
    private boolean guardPending;
    private long guardSentAt;
    private Component guardFeedback = Component.empty();
    private UUID selectedResident;
    private SettlementManagementPayload.Resident resident;
    private boolean residentChecked;
    private UUID selectedArea;
    private BlockPos selectedBlock;
    private BlockPos dragStart;
    private BlockPos dragEnd;
    private boolean drawing;
    private int managementRequestId;
    private int waitingRequestId = -1;
    private Page waitingPage;
    private long waitingSince;
    private long detailsAt;
    private long residentRefreshAt;
    private long areaRefreshAt;
    private int areaPage;
    private long projectRefreshAt;
    private int projectPage;

    SettlementViewScreen(SettlementManagementScreen parent, SettlementViewStatePayload payload) {
        super(text("title"));
        this.parent = parent;
        this.header = parent.snapshot().header();
        this.session = payload.session();
        this.plotSnapshot = payload.plots();
        this.window = payload.window();
        this.focusX = this.window.x();
        this.focusZ = this.window.z();
        this.viewHeight = this.window.height();
        this.cameraHeight = this.viewHeight;
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
        var updated = management.snapshot().header();
        if (updated != null && updated.id().equals(this.header.id())) {
            this.header = updated;
        }
        clearControls();
        this.lastFrame = 0L;
        this.waitingRequestId = -1;
        this.incomingAreas.clear();
        this.areaPage = 0;
        this.areaRefreshAt = 0L;
        this.incomingProjects.clear();
        this.projectPage = 0;
        this.projectRefreshAt = 0L;
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
        this.window = payload.window();
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
        if (payload.page() == Page.AREAS && this.waitingPage == Page.AREAS) {
            if (payload.index() == 0) {
                this.incomingAreas.clear();
            }
            for (var entry : payload.entries()) {
                if (entry instanceof SettlementManagementPayload.Area area) {
                    this.incomingAreas.put(area.id(), area);
                }
            }
            if ((payload.index() + 1) * SettlementManagementRequestPayload.PAGE_SIZE < payload.total()) {
                this.areaPage = payload.index() + 1;
                this.areaRefreshAt = 0L;
            } else {
                this.areas.clear();
                this.areas.putAll(this.incomingAreas);
                this.areaPage = 0;
                this.areaRefreshAt = Util.getMillis() + 3_000L;
            }
        } else if (payload.page() == Page.PROJECTS && this.waitingPage == Page.PROJECTS) {
            if (payload.index() == 0) this.incomingProjects.clear();
            for (var entry : payload.entries()) {
                if (entry instanceof SettlementManagementPayload.Project project
                        && (project.status() != SettlementProject.Status.CANCELLED
                        || project.blocker().equals("pending_world") || project.blocker().equals("world_conflict"))) {
                    this.incomingProjects.put(project.id(), project);
                }
            }
            if ((payload.index() + 1) * SettlementManagementRequestPayload.PAGE_SIZE < payload.total()) {
                this.projectPage = payload.index() + 1;
                this.projectRefreshAt = 0L;
            } else {
                this.projects.clear();
                this.projects.putAll(this.incomingProjects);
                this.projectPage = 0;
                this.projectRefreshAt = Util.getMillis() + 3_000L;
            }
        } else if (payload.page() == Page.RESIDENTS && this.waitingPage == Page.RESIDENTS) {
            this.residentChecked = payload.query().equals(String.valueOf(this.selectedResident));
            this.resident = payload.entries().stream().filter(entry -> entry.id().equals(this.selectedResident))
                    .filter(SettlementManagementPayload.Resident.class::isInstance)
                    .map(SettlementManagementPayload.Resident.class::cast).findFirst().orElse(null);
        }
        updateActions();
    }

    void acceptResult(SettlementManagementActionResultPayload payload) {
        if (this.plotPending && this.header.id().equals(payload.town())) {
            this.plotPending = false;
            this.plotFeedback = text(payload.applied() ? "plot_claimed" : "plot_" + payload.failure());
            this.moved = true;
            updatePlotActions();
        }
        if (this.guardPending && this.header.id().equals(payload.town())) {
            this.guardPending = false;
            this.guardFeedback = text(payload.applied() ? "guard_applied" : "guard_denied");
            this.residentRefreshAt = 0L;
            updateActions();
        }
    }

    @Override
    protected void init() {
        clearControls();
        this.popup = null;
        this.lastFrame = 0L;
        this.panelWidth = Math.clamp(this.width / 3, 128, 200);
        this.panelLeft = this.width - this.panelWidth;
        addRenderableWidget(Button.builder(text("overview"), ignored -> openManagement(Page.SUMMARY, ""))
                .bounds(this.panelLeft + 8, 62, this.panelWidth - 16, 20).build());
        addRenderableWidget(Button.builder(text("residents"), ignored -> openManagement(Page.RESIDENTS, ""))
                .bounds(this.panelLeft + 8, 84, this.panelWidth - 16, 20).build());
        addRenderableWidget(Button.builder(text("areas"), ignored -> openManagement(Page.AREAS, ""))
                .bounds(this.panelLeft + 8, 106, this.panelWidth - 16, 20).build());
        addRenderableWidget(Button.builder(text("projects"), ignored -> openManagement(Page.PROJECTS, ""))
                .bounds(this.panelLeft + 8, 128, this.panelWidth - 16, 20).build());
        this.manage = addRenderableWidget(Button.builder(text("manage_selection"), ignored -> manageSelection())
                .bounds(this.panelLeft + 8, this.height - 70, this.panelWidth - 16, 20).build());
        this.drawArea = addRenderableWidget(Button.builder(text("draw_area"), ignored -> {
            this.plotMode = false;
            updatePlotActions();
            this.guardMode = null;
            this.guardPoints.clear();
            this.parent.clearBuildingDraft();
            this.editingArea = null;
            this.gizmo = null;
            this.drawing = !this.drawing;
            this.dragStart = null;
            this.dragEnd = null;
            updateActions();
        }).bounds(this.panelLeft + 8, this.height - 48, this.panelWidth - 16, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose())
                .bounds(this.panelLeft + 8, this.height - 26, this.panelWidth - 16, 20).build());
        addRenderableWidget(Button.builder(text("center"), ignored -> {
            focus(this.window.center().getX() + 0.5D, this.window.center().getZ() + 0.5D);
        }).bounds(8, 8, 76, 20).build());
        addRenderableWidget(Button.builder(Component.literal("-"), ignored -> zoom(1.0D)).bounds(88, 8, 22, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), ignored -> zoom(-1.0D)).bounds(114, 8, 22, 20).build());
        addRenderableWidget(Button.builder(text("plots"), ignored -> togglePlots()).bounds(140, 8, Math.min(64, this.panelLeft - 148), 20).build());
        this.claimFree = addRenderableWidget(Button.builder(text("plot_free"), ignored -> claimPlot(true)).bounds(8, 62, Math.min(104, this.panelLeft - 16), 20).build());
        this.buyPlot = addRenderableWidget(Button.builder(text("plot_buy", 0), ignored -> claimPlot(false)).bounds(this.panelLeft >= 288 ? 116 : 8, this.panelLeft >= 288 ? 62 : 86,
                        Math.min(156, this.panelLeft - 16), 20).build());
        updatePlotActions();
        for (var mode : SettlementGuardOrder.Mode.values()) {
            this.guardButtons[mode.ordinal()] = addRenderableWidget(Button.builder(text("guard_" + mode.name().toLowerCase(java.util.Locale.ROOT)),
                    ignored -> beginGuardOrder(mode)).bounds(8 + mode.ordinal() * 76, 52, 72, 20).build());
        }
        this.returnToPost = addRenderableWidget(Button.builder(text("guard_post"), ignored -> submitGuardOrder(null))
                .bounds(8, 76, 148, 20).build());
        this.newBuilding = addRenderableWidget(Button.builder(SettlementManagementScreen.text("new_building"), ignored -> {
            this.drawing = false;
            this.editingArea = null;
            this.guardMode = null;
            this.gizmo = null;
            this.plotMode = false;
            updatePlotActions();
            management().newBuilding();
        }).bounds(this.panelLeft + 8, 150, this.panelWidth - 16, 20).build());
        this.transform = addRenderableWidget(Button.builder(text("gizmo_move"), ignored -> {
            if (this.gizmo != null) this.gizmo.mode(SettlementSelectionGizmo.Mode.TRANSFORM);
        }).bounds(8, 52, 92, 20).build());
        this.resize = addRenderableWidget(Button.builder(text("gizmo_resize"), ignored -> {
            if (this.gizmo != null) this.gizmo.mode(SettlementSelectionGizmo.Mode.RESIZE);
        }).bounds(104, 52, 68, 20).build());
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
        }).bounds(176, 52, 100, 20).build());
        this.changePrefab = addRenderableWidget(Button.builder(SettlementManagementScreen.text("change_prefab"), ignored -> management().newBuilding())
                .bounds(8, 76, 144, 20).build());
        this.cancelGizmo = addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), ignored -> cancelGizmo())
                .bounds(156, 76, 76, 20).build());
        updateActions();
        positionCamera();
    }

    @Override
    public void tick() {
        long now = Util.getMillis();
        refreshPopup();
        var draft = this.parent.buildingDraft();
        if (draft != null) draft.tick(this.header.id());
        synchronizeGizmo();
        updateActions();
        if (this.guardPending && now - this.guardSentAt > 3_000L) {
            this.guardPending = false;
            this.guardFeedback = text("guard_denied");
            updateActions();
        }
        keepAlive();
        if (this.waitingRequestId >= 0 && now - this.waitingSince > 3_000L) {
            this.waitingRequestId = -1;
        }
        if (this.waitingRequestId < 0 && now >= this.detailsAt) {
            if (this.selectedResident != null && now >= this.residentRefreshAt) {
                requestDetails(Page.RESIDENTS, 0, this.selectedResident.toString(), now);
                this.residentRefreshAt = now + 2_000L;
            } else if (now >= this.areaRefreshAt) {
                requestDetails(Page.AREAS, this.areaPage, "", now);
            } else if (now >= this.projectRefreshAt) {
                requestDetails(Page.PROJECTS, this.projectPage, "", now);
            }
        }
    }

    void keepAlive() {
        long now = Util.getMillis();
        if (now >= this.nextViewRequest && (this.moved || now >= this.keepAliveAt)) {
            PacketDistributor.sendToServer(new SettlementViewRequestPayload(this.session, nextRequestId(),
                    this.focusX, this.focusZ, this.viewHeight, false));
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
                .append(this.popup == null ? Component.empty() : this.popup.narration());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        double frameTime = this.lastFrame == 0L ? 0.0D : Math.max(0.0D, (now - this.lastFrame) / 1_000.0D);
        double elapsed = Math.min(frameTime, 0.05D);
        this.lastFrame = now;
        if (!this.minecraft.isWindowActive()) clearControls();
        rotateCamera(((held(GLFW.GLFW_KEY_E) ? 1 : 0) - (held(GLFW.GLFW_KEY_Q) ? 1 : 0)) * 90.0D * frameTime);
        double forward = (held(GLFW.GLFW_KEY_W) || held(GLFW.GLFW_KEY_UP) ? 1 : 0)
                - (held(GLFW.GLFW_KEY_S) || held(GLFW.GLFW_KEY_DOWN) ? 1 : 0);
        double right = (held(GLFW.GLFW_KEY_D) || held(GLFW.GLFW_KEY_RIGHT) ? 1 : 0)
                - (held(GLFW.GLFW_KEY_A) || held(GLFW.GLFW_KEY_LEFT) ? 1 : 0);
        double speed = this.viewHeight / Math.max(1.0D, Math.hypot(forward, right));
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
        this.hovered = mouseX >= 0 && mouseX < this.panelLeft && mouseY >= 0 && mouseY < this.height
                ? pick(mouseX, mouseY) : new Pick(null, null);
        var currentDraft = this.parent.buildingDraft();
        if (currentDraft != null && !currentDraft.placed && this.hovered.position() != null && terrainLoaded()) {
            currentDraft.move(this.hovered.position().above());
        }
        synchronizeGizmo();
        if (this.gizmo != null && mouseX < this.panelLeft && !SettlementViewClient.panelsOpen(this)) this.gizmo.hover(ray(mouseX, mouseY));
        graphics.fill(this.panelLeft, 0, this.width, this.height, 0xE0101010);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(this.header.name(), this.panelWidth - 16),
                this.panelLeft + 8, 10, 0xFFFFFF);
        graphics.drawString(this.font, text("population", this.header.population(), this.header.housing()),
                this.panelLeft + 8, 28, 0xD0D0D0);
        graphics.drawString(this.font, text("food", String.format(java.util.Locale.ROOT, "%.1f", this.header.foodDays())),
                this.panelLeft + 8, 42, 0xD0D0D0);
        if (this.plotMode) {
            var totals = this.plotSnapshot.totals();
            int plotY = 34;
            for (var line : this.font.split(text("plot_totals", totals.owned(), totals.capacity(), totals.free(), totals.spendable().decimal(2)), this.panelLeft - 16)) {
                graphics.drawString(this.font, line, 8, plotY, 0xFFFFFF);
                plotY += this.font.lineHeight + 1;
            }
        }
        Component selection = this.plotMode ? plotDescription() : selectedName();
        int y = this.plotMode ? this.buyPlot.getBottom() + 8 : 184;
        for (var line : this.font.split(selection, this.plotMode ? this.panelLeft - 16 : this.panelWidth - 16)) {
            if (y + this.font.lineHeight > this.height - (this.plotMode ? 48 : 78)) {
                break;
            }
            graphics.drawString(this.font, line, this.plotMode ? 8 : this.panelLeft + 8, y, 0xFFFFFF);
            y += this.font.lineHeight + 2;
        }
        var draft = this.parent.buildingDraft();
        Component hint = draft != null ? text(draft.placed ? "building_adjust_hint" : "building_place_hint")
                : this.editingArea != null ? text("gizmo_area_hint")
                : this.guardMode != null ? text(this.guardMode == SettlementGuardOrder.Mode.PATROL
                ? "guard_patrol_hint" : "guard_destination_hint", this.guardPoints.size(), SettlementGuardOrder.MAX_POINTS)
                : this.plotMode ? text("plot_hint")
                : !this.guardFeedback.getString().isEmpty() ? this.guardFeedback : text(this.drawing ? "draw_hint" : "controls");
        var lines = this.font.split(hint, Math.max(80, this.panelLeft - 16));
        int hintTop = this.height - 8 - lines.size() * (this.font.lineHeight + 1);
        graphics.fill(4, hintTop - 4, this.panelLeft - 4, this.height - 4, 0xD0101010);
        for (var line : lines) {
            graphics.drawString(this.font, line, 8, hintTop, 0xFFFFFF);
            hintTop += this.font.lineHeight + 1;
        }
        if (!terrainLoaded()) {
            graphics.drawString(this.font, text("loading"), 8, 34, 0xFFFFFF);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (this.popup != null) this.popup.render(graphics, this.font, mouseX, mouseY, partialTick);
    }

    private Component selectedName() {
        if (this.resident != null) {
            Component name = text("resident", this.resident.name(), Math.round(this.resident.health()), this.resident.food());
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
        if (!permits(SettlementPermission.BUILD)) {
            cancelGizmo();
            this.drawing = false;
            this.dragStart = null;
            this.dragEnd = null;
        }
        if (!selectedGuard()) {
            this.guardMode = null;
            this.guardPoints.clear();
        }
        var draft = this.parent.buildingDraft();
        boolean manipulating = this.gizmo != null;
        if (this.newBuilding != null) this.newBuilding.active = permits(SettlementPermission.BUILD) && this.header.projectLimit() > 0 && !this.guardPending;
        if (this.transform != null) {
            this.transform.visible = manipulating;
            this.transform.setMessage(text(draft == null ? "gizmo_move" : "gizmo_transform"));
            this.transform.active = manipulating && this.gizmo.mode() != SettlementSelectionGizmo.Mode.TRANSFORM;
            this.resize.visible = manipulating && draft == null;
            this.resize.active = manipulating && this.gizmo.mode() != SettlementSelectionGizmo.Mode.RESIZE;
            this.applyGizmo.visible = manipulating;
            this.applyGizmo.setX(draft == null ? 176 : 104);
            this.applyGizmo.setMessage(text(draft == null ? "gizmo_area_settings" : "building_choose_builder"));
            this.applyGizmo.active = draft == null || draft.placed && !draft.submitting;
            this.cancelGizmo.visible = manipulating;
            this.changePrefab.visible = manipulating && draft != null;
        }
        if (this.manage != null) {
            this.manage.active = !this.guardPending && (this.resident != null
                    || this.projects.containsKey(this.selectedArea)
                    || this.selectedArea != null && this.areas.containsKey(this.selectedArea) && permits(SettlementPermission.BUILD));
            this.drawArea.active = permits(SettlementPermission.BUILD) && !this.guardPending;
            this.drawArea.setMessage(text(this.drawing ? "cancel_area" : "draw_area"));
        }
        for (var button : this.guardButtons) {
            if (button != null) {
                button.visible = selectedGuard() && !manipulating && !this.plotMode;
                button.active = !this.guardPending;
            }
        }
        if (this.returnToPost != null) {
            this.returnToPost.visible = selectedGuard() && !manipulating && !this.plotMode;
            this.returnToPost.active = !this.guardPending;
            this.guardButtons[SettlementGuardOrder.Mode.PATROL.ordinal()].setMessage(text(
                    this.guardMode == SettlementGuardOrder.Mode.PATROL ? "guard_finish_patrol" : "guard_patrol"));
        }
    }

    private boolean selectedGuard() {
        var area = this.resident == null ? null : this.areas.get(this.resident.area());
        return area != null && area.kind() == SettlementAreaType.GUARD_POST && permits(SettlementPermission.STAFF);
    }

    private void beginGuardOrder(SettlementGuardOrder.Mode mode) {
        if (this.guardMode == SettlementGuardOrder.Mode.PATROL && mode == this.guardMode) {
            if (this.guardPoints.size() >= 2) {
                submitGuardOrder(new SettlementGuardOrder(mode, this.guardPoints));
            }
            return;
        }
        cancelGizmo();
        this.guardMode = mode;
        this.guardPoints.clear();
        this.guardFeedback = Component.empty();
        this.drawing = false;
        this.dragStart = null;
        this.dragEnd = null;
        updateActions();
    }

    private void submitGuardOrder(SettlementGuardOrder order) {
        if (!selectedGuard() || this.guardPending) {
            return;
        }
        PacketDistributor.sendToServer(new SettlementManagementActionPayload(this.header.id(),
                new SettlementManagementActionPayload.Guard(this.resident.id(), order)));
        this.guardPending = true;
        this.guardSentAt = Util.getMillis();
        this.guardMode = null;
        this.guardPoints.clear();
        this.guardFeedback = text("guard_sending");
        updateActions();
    }

    private void cancelGizmo() {
        this.parent.clearBuildingDraft();
        this.editingArea = null;
        this.areaBounds = null;
        this.gizmo = null;
        this.popup = null;
    }

    private void synchronizeGizmo() {
        var draft = this.parent.buildingDraft();
        if (draft != null) {
            if (this.gizmo == null || !this.buildingGizmo) {
                this.gizmo = new SettlementSelectionGizmo(draft.bounds(), true, false, true, this::allowedGizmoBounds);
                this.buildingGizmo = true;
                this.editingArea = null;
                this.drawing = false;
                this.guardMode = null;
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
        if (this.gizmo != null) this.gizmo.scale(this.cameraHeight * 0.055);
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

    private boolean held(int key) {
        return this.heldKeys.contains(key);
    }

    void focus(double x, double z) {
        this.panVelocityX = 0.0D;
        this.panVelocityZ = 0.0D;
        pan(x - this.focusX, z - this.focusZ);
    }

    private void pan(double x, double z) {
        double nextX = Math.clamp(this.focusX + x, this.window.center().getX() - this.window.radius(),
                this.window.center().getX() + this.window.radius());
        double nextZ = Math.clamp(this.focusZ + z, this.window.center().getZ() - this.window.radius(),
                this.window.center().getZ() + this.window.radius());
        this.moved |= nextX != this.focusX || nextZ != this.focusZ;
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
        this.panning = false;
        if (this.gizmo != null) this.gizmo.end();
        this.heldKeys.clear();
        this.panVelocityX = 0.0D;
        this.panVelocityZ = 0.0D;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && this.popup != null) {
            this.popup = null;
            return true;
        }
        if (this.popup != null && this.popup.key(keyCode)) return true;
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && this.editingArea != null) {
            confirmDraft();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && (this.gizmo != null || this.drawing)) {
            cancelGizmo();
            this.drawing = false;
            this.dragStart = null;
            this.dragEnd = null;
            updateActions();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && this.guardMode != null) {
            this.guardMode = null;
            this.guardPoints.clear();
            updateActions();
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
        return key == GLFW.GLFW_KEY_W || key == GLFW.GLFW_KEY_A || key == GLFW.GLFW_KEY_S || key == GLFW.GLFW_KEY_D
                || key == GLFW.GLFW_KEY_Q || key == GLFW.GLFW_KEY_E
                || key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_RIGHT;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (this.popup != null && this.popup.scroll(x, y, vertical) || this.gizmo != null && this.gizmo.dragging()) return true;
        if (x < this.panelLeft) {
            zoom(-vertical);
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (this.popup != null) {
            if (this.popup.click(x, y, button)) return true;
            this.popup = null;
        }
        if (super.mouseClicked(x, y, button)) {
            return true;
        }
        if (x >= this.panelLeft) {
            return false;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            this.panning = true;
            return true;
        }
        if (this.plotMode && (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
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
            this.guardPoints.clear();
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
        var matching = matchingAreas(hit, x, y);
        select(hit, matching.isEmpty() ? null : matching.getFirst());
        if (hit.resident() == null && matching.size() > 1 && !this.drawing) openChooser(matching, x, y);
        if (this.drawing) {
            this.dragStart = hit.position();
            this.dragEnd = hit.position();
        }
        updateActions();
        return true;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double deltaX, double deltaY) {
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
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.drawing && this.dragStart != null) {
            BlockPos position = pick(Math.min(x, this.panelLeft - 1), y).position();
            if (position != null) {
                this.dragEnd = position;
            }
            return true;
        }
        return super.mouseDragged(x, y, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) this.panning = false;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.gizmo != null && this.gizmo.dragging()) {
            this.gizmo.end();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.drawing && this.dragStart != null && this.dragEnd != null) {
            AABB bounds = draftBounds();
            var area = new SettlementManagementPayload.Area(UUID.randomUUID(), "", SettlementAreaType.HOME,
                    BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                    BlockPos.containing(bounds.maxX - 1.0D, bounds.maxY - 1.0D, bounds.maxZ - 1.0D), 5, true, 0, 0, 0);
            this.editingArea = area;
            this.areaBounds = bounds;
            this.drawing = false;
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
                .add(new Vec3(camera.getLeftVector()).scale(-(x / this.width * 2.0D - 1.0D) * tangent * this.width / this.height))
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
        for (Entity entity : this.minecraft.level.entitiesForRendering()) {
            if (entity instanceof Villager && this.window.contains(entity.blockPosition())) {
                var hit = entity.getBoundingBox().inflate(0.2D).clip(start, end);
                if (hit.isPresent() && hit.get().distanceToSqr(start) < distance) {
                    distance = hit.get().distanceToSqr(start);
                    residentId = entity.getUUID();
                }
            }
        }
        BlockPos position = block.getType() != HitResult.Type.MISS && this.window.contains(block.getBlockPos()) ? block.getBlockPos() : null;
        return new Pick(position, residentId);
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
            if (this.window.chunks().stream().anyMatch(chunk -> chunk.getMaxBlockX() >= area.min().getX()
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
        if (this.selectedResident != null || this.hovered.resident() != null) {
            for (Entity entity : this.minecraft.level.entitiesForRendering()) {
                if (entity.getUUID().equals(this.selectedResident)) {
                    LevelRenderer.renderLineBox(pose, lines, entity.getBoundingBox().inflate(0.08D),
                            0.25F, 0.9F, 1.0F, 1.0F);
                } else if (entity.getUUID().equals(this.hovered.resident()) && !SettlementViewClient.panelsOpen(this)) {
                    LevelRenderer.renderLineBox(pose, lines, entity.getBoundingBox().inflate(0.08D),
                            1.0F, 0.78F, 0.25F, 0.7F);
                }
            }
        }
        List<BlockPos> points = this.guardMode != null ? this.guardPoints
                : this.resident != null && this.resident.guardOrder() != null ? this.resident.guardOrder().points() : List.of();
        for (BlockPos point : points) {
            LevelRenderer.renderLineBox(pose, lines, new AABB(point).deflate(0.15D), 1.0F, 1.0F, 1.0F, 1.0F);
        }
        if (this.drawing && this.dragStart != null && this.dragEnd != null) {
            LevelRenderer.renderLineBox(pose, lines, draftBounds(), 0.62F, 0.72F, 0.78F, 0.65F);
        }
        for (var project : this.projects.values()) {
            if (this.window.chunks().stream().anyMatch(chunk -> chunk.getMaxBlockX() >= project.min().getX()
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
        this.plotMode = !this.plotMode;
        this.drawing = false;
        this.guardMode = null;
        this.guardPoints.clear();
        this.gizmo = null;
        this.editingArea = null;
        this.parent.clearBuildingDraft();
        this.popup = null;
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
        this.claimFree.visible = this.plotMode;
        this.buyPlot.visible = this.plotMode;
        this.claimFree.active = offer != null && offer.freeFailure().isEmpty() && !this.plotPending;
        this.buyPlot.active = offer != null && offer.paidFailure().isEmpty() && !this.plotPending;
        this.buyPlot.setMessage(text("plot_buy", offer == null ? 0 : offer.price()));
    }

    void claimPlot(boolean free) {
        var offer = plotOffer();
        if (offer == null || this.plotPending || !(free ? offer.freeFailure() : offer.paidFailure()).isEmpty()) return;
        this.plotPending = true;
        PacketDistributor.sendToServer(new SettlementManagementActionPayload(this.header.id(),
                new SettlementManagementActionPayload.ClaimPlot(offer.chunk().x, offer.chunk().z, free,
                        this.plotSnapshot.totals().revision(), offer.price())));
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
        return this.plotMode && this.hovered.position() != null
                ? new net.minecraft.world.level.ChunkPos(this.hovered.position()) : null;
    }

    void renderPlotTint(RenderLevelStageEvent event) {
        if (this.plotMode) SettlementPlotRenderer.render(event, this.plotSnapshot, this.selectedPlot, hoveredPlot());
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
        if (area == null || !permits(SettlementPermission.BUILD) || this.guardPending) return;
        cancelGizmo();
        clearControls();
        this.guardMode = null;
        this.guardPoints.clear();
        this.drawing = false;
        this.editingArea = area;
        this.areaBounds = new SettlementViewBounds(area.min(), area.max()).box();
        synchronizeGizmo();
        updateActions();
    }

    private void confirmDraft() {
        if (this.editingArea != null && this.gizmo != null && !this.gizmo.dragging()
                && allowedGizmoBounds(this.areaBounds) && permits(SettlementPermission.BUILD) && !this.guardPending) {
            this.applyGizmo.onPress();
        }
    }

    void areaSaved(UUID id) {
        cancelGizmo();
        this.selectedArea = id;
        this.selectedResident = null;
        this.resident = null;
        this.areas.remove(id);
        this.waitingRequestId = -1;
        this.incomingAreas.clear();
        this.areaPage = 0;
        this.areaRefreshAt = 0L;
        this.detailsAt = Math.max(this.detailsAt, Util.getMillis() + 300L);
    }

    private static Component boundsDetails(SettlementViewBounds bounds) {
        return text("bounds", bounds.min().toShortString(), bounds.max().toShortString()).copy().append("\n")
                .append(text("dimensions", bounds.size(Direction.Axis.X), bounds.size(Direction.Axis.Y), bounds.size(Direction.Axis.Z)));
    }

    private void select(Pick hit, UUID area) {
        this.selectedArea = hit.resident() == null ? area : null;
        this.selectedBlock = hit.position();
        if (!java.util.Objects.equals(this.selectedResident, hit.resident())) {
            this.selectedResident = hit.resident();
            this.resident = null;
            this.residentChecked = false;
            this.residentRefreshAt = 0L;
        }
        updateActions();
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
            select(hit, null);
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
        boolean ready = !this.guardPending;
        switch (target.kind()) {
            case RESIDENT -> {
                var current = this.resident != null && this.resident.id().equals(target.id()) ? this.resident : null;
                if (current == null) {
                    details.add(text(this.residentChecked && target.id().equals(this.selectedResident) ? "not_resident" : "checking_resident"));
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
                    details.add(text("guard_order", text(current.guardOrder() == null ? "guard_order_none"
                            : "guard_" + current.guardOrder().mode().name().toLowerCase(java.util.Locale.ROOT))));
                    actions.add(contextAction("manage_selection", ready, () -> openManagement(Page.RESIDENTS, target.id().toString())));
                    if (target.id().equals(this.selectedResident) && selectedGuard()) {
                        for (var mode : SettlementGuardOrder.Mode.values()) {
                            actions.add(contextAction("guard_" + mode.name().toLowerCase(java.util.Locale.ROOT), ready, () -> beginGuardOrder(mode)));
                        }
                        actions.add(contextAction("guard_post", ready, () -> submitGuardOrder(null)));
                    }
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

    private record Pick(BlockPos position, UUID resident) {
    }
}
