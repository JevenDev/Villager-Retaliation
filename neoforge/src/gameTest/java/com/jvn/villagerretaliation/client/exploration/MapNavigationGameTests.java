package com.jvn.villagerretaliation.client.exploration;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.lwjgl.glfw.GLFW;

@GameTestHolder("explored_map")
@PrefixGameTestTemplate(false)
public final class MapNavigationGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void zoomKeepsCursorAnchoredAndReusesStationaryProjection(GameTestHelper helper) {
        var navigation = new MapNavigation();
        navigation.resize(640, 360);
        navigation.focus(-120, -240);
        var before = navigation.view();
        navigation.zoomAt(420, 90, 5);
        var after = navigation.view();
        helper.assertTrue(Math.abs(before.worldX(420) - after.worldX(420)) < 0.000001
                && Math.abs(before.worldZ(90) - after.worldZ(90)) < 0.000001, "zoom preserves the world point under the cursor");
        navigation.update(1, true);
        navigation.update(50_000_001, true);
        navigation.zoomAt(420, 90, after.scale());
        helper.assertTrue(navigation.view() == after, "idle frames and unchanged zoom reuse the projection");
        navigation.beginDrag();
        navigation.drag(30, -20);
        helper.assertTrue(navigation.view().x() == after.x() - 6 && navigation.view().z() == after.z() + 4,
                "drag follows the current map scale");
        navigation.focus(Double.MAX_VALUE, -Double.MAX_VALUE);
        helper.assertTrue(navigation.view().x() == 29_999_000 && navigation.view().z() == -29_999_000,
                "direct focus respects the same world limits as dragging");
        navigation.resize(100_000, 80_000);
        helper.assertValueEqual(navigation.view().scale(), 24.0, "large viewports keep valid zoom limits");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void losingFocusAndResizingReleaseMapInput(GameTestHelper helper) {
        var navigation = new MapNavigation();
        navigation.resize(640, 360);
        navigation.update(1, true);
        navigation.press(GLFW.GLFW_KEY_RIGHT);
        navigation.update(50_000_001, true);
        helper.assertTrue(navigation.view().x() > 0, "held arrows pan the map");
        navigation.beginDrag();
        navigation.update(100_000_001, false);
        var inactive = navigation.view();
        navigation.update(150_000_001, true);
        navigation.update(200_000_001, true);
        helper.assertTrue(navigation.view() == inactive && !navigation.drag(10, 10), "focus loss clears both held keys and dragging");
        navigation.press(GLFW.GLFW_KEY_DOWN);
        navigation.beginDrag();
        navigation.resize(320, 180);
        var resized = navigation.view();
        navigation.update(250_000_001, true);
        navigation.update(300_000_001, true);
        helper.assertTrue(navigation.view() == resized && !navigation.drag(10, 10), "resizing cannot leave navigation stuck");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void transitionsResumeAndReverseWithoutReopening(GameTestHelper helper) {
        for (int step = 0; step <= 100_000; step++) {
            float eased = MapScreenTransition.ease(step / 100_000.0F);
            helper.assertTrue(eased >= 0 && eased <= 1, "floating point easing stays within valid opacity bounds");
        }
        long[] now = {0};
        var transition = new MapScreenTransition(() -> now[0]);
        transition.open();
        helper.assertValueEqual(transition.progress(true), 0.0F, "opening starts at zero even when the clock is zero");
        now[0] = 250_000_000;
        helper.assertTrue(Math.abs(transition.progress(true) - .2F) < .00001F, "opening advances with elapsed time");
        helper.assertFalse(transition.close(true), "first close animates from the current opening progress");
        now[0] += 60_000_000;
        helper.assertTrue(Math.abs(transition.progress(true) - .1F) < .00001F, "closing reverses smoothly from an interrupted opening");
        helper.assertTrue(transition.close(true), "second close skips the animation");
        transition.suspend();
        transition.open();
        helper.assertValueEqual(transition.progress(true), 1.0F, "returning from an editor keeps the map fully open");
        helper.assertFalse(transition.close(true), "a resumed map still animates closing");
        now[0] += 600_000_000;
        helper.assertValueEqual(transition.progress(true), 0.0F, "closing reaches the world");
        var instant = new MapScreenTransition(() -> now[0]);
        instant.open();
        helper.assertValueEqual(instant.progress(false), 1.0F, "disabled animations open immediately");
        helper.assertTrue(instant.close(false), "disabled animations close immediately");
        helper.succeed();
    }
}
