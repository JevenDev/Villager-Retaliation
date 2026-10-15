package com.jvn.villagerretaliation.authoring.camera;

import com.jvn.villagerretaliation.client.interaction.DialogueCameraTimeline.Pose;
import com.jvn.villagerretaliation.dialogue.DialogueCamera;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

final class AuthoringFreeCameraScreen extends Screen {
    private final AuthoringCameraSession session;
    private final Runnable returnToEditor;
    private final Set<Integer> keys = new HashSet<>();
    private boolean returning;

    AuthoringFreeCameraScreen(AuthoringCameraSession session, Runnable returnToEditor) {
        super(Component.literal("Dialogue camera positioning"));
        this.session = session;
        this.returnToEditor = returnToEditor;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void tick() {
        if (!session.active()) {
            onClose();
            return;
        }
        var pose = session.pose();
        var forward = Vec3.directionFromRotation(pose.pitch(), pose.yaw());
        var right = Vec3.directionFromRotation(0, pose.yaw() + 90);
        double speed = keys.contains(GLFW.GLFW_KEY_LEFT_SHIFT) ? 0.3 : 0.1;
        var delta = forward.scale(axis(GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_S))
                .add(right.scale(axis(GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_A)))
                .add(0, axis(GLFW.GLFW_KEY_E, GLFW.GLFW_KEY_Q), 0);
        if (delta.lengthSqr() > 0) session.setPose(new Pose(pose.position().add(delta.normalize().scale(speed)),
                pose.yaw(), pose.pitch(), pose.fov()));
    }

    private int axis(int positive, int negative) {
        return (keys.contains(positive) ? 1 : 0) - (keys.contains(negative) ? 1 : 0);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) {
            onClose();
            return true;
        }
        keys.add(key);
        return true;
    }

    @Override
    public boolean keyReleased(int key, int scanCode, int modifiers) {
        keys.remove(key);
        return true;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return false;
        var pose = session.pose();
        session.setPose(new Pose(pose.position(), Mth.wrapDegrees(pose.yaw() + (float) dx * 0.2F),
                Mth.clamp(pose.pitch() + (float) dy * 0.2F, -90, 90), pose.fov()));
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        session.setFov(Math.clamp(session.pose().fov() - vertical * 2, DialogueCamera.MIN_FOV, DialogueCamera.MAX_FOV));
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, 27, 0xB0000000);
        graphics.drawCenteredString(font, "WASD move | Q/E vertical | Shift fast | Right drag rotate | Wheel FOV", width / 2, 4, 0xFFFFFF);
        graphics.drawCenteredString(font, "Enter or Escape returns to the editor. Capture there to save this framing.", width / 2, 16, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        keys.clear();
        returning = true;
        if (minecraft.level == null || minecraft.player == null || !minecraft.player.isAlive()) minecraft.setScreen(null);
        else returnToEditor.run();
    }

    @Override
    public void removed() {
        if (!returning) session.close();
    }
}
