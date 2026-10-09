package com.jvn.villagerretaliation.dialogue.conversation;

import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public record ConversationLinePresentation(Camera camera, Map<String, ResourceLocation> voices) {
    public static final double MAX_ZOOM = 0.45D;
    public static final int MAX_VOICES = 64;
    public static final ConversationLinePresentation EMPTY = new ConversationLinePresentation(null, Map.of());

    public ConversationLinePresentation {
        voices = Map.copyOf(voices);
        if (voices.size() > MAX_VOICES || voices.entrySet().stream().anyMatch(entry ->
                !entry.getKey().matches("[a-z0-9_]{1,32}") || entry.getValue().toString().length() > 256))
            throw new IllegalArgumentException("Invalid conversation voice references.");
    }

    public ResourceLocation voice(String locale) {
        return voices.getOrDefault(com.jvn.villagerretaliation.util.VillagerLocale.normalize(locale), voices.get("en_us"));
    }

    public static ConversationLinePresentation read(JsonObject exchange) {
        Camera camera = null;
        if (exchange.has("camera")) {
            var value = exchange.getAsJsonObject("camera");
            camera = new Camera(Target.valueOf(value.get("target").getAsString().toUpperCase(java.util.Locale.ROOT)),
                    value.has("zoom") ? value.get("zoom").getAsDouble() : null);
        }
        var voices = new LinkedHashMap<String, ResourceLocation>();
        if (exchange.has("voices")) exchange.getAsJsonObject("voices").entrySet().forEach(entry ->
                voices.put(entry.getKey(), ResourceLocation.parse(entry.getValue().getAsString())));
        return new ConversationLinePresentation(camera, voices);
    }

    public static JsonObject cameraSchema() {
        var schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.addProperty("additionalProperties", false);
        var required = new com.google.gson.JsonArray();
        required.add("target");
        schema.add("required", required);
        var fields = new JsonObject();
        var target = new JsonObject();
        target.addProperty("type", "string");
        var targets = new com.google.gson.JsonArray();
        for (var value : Target.values()) targets.add(value.name().toLowerCase(java.util.Locale.ROOT));
        target.add("enum", targets);
        fields.add("target", target);
        var zoom = new JsonObject();
        zoom.addProperty("type", "number");
        zoom.addProperty("minimum", 0);
        zoom.addProperty("maximum", MAX_ZOOM);
        zoom.addProperty("description", "Caps the configured dialogue zoom for this exchange.");
        fields.add("zoom", zoom);
        schema.add("properties", fields);
        var none = new JsonObject();
        var noneFields = new JsonObject();
        var noneTarget = new JsonObject();
        noneTarget.addProperty("const", "none");
        noneFields.add("target", noneTarget);
        none.add("properties", noneFields);
        schema.add("if", none);
        var forbiddenZoom = new JsonObject();
        var zoomRequired = new JsonObject();
        var zoomFields = new com.google.gson.JsonArray();
        zoomFields.add("zoom");
        zoomRequired.add("required", zoomFields);
        forbiddenZoom.add("not", zoomRequired);
        schema.add("then", forbiddenZoom);
        return schema;
    }

    public static JsonObject voicesSchema() {
        var schema = new JsonObject();
        schema.addProperty("description", "Optional sound event IDs by locale, falling back to en_us when no matching voice is declared.");
        schema.addProperty("type", "object");
        schema.addProperty("minProperties", 1);
        schema.addProperty("maxProperties", MAX_VOICES);
        var locale = new JsonObject();
        locale.addProperty("type", "string");
        locale.addProperty("pattern", "^[a-z0-9_]{1,32}$(?![\\s\\S])");
        schema.add("propertyNames", locale);
        var sound = new JsonObject();
        sound.addProperty("type", "string");
        sound.addProperty("minLength", 1);
        sound.addProperty("maxLength", 256);
        sound.addProperty("pattern", "^[a-z0-9_.-]+:[a-z0-9_./-]+$(?![\\s\\S])");
        schema.add("additionalProperties", sound);
        return schema;
    }

    public enum Target { SPEAKER, CHARACTER, NONE }

    public record Camera(Target target, Double zoom) {
        public Camera {
            if (target == null || zoom != null && (target == Target.NONE || !Double.isFinite(zoom) || zoom < 0 || zoom > MAX_ZOOM))
                throw new IllegalArgumentException("Invalid conversation camera cue.");
        }
    }
}
