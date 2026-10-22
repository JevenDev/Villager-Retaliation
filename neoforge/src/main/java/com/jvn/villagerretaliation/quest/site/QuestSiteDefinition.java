package com.jvn.villagerretaliation.quest.site;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

public record QuestSiteDefinition(Selection selection, Terrain terrain, List<ResourceLocation> structures,
        List<ResourceLocation> templates, int minDistance, int maxDistance, int attempts, int buffer,
        MapStyle mapStyle, int searchRadius, Rescue rescue, int guards, ResourceLocation supportTag, int minimumSupportBlocks,
        net.minecraft.core.BlockPos escortDestination, QuestSiteAccess access) {
    public enum Selection { EXISTING_ONLY, PREFER_EXISTING, NEW_SITE }
    public enum Terrain { NEW_TERRAIN_ONLY, GENERATED_WILDERNESS }
    public enum MapStyle { EXACT, SEARCH_CIRCLE }
    public enum Rescue { NONE, FREE_AT_SITE, ESCORT_HOME }

    public QuestSiteDefinition(Selection selection, Terrain terrain, List<ResourceLocation> structures,
            List<ResourceLocation> templates, int minDistance, int maxDistance, int attempts, int buffer,
            MapStyle mapStyle, int searchRadius, Rescue rescue, int guards, ResourceLocation supportTag,
            int minimumSupportBlocks, net.minecraft.core.BlockPos escortDestination) {
        this(selection, terrain, structures, templates, minDistance, maxDistance, attempts, buffer, mapStyle,
                searchRadius, rescue, guards, supportTag, minimumSupportBlocks, escortDestination, null);
    }

    public static QuestSiteDefinition read(JsonObject object) {
        if (object == null) return null;
        var issues = com.jvn.villagerretaliation.quest.schema.QuestJsonSchema.validate(object, ValidationSchema.VALUE);
        if (!issues.isEmpty()) throw new IllegalArgumentException("Invalid quest site: " + issues);
        var result = new QuestSiteDefinition(
                Selection.valueOf(text(object, "selection", "existing_only").toUpperCase(java.util.Locale.ROOT)),
                Terrain.valueOf(text(object, "terrain", "new_terrain_only").toUpperCase(java.util.Locale.ROOT)),
                ids(object, "structures"), ids(object, "templates"), number(object, "min_distance", 256),
                number(object, "max_distance", 2048), number(object, "attempts", 32), number(object, "buffer", 16),
                MapStyle.valueOf(text(object, "map_style", "exact").toUpperCase(java.util.Locale.ROOT)),
                number(object, "search_radius", 96),
                Rescue.valueOf(text(object, "rescue", "none").toUpperCase(java.util.Locale.ROOT)),
                number(object, "guards", 2), ResourceLocation.parse(text(object, "support_tag", "minecraft:planks")),
                number(object, "minimum_support_blocks", 32), destination(object),
                object.has("access") ? new QuestSiteAccess(object.getAsJsonObject("access")) : null);
        if (result.minDistance > result.maxDistance) throw new IllegalArgumentException("Site minimum distance exceeds maximum distance.");
        if (result.selection != Selection.NEW_SITE && result.structures.isEmpty())
            throw new IllegalArgumentException("Existing quest sites require structures.");
        if (result.selection != Selection.EXISTING_ONLY && result.templates.isEmpty())
            throw new IllegalArgumentException("Quest site placement requires templates.");
        if (result.access != null && !result.access.guardPositions().isEmpty() && result.access.guardPositions().size() != result.guards)
            throw new IllegalArgumentException("Guard positions must match the number of guards.");
        return result;
    }

    private static net.minecraft.core.BlockPos destination(JsonObject object) {
        if (!object.has("escort_destination")) return null;
        var destination = object.getAsJsonObject("escort_destination");
        return new net.minecraft.core.BlockPos(destination.get("x").getAsInt(), destination.get("y").getAsInt(), destination.get("z").getAsInt());
    }

    private static List<ResourceLocation> ids(JsonObject object, String field) {
        if (!object.has(field)) return List.of();
        return java.util.stream.StreamSupport.stream(object.getAsJsonArray(field).spliterator(), false)
                .map(value -> ResourceLocation.parse(value.getAsString())).toList();
    }

    private static String text(JsonObject object, String field, String fallback) {
        return object.has(field) ? object.get(field).getAsString() : fallback;
    }

    private static int number(JsonObject object, String field, int fallback) {
        return object.has(field) ? object.get(field).getAsInt() : fallback;
    }

    private static final class ValidationSchema {
        private static final JsonObject VALUE = create();
        private static JsonObject create() {
            var schema = schema();
            schema.add("$defs", com.jvn.villagerretaliation.quest.schema.ContentSchemaDefinitions.definitions());
            return schema;
        }
    }

    public static JsonObject schema() {
        var schema = JsonParser.parseString("""
                {"type":"object","additionalProperties":false,"properties":{
                  "selection":{"enum":["existing_only","prefer_existing","new_site"]},
                  "terrain":{"description":"Defaults to new terrain only. Generated wilderness uses conservative construction checks and cannot prove that land is untouched.","enum":["new_terrain_only","generated_wilderness"]},
                  "structures":{"type":"array","maxItems":8,"uniqueItems":true,"items":{"type":"string","pattern":"^[a-z0-9_.-]+:[a-z0-9_./-]+$"}},
                  "templates":{"type":"array","maxItems":8,"uniqueItems":true,"items":{"type":"string","pattern":"^[a-z0-9_.-]+:[a-z0-9_./-]+$"}},
                  "min_distance":{"type":"integer","minimum":128,"maximum":8192},
                  "max_distance":{"type":"integer","minimum":128,"maximum":8192},
                  "attempts":{"type":"integer","minimum":1,"maximum":64},
                  "buffer":{"type":"integer","minimum":8,"maximum":32},
                  "map_style":{"enum":["exact","search_circle"]},
                  "search_radius":{"type":"integer","minimum":32,"maximum":192},
                  "rescue":{"enum":["none","free_at_site","escort_home"]},
                  "guards":{"type":"integer","minimum":0,"maximum":8},
                  "support_tag":{"description":"Block tag used to check whether an existing structure still supports this encounter. Defaults to minecraft:planks.","type":"string","pattern":"^[a-z0-9_.-]+:[a-z0-9_./-]+$"},
                  "minimum_support_blocks":{"type":"integer","minimum":1,"maximum":512},
                  "escort_destination":{"description":"Optional escort destination in the quest site dimension. Defaults to the quest giver position.","type":"object","additionalProperties":false,"required":["x","y","z"],"properties":{
                    "x":{"type":"integer","minimum":-29999000,"maximum":29999000},
                    "y":{"type":"integer","minimum":-2048,"maximum":2048},
                    "z":{"type":"integer","minimum":-29999000,"maximum":29999000}
                  }}
                },"allOf":[
                  {"if":{"required":["selection"],"properties":{"selection":{"enum":["prefer_existing","new_site"]}}},
                   "then":{"required":["templates"],"properties":{"templates":{"minItems":1}}}},
                  {"if":{"not":{"required":["selection"],"properties":{"selection":{"const":"new_site"}}}},
                   "then":{"required":["structures"],"properties":{"structures":{"minItems":1}}}}
                ]}
                """).getAsJsonObject();
        schema.getAsJsonObject("properties").add("access", QuestSiteAccess.schema());
        return schema;
    }
}
