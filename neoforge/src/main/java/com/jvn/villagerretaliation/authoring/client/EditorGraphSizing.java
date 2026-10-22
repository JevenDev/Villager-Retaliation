package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import java.util.Map;
import java.util.Set;

final class EditorGraphSizing {
    private EditorGraphSizing() { }

    record Drag(EditorGraph.Card original, float mouseX, float mouseY, float zoom) {
        EditorGraph.Card preview(EditorGraph.Card card, float x, float y) {
            return card.node().id().equals(original.node().id())
                    ? card.resized(original.width() + (x - mouseX) / zoom, original.height() + (y - mouseY) / zoom) : card;
        }
    }

    static EditorGraph.Card restore(EditorGraph.Card card, JsonObject metadata) {
        var point = EditorSchema.object(EditorSchema.object(metadata.get("node_positions")).get(card.node().id()));
        return card.resized(number(point, "width", EditorGraph.WIDTH), number(point, "height", 0));
    }

    private static float number(JsonObject point, String key, float fallback) {
        var value = point.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return fallback;
        float number = value.getAsFloat();
        return Float.isFinite(number) && number > 0 ? number : fallback;
    }

    static void positions(JsonObject metadata, Map<String, float[]> positions) {
        var previous = EditorSchema.object(metadata.get("node_positions"));
        var values = new JsonObject();
        positions.forEach((id, position) -> {
            var point = EditorSchema.object(previous.get(id)).deepCopy();
            point.addProperty("x", position[0]);
            point.addProperty("y", position[1]);
            values.add(id, point);
        });
        metadata.add("node_positions", values);
        metadata.addProperty("graph_card_layout", 1);
    }

    static void save(AuthoringDocument document, EditorGraph.Card card, Map<String, float[]> positions) {
        document.editMetadata("Resize node", metadata -> {
            positions(metadata, positions);
            var values = metadata.getAsJsonObject("node_positions");
            if (!values.has(card.node().id())) values.add(card.node().id(), new JsonObject());
            var point = values.getAsJsonObject(card.node().id());
            point.addProperty("width", card.width());
            point.addProperty("height", card.height());
        });
    }

    static void reset(AuthoringDocument document, Set<String> selection) {
        document.editMetadata("Reset node sizes", metadata -> {
            var positions = EditorSchema.object(metadata.get("node_positions"));
            for (String id : selection) {
                var point = EditorSchema.object(positions.get(id));
                point.remove("width"); point.remove("height");
            }
        });
    }
}
