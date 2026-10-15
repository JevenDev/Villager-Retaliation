package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.camera.AuthoringCameraSession;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.client.interaction.DialogueCameraTimeline;
import com.jvn.villagerretaliation.dialogue.DialogueCamera;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationLinePresentation.Camera;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationLinePresentation.Target;
import imgui.ImGui;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Consumer;

final class EditorCameraPanel {
    private static AuthoringCameraSession session;
    private static Entity character;
    private static Entity sessionSpeaker;
    private static DialogueCamera.Anchor anchor = DialogueCamera.Anchor.SPEAKER;
    private static DialogueCamera.Placement placement = DialogueCamera.Placement.RELATIVE;
    private static Target target = Target.SPEAKER;
    private static DialogueCamera.Preset preset = DialogueCamera.Preset.OVER_SHOULDER_LEFT;
    private static final ImString CAMERA_OWNER = new ImString(4096);
    private static AuthoringDocument ownerDocument;
    private static String lastPointer = "";

    private EditorCameraPanel() { }

    static void tick(Screen editor) {
        if (character != null && (!character.isAlive() || character.isRemoved() || character.level() != Minecraft.getInstance().level)) character = null;
        if (session != null && (!session.active() || Minecraft.getInstance().screen != editor
                && !session.ownsScreen(Minecraft.getInstance().screen))) close();
    }

    static void close() {
        if (session != null) {
            session.close();
            session = null;
            sessionSpeaker = null;
        }
    }

    static void render(AuthoringDocument document, String pointer, Entity speaker, Screen returnScreen,
            Runnable changed, Consumer<ImGuiAuthoringEditor.EditorOperation> operation) {
        if (session != null && (sessionSpeaker != speaker || ownerDocument != document)) close();
        if (!ImGui.collapsingHeader("In-world camera authoring")) return;
        if (ownerDocument != document || !lastPointer.equals(pointer)) {
            CAMERA_OWNER.set(pointer);
            ownerDocument = document;
            lastPointer = pointer;
        }
        ImGui.textWrapped("Select a dialogue node, exchange, response or line object. The camera belongs directly on that object, not inside speaker, text keys or metadata.");
        ImGui.inputText("Camera owner pointer", CAMERA_OWNER);
        if (ImGui.button("Use inspector selection")) CAMERA_OWNER.set(pointer);
        var minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.player != null && ImGui.beginCombo("Character target",
                character == null ? "Use speaker" : character.getName().getString())) {
            if (ImGui.selectable("Use speaker", character == null)) operation.accept(() -> { close(); character = null; });
            for (Entity entity : minecraft.level.getEntities(minecraft.player, minecraft.player.getBoundingBox().inflate(32), Entity::isAlive)) {
                if (ImGui.selectable(entity.getName().getString() + "##" + entity.getId(), entity == character))
                    operation.accept(() -> { close(); character = entity; });
            }
            ImGui.endCombo();
        }
        if (ImGui.button("Begin camera session")) operation.accept(() -> {
            close();
            session = AuthoringCameraSession.begin(speaker, character, () ->
                    minecraft.setScreen(minecraft.level != null && minecraft.player != null ? returnScreen : null));
            sessionSpeaker = speaker;
        });
        ImGui.sameLine();
        if (ImGui.button("Restore player camera")) close();
        if (session == null || !session.active()) return;
        anchor = enumeration("Anchor", anchor);
        placement = enumeration("Placement", placement);
        target = enumeration("Look at", target);
        preset = enumeration("Preset", preset);
        if (ImGui.button("Preview preset")) operation.accept(() -> session.preview(new Camera(Target.SPEAKER, null, List.of(
                new DialogueCamera.Shot(preset, anchor, placement, DialogueCamera.Perspective.THIRD_PERSON,
                        Vec3.ZERO, target, null, DialogueCamera.Transition.CUT, 0, DialogueCamera.Easing.LINEAR, 0)))));
        if (ImGui.button("Position free camera")) operation.accept(session::freeCamera);
        ImGui.sameLine();
        if (ImGui.button("Capture and append shot")) operation.accept(() -> {
            var shot = session.capture(anchor, placement, DialogueCamera.Perspective.THIRD_PERSON, target);
            JsonObject shotJson = DialogueCamera.json(new Camera(Target.SPEAKER, null, List.of(shot)))
                    .getAsJsonArray("shots").get(0).getAsJsonObject();
            EditorDocumentOperations.appendShot(document, CAMERA_OWNER.get(), shotJson);
            changed.run();
            session.preview(new Camera(Target.SPEAKER, null, List.of(shot)));
        });
        var pose = session.pose();
        float[] position = {(float) pose.position().x, (float) pose.position().y, (float) pose.position().z};
        float[] rotation = {pose.yaw(), pose.pitch()};
        float[] fov = {(float) pose.fov()};
        if (ImGui.inputFloat3("World XYZ", position)) operation.accept(() -> session.setPose(
                new DialogueCameraTimeline.Pose(new Vec3(position[0], position[1], position[2]), pose.yaw(), pose.pitch(), pose.fov())));
        if (ImGui.inputFloat2("Yaw / pitch", rotation)) operation.accept(() -> session.setPose(
                new DialogueCameraTimeline.Pose(pose.position(), rotation[0], rotation[1], pose.fov())));
        if (ImGui.sliderFloat("FOV", fov, 10, 110)) operation.accept(() -> session.setFov(fov[0]));
        if (ImGui.button("Preview saved sequence")) operation.accept(() -> {
            JsonObject owner = EditorDocumentOperations.cameraOwner(document, CAMERA_OWNER.get());
            Camera camera = DialogueCamera.read(owner);
            if (camera == null) throw new IllegalArgumentException("Selected object has no camera.");
            session.preview(camera);
        });
        ImGui.textWrapped("Use Properties to edit shot offsets, orientation, transition duration/easing and hold time, duplicate, remove or move shots up.");
        ImGui.textWrapped("Captured shots keep explicit yaw and pitch. Remove orientation to aim at the chosen look-at target. Relative offsets follow the anchor. Fixed captures use world coordinates.");
    }

    private static <E extends Enum<E>> E enumeration(String label, E value) {
        E selected = value;
        if (ImGui.beginCombo(label, value.name())) {
            for (E candidate : value.getDeclaringClass().getEnumConstants()) {
                if (candidate == Target.NONE) continue;
                if (ImGui.selectable(candidate.name(), candidate == value)) selected = candidate;
            }
            ImGui.endCombo();
        }
        return selected;
    }
}
