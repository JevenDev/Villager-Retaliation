package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.settlement.SettlementViewWindow;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.lwjgl.glfw.GLFW;

@GameTestHolder("settlement_view")
@PrefixGameTestTemplate(false)
public final class SettlementViewNavigationGameTests {
    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_view")
    public static void movementIsIndependentOfFrameRate(GameTestHelper helper) {
        Vec3 slow = travel(20), fast = travel(144);
        helper.assertTrue(slow.distanceTo(fast) < 0.000001D, "acceleration and coasting cover the same distance at 20 and 144 fps");
        var straight = navigation();
        var diagonal = navigation();
        straight.press(GLFW.GLFW_KEY_W);
        diagonal.press(GLFW.GLFW_KEY_W);
        diagonal.press(GLFW.GLFW_KEY_D);
        helper.assertTrue(Math.abs(straight.movement(0.05, false).length() - diagonal.movement(0.05, false).length()) < 0.000001D,
                "diagonal input does not increase travel speed");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_view")
    public static void navigationClearsInputAndRespectsAuthoritativeBounds(GameTestHelper helper) {
        var navigation = navigation();
        navigation.press(GLFW.GLFW_KEY_W);
        navigation.press(GLFW.GLFW_KEY_UP);
        navigation.release(GLFW.GLFW_KEY_W);
        helper.assertTrue(navigation.movement(0.05, false).length() > 0, "releasing one alias preserves the other held key");
        navigation.clear();
        helper.assertValueEqual(navigation.movement(0.05, false), Vec3.ZERO, "clearing controls stops held input and inertia");
        navigation.pan(window(), 1000, -1000);
        helper.assertTrue(navigation.x() < 16 && navigation.z() == 0, "panning stays within approved chunks");
        var shifted = new SettlementViewWindow(new BlockPos(32, 64, 32), 64, 40, 40, 16, List.of(new ChunkPos(2, 2)));
        navigation.pan(shifted, 0, 0);
        helper.assertTrue(navigation.x() >= 32 && navigation.z() >= 32, "a new chunk window constrains an idle camera");
        navigation.accept(shifted);
        helper.assertTrue(navigation.x() == 40 && navigation.z() == 40 && navigation.height() == 16,
                "accepted server state replaces the requested focus and height");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_view")
    public static void zoomAndDragPreserveCameraConstraints(GameTestHelper helper) {
        var navigation = navigation();
        navigation.zoom(100, 2);
        helper.assertValueEqual(navigation.height(), 24.0F, "zoom respects render distance");
        double before = navigation.cameraHeight();
        navigation.movement(0.05, false);
        helper.assertTrue(navigation.cameraHeight() > before && navigation.cameraHeight() < navigation.height(),
                "zoom approaches its target smoothly");
        navigation.zoom(-100, 2);
        helper.assertValueEqual(navigation.height(), SettlementViewWindow.MIN_HEIGHT, "zoom respects minimum height");
        navigation.rotate(-90);
        helper.assertValueEqual(navigation.yaw(), 270.0F, "rotation wraps negative yaw");
        navigation.press(GLFW.GLFW_KEY_W);
        navigation.movement(0.05, false);
        navigation.release(GLFW.GLFW_KEY_W);
        helper.assertTrue(navigation.drag(10, 0, 360, 60).length() > 0, "drag uses the rotated camera basis");
        helper.assertValueEqual(navigation.movement(0.05, false), Vec3.ZERO, "dragging cancels keyboard inertia");
        helper.succeed();
    }

    private static Vec3 travel(int frames) {
        var navigation = navigation();
        navigation.press(GLFW.GLFW_KEY_W);
        Vec3 position = Vec3.ZERO;
        for (int frame = 0; frame < frames; frame++) position = position.add(navigation.movement(1.0D / frames, false));
        navigation.release(GLFW.GLFW_KEY_W);
        for (int frame = 0; frame < frames; frame++) position = position.add(navigation.movement(1.0D / frames, false));
        return position;
    }

    private static SettlementViewNavigation navigation() {
        return new SettlementViewNavigation(window(), 16, 0, 8);
    }

    private static SettlementViewWindow window() {
        return new SettlementViewWindow(new BlockPos(8, 64, 8), 64, 8, 8, 16, List.of(new ChunkPos(0, 0)));
    }
}
