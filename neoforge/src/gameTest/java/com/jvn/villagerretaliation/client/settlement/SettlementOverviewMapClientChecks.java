package com.jvn.villagerretaliation.client.settlement;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

final class SettlementOverviewMapClientChecks {
    private SettlementOverviewMapClientChecks() { }

    static void verify(SettlementViewScreen screen, BlockPos center) {
        var map = new SettlementOverviewMap();
        map.layout(10, 20, 100, 60, new BlockPos(-128, 64, -256), 80);
        require(map.worldX(60) == -128 && map.worldZ(50) == -256, "the map center preserves negative world coordinates");
        require(map.x(-112) - map.x(-128) == map.y(-240) - map.y(-256), "rectangular layouts keep equal block scale on both axes");
        double originalSpan = map.worldX(110) - map.worldX(10);
        map.zoom(-1, -120, -248);
        require(map.worldX(110) - map.worldX(10) < originalSpan, "map zoom increases terrain detail");
        for (int i = 0; i < 100; i++) map.zoom(-1, -120, -248);
        require(map.worldZ(80) - map.worldZ(20) == 32, "map zoom stops at sixteen blocks of radius");
        map.center();
        require(map.worldX(110) - map.worldX(10) == originalSpan, "centering restores the settlement overview");
        require(!map.contains(110, 20) && map.coversChrome(5, 85), "map chrome has a separate hit region from terrain");
        require(SettlementMapTerrain.tint(0xFF0000FF, 0xFF0000, 1) == 0xFF0000FF,
                "native image red survives RGB biome tinting");
        require(SettlementMapTerrain.tint(0xFFFF0000, 0x0000FF, 1) == 0xFFFF0000,
                "native image blue survives RGB biome tinting");
        verifyTerrain(center);
        var overview = screen.overviewMap();
        double span = overview.worldX(overview.left + overview.width) - overview.worldX(overview.left);
        double focusX = screen.focusX(), focusZ = screen.focusZ();
        float height = screen.viewHeight();
        screen.mouseScrolled(overview.left + overview.width / 2.0D, overview.top + overview.height / 2.0D, 0, 1);
        require(overview.worldX(overview.left + overview.width) - overview.worldX(overview.left) < span,
                "scrolling over the overview zooms its terrain");
        require(screen.viewHeight() == height && screen.focusX() == focusX && screen.focusZ() == focusZ,
                "map zoom preserves the world camera");
        overview.center();
    }

    private static void verifyTerrain(BlockPos center) {
        var level = Minecraft.getInstance().level;
        var roof = center.offset(-5, 4, 2);
        var position = new ChunkPos(roof);
        var chunk = level.getChunkSource().getChunk(position.x, position.z, false);
        require(chunk != null, "the map checks use a loaded settlement chunk");
        var original = chunk.getBlockState(roof);
        try (var terrain = new SettlementMapTerrain()) {
            terrain.update(List.of(position), position.getMinBlockX(), position.getMinBlockZ(),
                    position.getMaxBlockX() + 1, position.getMaxBlockZ() + 1);
            var tile = terrain.tile(position);
            require(tile != null && terrain.size() == 1, "authorized loaded terrain creates a map tile");
            int roofColor = pixel(tile, roof);
            int groundColor = pixel(tile, center.offset(-1, -1, 8));
            require((roofColor & 255) > (roofColor >> 16 & 255), "the map shows the wooden roof above the red bed");
            require((groundColor >> 8 & 255) > (groundColor & 255), "grass receives its actual biome tint");
            require(roofColor != groundColor, "buildings and terrain have distinct surface colors");
            try {
                chunk.setBlockState(roof, Blocks.RED_WOOL.defaultBlockState(), false);
                tile.updated = 0;
                terrain.update(List.of(position), position.getMinBlockX(), position.getMinBlockZ(),
                        position.getMaxBlockX() + 1, position.getMaxBlockZ() + 1);
                int changed = pixel(tile, roof);
                require((changed & 255) > (changed >> 8 & 255) * 2 && (changed & 255) > (changed >> 16 & 255) * 2,
                        "terrain refresh displays a changed surface block");
                chunk.setBlockState(roof, Blocks.WATER.defaultBlockState(), false);
                tile.updated = 0;
                terrain.update(List.of(position), position.getMinBlockX(), position.getMinBlockZ(),
                        position.getMaxBlockX() + 1, position.getMaxBlockZ() + 1);
                int water = pixel(tile, roof);
                require((water >> 16 & 255) > (water & 255), "water uses the biome-tinted liquid texture");
                chunk.setBlockState(roof, original.setValue(BlockStateProperties.WATERLOGGED, true), false);
                tile.updated = 0;
                terrain.update(List.of(position), position.getMinBlockX(), position.getMinBlockZ(),
                        position.getMaxBlockX() + 1, position.getMaxBlockZ() + 1);
                int wetRoof = pixel(tile, roof);
                require((wetRoof & 255) > (wetRoof >> 16 & 255), "waterlogged slabs retain their visible wooden roof");
            } finally {
                chunk.setBlockState(roof, original, false);
            }
            terrain.update(List.of(), 0, 0, 0, 0);
            require(terrain.size() == 0 && tile.texture.getPixels() == null, "revoked map tiles release their image and GPU texture");
        }
    }

    private static int pixel(SettlementMapTerrain.Tile tile, BlockPos position) {
        return tile.texture.getPixels().getPixelRGBA((position.getX() & 15) * SettlementMapTerrain.PIXELS_PER_BLOCK + 1,
                (position.getZ() & 15) * SettlementMapTerrain.PIXELS_PER_BLOCK + 1);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
