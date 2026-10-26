package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.client.settlement.VillageMapPopup;
import com.jvn.villagerretaliation.client.settlement.VillageRelationsClient;
import com.jvn.villagerretaliation.client.settlement.VillageRelationsScreen;
import com.jvn.villagerretaliation.exploration.MapSnapshot;
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
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

final class VillageRelationsMap {
    private final ExploredMapScreen screen;
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private UUID perspective, selected;
    private Entry origin;
    private int requestId, page;
    private long nextRequest, revision = -1;
    private boolean enabled;
    private VillageMapPopup popup;

    VillageRelationsMap(ExploredMapScreen screen) { this.screen = screen; }

    void toggle() { enabled = !enabled; popup = null; if (enabled) refresh(); }
    void focus(UUID perspective, UUID selected) { this.perspective = perspective; this.selected = selected; enabled = true; refresh(); }
    void refresh() { page = 0; nextRequest = 0; requestId = 0; entries.clear(); origin = null; }
    boolean contains(String id) { return enabled && entries.keySet().stream().anyMatch(key -> key.toString().equals(id)); }

    void tick() {
        if (!enabled || Minecraft.getInstance().player == null || Util.getMillis() < nextRequest) return;
        requestId = VillageRelationsClient.nextRequest();
        PacketDistributor.sendToServer(new VillageRelationsRequestPayload(perspective, selected, null, null,
                VillageRelationsRequestPayload.Action.VIEW, VillageRelationsRequestPayload.View.MAP, page, "", 0, requestId));
        nextRequest = Util.getMillis() + 5000;
    }

    void accept(VillageRelationsPayload payload) {
        if (payload.requestId() != requestId || payload.view() != VillageRelationsRequestPayload.View.MAP) return;
        if (payload.index() > 0 && payload.revision() != revision) { refresh(); return; }
        if (payload.index() == 0) entries.clear();
        revision = payload.revision(); origin = payload.origin();
        payload.entries().forEach(entry -> entries.put(entry.id(), entry));
        boolean more = (payload.index() + 1L) * VillageRelationsRequestPayload.PAGE_SIZE < payload.total();
        page = more ? payload.index() + 1 : 0;
        nextRequest = Util.getMillis() + (more ? 300 : 5000);
    }

    void render(GuiGraphics graphics, MapView view, Font font) {
        var level = Minecraft.getInstance().level;
        if (!enabled || level == null) return;
        var dimension = level.dimension().location();
        if (origin != null && origin.dimension().equals(dimension)) {
            for (var entry : entries.values()) if (entry.relationKnown() && entry.dimension().equals(dimension)) {
                line(graphics, view, view.screenX(origin.position().getX()), view.screenY(origin.position().getZ()),
                        view.screenX(entry.position().getX()), view.screenY(entry.position().getZ()), color(entry.standing()));
            }
        }
        for (var entry : entries.values()) {
            if (!entry.dimension().equals(dimension)) continue;
            int x = (int) view.screenX(entry.position().getX()), y = (int) view.screenY(entry.position().getZ());
            if (x < -16 || y < -16 || x > view.width() + 16 || y > view.height() + 16) continue;
            MapVillageIcon.render(graphics, x, y);
            graphics.drawCenteredString(font, entry.name(), x, y + 10, 0xffe8c0);
            if (entry.id().equals(selected)) graphics.drawCenteredString(font, "[ ]", x, y - 17, 0xffffff);
            if (entry.relationKnown()) graphics.drawString(font, (entry.standing() > 0 ? "+" : entry.standing() < 0 ? "-" : "=")
                    + " " + Math.abs(entry.standing()), x + 10, y - 4, color(entry.standing()));
        }
        if (origin != null) graphics.drawCenteredString(font, VillageRelationsScreen.text("map_origin", origin.name()), view.width() / 2, 28, 0xffffff);
    }

    void renderPopup(GuiGraphics graphics, Font font, int x, int y, float partial) { if (popup != null) popup.render(graphics, font, x, y, partial); }
    boolean scroll(double x, double y, double amount) { return popup != null && popup.scroll(x, y, amount); }
    boolean key(int key) {
        if (popup == null) return false;
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { popup = null; return true; }
        return popup.key(key);
    }

    boolean click(Font font, MapView view, List<MapSnapshot.Marker> terrain, double x, double y, int button) {
        if (popup != null) { if (popup.click(x, y, button)) return true; popup = null; }
        var level = Minecraft.getInstance().level;
        if (level == null || button != 1 && (button != 0 || !enabled)) return false;
        UUID id = null; String name = "";
        if (enabled) for (var entry : entries.values()) {
            if (entry.dimension().equals(level.dimension().location()) && near(view, entry.position().getX(), entry.position().getZ(), x, y)) {
                id = entry.id(); name = entry.name(); break;
            }
        }
        if (id == null) for (var marker : terrain) {
            if (near(view, marker.x(), marker.z(), x, y)) {
                try { id = UUID.fromString(marker.id()); } catch (IllegalArgumentException invalid) { continue; }
                name = marker.name(); break;
            }
        }
        if (id == null) return false;
        UUID village = id;
        if (button == 0) { selected = village; refresh(); return true; }
        popup = new VillageMapPopup(font, view.width(), view.height(), village, name, x, y,
                () -> { popup = null; Minecraft.getInstance().setScreen(new VillageRelationsScreen(screen, perspective, village, null)); },
                () -> { popup = null; selected = village; enabled = true; refresh(); });
        Minecraft.getInstance().getNarrator().sayNow(popup.narration());
        return true;
    }

    private static boolean near(MapView view, int worldX, int worldZ, double x, double y) {
        return Math.abs(view.screenX(worldX) - x) <= 10 && Math.abs(view.screenY(worldZ) - y) <= 10;
    }

    private static int color(int score) { return score < 0 ? 0xffd28473 : score > 0 ? 0xff8dbe95 : 0xffc9c5b8; }

    private static void line(GuiGraphics graphics, MapView view, double x, double y, double endX, double endY, int color) {
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
