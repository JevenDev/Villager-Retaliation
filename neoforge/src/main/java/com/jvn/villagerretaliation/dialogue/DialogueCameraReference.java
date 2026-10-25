package com.jvn.villagerretaliation.dialogue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.DialogueCamera.Anchor;
import com.jvn.villagerretaliation.quest.schema.QuestJsonSchema;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public record DialogueCameraReference(String id, Kind kind, ResourceLocation match, Anchor origin,
        String marker, String location, BlockPos offset, int radius, String entityTag) {
    public enum Kind { ITEM, BLOCK }

    public DialogueCameraReference {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(match);
        Objects.requireNonNull(origin);
        Objects.requireNonNull(marker);
        Objects.requireNonNull(location);
        Objects.requireNonNull(offset);
        Objects.requireNonNull(entityTag);
        if (id == null || !id.matches("[a-z][a-z0-9_.-]{0,63}") || radius < 0 || radius > 16
                || Math.abs((long) offset.getX()) > 16 || Math.abs((long) offset.getY()) > 16 || Math.abs((long) offset.getZ()) > 16
                || marker.length() > 128 || location.length() > 64 || entityTag.length() > 128
                || !marker.isEmpty() && !location.isEmpty() || kind == Kind.BLOCK && !entityTag.isEmpty())
            throw new IllegalArgumentException("Invalid camera world reference");
        offset = offset.immutable();
    }

    public static DialogueCameraReference read(JsonObject value) {
        var issues = QuestJsonSchema.validate(value, schema());
        if (!issues.isEmpty()) throw new IllegalArgumentException("Invalid camera reference: " + issues);
        var offset = value.getAsJsonArray("offset");
        return new DialogueCameraReference(value.get("id").getAsString(),
                Kind.valueOf(value.get("kind").getAsString().toUpperCase(Locale.ROOT)),
                ResourceLocation.parse(value.get("match").getAsString()),
                value.has("origin") ? Anchor.valueOf(value.get("origin").getAsString().toUpperCase(Locale.ROOT)) : Anchor.SPEAKER,
                string(value, "marker"), string(value, "location"),
                offset == null ? BlockPos.ZERO : new BlockPos(offset.get(0).getAsInt(), offset.get(1).getAsInt(), offset.get(2).getAsInt()),
                value.has("radius") ? value.get("radius").getAsInt() : 8, string(value, "entity_tag"));
    }

    private static String string(JsonObject value, String key) { return value.has(key) ? value.get(key).getAsString() : ""; }

    public JsonObject json() {
        var value = new JsonObject();
        value.addProperty("id", id);
        value.addProperty("kind", kind.name().toLowerCase(Locale.ROOT));
        value.addProperty("match", match.toString());
        value.addProperty("origin", origin.name().toLowerCase(Locale.ROOT));
        value.addProperty("radius", radius);
        var coordinates = new com.google.gson.JsonArray();
        coordinates.add(offset.getX()); coordinates.add(offset.getY()); coordinates.add(offset.getZ());
        value.add("offset", coordinates);
        if (!marker.isEmpty()) value.addProperty("marker", marker);
        if (!location.isEmpty()) value.addProperty("location", location);
        if (!entityTag.isEmpty()) value.addProperty("entity_tag", entityTag);
        return value;
    }

    public record Binding(ResourceLocation dimension, UUID entity, BlockPos block, String diagnostic) {
        public Binding {
            Objects.requireNonNull(dimension);
            Objects.requireNonNull(diagnostic);
            if (diagnostic.length() > 256 || entity != null && block != null
                    || (entity != null || block != null) != diagnostic.isEmpty())
                throw new IllegalArgumentException("Invalid camera reference binding");
            if (block != null) {
                if (Math.abs((long) block.getX()) > 30_000_000 || Math.abs((long) block.getY()) > 30_000_000
                        || Math.abs((long) block.getZ()) > 30_000_000)
                    throw new IllegalArgumentException("Camera reference is outside world bounds");
                block = block.immutable();
            }
        }

        public JsonObject json() {
            var value = new JsonObject();
            value.addProperty("dimension", dimension.toString());
            value.addProperty("diagnostic", diagnostic);
            if (entity != null) value.addProperty("entity", entity.toString());
            if (block != null) {
                value.addProperty("x", block.getX()); value.addProperty("y", block.getY()); value.addProperty("z", block.getZ());
            }
            return value;
        }

        public static Binding read(JsonObject value) {
            var shape = JsonParser.parseString("""
                    {"type":"object","additionalProperties":false,"required":["dimension","diagnostic"],"properties":{
                      "dimension":{"type":"string","pattern":"^[a-z0-9_.-]+:[a-z0-9/._-]+$","maxLength":256},
                      "diagnostic":{"type":"string","maxLength":256},
                      "entity":{"type":"string","pattern":"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"},
                      "x":{"type":"integer","minimum":-30000000,"maximum":30000000},
                      "y":{"type":"integer","minimum":-30000000,"maximum":30000000},
                      "z":{"type":"integer","minimum":-30000000,"maximum":30000000}},
                     "if":{"anyOf":[{"required":["x"]},{"required":["y"]},{"required":["z"]}]},
                     "then":{"required":["x","y","z"]}}
                    """).getAsJsonObject();
            if (!QuestJsonSchema.validate(value, shape).isEmpty()) throw new IllegalArgumentException("Invalid camera reference binding");
            return new Binding(ResourceLocation.parse(value.get("dimension").getAsString()),
                    value.has("entity") ? UUID.fromString(value.get("entity").getAsString()) : null,
                    value.has("x") ? new BlockPos(value.get("x").getAsInt(), value.get("y").getAsInt(), value.get("z").getAsInt()) : null,
                    value.get("diagnostic").getAsString());
        }
    }

    public static JsonObject schema() {
        return JsonParser.parseString("""
                {"type":"object","additionalProperties":false,"required":["id","kind","match"],"properties":{
                  "id":{"type":"string","pattern":"^[a-z][a-z0-9_.-]{0,63}$"},
                  "kind":{"enum":["item","block"]},
                  "match":{"description":"Exact item or block registry ID.","type":"string","pattern":"^[a-z0-9_.-]+:[a-z0-9/._-]+$","maxLength":256},
                  "origin":{"enum":["speaker","player"]},
                  "marker":{"description":"Unique marker entity tag within 32 blocks of the origin.","type":"string","minLength":1,"maxLength":128},
                  "location":{"description":"Saved campaign location alias.","type":"string","pattern":"^[a-z][a-z0-9_.-]{0,63}$"},
                  "offset":{"description":"Block offset in world axes from the resolved origin.","type":"array","minItems":3,"maxItems":3,"items":{"type":"integer","minimum":-16,"maximum":16}},
                  "radius":{"description":"Find exactly one match in this block radius. Zero checks the origin block only.","type":"integer","minimum":0,"maximum":16},
                  "entity_tag":{"description":"Additional tag required on a dropped item.","type":"string","minLength":1,"maxLength":128}},
                 "allOf":[{"not":{"required":["marker","location"]}},
                   {"if":{"properties":{"kind":{"const":"block"}}},"then":{"not":{"required":["entity_tag"]}}}]}
                """).getAsJsonObject();
    }
}
