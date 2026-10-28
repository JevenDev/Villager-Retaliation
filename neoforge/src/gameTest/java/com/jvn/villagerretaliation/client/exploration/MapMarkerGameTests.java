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
    public static void destinationArrowsAndScaleStayWithinTheViewport(GameTestHelper helper) {
        helper.assertTrue(MapDestinationTracker.edge(320, 180, 640, 66, 290, 10) == null, "onscreen destinations need no edge arrow");
        for (double[] point : new double[][]{{-30000000, -30000000}, {30000000, 0}, {320, 30000000}, {0, 180}}) {
            var edge = MapDestinationTracker.edge(point[0], point[1], 640, 66, 290, 10);
            helper.assertTrue(edge != null && Double.isFinite(edge.angle()) && edge.x() >= 10 && edge.x() <= 630
                    && edge.y() >= 66 && edge.y() <= 290, "arrows remain inside the usable map area at world limits");
        }
        for (double scale : new double[]{.12, .25, 1, 2.5, 24}) {
            int blocks = MapOrientation.scaleBlocks(scale, 80);
            helper.assertTrue(blocks >= 1 && blocks * scale <= 80, "the scale uses real block distances and fits its allotted width");
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

    private static MapDestination village(String id, int x, int z) {
        return new MapDestination(MapDestination.Kind.VILLAGE, id, ResourceLocation.withDefaultNamespace("overworld"),
                x, z, id, id, null, true, 0xffffffff, 0);
    }
}
