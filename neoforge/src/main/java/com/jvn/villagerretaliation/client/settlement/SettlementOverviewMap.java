package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.network.SettlementPlotSnapshot;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

final class SettlementOverviewMap implements AutoCloseable {
    record Marker(SettlementManagementPayload.Entry entry, BlockPos position, boolean lastKnown, int color) { }
    int left;
    int top;
    int width;
    int height;
    private final SettlementMapTerrain terrain = new SettlementMapTerrain();
    private BlockPos center = BlockPos.ZERO;
    private int boundaryRadius = 1;
    private double radius = 1;
    private double centerX;
    private double centerZ;
    private int zoom;
    private List<Marker> markers = List.of();

    void layout(int left, int top, int width, int height, BlockPos center, int radius) {
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
        this.center = center;
        this.boundaryRadius = radius;
        this.radius = Math.max(Math.min(16, radius), radius / Math.pow(1.5D, this.zoom));
        if (this.zoom == 0) center();
        else clampCenter();
    }

    void center() {
        this.zoom = 0;
        this.radius = this.boundaryRadius;
        this.centerX = this.center.getX();
        this.centerZ = this.center.getZ();
    }

    void zoom(double direction, double focusX, double focusZ) {
        int maximum = Math.max(0, (int) Math.ceil(Math.log(this.boundaryRadius / 16.0D) / Math.log(1.5D)));
        this.zoom = Math.clamp(this.zoom + (direction < 0 ? 1 : -1), 0, maximum);
        this.radius = Math.max(Math.min(16, this.boundaryRadius), this.boundaryRadius / Math.pow(1.5D, this.zoom));
        this.centerX = this.zoom == 0 ? this.center.getX() : focusX;
        this.centerZ = this.zoom == 0 ? this.center.getZ() : focusZ;
        clampCenter();
    }

    private void clampCenter() {
        double margin = this.boundaryRadius - this.radius;
        this.centerX = Math.clamp(this.centerX, this.center.getX() - margin, this.center.getX() + margin);
        this.centerZ = Math.clamp(this.centerZ, this.center.getZ() - margin, this.center.getZ() + margin);
    }

    boolean contains(double x, double y) {
        return x >= this.left && x < this.left + this.width && y >= this.top && y < this.top + this.height;
    }

    boolean coversChrome(double x, double y) {
        return x >= this.left - 27 && x < this.left + this.width + 3 && y >= this.top - 3 && y < this.top + this.height + 19;
    }

    private double blocksPerPixel() { return this.radius * 2.0D / Math.min(this.width, this.height); }
    double worldX(double x) { return this.centerX + (x - this.left - this.width / 2.0D) * blocksPerPixel(); }
    double worldZ(double y) { return this.centerZ + (y - this.top - this.height / 2.0D) * blocksPerPixel(); }
    int x(double value) { return this.left + (int) Math.round(this.width / 2.0D + (value - this.centerX) / blocksPerPixel()); }
    int y(double value) { return this.top + (int) Math.round(this.height / 2.0D + (value - this.centerZ) / blocksPerPixel()); }

    Marker marker(double x, double y) {
        if (!contains(x, y)) return null;
        Marker closest = null;
        double distance = 25;
        for (var marker : this.markers) {
            int mx = x(marker.position().getX() + 0.5D);
            int my = y(marker.position().getZ() + 0.5D);
            if (!contains(mx, my)) continue;
            double candidate = Math.pow(x - mx, 2) + Math.pow(y - my, 2);
            if (candidate <= distance) { closest = marker; distance = candidate; }
        }
        return closest;
    }

    void render(GuiGraphics g, Font font, List<ChunkPos> chunks, List<SettlementPlotSnapshot.Plot> plots,
                List<SettlementManagementPayload.Area> areas, List<SettlementManagementPayload.Project> projects,
                List<Marker> markers, UUID selectedArea, List<Vec3> footprint,
                double focusX, double focusZ, double cameraX, double cameraZ, float yaw, int mouseX, int mouseY) {
        this.markers = List.copyOf(markers);
        SettlementViewUi.frame(g, this.left - 27, this.top - 3, this.width + 30, this.height + 22);
        g.fill(this.left - 1, this.top - 1, this.left + this.width + 1, this.top + this.height + 1, 0xFF080C10);
        g.fill(this.left, this.top, this.left + this.width, this.top + this.height, 0xFF1B2025);
        g.enableScissor(this.left, this.top, this.left + this.width, this.top + this.height);
        this.terrain.update(chunks, worldX(this.left), worldZ(this.top), worldX(this.left + this.width), worldZ(this.top + this.height));
        for (var chunk : chunks) {
            var tile = this.terrain.tile(chunk);
            if (tile == null) continue;
            int x = x(chunk.getMinBlockX());
            int z = y(chunk.getMinBlockZ());
            int sizeX = x(chunk.getMaxBlockX() + 1) - x;
            int sizeZ = y(chunk.getMaxBlockZ() + 1) - z;
            if (sizeX > 0 && sizeZ > 0) g.blit(tile.location, x, z, sizeX, sizeZ, 0, 0,
                    SettlementMapTerrain.TILE_SIZE, SettlementMapTerrain.TILE_SIZE, SettlementMapTerrain.TILE_SIZE, SettlementMapTerrain.TILE_SIZE);
        }
        territory(g, plots);
        for (var area : areas) footprint(g, area.min(), area.max(), area.id().equals(selectedArea) ? 0xFF70CBFF : 0x707CA5BD);
        for (var project : projects) footprint(g, project.min(), project.max(), project.id().equals(selectedArea) ? 0xFF70CBFF : 0xB0E7B64E);
        for (int i = 0; i < footprint.size(); i++) {
            var start = footprint.get(i);
            var end = footprint.get((i + 1) % footprint.size());
            line(g, x(start.x), y(start.z), x(end.x), y(end.z), 0xEFFFFFFF);
        }
        int fx = x(focusX), fz = y(focusZ);
        g.fill(fx - 2, fz, fx + 3, fz + 1, 0xFFFFFFFF);
        g.fill(fx, fz - 2, fx + 1, fz + 3, 0xFFFFFFFF);
        for (var marker : markers) if (marker.entry() instanceof SettlementManagementPayload.Area
                || marker.entry() instanceof SettlementManagementPayload.Project) renderMarker(g, marker);
        g.pose().pushPose();
        // keep resident and issue markers above the GUI item icons
        g.pose().translate(0, 0, 200);
        for (var marker : markers) if (!(marker.entry() instanceof SettlementManagementPayload.Area)
                && !(marker.entry() instanceof SettlementManagementPayload.Project)) renderMarker(g, marker);
        g.pose().popPose();
        int cx = x(cameraX), cz = y(cameraZ);
        double angle = Math.toRadians(yaw);
        int dx = (int) Math.round(-Math.sin(angle) * 5), dz = (int) Math.round(Math.cos(angle) * 5);
        line(g, cx, cz, cx + dx, cz + dz, 0xFFFFFFFF);
        line(g, cx + dx, cz + dz, cx - dz / 2, cz + dx / 2, 0xFFFFFFFF);
        line(g, cx + dx, cz + dz, cx + dz / 2, cz - dx / 2, 0xFFFFFFFF);
        g.disableScissor();
        compass(g, font, "N", this.left + this.width / 2, this.top + 2);
        compass(g, font, "S", this.left + this.width / 2, this.top + this.height - 10);
        compass(g, font, "W", this.left + 6, this.top + this.height / 2 - 4);
        compass(g, font, "E", this.left + this.width - 6, this.top + this.height / 2 - 4);
        int worldX = (int) Math.floor(contains(mouseX, mouseY) ? worldX(mouseX) : focusX);
        int worldZ = (int) Math.floor(contains(mouseX, mouseY) ? worldZ(mouseY) : focusZ);
        var biome = biome(chunks, worldX, worldZ);
        String coordinates = "x: " + worldX + "  z: " + worldZ;
        String status = coordinates + "  |  " + biome.getString();
        g.pose().pushPose();
        g.pose().translate(this.left - 23, this.top + this.height + 5, 0);
        g.pose().scale(0.75F, 0.75F, 1);
        g.drawString(font, font.plainSubstrByWidth(status, (int) ((this.width + 22) / 0.75F)), 0, 0, 0xDFE5EC);
        g.pose().popPose();
        if (contains(mouseX, mouseY)) {
            var marker = marker(mouseX, mouseY);
            var tooltip = marker == null ? Component.literal(coordinates).append(" | ").append(biome)
                    : SettlementManagementScreen.name(marker.entry()).copy().append(marker.lastKnown()
                            ? Component.translatable("villagerretaliation.gui.settlement_view.map_last_known") : Component.empty());
            g.renderTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    private static Component biome(List<ChunkPos> chunks, int x, int z) {
        var level = Minecraft.getInstance().level;
        var position = new ChunkPos(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        var chunk = level == null || !chunks.contains(position) ? null : level.getChunkSource().getChunk(position.x, position.z, false);
        if (chunk == null) return Component.translatable("villagerretaliation.gui.settlement_view.map_unavailable");
        int height = chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
        return chunk.getNoiseBiome(Math.floorDiv(x, 4), Math.floorDiv(height, 4), Math.floorDiv(z, 4)).unwrapKey()
                .map(key -> Component.translatableWithFallback("biome." + key.location().getNamespace() + "."
                        + key.location().getPath().replace('/', '.'), SettlementViewUi.readable(key.location().getPath())))
                .orElseGet(() -> Component.translatable("villagerretaliation.gui.settlement_view.map_unavailable"));
    }

    private void territory(GuiGraphics g, List<SettlementPlotSnapshot.Plot> plots) {
        Set<ChunkPos> owned = plots.stream().filter(SettlementPlotSnapshot.Plot::owned)
                .map(SettlementPlotSnapshot.Plot::chunk).collect(Collectors.toSet());
        for (var chunk : owned) {
            int minX = x(chunk.getMinBlockX()), minZ = y(chunk.getMinBlockZ());
            int maxX = x(chunk.getMaxBlockX() + 1), maxZ = y(chunk.getMaxBlockZ() + 1);
            if (!owned.contains(new ChunkPos(chunk.x - 1, chunk.z))) g.fill(minX, minZ, minX + 1, maxZ, 0x8078D79B);
            if (!owned.contains(new ChunkPos(chunk.x + 1, chunk.z))) g.fill(maxX - 1, minZ, maxX, maxZ, 0x8078D79B);
            if (!owned.contains(new ChunkPos(chunk.x, chunk.z - 1))) g.fill(minX, minZ, maxX, minZ + 1, 0x8078D79B);
            if (!owned.contains(new ChunkPos(chunk.x, chunk.z + 1))) g.fill(minX, maxZ - 1, maxX, maxZ, 0x8078D79B);
        }
    }

    private void renderMarker(GuiGraphics g, Marker marker) {
        int x = x(marker.position().getX() + 0.5D), z = y(marker.position().getZ() + 0.5D);
        if (!contains(x, z)) return;
        if (marker.entry() instanceof SettlementManagementPayload.Area area) {
            badge(g, x, z, areaIcon(area), marker.color());
        } else if (marker.entry() instanceof SettlementManagementPayload.Project) {
            badge(g, x, z, new ItemStack(Items.BRICKS), marker.color());
        } else if (marker.entry() instanceof SettlementManagementPayload.Issue) {
            g.fill(x - 3, z - 3, x + 4, z + 4, 0xFF13191F);
            g.fill(x - 2, z - 2, x + 3, z + 3, marker.color());
            g.fill(x, z - 1, x + 1, z + 1, 0xFF13191F);
            g.fill(x, z + 2, x + 1, z + 3, 0xFF13191F);
        } else {
            g.fill(x - 3, z - 3, x + 4, z + 4, 0xC010171B);
            if (marker.lastKnown()) g.renderOutline(x - 2, z - 2, 5, 5, marker.color());
            else {
                g.fill(x - 2, z - 2, x + 3, z + 3, marker.color());
                g.fill(x - 1, z - 1, x + 1, z, 0xA0FFFFFF);
            }
        }
    }

    private static void badge(GuiGraphics g, int x, int z, ItemStack icon, int color) {
        g.fill(x - 5, z - 5, x + 6, z + 6, 0xFF13191F);
        g.fill(x - 4, z - 4, x + 5, z + 5, 0xFF28495C);
        g.renderOutline(x - 5, z - 5, 11, 11, color);
        SettlementViewUi.item(g, icon, x - 4, z - 4, 9);
    }

    private static ItemStack areaIcon(SettlementManagementPayload.Area area) {
        return new ItemStack(switch (area.kind()) {
            case HOME -> Items.RED_BED;
            case FARM -> Items.WHEAT;
            case FISHERY -> Items.COD;
            case HUNTING_GROUNDS -> Items.BOW;
            case FORESTRY -> Items.OAK_SAPLING;
            case MINE -> Items.IRON_PICKAXE;
            case ANIMAL_PEN -> Items.EGG;
            case KITCHEN -> Items.BREAD;
            case WORKSHOP -> Items.CRAFTING_TABLE;
            case SMELTERY -> Items.FURNACE;
            case BREWERY -> Items.BREWING_STAND;
            case DEPOT -> Items.CHEST;
            case TRADING_POST -> Items.EMERALD;
            case GUARD_POST -> Items.SHIELD;
            case CONSTRUCTION -> Items.BRICKS;
        });
    }

    private static void compass(GuiGraphics g, Font font, String label, int x, int z) {
        int left = x - font.width(label) / 2;
        g.fill(left - 1, z - 1, left + font.width(label) + 1, z + 9, 0x8010151B);
        g.drawString(font, label, left, z, 0xFFFFFF);
    }

    private void footprint(GuiGraphics g, BlockPos min, BlockPos max, int color) {
        g.renderOutline(x(min.getX()), y(min.getZ()), Math.max(2, x(max.getX() + 1) - x(min.getX())),
                Math.max(2, y(max.getZ() + 1) - y(min.getZ())), color);
    }

    private static void line(GuiGraphics g, int x1, int z1, int x2, int z2, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(z2 - z1));
        for (int i = 0; i <= steps; i++) {
            double fraction = steps == 0 ? 0 : (double) i / steps;
            int x = (int) Math.round(x1 + (x2 - x1) * fraction);
            int z = (int) Math.round(z1 + (z2 - z1) * fraction);
            g.fill(x, z, x + 1, z + 1, color);
        }
    }

    @Override
    public void close() {
        this.terrain.close();
        this.markers = List.of();
    }
}
