package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.settlement.SettlementArea;
import com.jvn.villagerretaliation.settlement.SettlementPermission;
import com.jvn.villagerretaliation.settlement.SettlementAreaType;
import com.jvn.villagerretaliation.settlement.SettlementBuildingService;
import com.jvn.villagerretaliation.settlement.SettlementCareService;
import com.jvn.villagerretaliation.settlement.SettlementManagementService;
import com.jvn.villagerretaliation.settlement.SettlementSavedData;
import com.jvn.villagerretaliation.settlement.SettlementGuardOrder;
import com.jvn.villagerretaliation.settlement.SettlementWorkforceService;
import com.jvn.villagerretaliation.settlement.SettlementViewWindow;
import com.mojang.logging.LogUtils;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = VillagerRetaliation.MOD_ID, value = Dist.CLIENT)
public final class SettlementViewClientSmoke {
    private static final String OUTPUT = "villagerretaliation.settlementClientSmoke";
    private static CompletableFuture<Fixture> preparation;
    private static CompletableFuture<Void> serverCheck;
    private static Fixture fixture;
    private static ProjectFixture projectFixture;
    private static UUID brewer;
    private static long started;
    private static int phase;
    private static int ticks;
    private static double originalX;
    private static double originalZ;
    private static float originalHeight;
    private static float originalYaw;
    private static Vec3 expectedMovement;
    private static SettlementArea originalArea;
    private static SettlementViewBounds refinedBounds;
    private static int originalAreaCount;
    private static UUID temporaryLeader;
    private static Villager unavailableResident;
    private static int panDirection;
    private static final int[] PAN_KEYS = {GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_A,
            GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_LEFT};
    private static final int[] PAN_X = {-1, -1, 1, 1, -1, -1, 1, 1};
    private static final int[] PAN_Z = {1, -1, -1, 1, 1, -1, -1, 1};
    private static Vec3 body;
    private static Entity zoomResident;
    private static Vec3 zoomCamera;
    private static BlockEntity zoomBed;
    private static boolean maxZoomResidentRendered;

    private SettlementViewClientSmoke() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (System.getProperty(OUTPUT) == null || phase == 6) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.options.pauseOnLostFocus = false;
        if (started == 0L) {
            started = Util.getMillis();
        }
        if (Util.getMillis() - started >= 180_000L) {
            capture("failure");
        }
        require(Util.getMillis() - started < 180_000L, "client smoke run timed out at phase " + phase
                + " on " + (minecraft.screen == null ? "game" : minecraft.screen.getClass().getSimpleName()));
        if (minecraft.level == null || minecraft.player == null || minecraft.getSingleplayerServer() == null) {
            return;
        }
        if (phase == 0 && minecraft.screen == null) {
            minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            minecraft.options.renderDistance().set(6);
            minecraft.options.guiScale().set(2);
            minecraft.resizeDisplay();
            body = minecraft.player.position();
            UUID player = minecraft.player.getUUID();
            preparation = minecraft.getSingleplayerServer().submit(() ->
                    prepare(minecraft.getSingleplayerServer().getPlayerList().getPlayer(player)));
            phase = 1;
        } else if (phase == 1 && preparation.isDone()) {
            fixture = preparation.join();
            phase = 2;
        } else if (phase == 2 && minecraft.screen instanceof SettlementManagementScreen screen) {
            button(screen, "villagerretaliation.gui.settlement.world_view").onPress();
            ticks = 0;
            phase = 60;
        } else if (phase == 60 && minecraft.screen instanceof SettlementViewScreen screen && visibleResident(minecraft) != null) {
            if (++ticks == 1) {
                originalHeight = screen.viewHeight();
                screen.mouseScrolled(40, 80, 0, -100);
            }
            if (ticks < 120 && (!maxZoomResidentRendered || !screen.terrainLoaded())) return;
            require(screen.viewHeight() == SettlementViewWindow.MAX_HEIGHT, "the rendering check reaches maximum town zoom");
            require(Math.abs(screen.cameraHeight() - screen.viewHeight()) < 0.1D, "the camera eases into maximum zoom");
            require(screen.terrainLoaded(), "the maximum zoom capture includes the streamed terrain");
            require(maxZoomResidentRendered, "the real resident model renders beyond vanilla distance at maximum zoom");
            zoomResident = visibleResident(minecraft);
            zoomCamera = minecraft.gameRenderer.getMainCamera().getPosition();
            require(!minecraft.getEntityRenderDispatcher().shouldRender(zoomResident,
                            new Frustum(new Matrix4f(), new Matrix4f()), zoomCamera.x, zoomCamera.y, zoomCamera.z),
                    "maximum zoom retains frustum culling");
            var outside = EntityType.VILLAGER.create(minecraft.level);
            outside.setPos(fixture.center().offset(1_000, 0, 1_000).getBottomCenter());
            require(!minecraft.getEntityRenderDispatcher().shouldRender(outside, visibleFrustum(),
                            zoomCamera.x, zoomCamera.y, zoomCamera.z),
                    "villagers outside the authorized viewing window retain distance culling");
            zoomBed = minecraft.level.getBlockEntity(fixture.center().offset(-6, 0, 0));
            require(zoomBed != null && !minecraft.getBlockEntityRenderDispatcher().getRenderer(zoomBed).shouldRender(zoomBed, zoomCamera),
                    "the remote bed exceeds its ordinary rendering distance");
            require(renderBedVertices(zoomBed) > 0, "the native bed renderer emits geometry at maximum town zoom");
            capture("maximum-zoom-resident");
            screen.mouseScrolled(40, 80, 0, 100);
            ticks = 0;
            phase = 62;
        } else if (phase == 62 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 30
                && (screen.terrainLoaded() || ticks >= 120)) {
            require(screen.viewHeight() == SettlementViewWindow.MIN_HEIGHT, "the view reaches minimum zoom");
            require(Math.abs(screen.cameraHeight() - screen.viewHeight()) < 0.1D, "the camera eases into minimum zoom");
            require(screen.terrainLoaded(), "the minimum zoom capture includes the streamed terrain");
            require(Math.abs(minecraft.gameRenderer.getMainCamera().getXRot() - SettlementViewScreen.PITCH) < 3.0F,
                    "the centered settlement camera has an isometric-style tilt");
            capture("minimum-zoom");
            screen.mouseScrolled(40, 80, 0, Math.log(screen.viewHeight() / originalHeight) / Math.log(1.15D));
            ticks = 0;
            originalYaw = screen.cameraYaw();
            screen.keyPressed(GLFW.GLFW_KEY_Q, 0, 0);
            phase = 80;
        } else if (phase == 80 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 8
                && ((originalYaw - screen.cameraYaw() + 360) % 360 > 5 || ticks >= 120)) {
            screen.keyReleased(GLFW.GLFW_KEY_Q, 0, 0);
            require((originalYaw - screen.cameraYaw() + 360) % 360 > 5, "holding Q rotates left");
            originalYaw = screen.cameraYaw();
            screen.keyPressed(GLFW.GLFW_KEY_E, 0, 0);
            ticks = 0;
            phase = 81;
        } else if (phase == 81 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 8
                && ((screen.cameraYaw() - originalYaw + 360) % 360 > 5 || ticks >= 120)) {
            screen.keyReleased(GLFW.GLFW_KEY_E, 0, 0);
            require((screen.cameraYaw() - originalYaw + 360) % 360 > 5, "holding E rotates right");
            originalX = screen.focusX();
            originalZ = screen.focusZ();
            expectedMovement = SettlementViewCamera.movement(screen.cameraYaw(), 0, 1);
            screen.keyPressed(GLFW.GLFW_KEY_W, 0, 0);
            ticks = 0;
            phase = 82;
        } else if (phase == 82 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 8) {
            screen.keyReleased(GLFW.GLFW_KEY_W, 0, 0);
            require(new Vec3(screen.focusX() - originalX, 0, screen.focusZ() - originalZ).dot(expectedMovement) > 0.1D,
                    "panning follows the rotated camera");
            screen.clearControls();
            button(screen, "villagerretaliation.gui.settlement_view.center").onPress();
            screen.rotateCamera(45.0D - screen.cameraYaw());
            ticks = 0;
            phase = 3;
        } else if (phase == 3 && minecraft.screen instanceof SettlementViewScreen screen && visibleResident(minecraft) != null) {
            if (++ticks < 30) {
                return;
            }
            require(minecraft.getCameraEntity() instanceof Marker, "the view owns a separate camera");
            require(minecraft.options.getCameraType() == CameraType.FIRST_PERSON, "the view removes third person camera collision");
            require(minecraft.player.position().distanceToSqr(body) < 4.0D, "the player's body stayed at its original location");
            require(SettlementViewChunks.get(minecraft.level, fixture.center().getX() >> 4, fixture.center().getZ() >> 4) != null,
                    "the remote terrain uses the separate client chunk cache");
            require(capture("world-view") > 1_000, "the remote grass terrain is rendered");
            verifySodiumChunk(new ChunkPos(fixture.center()), true);
            Vec3 table = fixture.center().getCenter().add(0.0D, 0.5D, 0.0D);
            clickWorld(screen, table);
            require(screen.contextPopup() != null && screen.contextPopup().target().kind() == SettlementViewPopup.Kind.CHOOSER,
                    "overlapping designations open an explicit chooser");
            require(screen.contextPopup().buttons().size() == 2, "the chooser includes both uses of the shared building");
            clickPopup(screen, "Shared workshop");
            require(screen.contextPopup().narration().getString().contains("Fixtures"), "saved-area details include fixture counts");
            UUID area = screen.contextPopup().target().id();
            originalArea = serverArea(area);
            originalAreaCount = serverAreaCount();
            clickPopup(screen, "Edit bounds");
            require(screen.draftBounds().equals(new SettlementViewBounds(originalArea.min(), originalArea.max())),
                    "saved bounds are edited on a local copy");
            verifyGizmo(screen);
            refinedBounds = screen.draftBounds();
            verifyPopupScales(minecraft);
            verifyTerrainFollowing(screen);
            screen.rotateCamera(90);
            originalYaw = screen.cameraYaw();
            screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            ticks = 0;
            phase = 83;
        } else if (phase == 83 && minecraft.screen instanceof SettlementEditScreen screen && ++ticks >= 2) {
            require(field(screen, "start").getValue().equals(coordinates(refinedBounds.min()))
                            && field(screen, "end").getValue().equals(coordinates(refinedBounds.max())),
                    "confirmation opens configuration with the exact refined bounds");
            require(field(screen, "name").getValue().equals(originalArea.name())
                            && field(screen, "priority").getValue().equals(Integer.toString(originalArea.priority())),
                    "editing saved bounds preserves name and priority");
            capture("bounds-configuration");
            button(screen, "gui.cancel").onPress();
            ticks = 0;
            phase = 84;
        } else if (phase == 84 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 3) {
            require(screen.draftBounds().equals(refinedBounds), "configuration cancellation retains the draft in world view");
            require(screen.cameraYaw() == originalYaw, "returning from configuration retains camera yaw");
            capture("area-gizmo");
            verifyGizmo(screen);
            var target = screen.gizmo().targets(minecraft.gameRenderer.getMainCamera(), screen.width, screen.height,
                    screen.width - Math.clamp(screen.width / 3, 128, 200), screen.draftBounds()).stream()
                    .filter(item -> item.handle().face() == Direction.UP).findFirst().orElseThrow();
            screen.mouseClicked(target.point().x(), target.point().y(), GLFW.GLFW_MOUSE_BUTTON_LEFT);
            ticks = 0;
            phase = 105;
        } else if (phase == 105 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 3) {
            capture("gizmo-resize-face");
            screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            require(screen.draftBounds() == null && serverArea(originalArea.id()).equals(originalArea),
                    "cancelling bounds editing leaves the saved area unchanged");
            button(screen, "villagerretaliation.gui.settlement_view.edit_bounds").onPress();
            dragGizmo(screen, new SettlementViewGizmo.Handle(Direction.Axis.Y, Direction.UP), 2, false);
            refinedBounds = screen.draftBounds();
            button(screen, "villagerretaliation.gui.settlement_view.confirm_bounds").onPress();
            ticks = 0;
            phase = 85;
        } else if (phase == 85 && minecraft.screen instanceof SettlementEditScreen screen && ++ticks >= 2) {
            require(field(screen, "end").getValue().equals(coordinates(refinedBounds.max())),
                    "Confirm also passes the exact resized bounds");
            button(screen, "villagerretaliation.gui.settlement.save").onPress();
            ticks = 0;
            phase = 86;
        } else if (phase == 86 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 20
                && screen.getNarrationMessage().getString().contains(originalArea.name())) {
            var saved = serverArea(originalArea.id());
            require(saved.min().equals(refinedBounds.min()) && saved.max().equals(refinedBounds.max())
                            && saved.name().equals(originalArea.name()) && saved.type() == originalArea.type()
                            && saved.priority() == originalArea.priority() && saved.enabled() == originalArea.enabled(),
                    "Save updates the authoritative bounds while preserving saved metadata");
            require(screen.draftBounds() == null && screen.getNarrationMessage().getString().contains(originalArea.name()),
                    "successful saving clears the draft and refreshes the selected area");
            screen.rotateCamera(45.0D - screen.cameraYaw());
            button(screen, "villagerretaliation.gui.settlement_view.center").onPress();
            ticks = 0;
            phase = 87;
        } else if (phase == 87 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 4) {
            button(screen, "villagerretaliation.gui.settlement_view.draw_area").onPress();
            drawWorld(screen, fixture.center().offset(8, -1, -7).getCenter(), fixture.center().offset(10, -1, -5).getCenter());
            require(minecraft.screen == screen && screen.draftBounds() != null, "releasing an initial draw stays in world view");
            require(screen.draftBounds().size(Direction.Axis.X) == 3 && screen.draftBounds().size(Direction.Axis.Z) == 3,
                    "the initial draft includes the exact dragged blocks");
            refinedBounds = screen.draftBounds();
            var handle = screen.gizmo().targets(minecraft.gameRenderer.getMainCamera(), screen.width, screen.height,
                    screen.width - Math.clamp(screen.width / 3, 128, 200), refinedBounds).get(1);
            screen.mouseClicked(handle.point().x(), handle.point().y(), GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            require(screen.contextPopup() != null && screen.contextPopup().target().kind() == SettlementViewPopup.Kind.DRAFT
                            && screen.contextPopup().narration().getString().contains("Bounds are valid"),
                    "draft right-click shows bounds, dimensions and validation: " + (screen.contextPopup() == null ? "none"
                            : screen.contextPopup().target().kind() + " " + screen.contextPopup().narration().getString()));
            ticks = 0;
            phase = 101;
        } else if (phase == 101 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 3) {
            capture("draft-context");
            clickPopup(screen, "Configure");
            ticks = 0;
            phase = 88;
        } else if (phase == 88 && minecraft.screen instanceof SettlementEditScreen screen && ++ticks >= 2) {
            field(screen, "name").setValue("Refined area");
            field(screen, "priority").setValue("7");
            button(screen, "villagerretaliation.gui.settlement.save").onPress();
            ticks = 0;
            phase = 89;
        } else if (phase == 89 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 20) {
            Minecraft.getInstance().getSingleplayerServer().submit(() -> {
                var town = serverTown();
                var saved = town.areas().values().stream().filter(area -> area.name().equals("Refined area")).findFirst().orElseThrow();
                require(saved.min().equals(refinedBounds.min()) && saved.max().equals(refinedBounds.max()) && saved.priority() == 7,
                        "the new draft saves through the existing authoritative action");
                require(town.areas().size() == originalAreaCount + 1, "only Save adds the new area");
            }).join();
            require(screen.draftBounds() == null, "saving a new draft clears it");
            button(screen, "villagerretaliation.gui.settlement_view.draw_area").onPress();
            drawWorld(screen, fixture.center().offset(8, -1, -3).getCenter(), fixture.center().offset(8, -1, -3).getCenter());
            require(screen.draftBounds().size(Direction.Axis.X) == 1 && screen.draftBounds().size(Direction.Axis.Z) == 1,
                    "a single ground block can start a draft");
            screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            require(screen.draftBounds() == null && serverAreaCount() == originalAreaCount + 1,
                    "Escape discards a new draft without saving");
            temporaryLeader = UUID.randomUUID();
            UUID player = minecraft.player.getUUID();
            minecraft.getSingleplayerServer().submit(() -> {
                var town = serverTown();
                require(town.transferLeadership(player, temporaryLeader), "the permission fixture changes leadership");
                require(town.setPermissions(temporaryLeader, player, java.util.Set.of(SettlementPermission.VIEW)),
                        "the permission fixture retains view-only access");
            }).join();
            ticks = 0;
            phase = 90;
        } else if (phase == 90 && minecraft.screen instanceof SettlementViewScreen screen
                && !button(screen, "villagerretaliation.gui.settlement_view.draw_area").active) {
            clickWorld(screen, fixture.center().getCenter().add(0, 0.5D, 0), GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            require(screen.contextPopup() != null && screen.contextPopup().target().kind() == SettlementViewPopup.Kind.CHOOSER,
                    "view-only users retain the overlapping area chooser");
            clickPopup(screen, "Shared workshop");
            require(screen.contextPopup().buttons().isEmpty(), "view-only area details do not offer build actions");
            screen.mouseClicked(1, 1, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            require(screen.contextPopup() == null, "an outside click dismisses the popup");
            clickWorld(screen, visibleResident(minecraft).getBoundingBox().getCenter(), GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            ticks = 0;
            phase = 91;
        } else if (phase == 91 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 10
                && screen.contextPopup() != null && screen.contextPopup().narration().getString().contains("Tamsin")) {
            require(screen.contextPopup().buttons().size() == 1
                            && screen.contextPopup().buttons().getFirst().getMessage().getString().equals(
                            Component.translatable("villagerretaliation.gui.settlement_view.manage_selection").getString()),
                    "view-only resident details retain management and hide staff actions");
            capture("view-only-context");
            UUID player = minecraft.player.getUUID();
            minecraft.getSingleplayerServer().submit(() -> require(serverTown().transferLeadership(temporaryLeader, player),
                    "the permission fixture restores leadership")).join();
            ticks = 0;
            phase = 92;
        } else if (phase == 92 && minecraft.screen instanceof SettlementViewScreen screen
                && button(screen, "villagerretaliation.gui.settlement_view.draw_area").active) {
            screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            unavailableResident = EntityType.VILLAGER.create(minecraft.level);
            unavailableResident.setPos(fixture.center().offset(8, 0, -2).getBottomCenter());
            unavailableResident.setNoAi(true);
            minecraft.level.addEntity(unavailableResident);
            clickWorld(screen, unavailableResident.getBoundingBox().getCenter(), GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            require(screen.contextPopup() != null && screen.contextPopup().target().id().equals(unavailableResident.getUUID())
                            && !screen.contextPopup().narration().getString().contains("Tamsin"),
                    "a new resident target clears stale details while loading");
            ticks = 0;
            phase = 93;
        } else if (phase == 93 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 5
                && screen.contextPopup() != null && screen.contextPopup().narration().getString().contains("not a resident")) {
            require(screen.contextPopup().buttons().isEmpty(), "unavailable residents do not expose management or guard actions");
            capture("unavailable-resident");
            unavailableResident.discard();
            screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            originalX = screen.focusX();
            originalZ = screen.focusZ();
            originalYaw = screen.cameraYaw();
            screen.keyPressed(GLFW.GLFW_KEY_W, 0, 0);
            screen.keyPressed(GLFW.GLFW_KEY_Q, 0, 0);
            button(screen, "villagerretaliation.gui.settlement_view.overview").onPress();
            ticks = 0;
            phase = 94;
        } else if (phase == 94 && minecraft.screen instanceof SettlementManagementScreen screen && ++ticks >= 3) {
            button(screen, "villagerretaliation.gui.settlement.world_view").onPress();
            ticks = 0;
            phase = 95;
        } else if (phase == 95 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 5) {
            require(screen.cameraYaw() == originalYaw && screen.focusX() == originalX && screen.focusZ() == originalZ,
                    "screen transitions clear held rotation and movement without a key release");
            minecraft.options.guiScale().set(3);
            minecraft.resizeDisplay();
            ticks = 0;
            phase = 96;
        } else if (phase == 96 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 4) {
            button(screen, "villagerretaliation.gui.settlement_view.draw_area").onPress();
            drawWorld(screen, fixture.center().offset(8, -1, -3).getCenter(), fixture.center().offset(10, -1, -1).getCenter());
            verifyGizmo(screen);
            ticks = 0;
            phase = 102;
        } else if (phase == 102 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 3) {
            capture("gizmo-scale-three");
            screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            minecraft.options.guiScale().set(4);
            minecraft.resizeDisplay();
            ticks = 0;
            phase = 97;
        } else if (phase == 97 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 4) {
            button(screen, "villagerretaliation.gui.settlement_view.draw_area").onPress();
            drawWorld(screen, fixture.center().offset(8, -1, -3).getCenter(), fixture.center().offset(10, -1, -1).getCenter());
            verifyGizmo(screen);
            ticks = 0;
            phase = 103;
        } else if (phase == 103 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 3) {
            capture("gizmo-scale-four");
            screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            minecraft.options.renderDistance().set(2);
            screen.mouseScrolled(40, 80, 0, 100);
            ticks = 0;
            phase = 98;
        } else if (phase == 98 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 20) {
            require(screen.viewHeight() == 8.0F && Math.abs(screen.cameraHeight() - 8) < 0.1D,
                    "closer zoom remains usable at minimum render distance");
            button(screen, "villagerretaliation.gui.settlement_view.draw_area").onPress();
            drawWorld(screen, fixture.center().offset(2, -1, -4).getCenter(), fixture.center().offset(4, -1, -2).getCenter());
            verifyGizmo(screen);
            ticks = 0;
            phase = 104;
        } else if (phase == 104 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 3) {
            capture("gizmo-close-zoom");
            screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            screen.mouseClicked(40, 150, GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
            screen.mouseDragged(40, 150, GLFW.GLFW_MOUSE_BUTTON_MIDDLE, 100_000, 0);
            screen.mouseReleased(40, 150, GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
            int radius = screen.parent().snapshot().header().radius() + SettlementViewWindow.BORDER;
            require(screen.focusX() == fixture.center().getX() - radius && screen.focusZ() == fixture.center().getZ() - radius,
                    "orientation-relative middle drag clamps at the settlement corner");
            screen.rotateCamera(315 - screen.cameraYaw());
            ticks = 0;
            phase = 99;
        } else if (phase == 99 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 10) {
            var position = minecraft.gameRenderer.getMainCamera().getPosition();
            require(Double.isFinite(position.x) && Double.isFinite(position.y) && Double.isFinite(position.z),
                    "a rotated camera remains finite while corner terrain loads");
            capture("settlement-corner");
            screen.rotateCamera(45 - screen.cameraYaw());
            button(screen, "villagerretaliation.gui.settlement_view.center").onPress();
            minecraft.options.guiScale().set(2);
            minecraft.options.renderDistance().set(6);
            minecraft.resizeDisplay();
            screen.mouseScrolled(40, 80, 0, Math.log(screen.viewHeight() / 32.0D) / Math.log(1.15D));
            ticks = 0;
            phase = 100;
        } else if (phase == 100 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 10) {
            originalX = screen.focusX();
            originalZ = screen.focusZ();
            originalHeight = screen.viewHeight();
            panDirection = 0;
            screen.keyPressed(PAN_KEYS[panDirection], 0, 0);
            ticks = 0;
            phase = 61;
        } else if (phase == 61 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 8) {
            screen.keyReleased(PAN_KEYS[panDirection], 0, 0);
            require((screen.focusX() - originalX) * PAN_X[panDirection] > 0.05D,
                    "keyboard panning moves in the expected horizontal screen direction for key " + PAN_KEYS[panDirection]);
            require((screen.focusZ() - originalZ) * PAN_Z[panDirection] > 0.05D,
                    "keyboard panning moves in the expected vertical screen direction for key " + PAN_KEYS[panDirection]);
            button(screen, "villagerretaliation.gui.settlement_view.center").onPress();
            if (++panDirection < PAN_KEYS.length) {
                originalX = screen.focusX();
                originalZ = screen.focusZ();
                screen.keyPressed(PAN_KEYS[panDirection], 0, 0);
                ticks = 0;
            } else {
                originalX = screen.focusX();
                screen.keyPressed(GLFW.GLFW_KEY_W, 0, 0);
                ticks = 0;
                phase = 4;
            }
        } else if (phase == 4 && minecraft.screen instanceof SettlementViewScreen screen) {
            if (++ticks == 8) {
                capture("shared-workplace");
                screen.keyReleased(GLFW.GLFW_KEY_W, 0, 0);
                require(screen.focusX() < originalX, "panning changes the remote camera position");
                screen.mouseScrolled(40, 80, 0, 1);
                require(screen.viewHeight() < originalHeight, "scrolling changes the zoom");
            }
            if (ticks == 20) {
                Entity resident = visibleResident(minecraft);
                require(resident != null, "the resident remains visible after panning");
                clickWorld(screen, resident.getBoundingBox().getCenter(), GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            }
            if (ticks >= 40 && screen.getNarrationMessage().getString().contains("Tamsin")) {
                require(button(screen, "villagerretaliation.gui.settlement_view.manage_selection").active,
                        "selecting the resident enables its management action");
                require(screen.contextPopup() != null && screen.contextPopup().target().kind() == SettlementViewPopup.Kind.RESIDENT
                                && screen.contextPopup().narration().getString().contains("Assigned area")
                                && screen.contextPopup().narration().getString().contains("Guard order"),
                        "resident context includes work assignment and guard details");
                capture("selected-resident");
                clickPopup(screen, "Move");
                screen.mouseClicked(40, 150, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                require(screen.contextPopup() == null, "right-click cancels guard selection without opening a popup");
                button(screen, "villagerretaliation.gui.settlement_view.guard_move").onPress();
                clickWorld(screen, Vec3.atBottomCenterOf(fixture.center().offset(4, 0, 3)));
                ticks = 0;
                phase = 7;
            }
        } else if (phase == 7 && minecraft.screen instanceof SettlementViewScreen screen
                && screen.getNarrationMessage().getString().contains("\nMove")) {
            capture("guard-order");
            UUID player = minecraft.player.getUUID();
            serverCheck = minecraft.getSingleplayerServer().submit(() -> {
                ServerPlayer serverPlayer = minecraft.getSingleplayerServer().getPlayerList().getPlayer(player);
                require(serverPlayer.serverLevel().getChunkSource().chunkMap.getDistanceManager()
                        .shouldForceTicks(new ChunkPos(fixture.center()).toLong()), "the viewed settlement is actively ticking");
                require(serverPlayer.getCamera() == serverPlayer && !serverPlayer.isSpectator(), "the server retains the vulnerable body");
                var order = SettlementSavedData.get(serverPlayer.serverLevel()).get(fixture.town()).orElseThrow()
                        .guardOrders().get(fixture.resident());
                require(order != null && order.mode() == SettlementGuardOrder.Mode.MOVE
                        && order.points().equals(java.util.List.of(fixture.center().offset(4, 0, 3))),
                        "the world-view click applies the exact guard destination");
            });
            serverCheck.join();
            button(screen, "villagerretaliation.gui.settlement_view.manage_selection").onPress();
            ticks = 0;
            phase = 12;
        } else if (phase == 12 && minecraft.screen instanceof SettlementManagementScreen screen && ++ticks >= 30
                && screen.snapshot().page() == com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.RESIDENTS
                && screen.snapshot().entries().size() == 1 && screen.snapshot().entries().getFirst().id().equals(fixture.resident())) {
            require(((com.jvn.villagerretaliation.network.SettlementManagementPayload.Resident) screen.snapshot().entries().getFirst()).presence()
                    != com.jvn.villagerretaliation.network.VillageOverviewPayload.Presence.UNLOADED, "linked panels keep the selected resident loaded");
            require(minecraft.getCameraEntity() instanceof Marker, "linked panels retain the remote camera");
            require(SettlementViewChunks.get(minecraft.level, fixture.center().getX() >> 4, fixture.center().getZ() >> 4) != null,
                    "linked panels retain the remote chunks");
            require(!((com.jvn.villagerretaliation.network.SettlementManagementPayload.Resident) screen.snapshot().entries().getFirst()).status().equals("active"),
                    "linked management receives the live worker status");
            require(screen.snapshot().query().equals(fixture.resident().toString()) && screen.children().stream()
                    .filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                    .map(net.minecraft.client.gui.components.EditBox.class::cast).anyMatch(box -> box.getValue().equals("Tamsin")),
                    "exact resident filtering shows the resident name instead of its internal identity");
            capture("linked-management");
            button(screen, "villagerretaliation.gui.settlement.world_view").onPress();
            phase = 13;
        } else if (phase == 13 && minecraft.screen instanceof SettlementViewScreen screen) {
            button(screen, "villagerretaliation.gui.settlement_view.overview").onPress();
            UUID player = minecraft.player.getUUID();
            serverCheck = minecraft.getSingleplayerServer().submit(() -> {
                ServerPlayer serverPlayer = minecraft.getSingleplayerServer().getPlayerList().getPlayer(player);
                require(serverPlayer.serverLevel().getChunkSource().chunkMap.getDistanceManager()
                        .shouldForceTicks(new ChunkPos(fixture.center()).toLong()), "returning from panels retains the same viewed town");
                serverPlayer.hurt(serverPlayer.damageSources().generic(), 1.0F);
            });
            ticks = 0;
            phase = 5;
        } else if (phase == 5 && minecraft.screen == null) {
            serverCheck.join();
            require(minecraft.getCameraEntity() == minecraft.player, "damage restores the player's camera");
            require(!minecraft.getEntityRenderDispatcher().shouldRender(zoomResident, visibleFrustum(),
                            zoomCamera.x, zoomCamera.y, zoomCamera.z),
                    "leaving the town view restores ordinary resident distance culling");
            require(minecraft.options.getCameraType() == CameraType.THIRD_PERSON_BACK, "damage restores the previous perspective");
            require(SettlementViewChunks.get(minecraft.level, fixture.center().getX() >> 4, fixture.center().getZ() >> 4) == null,
                    "leaving releases the remote client chunks");
            verifySodiumChunk(new ChunkPos(fixture.center()), false);
            if (++ticks >= 20) {
                require(renderBedVertices(zoomBed) == 0, "leaving the town view restores ordinary bed distance culling");
                UUID player = minecraft.player.getUUID();
                minecraft.getSingleplayerServer().submit(() -> {
                    ServerPlayer serverPlayer = minecraft.getSingleplayerServer().getPlayerList().getPlayer(player);
                    require(!serverPlayer.serverLevel().getChunkSource().chunkMap.getDistanceManager()
                            .shouldForceTicks(new ChunkPos(fixture.center()).toLong()), "leaving releases the remote server tickets");
                }).join();
                capture("after-damage");
                minecraft.getSingleplayerServer().submit(() -> SettlementManagementService.open(
                        minecraft.getSingleplayerServer().getPlayerList().getPlayer(player), fixture.town())).join();
                phase = 8;
            }
        } else if (phase == 8 && minecraft.screen instanceof SettlementManagementScreen screen) {
            screen.navigateTo(com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.HISTORY, "");
            phase = 70;
        } else if (phase == 70 && minecraft.screen instanceof SettlementManagementScreen screen
                && screen.snapshot().page() == com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.HISTORY) {
            require(screen.snapshot().entries().stream().anyMatch(entry -> entry instanceof
                    com.jvn.villagerretaliation.network.SettlementManagementPayload.History history
                    && history.kind() == com.jvn.villagerretaliation.settlement.SettlementEvent.Type.ESTABLISHED),
                    "the native history page receives the persisted founding event");
            capture("history");
            screen.navigateTo(com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.RESIDENTS, fixture.resident().toString());
            phase = 9;
        } else if (phase == 9 && minecraft.screen instanceof SettlementManagementScreen screen
                && screen.snapshot().page() == com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.RESIDENTS
                && screen.snapshot().entries().size() == 1) {
            var rows = screen.children().stream().filter(net.minecraft.client.gui.components.ObjectSelectionList.class::isInstance)
                    .map(child -> (net.minecraft.client.gui.components.ObjectSelectionList<?>) child).findFirst().orElseThrow();
            rows.children().getFirst().mouseClicked(0, 0, 0);
            require(button(screen, "villagerretaliation.gui.settlement.worker_settings").active, "resident selection enables civic settings");
            button(screen, "villagerretaliation.gui.settlement.worker_settings").onPress();
            ticks = 0;
            phase = 10;
        } else if (phase == 10 && minecraft.screen instanceof SettlementWorkerScreen screen && ++ticks >= 5) {
            require(screen.getTitle().getString().contains("Tamsin"), "the unloaded resident keeps their name in worker settings");
            capture("worker-settings");
            String enabled = Component.translatable("villagerretaliation.gui.settlement.worker.enabled").getString();
            screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> button.getMessage().getString().startsWith(enabled + ":")).findFirst().orElseThrow().onPress();
            require(button(screen, "villagerretaliation.gui.settlement.save").active, "changing work settings enables Save");
            button(screen, "villagerretaliation.gui.settlement.save").onPress();
            ticks = 0;
            phase = 11;
        } else if (phase == 11 && minecraft.screen instanceof SettlementManagementScreen screen && ++ticks >= 10) {
            UUID player = minecraft.player.getUUID();
            minecraft.getSingleplayerServer().submit(() -> {
                ServerPlayer serverPlayer = minecraft.getSingleplayerServer().getPlayerList().getPlayer(player);
                var town = SettlementSavedData.get(serverPlayer.serverLevel()).get(fixture.town()).orElseThrow();
                require(town.workerSettings().containsKey(fixture.resident()) && !town.workerSettings().get(fixture.resident()).enabled(),
                        "saving the native settings form pauses the authoritative civic worker");
            }).join();
            capture("worker-settings-saved");
            projectFixture = minecraft.getSingleplayerServer().submit(() -> prepareProject(
                    minecraft.getSingleplayerServer().getPlayerList().getPlayer(player))).join();
            button(screen, "villagerretaliation.gui.settlement.world_view").onPress();
            ticks = 0;
            phase = 14;
        } else if (phase == 14 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 30) {
            button(screen, "villagerretaliation.gui.settlement_view.residents").onPress();
            phase = 15;
        } else if (phase == 15 && minecraft.screen instanceof SettlementManagementScreen screen) {
            screen.navigateTo(com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.PROJECTS, "");
            phase = 16;
        } else if (phase == 16 && minecraft.screen instanceof SettlementManagementScreen screen
                && screen.snapshot().page() == com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.PROJECTS) {
            require(button(screen, "villagerretaliation.gui.settlement.draft_blueprint").active, "Hamlet unlocks blueprint drafting");
            button(screen, "villagerretaliation.gui.settlement.draft_blueprint").onPress();
            phase = 24;
        } else if (phase == 24 && minecraft.screen instanceof SettlementBlueprintScreen screen) {
            screen.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                    .map(net.minecraft.client.gui.components.EditBox.class::cast).findFirst().orElseThrow()
                    .setValue("minecraft:village/plains/houses/plains_small_house_1");
            var rows = screen.children().stream().filter(net.minecraft.client.gui.components.ObjectSelectionList.class::isInstance)
                    .map(child -> (net.minecraft.client.gui.components.ObjectSelectionList<?>) child).findFirst().orElseThrow();
            require(rows.children().size() == 1, "the blueprint catalog filters to the exact house");
            rows.children().getFirst().mouseClicked(0, 0, 0);
            require(button(screen, "villagerretaliation.gui.settlement.draft_blueprint_cost").active, "one real paper enables drafting");
            ticks = 0;
            phase = 33;
        } else if (phase == 33 && minecraft.screen instanceof SettlementBlueprintScreen screen && ++ticks >= 5) {
            capture("blueprint-catalog");
            button(screen, "villagerretaliation.gui.settlement.draft_blueprint_cost").onPress();
            ticks = 0;
            phase = 25;
        } else if (phase == 25 && minecraft.screen instanceof SettlementBlueprintScreen screen && ++ticks >= 5
                && !screen.parent().submitting() && heldBlueprint(minecraft) != null) {
            projectFixture = new ProjectFixture(projectFixture.builder(), heldBlueprint(minecraft).jobId());
            require(minecraft.player.getInventory().countItem(net.minecraft.world.item.Items.PAPER) == 0, "the client inventory consumed one paper");
            capture("blueprint-drafted");
            screen.onClose();
            phase = 26;
        } else if (phase == 26 && minecraft.screen instanceof SettlementManagementScreen screen) {
            button(screen, "villagerretaliation.gui.settlement.world_view").onPress();
            ticks = 0;
            phase = 27;
        } else if (phase == 27 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 10) {
            require(button(screen, "villagerretaliation.gui.settlement_view.place_blueprint").active, "the held blueprint enables world placement");
            button(screen, "villagerretaliation.gui.settlement_view.place_blueprint").onPress();
            clickWorld(screen, Vec3.atBottomCenterOf(fixture.center().offset(18, 0, 4)));
            ticks = 0;
            phase = 28;
        } else if (phase == 28 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 10
                && heldBlueprint(minecraft).worldCenter().getX() == fixture.center().getX() + 18
                && heldBlueprint(minecraft).worldCenter().getZ() == fixture.center().getZ() + 4) {
            button(screen, "villagerretaliation.gui.settlement_view.rotate_blueprint").onPress();
            ticks = 0;
            phase = 29;
        } else if (phase == 29 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 10
                && heldBlueprint(minecraft).rotation() == net.minecraft.world.level.block.Rotation.CLOCKWISE_90) {
            capture("blueprint-positioned");
            button(screen, "villagerretaliation.gui.settlement_view.raise_blueprint").onPress();
            ticks = 0;
            phase = 30;
        } else if (phase == 30 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 10
                && heldBlueprint(minecraft).worldMin().getY() == fixture.center().getY() + 1
                && button(screen, "villagerretaliation.gui.settlement_view.lower_blueprint").active) {
            button(screen, "villagerretaliation.gui.settlement_view.lower_blueprint").onPress();
            ticks = 0;
            phase = 31;
        } else if (phase == 31 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 10
                && heldBlueprint(minecraft).worldMin().getY() == fixture.center().getY()) {
            screen.mouseClicked(40, 150, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            require(minecraft.screen == screen && screen.contextPopup() == null, "right-click cancels blueprint placement before context actions");
            button(screen, "villagerretaliation.gui.settlement_view.place_blueprint").onPress();
            screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            require(minecraft.screen == screen, "Escape stops positioning without leaving the town view");
            button(screen, "villagerretaliation.gui.settlement_view.projects").onPress();
            phase = 32;
        } else if (phase == 32 && minecraft.screen instanceof SettlementManagementScreen screen
                && screen.snapshot().page() == com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.PROJECTS) {
            require(button(screen, "villagerretaliation.gui.settlement.new_project").active, "the drafted and positioned blueprint can be commissioned");
            button(screen, "villagerretaliation.gui.settlement.new_project").onPress();
            phase = 17;
        } else if (phase == 17 && minecraft.screen instanceof SettlementManagementScreen screen) {
            screen.navigateTo(com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.RESIDENTS, projectFixture.builder().toString());
            ticks = 0;
            phase = 18;
        } else if (phase == 18 && minecraft.screen instanceof SettlementManagementScreen screen
                && screen.snapshot().page() == com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.RESIDENTS
                && screen.snapshot().entries().size() == 1 && screen.snapshot().entries().getFirst().id().equals(projectFixture.builder())) {
            if (++ticks == 1) selectFirstRow(screen);
            require(screen.snapshot().entries().getFirst() instanceof com.jvn.villagerretaliation.network.SettlementManagementPayload.Resident resident
                    && resident.presence() == com.jvn.villagerretaliation.network.VillageOverviewPayload.Presence.UNLOADED,
                    "the selected construction worker has no loaded entity");
            require(button(screen, "villagerretaliation.gui.settlement.commission").active, "the unloaded assigned builder remains available");
            if (ticks < 5) return;
            capture("unloaded-builder");
            button(screen, "villagerretaliation.gui.settlement.commission").onPress();
            ticks = 0;
            phase = 19;
        } else if (phase == 19 && minecraft.screen instanceof SettlementConfirmScreen screen && ++ticks >= 5) {
            capture("project-review");
            button(screen, "gui.yes").onPress();
            phase = 20;
        } else if (phase == 20 && minecraft.screen instanceof SettlementManagementScreen screen
                && screen.snapshot().page() == com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.PROJECTS
                && screen.snapshot().entries().stream().anyMatch(entry -> entry.id().equals(projectFixture.project()))) {
            capture("project-started");
            selectFirstRow(screen);
            button(screen, "villagerretaliation.gui.settlement.cancel_project").onPress();
            ticks = 0;
            phase = 21;
        } else if (phase == 21 && minecraft.screen instanceof SettlementConfirmScreen screen && ++ticks >= 5) {
            capture("project-cancel-review");
            button(screen, "gui.yes").onPress();
            phase = 22;
        } else if (phase == 22 && minecraft.screen instanceof SettlementManagementScreen screen
                && screen.snapshot().entries().stream().anyMatch(entry ->
                        entry instanceof com.jvn.villagerretaliation.network.SettlementManagementPayload.Project project
                                && project.id().equals(projectFixture.project())
                                && project.status() == com.jvn.villagerretaliation.settlement.SettlementProject.Status.CANCELLED)) {
            capture("project-cancelled");
            screen.onClose();
            ticks = 0;
            phase = 23;
        } else if (phase == 23 && minecraft.screen == null && ++ticks >= 20) {
            require(minecraft.getCameraEntity() == minecraft.player, "closing linked management restores the player's camera");
            require(SettlementViewChunks.get(minecraft.level, fixture.center().getX() >> 4, fixture.center().getZ() >> 4) == null,
                    "closing linked management releases remote client chunks");
            UUID player = minecraft.player.getUUID();
            minecraft.getSingleplayerServer().submit(() -> {
                ServerPlayer serverPlayer = minecraft.getSingleplayerServer().getPlayerList().getPlayer(player);
                require(!serverPlayer.serverLevel().getChunkSource().chunkMap.getDistanceManager()
                        .shouldForceTicks(new ChunkPos(fixture.center()).toLong()), "closing linked management releases server tickets");
                var town = SettlementSavedData.get(serverPlayer.serverLevel()).get(fixture.town()).orElseThrow();
                require(town.projects().get(projectFixture.project()).status()
                        == com.jvn.villagerretaliation.settlement.SettlementProject.Status.CANCELLED,
                        "the client cancelled the authoritative construction project");
            }).join();
            brewer = minecraft.getSingleplayerServer().submit(() -> prepareBrewer(
                    minecraft.getSingleplayerServer().getPlayerList().getPlayer(player))).join();
            phase = 34;
        } else if (phase == 34 && minecraft.screen instanceof SettlementManagementScreen screen) {
            screen.navigateTo(com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.RESIDENTS, brewer.toString());
            phase = 35;
        } else if (phase == 35 && minecraft.screen instanceof SettlementManagementScreen screen
                && screen.snapshot().entries().size() == 1 && screen.snapshot().entries().getFirst().id().equals(brewer)) {
            selectFirstRow(screen);
            button(screen, "villagerretaliation.gui.settlement.worker_settings").onPress();
            phase = 36;
        } else if (phase == 36 && minecraft.screen instanceof SettlementWorkerScreen screen) {
            button(screen, "villagerretaliation.gui.settlement.brewing_orders").onPress();
            phase = 37;
        } else if (phase == 37 && minecraft.screen instanceof SettlementBrewingScreen screen) {
            var fields = screen.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                    .map(net.minecraft.client.gui.components.EditBox.class::cast).toList();
            fields.getFirst().setValue("minecraft:healing");
            fields.getLast().setValue("12");
            var rows = screen.children().stream().filter(net.minecraft.client.gui.components.ObjectSelectionList.class::isInstance)
                    .map(child -> (net.minecraft.client.gui.components.ObjectSelectionList<?>) child).findFirst().orElseThrow();
            String potion = net.minecraft.world.item.alchemy.PotionContents.createItemStack(net.minecraft.world.item.Items.POTION,
                    net.minecraft.world.item.alchemy.Potions.HEALING).getHoverName().getString();
            rows.children().stream().filter(row -> row.getNarration().getString().startsWith(potion)).findFirst().orElseThrow().mouseClicked(0, 0, 0);
            require(button(screen, "villagerretaliation.gui.settlement.brewing_order").active, "a selected recipe and quantity enable production");
            ticks = 0;
            phase = 38;
        } else if (phase == 38 && minecraft.screen instanceof SettlementBrewingScreen screen && ++ticks >= 5) {
            capture("brewing-order");
            button(screen, "villagerretaliation.gui.settlement.brewing_order").onPress();
            phase = 39;
        } else if (phase == 39 && minecraft.screen instanceof SettlementManagementScreen screen
                && screen.snapshot().entries().size() == 1
                && screen.snapshot().entries().getFirst() instanceof com.jvn.villagerretaliation.network.SettlementManagementPayload.Resident resident
                && resident.id().equals(brewer) && resident.brewingOrder() != null && resident.brewingOrder().amount() == 12) {
            require(resident.brewingOrder().potion().equals(net.minecraft.resources.ResourceLocation.withDefaultNamespace("healing")),
                    "the native form accepted the selected potion recipe");
            selectFirstRow(screen);
            button(screen, "villagerretaliation.gui.settlement.worker_settings").onPress();
            phase = 40;
        } else if (phase == 40 && minecraft.screen instanceof SettlementWorkerScreen screen) {
            button(screen, "villagerretaliation.gui.settlement.brewing_orders").onPress();
            ticks = 0;
            phase = 41;
        } else if (phase == 41 && minecraft.screen instanceof SettlementBrewingScreen screen && ++ticks >= 10) {
            capture("brewing-saved");
            require(button(screen, "villagerretaliation.gui.settlement.brewing_cancel").active, "the accepted order can be stopped");
            button(screen, "villagerretaliation.gui.settlement.brewing_cancel").onPress();
            phase = 42;
        } else if (phase == 42 && minecraft.screen instanceof SettlementManagementScreen screen
                && screen.snapshot().entries().size() == 1
                && screen.snapshot().entries().getFirst() instanceof com.jvn.villagerretaliation.network.SettlementManagementPayload.Resident resident
                && resident.id().equals(brewer) && resident.brewingOrder() == null) {
            UUID player = minecraft.player.getUUID();
            minecraft.getSingleplayerServer().submit(() -> {
                var serverPlayer = minecraft.getSingleplayerServer().getPlayerList().getPlayer(player);
                var town = SettlementSavedData.get(serverPlayer.serverLevel()).get(fixture.town()).orElseThrow();
                require(com.jvn.villagerretaliation.settlement.SettlementBrewingService.snapshot(serverPlayer.serverLevel(), town, brewer) == null,
                        "the native form cancels the authoritative brewing order");
            }).join();
            screen.navigateTo(com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.BEDS, "");
            phase = 43;
        } else if (phase == 43 && minecraft.screen instanceof SettlementManagementScreen screen
                && screen.snapshot().page() == com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.BEDS
                && screen.snapshot().entries().size() == 1
                && screen.snapshot().entries().getFirst() instanceof com.jvn.villagerretaliation.network.SettlementManagementPayload.Bed bed && bed.available()) {
            selectFirstRow(screen);
            button(screen, "villagerretaliation.gui.settlement.assign_home").onPress();
            phase = 44;
        } else if (phase == 44 && minecraft.screen instanceof SettlementManagementScreen screen) {
            screen.navigateTo(com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.RESIDENTS, fixture.resident().toString());
            phase = 45;
        } else if (phase == 45 && minecraft.screen instanceof SettlementManagementScreen screen
                && screen.snapshot().page() == com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.RESIDENTS
                && screen.snapshot().entries().size() == 1 && screen.snapshot().entries().getFirst().id().equals(fixture.resident())) {
            selectFirstRow(screen);
            button(screen, "villagerretaliation.gui.settlement.assign_home").onPress();
            ticks = 0;
            phase = 46;
        } else if (phase == 46 && minecraft.screen instanceof SettlementConfirmScreen screen && ++ticks >= 5) {
            capture("home-review");
            button(screen, "gui.yes").onPress();
            ticks = 0;
            phase = 47;
        } else if (phase == 47 && minecraft.screen instanceof SettlementManagementScreen screen
                && screen.snapshot().page() == com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.BEDS
                && screen.snapshot().entries().size() == 1
                && screen.snapshot().entries().getFirst() instanceof com.jvn.villagerretaliation.network.SettlementManagementPayload.Bed bed
                && fixture.resident().equals(bed.resident()) && ++ticks >= 5) {
            capture("assigned-home");
            UUID player = minecraft.player.getUUID();
            minecraft.getSingleplayerServer().submit(() -> {
                var serverPlayer = minecraft.getSingleplayerServer().getPlayerList().getPlayer(player);
                var town = SettlementSavedData.get(serverPlayer.serverLevel()).get(fixture.town()).orElseThrow();
                require(town.homes().get(fixture.resident()).bed().equals(bed.position()), "the Beds page assigns the authoritative resident home");
            }).join();
            button(screen, "villagerretaliation.gui.settlement.world_view").onPress();
            phase = 50;
        } else if (phase == 50 && minecraft.screen instanceof SettlementViewScreen screen && visibleResident(minecraft) != null) {
            UUID player = minecraft.player.getUUID();
            minecraft.getSingleplayerServer().submit(() -> {
                var serverPlayer = minecraft.getSingleplayerServer().getPlayerList().getPlayer(player);
                var town = SettlementSavedData.get(serverPlayer.serverLevel()).get(fixture.town()).orElseThrow();
                var resident = (Villager) serverPlayer.serverLevel().getEntity(fixture.resident());
                require(resident.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME)
                        .filter(home -> home.pos().equals(town.homes().get(fixture.resident()).bed())).isPresent(),
                        "the returning native resident receives the assigned home");
                require(SettlementWorkforceService.role(resident) == com.jvn.villagerretaliation.interaction.HiredVillagerRole.COMBAT,
                        "assigning a home preserves the resident's guard job");
            }).join();
            button(screen, "villagerretaliation.gui.settlement_view.overview").onPress();
            phase = 51;
        } else if (phase == 51 && minecraft.screen instanceof SettlementManagementScreen screen) {
            screen.navigateTo(com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.BEDS, "");
            phase = 52;
        } else if (phase == 52 && minecraft.screen instanceof SettlementManagementScreen screen
                && screen.snapshot().page() == com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.BEDS
                && screen.snapshot().entries().size() == 1
                && screen.snapshot().entries().getFirst() instanceof com.jvn.villagerretaliation.network.SettlementManagementPayload.Bed bed
                && fixture.resident().equals(bed.resident())) {
            selectFirstRow(screen);
            button(screen, "villagerretaliation.gui.settlement.release_home").onPress();
            ticks = 0;
            phase = 48;
        } else if (phase == 48 && minecraft.screen instanceof SettlementConfirmScreen screen && ++ticks >= 5) {
            capture("home-release-review");
            button(screen, "gui.yes").onPress();
            phase = 49;
        } else if (phase == 49 && minecraft.screen instanceof SettlementManagementScreen screen
                && screen.snapshot().page() == com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.BEDS
                && screen.snapshot().entries().size() == 1
                && screen.snapshot().entries().getFirst() instanceof com.jvn.villagerretaliation.network.SettlementManagementPayload.Bed bed && bed.available()) {
            UUID player = minecraft.player.getUUID();
            minecraft.getSingleplayerServer().submit(() -> {
                var serverPlayer = minecraft.getSingleplayerServer().getPlayerList().getPlayer(player);
                var town = SettlementSavedData.get(serverPlayer.serverLevel()).get(fixture.town()).orElseThrow();
                require(town.homes().get(fixture.resident()).bed() == null, "the Beds page releases the authoritative assignment");
                var resident = (Villager) serverPlayer.serverLevel().getEntity(fixture.resident());
                require(resident.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME).isEmpty(),
                        "releasing the bed also clears the loaded resident's native home");
            }).join();
            screen.onClose();
            try {
                Files.writeString(Path.of(System.getProperty(OUTPUT), "result.txt"), "Settlement client smoke checks passed\n");
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            LogUtils.getLogger().info("Settlement client smoke checks passed");
            phase = 6;
            minecraft.stop();
        }
    }

    private static com.jvn.villagerretaliation.item.ConstructionBlueprintItem.PreviewData heldBlueprint(Minecraft minecraft) {
        return com.jvn.villagerretaliation.item.ConstructionBlueprintItem.previewData(minecraft.player.getMainHandItem()).orElse(null);
    }

    private static void selectFirstRow(SettlementManagementScreen screen) {
        var rows = screen.children().stream().filter(net.minecraft.client.gui.components.ObjectSelectionList.class::isInstance)
                .map(child -> (net.minecraft.client.gui.components.ObjectSelectionList<?>) child).findFirst().orElseThrow();
        rows.children().getFirst().mouseClicked(0, 0, 0);
    }

    private static UUID prepareBrewer(ServerPlayer player) {
        var level = player.serverLevel();
        var origin = fixture.center().offset(30, 0, 0);
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.BREWERY, "Brewery",
                origin, origin.offset(4, 4, 4), 5, true);
        require(SettlementBuildingService.register(player, fixture.town(), area), "the brewery is registered");
        Villager villager = EntityType.VILLAGER.create(level);
        villager.setPos(origin.getBottomCenter());
        villager.setNoAi(true);
        villager.setCustomName(Component.literal("Juniper"));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.CLERIC));
        VillageAllegianceApi.assignKnown(level, villager, fixture.town(), AllegianceAssignmentSource.EXPLICIT_API);
        require(level.addFreshEntity(villager), "the brewer is created");
        require(SettlementWorkforceService.assign(level, player, fixture.town(), villager, area.id()), "the cleric is assigned to the brewery");
        SettlementCareService.onUnloaded(villager);
        com.jvn.villagerretaliation.settlement.SettlementBackgroundService.onUnloaded(villager);
        SettlementManagementService.open(player, fixture.town());
        return villager.getUUID();
    }

    private static ProjectFixture prepareProject(ServerPlayer player) {
        var level = player.serverLevel();
        var saved = SettlementSavedData.get(level).save(new net.minecraft.nbt.CompoundTag(), level.registryAccess());
        for (var raw : saved.getList("Settlements", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            var entry = (net.minecraft.nbt.CompoundTag) raw;
            if (entry.getUUID("Id").equals(fixture.town().value())) {
                entry.putString("Stage", "HAMLET");
            }
        }
        level.getServer().overworld().getDataStorage().set("villagerretaliation_settlements", SettlementSavedData.load(saved, level.registryAccess()));
        var origin = fixture.center().offset(12, 0, 0);
        for (BlockPos position : BlockPos.betweenClosed(origin.offset(-3, -1, -4), origin.offset(14, 12, 12))) {
            level.setBlockAndUpdate(position, position.getY() == origin.getY() - 1
                    ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.CONSTRUCTION, "Second house",
                origin.offset(-3, -1, -4), origin.offset(14, 12, 12), 5, true);
        require(SettlementBuildingService.register(player, fixture.town(), area), "the construction area is registered");
        Villager builder = EntityType.VILLAGER.create(level);
        builder.setPos(origin.offset(-2, 0, 2).getBottomCenter());
        builder.setNoAi(true);
        builder.setCustomName(Component.literal("Rowan"));
        builder.setVillagerData(builder.getVillagerData().setProfession(VillagerProfession.MASON));
        VillageAllegianceApi.assignKnown(level, builder, fixture.town(), AllegianceAssignmentSource.EXPLICIT_API);
        require(level.getEntity(builder.getUUID()) == null, "the civic builder remains unloaded");
        require(SettlementWorkforceService.assign(level, player, fixture.town(), builder, area.id()), "the builder is assigned to construction");
        SettlementCareService.onUnloaded(builder);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER));
        player.containerMenu.broadcastChanges();
        return new ProjectFixture(builder.getUUID(), null);
    }

    private static Fixture prepare(ServerPlayer player) {
        var level = player.serverLevel();
        player.setGameMode(GameType.SURVIVAL);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
        level.setDayTime(6_000L);
        BlockPos center = new BlockPos(1024, -60, 1024);
        for (BlockPos position : BlockPos.betweenClosed(center.offset(-12, -1, -10), center.offset(12, 6, 10))) {
            level.setBlockAndUpdate(position, position.getY() == center.getY() - 1
                    ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        for (BlockPos position : BlockPos.betweenClosed(center.offset(-24, -1, 12), center.offset(-12, 12, 24))) {
            int top = center.getY() - 1 + (position.getX() - center.getX() + 24) / 2;
            if (position.getZ() >= center.getZ() + 20) top = center.getY() + 12;
            level.setBlockAndUpdate(position, position.getY() <= top ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        for (BlockPos position : BlockPos.betweenClosed(center.offset(-8, -1, -3), center.offset(-3, -1, 3))) {
            level.setBlockAndUpdate(position, Blocks.OAK_PLANKS.defaultBlockState());
        }
        for (BlockPos position : BlockPos.betweenClosed(center.offset(-8, 0, -3), center.offset(-3, 3, 3))) {
            if (position.getX() == center.getX() - 8 || position.getX() == center.getX() - 3
                    || position.getZ() == center.getZ() - 3 || position.getZ() == center.getZ() + 3) {
                level.setBlockAndUpdate(position, Blocks.OAK_PLANKS.defaultBlockState());
            }
        }
        for (BlockPos position : BlockPos.betweenClosed(center.offset(-9, 4, -4), center.offset(-2, 4, 4))) {
            level.setBlockAndUpdate(position, Blocks.OAK_SLAB.defaultBlockState());
        }
        level.setBlockAndUpdate(center.offset(-3, 0, 0), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(center.offset(-3, 1, 0), Blocks.AIR.defaultBlockState());
        BlockPos bed = center.offset(-6, 0, 0);
        level.setBlockAndUpdate(bed, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT));
        level.setBlockAndUpdate(bed.north(), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD));
        level.setBlockAndUpdate(center, Blocks.CRAFTING_TABLE.defaultBlockState());
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "Willowbank");
        SettlementSavedData.get(level).establish(registry, id, player.getUUID(), center, level.getGameTime()).orElseThrow();
        require(SettlementBuildingService.register(player, id, new SettlementArea(UUID.randomUUID(), SettlementAreaType.HOME,
                "First house", center.offset(-9, -1, -4), center.offset(-2, 5, 4), 5, true)), "the test house is registered");
        Villager resident = EntityType.VILLAGER.create(level);
        resident.setPos(center.offset(2, 0, 1).getCenter().subtract(0, 0.5D, 0));
        resident.setNoAi(true);
        resident.setCustomName(Component.literal("Tamsin"));
        resident.setVillagerData(resident.getVillagerData().setProfession(VillagerProfession.WEAPONSMITH));
        VillageAllegianceApi.assignKnown(level, resident, id, AllegianceAssignmentSource.EXPLICIT_API);
        level.addFreshEntity(resident);
        var post = new SettlementArea(UUID.randomUUID(), SettlementAreaType.GUARD_POST, "Guard post",
                center.offset(0, -1, -2), center.offset(5, 3, 4), 5, true);
        require(SettlementBuildingService.register(player, id, post), "the guard post is registered");
        require(SettlementBuildingService.register(player, id, new SettlementArea(UUID.randomUUID(), SettlementAreaType.WORKSHOP,
                "Shared workshop", post.min(), post.max(), 5, true)), "a workshop can share the guard post");
        require(SettlementWorkforceService.assign(level, player, id, resident, post.id()), "the resident is a civic guard");
        SettlementCareService.onUnloaded(resident);
        SettlementManagementService.open(player, id);
        return new Fixture(id, resident.getUUID(), center);
    }

    @SubscribeEvent
    public static void onResidentRendered(RenderLivingEvent.Post<?, ?> event) {
        if (System.getProperty(OUTPUT) == null || phase != 60 || fixture == null
                || !event.getEntity().getUUID().equals(fixture.resident())) return;
        var minecraft = Minecraft.getInstance();
        var camera = minecraft.gameRenderer.getMainCamera().getPosition();
        if (minecraft.screen instanceof SettlementViewScreen screen && screen.viewHeight() == SettlementViewWindow.MAX_HEIGHT
                && !event.getEntity().shouldRender(camera.x, camera.y, camera.z)) maxZoomResidentRendered = true;
    }

    private static int renderBedVertices(BlockEntity bed) {
        try (var memory = new ByteBufferBuilder(4_096)) {
            var buffer = new BufferBuilder(memory, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
            Minecraft.getInstance().getBlockEntityRenderDispatcher().render(bed, 0.0F, new PoseStack(), type -> buffer);
            try (var mesh = buffer.build()) {
                return mesh == null ? 0 : mesh.drawState().vertexCount();
            }
        }
    }

    private static Frustum visibleFrustum() {
        return new Frustum(new Matrix4f(), new Matrix4f()) {
            @Override
            public boolean isVisible(AABB bounds) {
                return true;
            }
        };
    }

    private static void clickWorld(SettlementViewScreen screen, Vec3 position) {
        clickWorld(screen, position, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private static void clickWorld(SettlementViewScreen screen, Vec3 position, int button) {
        var point = SettlementViewGizmo.project(Minecraft.getInstance().gameRenderer.getMainCamera(), screen.width, screen.height, position);
        require(point != null, "the world target is in front of the camera");
        screen.mouseClicked(point.x(), point.y(), button);
        screen.mouseReleased(point.x(), point.y(), button);
    }

    private static void drawWorld(SettlementViewScreen screen, Vec3 start, Vec3 end) {
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        var first = SettlementViewGizmo.project(camera, screen.width, screen.height, start);
        var last = SettlementViewGizmo.project(camera, screen.width, screen.height, end);
        require(first != null && last != null, "drawing targets are visible");
        screen.mouseClicked(first.x(), first.y(), GLFW.GLFW_MOUSE_BUTTON_LEFT);
        screen.mouseDragged(last.x(), last.y(), GLFW.GLFW_MOUSE_BUTTON_LEFT, last.x() - first.x(), last.y() - first.y());
        screen.mouseReleased(last.x(), last.y(), GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private static void clickPopup(SettlementViewScreen screen, String label) {
        var popup = screen.contextPopup();
        require(popup != null, "the context popup is open for " + label);
        int index = -1;
        for (int item = 0; item < popup.buttons().size(); item++) {
            if (popup.buttons().get(item).getMessage().getString().startsWith(label)) index = item;
        }
        require(index >= 0, "the popup offers " + label);
        for (int item = 0; item <= index; item++) popup.key(GLFW.GLFW_KEY_DOWN);
        var button = popup.buttons().get(index);
        require(button.active, "the popup action is enabled for " + label);
        screen.mouseClicked(button.getX() + button.getWidth() / 2.0D, button.getY() + 10, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private static net.minecraft.client.gui.components.EditBox field(SettlementEditScreen screen, String key) {
        String label = Component.translatable("villagerretaliation.gui.settlement.field." + key).getString();
        return screen.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                .map(net.minecraft.client.gui.components.EditBox.class::cast)
                .filter(field -> field.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }

    private static String coordinates(BlockPos position) {
        return position.getX() + " " + position.getY() + " " + position.getZ();
    }

    private static com.jvn.villagerretaliation.settlement.SettlementRecord serverTown() {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.getSingleplayerServer().getPlayerList().getPlayer(minecraft.player.getUUID());
        return SettlementSavedData.get(player.serverLevel()).get(fixture.town()).orElseThrow();
    }

    private static SettlementArea serverArea(UUID id) {
        return Minecraft.getInstance().getSingleplayerServer().submit(() -> serverTown().areas().get(id)).join();
    }

    private static int serverAreaCount() {
        return Minecraft.getInstance().getSingleplayerServer().submit(() -> serverTown().areas().size()).join();
    }

    private static void verifyGizmo(SettlementViewScreen screen) {
        var before = screen.draftBounds();
        for (Direction.Axis axis : Direction.Axis.values()) {
            var handle = new SettlementViewGizmo.Handle(axis, null);
            dragGizmo(screen, handle, 2, false);
            dragGizmo(screen, handle, -2, false);
            require(screen.draftBounds().equals(before), "each translation axis can return to its starting bounds");
        }
        for (Direction face : Direction.values()) {
            var handle = new SettlementViewGizmo.Handle(face.getAxis(), face);
            int sign = face.getAxisDirection().getStep();
            dragGizmo(screen, handle, sign, false);
            dragGizmo(screen, handle, -sign, false);
            require(screen.draftBounds().equals(before), "each face can resize outward and back");
        }
        dragGizmo(screen, new SettlementViewGizmo.Handle(Direction.Axis.X, null), 2, true);
        require(screen.draftBounds().equals(before), "Escape during a gizmo drag restores its starting bounds");
    }

    private static void dragGizmo(SettlementViewScreen screen, SettlementViewGizmo.Handle handle, int blocks, boolean cancel) {
        var minecraft = Minecraft.getInstance();
        var camera = minecraft.gameRenderer.getMainCamera();
        var before = screen.draftBounds();
        int viewportWidth = screen.width - Math.clamp(screen.width / 3, 128, 200);
        var target = screen.gizmo().targets(camera, screen.width, screen.height, viewportWidth, before).stream()
                .filter(item -> item.handle().equals(handle)).findFirst()
                .orElseThrow(() -> new IllegalStateException("No visible gizmo target for " + handle + " on " + before
                        + " from " + camera.getPosition() + ", yaw=" + camera.getYRot() + ", pitch=" + camera.getXRot()));
        var start = target.point();
        var hit = screen.gizmo().hit(camera, screen.width, screen.height, viewportWidth, before, start.x(), start.y());
        require(hit != null && hit.handle().equals(handle), "the visible handle picks its own transform");
        if (handle.face() == null) {
            for (double fraction : new double[]{0.55D, 0.7D, 0.35D}) {
                var point = new SettlementViewGizmo.Point(target.origin().x() + (start.x() - target.origin().x()) * fraction,
                        target.origin().y() + (start.y() - target.origin().y()) * fraction);
                var shaft = screen.gizmo().hit(camera, screen.width, screen.height, viewportWidth, before, point.x(), point.y());
                if (shaft != null && shaft.handle().equals(handle)) {
                    start = point;
                    break;
                }
            }
            require(!start.equals(target.point()), "translation arrows can be dragged by their shafts");
        }
        if (handle.face() != null) {
            var projected = SettlementViewGizmo.project(camera, screen.width, screen.height, target.anchor());
            require(projected != null && projected.distance(start) < 0.01D,
                    "resize handles remain attached to their actual faces");
        }
        require(start.x() >= 0 && start.x() < viewportWidth && start.y() >= 0 && start.y() < screen.height,
                "every gizmo handle has a usable world-view target");
        var ray = SettlementViewGizmo.ray(camera, screen.width, screen.height, start.x(), start.y());
        double scalar = SettlementViewGizmo.coordinate(ray, target.anchor(), handle.axis());
        Vec3 axis = new Vec3(handle.axis() == Direction.Axis.X ? 1 : 0, handle.axis() == Direction.Axis.Y ? 1 : 0,
                handle.axis() == Direction.Axis.Z ? 1 : 0);
        var pixels = SettlementViewGizmo.pixels(camera, screen.width, screen.height, target.anchor(), handle.axis());
        var end = !SettlementViewGizmo.screenDrag(camera, ray, handle.axis())
                ? SettlementViewGizmo.project(camera, screen.width, screen.height, target.anchor().add(axis.scale(scalar + blocks)))
                : new SettlementViewGizmo.Point(start.x() + pixels.x() * blocks, start.y() + pixels.y() * blocks);
        require(end != null, "the drag ray projects its exact constrained axis");
        screen.mouseClicked(start.x(), start.y(), GLFW.GLFW_MOUSE_BUTTON_LEFT);
        require(screen.gizmo().dragging(), "the gizmo takes priority over residents and areas");
        screen.mouseDragged(end.x(), end.y(), GLFW.GLFW_MOUSE_BUTTON_LEFT, end.x() - start.x(), end.y() - start.y());
        var header = screen.parent().snapshot().header();
        var limits = new SettlementViewBounds.Limits(header.center(), header.radius(), minecraft.level.getMinBuildHeight(), minecraft.level.getMaxBuildHeight() - 1);
        var expected = handle.face() == null ? before.translate(handle.axis(), blocks, limits) : before.resize(handle.face(), blocks, limits);
        require(screen.draftBounds().equals(expected), "ray dragging applies the exact block transform for " + handle);
        if (cancel) screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        screen.mouseReleased(end.x(), end.y(), GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private static void verifyPopupScales(Minecraft minecraft) {
        for (int scale : new int[]{2, 3, 4}) {
            int width = 1280 / scale;
            int height = 720 / scale;
            var popup = new SettlementViewPopup(SettlementViewPopup.Target.of(SettlementViewPopup.Kind.RESIDENT, fixture.resident()), width - 2, height - 2);
            var lines = java.util.Collections.<Component>nCopies(12, Component.literal("Resident details at the viewport edge"));
            var actions = java.util.Collections.nCopies(5, new SettlementViewPopup.Action(Component.literal("Manage"), true, () -> {}));
            popup.update(minecraft.font, width, height, lines, actions);
            require(popup.contains(width - 5, height - 5), "context popups stay inside the viewport at GUI scale " + scale);
            for (int item = 0; item < actions.size(); item++) popup.key(GLFW.GLFW_KEY_DOWN);
            var last = popup.buttons().getLast();
            require(last.getY() >= 0 && last.getY() + 20 <= height, "keyboard navigation reveals scrolled actions at every scale");
        }
    }

    private static void verifyTerrainFollowing(SettlementViewScreen screen) {
        var minecraft = Minecraft.getInstance();
        var header = screen.parent().snapshot().header();
        var window = new SettlementViewWindow(header.center(), header.radius() + SettlementViewWindow.BORDER,
                screen.focusX(), screen.focusZ(), 8, java.util.List.of(new ChunkPos(header.center())));
        SettlementViewCamera.Heights heights = (x, z) -> {
            var chunk = minecraft.level.getChunkSource().getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16), false);
            return chunk == null ? Double.NaN : chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z) + 1.0D;
        };
        var camera = new SettlementViewCamera();
        double x = fixture.center().getX() - 24 + 0.5D;
        double z = fixture.center().getZ() + 16.5D;
        var previous = camera.position(window, x, z, 8, 88, heights, 0);
        double start = previous.position().y;
        for (int step = 1; step <= 60; step++) {
            var next = camera.position(window, x + step * 0.2D, z, 8, 88, heights, 0.02D);
            require(Math.abs(next.position().y - previous.position().y) < 1.0D, "the camera follows a real slope continuously");
            previous = next;
        }
        require(previous.position().y > start + 3, "terrain following tracks the slope's elevation");
        double beforeCliff = previous.position().y;
        var cliff = camera.position(window, x + 12, z + 5, 8, 88, heights, 0.02D);
        require(cliff.position().y > beforeCliff && cliff.position().y < beforeCliff + 2, "focus height eases across a real cliff");
        var roofCamera = new SettlementViewCamera();
        var roof = roofCamera.position(window, fixture.center().getX() - 12.5D, fixture.center().getZ() + 8.5D, 8, 88, heights, 0);
        double surface = heights.sample((int) Math.floor(roof.position().x), (int) Math.floor(roof.position().z));
        require(roof.position().y >= surface + 4, "the camera clears a real roof by four blocks");
        var loading = roofCamera.position(window, fixture.center().getX() - 12.5D, fixture.center().getZ() + 8.5D, 8, 88,
                (bx, bz) -> Double.NaN, 0.05D);
        require(Math.abs(loading.position().y - roof.position().y) < 0.001D, "loading boundaries retain the last valid camera elevation");
        for (int distance : new int[]{2, 6, 12}) {
            var constrained = new SettlementViewCamera().position(window, screen.focusX(), screen.focusZ(), 64,
                    distance * 16 - 8, heights, 0);
            require(Float.isFinite(constrained.pitch()) && constrained.pitch() >= SettlementViewCamera.PITCH,
                    "camera geometry remains finite at low and high render distances");
        }
    }

    private static Entity visibleResident(Minecraft minecraft) {
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity.getUUID().equals(fixture.resident())) {
                return entity;
            }
        }
        return null;
    }

    private static Button button(net.minecraft.client.gui.screens.Screen screen, String translation) {
        String label = Component.translatable(translation).getString();
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }

    private static int capture(String name) {
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            image.writeToFile(Path.of(System.getProperty(OUTPUT), name + ".png"));
            int terrainPixels = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth() * 2 / 3; x++) {
                    int color = image.getPixelRGBA(x, y);
                    int red = color & 255;
                    int green = color >>> 8 & 255;
                    int blue = color >>> 16 & 255;
                    if (green > 70 && green > red && green > blue) {
                        terrainPixels++;
                    }
                }
            }
            return terrainPixels;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void verifySodiumChunk(ChunkPos position, boolean expected) {
        if (!net.neoforged.fml.ModList.get().isLoaded("sodium")) {
            return;
        }
        try {
            Class<?> holder = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder");
            Object tracker = holder.getMethod("get", net.minecraft.client.multiplayer.ClientLevel.class)
                    .invoke(null, Minecraft.getInstance().level);
            var chunks = (it.unimi.dsi.fastutil.longs.LongCollection) tracker.getClass().getMethod("getReadyChunks").invoke(tracker);
            require(chunks.contains(position.toLong()) == expected,
                    expected ? "Sodium tracks the remote settlement terrain" : "leaving releases Sodium's remote settlement terrain");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not inspect Sodium's settlement chunk lifecycle", exception);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            if (Minecraft.getInstance().isSameThread()) capture("failure");
            throw new IllegalStateException(message);
        }
    }

    private record ProjectFixture(UUID builder, UUID project) {
    }

    private record Fixture(VillageAllegianceId town, UUID resident, BlockPos center) {
    }
}
