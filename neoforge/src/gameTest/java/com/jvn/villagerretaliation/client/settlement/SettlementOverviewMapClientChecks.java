package com.jvn.villagerretaliation.client.settlement;

import net.minecraft.core.BlockPos;

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
        require(!map.contains(110, 20) && map.coversChrome(9, 19), "map chrome has a separate hit region from terrain");
        require(map.worldX(35) == map.view().worldX(25) && map.worldZ(45) == map.view().worldZ(25),
                "map input and recorded terrain use the same world projection");
        require(map.x(-112) - map.left == Math.round(map.view().screenX(-112)),
                "map overlays align with the shared world map renderer");
        map.close();
        var overview = screen.overviewMap();
        require(overview.width == overview.height, "the settlement minimap is square");
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

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
