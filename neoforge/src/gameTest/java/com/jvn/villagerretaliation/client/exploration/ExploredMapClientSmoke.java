package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

final class ExploredMapClientSmoke {
    private static CompletableFuture<?> capture, reload;
    private static boolean captured, minimap;
    private static int phase, ticks;
    private static long uploads, frames;
    private static float pitch, yaw;
    private static ExploredMapScreen reopened;

    private ExploredMapClientSmoke() { }

    static boolean prepare() {
        if (captured) return true;
        if (capture == null) capture = ExploredMapCaptureChecks.prepare();
        if (!capture.isDone()) return false;
        capture.join();
        if (!ExploredMapCaptureChecks.complete()) return false;
        ExploredMapCaptureChecks.verify();
        captured = true;
        return true;
    }

    static boolean verifyLifecycle(ExploredMapScreen screen) {
        var minecraft = Minecraft.getInstance();
        switch (phase) {
            case 0 -> {
                pitch = minecraft.player.getXRot(); yaw = minecraft.player.getYRot();
                screen.onClose();
                require(minecraft.screen == screen && screen.coversWorld(), "closing retains the map until the world can be revealed");
                double scale = screen.view().scale();
                screen.mouseScrolled(100, 100, 0, 1);
                require(screen.view().scale() == scale, "closing ignores map manipulation");
                phase++;
            }
            case 1 -> {
                if (minecraft.screen != null) return false;
                require(minecraft.player.getXRot() == pitch && minecraft.player.getYRot() == yaw,
                        "map transitions preserve player rotation");
                require(minecraft.gameRenderer.getMainCamera().getXRot() == pitch, "closing restores the world camera");
                minimap = VillagerRetaliationConfig.SHOW_EXPLORED_MAP_MINIMAP.get();
                VillagerRetaliationConfig.SHOW_EXPLORED_MAP_MINIMAP.set(false);
                uploads = ExploredMapClient.tiles().uploads();
                phase++;
            }
            case 2 -> {
                if (++ticks < 4) return false;
                require(ExploredMapClient.tiles().uploads() == uploads, "a closed map with its minimap disabled performs no terrain uploads");
                boolean animate = VillagerRetaliationConfig.ANIMATE_EXPLORED_MAP_OPENING.get();
                try {
                    VillagerRetaliationConfig.ANIMATE_EXPLORED_MAP_OPENING.set(false);
                    var instant = new ExploredMapScreen(); minecraft.setScreen(instant);
                    require(instant.coversWorld() && instant.transitionProgress() == 1, "disabled animations open immediately");
                    instant.onClose();
                    require(minecraft.screen == null, "disabled animations close immediately");
                } finally { VillagerRetaliationConfig.ANIMATE_EXPLORED_MAP_OPENING.set(animate); }
                reopened = new ExploredMapScreen(); minecraft.setScreen(reopened);
                require(reopened.transitionProgress() < 1, "a fresh map still animates after returning from editors");
                reopened.mouseScrolled(100, 100, 0, 1);
                require(reopened.transitionProgress() == 1, "map input skips the opening transition");
                reopened.onClose(); reopened.onClose();
                require(minecraft.screen == null, "a second close skips the closing transition");
                minecraft.setScreen(reopened);
                navigation(reopened).focus(minecraft.player.getX(), minecraft.player.getZ());
                frames = reopened.renderedFrames(); uploads = ExploredMapClient.tiles().uploads();
                reload = minecraft.reloadResourcePacks();
                phase++;
            }
            case 3 -> {
                if (!reload.isDone()) return false;
                reload.join();
                if (reopened.renderedFrames() <= frames || ExploredMapClient.tiles().uploads() <= uploads) return false;
                require(GL11.glGetError() == 0, "resource reload restores map rendering without GL errors");
                VillagerRetaliationConfig.SHOW_EXPLORED_MAP_MINIMAP.set(minimap);
                phase++;
                System.out.println("Map capture and lifecycle checks passed: surface discovery, camera restoration, closed uploads and resource reload");
                return true;
            }
            default -> { return true; }
        }
        return false;
    }
    static MapNavigation navigation(ExploredMapScreen screen) {
        return (MapNavigation) field(screen, "navigation");
    }
    static void zoom(ExploredMapScreen screen, double scale) {
        navigation(screen).zoomAt(screen.width / 2.0, screen.height / 2.0, scale);
    }
    private static Object field(Object target, String name) {
        try { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
