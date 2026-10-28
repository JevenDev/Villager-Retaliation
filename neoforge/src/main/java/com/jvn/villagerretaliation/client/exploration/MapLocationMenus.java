package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.client.party.PartyRosterClient;
import com.jvn.villagerretaliation.client.settlement.WorldViewMenu;
import com.jvn.villagerretaliation.client.settlement.WorldViewMenu.Action;
import com.jvn.villagerretaliation.exploration.MapLocation;
import com.jvn.villagerretaliation.network.MapLocationRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import static com.jvn.villagerretaliation.client.exploration.MapLocationsClient.text;

final class MapLocationMenus {
    private final ExploredMapScreen screen;
    private WorldViewMenu menu;
    private int anchorX, anchorY;

    MapLocationMenus(ExploredMapScreen screen) { this.screen = screen; }

    void close() { menu = null; }
    void hover(double x, double y) { if (menu != null) menu.hover(x, y); }
    void render(GuiGraphics graphics, Font font, int x, int y, float partial) {
        if (menu != null) menu.render(graphics, font, x, y, partial);
        else if (x >= 0 && y >= 64 && y < screen.height - 64) {
            var group = screen.hoveredMarker(x, y);
            if (group != null) {
                var destination = group.members().getFirst();
                graphics.renderComponentTooltip(font, group.members().size() > 1
                        ? List.of(MapLayers.text("cluster", group.members().size()), MapLayers.text("cluster_hint"))
                        : List.of(Component.literal(destination.name()), text("coordinates", (int) destination.x(), (int) destination.z())), x, y);
            }
        }
    }

    private void show(List<Component> details, List<Action> actions) {
        menu = new WorldViewMenu(Minecraft.getInstance().font, screen.width, screen.height, anchorX, anchorY, details, actions);
    }

    private void list() {
        close();
        Minecraft.getInstance().setScreen(new MapSearchScreen(screen));
    }

    boolean clickOpen(double x, double y, int button) {
        if (menu == null) return false;
        if (menu.click(x, y, button)) return true;
        close();
        return true;
    }

    boolean clickMarker(double x, double y, int button) {
        if (button != 0 && button != 1 || y < 64 || y >= screen.height - 64) return false;
        var group = screen.markerAt(x, y);
        if (group == null) return false;
        anchorX = (int) x; anchorY = (int) y;
        if (group.members().size() > 1) {
            show(List.of(MapLayers.text("cluster", group.members().size())), group.members().stream().map(destination ->
                    new Action(Component.literal(destination.name()), true, () -> open(destination))).toList());
        } else if (group.members().getFirst().kind() == MapDestination.Kind.VILLAGE && button == 0) {
            screen.selectMarker(group.members().getFirst());
            return screen.openVillage(group.members().getFirst(), x, y, button);
        } else open(group.members().getFirst());
        return true;
    }

    void shareAll() { anchorX = 154; anchorY = screen.height - 30; share(null); }

    void open(MapDestination destination, int x, int y) { anchorX = x; anchorY = y; open(destination); }

    private void open(MapDestination destination) {
        screen.selectMarker(destination);
        if (destination.kind() == MapDestination.Kind.LOCATION) {
            MapLocationsClient.all().stream().filter(location -> location.id().toString().equals(destination.id())).findFirst().ifPresent(this::selected);
        } else if (destination.kind() == MapDestination.Kind.VILLAGE) {
            close(); screen.openVillage(destination, anchorX, anchorY, 1);
        } else {
            var quest = com.jvn.villagerretaliation.client.quest.VillagerQuestTrackerOverlay.journalEntries().stream()
                    .filter(entry -> entry.questId().equals(destination.id()) && entry.state().equals("active")).findFirst().orElse(null);
            if (quest == null) { close(); return; }
            var details = new ArrayList<>(details(destination));
            if (!quest.objective().isBlank()) details.add(Component.literal(quest.objective()));
            if (!quest.status().isBlank()) details.add(Component.literal(quest.status()));
            if (destination.radius() > 0) details.add(MapLayers.text("quest_radius", destination.radius()));
            var actions = new ArrayList<Action>();
            actions.add(new Action(text("focus"), sameDimension(destination), () -> { close(); screen.focusDestination(destination); }));
            actions.addAll(destinationActions(destination));
            actions.add(new Action(MapLayers.text("open_journal"), true, () -> {
                close(); Minecraft.getInstance().setScreen(new com.jvn.villagerretaliation.client.interaction.QuestJournalScreen(
                        com.jvn.villagerretaliation.client.quest.VrQuestJournalSource.INSTANCE, screen, destination.id()));
            }));
            var source = com.jvn.villagerretaliation.client.quest.VrQuestJournalSource.INSTANCE.dataSource();
            actions.add(new Action(MapLayers.text(source.isTracked(quest) ? "untrack_quest" : "track_quest"), quest.trackable(), () -> {
                source.toggleTracking(quest); close();
            }));
            show(details, actions);
        }
    }

    boolean place(double x, double y, int button) {
        if (button != 1 || y < 64 || y >= screen.height - 64 || Minecraft.getInstance().level == null) return false;
        anchorX = (int) x; anchorY = (int) y;
        var view = screen.view();
        int worldX = (int) Math.clamp(Math.floor(view.worldX(x)), -29_999_984, 29_999_984);
        int worldZ = (int) Math.clamp(Math.floor(view.worldZ(y)), -29_999_984, 29_999_984);
        var location = new MapLocation(UUID.randomUUID(), Minecraft.getInstance().level.dimension().location(),
                worldX, worldZ, MapLocation.Icon.EMERALD, text("default_name").getString(), "", false);
        show(List.of(text("coordinates", worldX, worldZ)), List.of(
                new Action(text("create"), MapLocationsClient.all().size() < MapLocation.MAX_LOCATIONS, () -> edit(location)),
                new Action(text("title"), true, this::list)));
        return true;
    }

    private void selected(MapLocation location) {
        boolean sameDimension = Minecraft.getInstance().level != null
                && location.dimension().equals(Minecraft.getInstance().level.dimension().location());
        var destination = MapDestination.location(location);
        var actions = new ArrayList<Action>(List.of(
                new Action(text("focus"), sameDimension, () -> { close(); screen.focusDestination(destination); }),
                new Action(text("edit"), true, () -> edit(location)),
                new Action(text("share"), true, () -> share(location.id())),
                new Action(text("delete"), true, () -> confirmDelete(location))));
        actions.addAll(destinationActions(destination));
        show(details(destination), actions);
    }

    static boolean sameDimension(MapDestination destination) {
        return Minecraft.getInstance().level != null && destination.dimension().equals(Minecraft.getInstance().level.dimension().location());
    }
    static List<Component> details(MapDestination destination) {
        return List.of(Component.literal(destination.name()), text("coordinates", (int) destination.x(), (int) destination.z()),
                Component.literal(destination.dimension().toString()), MapDestinationTracker.distance(destination));
    }
    List<Action> destinationActions(MapDestination destination) {
        return List.of(new Action(MapLayers.text(MapDestinationTracker.tracking(destination) ? "stop_tracking" : "track_destination"), true, () -> {
                    MapDestinationTracker.toggle(destination); close();
                }), new Action(MapLayers.text(MapDestinationTracker.favorite(destination) ? "unfavorite" : "favorite"), true, () -> {
                    MapDestinationTracker.toggleFavorite(destination); close();
                }));
    }

    private void edit(MapLocation location) {
        close();
        Minecraft.getInstance().setScreen(new MapLocationEditScreen(screen, location));
    }

    private void confirmDelete(MapLocation location) {
        show(List.of(text("delete_confirm", location.name())), List.of(
                new Action(text("delete"), true, () -> {
                    PacketDistributor.sendToServer(new MapLocationRequest(MapLocationRequest.Action.DELETE, null,
                            location.id(), MapLocationRequest.Audience.PARTY, null));
                    close();
                }),
                new Action(Component.translatable("gui.cancel"), true, () -> selected(location))));
    }

    private void share(UUID selected) {
        show(List.of(text(selected == null ? "share_all" : "share"), text("share_hint")), List.of(
                new Action(text("party"), PartyRosterClient.roster().active(), () -> send(selected, MapLocationRequest.Audience.PARTY, null)),
                new Action(text("online"), true, () -> confirmOnline(selected)),
                new Action(text("player"), true, () -> players(selected))));
    }

    private void confirmOnline(UUID selected) {
        show(List.of(text("online_confirm")), List.of(
                new Action(text("online"), true, () -> send(selected, MapLocationRequest.Audience.ONLINE, null)),
                new Action(Component.translatable("gui.cancel"), true, () -> share(selected))));
    }

    private void players(UUID selected) {
        var mc = Minecraft.getInstance();
        var actions = new ArrayList<Action>();
        if (mc.getConnection() != null && mc.player != null) {
            mc.getConnection().getOnlinePlayers().stream()
                    .filter(player -> !player.getProfile().getId().equals(mc.player.getUUID()))
                    .sorted(Comparator.comparing(player -> player.getProfile().getName(), String.CASE_INSENSITIVE_ORDER))
                    .forEach(player -> actions.add(new Action(Component.literal(player.getProfile().getName()), true,
                            () -> send(selected, MapLocationRequest.Audience.PLAYER, player.getProfile().getId()))));
        }
        show(List.of(text(actions.isEmpty() ? "no_recipients" : "player")), actions);
    }

    private void send(UUID selected, MapLocationRequest.Audience audience, UUID recipient) {
        PacketDistributor.sendToServer(new MapLocationRequest(MapLocationRequest.Action.SHARE, null, selected, audience, recipient));
        close();
    }

    boolean scroll(double x, double y, double amount) {
        return menu != null && menu.scroll(x, y, amount);
    }

    boolean key(int key) {
        if (menu == null) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        return menu.key(key);
    }
}
