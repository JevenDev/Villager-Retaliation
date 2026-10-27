package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.network.SettlementBoundsPayload;
import com.jvn.villagerretaliation.network.SettlementBoundsRequestPayload;
import com.jvn.villagerretaliation.network.SettlementPlotSnapshot;
import com.jvn.villagerretaliation.sell.CurrencyAmount;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SettlementBoundsClient {
    private static UUID town;
    private static ResourceLocation dimension;
    private static ClientPacketListener connection;
    private static SettlementPlotSnapshot snapshot;
    private static int requestId;
    private static long refreshAt;
    private static long receivedAt;

    private SettlementBoundsClient() { }

    public static boolean enabled(UUID id) { return id != null && id.equals(town); }

    public static void toggle(UUID id) {
        var minecraft = Minecraft.getInstance();
        boolean disable = enabled(id);
        clear();
        if (id == null || disable || minecraft.level == null || minecraft.player == null || minecraft.getConnection() == null) return;
        town = id;
        dimension = minecraft.level.dimension().location();
        connection = minecraft.getConnection();
        request();
    }

    private static void request() {
        PacketDistributor.sendToServer(new SettlementBoundsRequestPayload(town, ++requestId));
        refreshAt = Util.getMillis() + 2_000L;
    }

    public static void accept(SettlementBoundsPayload payload) {
        var minecraft = Minecraft.getInstance();
        if (!enabled(payload.town()) || payload.requestId() != requestId || minecraft.getConnection() != connection
                || minecraft.level == null || !minecraft.level.dimension().location().equals(dimension)
                || !payload.dimension().equals(dimension)) return;
        if (!payload.allowed()) { clear(); return; }
        snapshot = new SettlementPlotSnapshot(new SettlementPlotSnapshot.Totals(payload.plots().size(),
                payload.plots().size(), 0, 0L, CurrencyAmount.ZERO, ""), payload.plots().stream()
                .map(plot -> new SettlementPlotSnapshot.Plot(plot, true, false, 0L, "", "")).toList());
        receivedAt = Util.getMillis();
    }

    static void tick() {
        if (town == null) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != connection || minecraft.level == null || minecraft.player == null
                || !minecraft.player.isAlive() || minecraft.player.isSpectator()
                || !minecraft.level.dimension().location().equals(dimension)) {
            clear();
            return;
        }
        long now = Util.getMillis();
        if (now >= refreshAt) request();
        if (receivedAt > 0 && now - receivedAt > 8_000L) snapshot = null;
    }

    static void render(RenderLevelStageEvent event) {
        if (town != null && snapshot != null) SettlementPlotRenderer.render(event, snapshot, null, null, true);
    }

    static void clear() {
        town = null;
        dimension = null;
        connection = null;
        snapshot = null;
        receivedAt = 0L;
        refreshAt = 0L;
    }
}
