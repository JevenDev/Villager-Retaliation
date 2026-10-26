package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.client.settlement.VillageRelationsClient;
import com.jvn.villagerretaliation.exploration.MapView;
import com.jvn.villagerretaliation.network.SettlementCourierMapPayload;
import com.jvn.villagerretaliation.network.SettlementCourierMapRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SettlementCourierMap {
    private final Map<UUID, SettlementCourierMapPayload.Courier> couriers = new LinkedHashMap<>();
    private final Map<Long, SettlementCourierMapPayload.Cell> cells = new LinkedHashMap<>();
    private final List<BlockPos> route = new ArrayList<>();
    private UUID perspective, village, selected;
    private net.minecraft.resources.ResourceLocation dimension;
    private boolean enabled, routeComplete;
    private int requestId, page, routeKey;
    private long epoch, after, nextRequest;
    public static void receive(SettlementCourierMapPayload payload) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof ExploredMapScreen map && minecraft.player != null && minecraft.level != null) map.acceptCouriers(payload);
    }
    void configure(boolean enabled, UUID perspective, UUID village) {
        var level = Minecraft.getInstance().level;
        var dimension = level == null ? null : level.dimension().location();
        if (this.enabled != enabled || !Objects.equals(this.perspective, perspective) || !Objects.equals(this.village, village)
                || !Objects.equals(this.dimension, dimension)) {
            this.dimension = dimension;
            this.enabled = enabled; this.perspective = perspective; this.village = village; this.selected = null;
            couriers.clear(); cells.clear(); route.clear(); page = 0; after = 0; epoch = 0; requestId = 0; nextRequest = 0; routeComplete = false;
        }
    }
    void tick() {
        if (!enabled || Minecraft.getInstance().player == null || Util.getMillis() < nextRequest) return;
        requestId = VillageRelationsClient.nextRequest();
        PacketDistributor.sendToServer(new SettlementCourierMapRequest(perspective, village, selected, page,
                routeComplete ? 65536 : route.size(), epoch, after, requestId));
        nextRequest = Util.getMillis() + 5000;
    }
    void accept(SettlementCourierMapPayload payload) {
        if (!enabled || payload.requestId() != requestId || !Objects.equals(village, payload.village())) return;
        if (payload.page() == 0) couriers.clear();
        payload.couriers().forEach(courier -> couriers.put(courier.id(), courier));
        page = payload.moreJourneys() ? payload.page() + 1 : 0;
        if (!payload.terrainAllowed() || epoch != payload.epoch()) cells.clear();
        epoch = payload.epoch(); after = payload.next();
        payload.cells().forEach(cell -> cells.put(net.minecraft.world.level.ChunkPos.asLong(cell.position().getX(), cell.position().getZ()), cell));
        while (cells.size() > 16384) cells.remove(cells.keySet().iterator().next());
        boolean restart = false;
        if (selected == null || !selected.equals(payload.routeJourney())) { route.clear(); routeComplete = false; }
        else {
            if (routeKey != payload.routeKey()) { route.clear(); routeKey = payload.routeKey(); routeComplete = false; }
            if (payload.routeOffset() == route.size()) {
                if (route.size() + payload.route().size() <= 65536) route.addAll(payload.route());
                routeComplete = !payload.moreRoute();
            } else if (!payload.route().isEmpty() || !routeComplete) { route.clear(); routeComplete = false; restart = true; }
        }
        nextRequest = Util.getMillis() + (payload.moreJourneys() || payload.moreTerrain() || payload.moreRoute() || restart ? 600 : 3000);
    }
    void renderTerrain(GuiGraphics graphics, MapView view) {
        if (!enabled) return;
        for (var cell : cells.values()) {
            double x = view.screenX(cell.position().getX()), y = view.screenY(cell.position().getZ());
            if (x < -view.scale() || y < -view.scale() || x > view.width() || y > view.height()) continue;
            graphics.fill((int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(x + view.scale()), (int) Math.ceil(y + view.scale()), 0xff000000 | cell.color());
        }
    }
    void render(GuiGraphics graphics, MapView view, Font font) {
        if (!enabled) return;
        for (int index = 1; index < route.size(); index++) {
            var first = route.get(index - 1); var second = route.get(index);
            VillageRelationsMap.line(graphics, view, view.screenX(first.getX() + 0.5), view.screenY(first.getZ() + 0.5),
                    view.screenX(second.getX() + 0.5), view.screenY(second.getZ() + 0.5), 0xffe2bd75);
        }
        for (var courier : couriers.values()) {
            int x = (int) view.screenX(courier.position().getX() + 0.5), y = (int) view.screenY(courier.position().getZ() + 0.5);
            if (x < -12 || y < -12 || x > view.width() + 12 || y > view.height() + 12) continue;
            int color = courier.stage() == SettlementCourierMapPayload.Stage.BLOCKED ? 0xffd28473 : courier.observed() ? 0xfff0d8a0 : 0xffb6bcc6;
            graphics.drawCenteredString(font, courier.observed() ? "◆" : "◇", x, y - 4, color);
            if (courier.id().equals(selected)) graphics.drawCenteredString(font, "[ ]", x, y - 14, color);
        }
        var selectedCourier = couriers.get(selected);
        if (selectedCourier != null) {
            var label = text(selectedCourier.observed() ? "observed" : "estimated").copy().append(": ")
                    .append(text("stage." + selectedCourier.stage().name().toLowerCase(java.util.Locale.ROOT)));
            if (!selectedCourier.name().isEmpty()) label.append(" / ").append(selectedCourier.name());
            graphics.drawCenteredString(font, label, view.width() / 2, view.height() - 44, 0xffe8c0);
            if (!selectedCourier.blocker().isEmpty()) graphics.drawCenteredString(font,
                    Component.translatable("villagerretaliation.gui.aid.blocker." + selectedCourier.blocker()), view.width() / 2, view.height() - 56, 0xffd49b);
        }
    }
    boolean click(MapView view, double x, double y, int button) {
        if (!enabled || button != 0) return false;
        for (var courier : couriers.values()) {
            if (Math.abs(view.screenX(courier.position().getX() + 0.5) - x) > 8 || Math.abs(view.screenY(courier.position().getZ() + 0.5) - y) > 8) continue;
            selected = courier.id(); route.clear(); routeComplete = false; nextRequest = 0;
            Minecraft.getInstance().getNarrator().sayNow(text(courier.observed() ? "observed" : "estimated")); return true;
        }
        return false;
    }
    private static Component text(String key) { return Component.translatable("villagerretaliation.gui.courier." + key); }
}
