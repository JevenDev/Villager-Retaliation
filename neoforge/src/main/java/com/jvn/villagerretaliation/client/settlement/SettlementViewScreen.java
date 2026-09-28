package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.item.ConstructionBlueprintItem;
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
    private final Set<Integer> heldKeys = new HashSet<>();
    private final SettlementViewCamera camera = new SettlementViewCamera();
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
    private Button manage;
    private Button drawArea;
    private Button editBounds;
    private SettlementManagementPayload.Area draft;
    private final SettlementViewGizmo gizmo = new SettlementViewGizmo();
    private SettlementViewPopup popup;
    private boolean panning;
    private final Button[] guardButtons = new Button[3];
    private Button returnToPost;
    private final Button[] blueprintButtons = new Button[4];
    private boolean placingBlueprint;
    private boolean blueprintPending;
    private long blueprintSentAt;
    private Component blueprintFeedback = Component.empty();
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

    SettlementViewScreen(SettlementManagementScreen parent, SettlementViewStatePayload payload) {
        super(text("title"));
        this.parent = parent;
        this.header = parent.snapshot().header();
        this.session = payload.session();
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
        this.detailsAt = Math.max(this.detailsAt, Util.getMillis() + 300L);
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
        this.window = payload.window();
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
        } else if (payload.page() == Page.RESIDENTS && this.waitingPage == Page.RESIDENTS) {
            if (!payload.query().equals(String.valueOf(this.selectedResident))) {
                return;
            }
            this.residentChecked = true;
            this.resident = payload.entries().stream().filter(entry -> entry.id().equals(this.selectedResident))
                    .filter(SettlementManagementPayload.Resident.class::isInstance)
                    .map(SettlementManagementPayload.Resident.class::cast).findFirst().orElse(null);
        }
        updateActions();
    }

    void acceptResult(SettlementManagementActionResultPayload payload) {
        if (this.blueprintPending && this.header.id().equals(payload.town())) {
            this.blueprintPending = false;
            this.blueprintSentAt = Util.getMillis();
            this.blueprintFeedback = payload.applied() && this.placingBlueprint ? Component.empty()
                    : text(payload.applied() ? "blueprint_applied" : "blueprint_denied");
            updateActions();
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
        this.lastFrame = 0L;
        this.popup = null;
        this.panelWidth = Math.clamp(this.width / 3, 128, 200);
        this.panelLeft = this.width - this.panelWidth;
        Page[] pages = {Page.SUMMARY, Page.RESIDENTS, Page.AREAS, Page.PROJECTS};
        String[] labels = {"overview", "residents", "areas", "projects"};
        boolean compact = this.height < 280;
        int half = (this.panelWidth - 20) / 2;
        for (int index = 0; index < pages.length; index++) {
            Page page = pages[index];
            addRenderableWidget(Button.builder(text(labels[index]), ignored -> openManagement(page, ""))
                    .bounds(this.panelLeft + 8 + (compact ? index % 2 * (half + 4) : 0),
                            62 + (compact ? index / 2 : index) * 22, compact ? half : this.panelWidth - 16, 20).build());
        }
        this.manage = addRenderableWidget(Button.builder(text("manage_selection"), ignored -> manageSelection())
                .bounds(this.panelLeft + 8, this.height - 70, this.panelWidth - 16, 20).build());
        this.editBounds = addRenderableWidget(Button.builder(text("edit_bounds"), ignored -> beginBoundsEdit(this.areas.get(this.selectedArea)))
                .bounds(this.panelLeft + 8, this.height - (compact ? 48 : 92), compact ? half : this.panelWidth - 16, 20).build());
        this.drawArea = addRenderableWidget(Button.builder(text("draw_area"), ignored -> {
            if (this.draft != null || this.drawing) {
                cancelDraft();
            } else {
                cancelPlacement();
                this.drawing = true;
            }
            updateActions();
        }).bounds(this.panelLeft + 8, this.height - (compact ? 26 : 48), compact ? half : this.panelWidth - 16, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose())
                .bounds(this.panelLeft + 8 + (compact ? half + 4 : 0), this.height - 26, compact ? half : this.panelWidth - 16, 20).build());
        addRenderableWidget(Button.builder(text("center"), ignored -> {
            this.focusX = this.window.center().getX() + 0.5D;
            this.focusZ = this.window.center().getZ() + 0.5D;
            this.panVelocityX = 0.0D;
            this.panVelocityZ = 0.0D;
            this.moved = true;
        }).bounds(8, 8, 76, 20).build());
        addRenderableWidget(Button.builder(Component.literal("-"), ignored -> zoom(1.0D)).bounds(88, 8, 22, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), ignored -> zoom(-1.0D)).bounds(114, 8, 22, 20).build());
        int guardWidth = Math.min(72, (this.panelLeft - 24) / 3);
        for (var mode : SettlementGuardOrder.Mode.values()) {
            this.guardButtons[mode.ordinal()] = addRenderableWidget(Button.builder(text("guard_" + mode.name().toLowerCase(java.util.Locale.ROOT)),
                    ignored -> beginGuardOrder(mode)).bounds(8 + mode.ordinal() * (guardWidth + 4), 52, guardWidth, 20).build());
        }
        this.returnToPost = addRenderableWidget(Button.builder(text("guard_post"), ignored -> submitGuardOrder(null))
                .bounds(8, 76, 148, 20).build());
        int blueprintWidth = Math.min(112, (this.panelLeft - 20) / 2);
        this.blueprintButtons[0] = addRenderableWidget(Button.builder(text("place_blueprint"), ignored -> {
            cancelDraft();
            this.placingBlueprint = !this.placingBlueprint;
            this.guardMode = null;
            this.guardPoints.clear();
            this.drawing = false;
            this.dragStart = null;
            this.dragEnd = null;
            updateActions();
        }).bounds(8, 102, blueprintWidth, 20).build());
        this.blueprintButtons[1] = addRenderableWidget(Button.builder(text("rotate_blueprint"), ignored -> adjustBlueprint(true, 0))
                .bounds(12 + blueprintWidth, 102, blueprintWidth, 20).build());
        this.blueprintButtons[2] = addRenderableWidget(Button.builder(text("raise_blueprint"), ignored -> adjustBlueprint(false, 1))
                .bounds(8, 124, blueprintWidth, 20).build());
        this.blueprintButtons[3] = addRenderableWidget(Button.builder(text("lower_blueprint"), ignored -> adjustBlueprint(false, -1))
                .bounds(12 + blueprintWidth, 124, blueprintWidth, 20).build());
        updateActions();
        positionCamera();
    }

    @Override
    public void tick() {
        long now = Util.getMillis();
        if (this.blueprintPending && now - this.blueprintSentAt > 3_000L) {
            this.blueprintPending = false;
            this.blueprintFeedback = text("blueprint_denied");
        }
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
        return this.title.copy().append(". ").append(selectedName()).append(". ")
                .append(text(this.draft == null ? "controls" : "refine_hint")).append(". ").append(this.gizmo.narration())
                .append(this.popup == null ? Component.empty() : this.popup.narration());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        double frameTime = this.lastFrame == 0L ? 0.0D : Math.max(0.0D, (now - this.lastFrame) / 1_000.0D);
        double elapsed = Math.min(frameTime, 0.05D);
        this.lastFrame = now;
        if (!this.minecraft.isWindowActive()) {
            clearControls();
        }
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
        if (this.draft != null && this.gizmo.hit(this.minecraft.gameRenderer.getMainCamera(), this.width, this.height,
                this.panelLeft, draftBounds(), mouseX, mouseY) != null) {
            this.hovered = new Pick(null, null);
        }
        graphics.fill(this.panelLeft, 0, this.width, this.height, 0xE0101010);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(this.header.name(), this.panelWidth - 16),
                this.panelLeft + 8, 10, 0xFFFFFF);
        graphics.drawString(this.font, text("population", this.header.population(), this.header.housing()),
                this.panelLeft + 8, 28, 0xD0D0D0);
        graphics.drawString(this.font, text("food", String.format(java.util.Locale.ROOT, "%.1f", this.header.foodDays())),
                this.panelLeft + 8, 42, 0xD0D0D0);
        Component selection = selectedName();
        int y = this.height < 280 ? 110 : 160;
        for (var line : this.font.split(selection, this.panelWidth - 16)) {
            if (y + this.font.lineHeight > this.height - (this.height < 280 ? this.editBounds.visible ? 76 : 54 : 100)) {
                break;
            }
            graphics.drawString(this.font, line, this.panelLeft + 8, y, 0xFFFFFF);
            y += this.font.lineHeight + 2;
        }
        Component hint = !this.blueprintFeedback.getString().isEmpty() ? this.blueprintFeedback
                : this.placingBlueprint ? text("blueprint_place_hint")
                : this.guardMode != null ? text(this.guardMode == SettlementGuardOrder.Mode.PATROL
                ? "guard_patrol_hint" : "guard_destination_hint", this.guardPoints.size(), SettlementGuardOrder.MAX_POINTS)
                : !this.guardFeedback.getString().isEmpty() ? this.guardFeedback : text(this.draft != null ? this.gizmo.limited() ? "bounds_limit" : this.height < 280 ? "refine_compact" : "refine_hint"
                : this.drawing ? "draw_hint" : this.height < 280 ? "controls_compact" : "controls");
        var lines = this.font.split(hint, Math.max(80, this.panelLeft - 16));
        int hintTop = this.height - 8 - lines.size() * (this.font.lineHeight + 1);
        graphics.fill(4, hintTop - 4, this.panelLeft - 4, this.height - 4, 0xD0101010);
        for (var line : lines) {
            graphics.drawString(this.font, line, 8, hintTop, 0xFFFFFF);
            hintTop += this.font.lineHeight + 1;
        }
        if (this.draft != null) {
            var bounds = draftBounds();
            List<Component> information = List.of(text("minimum", bounds.min().toShortString()), text("maximum", bounds.max().toShortString()),
                    text("dimensions", bounds.size(Direction.Axis.X), bounds.size(Direction.Axis.Y), bounds.size(Direction.Axis.Z)));
            int informationWidth = Math.min(this.panelLeft - 16, information.stream().mapToInt(this.font::width).max().orElse(0));
            graphics.fill(4, 30, informationWidth + 12, 34 + information.size() * (this.font.lineHeight + 2), 0xD0101010);
            int informationY = 34;
            for (var line : information) {
                graphics.drawString(this.font, this.font.plainSubstrByWidth(line.getString(), informationWidth), 8, informationY, 0xFFFFFF);
                informationY += this.font.lineHeight + 2;
            }
        }
        if (!terrainLoaded()) {
            graphics.drawString(this.font, text("loading"), 8, this.draft == null ? 34 : 70, 0xFFFFFF);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (this.draft != null) {
            graphics.enableScissor(0, 0, this.panelLeft, this.height);
            this.gizmo.render(graphics, this.font, this.minecraft.gameRenderer.getMainCamera(), this.width, this.height, this.panelLeft,
                    draftBounds(), mouseX, mouseY);
            graphics.disableScissor();
            if (this.popup == null) {
                this.gizmo.renderTooltip(graphics, this.font, this.minecraft.gameRenderer.getMainCamera(), this.width, this.height,
                        this.panelLeft, draftBounds(), mouseX, mouseY);
            }
        }
        refreshPopup();
        if (this.popup != null) {
            this.popup.render(graphics, this.font, mouseX, mouseY, partialTick);
        }
    }

    private Component selectedName() {
        if (this.draft != null) {
            return boundsDetails(draftBounds()).copy().append("\n").append(text(draftBounds().valid(boundsLimits()) ? "bounds_valid" : "bounds_invalid"));
        }
        if (this.resident != null) {
            Component name = text("resident", this.resident.name(), Math.round(this.resident.health()), this.resident.food());
            return this.resident.guardOrder() == null ? name : name.copy().append("\n")
                    .append(text("guard_" + this.resident.guardOrder().mode().name().toLowerCase(java.util.Locale.ROOT)));
        }
        var area = this.areas.get(this.selectedArea);
        if (area != null) {
            return text("area", area.name(), SettlementManagementScreen.enumText("area", area.kind()), area.workers(), area.beds());
        }
        if (this.selectedArea != null) {
            return text(this.areaRefreshAt == 0L ? "details_loading" : "details_unavailable");
        }
        if (this.selectedResident != null) {
            return text(this.residentChecked ? "not_resident" : "checking_resident");
        }
        return this.selectedBlock == null ? text("select_hint")
                : text("position", this.selectedBlock.getX(), this.selectedBlock.getY(), this.selectedBlock.getZ());
    }

    private void updateActions() {
        if (!permits(SettlementPermission.BUILD)) {
            this.placingBlueprint = false;
            if (this.draft != null || this.drawing) {
                cancelDraft();
            }
        }
        if (!selectedGuard()) {
            this.guardMode = null;
            this.guardPoints.clear();
        }
        var blueprint = heldBlueprint();
        if (blueprint == null || blueprint.placementLocked()) {
            this.placingBlueprint = false;
        }
        for (var button : this.blueprintButtons) {
            if (button != null) {
                button.visible = this.draft == null && !this.drawing && blueprint != null && permits(SettlementPermission.BUILD) && this.header.projectLimit() > 0;
                button.active = button.visible && !blueprint.placementLocked() && blueprintReady() && !this.guardPending;
            }
        }
        if (this.blueprintButtons[0] != null) {
            this.blueprintButtons[0].setMessage(text(this.placingBlueprint ? "cancel_blueprint_placement" : "place_blueprint"));
        }
        if (this.manage != null) {
            this.manage.setMessage(text(this.draft == null ? "manage_selection" : "confirm_bounds"));
            this.manage.active = !this.guardPending && !this.blueprintPending && (this.draft != null
                    ? permits(SettlementPermission.BUILD) && draftBounds().valid(boundsLimits())
                    : this.resident != null || this.selectedArea != null && this.areas.containsKey(this.selectedArea) && permits(SettlementPermission.BUILD));
            this.editBounds.visible = this.draft == null && this.areas.containsKey(this.selectedArea) && permits(SettlementPermission.BUILD);
            this.editBounds.active = !this.guardPending && !this.blueprintPending;
            if (this.height < 280) {
                this.manage.setX(this.panelLeft + 8);
                this.manage.setY(this.height - (this.editBounds.visible ? 70 : 48));
                this.manage.setWidth(this.panelWidth - 16);
                this.editBounds.setWidth(this.panelWidth - 16);
            }
            this.drawArea.active = permits(SettlementPermission.BUILD) && !this.guardPending && !this.blueprintPending;
            this.drawArea.setMessage(this.height < 280 && (this.drawing || this.draft != null)
                    ? Component.translatable("gui.cancel") : text(this.drawing || this.draft != null ? "cancel_area" : "draw_area"));
        }
        for (var button : this.guardButtons) {
            if (button != null) {
                button.visible = selectedGuard();
                button.active = !this.guardPending && !this.blueprintPending;
            }
        }
        if (this.returnToPost != null) {
            this.returnToPost.visible = selectedGuard();
            this.returnToPost.active = !this.guardPending && !this.blueprintPending;
            this.guardButtons[SettlementGuardOrder.Mode.PATROL.ordinal()].setMessage(text(
                    this.guardMode == SettlementGuardOrder.Mode.PATROL ? "guard_finish_patrol" : "guard_patrol"));
        }
    }

    private boolean selectedGuard() {
        var area = this.resident == null ? null : this.areas.get(this.resident.area());
        return this.draft == null && !this.drawing && area != null && area.kind() == SettlementAreaType.GUARD_POST && permits(SettlementPermission.STAFF);
    }

    private void beginGuardOrder(SettlementGuardOrder.Mode mode) {
        if (!selectedGuard() || this.guardPending || this.blueprintPending) {
            return;
        }
        this.popup = null;
        if (this.guardMode == SettlementGuardOrder.Mode.PATROL && mode == this.guardMode) {
            if (this.guardPoints.size() >= 2) {
                submitGuardOrder(new SettlementGuardOrder(mode, this.guardPoints));
            }
            return;
        }
        this.placingBlueprint = false;
        this.blueprintFeedback = Component.empty();
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

    private ConstructionBlueprintItem.PreviewData heldBlueprint() {
        if (this.minecraft == null || this.minecraft.player == null || this.minecraft.level == null) {
            return null;
        }
        for (var stack : this.minecraft.player.getHandSlots()) {
            var preview = ConstructionBlueprintItem.previewData(stack).orElse(null);
            if (preview != null && !preview.locked() && preview.dimension().location().equals(this.minecraft.level.dimension().location())) {
                return preview;
            }
        }
        return null;
    }

    private void adjustBlueprint(boolean rotate, int height) {
        var blueprint = heldBlueprint();
        if (blueprint == null) {
            return;
        }
        var anchor = new BlockPos(blueprint.worldCenter().getX(), blueprint.worldMin().getY() + height, blueprint.worldCenter().getZ());
        Rotation rotation = rotate ? blueprint.rotation().getRotated(Rotation.CLOCKWISE_90) : blueprint.rotation();
        submitBlueprint(blueprint, anchor, rotation);
    }

    private boolean blueprintReady() {
        return !this.blueprintPending && Util.getMillis() - this.blueprintSentAt >= 300L;
    }

    private void submitBlueprint(ConstructionBlueprintItem.PreviewData blueprint, BlockPos anchor, Rotation rotation) {
        if (!blueprintReady() || this.guardPending || blueprint.placementLocked() || !permits(SettlementPermission.BUILD)) {
            return;
        }
        PacketDistributor.sendToServer(new SettlementManagementActionPayload(this.header.id(),
                new SettlementManagementActionPayload.PlaceBlueprint(blueprint.jobId(), anchor, rotation)));
        this.blueprintPending = true;
        this.blueprintSentAt = Util.getMillis();
        this.blueprintFeedback = text("blueprint_sending");
        updateActions();
    }

    private boolean permits(SettlementPermission permission) {
        return this.header.permissions().contains(permission);
    }

    private boolean held(int key) {
        return this.heldKeys.contains(key);
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
        this.heldKeys.clear();
        this.panVelocityX = 0.0D;
        this.panVelocityZ = 0.0D;
        this.panning = false;
        if (this.gizmo.dragging()) {
            updateDraft(this.gizmo.cancel());
        }
        this.dragStart = null;
        this.dragEnd = null;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (this.gizmo.dragging()) {
                updateDraft(this.gizmo.cancel());
                updateActions();
                return true;
            }
            if (this.popup != null) {
                this.popup = null;
                return true;
            }
            if (this.placingBlueprint || this.guardMode != null) {
                cancelPlacement();
                updateActions();
                return true;
            }
            if (this.draft != null || this.drawing) {
                cancelDraft();
                updateActions();
                return true;
            }
        }
        if (this.popup != null && this.popup.key(keyCode)) {
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && this.draft != null) {
            confirmDraft();
            return true;
        }
        if (navigationKey(keyCode)) {
            if (!this.gizmo.dragging()) this.heldKeys.add(keyCode);
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
        if (this.popup != null && this.popup.scroll(x, y, vertical) || this.gizmo.dragging()) {
            return true;
        }
        if (x < this.panelLeft) {
            zoom(-vertical);
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        refreshPopup();
        if (this.popup != null) {
            if (this.popup.click(x, y, button)) {
                return true;
            }
            this.popup = null;
            return true;
        }
        if (super.mouseClicked(x, y, button)) {
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.draft != null && x < this.panelLeft) {
            var target = this.gizmo.hit(this.minecraft.gameRenderer.getMainCamera(), this.width, this.height, this.panelLeft, draftBounds(), x, y);
            if (target != null) {
                clearControls();
                this.gizmo.begin(target, draftBounds(), this.minecraft.gameRenderer.getMainCamera(), this.width, this.height, x, y);
                return true;
            }
        }
        if (x >= this.panelLeft || x < 0 || y < 0 || y >= this.height) {
            return false;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && !this.gizmo.dragging()) {
            this.panning = true;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && (this.placingBlueprint || this.guardMode != null)) {
            cancelPlacement();
            updateActions();
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT || this.gizmo.dragging()) {
            return false;
        }
        var hit = pick(x, y);
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            openContext(hit, x, y);
            return true;
        }
        if (this.placingBlueprint) {
            var blueprint = heldBlueprint();
            if (blueprint != null && hit.position() != null) {
                submitBlueprint(blueprint, hit.position().above(), blueprint.rotation());
            }
            return true;
        }
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
        if (this.drawing) {
            this.dragStart = hit.position();
            this.dragEnd = hit.position();
            return true;
        }
        this.guardFeedback = Component.empty();
        this.blueprintFeedback = Component.empty();
        List<UUID> matching = matchingAreas(hit, x, y);
        select(hit, hit.resident() == null && !matching.isEmpty() ? matching.getFirst() : null);
        if (hit.resident() == null && matching.size() > 1) {
            openChooser(matching, x, y);
        }
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
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.gizmo.dragging()) {
            updateDraft(this.gizmo.move(this.minecraft.gameRenderer.getMainCamera(), this.width, this.height, x, y, boundsLimits()));
            updateActions();
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
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            this.panning = false;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.gizmo.dragging()) {
            this.gizmo.end();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.drawing && this.dragStart != null && this.dragEnd != null) {
            var bounds = drawingBounds();
            this.draft = new SettlementManagementPayload.Area(UUID.randomUUID(), "", SettlementAreaType.HOME,
                    bounds.min(), bounds.max(), 5, true, 0, 0, 0);
            this.drawing = false;
            this.dragStart = null;
            this.dragEnd = null;
            select(new Pick(null, null), null);
            updateActions();
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    private Pick pick(double x, double y) {
        var camera = this.minecraft.gameRenderer.getMainCamera();
        var ray = SettlementViewGizmo.ray(camera, this.width, this.height, x, y);
        Vec3 start = ray.start();
        Vec3 end = ray.end();
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
        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        var buffers = this.minecraft.renderBuffers().bufferSource();
        var lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        for (var area : this.areas.values()) {
            if (this.draft != null && this.draft.id().equals(area.id())) {
                continue;
            }
            if (this.window.chunks().stream().anyMatch(chunk -> chunk.getMaxBlockX() >= area.min().getX()
                    && chunk.getMinBlockX() <= area.max().getX() && chunk.getMaxBlockZ() >= area.min().getZ()
                    && chunk.getMinBlockZ() <= area.max().getZ())) {
                boolean selected = area.id().equals(this.selectedArea);
                LevelRenderer.renderLineBox(pose, lines, new AABB(Vec3.atLowerCornerOf(area.min()), Vec3.atLowerCornerOf(area.max().offset(1, 1, 1))),
                        selected ? 0.25F : 0.65F, selected ? 0.9F : 0.65F,
                        selected ? 1.0F : 0.65F, selected ? 1.0F : 0.45F);
            }
        }
        if (this.selectedBlock != null && this.selectedArea == null && this.selectedResident == null) {
            LevelRenderer.renderLineBox(pose, lines, new AABB(this.selectedBlock).inflate(0.015D),
                    0.25F, 0.9F, 1.0F, 1.0F);
        }
        if (this.hovered.position() != null && !this.hovered.position().equals(this.selectedBlock)
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
            LevelRenderer.renderLineBox(pose, lines, drawingBounds().box(), 1.0F, 1.0F, 1.0F, 1.0F);
        }
        if (this.draft != null) {
            LevelRenderer.renderLineBox(pose, lines, draftBounds().box(), 1.0F, 1.0F, 1.0F, 1.0F);
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
        if (this.draft != null && this.minecraft.screen == this) {
            pose.pushPose();
            pose.translate(-camera.x, -camera.y, -camera.z);
            double mouseX = this.minecraft.mouseHandler.xpos() * this.width / this.minecraft.getWindow().getScreenWidth();
            double mouseY = this.minecraft.mouseHandler.ypos() * this.height / this.minecraft.getWindow().getScreenHeight();
            this.gizmo.renderWorld(pose, buffers, event.getCamera(), this.width, this.height, this.panelLeft,
                    draftBounds(), mouseX, mouseY);
            pose.popPose();
        }
    }

    SettlementViewBounds draftBounds() {
        return this.draft == null ? null : new SettlementViewBounds(this.draft.min(), this.draft.max());
    }

    SettlementViewPopup contextPopup() {
        return this.popup;
    }

    SettlementViewGizmo gizmo() {
        return this.gizmo;
    }

    private SettlementViewBounds.Limits boundsLimits() {
        return new SettlementViewBounds.Limits(this.header.center(), this.header.radius(),
                this.minecraft.level.getMinBuildHeight(), this.minecraft.level.getMaxBuildHeight() - 1);
    }

    private SettlementViewBounds drawingBounds() {
        BlockPos first = new BlockPos(this.dragStart.getX(),
                Math.min(this.header.center().getY(), Math.min(this.dragStart.getY(), this.dragEnd.getY())), this.dragStart.getZ());
        BlockPos second = new BlockPos(this.dragEnd.getX(), Math.max(this.dragStart.getY(), this.dragEnd.getY()) + 4, this.dragEnd.getZ());
        return SettlementViewBounds.drawing(first, second, boundsLimits());
    }

    private void updateDraft(SettlementViewBounds bounds) {
        var area = this.draft;
        this.draft = new SettlementManagementPayload.Area(area.id(), area.name(), area.kind(), bounds.min(), bounds.max(),
                area.priority(), area.enabled(), area.beds(), area.fixtures(), area.workers());
    }

    private void cancelDraft() {
        this.draft = null;
        this.drawing = false;
        this.dragStart = null;
        this.dragEnd = null;
        this.gizmo.end();
        this.popup = null;
    }

    private void cancelPlacement() {
        this.placingBlueprint = false;
        this.guardMode = null;
        this.guardPoints.clear();
        this.popup = null;
        this.blueprintFeedback = Component.empty();
        this.guardFeedback = Component.empty();
    }

    private void beginBoundsEdit(SettlementManagementPayload.Area area) {
        if (area == null || !permits(SettlementPermission.BUILD) || this.guardPending || this.blueprintPending) {
            return;
        }
        cancelPlacement();
        clearControls();
        this.drawing = false;
        this.draft = area;
        updateActions();
    }

    private void confirmDraft() {
        if (this.draft != null && !this.gizmo.dragging() && draftBounds().valid(boundsLimits())
                && permits(SettlementPermission.BUILD) && !this.guardPending && !this.blueprintPending) {
            editArea(this.draft);
        }
    }

    void areaSaved(UUID id) {
        cancelDraft();
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
            var touching = this.areas.values().stream()
                    .filter(area -> new SettlementViewBounds(area.min(), area.max()).box().contains(Vec3.atCenterOf(hit.position())))
                    .map(SettlementManagementPayload.Area::id).toList();
            if (!touching.isEmpty()) {
                return touching;
            }
        }
        var ray = SettlementViewGizmo.ray(this.minecraft.gameRenderer.getMainCamera(), this.width, this.height, x, y);
        Vec3 end = hit.position() == null ? ray.end() : ray.start().add(ray.direction()
                .scale(ray.start().distanceTo(Vec3.atCenterOf(hit.position())) + 1.0D));
        return this.areas.values().stream().filter(area -> new SettlementViewBounds(area.min(), area.max()).box()
                .clip(ray.start(), end).isPresent()).map(SettlementManagementPayload.Area::id).toList();
    }

    private void openChooser(List<UUID> choices, double x, double y) {
        this.popup = new SettlementViewPopup(new SettlementViewPopup.Target(SettlementViewPopup.Kind.CHOOSER, null, choices), x, y);
        clearControls();
        refreshPopup();
    }

    private void openContext(Pick hit, double x, double y) {
        clearControls();
        var ray = SettlementViewGizmo.ray(this.minecraft.gameRenderer.getMainCamera(), this.width, this.height, x, y);
        boolean handle = this.draft != null && this.gizmo.hit(this.minecraft.gameRenderer.getMainCamera(),
                this.width, this.height, this.panelLeft, draftBounds(), x, y) != null;
        if (handle) {
            this.popup = new SettlementViewPopup(SettlementViewPopup.Target.of(SettlementViewPopup.Kind.DRAFT, this.draft.id()), x, y);
        } else if (hit.resident() != null) {
            select(hit, null);
            this.popup = new SettlementViewPopup(SettlementViewPopup.Target.of(SettlementViewPopup.Kind.RESIDENT, hit.resident()), x, y);
        } else if (this.draft != null && draftBounds().box().clip(ray.start(), ray.end()).isPresent()) {
            this.popup = new SettlementViewPopup(SettlementViewPopup.Target.of(SettlementViewPopup.Kind.DRAFT, this.draft.id()), x, y);
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
        boolean ready = !this.guardPending && !this.blueprintPending;
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
                if (area == null) {
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
                if (this.draft == null || !this.draft.id().equals(target.id())) {
                    details.add(text("details_unavailable"));
                } else {
                    details.add(text("draft"));
                    details.add(boundsDetails(draftBounds()));
                    boolean valid = draftBounds().valid(boundsLimits());
                    details.add(text(valid ? "bounds_valid" : "bounds_invalid"));
                    actions.add(contextAction("configure", ready && valid && permits(SettlementPermission.BUILD), this::confirmDraft));
                    actions.add(contextAction("cancel_area", ready, () -> {
                        cancelDraft();
                        updateActions();
                    }));
                }
            }
            case CHOOSER -> {
                details.add(text("choose_area"));
                for (UUID id : target.choices()) {
                    var area = this.areas.get(id);
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
        cancelPlacement();
        clearControls();
        SettlementViewClient.openPanels(this);
        return this.parent;
    }

    private void openManagement(Page page, String query) {
        var screen = management();
        this.minecraft.setScreen(screen);
        screen.navigateTo(page, query);
    }

    private void manageSelection() {
        if (this.draft != null) {
            confirmDraft();
        } else if (this.resident != null) {
            openManagement(Page.RESIDENTS, this.resident.id().toString());
        } else if (this.areas.containsKey(this.selectedArea)) {
            beginBoundsEdit(this.areas.get(this.selectedArea));
            confirmDraft();
        }
    }

    private void editArea(SettlementManagementPayload.Area area) {
        this.popup = null;
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
