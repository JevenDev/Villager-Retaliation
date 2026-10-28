package com.jvn.villagerretaliation.client.settlement;

import com.google.gson.GsonBuilder;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.settlement.SettlementCityAcceptanceScenario;
import com.jvn.villagerretaliation.settlement.SettlementViewWindow;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = VillagerRetaliation.MOD_ID, value = Dist.CLIENT)
public final class SettlementCityAcceptance {
    private static final String OUTPUT = "villagerretaliation.settlementCityAcceptance";
    private static volatile SettlementCityAcceptanceScenario fixture;
    private static volatile SettlementCityAcceptanceScenario.Snapshot latest;
    private static CompletableFuture<SettlementCityAcceptanceScenario> preparation;
    private static CompletableFuture<java.util.Map<String, SettlementCityAcceptanceScenario.Snapshot>> completion;
    private static UUID playerId;
    private static long started;
    private static long lastReport;
    private static int viewTicks;
    private static Vec3 body;
    private static boolean reported;

    private SettlementCityAcceptance() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (System.getProperty(OUTPUT) == null || reported) return;
        var minecraft = Minecraft.getInstance();
        minecraft.options.pauseOnLostFocus = false;
        if (started == 0L) started = Util.getMillis();
        if (Util.getMillis() - started > 1_800_000L) throw new IllegalStateException("Settlement City acceptance timed out: " + latest);
        if (minecraft.level == null || minecraft.player == null || minecraft.getSingleplayerServer() == null) return;
        if (preparation == null && minecraft.screen == null) {
            minecraft.options.renderDistance().set(6);
            minecraft.options.simulationDistance().set(6);
            minecraft.options.guiScale().set(2);
            minecraft.options.framerateLimit().set(60);
            minecraft.options.enableVsync().set(false);
            minecraft.resizeDisplay();
            playerId = minecraft.player.getUUID();
            preparation = minecraft.getSingleplayerServer().submit(() -> {
                var prepared = SettlementCityAcceptanceScenario.prepare(minecraft.getSingleplayerServer().getPlayerList().getPlayer(playerId));
                minecraft.getSingleplayerServer().tickRateManager().setTickRate(1_000.0F);
                return prepared;
            });
        }
        if (fixture == null && preparation != null && preparation.isDone()) fixture = preparation.join();
        if (latest == null || !latest.phase().equals("VIEW")) return;
        if (minecraft.screen instanceof SettlementManagementScreen screen && !screen.expectingView()) {
            String label = Component.translatable("villagerretaliation.gui.settlement.world_view").getString();
            screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> button.active && button.getMessage().getString().equals(label)).findFirst().ifPresent(Button::onPress);
        }
        if (minecraft.screen instanceof SettlementViewScreen screen) {
            if (++viewTicks == 1) {
                body = minecraft.player.position();
                screen.mouseScrolled(screen.viewportWidth() / 2.0D, 80, 0, -100);
                screen.keyPressed(GLFW.GLFW_KEY_D, 0, 0);
            }
            if (viewTicks == 40) screen.keyReleased(GLFW.GLFW_KEY_D, 0, 0);
            if (viewTicks == 80) {
                require(minecraft.getCameraEntity() instanceof Marker, "City management uses the dedicated camera");
                require(screen.viewHeight() == SettlementViewWindow.MAX_HEIGHT, "the earned City supports bounded maximum zoom");
                require(minecraft.player.position().distanceToSqr(body) < 1.0D, "RTS movement leaves the player body in place");
                screen.keyPressed(GLFW.GLFW_KEY_HOME, 0, 0);
            }
            if (viewTicks == 120) {
                require(minecraft.player.position().distanceToSqr(body) < 1.0D, "recentering leaves the player body in place");
                capture();
                screen.onClose();
                completion = minecraft.getSingleplayerServer().submit(() -> fixture.checkpoints());
            }
        }
        if (viewTicks >= 120 && completion != null && completion.isDone() && minecraft.getCameraEntity() == minecraft.player) {
            writeReport(completion.join());
            reported = true;
            minecraft.stop();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerTick(ServerTickEvent.Post event) {
        if (System.getProperty(OUTPUT) == null || fixture == null || reported) return;
        var player = event.getServer().getPlayerList().getPlayer(playerId);
        require(player != null, "the founding player remains connected");
        fixture.tick(player);
        if (event.getServer().overworld().getGameTime() % 200L == 0 || fixture.readyForView()) latest = fixture.snapshot();
        if (Util.getMillis() - lastReport >= 15_000L) {
            lastReport = Util.getMillis();
            LogUtils.getLogger().info("Settlement City acceptance checkpoint: {}", latest);
            writeProgress();
        }
    }

    private static void writeProgress() {
        try {
            Files.writeString(Path.of(System.getProperty(OUTPUT), "progress.json"), new GsonBuilder().setPrettyPrinting().create().toJson(latest));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void writeReport(java.util.Map<String, SettlementCityAcceptanceScenario.Snapshot> checkpoints) {
        var report = new LinkedHashMap<String, Object>();
        report.put("scenario", "Management-block founding, approved arrivals, actual production, four explicit advances, unloaded support and returned City RTS controls");
        report.put("acceleration", "Native server tick rate 1000, invitations attempted every 20 ticks. Actual configured support interval required at every advancement. No support counters or stages are seeded.");
        report.put("fixture", "Prepared terrain, 75 real beds, one mature wheat field and three mature carrot fields, finite 128 named starter bread, 512 seeds, 64 logs, 256 iron and one house plan. Assigned workers receive tools, one guard receives 64 arrows, and workers receive aptitude 60. Kitchen staffing grows to eleven cooks using five crafting tables. Farmer staffing grows to nine residents across the four fields. Male residents with enabled family and gender rules isolate progression from separately tested family growth.");
        report.put("scope", "Single-player acceptance. Multiplayer transport requires separate validation.");
        report.put("elapsedMilliseconds", Util.getMillis() - started);
        report.put("checkpoints", checkpoints);
        try {
            Files.writeString(Path.of(System.getProperty(OUTPUT), "acceptance.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
            Files.writeString(Path.of(System.getProperty(OUTPUT), "result.txt"), "Settlement City acceptance passed\n");
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        LogUtils.getLogger().info("Settlement City acceptance passed: {}", latest);
    }

    private static void capture() {
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            image.writeToFile(Path.of(System.getProperty(OUTPUT), "city.png"));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
