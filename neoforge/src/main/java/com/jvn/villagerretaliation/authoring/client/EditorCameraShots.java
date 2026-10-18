package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.JsonPointers;
import com.jvn.villagerretaliation.dialogue.DialogueCamera;
import java.util.function.Consumer;

final class EditorCameraShots {
    private EditorCameraShots() { }

    static JsonArray thumbnails(JsonObject metadata, String pointer, int count) {
        if (!metadata.has("camera_thumbnails")) metadata.add("camera_thumbnails", new JsonObject());
        var images = metadata.getAsJsonObject("camera_thumbnails");
        if (!images.has(pointer)) images.add(pointer, new JsonArray());
        var values = images.getAsJsonArray(pointer);
        while (values.size() < count) values.add("");
        return values;
    }

    static void edit(AuthoringDocument document, String pointer, int index, String action, Consumer<JsonObject> change) {
        JsonObject owner = EditorDocumentOperations.cameraOwner(document, pointer);
        int count = owner.getAsJsonObject("camera").getAsJsonArray("shots").size();
        if (index < 0 || index >= count) throw new IllegalArgumentException("Select an existing shot");
        document.edit("Edit camera sequence", root -> {
            JsonObject selected = JsonPointers.get(root, pointer).getAsJsonObject();
            JsonArray shots = selected.getAsJsonObject("camera").getAsJsonArray("shots");
            mutate(shots, index, action, change);
            if (shots.isEmpty()) selected.remove("camera");
            else DialogueCamera.read(selected);
        }, metadata -> mutate(thumbnails(metadata, pointer, count), index, action, ignored -> { }));
    }

    private static void mutate(JsonArray values, int index, String action, Consumer<JsonObject> change) {
        switch (action) {
            case "delete" -> values.remove(index);
            case "duplicate" -> values.add(values.get(index).deepCopy());
            case "up", "down" -> {
                int next = index + (action.equals("up") ? -1 : 1);
                if (next < 0 || next >= values.size()) throw new IllegalArgumentException("Shot is already at the end");
                var previous = values.get(index);
                values.set(index, values.get(next)); values.set(next, previous);
            }
            default -> { if (values.get(index).isJsonObject()) change.accept(values.get(index).getAsJsonObject()); }
        }
    }
}
