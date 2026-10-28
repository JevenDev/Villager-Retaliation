package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.client.config.VillagerRetaliationClientPreferences;
import com.jvn.villagerretaliation.client.interaction.QuestJournalScreen;
import com.jvn.villagerretaliation.client.quest.VillagerQuestTrackerOverlay;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.exploration.MapLocation;
import com.jvn.villagerretaliation.network.MapLocationsPayload;
import com.jvn.villagerretaliation.network.QuestTrackerSyncPayload;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

final class MapFeatureClientChecks {
    private static int phase, fullLayers, minimapLayers;
    private static boolean minimap;
    private static List<MapLocation> originalLocations, fixtureLocations;
    private static QuestTrackerSyncPayload originalQuests;
    private static MapDestination destination, quest;

    static boolean tick(ExploredMapScreen map) {
        var mc = Minecraft.getInstance();
        switch (phase) {
            case 0 -> {
                originalLocations = MapLocationsClient.all();
                originalQuests = new QuestTrackerSyncPayload(VillagerQuestTrackerOverlay.entries(),
                        VillagerQuestTrackerOverlay.trackedEntries().stream().map(QuestTrackerSyncPayload.Entry::questId).toList(),
                        false, VillagerQuestTrackerOverlay.questlineNodes());
                fullLayers = VillagerRetaliationConfig.FULL_MAP_LAYERS.get(); minimapLayers = VillagerRetaliationConfig.MINIMAP_LAYERS.get();
                minimap = VillagerRetaliationConfig.SHOW_EXPLORED_MAP_MINIMAP.get();
                VillagerRetaliationConfig.FULL_MAP_LAYERS.set(7); VillagerRetaliationConfig.MINIMAP_LAYERS.set(6);
                verifyMinimapLabels();
                var dimension = mc.level.dimension().location();
                int x = mc.player.getBlockX() + 200, z = mc.player.getBlockZ() + 100;
                var local = new MapLocation(UUID.randomUUID(), dimension, x, z, MapLocation.Icon.EMERALD, "Needle supplies", "Supplies", true);
                var neighbor = new MapLocation(UUID.randomUUID(), dimension, x + 1, z, MapLocation.Icon.IRON_SWORD, "Needle shelter", "Shelter", true);
                var remote = new MapLocation(UUID.randomUUID(), net.minecraft.resources.ResourceLocation.withDefaultNamespace("the_nether"),
                        0, 0, MapLocation.Icon.EMERALD, "Needle remote", "Remote", true);
                fixtureLocations = List.of(local, neighbor, remote);
                mc.getSingleplayerServer().submit(() -> {
                    var data = com.jvn.villagerretaliation.exploration.MapLocationSavedData.get(mc.getSingleplayerServer().overworld());
                    fixtureLocations.forEach(location -> data.put(mc.player.getUUID(), location));
                }).join();
                MapLocationsClient.accept(new MapLocationsPayload(fixtureLocations));
                destination = MapDestination.location(local);
                var entry = new QuestTrackerSyncPayload.Entry("map-smoke-survey", "Survey the valley", "Find the old watchtower", "", .25f, true,
                        "active", "In progress", "Cartographer", "Village", List.of()).withJournal(new QuestTrackerSyncPayload.Journal(
                        "", List.of(), "", "", "", 0, false, -1, -1, new QuestTrackerSyncPayload.Waypoint(dimension.toString(), x + 80, 64, z - 60, 24)));
                VillagerQuestTrackerOverlay.accept(new QuestTrackerSyncPayload(List.of(entry), "", false));
                quest = map.destinations().stream().filter(value -> value.kind() == MapDestination.Kind.QUEST && value.id().equals(entry.questId())).findFirst().orElseThrow();
                map.focusDestination(destination);
                require(map.markerAt(map.width / 2.0, map.height / 2.0).members().size() == 1, "focused destination remains accessible outside the neighboring cluster");
                map.showDestination(destination); activate(map, 4);
                require(MapDestinationTracker.tracking(destination), "the marker's track action selects its destination");
                var updated = new MapLocation(local.id(), dimension, x + 12, z, local.icon(), "Updated supplies", "Supplies", true);
                MapLocationsClient.accept(new MapLocationsPayload(List.of(updated, neighbor, remote))); MapDestinationTracker.refresh();
                require(MapDestinationTracker.target().name().equals(updated.name()) && MapDestinationTracker.target().x() == updated.x(),
                        "tracking follows renamed or moved markers");
                MapLocationsClient.accept(new MapLocationsPayload(fixtureLocations)); MapDestinationTracker.refresh();
                map.showDestination(destination); activate(map, 5);
                require(MapDestinationTracker.favorite(destination), "the marker's favorite action saves its destination");
                set(VillagerRetaliationClientPreferences.class, null, "loaded", false);
                ((java.util.Set<?>) field(VillagerRetaliationClientPreferences.class, null, "mapFavorites")).clear();
                require(MapDestinationTracker.favorite(destination), "favorites survive reloading client preferences");
                var layers = new MapLayers(false); layers.toggle(MapLayers.Layer.LOCATIONS);
                var hidden = map.markerAt(map.width / 2.0, map.height / 2.0);
                require(hidden == null || hidden.members().stream().noneMatch(value -> value.kind() == MapDestination.Kind.LOCATION), "hidden personal markers cannot receive clicks");
                layers.toggle(MapLayers.Layer.LOCATIONS);
                button(map, MapLocationsClient.text("title").getString()).onPress();
                require(mc.screen instanceof MapSearchScreen, "locations opens unified search");
                search(mc.screen).setValue("NEEDLE");
                require(rows(mc.screen).children().size() == 2, "search combines names with the current dimension filter");
                phase++;
            }
            case 1 -> {
                capture("map-search.png");
                search(mc.screen).setValue("needle supplies"); mc.screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                require(mc.screen == map && map.view().x() == destination.x(), "keyboard search focuses the chosen destination");
                map.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                button(map, MapLayers.text("layers").getString()).onPress();
                button(mc.screen, MapLayers.text("minimap").getString()).onPress();
                button(mc.screen, MapLayers.text("layer_off", MapLayers.Layer.VILLAGE_NAMES.title()).getString()).onPress();
                require(new MapLayers(true).visible(MapLayers.Layer.VILLAGE_NAMES) && new MapLayers(false).visible(MapLayers.Layer.VILLAGE_NAMES), "minimap layer changes preserve the full map setting");
                button(mc.screen, MapLayers.text("layer_on", MapLayers.Layer.QUESTS.title()).getString()).onPress();
                require(!new MapLayers(true).visible(MapLayers.Layer.QUESTS) && new MapLayers(false).visible(MapLayers.Layer.QUESTS), "quest layers are independent for the minimap");
                phase++;
            }
            case 2 -> {
                capture("map-layers.png"); mc.screen.onClose();
                button(map, MapLocationsClient.text("title").getString()).onPress();
                button(mc.screen, MapLayers.text("favorites_all").getString()).onPress();
                require(rows(mc.screen).children().size() == 1, "favorites filter finds only the saved favorite");
                phase++;
            }
            case 3 -> {
                capture("map-favorites.png"); mc.screen.onClose();
                map.focusDestination(quest); map.showDestination(quest); phase++;
            }
            case 4 -> {
                capture("map-quest-actions.png"); activate(map, 3);
                require(mc.screen instanceof QuestJournalScreen, "quest marker opens the journal");
                require(field(QuestJournalScreen.class, mc.screen, "selectedQuestId").equals(quest.id()), "journal opens the matching quest");
                phase++;
            }
            case 5 -> {
                capture("map-quest-journal.png");
                var journal = (QuestJournalScreen) mc.screen; journal.onClose();
                set(QuestJournalScreen.class, journal, "animationStartMillis", net.minecraft.Util.getMillis() - 1000);
                journal.tick();
                require(mc.screen == map && map.transitionProgress() == 1 && map.view().x() == quest.x(), "closing the journal restores the same open map and camera");
                button(map, MapLayers.text("follow_off").getString()).onPress(); phase++;
            }
            case 6 -> {
                require(map.view().x() == mc.player.getX() && map.view().z() == mc.player.getZ(), "follow centers the map on the moving player");
                capture("map-tracking.png");
                map.mouseClicked(map.width / 2.0 + 40, map.height / 2.0 + 40, 0);
                map.mouseDragged(map.width / 2.0 + 60, map.height / 2.0 + 40, 0, 20, 0); map.mouseReleased(0, 0, 0);
                require(!(boolean) field(ExploredMapScreen.class, map, "following"), "manual dragging turns off follow");
                button(map, MapLayers.text("follow_off").getString()).onPress();
                map.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0); map.keyReleased(GLFW.GLFW_KEY_RIGHT, 0, 0);
                require(!(boolean) field(ExploredMapScreen.class, map, "following"), "arrow navigation turns off follow");
                VillagerRetaliationConfig.SHOW_EXPLORED_MAP_MINIMAP.set(true);
                mc.setScreen(null); phase++;
            }
            case 7 -> {
                capture("map-minimap-tracking.png");
                MapDestinationTracker.toggle(quest);
                VillagerQuestTrackerOverlay.accept(originalQuests); MapDestinationTracker.refresh();
                require(MapDestinationTracker.target() == null, "completed or removed quest destinations stop tracking");
                MapDestinationTracker.toggle(destination);
                mc.getSingleplayerServer().submit(() -> {
                    var data = com.jvn.villagerretaliation.exploration.MapLocationSavedData.get(mc.getSingleplayerServer().overworld());
                    fixtureLocations.forEach(location -> data.remove(mc.player.getUUID(), location.id()));
                }).join();
                MapLocationsClient.accept(new MapLocationsPayload(originalLocations)); MapDestinationTracker.refresh();
                require(MapDestinationTracker.target() == null, "deleted marker destinations stop tracking");
                MapDestinationTracker.toggleFavorite(destination);
                VillagerRetaliationConfig.FULL_MAP_LAYERS.set(fullLayers); VillagerRetaliationConfig.MINIMAP_LAYERS.set(minimapLayers);
                VillagerRetaliationConfig.SHOW_EXPLORED_MAP_MINIMAP.set(minimap); VillagerRetaliationConfig.CONFIG.save();
                mc.setScreen(map); phase++; return true;
            }
            default -> { return true; }
        }
        return false;
    }
    private static void verifyMinimapLabels() {
        var mc = Minecraft.getInstance();
        var labels = new java.util.ArrayList<String>();
        var graphics = new net.minecraft.client.gui.GuiGraphics(mc, mc.renderBuffers().bufferSource()) {
            @Override public int drawString(net.minecraft.client.gui.Font font, String text, int x, int y, int color) {
                labels.add(text);
                return font.width(text);
            }
            @Override public void fill(int left, int top, int right, int bottom, int color) { }
            @Override public void renderItem(net.minecraft.world.item.ItemStack item, int x, int y) { }
        };
        var view = new com.jvn.villagerretaliation.exploration.MapView(320, 240, mc.player.getX(), mc.player.getZ(), 1);
        var dimension = mc.level.dimension().location();
        var location = new MapDestination(MapDestination.Kind.LOCATION, "label-location", dimension, view.x() - 70, view.z(),
                "Supplies", "Supplies", MapLocation.Icon.EMERALD, true, 0xffffffff, 0);
        var quest = new MapDestination(MapDestination.Kind.QUEST, "label-quest", dimension, view.x() + 70, view.z(),
                "Survey", "Survey", null, true, 0xffffffff, 0);
        var groups = List.of(new MapMarkerLayout.Group(90, 120, List.of(location)), new MapMarkerLayout.Group(230, 120, List.of(quest)));
        MapMarkerOverlay.render(graphics, view, mc.font, groups, new MapLayers(true), "", -1, -1, true);
        require(labels.containsAll(List.of("Supplies", "Survey")), "hiding village names preserves minimap location and quest labels");
    }

    private static void activate(Screen screen, int index) {
        for (int i = 0; i < index; i++) screen.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
        screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
    }
    private static Button button(Screen screen, String label) {
        return screen.children().stream().filter(child -> child instanceof Button button && button.getMessage().getString().equals(label))
                .map(Button.class::cast).findFirst().orElseThrow(() -> new AssertionError("Missing button: " + label));
    }
    private static EditBox search(Screen screen) { return screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow(); }
    private static ObjectSelectionList<?> rows(Screen screen) { return screen.children().stream().filter(ObjectSelectionList.class::isInstance).map(child -> (ObjectSelectionList<?>) child).findFirst().orElseThrow(); }
    private static Object field(Class<?> type, Object target, String name) {
        try { var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(target); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void set(Class<?> type, Object target, String name, Object value) {
        try { var field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void capture(String name) {
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            image.writeToFile(Path.of(System.getProperty("villagerretaliation.mapLocationClientSmoke"), name));
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
