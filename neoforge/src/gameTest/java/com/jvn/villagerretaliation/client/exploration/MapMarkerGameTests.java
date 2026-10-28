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

    private static MapDestination village(String id, int x, int z) {
        return new MapDestination(MapDestination.Kind.VILLAGE, id, ResourceLocation.withDefaultNamespace("overworld"),
                x, z, id, id, null, true, 0xffffffff, 0);
    }
}
