package com.jvn.villagerretaliation.client.settlement;

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

    private SettlementViewClient() {
    }

    public static void acceptResult(com.jvn.villagerretaliation.network.SettlementManagementActionResultPayload payload) {
        if (active != null) {
            active.acceptResult(payload);
        }
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
        if (!(minecraft.screen instanceof SettlementManagementScreen parent) || !parent.expectingView()
                || parent.snapshot().header() == null || !parent.snapshot().header().id().equals(payload.town())) {
            reject(payload);
            return;
        }
        previousCamera = minecraft.getCameraEntity();
        previousPerspective = minecraft.options.getCameraType();
        camera = EntityType.MARKER.create(minecraft.level);
        if (camera == null) {
            reject(payload);
            return;
        }
        SettlementViewChunks.begin(minecraft.level, payload.session(), payload.window());
        active = new SettlementViewScreen(parent, payload);
        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        minecraft.setCameraEntity(camera);
        minecraft.setScreen(active);
        parent.applySupplyFocus(active);
    }

    public static void acceptBatch(com.jvn.villagerretaliation.network.SettlementViewBatchResultPayload payload) {
        if (active != null && active.session().equals(payload.session())) active.acceptBatch(payload);
    }

    public static void acceptOverview(com.jvn.villagerretaliation.network.SettlementViewOverviewPayload payload) {
        if (active != null && active.session().equals(payload.session())) active.acceptOverview(payload);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (active == null) {
            return;
        }
        var management = SettlementManagementClient.current();
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
            active.keepAlive();
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
        if (active != null) {
            end(active);
        }
    }

    public static boolean contains(Level level, BlockPos position) {
        return active != null && camera != null && camera.level() == level
                && Minecraft.getInstance().getCameraEntity() == camera && active.contains(position);
    }

    public static float projectionOffset() {
        var minecraft = Minecraft.getInstance();
        return active != null && (minecraft.screen == active || connectedPanel()) && minecraft.getCameraEntity() == camera && active.width > 0
                ? (float) (active.width - active.viewportWidth()) / active.width : 0;
    }

    public static boolean ownsCamera(Camera current) {
        return active != null && current.getEntity() == camera;
    }

    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (ownsCamera(event.getCamera())) {
            event.setFOV(FOV);
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
        if (active != null && event.getStage() == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
            active.renderPlotTint(event);
        }
        if (active != null && event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            active.renderWorldSelection(event);
        }
    }

    static void position(double x, double y, double z, float yaw, float pitch) {
        if (camera != null) {
            camera.moveTo(x, y, z, yaw, pitch);
            camera.setOldPosAndRot();
        }
    }

    static void end(SettlementViewScreen screen) {
        if (screen == null || active != screen) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        screen.endSession();
        active = null;
        panels = false;
        if (minecraft.getConnection() != null) {
            PacketDistributor.sendToServer(new SettlementViewRequestPayload(screen.session(), screen.nextRequestId(),
                    screen.focusX(), screen.focusZ(), screen.viewHeight(), true));
        }
        if (minecraft.getCameraEntity() == camera) {
            minecraft.setCameraEntity(previousCamera != null && !previousCamera.isRemoved() && previousCamera.level() == minecraft.level
                    ? previousCamera : minecraft.player);
        }
        if (minecraft.options.getCameraType() == CameraType.FIRST_PERSON && previousPerspective != null) {
            minecraft.options.setCameraType(previousPerspective);
        }
        SettlementViewChunks.clear();
        SettlementPlotRenderer.clear();
        camera = null;
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
