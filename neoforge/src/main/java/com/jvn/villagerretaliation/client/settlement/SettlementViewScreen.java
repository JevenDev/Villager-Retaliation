package com.jvn.villagerretaliation.client.settlement;

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
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class SettlementViewScreen extends Screen {
    private static final float YAW = 45.0F;
    private static final float PITCH = 60.0F;
    private final SettlementManagementScreen parent;
    private final UUID session;
    private final Map<UUID, SettlementManagementPayload.Area> areas = new LinkedHashMap<>();
    private final Map<UUID, SettlementManagementPayload.Area> incomingAreas = new LinkedHashMap<>();
    private final Set<Integer> heldKeys = new HashSet<>();
    private SettlementManagementPayload.Header header;
    private SettlementViewWindow window;
    private double focusX;
    private double focusZ;
    private float viewHeight;
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
    private final Button[] guardButtons = new Button[3];
    private Button returnToPost;
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
        this.requestId = payload.requestId();
        this.acceptedRequestId = payload.requestId();
        this.managementRequestId = parent.snapshot().requestId() + 1;
    }

    SettlementManagementScreen parent() {
        return this.parent;
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
            this.residentChecked = payload.query().equals(String.valueOf(this.selectedResident));
            this.resident = payload.entries().stream().filter(entry -> entry.id().equals(this.selectedResident))
                    .filter(SettlementManagementPayload.Resident.class::isInstance)
                    .map(SettlementManagementPayload.Resident.class::cast).findFirst().orElse(null);
        }
        updateActions();
    }

    void acceptResult(SettlementManagementActionResultPayload payload) {
        if (this.guardPending && this.header.id().equals(payload.town())) {
            this.guardPending = false;
            this.guardFeedback = text(payload.applied() ? "guard_applied" : "guard_denied");
            this.residentRefreshAt = 0L;
            updateActions();
        }
    }

    @Override
    protected void init() {
        this.panelWidth = Math.clamp(this.width / 3, 128, 200);
        this.panelLeft = this.width - this.panelWidth;
        addRenderableWidget(Button.builder(text("overview"), ignored -> openManagement(Page.SUMMARY, ""))
                .bounds(this.panelLeft + 8, 62, this.panelWidth - 16, 20).build());
        addRenderableWidget(Button.builder(text("residents"), ignored -> openManagement(Page.RESIDENTS, ""))
                .bounds(this.panelLeft + 8, 84, this.panelWidth - 16, 20).build());
        addRenderableWidget(Button.builder(text("areas"), ignored -> openManagement(Page.AREAS, ""))
                .bounds(this.panelLeft + 8, 106, this.panelWidth - 16, 20).build());
        this.manage = addRenderableWidget(Button.builder(text("manage_selection"), ignored -> manageSelection())
                .bounds(this.panelLeft + 8, this.height - 70, this.panelWidth - 16, 20).build());
        this.drawArea = addRenderableWidget(Button.builder(text("draw_area"), ignored -> {
            this.guardMode = null;
            this.guardPoints.clear();
            this.drawing = !this.drawing;
            this.dragStart = null;
            this.dragEnd = null;
            updateActions();
        }).bounds(this.panelLeft + 8, this.height - 48, this.panelWidth - 16, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose())
                .bounds(this.panelLeft + 8, this.height - 26, this.panelWidth - 16, 20).build());
        addRenderableWidget(Button.builder(text("center"), ignored -> {
            this.focusX = this.window.center().getX() + 0.5D;
            this.focusZ = this.window.center().getZ() + 0.5D;
            this.moved = true;
        }).bounds(8, 8, 76, 20).build());
        addRenderableWidget(Button.builder(Component.literal("-"), ignored -> zoom(1.0D)).bounds(88, 8, 22, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), ignored -> zoom(-1.0D)).bounds(114, 8, 22, 20).build());
        for (var mode : SettlementGuardOrder.Mode.values()) {
            this.guardButtons[mode.ordinal()] = addRenderableWidget(Button.builder(text("guard_" + mode.name().toLowerCase(java.util.Locale.ROOT)),
                    ignored -> beginGuardOrder(mode)).bounds(8 + mode.ordinal() * 76, 52, 72, 20).build());
        }
        this.returnToPost = addRenderableWidget(Button.builder(text("guard_post"), ignored -> submitGuardOrder(null))
                .bounds(8, 76, 148, 20).build());
        updateActions();
        positionCamera();
    }

    @Override
    public void tick() {
        long now = Util.getMillis();
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
        return this.title.copy().append(". ").append(selectedName());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        double elapsed = this.lastFrame == 0L ? 0.0D : Math.clamp((now - this.lastFrame) / 1_000.0D, 0.0D, 0.05D);
        this.lastFrame = now;
        double forward = (held(GLFW.GLFW_KEY_W) || held(GLFW.GLFW_KEY_UP) ? 1 : 0)
                - (held(GLFW.GLFW_KEY_S) || held(GLFW.GLFW_KEY_DOWN) ? 1 : 0);
        double right = (held(GLFW.GLFW_KEY_D) || held(GLFW.GLFW_KEY_RIGHT) ? 1 : 0)
                - (held(GLFW.GLFW_KEY_A) || held(GLFW.GLFW_KEY_LEFT) ? 1 : 0);
        double speed = this.viewHeight * elapsed / Math.max(1.0D, Math.hypot(forward, right));
        double yaw = Math.toRadians(YAW);
        pan((right * Math.cos(yaw) - forward * Math.sin(yaw)) * speed,
                (right * Math.sin(yaw) + forward * Math.cos(yaw)) * speed);
        positionCamera();
        graphics.fill(this.panelLeft, 0, this.width, this.height, 0xE0101010);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(this.header.name(), this.panelWidth - 16),
                this.panelLeft + 8, 10, 0xFFFFFF);
        graphics.drawString(this.font, text("population", this.header.population(), this.header.housing()),
                this.panelLeft + 8, 28, 0xD0D0D0);
        graphics.drawString(this.font, text("food", String.format(java.util.Locale.ROOT, "%.1f", this.header.foodDays())),
                this.panelLeft + 8, 42, 0xD0D0D0);
        Component selection = selectedName();
        int y = 138;
        for (var line : this.font.split(selection, this.panelWidth - 16)) {
            if (y + this.font.lineHeight > this.height - 78) {
                break;
            }
            graphics.drawString(this.font, line, this.panelLeft + 8, y, 0xFFFFFF);
            y += this.font.lineHeight + 2;
        }
        Component hint = this.guardMode != null ? text(this.guardMode == SettlementGuardOrder.Mode.PATROL
                ? "guard_patrol_hint" : "guard_destination_hint", this.guardPoints.size(), SettlementGuardOrder.MAX_POINTS)
                : !this.guardFeedback.getString().isEmpty() ? this.guardFeedback : text(this.drawing ? "draw_hint" : "controls");
        var lines = this.font.split(hint, Math.max(80, this.panelLeft - 16));
        int hintTop = this.height - 8 - lines.size() * (this.font.lineHeight + 1);
        graphics.fill(4, hintTop - 4, this.panelLeft - 4, this.height - 4, 0xD0101010);
        for (var line : lines) {
            graphics.drawString(this.font, line, 8, hintTop, 0xFFFFFF);
            hintTop += this.font.lineHeight + 1;
        }
        boolean loading = this.window.chunks().stream().anyMatch(position ->
                this.minecraft.level.getChunkSource().getChunk(position.x, position.z, false) == null);
        if (loading) {
            graphics.drawString(this.font, text("loading"), 8, 34, 0xFFFFFF);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private Component selectedName() {
        if (this.resident != null) {
            Component name = text("resident", this.resident.name(), Math.round(this.resident.health()), this.resident.food());
            return this.resident.guardOrder() == null ? name : name.copy().append("\n")
                    .append(text("guard_" + this.resident.guardOrder().mode().name().toLowerCase(java.util.Locale.ROOT)));
        }
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
            this.drawing = false;
            this.dragStart = null;
            this.dragEnd = null;
        }
        if (!selectedGuard()) {
            this.guardMode = null;
            this.guardPoints.clear();
        }
        if (this.manage != null) {
            this.manage.active = !this.guardPending && (this.resident != null
                    || this.selectedArea != null && this.areas.containsKey(this.selectedArea) && permits(SettlementPermission.BUILD));
            this.drawArea.active = permits(SettlementPermission.BUILD) && !this.guardPending;
            this.drawArea.setMessage(text(this.drawing ? "cancel_area" : "draw_area"));
        }
        for (var button : this.guardButtons) {
            if (button != null) {
                button.visible = selectedGuard();
                button.active = !this.guardPending;
            }
        }
        if (this.returnToPost != null) {
            this.returnToPost.visible = selectedGuard();
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
        if (this.minecraft == null || this.minecraft.level == null) {
            return;
        }
        int ground = this.window.center().getY();
        var chunk = this.minecraft.level.getChunkSource().getChunk(Math.floorDiv((int) Math.floor(this.focusX), 16),
                Math.floorDiv((int) Math.floor(this.focusZ), 16), false);
        if (chunk != null) {
            ground = Math.max(ground, chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(this.focusX),
                    (int) Math.floor(this.focusZ)));
        }
        double offset = this.viewHeight / Math.tan(Math.toRadians(PITCH));
        double x = Math.clamp(this.focusX + Math.sin(Math.toRadians(YAW)) * offset,
                this.window.center().getX() - this.window.radius(), this.window.center().getX() + this.window.radius());
        double z = Math.clamp(this.focusZ - Math.cos(Math.toRadians(YAW)) * offset,
                this.window.center().getZ() - this.window.radius(), this.window.center().getZ() + this.window.radius());
        double y = ground + this.viewHeight;
        var cameraChunk = this.minecraft.level.getChunkSource().getChunk(Math.floorDiv((int) Math.floor(x), 16),
                Math.floorDiv((int) Math.floor(z), 16), false);
        if (cameraChunk != null) {
            y = Math.max(y, cameraChunk.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z)) + 4.0D);
        }
        SettlementViewClient.position(x, y, z, YAW, PITCH);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && this.guardMode != null) {
            this.guardMode = null;
            this.guardPoints.clear();
            updateActions();
            return true;
        }
        if (navigationKey(keyCode)) {
            this.heldKeys.add(keyCode);
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
                || key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_RIGHT;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (x < this.panelLeft) {
            zoom(-vertical);
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (super.mouseClicked(x, y, button)) {
            return true;
        }
        if (x >= this.panelLeft) {
            return false;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && this.guardMode != null) {
            this.guardMode = null;
            this.guardPoints.clear();
            updateActions();
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        var hit = pick(x, y);
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
        this.selectedBlock = hit.position();
        this.selectedResident = hit.resident();
        this.resident = null;
        this.residentChecked = false;
        this.residentRefreshAt = 0L;
        this.selectedArea = this.selectedBlock == null ? null : this.areas.values().stream()
                .filter(area -> contains(area, this.selectedBlock)).map(SettlementManagementPayload.Area::id).findFirst().orElse(null);
        if (this.drawing) {
            this.dragStart = hit.position();
            this.dragEnd = hit.position();
        }
        updateActions();
        return true;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double deltaX, double deltaY) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && x < this.panelLeft) {
            double scale = 2.0D * this.viewHeight * Math.tan(Math.toRadians(SettlementViewClient.FOV / 2.0D)) / this.height;
            double yaw = Math.toRadians(YAW);
            pan((-Math.cos(yaw) * deltaX - Math.sin(yaw) * deltaY) * scale,
                    (-Math.sin(yaw) * deltaX + Math.cos(yaw) * deltaY) * scale);
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
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.drawing && this.dragStart != null && this.dragEnd != null) {
            AABB bounds = draftBounds();
            var area = new SettlementManagementPayload.Area(UUID.randomUUID(), "", SettlementAreaType.HOME,
                    BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                    BlockPos.containing(bounds.maxX - 1.0D, bounds.maxY - 1.0D, bounds.maxZ - 1.0D), 5, true, 0, 0, 0);
            editArea(area);
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    private Pick pick(double x, double y) {
        var camera = this.minecraft.gameRenderer.getMainCamera();
        double tangent = Math.tan(Math.toRadians(SettlementViewClient.FOV / 2.0D));
        Vec3 direction = new Vec3(camera.getLookVector())
                .add(new Vec3(camera.getLeftVector()).scale(-(x / this.width * 2.0D - 1.0D) * tangent * this.width / this.height))
                .add(new Vec3(camera.getUpVector()).scale((1.0D - y / this.height * 2.0D) * tangent)).normalize();
        Vec3 start = camera.getPosition();
        Vec3 end = start.add(direction.scale(256.0D));
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
            if (this.window.chunks().stream().anyMatch(chunk -> chunk.getMaxBlockX() >= area.min().getX()
                    && chunk.getMinBlockX() <= area.max().getX() && chunk.getMaxBlockZ() >= area.min().getZ()
                    && chunk.getMinBlockZ() <= area.max().getZ())) {
                boolean selected = area.id().equals(this.selectedArea);
                LevelRenderer.renderLineBox(pose, lines, new AABB(Vec3.atLowerCornerOf(area.min()), Vec3.atLowerCornerOf(area.max().offset(1, 1, 1))),
                        selected ? 1.0F : 0.65F, selected ? 1.0F : 0.65F, selected ? 1.0F : 0.65F, selected ? 1.0F : 0.5F);
            }
        }
        if (this.selectedResident != null) {
            for (Entity entity : this.minecraft.level.entitiesForRendering()) {
                if (entity.getUUID().equals(this.selectedResident)) {
                    LevelRenderer.renderLineBox(pose, lines, entity.getBoundingBox().inflate(0.08D), 1.0F, 1.0F, 1.0F, 1.0F);
                    break;
                }
            }
        }
        List<BlockPos> points = this.guardMode != null ? this.guardPoints
                : this.resident != null && this.resident.guardOrder() != null ? this.resident.guardOrder().points() : List.of();
        for (BlockPos point : points) {
            LevelRenderer.renderLineBox(pose, lines, new AABB(point).deflate(0.15D), 1.0F, 1.0F, 1.0F, 1.0F);
        }
        if (this.drawing && this.dragStart != null && this.dragEnd != null) {
            LevelRenderer.renderLineBox(pose, lines, draftBounds(), 1.0F, 1.0F, 1.0F, 1.0F);
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private AABB draftBounds() {
        return new AABB(Math.min(this.dragStart.getX(), this.dragEnd.getX()),
                Math.min(this.header.center().getY(), Math.min(this.dragStart.getY(), this.dragEnd.getY())),
                Math.min(this.dragStart.getZ(), this.dragEnd.getZ()), Math.max(this.dragStart.getX(), this.dragEnd.getX()) + 1,
                Math.max(this.dragStart.getY(), this.dragEnd.getY()) + 5, Math.max(this.dragStart.getZ(), this.dragEnd.getZ()) + 1);
    }

    private static boolean contains(SettlementManagementPayload.Area area, BlockPos position) {
        return position.getX() >= area.min().getX() && position.getX() <= area.max().getX()
                && position.getY() >= area.min().getY() && position.getY() <= area.max().getY()
                && position.getZ() >= area.min().getZ() && position.getZ() <= area.max().getZ();
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
        } else if (this.areas.containsKey(this.selectedArea)) {
            editArea(this.areas.get(this.selectedArea));
        }
    }

    private void editArea(SettlementManagementPayload.Area area) {
        var screen = management();
        this.minecraft.setScreen(screen);
        this.minecraft.setScreen(new SettlementEditScreen(screen, SettlementEditScreen.Mode.AREA, area));
    }

    @Override
    public void removed() {
        this.heldKeys.clear();
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
