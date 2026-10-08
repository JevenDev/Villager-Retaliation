package com.jvn.villagerretaliation.client.interaction;

import static com.jvn.villagerretaliation.client.interaction.CampaignConversationClientSmoke.choose;
import static com.jvn.villagerretaliation.client.interaction.CampaignConversationClientSmoke.chooseIfPresent;
import static com.jvn.villagerretaliation.client.interaction.CampaignConversationClientSmoke.controller;
import static com.jvn.villagerretaliation.client.interaction.CampaignConversationClientSmoke.localized;

import com.google.gson.Gson;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.campaign.CampaignConversationClientFixture;
import com.jvn.villagerretaliation.campaign.CampaignInstance;
import com.jvn.villagerretaliation.campaign.CampaignSavedData;
import com.jvn.villagerretaliation.config.DialogueTextSpeed;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationPresentationService;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationService;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession.Phase;
import com.jvn.villagerretaliation.network.CampaignConversationRequestPayload;
import com.jvn.villagerretaliation.network.CampaignConversationRequestPayload.Action;
import com.jvn.villagerretaliation.network.CampaignConversationViewPayload;
import com.jvn.villagerretaliation.party.PartyService;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = VillagerRetaliation.MOD_ID, value = Dist.CLIENT)
public final class CampaignConversationMultiplayerAcceptance {
    private static final String OUTPUT = "villagerretaliation.campaignConversationMultiplayer";
    private static final Gson JSON = new Gson();
    private static final Map<String, String> WRITTEN = new java.util.HashMap<>();
    private static CompletableFuture<Integer> listener;
    private static volatile CampaignConversationClientFixture fixture;
    private static volatile Process peerProcess;
    private static volatile int port;
    private static volatile int generation;
    private static UUID leader;
    private static UUID peerId;
    private static String serverPhase = "";
    private static String clientPhase = "";
    private static long started;
    private static int ticks;
    private static int presentationTicks;
    private static int completedTicks;
    private static int step;
    private static boolean configured;
    private static volatile boolean reported;
    private static String observedLine = "";
    private static int lineTicks;
    private static int speakers;
    private static ConversationSession.Check savedCheck;
    private static CampaignInstance.OutcomeCommit savedOutcome;
    private static int savedReceipts;

    private CampaignConversationMultiplayerAcceptance() { }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        if (System.getProperty(OUTPUT) == null || reported) return;
        var minecraft = Minecraft.getInstance();
        if (started == 0) started = Util.getMillis();
        require(Util.getMillis() - started < 360_000L, "campaign multiplayer acceptance timed out at " + clientPhase);
        minecraft.options.pauseOnLostFocus = false;
        if (!configured && minecraft.level != null && minecraft.player != null && minecraft.screen == null) {
            minecraft.options.renderDistance().set(6);
            minecraft.options.simulationDistance().set(6);
            minecraft.options.guiScale().set(2);
            minecraft.options.framerateLimit().set(60);
            minecraft.options.enableVsync().set(false);
            VillagerRetaliationConfig.DIALOGUE_TEXT_SPEED.set(DialogueTextSpeed.MEDIUM);
            minecraft.resizeDisplay();
            GLFW.glfwHideWindow(minecraft.getWindow().getWindow());
            configured = true;
        }
        if (host()) {
            if (peerProcess != null && !peerProcess.isAlive()) require(peerProcess.exitValue() == 0, "peer client failed, inspect its process log");
            if (listener == null && configured && minecraft.getSingleplayerServer() != null) {
                leader = minecraft.player.getUUID();
                listener = minecraft.getSingleplayerServer().submit(() -> listen(minecraft.getSingleplayerServer()));
            }
            if (port == 0 && listener != null && listener.isDone()) {
                port = listener.join();
                launchPeer();
            }
            if (read("host-state.state").equals("relaunch") && peerProcess != null && !peerProcess.isAlive()) {
                generation++;
                launchPeer();
                write("host-state.state", "reconnect");
            }
            if (read("host-state.state").equals("finished") && peerProcess != null && !peerProcess.isAlive()) {
                write("result.txt", "Campaign conversation multiplayer acceptance passed\n");
                write("acceptance.json", JSON.toJson(Map.of("transport", "Two native clients over loopback TCP, with a fresh peer process reconnect",
                        "checks", List.of("listeners cannot confirm or take control", "both clients receive speaker changes",
                                "shared impact confirmations precede effects", "explicit controller handover", "revoked permissions pause advances",
                                "saved d20 survives reconnect", "duplicate and stale requests cannot reroll or repeat effects",
                                "both clients receive the final outcome"), "elapsedMilliseconds", Util.getMillis() - started)));
                reported = true;
                minecraft.stop();
                return;
            }
        }
        if (!configured || minecraft.level == null || minecraft.player == null) return;
        if (fixture == null && !read("fixture.json").isBlank()) fixture = JSON.fromJson(read("fixture.json"), CampaignConversationClientFixture.class);
        if (fixture == null) return;
        String phase = read("host-state.state");
        if (!phase.equals(clientPhase)) {
            clientPhase = phase;
            ticks = 0;
            presentationTicks = 0;
            completedTicks = 0;
            step = 0;
            LogUtils.getLogger().info("Campaign multiplayer {} phase: {}", host() ? "host" : "peer", phase);
        }
        ticks++;
        if (phase.equals("ready") || phase.equals("reconnect")) {
            if (minecraft.level.getEntity(fixture.entity()) instanceof Villager) write(host() ? "host-ready.state" : "peer-ready.state", phase);
            return;
        }
        if (!(minecraft.screen instanceof VillagerInteractionScreen screen)) return;
        var control = controller(screen);
        var session = control.session();
        if (session == null || !session.id().equals(fixture.session())) return;
        presentationTicks++;
        if (host()) hostActions(screen, session);
        else peerActions(minecraft, screen, control, session);
    }

    private static void hostActions(VillagerInteractionScreen screen, CampaignConversationViewPayload.Session session) {
        if (clientPhase.equals("story") && session.canControl() && ticks % 15 == 0) {
            if (session.phase() == Phase.CONFIRMATION) {
                if (!chooseIfPresent(screen, localized("more_consequences"))) chooseIfPresent(screen, localized("confirm"));
            } else if (session.phase() == Phase.EXCHANGES) {
                String speaker = session.line().speaker() == null ? "narrator" : session.line().speaker().equals(leader) ? "player" : "mentor";
                if (!read("peer-line.state").equals(session.line().key())) return;
                capture("host-" + speaker);
                chooseIfPresent(screen, localized("continue"));
            } else if (session.phase() == Phase.RESPONSES) {
                chooseIfPresent(screen, session.responses().getFirst().label());
            } else if (session.phase() == Phase.CHECK) {
                write("host-ack.state", "story_done");
            }
        } else if (clientPhase.equals("handover") && session.canControl() && step == 0 && ticks % 10 == 0) {
            var member = session.handover().stream().filter(value -> value.name().equals("CampaignPeer")).findFirst();
            if (member.isPresent() && chooseIfPresent(screen, localized("handover", member.get().name()))) step++;
        } else if (clientPhase.equals("finish") && session.phase() == Phase.COMPLETE && step == 0 && ++completedTicks >= 20) {
            require(!session.outcome().isBlank(), "host receives the final conversation outcome");
            capture("host-outcome");
            write("host-ack.state", "complete");
            step++;
        }
    }

    private static void peerActions(Minecraft minecraft, VillagerInteractionScreen screen,
            CampaignConversationScreenController control, CampaignConversationViewPayload.Session session) {
        require(minecraft.getSingleplayerServer() == null, "peer conversation uses a real multiplayer connection");
        if (clientPhase.equals("listen")) {
            require(!session.canControl() && !session.canTakeControl(), "unchecked party members only listen");
            if (step == 0 && presentationTicks >= 20) {
                require(session.phase() == Phase.CONFIRMATION, "listeners see the same pending impact confirmation");
                capture("peer-listening");
                send(screen, session, Action.TAKE_CONTROL, "");
                send(screen, session, Action.CONFIRM, session.confirmation().toString());
                step++;
                ticks = 0;
            } else if (step == 1 && ticks >= 20) write("peer-ack.state", "listened");
        } else if (clientPhase.equals("story") && session.phase() == Phase.EXCHANGES) {
            require(!session.canControl(), "listener does not gain control while exchanges advance");
            if (!observedLine.equals(session.line().key())) {
                observedLine = session.line().key();
                lineTicks = 0;
            }
            if (++lineTicks >= 40 && !read("peer-line.state").equals(observedLine)) {
                UUID expected = speakers == 0 ? fixture.character() : speakers == 1 ? UUID.fromString(read("host-player.txt")) : null;
                require(speakers < 3 && java.util.Objects.equals(session.line().speaker(), expected), "listener preserves mentor, original player, and narrator identities");
                speakers++;
                capture("peer-speaker-" + session.line().key().replaceAll("[^a-zA-Z0-9_-]", "_"));
                write("peer-line.state", session.line().key());
            }
        } else if (clientPhase.equals("roll")) {
            if (step == 0 && session.canControl() && session.phase() == Phase.CHECK) {
                var request = request(screen, session, Action.ROLL, "");
                write("roll-request.json", JSON.toJson(request));
                PacketDistributor.sendToServer(request);
                step++;
            } else if (step == 1 && session.phase() == Phase.ROLLED && control.dieProgress() >= 0.25F) {
                require(control.dieAnimating(), "the peer animates a server-acknowledged roll");
                capture("peer-rolling");
                choose(screen, localized("skip_roll"));
                require(!control.dieAnimating(), "skipping changes presentation immediately");
                write("saved-die.txt", Integer.toString(session.check().die()));
                ticks = 0;
                step++;
            } else if (step == 2 && ticks >= 10) {
                capture("peer-roll-result");
                write("peer-ack.state", "rolled");
                step++;
            }
        } else if (clientPhase.equals("revoked") && !session.canControl() && !session.canTakeControl()) {
            if (step == 0) {
                capture("peer-revoked");
                send(screen, session, Action.ADVANCE, "");
                send(screen, session, Action.ROLL, "");
                step++;
                ticks = 0;
            } else if (ticks >= 20) write("peer-ack.state", "revoked_done");
        } else if (clientPhase.equals("disconnect")) {
            if (session.canTakeControl() && ticks % 10 == 0) chooseIfPresent(screen, localized("take_control"));
            if (session.canControl() && ticks >= 30) {
                write("peer-ack.state", "disconnected");
                minecraft.disconnect();
                reported = true;
                minecraft.stop();
            }
        } else if (clientPhase.equals("restored")) {
            require(session.check() != null && session.check().resolved()
                    && session.check().die() == Integer.parseInt(read("saved-die.txt")), "reconnecting restores the exact saved d20");
            require(!control.dieAnimating(), "reconnecting does not animate a new roll");
            if (step == 0 && session.canTakeControl() && presentationTicks >= 20) {
                capture("peer-reconnected");
                choose(screen, localized("take_control"));
                step++;
            } else if (step == 1 && session.canControl()) {
                var prior = JSON.fromJson(read("roll-request.json"), CampaignConversationRequestPayload.class);
                PacketDistributor.sendToServer(prior);
                PacketDistributor.sendToServer(new CampaignConversationRequestPayload(view(screen), prior.requestId(), fixture.entity(), fixture.character(),
                        Action.ROLL, fixture.session(), prior.revision(), "", "", 0, 0));
                send(screen, session, Action.ROLL, "");
                PacketDistributor.sendToServer(new CampaignConversationRequestPayload(view(screen), UUID.randomUUID(), fixture.entity(), fixture.character(),
                        Action.ADVANCE, fixture.session(), prior.revision(), "", "", 0, 0));
                ticks = 0;
                step++;
            } else if (step == 2 && ticks >= 20) write("peer-ack.state", "restored_done");
        } else if (clientPhase.equals("finish") && ticks % 15 == 0) {
            if (session.canControl() && (session.phase() == Phase.ROLLED || session.phase() == Phase.EXCHANGES)) {
                chooseIfPresent(screen, localized("continue"));
            } else if (session.phase() == Phase.COMPLETE && read("host-ack.state").equals("complete")) {
                require(!session.outcome().isBlank(), "peer receives the final conversation outcome");
                capture("peer-outcome");
                write("peer-ack.state", "complete");
                reported = true;
                minecraft.stop();
            }
        }
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        if (System.getProperty(OUTPUT) == null || !host() || port == 0 || reported) return;
        var server = event.getServer();
        var host = server.getPlayerList().getPlayer(leader);
        var peer = server.getPlayerList().getPlayerByName("CampaignPeer");
        if (serverPhase.isEmpty() && peer != null) {
            peerId = peer.getUUID();
            require(PartyService.createParty(host).success(), "fixture party is created");
            require(PartyService.acceptInvitation(peer, PartyService.sendInvitation(host, peer).invitationId()).success(), "peer joins the campaign party");
            try { fixture = CampaignConversationClientFixture.prepare(host); }
            catch (IOException exception) { throw new UncheckedIOException(exception); }
            peer.connection.teleport(-2.5, 1, 3.5, -135, 0);
            write("host-player.txt", leader.toString());
            write("fixture.json", JSON.toJson(fixture));
            next("ready");
        } else if (serverPhase.equals("ready") && read("host-ready.state").equals("ready") && read("peer-ready.state").equals("ready")) {
            open(host); open(peer);
            next("listen");
        } else if (serverPhase.equals("listen") && read("peer-ack.state").equals("listened")) {
            var saved = snapshot(host);
            require(saved.phase() == Phase.CONFIRMATION && leader.equals(saved.controller()) && host.totalExperience == 0,
                    "unauthorized confirmation and takeover apply no effects");
            require(saved.participants().contains(peerId), "the peer joined the same session as a listener");
            next("story");
        } else if (serverPhase.equals("story") && read("host-ack.state").equals("story_done")) {
            require(host.totalExperience == 20 && peer.totalExperience == 0, "acceptance and decision rewards belong to the chooser");
            savedOutcome = outcome(server);
            require(savedOutcome.outcome().equals("help"), "the confirmed world outcome commits before the check");
            var campaign = CampaignSavedData.get(server.overworld()).instances().get(ResourceLocation.parse("campaign_fixture:characters"));
            for (var participant : List.of(leader, peerId)) require(campaign.players().get(participant).participation().contains("decision"),
                    "both present participants receive credit for the shared decision");
            require(PartyService.setAdminPrivileges(host, peerId, true).success(), "leader grants conversation authority");
            next("handover");
        } else if (serverPhase.equals("handover") && peerId.equals(snapshot(host).controller())) {
            next("roll");
        } else if (serverPhase.equals("roll") && read("peer-ack.state").equals("rolled")) {
            var saved = snapshot(host);
            savedCheck = saved.checks().get("convince");
            savedReceipts = saved.receipts().size();
            require(savedCheck != null && savedCheck.player().equals(peerId), "the authorized peer owns the saved roll");
            require(PartyService.setAdminPrivileges(host, peerId, false).success(), "leader revokes conversation authority");
            next("revoked");
        } else if (serverPhase.equals("revoked") && read("peer-ack.state").equals("revoked_done")) {
            verifySaved(host, peer);
            require(snapshot(host).controller() == null, "revoking permissions releases the controller");
            require(PartyService.setAdminPrivileges(host, peerId, true).success(), "leader restores conversation authority");
            next("disconnect");
        } else if (serverPhase.equals("disconnect") && read("peer-ack.state").equals("disconnected") && peer == null && !peerProcess.isAlive()) {
            verifySaved(host, null);
            require(snapshot(host).controller() == null, "disconnect pauses the saved conversation");
            next("relaunch");
        } else if (serverPhase.equals("relaunch") && peer != null && generation == 1 && read("peer-ready.state").equals("reconnect")) {
            require(peer.getUUID().equals(peerId), "fresh client reconnects as the same player");
            open(peer);
            next("restored");
        } else if (serverPhase.equals("restored") && read("peer-ack.state").equals("restored_done")) {
            verifySaved(host, peer);
            next("finish");
        } else if (serverPhase.equals("finish") && read("peer-ack.state").equals("complete") && peer == null && !peerProcess.isAlive()) {
            require(snapshot(host).phase() == Phase.COMPLETE, "the saved conversation reaches its authored ending");
            require(snapshot(host).checks().get("convince").equals(savedCheck), "finishing retains the full original check");
            require(outcome(server).equals(savedOutcome) && host.totalExperience == 20, "finishing does not repeat rewards or the world commit");
            next("finished");
        }
    }

    private static void verifySaved(ServerPlayer host, ServerPlayer peer) {
        var saved = snapshot(host);
        require(saved.phase() == Phase.ROLLED && saved.checks().get("convince").equals(savedCheck), "rejected requests preserve the complete roll and branch");
        require(saved.receipts().size() == savedReceipts && saved.choices().size() == 1, "reconnect and duplicate requests do not repeat choices or effects");
        require(outcome(host.server).equals(savedOutcome), "the committed world outcome remains identical");
        require(host.totalExperience == 20 && (peer == null || peer.totalExperience == 0), "rewards are not duplicated or redirected");
    }

    private static ConversationSession.Snapshot snapshot(ServerPlayer player) { return ConversationService.view(player, fixture.session()).session(); }
    private static CampaignInstance.OutcomeCommit outcome(MinecraftServer server) {
        return CampaignSavedData.get(server.overworld()).instances().get(ResourceLocation.parse("campaign_fixture:characters")).outcomes().get("decision");
    }
    private static void open(ServerPlayer player) {
        require(ConversationPresentationService.tryOpen(player, (Villager) player.serverLevel().getEntity(fixture.character())), "saved conversation opens for " + player.getGameProfile().getName());
    }
    private static CampaignConversationRequestPayload request(VillagerInteractionScreen screen, CampaignConversationViewPayload.Session session, Action action, String argument) {
        return new CampaignConversationRequestPayload(view(screen), UUID.randomUUID(), fixture.entity(), fixture.character(), action, session.id(), session.revision(), "", argument, 0, 0);
    }
    private static void send(VillagerInteractionScreen screen, CampaignConversationViewPayload.Session session, Action action, String argument) {
        PacketDistributor.sendToServer(request(screen, session, action, argument));
    }
    private static UUID view(VillagerInteractionScreen screen) {
        try {
            var field = CampaignConversationScreenController.class.getDeclaredField("view"); field.setAccessible(true);
            return (UUID) field.get(controller(screen));
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException("Conversation view identity is unavailable", exception); }
    }
    private static int listen(MinecraftServer server) {
        try {
            var loopback = InetAddress.getByName("127.0.0.1");
            int result;
            try (var socket = new ServerSocket(0, 1, loopback)) { result = socket.getLocalPort(); }
            server.setUsesAuthentication(false);
            server.getConnection().startTcpServerListener(loopback, result);
            return result;
        } catch (IOException exception) { throw new UncheckedIOException(exception); }
    }
    private static void launchPeer() {
        var args = Path.of(System.getProperty(OUTPUT + ".args"));
        var directory = Path.of(System.getProperty(OUTPUT), "peer");
        try {
            var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                    "-Dfml.modFolders=" + System.getProperty("fml.modFolders"),
                    "@" + args.resolve("campaignConversationMultiplayerPeerRunVmArgs.txt"),
                    "-cp", System.getProperty("java.class.path"), "net.neoforged.devlaunch.Main",
                    "@" + args.resolve("campaignConversationMultiplayerPeerRunProgramArgs.txt"),
                    "--quickPlayMultiplayer", "127.0.0.1:" + port)
                    .directory(directory.toFile()).redirectErrorStream(true)
                    .redirectOutput(directory.resolve("process-" + generation + ".log").toFile()).start();
            peerProcess = process;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> { if (process.isAlive()) process.destroy(); }));
        } catch (IOException exception) { throw new UncheckedIOException(exception); }
    }
    private static void next(String phase) {
        serverPhase = phase;
        write("host-state.state", phase);
        LogUtils.getLogger().info("Campaign multiplayer server phase: {}", phase);
    }
    private static boolean host() { return System.getProperty(OUTPUT + ".role", "").equals("host"); }
    private static String read(String name) {
        try {
            var path = Path.of(System.getProperty(OUTPUT), name);
            if (!Files.exists(path)) return "";
            String text = Files.readString(path);
            if (!name.endsWith(".state")) return text;
            int end = text.lastIndexOf('\n');
            return end < 0 ? "" : JSON.fromJson(text.substring(text.lastIndexOf('\n', end - 1) + 1, end), String.class);
        } catch (IOException exception) { throw new UncheckedIOException(exception); }
    }
    private static synchronized void write(String name, String value) {
        if (value.equals(WRITTEN.get(name))) return;
        var path = Path.of(System.getProperty(OUTPUT), name);
        try {
            if (name.endsWith(".state")) {
                Files.writeString(path, JSON.toJson(value) + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } else {
                var temporary = path.resolveSibling(name + ".tmp");
                Files.writeString(temporary, value);
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE);
            }
            WRITTEN.put(name, value);
        } catch (IOException exception) { throw new UncheckedIOException(exception); }
    }
    private static void capture(String name) {
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            image.writeToFile(Path.of(System.getProperty(OUTPUT), name + ".png"));
        } catch (IOException exception) { throw new UncheckedIOException(exception); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
