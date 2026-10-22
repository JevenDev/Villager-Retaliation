package com.jvn.villagerretaliation.quest.site;

import com.jvn.villagerretaliation.client.exploration.ExploredMapClient;
import com.jvn.villagerretaliation.client.exploration.ExploredMapScreen;
import com.jvn.villagerretaliation.client.quest.VillagerQuestTrackerOverlay;
import com.jvn.villagerretaliation.network.QuestTrackerSyncPayload;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = "villagerretaliation", value = Dist.CLIENT)
public final class QuestSiteClientSmoke {
    private static final String OUTPUT = "villagerretaliation.questSiteClientSmoke";
    private static int phase, ticks, startupTicks;
    private static long started;
    private static CompletableFuture<List<Preview>> pending;
    private QuestSiteClientSmoke() { }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (System.getProperty(OUTPUT) == null || phase == 99) return;
        var mc = Minecraft.getInstance();
        if (started == 0) started = System.nanoTime();
        if (System.nanoTime() - started > 180_000_000_000L) throw new AssertionError("Quest map client smoke timed out at phase " + phase);
        if (mc.level == null && ++startupTicks % 100 == 0) {
            screenshot("startup.png");
            try { Files.writeString(Path.of(System.getProperty(OUTPUT), "startup.txt"),
                    String.valueOf(mc.screen) + " overlay=" + mc.getOverlay()); }
            catch (IOException exception) { throw new UncheckedIOException(exception); }
        }
        mc.setWindowActive(true);
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof com.jvn.villagerretaliation.client.BetaWarningScreen warning) {
            warning.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                    .map(net.minecraft.client.gui.components.Button.class::cast)
                    .filter(button -> button.active && button.getMessage().getString().equals(Component.translatable(
                            "screen.villagerretaliation.beta_warning.acknowledge").getString()))
                    .findFirst().ifPresent(net.minecraft.client.gui.components.Button::onPress);
            return;
        }
        if (mc.screen instanceof com.jvn.villagerretaliation.client.campaign.CampaignSelectScreen selection) {
            try {
                var field = selection.getClass().getDeclaredField("campaigns");
                field.setAccessible(true);
                var campaigns = (List<?>) field.get(selection);
                var campaign = campaigns.stream().map(com.jvn.villagerretaliation.campaign.selection.WorldCampaign.class::cast)
                        .filter(value -> value.id().equals(com.jvn.villagerretaliation.campaign.selection.WorldCampaign.DEFAULT))
                        .findFirst().orElseThrow();
                selection.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                        .map(net.minecraft.client.gui.components.Button.class::cast)
                        .filter(button -> button.active && button.getMessage().getString().equals(campaign.name()))
                        .findFirst().orElseThrow().onPress();
            } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
            return;
        }
        if (mc.level == null || mc.player == null || mc.getOverlay() != null) return;
        if (phase == 0) {
            try { Files.createDirectories(Path.of(System.getProperty(OUTPUT))); }
            catch (IOException exception) { throw new UncheckedIOException(exception); }
            UUID player = mc.player.getUUID();
            pending = mc.getSingleplayerServer().submit(() -> previews(mc.getSingleplayerServer().getPlayerList().getPlayer(player)));
            phase = 1;
        }
        if (phase == 1 && pending.isDone()) {
            var previews = pending.join();
            for (var preview : previews) {
                var data = MapItemSavedData.load(preview.tag, mc.level.registryAccess());
                data.addClientSideDecorations(preview.decorations);
                mc.level.overrideMapData(preview.id, data);
            }
            mc.setScreen(new PhysicalMaps(previews));
            phase = 2;
            ticks = 0;
        }
        if (phase == 2 && ++ticks >= 40) {
            screenshot("physical-quest-maps.png");
            verifyPhysicalMaps();
            String dimension = mc.level.dimension().location().toString();
            int x = mc.player.blockPosition().getX(), z = mc.player.blockPosition().getZ();
            var entries = List.of(entry("Exact destination", new QuestTrackerSyncPayload.Waypoint(dimension, x - 80, 64, z)),
                    entry("Search area", new QuestTrackerSyncPayload.Waypoint(dimension, x + 80, 0, z, 48)));
            VillagerQuestTrackerOverlay.accept(new QuestTrackerSyncPayload(entries, List.of(), false));
            mc.setScreen(new ExploredMapScreen());
            try {
                var zoom = ExploredMapScreen.class.getDeclaredField("zoom");
                zoom.setAccessible(true);
                zoom.set(mc.screen, 1.0);
            } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
            phase = 3;
            ticks = 0;
        }
        if (phase == 3 && ExploredMapClient.repository() != null && ++ticks >= 100) {
            screenshot("exploration-quest-markers.png");
            try { Files.writeString(Path.of(System.getProperty(OUTPUT), "result.txt"), "Physical quest maps and exploration marker client smoke passed\n"); }
            catch (IOException exception) { throw new UncheckedIOException(exception); }
            phase = 99;
            mc.stop();
        }
    }

    private static QuestTrackerSyncPayload.Entry entry(String title, QuestTrackerSyncPayload.Waypoint waypoint) {
        return new QuestTrackerSyncPayload.Entry("test:" + (waypoint.radius() > 0 ? "circle" : "exact"), title, "", "", "",
                0, false, "active", "", "", "", List.of(), List.of(), List.of(), List.of(), false, false,
                QuestTrackerSyncPayload.Journal.EMPTY.withRuntime(-1, -1, waypoint));
    }

    private static List<Preview> previews(ServerPlayer player) {
        var quest = VillagerQuestResources.quest(player.server, ResourceLocation.parse("villagerretaliation:missing_neighbor")).orElseThrow();
        var site = new QuestSiteSavedData.Site(UUID.randomUUID(), player.getUUID(), quest.id(), null,
                player.level().dimension().location(), player.blockPosition());
        site.position = player.blockPosition().offset(300, 0, 0);
        QuestSiteSavedData.get(player.serverLevel()).sites.put(site.id, site);
        player.getInventory().clearContent();
        QuestSiteMaps.give(player, quest, site, true);
        var circle = preview(player);
        player.getInventory().clearContent();
        site.discovered = true;
        QuestSiteMaps.give(player, quest, site, true);
        var exact = preview(player);
        if (exact.decorations.stream().noneMatch(value -> value.type().equals(net.minecraft.world.level.saveddata.maps.MapDecorationTypes.RED_X)))
            throw new AssertionError("Exact quest maps must contain a red X decoration");
        return List.of(circle, exact);
    }

    private static Preview preview(ServerPlayer player) {
        var map = player.getInventory().items.stream().filter(stack -> stack.get(DataComponents.MAP_ID) != null).findFirst().orElseThrow();
        var data = MapItem.getSavedData(map, player.serverLevel());
        data.tickCarriedBy(player, map);
        return new Preview(map.get(DataComponents.MAP_ID), data.save(new CompoundTag(), player.registryAccess()),
                java.util.stream.StreamSupport.stream(data.getDecorations().spliterator(), false).toList());
    }

    private static void verifyPhysicalMaps() {
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            int red = 0, exactRed = 0, green = 0;
            for (int y = 80; y < image.getHeight() - 80; y++) for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getPixelRGBA(x, y);
                int r = pixel & 255, g = pixel >> 8 & 255, b = pixel >> 16 & 255;
                if (r > 120 && r > g * 1.8 && r > b * 1.8) {
                    red++;
                    if (x > image.getWidth() / 2) exactRed++;
                }
                if (g > r * 1.1 && g > b * 1.1) green++;
            }
            if (red < 100 || exactRed < 10 || green < 1000) throw new AssertionError("Physical maps must visibly render terrain, a search circle, and an exact marker");
        }
    }

    private static void screenshot(String name) {
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Files.createDirectories(Path.of(System.getProperty(OUTPUT)));
            image.writeToFile(Path.of(System.getProperty(OUTPUT), name));
        } catch (IOException exception) { throw new UncheckedIOException(exception); }
    }

    private record Preview(MapId id, CompoundTag tag, List<net.minecraft.world.level.saveddata.maps.MapDecoration> decorations) { }

    private static final class PhysicalMaps extends Screen {
        private final List<Preview> previews;
        PhysicalMaps(List<Preview> previews) {
            super(Component.literal("Quest map previews"));
            this.previews = previews;
        }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
            graphics.fill(0, 0, width, height, 0xff24272a);
            graphics.drawCenteredString(font, title, width / 2, 20, 0xffffff);
            for (int i = 0; i < previews.size(); i++) {
                int x = width / 2 - 276 + i * 288, y = height / 2 - 120;
                graphics.fill(x - 5, y - 5, x + 261, y + 261, 0xffd7c39b);
                graphics.pose().pushPose();
                graphics.pose().translate(x, y, 100);
                graphics.pose().scale(2, 2, -1);
                var preview = previews.get(i);
                minecraft.gameRenderer.getMapRenderer().render(graphics.pose(), minecraft.renderBuffers().bufferSource(),
                        preview.id, minecraft.level.getMapData(preview.id), false, 15728880);
                graphics.flush();
                graphics.pose().popPose();
                graphics.drawCenteredString(font, i == 0 ? "Search circle" : "Exact marker", x + 128, y + 270, 0xffffff);
            }
        }
        @Override public boolean isPauseScreen() { return false; }
    }
}
