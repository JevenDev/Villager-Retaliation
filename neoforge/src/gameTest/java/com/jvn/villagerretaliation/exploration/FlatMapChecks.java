package com.jvn.villagerretaliation.exploration;

import java.util.List;

final class FlatMapChecks {
    private static int checks;
    static void run() {
        for (double center : new double[]{0, -8.25, 29_999_000, -29_999_000}) for (double scale : new double[]{.25, 1, 8, 24}) {
            var view = new MapView(640, 360, center, -center, scale);
            for (double pixel : new double[]{0, .5, 160, 320, 639.5}) {
                require(Math.abs(view.screenX(view.worldX(pixel)) - pixel) < .000001, "east-west round trip at world border");
                require(Math.abs(view.screenY(view.worldZ(pixel)) - pixel) < .000001, "north-south round trip at world border");
            }
            require(Math.max(view.width(), view.height()) / view.scale() / (8 << view.level()) <= 44, "view fits bounded tile table");
        }
        var reader = new MapSurfaceSnapshot.Reader(); var colors = new int[64]; var masks = new long[64]; var heights = new int[72];
        for (int stride : new int[]{1, 2, 4, 8}) for (int bit = 0; bit < stride * stride; bit++) {
            var side = new MapSurfaceSnapshot.Layer(new MapSnapshot.Block(100, "side", 0, 0, 40, 0), 60);
            var top = new MapSurfaceSnapshot.Layer(new MapSnapshot.Block(60, "top", 0, 0, 2, 12), 60);
            var buried = new MapSurfaceSnapshot.Layer(new MapSnapshot.Block(50, "buried", 0, 0, 2, 0), 50);
            var source = new MapSurfaceSnapshot(-2, 3, 1, List.of(new MapSurfaceSnapshot.Column(17, List.of(side, top, buried), 1L << bit)), List.of(), stride);
            MapFlatTile.prepare(source, reader, colors, masks, heights, null, cell -> {
                require(cell.state().equals("top"), "flat cells choose the first known upward face"); return 0x123456;
            });
            require(colors[17] == (12 * 17 << 24 | MapFlatTile.shade(0x123456, 220)), "surface color and block light stay separate");
            for (int z = 0; z < stride; z++) for (int x = 0; x < stride; x++)
                require(MapFlatTile.covered(masks[17], stride, x, z) == (x + z * stride == bit), "partial LOD never reveals adjacent unknown cells");
            require(colors[0] == 0 && masks[0] == 0, "unused cells clear when buffers are reused");
        }
        MapFlatTile.prepare(new MapSurfaceSnapshot(0, 0, 0, List.of(), List.of()), reader, colors, masks, heights, null, cell -> 0);
        require(java.util.Arrays.stream(masks).allMatch(mask -> mask == 0), "empty tiles remove previously displayed discovery");
        require(MapFlatTile.brightness(64, 64, 1, 0, 0) == 220, "flat land uses vanilla normal shade");
        require(MapFlatTile.brightness(66, 64, 1, 0, 0) == 255, "south-facing rise uses vanilla high shade");
        require(MapFlatTile.brightness(62, 64, 1, 1, 0) == 180, "north-facing fall uses vanilla low shade");
        require(MapFlatTile.brightness(64, 64, 1, 0, 1) == 255, "shallow water uses high shade");
        require(MapFlatTile.brightness(64, 64, 1, 0, 6) == 220, "medium water uses normal shade");
        require(MapFlatTile.brightness(64, 64, 1, 1, 10) == 180, "deep water uses low shade");
        var northBlock = new MapSnapshot.Block(62, "grass", 0, 0, 2);
        var southBlock = new MapSnapshot.Block(64, "grass", 0, 0, 2);
        var north = new MapSurfaceSnapshot(0, -1, 1, List.of(new MapSurfaceSnapshot.Column(56,
                List.of(new MapSurfaceSnapshot.Layer(northBlock, 62)))), List.of());
        var south = new MapSurfaceSnapshot(0, 0, 1, List.of(new MapSurfaceSnapshot.Column(0,
                List.of(new MapSurfaceSnapshot.Layer(southBlock, 64)))), List.of());
        MapFlatTile.prepare(south, reader, colors, masks, heights, north, cell -> 0xffffff);
        require(colors[0] == 0xffffff, "height shading crosses the north tile boundary without seams");
        var fluid = new MapSnapshot(0, 0, 1, List.of(new MapSnapshot.Column(0, 64, List.of(
                new MapSnapshot.Block(64, "water", 0, 0, 2), new MapSnapshot.Block(55, "water", 0, 0, 0)), true)), List.of());
        var surface = MapSurfaceSnapshot.from(fluid);
        require(surface.columns().getFirst().layers().getFirst().bottom() == 55, "fluid depth survives flat surface conversion");
        var overview = MapOverview.update(MapOverview.empty(0, 0, 2), surface);
        require(overview.columns().getFirst().layers().getFirst().bottom() == 55, "fluid depth survives coarse overview aggregation");
        MapFlatTile.prepare(surface, reader, colors, masks, heights, null, cell -> 0x1ffffff);
        require(colors[0] == 0xb4b4b4, "deep water shades separately from terrain height");
        try {
            java.nio.file.Path root = java.nio.file.Files.createTempDirectory("flat-water-check");
            try (var store = new MapStore(root, "server", "world", "player", "dimension")) {
                store.write(fluid);
                var stored = store.readSurface(0, 0);
                require(stored.sameContent(surface), "stored fluid depth matches direct capture without a save format change");
            }
        } catch (java.io.IOException e) { throw new AssertionError(e); }
        System.out.println("Passed " + checks + " flat map checks");
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
}
