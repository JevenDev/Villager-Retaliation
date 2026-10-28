package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.exploration.MapLocation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = "villagerretaliation", value = Dist.CLIENT)
public final class MapLocationClientSmoke {
    private static final String OUTPUT = "villagerretaliation.mapLocationClientSmoke";
    private static int phase, ticks;
    private static long start;
    private static ExploredMapScreen map;
    private static MapLocation saved;
    private static int worldX, worldZ;
    private static List<MapLocation> backgroundLocations;
    private static MapLocation coveredLocation;
    private static boolean occlusionChecked;

    @SubscribeEvent public static void beforeRender(ScreenEvent.Render.Pre event) {
        if (System.getProperty(OUTPUT) == null || phase != 1 || !(event.getScreen() instanceof MapLocationEditScreen editor)) return;
        var mc = Minecraft.getInstance();
        int top = (int) field(editor, "top");
        coveredLocation = new MapLocation(java.util.UUID.randomUUID(), mc.level.dimension().location(),
                (int) Math.floor(map.view().worldX(editor.width / 2.0)),
                (int) Math.floor(map.view().worldZ(top + 152)), MapLocation.Icon.EMERALD, "Aspenview", "", true);
        backgroundLocations = MapLocationsClient.all();
        MapLocationsClient.accept(new com.jvn.villagerretaliation.network.MapLocationsPayload(List.of(coveredLocation)));
    }

    @SubscribeEvent public static void afterRender(ScreenEvent.Render.Post event) {
        if (backgroundLocations == null || !(event.getScreen() instanceof MapLocationEditScreen)) return;
        event.getGuiGraphics().flush();
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            double scaleX = image.getWidth() / (double) event.getScreen().width;
            double scaleY = image.getHeight() / (double) event.getScreen().height;
            int center = (int) map.view().screenX(coveredLocation.x());
            int top = (int) map.view().screenY(coveredLocation.z()) + 10;
            int halfWidth = mc.font.width(coveredLocation.name()) / 2 + 2;
            int background = image.getPixelRGBA((int) ((center - halfWidth - 4) * scaleX), (int) ((top + 2) * scaleY));
            int leaking = 0;
            for (int y = (int) (top * scaleY); y < (top + mc.font.lineHeight) * scaleY; y++) {
                for (int x = (int) ((center - halfWidth) * scaleX); x < (center + halfWidth) * scaleX; x++) {
                    int color = image.getPixelRGBA(x, y);
                    if (color != background) leaking++;
                }
            }
            image.writeToFile(Path.of(System.getProperty(OUTPUT), "marker-editor-occlusion.png"));
            require(leaking == 0, "map label cannot show through the editor panel (leaking pixels: " + leaking + ")");
            occlusionChecked = true;
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
        finally {
            MapLocationsClient.accept(new com.jvn.villagerretaliation.network.MapLocationsPayload(backgroundLocations));
            backgroundLocations = null;
        }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (System.getProperty(OUTPUT) == null || phase == 99) return;
        var mc = Minecraft.getInstance();
        if (start == 0) start = System.nanoTime();
        require(System.nanoTime() - start < 180_000_000_000L, "marker smoke timed out at phase " + phase);
        mc.options.pauseOnLostFocus = false;
        if (mc.level == null || mc.player == null || mc.getOverlay() != null || ExploredMapClient.repository() == null) return;
        if (++ticks < 10) return;
        ticks = 0;
        switch (phase) {
            case 0 -> {
                com.jvn.villagerretaliation.client.settlement.SettlementViewPanelClientChecks.verifyPopup();
                VillagerRetaliationConfig.ANIMATE_EXPLORED_MAP_OPENING.set(false);
                map = new ExploredMapScreen(); mc.setScreen(map);
                int x = map.width / 2 + 30, y = map.height / 2 + 20;
                worldX = (int) Math.floor(map.view().worldX(x)); worldZ = (int) Math.floor(map.view().worldZ(y));
                require(map.mouseClicked(x, y, 1), "right click opens placement");
                map.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                require(mc.screen instanceof MapLocationEditScreen, "placement opens native editor");
                fields(mc.screen).get(0).setValue("Village supplies and food");
                fields(mc.screen).get(1).setValue("Supplies");
                button(mc.screen, MapLocationsClient.text("hover").getString()).onPress();
                phase = 1;
            }
            case 1 -> {
                require(occlusionChecked, "editor occlusion was checked on a rendered frame");
                screenshot("marker-editor.png");
                for (var icon : MapLocation.Icon.values()) require(!MapLocationsClient.icon(icon).isEmpty(), "every icon resolves to an item");
                var editor = (MapLocationEditScreen) mc.screen;
                editor.children().stream().filter(child -> child instanceof Button button && button.getMessage().getString().startsWith("Icon:"))
                        .map(Button.class::cast).findFirst().orElseThrow().onPress();
                phase = 2;
            }
            case 2 -> {
                screenshot("marker-icons.png");
                var editor = (MapLocationEditScreen) mc.screen;
                Object menu = field(editor, "menu"), popup = field(menu, "popup");
                int x = (int) field(popup, "left") + 8, y = (int) field(popup, "top") + 8;
                double zoom = map.view().scale();
                editor.mouseScrolled(x, y, 0, -1);
                editor.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
                editor.mouseClicked(x, y, 0);
                require(field(editor, "icon") == MapLocation.Icon.IRON_SWORD, "wheel and keyboard select item icons");
                require(map.view().scale() == zoom, "menu scroll does not zoom map");
                editor.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                MapLocationsClient.accept(new com.jvn.villagerretaliation.network.MapLocationsPayload(MapLocationsClient.all()));
                require(mc.screen == editor && field(editor, "pending") != null, "unrelated sync cannot acknowledge an outstanding save");
                phase = 3;
            }
            case 3 -> {
                if (!(mc.screen instanceof ExploredMapScreen)) return;
                saved = MapLocationsClient.all().stream().filter(location -> location.name().equals("Village supplies and food")).findFirst().orElseThrow();
                require(saved.x() == worldX && saved.z() == worldZ, "cursor placement retains exact map coordinates");
                require(saved.previewName().equals("Supplies") && saved.alwaysShowName(), "both names and visibility survive server save");
                require(saved.icon() == MapLocation.Icon.IRON_SWORD, "selected item survives server save");
                map.focusLocation(saved);
                phase = 4;
            }
            case 4 -> {
                screenshot("marker-map.png");
                map.mouseClicked(map.width / 2.0, map.height / 2.0, 1);
                phase = 5;
            }
            case 5 -> {
                screenshot("marker-actions.png");
                map.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
                map.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                require(mc.screen instanceof MapLocationEditScreen, "existing marker opens editor");
                fields(mc.screen).get(0).setValue("Discarded edit");
                button(mc.screen, MapLocationsClient.text("always").getString()).onPress();
                mc.screen.onClose();
                require(MapLocationsClient.all().contains(saved), "cancel does not mutate saved data");
                map.mouseClicked(map.width / 2.0, map.height / 2.0, 0);
                map.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
                map.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
                map.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                phase = 6;
            }
            case 6 -> {
                screenshot("marker-sharing.png");
                map.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                require(mc.screen == map, "escape closes popup before the map");
                map.mouseClicked(map.width / 2.0, map.height / 2.0, 0);
                for (int i = 0; i < 3; i++) map.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
                map.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                map.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                phase = 7;
            }
            case 7 -> {
                require(MapLocationsClient.all().stream().noneMatch(location -> location.id().equals(saved.id())), "confirmed delete syncs to client");
                try {
                    Files.writeString(Path.of(System.getProperty(OUTPUT), "result.txt"),
                            "Map location client smoke passed\nPlacement, item textures, scroll selection, server save, names, labels, cancel, sharing menu and delete passed\n");
                } catch (IOException failure) { throw new UncheckedIOException(failure); }
                phase = 99; mc.stop();
            }
            default -> throw new AssertionError("Unexpected smoke phase");
        }
    }

    private static List<EditBox> fields(Screen screen) {
        return screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();
    }
    private static Button button(Screen screen, String text) {
        return screen.children().stream().filter(child -> child instanceof Button button && button.getMessage().getString().equals(text))
                .map(Button.class::cast).findFirst().orElseThrow();
    }
    private static Object field(Object target, String name) {
        try { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void screenshot(String name) {
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            image.writeToFile(Path.of(System.getProperty(OUTPUT), name));
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
