package com.jvn.villagerretaliation.campaign.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.quest.debug.QuestDiagnostic;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public final class CampaignSchema {
    public static final String SCHEMA_ID = "villagerretaliation:campaign/v1";
    private static final String LOCAL_ID = "^[a-z0-9][a-z0-9_./-]*$";
    private static final String RESOURCE_ID = "^[a-z0-9_.-]+:[a-z0-9_./-]+$";

    private CampaignSchema() {
    }

    public static JsonObject export() {
        JsonObject prerequisite = object(List.of(),
                "chapter", text(LOCAL_ID), "milestone", text(LOCAL_ID), "outcome", text(LOCAL_ID));
        JsonObject chapter = object(List.of("id", "title_key", "quests"),
                "id", text(LOCAL_ID), "title_key", text(""),
                "prerequisites", array(prerequisite, 0), "quests", array(text(RESOURCE_ID), 1));
        JsonObject outcome = object(List.of("id", "title_key"), "id", text(LOCAL_ID), "title_key", text(""));
        JsonObject catchUp = object(List.of("quest", "stage"), "quest", text(RESOURCE_ID), "stage", text(LOCAL_ID));
        JsonObject observation = object(List.of("event", "outcome"),
                "event", enumeration("minecraft:ender_dragon_defeated"), "outcome", text(LOCAL_ID));
        JsonObject milestone = object(List.of("id", "chapter", "quest", "outcomes"),
                "id", text(LOCAL_ID), "chapter", text(LOCAL_ID), "quest", text(RESOURCE_ID), "stage", text(LOCAL_ID),
                "irreversible", type("boolean"), "outcomes", array(outcome, 1),
                "catch_up", catchUp, "observe", observation);
        JsonObject fact = object(List.of("id", "scope", "kind"),
                "id", text(LOCAL_ID), "scope", enumeration("world", "player"),
                "kind", enumeration("tag", "variable", "counter"));
        JsonObject experience = type("integer");
        experience.addProperty("minimum", 0);
        experience.addProperty("maximum", Integer.MAX_VALUE);
        JsonObject reward = object(List.of("id", "quest", "scope"), "id", text(LOCAL_ID), "quest", text(RESOURCE_ID),
                "scope", enumeration("personal", "participants", "shared"), "milestone", text(LOCAL_ID), "outcome", text(LOCAL_ID),
                "experience", experience, "loot_table", text(RESOURCE_ID));
        JsonObject coordinate = type("integer");
        coordinate.addProperty("minimum", -30_000_000);
        coordinate.addProperty("maximum", 30_000_000);
        JsonObject radius = type("integer");
        radius.addProperty("minimum", 1);
        radius.addProperty("maximum", CampaignLocationDefinition.MAX_SEARCH_RADIUS);
        JsonObject location = object(List.of("id", "title_key", "source", "dimension"),
                "id", text(LOCAL_ID), "title_key", text(""), "source", enumeration("marker", "village", "structure"),
                "dimension", text(RESOURCE_ID), "origin", object(List.of("x", "y", "z"),
                        "x", coordinate, "y", coordinate, "z", coordinate),
                "search_radius", radius, "structure", text(RESOURCE_ID));
        JsonObject characterRadius = type("integer");
        characterRadius.addProperty("minimum", 1);
        characterRadius.addProperty("maximum", CampaignCharacterDefinition.MAX_SEARCH_RADIUS);
        JsonObject character = object(List.of("id", "title_key", "source", "location", "loss_policy"),
                "id", text(LOCAL_ID), "title_key", text(""), "source", enumeration("existing_villager"),
                "location", text(LOCAL_ID), "search_radius", characterRadius, "profession", text(RESOURCE_ID),
                "loss_policy", enumeration("downed"));
        JsonObject revision = type("integer");
        revision.addProperty("minimum", 1);
        revision.addProperty("maximum", Integer.MAX_VALUE);
        JsonObject root = object(List.of("schema", "id", "revision", "title_key", "localization_prefix",
                        "quest_bundles", "chapters", "milestones"),
                "schema", enumeration(SCHEMA_ID), "id", text(RESOURCE_ID), "revision", revision,
                "title_key", text(""), "localization_prefix", text(""),
                "quest_bundles", array(text(RESOURCE_ID), 1), "chapters", array(chapter, 1),
                "milestones", array(milestone, 0), "facts", array(fact, 0), "rewards", array(reward, 0),
                "locations", array(location, 0), "characters", array(character, 0));
        root.addProperty("$schema", "https://json-schema.org/draft/2020-12/schema");
        root.addProperty("$id", "https://villagerretaliation.dev/schema/campaign-v1.schema.json");
        root.addProperty("title", "Villager Retaliation Campaign v1");
        return root;
    }

    public static List<QuestDiagnostic> validate(ResourceLocation source, JsonElement input) {
        List<QuestDiagnostic> diagnostics = new ArrayList<>();
        validate(source, input, export(), "", diagnostics);
        return List.copyOf(diagnostics);
    }

    private static void validate(ResourceLocation source, JsonElement value, JsonObject schema,
            String pointer, List<QuestDiagnostic> diagnostics) {
        String type = schema.get("type").getAsString();
        boolean matches = value != null && switch (type) {
            case "object" -> value.isJsonObject();
            case "array" -> value.isJsonArray();
            case "string" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
            case "boolean" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean();
            case "integer" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() && integer(value);
            default -> throw new IllegalStateException("unsupported campaign schema type " + type);
        };
        if (!matches) {
            diagnostics.add(error(source, pointer, "expected " + type));
            return;
        }
        if (schema.has("enum")) {
            boolean accepted = false;
            for (JsonElement candidate : schema.getAsJsonArray("enum")) accepted |= candidate.equals(value);
            if (!accepted) diagnostics.add(error(source, pointer, "unsupported value " + value));
        }
        switch (type) {
            case "object" -> {
                JsonObject object = value.getAsJsonObject();
                JsonObject properties = schema.getAsJsonObject("properties");
                for (JsonElement required : schema.getAsJsonArray("required")) {
                    String key = required.getAsString();
                    if (!object.has(key)) diagnostics.add(error(source, child(pointer, key), "required field is missing"));
                }
                for (var entry : object.entrySet()) {
                    if (!properties.has(entry.getKey())) {
                        diagnostics.add(error(source, child(pointer, entry.getKey()), "unsupported field"));
                    } else {
                        validate(source, entry.getValue(), properties.getAsJsonObject(entry.getKey()),
                                child(pointer, entry.getKey()), diagnostics);
                    }
                }
            }
            case "array" -> {
                JsonArray array = value.getAsJsonArray();
                if (array.size() < schema.get("minItems").getAsInt()) {
                    diagnostics.add(error(source, pointer, "array requires at least " + schema.get("minItems") + " entries"));
                }
                if (array.size() > schema.get("maxItems").getAsInt()) {
                    diagnostics.add(error(source, pointer, "array exceeds " + schema.get("maxItems") + " entries"));
                    return;
                }
                for (int index = 0; index < array.size(); index++) {
                    validate(source, array.get(index), schema.getAsJsonObject("items"), pointer + "/" + index, diagnostics);
                }
            }
            case "string" -> {
                String text = value.getAsString();
                if (text.isBlank() || text.length() > schema.get("maxLength").getAsInt()) {
                    diagnostics.add(error(source, pointer, "text must be nonblank and at most " + schema.get("maxLength") + " characters"));
                }
                if (schema.has("pattern") && !text.matches(schema.get("pattern").getAsString())) {
                    diagnostics.add(error(source, pointer, "text does not match " + schema.get("pattern").getAsString()));
                }
            }
            case "integer" -> {
                BigDecimal number = value.getAsBigDecimal();
                if (number.compareTo(schema.get("minimum").getAsBigDecimal()) < 0
                        || number.compareTo(schema.get("maximum").getAsBigDecimal()) > 0) {
                    diagnostics.add(error(source, pointer, "integer is outside the supported range"));
                }
            }
            default -> {
            }
        }
    }

    private static boolean integer(JsonElement value) {
        try {
            return value.getAsBigDecimal().stripTrailingZeros().scale() <= 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    static QuestDiagnostic error(ResourceLocation source, String pointer, String message) {
        return new QuestDiagnostic(QuestDiagnostic.Severity.ERROR, "campaign.invalid", source, "",
                source == null ? "" : source.toString(), pointer, message,
                "Correct the campaign definition at this source location and reload datapacks.", Set.of());
    }

    private static String child(String pointer, String key) {
        return pointer + "/" + key.replace("~", "~0").replace("/", "~1");
    }

    private static JsonObject object(List<String> required, Object... fields) {
        JsonObject result = type("object");
        result.addProperty("additionalProperties", false);
        JsonArray requiredFields = new JsonArray();
        required.forEach(requiredFields::add);
        result.add("required", requiredFields);
        JsonObject properties = new JsonObject();
        for (int index = 0; index < fields.length; index += 2) {
            properties.add((String) fields[index], (JsonElement) fields[index + 1]);
        }
        result.add("properties", properties);
        return result;
    }

    private static JsonObject type(String type) {
        JsonObject result = new JsonObject();
        result.addProperty("type", type);
        return result;
    }

    private static JsonObject text(String pattern) {
        JsonObject result = type("string");
        result.addProperty("minLength", 1);
        result.addProperty("maxLength", 256);
        result.addProperty("pattern", pattern.isBlank() ? "^[a-zA-Z0-9_.:/-]+$" : pattern);
        return result;
    }

    private static JsonObject enumeration(String... values) {
        JsonObject result = text("");
        JsonArray options = new JsonArray();
        for (String value : values) options.add(value);
        result.add("enum", options);
        return result;
    }

    private static JsonObject array(JsonObject items, int minimum) {
        JsonObject result = type("array");
        result.add("items", items);
        result.addProperty("minItems", minimum);
        result.addProperty("maxItems", 1024);
        return result;
    }
}
