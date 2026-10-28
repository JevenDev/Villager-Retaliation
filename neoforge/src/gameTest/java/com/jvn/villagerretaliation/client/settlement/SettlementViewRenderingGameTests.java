package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.settlement.SettlementAreaType;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("settlement_view")
@PrefixGameTestTemplate(false)
public final class SettlementViewRenderingGameTests {
    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_view")
    public static void frameEntitiesRefreshAfterRemovalAndReuseLivePositions(GameTestHelper helper) {
        var first = EntityType.MARKER.create(helper.getLevel());
        var second = EntityType.MARKER.create(helper.getLevel());
        var traversals = new AtomicInteger();
        Iterable<Entity> source = () -> { traversals.incrementAndGet(); return List.<Entity>of(first, second).iterator(); };
        var cache = new SettlementViewEntities();
        cache.prepare(source);
        cache.prepare(source);
        helper.assertValueEqual(traversals.get(), 1, "overlays traverse the source once per frame");
        first.setPos(20, 64, 30);
        helper.assertValueEqual(cache.get(first.getUUID()).blockPosition(), new BlockPos(20, 64, 30), "lookup retains live entity positions");
        cache.invalidate();
        cache.prepare(List.of(second));
        helper.assertTrue(cache.get(first.getUUID()) == null && cache.all().size() == 1, "the next frame removes departed entities");
        cache.clear();
        helper.assertTrue(cache.all().isEmpty(), "closing the view releases entity references");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_view")
    public static void staticMapMarkersRefreshWithSelectionAndSnapshots(GameTestHelper helper) {
        var home = area(SettlementAreaType.HOME, new BlockPos(-20, 64, -30));
        var farm = area(SettlementAreaType.FARM, new BlockPos(20, 64, 30));
        var cache = new SettlementMapMarkers();
        cache.update(List.of(home, farm), List.of());
        var ordinary = cache.markers(null);
        helper.assertTrue(ordinary.size() == 1 && ordinary.getFirst().entry().equals(farm), "unselected homes are excluded");
        helper.assertTrue(cache.markers(null) == ordinary, "stationary frames reuse marker data");
        var selected = cache.markers(home.id());
        helper.assertTrue(selected.size() == 2 && selected.getFirst().color() == 0xFF70CBFF, "selecting a home reveals and highlights it");
        helper.assertValueEqual(selected.getFirst().position(), new BlockPos(-18, 64, -28), "cached anchors preserve negative coordinates");
        cache.update(List.of(farm), List.of());
        helper.assertTrue(cache.markers(home.id()).size() == 1 && cache.areas().equals(List.of(farm)), "new snapshots invalidate removed markers and footprints");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_view")
    public static void mapLinesClipBeforeRasterization(GameTestHelper helper) {
        var horizontal = SettlementOverviewMap.clipLine(-30000000, 40, 30000000, 40, 10, 20, 100, 60);
        helper.assertValueEqual(horizontal, new SettlementOverviewMap.Line(10, 40, 109, 40), "long lines only rasterize visible pixels");
        helper.assertValueEqual(SettlementOverviewMap.clipLine(30000000, 40, -30000000, 40, 10, 20, 100, 60),
                new SettlementOverviewMap.Line(109, 40, 10, 40), "reversed lines preserve their direction");
        helper.assertTrue(SettlementOverviewMap.clipLine(-20, 10, 500, 10, 10, 20, 100, 60) == null, "fully clipped lines produce no work");
        helper.assertValueEqual(SettlementOverviewMap.clipLine(10, 20, 10, 20, 10, 20, 100, 60),
                new SettlementOverviewMap.Line(10, 20, 10, 20), "a visible point still draws");
        var diagonal = SettlementOverviewMap.clipLine(-1000, -1000, 1000, 1000, 10, 20, 100, 60);
        helper.assertValueEqual(diagonal, new SettlementOverviewMap.Line(20, 20, 79, 79), "diagonals clip both axes");
        helper.succeed();
    }

    private static SettlementManagementPayload.Area area(SettlementAreaType type, BlockPos min) {
        return new SettlementManagementPayload.Area(UUID.randomUUID(), type.name(), type, min, min.offset(4, 4, 4), 5, true, 0, 0, 0);
    }
}
