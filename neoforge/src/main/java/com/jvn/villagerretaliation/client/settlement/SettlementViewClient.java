package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.mixin.client.CameraAccessor;
import com.jvn.villagerretaliation.network.SettlementViewRequestPayload;
import com.jvn.villagerretaliation.network.SettlementViewStatePayload;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SettlementViewClient {
    public static final double FOV = 55.0D;
    private static SettlementViewScreen active;
    private static Entity previousCamera;
    private static CameraType previousPerspective;
    private static Marker camera;
    private static boolean panels;
    private static int openingSequence;
    private static int openingRequest;
    private static long openingDeadline;
    private static Level openingLevel;
    private static SettlementManagementScreen openingParent;
    private static SettlementViewCamera.Pose transitionStart;
    private static double transitionElapsed;
    private static double lastFov = FOV;
    private static double transitionFov;
    private static double renderedFov = FOV;
    private static Camera returnCamera;
    private static boolean closing;
    private static long lastFrame;

    private SettlementViewClient() {
    }

    public static void acceptResult(com.jvn.villagerretaliation.network.SettlementManagementActionResultPayload payload) {
        if (active != null) {
            active.acceptResult(payload);
        }
    }

    static void requestCurrent() {
        var minecraft = Minecraft.getInstance();
        if (active != null || openingRequest != 0 || minecraft.screen != null || minecraft.level == null
                || minecraft.player == null || !minecraft.player.isAlive() || minecraft.player.isSpectator()
                || minecraft.player.hurtTime > 0 || minecraft.getCameraEntity() != minecraft.player) return;
        openingSequence = openingSequence >= 1_000_000 ? 1 : openingSequence + 1;
        openingRequest = openingSequence;
        openingDeadline = net.minecraft.Util.getMillis() + 10_000L;
        openingLevel = minecraft.level;
        PacketDistributor.sendToServer(new com.jvn.villagerretaliation.network.SettlementViewOpenPayload(openingRequest));
    }

    private static boolean expectingCurrent() {
        var minecraft = Minecraft.getInstance();
        return openingRequest != 0 && net.minecraft.Util.getMillis() < openingDeadline
                && minecraft.level == openingLevel && minecraft.screen == null && minecraft.player != null
                && minecraft.player.isAlive() && !minecraft.player.isSpectator() && minecraft.player.hurtTime == 0
                && minecraft.getCameraEntity() == minecraft.player;
    }

    private static void clearOpening() {
        openingRequest = 0;
        openingLevel = null;
        openingParent = null;
    }

    public static void acceptOpening(com.jvn.villagerretaliation.network.SettlementViewOpenResultPayload payload) {
        if (!expectingCurrent() || payload.requestId() != openingRequest) return;
        var snapshot = payload.snapshot();
        if (snapshot == null || !openingLevel.dimension().location().equals(snapshot.dimension())
                || !snapshot.header().permissions().contains(com.jvn.villagerretaliation.settlement.SettlementPermission.VIEW)) {
            clearOpening();
            return;
        }
        openingParent = new SettlementManagementScreen(snapshot);
    }

    public static void acceptState(SettlementViewStatePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!payload.active()) {
            if (active != null && active.session().equals(payload.session())) {
                minecraft.setScreen(null);
                end(active);
            }
            return;
        }
        if (minecraft.level == null || minecraft.player == null || !minecraft.player.isAlive() || minecraft.player.hurtTime > 0
                || !minecraft.level.dimension().location().equals(payload.dimension())) {
            reject(payload);
            return;
        }
        if (active != null && active.session().equals(payload.session())) {
            active.acceptWindow(payload);
            return;
        }
        SettlementManagementScreen parent;
        if (expectingCurrent() && openingParent != null && payload.requestId() == openingRequest
                && openingParent.snapshot().header().id().equals(payload.town())) {
            parent = openingParent;
            parent.init(minecraft, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
        } else if (minecraft.screen instanceof SettlementManagementScreen management && management.expectingView()
                && management.snapshot().header() != null && management.snapshot().header().id().equals(payload.town())) {
            parent = management;
        } else {
            reject(payload);
            return;
        }
        clearOpening();
        previousCamera = minecraft.getCameraEntity();
        if (previousCamera == null) previousCamera = minecraft.player;
        previousPerspective = minecraft.options.getCameraType();
        camera = EntityType.MARKER.create(minecraft.level);
        if (camera == null) {
            reject(payload);
            return;
        }
        var currentCamera = minecraft.gameRenderer.getMainCamera();
        returnCamera = new Camera();
        copyEyeHeight(currentCamera, returnCamera);
        returnCamera.setup(minecraft.level, previousCamera, !previousPerspective.isFirstPerson(), previousPerspective.isMirrored(),
                currentCamera.getPartialTickTime());
        setEyeHeight(currentCamera, 0, 0);
        closing = false;
        lastFrame = 0;
        transitionStart = com.jvn.villagerretaliation.config.VillagerRetaliationConfig.ANIMATE_WORLD_VIEW_TRANSITION.get()
                ? new SettlementViewCamera.Pose(currentCamera.getPosition(), currentCamera.getYRot(), currentCamera.getXRot()) : null;
        transitionElapsed = 0;
        transitionFov = lastFov;
        renderedFov = transitionStart == null ? FOV : transitionFov;
        SettlementViewChunks.begin(minecraft.level, payload.session(), payload.window());
        active = new SettlementViewScreen(parent, payload);
        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        minecraft.setCameraEntity(camera);
        minecraft.setScreen(active);
        parent.applySupplyFocus(active);
    }

    public static void acceptPath(com.jvn.villagerretaliation.network.SettlementPathResultPayload payload) {
        if (active != null && active.session().equals(payload.session())) active.acceptPath(payload);
    }

    public static void acceptBatch(com.jvn.villagerretaliation.network.SettlementViewBatchResultPayload payload) {
        if (active != null && active.session().equals(payload.session())) active.acceptBatch(payload);
    }

    public static void acceptOverview(com.jvn.villagerretaliation.network.SettlementViewOverviewPayload payload) {
        if (active != null && active.session().equals(payload.session())) active.acceptOverview(payload);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (openingRequest != 0 && !expectingCurrent()) clearOpening();
        while (SettlementViewKeyMappings.OPEN.consumeClick()) requestCurrent();
        if (active == null) {
            return;
        }
        var management = SettlementManagementClient.current();
        if (returnCamera != null) returnCamera.tick();
        boolean linked = panels && management != null && management.root() == active.parent().root()
                && management.snapshot().header() != null;
        if (minecraft.screen != active && !linked || minecraft.level == null || minecraft.player == null
                || !minecraft.player.isAlive() || minecraft.player.isSpectator() || minecraft.player.hurtTime > 0
                || camera == null || camera.level() != minecraft.level || minecraft.getCameraEntity() != camera) {
            var closing = active;
            if (minecraft.screen == active || linked) {
                minecraft.setScreen(null);
            }
            end(closing);
        } else if (linked) {
            active.clearPeek();
            active.keepAlive();
        } else if (!minecraft.isWindowActive()) {
            active.clearPeek();
        }
    }

    public static boolean basicUi() {
        return SettlementManagementClient.current() != null;
    }

    public static boolean connectedPanel() {
        var management = SettlementManagementClient.current();
        return active != null && panels && management != null && management.root() == active.parent().root();
    }

    public static SettlementViewPanelLayout panelLayout(int width, int height, int preferredWidth, int preferredHeight, boolean compact) {
        return SettlementViewPanelLayout.create(width, height, preferredWidth, preferredHeight, connectedPanel(), compact);
    }

    public static boolean renderPanelBackground(net.minecraft.client.gui.GuiGraphics graphics, int left, int top, int width, int height) {
        if (!connectedPanel()) return false;
        if (top - 8 >= SettlementViewLayout.HEADER_HEIGHT)
            active.renderPanelBackdrop(graphics, Minecraft.getInstance().screen.width);
        SettlementViewUi.frame(graphics, left - 8, top - 8, width + 16, height + 16);
        return true;
    }

    static void openPanels(SettlementViewScreen view) {
        panels = active == view;
    }

    static boolean panelsOpen(SettlementViewScreen view) {
        return active == view && panels;
    }

    static SettlementViewScreen view(SettlementManagementScreen management) {
        return active != null && management.root() == active.parent().root() ? active : null;
    }

    static boolean focusSupply(SettlementManagementScreen management, java.util.UUID area, BlockPos position, boolean resume) {
        if (active == null || management.root() != active.parent().root()) return false;
        active.focusSupply(area, position);
        if (resume) resume(management);
        return true;
    }

    static boolean resume(SettlementManagementScreen management) {
        if (active == null || !panels || management.root() != active.parent().root()) {
            return false;
        }
        active.resumeManagement(management);
        panels = false;
        Minecraft.getInstance().setScreen(active);
        return true;
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clearOpening();
        if (active != null) {
            end(active);
        }
        SettlementBoundsClient.clear();
        SettlementPlotRenderer.clear();
    }

    public static boolean contains(Level level, BlockPos position) {
        return active != null && camera != null && camera.level() == level
                && Minecraft.getInstance().getCameraEntity() == camera && active.contains(position);
    }

    public static float projectionOffset() {
        var minecraft = Minecraft.getInstance();
        return active != null && (minecraft.screen == active || connectedPanel()) && minecraft.getCameraEntity() == camera && active.width > 0
                ? (float) ((active.width - active.viewportWidth()) / (double) active.width * (closing ? 1 - transitionProgress() : transitionProgress())) : 0;
    }

    public static boolean ownsCamera(Camera current) {
        return active != null && current.getEntity() == camera;
    }

    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (ownsCamera(event.getCamera())) {
            double target = closing ? event.getFOV() : FOV;
            event.setFOV(transitionFov + (target - transitionFov) * transitionProgress());
            if (event.usedConfiguredFov()) renderedFov = event.getFOV();
        } else if (event.usedConfiguredFov()) {
            lastFov = event.getFOV();
        }
    }

    public static void onRenderGui(RenderGuiEvent.Pre event) {
        if (active != null) {
            event.setCanceled(true);
        }
    }

    public static void onRenderHand(RenderHandEvent event) {
        if (active != null) {
            event.setCanceled(true);
        }
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
            if (active != null) active.renderPlotTint(event);
            else SettlementBoundsClient.render(event);
        }
        if (active != null && event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            active.renderWorldSelection(event);
        }
    }

    static boolean closing() { return closing; }

    static void close(SettlementViewScreen screen) {
        var minecraft = Minecraft.getInstance();
        if (active != screen || closing) return;
        if (!com.jvn.villagerretaliation.config.VillagerRetaliationConfig.ANIMATE_WORLD_VIEW_TRANSITION.get()
                || !canReturn(minecraft)) {
            minecraft.setScreen(null);
            return;
        }
        screen.clearControls();
        screen.clearPeek();
        var current = minecraft.gameRenderer.getMainCamera();
        transitionStart = new SettlementViewCamera.Pose(current.getPosition(), current.getYRot(), current.getXRot());
        transitionFov = renderedFov;
        transitionElapsed = 0;
        lastFrame = 0;
        closing = true;
    }

    private static boolean canReturn(Minecraft minecraft) {
        return camera != null && minecraft.getCameraEntity() == camera && returnCamera != null
                && previousCamera != null && !previousCamera.isRemoved() && previousCamera.level() == minecraft.level
                && previousPerspective != null && minecraft.player != null && minecraft.player.isAlive()
                && !minecraft.player.isSpectator() && minecraft.player.hurtTime == 0;
    }

    public static void beforeRender(float partialTick) {
        if (active == null || camera == null) return;
        active.beginFrame();
        setEyeHeight(Minecraft.getInstance().gameRenderer.getMainCamera(), 0, 0);
        long now = net.minecraft.Util.getNanos();
        double elapsed = lastFrame == 0 ? 0 : Math.clamp((now - lastFrame) / 1_000_000_000.0D, 0, 0.05D);
        lastFrame = now;
        if (!closing) {
            active.updateCamera(elapsed);
            return;
        }
        if (!canReturn(Minecraft.getInstance())) {
            Minecraft.getInstance().setScreen(null);
            return;
        }
        returnCamera.setup(Minecraft.getInstance().level, previousCamera, !previousPerspective.isFirstPerson(),
                previousPerspective.isMirrored(), partialTick);
        position(new SettlementViewCamera.Pose(returnCamera.getPosition(), returnCamera.getYRot(), returnCamera.getXRot()), elapsed);
        if (transitionProgress() >= 1) {
            end(active);
            Minecraft.getInstance().setScreen(null);
        }
    }

    private static void copyEyeHeight(Camera source, Camera target) {
        var access = (CameraAccessor) source;
        setEyeHeight(target, access.villagerretaliation$getEyeHeight(), access.villagerretaliation$getEyeHeightOld());
    }

    private static void setEyeHeight(Camera target, float height, float previous) {
        var access = (CameraAccessor) target;
        access.villagerretaliation$setEyeHeight(height);
        access.villagerretaliation$setEyeHeightOld(previous);
    }

    static boolean transitioning() {
        return transitionStart != null;
    }

    private static double transitionProgress() {
        return transitionStart == null ? 1.0D : SettlementViewCamera.transitionProgress(transitionElapsed);
    }

    static void position(SettlementViewCamera.Pose target, double elapsed) {
        if (camera == null) return;
        if (transitionStart != null) {
            transitionElapsed += Math.max(0, elapsed);
            double progress = transitionProgress();
            target = SettlementViewCamera.transition(transitionStart, target, progress);
            if (progress >= 1 && !closing) transitionStart = null;
        }
        camera.moveTo(target.position().x, target.position().y, target.position().z, target.yaw(), target.pitch());
        camera.setOldPosAndRot();
    }

    static void end(SettlementViewScreen screen) {
        if (screen == null || active != screen) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        screen.endSession();
        active = null;
        panels = false;
        transitionStart = null;
        closing = false;
        lastFrame = 0;
        if (minecraft.getConnection() != null) {
            PacketDistributor.sendToServer(new SettlementViewRequestPayload(screen.session(), screen.nextRequestId(),
                    screen.focusX(), screen.focusZ(), screen.viewHeight(), true));
        }
        if (minecraft.getCameraEntity() == camera) {
            if (returnCamera != null) copyEyeHeight(returnCamera, minecraft.gameRenderer.getMainCamera());
            minecraft.setCameraEntity(previousCamera != null && !previousCamera.isRemoved() && previousCamera.level() == minecraft.level
                    ? previousCamera : minecraft.player);
        }
        if (minecraft.options.getCameraType() == CameraType.FIRST_PERSON && previousPerspective != null) {
            minecraft.options.setCameraType(previousPerspective);
        }
        SettlementViewChunks.clear();
        SettlementPlotRenderer.clear();
        camera = null;
        returnCamera = null;
        previousCamera = null;
        previousPerspective = null;
    }

    private static void reject(SettlementViewStatePayload payload) {
        if (Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new SettlementViewRequestPayload(payload.session(), payload.requestId(),
                    payload.window().x(), payload.window().z(), payload.window().height(), true));
        }
    }
}
