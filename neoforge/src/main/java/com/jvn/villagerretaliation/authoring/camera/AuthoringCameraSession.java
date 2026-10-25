package com.jvn.villagerretaliation.authoring.camera;

import com.jvn.toucanlib.client.camera.ToucanCameraTargetPoints;
import com.jvn.villagerretaliation.client.interaction.DialogueCameraController;
import com.jvn.villagerretaliation.client.interaction.DialogueCameraTimeline.Pose;
import com.jvn.villagerretaliation.client.interaction.DialogueCameraTransforms;
import com.jvn.villagerretaliation.dialogue.DialogueCamera;
import com.jvn.villagerretaliation.dialogue.DialogueCamera.Anchor;
import com.jvn.villagerretaliation.dialogue.DialogueCamera.Placement;
import com.jvn.villagerretaliation.dialogue.DialogueCamera.Perspective;
import com.jvn.villagerretaliation.dialogue.DialogueCamera.Shot;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationLinePresentation.Camera;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationLinePresentation.Target;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class AuthoringCameraSession implements AutoCloseable {
    private final DialogueCameraController.Preview preview;
    private final Entity speaker;
    private final Entity character;
    private final Runnable returnToEditor;
    private final CameraType perspective;
    private final boolean hideGui;
    private final boolean mouseGrabbed;
    private final net.minecraft.client.gui.screens.Screen initialScreen;
    private final Object level;
    private Pose requested;
    private int sequence;
    private boolean closed;
    private AuthoringFreeCameraScreen freeScreen;

    private AuthoringCameraSession(Entity speaker, Entity character, Runnable returnToEditor) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || speaker == null || !speaker.isAlive() || speaker.isRemoved()
                || speaker.level() != minecraft.level || character != null
                    && (character.level() != minecraft.level || !character.isAlive() || character.isRemoved()))
            throw new IllegalArgumentException("Select a living camera target in the current world");
        this.speaker = speaker;
        this.character = character == null ? speaker : character;
        this.returnToEditor = Objects.requireNonNull(returnToEditor);
        perspective = minecraft.options.getCameraType();
        hideGui = minecraft.options.hideGui;
        mouseGrabbed = minecraft.mouseHandler.isMouseGrabbed();
        initialScreen = minecraft.screen;
        level = minecraft.level;
        var rendered = DialogueCameraController.renderedPose();
        var camera = minecraft.gameRenderer.getMainCamera();
        requested = rendered != null ? rendered : new Pose(camera.getPosition(), camera.getYRot(), camera.getXRot(), minecraft.options.fov().get());
        requested = new Pose(requested.position(), requested.yaw(), requested.pitch(),
                Math.clamp(requested.fov(), DialogueCamera.MIN_FOV, DialogueCamera.MAX_FOV));
        var initial = frame(new Camera(Target.SPEAKER, null, List.of(defaultShot())));
        preview = DialogueCameraController.beginPreview();
        preview.frame(initial);
        preview.pose(requested);
        minecraft.options.hideGui = true;
    }

    public static AuthoringCameraSession begin(Entity speaker, Entity character, Runnable returnToEditor) {
        if (com.jvn.villagerretaliation.client.interaction.ClientVillagerConversationState.active())
            throw new IllegalStateException("Close the current conversation before authoring cameras");
        return new AuthoringCameraSession(speaker, character, returnToEditor);
    }

    public boolean active() {
        if (closed) return false;
        var minecraft = Minecraft.getInstance();
        if (level != minecraft.level || !speaker.isAlive() || speaker.isRemoved() || !character.isAlive()
                || character.isRemoved() || minecraft.player == null || !minecraft.player.isAlive()
                || minecraft.getCameraEntity() != minecraft.player || !preview.active()) {
            close();
            return false;
        }
        return preview.active();
    }

    public Pose pose() {
        var rendered = DialogueCameraController.renderedPose();
        return active() && rendered != null ? rendered : requested;
    }

    Pose controlPose() {
        var visible = pose();
        return new Pose(visible.position(), requested.yaw(), requested.pitch(), requested.fov());
    }

    boolean canReturnToEditor() {
        var minecraft = Minecraft.getInstance();
        return minecraft.level == level && minecraft.player != null && minecraft.player.isAlive();
    }

    public void setPose(Pose value) {
        requireActive();
        preview.pose(value);
        requested = value;
    }

    public void setFov(double fov) {
        var pose = ownsScreen(Minecraft.getInstance().screen) ? controlPose() : pose();
        setPose(new Pose(pose.position(), pose.yaw(), pose.pitch(), fov));
    }

    public void preview(Camera camera) {
        requireActive();
        if (camera == null || camera.shots().isEmpty()) throw new IllegalArgumentException("Add a camera shot before previewing");
        preview.frame(frame(camera));
    }

    public void freeCamera() {
        requireActive();
        setPose(pose());
        freeScreen = new AuthoringFreeCameraScreen(this, returnToEditor);
        Minecraft.getInstance().setScreen(freeScreen);
    }

    public boolean ownsScreen(net.minecraft.client.gui.screens.Screen screen) {
        return screen != null && screen == freeScreen;
    }

    public Shot capture(Anchor anchor, Placement placement, Perspective perspective, Target target) {
        requireActive();
        var minecraft = Minecraft.getInstance();
        var pose = pose();
        var player = minecraft.player;
        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        var other = speaker == player ? character : speaker;
        var principal = speaker != player && speaker != character ? character : player;
        var forward = DialogueCameraTransforms.forward(ToucanCameraTargetPoints.focusPoint(other, partialTick),
                principal.getEyePosition(partialTick), player.getYRot());
        var pivot = (anchor == Anchor.SPEAKER ? speaker : player).getEyePosition(partialTick);
        var offset = placement == Placement.FIXED ? Vec3.ZERO : DialogueCameraTransforms.offset(pose.position(), pivot, forward);
        if (Math.abs(offset.x) > DialogueCamera.MAX_DISTANCE || Math.abs(offset.y) > DialogueCamera.MAX_DISTANCE
                || Math.abs(offset.z) > DialogueCamera.MAX_DISTANCE)
            throw new IllegalArgumentException("Camera is too far from this anchor. Move closer or capture a fixed position");
        var orientation = new DialogueCamera.Orientation(placement == Placement.RELATIVE
                ? DialogueCameraTransforms.relativeYaw(pose.yaw(), forward) : Mth.wrapDegrees(pose.yaw()), pose.pitch());
        return new Shot(DialogueCamera.Preset.CUSTOM, anchor, placement, perspective, offset, target,
                pose.fov(), DialogueCamera.Transition.LERP, DialogueCamera.DEFAULT_TRANSITION_SECONDS, DialogueCamera.Easing.SMOOTHSTEP, 2,
                orientation, placement == Placement.FIXED ? pose.position() : null);
    }

    private DialogueCameraController.Frame frame(Camera camera) {
        var subjects = new java.util.LinkedHashSet<Entity>();
        subjects.add(Minecraft.getInstance().player);
        subjects.add(speaker);
        subjects.add(character);
        return new DialogueCameraController.Frame("authoring-camera-" + ++sequence, camera, speaker, character,
                List.copyOf(subjects), false);
    }

    private Shot defaultShot() {
        return new Shot(DialogueCamera.Preset.CUSTOM, Anchor.PLAYER, Placement.RELATIVE, Perspective.THIRD_PERSON,
                Vec3.ZERO, Target.SPEAKER, requested.fov(), DialogueCamera.Transition.CUT, 0, DialogueCamera.Easing.LINEAR, 0);
    }

    private void requireActive() {
        if (!active()) throw new IllegalStateException("Camera preview has ended");
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        preview.close();
        var minecraft = Minecraft.getInstance();
        minecraft.options.setCameraType(perspective);
        minecraft.options.hideGui = hideGui;
        if (minecraft.screen == initialScreen && minecraft.level == level) {
            if (mouseGrabbed && minecraft.screen == null && minecraft.isWindowActive()) minecraft.mouseHandler.grabMouse();
            else if (!mouseGrabbed) minecraft.mouseHandler.releaseMouse();
        }
    }
}
