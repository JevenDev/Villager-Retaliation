package com.jvn.villagerretaliation.dialogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationLinePresentation.Camera;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationLinePresentation.Target;
import com.jvn.villagerretaliation.quest.schema.QuestJsonSchema;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

public final class DialogueCamera {
    public static final int MAX_SHOTS = 32;
    public static final double MAX_DISTANCE = 8;
    public static final double MIN_FOV = 10;
    public static final double MAX_FOV = 110;
    public static final double MAX_SECONDS = 600;

    private DialogueCamera() { }

    public enum Preset {
        FIRST_PERSON, THIRD_PERSON, OVER_SHOULDER_LEFT, OVER_SHOULDER_RIGHT,
        BIRDS_EYE, BEHIND, TELESCOPE, SPEAKER_FACING, CUSTOM
    }
    public enum Anchor { PLAYER, SPEAKER }
    public enum Placement { RELATIVE, FIXED }
    public enum Perspective { FIRST_PERSON, THIRD_PERSON }
    public enum Transition { CUT, LERP }
    public enum Easing { LINEAR, SMOOTHSTEP }

    public record Shot(Preset preset, Anchor anchor, Placement placement, Perspective perspective,
            Vec3 offset, Target target, Double fov, Transition transition, double durationSeconds,
            Easing easing, double holdSeconds) {
        public Shot {
            Objects.requireNonNull(preset);
            Objects.requireNonNull(anchor);
            Objects.requireNonNull(placement);
            Objects.requireNonNull(perspective);
            Objects.requireNonNull(offset);
            Objects.requireNonNull(transition);
            Objects.requireNonNull(easing);
            if (target == null || target == Target.NONE || !Double.isFinite(offset.lengthSqr())
                    || Math.abs(offset.x) > MAX_DISTANCE || Math.abs(offset.y) > MAX_DISTANCE || Math.abs(offset.z) > MAX_DISTANCE
                    || fov != null && (!Double.isFinite(fov) || fov < MIN_FOV || fov > MAX_FOV)
                    || !seconds(holdSeconds) || !seconds(durationSeconds)
                    || (transition == Transition.LERP ? durationSeconds < 0.001 : durationSeconds != 0 || easing != Easing.LINEAR))
                throw new IllegalArgumentException("Invalid dialogue camera shot");
        }

        public double progress(double seconds) {
            double value = transition == Transition.CUT ? 1 : Math.clamp(seconds / durationSeconds, 0, 1);
            return easing == Easing.SMOOTHSTEP ? value * value * (3 - 2 * value) : value;
        }
    }

    private static boolean seconds(double value) { return Double.isFinite(value) && value >= 0 && value <= MAX_SECONDS; }

    public static Camera read(JsonObject entry) {
        if (!entry.has("camera")) return null;
        var issues = QuestJsonSchema.validate(entry.get("camera"), schema());
        if (!issues.isEmpty()) throw new IllegalArgumentException("Invalid dialogue camera: " + issues);
        var value = entry.getAsJsonObject("camera");
        if (!value.has("preset") && !value.has("shots"))
            return new Camera(enumeration(value, "target", Target.class, Target.SPEAKER),
                    value.has("zoom") ? value.get("zoom").getAsDouble() : null);
        var shots = new ArrayList<Shot>();
        if (value.has("shots")) {
            for (var raw : value.getAsJsonArray("shots")) shots.add(readShot(raw.getAsJsonObject()));
        } else shots.add(readShot(value));
        return new Camera(Target.SPEAKER, null, shots);
    }

    private static Shot readShot(JsonObject value) {
        var preset = enumeration(value, "preset", Preset.class, Preset.CUSTOM);
        var offset = Vec3.ZERO;
        if (value.has("offset")) {
            var coordinates = value.getAsJsonArray("offset");
            offset = new Vec3(coordinates.get(0).getAsDouble(), coordinates.get(1).getAsDouble(), coordinates.get(2).getAsDouble());
        }
        var transition = value.has("transition") ? value.getAsJsonObject("transition") : new JsonObject();
        return new Shot(preset, enumeration(value, "anchor", Anchor.class,
                preset == Preset.SPEAKER_FACING ? Anchor.SPEAKER : Anchor.PLAYER),
                enumeration(value, "placement", Placement.class, Placement.RELATIVE),
                enumeration(value, "perspective", Perspective.class,
                        preset == Preset.FIRST_PERSON || preset == Preset.TELESCOPE ? Perspective.FIRST_PERSON : Perspective.THIRD_PERSON),
                offset, enumeration(value, "target", Target.class, Target.SPEAKER),
                value.has("fov") ? value.get("fov").getAsDouble() : null,
                enumeration(transition, "type", Transition.class, Transition.CUT),
                transition.has("duration_seconds") ? transition.get("duration_seconds").getAsDouble() : 0,
                enumeration(transition, "easing", Easing.class, Easing.LINEAR),
                value.has("hold_seconds") ? value.get("hold_seconds").getAsDouble() : 0);
    }

    private static <T extends Enum<T>> T enumeration(JsonObject value, String key, Class<T> type, T fallback) {
        return value.has(key) ? Enum.valueOf(type, value.get(key).getAsString().toUpperCase(Locale.ROOT)) : fallback;
    }

    public static void write(RegistryFriendlyByteBuf buffer, Camera camera) {
        buffer.writeBoolean(camera != null);
        if (camera == null) return;
        buffer.writeEnum(camera.target());
        buffer.writeBoolean(camera.zoom() != null);
        if (camera.zoom() != null) buffer.writeDouble(camera.zoom());
        buffer.writeVarInt(camera.shots().size());
        for (var shot : camera.shots()) {
            buffer.writeEnum(shot.preset()); buffer.writeEnum(shot.anchor()); buffer.writeEnum(shot.placement());
            buffer.writeEnum(shot.perspective());
            buffer.writeDouble(shot.offset().x); buffer.writeDouble(shot.offset().y); buffer.writeDouble(shot.offset().z);
            buffer.writeEnum(shot.target()); buffer.writeBoolean(shot.fov() != null);
            if (shot.fov() != null) buffer.writeDouble(shot.fov());
            buffer.writeEnum(shot.transition()); buffer.writeDouble(shot.durationSeconds());
            buffer.writeEnum(shot.easing()); buffer.writeDouble(shot.holdSeconds());
        }
    }

    public static Camera read(RegistryFriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) return null;
        var target = buffer.readEnum(Target.class);
        Double zoom = buffer.readBoolean() ? buffer.readDouble() : null;
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_SHOTS) throw new IllegalArgumentException("Invalid camera shot count");
        var shots = new ArrayList<Shot>();
        for (int index = 0; index < count; index++) shots.add(new Shot(
                buffer.readEnum(Preset.class), buffer.readEnum(Anchor.class), buffer.readEnum(Placement.class),
                buffer.readEnum(Perspective.class), new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                buffer.readEnum(Target.class), buffer.readBoolean() ? buffer.readDouble() : null,
                buffer.readEnum(Transition.class), buffer.readDouble(), buffer.readEnum(Easing.class), buffer.readDouble()));
        return new Camera(target, zoom, shots);
    }

    public static JsonObject json(Camera camera) {
        var value = new JsonObject();
        if (camera.shots().isEmpty()) {
            value.addProperty("target", camera.target().name().toLowerCase(Locale.ROOT));
            if (camera.zoom() != null) value.addProperty("zoom", camera.zoom());
            return value;
        }
        var shots = new JsonArray();
        for (var shot : camera.shots()) {
            var entry = new JsonObject();
            entry.addProperty("preset", shot.preset().name().toLowerCase(Locale.ROOT));
            entry.addProperty("anchor", shot.anchor().name().toLowerCase(Locale.ROOT));
            entry.addProperty("placement", shot.placement().name().toLowerCase(Locale.ROOT));
            entry.addProperty("perspective", shot.perspective().name().toLowerCase(Locale.ROOT));
            entry.addProperty("target", shot.target().name().toLowerCase(Locale.ROOT));
            var offset = new JsonArray();
            offset.add(shot.offset().x);
            offset.add(shot.offset().y);
            offset.add(shot.offset().z);
            entry.add("offset", offset);
            if (shot.fov() != null) entry.addProperty("fov", shot.fov());
            entry.addProperty("hold_seconds", shot.holdSeconds());
            var transition = new JsonObject();
            transition.addProperty("type", shot.transition().name().toLowerCase(Locale.ROOT));
            if (shot.transition() == Transition.LERP) {
                transition.addProperty("duration_seconds", shot.durationSeconds());
                transition.addProperty("easing", shot.easing().name().toLowerCase(Locale.ROOT));
            }
            entry.add("transition", transition);
            shots.add(entry);
        }
        value.add("shots", shots);
        return value;
    }

    public static Vec3 clamp(Vec3 position, Vec3 player) {
        var delta = position.subtract(player);
        return delta.lengthSqr() > MAX_DISTANCE * MAX_DISTANCE ? player.add(delta.normalize().scale(MAX_DISTANCE)) : position;
    }

    public static JsonObject schema() {
        var schema = JsonParser.parseString("""
                {"oneOf":[
                  {"type":"object","additionalProperties":false,"required":["target"],"properties":{
                    "target":{"enum":["speaker","character","none"]},
                    "zoom":{"type":"number","minimum":0,"maximum":0.45}},
                    "if":{"properties":{"target":{"const":"none"}}},"then":{"not":{"required":["zoom"]}}},
                  {},
                  {"type":"object","additionalProperties":false,"required":["shots"],"properties":{
                    "shots":{"type":"array","minItems":1,"maxItems":32,"items":{}}}}
                ]}
                """).getAsJsonObject();
        var shot = JsonParser.parseString("""
                {"type":"object","additionalProperties":false,"required":["preset"],"properties":{
                  "preset":{"enum":["first_person","third_person","over_shoulder_left","over_shoulder_right",
                    "birds_eye","behind","telescope","speaker_facing","custom"]},
                  "anchor":{"enum":["player","speaker"]},"placement":{"enum":["relative","fixed"]},
                  "perspective":{"enum":["first_person","third_person"]},
                  "offset":{"type":"array","minItems":3,"maxItems":3,"items":{"type":"number","minimum":-8,"maximum":8}},
                  "target":{"enum":["speaker","character","group"]},
                  "fov":{"type":"number","minimum":10,"maximum":110},
                  "hold_seconds":{"type":"number","minimum":0,"maximum":600},
                  "transition":{"oneOf":[
                    {"type":"object","additionalProperties":false,"required":["type"],"properties":{"type":{"const":"cut"}}},
                    {"type":"object","additionalProperties":false,"required":["type","duration_seconds"],"properties":{
                      "type":{"const":"lerp"},"duration_seconds":{"type":"number","minimum":0.001,"maximum":600},
                      "easing":{"enum":["linear","smoothstep"]}}}]}},
                  "if":{"properties":{"preset":{"const":"custom"}}},"then":{"required":["anchor","offset","perspective"]}}
                """).getAsJsonObject();
        schema.getAsJsonArray("oneOf").set(1, shot);
        schema.getAsJsonArray("oneOf").get(2).getAsJsonObject().getAsJsonObject("properties")
                .getAsJsonObject("shots").add("items", shot.deepCopy());
        return schema;
    }
}
