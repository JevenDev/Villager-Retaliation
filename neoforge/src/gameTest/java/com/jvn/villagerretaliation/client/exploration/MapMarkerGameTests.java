package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.exploration.MapView;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("explored_map")
@PrefixGameTestTemplate(false)
public final class MapMarkerGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void clustersCrossCellBoundariesAndKeepSelectionAccessible(GameTestHelper helper) {
        var first = village("one", -2, 0);
        var second = village("two", 2, 0);
        var far = village("far", 300, 0);
        var view = new MapView(640, 360, 0, 0, 1);
        var groups = MapMarkerLayout.groups(List.of(first, second, far), view, "");
        helper.assertValueEqual(groups.size(), 2, "nearby villages share a group even across grid boundaries");
        helper.assertValueEqual(groups.getFirst().members().size(), 2, "the cluster keeps both selectable destinations");
        groups = MapMarkerLayout.groups(List.of(first, second, far), view, first.key());
        helper.assertValueEqual(groups.size(), 3, "selection stays outside its neighboring cluster");
        helper.assertTrue(groups.getLast().members().getFirst().equals(first), "the selected marker draws and receives clicks last");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void labelsAvoidEachOtherIconsAndControls(GameTestHelper helper) {
        var first = village("Long village name", 0, 0);
        var second = village("Another village name", 50, 0);
        var view = new MapView(640, 360, 0, 0, 1);
        var groups = MapMarkerLayout.groups(List.of(first, second), view, "");
        var reserved = List.of(new MapMarkerLayout.Box(0, 0, 640, 40), new MapMarkerLayout.Box(0, 296, 640, 360));
        var labels = MapMarkerLayout.labels(groups, view, first.key(), -1, -1, true, text -> text.length() * 6, 9, reserved);
        helper.assertValueEqual(labels.size(), 2, "alternate positions keep both nearby names readable");
        var a = labels.getFirst(); var b = labels.getLast();
        helper.assertFalse(new MapMarkerLayout.Box(a.x()-2, a.y()-1, a.x()+a.width()+2, a.y()+10).overlaps(
                new MapMarkerLayout.Box(b.x()-2, b.y()-1, b.x()+b.width()+2, b.y()+10)), "labels never overlap");
        var distant = new MapView(640, 360, 0, 0, .5);
        groups = MapMarkerLayout.groups(List.of(first), distant, "");
        helper.assertTrue(MapMarkerLayout.labels(groups, distant, "", -1, -1, true, text -> text.length()*6, 9, reserved).isEmpty(),
                "distant unselected labels stay hidden");
        helper.assertValueEqual(MapMarkerLayout.labels(groups, distant, first.key(), -1, -1, false, text -> text.length()*6, 9, reserved).size(),
                1, "selection reveals its label even at distant zoom with village names disabled");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void searchFiltersDimensionsKindsAndFavoritesBeforeSorting(GameTestHelper helper) {
        var near = village("Needle near", 10, 0);
        var far = village("Needle far", 100, 0);
        var other = new MapDestination(MapDestination.Kind.LOCATION, "other", ResourceLocation.withDefaultNamespace("the_nether"),
                0, 0, "Needle other", "Supplies", null, true, 0xffffffff, 0);
        var overworld = near.dimension();
        var source = List.of(far, other, near);
        var result = MapSearchModel.filter(source, "  NEEDLE ", null, null, false, destination -> destination.equals(far), true, overworld, 0, 0);
        helper.assertTrue(result.equals(List.of(near, far, other)), "distance sorting puts other dimensions after comparable distances");
        result = MapSearchModel.filter(source, "needle", overworld, null, true, destination -> destination.equals(far), false, overworld, 0, 0);
        helper.assertTrue(result.equals(List.of(far)), "favorites and dimension filters combine with case insensitive search");
        result = MapSearchModel.filter(source, "supplies", null, MapDestination.Kind.LOCATION, false, destination -> false, false, overworld, 0, 0);
        helper.assertTrue(result.equals(List.of(other)), "search also matches a marker's short label");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void destinationArrowsStayWithinTheViewport(GameTestHelper helper) {
        helper.assertTrue(MapDestinationTracker.edge(320, 180, 640, 66, 290, 10) == null, "onscreen destinations need no edge arrow");
        for (double[] point : new double[][]{{-30000000, -30000000}, {30000000, 0}, {320, 30000000}, {0, 180}}) {
            var edge = MapDestinationTracker.edge(point[0], point[1], 640, 66, 290, 10);
            helper.assertTrue(edge != null && Double.isFinite(edge.angle()) && edge.x() >= 10 && edge.x() <= 630
                    && edge.y() >= 66 && edge.y() <= 290, "arrows remain inside the usable map area at world limits");
        }
        helper.assertTrue(village("distance", -3, -4).distance(0, 0) == 5, "distance handles negative world coordinates");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void villageNameFilterPreservesOtherDestinationLabels(GameTestHelper helper) {
        var village = village("Village", -80, 0);
        var location = new MapDestination(MapDestination.Kind.LOCATION, "location", village.dimension(),
                0, 0, "Supplies", "Supplies", null, true, 0xffffffff, 0);
        var quest = new MapDestination(MapDestination.Kind.QUEST, "quest", village.dimension(),
                80, 0, "Quest", "Quest", null, true, 0xffffffff, 0);
        var view = new MapView(320, 240, 0, 0, 1);
        var groups = MapMarkerLayout.groups(List.of(village, location, quest), view, "");
        var labels = MapMarkerLayout.labels(groups, view, "", -1, -1, false, text -> text.length() * 6, 9, List.of());
        helper.assertTrue(labels.stream().map(MapMarkerLayout.Label::destination).toList().containsAll(List.of(location, quest)),
                "saved locations and quest labels remain visible with village names disabled");
        helper.assertFalse(labels.stream().anyMatch(label -> label.destination().equals(village)), "ordinary village names stay hidden");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void spriteAnchorsDriveHitTestingLabelsAndViewportCulling(GameTestHelper helper) {
        var view = new MapView(400, 240, 0, 0, 1);
        var dimension = ResourceLocation.withDefaultNamespace("overworld");
        var destinations = new java.util.ArrayList<MapDestination>();
        destinations.add(village("Village", 0, 0));
        for (var icon : com.jvn.villagerretaliation.exploration.MapLocation.Icon.values()) if (icon.mapSprite())
            destinations.add(new MapDestination(MapDestination.Kind.LOCATION, icon.name(), dimension, 0, 0, icon.name(), icon.name(), icon, true, 0xffffffff, 0));
        for (var destination : destinations) {
            var group = MapMarkerLayout.groups(List.of(destination), view, destination.key()).getFirst();
            var box = MapMarkerLayout.bounds(group);
            helper.assertValueEqual(box, new MapMarkerLayout.Box(184, 88, 216, 120), "sprite canvas ends at the projected target's bottom center");
            helper.assertTrue(MapMarkerLayout.contains(group, 200, 88) && MapMarkerLayout.contains(group, 200, 120),
                    "the sprite's upper body and target anchor both receive clicks");
            helper.assertFalse(MapMarkerLayout.contains(group, 200, 125), "empty space below a bottom-anchored sprite is not clickable");
            var labels = MapMarkerLayout.labels(List.of(group), view, destination.key(), -1, -1, true, text -> text.length() * 6, 9, List.of());
            helper.assertValueEqual(labels.size(), 1, "bottom-anchored markers retain their labels");
            var label = labels.getFirst();
            helper.assertFalse(box.overlaps(new MapMarkerLayout.Box(label.x() - 2, label.y() - 1, label.x() + label.width() + 2, label.y() + 10)),
                    "labels stay outside the actual sprite canvas");
        }
        var legacy = new MapDestination(MapDestination.Kind.LOCATION, "legacy", dimension, 0, 0, "Chest", "Chest",
                com.jvn.villagerretaliation.exploration.MapLocation.Icon.CHEST, true, 0xffffffff, 0);
        helper.assertValueEqual(MapMarkerLayout.bounds(MapMarkerLayout.groups(List.of(legacy), view, "").getFirst()), new MapMarkerLayout.Box(192, 112, 208, 128),
                "existing item markers keep their centered placement");
        for (double zoom : new double[]{.12, .75, 1, 1.25, 2.5, 4, 24}) {
            var zoomed = new MapView(1000, 1000, 0, 0, zoom);
            double size = 16 * (1 << zoomed.level()) * zoom;
            for (var destination : destinations) {
                var group = MapMarkerLayout.groups(List.of(destination), zoomed, destination.key()).getFirst();
                var box = MapMarkerLayout.bounds(group);
                helper.assertValueEqual(box, new MapMarkerLayout.Box((int) Math.floor(500 - size / 2), (int) Math.floor(500 - size), (int) Math.ceil(500 + size / 2), 500),
                        "zoomed sprites grow with terrain and keep their bottom-center anchor");
                helper.assertTrue(MapMarkerLayout.contains(group, box.left(), box.top()), "the enlarged upper sprite receives clicks");
                helper.assertFalse(MapMarkerLayout.contains(group, box.left() - 3, box.top()), "clicks outside the enlarged canvas miss");
                var labels = MapMarkerLayout.labels(List.of(group), zoomed, destination.key(), -1, -1, true, text -> text.length() * 6, 9, List.of());
                helper.assertValueEqual(labels.size(), 1, "zoomed sprites retain an unobstructed label");
                var label = labels.getFirst();
                helper.assertFalse(box.overlaps(new MapMarkerLayout.Box(label.x() - 2, label.y() - 1, label.x() + label.width() + 2, label.y() + 10)),
                        "labels avoid the full zoomed sprite canvas");
            }
            int offset = (int) Math.floor((zoomed.height() / 2.0 + size - 8) / zoom);
            helper.assertFalse(MapMarkerLayout.groups(List.of(village("Zoomed edge", 0, offset)), zoomed, "").isEmpty(),
                    "zoomed sprites remain visible while their upper body intersects the viewport");
        }
        helper.assertFalse(MapMarkerLayout.groups(List.of(village("Edge village", 0, 128)), view, "").isEmpty(),
                "a sprite remains visible when its target is just below the viewport");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void spriteTexelsStayAlignedThroughPanningAndLodChanges(GameTestHelper helper) {
        for (int coordinate : new int[]{-29999987, -3, 29999985}) {
            var destination = village("Grid village", coordinate, coordinate + 8);
            for (double zoom : new double[]{.12, .75, 1, 1.25, 2.5, 4, 24}) {
                var view = new MapView(640, 480, coordinate - .375, coordinate + 8.125, zoom);
                var moved = new MapView(640, 480, view.x() + .125, view.z() - .25, zoom);
                var sprite = MapMarkerLayout.groups(List.of(destination), view, "").getFirst().sprite();
                var panned = MapMarkerLayout.groups(List.of(destination), moved, "").getFirst().sprite();
                int stride = 1 << view.level();
                helper.assertTrue(Math.abs(sprite.size() / 16 - stride * zoom) < 1e-8,
                        "each source pixel has exactly the terrain texel's projected size at every LOD");
                double left = view.worldX(sprite.x()), top = view.worldZ(sprite.y());
                helper.assertTrue(Math.abs(left / stride - Math.rint(left / stride)) < 1e-8
                        && Math.abs(top / stride - Math.rint(top / stride)) < 1e-8, "sprite edges lie on world terrain texel boundaries");
                helper.assertTrue(Math.abs(left + 8 * stride - Math.floorDiv(coordinate, stride) * stride) < 1e-8
                        && Math.abs(top + 16 * stride - Math.floorDiv(coordinate + 8, stride) * stride) < 1e-8,
                        "bottom-center anchors snap to their target texel including negative world coordinates");
                helper.assertTrue(Math.abs(panned.x() - sprite.x() + .125 * zoom) < 1e-8
                        && Math.abs(panned.y() - sprite.y() - .25 * zoom) < 1e-8,
                        "fractional panning moves terrain and sprite texels together without screen-pixel snapping");
            }
        }
        helper.succeed();
    }

    private static MapDestination village(String id, int x, int z) {
        return new MapDestination(MapDestination.Kind.VILLAGE, id, ResourceLocation.withDefaultNamespace("overworld"),
                x, z, id, id, null, true, 0xffffffff, 0);
    }
}
