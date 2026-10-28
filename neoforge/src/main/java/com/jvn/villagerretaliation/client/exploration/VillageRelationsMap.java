package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.client.settlement.VillageMapPopup;
import com.jvn.villagerretaliation.client.settlement.VillageRelationsClient;
import com.jvn.villagerretaliation.client.settlement.VillageRelationsScreen;
import com.jvn.villagerretaliation.exploration.MapView;
import com.jvn.villagerretaliation.network.VillageRelationsPayload;
import com.jvn.villagerretaliation.network.VillageRelationsPayload.Entry;
import com.jvn.villagerretaliation.network.VillageRelationsRequestPayload;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.network.PacketDistributor;

final class VillageRelationsMap {
    private final SettlementCourierMap couriers = new SettlementCourierMap();
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private UUID perspective, selected;
    private Entry origin;
    private int requestId, page;
    private long nextRequest, revision = -1;
    private boolean enabled;
    private VillageMapPopup popup;

    boolean hasPopup() { return popup != null; }
    void closePopup() { popup = null; }
    void toggle() { new MapLayers(false).toggle(MapLayers.Layer.RELATIONS); popup = null; }
    void focus(UUID perspective, UUID selected) {
        this.perspective = perspective; this.selected = selected;
        var layers = new MapLayers(false);
        if (!layers.visible(MapLayers.Layer.RELATIONS)) layers.toggle(MapLayers.Layer.RELATIONS);
        refresh();
    }
    void refresh() { page = 0; nextRequest = 0; requestId = 0; entries.clear(); origin = null; }
    void clear() { enabled = false; perspective = null; selected = null; popup = null; refresh(); couriers.configure(false, null, null); }
    void acceptCouriers(com.jvn.villagerretaliation.network.SettlementCourierMapPayload payload) { couriers.accept(payload); }
    void renderTerrain(GuiGraphics graphics, MapView view, MapLayers layers) {
        if (layers.visible(MapLayers.Layer.COURIERS)) couriers.renderTerrain(graphics, view);
    }
    void tick(MapLayers layers) { tick(layers, false); }
    void tick(MapLayers layers, boolean searching) {
        boolean active = searching || layers.visible(MapLayers.Layer.RELATIONS) || layers.visible(MapLayers.Layer.COURIERS);
        if (active && !enabled) refresh();
        enabled = active;
        couriers.configure(layers.visible(MapLayers.Layer.COURIERS), perspective, origin != null ? origin.id() : selected != null ? selected : perspective);
        couriers.tick();
        if (!enabled || Minecraft.getInstance().player == null || Util.getMillis() < nextRequest) return;
        requestId = VillageRelationsClient.nextRequest();
        PacketDistributor.sendToServer(new VillageRelationsRequestPayload(perspective, selected, null, null,
                VillageRelationsRequestPayload.Action.VIEW, VillageRelationsRequestPayload.View.MAP, page, "", 0, requestId));
        nextRequest = Util.getMillis() + 5000;
    }

    void accept(VillageRelationsPayload payload) {
        if (!enabled || payload.requestId() != requestId || payload.view() != VillageRelationsRequestPayload.View.MAP) return;
        if (payload.index() > 0 && payload.revision() != revision) { refresh(); return; }
        if (payload.index() == 0) entries.clear();
        revision = payload.revision(); origin = payload.origin();
        payload.entries().forEach(entry -> entries.put(entry.id(), entry));
        boolean more = (payload.index() + 1L) * VillageRelationsRequestPayload.PAGE_SIZE < payload.total();
        page = more ? payload.index() + 1 : 0;
        nextRequest = Util.getMillis() + (more ? 300 : 5000);
    }

    List<MapDestination> destinations(MapLayers layers) {
        boolean relations = layers.visible(MapLayers.Layer.RELATIONS);
        return entries.values().stream().map(entry -> new MapDestination(MapDestination.Kind.VILLAGE, entry.id().toString(),
                entry.dimension(), entry.position().getX(), entry.position().getZ(), entry.name(), entry.name()
                + (relations && entry.relationKnown() ? " (" + (entry.standing() > 0 ? "+" : "") + entry.standing() + ")" : ""),
                null, true, relations && entry.relationKnown() ? color(entry.standing()) : 0xffffe8c0, 0)).toList();
    }

    void render(GuiGraphics graphics, MapView view, Font font, MapLayers layers, boolean minimap) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var dimension = level.dimension().location();
        if (layers.visible(MapLayers.Layer.RELATIONS) && origin != null && origin.dimension().equals(dimension)) {
            for (var entry : entries.values()) if (entry.relationKnown() && entry.dimension().equals(dimension)) {
                line(graphics, view, view.screenX(origin.position().getX()), view.screenY(origin.position().getZ()),
                        view.screenX(entry.position().getX()), view.screenY(entry.position().getZ()), color(entry.standing()));
            }
            if (!minimap) graphics.drawCenteredString(font, VillageRelationsScreen.text("map_origin", origin.name()), view.width() / 2, 28, 0xffffff);
        }
        if (layers.visible(MapLayers.Layer.COURIERS)) couriers.render(graphics, view, font, minimap);
    }

    void renderPopup(GuiGraphics graphics, Font font, int x, int y, float partial) { if (popup != null) popup.render(graphics, font, x, y, partial); }
    boolean scroll(double x, double y, double amount) { return popup != null && popup.scroll(x, y, amount); }
    boolean key(int key) {
        if (popup == null) return false;
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { popup = null; return true; }
        return popup.key(key);
    }
    boolean clickOpen(double x, double y, int button) {
        if (popup == null) return false;
        if (!popup.click(x, y, button)) popup = null;
        return true;
    }
    boolean clickCourier(MapView view, MapLayers layers, double x, double y, int button) {
        return layers.visible(MapLayers.Layer.COURIERS) && couriers.click(view, x, y, button);
    }
    boolean click(ExploredMapScreen screen, MapDestination destination, double x, double y, int button) {
        UUID village;
        try { village = UUID.fromString(destination.id()); } catch (IllegalArgumentException invalid) { return false; }
        if (button == 0 && new MapLayers(false).visible(MapLayers.Layer.RELATIONS)) { selected = village; refresh(); return true; }
        popup = new VillageMapPopup(Minecraft.getInstance().font, screen.width, screen.height, village, destination.name(), x, y,
                () -> { popup = null; Minecraft.getInstance().setScreen(new VillageRelationsScreen(screen, perspective, village, null)); },
                () -> { popup = null; focus(perspective, village); }, MapLocationMenus.details(destination), List.of(
                new com.jvn.villagerretaliation.client.settlement.WorldViewMenu.Action(
                        MapLayers.text(MapDestinationTracker.tracking(destination) ? "stop_tracking" : "track_destination"), true,
                        () -> { MapDestinationTracker.toggle(destination); popup = null; }),
                new com.jvn.villagerretaliation.client.settlement.WorldViewMenu.Action(
                        MapLayers.text(MapDestinationTracker.favorite(destination) ? "unfavorite" : "favorite"), true,
                        () -> { MapDestinationTracker.toggleFavorite(destination); popup = null; })));
        Minecraft.getInstance().getNarrator().sayNow(popup.narration());
        return true;
    }
    private static int color(int score) { return score < 0 ? 0xffd28473 : score > 0 ? 0xff8dbe95 : 0xffc9c5b8; }

    static void line(GuiGraphics graphics, MapView view, double x, double y, double endX, double endY, int color) {
        double dx = endX - x, dy = endY - y, lower = 0, upper = 1;
        double[] p = {-dx, dx, -dy, dy}, q = {x, view.width() - x, y, view.height() - y};
        for (int i = 0; i < 4; i++) {
            if (p[i] == 0) { if (q[i] < 0) return; continue; }
            double fraction = q[i] / p[i];
            if (p[i] < 0) lower = Math.max(lower, fraction); else upper = Math.min(upper, fraction);
            if (lower > upper) return;
        }
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)) * (upper - lower)));
        for (int i = 0; i <= steps; i += 3) {
            double fraction = lower + (upper - lower) * i / steps;
            int px = (int) (x + dx * fraction), py = (int) (y + dy * fraction);
            graphics.fill(px, py, px + 2, py + 2, color);
        }
    }
}
