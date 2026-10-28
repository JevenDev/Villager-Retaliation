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
        else if (x >= 0 && y >= 34 && y < screen.height - 32) {
            var location = MapLocationsClient.at(screen.view(), x, y);
            if (location != null) graphics.renderComponentTooltip(font, List.of(Component.literal(location.name()),
                    text("coordinates", location.x(), location.z()), text("edit_hint")), x, y);
        }
    }

    private void show(List<Component> details, List<Action> actions) {
        menu = new WorldViewMenu(Minecraft.getInstance().font, screen.width, screen.height, anchorX, anchorY, details, actions);
    }

    void list() {
        anchorX = 154; anchorY = screen.height - 30;
        var actions = new ArrayList<Action>();
        actions.add(new Action(text("share_all"), !MapLocationsClient.all().isEmpty(), () -> share(null)));
        for (var location : MapLocationsClient.all().stream().sorted(Comparator.comparing(MapLocation::name, String.CASE_INSENSITIVE_ORDER)).toList())
            actions.add(new Action(Component.literal(location.name()), true, () -> selected(location), MapLocationsClient.icon(location.icon())));
        show(List.of(text("title"), text("count", MapLocationsClient.all().size(), MapLocation.MAX_LOCATIONS)), actions);
    }

    boolean clickOpen(double x, double y, int button) {
        if (menu == null) return false;
        if (menu.click(x, y, button)) return true;
        close();
        return true;
    }

    boolean clickMarker(double x, double y, int button) {
        if (button != 0 && button != 1 || y < 34 || y >= screen.height - 32) return false;
        var location = MapLocationsClient.at(screen.view(), x, y);
        if (location == null) return false;
        anchorX = (int) x; anchorY = (int) y;
        selected(location);
        return true;
    }

    boolean place(double x, double y, int button) {
        if (button != 1 || y < 34 || y >= screen.height - 32 || Minecraft.getInstance().level == null) return false;
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
        show(List.of(Component.literal(location.name()), text("coordinates", location.x(), location.z()),
                Component.literal(location.dimension().toString())), List.of(
                new Action(text("focus"), sameDimension, () -> { close(); screen.focusLocation(location); }),
                new Action(text("edit"), true, () -> edit(location)),
                new Action(text("share"), true, () -> share(location.id())),
                new Action(text("delete"), true, () -> confirmDelete(location))));
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
