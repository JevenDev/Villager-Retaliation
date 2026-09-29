package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.settlement.SettlementArea;
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
    private static com.jvn.villagerretaliation.network.SettlementManagementPayload.Project projectSnapshot;
    private static UUID brewer;
    private static Villager issueFarmer;
    private static long started;
    private static int phase;
    private static int ticks;
    private static double originalX;
    private static double originalZ;
    private static float originalHeight;
    private static float originalYaw;
    private static String originalAreaNarration;
    private static int panDirection;
    private static int plotCount;
    private static int plotCredits;
    private static com.jvn.villagerretaliation.sell.CurrencyAmount plotBalance;
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
        minecraft.setWindowActive(true);
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
            minecraft.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
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
                minecraft.options.broadcastOptions();
            }
            if (screen.viewHeight() != SettlementViewWindow.MAX_HEIGHT) screen.mouseScrolled(40, 80, 0, -100);
            if (ticks < 120 && (screen.viewHeight() != SettlementViewWindow.MAX_HEIGHT
                    || Math.abs(screen.cameraHeight() - screen.viewHeight()) >= 0.1D
                    || !maxZoomResidentRendered || !screen.terrainLoaded())) return;
            require(screen.viewHeight() == SettlementViewWindow.MAX_HEIGHT, "the rendering check reaches maximum town zoom");
            require(Math.abs(screen.cameraHeight() - screen.viewHeight()) < 0.1D, "the camera eases into maximum zoom");
            require(screen.terrainLoaded(), "the maximum zoom capture includes the streamed terrain");
            require(!screen.getNarrationMessage().getString().contains("Remote terrain queued:"),
                    "server progress clears the remote terrain queue message");
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
            screen.keyPressed(GLFW.GLFW_KEY_E, 0, 0);
            phase = 90;
        } else if (phase == 90 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 10) {
            screen.keyReleased(GLFW.GLFW_KEY_E, 0, 0);
            require(Math.abs(screen.cameraYaw() - originalYaw) > 0.5F, "E rotates the world camera");
            capture("camera-rotation");
            screen.rotateCamera(originalYaw - screen.cameraYaw());
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
            verifyViewInteractions(screen);
            Vec3 table = fixture.center().getCenter().add(0.0D, 0.5D, 0.0D);
            clickWorld(screen, table);
            require(screen.contextPopup() != null && screen.contextPopup().target().kind() == SettlementViewPopup.Kind.CHOOSER,
                    "overlapping building uses open a choice menu");
            require(screen.contextPopup().buttons().size() == 2, "the menu includes both shared designations");
            ticks = 0;
            phase = 91;
        } else if (phase == 91 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 5) {
            capture("shared-use-choice");
            clickContextAction(screen, 0);
            require(screen.contextPopup().target().kind() == SettlementViewPopup.Kind.AREA, "choosing an area opens its context actions");
            originalAreaNarration = screen.getNarrationMessage().getString();
            require(originalAreaNarration.contains("Guard post") || originalAreaNarration.contains("Shared workshop"),
                    "the first designation is selected");
            ticks = 0;
            phase = 92;
        } else if (phase == 92 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 5) {
            require(screen.contextPopup().buttons().stream().anyMatch(action -> action.getMessage().getString().equals(
                    Component.translatable("villagerretaliation.gui.settlement_view.edit_bounds").getString())),
                    "area context actions include bounds editing");
            capture("area-context");
            screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            clickWorld(screen, fixture.center().getCenter().add(0.0D, 0.5D, 0.0D));
            ticks = 0;
            phase = 93;
        } else if (phase == 93 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 5) {
            clickContextAction(screen, 1);
            ticks = 0;
            phase = 94;
        } else if (phase == 94 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 5) {
            require(!screen.getNarrationMessage().getString().equals(originalAreaNarration),
                    "the menu can select the other use of the same building");
            screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            require(minecraft.screen == screen && screen.contextPopup() == null, "Escape dismisses context actions and keeps the camera session");
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
                clickWorld(screen, resident.getBoundingBox().getCenter());
            }
            if (ticks >= 40 && screen.getNarrationMessage().getString().contains("Tamsin")) {
                require(button(screen, "villagerretaliation.gui.settlement_view.manage_selection").active,
                        "selecting the resident enables its management action");
                capture("selected-resident");
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
            require(screen.snapshot().header() != null, "Summary includes the current settlement assessment");
            require(screen.snapshot().header().populations().size() == 5 && screen.snapshot().header().populations().get(4) == 75,
                    "Summary receives effective stage populations from the server");
            require(screen.snapshot().header().growthTarget() == 100, "Summary receives the natural growth cap");
            capture("issues-summary");
            requireButtonsFit(screen);
            button(screen, "villagerretaliation.gui.settlement.page.issues").onPress();
            ticks = 0;
            phase = 80;
        } else if (phase == 80 && minecraft.screen instanceof SettlementManagementScreen screen
                && screen.snapshot().page() == com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.ISSUES) {
            if (++ticks < 5) return;
            require(screen.snapshot().total() >= screen.snapshot().header().issues(), "the Issues page also includes nonblocking progression information");
            requireButtonsFit(screen);
            capture("issues-progression");
            UUID player = minecraft.player.getUUID();
            issueFarmer = minecraft.getSingleplayerServer().submit(() -> prepareIssueFarmer(
                    minecraft.getSingleplayerServer().getPlayerList().getPlayer(player))).join();
            screen.navigateTo(com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.ISSUES, issueFarmer.getUUID().toString());
            ticks = 0;
            phase = 81;
        } else if (phase == 81 && minecraft.screen instanceof SettlementManagementScreen screen
                && screen.snapshot().page() == com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.ISSUES
                && screen.snapshot().entries().size() == 1
                && screen.snapshot().entries().getFirst() instanceof com.jvn.villagerretaliation.network.SettlementManagementPayload.Issue issue
                && issue.resident() != null && issue.resident().equals(issueFarmer.getUUID())) {
            selectFirstRow(screen);
            require(button(screen, "villagerretaliation.gui.settlement.pause_worker").active, "a blocked worker exposes quick pause");
            requireButtonsFit(screen);
            if (++ticks < 5) return;
            capture("issues-worker");
            button(screen, "villagerretaliation.gui.settlement.worker_settings").onPress();
            ticks = 0;
            phase = 82;
        } else if (phase == 82 && minecraft.screen instanceof SettlementWorkerScreen screen && ++ticks >= 5) {
            require(screen.getTitle().getString().contains("River"), "issue settings open the correct fresh resident");
            screen.onClose();
            ticks = 0;
            phase = 83;
        } else if (phase == 83 && minecraft.screen instanceof SettlementManagementScreen screen) {
            if (++ticks < 5) return;
            requireButtonsFit(screen);
            capture("issues-resident-controls");
            screen.navigateTo(com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.ISSUES, issueFarmer.getUUID().toString());
            phase = 89;
        } else if (phase == 89 && minecraft.screen instanceof SettlementManagementScreen screen
                && screen.snapshot().page() == com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.ISSUES
                && screen.snapshot().entries().size() == 1) {
            selectFirstRow(screen);
            button(screen, "villagerretaliation.gui.settlement.pause_worker").onPress();
            ticks = 0;
            phase = 84;
        } else if (phase == 84 && minecraft.screen instanceof SettlementManagementScreen screen && !screen.submitting()
                && screen.snapshot().entries().getFirst() instanceof com.jvn.villagerretaliation.network.SettlementManagementPayload.Resident resident
                && resident.settings() != null && !resident.settings().enabled()) {
            if (++ticks < 8) return;
            button(screen, "villagerretaliation.gui.settlement.resume_worker").onPress();
            phase = 85;
        } else if (phase == 85 && minecraft.screen instanceof SettlementManagementScreen screen && !screen.submitting()
                && screen.snapshot().entries().getFirst() instanceof com.jvn.villagerretaliation.network.SettlementManagementPayload.Resident resident
                && resident.settings() != null && resident.settings().enabled()) {
            minecraft.getSingleplayerServer().submit(() -> {
                com.jvn.villagerretaliation.settlement.SettlementBackgroundService.onActive(issueFarmer);
                blockIssueFarmer();
            }).join();
            screen.navigateTo(com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.ISSUES, issueFarmer.getUUID().toString());
            phase = 86;
        } else if (phase == 86 && minecraft.screen instanceof SettlementManagementScreen screen
                && screen.snapshot().page() == com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.ISSUES
                && screen.snapshot().entries().size() == 1) {
            selectFirstRow(screen);
            button(screen, "villagerretaliation.gui.settlement.assign").onPress();
            phase = 87;
        } else if (phase == 87 && minecraft.screen instanceof SettlementManagementScreen screen
                && screen.snapshot().page() == com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.AREAS) {
            requireButtonsFit(screen);
            capture("issues-reassignment");
            screen.onClose();
            phase = 88;
        } else if (phase == 88 && minecraft.screen instanceof SettlementManagementScreen screen) {
            UUID player = minecraft.player.getUUID();
            minecraft.getSingleplayerServer().submit(() -> {
                var serverPlayer = minecraft.getSingleplayerServer().getPlayerList().getPlayer(player);
                var data = SettlementSavedData.get(serverPlayer.serverLevel());
                var area = data.get(fixture.town()).orElseThrow().duties().get(issueFarmer.getUUID());
                require(SettlementWorkforceService.release(serverPlayer.serverLevel(), serverPlayer, fixture.town(), issueFarmer.getUUID()),
                        "the issue fixture can be released through existing staffing controls");
                require(data.removeArea(VillageAllegianceRegistrySavedData.get(serverPlayer.serverLevel()), fixture.town(), serverPlayer.getUUID(), area),
                        "the issue fixture releases its farm before construction checks");
            }).join();
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
            screen.navigateTo(com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.AREAS, "Guard post");
            phase = 95;
        } else if (phase == 95 && minecraft.screen instanceof SettlementManagementScreen screen
                && screen.snapshot().page() == com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.AREAS
                && screen.snapshot().entries().size() == 1) {
            var rows = screen.children().stream().filter(net.minecraft.client.gui.components.ObjectSelectionList.class::isInstance)
                    .map(child -> (net.minecraft.client.gui.components.ObjectSelectionList<?>) child).findFirst().orElseThrow();
            rows.children().getFirst().mouseClicked(0, 0, 0);
            requireButtonsFit(screen);
            button(screen, "villagerretaliation.gui.settlement.worksite_settings").onPress();
            phase = 96;
        } else if (phase == 96 && minecraft.screen instanceof SettlementWorkerScreen screen) {
            button(screen, "villagerretaliation.gui.settlement.worksite_links").onPress();
            phase = 101;
        } else if (phase == 101 && minecraft.screen instanceof SettlementWorksiteLinksScreen screen
                && screen.parent().parent().snapshot().page() == com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.DEPOTS) {
            requireButtonsFit(screen);
            capture("worksite-storage-links");
            button(screen, "villagerretaliation.gui.settlement.worksite_stations").onPress();
            phase = 102;
        } else if (phase == 102 && minecraft.screen instanceof SettlementWorksiteLinksScreen screen
                && screen.parent().parent().snapshot().page() == com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.STATIONS) {
            requireButtonsFit(screen);
            capture("worksite-station-links");
            screen.onClose();
            phase = 103;
        } else if (phase == 103 && minecraft.screen instanceof SettlementWorkerScreen screen) {
            requireButtonsFit(screen);
            screen.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                    .map(net.minecraft.client.gui.components.EditBox.class::cast).filter(box -> box.visible).findFirst().orElseThrow().setValue("2");
            capture("worksite-adoption");
            require(button(screen, "villagerretaliation.gui.settlement.worksite_adopt").active, "a valid plan can be adopted");
            button(screen, "villagerretaliation.gui.settlement.worksite_adopt").onPress();
            ticks = 0;
            phase = 97;
        } else if (phase == 97 && minecraft.screen instanceof SettlementManagementScreen screen && ++ticks >= 10) {
            UUID player = minecraft.player.getUUID();
            minecraft.getSingleplayerServer().submit(() -> {
                var serverPlayer = minecraft.getSingleplayerServer().getPlayerList().getPlayer(player);
                var town = SettlementSavedData.get(serverPlayer.serverLevel()).get(fixture.town()).orElseThrow();
                var plan = town.worksitePlans().get(town.duties().get(fixture.resident()));
                require(plan != null && plan.desiredStaff() == 2, "the worksite form saves its staffing target");
                require(plan.settings().enabled() && !town.effectiveWorkerSettings(fixture.resident()).enabled(),
                        "adopting the plan preserves the paused resident's override");
            }).join();
            screen.navigateTo(com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.RESIDENTS, fixture.resident().toString());
            phase = 98;
        } else if (phase == 98 && minecraft.screen instanceof SettlementManagementScreen screen
                && screen.snapshot().page() == com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.RESIDENTS
                && screen.snapshot().entries().size() == 1) {
            var rows = screen.children().stream().filter(net.minecraft.client.gui.components.ObjectSelectionList.class::isInstance)
                    .map(child -> (net.minecraft.client.gui.components.ObjectSelectionList<?>) child).findFirst().orElseThrow();
            rows.children().getFirst().mouseClicked(0, 0, 0);
            button(screen, "villagerretaliation.gui.settlement.worker_settings").onPress();
            phase = 99;
        } else if (phase == 99 && minecraft.screen instanceof SettlementWorkerScreen screen) {
            requireButtonsFit(screen);
            button(screen, "villagerretaliation.gui.settlement.worksite_reset").onPress();
            require(button(screen, "villagerretaliation.gui.settlement.save").active, "resetting overrides enables Save");
            capture("worksite-inheritance");
            screen.onClose();
            phase = 100;
        } else if (phase == 100 && minecraft.screen instanceof SettlementManagementScreen screen) {
            for (var kind : java.util.List.of(SettlementAreaType.FARM, SettlementAreaType.ANIMAL_PEN)) {
                var area = new com.jvn.villagerretaliation.network.SettlementManagementPayload.Area(
                        UUID.randomUUID(), "Resource worksite", kind, fixture.center(), fixture.center().offset(3, 2, 3),
                        5, true, 0, 0, 0);
                var form = new SettlementWorkerScreen(screen, area, null);
                form.init(minecraft, screen.width, screen.height);
                requireButtonsFit(form);
            }
            UUID player = minecraft.player.getUUID();
            projectFixture = minecraft.getSingleplayerServer().submit(() -> prepareProject(
                    minecraft.getSingleplayerServer().getPlayerList().getPlayer(player))).join();
            button(screen, "villagerretaliation.gui.settlement.world_view").onPress();
            ticks = 0;
            phase = 14;
        } else if (phase == 14 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 30) {
            clickWorld(screen, fixture.center().getCenter().add(0, 0.5D, 0), GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            require(screen.contextPopup() != null, "the shared worksite has context actions");
            if (screen.contextPopup().target().kind() == SettlementViewPopup.Kind.CHOOSER) {
                screen.contextPopup().buttons().stream().filter(button -> button.getMessage().getString().contains("Guard post"))
                        .findFirst().orElseThrow().onPress();
            }
            screen.contextPopup().buttons().stream().filter(button -> button.getMessage().getString().equals("Set shared patrol"))
                    .findFirst().orElseThrow().onPress();
            clickWorld(screen, fixture.center().offset(6, 0, 6).getBottomCenter());
            clickWorld(screen, fixture.center().offset(9, 0, 6).getBottomCenter());
            ticks = 0;
            phase = 125;
        } else if (phase == 125 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 5) {
            capture("shared-worksite-patrol");
            screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            ticks = 0;
            phase = 126;
        } else if (phase == 126 && minecraft.screen instanceof SettlementViewScreen screen && !screen.parent().submitting() && ++ticks >= 15) {
            UUID player = minecraft.player.getUUID();
            minecraft.getSingleplayerServer().submit(() -> {
                var serverPlayer = minecraft.getSingleplayerServer().getPlayerList().getPlayer(player);
                var town = SettlementSavedData.get(serverPlayer.serverLevel()).get(fixture.town()).orElseThrow();
                var plan = town.worksitePlans().get(town.duties().get(fixture.resident()));
                require(plan.route() != null && plan.route().mode() == SettlementGuardOrder.Mode.PATROL && plan.route().points().size() == 2,
                        "drawing and Enter save the shared worksite route");
            }).join();
            require(minecraft.getCameraEntity() != minecraft.player, "saving the route preserves the map camera");
            var targets = java.util.List.of(new com.jvn.villagerretaliation.network.SettlementViewBatchPayload.Target(fixture.resident(), null));
            var form = new SettlementTemporaryTaskScreen(screen, targets,
                    com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask.Kind.FARM_PATCH,
                    fixture.center(), fixture.center().offset(3, 2, 3), true);
            SettlementViewClient.openPanels(screen);
            minecraft.setScreen(form);
            phase = 107;
        } else if (phase == 107 && minecraft.screen instanceof SettlementTemporaryTaskScreen screen) {
            requireButtonsFit(screen);
            var personal = new com.jvn.villagerretaliation.client.inventory.ClipboardFishingTaskScreen(screen, fixture.resident(), true);
            personal.init(minecraft, screen.width, screen.height);
            requireButtonsFit(personal);
            capture("temporary-task-picker");
            button(screen, "Work farm patch").onPress();
            button(screen, "Excavate area").onPress();
            button(screen, "Catch fish").onPress();
            require(screen.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                    .map(net.minecraft.client.gui.components.EditBox.class::cast).noneMatch(field -> field.active),
                    "selected trees use server-derived bounds and no catch quantity");
            ticks = 0;
            phase = 109;
        } else if (phase == 109 && minecraft.screen instanceof SettlementTemporaryTaskScreen screen && ++ticks >= 2) {
            capture("selected-tree-picker");
            button(screen, "Harvest selected tree").onPress();
            require(screen.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                    .map(net.minecraft.client.gui.components.EditBox.class::cast).noneMatch(field -> field.active), "pursuits use server-derived bounds");
            button(screen, "Apply to 1 worker").onPress();
            require(minecraft.screen == screen, "an entity task without a selected entity stays open");
            screen.onClose();
            ticks = 0;
            phase = 108;
        } else if (phase == 108 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 10) {
            require(minecraft.getCameraEntity() != minecraft.player, "closing the task picker preserves the settlement camera");
            button(screen, "villagerretaliation.gui.settlement_view.draw_area").onPress();
            dragWorld(screen, Vec3.atBottomCenterOf(fixture.center().offset(7, 0, 7)),
                    Vec3.atBottomCenterOf(fixture.center().offset(9, 0, 9)));
            require(minecraft.screen == screen && screen.selectionGizmo() != null, "drawing opens area adjustments before settings");
            button(screen, "villagerretaliation.gui.settlement_view.gizmo_resize").onPress();
            var bounds = screen.selectionGizmo().bounds();
            var face = new Vec3(bounds.maxX, bounds.getCenter().y, bounds.getCenter().z);
            dragWorld(screen, face, face.add(2.4, 0, 0));
            require(screen.selectionGizmo().bounds().minX == bounds.minX
                    && screen.selectionGizmo().bounds().maxX == bounds.maxX + 2, "resizing snaps one boundary and preserves the opposite face");
            ticks = 0;
            phase = 71;
        } else if (phase == 71 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 5) {
            capture("area-resize");
            button(screen, "villagerretaliation.gui.settlement_view.gizmo_move").onPress();
            var bounds = screen.selectionGizmo().bounds();
            var handle = gizmoPivot(bounds).add(gizmoScale(screen), 0, 0);
            dragWorld(screen, handle, handle.add(1.4, 0, 0));
            require(screen.selectionGizmo().bounds().equals(bounds.move(1, 0, 0)), "area transform moves the fixed-size bounds by whole blocks");
            ticks = 0;
            phase = 72;
        } else if (phase == 72 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 5) {
            capture("area-transform");
            button(screen, "villagerretaliation.gui.settlement_view.gizmo_area_settings").onPress();
            require(minecraft.screen instanceof SettlementEditScreen, "adjusted area bounds open the existing settings form");
            minecraft.screen.onClose();
            ticks = 0;
            phase = 73;
        } else if (phase == 73 && minecraft.screen instanceof SettlementViewScreen) {
            phase = 74;
        } else if (phase == 73 && minecraft.screen instanceof SettlementManagementScreen screen) {
            button(screen, "villagerretaliation.gui.settlement.world_view").onPress();
            phase = 74;
        } else if (phase == 74 && minecraft.screen instanceof SettlementViewScreen screen) {
            screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            require(minecraft.screen == screen && screen.selectionGizmo() == null, "area cancellation keeps the camera session");
            button(screen, "villagerretaliation.gui.settlement_view.overview").onPress();
            phase = 15;
        } else if (phase == 15 && minecraft.screen instanceof SettlementManagementScreen screen) {
            screen.navigateTo(com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.PROJECTS, "");
            phase = 16;
        } else if (phase == 16 && minecraft.screen instanceof SettlementManagementScreen screen
                && screen.snapshot().page() == com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.PROJECTS) {
            require(button(screen, "villagerretaliation.gui.settlement.new_building").active, "Hamlet unlocks direct building requests without paper");
            button(screen, "villagerretaliation.gui.settlement.new_building").onPress();
            phase = 24;
        } else if (phase == 24 && minecraft.screen instanceof SettlementBlueprintScreen screen) {
            screen.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                    .map(net.minecraft.client.gui.components.EditBox.class::cast).findFirst().orElseThrow()
                    .setValue("minecraft:village/plains/houses/plains_small_house_1");
            var rows = screen.children().stream().filter(net.minecraft.client.gui.components.ObjectSelectionList.class::isInstance)
                    .map(child -> (net.minecraft.client.gui.components.ObjectSelectionList<?>) child).findFirst().orElseThrow();
            require(rows.children().size() == 1, "the prefab catalog filters to the exact house");
            rows.children().getFirst().mouseClicked(0, 0, 0);
            require(button(screen, "villagerretaliation.gui.settlement.place_prefab").active, "a prefab can be placed without an item");
            ticks = 0;
            phase = 33;
        } else if (phase == 33 && minecraft.screen instanceof SettlementBlueprintScreen screen && ++ticks >= 5) {
            capture("prefab-catalog");
            button(screen, "villagerretaliation.gui.settlement.place_prefab").onPress();
            ticks = 0;
            phase = 27;
        } else if (phase == 27 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 10
                && screen.parent().buildingDraft().ghost() != null) {
            clickWorld(screen, Vec3.atBottomCenterOf(fixture.center().offset(18, 0, 4)));
            var draft = screen.parent().buildingDraft();
            require(draft.placed && draft.anchor.equals(fixture.center().offset(18, 0, 4)), "terrain selection pins the prefab anchor");
            ticks = 0;
            phase = 28;
        } else if (phase == 28 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 10) {
            var draft = screen.parent().buildingDraft();
            var pivot = gizmoPivot(draft.bounds());
            double radius = gizmoScale(screen) * 1.4;
            dragWorld(screen, pivot.add(radius, 0, 0), pivot.add(0, 0, radius));
            require(draft.rotation == net.minecraft.world.level.block.Rotation.CLOCKWISE_90, "the horizontal rotation control snaps to a quarter turn");
            ticks = 0;
            phase = 29;
        } else if (phase == 29 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 10
                && screen.parent().buildingDraft().preview.rotation() == net.minecraft.world.level.block.Rotation.CLOCKWISE_90) {
            capture("prefab-positioned");
            var draft = screen.parent().buildingDraft();
            var handle = gizmoPivot(draft.bounds()).add(0, gizmoScale(screen), 0);
            var anchor = draft.anchor;
            dragWorld(screen, handle, handle.add(0, 1.4, 0));
            require(draft.anchor.equals(anchor.above()), "the vertical movement arrow snaps to blocks");
            ticks = 0;
            phase = 30;
        } else if (phase == 30 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 10) {
            var draft = screen.parent().buildingDraft();
            var handle = gizmoPivot(draft.bounds()).add(0, gizmoScale(screen), 0);
            dragWorld(screen, handle, handle.add(0, -1.4, 0));
            require(draft.anchor.getY() == fixture.center().getY(), "the prefab returns to ground height");
            originalX = screen.focusX();
            screen.mouseClicked(40, 120, GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
            screen.mouseDragged(44, 120, GLFW.GLFW_MOUSE_BUTTON_MIDDLE, 4, 0);
            screen.mouseReleased(44, 120, GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
            require(screen.focusX() != originalX && draft.placed, "camera dragging remains available during prefab adjustment");
            screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            require(minecraft.screen == screen && screen.parent().buildingDraft() == null, "Escape cancels the draft without leaving town view");
            UUID player = minecraft.player.getUUID();
            minecraft.getSingleplayerServer().submit(() -> {
                var town = SettlementSavedData.get(minecraft.getSingleplayerServer().getPlayerList().getPlayer(player).serverLevel()).get(fixture.town()).orElseThrow();
                require(town.projects().isEmpty() && town.economy().balance().equals(com.jvn.villagerretaliation.sell.CurrencyAmount.of(500, 1)), "preview and cancellation consume no funds or project slots");
            }).join();
            button(screen, "villagerretaliation.gui.settlement.new_building").onPress();
            phase = 31;
        } else if (phase == 31 && minecraft.screen instanceof SettlementBlueprintScreen screen) {
            screen.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                    .map(net.minecraft.client.gui.components.EditBox.class::cast).findFirst().orElseThrow()
                    .setValue("minecraft:village/plains/houses/plains_small_house_1");
            var rows = screen.children().stream().filter(net.minecraft.client.gui.components.ObjectSelectionList.class::isInstance)
                    .map(child -> (net.minecraft.client.gui.components.ObjectSelectionList<?>) child).findFirst().orElseThrow();
            rows.children().getFirst().mouseClicked(0, 0, 0);
            button(screen, "villagerretaliation.gui.settlement.place_prefab").onPress();
            ticks = 0;
            phase = 32;
        } else if (phase == 32 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 10
                && screen.parent().buildingDraft().ghost() != null) {
            clickWorld(screen, Vec3.atBottomCenterOf(fixture.center().offset(18, 0, 4)));
            projectFixture = new ProjectFixture(projectFixture.builder(), screen.parent().buildingDraft().request);
            button(screen, "villagerretaliation.gui.settlement_view.building_choose_builder").onPress();
            ticks = 0;
            phase = 18;
        } else if (phase == 18 && minecraft.screen instanceof SettlementBuildingScreen screen && ++ticks >= 5
                && screen.parent().buildingDraft().preview != null) {
            var draft = screen.parent().buildingDraft();
            require(draft.preview.builders().size() == 1 && draft.preview.builders().getFirst().id().equals(projectFixture.builder()), "an unassigned unloaded builder is available");
            require(draft.preview.materials().stream().anyMatch(material -> material.available() < material.required()), "the review identifies missing supplies");
            var rows = screen.children().stream().filter(net.minecraft.client.gui.components.ObjectSelectionList.class::isInstance)
                    .map(child -> (net.minecraft.client.gui.components.ObjectSelectionList<?>) child).findFirst().orElseThrow();
            rows.children().getFirst().mouseClicked(0, 0, 0);
            ticks = 0;
            phase = 19;
        } else if (phase == 19 && minecraft.screen instanceof SettlementBuildingScreen screen && ++ticks >= 5
                && button(screen, "villagerretaliation.gui.settlement.request_build").active) {
            capture("project-review");
            screen.onClose();
            phase = 75;
        } else if (phase == 75 && minecraft.screen instanceof SettlementViewScreen screen) {
            require(screen.parent().buildingDraft().request.equals(projectFixture.project())
                    && screen.parent().buildingDraft().builder.equals(projectFixture.builder()), "returning to placement keeps the reviewed draft and builder");
            screen.selectionGizmo().mode(SettlementSelectionGizmo.Mode.RESIZE);
            require(screen.selectionGizmo().mode() == SettlementSelectionGizmo.Mode.TRANSFORM, "prefabs retain their fixed dimensions");
            button(screen, "villagerretaliation.gui.settlement.change_prefab").onPress();
            phase = 76;
        } else if (phase == 76 && minecraft.screen instanceof SettlementBlueprintScreen screen) {
            require(button(screen, "villagerretaliation.gui.settlement.place_prefab").active, "the current prefab remains selected when returning to the catalog");
            button(screen, "villagerretaliation.gui.settlement.place_prefab").onPress();
            phase = 77;
        } else if (phase == 77 && minecraft.screen instanceof SettlementViewScreen screen) {
            require(screen.parent().buildingDraft().request.equals(projectFixture.project())
                    && screen.parent().buildingDraft().anchor.equals(fixture.center().offset(18, 0, 4)), "prefab selection preserves the placement draft");
            button(screen, "villagerretaliation.gui.settlement_view.building_choose_builder").onPress();
            ticks = 0;
            phase = 78;
        } else if (phase == 78 && minecraft.screen instanceof SettlementBuildingScreen screen && ++ticks >= 5
                && button(screen, "villagerretaliation.gui.settlement.request_build").active) {
            button(screen, "villagerretaliation.gui.settlement.request_build").onPress();
            phase = 20;
        } else if (phase == 20 && minecraft.screen instanceof SettlementManagementScreen screen
                && screen.snapshot().page() == com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.PROJECTS
                && screen.snapshot().entries().stream().anyMatch(entry -> entry.id().equals(projectFixture.project()))) {
            capture("project-started");
            projectSnapshot = screen.snapshot().entries().stream()
                    .filter(entry -> entry.id().equals(projectFixture.project()))
                    .map(com.jvn.villagerretaliation.network.SettlementManagementPayload.Project.class::cast).findFirst().orElseThrow();
            require(projectSnapshot.min().getX() <= projectSnapshot.max().getX(), "project bounds survive the management payload");
            button(screen, "villagerretaliation.gui.settlement.world_view").onPress();
            ticks = 0;
            phase = 110;
        } else if (phase == 110 && minecraft.screen instanceof SettlementViewScreen screen && screen.hasProject(projectFixture.project()) && ++ticks >= 10) {
            var bounds = new SettlementViewBounds(projectSnapshot.min(), projectSnapshot.max()).box();
            clickWorld(screen, bounds.getCenter(), GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            require(screen.contextPopup() != null && screen.contextPopup().target().id().equals(projectFixture.project()),
                    "queued construction can be selected in the world view");
            require(screen.contextPopup().narration().getString().contains(projectSnapshot.name()), "construction context identifies the selected project");
            capture("project-world-selection");
            clickContextAction(screen, 2);
            require(minecraft.screen instanceof SettlementEditScreen, "construction footprint can open a new area designation");
            minecraft.screen.onClose();
            ticks = 0;
            phase = 111;
        } else if (phase == 111 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 5) {
            require(screen.selectionGizmo() != null
                    && screen.selectionGizmo().bounds().equals(new SettlementViewBounds(projectSnapshot.min(), projectSnapshot.max()).box()),
                    "the new designation starts at the exact construction footprint");
            screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            button(screen, "villagerretaliation.gui.settlement_view.overview").onPress();
            phase = 114;
        } else if (phase == 114 && minecraft.screen instanceof SettlementManagementScreen screen) {
            screen.navigateTo(com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.PROJECTS, "");
            phase = 112;
        } else if (phase == 112 && minecraft.screen instanceof SettlementManagementScreen screen
                && screen.snapshot().page() == com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.PROJECTS
                && screen.snapshot().entries().stream().anyMatch(entry -> entry.id().equals(projectFixture.project()))) {
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
            button(screen, "villagerretaliation.gui.settlement.world_view").onPress();
            ticks = 0;
            phase = 113;
        } else if (phase == 113 && minecraft.screen instanceof SettlementViewScreen screen && !screen.hasProject(projectFixture.project()) && ++ticks >= 10) {
            var bounds = new SettlementViewBounds(projectSnapshot.min(), projectSnapshot.max()).box();
            clickWorld(screen, bounds.getCenter(), GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            require(screen.contextPopup() == null, "cancelled construction no longer leaves a selectable footprint");
            screen.onClose();
            ticks = 0;
            phase = 23;
        } else if (phase == 23 && minecraft.screen == null && ++ticks >= 20) {
            var personal = new com.jvn.villagerretaliation.client.inventory.ClipboardAnimalTaskScreen(null, fixture.resident(), true);
            minecraft.setScreen(personal);
            requireButtonsFit(personal);
            for (String label : java.util.List.of("Breed selected animal", "Shear selected sheep", "Milk selected animal", "Cull selected excess animal", "Collect animal products")) {
                button(personal, label).onPress();
                requireButtonsFit(personal);
            }
            ticks = 0;
            phase = 123;
        } else if (phase == 123 && minecraft.screen instanceof com.jvn.villagerretaliation.client.inventory.ClipboardAnimalTaskScreen screen && ++ticks >= 2) {
            capture("personal-animal-task-picker");
            screen.onClose();
            ticks = 0;
            phase = 124;
        } else if (phase == 124 && minecraft.screen == null && ++ticks >= 2) {
            require(minecraft.getCameraEntity() == minecraft.player, "closing linked management restores the player's camera");
            require(SettlementViewChunks.get(minecraft.level, fixture.center().getX() >> 4, fixture.center().getZ() >> 4) == null,
                    "closing linked management releases remote client chunks");
            UUID player = minecraft.player.getUUID();
            minecraft.getSingleplayerServer().submit(() -> {
                ServerPlayer serverPlayer = minecraft.getSingleplayerServer().getPlayerList().getPlayer(player);
                require(!serverPlayer.serverLevel().getChunkSource().chunkMap.getDistanceManager()
                        .shouldForceTicks(new ChunkPos(fixture.center()).toLong()), "closing linked management releases server tickets");
                var town = SettlementSavedData.get(serverPlayer.serverLevel()).get(fixture.town()).orElseThrow();
                require(town.project(projectFixture.project()).status()
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
                for (int x = -32; x <= 32; x++) {
                    for (int z = -32; z <= -8; z++) {
                        int top = 1 + Math.floorMod(Math.floorDiv(x, 8) + Math.floorDiv(z, 8), 3) * 2;
                        for (int y = -1; y <= top; y++) {
                            serverPlayer.serverLevel().setBlock(town.center().offset(x, y, z), Blocks.STONE.defaultBlockState(), 3);
                        }
                    }
                }
            }).join();
            button(screen, "villagerretaliation.gui.settlement.world_view").onPress();
            phase = 100;
        } else if (phase == 100 && minecraft.screen instanceof SettlementViewScreen screen && screen.terrainLoaded()) {
            hideOverviewMap(screen);
            screen.focus(fixture.center().getX() + 12.5D, fixture.center().getZ() - 11.5D);
            screen.togglePlots();
            var offer = screen.plots().plots().stream().filter(plot -> plot.eligible() && plot.paidFailure().isEmpty()).findFirst().orElseThrow();
            screen.selectPlot(offer.chunk());
            hoverTerrainPlot(screen);
            plotCount = screen.plots().totals().owned();
            plotCredits = screen.plots().totals().free();
            plotBalance = screen.plots().totals().spendable().subtract(com.jvn.villagerretaliation.sell.CurrencyAmount.of(offer.price(), 1));
            require(plotCredits > 0, "the paid claim can preserve banked free credits");
            ticks = 0;
            phase = 101;
        } else if (phase == 101 && minecraft.screen instanceof SettlementViewScreen screen) {
            hoverTerrainPlot(screen);
            if (++ticks < 40) return;
            capture("plots-terrain-tints");
            require(new ChunkPos(fixture.center().north(16)).equals(screen.hoveredPlot()), "plot hover follows the terrain under the cursor: " + screen.hoveredPlot()
                    + ", focus=" + screen.focusX() + "," + screen.focusZ()
                    + ", camera=" + minecraft.gameRenderer.getMainCamera().getPosition()
                    + ", cursor=" + minecraft.mouseHandler.xpos() + "," + minecraft.mouseHandler.ypos());
            minecraft.options.graphicsMode().set(net.minecraft.client.GraphicsStatus.FABULOUS);
            minecraft.levelRenderer.allChanged();
            ticks = 0;
            phase = 106;
        } else if (phase == 106 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 40 && screen.terrainLoaded()) {
            capture("plots-terrain-tints-fabulous");
            button(screen, "villagerretaliation.gui.settlement_view.plot_buy").onPress();
            ticks = 0;
            phase = 102;
        } else if (phase == 102 && minecraft.screen instanceof SettlementViewScreen screen && screen.plots().totals().owned() == plotCount + 1 && ++ticks >= 10) {
            require(screen.plots().totals().free() == plotCredits, "paying preserves free credits");
            require(screen.plots().totals().spendable().equals(plotBalance), "the quoted price is charged exactly once");
            var offer = screen.plots().plots().stream().filter(plot -> plot.eligible() && plot.freeFailure().isEmpty()).findFirst().orElseThrow();
            screen.selectPlot(offer.chunk());
            button(screen, "villagerretaliation.gui.settlement_view.plot_free").onPress();
            phase = 103;
        } else if (phase == 103 && minecraft.screen instanceof SettlementViewScreen screen && screen.plots().totals().owned() == plotCount + 2) {
            require(screen.plots().totals().free() == plotCredits - 1, "the free claim consumes one credit");
            require(screen.plots().totals().spendable().equals(plotBalance), "the free claim preserves currency");
            capture("plots-purchased");
            UUID player = minecraft.player.getUUID();
            minecraft.getSingleplayerServer().submit(() -> {
                var serverPlayer = minecraft.getSingleplayerServer().getPlayerList().getPlayer(player);
                var town = SettlementSavedData.get(serverPlayer.serverLevel()).get(fixture.town()).orElseThrow();
                com.jvn.villagerretaliation.settlement.SettlementTestPlots.claim(serverPlayer.serverLevel(), town, town.center(), town.center().east(600));
            }).join();
            ticks = 0;
            phase = 104;
        } else if (phase == 104 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 20) {
            screen.focus(fixture.center().getX() + 600.5D, fixture.center().getZ() + 0.5D);
            ticks = 0;
            phase = 105;
        } else if (phase == 105 && minecraft.screen instanceof SettlementViewScreen screen
                && screen.focusX() > fixture.center().getX() + 512 && screen.terrainLoaded() && ++ticks >= 20) {
            require(screen.plots().plots().size() <= SettlementViewWindow.MAX_CHUNKS, "remote plot snapshots stay bounded");
            require(screen.plots().plots().stream().anyMatch(plot -> plot.owned() && plot.chunk().equals(new ChunkPos(fixture.center().east(600)))),
                    "the long extension remains visible beyond the old radius ceiling");
            capture("plots-long-extension");
            screen.togglePlots();
            screen.keyPressed(GLFW.GLFW_KEY_HOME, 0, 0);
            minecraft.options.guiScale().set(3);
            minecraft.resizeDisplay();
            ticks = 0;
            phase = 120;
        } else if (phase == 120 && minecraft.screen instanceof SettlementViewScreen screen && screen.terrainLoaded() && ++ticks >= 15) {
            capture("browser-gui-scale-3");
            minecraft.options.guiScale().set(4);
            minecraft.resizeDisplay();
            ticks = 0;
            phase = 121;
        } else if (phase == 121 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 15) {
            capture("browser-gui-scale-4");
            minecraft.getWindow().setWindowed(960, 720);
            ticks = 0;
            phase = 122;
        } else if (phase == 122 && minecraft.screen instanceof SettlementViewScreen screen && ++ticks >= 15) {
            capture("browser-narrow-window");
            verifyViewBounds(screen);
            minecraft.options.guiScale().set(2);
            minecraft.resizeDisplay();
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

    private static void hoverTerrainPlot(SettlementViewScreen screen) {
        var minecraft = Minecraft.getInstance();
        int x = fixture.center().getX() + 12, z = fixture.center().getZ() - 12;
        var chunk = minecraft.level.getChunkSource().getChunk(x >> 4, z >> 4, false);
        require(chunk != null, "the plot hover target has streamed terrain");
        var point = screenPoint(screen, new Vec3(x + 0.5D,
                chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15) + 1.01D, z + 0.5D));
        long window = minecraft.getWindow().getWindow();
        var callback = GLFW.glfwSetCursorPosCallback(window, null);
        GLFW.glfwSetCursorPosCallback(window, callback);
        require(callback != null, "Minecraft has installed its cursor input callback");
        // deliver cursor input even when the test window lacks desktop focus
        callback.invoke(window, point.x * minecraft.getWindow().getGuiScale(), point.y * minecraft.getWindow().getGuiScale());
    }

    private static void hideOverviewMap(SettlementViewScreen screen) {
        screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.visible && button.getMessage().getString().equals("Hide map"))
                .findFirst().ifPresent(Button::onPress);
    }

    private static void verifyViewInteractions(SettlementViewScreen screen) {
        SettlementManagementClientChecks.verify();
        hideOverviewMap(screen);
        var minecraft = Minecraft.getInstance();
        var entity = visibleResident(minecraft);
        var point = screenPoint(screen, entity.getBoundingBox().getCenter());
        screen.mouseClicked(point.x - 9, point.y - 12, 0);
        screen.mouseDragged(point.x + 9, point.y + 12, 0, 18, 24);
        screen.mouseReleased(point.x + 9, point.y + 12, 0);
        require(screen.selectedResidents().contains(fixture.resident()), "box selection finds a visible confirmed resident");
        screen.keyPressed(GLFW.GLFW_KEY_LEFT_SHIFT, 0, GLFW.GLFW_MOD_SHIFT);
        clickWorld(screen, entity.getBoundingBox().getCenter());
        require(screen.selectedResidents().isEmpty(), "Shift click toggles a selected resident off");
        clickWorld(screen, entity.getBoundingBox().getCenter());
        screen.keyReleased(GLFW.GLFW_KEY_LEFT_SHIFT, 0, 0);
        require(screen.selectedResidents().contains(fixture.resident()), "Shift click adds a resident");
        screen.keyPressed(GLFW.GLFW_KEY_1, 0, GLFW.GLFW_MOD_CONTROL);
        screen.keyPressed(GLFW.GLFW_KEY_2, 0, 0);
        require(screen.selectedResidents().isEmpty(), "an empty control group clears selection");
        screen.keyPressed(GLFW.GLFW_KEY_1, 0, 0);
        require(screen.selectedResidents().contains(fixture.resident()), "control groups restore their residents");
        screen.keyPressed(GLFW.GLFW_KEY_1, 0, 0);
        require(Math.abs(screen.focusX() - entity.blockPosition().getX()) < 1, "double recall focuses its members");
        screen.keyPressed(GLFW.GLFW_KEY_HOME, 0, 0);
        var search = screen.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                .map(net.minecraft.client.gui.components.EditBox.class::cast).findFirst().orElseThrow();
        screen.setFocused(search);
        search.setFocused(true);
        double x = screen.focusX();
        screen.keyPressed(GLFW.GLFW_KEY_W, 0, 0);
        screen.charTyped('w', 0);
        screen.keyPressed(GLFW.GLFW_KEY_2, 0, 0);
        require(search.getValue().equals("w") && screen.selectedResidents().contains(fixture.resident()),
                "search consumes camera and group shortcuts");
        require(screen.focusX() == x, "typing does not move the camera");
        screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        search.setValue("");
        int expanded = screen.viewportWidth();
        literalButton(screen, ">").onPress();
        require(screen.viewportWidth() > expanded, "collapsing the entire sidebar reclaims its viewport");
        capture("browser-collapsed");
        literalButton(screen, "<").onPress();
        require(screen.viewportWidth() == expanded, "reopening restores the sidebar viewport");
        literalButton(screen, "Map").onPress();
        screen.savePreferences();
        var restored = SettlementViewPreferences.forSettlement(fixture.town().value());
        require(restored.groups.getFirst().contains(fixture.resident()), "control groups survive preference reload");
        require(restored.sidebar && restored.map, "visibility preferences survive reload");
        var map = new SettlementOverviewMap();
        map.layout(screen.viewportWidth(), screen.height, fixture.center(), 32);
        double oldX = screen.focusX();
        screen.mouseClicked(map.left + 3, map.top + map.size - 3, 0);
        screen.mouseReleased(map.left + 3, map.top + map.size - 3, 0);
        require(screen.focusX() != oldX, "overview terrain pans without moving the player");
        screen.keyPressed(GLFW.GLFW_KEY_HOME, 0, 0);
        button(screen, "villagerretaliation.gui.settlement_view.draw_area").onPress();
        screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        require(minecraft.screen == screen, "Escape cancels area mode before closing the view");
        verifyPreferenceRecovery();
    }

    private static void verifyViewBounds(SettlementViewScreen screen) {
        var minecraft = Minecraft.getInstance();
        for (int[] size : java.util.List.of(new int[]{320, 240}, new int[]{426, 240}, new int[]{640, 360})) {
            screen.resize(minecraft, size[0], size[1]);
            for (var child : screen.children()) {
                if (child instanceof Button b && b.visible) require(b.getX() >= 0 && b.getRight() <= size[0]
                        && b.getY() >= 0 && b.getBottom() <= size[1], "visible view controls fit narrow layouts");
            }
        }
        screen.resize(minecraft, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
    }

    private static Button literalButton(SettlementViewScreen screen, String label) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.visible && button.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }

    private static void verifyPreferenceRecovery() {
        Path path = Path.of(System.getProperty(OUTPUT), "preference-recovery.properties");
        try {
            var saved = new SettlementViewPreferences(path);
            saved.height = 10_000;
            saved.yaw = -45;
            saved.sidebar = false;
            saved.save();
            var restored = new SettlementViewPreferences(path);
            require(restored.height == SettlementViewWindow.MAX_HEIGHT && restored.yaw == 315 && !restored.sidebar,
                    "restored preferences clamp camera limits");
            Files.writeString(path, "version=broken");
            var corrupt = new SettlementViewPreferences(path);
            corrupt.save();
            require(Files.readString(path).equals("version=broken") && corrupt.sidebar,
                    "corrupt preferences use defaults without overwriting the unreadable file");
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void selectFirstRow(SettlementManagementScreen screen) {
        var rows = screen.children().stream().filter(net.minecraft.client.gui.components.ObjectSelectionList.class::isInstance)
                .map(child -> (net.minecraft.client.gui.components.ObjectSelectionList<?>) child).findFirst().orElseThrow();
        rows.children().getFirst().mouseClicked(0, 0, 0);
    }

    private static void requireButtonsFit(net.minecraft.client.gui.screens.Screen screen) {
        int width = screen.width;
        int height = screen.height;
        for (var size : java.util.List.of(new int[]{320, 240}, new int[]{426, 240}, new int[]{width, height})) {
            screen.resize(Minecraft.getInstance(), size[0], size[1]);
            checkButtonsFit(screen);
        }
    }

    private static void checkButtonsFit(net.minecraft.client.gui.screens.Screen screen) {
        var buttons = screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(button -> button.visible).toList();
        for (var button : buttons) {
            require(button.getX() >= 0 && button.getY() >= 0 && button.getRight() <= screen.width
                    && button.getBottom() <= screen.height, "management buttons remain inside the window");
            require(Minecraft.getInstance().font.width(button.getMessage()) <= button.getWidth() - 4, "management button labels fit");
            for (var other : buttons) {
                if (button == other) continue;
                require(button.getRight() <= other.getX() || other.getRight() <= button.getX()
                        || button.getBottom() <= other.getY() || other.getBottom() <= button.getY(), "management buttons do not overlap");
            }
        }
    }

    private static Villager prepareIssueFarmer(ServerPlayer player) {
        var level = player.serverLevel();
        var origin = fixture.center().offset(14, 0, 0);
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.FARM, "River's farm", origin, origin.offset(3, 2, 3), 5, true);
        require(SettlementBuildingService.register(player, fixture.town(), area), "the issue fixture has a farm");
        var farmer = EntityType.VILLAGER.create(level);
        farmer.setPos(origin.getBottomCenter());
        farmer.setNoAi(true);
        farmer.setCustomName(Component.literal("River"));
        farmer.setVillagerData(farmer.getVillagerData().setProfession(VillagerProfession.FARMER));
        VillageAllegianceApi.assignKnown(level, farmer, fixture.town(), AllegianceAssignmentSource.EXPLICIT_API);
        com.jvn.villagerretaliation.social.VillagerSocialGraphSavedData.get(level).ensureProfile(level, farmer);
        require(SettlementWorkforceService.assign(level, player, fixture.town(), farmer, area.id()), "the issue farmer is assigned");
        issueFarmer = farmer;
        blockIssueFarmer();
        return farmer;
    }

    private static void blockIssueFarmer() {
        var state = com.jvn.villagerretaliation.interaction.HiredWorkStateStore.state(issueFarmer);
        state.putString("WorkerTaskState", "paused_missing_tool");
        state.putString("WorkerFailureReason", "missing_hoe");
        state.putString("Status", "interaction.work.farming.missing_hoe");
        com.jvn.villagerretaliation.settlement.SettlementBackgroundService.onUnloaded(issueFarmer);
    }

    private static UUID prepareBrewer(ServerPlayer player) {
        var level = player.serverLevel();
        var origin = fixture.center().offset(30, 0, 0);
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.BREWERY, "Brewery",
                origin, origin.offset(4, 4, 4), 5, true);
        com.jvn.villagerretaliation.settlement.SettlementTestPlots.claim(level, SettlementSavedData.get(level).get(fixture.town()).orElseThrow(), area.min(), area.max());
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
                entry.putBoolean("ManualManagement", true);
            }
        }
        level.getServer().overworld().getDataStorage().set("villagerretaliation_settlements", SettlementSavedData.load(saved, level.registryAccess()));
        var origin = fixture.center().offset(12, 0, 0);
        com.jvn.villagerretaliation.settlement.SettlementTestPlots.claim(level, SettlementSavedData.get(level).get(fixture.town()).orElseThrow(),
                origin.offset(-3, -1, -4), origin.offset(14, 12, 12));
        for (BlockPos position : BlockPos.betweenClosed(origin.offset(-3, -1, -4), origin.offset(14, 12, 12))) {
            level.setBlockAndUpdate(position, position.getY() == origin.getY() - 1
                    ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        Villager builder = EntityType.VILLAGER.create(level);
        builder.setPos(origin.offset(-2, 0, 2).getBottomCenter());
        builder.setNoAi(true);
        builder.setCustomName(Component.literal("Rowan"));
        builder.setVillagerData(builder.getVillagerData().setProfession(VillagerProfession.MASON));
        VillageAllegianceApi.assignKnown(level, builder, fixture.town(), AllegianceAssignmentSource.EXPLICIT_API);
        require(level.getEntity(builder.getUUID()) == null, "the civic builder remains unloaded");
        SettlementCareService.onUnloaded(builder);
        com.jvn.villagerretaliation.interaction.VillagerCurrencyPayment.give(player, 500);
        require(com.jvn.villagerretaliation.settlement.SettlementTradeService.depositCurrency(player, fixture.town(), 500), "the construction fee is funded");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
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

    private static Vec3 gizmoPivot(AABB bounds) {
        return new Vec3(bounds.getCenter().x, bounds.minY + 0.5, bounds.getCenter().z);
    }

    private static double gizmoScale(SettlementViewScreen screen) {
        return Math.clamp(screen.cameraHeight() * 0.055, 1.2, 8);
    }

    private static Vec3 screenPoint(SettlementViewScreen screen, Vec3 position) {
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 relative = position.subtract(camera.getPosition());
        double distance = relative.dot(new Vec3(camera.getLookVector()));
        double tangent = Math.tan(Math.toRadians(SettlementViewClient.FOV / 2.0D));
        double x = screen.viewportWidth() / 2.0D - relative.dot(new Vec3(camera.getLeftVector())) * screen.height / (2.0D * distance * tangent);
        double y = screen.height / 2.0D - relative.dot(new Vec3(camera.getUpVector())) * screen.height / (2.0D * distance * tangent);
        return new Vec3(x, y, 0);
    }

    private static void clickWorld(SettlementViewScreen screen, Vec3 position) {
        clickWorld(screen, position, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private static void clickWorld(SettlementViewScreen screen, Vec3 position, int button) {
        var point = screenPoint(screen, position);
        screen.mouseClicked(point.x, point.y, button);
        screen.mouseReleased(point.x, point.y, button);
    }

    private static void clickContextAction(SettlementViewScreen screen, int index) {
        var action = screen.contextPopup().buttons().get(index);
        double x = action.getX() + action.getWidth() / 2.0D;
        double y = action.getY() + action.getHeight() / 2.0D;
        screen.mouseClicked(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        screen.mouseReleased(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private static void dragWorld(SettlementViewScreen screen, Vec3 start, Vec3 end) {
        var from = screenPoint(screen, start);
        var to = screenPoint(screen, end);
        screen.mouseClicked(from.x, from.y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        screen.mouseDragged(to.x, to.y, GLFW.GLFW_MOUSE_BUTTON_LEFT, to.x - from.x, to.y - from.y);
        screen.mouseReleased(to.x, to.y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
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
                .filter(button -> button.visible && (button.getMessage().getString().equals(label)
                        || button.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents contents
                        && contents.getKey().equals(translation))).findFirst().orElseThrow();
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
