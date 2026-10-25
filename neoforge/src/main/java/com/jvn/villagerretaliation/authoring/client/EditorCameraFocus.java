package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.dialogue.DialogueCamera;
import com.jvn.villagerretaliation.dialogue.DialogueCameraReference;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationLinePresentation.Target;
import imgui.ImGui;
import imgui.type.ImString;
import java.util.Locale;
import java.util.function.Consumer;

final class EditorCameraFocus {
    private static String selection = "";
    private static JsonObject reference;
    private static boolean editingReference;

    private EditorCameraFocus() { }

    static void render(AuthoringDocument document, String owner, int index, JsonObject shot, Runnable changed,
            Consumer<ImGuiAuthoringEditor.EditorOperation> operation) {
        Target target = Target.valueOf(text(shot, "target", "speaker").toUpperCase(Locale.ROOT));
        Target nextTarget = EditorCameraPanel.enumeration("Look at / fallback", target);
        if (nextTarget != target) edit(document, owner, index, changed, operation, value -> {
            value.addProperty("target", nextTarget.name().toLowerCase(Locale.ROOT));
            value.remove("orientation");
            if (nextTarget == Target.GROUP) { value.remove("focus"); value.remove("reference"); }
        });
        var focus = DialogueCamera.Focus.valueOf(text(shot, "focus", "eyes").toUpperCase(Locale.ROOT));
        var nextFocus = EditorCameraPanel.enumeration("Body focus", focus);
        if (nextFocus != focus) edit(document, owner, index, changed, operation, value -> {
            value.addProperty("focus", nextFocus.name().toLowerCase(Locale.ROOT));
            value.remove("orientation");
            value.remove("reference");
            if (target == Target.GROUP) value.addProperty("target", "speaker");
        });
        if (ImGui.isItemHovered()) ImGui.setTooltip("Hands follow the character's main arm. Empty slots still focus that part of the body.");
        if (shot.has("orientation")) {
            if (ImGui.button("Track look-at target")) edit(document, owner, index, changed, operation, value -> value.remove("orientation"));
            if (ImGui.isItemHovered()) ImGui.setTooltip("Replace the captured camera rotation with a moving focus target.");
        }
        String nextSelection = System.identityHashCode(document) + owner + "/" + index + "/" + shot;
        if (!selection.equals(nextSelection)) {
            selection = nextSelection;
            editingReference = shot.has("reference");
            reference = shot.has("reference") ? shot.getAsJsonObject("reference").deepCopy()
                    : com.google.gson.JsonParser.parseString("""
                            {"id":"focus_target","kind":"item","match":"minecraft:emerald","origin":"speaker","radius":8}
                            """).getAsJsonObject();
        }
        if (ImGui.checkbox("Focus a world object", editingReference)) {
            editingReference = !editingReference;
            if (!editingReference && shot.has("reference"))
                edit(document, owner, index, changed, operation, value -> value.remove("reference"));
        }
        if (!editingReference) return;
        var kind = DialogueCameraReference.Kind.valueOf(reference.get("kind").getAsString().toUpperCase(Locale.ROOT));
        kind = EditorCameraPanel.enumeration("Object", kind);
        reference.addProperty("kind", kind.name().toLowerCase(Locale.ROOT));
        input("Reference name", "id", false, 64);
        input(kind == DialogueCameraReference.Kind.ITEM ? "Item ID" : "Block ID", "match", false, 256);
        var origin = DialogueCamera.Anchor.valueOf(text(reference, "origin", "speaker").toUpperCase(Locale.ROOT));
        reference.addProperty("origin", EditorCameraPanel.enumeration("Search near", origin).name().toLowerCase(Locale.ROOT));
        input("Marker tag (optional)", "marker", true, 128);
        input("Campaign location (optional)", "location", true, 64);
        if (kind == DialogueCameraReference.Kind.ITEM) input("Dropped item tag (optional)", "entity_tag", true, 128);
        else reference.remove("entity_tag");
        int[] radius = {reference.has("radius") ? reference.get("radius").getAsInt() : 8};
        if (ImGui.sliderInt(EditorWidgets.field("Search radius"), radius, 0, 16)) reference.addProperty("radius", radius[0]);
        var rawOffset = reference.getAsJsonArray("offset");
        int[] offset = rawOffset == null ? new int[3] : new int[]{rawOffset.get(0).getAsInt(), rawOffset.get(1).getAsInt(), rawOffset.get(2).getAsInt()};
        if (ImGui.inputInt3(EditorWidgets.field("Offset XYZ"), offset)) {
            var coordinates = new com.google.gson.JsonArray();
            for (int coordinate : offset) coordinates.add(coordinate);
            reference.add("offset", coordinates);
        }
        ImGui.textWrapped("Use one marker or campaign location. Exactly one object must match. Missing or ambiguous objects use the fallback target. Reuse the reference name and settings to keep the same object.");
        if (ImGui.button("Apply world focus")) {
            JsonObject saved = reference.deepCopy();
            edit(document, owner, index, changed, operation, value -> {
                DialogueCameraReference.read(saved);
                value.add("reference", saved);
                value.remove("focus");
                value.remove("orientation");
                if (target == Target.GROUP) value.addProperty("target", "speaker");
            });
        }
    }

    private static String text(JsonObject value, String key, String fallback) {
        return value.has(key) ? value.get(key).getAsString() : fallback;
    }

    private static void input(String label, String key, boolean optional, int capacity) {
        var value = new ImString(text(reference, key, ""), capacity + 1);
        if (ImGui.inputText(EditorWidgets.field(label), value)) {
            if (optional && value.get().isBlank()) reference.remove(key);
            else reference.addProperty(key, value.get());
        }
    }

    private static void edit(AuthoringDocument document, String owner, int index, Runnable changed,
            Consumer<ImGuiAuthoringEditor.EditorOperation> operation, Consumer<JsonObject> change) {
        operation.accept(() -> {
            EditorCameraShots.edit(document, owner, index, "update", change);
            changed.run();
        });
    }
}
