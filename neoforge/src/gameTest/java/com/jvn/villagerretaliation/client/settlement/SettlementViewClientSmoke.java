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
    private static UUID brewer;
    private static long started;
    private static int phase;
    private static int ticks;
    private static double originalX;
    private static float originalHeight;
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
        if (Util.getMillis() - started >= 120_000L) {
            capture("failure");
        }
        require(Util.getMillis() - started < 120_000L, "client smoke run timed out at phase " + phase
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
            if (ticks < 30) return;
            require(screen.viewHeight() == SettlementViewWindow.MAX_HEIGHT, "the rendering check reaches maximum town zoom");
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
            screen.mouseScrolled(40, 80, 0, Math.log(screen.viewHeight() / originalHeight) / Math.log(1.15D));
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
            Vec3 table = fixture.center().getCenter().add(0.0D, 0.5D, 0.0D);
            clickWorld(screen, table);
            String firstArea = screen.getNarrationMessage().getString();
            require(firstArea.contains("Guard post") || firstArea.contains("Shared workshop"), "the shared building can be selected");
            clickWorld(screen, table);
            String secondArea = screen.getNarrationMessage().getString();
            require(!secondArea.equals(firstArea) && (secondArea.contains("Guard post") || secondArea.contains("Shared workshop")),
                    "another click selects the other use of the same building");
            require(button(screen, "villagerretaliation.gui.settlement_view.manage_selection").active,
                    "each shared designation can open its management panel");
            clickWorld(screen, table);
            require(screen.getNarrationMessage().getString().equals(firstArea), "selection cycles back through the shared designations");
            originalX = screen.focusX();
            originalHeight = screen.viewHeight();
            screen.keyPressed(GLFW.GLFW_KEY_W, 0, 0);
            ticks = 0;
            phase = 4;
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
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 relative = position.subtract(camera.getPosition());
        double distance = relative.dot(new Vec3(camera.getLookVector()));
        double tangent = Math.tan(Math.toRadians(SettlementViewClient.FOV / 2.0D));
        double x = screen.width / 2.0D - relative.dot(new Vec3(camera.getLeftVector())) * screen.height / (2.0D * distance * tangent);
        double y = screen.height / 2.0D - relative.dot(new Vec3(camera.getUpVector())) * screen.height / (2.0D * distance * tangent);
        screen.mouseClicked(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        screen.mouseReleased(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
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

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private record ProjectFixture(UUID builder, UUID project) {
    }

    private record Fixture(VillageAllegianceId town, UUID resident, BlockPos center) {
    }
}
