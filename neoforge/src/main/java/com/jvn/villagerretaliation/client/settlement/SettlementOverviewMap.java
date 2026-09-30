package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

final class SettlementOverviewMap {
    record Marker(SettlementManagementPayload.Entry entry, BlockPos position, boolean lastKnown, int color) { }
    int left;
    int top;
    int width;
    int height;
    private BlockPos center = BlockPos.ZERO;
    private int radius = 1;
    private List<Marker> markers = List.of();

    void layout(int left, int top, int width, int height, BlockPos center, int radius) {
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
        this.center = center;
        this.radius = radius;
    }

    boolean contains(double x, double y) {
        return x >= this.left && x < this.left + this.width && y >= this.top && y < this.top + this.height;
    }

    double worldX(double x) { return this.center.getX() - this.radius + (x - this.left) * this.radius * 2 / this.width; }
    double worldZ(double y) { return this.center.getZ() - this.radius + (y - this.top) * this.radius * 2 / this.height; }
    int x(double value) { return this.left + (int) Math.round((value - this.center.getX() + this.radius) * this.width / (2 * this.radius)); }
    int y(double value) { return this.top + (int) Math.round((value - this.center.getZ() + this.radius) * this.height / (2 * this.radius)); }

    Marker marker(double x, double y) {
        Marker closest = null;
        double distance = 25;
        for (var marker : this.markers) {
            double candidate = Math.pow(x - x(marker.position().getX()), 2) + Math.pow(y - y(marker.position().getZ()), 2);
            if (candidate <= distance) { closest = marker; distance = candidate; }
        }
        return closest;
    }

    void render(GuiGraphics g, Font font, List<com.jvn.villagerretaliation.network.SettlementPlotSnapshot.Plot> plots,
                List<SettlementManagementPayload.Area> areas,
                List<SettlementManagementPayload.Project> projects, List<Marker> markers,
                double cameraX, double cameraZ, float yaw, int mouseX, int mouseY) {
        this.markers = List.copyOf(markers);
        SettlementViewUi.frame(g, this.left - 3, this.top - 3, this.width + 6, this.height + 6);
        g.fill(this.left, this.top, this.left + this.width, this.top + this.height, 0xFF17241C);
        g.enableScissor(this.left + 1, this.top + 1, this.left + this.width - 1, this.top + this.height - 1);
        for (var plot : plots) {
            var chunk = plot.chunk();
            g.fill(x(chunk.getMinBlockX()), y(chunk.getMinBlockZ()), x(chunk.getMaxBlockX() + 1), y(chunk.getMaxBlockZ() + 1),
                    plot.owned() ? 0xFF233D2B : 0xFF303030);
        }
        for (var area : areas) footprint(g, area.min(), area.max(), 0xFF777777);
        for (var project : projects) footprint(g, project.min(), project.max(), 0xFFFFC740);
        for (var marker : markers) {
            int x = x(marker.position().getX());
            int y = y(marker.position().getZ());
            g.fill(x - 1, y - 1, x + 2, y + 2, marker.color());
        }
        int cx = x(cameraX);
        int cy = y(cameraZ);
        g.renderOutline(cx - 3, cy - 3, 7, 7, 0xFFFFFFFF);
        for (int i = 4; i <= 9; i++) {
            int dx = cx - (int) Math.round(Math.sin(Math.toRadians(yaw)) * i);
            int dy = cy + (int) Math.round(Math.cos(Math.toRadians(yaw)) * i);
            g.fill(dx, dy, dx + 1, dy + 1, 0xFFFFFFFF);
        }
        g.disableScissor();
        g.drawString(font, "N", this.left + this.width / 2 - 3, this.top + 3, 0xFFFFFF);
        g.drawString(font, "S", this.left + this.width / 2 - 3, this.top + this.height - 11, 0xFFFFFF);
        g.drawString(font, "W", this.left + 3, this.top + this.height / 2 - 4, 0xFFFFFF);
        g.drawString(font, "E", this.left + this.width - 9, this.top + this.height / 2 - 4, 0xFFFFFF);
        if (contains(mouseX, mouseY)) {
            var marker = marker(mouseX, mouseY);
            if (marker != null) g.renderTooltip(font, Component.literal(SettlementManagementScreen.name(marker.entry()).getString()
                    + (marker.lastKnown() ? " (last known)" : "")), mouseX, mouseY);
            else g.renderTooltip(font, Component.translatable("villagerretaliation.gui.settlement.view_boundary"), mouseX, mouseY);
        }
    }

    private void footprint(GuiGraphics g, BlockPos min, BlockPos max, int color) {
        g.renderOutline(x(min.getX()), y(min.getZ()), Math.max(2, x(max.getX() + 1) - x(min.getX())),
                Math.max(2, y(max.getZ() + 1) - y(min.getZ())), color);
    }
}
