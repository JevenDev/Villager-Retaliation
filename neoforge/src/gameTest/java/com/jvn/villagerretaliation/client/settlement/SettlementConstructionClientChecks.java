package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page;
import com.jvn.villagerretaliation.settlement.SettlementConstructionQueueGameTests;
import com.jvn.villagerretaliation.settlement.SettlementManagementService;
import com.jvn.villagerretaliation.settlement.SettlementProject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Dedicated disposable-world native checks for the queue and actual catalog previews. */
@EventBusSubscriber(modid = VillagerRetaliation.MOD_ID, value = Dist.CLIENT)
public final class SettlementConstructionClientChecks {
    private static final String OUTPUT = "villagerretaliation.constructionClientSmoke";
    private static long started;
    private static int phase;
    private static int ticks;
    private static CompletableFuture<VillageAllegianceId> preparing;
    private static VillageAllegianceId town;
    private static SettlementManagementScreen management;
    private static UUID moved;
    private static net.minecraft.core.BlockPos originalAnchor;

    private SettlementConstructionClientChecks() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (System.getProperty(OUTPUT) == null || phase == 99) return;
        var minecraft = Minecraft.getInstance();
        minecraft.setWindowActive(true);
        minecraft.options.pauseOnLostFocus = false;
        if (started == 0) started = Util.getMillis();
        require(Util.getMillis() - started < 180_000L, "Construction client smoke timed out at phase " + phase);
        if (minecraft.level == null || minecraft.player == null || minecraft.getSingleplayerServer() == null) return;
        if (++ticks < 5) return;
        switch (phase) {
            case 0 -> {
                if (minecraft.screen != null) return;
                minecraft.options.guiScale().set(2);
                minecraft.resizeDisplay();
                var player = minecraft.player.getUUID();
                preparing = minecraft.getSingleplayerServer().submit(() -> SettlementConstructionQueueGameTests.prepareSmoke(
                        minecraft.getSingleplayerServer().getPlayerList().getPlayer(player)));
                next();
            }
            case 1 -> {
                if (!preparing.isDone() || !(minecraft.screen instanceof SettlementManagementScreen current)) return;
                town = preparing.join();
                management = current;
                management.navigateTo(Page.PROJECTS, "");
                next();
            }
            case 2 -> {
                if (management.snapshot().page() != Page.PROJECTS || management.snapshot().entries().size() != 3) return;
                var projects = management.snapshot().entries().stream().map(SettlementManagementPayload.Project.class::cast).toList();
                require(projects.getFirst().status() == SettlementProject.Status.ACTIVE, "Active construction is shown ahead of the pending queue");
                require(SettlementManagementScreen.pendingMove(projects.getFirst(), projects.get(1)) == null, "Active rows cannot begin a move");
                moved = projects.get(2).id();
                capture("queue-before");
                var rows = list(management);
                double x = rows.getX() + 30, from = rows.getY() + 4 + 32 * 2 + 12, to = rows.getY() + 4 + 32 + 12;
                require(rows.mouseClicked(x, from, 0), "Pending row receives mouse input");
                require(rows.mouseDragged(x, to, 0, 0, -32), "Pending row can be dragged");
                require(rows.mouseReleased(x, to, 0), "Dropping a pending row sends its queue move");
                next();
            }
            case 3 -> {
                if (management.submitting() || !management.snapshot().entries().get(1).id().equals(moved)) return;
                capture("queue-reordered");
                var projects = management.snapshot().entries().stream().map(SettlementManagementPayload.Project.class::cast).toList();
                var forged = new SettlementManagementActionPayload(town.value(), new SettlementManagementActionPayload.ReorderProject(
                        projects.getFirst().id(), projects.get(1).id(), projects.get(1).crewRevision()));
                var player = minecraft.player.getUUID();
                String rejected = minecraft.getSingleplayerServer().submit(() -> SettlementConstructionQueueGameTests.rejectSmokeCommand(
                        minecraft.getSingleplayerServer().getPlayerList().getPlayer(player), forged)).join();
                require(!rejected.isEmpty(), "The server rejects forged active-project moves");
                management.newBuilding();
                next();
            }
            case 4 -> {
                if (!(minecraft.screen instanceof SettlementBlueprintScreen catalog) || catalog.thumbnailCount() < 2) return;
                require(!list(catalog).children().isEmpty(), "Small house variants are available");
                capture("house-small-thumbnails");
                click(catalog, "House Medium");
                next();
            }
            case 5 -> {
                var catalog = (SettlementBlueprintScreen) minecraft.screen;
                require(!list(catalog).children().isEmpty(), "Medium house variants are available");
                capture("house-medium");
                click(catalog, "House Large");
                next();
            }
            case 6 -> {
                var catalog = (SettlementBlueprintScreen) minecraft.screen;
                require(!list(catalog).children().isEmpty(), "Large house variants are available");
                list(catalog).children().getFirst().mouseClicked(0, 0, 0);
                capture("house-large");
                click(catalog, SettlementManagementScreen.text("place_prefab").getString());
                next();
            }
            case 7 -> {
                if (!(minecraft.screen instanceof SettlementViewScreen) || management.buildingDraft() == null
                        || management.buildingDraft().preview == null || management.buildingDraft().ghost() == null) return;
                var draft = management.buildingDraft();
                originalAnchor = draft.anchor;
                draft.placed = true;
                draft.move(draft.anchor.east(2));
                draft.rotate(1);
                next();
            }
            case 8 -> {
                var draft = management.buildingDraft();
                if (draft.preview == null || draft.preview.rotation() != Rotation.CLOCKWISE_90 || !draft.preview.anchor().equals(draft.anchor)) return;
                require(draft.anchor.equals(originalAnchor.east(2)), "Moving the blueprint preserves its requested world position");
                require(draft.ghost() != null && !draft.ghost().blocks().isEmpty(), "Rotated preview retains the authored block palette");
                capture("moved-rotated-blueprint");
                minecraft.setScreen(new SettlementBuildingScreen(management));
                next();
            }
            case 9 -> {
                if (!(minecraft.screen instanceof SettlementBuildingScreen)) return;
                capture("construction-materials");
                try {
                    Files.createDirectories(Path.of(System.getProperty(OUTPUT)));
                    Files.writeString(Path.of(System.getProperty(OUTPUT), "result.txt"), "Settlement construction client smoke passed\n"
                            + "Native pending drag reordered server state; active move rejected; house size filters and real template thumbnails; moved rotated blueprint and material UI.\n");
                } catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
                phase = 99;
                minecraft.stop();
            }
            default -> throw new IllegalStateException("Unknown smoke phase");
        }
    }

    private static void next() { phase++; ticks = 0; }

    private static ObjectSelectionList<?> list(Screen screen) {
        return screen.children().stream().filter(ObjectSelectionList.class::isInstance).map(child -> (ObjectSelectionList<?>) child).findFirst().orElseThrow();
    }

    private static void click(Screen screen, String label) {
        var button = screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(candidate -> candidate.getMessage().getString().equals(label)).findFirst().orElseThrow();
        require(button.active, "Button is enabled: " + label);
        button.onPress();
    }

    private static void capture(String name) {
        try {
            var output = Path.of(System.getProperty(OUTPUT));
            Files.createDirectories(output);
            try (var screenshot = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
                screenshot.writeToFile(output.resolve(name + ".png"));
            }
        } catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
