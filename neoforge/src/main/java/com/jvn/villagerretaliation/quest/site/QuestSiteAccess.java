package com.jvn.villagerretaliation.quest.site;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public final class QuestSiteAccess {
    private final JsonObject definition;
    private final transient List<Route> routes;
    private final transient List<Approach> approaches;
    private final transient List<BlockPos> guardPositions;

    public record Route(String id, String label, BlockPos offset, int radius, List<DialogueCondition> revealWhen) { }
    public record Approach(String id, String route, boolean guardsDefeated, List<DialogueCondition> conditions) { }

    public QuestSiteAccess(JsonObject value) {
        definition = value.deepCopy();
        routes = objects(value, "routes").stream().map(route -> new Route(route.get("id").getAsString(),
                route.get("label").getAsString(), position(route.getAsJsonObject("offset")),
                route.has("radius") ? route.get("radius").getAsInt() : 2, conditions(route, "reveal_when"))).toList();
        approaches = objects(value, "approaches").stream().map(approach -> new Approach(approach.get("id").getAsString(),
                approach.has("route") ? approach.get("route").getAsString() : "",
                approach.has("guards_defeated") && approach.get("guards_defeated").getAsBoolean(), conditions(approach, "conditions"))).toList();
        guardPositions = objects(value, "guard_positions").stream().map(QuestSiteAccess::position).toList();
        var routeIds = new HashSet<String>();
        for (var route : routes) if (!routeIds.add(route.id())) throw new IllegalArgumentException("Duplicate site route: " + route.id());
        var approachIds = new HashSet<String>();
        for (var approach : approaches) {
            if (!approachIds.add(approach.id())) throw new IllegalArgumentException("Duplicate site approach: " + approach.id());
            if (!approach.route().isBlank() && !routeIds.contains(approach.route()))
                throw new IllegalArgumentException("Unknown approach route: " + approach.route());
            if (approach.route().isBlank() && !approach.guardsDefeated() && approach.conditions().isEmpty())
                throw new IllegalArgumentException("An approach requires a route, defeated guards, or conditions.");
        }
    }

    public List<Route> routes() { return routes; }
    public List<Approach> approaches() { return approaches; }
    public List<BlockPos> guardPositions() { return guardPositions; }

    private static List<JsonObject> objects(JsonObject root, String field) {
        return !root.has(field) ? List.of() : java.util.stream.StreamSupport.stream(root.getAsJsonArray(field).spliterator(), false)
                .map(value -> value.getAsJsonObject()).toList();
    }

    private static List<DialogueCondition> conditions(JsonObject root, String field) {
        if (!root.has(field)) return List.of();
        var input = new JsonObject();
        input.add("conditions", root.get(field));
        var conditions = DialogueCondition.readList(ResourceLocation.parse("villagerretaliation:quest_site_access"), field, input);
        if (conditions.stream().anyMatch(condition -> condition instanceof DialogueCondition.Invalid))
            throw new IllegalArgumentException("Invalid site access condition in " + field);
        return conditions;
    }

    private static BlockPos position(JsonObject object) {
        return new BlockPos(object.get("x").getAsInt(), object.get("y").getAsInt(), object.get("z").getAsInt());
    }

    public static List<String> referenceErrors(JsonObject quest) {
        var errors = new java.util.ArrayList<String>();
        var site = quest.has("target") && quest.get("target").isJsonObject()
                ? quest.getAsJsonObject("target").get("site") : null;
        JsonObject access = site != null && site.isJsonObject() && site.getAsJsonObject().has("access")
                && site.getAsJsonObject().get("access").isJsonObject() ? site.getAsJsonObject().getAsJsonObject("access") : new JsonObject();
        checkReferences(quest, "", quest.has("id") && quest.get("id").isJsonPrimitive() ? quest.get("id").getAsString() : "", site != null, access, errors);
        return errors;
    }

    private static void checkReferences(com.google.gson.JsonElement value, String pointer, String quest, boolean hasSite,
            JsonObject access, List<String> errors) {
        if (value.isJsonArray()) {
            for (int i = 0; i < value.getAsJsonArray().size(); i++) checkReferences(value.getAsJsonArray().get(i), pointer + "/" + i, quest, hasSite, access, errors);
        } else if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            if (object.has("type") && object.get("type").isJsonPrimitive() && object.get("type").getAsString().equals("quest_site")
                    && object.has("quest") && object.get("quest").isJsonPrimitive() && object.get("quest").getAsString().equals(quest)) {
                if (!hasSite) errors.add(pointer + ": this quest has no site");
                for (String field : List.of("route", "approach")) if (object.has(field) && object.get(field).isJsonPrimitive()) {
                    String plural = field.equals("route") ? "routes" : "approaches";
                    var definitions = access.has(plural) && access.get(plural).isJsonArray() ? access.getAsJsonArray(plural) : new com.google.gson.JsonArray();
                    boolean exists = java.util.stream.StreamSupport.stream(definitions.spliterator(), false).anyMatch(entry -> entry.isJsonObject()
                            && entry.getAsJsonObject().has("id") && entry.getAsJsonObject().get("id").equals(object.get(field)));
                    if (!exists) errors.add(pointer + "/" + field + ": unknown site " + field);
                }
            }
            for (var entry : object.entrySet()) checkReferences(entry.getValue(), pointer + "/" + entry.getKey(), quest, hasSite, access, errors);
        }
    }

    public static JsonObject schema() {
        var root = JsonParser.parseString("""
                {"type":"object","additionalProperties":false,"required":["approaches"],"properties":{
                  "routes":{"type":"array","maxItems":16,"items":{"type":"object","additionalProperties":false,
                    "required":["id","label","offset"],"properties":{
                      "id":{"type":"string","pattern":"^[a-z0-9_.-]+$","maxLength":64},
                      "label":{"type":"string","minLength":1,"maxLength":128},
                      "radius":{"type":"integer","minimum":1,"maximum":8},
                      "reveal_when":{"type":"array","minItems":1,"maxItems":16,"items":{"$ref":"#/$defs/condition"}}}}},
                  "approaches":{"type":"array","minItems":1,"maxItems":16,"items":{"type":"object","additionalProperties":false,
                    "required":["id"],"properties":{
                      "id":{"type":"string","pattern":"^[a-z0-9_.-]+$","maxLength":64},
                      "route":{"type":"string","pattern":"^[a-z0-9_.-]+$","maxLength":64},
                      "guards_defeated":{"type":"boolean"},
                      "conditions":{"type":"array","minItems":1,"maxItems":16,"items":{"$ref":"#/$defs/condition"}}}}},
                  "guard_positions":{"type":"array","minItems":1,"maxItems":8,"uniqueItems":true}
                }}
                """).getAsJsonObject();
        var position = JsonParser.parseString("""
                {"type":"object","additionalProperties":false,"required":["x","y","z"],"properties":{
                  "x":{"type":"integer","minimum":-16,"maximum":16},
                  "y":{"type":"integer","minimum":-8,"maximum":8},
                  "z":{"type":"integer","minimum":-16,"maximum":16}}}
                """).getAsJsonObject();
        var properties = root.getAsJsonObject("properties");
        properties.getAsJsonObject("routes").getAsJsonObject("items").getAsJsonObject("properties").add("offset", position.deepCopy());
        properties.getAsJsonObject("guard_positions").add("items", position);
        root.addProperty("description", "Named alternatives for freeing a captive. Any matching approach permits release. No approach is selected until release, and all share the same rescue progress.");
        properties.getAsJsonObject("routes").addProperty("description", "Offsets are relative to the site center in world axes, with positive X east and positive Z south. Routes are always physically accessible. Reaching one records a visit for that player and run. reveal_when only reveals its location.");
        properties.getAsJsonObject("approaches").addProperty("description", "Requirements within an approach are combined with AND. The first matching approach records its id at release. A route requirement means the player has visited that route. Omitted access preserves the default guard-defeat requirement.");
        properties.getAsJsonObject("guard_positions").addProperty("description", "Optional spawn offsets from the site center. Supply exactly one position per guard. This does not change guard AI.");
        return root;
    }
}
