package com.jvn.villagerretaliation.client.settlement;

import com.google.gson.Gson;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue;
import com.jvn.villagerretaliation.interaction.work.courier.TransferManagementService;
import com.jvn.villagerretaliation.network.ProductionTarget;
import com.jvn.villagerretaliation.network.TransferRequestPayload;
import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload;
import com.jvn.villagerretaliation.network.SettlementViewRequestPayload;
import com.jvn.villagerretaliation.settlement.SettlementArea;
import com.jvn.villagerretaliation.settlement.SettlementAreaType;
import com.jvn.villagerretaliation.settlement.SettlementBuildingService;
import com.jvn.villagerretaliation.settlement.SettlementCareService;
import com.jvn.villagerretaliation.settlement.SettlementGuardOrder;
import com.jvn.villagerretaliation.settlement.SettlementDepotService;
import com.jvn.villagerretaliation.settlement.SettlementWorksitePlan;
import com.jvn.villagerretaliation.settlement.SettlementWorksiteService;
import com.jvn.villagerretaliation.settlement.SettlementPermission;
import com.jvn.villagerretaliation.settlement.SettlementSavedData;
import com.jvn.villagerretaliation.settlement.SettlementViewService;
import com.jvn.villagerretaliation.settlement.SettlementWorkforceService;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = VillagerRetaliation.MOD_ID, value = Dist.CLIENT)
public final class SettlementMultiplayerAcceptance {
    private static final String OUTPUT = "villagerretaliation.settlementMultiplayer";
    private static final BlockPos CENTER = new BlockPos(1_024, -60, 1_024);
    private static final Vec3 BODY = new Vec3(0.5D, -60, 0.5D);
    private static CompletableFuture<Fixture> preparation;
    private static volatile Fixture fixture;
    private static Process peerProcess;
    private static long started;
    private static UUID leader;
    private static volatile String hostPhase = "";
    private static String peerPhase = "";
    private static int ticks;
    private static int viewTicks;
    private static int commandStep;
    private static int commandTick;
    private static long phaseStarted;
    private static boolean reported;
    private static boolean configured;
    private static boolean hostViewing;
    private static int simultaneousTicks;
    private static int peakTickets;
    private static int peakSends;

    private SettlementMultiplayerAcceptance() {
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        if (System.getProperty(OUTPUT) == null || reported) return;
        var minecraft = Minecraft.getInstance();
        if (started == 0) started = Util.getMillis();
        require(Util.getMillis() - started < 900_000L, "multiplayer acceptance completes within fifteen minutes: " + hostPhase + "/" + peerPhase);
        minecraft.options.pauseOnLostFocus = false;
        if (!configured && minecraft.level != null && minecraft.player != null && minecraft.screen == null) {
            minecraft.options.renderDistance().set(6);
            minecraft.options.simulationDistance().set(6);
            minecraft.options.guiScale().set(2);
            minecraft.options.framerateLimit().set(60);
            minecraft.options.enableVsync().set(false);
            minecraft.resizeDisplay();
            GLFW.glfwHideWindow(minecraft.getWindow().getWindow());
            configured = true;
        }
        if (host()) {
            if (peerProcess != null && !peerProcess.isAlive()) require(peerProcess.exitValue() == 0, "the peer client starts successfully, inspect peer/process.log");
            if (preparation == null && configured && minecraft.getSingleplayerServer() != null) {
                leader = minecraft.player.getUUID();
                preparation = minecraft.getSingleplayerServer().submit(() -> prepare(minecraft.getSingleplayerServer().getPlayerList().getPlayer(leader)));
            }
            if (fixture == null && preparation != null && preparation.isDone()) {
                fixture = preparation.join();
                launchPeer();
            }
            if (fixture != null && hostPhase.equals("view_only")) {
                if (!hostViewing) {
                    PacketDistributor.sendToServer(new SettlementManagementRequestPayload(fixture.town(),
                            SettlementManagementRequestPayload.Page.SUMMARY, 0, "", 0, true));
                    hostViewing = true;
                }
                if (minecraft.screen instanceof SettlementManagementScreen screen && !screen.expectingView()
                        && screen.snapshot().header() != null) button(screen, "villagerretaliation.gui.settlement.world_view").onPress();
            } else if (hostViewing && !hostPhase.equals("view_only")) {
                if (minecraft.screen instanceof SettlementViewScreen screen) screen.onClose();
                hostViewing = false;
            }
            if (read("host-state.txt").equals("finished")) {
                require(peerProcess != null && !peerProcess.isAlive(), "the second native client has exited");
                write("result.txt", "Settlement multiplayer acceptance passed\n");
                write("acceptance.json", new Gson().toJson(Map.of(
                        "transport", "Two native clients using a TCP listener bound only to loopback",
                        "fixture", "Disposable integrated server, one stationary civic guard, separate remote workplace and survival player bodies",
                        "checks", List.of("outsider access denied", "delegated view cannot grant itself authority or order guards",
                                "authorized guard selection and movement order", "stale camera request rejected",
                                "remote body remains vulnerable and unmoved", "damage closes view and releases chunks",
                                "normal close releases chunks", "disconnect releases server tickets",
                                "competing shared transfer edits reject stale revisions", "finite and recurring orders survive TCP transport",
                                "replayed append and revoked logistics edits preserve the shared queue"),
                        "simultaneousTicks", simultaneousTicks,
                        "peakSharedTickets", peakTickets,
                        "peakChunkPacketsPerTick", peakSends,
                        "elapsedMilliseconds", Util.getMillis() - started)));
                LogUtils.getLogger().info("Settlement multiplayer acceptance passed");
                reported = true;
                minecraft.stop();
            }
            return;
        }
        if (!configured || minecraft.level == null || minecraft.player == null) return;
        if (fixture == null) fixture = new Gson().fromJson(read("fixture.json"), Fixture.class);
        String phase = read("host-state.txt");
        if (!phase.equals(peerPhase)) {
            peerPhase = phase;
            ticks = 0;
            viewTicks = 0;
            commandStep = 0;
            LogUtils.getLogger().info("Settlement multiplayer peer phase: {}", phase);
        }
        ticks++;
        if (phase.equals("outsider")) {
            if (ticks == 10) action(new SettlementManagementActionPayload.View());
            if (ticks == 20) action(new SettlementManagementActionPayload.RemoveArea(fixture.area()));
            if (ticks == 30) action(new SettlementManagementActionPayload.Permissions(minecraft.player.getUUID(), Set.of(SettlementPermission.VIEW, SettlementPermission.STAFF)));
            if (ticks == 60) write("peer-state.txt", "outsider_done");
        } else if (phase.equals("view_only")) {
            openView(minecraft);
            if (minecraft.screen instanceof SettlementViewScreen screen) {
                if (++viewTicks == 60) {
                    verifyView(minecraft);
                    capture("peer-view");
                    action(new SettlementManagementActionPayload.Guard(fixture.resident(), moveOrder()));
                }
                if (viewTicks == 70) action(new SettlementManagementActionPayload.Permissions(minecraft.player.getUUID(), Set.of(SettlementPermission.VIEW, SettlementPermission.STAFF)));
                if (viewTicks == 100) write("peer-state.txt", "view_done");
            }
        } else if (phase.equals("staff") && minecraft.screen instanceof SettlementViewScreen screen) {
            viewTicks++;
            if (commandStep == 0 && viewTicks >= 20) {
                var resident = resident(minecraft);
                if (resident == null) return;
                clickWorld(screen, resident.getBoundingBox().getCenter());
                commandStep = 1;
            } else if (commandStep == 1 && screen.getNarrationMessage().getString().contains("Tamsin")) {
                var move = button(screen, "villagerretaliation.gui.settlement_view.guard_move");
                if (!move.active) return;
                move.onPress();
                clickWorld(screen, CENTER.offset(4, 0, 3).getBottomCenter());
                commandStep = 2;
            } else if (commandStep == 2 && screen.getNarrationMessage().getString().contains("Temporary move")) {
                verifyView(minecraft);
                capture("peer-guard-order");
                PacketDistributor.sendToServer(new SettlementViewRequestPayload(screen.session(), 1_000, CENTER.getX() + 3.5D, CENTER.getZ() + 0.5D, 32, false));
                commandTick = ticks;
                commandStep = 3;
            } else if (commandStep == 3 && ticks >= commandTick + 10) {
                PacketDistributor.sendToServer(new SettlementViewRequestPayload(screen.session(), 999, CENTER.getX() + 20.5D, CENTER.getZ() + 0.5D, 32, false));
                commandStep = 4;
            } else if (commandStep == 4 && ticks >= commandTick + 30) {
                require(Math.abs(screen.focusX() - CENTER.getX() - 3.5D) < 0.01D, "an older camera request cannot replace the accepted view");
                write("peer-state.txt", "staff_done");
                commandStep = 5;
            }
        } else if (phase.equals("damage") && minecraft.screen == null && ticks > 20) {
            require(minecraft.getCameraEntity() == minecraft.player, "damage restores the peer body camera");
            require(SettlementViewChunks.get(minecraft.level, CENTER.getX() >> 4, CENTER.getZ() >> 4) == null, "damage clears remote client chunks");
            write("peer-state.txt", "damage_done");
        } else if (phase.equals("close")) {
            if (commandStep == 0) openView(minecraft);
            if (minecraft.screen instanceof SettlementViewScreen screen && ++viewTicks >= 50 && commandStep == 0) {
                verifyView(minecraft);
                screen.onClose();
                commandStep = 1;
                commandTick = ticks;
            }
            if (commandStep == 1 && ticks > commandTick + 20 && minecraft.getCameraEntity() == minecraft.player) {
                require(SettlementViewChunks.get(minecraft.level, CENTER.getX() >> 4, CENTER.getZ() >> 4) == null, "normal close clears remote client chunks");
                write("peer-state.txt", "close_done");
            }
        } else if (phase.equals("orders_stale") || phase.equals("orders_append") || phase.equals("orders_replay")) {
            if (ticks == 10) PacketDistributor.sendToServer(transfer(
                    phase.equals("orders_stale") ? 0 : 1, HiredTransferQueue.Mode.MAINTAIN_STOCK));
            if (ticks == 40) write("peer-state.txt", phase + "_done");
        } else if (phase.equals("orders_revoked")) {
            if (ticks == 10) PacketDistributor.sendToServer(new TransferRequestPayload(
                    new ProductionTarget(fixture.town(), fixture.courier()), UUID.randomUUID(),
                    Long.parseLong(read("queue-revision.txt")), new TransferRequestPayload.Edit(
                    UUID.fromString(read("queue-order.txt")), HiredTransferQueue.Edit.CANCEL, 0)));
            if (ticks == 40) write("peer-state.txt", "orders_revoked_done");
        } else if (phase.equals("disconnect")) {
            openView(minecraft);
            if (minecraft.screen instanceof SettlementViewScreen && ++viewTicks >= 50) {
                verifyView(minecraft);
                var level = minecraft.level;
                minecraft.disconnect();
                require(SettlementViewChunks.get(level, CENTER.getX() >> 4, CENTER.getZ() >> 4) == null, "disconnect clears the peer chunk cache");
                write("peer-state.txt", "disconnected");
                reported = true;
                minecraft.stop();
            }
        }
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        if (System.getProperty(OUTPUT) == null || !host() || fixture == null || reported) return;
        var server = event.getServer();
        var viewMetrics = SettlementViewService.metrics(server);
        if (viewMetrics.viewers() >= 2) simultaneousTicks++;
        peakTickets = Math.max(peakTickets, viewMetrics.chunks());
        peakSends = Math.max(peakSends, viewMetrics.sends());
        require(viewMetrics.loads() <= com.jvn.villagerretaliation.config.VillagerRetaliationConfig.SETTLEMENT_VIEW_LOADS.get()
                && viewMetrics.sends() <= com.jvn.villagerretaliation.config.VillagerRetaliationConfig.SETTLEMENT_VIEW_SENDS.get(),
                "concurrent clients respect aggregate chunk admission and transmission limits");
        var peer = server.getPlayerList().getPlayerByName("SettlementPeer");
        var town = SettlementSavedData.get(server.overworld()).get(new VillageAllegianceId(fixture.town())).orElseThrow();
        String ack = read("peer-state.txt");
        long now = server.overworld().getGameTime();
        if (hostPhase.isEmpty() && peer != null) {
            peer.setGameMode(GameType.SURVIVAL);
            peer.connection.teleport(BODY.x, BODY.y, BODY.z, 0, 0);
            next("outsider", now);
        } else if (hostPhase.equals("outsider") && ack.equals("outsider_done")) {
            require(!town.permits(peer.getUUID(), SettlementPermission.VIEW), "an outsider cannot grant itself settlement permissions");
            require(town.areas().containsKey(fixture.area()) && town.duties().containsKey(fixture.resident()), "forged outsider edits preserve the workplace and duty");
            require(!ticketed(server), "unauthorized viewing does not retain remote tickets");
            require(town.setPermissions(leader, peer.getUUID(), Set.of(SettlementPermission.VIEW)), "the leader grants view access");
            next("view_only", now);
        } else if (hostPhase.equals("view_only") && ack.equals("view_done")) {
            require(simultaneousTicks >= 20, "both native clients receive the shared view concurrently");

            require(town.guardOrders().get(fixture.resident()) == null, "view access cannot order the guard");
            require(town.permits(peer.getUUID(), SettlementPermission.VIEW) && !town.permits(peer.getUUID(), SettlementPermission.STAFF),
                    "a viewing delegate cannot grant itself staffing authority");
            verifyBody(peer);
            require(ticketed(server), "authorized viewing holds the remote town tickets");
            require(town.setPermissions(leader, peer.getUUID(), Set.of(SettlementPermission.VIEW, SettlementPermission.STAFF)), "the leader grants independent staffing authority");
            next("staff", now);
        } else if (hostPhase.equals("staff") && ack.equals("staff_done")) {
            require(moveOrder().equals(town.temporaryGuardOrders().get(fixture.resident())), "the authorized world click applies the exact guard order");
            verifyBody(peer);
            require(peer.hurt(peer.damageSources().generic(), 1.0F), "the viewing player's survival body accepts damage");
            next("damage", now);
        } else if (hostPhase.equals("damage") && ack.equals("damage_done") && now - phaseStarted >= 30) {
            require(!ticketed(server), "damage releases remote server tickets");
            next("close", now);
        } else if (hostPhase.equals("close") && ack.equals("close_done") && now - phaseStarted >= 30) {
            require(!ticketed(server), "normal view close releases remote server tickets");
            require(town.setPermissions(leader, peer.getUUID(), Set.of(SettlementPermission.VIEW,
                    SettlementPermission.STAFF, SettlementPermission.LOGISTICS)), "the peer can edit the shared courier queue");
            require(TransferManagementService.apply(server.getPlayerList().getPlayer(leader),
                    transfer(0, HiredTransferQueue.Mode.FINITE)).isEmpty(), "the host commits its transfer before the peer's stale edit");
            next("orders_stale", now);
        } else if (hostPhase.equals("orders_stale") && ack.equals("orders_stale_done")) {
            var queue = town.transferQueues().get(fixture.courier());
            require(queue.orders().size() == 1 && queue.revision() == 1,
                    "the peer's stale recurring append cannot duplicate the host's finite order");
            next("orders_append", now);
        } else if (hostPhase.equals("orders_append") && ack.equals("orders_append_done")) {
            var queue = town.transferQueues().get(fixture.courier());
            require(queue.orders().size() == 2 && queue.revision() == 2
                    && queue.orders().getFirst().mode() == HiredTransferQueue.Mode.FINITE
                    && queue.orders().getLast().mode() == HiredTransferQueue.Mode.MAINTAIN_STOCK,
                    "both transfer modes arrive intact through the native multiplayer handler");
            next("orders_replay", now);
        } else if (hostPhase.equals("orders_replay") && ack.equals("orders_replay_done")) {
            var queue = town.transferQueues().get(fixture.courier());
            require(queue.orders().size() == 2 && queue.revision() == 2, "replaying the peer append preserves exactly two orders");
            write("queue-revision.txt", Long.toString(queue.revision()));
            write("queue-order.txt", queue.orders().getFirst().id().toString());
            require(town.setPermissions(leader, peer.getUUID(), Set.of(SettlementPermission.VIEW, SettlementPermission.STAFF)),
                    "the host revokes logistics before the peer submits its pending edit");
            next("orders_revoked", now);
        } else if (hostPhase.equals("orders_revoked") && ack.equals("orders_revoked_done")) {
            var queue = town.transferQueues().get(fixture.courier());
            require(queue.orders().size() == 2 && queue.revision() == 2 && !queue.orders().getFirst().cancelled(),
                    "revoked logistics rejects the peer's current-revision cancellation");
            next("disconnect", now);
        } else if (hostPhase.equals("disconnect") && ack.equals("disconnected") && peer == null) {
            next("cleanup", now);
        } else if (hostPhase.equals("cleanup") && now - phaseStarted >= 40 && peerProcess != null && !peerProcess.isAlive()) {
            require(!ticketed(server), "disconnect releases every remote server ticket");
            require(SettlementViewService.viewers(server.overworld(), new ChunkPos(CENTER)).isEmpty(), "disconnect removes the viewing session");
            require(town.duties().containsKey(fixture.resident()) && moveOrder().equals(town.temporaryGuardOrders().get(fixture.resident())),
                    "view lifecycle changes retain the resident's authoritative assignment and order");
            next("finished", now);
        }
    }

    private static Fixture prepare(ServerPlayer player) {
        var level = player.serverLevel();
        var server = level.getServer();
        player.setGameMode(GameType.SURVIVAL);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.setDayTime(6_000L);
        level.setWeatherParameters(1_000_000, 0, false, false);
        for (var pos : BlockPos.betweenClosed(CENTER.offset(-8, -1, -8), CENTER.offset(8, 5, 8))) {
            level.setBlock(pos, pos.getY() == CENTER.getY() - 1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        }
        level.setBlockAndUpdate(CENTER, Blocks.CRAFTING_TABLE.defaultBlockState());
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), CENTER, "Multiplayer town");
        SettlementSavedData.get(level).establish(registry, id, player.getUUID(), CENTER, level.getGameTime()).orElseThrow();
        var resident = EntityType.VILLAGER.create(level);
        resident.setPos(CENTER.offset(2, 0, 1).getBottomCenter());
        resident.setNoAi(true);
        resident.setCustomName(Component.literal("Tamsin"));
        resident.setVillagerData(resident.getVillagerData().setProfession(VillagerProfession.WEAPONSMITH));
        VillageAllegianceApi.assignKnown(level, resident, id, AllegianceAssignmentSource.EXPLICIT_API);
        level.addFreshEntity(resident);
        var post = new SettlementArea(UUID.randomUUID(), SettlementAreaType.GUARD_POST, "Guard post",
                CENTER.offset(-4, -1, -4), CENTER.offset(6, 3, 6), 5, true);
        require(SettlementBuildingService.register(player, id, post), "the multiplayer guard post is registered");
        require(SettlementWorkforceService.assign(level, player, id, resident, post.id()), "the multiplayer resident is a civic guard");
        SettlementCareService.onUnloaded(resident);
        var town = SettlementSavedData.get(level).get(id).orElseThrow();
        var courier = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Shared courier",
                CENTER.offset(-6, 0, 4), CENTER.offset(-3, 2, 6), 5, true);
        com.jvn.villagerretaliation.settlement.SettlementTestPlots.claim(level, town, courier.min(), courier.max());
        require(SettlementBuildingService.register(player, id, courier), "the shared courier worksite is registered");
        require(SettlementWorksiteService.configure(player, town, courier.id(),
                SettlementWorksitePlan.defaults(HiredVillagerRole.COURIER), true), "the shared courier settings are adopted");
        player.setPos(courier.min().getCenter());
        for (var position : List.of(courier.min(), courier.min().east(3))) {
            level.setBlockAndUpdate(position, com.jvn.villagerretaliation.block.VillagerRetaliationBlocks.SETTLEMENT_DEPOT.get().defaultBlockState());
            require(SettlementDepotService.register(player, position), "shared transfer storage is registered");
        }
        UUID source = town.depots().values().stream().filter(depot -> depot.position().equals(courier.min())).findFirst().orElseThrow().id();
        UUID destination = town.depots().values().stream().filter(depot -> depot.position().equals(courier.min().east(3))).findFirst().orElseThrow().id();
        for (var pos : BlockPos.betweenClosed(new BlockPos(-3, -61, -3), new BlockPos(3, -61, 3))) level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        player.connection.teleport(BODY.x, BODY.y, BODY.z, 0, 0);
        try {
            var loopback = InetAddress.getByName("127.0.0.1");
            int port;
            try (var socket = new ServerSocket(0, 1, loopback)) {
                port = socket.getLocalPort();
            }
            server.setUsesAuthentication(false);
            server.getConnection().startTcpServerListener(loopback, port);
            var result = new Fixture(id.value(), resident.getUUID(), post.id(), courier.id(), source, destination, port);
            write("fixture.json", new Gson().toJson(result));
            return result;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void launchPeer() {
        var args = Path.of(System.getProperty(OUTPUT + ".args"));
        var directory = Path.of(System.getProperty(OUTPUT), "peer");
        try {
            peerProcess = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                    "-Dfml.modFolders=" + System.getProperty("fml.modFolders"),
                    "@" + args.resolve("settlementMultiplayerPeerRunVmArgs.txt"),
                    "-cp", System.getProperty("java.class.path"), "net.neoforged.devlaunch.Main",
                    "@" + args.resolve("settlementMultiplayerPeerRunProgramArgs.txt"),
                    "--quickPlayMultiplayer", "127.0.0.1:" + fixture.port())
                    .directory(directory.toFile()).redirectErrorStream(true)
                    .redirectOutput(directory.resolve("process.log").toFile()).start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (peerProcess.isAlive()) peerProcess.destroy();
            }));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void openView(Minecraft minecraft) {
        if (ticks == 1) PacketDistributor.sendToServer(new SettlementManagementRequestPayload(fixture.town(),
                SettlementManagementRequestPayload.Page.SUMMARY, 0, "", 0, true));
        if (minecraft.screen instanceof SettlementManagementScreen screen && screen.snapshot().header() != null
                && screen.snapshot().header().id().equals(fixture.town()) && !screen.expectingView()) {
            button(screen, "villagerretaliation.gui.settlement.world_view").onPress();
        }
    }

    private static void verifyView(Minecraft minecraft) {
        require(minecraft.getSingleplayerServer() == null, "the peer receives the settlement through multiplayer transport");
        require(minecraft.getCameraEntity() instanceof Marker, "the peer uses a separate RTS camera");
        require(minecraft.player.position().distanceToSqr(BODY) < 1.0D, "RTS controls do not move the peer's body");
        require(SettlementViewChunks.get(minecraft.level, CENTER.getX() >> 4, CENTER.getZ() >> 4) != null, "remote chunks reach the peer");
        require(resident(minecraft) != null, "remote resident entities reach the peer");
    }

    private static void verifyBody(ServerPlayer peer) {
        require(peer.getCamera() == peer && !peer.isSpectator() && peer.position().distanceToSqr(BODY) < 1.0D,
                "the server retains the unmoved vulnerable player body");
    }

    private static boolean ticketed(MinecraftServer server) {
        return server.overworld().getChunkSource().chunkMap.getDistanceManager().shouldForceTicks(new ChunkPos(CENTER).toLong());
    }

    private static Entity resident(Minecraft minecraft) {
        for (var entity : minecraft.level.entitiesForRendering()) if (entity.getUUID().equals(fixture.resident())) return entity;
        return null;
    }

    private static SettlementGuardOrder moveOrder() {
        return new SettlementGuardOrder(SettlementGuardOrder.Mode.MOVE, List.of(CENTER.offset(4, 0, 3)));
    }

    private static TransferRequestPayload transfer(long revision, HiredTransferQueue.Mode mode) {
        return new TransferRequestPayload(new ProductionTarget(fixture.town(), fixture.courier()), UUID.randomUUID(), revision,
                new TransferRequestPayload.Append(new HiredTransferQueue.Endpoint(CENTER.offset(-6, 0, 4), fixture.source()),
                        new HiredTransferQueue.Endpoint(CENTER.offset(-3, 0, 4), fixture.destination()), new ItemStack(Items.WHEAT), 12, mode));
    }

    private static void action(SettlementManagementActionPayload.Command command) {
        PacketDistributor.sendToServer(new SettlementManagementActionPayload(fixture.town(), command));
    }

    private static void next(String phase, long now) {
        hostPhase = phase;
        phaseStarted = now;
        write("host-state.txt", phase);
        LogUtils.getLogger().info("Settlement multiplayer host phase: {}", phase);
    }

    private static boolean host() {
        return System.getProperty(OUTPUT + ".role", "").equals("host");
    }

    private static String read(String name) {
        var path = Path.of(System.getProperty(OUTPUT), name);
        try {
            return Files.exists(path) ? Files.readString(path) : "";
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void write(String name, String value) {
        var path = Path.of(System.getProperty(OUTPUT), name);
        var temporary = path.resolveSibling(name + ".tmp");
        try {
            Files.writeString(temporary, value);
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Button button(net.minecraft.client.gui.screens.Screen screen, String translation) {
        String label = Component.translatable(translation).getString();
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }

    private static void clickWorld(SettlementViewScreen screen, Vec3 position) {
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 relative = position.subtract(camera.getPosition());
        double distance = relative.dot(new Vec3(camera.getLookVector()));
        double tangent = Math.tan(Math.toRadians(SettlementViewClient.FOV / 2.0D));
        double x = screen.viewportWidth() / 2.0D - relative.dot(new Vec3(camera.getLeftVector())) * screen.height / (2.0D * distance * tangent);
        double y = screen.height / 2.0D - relative.dot(new Vec3(camera.getUpVector())) * screen.height / (2.0D * distance * tangent);
        screen.mouseClicked(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        screen.mouseReleased(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private static void capture(String name) {
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            image.writeToFile(Path.of(System.getProperty(OUTPUT), name + ".png"));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private record Fixture(UUID town, UUID resident, UUID area, UUID courier, UUID source, UUID destination, int port) {
    }
}
