package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.camera.AuthoringCameraSession;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.client.interaction.DialogueCameraTimeline;
import com.jvn.villagerretaliation.dialogue.DialogueCamera;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationLinePresentation.Camera;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationLinePresentation.Target;
import imgui.ImGui;
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
    private static String cameraOwner;
    private static AuthoringDocument ownerDocument;
    private static final EditorWorldPreview worldPreview = new EditorWorldPreview();
    private static final java.util.Map<String, net.minecraft.client.renderer.texture.DynamicTexture> thumbnails = new java.util.HashMap<>();
    private static int selectedShot;

    private EditorCameraPanel() { }

    static void tick(Screen editor) {
        if (character != null && (!character.isAlive() || character.isRemoved() || character.level() != Minecraft.getInstance().level)) character = null;
        if (session != null && (!session.active() || Minecraft.getInstance().screen != editor
                && !session.ownsScreen(Minecraft.getInstance().screen))) close();
    }

    static void close() {
        worldPreview.close();
        thumbnails.values().forEach(net.minecraft.client.renderer.texture.DynamicTexture::close);
        thumbnails.clear();
        if (session != null) {
            session.close();
            session = null;
            sessionSpeaker = null;
        }
    }

    static void render(AuthoringDocument document, String pointer, Entity speaker, Screen returnScreen,
            Runnable changed, Consumer<ImGuiAuthoringEditor.EditorOperation> operation) {
        if (session != null && (sessionSpeaker != speaker || ownerDocument != document)) close();
        String nextOwner;
        try {
            nextOwner = EditorDocumentOperations.cameraSelection(document, pointer);
        } catch (IllegalArgumentException | IllegalStateException | ClassCastException
                | UnsupportedOperationException | IndexOutOfBoundsException exception) {
            close();
            if (EditorWidgets.section("Camera"))
                ImGui.textWrapped("Repair the definition in Check & export before attaching a camera: " + exception.getMessage());
            return;
        }
        if (ownerDocument != document || !java.util.Objects.equals(cameraOwner, nextOwner)) {
            if (!java.util.Objects.equals(cameraOwner, nextOwner)) close();
            cameraOwner = nextOwner;
            ownerDocument = document;
        }
        if (!EditorWidgets.section("Camera")) return;
        if (cameraOwner == null) {
            ImGui.textWrapped("Select a dialogue node or spoken line in Edit to attach camera shots.");
            return;
        }
        JsonObject owner = EditorDocumentOperations.cameraOwner(document, cameraOwner);
        String name = com.jvn.villagerretaliation.authoring.core.DialogueGraph.string(owner, "id");
        if (name.isBlank()) name = com.jvn.villagerretaliation.authoring.core.DialogueGraph.nodes(document).stream()
                .filter(node -> node.pointer().equals(cameraOwner)).map(node -> node.id()).findFirst().orElse("Selected dialogue");
        ImGui.textUnformatted("Attach shots to: " + name);
        if (ImGui.isItemHovered()) ImGui.setTooltip(cameraOwner);
        var minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.player != null && ImGui.beginCombo(EditorWidgets.field("Character target"),
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
        EditorWidgets.sameLineFor("Restore player camera");
        if (ImGui.button("Restore player camera")) close();
        if (session == null || !session.active()) return;
        preset = enumeration("Preset", preset);
        if (EditorWidgets.section("Advanced framing")) {
            anchor = enumeration("Anchor", anchor);
            placement = enumeration("Placement", placement);
            target = enumeration("Look at", target);
        }
        if (ImGui.button("Preview preset")) operation.accept(() -> session.preview(new Camera(Target.SPEAKER, null, List.of(
                new DialogueCamera.Shot(preset, anchor, placement, DialogueCamera.Perspective.THIRD_PERSON,
                        Vec3.ZERO, target, null, DialogueCamera.Transition.LERP, DialogueCamera.DEFAULT_TRANSITION_SECONDS,
                        DialogueCamera.Easing.SMOOTHSTEP, 0)))));
        if (ImGui.button("Position free camera")) operation.accept(session::freeCamera);
        EditorWidgets.sameLineFor("Add shot");
        if (ImGui.button("Add shot")) operation.accept(() -> {
            var shot = session.capture(anchor, placement, DialogueCamera.Perspective.THIRD_PERSON, target);
            JsonObject shotJson = DialogueCamera.json(new Camera(Target.SPEAKER, null, List.of(shot)))
                    .getAsJsonArray("shots").get(0).getAsJsonObject();
            EditorDocumentOperations.appendShot(document, cameraOwner, shotJson, worldPreview.thumbnail());
            changed.run();
            session.preview(new Camera(Target.SPEAKER, null, List.of(shot)));
        });
        if (EditorWidgets.section("Coordinates and FOV")) {
            var pose = session.pose();
            float[] position = {(float) pose.position().x, (float) pose.position().y, (float) pose.position().z};
            float[] rotation = {pose.yaw(), pose.pitch()};
            float[] fov = {(float) pose.fov()};
            if (ImGui.inputFloat3(EditorWidgets.field("World XYZ"), position)) operation.accept(() -> session.setPose(
                    new DialogueCameraTimeline.Pose(new Vec3(position[0], position[1], position[2]), pose.yaw(), pose.pitch(), pose.fov())));
            if (ImGui.inputFloat2(EditorWidgets.field("Yaw / pitch"), rotation)) operation.accept(() -> session.setPose(
                    new DialogueCameraTimeline.Pose(pose.position(), rotation[0], rotation[1], pose.fov())));
            if (ImGui.sliderFloat(EditorWidgets.field("FOV"), fov, 10, 110)) operation.accept(() -> session.setFov(fov[0]));
        }
        if (ImGui.button("Preview saved sequence")) operation.accept(() -> {
            JsonObject savedOwner = EditorDocumentOperations.cameraOwner(document, cameraOwner);
            Camera camera = DialogueCamera.read(savedOwner);
            if (camera == null) throw new IllegalArgumentException("Selected object has no camera.");
            session.preview(camera);
        });
        if (EditorWidgets.section("Shot settings")) {
            ImGui.textWrapped("Use Camera preview and shots to adjust timing, reorder or remove shots.");
            ImGui.textWrapped("Relative shots follow the anchor. Fixed shots use world coordinates. Captured shots keep their rotation. Remove orientation to track the look-at target.");
        }
        studio(document, changed, operation);
    }

    private static void studio(AuthoringDocument document, Runnable changed, Consumer<ImGuiAuthoringEditor.EditorOperation> operation) {
        var viewport = ImGui.getMainViewport();
        ImGui.setNextWindowPos(viewport.getWorkPosX() + Math.max(480, viewport.getWorkSizeX() - 650), viewport.getWorkPosY() + 180, imgui.flag.ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(620, 660, imgui.flag.ImGuiCond.FirstUseEver);
        if (ImGui.begin("Camera preview and shots###VR_CameraStudio")) {
            ImGui.setWindowFontScale(1);
            worldPreview.render(Math.max(160, ImGui.getContentRegionAvailX()));
            ImGui.textWrapped("Live framing. Position the free camera, then add a shot.");
            JsonObject owner = EditorDocumentOperations.cameraOwner(document, cameraOwner);
            if (owner.has("camera") && owner.getAsJsonObject("camera").has("shots")) {
                var shots = owner.getAsJsonObject("camera").getAsJsonArray("shots");
                selectedShot = Math.clamp(selectedShot, 0, Math.max(0, shots.size() - 1));
                var images = EditorCameraShots.thumbnails(document.metadata(), cameraOwner, shots.size());
                for (int i = 0; i < shots.size(); i++) {
                    int index = i;
                    ImGui.pushID(i);
                    String encoded = images.get(i).getAsString();
                    if (!encoded.isBlank()) {
                        try {
                            var texture = thumbnails.get(encoded);
                            if (texture == null) {
                                texture = new net.minecraft.client.renderer.texture.DynamicTexture(com.mojang.blaze3d.platform.NativeImage.read(
                                        new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(encoded))));
                                thumbnails.put(encoded, texture);
                            }
                            ImGui.image(texture.getId(), 96, 54);
                            EditorWidgets.sameLineFor("Shot " + (i + 1));
                        } catch (java.io.IOException | IllegalArgumentException exception) {
                            ImGui.textWrapped("Thumbnail unavailable: " + exception.getMessage());
                        }
                    }
                    if (ImGui.selectable("Shot " + (i + 1), selectedShot == i)) selectedShot = i;
                    if (selectedShot == i) {
                        JsonObject shot = shots.get(i).getAsJsonObject();
                        float[] hold = {shot.has("hold_seconds") ? shot.get("hold_seconds").getAsFloat() : 0};
                        if (ImGui.sliderFloat(EditorWidgets.field("Hold seconds"), hold, 0, 30)) operation.accept(() -> { EditorCameraShots.edit(document, cameraOwner, index, "update", value -> value.addProperty("hold_seconds", hold[0])); changed.run(); });
                        JsonObject transition = shot.has("transition") ? shot.getAsJsonObject("transition") : new JsonObject();
                        boolean smooth = !shot.has("transition")
                                || "lerp".equals(com.jvn.villagerretaliation.authoring.core.DialogueGraph.string(transition, "type"));
                        if (ImGui.checkbox("Smooth transition", smooth)) operation.accept(() -> { EditorCameraShots.edit(document, cameraOwner, index, "update", value -> {
                            JsonObject settings = new JsonObject(); settings.addProperty("type", smooth ? "cut" : "lerp");
                            if (!smooth) { settings.addProperty("duration_seconds", DialogueCamera.DEFAULT_TRANSITION_SECONDS); settings.addProperty("easing", "smoothstep"); }
                            value.add("transition", settings);
                        }); changed.run(); });
                        if (ImGui.isItemHovered()) ImGui.setTooltip("Turn off for an instant camera and FOV cut.");
                        if (smooth) {
                            float[] duration = {transition.has("duration_seconds") ? transition.get("duration_seconds").getAsFloat()
                                    : (float) DialogueCamera.DEFAULT_TRANSITION_SECONDS};
                            if (ImGui.sliderFloat(EditorWidgets.field("Transition seconds"), duration, .05f, 10)) operation.accept(() -> {
                                EditorCameraShots.edit(document, cameraOwner, index, "update", value -> {
                                    JsonObject settings = transition.deepCopy();
                                    settings.addProperty("type", "lerp");
                                    settings.addProperty("duration_seconds", duration[0]);
                                    if (!shot.has("transition")) settings.addProperty("easing", "smoothstep");
                                    value.add("transition", settings);
                                });
                                changed.run();
                            });
                        }
                        for (String action : List.of("up", "down", "duplicate", "delete")) {
                            if (!action.equals("up")) EditorWidgets.sameLineFor(action);
                            ImGui.beginDisabled(action.equals("up") && i == 0 || action.equals("down") && i == shots.size() - 1);
                            if (ImGui.button(action)) operation.accept(() -> { EditorCameraShots.edit(document, cameraOwner, index, action, ignored -> { }); changed.run(); });
                            ImGui.endDisabled();
                        }
                    }
                    ImGui.popID();
                }
            }
        }
        ImGui.end();
    }

    private static String enumLabel(Enum<?> value) {
        String text = value.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static <E extends Enum<E>> E enumeration(String label, E value) {
        E selected = value;
        if (ImGui.beginCombo(EditorWidgets.field(label), enumLabel(value))) {
            for (E candidate : value.getDeclaringClass().getEnumConstants()) {
                if (candidate == Target.NONE) continue;
                if (ImGui.selectable(enumLabel(candidate), candidate == value)) selected = candidate;
            }
            ImGui.endCombo();
        }
        return selected;
    }
}
