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
        Camera camera = com.jvn.villagerretaliation.dialogue.DialogueCamera.read(exchange);
        var voices = new LinkedHashMap<String, ResourceLocation>();
        if (exchange.has("voices")) exchange.getAsJsonObject("voices").entrySet().forEach(entry ->
                voices.put(entry.getKey(), ResourceLocation.parse(entry.getValue().getAsString())));
        return new ConversationLinePresentation(camera, voices);
    }

    public static JsonObject cameraSchema() {
        return com.jvn.villagerretaliation.dialogue.DialogueCamera.schema();
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

    public enum Target { SPEAKER, CHARACTER, NONE, GROUP }

    public record Camera(Target target, Double zoom, java.util.List<com.jvn.villagerretaliation.dialogue.DialogueCamera.Shot> shots) {
        public Camera(Target target, Double zoom) { this(target, zoom, java.util.List.of()); }

        public Camera {
            shots = java.util.List.copyOf(shots);
            if (shots.size() > com.jvn.villagerretaliation.dialogue.DialogueCamera.MAX_SHOTS
                    || shots.isEmpty() && target == Target.GROUP || !shots.isEmpty() && (target != Target.SPEAKER || zoom != null))
                throw new IllegalArgumentException("Invalid dialogue camera sequence");
            if (target == null || zoom != null && (target == Target.NONE || !Double.isFinite(zoom) || zoom < 0 || zoom > MAX_ZOOM))
                throw new IllegalArgumentException("Invalid conversation camera cue.");
        }
    }
}
